package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreakEvenAnalysis {
    private String leftCardCode;
    private String rightCardCode;
    private String leftPromoVersionId;
    private String rightPromoVersionId;
    private Integer breakEvenAmount;
    private Integer variableRewardCapAmount;
    private Integer leftMinAmount;
    private Integer rightMinAmount;
    private String summary;
}