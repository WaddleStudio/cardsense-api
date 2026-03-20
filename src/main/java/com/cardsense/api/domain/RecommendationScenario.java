package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationScenario {
    private Integer amount;
    private String category;
    private LocalDate date;
    private String location;
    private String channel;
    private String merchantName;
    private String merchantId;
    private String paymentMethod;
    private Integer installmentCount;
    private Boolean newCustomer;
    private String customerSegment;
    private String membershipTier;
    private List<String> tags;
    private Map<String, String> attributes;
}