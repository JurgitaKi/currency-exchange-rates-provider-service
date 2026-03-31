package com.example.currencyexchange.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for adding a new supported currency.
 */
public record AddCurrencyRequest(
        @NotEmpty(message = "Currency code must not be empty")
        @Pattern(regexp = "[A-Za-z]{3}", message = "Currency code must be 3 letters")
        String currency
) {
}
