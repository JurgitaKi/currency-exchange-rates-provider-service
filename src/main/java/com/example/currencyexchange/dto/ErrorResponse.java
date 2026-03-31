package com.example.currencyexchange.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Standard error response body.
 */
@Builder
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp
) { }
