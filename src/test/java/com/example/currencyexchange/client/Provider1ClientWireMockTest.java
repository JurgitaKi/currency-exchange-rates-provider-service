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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Provider1Client} using WireMock to simulate the external provider API.
 */
class Provider1ClientWireMockTest {

    private WireMockServer wireMockServer;
    private Provider1Client client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        client = new Provider1Client(
                "http://localhost:" + wireMockServer.port(),
                WebClient.builder()
        );
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void fetchRates_parsesRatesFromProviderResponse() {
        wireMockServer.stubFor(get(urlEqualTo("/rates"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "provider": "Provider1",
                                  "base": "EUR",
                                  "rates": {
                                    "USD": 1.0850,
                                    "GBP": 0.8623,
                                    "JPY": 161.23
                                  },
                                  "timestamp": 1700000000
                                }
                                """)));

        Map<String, BigDecimal> rates = client.fetchRates("EUR", List.of("USD", "GBP", "JPY"));

        assertThat(rates).hasSize(3);
        assertThat(rates.get("USD")).isEqualByComparingTo(new BigDecimal("1.0850"));
        assertThat(rates.get("GBP")).isEqualByComparingTo(new BigDecimal("0.8623"));
        assertThat(rates.get("JPY")).isEqualByComparingTo(new BigDecimal("161.23"));

        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/rates")));
    }

    @Test
    void fetchRates_throwsOnServerError() {
        wireMockServer.stubFor(get(urlEqualTo("/rates"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        assertThatThrownBy(() -> client.fetchRates("EUR", List.of("USD")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Provider1");
    }

    @Test
    void fetchRates_throwsOnEmptyBody() {
        wireMockServer.stubFor(get(urlEqualTo("/rates"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("null")));

        Map<String, BigDecimal> rates = client.fetchRates("EUR", List.of("USD"));
        assertThat(rates).isEmpty();
    }

    @Test
    void getProviderName_returnsProvider1() {
        assertThat(client.getProviderName()).isEqualTo("Provider1");
    }
}
