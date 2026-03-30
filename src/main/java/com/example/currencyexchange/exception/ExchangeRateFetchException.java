package com.example.currencyexchange.exception;

/**
 * Thrown when exchange rates cannot be fetched from any provider.
 */
public class ExchangeRateFetchException extends RuntimeException {

    public ExchangeRateFetchException(String message) {
        super(message);
    }

    public ExchangeRateFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
