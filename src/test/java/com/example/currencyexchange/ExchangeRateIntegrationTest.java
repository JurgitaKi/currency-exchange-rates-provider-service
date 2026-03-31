package com.example.currencyexchange;

import com.example.currencyexchange.dto.ExchangeRateResponse;
import com.example.currencyexchange.dto.ErrorResponse;
import com.example.currencyexchange.dto.TrendResponse;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.model.HistoricalRate;
import com.example.currencyexchange.repository.CurrencyRepository;
import com.example.currencyexchange.repository.ExchangeRateRepository;
import com.example.currencyexchange.repository.HistoricalRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;

import com.example.currencyexchange.dto.ErrorResponse;
import com.example.currencyexchange.dto.TrendResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering refresh flow, provider failures, trends, and conversions
 * with real PostgreSQL and WireMock-backed providers.
 */
class ExchangeRateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private HistoricalRateRepository historicalRateRepository;

    @BeforeEach
    void setUpData() {
        WIREMOCK.resetAll();

        if (!currencyRepository.existsByCode("EUR")) {
            currencyRepository.save(Currency.builder().code("EUR").name("Euro").active(true).build());
        }
        if (!currencyRepository.existsByCode("USD")) {
            currencyRepository.save(Currency.builder().code("USD").name("US Dollar").active(true).build());
        }
        if (!currencyRepository.existsByCode("GBP")) {
            currencyRepository.save(Currency.builder().code("GBP").name("British Pound").active(true).build());
        }
    }

    @Test
    void refreshAndConvert_usesProviderAndPersistsToDatabase() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "base": "EUR",
                                  "date": "2026-03-31",
                                  "rates": {
                                    "USD": 1.1000,
                                    "GBP": 0.8500
                                  }
                                }
                                """)));

        HttpEntity<Void> adminRequest = new HttpEntity<>(basicAuth("admin", "admin123"));
        ResponseEntity<Void> refreshResponse = restTemplate.exchange(
                "/api/v1/currency/exchange-rates/refresh",
                HttpMethod.POST,
                adminRequest,
                Void.class
        );

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchangeRateRepository.findByFromCodeAndToCode("EUR", "USD")).isPresent();

        HttpEntity<Void> userRequest = new HttpEntity<>(basicAuth("user", "user123"));
        ResponseEntity<ExchangeRateResponse> conversion = restTemplate.exchange(
                "/api/v1/currency/exchange-rates?amount=100&from=EUR&to=USD",
                HttpMethod.GET,
                userRequest,
                ExchangeRateResponse.class
        );

        assertThat(conversion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(conversion.getBody()).isNotNull();
        assertThat(conversion.getBody().fromCurrency()).isEqualTo("EUR");
        assertThat(conversion.getBody().toCurrency()).isEqualTo("USD");

        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/latest")));
    }

    /**
     * Test: All providers fail with 503 error.
     * Expected: refreshRates() throws ExchangeRateFetchException → HTTP 503 returned.
     */
    @Test
    void refreshRates_allProvidersFail_returns503() {
        // Stub all provider endpoints to fail
        WIREMOCK.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/rates"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        HttpEntity<Void> adminRequest = new HttpEntity<>(basicAuth("admin", "admin123"));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/currency/exchange-rates/refresh",
                HttpMethod.POST,
                adminRequest,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().message()).contains("providers failed");
    }

    /**
     * Test: Retrieve trend with insufficient historical data.
     * Expected: getTrend() throws InsufficientDataException → HTTP 422 returned.
     */
    @Test
    void getTrend_insufficientHistoricalData_returns422() {
        // Setup: refresh rates first to populate exchange rates
        WIREMOCK.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "base": "EUR",
                                  "date": "2026-03-31",
                                  "rates": {
                                    "USD": 1.1000,
                                    "GBP": 0.8500
                                  }
                                }
                                """)));

        HttpEntity<Void> adminRequest = new HttpEntity<>(basicAuth("admin", "admin123"));
        restTemplate.exchange(
                "/api/v1/currency/exchange-rates/refresh",
                HttpMethod.POST,
                adminRequest,
                Void.class
        );

        // Try to get a trend with insufficient history (newly added rates have < 2 data points)
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/currency/exchange-rates/trends?from=EUR&to=USD&period=12H",
                HttpMethod.GET,
                adminRequest,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(422);
        assertThat(response.getBody().message()).contains("Not enough historical data");
    }

    /**
     * Test: Convert currency when exchange rate is not in cache (no successful refresh was done).
     * Expected: convert() throws ExchangeRateNotAvailableException → HTTP 503 returned.
     */
    @Test
    void convert_rateNotInCache_returns503() {
        HttpEntity<Void> userRequest = new HttpEntity<>(basicAuth("user", "user123"));
        
        // Try to convert without any rates in cache
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/currency/exchange-rates?amount=100&from=EUR&to=USD",
                HttpMethod.GET,
                userRequest,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().message()).contains("Exchange rate not available");
    }

    /**
     * Test: Successfully retrieve trend with sufficient historical data.
     * Expected: getTrend() returns valid TrendResponse with trend calculation.
     */
    @Test
    void getTrend_sufficientHistoricalData_returnsTrendResponse() {
        // Setup currencies
        Currency eur = currencyRepository.save(Currency.builder().code("EUR").name("Euro").active(true).build());
        Currency usd = currencyRepository.save(Currency.builder().code("USD").name("US Dollar").active(true).build());

        // Manually insert historical rates (simulating 2+ data points)
        LocalDateTime now = LocalDateTime.now();
        HistoricalRate rate1 = HistoricalRate.builder()
                .fromCurrency(eur)
                .toCurrency(usd)
                .rate(new BigDecimal("1.0000"))
                .fetchedAt(now.minusHours(12))
                .build();
        HistoricalRate rate2 = HistoricalRate.builder()
                .fromCurrency(eur)
                .toCurrency(usd)
                .rate(new BigDecimal("1.1000"))
                .fetchedAt(now)
                .build();

        historicalRateRepository.save(rate1);
        historicalRateRepository.save(rate2);

        HttpEntity<Void> adminRequest = new HttpEntity<>(basicAuth("admin", "admin123"));
        ResponseEntity<TrendResponse> response = restTemplate.exchange(
                "/api/v1/currency/exchange-rates/trends?from=EUR&to=USD&period=24H",
                HttpMethod.GET,
                adminRequest,
                TrendResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fromCurrency()).isEqualTo("EUR");
        assertThat(response.getBody().toCurrency()).isEqualTo("USD");
        assertThat(response.getBody().trend()).isEqualTo("UP");
        assertThat(response.getBody().changePercent()).isGreaterThan(BigDecimal.ZERO);
    }

    private HttpHeaders basicAuth(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }
}
