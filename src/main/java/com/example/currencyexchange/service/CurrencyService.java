package com.example.currencyexchange.service;

import com.example.currencyexchange.dto.CurrencyResponse;
import com.example.currencyexchange.exception.CurrencyAlreadyExistsException;
import com.example.currencyexchange.exception.CurrencyNotFoundException;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for managing supported currencies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrencyService {

    private final CurrencyRepository currencyRepository;

    /**
     * Returns all active currencies.
     */
    @Cacheable("currencies")
    public List<CurrencyResponse> getAllCurrencies() {
        return currencyRepository.findAllByActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Adds a new currency to the tracked list.
     *
     * @param code ISO 4217 three-letter currency code (e.g. "USD")
     * @return the created currency response
     */
    @Transactional
    @CacheEvict(value = "currencies", allEntries = true)
    public CurrencyResponse addCurrency(String code) {
        String upperCode = code.toUpperCase();

        if (currencyRepository.existsByCode(upperCode)) {
            throw new CurrencyAlreadyExistsException(upperCode);
        }

        String name = resolveName(upperCode);

        Currency currency = Currency.builder()
                .code(upperCode)
                .name(name)
                .active(true)
                .build();

        Currency saved = currencyRepository.save(currency);
        log.info("Added new currency: {}", upperCode);
        return toResponse(saved);
    }

    /**
     * Retrieves a currency entity by code, throwing if not found.
     */
    public Currency getCurrencyByCode(String code) {
        return currencyRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new CurrencyNotFoundException(code.toUpperCase()));
    }

    /**
     * Returns all active currency codes as strings.
     */
    public List<String> getActiveCurrencyCodes() {
        return currencyRepository.findAllByActiveTrue()
                .stream()
                .map(Currency::getCode)
                .toList();
    }

    private CurrencyResponse toResponse(Currency c) {
        return CurrencyResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .build();
    }

    /**
     * Returns a human-readable name for a currency code.
     * Extends this map as needed.
     */
    private String resolveName(String code) {
        return switch (code) {
            case "USD" -> "US Dollar";
            case "EUR" -> "Euro";
            case "GBP" -> "British Pound";
            case "JPY" -> "Japanese Yen";
            case "CHF" -> "Swiss Franc";
            case "CAD" -> "Canadian Dollar";
            case "AUD" -> "Australian Dollar";
            case "CNY" -> "Chinese Yuan";
            case "INR" -> "Indian Rupee";
            case "MXN" -> "Mexican Peso";
            case "BRL" -> "Brazilian Real";
            case "RUB" -> "Russian Ruble";
            case "KRW" -> "South Korean Won";
            case "SGD" -> "Singapore Dollar";
            case "HKD" -> "Hong Kong Dollar";
            case "NOK" -> "Norwegian Krone";
            case "SEK" -> "Swedish Krona";
            case "DKK" -> "Danish Krone";
            case "NZD" -> "New Zealand Dollar";
            case "ZAR" -> "South African Rand";
            default -> code + " Currency";
        };
    }
}
