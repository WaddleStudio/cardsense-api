package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.math.BigDecimal;
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
        Promotion promo1 = Promotion.builder()
                .promoId("promo1")
            .promoVersionId("ver1")
            .cardCode("CATHAY_DEMO_LIFESTYLE")
            .cardName("國泰世華 示例生活卡")
            .bankCode("CATHAY")
            .bankName("國泰世華")
            .category("ONLINE")
            .cashbackType("PERCENT")
            .cashbackValue(BigDecimal.valueOf(3.0))
            .maxCashback(400)
            .annualFee(1800)
            .validUntil(LocalDate.of(2026, 4, 30))
            .conditions(List.of("線上消費適用"))
            .status("ACTIVE")
                .build();

        Promotion promo2 = Promotion.builder()
                .promoId("promo2")
            .promoVersionId("ver2")
            .cardCode("CTBC_DEMO_ONLINE")
            .cardName("中國信託 示例網購卡")
            .bankCode("CTBC")
            .bankName("中國信託")
            .category("ONLINE")
            .cashbackType("PERCENT")
            .cashbackValue(BigDecimal.valueOf(3.0))
            .maxCashback(400)
            .annualFee(1800)
            .validUntil(LocalDate.of(2026, 6, 30))
            .conditions(List.of("需登錄活動"))
            .status("ACTIVE")
            .build();

        Promotion promo3 = Promotion.builder()
            .promoId("promo3")
            .promoVersionId("ver3")
            .cardCode("TAISHIN_DEMO_DIGITAL")
            .cardName("台新銀行 示例數位卡")
            .bankCode("TAISHIN")
            .bankName("台新銀行")
            .category("ONLINE")
            .cashbackType("PERCENT")
            .cashbackValue(BigDecimal.valueOf(2.0))
            .maxCashback(400)
            .annualFee(3000)
            .validUntil(LocalDate.of(2026, 12, 31))
            .conditions(List.of("需指定帳戶扣繳"))
            .status("ACTIVE")
                .build();

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo1, promo2, promo3));

        RecommendationRequest request = RecommendationRequest.builder()
            .amount(1000)
            .category("ONLINE")
            .date(LocalDate.now())
                .build();

        RecommendationResponse response = decisionEngine.recommend(request);

        assertNotNull(response);
        assertEquals(3, response.getRecommendations().size());
        assertEquals("promo1", response.getRecommendations().get(0).getPromotionId());
        assertEquals("promo2", response.getRecommendations().get(1).getPromotionId());
        assertEquals(30, response.getRecommendations().get(0).getEstimatedReturn());
        assertEquals(DecisionEngine.DISCLAIMER, response.getDisclaimer());
        assertNotNull(response.getRequestId());
        }

        @Test
        public void testRecommendFiltersByCardCode() {
        Promotion promo = Promotion.builder()
            .promoId("promo1")
            .promoVersionId("ver1")
            .cardCode("CTBC_DEMO_ONLINE")
            .cardName("中國信託 示例網購卡")
            .bankCode("CTBC")
            .bankName("中國信託")
            .category("ONLINE")
            .cashbackType("PERCENT")
            .cashbackValue(BigDecimal.valueOf(3.0))
            .maxCashback(300)
            .annualFee(1800)
            .validUntil(LocalDate.of(2026, 6, 30))
            .conditions(List.of("需登錄活動"))
            .status("ACTIVE")
            .build();

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo));

        RecommendationRequest request = RecommendationRequest.builder()
            .amount(1000)
            .category("ONLINE")
            .cardCodes(List.of("CATHAY_DEMO_LIFESTYLE"))
            .date(LocalDate.now())
            .build();

        RecommendationResponse response = decisionEngine.recommend(request);

        assertTrue(response.getRecommendations().isEmpty());
        assertEquals(DecisionEngine.DISCLAIMER, response.getDisclaimer());
    }
}
