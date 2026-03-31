package com.example.currencyexchange.exception;

/**
 * Thrown when a requested currency is not found in the database.
 */
public class CurrencyNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CurrencyNotFoundException(String code) {
        super("Currency not found: " + code);
    }
}
