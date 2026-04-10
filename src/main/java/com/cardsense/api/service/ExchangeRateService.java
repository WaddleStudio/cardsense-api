package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
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
import java.util.Locale;
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
    private final Map<String, ExchangeRateEntry> systemRateEntries = new HashMap<>();

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
        if (rates == null) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> typeIterator = rates.properties().iterator();
        while (typeIterator.hasNext()) {
            Map.Entry<String, JsonNode> typeEntry = typeIterator.next();
            String rewardType = typeEntry.getKey();
            JsonNode bankNodes = typeEntry.getValue();

            Iterator<Map.Entry<String, JsonNode>> bankIterator = bankNodes.properties().iterator();
            while (bankIterator.hasNext()) {
                Map.Entry<String, JsonNode> bankEntry = bankIterator.next();
                String bankCode = bankEntry.getKey();
                JsonNode detail = bankEntry.getValue();

                BigDecimal value = new BigDecimal(detail.get("value").asText());
                String unit = detail.has("unit") ? detail.get("unit").asText() : rewardType;
                String note = detail.has("note") ? detail.get("note").asText() : "";

                addRateEntry(rewardType, bankCode, unit, value, note);
            }
        }
    }

    private void initFallbackRates() {
        this.version = "fallback";
        addRateEntry("POINTS", "_DEFAULT", "點數", FALLBACK_POINTS_RATE, "預設 1:1");
        addRateEntry("MILES", "_DEFAULT", "航空哩程", FALLBACK_MILES_RATE, "保守估值");
    }

    /**
     * Resolve the TWD value of 1 POINTS unit for a given bank.
     *
     * @param bankCode e.g. "CTBC", "ESUN"
     * @param customRates optional user overrides (nullable)
     */
    public BigDecimal getPointValueRate(String bankCode, Map<String, BigDecimal> customRates) {
        return resolveRate("POINTS", bankCode, customRates, FALLBACK_POINTS_RATE);
    }

    public BigDecimal getPointValueRateForPromotion(Promotion promotion, Map<String, BigDecimal> customRates) {
        return resolveRewardRate("POINTS", promotion, customRates).rate();
    }

    /**
     * Resolve the TWD value of 1 MILES unit.
     *
     * @param bankCode bank code (or resolved miles profile key)
     * @param customRates optional user overrides (nullable)
     */
    public BigDecimal getMileValueRate(String bankCode, Map<String, BigDecimal> customRates) {
        return resolveRate("MILES", bankCode, customRates, FALLBACK_MILES_RATE);
    }

    public BigDecimal getMileValueRateForPromotion(Promotion promotion, Map<String, BigDecimal> customRates) {
        return resolveRewardRate("MILES", promotion, customRates).rate();
    }

    /**
     * Checks whether the rate was overridden by the user.
     */
    public String getRateSource(String rewardType, String bankCode, Map<String, BigDecimal> customRates) {
        if (customRates == null || customRates.isEmpty()) {
            return "SYSTEM_DEFAULT";
        }
        String normalizedCode = normalizeCode(bankCode);
        String specificKey = rewardType + "." + normalizedCode;
        String defaultKey = rewardType + "._DEFAULT";
        if (customRates.containsKey(specificKey) || customRates.containsKey(defaultKey)) {
            return "USER_CUSTOM";
        }
        return "SYSTEM_DEFAULT";
    }

    public String getRateSourceForPromotion(String rewardType, Promotion promotion, Map<String, BigDecimal> customRates) {
        return getRateSource(rewardType, resolveRewardCode(rewardType, promotion), customRates);
    }

    public ExchangeRateResolution resolveRewardRate(String rewardType, Promotion promotion, Map<String, BigDecimal> customRates) {
        String resolvedCode = resolveRewardCode(rewardType, promotion);
        BigDecimal fallback = "MILES".equalsIgnoreCase(rewardType) ? FALLBACK_MILES_RATE : FALLBACK_POINTS_RATE;
        String resolvedKey = resolveRateKey(rewardType, resolvedCode, customRates);
        ExchangeRateEntry entry = systemRateEntries.get(resolvedKey);
        if (entry == null) {
            entry = systemRateEntries.get(rewardType + "._DEFAULT");
        }

        return new ExchangeRateResolution(
                resolvedKey,
                resolveRate(rewardType, resolvedCode, customRates, fallback),
                getRateSource(rewardType, resolvedCode, customRates),
                entry != null ? entry.unit() : defaultUnitFor(rewardType),
                entry != null ? entry.note() : ""
        );
    }

    /**
     * Returns all system default rates for the GET /v1/exchange-rates endpoint.
     */
    public ExchangeRateResponse getSystemRates() {
        return new ExchangeRateResponse(version, rateEntries);
    }

    String resolveRewardCode(String rewardType, Promotion promotion) {
        if (promotion == null) {
            return "_DEFAULT";
        }
        if ("MILES".equalsIgnoreCase(rewardType)) {
            return resolveMilesProgramCode(promotion);
        }
        return normalizeCode(promotion.getBankCode());
    }

    private BigDecimal resolveRate(String rewardType, String bankCode, Map<String, BigDecimal> customRates, BigDecimal fallback) {
        String resolvedKey = resolveRateKey(rewardType, bankCode, customRates);
        if (resolvedKey == null) {
            return fallback;
        }

        if (customRates != null && customRates.containsKey(resolvedKey)) {
            return customRates.get(resolvedKey);
        }

        BigDecimal systemRate = systemRates.get(resolvedKey);
        return systemRate != null ? systemRate : fallback;
    }

    private String resolveRateKey(String rewardType, String bankCode, Map<String, BigDecimal> customRates) {
        String normalizedCode = normalizeCode(bankCode);
        String specificKey = rewardType + "." + normalizedCode;
        String defaultKey = rewardType + "._DEFAULT";

        if (customRates != null && !customRates.isEmpty()) {
            if (customRates.containsKey(specificKey)) {
                return specificKey;
            }
            if (customRates.containsKey(defaultKey)) {
                return defaultKey;
            }
        }

        if (systemRates.containsKey(specificKey)) {
            return specificKey;
        }
        if (systemRates.containsKey(defaultKey)) {
            return defaultKey;
        }
        return null;
    }

    private void addRateEntry(String rewardType, String bankCode, String unit, BigDecimal value, String note) {
        String key = rewardType + "." + bankCode;
        ExchangeRateEntry entry = new ExchangeRateEntry(rewardType, bankCode, unit, value, note);
        systemRates.put(key, value);
        systemRateEntries.put(key, entry);
        rateEntries.add(entry);
    }

    private String resolveMilesProgramCode(Promotion promotion) {
        List<String> signals = collectPromotionSignals(promotion);

        if (containsAnySignal(
                signals,
                "EVA_INFINITY",
                "CATHAY_EVA",
                "EVA_AIR",
                "EVA AIR",
                "EVA_AIRLINES",
                "長榮",
                "INFINITY MILEAGELANDS"
        )) {
            return "EVA_INFINITY";
        }
        if (containsAnySignal(
                signals,
                "ASIA_MILES",
                "ASIA MILES",
                "亞洲萬里通",
                "CATHAY PACIFIC",
                "CATHAY_PACIFIC",
                "CATHAY AIRWAYS",
                "國泰航空",
                "CX"
        )) {
            return "ASIA_MILES";
        }
        if (containsAnySignal(
                signals,
                "JALPAK",
                "JAL",
                "JAPAN AIRLINES",
                "JAPAN_AIRLINES",
                "JAL MILEAGE BANK",
                "日本航空",
                "日航"
        )) {
            return "JALPAK";
        }

        return normalizeCode(promotion.getBankCode());
    }

    private List<String> collectPromotionSignals(Promotion promotion) {
        List<String> signals = new ArrayList<>();
        addSignal(signals, promotion.getBankCode());
        addSignal(signals, promotion.getCardCode());
        addSignal(signals, promotion.getCardName());
        addSignal(signals, promotion.getTitle());
        addSignal(signals, promotion.getPlanId());

        if (promotion.getConditions() != null) {
            for (PromotionCondition condition : promotion.getConditions()) {
                if (condition == null) {
                    continue;
                }
                addSignal(signals, condition.getType());
                addSignal(signals, condition.getValue());
                addSignal(signals, condition.getLabel());
            }
        }

        return signals;
    }

    private void addSignal(List<String> signals, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        signals.add(value.toUpperCase(Locale.ROOT));
    }

    private boolean containsAnySignal(List<String> signals, String... tokens) {
        for (String signal : signals) {
            for (String token : tokens) {
                if (signal.contains(token.toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "_DEFAULT";
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private String defaultUnitFor(String rewardType) {
        if ("MILES".equalsIgnoreCase(rewardType)) {
            return "航空哩程";
        }
        if ("POINTS".equalsIgnoreCase(rewardType)) {
            return "點數";
        }
        return rewardType;
    }

    // --- Response DTOs for the endpoint ---

    public record ExchangeRateEntry(String type, String bank, String unit, BigDecimal value, String note) {}
    public record ExchangeRateResponse(String version, List<ExchangeRateEntry> rates) {}
    public record ExchangeRateResolution(String key, BigDecimal rate, String source, String unit, String note) {}
}
