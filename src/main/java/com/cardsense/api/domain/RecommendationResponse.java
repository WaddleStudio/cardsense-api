package com.cardsense.api.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponse {

    private String requestId;
    private RecommendationScenario scenario;
    private RecommendationComparisonSummary comparison;
    private List<CardRecommendation> recommendations;
    private List<String> noResultReasons;
    private LocalDateTime generatedAt;
    private String disclaimer;
}
