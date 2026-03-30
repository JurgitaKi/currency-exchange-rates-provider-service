package com.example.currencyexchange.exception;

/**
 * Thrown when insufficient historical data is available for trend calculations.
 */
public class InsufficientDataException extends RuntimeException {

    public InsufficientDataException(String message) {
        super(message);
    }
}
