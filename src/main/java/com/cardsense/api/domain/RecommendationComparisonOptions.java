package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationComparisonOptions {
    private Boolean includePromotionBreakdown;
    private Boolean includeBreakEvenAnalysis;
    @Min(1)
    @Max(50)
    private Integer maxResults;
    private List<String> compareCardCodes;
}
