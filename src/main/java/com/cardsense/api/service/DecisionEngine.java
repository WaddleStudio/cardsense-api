package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionEngine {

    private final PromotionRepository promotionRepository;
    private final RewardCalculator rewardCalculator;

    public RecommendationResponse recommend(RecommendationRequest request) {
        List<Promotion> activePromotions = promotionRepository.findActivePromotions(request.getTransactionDate());

        List<Promotion> eligiblePromotions = activePromotions.stream()
                .filter(p -> isEligible(p, request))
                .collect(Collectors.toList());

        if (eligiblePromotions.isEmpty()) {
            return RecommendationResponse.builder()
                    .bestCard(null)
                    .reasons(List.of("No eligible promotions found."))
                    .requirements(List.of())
                    .evidence(Map.of())
                    .expectedReward(0)
                    .currency("N/A")
                    .build();
        }

        // Calculate rewards for sorting
        Map<Promotion, Integer> rewards = new HashMap<>();
        for (Promotion p : eligiblePromotions) {
            rewards.put(p, rewardCalculator.calculateReward(p, request.getTransactionAmount()));
        }

        // Sort deterministically:
        // 1. Reward amount (DESC)
        // 2. Bank name (ASC) - for stability
        // 3. Card ID (ASC) - for stability
        eligiblePromotions.sort(Comparator.comparing((Promotion p) -> rewards.get(p)).reversed()
                .thenComparing(Promotion::getBank)
                .thenComparing(Promotion::getCardId));

        Promotion bestPromo = eligiblePromotions.get(0);
        int bestReward = rewards.get(bestPromo);

        List<String> reasons = new ArrayList<>();
        reasons.add(String.format("Best reward: %s (%.1f%% effective)", bestReward, (double)bestReward * 100 / request.getTransactionAmount()));
        reasons.add("Based on category: " + request.getMerchantCategory());

        List<String> requirements = new ArrayList<>();
        if (bestPromo.getMinAmount() != null && bestPromo.getMinAmount() > 0) {
            requirements.add("Min spend: " + bestPromo.getMinAmount());
        }
        if (bestPromo.isRequiresRegistration()) {
            requirements.add("Requires registration");
        }

        Map<String, String> evidence = new HashMap<>();
        evidence.put("promo_version_id", bestPromo.getPromoVersionId());
        evidence.put("promo_id", bestPromo.getPromoId());

        return RecommendationResponse.builder()
                .bestCard(bestPromo.getCardId())
                .reasons(reasons)
                .requirements(requirements)
                .evidence(evidence)
                .expectedReward(bestReward)
                .currency(bestPromo.getRewardType()) // defaulting to reward type as currency proxy
                .build();
    }

    private boolean isEligible(Promotion p, RecommendationRequest r) {
        // Channel check
        if (r.getChannel() != null && p.getChannel() != null && !p.getChannel().equalsIgnoreCase("all")) {
            if (!p.getChannel().equalsIgnoreCase(r.getChannel())) {
                return false;
            }
        }

        // Category check
        // If promotion has categories, request merchant category must be in it.
        // If promotion categories is empty/null, assumingly it applies to everything? 
        // Safe assumption: valid promo has categories.
        if (p.getCategories() != null && !p.getCategories().isEmpty()) {
            boolean categoryMatch = p.getCategories().contains(r.getMerchantCategory()) || p.getCategories().contains("other");
             if (!categoryMatch) {
                 return false;
             }
        }
        
        return true;
    }
}
