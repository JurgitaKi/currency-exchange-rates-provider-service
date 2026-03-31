package com.example.currencyexchange.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * Exchange rate client backed by Provider 1 service.
 */
@Slf4j
@Component
public class Provider1Client implements ExchangeRateClient {

    private static final String PROVIDER_NAME = "Provider1";

    private final WebClient webClient;

    public Provider1Client(
            @Value("${exchange.providers.provider1.base-url:http://localhost:8081}") String baseUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Map<String, BigDecimal> fetchRates(String baseCurrency, Iterable<String> targetCurrencies) {
        log.info("[{}] Fetching rates", PROVIDER_NAME);

        try {
            ProviderResponse response = webClient.get()
                    .uri("/rates")
                    .retrieve()
                    .bodyToMono(ProviderResponse.class)
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

    /** Internal DTO matching the Provider response. */
    private record ProviderResponse(
            String provider,
            String base,
            Map<String, BigDecimal> rates,
            long timestamp
    ) { }
}
