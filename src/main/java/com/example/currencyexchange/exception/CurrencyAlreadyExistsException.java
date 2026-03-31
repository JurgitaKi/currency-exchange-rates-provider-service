package com.example.currencyexchange.exception;

/**
 * Thrown when attempting to add a currency that already exists.
 */
public class CurrencyAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CurrencyAlreadyExistsException(String code) {
        super("Currency already exists: " + code);
    }
}
