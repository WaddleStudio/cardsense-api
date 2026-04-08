package com.cardsense.api.domain;

public record ScoredPromotion(
        Promotion promotion,
        int estimatedReturn,
        int cappedReturn,
        RewardDetail rewardDetail
) {
}