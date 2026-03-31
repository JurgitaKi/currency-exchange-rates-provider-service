package com.example.currencyexchange.exception;

/**
 * Thrown when exchange rates cannot be fetched from any provider.
 */
public class ExchangeRateFetchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExchangeRateFetchException(String message) {
        super(message);
    }

    public ExchangeRateFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
