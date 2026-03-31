package com.example.currencyexchange.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for currency conversion query parameters.
 */
public record ConvertRequest(
        @NotNull(message = "Amount must be provided")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @NotEmpty(message = "From currency must not be empty")
        @Pattern(regexp = "[A-Za-z]{3}", message = "From currency must be 3 letters")
        String from,

        @NotEmpty(message = "To currency must not be empty")
        @Pattern(regexp = "[A-Za-z]{3}", message = "To currency must be 3 letters")
        String to
) {
}
