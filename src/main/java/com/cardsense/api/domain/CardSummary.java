package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummary {
    private String cardCode;
    private String cardName;
    private String cardStatus;
    private Integer annualFee;
    private String applyUrl;
    private String bankCode;
    private String bankName;
    private List<String> recommendationScopes;
    private String eligibilityType;
    private List<String> availableCategories;
    private boolean hasBenefitPlans;
    private Integer totalPromotionCount;
    private Integer recommendablePromotionCount;
    private Integer catalogOnlyPromotionCount;
    private Integer futureScopePromotionCount;
    private boolean generalRewardsOnly;
    private boolean sparsePromotionCard;
    private boolean coBrandCard;
    private String catalogReviewHint;
}
