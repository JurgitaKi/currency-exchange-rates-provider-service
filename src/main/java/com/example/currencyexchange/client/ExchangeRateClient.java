package com.example.currencyexchange.client;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Contract for external exchange rate API providers.
 * Each implementation must supply rates for all requested currency pairs.
 */
public interface ExchangeRateClient {

    /**
     * Returns the provider name (used for logging and tracing).
     */
    String getProviderName();

    /**
     * Fetches the latest exchange rates relative to the given base currency.
     *
     * @param baseCurrency ISO 4217 currency code, e.g. "USD"
     * @param targetCurrencies set of ISO 4217 codes to fetch rates for
     * @return map of target currency code → rate (base = 1.0)
     */
    Map<String, BigDecimal> fetchRates(String baseCurrency, Iterable<String> targetCurrencies);
}
