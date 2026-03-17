package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import org.springframework.stereotype.Service;

@Service
public class RewardCalculator {

    public int calculateReward(Promotion promotion, int transactionAmount) {
        if (promotion.getMinAmount() != null && transactionAmount < promotion.getMinAmount()) {
            return 0;
        }

        // Simplification: treating points and miles as 1:1 with currency subunits for sorting magnitude,
        // or just treating everything as specific 'units'.
        // In a real system, we'd need conversion rates. 
        // For this MVP, we perform the raw calculation.

        double rate = promotion.getRewardRate() != null ? promotion.getRewardRate() : 0.0;
        int rawReward = (int) (transactionAmount * rate);

        if (promotion.getRewardCap() != null && promotion.getRewardCap() > 0) {
            return Math.min(rawReward, promotion.getRewardCap());
        }

        return rawReward;
    }
}
