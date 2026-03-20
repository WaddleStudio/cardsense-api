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
public class PromotionStackability {
    private String benefitLayer;
    private String relationshipMode;
    private String groupId;
    private Integer priority;
    private List<String> requiresPromoVersionIds;
    private List<String> excludesPromoVersionIds;
    private List<String> stackWithPromoVersionIds;
    private String notes;
}