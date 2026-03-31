package com.example.currencyexchange.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrankfurterExchangeRateClientTest {

    private WireMockServer wireMockServer;
    private FrankfurterExchangeRateClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        String baseUrl = "http://localhost:" + wireMockServer.port();
        client = new FrankfurterExchangeRateClient(baseUrl, WebClient.builder());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void fetchRates_returnsRatesFromApi() {
        wireMockServer.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "base": "EUR",
                                  "date": "2024-01-15",
                                  "rates": {
                                    "USD": 1.0850,
                                    "GBP": 0.8623
                                  }
                                }
                                """)));

        Map<String, BigDecimal> rates = client.fetchRates("EUR", List.of("USD", "GBP"));

        assertThat(rates).containsKey("USD");
        assertThat(rates).containsKey("GBP");
        assertThat(rates.get("USD")).isEqualByComparingTo(new BigDecimal("1.0850"));
        assertThat(rates.get("GBP")).isEqualByComparingTo(new BigDecimal("0.8623"));
    }

    @Test
    void fetchRates_returnsEmptyMapOnError() {
        wireMockServer.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

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
