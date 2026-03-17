package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DecisionEngineTest {

    private PromotionRepository promotionRepository;
    private RewardCalculator rewardCalculator;
    private DecisionEngine decisionEngine;

    @BeforeEach
    public void setup() {
        promotionRepository = Mockito.mock(PromotionRepository.class);
        rewardCalculator = new RewardCalculator(); // Real logic
        decisionEngine = new DecisionEngine(promotionRepository, rewardCalculator);
    }

    @Test
    public void testRecommendBestCard() {
        // Setup mock data
        Promotion promo1 = Promotion.builder()
                .promoId("promo1")
                .cardId("card_a")
                .bank("BankA")
                .rewardRate(0.05)
                .categories(List.of("dining"))
                .channel("all")
                .build();
        
        Promotion promo2 = Promotion.builder()
                .promoId("promo2")
                .cardId("card_b")
                .bank("BankB")
                .rewardRate(0.01)
                .categories(List.of("dining"))
                .channel("all")
                .build();

        when(promotionRepository.findActivePromotions(any())).thenReturn(List.of(promo1, promo2));

        RecommendationRequest request = RecommendationRequest.builder()
                .transactionAmount(100)
                .merchantCategory("dining")
                .transactionDate(LocalDate.now())
                .channel("offline")
                .build();

        RecommendationResponse response = decisionEngine.recommend(request);

        assertNotNull(response);
        assertEquals("card_a", response.getBestCard());
        assertEquals(5, response.getExpectedReward());
    }
}
