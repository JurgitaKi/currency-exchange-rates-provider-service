package com.example.currencyexchange.scheduler;

import com.example.currencyexchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that periodically refreshes exchange rates.
 * Also triggers a fetch on application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateScheduler {

    private final ExchangeRateService exchangeRateService;

    /**
     * Triggered once the application context is fully started.
     * Fetches initial exchange rates if currencies are already seeded.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready — performing initial exchange rate fetch");
        try {
            exchangeRateService.refreshRates();
        } catch (Exception ex) {
            log.warn("Initial exchange rate fetch failed (this is normal if no currencies are configured yet): {}",
                    ex.getMessage());
        }
    }

    /**
     * Refreshes exchange rates every hour.
     * Cron expression: every hour at the top of the hour.
     */
    @Scheduled(cron = "${exchange.scheduler.cron:0 0 * * * *}")
    public void scheduledRefresh() {
        log.info("Scheduled exchange rate refresh triggered");
        try {
            exchangeRateService.refreshRates();
        } catch (Exception ex) {
            log.error("Scheduled exchange rate refresh failed: {}", ex.getMessage(), ex);
        }
    }
}
