package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RewardCalculatorTest {

    private RewardCalculator calculator;

    @BeforeEach
    public void setup() {
        ExchangeRateService mockRateService = org.mockito.Mockito.mock(ExchangeRateService.class);
        org.mockito.Mockito.when(mockRateService.getPointValueRate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Map<String, BigDecimal>>any()
        )).thenReturn(BigDecimal.ONE);
        org.mockito.Mockito.when(mockRateService.getPointValueRateForPromotion(
                org.mockito.ArgumentMatchers.any(Promotion.class),
                org.mockito.ArgumentMatchers.<Map<String, BigDecimal>>any()
        )).thenReturn(BigDecimal.ONE);
        org.mockito.Mockito.when(mockRateService.getMileValueRate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Map<String, BigDecimal>>any()
        )).thenReturn(new BigDecimal("0.40"));
        org.mockito.Mockito.when(mockRateService.getMileValueRateForPromotion(
                org.mockito.ArgumentMatchers.any(Promotion.class),
                org.mockito.ArgumentMatchers.<Map<String, BigDecimal>>any()
        )).thenReturn(new BigDecimal("0.40"));
        org.mockito.Mockito.when(mockRateService.getRateSource(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Map<String, BigDecimal>>any()
        )).thenReturn("SYSTEM_DEFAULT");
        org.mockito.Mockito.when(mockRateService.resolveRewardRate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Promotion.class),
                org.mockito.ArgumentMatchers.<Map<String, BigDecimal>>any()
        )).thenAnswer(invocation -> {
            String rewardType = invocation.getArgument(0, String.class);
            if ("MILES".equalsIgnoreCase(rewardType)) {
                return new ExchangeRateService.ExchangeRateResolution(
                        "MILES._DEFAULT",
                        new BigDecimal("0.40"),
                        "SYSTEM_DEFAULT",
                        "航空哩程",
                        "保守估值"
                );
            }
            return new ExchangeRateService.ExchangeRateResolution(
                    "POINTS._DEFAULT",
                    BigDecimal.ONE,
                    "SYSTEM_DEFAULT",
                    "點數",
                    "預設 1:1"
            );
        });
        calculator = new RewardCalculator(mockRateService);
    }

    // --- PERCENT ---

    @Test
    public void testPercentReward() {
        Promotion promo = promotion("PERCENT", BigDecimal.valueOf(3.0), null, null);
        assertEquals(30, calculator.calculateReward(promo, 1000, java.util.Map.of()).getCappedReturn());
    }

    @Test
    public void testPercentRewardCappedByMaxCashback() {
        Promotion promo = promotion("PERCENT", BigDecimal.valueOf(3.0), 20, null);
        assertEquals(20, calculator.calculateReward(promo, 1000, java.util.Map.of()).getCappedReturn());
    }

    @Test
    public void testPercentRewardBelowMinAmount() {
        Promotion promo = promotion("PERCENT", BigDecimal.valueOf(3.0), null, 500);
        assertEquals(0, calculator.calculateReward(promo, 400, java.util.Map.of()).getCappedReturn());
    }

    // --- FIXED ---

    @Test
    public void testFixedReward() {
        Promotion promo = promotion("FIXED", BigDecimal.valueOf(50), null, null);
        assertEquals(50, calculator.calculateReward(promo, 1000, java.util.Map.of()).getCappedReturn());
    }

    @Test
    public void testFixedRewardCappedBySanityGuard() {
        // Fixed reward > transaction amount is capped at transaction amount
        Promotion promo = promotion("FIXED", BigDecimal.valueOf(200), null, null);
        assertEquals(100, calculator.calculateReward(promo, 100, java.util.Map.of()).getCappedReturn());
    }

    // --- POINTS: percentage-rate (value < 30) ---

    @Test
    public void testPointsPercentageRate() {
        // "5% P幣回饋" — cashbackValue=5 is a percentage rate
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(5.0), null, null);
        assertEquals(50, calculator.calculateReward(promo, 1000, java.util.Map.of()).getCappedReturn());
    }

    @Test
    public void testPointsSmallPercentageRate() {
        // "0.2% 玉山e point" — cashbackValue=0.2
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(0.2), null, null);
        assertEquals(2, calculator.calculateReward(promo, 1000, java.util.Map.of()).getCappedReturn());
    }

    @Test
    public void testPointsJustBelowThreshold() {
        // cashbackValue=29 is still treated as a percentage (29% = 290 on 1000)
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(29), null, null);
        assertEquals(290, calculator.calculateReward(promo, 1000, java.util.Map.of()).getCappedReturn());
    }

    // --- POINTS: fixed-bonus (value >= 30) ---

    @Test
    public void testPointsFixedBonusAtThreshold() {
        // cashbackValue=30 is the boundary — treated as FIXED bonus
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(30), null, null);
        assertEquals(30, calculator.calculateReward(promo, 1000, java.util.Map.of()).getCappedReturn());
    }

    @Test
    public void testPointsLargeFixedBonusDoesNotExplodeReturn() {
        // "最高享2000點" — without the fix this would clamp to transactionAmount (100% return)
        // With fix: treated as FIXED 2000, capped at transactionAmount=1000
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(2000), null, null);
        assertEquals(1000, calculator.calculateReward(promo, 1000, java.util.Map.of()).getCappedReturn());
    }

    @Test
    public void testPointsFixedBonusDoesNotScaleWithAmount() {
        // Fixed-bonus POINTS should return the same value regardless of transaction amount
        // (not scaled by amount), unlike percentage-rate POINTS
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(50), null, null);
        assertEquals(50, calculator.calculateReward(promo, 500, java.util.Map.of()).getCappedReturn());
        assertEquals(50, calculator.calculateReward(promo, 5000, java.util.Map.of()).getCappedReturn());
    }

    @Test
    public void testPointsFixedBonusWithMaxCashback() {
        // maxCashback acts as a further cap on fixed bonus
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(2000), 500, null);
        assertEquals(500, calculator.calculateReward(promo, 10000, java.util.Map.of()).getCappedReturn());
    }

    // --- Break-even: POINTS percentage-rate qualifies as variable reward ---

    @Test
    public void testBreakEvenForFixedVsPointsPercentageRate() {
        Promotion fixedPromo = promotion("FIXED", BigDecimal.valueOf(50), null, null);
        Promotion pointsRatePromo = promotion("POINTS", BigDecimal.valueOf(5.0), null, null);
        // break-even = 50 × 100 / 5 = 1000
        assertEquals(1000, calculator.calculateBreakEvenAmount(fixedPromo, pointsRatePromo, java.util.Map.of()));
    }

    @Test
    public void testBreakEvenForFixedVsPointsFixedBonusIsNull() {
        // Fixed-count POINTS don't behave as variable rewards, so no break-even applies
        Promotion fixedPromo = promotion("FIXED", BigDecimal.valueOf(50), null, null);
        Promotion pointsBonusPromo = promotion("POINTS", BigDecimal.valueOf(2000), null, null);
        assertNull(calculator.calculateBreakEvenAmount(fixedPromo, pointsBonusPromo, java.util.Map.of()));
    }

    @Test
    public void testMilesRewardUsesProgramSpecificRateForCathayEvaCard() {
        ExchangeRateService rateService = new ExchangeRateService();
        rateService.init();
        RewardCalculator realCalculator = new RewardCalculator(rateService);

        Promotion promo = milesPromotion(
                "CATHAY",
                "CATHAY_EVA",
                "國泰世華長榮航空聯名卡",
                "國外消費最優 NT$10 累積 1 里",
                null
        );

        RewardCalculator.RewardCalculationResult result = realCalculator.calculateReward(promo, 1000, Map.of());

        assertEquals(50, result.getCappedReturn());
        assertNotNull(result.getRewardDetail());
        assertEquals("長榮無限萬哩遊", result.getRewardDetail().getRawUnit());
    }

    @Test
    public void testMilesRewardUsesProfileOverrideResolvedFromPromotionMetadata() {
        ExchangeRateService rateService = new ExchangeRateService();
        rateService.init();
        RewardCalculator realCalculator = new RewardCalculator(rateService);

        Promotion promo = milesPromotion(
                "TAISHIN",
                "TAISHIN_CG003",
                "台新國泰航空聯名卡",
                "指定類別最優 NT$10 累積 1 亞洲萬里通里數",
                List.of(PromotionCondition.builder().type("TEXT").label("累積亞洲萬里通里數").value("累積亞洲萬里通里數").build())
        );

        RewardCalculator.RewardCalculationResult result = realCalculator.calculateReward(
                promo,
                1000,
                Map.of("MILES.ASIA_MILES", new BigDecimal("0.65"))
        );

        assertEquals(65, result.getCappedReturn());
        assertNotNull(result.getRewardDetail());
        assertEquals("亞洲萬里通", result.getRewardDetail().getRawUnit());
        assertEquals("USER_CUSTOM", result.getRewardDetail().getExchangeRateSource());
    }

    // --- Helpers ---

    private Promotion promotion(String cashbackType, BigDecimal cashbackValue, Integer maxCashback, Integer minAmount) {
        return Promotion.builder()
                .promoId("test-promo")
                .promoVersionId("test-ver-001")
                .cardCode("TEST_CARD")
                .cardName("Test Card")
                .bankCode("TEST")
                .bankName("Test Bank")
                .category("ONLINE")
                .recommendationScope("RECOMMENDABLE")
                .cashbackType(cashbackType)
                .cashbackValue(cashbackValue)
                .maxCashback(maxCashback)
                .minAmount(minAmount)
                .cardStatus("ACTIVE")
                .validUntil(LocalDate.of(2026, 12, 31))
                .status("ACTIVE")
                .build();
    }

    private Promotion milesPromotion(String bankCode, String cardCode, String cardName, String title, List<PromotionCondition> conditions) {
        return Promotion.builder()
                .promoId("test-promo")
                .promoVersionId("test-ver-001")
                .title(title)
                .cardCode(cardCode)
                .cardName(cardName)
                .bankCode(bankCode)
                .bankName("Test Bank")
                .category("OVERSEAS")
                .recommendationScope("RECOMMENDABLE")
                .cashbackType("MILES")
                .cashbackValue(BigDecimal.TEN)
                .cardStatus("ACTIVE")
                .validUntil(LocalDate.of(2026, 12, 31))
                .status("ACTIVE")
                .conditions(conditions)
                .build();
    }
}
