package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RewardCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public int calculateReward(Promotion promotion, int transactionAmount) {
        if (promotion.getMinAmount() != null && transactionAmount < promotion.getMinAmount()) {
            return 0;
        }

        if (promotion.getCashbackType() == null || promotion.getCashbackValue() == null) {
            return 0;
        }

        BigDecimal amount = BigDecimal.valueOf(transactionAmount);
        BigDecimal reward = switch (promotion.getCashbackType().toUpperCase()) {
            case "PERCENT", "POINTS" -> amount
                    .multiply(promotion.getCashbackValue())
                    .divide(ONE_HUNDRED, 0, RoundingMode.DOWN);
            case "FIXED" -> promotion.getCashbackValue();
            default -> BigDecimal.ZERO;
        };

        int rawReward = reward.intValue();

        if (promotion.getMaxCashback() != null && promotion.getMaxCashback() > 0) {
            return Math.min(rawReward, promotion.getMaxCashback());
        }

        return rawReward;
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
        return promotion.getCashbackType() != null
                && ("PERCENT".equalsIgnoreCase(promotion.getCashbackType())
                || "POINTS".equalsIgnoreCase(promotion.getCashbackType()));
    }
}
