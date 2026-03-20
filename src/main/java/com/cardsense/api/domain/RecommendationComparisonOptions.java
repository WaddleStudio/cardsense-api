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
public class RecommendationComparisonOptions {
    private ComparisonMode mode;
    private Boolean includePromotionBreakdown;
    private Boolean includeBreakEvenAnalysis;
    private Integer maxResults;
    private List<String> compareCardCodes;
}