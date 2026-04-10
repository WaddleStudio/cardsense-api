package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.RewardDetail;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RewardCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final ExchangeRateService exchangeRateService;

    public static final BigDecimal POINTS_FIXED_BONUS_THRESHOLD = BigDecimal.valueOf(30);

    @Getter
    @AllArgsConstructor
    public static class RewardCalculationResult {
        private final int estimatedReturn;
        private final int cappedReturn;
        private final RewardDetail rewardDetail;
    }

    public RewardCalculationResult calculateReward(Promotion promotion, int transactionAmount, Map<String, BigDecimal> customRates) {
        if (promotion.getMinAmount() != null && transactionAmount < promotion.getMinAmount()) {
            return new RewardCalculationResult(0, 0, null);
        }

        if (promotion.getCashbackType() == null || promotion.getCashbackValue() == null) {
            return new RewardCalculationResult(0, 0, null);
        }

        BigDecimal amount = BigDecimal.valueOf(transactionAmount);
        BigDecimal rawRewardValue = BigDecimal.ZERO;
        BigDecimal exchangeRate = BigDecimal.ONE;
        String rawUnit = "TWD";
        String rateSource = "SYSTEM_DEFAULT";
        String note = null;

        switch (promotion.getCashbackType().toUpperCase()) {
            case "PERCENT" -> rawRewardValue = amount.multiply(promotion.getCashbackValue())
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            case "MILES" -> {
                if (promotion.getCashbackValue().compareTo(BigDecimal.ZERO) > 0) {
                    rawRewardValue = amount.divide(promotion.getCashbackValue(), 2, RoundingMode.HALF_UP);
                    ExchangeRateService.ExchangeRateResolution resolution =
                            exchangeRateService.resolveRewardRate("MILES", promotion, customRates);
                    exchangeRate = resolution.rate();
                    rawUnit = resolution.unit();
                    rateSource = resolution.source();
                    note = resolution.note();
                }
            }
            case "POINTS" -> {
                rawRewardValue = isPointsFixedBonus(promotion.getCashbackValue())
                        ? promotion.getCashbackValue()
                        : amount.multiply(promotion.getCashbackValue()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
                ExchangeRateService.ExchangeRateResolution resolution =
                        exchangeRateService.resolveRewardRate("POINTS", promotion, customRates);
                exchangeRate = resolution.rate();
                rawUnit = resolution.unit();
                rateSource = resolution.source();
                note = resolution.note();
            }
            case "FIXED" -> rawRewardValue = promotion.getCashbackValue();
            default -> {
            }
        }

        int estimatedReturn = rawRewardValue.multiply(exchangeRate).setScale(0, RoundingMode.DOWN).intValue();
        int rawRewardIntVal = Math.min(estimatedReturn, transactionAmount);

        int cappedReturn = rawRewardIntVal;

        if (promotion.getMaxCashback() != null && promotion.getMaxCashback() > 0) {
            int ntdCapLimit = switch (promotion.getCashbackType().toUpperCase()) {
                case "PERCENT", "FIXED" -> promotion.getMaxCashback();
                case "POINTS" -> BigDecimal.valueOf(promotion.getMaxCashback())
                        .multiply(exchangeRateService.getPointValueRateForPromotion(promotion, customRates))
                        .intValue();
                case "MILES" -> BigDecimal.valueOf(promotion.getMaxCashback())
                        .multiply(exchangeRateService.getMileValueRateForPromotion(promotion, customRates))
                        .intValue();
                default -> promotion.getMaxCashback();
            };
            cappedReturn = Math.min(rawRewardIntVal, ntdCapLimit);
        }

        RewardDetail detail = RewardDetail.builder()
                .rawReward(rawRewardValue)
                .rawUnit(rawUnit)
                .exchangeRate(exchangeRate)
                .exchangeRateSource(rateSource)
                .ntdEquivalent(cappedReturn)
                .note(note)
                .build();

        return new RewardCalculationResult(rawRewardIntVal, cappedReturn, detail);
    }

    public Integer calculateBreakEvenAmount(Promotion fixedPromotion, Promotion variablePromotion, Map<String, BigDecimal> customRates) {
        if (fixedPromotion == null || variablePromotion == null) {
            return null;
        }

        if (!"FIXED".equalsIgnoreCase(fixedPromotion.getCashbackType()) || fixedPromotion.getCashbackValue() == null) {
            return null;
        }

        if (!isVariableReward(variablePromotion)
                || variablePromotion.getCashbackValue() == null
                || variablePromotion.getCashbackValue().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal fixedNtdValue = fixedPromotion.getCashbackValue();

        return switch (variablePromotion.getCashbackType().toUpperCase()) {
            case "MILES" -> {
                BigDecimal mileValue = exchangeRateService.getMileValueRateForPromotion(variablePromotion, customRates);
                if (mileValue.compareTo(BigDecimal.ZERO) <= 0) {
                    yield null;
                }
                yield fixedNtdValue.multiply(variablePromotion.getCashbackValue())
                        .divide(mileValue, 0, RoundingMode.CEILING)
                        .intValue();
            }
            case "PERCENT" -> fixedNtdValue.multiply(ONE_HUNDRED)
                    .divide(variablePromotion.getCashbackValue(), 0, RoundingMode.CEILING)
                    .intValue();
            case "POINTS" -> {
                BigDecimal pointValue = exchangeRateService.getPointValueRateForPromotion(variablePromotion, customRates);
                if (pointValue.compareTo(BigDecimal.ZERO) <= 0) {
                    yield null;
                }
                yield fixedNtdValue.multiply(ONE_HUNDRED)
                        .divide(variablePromotion.getCashbackValue().multiply(pointValue), 0, RoundingMode.CEILING)
                        .intValue();
            }
            default -> null;
        };
    }

    public Integer calculateCapSaturationAmount(Promotion promotion) {
        if (promotion == null || promotion.getMaxCashback() == null || promotion.getMaxCashback() <= 0) {
            return null;
        }

        if (!isVariableReward(promotion)
                || promotion.getCashbackValue() == null
                || promotion.getCashbackValue().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return switch (promotion.getCashbackType().toUpperCase()) {
            case "MILES" -> BigDecimal.valueOf(promotion.getMaxCashback())
                    .multiply(promotion.getCashbackValue())
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
        if ("PERCENT".equalsIgnoreCase(promotion.getCashbackType())
                || "MILES".equalsIgnoreCase(promotion.getCashbackType())) {
            return true;
        }
        return "POINTS".equalsIgnoreCase(promotion.getCashbackType())
                && promotion.getCashbackValue() != null
                && !isPointsFixedBonus(promotion.getCashbackValue());
    }

    private boolean isPointsFixedBonus(BigDecimal cashbackValue) {
        return cashbackValue.compareTo(POINTS_FIXED_BONUS_THRESHOLD) >= 0;
    }
}
