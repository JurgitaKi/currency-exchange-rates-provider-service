package com.example.provider1.controller;

import com.example.provider1.model.RatesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider 1 Exchange Rates Controller.
 * Returns hardcoded exchange rates with ±5% randomization on each call.
 */
@Slf4j
@RestController
public class RatesController {

    private static final String PROVIDER_NAME = "Provider1";
    private static final String BASE_CURRENCY = "EUR";

    // Hardcoded list A of exchange rates (EUR base)
    private static final Map<String, BigDecimal> HARDCODED_RATES = new LinkedHashMap<>() {{
        put("USD", new BigDecimal("1.10"));
        put("GBP", new BigDecimal("0.87"));
        put("JPY", new BigDecimal("163.0"));
        put("CHF", new BigDecimal("0.97"));
        put("AUD", new BigDecimal("1.68"));
        put("CAD", new BigDecimal("1.51"));
        put("CNY", new BigDecimal("7.88"));
        put("SEK", new BigDecimal("11.36"));
        put("PLN", new BigDecimal("4.28"));
        put("CZK", new BigDecimal("25.2"));
        put("HUF", new BigDecimal("403.0"));
        put("TRY", new BigDecimal("37.6"));
        put("BRL", new BigDecimal("6.11"));
        put("INR", new BigDecimal("91.6"));
        put("ZAR", new BigDecimal("20.2"));
        put("KRW", new BigDecimal("1501.0"));
        put("ILS", new BigDecimal("4.01"));
        put("HKD", new BigDecimal("8.51"));
        put("SGD", new BigDecimal("1.48"));
        put("NZD", new BigDecimal("1.84"));
    }};

    /**
     * Returns exchange rates with randomization (±5%).
     */
    @GetMapping("/rates")
    public ResponseEntity<RatesResponse> getRates() {
        log.debug("[{}] /rates endpoint called", PROVIDER_NAME);

        Map<String, BigDecimal> randomizedRates = randomizeRates(HARDCODED_RATES);

        RatesResponse response = RatesResponse.builder()
                .provider(PROVIDER_NAME)
                .base(BASE_CURRENCY)
                .rates(randomizedRates)
                .timestamp(System.currentTimeMillis())
                .build();

        log.debug("[{}] Returning {} rates", PROVIDER_NAME, randomizedRates.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Randomizes rates by ±5%: rate * (0.95 + Math.random() * 0.1)
     */
    private Map<String, BigDecimal> randomizeRates(Map<String, BigDecimal> rates) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            double randomFactor = 0.95 + Math.random() * 0.1; // 95% - 105%
            BigDecimal randomized = entry.getValue().multiply(new BigDecimal(randomFactor))
                    .setScale(4, java.math.RoundingMode.HALF_UP);
            result.put(entry.getKey(), randomized);
        }
        return result;
    }
}
