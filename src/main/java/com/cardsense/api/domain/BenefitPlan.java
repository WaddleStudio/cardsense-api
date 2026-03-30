package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitPlan {
    private String planId;
    private String bankCode;
    private String cardCode;
    private String planName;
    private String planDescription;
    private String switchFrequency;
    private Integer switchMaxPerMonth;
    private boolean requiresSubscription;
    private String subscriptionCost;
    private String exclusiveGroup;
    private String status;
    private LocalDate validFrom;
    private LocalDate validUntil;
}
