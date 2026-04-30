package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardRecommendation {
    private String cardCode;
    private String cardName;
    private String bankCode;
    private String bankName;
    private String subcategory;
    private String cashbackType;
    private BigDecimal cashbackValue;
    private Integer estimatedReturn;
    private Integer matchedPromotionCount;
    private String reason;
    private String promotionId;
    private String promoVersionId;
    private LocalDate validUntil;
    private List<PromotionCondition> conditions;
    private List<PromotionRewardBreakdown> promotionBreakdown;
    private String applyUrl;
    private String sourceUrl;
    private LocalDateTime verifiedAt;
    private BigDecimal confidence;
    private ActivePlan activePlan;
    private boolean generalRewardOnly;
    private RewardDetail rewardDetail;
}
