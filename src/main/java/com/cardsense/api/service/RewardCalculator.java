package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RewardCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_MILE_VALUE = new BigDecimal("0.40");

    /**
     * Threshold distinguishing POINTS-as-percentage from POINTS-as-fixed-bonus.
     *
     * <p>The extractor stores two semantically different values under the same POINTS type:
     * <ul>
     *   <li><b>Percentage rate</b> (value &lt; 30): extracted from "X% P幣/e point/哩" patterns.
     *       e.g. cashbackValue=5 → "5% P幣回饋" → reward = amount × 5 / 100.</li>
     *   <li><b>Fixed bonus count</b> (value &ge; 30): extracted from "送N點/哩" patterns.</li>
     * </ul>
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
            case "MILES" -> {
                if (promotion.getCashbackValue().compareTo(BigDecimal.ZERO) <= 0) {
                    yield BigDecimal.ZERO;
                }
                // cashbackValue is "NTD per Mile" (e.g. 15 NTD = 1 Mile)
                BigDecimal milesEarned = amount.divide(promotion.getCashbackValue(), 2, RoundingMode.HALF_UP);
                yield milesEarned.multiply(getMileValueRate(promotion.getBankCode())).setScale(0, RoundingMode.DOWN);
            }
            case "POINTS" -> {
                BigDecimal pointsEarned = isPointsFixedBonus(promotion.getCashbackValue())
                        ? promotion.getCashbackValue()
                        : amount.multiply(promotion.getCashbackValue()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
                yield pointsEarned.multiply(getPointValueRate(promotion.getBankCode())).setScale(0, RoundingMode.DOWN);
            }
            case "FIXED" -> promotion.getCashbackValue();
            default -> BigDecimal.ZERO;
        };

        int rawReward = reward.intValue();

        if (promotion.getMaxCashback() != null && promotion.getMaxCashback() > 0) {
            // maxCashback is in native points/miles/NTD. We need to convert the native limit to NTD valuation cap
            int ntdCapLimit = switch (promotion.getCashbackType().toUpperCase()) {
                case "PERCENT", "FIXED" -> promotion.getMaxCashback();
                case "POINTS" -> BigDecimal.valueOf(promotion.getMaxCashback()).multiply(getPointValueRate(promotion.getBankCode())).intValue();
                case "MILES" -> BigDecimal.valueOf(promotion.getMaxCashback()).multiply(getMileValueRate(promotion.getBankCode())).intValue();
                default -> promotion.getMaxCashback();
            };
            rawReward = Math.min(rawReward, ntdCapLimit);
        }

        // Sanity guard: reward should never exceed the transaction amount
        return Math.min(rawReward, transactionAmount);
    }

    public Integer calculateBreakEvenAmount(Promotion fixedPromotion, Promotion variablePromotion) {
        if (fixedPromotion == null || variablePromotion == null) {
            return null;
        }

        if (!"FIXED".equalsIgnoreCase(fixedPromotion.getCashbackType()) || fixedPromotion.getCashbackValue() == null) {
            return null;
        }

        if (!isVariableReward(variablePromotion) || variablePromotion.getCashbackValue() == null || variablePromotion.getCashbackValue().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal fixedNtdValue = fixedPromotion.getCashbackValue();

        return switch (variablePromotion.getCashbackType().toUpperCase()) {
            case "MILES" -> {
                BigDecimal mileValue = getMileValueRate(variablePromotion.getBankCode());
                if (mileValue.compareTo(BigDecimal.ZERO) <= 0) yield null;
                // amount = fixed * rate / mile_value
                yield fixedNtdValue.multiply(variablePromotion.getCashbackValue())
                        .divide(mileValue, 0, RoundingMode.CEILING)
                        .intValue();
            }
            case "PERCENT" -> fixedNtdValue.multiply(ONE_HUNDRED)
                    .divide(variablePromotion.getCashbackValue(), 0, RoundingMode.CEILING)
                    .intValue();
            case "POINTS" -> {
                BigDecimal ptValue = getPointValueRate(variablePromotion.getBankCode());
                if (ptValue.compareTo(BigDecimal.ZERO) <= 0) yield null;
                yield fixedNtdValue.multiply(ONE_HUNDRED)
                        .divide(variablePromotion.getCashbackValue().multiply(ptValue), 0, RoundingMode.CEILING)
                        .intValue();
            }
            default -> null;
        };
    }

    public Integer calculateCapSaturationAmount(Promotion promotion) {
        if (promotion == null || promotion.getMaxCashback() == null || promotion.getMaxCashback() <= 0) {
            return null;
        }

        if (!isVariableReward(promotion) || promotion.getCashbackValue() == null || promotion.getCashbackValue().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return switch (promotion.getCashbackType().toUpperCase()) {
            case "MILES" -> BigDecimal.valueOf(promotion.getMaxCashback())
                    .multiply(promotion.getCashbackValue()) // MaxMiles * NTD_per_mile
                    .setScale(0, RoundingMode.CEILING)
                    .intValue();
            case "PERCENT", "POINTS" -> BigDecimal.valueOf(promotion.getMaxCashback())
                    .multiply(ONE_HUNDRED)
                    .divide(promotion.getCashbackValue(), 0, RoundingMode.CEILING)
                    .intValue();
            default -> null;
        };
    }

    private boolean isVariableReward(Promotion promotion) {
        if (promotion.getCashbackType() == null) {
            return false;
        }
        if ("PERCENT".equalsIgnoreCase(promotion.getCashbackType()) || "MILES".equalsIgnoreCase(promotion.getCashbackType())) {
            return true;
        }
        return "POINTS".equalsIgnoreCase(promotion.getCashbackType())
                && promotion.getCashbackValue() != null
                && !isPointsFixedBonus(promotion.getCashbackValue());
    }

    private boolean isPointsFixedBonus(BigDecimal cashbackValue) {
        return cashbackValue.compareTo(POINTS_FIXED_BONUS_THRESHOLD) >= 0;
    }

    /**
     * Determines the monetary value (NTD) of one airline mile based on the bank or default conservative estimation.
     */
    private BigDecimal getMileValueRate(String bankCode) {
        // High-end miles usually value around 0.4 NTD to 0.5 NTD.
        return DEFAULT_MILE_VALUE;
    }

    /**
     * Determines the monetary value (NTD) of one bank point.
     */
    private BigDecimal getPointValueRate(String bankCode) {
        if (bankCode == null) return BigDecimal.ONE;
        return switch (bankCode.toUpperCase()) {
            // Most modern reward points (CUBE, LINE Points, e-point) are 1:1 with NTD.
            case "CTBC", "CATHAY", "TAISHIN", "ESUN", "FUBON" -> BigDecimal.ONE;
            default -> BigDecimal.ONE;
        };
    }
}
