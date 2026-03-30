package com.example.currencyexchange.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Response DTO for a supported currency.
 */
@Builder
public record CurrencyResponse(
        Long id,
        String code,
        String name,
        boolean active,
        LocalDateTime createdAt
) {}
