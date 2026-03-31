package com.example.currencyexchange.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Response DTO for exchange rate trend analysis.
 */
@Builder
public record TrendResponse(
        String fromCurrency,
        String toCurrency,
        String period,
        BigDecimal startRate,
        BigDecimal endRate,
        BigDecimal changePercent,
        String trend
) { }
