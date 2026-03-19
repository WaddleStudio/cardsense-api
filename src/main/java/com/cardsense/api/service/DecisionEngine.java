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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DecisionEngine {

    public static final String DISCLAIMER = "CardSense 提供信用卡優惠比較資訊，不構成金融建議。實際回饋依各銀行公告為準，請以銀行官網資訊為最終依據。";

    private final PromotionRepository promotionRepository;
    private final RewardCalculator rewardCalculator;

    public RecommendationResponse recommend(RecommendationRequest request) {
        LocalDate requestDate = request.getDate() != null ? request.getDate() : LocalDate.now();
        List<Promotion> activePromotions = promotionRepository.findActivePromotions(requestDate);
        Comparator<ScoredPromotion> rankingComparator = recommendationComparator();

        List<ScoredPromotion> scoredPromotions = activePromotions.stream()
                .filter(p -> isEligible(p, request))
            .map(promotion -> toScoredPromotion(promotion, request.getAmount()))
            .filter(scored -> scored.cappedReturn() > 0)
            .sorted(rankingComparator)
            .toList();

        Map<String, ScoredPromotion> bestPromotionByCard = new LinkedHashMap<>();
        for (ScoredPromotion scoredPromotion : scoredPromotions) {
            bestPromotionByCard.putIfAbsent(distinctCardKey(scoredPromotion), scoredPromotion);
        }

        List<CardRecommendation> recommendations = bestPromotionByCard.values().stream()
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

        if (p.getCardStatus() != null && !"ACTIVE".equalsIgnoreCase(p.getCardStatus())) {
            return false;
        }

        String normalizedCategory = normalizeValue(r.getCategory());

        if (p.getCategory() != null && !normalizeValue(p.getCategory()).equals(normalizedCategory)) {
            return false;
        }

        if (p.getMinAmount() != null && r.getAmount() < p.getMinAmount()) {
            return false;
        }

        if (r.getCardCodes() != null && !r.getCardCodes().isEmpty()) {
            boolean matchesCard = r.getCardCodes().stream()
                    .map(this::normalizeValue)
                    .anyMatch(cardCode -> cardCode.equals(normalizeValue(p.getCardCode())));
            if (!matchesCard) {
                return false;
            }
        }

        if (!matchesLocation(p, r.getLocation())) {
            return false;
        }

        if (matchesExcludedConditions(p, r)) {
            return false;
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

    private Comparator<ScoredPromotion> recommendationComparator() {
        return Comparator
                .comparingInt(ScoredPromotion::cappedReturn).reversed()
                .thenComparing(scored -> scored.promotion().getValidUntil(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(scored -> scored.promotion().getAnnualFee(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(scored -> scored.promotion().getBankCode(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(scored -> scored.promotion().getCardCode(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(scored -> scored.promotion().getPromoVersionId(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private String distinctCardKey(ScoredPromotion scoredPromotion) {
        Promotion promotion = scoredPromotion.promotion();
        String cardCode = normalizeValue(promotion.getCardCode());
        if (!cardCode.isBlank()) {
            return cardCode;
        }

        return normalizeValue(promotion.getPromoVersionId());
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private CardRecommendation toRecommendation(ScoredPromotion scoredPromotion) {
        Promotion promotion = scoredPromotion.promotion();
        List<String> recommendationConditions = buildRecommendationConditions(promotion);
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
                .conditions(recommendationConditions)
                .applyUrl(promotion.getApplyUrl())
                .build();
    }

    private boolean matchesLocation(Promotion promotion, String requestLocation) {
        List<String> conditions = promotion.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        List<String> locationOnlyTokens = conditions.stream()
                .map(this::extractLocationOnlyToken)
                .filter(token -> !token.isBlank())
                .toList();

        if (locationOnlyTokens.isEmpty()) {
            return true;
        }

        String normalizedLocation = normalizeValue(requestLocation);
        if (normalizedLocation.isBlank()) {
            return false;
        }

        return locationOnlyTokens.stream().anyMatch(normalizedLocation::contains);
    }

    private boolean matchesExcludedConditions(Promotion promotion, RecommendationRequest request) {
        List<String> excludedConditions = promotion.getExcludedConditions();
        if (excludedConditions == null || excludedConditions.isEmpty()) {
            return false;
        }

        String normalizedCategory = normalizeValue(request.getCategory());
        String normalizedLocation = normalizeValue(request.getLocation());

        for (String excludedCondition : excludedConditions) {
            String normalizedCondition = normalizeValue(excludedCondition);
            if (normalizedCondition.startsWith("CATEGORY:")) {
                if (normalizedCategory.equals(normalizedCondition.substring("CATEGORY:".length()))) {
                    return true;
                }
            } else if (normalizedCondition.startsWith("LOCATION:")) {
                String locationToken = normalizedCondition.substring("LOCATION:".length());
                if (!normalizedLocation.isBlank() && normalizedLocation.contains(locationToken)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String extractLocationOnlyToken(String condition) {
        String normalizedCondition = normalizeValue(condition);
        if (!normalizedCondition.startsWith("LOCATION_ONLY:")) {
            return "";
        }

        return normalizedCondition.substring("LOCATION_ONLY:".length());
    }

    private List<String> buildRecommendationConditions(Promotion promotion) {
        List<String> conditions = promotion.getConditions() == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(promotion.getConditions());

        if (promotion.getMinAmount() != null && promotion.getMinAmount() > 0) {
            conditions.add("最低消費 " + promotion.getMinAmount() + " 元");
        }

        if (promotion.isRequiresRegistration()) {
            conditions.add("需登錄活動");
        }

        if (promotion.getFrequencyLimit() != null && !promotion.getFrequencyLimit().isBlank()) {
            conditions.add("頻率限制 " + promotion.getFrequencyLimit().toUpperCase());
        }

        return conditions.stream().distinct().toList();
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
