package com.example.currencyexchange.controller;

import com.example.currencyexchange.dto.CurrencyResponse;
import com.example.currencyexchange.dto.ExchangeRateResponse;
import com.example.currencyexchange.dto.AddCurrencyRequest;
import com.example.currencyexchange.dto.ConvertRequest;
import com.example.currencyexchange.dto.TrendResponse;
import com.example.currencyexchange.service.CurrencyService;
import com.example.currencyexchange.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
@Tag(
        name = "Currency Exchange",
        description = "Endpoints for querying and managing currency exchange rates"
)
public class CurrencyController {

    private final CurrencyService currencyService;
    private final ExchangeRateService exchangeRateService;

    // -------------------------------------------------------------------------
    // 1. GET /api/v1/currency
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List supported currencies",
            description = "Returns all active currencies tracked by the service"
    )
    @ApiResponse(responseCode = "200", description = "List of currencies returned successfully")
    @GetMapping
    public ResponseEntity<List<CurrencyResponse>> getSupportedCurrencies() {
        log.debug("Fetching all supported currencies");
        return ResponseEntity.ok(currencyService.getAllCurrencies());
    }

    // -------------------------------------------------------------------------
    // 2. POST /api/v1/currency?currency=USD
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Add a currency",
            description = "Registers a new currency to be tracked. Requires ADMIN role."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Currency created"),
        @ApiResponse(responseCode = "409", description = "Currency already exists"),
        @ApiResponse(responseCode = "400", description = "Invalid currency code"),
        @ApiResponse(responseCode = "403", description = "Access denied – ADMIN role required")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CurrencyResponse> addCurrency(
            @Valid @ModelAttribute
            @Parameter(description = "ISO 4217 currency code, e.g. USD", example = "USD")
            AddCurrencyRequest request) {

        log.info("Adding currency: {}", request.currency());
        CurrencyResponse response = currencyService.addCurrency(request.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // 3. GET /api/v1/currency/exchange-rates?amount=15&from=USD&to=EUR
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Convert currency",
            description = "Converts an amount from one currency to another using the latest cached rate"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversion result returned"),
        @ApiResponse(responseCode = "404", description = "Exchange rate pair not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @GetMapping("/exchange-rates")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @Valid @ModelAttribute ConvertRequest request) {

        log.debug("Converting {} {} to {}", request.amount(), request.from(), request.to());
        ExchangeRateResponse response = exchangeRateService.convert(
                request.amount(), request.from(), request.to());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // 4. POST /api/v1/currency/exchange-rates/refresh
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Force rate refresh",
            description = "Triggers an immediate fetch from all configured providers. Requires ADMIN role."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rates refreshed successfully"),
        @ApiResponse(responseCode = "503", description = "All providers failed"),
        @ApiResponse(responseCode = "403", description = "Access denied – ADMIN role required")
    })
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

    @Operation(
            summary = "Get rate trend",
            description = "Returns percentage change over a time window. Requires ADMIN or PREMIUM_USER role."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trend data returned"),
        @ApiResponse(responseCode = "404", description = "Currency pair not found"),
        @ApiResponse(responseCode = "422", description = "Not enough historical data"),
        @ApiResponse(responseCode = "403", description = "Access denied – PREMIUM_USER or ADMIN role required")
    })
    @GetMapping("/exchange-rates/trends")
    @PreAuthorize("hasAnyRole('ADMIN', 'PREMIUM_USER')")
    public ResponseEntity<TrendResponse> getTrends(
            @RequestParam
            @NotBlank(message = "From currency must not be blank")
            @Pattern(regexp = "[A-Za-z]{3}", message = "From currency must be 3 letters")
        @Parameter(description = "Source currency code", example = "EUR")
            String from,

            @RequestParam
            @NotBlank(message = "To currency must not be blank")
            @Pattern(regexp = "[A-Za-z]{3}", message = "To currency must be 3 letters")
        @Parameter(description = "Target currency code", example = "USD")
            String to,

            @RequestParam(defaultValue = "24H")
            @Pattern(
                    regexp = "\\d+[HDMYhdmy]",
                    message = "Period must match pattern: e.g. 12H, 10D, 3M, 1Y"
            )
        @Parameter(description = "Time window, e.g. 12H, 10D, 3M, 1Y", example = "24H")
            String period) {

        log.debug("Fetching trends for {}/{} over period {}", from, to, period);
        return ResponseEntity.ok(exchangeRateService.getTrend(from, to, period));
    }
}
