package com.example.currencyexchange.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FrankfurterExchangeRateClientTest {

    private MockWebServer mockWebServer;
    private FrankfurterExchangeRateClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        client = new FrankfurterExchangeRateClient(baseUrl, WebClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchRates_returnsRatesFromApi() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "base": "EUR",
                          "date": "2024-01-15",
                          "rates": {
                            "USD": 1.0850,
                            "GBP": 0.8623
                          }
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        Map<String, BigDecimal> rates = client.fetchRates("EUR", List.of("USD", "GBP"));

        assertThat(rates).containsKey("USD");
        assertThat(rates).containsKey("GBP");
        assertThat(rates.get("USD")).isEqualByComparingTo(new BigDecimal("1.0850"));
        assertThat(rates.get("GBP")).isEqualByComparingTo(new BigDecimal("0.8623"));
    }

    @Test
    void fetchRates_returnsEmptyMapOnError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        assertThatThrownBy(() -> client.fetchRates("EUR", List.of("USD")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Frankfurter");
    }

    @Test
    void fetchRates_returnsEmptyMapWhenTargetMatchesBase() {
        Map<String, BigDecimal> rates = client.fetchRates("EUR", List.of("EUR"));

        assertThat(rates).isEmpty();
    }

    @Test
    void getProviderName_returnsCorrectName() {
        assertThat(client.getProviderName()).isEqualTo("Frankfurter");
    }
}
