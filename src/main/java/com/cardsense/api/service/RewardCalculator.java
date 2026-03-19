package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RewardCalculator {

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
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            case "FIXED" -> promotion.getCashbackValue();
            default -> BigDecimal.ZERO;
        };

        int rawReward = reward.intValue();

        if (promotion.getMaxCashback() != null && promotion.getMaxCashback() > 0) {
            return Math.min(rawReward, promotion.getMaxCashback());
        }

        return rawReward;
    }
}
