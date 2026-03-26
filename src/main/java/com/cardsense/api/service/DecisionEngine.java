package com.cardsense.api.service;

import com.cardsense.api.domain.BenefitUsage;
import com.cardsense.api.domain.BreakEvenAnalysis;
import com.cardsense.api.domain.CardRecommendation;
import com.cardsense.api.domain.ComparisonMode;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
import com.cardsense.api.domain.PromotionRewardBreakdown;
import com.cardsense.api.domain.PromotionStackability;
import com.cardsense.api.domain.RecommendationComparisonSummary;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.domain.RecommendationScenario;
import com.cardsense.api.domain.ScoredPromotion;
import com.cardsense.api.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionEngine {

    public static final String DISCLAIMER = "CardSense 提供信用卡優惠比較資訊，不構成金融建議。實際回饋依各銀行公告為準，請以銀行官網資訊為最終依據。";

    private final PromotionRepository promotionRepository;
    private final RewardCalculator rewardCalculator;

    public RecommendationResponse recommend(RecommendationRequest request) {
        RecommendationScenario resolvedScenario = request.toResolvedScenario();
        LocalDate requestDate = resolvedScenario.getDate() != null ? resolvedScenario.getDate() : LocalDate.now();
        ComparisonMode comparisonMode = request.getResolvedComparisonMode();

        List<Promotion> activePromotions = promotionRepository.findActivePromotions(requestDate);
        Comparator<ScoredPromotion> promotionComparator = recommendationComparator();

        List<ScoredPromotion> scoredPromotions = activePromotions.stream()
                .filter(promotion -> isEligible(promotion, request))
                .map(promotion -> toScoredPromotion(promotion, resolvedScenario.getAmount()))
                .filter(scored -> scored.cappedReturn() > 0)
                .sorted(promotionComparator)
                .toList();

        List<CardAggregate> rankedCards = scoredPromotions.stream()
                .collect(Collectors.groupingBy(this::distinctCardKey, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(promotions -> toCardAggregate(promotions, comparisonMode))
                .sorted(cardAggregateComparator())
                .toList();

        List<CardRecommendation> recommendations = rankedCards.stream()
                .limit(request.getResolvedMaxResults())
                .map(cardAggregate -> toRecommendation(cardAggregate, comparisonMode, request.shouldIncludePromotionBreakdown()))
                .toList();

        List<BreakEvenAnalysis> breakEvenAnalyses = request.shouldIncludeBreakEvenAnalysis()
                ? buildBreakEvenAnalyses(rankedCards)
                : List.of();

        return RecommendationResponse.builder()
                .requestId(java.util.UUID.randomUUID().toString())
                .scenario(resolvedScenario)
                .comparison(buildComparisonSummary(comparisonMode, activePromotions.size(), scoredPromotions.size(), rankedCards.size(), breakEvenAnalyses))
                .recommendations(recommendations)
                .generatedAt(LocalDateTime.now())
                .disclaimer(DISCLAIMER)
                .build();
    }

    private boolean isEligible(Promotion promotion, RecommendationRequest request) {
        Integer amount = request.getResolvedAmount();
        String category = request.getResolvedCategory();
        String location = request.getResolvedLocation();
        String channel = request.getResolvedChannel();

        if (amount == null || amount < 0 || category == null || category.isBlank()) {
            return false;
        }

        if (!isRecommendationScopeEligible(promotion)) {
            return false;
        }

        if (promotion.getCardStatus() != null && !"ACTIVE".equalsIgnoreCase(promotion.getCardStatus())) {
            return false;
        }

        if (promotion.getCategory() != null && !normalizeValue(promotion.getCategory()).equals(normalizeValue(category))) {
            return false;
        }

        if (promotion.getMinAmount() != null && amount < promotion.getMinAmount()) {
            return false;
        }

        if (request.getResolvedCardCodes() != null && !request.getResolvedCardCodes().isEmpty()) {
            boolean matchesCard = request.getResolvedCardCodes().stream()
                    .map(this::normalizeValue)
                    .anyMatch(cardCode -> cardCode.equals(normalizeValue(promotion.getCardCode())));
            if (!matchesCard) {
                return false;
            }
        }

        if (!matchesChannel(promotion, channel)) {
            return false;
        }

        if (!matchesLocation(promotion, location)) {
            return false;
        }

        if (matchesExcludedConditions(promotion, request)) {
            return false;
        }

        if (!matchesRegistrationState(promotion, request)) {
            return false;
        }

        return !hasExhaustedBenefit(promotion, request);
    }

    private boolean isRecommendationScopeEligible(Promotion promotion) {
        String recommendationScope = promotion.getRecommendationScope();
        return recommendationScope == null
                || recommendationScope.isBlank()
                || "RECOMMENDABLE".equalsIgnoreCase(recommendationScope);
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

    private Comparator<CardAggregate> cardAggregateComparator() {
        return Comparator
                .comparingInt(CardAggregate::totalReturn).reversed()
                .thenComparing(cardAggregate -> cardAggregate.primaryPromotion().getValidUntil(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(cardAggregate -> cardAggregate.primaryPromotion().getAnnualFee(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(cardAggregate -> cardAggregate.primaryPromotion().getBankCode(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(cardAggregate -> cardAggregate.primaryPromotion().getCardCode(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(cardAggregate -> cardAggregate.primaryPromotion().getPromoVersionId(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private CardAggregate toCardAggregate(List<ScoredPromotion> promotions, ComparisonMode comparisonMode) {
        List<String> notes = new ArrayList<>();
        List<ScoredPromotion> contributingPromotions;
        if (comparisonMode == ComparisonMode.STACK_ALL_ELIGIBLE) {
            StackResolution resolution = resolveContributingPromotions(promotions);
            contributingPromotions = resolution.contributingPromotions();
            notes.addAll(resolution.notes());
        } else {
            contributingPromotions = List.of(promotions.get(0));
        }

        ScoredPromotion primary = contributingPromotions.get(0);
        int totalReturn = contributingPromotions.stream().mapToInt(ScoredPromotion::cappedReturn).sum();

        return new CardAggregate(primary.promotion(), promotions, contributingPromotions, totalReturn, notes);
    }

    // Maximum promotions to consider in bitmask combination search.
    // Keeps search cost at O(2^MAX_BITMASK_SIZE) = O(32) worst case.
    // Candidates are pre-sorted by cappedReturn so the top value-bearing promotions
    // are always evaluated, even when a card has more than this many eligible promotions.
    private static final int MAX_BITMASK_SIZE = 5;

    private StackResolution resolveContributingPromotions(List<ScoredPromotion> promotions) {
        if (promotions.size() <= 1) {
            return new StackResolution(List.of(promotions.get(0)), List.of());
        }

        // Take at most MAX_BITMASK_SIZE highest-value promotions before the bitmask search.
        // This preserves ranking accuracy: a card with 16 promotions still gets its best
        // combination evaluated, rather than silently falling back to rank-#1 only.
        List<ScoredPromotion> candidates = promotions.stream()
                .sorted(Comparator.comparingInt(ScoredPromotion::cappedReturn).reversed())
                .limit(MAX_BITMASK_SIZE)
                .toList();

        List<ScoredPromotion> bestSelection = List.of(candidates.get(0));
        int selectionCount = 1 << candidates.size();

        for (int mask = 1; mask < selectionCount; mask++) {
            List<ScoredPromotion> selection = new ArrayList<>();
            for (int index = 0; index < candidates.size(); index++) {
                if ((mask & (1 << index)) != 0) {
                    selection.add(candidates.get(index));
                }
            }

            if (!isValidStackSelection(selection)) {
                continue;
            }

            if (compareSelection(selection, bestSelection) > 0) {
                bestSelection = List.copyOf(selection);
            }
        }

        List<String> notes = new ArrayList<>();
        if (bestSelection.size() > 1) {
            notes.add("多優惠並存模式已由 promotion.stackability 顯式 metadata 控制；未標註 metadata 的舊資料不得直接視為可並存。");
        }
        if (promotions.size() > bestSelection.size()) {
            notes.add("部分符合條件的優惠未納入卡片總回饋，原因可能是缺少 stackability metadata、未滿足 requires 條件，或與已選優惠互斥。");
        }

        return new StackResolution(bestSelection, notes);
    }

    private boolean isValidStackSelection(List<ScoredPromotion> selection) {
        if (selection.isEmpty()) {
            return false;
        }
        if (selection.size() == 1) {
            return true;
        }

        Set<String> selectedPromoVersionIds = selection.stream()
                .map(scoredPromotion -> normalizeValue(scoredPromotion.promotion().getPromoVersionId()))
                .collect(Collectors.toCollection(HashSet::new));

        for (ScoredPromotion scoredPromotion : selection) {
            PromotionStackability stackability = scoredPromotion.promotion().getStackability();
            if (!hasDeterministicStackability(scoredPromotion.promotion())) {
                return false;
            }

            Set<String> requiredPromoVersionIds = normalizeValues(stackability.getRequiresPromoVersionIds());
            if (!selectedPromoVersionIds.containsAll(requiredPromoVersionIds)) {
                return false;
            }

            Set<String> excludedPromoVersionIds = normalizeValues(stackability.getExcludesPromoVersionIds());
            if (selection.stream()
                    .map(candidate -> normalizeValue(candidate.promotion().getPromoVersionId()))
                    .filter(candidateId -> !candidateId.equals(normalizeValue(scoredPromotion.promotion().getPromoVersionId())))
                    .anyMatch(excludedPromoVersionIds::contains)) {
                return false;
            }

            Set<String> allowedPromoVersionIds = normalizeValues(stackability.getStackWithPromoVersionIds());
            if (!allowedPromoVersionIds.isEmpty()) {
                for (ScoredPromotion peerPromotion : selection) {
                    String peerPromoVersionId = normalizeValue(peerPromotion.promotion().getPromoVersionId());
                    if (peerPromoVersionId.equals(normalizeValue(scoredPromotion.promotion().getPromoVersionId()))) {
                        continue;
                    }
                    if (!allowedPromoVersionIds.contains(peerPromoVersionId) && !requiredPromoVersionIds.contains(peerPromoVersionId)) {
                        return false;
                    }
                }
            }
        }

        for (int leftIndex = 0; leftIndex < selection.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < selection.size(); rightIndex++) {
                if (!canCoexist(selection.get(leftIndex).promotion(), selection.get(rightIndex).promotion())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean canCoexist(Promotion leftPromotion, Promotion rightPromotion) {
        if (!hasDeterministicStackability(leftPromotion) || !hasDeterministicStackability(rightPromotion)) {
            return false;
        }

        PromotionStackability leftStackability = leftPromotion.getStackability();
        PromotionStackability rightStackability = rightPromotion.getStackability();

        String leftPromoVersionId = normalizeValue(leftPromotion.getPromoVersionId());
        String rightPromoVersionId = normalizeValue(rightPromotion.getPromoVersionId());
        if (normalizeValues(leftStackability.getExcludesPromoVersionIds()).contains(rightPromoVersionId)
                || normalizeValues(rightStackability.getExcludesPromoVersionIds()).contains(leftPromoVersionId)) {
            return false;
        }

        String leftRelationshipMode = normalizeValue(leftStackability.getRelationshipMode());
        String rightRelationshipMode = normalizeValue(rightStackability.getRelationshipMode());
        String leftGroupId = normalizeValue(leftStackability.getGroupId());
        String rightGroupId = normalizeValue(rightStackability.getGroupId());
        if (!leftGroupId.isBlank()
                && leftGroupId.equals(rightGroupId)
                && ("MUTUALLY_EXCLUSIVE".equals(leftRelationshipMode) || "MUTUALLY_EXCLUSIVE".equals(rightRelationshipMode))) {
            return false;
        }

        Set<String> leftAllowedPromoVersionIds = normalizeValues(leftStackability.getStackWithPromoVersionIds());
        if (!leftAllowedPromoVersionIds.isEmpty() && !leftAllowedPromoVersionIds.contains(rightPromoVersionId)) {
            return false;
        }

        Set<String> rightAllowedPromoVersionIds = normalizeValues(rightStackability.getStackWithPromoVersionIds());
        return rightAllowedPromoVersionIds.isEmpty() || rightAllowedPromoVersionIds.contains(leftPromoVersionId);
    }

    private int compareSelection(List<ScoredPromotion> leftSelection, List<ScoredPromotion> rightSelection) {
        int leftTotal = leftSelection.stream().mapToInt(ScoredPromotion::cappedReturn).sum();
        int rightTotal = rightSelection.stream().mapToInt(ScoredPromotion::cappedReturn).sum();
        if (leftTotal != rightTotal) {
            return Integer.compare(leftTotal, rightTotal);
        }

        Comparator<ScoredPromotion> promotionComparator = recommendationComparator();
        int primaryComparison = promotionComparator.compare(leftSelection.get(0), rightSelection.get(0));
        if (primaryComparison != 0) {
            return -primaryComparison;
        }

        return Integer.compare(leftSelection.size(), rightSelection.size());
    }

    private boolean hasDeterministicStackability(Promotion promotion) {
        PromotionStackability stackability = promotion.getStackability();
        if (stackability == null) {
            return false;
        }

        String relationshipMode = normalizeValue(stackability.getRelationshipMode());
        return !relationshipMode.isBlank() && !"MANUAL_REVIEW".equals(relationshipMode);
    }

    private Set<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        return values.stream()
                .map(this::normalizeValue)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
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

    private CardRecommendation toRecommendation(CardAggregate cardAggregate, ComparisonMode comparisonMode, boolean includePromotionBreakdown) {
        Promotion promotion = cardAggregate.primaryPromotion();
        List<PromotionCondition> recommendationConditions = buildRecommendationConditions(promotion);
        String cashbackValueText = promotion.getCashbackValue() == null
                ? "0"
                : promotion.getCashbackValue().stripTrailingZeros().toPlainString();
        String reason = comparisonMode == ComparisonMode.STACK_ALL_ELIGIBLE && cardAggregate.allEligiblePromotions().size() > 1
                ? String.format(
                "%s %s — %d 個可命中的優惠合計預估回饋 $%d 元；代表優惠為 %s%s，優惠至 %s",
                promotion.getBankName(),
                promotion.getCardName(),
                cardAggregate.contributingPromotions().size(),
                cardAggregate.totalReturn(),
                cashbackValueText,
                resolveCashbackSuffix(promotion.getCashbackType()),
                promotion.getValidUntil())
                : String.format(
                "%s %s — %s 消費享 %s%s 回饋，預估回饋 $%d 元，優惠至 %s",
                promotion.getBankName(),
                promotion.getCardName(),
                promotion.getCategory(),
                cashbackValueText,
                resolveCashbackSuffix(promotion.getCashbackType()),
                cardAggregate.totalReturn(),
                promotion.getValidUntil());

        List<PromotionRewardBreakdown> breakdown = includePromotionBreakdown
                ? buildPromotionBreakdown(cardAggregate, comparisonMode)
                : List.of();

        return CardRecommendation.builder()
                .cardCode(promotion.getCardCode())
                .cardName(promotion.getCardName())
                .bankCode(promotion.getBankCode())
                .bankName(promotion.getBankName())
                .cashbackType(promotion.getCashbackType())
                .cashbackValue(promotion.getCashbackValue())
                .estimatedReturn(cardAggregate.totalReturn())
                .matchedPromotionCount(cardAggregate.allEligiblePromotions().size())
                .rankingMode(comparisonMode.name())
                .reason(reason)
                .promotionId(promotion.getPromoId())
                .promoVersionId(promotion.getPromoVersionId())
                .validUntil(promotion.getValidUntil())
                .conditions(recommendationConditions)
                .promotionBreakdown(breakdown)
                .applyUrl(promotion.getApplyUrl())
                .build();
    }

    private List<PromotionRewardBreakdown> buildPromotionBreakdown(CardAggregate cardAggregate, ComparisonMode comparisonMode) {
        return cardAggregate.allEligiblePromotions().stream()
                .map(scoredPromotion -> {
                    Promotion promotion = scoredPromotion.promotion();
                    boolean contributes = cardAggregate.contributingPromotions().contains(scoredPromotion);
                    return PromotionRewardBreakdown.builder()
                            .promotionId(promotion.getPromoId())
                            .promoVersionId(promotion.getPromoVersionId())
                            .title(promotion.getTitle())
                            .cashbackType(promotion.getCashbackType())
                            .cashbackValue(promotion.getCashbackValue())
                            .estimatedReturn(scoredPromotion.estimatedReturn())
                            .cappedReturn(scoredPromotion.cappedReturn())
                            .contributesToCardTotal(contributes)
                                .assumedStackable(false)
                            .validUntil(promotion.getValidUntil())
                            .conditions(buildRecommendationConditions(promotion))
                                .reason(buildBreakdownReason(cardAggregate, comparisonMode, scoredPromotion, contributes))
                            .build();
                })
                .toList();
    }

                    private String buildBreakdownReason(
                        CardAggregate cardAggregate,
                        ComparisonMode comparisonMode,
                        ScoredPromotion scoredPromotion,
                        boolean contributes
                    ) {
                    if (comparisonMode == ComparisonMode.STACK_ALL_ELIGIBLE && cardAggregate.contributingPromotions().size() > 1) {
                        return contributes
                            ? "依 stackability metadata 判定可計入卡片總回饋。"
                            : "未納入卡片總回饋：缺少 stackability metadata、未滿足 requires 條件，或與已選優惠互斥。";
                    }

                    return String.format(
                        "%s：預估回饋 $%d 元，封頂後 $%d 元。",
                        contributes ? "計入卡片總回饋" : "僅作為比較參考",
                        scoredPromotion.estimatedReturn(),
                        scoredPromotion.cappedReturn());
                    }

    private boolean matchesChannel(Promotion promotion, String requestChannel) {
        String normalizedRequestChannel = normalizeValue(requestChannel);
        if (normalizedRequestChannel.isBlank()) {
            return true;
        }
        return normalizeValue(promotion.getChannel()).equals(normalizedRequestChannel);
    }

    private boolean matchesLocation(Promotion promotion, String requestLocation) {
        List<PromotionCondition> conditions = promotion.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        List<String> locationOnlyTokens = conditions.stream()
                .filter(condition -> "LOCATION_ONLY".equalsIgnoreCase(condition.getType()))
                .map(PromotionCondition::getValue)
                .map(this::normalizeValue)
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
        List<PromotionCondition> excludedConditions = promotion.getExcludedConditions();
        if (excludedConditions == null || excludedConditions.isEmpty()) {
            return false;
        }

        String normalizedCategory = normalizeValue(request.getResolvedCategory());
        String normalizedLocation = normalizeValue(request.getResolvedLocation());

        for (PromotionCondition excludedCondition : excludedConditions) {
            String normalizedType = normalizeValue(excludedCondition.getType());
            String normalizedValue = normalizeValue(excludedCondition.getValue());
            if ("CATEGORY_EXCLUDE".equals(normalizedType)) {
                if (normalizedCategory.equals(normalizedValue)) {
                    return true;
                }
            } else if ("LOCATION_EXCLUDE".equals(normalizedType)) {
                if (!normalizedLocation.isBlank() && normalizedLocation.contains(normalizedValue)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matchesRegistrationState(Promotion promotion, RecommendationRequest request) {
        if (!promotion.isRequiresRegistration()) {
            return true;
        }

        if (request.getRegisteredPromotionIds() == null || request.getRegisteredPromotionIds().isEmpty()) {
            return false;
        }

        String promotionKey = normalizeValue(promotion.getPromoVersionId());
        return request.getRegisteredPromotionIds().stream()
                .map(this::normalizeValue)
                .anyMatch(promotionKey::equals);
    }

    private boolean hasExhaustedBenefit(Promotion promotion, RecommendationRequest request) {
        if (request.getBenefitUsage() == null || request.getBenefitUsage().isEmpty()) {
            return false;
        }

        BenefitUsage matchedUsage = request.getBenefitUsage().stream()
                .filter(usage -> normalizeValue(usage.getPromoVersionId()).equals(normalizeValue(promotion.getPromoVersionId())))
                .findFirst()
                .orElse(null);

        if (matchedUsage == null || matchedUsage.getConsumedAmount() == null) {
            return false;
        }

        if (promotion.getMaxCashback() != null && matchedUsage.getConsumedAmount() >= promotion.getMaxCashback()) {
            return true;
        }

        return promotion.getFrequencyLimit() != null
                && "ONCE".equalsIgnoreCase(promotion.getFrequencyLimit())
                && matchedUsage.getConsumedAmount() > 0;
    }

    private List<PromotionCondition> buildRecommendationConditions(Promotion promotion) {
        List<PromotionCondition> conditions = promotion.getConditions() == null
                ? new ArrayList<>()
                : new ArrayList<>(promotion.getConditions());

        if (promotion.getMinAmount() != null && promotion.getMinAmount() > 0) {
            conditions.add(PromotionCondition.builder()
                    .type("MIN_SPEND")
                    .value(String.valueOf(promotion.getMinAmount()))
                    .label("最低消費 " + promotion.getMinAmount() + " 元")
                    .build());
        }

        if (promotion.isRequiresRegistration()) {
            conditions.add(PromotionCondition.builder()
                    .type("REGISTRATION_REQUIRED")
                    .value("true")
                    .label("需登錄活動")
                    .build());
        }

        if (promotion.getFrequencyLimit() != null && !promotion.getFrequencyLimit().isBlank()) {
            conditions.add(PromotionCondition.builder()
                    .type("FREQUENCY_LIMIT")
                    .value(promotion.getFrequencyLimit().toUpperCase())
                    .label("頻率限制 " + promotion.getFrequencyLimit().toUpperCase())
                    .build());
        }

        return conditions.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                condition -> normalizeValue(condition.getType()) + ":" + normalizeValue(condition.getValue()),
                                condition -> condition,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ),
                        map -> List.copyOf(map.values())
                ));
    }

    private RecommendationComparisonSummary buildComparisonSummary(
            ComparisonMode comparisonMode,
            int evaluatedPromotionCount,
            int eligiblePromotionCount,
            int rankedCardCount,
            List<BreakEvenAnalysis> breakEvenAnalyses
    ) {
        List<String> notes = new ArrayList<>();
        if (comparisonMode == ComparisonMode.STACK_ALL_ELIGIBLE) {
            notes.add("多優惠並存模式已由 promotion.stackability 顯式 metadata 控制；未標註 metadata 的舊資料不得直接視為可並存。");
        }
        if (breakEvenAnalyses.isEmpty()) {
            notes.add("本次比較沒有可計算的 FIXED vs PERCENT/POINTS break-even pair，或呼叫端未要求輸出 break-even 分析。");
        }

        return RecommendationComparisonSummary.builder()
                .mode(comparisonMode)
                .evaluatedPromotionCount(evaluatedPromotionCount)
                .eligiblePromotionCount(eligiblePromotionCount)
                .rankedCardCount(rankedCardCount)
                .breakEvenEvaluated(!breakEvenAnalyses.isEmpty())
                .breakEvenAnalyses(breakEvenAnalyses)
                .notes(notes)
                .build();
    }

    private List<BreakEvenAnalysis> buildBreakEvenAnalyses(List<CardAggregate> rankedCards) {
        List<BreakEvenAnalysis> analyses = new ArrayList<>();
        for (int index = 0; index < rankedCards.size() - 1; index++) {
            Optional<BreakEvenAnalysis> maybeAnalysis = buildBreakEvenAnalysis(rankedCards.get(index), rankedCards.get(index + 1));
            maybeAnalysis.ifPresent(analyses::add);
            if (analyses.size() >= 3) {
                break;
            }
        }
        return analyses;
    }

    private Optional<BreakEvenAnalysis> buildBreakEvenAnalysis(CardAggregate left, CardAggregate right) {
        Promotion leftPromotion = left.primaryPromotion();
        Promotion rightPromotion = right.primaryPromotion();

        Promotion fixedPromotion;
        Promotion variablePromotion;
        if ("FIXED".equalsIgnoreCase(leftPromotion.getCashbackType()) && isVariableReward(rightPromotion)) {
            fixedPromotion = leftPromotion;
            variablePromotion = rightPromotion;
        } else if ("FIXED".equalsIgnoreCase(rightPromotion.getCashbackType()) && isVariableReward(leftPromotion)) {
            fixedPromotion = rightPromotion;
            variablePromotion = leftPromotion;
        } else {
            return Optional.empty();
        }

        Integer breakEvenAmount = rewardCalculator.calculateBreakEvenAmount(fixedPromotion, variablePromotion);
        if (breakEvenAmount == null) {
            return Optional.empty();
        }

        Integer capSaturationAmount = rewardCalculator.calculateCapSaturationAmount(variablePromotion);
        String summary = capSaturationAmount != null && capSaturationAmount < breakEvenAmount
                ? String.format(
                "%s 與 %s 的理論 break-even 約在 %d 元，但百分比優惠會在約 %d 元時先達封頂。",
                leftPromotion.getCardName(),
                rightPromotion.getCardName(),
                breakEvenAmount,
                capSaturationAmount)
                : String.format(
                "%s 與 %s 的理論 break-even 約在 %d 元。若交易金額高於此值，百分比型優惠通常會優於固定回饋。",
                leftPromotion.getCardName(),
                rightPromotion.getCardName(),
                breakEvenAmount);

        return Optional.of(BreakEvenAnalysis.builder()
                .leftCardCode(leftPromotion.getCardCode())
                .rightCardCode(rightPromotion.getCardCode())
                .leftPromoVersionId(leftPromotion.getPromoVersionId())
                .rightPromoVersionId(rightPromotion.getPromoVersionId())
                .breakEvenAmount(breakEvenAmount)
                .variableRewardCapAmount(capSaturationAmount)
                .leftMinAmount(leftPromotion.getMinAmount())
                .rightMinAmount(rightPromotion.getMinAmount())
                .summary(summary)
                .build());
    }

    private boolean isVariableReward(Promotion promotion) {
        return promotion.getCashbackType() != null
                && ("PERCENT".equalsIgnoreCase(promotion.getCashbackType())
                || "POINTS".equalsIgnoreCase(promotion.getCashbackType()));
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

    private record CardAggregate(
            Promotion primaryPromotion,
            List<ScoredPromotion> allEligiblePromotions,
            List<ScoredPromotion> contributingPromotions,
            int totalReturn,
            List<String> notes
    ) {
    }

        private record StackResolution(
            List<ScoredPromotion> contributingPromotions,
            List<String> notes
        ) {
        }
}
