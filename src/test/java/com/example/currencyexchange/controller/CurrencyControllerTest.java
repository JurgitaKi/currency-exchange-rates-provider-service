package com.example.currencyexchange.controller;

import com.example.currencyexchange.config.SecurityConfig;
import com.example.currencyexchange.dto.CurrencyResponse;
import com.example.currencyexchange.dto.ExchangeRateResponse;
import com.example.currencyexchange.dto.TrendResponse;
import com.example.currencyexchange.exception.CurrencyAlreadyExistsException;
import com.example.currencyexchange.exception.CurrencyNotFoundException;
import com.example.currencyexchange.exception.InsufficientDataException;
import com.example.currencyexchange.service.CurrencyService;
import com.example.currencyexchange.service.ExchangeRateService;
import com.example.currencyexchange.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CurrencyController.class)
@Import(SecurityConfig.class)
class CurrencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrencyService currencyService;

    @MockBean
    private ExchangeRateService exchangeRateService;

        @MockBean
        private CustomUserDetailsService customUserDetailsService;

    // -------------------------------------------------------------------------
    // GET /api/v1/currency
    // -------------------------------------------------------------------------

    @Test
        @WithMockUser(roles = "USER")
    void getCurrencies_returnsListWithoutAuth() throws Exception {
        CurrencyResponse usd = CurrencyResponse.builder()
                .id(1L).code("USD").name("US Dollar").active(true).createdAt(LocalDateTime.now()).build();

        when(currencyService.getAllCurrencies()).thenReturn(List.of(usd));

        mockMvc.perform(get("/api/v1/currency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("USD"))
                .andExpect(jsonPath("$[0].name").value("US Dollar"));
    }

    @Test
        @WithMockUser(roles = "USER")
    void getCurrencies_returnsEmptyList() throws Exception {
        when(currencyService.getAllCurrencies()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/currency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/currency
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void addCurrency_adminCanAdd() throws Exception {
        CurrencyResponse response = CurrencyResponse.builder()
                .id(2L).code("EUR").name("Euro").active(true).createdAt(LocalDateTime.now()).build();

        when(currencyService.addCurrency("EUR")).thenReturn(response);

        mockMvc.perform(post("/api/v1/currency")
                        .param("currency", "EUR")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("EUR"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void addCurrency_userGetsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/currency")
                        .param("currency", "EUR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addCurrency_unauthenticatedGets401() throws Exception {
        mockMvc.perform(post("/api/v1/currency")
                        .param("currency", "EUR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addCurrency_conflictWhenAlreadyExists() throws Exception {
        when(currencyService.addCurrency("USD")).thenThrow(new CurrencyAlreadyExistsException("USD"));

        mockMvc.perform(post("/api/v1/currency")
                        .param("currency", "USD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Currency already exists: USD"));
    }

        @Test
        @WithMockUser(roles = "ADMIN")
        void addCurrency_invalidCodeReturns400() throws Exception {
                mockMvc.perform(post("/api/v1/currency")
                                                .param("currency", "US"))
                                .andExpect(status().isBadRequest());
        }

    // -------------------------------------------------------------------------
    // GET /api/v1/currency/exchange-rates
    // -------------------------------------------------------------------------

    @Test
        @WithMockUser(roles = "USER")
        void getExchangeRate_returnsConversion() throws Exception {
        ExchangeRateResponse resp = ExchangeRateResponse.builder()
                .fromCurrency("USD").toCurrency("EUR")
                .rate(new BigDecimal("0.92"))
                .amount(new BigDecimal("15"))
                .convertedAmount(new BigDecimal("13.80"))
                .provider("Frankfurter")
                .fetchedAt(LocalDateTime.now())
                .build();

        when(exchangeRateService.convert(any(), eq("USD"), eq("EUR"))).thenReturn(resp);

        mockMvc.perform(get("/api/v1/currency/exchange-rates")
                        .param("amount", "15")
                        .param("from", "USD")
                        .param("to", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value("USD"))
                .andExpect(jsonPath("$.convertedAmount").value(13.80));
    }

    @Test
        @WithMockUser(roles = "USER")
        void getExchangeRate_notFoundWhenNoRate() throws Exception {
        when(exchangeRateService.convert(any(), any(), any()))
                .thenThrow(new CurrencyNotFoundException("USD"));

        mockMvc.perform(get("/api/v1/currency/exchange-rates")
                        .param("amount", "100")
                        .param("from", "USD")
                        .param("to", "XYZ"))
                .andExpect(status().isNotFound());
    }

        @Test
        @WithMockUser(roles = "USER")
        void getExchangeRate_negativeAmountReturns400() throws Exception {
                mockMvc.perform(get("/api/v1/currency/exchange-rates")
                                                .param("amount", "-15")
                                                .param("from", "USD")
                                                .param("to", "EUR"))
                                .andExpect(status().isBadRequest());
        }

    // -------------------------------------------------------------------------
    // POST /api/v1/currency/exchange-rates/refresh
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void refreshRates_adminCanRefresh() throws Exception {
        doNothing().when(exchangeRateService).refreshRates();

        mockMvc.perform(post("/api/v1/currency/exchange-rates/refresh"))
                .andExpect(status().isOk());

        verify(exchangeRateService).refreshRates();
    }

    @Test
    @WithMockUser(roles = "USER")
    void refreshRates_userGetsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/currency/exchange-rates/refresh"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/currency/exchange-rates/trends
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void getTrends_adminCanAccess() throws Exception {
        TrendResponse trend = TrendResponse.builder()
                .fromCurrency("USD").toCurrency("EUR").period("12H")
                .startRate(new BigDecimal("0.90")).endRate(new BigDecimal("0.92"))
                .changePercent(new BigDecimal("2.2222")).trend("UP")
                .build();

        when(exchangeRateService.getTrend("USD", "EUR", "12H")).thenReturn(trend);

        mockMvc.perform(get("/api/v1/currency/exchange-rates/trends")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .param("period", "12H"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend").value("UP"))
                .andExpect(jsonPath("$.fromCurrency").value("USD"));
    }

    @Test
    @WithMockUser(roles = "PREMIUM_USER")
    void getTrends_premiumUserCanAccess() throws Exception {
        TrendResponse trend = TrendResponse.builder()
                .fromCurrency("USD").toCurrency("EUR").period("10D")
                .startRate(new BigDecimal("0.90")).endRate(new BigDecimal("0.88"))
                .changePercent(new BigDecimal("-2.2222")).trend("DOWN")
                .build();

        when(exchangeRateService.getTrend("USD", "EUR", "10D")).thenReturn(trend);

        mockMvc.perform(get("/api/v1/currency/exchange-rates/trends")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .param("period", "10D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend").value("DOWN"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTrends_regularUserGetsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/currency/exchange-rates/trends")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .param("period", "12H"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getTrends_insufficientDataReturns422() throws Exception {
        when(exchangeRateService.getTrend(any(), any(), any()))
                .thenThrow(new InsufficientDataException("Not enough data"));

        mockMvc.perform(get("/api/v1/currency/exchange-rates/trends")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .param("period", "12H"))
                .andExpect(status().isUnprocessableEntity());
    }
}
