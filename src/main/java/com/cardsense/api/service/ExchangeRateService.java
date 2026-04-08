package com.cardsense.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Manages exchange rates for converting POINTS and MILES to TWD equivalents.
 *
 * <p>Loads system defaults from {@code exchange-rates.json} at startup.
 * Supports runtime override via user-supplied custom rates in the recommendation request.
 */
@Slf4j
@Service
public class ExchangeRateService {

    private static final BigDecimal FALLBACK_POINTS_RATE = BigDecimal.ONE;
    private static final BigDecimal FALLBACK_MILES_RATE = new BigDecimal("0.40");

    /** key = "POINTS.CTBC" or "MILES._DEFAULT", value = rate in TWD */
    private final Map<String, BigDecimal> systemRates = new HashMap<>();

    /** For the GET /v1/exchange-rates endpoint */
    private final List<ExchangeRateEntry> rateEntries = new ArrayList<>();

    private String version;

    @PostConstruct
    public void init() {
        try {
            loadFromClasspath("exchange-rates.json");
            log.info("Exchange rates loaded: version={}, entries={}", version, rateEntries.size());
        } catch (Exception e) {
            log.warn("Failed to load exchange-rates.json, using hardcoded fallbacks: {}", e.getMessage());
            initFallbackRates();
        }
    }

    private void loadFromClasspath(String filename) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = new ClassPathResource(filename).getInputStream();
        JsonNode root = mapper.readTree(is);

        this.version = root.has("version") ? root.get("version").asText() : "unknown";

        JsonNode rates = root.get("rates");
        if (rates == null) return;

        Iterator<Map.Entry<String, JsonNode>> typeIterator = rates.properties().iterator();
        while (typeIterator.hasNext()) {
            Map.Entry<String, JsonNode> typeEntry = typeIterator.next();
            String rewardType = typeEntry.getKey(); // "POINTS" or "MILES"
            JsonNode bankNodes = typeEntry.getValue();

            Iterator<Map.Entry<String, JsonNode>> bankIterator = bankNodes.properties().iterator();
            while (bankIterator.hasNext()) {
                Map.Entry<String, JsonNode> bankEntry = bankIterator.next();
                String bankCode = bankEntry.getKey(); // "CTBC" or "_DEFAULT"
                JsonNode detail = bankEntry.getValue();

                BigDecimal value = new BigDecimal(detail.get("value").asText());
                String unit = detail.has("unit") ? detail.get("unit").asText() : rewardType;
                String note = detail.has("note") ? detail.get("note").asText() : "";

                String key = rewardType + "." + bankCode;
                systemRates.put(key, value);
                rateEntries.add(new ExchangeRateEntry(rewardType, bankCode, unit, value, note));
            }
        }
    }

    private void initFallbackRates() {
        this.version = "fallback";
        systemRates.put("POINTS._DEFAULT", FALLBACK_POINTS_RATE);
        systemRates.put("MILES._DEFAULT", FALLBACK_MILES_RATE);
        rateEntries.add(new ExchangeRateEntry("POINTS", "_DEFAULT", "點數", FALLBACK_POINTS_RATE, "預設 1:1"));
        rateEntries.add(new ExchangeRateEntry("MILES", "_DEFAULT", "航空哩程", FALLBACK_MILES_RATE, "保守估值"));
    }

    /**
     * Resolve the TWD value of 1 POINTS unit for a given bank.
     *
     * @param bankCode  e.g. "CTBC", "ESUN"
     * @param customRates  optional user overrides (nullable)
     */
    public BigDecimal getPointValueRate(String bankCode, Map<String, BigDecimal> customRates) {
        return resolveRate("POINTS", bankCode, customRates, FALLBACK_POINTS_RATE);
    }

    /**
     * Resolve the TWD value of 1 MILES unit.
     *
     * @param bankCode  bank code (currently not bank-differentiated, uses _DEFAULT)
     * @param customRates  optional user overrides (nullable)
     */
    public BigDecimal getMileValueRate(String bankCode, Map<String, BigDecimal> customRates) {
        return resolveRate("MILES", bankCode, customRates, FALLBACK_MILES_RATE);
    }

    /**
     * Checks whether the rate was overridden by the user.
     */
    public String getRateSource(String rewardType, String bankCode, Map<String, BigDecimal> customRates) {
        if (customRates == null || customRates.isEmpty()) return "SYSTEM_DEFAULT";
        String specificKey = rewardType + "." + bankCode;
        String defaultKey = rewardType + "._DEFAULT";
        if (customRates.containsKey(specificKey) || customRates.containsKey(defaultKey)) {
            return "USER_CUSTOM";
        }
        return "SYSTEM_DEFAULT";
    }

    /**
     * Returns all system default rates for the GET /v1/exchange-rates endpoint.
     */
    public ExchangeRateResponse getSystemRates() {
        return new ExchangeRateResponse(version, rateEntries);
    }

    private BigDecimal resolveRate(String rewardType, String bankCode, Map<String, BigDecimal> customRates, BigDecimal fallback) {
        // Priority 1: user custom rate for specific bank
        if (customRates != null && !customRates.isEmpty()) {
            String specificKey = rewardType + "." + (bankCode != null ? bankCode.toUpperCase() : "_DEFAULT");
            if (customRates.containsKey(specificKey)) {
                return customRates.get(specificKey);
            }
            // Priority 2: user custom rate for default
            String defaultKey = rewardType + "._DEFAULT";
            if (customRates.containsKey(defaultKey)) {
                return customRates.get(defaultKey);
            }
        }

        // Priority 3: system rate for specific bank
        if (bankCode != null) {
            String specificKey = rewardType + "." + bankCode.toUpperCase();
            BigDecimal rate = systemRates.get(specificKey);
            if (rate != null) return rate;
        }

        // Priority 4: system default rate
        BigDecimal defaultRate = systemRates.get(rewardType + "._DEFAULT");
        return defaultRate != null ? defaultRate : fallback;
    }

    // --- Response DTOs for the endpoint ---

    public record ExchangeRateEntry(String type, String bank, String unit, BigDecimal value, String note) {}
    public record ExchangeRateResponse(String version, List<ExchangeRateEntry> rates) {}
}
