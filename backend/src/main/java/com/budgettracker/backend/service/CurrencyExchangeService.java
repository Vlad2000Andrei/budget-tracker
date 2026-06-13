package com.budgettracker.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CurrencyExchangeService {

    @Autowired
    @Lazy
    private CurrencyExchangeService self;

    @Value("${app.exchange-rate.api-enabled:true}")
    private boolean apiEnabled;

    private final Map<String, Map<String, BigDecimal>> staticRatesFallback = new HashMap<>();
    private final Map<String, BigDecimal> cachedRatesFromEur = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    
    private LocalDateTime lastFetched = null;
    private static final String API_URL = "https://api.frankfurter.app/latest?from=EUR";
    private static final long CACHE_TTL_HOURS = 24;

    public CurrencyExchangeService() {
        // Initialize static rates for local development and offline/testing fallback
        addStaticRate("EUR", "RON", new BigDecimal("4.970000"));
        addStaticRate("RON", "EUR", new BigDecimal("0.201207"));
        addStaticRate("USD", "EUR", new BigDecimal("0.930000"));
        addStaticRate("EUR", "USD", new BigDecimal("1.080000"));
    }

    private void addStaticRate(String from, String to, BigDecimal rate) {
        staticRatesFallback.computeIfAbsent(from.toUpperCase(), k -> new HashMap<>())
                .put(to.toUpperCase(), rate);
    }

    @PostConstruct
    public void init() {
        // Initial fetch on application startup
        log.info("Initializing dynamic exchange rates...");
        fetchLatestRates();
    }

    // Run every day at midnight CET/local time to keep exchange rates fresh
    @Scheduled(cron = "0 0 0 * * ?")
    public void fetchLatestRates() {
        if (!apiEnabled) {
            log.info("Frankfurter API integration is disabled. Skipping remote exchange rate update.");
            return;
        }
        try {
            log.info("Fetching latest exchange rates from Frankfurter API...");
            FrankfurterResponse response = restTemplate.getForObject(API_URL, FrankfurterResponse.class);
            if (response != null && response.getRates() != null) {
                cachedRatesFromEur.clear();
                // Store base EUR rate (1.0)
                cachedRatesFromEur.put("EUR", BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP));
                response.getRates().forEach((currency, rate) -> {
                    cachedRatesFromEur.put(currency.toUpperCase(), rate.setScale(6, RoundingMode.HALF_UP));
                });
                lastFetched = LocalDateTime.now();
                log.info("Exchange rates successfully updated from Frankfurter API. Date: {}", response.getDate());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch exchange rates from Frankfurter API: {}. Falling back to static cache.", e.getMessage());
        }
    }

    @Cacheable(value = "exchangeRates", key = "#from.toUpperCase() + '-' + #to.toUpperCase()")
    public BigDecimal getExchangeRate(String from, String to) {
        String fromKey = from.toUpperCase();
        String toKey = to.toUpperCase();

        if (fromKey.equals(toKey)) {
            return BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
        }

        // Lazy-refresh check if cache is too old (e.g. 24 hours)
        if (lastFetched == null || Duration.between(lastFetched, LocalDateTime.now()).toHours() >= CACHE_TTL_HOURS) {
            fetchLatestRates();
        }

        // Try using dynamic cached rates from EUR first
        if (!cachedRatesFromEur.isEmpty()) {
            BigDecimal eurToFrom = cachedRatesFromEur.get(fromKey);
            BigDecimal eurToTo = cachedRatesFromEur.get(toKey);

            if (eurToFrom != null && eurToTo != null) {
                // Rate (from -> to) = (EUR -> to) / (EUR -> from)
                return eurToTo.divide(eurToFrom, 6, RoundingMode.HALF_UP);
            }
        }

        // Fallback to static rates if API is unavailable or requested currency pair isn't in API response
        if (staticRatesFallback.containsKey(fromKey) && staticRatesFallback.get(fromKey).containsKey(toKey)) {
            return staticRatesFallback.get(fromKey).get(toKey).setScale(6, RoundingMode.HALF_UP);
        }

        throw new IllegalArgumentException("Unsupported currency conversion from " + fromKey + " to " + toKey);
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal rate = self.getExchangeRate(from, to);
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    @Data
    public static class FrankfurterResponse {
        private String base;
        private String date;
        private Map<String, BigDecimal> rates;
    }
}
