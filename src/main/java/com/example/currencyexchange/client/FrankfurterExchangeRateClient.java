package com.example.currencyexchange.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Exchange rate client backed by the free Frankfurter API (https://www.frankfurter.app).
 * No API key required.
 */
@Slf4j
@Component
public class FrankfurterExchangeRateClient implements ExchangeRateClient {

    private static final String PROVIDER_NAME = "Frankfurter";

    private final WebClient webClient;

    public FrankfurterExchangeRateClient(
            @Value("${exchange.providers.frankfurter.base-url:https://api.frankfurter.app}") String baseUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Map<String, BigDecimal> fetchRates(String baseCurrency, Iterable<String> targetCurrencies) {
        String symbols = String.join(",",
                StreamSupport.stream(targetCurrencies.spliterator(), false)
                        .filter(c -> !c.equalsIgnoreCase(baseCurrency))
                        .toList());

        if (symbols.isEmpty()) {
            log.debug("[{}] No target currencies to fetch", PROVIDER_NAME);
            return Collections.emptyMap();
        }

        log.info("[{}] Fetching rates: base={}, symbols={}", PROVIDER_NAME, baseCurrency, symbols);

        try {
            FrankfurterResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/latest")
                            .queryParam("base", baseCurrency)
                            .queryParam("symbols", symbols)
                            .build())
                    .retrieve()
                    .bodyToMono(FrankfurterResponse.class)
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

    /** Internal DTO matching the Frankfurter JSON response. */
    private record FrankfurterResponse(
            String base,
            String date,
            Map<String, BigDecimal> rates
    ) {}
}
