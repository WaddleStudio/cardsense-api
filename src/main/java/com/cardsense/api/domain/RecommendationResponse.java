package com.cardsense.api.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponse {

    @JsonProperty("best_card")
    private String bestCard;

    private List<String> reasons;

    private List<String> requirements;

    private Map<String, String> evidence; // promo_version_id -> promo_id or similar mapping

    @JsonProperty("expected_reward")
    private Integer expectedReward;

    @JsonProperty("currency")
    private String currency;
}
