package com.example.currencyexchange.controller;

import com.example.currencyexchange.dto.CurrencyResponse;
import com.example.currencyexchange.dto.ExchangeRateResponse;
import com.example.currencyexchange.dto.TrendResponse;
import com.example.currencyexchange.service.CurrencyService;
import com.example.currencyexchange.service.ExchangeRateService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller for currency and exchange rate operations.
 *
 * <pre>
 * GET  /api/v1/currency                              – list supported currencies (public)
 * POST /api/v1/currency?currency=USD                 – add currency (ADMIN)
 * GET  /api/v1/currency/exchange-rates               – convert amount (public)
 * POST /api/v1/currency/exchange-rates/refresh       – force refresh (ADMIN)
 * GET  /api/v1/currency/exchange-rates/trends        – percentage change (ADMIN, PREMIUM_USER)
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/currency")
@RequiredArgsConstructor
@Validated
public class CurrencyController {

    private final CurrencyService currencyService;
    private final ExchangeRateService exchangeRateService;

    // -------------------------------------------------------------------------
    // 1. GET /api/v1/currency
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<CurrencyResponse>> getSupportedCurrencies() {
        log.debug("Fetching all supported currencies");
        return ResponseEntity.ok(currencyService.getAllCurrencies());
    }

    // -------------------------------------------------------------------------
    // 2. POST /api/v1/currency?currency=USD
    // -------------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CurrencyResponse> addCurrency(
            @RequestParam("currency")
            @NotBlank(message = "Currency code must not be blank")
            @Pattern(regexp = "[A-Za-z]{3}", message = "Currency code must be 3 letters")
            String currency) {

        log.info("Adding currency: {}", currency);
        CurrencyResponse response = currencyService.addCurrency(currency);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // 3. GET /api/v1/currency/exchange-rates?amount=15&from=USD&to=EUR
    // -------------------------------------------------------------------------

    @GetMapping("/exchange-rates")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @RequestParam
            @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be positive")
            BigDecimal amount,

            @RequestParam
            @NotBlank(message = "From currency must not be blank")
            @Pattern(regexp = "[A-Za-z]{3}", message = "From currency must be 3 letters")
            String from,

            @RequestParam
            @NotBlank(message = "To currency must not be blank")
            @Pattern(regexp = "[A-Za-z]{3}", message = "To currency must be 3 letters")
            String to) {

        log.debug("Converting {} {} to {}", amount, from, to);
        return ResponseEntity.ok(exchangeRateService.convert(amount, from, to));
    }

    // -------------------------------------------------------------------------
    // 4. POST /api/v1/currency/exchange-rates/refresh
    // -------------------------------------------------------------------------

    @PostMapping("/exchange-rates/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> refreshExchangeRates() {
        log.info("Manual exchange rate refresh triggered");
        exchangeRateService.refreshRates();
        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // 5. GET /api/v1/currency/exchange-rates/trends?from=USD&to=EUR&period=12H
    // -------------------------------------------------------------------------

    @GetMapping("/exchange-rates/trends")
    @PreAuthorize("hasAnyRole('ADMIN', 'PREMIUM_USER')")
    public ResponseEntity<TrendResponse> getTrends(
            @RequestParam
            @NotBlank(message = "From currency must not be blank")
            @Pattern(regexp = "[A-Za-z]{3}", message = "From currency must be 3 letters")
            String from,

            @RequestParam
            @NotBlank(message = "To currency must not be blank")
            @Pattern(regexp = "[A-Za-z]{3}", message = "To currency must be 3 letters")
            String to,

            @RequestParam(defaultValue = "24H")
            @Pattern(regexp = "\\d+[HDMYhdmy]", message = "Period must match pattern: e.g. 12H, 10D, 3M, 1Y")
            String period) {

        log.debug("Fetching trends for {}/{} over period {}", from, to, period);
        return ResponseEntity.ok(exchangeRateService.getTrend(from, to, period));
    }
}
