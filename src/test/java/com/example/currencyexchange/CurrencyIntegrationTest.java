package com.example.currencyexchange;

import com.example.currencyexchange.dto.CurrencyResponse;
import com.example.currencyexchange.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for currency management endpoints.
 * Uses a real PostgreSQL container via Testcontainers.
 */
class CurrencyIntegrationTest extends AbstractIntegrationTest {

    // -------------------------------------------------------------------------
    // GET /api/v1/currency — requires USER/PREMIUM_USER/ADMIN
    // -------------------------------------------------------------------------

    @Test
    void getCurrencies_userAccessReturnsOk() {
        HttpHeaders headers = basicAuth("user", "user123");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<CurrencyResponse[]> response =
                restTemplate.exchange("/api/v1/currency", HttpMethod.GET, request, CurrencyResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getCurrencies_unauthenticatedReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/currency", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/currency — requires ADMIN
    // -------------------------------------------------------------------------

    @Test
    void addCurrency_adminCreatesNewCurrency() {
        HttpHeaders headers = basicAuth("admin", "admin123");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<CurrencyResponse> response = restTemplate.exchange(
                "/api/v1/currency?currency=JPY",
                HttpMethod.POST,
                request,
                CurrencyResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("JPY");
    }

    @Test
    void addCurrency_duplicateReturnsConflict() {
        HttpHeaders headers = basicAuth("admin", "admin123");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // First add
        restTemplate.exchange("/api/v1/currency?currency=CHF", HttpMethod.POST, request, CurrencyResponse.class);

        // Second add – should conflict
        ResponseEntity<ErrorResponse> conflict = restTemplate.exchange(
                "/api/v1/currency?currency=CHF",
                HttpMethod.POST,
                request,
                ErrorResponse.class
        );

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).isNotNull();
        assertThat(conflict.getBody().status()).isEqualTo(409);
    }

    @Test
    void addCurrency_userRoleGetsForbidden() {
        HttpHeaders headers = basicAuth("user", "user123");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/currency?currency=SEK",
                HttpMethod.POST,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void addCurrency_unauthenticatedReturns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/currency?currency=NOK",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // Exception handler integration – structured JSON error response
    // -------------------------------------------------------------------------

    @Test
    void getExchangeRate_unknownPairReturnsStructuredError() {
        HttpHeaders headers = basicAuth("user", "user123");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
            "/api/v1/currency/exchange-rates?amount=100&from=ZZZ&to=YYY",
            HttpMethod.GET,
            request,
            ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().error()).isNotBlank();
        assertThat(response.getBody().message()).isNotBlank();
        assertThat(response.getBody().path()).contains("exchange-rates");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private HttpHeaders basicAuth(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }
}
