package com.example.currencyexchange.service;

import com.example.currencyexchange.exception.CurrencyAlreadyExistsException;
import com.example.currencyexchange.exception.CurrencyNotFoundException;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.repository.CurrencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private CurrencyService currencyService;

    private Currency usd;

    @BeforeEach
    void setUp() {
        usd = Currency.builder()
                .id(1L)
                .code("USD")
                .name("US Dollar")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllCurrencies_returnsMappedList() {
        when(currencyRepository.findAllByActiveTrue()).thenReturn(List.of(usd));

        var result = currencyService.getAllCurrencies();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("USD");
        assertThat(result.get(0).name()).isEqualTo("US Dollar");
    }

    @Test
    void getAllCurrencies_emptyWhenNoCurrencies() {
        when(currencyRepository.findAllByActiveTrue()).thenReturn(List.of());

        var result = currencyService.getAllCurrencies();

        assertThat(result).isEmpty();
    }

    @Test
    void addCurrency_success() {
        when(currencyRepository.existsByCode("USD")).thenReturn(false);
        when(currencyRepository.save(any(Currency.class))).thenReturn(usd);

        var result = currencyService.addCurrency("USD");

        assertThat(result.code()).isEqualTo("USD");
        verify(currencyRepository).save(any(Currency.class));
    }

    @Test
    void addCurrency_convertsToUpperCase() {
        Currency eurCurrency = Currency.builder()
                .id(2L).code("EUR").name("Euro").active(true).createdAt(LocalDateTime.now()).build();
        when(currencyRepository.existsByCode("EUR")).thenReturn(false);
        when(currencyRepository.save(any(Currency.class))).thenReturn(eurCurrency);

        var result = currencyService.addCurrency("eur");

        assertThat(result.code()).isEqualTo("EUR");
    }

    @Test
    void addCurrency_throwsWhenAlreadyExists() {
        when(currencyRepository.existsByCode("USD")).thenReturn(true);

        assertThatThrownBy(() -> currencyService.addCurrency("USD"))
                .isInstanceOf(CurrencyAlreadyExistsException.class)
                .hasMessageContaining("USD");
    }

    @Test
    void getCurrencyByCode_returnsEntityWhenFound() {
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));

        Currency result = currencyService.getCurrencyByCode("USD");

        assertThat(result.getCode()).isEqualTo("USD");
    }

    @Test
    void getCurrencyByCode_throwsWhenNotFound() {
        when(currencyRepository.findByCode("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.getCurrencyByCode("XYZ"))
                .isInstanceOf(CurrencyNotFoundException.class)
                .hasMessageContaining("XYZ");
    }

    @Test
    void getActiveCurrencyCodes_returnsCodes() {
        when(currencyRepository.findAllByActiveTrue()).thenReturn(List.of(usd));

        List<String> codes = currencyService.getActiveCurrencyCodes();

        assertThat(codes).containsExactly("USD");
    }
}
