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
        assertRate(response, "POINTS", "_DEFAULT", "點數", "預設 1:1 台幣估值", "1.0");
        assertRate(response, "POINTS", "CTBC", "LINE Points", "中信 LINE Points 以 1:1 折抵估值", "1.0");
        assertRate(response, "POINTS", "CATHAY", "小樹點", "國泰小樹點以 1:1 折抵估值", "1.0");
        assertRate(response, "POINTS", "TAISHIN", "DAWHO Points", "台新 DAWHO Points 以 1:1 折抵估值", "1.0");
        assertRate(response, "POINTS", "ESUN", "e point", "玉山 e point 以 1:1 折抵估值", "1.0");
        assertRate(response, "POINTS", "FUBON", "momo 幣 / mmo Point", "富邦體系點數以保守 1:1 折抵估值", "1.0");
        assertRate(response, "MILES", "_DEFAULT", "航空哩程", "保守估值，適合作為一般哩程比較基準", "0.40");
        assertRate(response, "MILES", "ASIA_MILES", "亞洲萬里通", "以亞洲區段經濟艙的保守兌換價值估算", "0.40");
        assertRate(response, "MILES", "EVA_INFINITY", "長榮無限萬哩遊", "以長榮亞洲線兌換價值做保守估算", "0.50");
        assertRate(response, "MILES", "JALPAK", "JAL 哩程", "以日航經濟艙兌換價值做保守估算", "0.35");
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

    @Test
    void fallsBackToSystemDefaultsForUnknownBanksWithoutOverrides() {
        ExchangeRateService service = new ExchangeRateService();
        service.init();

        assertEquals(0, new BigDecimal("1.0").compareTo(service.getPointValueRate("UNKNOWN_BANK", Map.of())));
        assertEquals(0, new BigDecimal("0.40").compareTo(service.getMileValueRate("UNKNOWN_BANK", Map.of())));
    }

    private static void assertRate(
            ExchangeRateService.ExchangeRateResponse response,
            String type,
            String bank,
            String unit,
            String note,
            String value
    ) {
        ExchangeRateService.ExchangeRateEntry entry = response.rates().stream()
                .filter(rate -> type.equals(rate.type()) && bank.equals(rate.bank()))
                .findFirst()
                .orElse(null);

        assertNotNull(entry, type + "." + bank + " should exist");
        assertEquals(unit, entry.unit());
        assertEquals(note, entry.note());
        assertEquals(0, new BigDecimal(value).compareTo(entry.value()));
    }
}
