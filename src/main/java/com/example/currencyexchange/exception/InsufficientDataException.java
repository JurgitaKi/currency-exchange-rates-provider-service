package com.example.currencyexchange.exception;

/**
 * Thrown when insufficient historical data is available for trend calculations.
 */
public class InsufficientDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InsufficientDataException(String message) {
        super(message);
    }
}
