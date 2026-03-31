package com.example.currencyexchange.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores historical exchange rate snapshots for trend analysis.
 */
@Entity
@Table(name = "historical_rates",
       indexes = {
           @Index(name = "idx_hist_base_target_ts", columnList = "base_currency_id,target_currency_id,timestamp")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_currency_id", nullable = false)
    private Currency fromCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_currency_id", nullable = false)
    private Currency toCurrency;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal rate;

    @Column(length = 100)
    private String provider;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime fetchedAt;
}
