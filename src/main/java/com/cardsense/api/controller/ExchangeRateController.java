package com.cardsense.api.controller;

import com.cardsense.api.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/v1/exchange-rates")
    public ResponseEntity<ExchangeRateService.ExchangeRateResponse> getExchangeRates() {
        return ResponseEntity.ok(exchangeRateService.getSystemRates());
    }
}
