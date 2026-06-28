package com.budgettracker.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public class CurrencyExchangeServiceTest {

    private CurrencyExchangeService service;
    private RestTemplate mockRestTemplate;

    @BeforeEach
    public void setUp() {
        service = new CurrencyExchangeService();
        mockRestTemplate = Mockito.mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", mockRestTemplate);
    }

    @Test
    public void testFetchLatestRates_ApiDisabled() {
        ReflectionTestUtils.setField(service, "apiEnabled", false);
        service.fetchLatestRates();
        // Since apiEnabled is false, restTemplate should not be called
        Mockito.verifyNoInteractions(mockRestTemplate);
    }

    @Test
    public void testFetchLatestRates_Success() {
        ReflectionTestUtils.setField(service, "apiEnabled", true);

        // Prepare mock FrankfurterResponse
        CurrencyExchangeService.FrankfurterResponse mockResponse = new CurrencyExchangeService.FrankfurterResponse();
        mockResponse.setDate("2026-06-28");
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1.080000"));
        rates.put("RON", new BigDecimal("4.970000"));
        mockResponse.setRates(rates);

        Mockito.when(mockRestTemplate.getForObject(anyString(), eq(CurrencyExchangeService.FrankfurterResponse.class)))
                .thenReturn(mockResponse);

        service.fetchLatestRates();

        // Verify rates were cached
        Map<String, BigDecimal> cachedRates = (Map<String, BigDecimal>) ReflectionTestUtils.getField(service, "cachedRatesFromEur");
        assertNotNull(cachedRates);
        assertEquals(new BigDecimal("1.080000"), cachedRates.get("USD"));
        assertEquals(new BigDecimal("4.970000"), cachedRates.get("RON"));
        assertNotNull(ReflectionTestUtils.getField(service, "lastFetched"));
    }

    @Test
    public void testFetchLatestRates_NullResponse() {
        ReflectionTestUtils.setField(service, "apiEnabled", true);

        Mockito.when(mockRestTemplate.getForObject(anyString(), eq(CurrencyExchangeService.FrankfurterResponse.class)))
                .thenReturn(null);

        // Should handle null gracefully without throwing exception
        assertDoesNotThrow(() -> service.fetchLatestRates());
    }

    @Test
    public void testFetchLatestRates_Exception() {
        ReflectionTestUtils.setField(service, "apiEnabled", true);

        Mockito.when(mockRestTemplate.getForObject(anyString(), eq(CurrencyExchangeService.FrankfurterResponse.class)))
                .thenThrow(new RuntimeException("API connection failure"));

        // Should log and fall back gracefully without throwing exception
        assertDoesNotThrow(() -> service.fetchLatestRates());
    }

    @Test
    public void testGetExchangeRate_EqualCurrencies() {
        BigDecimal rate = service.getExchangeRate("USD", "USD");
        assertEquals(0, BigDecimal.ONE.compareTo(rate));
    }

    @Test
    public void testGetExchangeRate_StaticFallback() {
        // EUR -> USD static rate is 1.080000
        BigDecimal rate = service.getExchangeRate("EUR", "USD");
        assertEquals(new BigDecimal("1.080000"), rate);

        // USD -> EUR static rate is 0.930000
        rate = service.getExchangeRate("USD", "EUR");
        assertEquals(new BigDecimal("0.930000"), rate);
        
        // Unsupported static rate should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> service.getExchangeRate("USD", "RON"));
    }

    @Test
    public void testGetExchangeRate_LazyRefreshTriggered() {
        // Set lastFetched to null to trigger lazy-refresh check branch
        ReflectionTestUtils.setField(service, "lastFetched", null);
        ReflectionTestUtils.setField(service, "apiEnabled", false); // skip actual API fetch in init

        // This call will trigger fetchLatestRates
        BigDecimal rate = service.getExchangeRate("EUR", "USD");
        assertEquals(new BigDecimal("1.080000"), rate);
    }
}
