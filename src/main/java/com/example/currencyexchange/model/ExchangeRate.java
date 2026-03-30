package com.example.currencyexchange.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents the latest exchange rate between two currencies.
 */
@Entity
@Table(name = "exchange_rates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"from_currency_id", "to_currency_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_currency_id", nullable = false)
    private Currency fromCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_currency_id", nullable = false)
    private Currency toCurrency;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal rate;

    /** Source API provider that supplied this rate */
    @Column(length = 100)
    private String provider;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        fetchedAt = LocalDateTime.now();
    }
}
