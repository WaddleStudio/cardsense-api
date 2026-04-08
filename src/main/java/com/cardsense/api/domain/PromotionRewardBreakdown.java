package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionRewardBreakdown {
    private String promotionId;
    private String promoVersionId;
    private String title;
    private String cashbackType;
    private BigDecimal cashbackValue;
    private Integer estimatedReturn;
    private Integer cappedReturn;
    private Boolean contributesToCardTotal;
    private Boolean assumedStackable;
    private LocalDate validUntil;
    private List<PromotionCondition> conditions;
    private String reason;
    private RewardDetail rewardDetail;
}