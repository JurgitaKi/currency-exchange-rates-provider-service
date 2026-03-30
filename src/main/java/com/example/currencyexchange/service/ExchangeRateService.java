package com.example.currencyexchange.service;

import com.example.currencyexchange.client.ExchangeRateClient;
import com.example.currencyexchange.dto.ExchangeRateResponse;
import com.example.currencyexchange.dto.TrendResponse;
import com.example.currencyexchange.exception.CurrencyNotFoundException;
import com.example.currencyexchange.exception.ExchangeRateFetchException;
import com.example.currencyexchange.exception.InsufficientDataException;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.model.ExchangeRate;
import com.example.currencyexchange.model.HistoricalRate;
import com.example.currencyexchange.repository.CurrencyRepository;
import com.example.currencyexchange.repository.ExchangeRateRepository;
import com.example.currencyexchange.repository.HistoricalRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Business logic for fetching, refreshing, converting, and analyzing exchange rates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeRateService {

    private static final String BASE_CURRENCY = "EUR";
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^(\\d+)([HDMY])$");

    private final List<ExchangeRateClient> clients;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final HistoricalRateRepository historicalRateRepository;

    /**
     * Converts {@code amount} from one currency to another using stored rates.
     */
    @Cacheable(value = "exchangeRates", key = "#amount + '_' + #from + '_' + #to")
    public ExchangeRateResponse convert(BigDecimal amount, String from, String to) {
        ExchangeRate rate = exchangeRateRepository.findByFromCodeAndToCode(
                        from.toUpperCase(), to.toUpperCase())
                .orElseThrow(() -> new CurrencyNotFoundException(
                        "No exchange rate found for " + from + " -> " + to));

        BigDecimal converted = amount.multiply(rate.getRate())
                .setScale(4, RoundingMode.HALF_UP);

        return ExchangeRateResponse.builder()
                .fromCurrency(from.toUpperCase())
                .toCurrency(to.toUpperCase())
                .rate(rate.getRate())
                .amount(amount)
                .convertedAmount(converted)
                .provider(rate.getProvider())
                .fetchedAt(rate.getFetchedAt())
                .build();
    }

    /**
     * Returns the percentage change of an exchange rate over the given period.
     * Period format examples: 12H, 10D, 3M, 1Y
     */
    public TrendResponse getTrend(String from, String to, String period) {
        LocalDateTime since = parsePeriod(period);

        List<HistoricalRate> history = historicalRateRepository
                .findByFromCodeAndToCodeSince(from.toUpperCase(), to.toUpperCase(), since);

        if (history.size() < 2) {
            throw new InsufficientDataException(
                    "Not enough historical data for " + from + "->" + to + " over period " + period);
        }

        BigDecimal startRate = history.get(0).getRate();
        BigDecimal endRate = history.get(history.size() - 1).getRate();

        BigDecimal changePercent = endRate.subtract(startRate)
                .divide(startRate, MathContext.DECIMAL64)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);

        String trend = changePercent.compareTo(BigDecimal.ZERO) > 0 ? "UP"
                : changePercent.compareTo(BigDecimal.ZERO) < 0 ? "DOWN"
                : "STABLE";

        return TrendResponse.builder()
                .fromCurrency(from.toUpperCase())
                .toCurrency(to.toUpperCase())
                .period(period)
                .startRate(startRate)
                .endRate(endRate)
                .changePercent(changePercent)
                .trend(trend)
                .build();
    }

    /**
     * Refreshes exchange rates for all active currencies by calling external providers.
     * Falls back to the next provider if one fails.
     */
    @Transactional
    @CacheEvict(value = "exchangeRates", allEntries = true)
    public void refreshRates() {
        List<Currency> activeCurrencies = currencyRepository.findAllByActiveTrue();

        if (activeCurrencies.isEmpty()) {
            log.info("No active currencies to refresh");
            return;
        }

        List<String> codes = activeCurrencies.stream().map(Currency::getCode).toList();
        log.info("Refreshing exchange rates for {} currencies", codes.size());

        // Try each client; use the first one that succeeds (with fallback)
        FetchResult fetchResult = fetchRatesWithFallback(BASE_CURRENCY, codes);

        if (fetchResult.rates().isEmpty()) {
            throw new ExchangeRateFetchException("All exchange rate providers failed");
        }

        Map<String, BigDecimal> rates = fetchResult.rates();
        String provider = fetchResult.providerName();

        Currency baseCurrency = currencyRepository.findByCode(BASE_CURRENCY).orElse(null);

        for (Currency targetCurrency : activeCurrencies) {
            if (targetCurrency.getCode().equals(BASE_CURRENCY)) continue;

            BigDecimal rate = rates.get(targetCurrency.getCode());
            if (rate == null) {
                log.warn("No rate found for {} from any provider", targetCurrency.getCode());
                continue;
            }

            // Persist latest rate
            if (baseCurrency != null) {
                saveOrUpdateExchangeRate(baseCurrency, targetCurrency, rate, provider);
                // Also save reverse rate
                if (rate.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal reverseRate = BigDecimal.ONE.divide(rate, 10, RoundingMode.HALF_UP);
                    saveOrUpdateExchangeRate(targetCurrency, baseCurrency, reverseRate, provider);
                }
            }
        }

        // Also persist cross-currency rates
        persistCrossRates(activeCurrencies, rates, provider);

        log.info("Exchange rates refreshed successfully for {} currencies", activeCurrencies.size());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Holds the fetched rates together with the name of the provider that supplied them. */
    private record FetchResult(Map<String, BigDecimal> rates, String providerName) {}

    private FetchResult fetchRatesWithFallback(String base, List<String> codes) {
        for (ExchangeRateClient client : clients) {
            try {
                Map<String, BigDecimal> rates = client.fetchRates(base, codes);
                if (!rates.isEmpty()) {
                    log.info("Successfully fetched rates from provider: {}", client.getProviderName());
                    return new FetchResult(rates, client.getProviderName());
                }
            } catch (Exception ex) {
                log.warn("Provider {} failed, trying next. Error: {}", client.getProviderName(), ex.getMessage());
            }
        }
        return new FetchResult(Map.of(), "unknown");
    }

    private void saveOrUpdateExchangeRate(Currency from, Currency to, BigDecimal rate, String provider) {
        ExchangeRate exchangeRate = exchangeRateRepository
                .findByFromCurrencyAndToCurrency(from, to)
                .orElseGet(() -> ExchangeRate.builder()
                        .fromCurrency(from)
                        .toCurrency(to)
                        .build());

        exchangeRate.setRate(rate);
        exchangeRate.setProvider(provider);
        exchangeRateRepository.save(exchangeRate);

        // Archive to historical rates
        HistoricalRate historicalRate = HistoricalRate.builder()
                .fromCurrency(from)
                .toCurrency(to)
                .rate(rate)
                .provider(provider)
                .fetchedAt(LocalDateTime.now())
                .build();
        historicalRateRepository.save(historicalRate);
    }

    private void persistCrossRates(List<Currency> currencies, Map<String, BigDecimal> baseRates, String provider) {
        // For each pair A→B compute rate using base: rate(A→B) = rate(EUR→B) / rate(EUR→A)
        for (Currency from : currencies) {
            for (Currency to : currencies) {
                if (from.getCode().equals(to.getCode())) continue;
                if (from.getCode().equals(BASE_CURRENCY) || to.getCode().equals(BASE_CURRENCY)) continue;

                BigDecimal fromRate = baseRates.get(from.getCode());
                BigDecimal toRate = baseRates.get(to.getCode());
                if (fromRate == null || toRate == null || fromRate.compareTo(BigDecimal.ZERO) == 0) continue;

                BigDecimal crossRate = toRate.divide(fromRate, 10, RoundingMode.HALF_UP);
                saveOrUpdateExchangeRate(from, to, crossRate, provider);
            }
        }
    }

    private LocalDateTime parsePeriod(String period) {
        Matcher m = PERIOD_PATTERN.matcher(period.toUpperCase());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Invalid period format: '" + period + "'. Expected format: 12H, 10D, 3M, 1Y");
        }

        int value = Integer.parseInt(m.group(1));
        String unit = m.group(2);
        LocalDateTime now = LocalDateTime.now();

        return switch (unit) {
            case "H" -> now.minus(value, ChronoUnit.HOURS);
            case "D" -> now.minus(value, ChronoUnit.DAYS);
            case "M" -> now.minusMonths(value);
            case "Y" -> now.minusYears(value);
            default -> throw new IllegalArgumentException("Unknown period unit: " + unit);
        };
    }
}
