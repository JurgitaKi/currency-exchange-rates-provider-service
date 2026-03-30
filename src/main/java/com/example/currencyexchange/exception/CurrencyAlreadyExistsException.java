package com.example.currencyexchange.exception;

/**
 * Thrown when attempting to add a currency that already exists.
 */
public class CurrencyAlreadyExistsException extends RuntimeException {

    public CurrencyAlreadyExistsException(String code) {
        super("Currency already exists: " + code);
    }
}
