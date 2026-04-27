package com.cardsense.api.service;

import com.cardsense.api.domain.ActivePlan;
import com.cardsense.api.domain.BenefitPlan;
import com.cardsense.api.domain.BenefitUsage;
import com.cardsense.api.domain.BreakEvenAnalysis;
import com.cardsense.api.domain.CardRecommendation;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
import com.cardsense.api.domain.PromotionRewardBreakdown;
import com.cardsense.api.domain.PromotionStackability;
import com.cardsense.api.domain.RecommendationComparisonSummary;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.domain.RecommendationScenario;
import com.cardsense.api.domain.ScoredPromotion;
import com.cardsense.api.repository.BenefitPlanRepository;
import com.cardsense.api.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionEngine {

    public static final String DISCLAIMER = "CardSense 提供信用卡優惠比較資訊，不構成金融建議。實際回饋依各銀行公告為準，請以銀行官網資訊為最終依據。";

    private static final Set<String> MERCHANT_CONDITION_TYPES = Set.of(
            "VENUE"
    );
    private static final Set<String> PAYMENT_CONDITION_TYPES = Set.of(
            "PAYMENT"
    );
    private static final Map<String, String> HIGH_FREQUENCY_MERCHANT_ALIASES = Map.ofEntries(
            Map.entry("全聯", "PXMART"),
            Map.entry("全聯福利中心", "PXMART"),
            Map.entry("大全聯", "PXMART"),
            Map.entry("PX MART", "PXMART"),
            Map.entry("PXMART", "PXMART"),
            Map.entry("家樂福", "CARREFOUR"),
            Map.entry("CARREFOUR", "CARREFOUR"),
            Map.entry("MOMO", "MOMO"),
            Map.entry("蝦皮", "SHOPEE"),
            Map.entry("蝦皮購物", "SHOPEE"),
            Map.entry("SHOPEE", "SHOPEE"),
            Map.entry("麥當勞", "MCDONALD"),
            Map.entry("MCDONALD", "MCDONALD"),
            Map.entry("MCDONALDS", "MCDONALD"),
            Map.entry("星巴克", "STARBUCKS"),
            Map.entry("STARBUCKS", "STARBUCKS"),
            Map.entry("寶雅", "POYA"),
            Map.entry("POYA", "POYA"),
            Map.entry("AGODA", "AGODA"),
            Map.entry("壽司郎", "SUSHIRO"),
            Map.entry("台灣壽司郎", "SUSHIRO"),
            Map.entry("SUSHIRO", "SUSHIRO"),
            Map.entry("DONKI", "DON_DON_DONKI"),
            Map.entry("DON DON DONKI", "DON_DON_DONKI"),
            Map.entry("台灣DON DON DONKI", "DON_DON_DONKI"),
            Map.entry("GU", "GU"),
            Map.entry("UNIQLO", "UNIQLO"),
            Map.entry("GO TAXI", "GO_TAXI_JP"),
            Map.entry("GO TAXI APP", "GO_TAXI_JP"),
            Map.entry("日本GO TAXI APP", "GO_TAXI_JP"),
            Map.entry("桃園機場捷運", "TAOYUAN_AIRPORT_MRT"),
            Map.entry("機場捷運", "TAOYUAN_AIRPORT_MRT"),
            Map.entry("桃捷", "TAOYUAN_AIRPORT_MRT"),
            Map.entry("UBER EATS", "UBER_EATS"),
            Map.entry("UBEREATS", "UBER_EATS"),
            Map.entry("UBER_EATS", "UBER_EATS")
    );
    private static final Set<String> MOBILE_PAY_PLATFORM_VALUES = Set.of(
            "MOBILE_PAY",
            "LINE_PAY",
            "APPLE_PAY",
            "GOOGLE_PAY",
            "SAMSUNG_PAY",
            "JKOPAY",
            "ESUN_WALLET",
            "全支付",
            "街口支付",
            "悠遊付",
            "全盈_PAY",
            "IPASS_MONEY",
            "ICASH_PAY",
            "TWQR"
    );
    private static final String CATHAY_CUBE_CARD_CODE = "CATHAY_CUBE";
    private static final String ESUN_UNICARD_CARD_CODE = "ESUN_UNICARD";
    private static final String TAISHIN_RICHART_CARD_CODE = "TAISHIN_RICHART";
    private static final String UNICARD_SIMPLE_PLAN_ID = "ESUN_UNICARD_SIMPLE";
    private static final String UNICARD_FLEXIBLE_PLAN_ID = "ESUN_UNICARD_FLEXIBLE";
    private static final String UNICARD_UP_PLAN_ID = "ESUN_UNICARD_UP";
    private static final String UNICARD_HUNDRED_STORE_MARKER = "UNICARD_HUNDRED_STORE_CATALOG";
    private static final String CUBE_DEFAULT_TIER = "LEVEL_1";
    private static final String RICHART_DEFAULT_TIER = "LEVEL_1";
    private static final Set<String> CUBE_TIERED_PLAN_IDS = Set.of(
            "CATHAY_CUBE_DIGITAL",
            "CATHAY_CUBE_SHOPPING",
            "CATHAY_CUBE_TRAVEL"
    );
    private static final Set<String> RICHART_TIERED_PLAN_IDS = Set.of(
            "TAISHIN_RICHART_PAY",
            "TAISHIN_RICHART_DAILY",
            "TAISHIN_RICHART_BIG",
            "TAISHIN_RICHART_DINING",
            "TAISHIN_RICHART_DIGITAL",
            "TAISHIN_RICHART_TRAVEL",
            "TAISHIN_RICHART_WEEKEND"
    );

    private final PromotionRepository promotionRepository;
    private final RewardCalculator rewardCalculator;
    private final BenefitPlanRepository benefitPlanRepository;

    public RecommendationResponse recommend(RecommendationRequest request) {
        RecommendationScenario resolvedScenario = request.toResolvedScenario();
        LocalDate requestDate = resolvedScenario.getDate() != null ? resolvedScenario.getDate() : LocalDate.now();

        List<Promotion> activePromotions = promotionRepository.findActivePromotions(requestDate);
        Comparator<ScoredPromotion> promotionComparator = recommendationComparator();

        List<ScoredPromotion> scoredPromotions = activePromotions.stream()
                .map(promotion -> applyRuntimeAdjustments(promotion, request))
                .filter(promotion -> isEligible(promotion, request))
                .map(promotion -> toScoredPromotion(promotion, resolvedScenario.getAmount(), request))
                .filter(scored -> scored.cappedReturn() > 0)
                .sorted(promotionComparator)
                .toList();

        List<CardAggregate> rankedCards = scoredPromotions.stream()
                .collect(Collectors.groupingBy(this::distinctCardKey, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(promos -> toCardAggregate(promos, requestDate, request))
                .filter(Objects::nonNull)
                .sorted(cardAggregateComparator())
                .toList();

        List<CardRecommendation> recommendations = rankedCards.stream()
                .limit(request.getResolvedMaxResults())
                .map(cardAggregate -> toRecommendation(cardAggregate, request.shouldIncludePromotionBreakdown()))
                .toList();

        List<BreakEvenAnalysis> breakEvenAnalyses = request.shouldIncludeBreakEvenAnalysis()
                ? buildBreakEvenAnalyses(rankedCards, request)
                : List.of();

        return RecommendationResponse.builder()
                .requestId(java.util.UUID.randomUUID().toString())
                .scenario(resolvedScenario)
                .comparison(buildComparisonSummary(activePromotions.size(), scoredPromotions.size(), rankedCards.size(), breakEvenAnalyses))
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

        if (!isRecommendationScopeEligible(promotion, request)) {
            return false;
        }

        if (!isEligibilityTypeEligible(promotion, request)) {
            return false;
        }

        if (promotion.getCardStatus() != null && !"ACTIVE".equalsIgnoreCase(promotion.getCardStatus())) {
            return false;
        }

        boolean merchantMatchesVenue = hasMerchantMatchingVenueCondition(promotion, request);

        if (!merchantMatchesVenue) {
            if (promotion.getCategory() != null && !normalizeValue(promotion.getCategory()).equals(normalizeValue(category))) {
                return false;
            }

            String requestSubcategory = normalizeSubcategoryForMatching(request.getResolvedSubcategory());
            String promoSubcategory = normalizeSubcategoryForMatching(promotion.getSubcategory());
            boolean hasStrictSubcategory = !requestSubcategory.isBlank() && !"GENERAL".equals(requestSubcategory);

            if (hasStrictSubcategory) {
                if (!promoSubcategory.isBlank()
                        && !"GENERAL".equals(promoSubcategory)
                        && !requestSubcategory.equals(promoSubcategory)) {
                    return false;
                }
            } else if (!"GENERAL".equals(promoSubcategory) && !promoSubcategory.isBlank()) {
                return false;
            }
        }

        if (promotion.getMinAmount() != null && amount < promotion.getMinAmount()) {
            return false;
        }

        // GUARDRAIL: Block unbounded high fixed cashbacks from distorting rankings on small transactions.
        // Applies to both FIXED type and POINTS fixed-bonus (value >= 30, treated as fixed NTD by RewardCalculator).
        boolean isFixedReward = "FIXED".equalsIgnoreCase(promotion.getCashbackType());
        boolean isPointsFixedBonus = "POINTS".equalsIgnoreCase(promotion.getCashbackType())
                && promotion.getCashbackValue() != null
                && promotion.getCashbackValue().compareTo(RewardCalculator.POINTS_FIXED_BONUS_THRESHOLD) >= 0;

        if ((isFixedReward || isPointsFixedBonus) && promotion.getCashbackValue() != null) {
            boolean hasNoMinAmount = promotion.getMinAmount() == null || promotion.getMinAmount() == 0;
            // E.g., preventing a "200 NTD voucher" or "2000-point bonus" from being recommended
            // on a "50 NTD" transaction unless it's a platform-specific targeted transport reward
            if (hasNoMinAmount && promotion.getCashbackValue().intValue() >= 100 && promotion.getCashbackValue().intValue() > amount) {
                if (!"TRANSPORT".equalsIgnoreCase(normalizeValue(promotion.getCategory()))) {
                    return false;
                }
            }
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

        if (!matchesPlatformConditions(promotion, request)) {
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

    private boolean isRecommendationScopeEligible(Promotion promotion, RecommendationRequest request) {
        String recommendationScope = promotion.getRecommendationScope();
        if (recommendationScope == null
                || recommendationScope.isBlank()
                || "RECOMMENDABLE".equalsIgnoreCase(recommendationScope)) {
            return true;
        }

        return isRuntimeRecommendableCatalogPromotion(promotion, request);
    }

    private boolean isEligibilityTypeEligible(Promotion promotion, RecommendationRequest request) {
        String eligibilityType = promotion.getEligibilityType();
        if (eligibilityType == null || eligibilityType.isBlank() || "GENERAL".equalsIgnoreCase(eligibilityType)) {
            return true;
        }

        // If explicitly requesting this specific card in a comparison, bypass the eligibility filter
        if (request.getResolvedCardCodes() != null && !request.getResolvedCardCodes().isEmpty()) {
            boolean explicitlyRequested = request.getResolvedCardCodes().stream()
                    .map(this::normalizeValue)
                    .anyMatch(cardCode -> cardCode.equals(normalizeValue(promotion.getCardCode())));
            if (explicitlyRequested) {
                return true;
            }
        }

        // Otherwise, the user must explicitly be within the specified customer segment (e.g. "PROFESSION_SPECIFIC")
        RecommendationScenario resolvedScenario = request.toResolvedScenario();
        String userSegment = resolvedScenario != null ? resolvedScenario.getCustomerSegment() : null;
        if (userSegment != null && eligibilityType.equalsIgnoreCase(userSegment)) {
             return true;
        }

        return false;
    }

    private Promotion applyRuntimeAdjustments(Promotion promotion, RecommendationRequest request) {
        Promotion runtimeAdjustedPromotion = applyUnicardHundredStoreRuntimeAdjustments(promotion, request);
        if (runtimeAdjustedPromotion != promotion) {
            promotion = runtimeAdjustedPromotion;
        }

        runtimeAdjustedPromotion = applyCubeTierRuntimeAdjustments(promotion, request);
        if (runtimeAdjustedPromotion != promotion) {
            return runtimeAdjustedPromotion;
        }

        return applyRichartTierRuntimeAdjustments(promotion, request);
    }

    private Promotion applyCubeTierRuntimeAdjustments(Promotion promotion, RecommendationRequest request) {
        if (!CATHAY_CUBE_CARD_CODE.equals(normalizeValue(promotion.getCardCode()))) {
            return promotion;
        }

        String normalizedPlanId = normalizeValue(promotion.getPlanId());
        if (!CUBE_TIERED_PLAN_IDS.contains(normalizedPlanId)) {
            return promotion;
        }

        String normalizedTier = normalizeCubeTier(resolveCubeTier(request));
        BigDecimal adjustedRate = cubeTierRate(normalizedTier);
        if (adjustedRate == null || adjustedRate.compareTo(promotion.getCashbackValue()) == 0) {
            return promotion;
        }

        List<PromotionCondition> adjustedConditions = new ArrayList<>();
        if (promotion.getConditions() != null) {
            adjustedConditions.addAll(promotion.getConditions());
        }
        adjustedConditions.add(PromotionCondition.builder()
                .type("ASSUMED_BENEFIT_TIER")
                .value(normalizedTier)
                .label("依 CUBE 等級假設計算：" + normalizedTier)
                .build());

        return Promotion.builder()
                .promoId(promotion.getPromoId())
                .promoVersionId(promotion.getPromoVersionId())
                .title(promotion.getTitle())
                .cardCode(promotion.getCardCode())
                .cardName(promotion.getCardName())
                .cardStatus(promotion.getCardStatus())
                .annualFee(promotion.getAnnualFee())
                .applyUrl(promotion.getApplyUrl())
                .bankCode(promotion.getBankCode())
                .bankName(promotion.getBankName())
                .category(promotion.getCategory())
                .subcategory(promotion.getSubcategory())
                .channel(promotion.getChannel())
                .validFrom(promotion.getValidFrom())
                .validUntil(promotion.getValidUntil())
                .minAmount(promotion.getMinAmount())
                .cashbackType(promotion.getCashbackType())
                .cashbackValue(adjustedRate)
                .maxCashback(promotion.getMaxCashback())
                .frequencyLimit(promotion.getFrequencyLimit())
                .requiresRegistration(promotion.isRequiresRegistration())
                .recommendationScope(promotion.getRecommendationScope())
                .eligibilityType(promotion.getEligibilityType())
                .stackability(promotion.getStackability())
                .conditions(adjustedConditions)
                .excludedConditions(promotion.getExcludedConditions())
                .status(promotion.getStatus())
                .planId(promotion.getPlanId())
                .build();
    }

    private Promotion applyRichartTierRuntimeAdjustments(Promotion promotion, RecommendationRequest request) {
        if (!TAISHIN_RICHART_CARD_CODE.equals(normalizeValue(promotion.getCardCode()))) {
            return promotion;
        }

        String normalizedPlanId = normalizeValue(promotion.getPlanId());
        if (!RICHART_TIERED_PLAN_IDS.contains(normalizedPlanId)) {
            return promotion;
        }

        BigDecimal currentRate = promotion.getCashbackValue();
        if (currentRate == null) {
            return promotion;
        }

        String normalizedTier = normalizeRichartTier(resolveRichartTier(request));
        BigDecimal adjustedRate = richartAdjustedRate(normalizedPlanId, normalizedTier, currentRate);
        if (adjustedRate == null || adjustedRate.compareTo(currentRate) == 0) {
            return promotion;
        }

        List<PromotionCondition> adjustedConditions = new ArrayList<>();
        if (promotion.getConditions() != null) {
            adjustedConditions.addAll(promotion.getConditions());
        }
        adjustedConditions.add(PromotionCondition.builder()
                .type("ASSUMED_BENEFIT_TIER")
                .value(normalizedTier)
                .label("Assumed Richart benefit tier: " + normalizedTier)
                .build());

        return Promotion.builder()
                .promoId(promotion.getPromoId())
                .promoVersionId(promotion.getPromoVersionId())
                .title(promotion.getTitle())
                .cardCode(promotion.getCardCode())
                .cardName(promotion.getCardName())
                .cardStatus(promotion.getCardStatus())
                .annualFee(promotion.getAnnualFee())
                .applyUrl(promotion.getApplyUrl())
                .bankCode(promotion.getBankCode())
                .bankName(promotion.getBankName())
                .category(promotion.getCategory())
                .subcategory(promotion.getSubcategory())
                .channel(promotion.getChannel())
                .validFrom(promotion.getValidFrom())
                .validUntil(promotion.getValidUntil())
                .minAmount(promotion.getMinAmount())
                .cashbackType(promotion.getCashbackType())
                .cashbackValue(adjustedRate)
                .maxCashback(promotion.getMaxCashback())
                .frequencyLimit(promotion.getFrequencyLimit())
                .requiresRegistration(promotion.isRequiresRegistration())
                .recommendationScope(promotion.getRecommendationScope())
                .eligibilityType(promotion.getEligibilityType())
                .stackability(promotion.getStackability())
                .conditions(adjustedConditions)
                .excludedConditions(promotion.getExcludedConditions())
                .status(promotion.getStatus())
                .planId(promotion.getPlanId())
                .build();
    }

    private Promotion applyUnicardHundredStoreRuntimeAdjustments(Promotion promotion, RecommendationRequest request) {
        if (!isUnicardHundredStorePromotion(promotion)) {
            return promotion;
        }

        String activePlanId = normalizeValue(request.getResolvedActivePlanId(ESUN_UNICARD_CARD_CODE));
        if (activePlanId.isBlank()) {
            return promotion;
        }

        BigDecimal adjustedRate = unicardHundredStoreRate(activePlanId);
        if (adjustedRate == null) {
            return promotion;
        }

        List<PromotionCondition> adjustedConditions = new ArrayList<>();
        if (promotion.getConditions() != null) {
            adjustedConditions.addAll(promotion.getConditions());
        }
        adjustedConditions.add(PromotionCondition.builder()
                .type("ASSUMED_ACTIVE_PLAN")
                .value(activePlanId)
                .label("依目前方案計算：" + activePlanId)
                .build());

        if (UNICARD_FLEXIBLE_PLAN_ID.equals(activePlanId)) {
            List<String> selectedMerchants = getSelectedUnicardFlexibleMerchants(request);
            if (!selectedMerchants.isEmpty()) {
                adjustedConditions.add(PromotionCondition.builder()
                        .type("ASSUMED_SELECTED_MERCHANTS")
                        .value(String.join(",", selectedMerchants))
                        .label("任意選已選商家：" + String.join("、", selectedMerchants))
                        .build());
            }
        }

        return Promotion.builder()
                .promoId(promotion.getPromoId())
                .promoVersionId(promotion.getPromoVersionId())
                .title(promotion.getTitle())
                .cardCode(promotion.getCardCode())
                .cardName(promotion.getCardName())
                .cardStatus(promotion.getCardStatus())
                .annualFee(promotion.getAnnualFee())
                .applyUrl(promotion.getApplyUrl())
                .bankCode(promotion.getBankCode())
                .bankName(promotion.getBankName())
                .category(promotion.getCategory())
                .subcategory(promotion.getSubcategory())
                .channel(promotion.getChannel())
                .validFrom(promotion.getValidFrom())
                .validUntil(promotion.getValidUntil())
                .minAmount(promotion.getMinAmount())
                .cashbackType(promotion.getCashbackType())
                .cashbackValue(adjustedRate)
                .maxCashback(promotion.getMaxCashback())
                .frequencyLimit(promotion.getFrequencyLimit())
                .requiresRegistration(promotion.isRequiresRegistration())
                .recommendationScope("RECOMMENDABLE")
                .eligibilityType(promotion.getEligibilityType())
                .stackability(promotion.getStackability())
                .conditions(adjustedConditions)
                .excludedConditions(promotion.getExcludedConditions())
                .status(promotion.getStatus())
                .planId(activePlanId)
                .build();
    }

    private String normalizeCubeTier(String rawTier) {
        String normalized = normalizeValue(rawTier);
        if ("LEVEL_2".equals(normalized) || "L2".equals(normalized)) {
            return "LEVEL_2";
        }
        if ("LEVEL_3".equals(normalized) || "L3".equals(normalized)) {
            return "LEVEL_3";
        }
        return CUBE_DEFAULT_TIER;
    }

    private String resolveCubeTier(RecommendationRequest request) {
        String runtimeTier = request.getResolvedPlanRuntimeValue(CATHAY_CUBE_CARD_CODE, "TIER");
        if (runtimeTier != null && !runtimeTier.isBlank()) {
            return runtimeTier;
        }
        return request.getResolvedBenefitPlanTier(CATHAY_CUBE_CARD_CODE);
    }

    private String normalizeRichartTier(String rawTier) {
        String normalized = normalizeValue(rawTier);
        if ("LEVEL_2".equals(normalized) || "L2".equals(normalized)) {
            return "LEVEL_2";
        }
        return RICHART_DEFAULT_TIER;
    }

    private String resolveRichartTier(RecommendationRequest request) {
        String runtimeTier = request.getResolvedPlanRuntimeValue(TAISHIN_RICHART_CARD_CODE, "TIER");
        if (runtimeTier != null && !runtimeTier.isBlank()) {
            return runtimeTier;
        }
        return request.getResolvedBenefitPlanTier(TAISHIN_RICHART_CARD_CODE);
    }

    private BigDecimal cubeTierRate(String normalizedTier) {
        return switch (normalizedTier) {
            case "LEVEL_2" -> BigDecimal.valueOf(3.0);
            case "LEVEL_3" -> BigDecimal.valueOf(3.3);
            default -> BigDecimal.valueOf(2.0);
        };
    }

    private BigDecimal richartAdjustedRate(String planId, String normalizedTier, BigDecimal currentRate) {
        BigDecimal standardLevel1Rate = BigDecimal.valueOf(1.3);
        BigDecimal standardLevel2Rate = switch (planId) {
            case "TAISHIN_RICHART_PAY" -> null;
            case "TAISHIN_RICHART_WEEKEND" -> BigDecimal.valueOf(2.0);
            default -> BigDecimal.valueOf(3.3);
        };

        if ("TAISHIN_RICHART_PAY".equals(planId)) {
            if (currentRate.compareTo(BigDecimal.valueOf(3.8)) == 0 || currentRate.compareTo(BigDecimal.valueOf(2.3)) == 0) {
                return "LEVEL_2".equals(normalizedTier) ? currentRate : standardLevel1Rate;
            }
            if (currentRate.compareTo(standardLevel1Rate) == 0) {
                return currentRate;
            }
            return null;
        }

        if (currentRate.compareTo(standardLevel1Rate) == 0) {
            return currentRate;
        }
        if (standardLevel2Rate != null && currentRate.compareTo(standardLevel2Rate) == 0) {
            return "LEVEL_2".equals(normalizedTier) ? standardLevel2Rate : standardLevel1Rate;
        }
        return null;
    }

    private BigDecimal unicardHundredStoreRate(String activePlanId) {
        return switch (activePlanId) {
            case UNICARD_SIMPLE_PLAN_ID -> BigDecimal.valueOf(3.0);
            case UNICARD_FLEXIBLE_PLAN_ID -> BigDecimal.valueOf(3.5);
            case UNICARD_UP_PLAN_ID -> BigDecimal.valueOf(4.5);
            default -> null;
        };
    }

    private boolean isRuntimeRecommendableCatalogPromotion(Promotion promotion, RecommendationRequest request) {
        if (!isUnicardHundredStorePromotion(promotion)) {
            return false;
        }

        String activePlanId = normalizeValue(request.getResolvedActivePlanId(ESUN_UNICARD_CARD_CODE));
        if (activePlanId.isBlank()) {
            return false;
        }

        if (!List.of(UNICARD_SIMPLE_PLAN_ID, UNICARD_FLEXIBLE_PLAN_ID, UNICARD_UP_PLAN_ID).contains(activePlanId)) {
            return false;
        }

        if (!UNICARD_FLEXIBLE_PLAN_ID.equals(activePlanId)) {
            return true;
        }

        return hasMatchingSelectedFlexibleMerchant(promotion, request);
    }

    private boolean hasMatchingSelectedFlexibleMerchant(Promotion promotion, RecommendationRequest request) {
        List<String> selectedMerchants = getSelectedUnicardFlexibleMerchants(request);
        if (selectedMerchants.isEmpty()) {
            return false;
        }

        List<String> merchantTokens = getNormalizedConditionTokens(promotion, MERCHANT_CONDITION_TYPES);
        return merchantTokens.stream().anyMatch(selectedMerchants::contains);
    }

    private List<String> getSelectedUnicardFlexibleMerchants(RecommendationRequest request) {
        String rawSelectedMerchants = request.getResolvedPlanRuntimeValue(ESUN_UNICARD_CARD_CODE, "SELECTED_MERCHANTS");
        if (rawSelectedMerchants == null || rawSelectedMerchants.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(rawSelectedMerchants.split("[,\\n]"))
                .map(this::normalizeValue)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private boolean isUnicardHundredStorePromotion(Promotion promotion) {
        return ESUN_UNICARD_CARD_CODE.equals(normalizeValue(promotion.getCardCode()))
                && getNormalizedConditionValues(promotion, Set.of("TEXT")).stream()
                .anyMatch(UNICARD_HUNDRED_STORE_MARKER::equals);
    }

    private ScoredPromotion toScoredPromotion(Promotion promotion, Integer amount, RecommendationRequest request) {
        RewardCalculator.RewardCalculationResult result = rewardCalculator.calculateReward(promotion, amount, request.getCustomExchangeRates());
        return new ScoredPromotion(promotion, result.getEstimatedReturn(), result.getCappedReturn(), result.getRewardDetail());
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

    private CardAggregate toCardAggregate(List<ScoredPromotion> promotions, LocalDate requestDate, RecommendationRequest request) {
        List<String> notes = new ArrayList<>();

        // Resolve best plan before stack resolution
        PlanResolution planResolution = resolveBestPlan(promotions, requestDate, request);
        List<ScoredPromotion> effectivePromotions = planResolution.promotions();
        if (effectivePromotions.isEmpty()) {
            return null;
        }

        StackResolution resolution = resolveContributingPromotions(effectivePromotions);
        List<ScoredPromotion> contributingPromotions = resolution.contributingPromotions();
        if (contributingPromotions.isEmpty()) {
            return null;
        }

        if (requiresStrictSceneMatch(request)
                && effectivePromotions.stream().map(ScoredPromotion::promotion).noneMatch(promotion -> isStrictSceneMatch(promotion, request))) {
            return null;
        }
        notes.addAll(resolution.notes());

        if (planResolution.winningPlan() != null) {
            notes.add(String.format("推薦切換至「%s」方案以獲得最高回饋。", planResolution.winningPlan().getPlanName()));
        }

        ScoredPromotion primary = contributingPromotions.get(0);
        int totalReturn = contributingPromotions.stream().mapToInt(ScoredPromotion::cappedReturn).sum();

        return new CardAggregate(primary.promotion(), promotions, contributingPromotions, totalReturn, notes, planResolution.winningPlan());
    }

    private boolean requiresStrictSceneMatch(RecommendationRequest request) {
        return !normalizeSubcategoryForMatching(request.getResolvedSubcategory()).isBlank()
                && !"GENERAL".equals(normalizeSubcategoryForMatching(request.getResolvedSubcategory()))
                || !normalizeValue(request.getResolvedMerchantName()).isBlank()
                || !normalizeValue(request.getResolvedPaymentMethod()).isBlank();
    }

    private boolean isStrictSceneMatch(Promotion promotion, RecommendationRequest request) {
        String normalizedMerchant = normalizeValue(request.getResolvedMerchantName());
        String normalizedPaymentMethod = normalizeValue(request.getResolvedPaymentMethod());
        String requestSubcategory = normalizeSubcategoryForMatching(request.getResolvedSubcategory());
        boolean hasMerchantOrPaymentConstraint = !normalizedMerchant.isBlank() || !normalizedPaymentMethod.isBlank();

        if (!normalizedMerchant.isBlank() && !hasMatchingMerchantCondition(promotion, normalizedMerchant)) {
            return false;
        }

        if (!normalizedPaymentMethod.isBlank() && !hasMatchingPaymentCondition(promotion, normalizedPaymentMethod, normalizedMerchant)) {
            return false;
        }

        if (!hasMerchantOrPaymentConstraint && !requestSubcategory.isBlank() && !"GENERAL".equals(requestSubcategory)) {
            String promoSubcategory = normalizeSubcategoryForMatching(promotion.getSubcategory());
            return requestSubcategory.equals(promoSubcategory);
        }

        return true;
    }

    private boolean hasMatchingMerchantCondition(Promotion promotion, String normalizedMerchant) {
        return getNormalizedConditionTokens(promotion, MERCHANT_CONDITION_TYPES).stream()
                .anyMatch(normalizedMerchant::equals);
    }

    private boolean hasMatchingPaymentCondition(Promotion promotion, String normalizedPaymentMethod, String normalizedMerchant) {
        Set<String> normalizedPaymentMethods = expandPaymentMethods(normalizedPaymentMethod);
        if (!normalizedMerchant.isBlank()) {
            normalizedPaymentMethods.add(normalizedMerchant);
        }
        if (normalizedPaymentMethods.isEmpty()) {
            return false;
        }

        return getNormalizedConditionValues(promotion, PAYMENT_CONDITION_TYPES).stream()
                .anyMatch(normalizedPaymentMethods::contains);
    }

    private PlanResolution resolveBestPlan(List<ScoredPromotion> promotions, LocalDate requestDate, RecommendationRequest request) {
        List<ScoredPromotion> traditional = promotions.stream()
                .filter(sp -> sp.promotion().getPlanId() == null || sp.promotion().getPlanId().isBlank())
                .toList();

        String cardCode = promotions.isEmpty() ? null : promotions.get(0).promotion().getCardCode();
        BenefitPlan requestedActivePlan = resolveRequestedActivePlan(cardCode, requestDate, request);

        List<ScoredPromotion> planBound = promotions.stream()
                .filter(sp -> sp.promotion().getPlanId() != null && !sp.promotion().getPlanId().isBlank())
                .filter(sp -> {
                    BenefitPlan plan = benefitPlanRepository.findByPlanId(sp.promotion().getPlanId());
                    return isPlanActiveOn(plan, requestDate);
                })
                .toList();

        if (planBound.isEmpty()) {
            return new PlanResolution(traditional, requestedActivePlan);
        }

        if (requestedActivePlan != null) {
            String requestedPlanId = normalizeValue(requestedActivePlan.getPlanId());
            List<ScoredPromotion> selectedPlanPromotions = planBound.stream()
                    .filter(sp -> requestedPlanId.equals(normalizeValue(sp.promotion().getPlanId())))
                    .toList();

            List<ScoredPromotion> combined = new ArrayList<>(traditional);
            combined.addAll(selectedPlanPromotions);
            return new PlanResolution(combined, requestedActivePlan);
        }

        // Group plan-bound promotions by exclusiveGroup, then by planId within each group
        // Pick the planId with the highest total return per exclusive group
        record PlanGroup(String exclusiveGroup, String planId, List<ScoredPromotion> promotions, int totalReturn) {}

        java.util.Map<String, List<PlanGroup>> groupsByExclusive = planBound.stream()
                .collect(Collectors.groupingBy(sp -> {
                    BenefitPlan plan = benefitPlanRepository.findByPlanId(sp.promotion().getPlanId());
                    return plan != null ? plan.getExclusiveGroup() : "__NONE__";
                }))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .collect(Collectors.groupingBy(sp -> normalizeValue(sp.promotion().getPlanId())))
                                .entrySet().stream()
                                .map(planEntry -> new PlanGroup(
                                        entry.getKey(),
                                        planEntry.getKey(),
                                        planEntry.getValue(),
                                        planEntry.getValue().stream().mapToInt(ScoredPromotion::cappedReturn).sum()))
                                .toList()
                ));

        List<ScoredPromotion> winningPlanPromotions = new ArrayList<>();
        BenefitPlan winningPlan = null;
        int winningPlanReturn = Integer.MIN_VALUE;

        for (var entry : groupsByExclusive.entrySet()) {
            List<PlanGroup> planGroups = entry.getValue();
            PlanGroup best = planGroups.stream()
                    .max(Comparator.comparingInt(PlanGroup::totalReturn))
                    .orElse(null);
            if (best != null) {
                winningPlanPromotions.addAll(best.promotions());
                String originalPlanId = best.promotions().get(0).promotion().getPlanId();
                BenefitPlan candidate = benefitPlanRepository.findByPlanId(originalPlanId);
                if (candidate != null && best.totalReturn() > winningPlanReturn) {
                    winningPlan = candidate;
                    winningPlanReturn = best.totalReturn();
                }
            }
        }

        List<ScoredPromotion> combined = new ArrayList<>(traditional);
        combined.addAll(winningPlanPromotions);

        return new PlanResolution(combined, winningPlan);
    }

    private BenefitPlan resolveRequestedActivePlan(String cardCode, LocalDate requestDate, RecommendationRequest request) {
        String requestedPlanId = request.getResolvedActivePlanId(cardCode);
        if (requestedPlanId == null || requestedPlanId.isBlank()) {
            return null;
        }

        BenefitPlan requestedPlan = benefitPlanRepository.findByPlanId(requestedPlanId);
        if (requestedPlan == null || !isPlanActiveOn(requestedPlan, requestDate)) {
            return null;
        }

        if (!normalizeValue(cardCode).equals(normalizeValue(requestedPlan.getCardCode()))) {
            return null;
        }

        return requestedPlan;
    }

    private boolean isPlanActiveOn(BenefitPlan plan, LocalDate requestDate) {
        if (plan == null) {
            return false;
        }

        if (plan.getStatus() != null && !"ACTIVE".equalsIgnoreCase(plan.getStatus())) {
            return false;
        }

        if (requestDate == null) {
            return true;
        }

        if (plan.getValidFrom() != null && requestDate.isBefore(plan.getValidFrom())) {
            return false;
        }

        return plan.getValidUntil() == null || !requestDate.isAfter(plan.getValidUntil());
    }

    private record PlanResolution(
            List<ScoredPromotion> promotions,
            BenefitPlan winningPlan
    ) {}

    // Maximum promotions to consider in bitmask combination search.
    // Keeps search cost at O(2^MAX_BITMASK_SIZE) = O(32) worst case.
    // Candidates are pre-sorted by cappedReturn so the top value-bearing promotions
    // are always evaluated, even when a card has more than this many eligible promotions.
    private static final int MAX_BITMASK_SIZE = 5;

    private StackResolution resolveContributingPromotions(List<ScoredPromotion> promotions) {
        if (promotions.isEmpty()) {
            return new StackResolution(List.of(), List.of());
        }
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

    private String normalizeSubcategoryForMatching(String subcategory) {
        String normalized = normalizeValue(subcategory);
        return "MOBILE_PAY".equals(normalized) ? "GENERAL" : normalized;
    }

    private CardRecommendation toRecommendation(CardAggregate cardAggregate, boolean includePromotionBreakdown) {
        Promotion promotion = cardAggregate.primaryPromotion();
        List<PromotionCondition> recommendationConditions = buildRecommendationConditions(promotion);
        String cashbackValueText = promotion.getCashbackValue() == null
                ? "0"
                : promotion.getCashbackValue().stripTrailingZeros().toPlainString();
        String reason = cardAggregate.contributingPromotions().size() > 1
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
                ? buildPromotionBreakdown(cardAggregate)
                : List.of();

        return CardRecommendation.builder()
                .cardCode(promotion.getCardCode())
                .cardName(promotion.getCardName())
                .bankCode(promotion.getBankCode())
                .bankName(promotion.getBankName())
                .subcategory(promotion.getSubcategory())
                .cashbackType(promotion.getCashbackType())
                .cashbackValue(promotion.getCashbackValue())
                .estimatedReturn(cardAggregate.totalReturn())
                .matchedPromotionCount(cardAggregate.allEligiblePromotions().size())
                .reason(reason)
                .promotionId(promotion.getPromoId())
                .promoVersionId(promotion.getPromoVersionId())
                .validUntil(promotion.getValidUntil())
                .conditions(recommendationConditions)
                .promotionBreakdown(breakdown)
                .applyUrl(promotion.getApplyUrl())
                .activePlan(buildActivePlan(cardAggregate.winningPlan()))
                .generalRewardOnly(isGeneralRewardOnly(cardAggregate))
                .rewardDetail(cardAggregate.contributingPromotions().get(0).rewardDetail())
                .build();
    }

    private boolean isGeneralRewardOnly(CardAggregate cardAggregate) {
        return cardAggregate.allEligiblePromotions().stream()
                .map(ScoredPromotion::promotion)
                .allMatch(this::isGeneralRewardPromotion);
    }

    private boolean isGeneralRewardPromotion(Promotion promotion) {
        String subcategory = normalizeSubcategoryForMatching(promotion.getSubcategory());
        if (!subcategory.isBlank() && !"GENERAL".equals(subcategory)) {
            return false;
        }

        if (promotion.getConditions() == null) {
            return true;
        }

        return promotion.getConditions().stream()
                .map(PromotionCondition::getType)
                .map(this::normalizeValue)
                .noneMatch(type -> MERCHANT_CONDITION_TYPES.contains(type));
    }

    private ActivePlan buildActivePlan(BenefitPlan plan) {
        if (plan == null) {
            return null;
        }
        String frequencyText = switch (plan.getSwitchFrequency().toUpperCase()) {
            case "DAILY" -> "每天可切換1次";
            case "MONTHLY" -> plan.getSwitchMaxPerMonth() != null
                    ? String.format("每月最多切換%d次", plan.getSwitchMaxPerMonth())
                    : "每月可切換";
            case "UNLIMITED" -> "不限次數";
            default -> plan.getSwitchFrequency();
        };
        return ActivePlan.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .switchRequired(true)
                .switchFrequency(frequencyText)
                .requiresSubscription(plan.isRequiresSubscription())
                .subscriptionCost(plan.getSubscriptionCost())
                .build();
    }

    private List<PromotionRewardBreakdown> buildPromotionBreakdown(CardAggregate cardAggregate) {
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
                            .reason(buildBreakdownReason(cardAggregate, scoredPromotion, contributes))
                            .rewardDetail(scoredPromotion.rewardDetail())
                            .build();
                })
                .toList();
    }

                    private String buildBreakdownReason(
                        CardAggregate cardAggregate,
                        ScoredPromotion scoredPromotion,
                        boolean contributes
                    ) {
                    if (cardAggregate.contributingPromotions().size() > 1) {
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

    private boolean hasMerchantMatchingVenueCondition(Promotion promotion, RecommendationRequest request) {
        Set<String> merchantTokens = expandMerchantTokens(request.getResolvedMerchantName());
        if (merchantTokens.isEmpty()) {
            return false;
        }
        List<String> conditionTokens = getNormalizedConditionTokens(promotion, MERCHANT_CONDITION_TYPES);
        return !conditionTokens.isEmpty() && conditionTokens.stream().anyMatch(merchantTokens::contains);
    }

    private boolean matchesPlatformConditions(Promotion promotion, RecommendationRequest request) {
        Set<String> normalizedMerchantTokens = expandMerchantTokens(request.getResolvedMerchantName());
        List<String> merchantValues = getNormalizedConditionTokens(promotion, MERCHANT_CONDITION_TYPES);
        if (!merchantValues.isEmpty() && (normalizedMerchantTokens.isEmpty() || merchantValues.stream().noneMatch(normalizedMerchantTokens::contains))) {
            return false;
        }

        List<String> paymentValues = getNormalizedConditionValues(promotion, PAYMENT_CONDITION_TYPES);
        if (paymentValues.isEmpty()) {
            return true;
        }

        Set<String> normalizedPaymentMethods = expandPaymentMethods(request.getResolvedPaymentMethod());
        normalizedPaymentMethods.addAll(normalizedMerchantTokens);
        if (normalizedPaymentMethods.isEmpty()) {
            return false;
        }

        return paymentValues.stream().anyMatch(normalizedPaymentMethods::contains);
    }

    private Set<String> expandMerchantTokens(String merchantName) {
        String normalizedMerchant = normalizeValue(merchantName);
        if (normalizedMerchant.isBlank()) {
            return new HashSet<>();
        }

        Set<String> values = new HashSet<>();
        values.add(normalizedMerchant);

        String canonical = HIGH_FREQUENCY_MERCHANT_ALIASES.get(normalizedMerchant);
        if (canonical != null && !canonical.isBlank()) {
            values.add(canonical);
        }

        return values;
    }

    private List<String> getNormalizedConditionValues(Promotion promotion, Set<String> allowedTypes) {
        List<PromotionCondition> conditions = promotion.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }

        return conditions.stream()
                .filter(condition -> allowedTypes.contains(normalizeValue(condition.getType())))
                .map(PromotionCondition::getValue)
                .map(this::normalizeValue)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> getNormalizedConditionTokens(Promotion promotion, Set<String> allowedTypes) {
        List<PromotionCondition> conditions = promotion.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }

        return conditions.stream()
                .filter(condition -> allowedTypes.contains(normalizeValue(condition.getType())))
                .flatMap(condition -> java.util.stream.Stream.of(condition.getValue(), condition.getLabel()))
                .map(this::normalizeValue)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private Set<String> expandPaymentMethods(String paymentMethod) {
        String normalizedPaymentMethod = normalizeValue(paymentMethod);
        if (normalizedPaymentMethod.isBlank()) {
            return new HashSet<>();
        }

        Set<String> values = new HashSet<>();
        values.add(normalizedPaymentMethod);
        if (MOBILE_PAY_PLATFORM_VALUES.contains(normalizedPaymentMethod)) {
            values.add("MOBILE_PAY");
        }
        return values;
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
            int evaluatedPromotionCount,
            int eligiblePromotionCount,
            int rankedCardCount,
            List<BreakEvenAnalysis> breakEvenAnalyses
    ) {
        List<String> notes = new ArrayList<>();
        notes.add("多優惠並存模式已由 promotion.stackability 顯式 metadata 控制；未標註 metadata 的舊資料不得直接視為可並存。");
        if (breakEvenAnalyses.isEmpty()) {
            notes.add("本次比較沒有可計算的 FIXED vs PERCENT/POINTS break-even pair，或呼叫端未要求輸出 break-even 分析。");
        }

        return RecommendationComparisonSummary.builder()
                .mode("STACK_ALL_ELIGIBLE")
                .evaluatedPromotionCount(evaluatedPromotionCount)
                .eligiblePromotionCount(eligiblePromotionCount)
                .rankedCardCount(rankedCardCount)
                .breakEvenEvaluated(!breakEvenAnalyses.isEmpty())
                .breakEvenAnalyses(breakEvenAnalyses)
                .notes(notes)
                .build();
    }

    private List<BreakEvenAnalysis> buildBreakEvenAnalyses(List<CardAggregate> rankedCards, RecommendationRequest request) {
        List<BreakEvenAnalysis> analyses = new ArrayList<>();
        for (int index = 0; index < rankedCards.size() - 1; index++) {
            Optional<BreakEvenAnalysis> maybeAnalysis = buildBreakEvenAnalysis(rankedCards.get(index), rankedCards.get(index + 1), request);
            maybeAnalysis.ifPresent(analyses::add);
            if (analyses.size() >= 3) {
                break;
            }
        }
        return analyses;
    }

    private Optional<BreakEvenAnalysis> buildBreakEvenAnalysis(CardAggregate left, CardAggregate right, RecommendationRequest request) {
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

        Integer breakEvenAmount = rewardCalculator.calculateBreakEvenAmount(fixedPromotion, variablePromotion, request.getCustomExchangeRates());
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
                || "POINTS".equalsIgnoreCase(promotion.getCashbackType())
                || "MILES".equalsIgnoreCase(promotion.getCashbackType()));
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
            List<String> notes,
            BenefitPlan winningPlan
    ) {
    }

        private record StackResolution(
            List<ScoredPromotion> contributingPromotions,
            List<String> notes
        ) {
        }
}
