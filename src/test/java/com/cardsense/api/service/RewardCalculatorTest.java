package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RewardCalculatorTest {

    private RewardCalculator calculator;

    @BeforeEach
    public void setup() {
        calculator = new RewardCalculator();
    }

    // --- PERCENT ---

    @Test
    public void testPercentReward() {
        Promotion promo = promotion("PERCENT", BigDecimal.valueOf(3.0), null, null);
        assertEquals(30, calculator.calculateReward(promo, 1000));
    }

    @Test
    public void testPercentRewardCappedByMaxCashback() {
        Promotion promo = promotion("PERCENT", BigDecimal.valueOf(3.0), 20, null);
        assertEquals(20, calculator.calculateReward(promo, 1000));
    }

    @Test
    public void testPercentRewardBelowMinAmount() {
        Promotion promo = promotion("PERCENT", BigDecimal.valueOf(3.0), null, 500);
        assertEquals(0, calculator.calculateReward(promo, 400));
    }

    // --- FIXED ---

    @Test
    public void testFixedReward() {
        Promotion promo = promotion("FIXED", BigDecimal.valueOf(50), null, null);
        assertEquals(50, calculator.calculateReward(promo, 1000));
    }

    @Test
    public void testFixedRewardCappedBySanityGuard() {
        // Fixed reward > transaction amount is capped at transaction amount
        Promotion promo = promotion("FIXED", BigDecimal.valueOf(200), null, null);
        assertEquals(100, calculator.calculateReward(promo, 100));
    }

    // --- POINTS: percentage-rate (value < 30) ---

    @Test
    public void testPointsPercentageRate() {
        // "5% P幣回饋" — cashbackValue=5 is a percentage rate
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(5.0), null, null);
        assertEquals(50, calculator.calculateReward(promo, 1000));
    }

    @Test
    public void testPointsSmallPercentageRate() {
        // "0.2% 玉山e point" — cashbackValue=0.2
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(0.2), null, null);
        assertEquals(2, calculator.calculateReward(promo, 1000));
    }

    @Test
    public void testPointsJustBelowThreshold() {
        // cashbackValue=29 is still treated as a percentage (29% = 290 on 1000)
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(29), null, null);
        assertEquals(290, calculator.calculateReward(promo, 1000));
    }

    // --- POINTS: fixed-bonus (value >= 30) ---

    @Test
    public void testPointsFixedBonusAtThreshold() {
        // cashbackValue=30 is the boundary — treated as FIXED bonus
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(30), null, null);
        assertEquals(30, calculator.calculateReward(promo, 1000));
    }

    @Test
    public void testPointsLargeFixedBonusDoesNotExplodeReturn() {
        // "最高享2000點" — without the fix this would clamp to transactionAmount (100% return)
        // With fix: treated as FIXED 2000, capped at transactionAmount=1000
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(2000), null, null);
        assertEquals(1000, calculator.calculateReward(promo, 1000));
    }

    @Test
    public void testPointsFixedBonusDoesNotScaleWithAmount() {
        // Fixed-bonus POINTS should return the same value regardless of transaction amount
        // (not scaled by amount), unlike percentage-rate POINTS
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(50), null, null);
        assertEquals(50, calculator.calculateReward(promo, 500));
        assertEquals(50, calculator.calculateReward(promo, 5000));
    }

    @Test
    public void testPointsFixedBonusWithMaxCashback() {
        // maxCashback acts as a further cap on fixed bonus
        Promotion promo = promotion("POINTS", BigDecimal.valueOf(2000), 500, null);
        assertEquals(500, calculator.calculateReward(promo, 10000));
    }

    // --- Break-even: POINTS percentage-rate qualifies as variable reward ---

    @Test
    public void testBreakEvenForFixedVsPointsPercentageRate() {
        Promotion fixedPromo = promotion("FIXED", BigDecimal.valueOf(50), null, null);
        Promotion pointsRatePromo = promotion("POINTS", BigDecimal.valueOf(5.0), null, null);
        // break-even = 50 × 100 / 5 = 1000
        assertEquals(1000, calculator.calculateBreakEvenAmount(fixedPromo, pointsRatePromo));
    }

    @Test
    public void testBreakEvenForFixedVsPointsFixedBonusIsNull() {
        // Fixed-count POINTS don't behave as variable rewards, so no break-even applies
        Promotion fixedPromo = promotion("FIXED", BigDecimal.valueOf(50), null, null);
        Promotion pointsBonusPromo = promotion("POINTS", BigDecimal.valueOf(2000), null, null);
        assertNull(calculator.calculateBreakEvenAmount(fixedPromo, pointsBonusPromo));
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
}
