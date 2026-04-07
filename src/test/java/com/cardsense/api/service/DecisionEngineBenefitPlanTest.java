package com.cardsense.api.service;

import com.cardsense.api.domain.BenefitPlan;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.domain.RecommendationScenario;
import com.cardsense.api.domain.RecommendationComparisonOptions;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DecisionEngineBenefitPlanTest {

    private PromotionRepository promotionRepository;
    private BenefitPlanRepository benefitPlanRepository;
    private RewardCalculator rewardCalculator;
    private DecisionEngine decisionEngine;

    @BeforeEach
    public void setup() {
        promotionRepository = Mockito.mock(PromotionRepository.class);
        benefitPlanRepository = Mockito.mock(BenefitPlanRepository.class);
        rewardCalculator = new RewardCalculator();
        decisionEngine = new DecisionEngine(promotionRepository, rewardCalculator, benefitPlanRepository);
    }

    @Test
    public void testRecommendPicksBestPlanForSwitchingCard() {
        // CUBE card: base promo (no plan) + digital plan promo (蝦皮 3%) + shopping plan promo (SOGO 3%)
        Promotion basePromo = buildPromotion("base1", "base_ver1", "CATHAY_CUBE", "CUBE卡", "CATHAY", "國泰世華",
                BigDecimal.valueOf(0.3), null, 0, LocalDate.of(2026, 6, 30));
        basePromo.setPlanId(null);

        Promotion shopeePromo = buildPromotion("digital1", "digital_ver1", "CATHAY_CUBE", "CUBE卡", "CATHAY", "國泰世華",
                BigDecimal.valueOf(3.0), 500, 0, LocalDate.of(2026, 6, 30));
        shopeePromo.setPlanId("CATHAY_CUBE_DIGITAL");
        shopeePromo.setConditions(List.of(condition("VENUE", "SHOPEE", "蝦皮")));

        Promotion sogoPromo = buildPromotion("shopping1", "shopping_ver1", "CATHAY_CUBE", "CUBE卡", "CATHAY", "國泰世華",
                BigDecimal.valueOf(3.0), 500, 0, LocalDate.of(2026, 6, 30));
        sogoPromo.setPlanId("CATHAY_CUBE_SHOPPING");
        sogoPromo.setConditions(List.of(condition("VENUE", "SOGO", "SOGO")));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(basePromo, shopeePromo, sogoPromo));

        BenefitPlan digitalPlan = BenefitPlan.builder()
                .planId("CATHAY_CUBE_DIGITAL").cardCode("CATHAY_CUBE").planName("玩數位")
                .switchFrequency("DAILY").exclusiveGroup("CATHAY_CUBE_PLANS")
                .status("ACTIVE").validFrom(LocalDate.of(2026, 1, 1)).validUntil(LocalDate.of(2026, 6, 30))
                .build();
        BenefitPlan shoppingPlan = BenefitPlan.builder()
                .planId("CATHAY_CUBE_SHOPPING").cardCode("CATHAY_CUBE").planName("樂饗購")
                .switchFrequency("DAILY").exclusiveGroup("CATHAY_CUBE_PLANS")
                .status("ACTIVE").validFrom(LocalDate.of(2026, 1, 1)).validUntil(LocalDate.of(2026, 6, 30))
                .build();
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_DIGITAL")).thenReturn(digitalPlan);
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_SHOPPING")).thenReturn(shoppingPlan);

        // Query: 蝦皮消費 3000 元 → digital plan should win (shopee matches, 3% = 90元)
        // shopping plan's SOGO promo doesn't match merchantName=SHOPEE so it's filtered by isEligible
        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(3000)
                        .category("ONLINE")
                        .merchantName("SHOPEE")
                        .date(LocalDate.of(2026, 3, 15))
                        .build())
                .comparison(RecommendationComparisonOptions.builder()
                        .includePromotionBreakdown(true)
                        .build())
                .benefitPlanTiers(java.util.Map.of("CATHAY_CUBE", "LEVEL_2"))
                .build());

        assertEquals(1, response.getRecommendations().size());
        var rec = response.getRecommendations().get(0);
        assertEquals("CATHAY_CUBE", rec.getCardCode());

        // activePlan should be the digital plan
        assertNotNull(rec.getActivePlan());
        assertEquals("CATHAY_CUBE_DIGITAL", rec.getActivePlan().getPlanId());
        assertEquals("玩數位", rec.getActivePlan().getPlanName());
        assertTrue(rec.getActivePlan().isSwitchRequired());
        assertEquals("每天可切換1次", rec.getActivePlan().getSwitchFrequency());

        // Total should be base (0.3% of 3000 = 9) + digital (3% of 3000 = 90) = at least 90
        assertTrue(rec.getEstimatedReturn() >= 90);
    }

    @Test
    public void testTraditionalCardHasNullActivePlan() {
        Promotion traditionalPromo = buildPromotion("trad1", "trad_ver1", "CTBC_DEMO", "中信卡", "CTBC", "中國信託",
                BigDecimal.valueOf(3.0), 500, 0, LocalDate.of(2026, 6, 30));
        traditionalPromo.setPlanId(null);

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(traditionalPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000).category("ONLINE").date(LocalDate.of(2026, 3, 15)).build());

        assertEquals(1, response.getRecommendations().size());
        assertNull(response.getRecommendations().get(0).getActivePlan());
    }

    @Test
    public void testPlanPromotionsFromNonWinningPlansAreExcluded() {
        // Two plan-bound promos from different plans, same card, same category.
        Promotion digitalPromo = buildPromotion("d1", "d_ver1", "CATHAY_CUBE", "CUBE卡", "CATHAY", "國泰世華",
                BigDecimal.valueOf(3.0), 500, 0, LocalDate.of(2026, 6, 30));
        digitalPromo.setPlanId("CATHAY_CUBE_DIGITAL");

        Promotion essentialsPromo = buildPromotion("e1", "e_ver1", "CATHAY_CUBE", "CUBE卡", "CATHAY", "國泰世華",
                BigDecimal.valueOf(2.0), 500, 0, LocalDate.of(2026, 6, 30));
        essentialsPromo.setPlanId("CATHAY_CUBE_ESSENTIALS");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(digitalPromo, essentialsPromo));

        BenefitPlan digitalPlan = BenefitPlan.builder()
                .planId("CATHAY_CUBE_DIGITAL").cardCode("CATHAY_CUBE").planName("玩數位")
                .switchFrequency("DAILY").exclusiveGroup("CATHAY_CUBE_PLANS")
                .status("ACTIVE").validFrom(LocalDate.of(2026, 1, 1)).validUntil(LocalDate.of(2026, 6, 30))
                .build();
        BenefitPlan essentialsPlan = BenefitPlan.builder()
                .planId("CATHAY_CUBE_ESSENTIALS").cardCode("CATHAY_CUBE").planName("集精選")
                .switchFrequency("DAILY").exclusiveGroup("CATHAY_CUBE_PLANS")
                .status("ACTIVE").validFrom(LocalDate.of(2026, 1, 1)).validUntil(LocalDate.of(2026, 6, 30))
                .build();
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_DIGITAL")).thenReturn(digitalPlan);
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_ESSENTIALS")).thenReturn(essentialsPlan);

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000).category("ONLINE").date(LocalDate.of(2026, 3, 15)).build())
                .comparison(RecommendationComparisonOptions.builder()
                        .includePromotionBreakdown(true).build())
                .benefitPlanTiers(java.util.Map.of("CATHAY_CUBE", "LEVEL_2"))
                .build());

        assertEquals(1, response.getRecommendations().size());
        var rec = response.getRecommendations().get(0);
        // Digital plan (3%) beats essentials plan (2%)
        assertEquals("CATHAY_CUBE_DIGITAL", rec.getActivePlan().getPlanId());
        assertEquals(30, rec.getEstimatedReturn()); // 3% of 1000
    }

    @Test
    public void testMonthlyPlanShowsCorrectSwitchFrequency() {
        Promotion unicardPromo = buildPromotion("u1", "u_ver1", "ESUN_UNICARD", "Unicard", "ESUN", "玉山銀行",
                BigDecimal.valueOf(4.5), 500, 0, LocalDate.of(2026, 6, 30));
        unicardPromo.setPlanId("ESUN_UNICARD_UP");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(unicardPromo));

        BenefitPlan upPlan = BenefitPlan.builder()
                .planId("ESUN_UNICARD_UP").cardCode("ESUN_UNICARD").planName("UP選")
                .switchFrequency("MONTHLY").switchMaxPerMonth(30).exclusiveGroup("ESUN_UNICARD_PLANS")
                .status("ACTIVE").validFrom(LocalDate.of(2026, 1, 1)).validUntil(LocalDate.of(2026, 6, 30))
                .build();
        when(benefitPlanRepository.findByPlanId("ESUN_UNICARD_UP")).thenReturn(upPlan);

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000).category("ONLINE").date(LocalDate.of(2026, 3, 15)).build());

        assertEquals(1, response.getRecommendations().size());
        var rec = response.getRecommendations().get(0);
        assertNotNull(rec.getActivePlan());
        assertEquals("每月最多切換30次", rec.getActivePlan().getSwitchFrequency());
    }

    @Test
    public void testExpiredPlanPromotionsAreFilteredByRequestDate() {
        Promotion basePromo = buildPromotion("base2", "base_ver2", "CATHAY_CUBE", "Cube", "CATHAY", "Cathay",
                BigDecimal.valueOf(1.0), 500, 0, LocalDate.of(2026, 6, 30));
        basePromo.setPlanId(null);

        Promotion expiredPlanPromo = buildPromotion("birthday1", "birthday_ver1", "CATHAY_CUBE", "Cube", "CATHAY", "Cathay",
                BigDecimal.valueOf(5.0), 500, 0, LocalDate.of(2026, 6, 30));
        expiredPlanPromo.setPlanId("CATHAY_CUBE_BIRTHDAY");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(basePromo, expiredPlanPromo));
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_BIRTHDAY")).thenReturn(
                buildPlan("CATHAY_CUBE_BIRTHDAY", "CATHAY_CUBE", "Birthday", "CATHAY_CUBE_PLANS",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "DAILY")
        );

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000).category("ONLINE").date(LocalDate.of(2026, 4, 1)).build());

        assertEquals(1, response.getRecommendations().size());
        var rec = response.getRecommendations().get(0);
        assertNull(rec.getActivePlan());
        assertEquals(10, rec.getEstimatedReturn());
    }

    @Test
    public void testPlanValidUntilIsInclusiveOnRequestDate() {
        Promotion birthdayPromo = buildPromotion("birthday2", "birthday_ver2", "CATHAY_CUBE", "Cube", "CATHAY", "Cathay",
                BigDecimal.valueOf(5.0), 500, 0, LocalDate.of(2026, 6, 30));
        birthdayPromo.setPlanId("CATHAY_CUBE_BIRTHDAY");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(birthdayPromo));
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_BIRTHDAY")).thenReturn(
                buildPlan("CATHAY_CUBE_BIRTHDAY", "CATHAY_CUBE", "Birthday", "CATHAY_CUBE_PLANS",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "DAILY")
        );

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000).category("ONLINE").date(LocalDate.of(2026, 3, 31)).build());

        assertEquals(1, response.getRecommendations().size());
        var rec = response.getRecommendations().get(0);
        assertNotNull(rec.getActivePlan());
        assertEquals("CATHAY_CUBE_BIRTHDAY", rec.getActivePlan().getPlanId());
        assertEquals(50, rec.getEstimatedReturn());
    }

    @Test
    public void testUserSelectedActivePlanOverridesAutoBestPlan() {
        Promotion digitalPromo = buildPromotion("d1", "d_ver1", "CATHAY_CUBE", "Cube", "CATHAY", "Cathay",
                BigDecimal.valueOf(3.0), 500, 0, LocalDate.of(2026, 6, 30));
        digitalPromo.setPlanId("CATHAY_CUBE_DIGITAL");

        Promotion shoppingPromo = buildPromotion("s1", "s_ver1", "CATHAY_CUBE", "Cube", "CATHAY", "Cathay",
                BigDecimal.valueOf(5.0), 500, 0, LocalDate.of(2026, 6, 30));
        shoppingPromo.setPlanId("CATHAY_CUBE_SHOPPING");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(digitalPromo, shoppingPromo));
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_DIGITAL")).thenReturn(
                buildPlan("CATHAY_CUBE_DIGITAL", "CATHAY_CUBE", "Digital", "CATHAY_CUBE_PLANS",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "DAILY")
        );
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_SHOPPING")).thenReturn(
                buildPlan("CATHAY_CUBE_SHOPPING", "CATHAY_CUBE", "Shopping", "CATHAY_CUBE_PLANS",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "DAILY")
        );

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .date(LocalDate.of(2026, 3, 15))
                .activePlansByCard(Map.of("CATHAY_CUBE", "CATHAY_CUBE_DIGITAL"))
                .planRuntimeByCard(Map.of("CATHAY_CUBE", Map.of("tier", "LEVEL_2")))
                .build());

        assertEquals(1, response.getRecommendations().size());
        var rec = response.getRecommendations().get(0);
        assertNotNull(rec.getActivePlan());
        assertEquals("CATHAY_CUBE_DIGITAL", rec.getActivePlan().getPlanId());
        assertEquals(30, rec.getEstimatedReturn());
    }

    @Test
    public void testCubeTierCanBeReadFromPlanRuntimeState() {
        Promotion cubePromo = buildPromotion("promo1", "ver1", "CATHAY_CUBE", "Cube", "CATHAY", "Cathay",
                BigDecimal.valueOf(3.0), 500, 0, LocalDate.of(2026, 6, 30));
        cubePromo.setPlanId("CATHAY_CUBE_DIGITAL");
        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(cubePromo));
        when(benefitPlanRepository.findByPlanId("CATHAY_CUBE_DIGITAL")).thenReturn(
                buildPlan("CATHAY_CUBE_DIGITAL", "CATHAY_CUBE", "Digital", "CATHAY_CUBE_PLANS",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "DAILY")
        );

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .date(LocalDate.of(2026, 3, 15))
                .planRuntimeByCard(Map.of("CATHAY_CUBE", Map.of("tier", "LEVEL_3")))
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals(33, response.getRecommendations().get(0).getEstimatedReturn());
        assertTrue(response.getRecommendations().get(0).getConditions().stream()
                .anyMatch(condition -> "ASSUMED_BENEFIT_TIER".equals(condition.getType()) && "LEVEL_3".equals(condition.getValue())));
    }

    @Test
    public void testUnicardHundredStoreCatalogPromotionBecomesRecommendableWithExplicitRuntimeState() {
        Promotion unicardPromo = buildPromotion("u_hundred", "u_hundred_v1", "ESUN_UNICARD", "Unicard", "ESUN", "E.SUN",
                BigDecimal.valueOf(4.5), 500, 0, LocalDate.of(2026, 6, 30));
        unicardPromo.setCategory("SHOPPING");
        unicardPromo.setSubcategory("SPORTING_GOODS");
        unicardPromo.setRecommendationScope("CATALOG_ONLY");
        unicardPromo.setConditions(List.of(
                condition("TEXT", "UNICARD_HUNDRED_STORE_CATALOG", "百大指定消費"),
                condition("VENUE", "DECATHLON", "迪卡儂")
        ));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(unicardPromo));
        when(benefitPlanRepository.findByPlanId("ESUN_UNICARD_FLEXIBLE")).thenReturn(
                buildPlan("ESUN_UNICARD_FLEXIBLE", "ESUN_UNICARD", "Flexible", "ESUN_UNICARD_PLANS",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "MONTHLY")
        );

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("SHOPPING")
                        .subcategory("SPORTING_GOODS")
                        .merchantName("DECATHLON")
                        .date(LocalDate.of(2026, 3, 15))
                        .build())
                .activePlansByCard(Map.of("ESUN_UNICARD", "ESUN_UNICARD_FLEXIBLE"))
                .planRuntimeByCard(Map.of("ESUN_UNICARD", Map.of("selected_merchants", "DECATHLON")))
                .build());

        assertEquals(1, response.getRecommendations().size());
        var rec = response.getRecommendations().get(0);
        assertNotNull(rec.getActivePlan());
        assertEquals("ESUN_UNICARD_FLEXIBLE", rec.getActivePlan().getPlanId());
        assertEquals(35, rec.getEstimatedReturn());
        assertTrue(rec.getConditions().stream()
                .anyMatch(condition -> "ASSUMED_ACTIVE_PLAN".equals(condition.getType()) && "ESUN_UNICARD_FLEXIBLE".equals(condition.getValue())));
    }

    @Test
    public void testWinningPlanUsesHighestReturnAcrossExclusiveGroups() {
        Promotion alphaPromo = buildPromotion("alpha1", "alpha_ver1", "MULTI_PLAN_CARD", "Multi Plan", "BANK1", "Bank One",
                BigDecimal.valueOf(5.0), 500, 0, LocalDate.of(2026, 6, 30));
        alphaPromo.setPlanId("PLAN_ALPHA");

        Promotion betaPromo = buildPromotion("beta1", "beta_ver1", "MULTI_PLAN_CARD", "Multi Plan", "BANK1", "Bank One",
                BigDecimal.valueOf(3.0), 500, 0, LocalDate.of(2026, 6, 30));
        betaPromo.setPlanId("PLAN_BETA");

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(alphaPromo, betaPromo));
        when(benefitPlanRepository.findByPlanId("PLAN_ALPHA")).thenReturn(
                buildPlan("PLAN_ALPHA", "MULTI_PLAN_CARD", "Alpha", "GROUP_ALPHA",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "DAILY")
        );
        when(benefitPlanRepository.findByPlanId("PLAN_BETA")).thenReturn(
                buildPlan("PLAN_BETA", "MULTI_PLAN_CARD", "Beta", "GROUP_BETA",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "MONTHLY")
        );

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000).category("ONLINE").date(LocalDate.of(2026, 3, 15)).build());

        assertEquals(1, response.getRecommendations().size());
        var rec = response.getRecommendations().get(0);
        assertNotNull(rec.getActivePlan());
        assertEquals("PLAN_ALPHA", rec.getActivePlan().getPlanId());
    }

    private Promotion buildPromotion(String promoId, String promoVersionId, String cardCode, String cardName,
                                      String bankCode, String bankName, BigDecimal cashbackValue,
                                      Integer maxCashback, Integer annualFee, LocalDate validUntil) {
        return Promotion.builder()
                .promoId(promoId)
                .promoVersionId(promoVersionId)
                .cardCode(cardCode)
                .cardName(cardName)
                .bankCode(bankCode)
                .bankName(bankName)
                .category("ONLINE")
                .recommendationScope("RECOMMENDABLE")
                .cashbackType("PERCENT")
                .cashbackValue(cashbackValue)
                .maxCashback(maxCashback)
                .annualFee(annualFee)
                .cardStatus("ACTIVE")
                .validUntil(validUntil)
                .status("ACTIVE")
                .build();
    }

    private BenefitPlan buildPlan(String planId, String cardCode, String planName, String exclusiveGroup,
                                  LocalDate validFrom, LocalDate validUntil, String switchFrequency) {
        return BenefitPlan.builder()
                .planId(planId)
                .cardCode(cardCode)
                .planName(planName)
                .switchFrequency(switchFrequency)
                .exclusiveGroup(exclusiveGroup)
                .status("ACTIVE")
                .validFrom(validFrom)
                .validUntil(validUntil)
                .build();
    }

    private PromotionCondition condition(String type, String value, String label) {
        return PromotionCondition.builder().type(type).value(value).label(label).build();
    }
}
