package com.example.currencyexchange;

import com.example.currencyexchange.dto.ExchangeRateResponse;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.repository.CurrencyRepository;
import com.example.currencyexchange.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering refresh flow with real PostgreSQL and WireMock-backed providers.
 */
class ExchangeRateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

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

    private HttpHeaders basicAuth(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }
}
