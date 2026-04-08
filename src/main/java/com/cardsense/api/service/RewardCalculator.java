package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RewardCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * Threshold distinguishing POINTS-as-percentage from POINTS-as-fixed-bonus.
     *
     * <p>The extractor stores two semantically different values under the same POINTS type:
     * <ul>
     *   <li><b>Percentage rate</b> (value &lt; 30): extracted from "X% P幣/e point/哩" patterns.
     *       e.g. cashbackValue=5 → "5% P幣回饋" → reward = amount × 5 / 100.</li>
     *   <li><b>Fixed bonus count</b> (value &ge; 30): extracted from "送N點/哩" patterns or
     *       campaign bonus text. e.g. cashbackValue=2000 → "最高享2,000點玉山e point" →
     *       reward = 2000 (NTD-equivalent, treated like FIXED).</li>
     * </ul>
     *
     * <p>Without this split, a "2000-point bonus" would compute as amount × 2000 / 100,
     * which the sanity cap then clamps to transactionAmount — making it appear to yield
     * 100% cashback and dominate rankings incorrectly.
     *
     * <p>Note: 1 玉山 e point ≈ 0.3–1 NTD; treating fixed bonus points at face value
     * is optimistic but bounded by maxCashback if set. Proper point-to-NTD conversion
     * requires extractor changes to capture the exchange rate.
     */
    static final BigDecimal POINTS_FIXED_BONUS_THRESHOLD = BigDecimal.valueOf(30);

    public int calculateReward(Promotion promotion, int transactionAmount) {
        if (promotion.getMinAmount() != null && transactionAmount < promotion.getMinAmount()) {
            return 0;
        }

        if (promotion.getCashbackType() == null || promotion.getCashbackValue() == null) {
            return 0;
        }

        BigDecimal amount = BigDecimal.valueOf(transactionAmount);
        BigDecimal reward = switch (promotion.getCashbackType().toUpperCase()) {
            case "PERCENT" -> amount
                    .multiply(promotion.getCashbackValue())
                    .divide(ONE_HUNDRED, 0, RoundingMode.DOWN);
            case "MILES" -> amount
                    .multiply(promotion.getCashbackValue())
                    .setScale(0, RoundingMode.DOWN);
            case "POINTS" -> isPointsFixedBonus(promotion.getCashbackValue())
                    // Fixed-count bonus (e.g. "送2000點"): treat as FIXED NTD-equivalent
                    ? promotion.getCashbackValue()
                    // Percentage-rate points (e.g. "5% P幣"): same formula as PERCENT
                    : amount.multiply(promotion.getCashbackValue()).divide(ONE_HUNDRED, 0, RoundingMode.DOWN);
            case "FIXED" -> promotion.getCashbackValue();
            default -> BigDecimal.ZERO;
        };

        int rawReward = reward.intValue();

        if (promotion.getMaxCashback() != null && promotion.getMaxCashback() > 0) {
            rawReward = Math.min(rawReward, promotion.getMaxCashback());
        }

        // Sanity guard: reward should never exceed the transaction amount
        return Math.min(rawReward, transactionAmount);
    }

    public Integer calculateBreakEvenAmount(Promotion fixedPromotion, Promotion variablePromotion) {
        if (fixedPromotion == null || variablePromotion == null) {
            return null;
        }

        if (!"FIXED".equalsIgnoreCase(fixedPromotion.getCashbackType())) {
            return null;
        }

        if (!isVariableReward(variablePromotion)) {
            return null;
        }

        if (fixedPromotion.getCashbackValue() == null || variablePromotion.getCashbackValue() == null) {
            return null;
        }

        if (variablePromotion.getCashbackValue().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return fixedPromotion.getCashbackValue()
                .multiply(ONE_HUNDRED)
                .divide(variablePromotion.getCashbackValue(), 0, RoundingMode.CEILING)
                .intValue();
    }

    public Integer calculateCapSaturationAmount(Promotion promotion) {
        if (promotion == null || promotion.getMaxCashback() == null || promotion.getMaxCashback() <= 0) {
            return null;
        }

        if (!isVariableReward(promotion) || promotion.getCashbackValue() == null || promotion.getCashbackValue().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return BigDecimal.valueOf(promotion.getMaxCashback())
                .multiply(ONE_HUNDRED)
                .divide(promotion.getCashbackValue(), 0, RoundingMode.CEILING)
                .intValue();
    }

    private boolean isVariableReward(Promotion promotion) {
        if (promotion.getCashbackType() == null) {
            return false;
        }
        if ("PERCENT".equalsIgnoreCase(promotion.getCashbackType()) || "MILES".equalsIgnoreCase(promotion.getCashbackType())) {
            return true;
        }
        // Only percentage-rate POINTS behave as variable rewards for break-even analysis
        return "POINTS".equalsIgnoreCase(promotion.getCashbackType())
                && promotion.getCashbackValue() != null
                && !isPointsFixedBonus(promotion.getCashbackValue());
    }

    private boolean isPointsFixedBonus(BigDecimal cashbackValue) {
        return cashbackValue.compareTo(POINTS_FIXED_BONUS_THRESHOLD) >= 0;
    }
}
