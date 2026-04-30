package com.cardsense.api.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationRequest {

    @JsonAlias("transaction_amount")
    private Integer amount;

    @JsonAlias("merchant_category")
    @Pattern(regexp = "DINING|TRANSPORT|ONLINE|TRAVEL|OVERSEAS|SHOPPING|GROCERY|ENTERTAINMENT|OTHER")
    private String category;

    private String subcategory;

    private List<String> cardCodes;

    private List<String> registeredPromotionIds;

    private List<BenefitUsage> benefitUsage;

    private String location;

    @JsonAlias("transaction_date")
    private LocalDate date;

    @Valid
    private RecommendationScenario scenario;

    @Valid
    private RecommendationComparisonOptions comparison;

    @JsonAlias("benefit_plan_tiers")
    private Map<String, String> benefitPlanTiers;

    private Map<String, String> activePlansByCard;

    private Map<String, Map<String, String>> planRuntimeByCard;

    /**
     * Optional user-supplied exchange rates to override system defaults.
     * Key format: "{CASHBACK_TYPE}.{BANK_CODE}" or "{CASHBACK_TYPE}._DEFAULT"
     * Examples: "MILES._DEFAULT" → 0.60, "POINTS.ESUN" → 0.80
     */
    private Map<String, BigDecimal> customExchangeRates;

    @JsonIgnore
    public Integer getResolvedAmount() {
        return scenario != null && scenario.getAmount() != null ? scenario.getAmount() : amount;
    }

    @JsonIgnore
    public String getResolvedCategory() {
        return scenario != null && scenario.getCategory() != null && !scenario.getCategory().isBlank()
                ? scenario.getCategory()
                : category;
    }

    @JsonIgnore
    public String getResolvedSubcategory() {
        return scenario != null && scenario.getSubcategory() != null && !scenario.getSubcategory().isBlank()
                ? scenario.getSubcategory()
                : subcategory;
    }

    @JsonIgnore
    public String getResolvedLocation() {
        return scenario != null && scenario.getLocation() != null && !scenario.getLocation().isBlank()
                ? scenario.getLocation()
                : location;
    }

    @JsonIgnore
    public LocalDate getResolvedDate() {
        return scenario != null && scenario.getDate() != null ? scenario.getDate() : date;
    }

    @JsonIgnore
    public String getResolvedChannel() {
        return scenario != null ? scenario.getChannel() : null;
    }

    @JsonIgnore
    public String getResolvedMerchantName() {
        return scenario != null ? scenario.getMerchantName() : null;
    }

    @JsonIgnore
    public String getResolvedPaymentMethod() {
        return scenario != null ? scenario.getPaymentMethod() : null;
    }

    @JsonIgnore
    public RecommendationScenario toResolvedScenario() {
        RecommendationScenario baseScenario = scenario == null ? new RecommendationScenario() : scenario;
        return RecommendationScenario.builder()
                .amount(getResolvedAmount())
                .category(getResolvedCategory())
                .subcategory(getResolvedSubcategory())
                .date(getResolvedDate())
                .location(getResolvedLocation())
                .channel(baseScenario.getChannel())
                .merchantName(baseScenario.getMerchantName())
                .merchantId(baseScenario.getMerchantId())
                .paymentMethod(baseScenario.getPaymentMethod())
                .installmentCount(baseScenario.getInstallmentCount())
                .newCustomer(baseScenario.getNewCustomer())
                .customerSegment(baseScenario.getCustomerSegment())
                .membershipTier(baseScenario.getMembershipTier())
                .tags(baseScenario.getTags())
                .attributes(baseScenario.getAttributes())
                .build();
    }

    @JsonIgnore
    public boolean shouldIncludePromotionBreakdown() {
        return comparison == null || comparison.getIncludePromotionBreakdown() == null || comparison.getIncludePromotionBreakdown();
    }

    @JsonIgnore
    public boolean shouldIncludeBreakEvenAnalysis() {
        return comparison != null && Boolean.TRUE.equals(comparison.getIncludeBreakEvenAnalysis());
    }

    @JsonIgnore
    public int getResolvedMaxResults() {
        if (comparison == null || comparison.getMaxResults() == null || comparison.getMaxResults() <= 0) {
            return 5;
        }
        return comparison.getMaxResults();
    }

    @JsonIgnore
    public List<String> getResolvedCardCodes() {
        if (comparison != null && comparison.getCompareCardCodes() != null && !comparison.getCompareCardCodes().isEmpty()) {
            return comparison.getCompareCardCodes();
        }
        return cardCodes;
    }

    @JsonIgnore
    public Map<String, String> getResolvedBenefitPlanTiers() {
        if (benefitPlanTiers == null || benefitPlanTiers.isEmpty()) {
            return Collections.emptyMap();
        }
        return benefitPlanTiers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().trim().toUpperCase(),
                        entry -> entry.getValue().trim().toUpperCase(),
                        (left, right) -> right
                ));
    }

    @JsonIgnore
    public String getResolvedBenefitPlanTier(String cardCode) {
        if (cardCode == null || cardCode.isBlank()) {
            return null;
        }
        return getResolvedBenefitPlanTiers().get(cardCode.trim().toUpperCase());
    }

    @JsonIgnore
    public Map<String, String> getResolvedActivePlansByCard() {
        if (activePlansByCard == null || activePlansByCard.isEmpty()) {
            return Collections.emptyMap();
        }
        return activePlansByCard.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().trim().toUpperCase(),
                        entry -> entry.getValue().trim().toUpperCase(),
                        (left, right) -> right
                ));
    }

    @JsonIgnore
    public String getResolvedActivePlanId(String cardCode) {
        if (cardCode == null || cardCode.isBlank()) {
            return null;
        }
        return getResolvedActivePlansByCard().get(cardCode.trim().toUpperCase());
    }

    @JsonIgnore
    public Map<String, Map<String, String>> getResolvedPlanRuntimeByCard() {
        if (planRuntimeByCard == null || planRuntimeByCard.isEmpty()) {
            return Collections.emptyMap();
        }
        return planRuntimeByCard.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().trim().toUpperCase(),
                        entry -> entry.getValue().entrySet().stream()
                                .filter(runtimeEntry -> runtimeEntry.getKey() != null && !runtimeEntry.getKey().isBlank())
                                .filter(runtimeEntry -> runtimeEntry.getValue() != null && !runtimeEntry.getValue().isBlank())
                                .collect(Collectors.toUnmodifiableMap(
                                        runtimeEntry -> runtimeEntry.getKey().trim().toUpperCase(),
                                        runtimeEntry -> runtimeEntry.getValue().trim().toUpperCase(),
                                        (left, right) -> right
                                )),
                        (left, right) -> right
                ));
    }

    @JsonIgnore
    public String getResolvedPlanRuntimeValue(String cardCode, String runtimeKey) {
        if (cardCode == null || cardCode.isBlank() || runtimeKey == null || runtimeKey.isBlank()) {
            return null;
        }
        Map<String, String> runtime = getResolvedPlanRuntimeByCard().get(cardCode.trim().toUpperCase());
        if (runtime == null || runtime.isEmpty()) {
            return null;
        }
        return runtime.get(runtimeKey.trim().toUpperCase());
    }

    @AssertTrue(message = "Scenario amount is required and must be non-negative")
    @JsonIgnore
    public boolean isResolvedAmountValid() {
        Integer resolvedAmount = getResolvedAmount();
        return resolvedAmount != null && resolvedAmount >= 0;
    }

    @AssertTrue(message = "Scenario category is required")
    @JsonIgnore
    public boolean isResolvedCategoryValid() {
        String resolvedCategory = getResolvedCategory();
        return resolvedCategory != null && !resolvedCategory.isBlank();
    }
}
