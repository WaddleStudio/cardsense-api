package com.cardsense.api.service;

import com.cardsense.api.domain.BenefitUsage;
import com.cardsense.api.domain.ComparisonMode;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
import com.cardsense.api.domain.RecommendationComparisonOptions;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.domain.RecommendationScenario;
import com.cardsense.api.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DecisionEngineTest {

    private PromotionRepository promotionRepository;
    private RewardCalculator rewardCalculator;
    private DecisionEngine decisionEngine;

    @BeforeEach
    public void setup() {
        promotionRepository = Mockito.mock(PromotionRepository.class);
        rewardCalculator = new RewardCalculator();
        decisionEngine = new DecisionEngine(promotionRepository, rewardCalculator);
    }

    @Test
    public void testRecommendReturnsSortedTopCards() {
        Promotion promo1 = buildPromotion("promo1", "ver1", "CATHAY_DEMO_LIFESTYLE", "國泰世華 示例生活卡", "CATHAY", "國泰世華", BigDecimal.valueOf(3.0), 400, 1800, LocalDate.of(2026, 4, 30));
        promo1.setConditions(List.of(condition("TEXT", "ONLINE", "線上消費適用")));

        Promotion promo2 = buildPromotion("promo2", "ver2", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(3.0), 400, 1800, LocalDate.of(2026, 6, 30));
        promo2.setRequiresRegistration(true);
        promo2.setConditions(List.of(condition("TEXT", "REGISTER", "需登錄活動")));

        Promotion promo3 = buildPromotion("promo3", "ver3", "TAISHIN_DEMO_DIGITAL", "台新銀行 示例數位卡", "TAISHIN", "台新銀行", BigDecimal.valueOf(2.0), 400, 3000, LocalDate.of(2026, 12, 31));
        promo3.setConditions(List.of(condition("TEXT", "ACCOUNT", "需指定帳戶扣繳")));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo1, promo2, promo3));

        RecommendationRequest request = RecommendationRequest.builder()
                .amount(1000)
                .category("online")
                .registeredPromotionIds(List.of("ver2"))
                .date(LocalDate.now())
                .build();

        RecommendationResponse response = decisionEngine.recommend(request);

        assertNotNull(response);
        assertEquals(3, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
        assertEquals("promo2", response.getRecommendations().get(1).getPromotionId());
        assertEquals(30, response.getRecommendations().get(0).getEstimatedReturn());
        assertTrue(response.getRecommendations().get(1).getConditions().stream().anyMatch(condition -> "REGISTRATION_REQUIRED".equals(condition.getType())));
        assertEquals(DecisionEngine.DISCLAIMER, response.getDisclaimer());
        assertNotNull(response.getRequestId());
                assertEquals(ComparisonMode.BEST_SINGLE_PROMOTION, response.getComparison().getMode());
                assertEquals(1000, response.getScenario().getAmount());
    }

    @Test
    public void testRecommendFiltersByCardCode() {
        Promotion promo = buildPromotion("promo1", "ver1", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(3.0), 300, 1800, LocalDate.of(2026, 6, 30));
        promo.setRequiresRegistration(true);
        promo.setConditions(List.of(condition("TEXT", "REGISTER", "需登錄活動")));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationRequest request = RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .cardCodes(List.of("CATHAY_DEMO_LIFESTYLE"))
                .registeredPromotionIds(List.of("ver1"))
                .date(LocalDate.now())
                .build();

        RecommendationResponse response = decisionEngine.recommend(request);

        assertTrue(response.getRecommendations().isEmpty());
        assertEquals(DecisionEngine.DISCLAIMER, response.getDisclaimer());
    }

    @Test
    public void testRecommendKeepsOnlyBestPromotionPerCard() {
        Promotion betterPromo = buildPromotion("promo1", "ver1", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(3.0), 300, 1800, LocalDate.of(2026, 6, 30));
        Promotion weakerPromoSameCard = buildPromotion("promo2", "ver2", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(1.0), 300, 1800, LocalDate.of(2026, 5, 31));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(betterPromo, weakerPromoSameCard));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .date(LocalDate.now())
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
        assertEquals(2, response.getRecommendations().get(0).getMatchedPromotionCount());
        assertEquals(2, response.getRecommendations().get(0).getPromotionBreakdown().size());
    }

    @Test
    public void testRecommendStacksEligiblePromotionsWhenRequested() {
        Promotion percentPromo = buildPromotion("promo1", "ver1", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(3.0), 300, 1800, LocalDate.of(2026, 6, 30));
        Promotion fixedPromo = buildPromotion("promo2", "ver2", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(50), 50, 1800, LocalDate.of(2026, 5, 31));
        fixedPromo.setCashbackType("FIXED");
        fixedPromo.setMaxCashback(null);

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(percentPromo, fixedPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("ONLINE")
                        .date(LocalDate.now())
                        .build())
                .comparison(RecommendationComparisonOptions.builder()
                        .mode(ComparisonMode.STACK_ALL_ELIGIBLE)
                        .includePromotionBreakdown(true)
                        .build())
                .build());

        assertEquals(1, response.getRecommendations().size());
        assertEquals(80, response.getRecommendations().get(0).getEstimatedReturn());
        assertEquals("STACK_ALL_ELIGIBLE", response.getRecommendations().get(0).getRankingMode());
        assertEquals(2, response.getRecommendations().get(0).getPromotionBreakdown().size());
        assertTrue(response.getRecommendations().get(0).getPromotionBreakdown().stream().allMatch(item -> Boolean.TRUE.equals(item.getContributesToCardTotal())));
    }

    @Test
    public void testRecommendBuildsBreakEvenAnalysisForFixedVsPercent() {
        Promotion fixedPromo = buildPromotion("promo1", "ver1", "CARD_FIXED", "固定回饋卡", "CTBC", "中國信託", BigDecimal.valueOf(50), null, 1800, LocalDate.of(2026, 6, 30));
        fixedPromo.setCashbackType("FIXED");
        fixedPromo.setMaxCashback(null);

        Promotion percentPromo = buildPromotion("promo2", "ver2", "CARD_PERCENT", "百分比回饋卡", "CATHAY", "國泰世華", BigDecimal.valueOf(3.0), 120, 1800, LocalDate.of(2026, 6, 30));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(fixedPromo, percentPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .scenario(RecommendationScenario.builder()
                        .amount(1000)
                        .category("ONLINE")
                        .location("Taipei Xinyi")
                        .date(LocalDate.of(2026, 3, 20))
                        .build())
                .comparison(RecommendationComparisonOptions.builder()
                        .includeBreakEvenAnalysis(true)
                        .build())
                .build());

        assertNotNull(response.getComparison());
        assertTrue(response.getComparison().getBreakEvenEvaluated());
        assertEquals(1667, response.getComparison().getBreakEvenAnalyses().get(0).getBreakEvenAmount());
        assertEquals(1000, response.getScenario().getAmount());
        assertEquals("ONLINE", response.getScenario().getCategory());
    }

    @Test
    public void testRecommendSkipsInactiveCardsAndZeroReward() {
        Promotion inactiveCardPromo = buildPromotion("promo1", "ver1", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(3.0), 300, 1800, LocalDate.of(2026, 6, 30));
        inactiveCardPromo.setCardStatus("DISCONTINUED");

        Promotion zeroRewardPromo = buildPromotion("promo2", "ver2", "CATHAY_DEMO_LIFESTYLE", "國泰世華 示例生活卡", "CATHAY", "國泰世華", BigDecimal.ZERO, 300, 1800, LocalDate.of(2026, 6, 30));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(inactiveCardPromo, zeroRewardPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .date(LocalDate.now())
                .build());

        assertTrue(response.getRecommendations().isEmpty());
    }

    @Test
    public void testRecommendAppliesLocationOnlyConditions() {
        Promotion taipeiPromo = buildPromotion("promo1", "ver1", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(4.0), 400, 1800, LocalDate.of(2026, 6, 30));
        taipeiPromo.setConditions(List.of(condition("LOCATION_ONLY", "TAIPEI", "台北限定活動")));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(taipeiPromo));

        RecommendationResponse mismatch = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .location("Kaohsiung")
                .date(LocalDate.now())
                .build());

        RecommendationResponse match = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .location("Taipei Xinyi")
                .date(LocalDate.now())
                .build());

        assertTrue(mismatch.getRecommendations().isEmpty());
        assertEquals(1, match.getRecommendations().size());
    }

    @Test
    public void testRecommendHonorsExcludedConditions() {
        Promotion excludedPromo = buildPromotion("promo1", "ver1", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(4.0), 400, 1800, LocalDate.of(2026, 6, 30));
        excludedPromo.setExcludedConditions(List.of(
                condition("LOCATION_EXCLUDE", "TAIPEI", "台北排除"),
                condition("CATEGORY_EXCLUDE", "ONLINE", "排除一般線上消費")
        ));

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(excludedPromo));

        RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .location("Taipei")
                .date(LocalDate.now())
                .build());

        assertTrue(response.getRecommendations().isEmpty());
    }

    @Test
    public void testRecommendRequiresRegistrationState() {
        Promotion promo = buildPromotion("promo1", "ver1", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(3.0), 300, 1800, LocalDate.of(2026, 6, 30));
        promo.setRequiresRegistration(true);

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse missingRegistration = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .date(LocalDate.now())
                .build());

        RecommendationResponse registered = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .registeredPromotionIds(List.of("ver1"))
                .date(LocalDate.now())
                .build());

        assertTrue(missingRegistration.getRecommendations().isEmpty());
        assertEquals(1, registered.getRecommendations().size());
    }

    @Test
    public void testRecommendSkipsExhaustedBenefits() {
        Promotion promo = buildPromotion("promo1", "ver1", "CTBC_DEMO_ONLINE", "中國信託 示例網購卡", "CTBC", "中國信託", BigDecimal.valueOf(3.0), 300, 1800, LocalDate.of(2026, 6, 30));
        promo.setRequiresRegistration(true);

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationResponse exhausted = decisionEngine.recommend(RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .registeredPromotionIds(List.of("ver1"))
                .benefitUsage(List.of(BenefitUsage.builder().promoVersionId("ver1").consumedAmount(300).build()))
                .date(LocalDate.now())
                .build());

        assertTrue(exhausted.getRecommendations().isEmpty());
    }

        @Test
        public void testRecommendSkipsCatalogOnlyAndFutureScopePromotions() {
                Promotion catalogOnlyPromo = buildPromotion("promo1", "ver1", "ESUN_DEMO_SERVICE", "玉山服務卡", "ESUN", "玉山銀行", BigDecimal.valueOf(3.0), 300, 1800, LocalDate.of(2026, 6, 30));
                catalogOnlyPromo.setRecommendationScope("CATALOG_ONLY");

                Promotion futureScopePromo = buildPromotion("promo2", "ver2", "ESUN_DEMO_INSURANCE", "玉山保費卡", "ESUN", "玉山銀行", BigDecimal.valueOf(3.0), 300, 1800, LocalDate.of(2026, 6, 30));
                futureScopePromo.setRecommendationScope("FUTURE_SCOPE");

                when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(catalogOnlyPromo, futureScopePromo));

                RecommendationResponse response = decisionEngine.recommend(RecommendationRequest.builder()
                                .amount(1000)
                                .category("ONLINE")
                                .date(LocalDate.now())
                                .build());

                assertTrue(response.getRecommendations().isEmpty());
        }

    private Promotion buildPromotion(String promoId, String promoVersionId, String cardCode, String cardName, String bankCode, String bankName, BigDecimal cashbackValue, Integer maxCashback, Integer annualFee, LocalDate validUntil) {
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

    private PromotionCondition condition(String type, String value, String label) {
        return PromotionCondition.builder()
                .type(type)
                .value(value)
                .label(label)
                .build();
    }
}
