package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationScenario {
    @Min(0)
    private Integer amount;
    @Pattern(regexp = "DINING|TRANSPORT|ONLINE|TRAVEL|OVERSEAS|SHOPPING|GROCERY|ENTERTAINMENT|OTHER")
    private String category;
    private String subcategory;
    private LocalDate date;
    @Size(min = 1, max = 160)
    private String location;
    @Pattern(regexp = "ONLINE|OFFLINE|ALL")
    private String channel;
    @Size(min = 1, max = 120)
    private String merchantName;
    @Size(min = 1, max = 80)
    private String merchantId;
    @Size(min = 1, max = 80)
    private String paymentMethod;
    @Min(1)
    @Max(60)
    private Integer installmentCount;
    private Boolean newCustomer;
    @Size(min = 1, max = 80)
    private String customerSegment;
    @Size(min = 1, max = 80)
    private String membershipTier;
    private List<String> tags;
    private Map<String, String> attributes;
}
