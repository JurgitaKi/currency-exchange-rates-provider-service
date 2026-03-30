package com.example.currencyexchange.service;

import com.example.currencyexchange.client.ExchangeRateClient;
import com.example.currencyexchange.exception.ExchangeRateFetchException;
import com.example.currencyexchange.exception.InsufficientDataException;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.model.ExchangeRate;
import com.example.currencyexchange.model.HistoricalRate;
import com.example.currencyexchange.repository.CurrencyRepository;
import com.example.currencyexchange.repository.ExchangeRateRepository;
import com.example.currencyexchange.repository.HistoricalRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private List<ExchangeRateClient> clients;

    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private HistoricalRateRepository historicalRateRepository;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    private Currency eur;
    private Currency usd;
    private Currency gbp;

    @BeforeEach
    void setUp() {
        eur = Currency.builder().id(1L).code("EUR").name("Euro").active(true).build();
        usd = Currency.builder().id(2L).code("USD").name("US Dollar").active(true).build();
        gbp = Currency.builder().id(3L).code("GBP").name("British Pound").active(true).build();
    }

    @Test
    void convert_returnsCorrectConvertedAmount() {
        ExchangeRate rate = ExchangeRate.builder()
                .fromCurrency(usd)
                .toCurrency(eur)
                .rate(new BigDecimal("0.9200"))
                .provider("Frankfurter")
                .fetchedAt(LocalDateTime.now())
                .build();

        when(exchangeRateRepository.findByFromCodeAndToCode("USD", "EUR"))
                .thenReturn(Optional.of(rate));

        var result = exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR");

        assertThat(result.fromCurrency()).isEqualTo("USD");
        assertThat(result.toCurrency()).isEqualTo("EUR");
        assertThat(result.convertedAmount()).isEqualByComparingTo(new BigDecimal("92.0000"));
    }

    @Test
    void convert_throwsWhenRateNotFound() {
        when(exchangeRateRepository.findByFromCodeAndToCode("USD", "JPY"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> exchangeRateService.convert(BigDecimal.TEN, "USD", "JPY"))
                .isInstanceOf(com.example.currencyexchange.exception.CurrencyNotFoundException.class);
    }

    @Test
    void getTrend_calculatesCorrectPercentage() {
        LocalDateTime now = LocalDateTime.now();
        HistoricalRate h1 = HistoricalRate.builder()
                .fromCurrency(usd).toCurrency(eur)
                .rate(new BigDecimal("0.9000"))
                .fetchedAt(now.minusHours(12))
                .build();
        HistoricalRate h2 = HistoricalRate.builder()
                .fromCurrency(usd).toCurrency(eur)
                .rate(new BigDecimal("0.9450"))
                .fetchedAt(now)
                .build();

        when(historicalRateRepository.findByFromCodeAndToCodeSince(eq("USD"), eq("EUR"), any()))
                .thenReturn(List.of(h1, h2));

        var result = exchangeRateService.getTrend("USD", "EUR", "12H");

        assertThat(result.trend()).isEqualTo("UP");
        assertThat(result.changePercent()).isPositive();
    }

    @Test
    void getTrend_throwsWhenInsufficientData() {
        when(historicalRateRepository.findByFromCodeAndToCodeSince(any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> exchangeRateService.getTrend("USD", "EUR", "12H"))
                .isInstanceOf(InsufficientDataException.class);
    }

    @Test
    void getTrend_throwsOnInvalidPeriodFormat() {
        assertThatThrownBy(() -> exchangeRateService.getTrend("USD", "EUR", "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid period format");
    }

    @Test
    void refreshRates_noActiveCurrencies_doesNothing() {
        when(currencyRepository.findAllByActiveTrue()).thenReturn(List.of());

        assertThatCode(() -> exchangeRateService.refreshRates()).doesNotThrowAnyException();

        verifyNoInteractions(exchangeRateRepository, historicalRateRepository);
    }

    @Test
    void refreshRates_throwsWhenAllProvidersFail() {
        when(currencyRepository.findAllByActiveTrue()).thenReturn(List.of(usd, gbp));

        ExchangeRateClient client = mock(ExchangeRateClient.class);
        when(clients.iterator()).thenReturn(List.of(client).iterator());
        when(client.fetchRates(any(), any())).thenThrow(new RuntimeException("API down"));

        assertThatThrownBy(() -> exchangeRateService.refreshRates())
                .isInstanceOf(ExchangeRateFetchException.class)
                .hasMessageContaining("All exchange rate providers failed");
    }
}
