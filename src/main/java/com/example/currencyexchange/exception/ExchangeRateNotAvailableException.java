package com.example.currencyexchange.exception;

/**
 * Thrown when an exchange rate is not available in the cache (in-memory or Redis).
 * This indicates that the rate must be refreshed from external providers.
 */
public class ExchangeRateNotAvailableException extends RuntimeException {

    public ExchangeRateNotAvailableException(String from, String to) {
        super("Exchange rate not available in cache for " + from + " -> " + to + 
              ". Please refresh rates from external providers.");
    }

    public ExchangeRateNotAvailableException(String message) {
        super(message);
    }
}
