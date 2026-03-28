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
public class RecommendationComparisonSummary {
    private String mode;
    private Integer evaluatedPromotionCount;
    private Integer eligiblePromotionCount;
    private Integer rankedCardCount;
    private Boolean breakEvenEvaluated;
    private List<BreakEvenAnalysis> breakEvenAnalyses;
    private List<String> notes;
}