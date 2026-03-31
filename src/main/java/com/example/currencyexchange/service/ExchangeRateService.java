package com.example.currencyexchange.service;

import com.example.currencyexchange.client.ExchangeRateClient;
import com.example.currencyexchange.dto.ExchangeRateResponse;
import com.example.currencyexchange.dto.TrendResponse;
import com.example.currencyexchange.exception.ExchangeRateFetchException;
import com.example.currencyexchange.exception.ExchangeRateNotAvailableException;
import com.example.currencyexchange.exception.InsufficientDataException;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.model.ExchangeRate;
import com.example.currencyexchange.model.HistoricalRate;
import com.example.currencyexchange.repository.CurrencyRepository;
import com.example.currencyexchange.repository.ExchangeRateRepository;
import com.example.currencyexchange.repository.HistoricalRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
    private final RateCacheService rateCacheService;

    /**
     * Converts {@code amount} from one currency to another using cached exchange rates.
     * Only reads from the in-memory cache (or Redis); DOES NOT fallback to database.
     * 
     * @throws ExchangeRateNotAvailableException if rate is not found in cache
     */
    public ExchangeRateResponse convert(BigDecimal amount, String from, String to) {
        String fromUpper = from.toUpperCase();
        String toUpper = to.toUpperCase();

        // Only read from cache; no database fallback
        BigDecimal rateValue = rateCacheService.get(fromUpper, toUpper)
            .orElseThrow(() -> new ExchangeRateNotAvailableException(fromUpper, toUpper));

        BigDecimal converted = amount.multiply(rateValue)
                .setScale(4, RoundingMode.HALF_UP);

        return ExchangeRateResponse.builder()
                .fromCurrency(fromUpper)
                .toCurrency(toUpper)
                .rate(rateValue)
                .amount(amount)
                .convertedAmount(converted)
                .provider("cache")
                .fetchedAt(java.time.LocalDateTime.now())
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
    public void refreshRates() {
        rateCacheService.evictAll();
        List<Currency> activeCurrencies = currencyRepository.findAllByActiveTrue();

        if (activeCurrencies.isEmpty()) {
            log.info("No active currencies to refresh");
            return;
        }

        List<String> codes = activeCurrencies.stream().map(Currency::getCode).toList();
        log.info("Refreshing exchange rates for {} currencies", codes.size());

        // Fetch from all providers and merge into one latest view.
        Map<String, ProviderRate> mergedRates = fetchRatesFromAllProviders(BASE_CURRENCY, codes);

        if (mergedRates.isEmpty()) {
            throw new ExchangeRateFetchException("All exchange rate providers failed");
        }

        Currency baseCurrency = currencyRepository.findByCode(BASE_CURRENCY).orElse(null);

        for (Currency targetCurrency : activeCurrencies) {
            if (targetCurrency.getCode().equals(BASE_CURRENCY)) {
                continue;
            }

            ProviderRate providerRate = mergedRates.get(targetCurrency.getCode());
            if (providerRate == null) {
                log.warn("No rate found for {} from any provider", targetCurrency.getCode());
                continue;
            }

            BigDecimal rate = providerRate.rate();
            String provider = providerRate.providerName();

            // Persist latest rate
            if (baseCurrency != null) {
                saveOrUpdateExchangeRate(baseCurrency, targetCurrency, rate, provider);
                rateCacheService.put(BASE_CURRENCY, targetCurrency.getCode(), rate);
                // Also save reverse rate
                if (rate.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal reverseRate = BigDecimal.ONE.divide(rate, 10, RoundingMode.HALF_UP);
                    saveOrUpdateExchangeRate(targetCurrency, baseCurrency, reverseRate, provider);
                    rateCacheService.put(targetCurrency.getCode(), BASE_CURRENCY, reverseRate);
                }
            }
        }

        // Also persist cross-currency rates
        persistCrossRates(activeCurrencies, mergedRates);

        log.info("Exchange rates refreshed successfully for {} currencies using {} providers",
            activeCurrencies.size(), clients.size());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Holds a single rate with provider metadata. */
    private record ProviderRate(BigDecimal rate, String providerName) { }

    private Map<String, ProviderRate> fetchRatesFromAllProviders(String base, List<String> codes) {
        Map<String, ProviderRate> merged = new HashMap<>();

        for (ExchangeRateClient client : clients) {
            try {
                Map<String, BigDecimal> rates = client.fetchRates(base, codes);
                if (!rates.isEmpty()) {
                    rates.forEach((code, rate) -> merged.put(code, new ProviderRate(rate, client.getProviderName())));
                    log.info("Fetched {} rates from provider: {}", rates.size(), client.getProviderName());
                } else {
                    log.warn("Provider {} returned an empty rate set", client.getProviderName());
                }
            } catch (Exception ex) {
                log.warn("Provider {} failed, skipping. Error: {}", client.getProviderName(), ex.getMessage());
            }
        }

        return merged;
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

    private void persistCrossRates(List<Currency> currencies, Map<String, ProviderRate> baseRates) {
        // For each pair A→B compute rate using base: rate(A→B) = rate(EUR→B) / rate(EUR→A)
        for (Currency from : currencies) {
            for (Currency to : currencies) {
                if (from.getCode().equals(to.getCode())) {
                    continue;
                }
                if (from.getCode().equals(BASE_CURRENCY) || to.getCode().equals(BASE_CURRENCY)) {
                    continue;
                }

                ProviderRate fromProviderRate = baseRates.get(from.getCode());
                ProviderRate toProviderRate = baseRates.get(to.getCode());
                BigDecimal fromRate = fromProviderRate != null ? fromProviderRate.rate() : null;
                BigDecimal toRate = toProviderRate != null ? toProviderRate.rate() : null;
                if (fromRate == null || toRate == null || fromRate.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                BigDecimal crossRate = toRate.divide(fromRate, 10, RoundingMode.HALF_UP);
                String provider = toProviderRate != null ? toProviderRate.providerName() : "aggregated";
                saveOrUpdateExchangeRate(from, to, crossRate, provider);
                rateCacheService.put(from.getCode(), to.getCode(), crossRate);
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
