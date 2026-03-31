package com.example.currencyexchange.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for a currency conversion result.
 */
@Builder
public record ExchangeRateResponse(
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        BigDecimal amount,
        BigDecimal convertedAmount,
        String provider,
        LocalDateTime fetchedAt
) { }
