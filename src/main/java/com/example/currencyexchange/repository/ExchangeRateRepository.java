package com.example.currencyexchange.repository;

import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByFromCurrencyAndToCurrency(Currency fromCurrency, Currency toCurrency);

    @Query("SELECT e FROM ExchangeRate e WHERE e.fromCurrency.code = :fromCode AND e.toCurrency.code = :toCode")
    Optional<ExchangeRate> findByFromCodeAndToCode(String fromCode, String toCode);
}
