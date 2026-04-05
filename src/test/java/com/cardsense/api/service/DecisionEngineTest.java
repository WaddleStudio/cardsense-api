package com.cardsense.api.service;

import com.cardsense.api.domain.BenefitPlan;
import com.cardsense.api.domain.BenefitUsage;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
import com.cardsense.api.domain.PromotionStackability;
import com.cardsense.api.domain.RecommendationComparisonOptions;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.domain.RecommendationScenario;
import com.cardsense.api.repository.BenefitPlanRepository;
import com.cardsense.api.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DecisionEngineTest {

    private PromotionRepository promotionRepository;
    private BenefitPlanRepository benefitPlanRepository;
    private DecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        promotionRepository = Mockito.mock(PromotionRepository.class);
        benefitPlanRepository = Mockito.mock(BenefitPlanRepository.class);
        decisionEngine = new DecisionEngine(promotionRepository, new RewardCalculator(), benefitPlanRepository);
    }

    @Test
    void recommendReturnsSortedTopCards() {
        Promotion onlinePromo = buildPromotion("promo1", "ver1", "CARD_A", BigDecimal.valueOf(3.0), 400, LocalDate.of(2026, 6, 30));
        Promotion lowerPromo = buildPromotion("promo2", "ver2", "CARD_B", BigDecimal.valueOf(1.5), 400, LocalDate.of(2026, 6, 30));
        lowerPromo.setRequiresRegistration(true);

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(onlinePromo, lowerPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .registeredPromotionIds(List.of("ver2"))
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertNotNull(response);
        assertEquals(2, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
        assertEquals(30, response.getRecommendations().get(0).getEstimatedReturn());
        assertEquals(15, response.getRecommendations().get(1).getEstimatedReturn());
        assertEquals(DecisionEngine.DISCLAIMER, response.getDisclaimer());
    }

    @Test
    void recommendFiltersByCardCode() {
        Promotion promo = buildPromotion("promo1", "ver1", "CARD_A", BigDecimal.valueOf(3.0), 300, LocalDate.of(2026, 6, 30));
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .cardCodes(List.of("CARD_B"))
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertTrue(response.getRecommendations().isEmpty());
    }

    @Test
    void recommendRequiresRegistrationState() {
        Promotion promo = buildPromotion("promo1", "ver1", "CARD_A", BigDecimal.valueOf(3.0), 300, LocalDate.of(2026, 6, 30));
        promo.setRequiresRegistration(true);
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse missingRegistration = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .date(LocalDate.of(2026, 4, 5))
                .build());

        RecommendationResponse registered = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .registeredPromotionIds(List.of("ver1"))
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertTrue(missingRegistration.getRecommendations().isEmpty());
        assertEquals(1, registered.getRecommendations().size());
    }

    @Test
    void recommendSkipsExhaustedBenefits() {
        Promotion promo = buildPromotion("promo1", "ver1", "CARD_A", BigDecimal.valueOf(3.0), 300, LocalDate.of(2026, 6, 30));
        promo.setRequiresRegistration(true);
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .registeredPromotionIds(List.of("ver1"))
                .benefitUsage(List.of(BenefitUsage.builder().promoVersionId("ver1").consumedAmount(300).build()))
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertTrue(response.getRecommendations().isEmpty());
    }

    @Test
    void recommendMatchesMerchantCondition() {
        Promotion promo = buildPromotion("promo1", "ver1", "CARD_A", BigDecimal.valueOf(5.0), 500, LocalDate.of(2026, 6, 30));
        promo.setConditions(List.of(condition("MERCHANT", "CHATGPT", "ChatGPT")));
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse match = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("ONLINE")
                        .merchantName("CHATGPT")
                        .date(LocalDate.of(2026, 4, 5))
                        .build())
                .build());

        RecommendationResponse mismatch = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("ONLINE")
                        .merchantName("CLAUDE")
                        .date(LocalDate.of(2026, 4, 5))
                        .build())
                .build());

        assertEquals(1, match.getRecommendations().size());
        assertTrue(mismatch.getRecommendations().isEmpty());
    }

    @Test
    void recommendMatchesMerchantConditionByLocalizedLabel() {
        Promotion promo = buildPromotion("promo1", "ver1", "ESUN_UNICARD", BigDecimal.valueOf(4.5), 500, LocalDate.of(2026, 6, 30));
        promo.setCategory("SHOPPING");
        promo.setSubcategory("SPORTING_GOODS");
        promo.setConditions(List.of(condition("RETAIL_CHAIN", "DECATHLON", "迪卡儂")));
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("SHOPPING")
                        .subcategory("SPORTING_GOODS")
                        .merchantName("迪卡儂")
                        .date(LocalDate.of(2026, 4, 5))
                        .build())
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
    }

    @Test
    void recommendMatchesPaymentPlatformConditionViaPaymentMethod() {
        Promotion promo = buildPromotion("promo1", "ver1", "ESUN_UNICARD", BigDecimal.valueOf(5.0), 500, LocalDate.of(2026, 6, 30));
        promo.setConditions(List.of(condition("PAYMENT_PLATFORM", "LINE_PAY", "LINE Pay")));
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("ONLINE")
                        .paymentMethod("LINE_PAY")
                        .date(LocalDate.of(2026, 4, 5))
                        .build())
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
    }

    @Test
    void recommendMatchesBroadPaymentMethodConditionViaSpecificPlatform() {
        Promotion promo = buildPromotion("promo1", "ver1", "TAISHIN_RICHART", BigDecimal.valueOf(3.8), 500, LocalDate.of(2026, 6, 30));
        promo.setConditions(List.of(condition("PAYMENT_METHOD", "MOBILE_PAY", "Mobile Pay")));
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("ONLINE")
                        .paymentMethod("LINE_PAY")
                        .date(LocalDate.of(2026, 4, 5))
                        .build())
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
    }

    @Test
    void recommendSubcategoryQueryRequiresAtLeastOneExactScenePromotionPerCard() {
        Promotion deliveryPromo = buildPromotion("promo1", "ver1", "CARD_DELIVERY", BigDecimal.valueOf(10.0), 500, LocalDate.of(2026, 6, 30));
        deliveryPromo.setCategory("DINING");
        deliveryPromo.setSubcategory("DELIVERY");

        Promotion restaurantPromo = buildPromotion("promo2", "ver2", "CARD_RESTAURANT", BigDecimal.valueOf(8.0), 500, LocalDate.of(2026, 6, 30));
        restaurantPromo.setCategory("DINING");
        restaurantPromo.setSubcategory("RESTAURANT");

        Promotion generalPromo = buildPromotion("promo3", "ver3", "CARD_GENERAL", BigDecimal.valueOf(6.0), 500, LocalDate.of(2026, 6, 30));
        generalPromo.setCategory("DINING");
        generalPromo.setSubcategory("GENERAL");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(deliveryPromo, restaurantPromo, generalPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("DINING")
                .subcategory("DELIVERY")
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
    }

    @Test
    void recommendMerchantScopedQueryExcludesCardsWithoutMerchantScopedPromotion() {
        Promotion merchantPromo = buildPromotion("promo1", "ver1", "CARD_MERCHANT", BigDecimal.valueOf(8.0), 500, LocalDate.of(2026, 6, 30));
        merchantPromo.setCategory("ONLINE");
        merchantPromo.setSubcategory("GENERAL");
        merchantPromo.setConditions(List.of(condition("ECOMMERCE_PLATFORM", "PCHOME_24H", "PChome 24h")));

        Promotion genericPromo = buildPromotion("promo2", "ver2", "CARD_GENERIC", BigDecimal.valueOf(10.0), 500, LocalDate.of(2026, 6, 30));
        genericPromo.setCategory("ONLINE");
        genericPromo.setSubcategory("GENERAL");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(merchantPromo, genericPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("ONLINE")
                        .subcategory("ECOMMERCE")
                        .merchantName("PCHOME_24H")
                        .date(LocalDate.of(2026, 4, 5))
                        .build())
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
    }

    @Test
    void recommendGeneralQueryExcludesSceneSpecificPromotions() {
        Promotion deliveryPromo = buildPromotion("promo1", "ver1", "CARD_DELIVERY", BigDecimal.valueOf(10.0), 500, LocalDate.of(2026, 6, 30));
        deliveryPromo.setCategory("DINING");
        deliveryPromo.setSubcategory("DELIVERY");

        Promotion generalPromo = buildPromotion("promo2", "ver2", "CARD_GENERAL", BigDecimal.valueOf(3.0), 500, LocalDate.of(2026, 6, 30));
        generalPromo.setCategory("DINING");
        generalPromo.setSubcategory("GENERAL");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(deliveryPromo, generalPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("DINING")
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals("promo2", response.getRecommendations().get(0).getPromotionId());
    }

    @Test
    void recommendStacksGeneralAndSceneSpecificPromotionsForSameCard() {
        Promotion generalPromo = buildPromotion("promo1", "ver1", "CARD_STACK", BigDecimal.valueOf(3.0), 500, LocalDate.of(2026, 6, 30));
        generalPromo.setCategory("DINING");
        generalPromo.setSubcategory("GENERAL");
        generalPromo.setStackability(stackability("ALWAYS_STACKABLE", null, null, null, List.of("ver2")));

        Promotion deliveryPromo = buildPromotion("promo2", "ver2", "CARD_STACK", BigDecimal.valueOf(5.0), 500, LocalDate.of(2026, 6, 30));
        deliveryPromo.setCategory("DINING");
        deliveryPromo.setSubcategory("DELIVERY");
        deliveryPromo.setStackability(stackability("ALWAYS_STACKABLE", null, null, null, List.of("ver1")));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(generalPromo, deliveryPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("DINING")
                .subcategory("DELIVERY")
                .date(LocalDate.of(2026, 4, 5))
                .comparison(RecommendationComparisonOptions.builder().includePromotionBreakdown(true).build())
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals(80, response.getRecommendations().get(0).getEstimatedReturn());
        assertEquals(2, response.getRecommendations().get(0).getMatchedPromotionCount());
    }

    @Test
    void recommendSkipsCardWhenPlanResolutionLeavesNoPromotions() {
        Promotion cubePromo = buildPromotion("promo1", "ver1", "CATHAY_CUBE", BigDecimal.valueOf(3.0), 500, LocalDate.of(2026, 6, 30));
        cubePromo.setCategory("SHOPPING");
        cubePromo.setSubcategory("DEPARTMENT");
        cubePromo.setPlanId("CATHAY_CUBE_SHOPPING");
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(cubePromo));
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_SHOPPING")).thenReturn(null);

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("SHOPPING")
                .subcategory("DEPARTMENT")
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertTrue(response.getRecommendations().isEmpty());
    }

    @Test
    void cubeTierDefaultsToLevel1WhenRequestDoesNotSpecifyTier() {
        Promotion cubePromo = buildPromotion("promo1", "ver1", "CATHAY_CUBE", BigDecimal.valueOf(3.0), 500, LocalDate.of(2026, 6, 30));
        cubePromo.setPlanId("CATHAY_CUBE_DIGITAL");
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(cubePromo));
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_DIGITAL")).thenReturn(activeCubePlan("CATHAY_CUBE_DIGITAL"));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals(20, response.getRecommendations().get(0).getEstimatedReturn());
        assertTrue(response.getRecommendations().get(0).getConditions().stream()
                .anyMatch(condition -> "ASSUMED_BENEFIT_TIER".equals(condition.getType()) && "LEVEL_1".equals(condition.getValue())));
    }

    @Test
    void cubeTierCanUpgradeToLevel3ViaRequest() {
        Promotion cubePromo = buildPromotion("promo1", "ver1", "CATHAY_CUBE", BigDecimal.valueOf(3.0), 500, LocalDate.of(2026, 6, 30));
        cubePromo.setPlanId("CATHAY_CUBE_DIGITAL");
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(cubePromo));
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_DIGITAL")).thenReturn(activeCubePlan("CATHAY_CUBE_DIGITAL"));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .benefitPlanTiers(Map.of("CATHAY_CUBE", "LEVEL_3"))
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals(33, response.getRecommendations().get(0).getEstimatedReturn());
        assertTrue(response.getRecommendations().get(0).getConditions().stream()
                .anyMatch(condition -> "ASSUMED_BENEFIT_TIER".equals(condition.getType()) && "LEVEL_3".equals(condition.getValue())));
    }

    @Test
    void richartTierDefaultsToLevel1WhenRequestDoesNotSpecifyTier() {
        Promotion richartPromo = buildPromotion("promo1", "ver1", "TAISHIN_RICHART", BigDecimal.valueOf(3.3), 500, LocalDate.of(2026, 6, 30));
        richartPromo.setPlanId("TAISHIN_RICHART_DIGITAL");
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(richartPromo));
        when(benefitPlanRepository.findByPlanId("TAISHIN_RICHART_DIGITAL")).thenReturn(activeRichartPlan("TAISHIN_RICHART_DIGITAL"));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals(13, response.getRecommendations().get(0).getEstimatedReturn());
        assertTrue(response.getRecommendations().get(0).getConditions().stream()
                .anyMatch(condition -> "ASSUMED_BENEFIT_TIER".equals(condition.getType()) && "LEVEL_1".equals(condition.getValue())));
    }

    @Test
    void richartTierCanUpgradeToLevel2ViaRuntimeState() {
        Promotion richartPromo = buildPromotion("promo1", "ver1", "TAISHIN_RICHART", BigDecimal.valueOf(3.3), 500, LocalDate.of(2026, 6, 30));
        richartPromo.setPlanId("TAISHIN_RICHART_DIGITAL");
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(richartPromo));
        when(benefitPlanRepository.findByPlanId("TAISHIN_RICHART_DIGITAL")).thenReturn(activeRichartPlan("TAISHIN_RICHART_DIGITAL"));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .planRuntimeByCard(Map.of("TAISHIN_RICHART", Map.of("tier", "LEVEL_2")))
                .date(LocalDate.of(2026, 4, 5))
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals(33, response.getRecommendations().get(0).getEstimatedReturn());
    }

    private BenefitPlan activeCubePlan(String planId) {
        return BenefitPlan.builder()
                .planId(planId)
                .cardCode("CATHAY_CUBE")
                .planName("Digital")
                .exclusiveGroup("CATHAY_CUBE_PLANS")
                .switchFrequency("DAILY")
                .status("ACTIVE")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 6, 30))
                .build();
    }

    private BenefitPlan activeRichartPlan(String planId) {
        return BenefitPlan.builder()
                .planId(planId)
                .cardCode("TAISHIN_RICHART")
                .planName("Digital")
                .exclusiveGroup("TAISHIN_RICHART_PLANS")
                .switchFrequency("DAILY")
                .status("ACTIVE")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 6, 30))
                .build();
    }

    private Promotion buildPromotion(String promoId, String promoVersionId, String cardCode, BigDecimal cashbackValue, Integer maxCashback, LocalDate validUntil) {
        return Promotion.builder()
                .promoId(promoId)
                .promoVersionId(promoVersionId)
                .title(cardCode + " reward")
                .cardCode(cardCode)
                .cardName(cardCode + " name")
                .bankCode("TEST")
                .bankName("Test Bank")
                .category("ONLINE")
                .subcategory("GENERAL")
                .recommendationScope("RECOMMENDABLE")
                .cashbackType("PERCENT")
                .cashbackValue(cashbackValue)
                .maxCashback(maxCashback)
                .annualFee(1800)
                .cardStatus("ACTIVE")
                .validUntil(validUntil)
                .status("ACTIVE")
                .build();
    }

    private PromotionCondition condition(String type, String value, String label) {
        return PromotionCondition.builder()
                .type(type)
                .value(value)
                .label(label)
                .build();
    }

    private PromotionStackability stackability(
            String relationshipMode,
            String groupId,
            List<String> requiresPromoVersionIds,
            List<String> excludesPromoVersionIds,
            List<String> stackWithPromoVersionIds
    ) {
        return PromotionStackability.builder()
                .relationshipMode(relationshipMode)
                .groupId(groupId)
                .requiresPromoVersionIds(requiresPromoVersionIds)
                .excludesPromoVersionIds(excludesPromoVersionIds)
                .stackWithPromoVersionIds(stackWithPromoVersionIds)
                .build();
    }
}
