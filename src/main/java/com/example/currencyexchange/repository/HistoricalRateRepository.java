package com.example.currencyexchange.repository;

import com.example.currencyexchange.model.HistoricalRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoricalRateRepository extends JpaRepository<HistoricalRate, Long> {

    @Query("""
           SELECT h FROM HistoricalRate h
           WHERE h.fromCurrency.code = :fromCode
             AND h.toCurrency.code   = :toCode
             AND h.fetchedAt        >= :since
           ORDER BY h.fetchedAt ASC
           """)
    List<HistoricalRate> findByFromCodeAndToCodeSince(String fromCode, String toCode, LocalDateTime since);

    @Query("""
           SELECT h FROM HistoricalRate h
           WHERE h.fromCurrency.code = :fromCode
             AND h.toCurrency.code   = :toCode
           ORDER BY h.fetchedAt DESC
           LIMIT 1
           """)
    java.util.Optional<HistoricalRate> findLatestByFromCodeAndToCode(String fromCode, String toCode);
}
