package com.budgettracker.backend.controller;

import com.budgettracker.backend.service.CurrencyExchangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/v1/exchange-rates")
public class ExchangeRateController {

    private final CurrencyExchangeService currencyExchangeService;

    @Autowired
    public ExchangeRateController(CurrencyExchangeService currencyExchangeService) {
        this.currencyExchangeService = currencyExchangeService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getExchangeRate(
            @RequestParam String from,
            @RequestParam String to) {
        BigDecimal rate = currencyExchangeService.getExchangeRate(from, to);
        return ResponseEntity.ok(Map.of(
                "from", from.toUpperCase(),
                "to", to.toUpperCase(),
                "rate", rate
        ));
    }
}
