package com.cardsense.api.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExchangeRateServiceTest {

    @Test
    void loadsCleanSystemRatesFromSeedData() {
        ExchangeRateService service = new ExchangeRateService();
        service.init();

        ExchangeRateService.ExchangeRateResponse response = service.getSystemRates();

        assertEquals("2026-04-10", response.version());
        assertRate(response, "POINTS", "CTBC", "LINE Points", "1.0");
        assertRate(response, "MILES", "_DEFAULT", "航空哩程", "0.40");
    }

    @Test
    void keepsCustomOverrideBehaviorForPointsAndMiles() {
        ExchangeRateService service = new ExchangeRateService();
        service.init();

        Map<String, BigDecimal> customRates = new HashMap<>();
        customRates.put("POINTS.ESUN", new BigDecimal("1.25"));
        customRates.put("MILES._DEFAULT", new BigDecimal("0.55"));

        assertEquals(new BigDecimal("1.25"), service.getPointValueRate("ESUN", customRates));
        assertEquals(new BigDecimal("0.55"), service.getMileValueRate("ANY", customRates));
    }

    private static void assertRate(
            ExchangeRateService.ExchangeRateResponse response,
            String type,
            String bank,
            String unit,
            String value
    ) {
        ExchangeRateService.ExchangeRateEntry entry = response.rates().stream()
                .filter(rate -> type.equals(rate.type()) && bank.equals(rate.bank()))
                .findFirst()
                .orElse(null);

        assertNotNull(entry, type + "." + bank + " should exist");
        assertEquals(unit, entry.unit());
        assertEquals(0, new BigDecimal(value).compareTo(entry.value()));
    }
}
