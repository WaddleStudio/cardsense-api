package com.cardsense.api.service;

import com.cardsense.api.domain.CardRecommendation;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.domain.ScoredPromotion;
import com.cardsense.api.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionEngine {

    public static final String DISCLAIMER = "CardSense 提供信用卡優惠比較資訊，不構成金融建議。實際回饋依各銀行公告為準，請以銀行官網資訊為最終依據。";

    private final PromotionRepository promotionRepository;
    private final RewardCalculator rewardCalculator;

    public RecommendationResponse recommend(RecommendationRequest request) {
        LocalDate requestDate = request.getDate() != null ? request.getDate() : LocalDate.now();
        List<Promotion> activePromotions = promotionRepository.findActivePromotions(requestDate);

        List<Promotion> eligiblePromotions = activePromotions.stream()
                .filter(p -> isEligible(p, request))
                .collect(Collectors.toList());

        List<CardRecommendation> recommendations = eligiblePromotions.stream()
                .map(promotion -> toScoredPromotion(promotion, request.getAmount()))
                .sorted(Comparator
                        .comparingInt(ScoredPromotion::cappedReturn).reversed()
                        .thenComparing(scored -> scored.promotion().getValidUntil(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(scored -> scored.promotion().getAnnualFee(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(scored -> scored.promotion().getBankCode(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(scored -> scored.promotion().getCardCode(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(scored -> scored.promotion().getPromoVersionId(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .limit(5)
                .map(this::toRecommendation)
                .toList();

        return RecommendationResponse.builder()
                .requestId(java.util.UUID.randomUUID().toString())
                .recommendations(recommendations)
                .generatedAt(LocalDateTime.now())
                .disclaimer(DISCLAIMER)
                .build();
    }

    private boolean isEligible(Promotion p, RecommendationRequest r) {
        if (r.getAmount() == null || r.getAmount() < 0 || r.getCategory() == null || r.getCategory().isBlank()) {
            return false;
        }

        if (p.getCategory() != null && !p.getCategory().equalsIgnoreCase(r.getCategory())) {
            return false;
        }

        if (p.getMinAmount() != null && r.getAmount() < p.getMinAmount()) {
            return false;
        }

        if (r.getCardCodes() != null && !r.getCardCodes().isEmpty()) {
            boolean matchesCard = r.getCardCodes().stream()
                    .anyMatch(cardCode -> cardCode.equalsIgnoreCase(p.getCardCode()));
            if (!matchesCard) {
                return false;
            }
        }

        return true;
    }

    private ScoredPromotion toScoredPromotion(Promotion promotion, Integer amount) {
        int estimatedReturn = rewardCalculator.calculateReward(promotion, amount);
        int cappedReturn = promotion.getMaxCashback() == null
                ? estimatedReturn
                : Math.min(estimatedReturn, promotion.getMaxCashback());

        return new ScoredPromotion(promotion, estimatedReturn, cappedReturn);
    }

    private CardRecommendation toRecommendation(ScoredPromotion scoredPromotion) {
        Promotion promotion = scoredPromotion.promotion();
        String cashbackValueText = promotion.getCashbackValue() == null
                ? "0"
                : promotion.getCashbackValue().stripTrailingZeros().toPlainString();
        String reason = String.format(
                "%s %s — %s 消費享 %s%s 回饋，預估回饋 $%d 元，優惠至 %s",
                promotion.getBankName(),
                promotion.getCardName(),
                promotion.getCategory(),
                cashbackValueText,
                resolveCashbackSuffix(promotion.getCashbackType()),
                scoredPromotion.cappedReturn(),
                promotion.getValidUntil());

        return CardRecommendation.builder()
                .cardName(promotion.getCardName())
                .bankName(promotion.getBankName())
                .cashbackType(promotion.getCashbackType())
                .cashbackValue(promotion.getCashbackValue())
                .estimatedReturn(scoredPromotion.cappedReturn())
                .reason(reason)
                .promotionId(promotion.getPromoId())
                .promoVersionId(promotion.getPromoVersionId())
                .validUntil(promotion.getValidUntil())
                .conditions(promotion.getConditions() == null ? List.of() : List.copyOf(promotion.getConditions()))
                .applyUrl(promotion.getApplyUrl())
                .build();
    }

    private String resolveCashbackSuffix(String cashbackType) {
        if (cashbackType == null) {
            return "";
        }

        return switch (cashbackType.toUpperCase()) {
            case "PERCENT", "POINTS" -> "%";
            case "FIXED" -> " 元";
            default -> "";
        };
    }
}
