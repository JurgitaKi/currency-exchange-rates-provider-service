package com.example.currencyexchange.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * Exchange rate client backed by Open Exchange Rates (https://openexchangerates.org).
 * Requires an API key configured via {@code exchange.providers.openexchangerates.api-key}.
 * Only activated when the API key property is present.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "exchange.providers.openexchangerates.api-key")
public class OpenExchangeRatesClient implements ExchangeRateClient {

    private static final String PROVIDER_NAME = "OpenExchangeRates";

    private final WebClient webClient;
    private final String apiKey;

    public OpenExchangeRatesClient(
            @Value("${exchange.providers.openexchangerates.base-url:https://openexchangerates.org/api}") String baseUrl,
            @Value("${exchange.providers.openexchangerates.api-key}") String apiKey,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Map<String, BigDecimal> fetchRates(String baseCurrency, Iterable<String> targetCurrencies) {
        // Free tier only supports USD as base; for other bases we fetch USD rates and derive
        log.info("[{}] Fetching latest rates", PROVIDER_NAME);

        try {
            OpenExchangeResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/latest.json")
                            .queryParam("app_id", apiKey)
                            .queryParam("base", baseCurrency)
                            .build())
                    .retrieve()
                    .bodyToMono(OpenExchangeResponse.class)
                    .block();

            if (response == null || response.rates() == null) {
                log.warn("[{}] Received empty response", PROVIDER_NAME);
                return Collections.emptyMap();
            }

            return response.rates();
        } catch (Exception ex) {
            log.error("[{}] Failed to fetch rates: {}", PROVIDER_NAME, ex.getMessage(), ex);
            throw new RuntimeException("[" + PROVIDER_NAME + "] Rate fetch failed: " + ex.getMessage(), ex);
        }
    }

    /** Internal DTO matching the Open Exchange Rates JSON response. */
    private record OpenExchangeResponse(
            String base,
            long timestamp,
            Map<String, BigDecimal> rates
    ) {}
}
