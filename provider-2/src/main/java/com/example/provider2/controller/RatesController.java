package com.example.provider2.controller;

import com.example.provider2.model.RatesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider 2 Exchange Rates Controller.
 * Returns hardcoded exchange rates with ±5% randomization on each call.
 */
@Slf4j
@RestController
public class RatesController {

    private static final String PROVIDER_NAME = "Provider2";
    private static final String BASE_CURRENCY = "EUR";

    // Hardcoded list B of exchange rates (EUR base) - slightly different values
    private static final Map<String, BigDecimal> HARDCODED_RATES = new LinkedHashMap<>() {{
        put("USD", new BigDecimal("1.12"));
        put("GBP", new BigDecimal("0.88"));
        put("JPY", new BigDecimal("164.0"));
        put("CHF", new BigDecimal("0.98"));
        put("AUD", new BigDecimal("1.69"));
        put("CAD", new BigDecimal("1.52"));
        put("CNY", new BigDecimal("7.89"));
        put("SEK", new BigDecimal("11.37"));
        put("PLN", new BigDecimal("4.29"));
        put("CZK", new BigDecimal("25.3"));
        put("HUF", new BigDecimal("404.0"));
        put("TRY", new BigDecimal("37.7"));
        put("BRL", new BigDecimal("6.12"));
        put("INR", new BigDecimal("91.7"));
        put("ZAR", new BigDecimal("20.3"));
        put("KRW", new BigDecimal("1502.0"));
        put("ILS", new BigDecimal("4.02"));
        put("HKD", new BigDecimal("8.52"));
        put("SGD", new BigDecimal("1.49"));
        put("NZD", new BigDecimal("1.85"));
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
