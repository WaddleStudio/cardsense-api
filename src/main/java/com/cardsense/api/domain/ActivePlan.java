package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivePlan {
    private String planId;
    private String planName;
    private boolean switchRequired;
    private String switchFrequency;
    private boolean requiresSubscription;
    private String subscriptionCost;
}
