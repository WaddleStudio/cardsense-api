package com.cardsense.api.service;

import com.cardsense.api.domain.BankSummary;
import com.cardsense.api.domain.CardSummary;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final PromotionRepository promotionRepository;

    public List<CardSummary> listCards(String bank, String status, String scope, String eligibilityType) {
        return promotionsByCard().values().stream()
                .map(this::toCardSummary)
                .filter(card -> matchesBank(card, bank))
                .filter(card -> matchesStatus(card.getCardStatus(), status))
                .filter(card -> matchesScope(card, scope))
                .filter(card -> matchesEligibilityType(card, eligibilityType))
                .sorted(Comparator.comparing(CardSummary::getBankCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(CardSummary::getCardCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    public List<Promotion> listCardPromotions(String cardCode) {
        return promotionRepository.findPromotionsByCardCode(cardCode, LocalDate.now());
    }

    public List<BankSummary> listBanks() {
        return promotionRepository.findAllPromotions().stream()
                .collect(LinkedHashMap<String, Promotion>::new,
                        (map, promotion) -> map.putIfAbsent(promotion.getBankCode(), promotion),
                        Map::putAll)
                .values().stream()
                .map(this::toBankSummary)
                .sorted(Comparator.comparing(BankSummary::getCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Map<String, List<Promotion>> promotionsByCard() {
        return promotionRepository.findAllPromotions().stream()
                .collect(LinkedHashMap<String, List<Promotion>>::new,
                        (map, promotion) -> map.computeIfAbsent(cardKey(promotion), ignored -> new java.util.ArrayList<>()).add(promotion),
                        Map::putAll);
    }

    private CardSummary toCardSummary(List<Promotion> promotions) {
        Promotion promotion = promotions.get(0);
        List<String> scopes = resolveCardScopes(promotions);
        long recommendableCount = promotions.stream()
                .map(Promotion::getRecommendationScope)
                .map(this::normalizeScope)
                .filter("RECOMMENDABLE"::equals)
                .count();
        long catalogOnlyCount = promotions.stream()
                .map(Promotion::getRecommendationScope)
                .map(this::normalizeScope)
                .filter("CATALOG_ONLY"::equals)
                .count();
        long futureScopeCount = promotions.stream()
                .map(Promotion::getRecommendationScope)
                .map(this::normalizeScope)
                .filter("FUTURE_SCOPE"::equals)
                .count();

        List<String> categories = promotions.stream()
                .map(Promotion::getCategory)
                .filter(cat -> cat != null && !cat.isBlank())
                .map(cat -> cat.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();

        return CardSummary.builder()
                .cardCode(promotion.getCardCode())
                .cardName(promotion.getCardName())
                .cardStatus(promotion.getCardStatus())
                .annualFee(promotion.getAnnualFee())
                .applyUrl(promotion.getApplyUrl())
                .bankCode(promotion.getBankCode())
                .bankName(promotion.getBankName())
                .recommendationScopes(List.copyOf(scopes))
                .eligibilityType(resolveEligibilityType(promotions))
                .availableCategories(categories)
                .hasBenefitPlans(promotions.stream().anyMatch(p -> p.getPlanId() != null && !p.getPlanId().isBlank()))
                .totalPromotionCount(promotions.size())
                .recommendablePromotionCount((int) recommendableCount)
                .catalogOnlyPromotionCount((int) catalogOnlyCount)
                .futureScopePromotionCount((int) futureScopeCount)
                .generalRewardsOnly(isGeneralRewardsOnly(promotions))
                .sparsePromotionCard(isSparsePromotionCard(promotions, recommendableCount))
                .coBrandCard(isCoBrandCard(promotion))
                .catalogReviewHint(buildCatalogReviewHint(promotions))
                .build();
    }

    private boolean isGeneralRewardsOnly(List<Promotion> promotions) {
        List<Promotion> recommendablePromotions = promotions.stream()
                .filter(promotion -> "RECOMMENDABLE".equals(normalizeScope(promotion.getRecommendationScope())))
                .toList();

        if (recommendablePromotions.isEmpty()) {
            return false;
        }

        return recommendablePromotions.stream().allMatch(this::isGeneralPromotion);
    }

    private boolean isGeneralPromotion(Promotion promotion) {
        String subcategory = promotion.getSubcategory();
        if (subcategory != null && !subcategory.isBlank() && !"GENERAL".equalsIgnoreCase(subcategory)) {
            return false;
        }

        return promotion.getConditions() == null || promotion.getConditions().stream()
                .noneMatch(condition -> Set.of("MERCHANT", "RETAIL_CHAIN", "ECOMMERCE_PLATFORM").contains(
                        condition.getType() == null ? "" : condition.getType().trim().toUpperCase(Locale.ROOT)
                ));
    }

    private boolean isSparsePromotionCard(List<Promotion> promotions, long recommendableCount) {
        return promotions.size() <= 2 || recommendableCount <= 1;
    }

    private boolean isCoBrandCard(Promotion promotion) {
        String cardName = promotion.getCardName();
        if (cardName == null || cardName.isBlank()) {
            return false;
        }
        String normalized = cardName.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("聯名") || normalized.contains("COBRAND") || normalized.contains("CO-BRAND");
    }

    private String buildCatalogReviewHint(List<Promotion> promotions) {
        boolean hasCatalogOnly = promotions.stream()
                .anyMatch(promotion -> "CATALOG_ONLY".equals(normalizeScope(promotion.getRecommendationScope())));
        if (!hasCatalogOnly) {
            return null;
        }

        boolean registrationHeavy = promotions.stream()
                .filter(promotion -> "CATALOG_ONLY".equals(normalizeScope(promotion.getRecommendationScope())))
                .anyMatch(Promotion::isRequiresRegistration);
        boolean planSwitchHeavy = promotions.stream()
                .filter(promotion -> "CATALOG_ONLY".equals(normalizeScope(promotion.getRecommendationScope())))
                .anyMatch(promotion -> promotion.getPlanId() != null && !promotion.getPlanId().isBlank());
        boolean allGeneralRecommendable = isGeneralRewardsOnly(promotions);

        if (registrationHeavy && planSwitchHeavy) {
            return "部分優惠需先登錄，且實際回饋會受方案切換影響。";
        }
        if (registrationHeavy) {
            return "部分優惠需先完成登錄，暫時保留在目錄展示。";
        }
        if (planSwitchHeavy) {
            return "部分優惠需依當前方案或權益切換判斷，暫時保留在目錄展示。";
        }
        if (allGeneralRecommendable) {
            return "目前可進榜的內容以通用回饋為主，專屬場景優惠仍待補全。";
        }

        Set<String> catalogCategories = promotions.stream()
                .filter(promotion -> "CATALOG_ONLY".equals(normalizeScope(promotion.getRecommendationScope())))
                .map(Promotion::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .map(category -> category.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!catalogCategories.isEmpty()) {
            return "仍有待審查的目錄型優惠：" + String.join(" / ", catalogCategories);
        }
        return "仍有部分優惠保留在目錄展示，待進一步審查。";
    }

    private List<String> resolveCardScopes(List<Promotion> promotions) {
        Set<String> scopes = promotions.stream()
                .map(Promotion::getRecommendationScope)
                .map(this::normalizeScope)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (scopes.contains("RECOMMENDABLE")) {
            return List.of("RECOMMENDABLE");
        }
        if (scopes.contains("CATALOG_ONLY")) {
            return List.of("CATALOG_ONLY");
        }
        if (scopes.contains("FUTURE_SCOPE")) {
            return List.of("FUTURE_SCOPE");
        }
        return List.of("RECOMMENDABLE");
    }

    private String resolveEligibilityType(List<Promotion> promotions) {
        Set<String> types = promotions.stream()
                .map(Promotion::getEligibilityType)
                .map(this::normalizeEligibilityType)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (types.contains("BUSINESS")) {
            return "BUSINESS";
        }
        if (types.contains("PROFESSION_SPECIFIC")) {
            return "PROFESSION_SPECIFIC";
        }
        return "GENERAL";
    }

    private String cardKey(Promotion promotion) {
        if (promotion.getCardCode() != null && !promotion.getCardCode().isBlank()) {
            return promotion.getCardCode();
        }

        return (promotion.getBankCode() == null ? "" : promotion.getBankCode())
                + ":"
                + (promotion.getCardName() == null ? "" : promotion.getCardName());
    }

    private BankSummary toBankSummary(Promotion promotion) {
        return BankSummary.builder()
                .code(promotion.getBankCode())
                .nameZh(promotion.getBankName())
                .nameEn(promotion.getBankCode() == null ? null : promotion.getBankCode().toUpperCase(Locale.ROOT))
                .build();
    }

    private boolean matchesBank(CardSummary card, String bank) {
        if (bank == null || bank.isBlank()) {
            return true;
        }

        return bank.equalsIgnoreCase(card.getBankCode()) || bank.equalsIgnoreCase(card.getBankName());
    }

    private boolean matchesStatus(String actualStatus, String requestedStatus) {
        String normalizedRequestedStatus = normalizeStatus(requestedStatus);
        String normalizedActualStatus = normalizeStatus(actualStatus);
        return normalizedRequestedStatus.equalsIgnoreCase(normalizedActualStatus);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INACTIVE", "DISCONTINUED", "STOPPED" -> "DISCONTINUED";
            default -> "ACTIVE";
        };
    }

    private boolean matchesScope(CardSummary card, String requestedScope) {
        if (requestedScope == null || requestedScope.isBlank()) {
            return true;
        }

        String normalizedRequestedScope = normalizeScope(requestedScope);
        return card.getRecommendationScopes() != null
                && card.getRecommendationScopes().stream().anyMatch(normalizedRequestedScope::equalsIgnoreCase);
    }

    private boolean matchesEligibilityType(CardSummary card, String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return true;
        }
        return requestedType.equalsIgnoreCase(card.getEligibilityType());
    }

    private String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "RECOMMENDABLE" : scope.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEligibilityType(String eligibilityType) {
        return eligibilityType == null || eligibilityType.isBlank()
                ? "GENERAL"
                : eligibilityType.trim().toUpperCase(Locale.ROOT);
    }
}
