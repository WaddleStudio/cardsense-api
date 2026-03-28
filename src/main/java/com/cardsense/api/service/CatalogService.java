package com.cardsense.api.service;

import com.cardsense.api.domain.BankSummary;
import com.cardsense.api.domain.CardSummary;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        Set<String> scopes = promotions.stream()
                .map(Promotion::getRecommendationScope)
                .map(this::normalizeScope)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

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
                .eligibilityType(promotion.getEligibilityType() != null ? promotion.getEligibilityType() : "GENERAL")
                .availableCategories(categories)
                .build();
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
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return true;
        }

        if (actualStatus == null) {
            return false;
        }

        return requestedStatus.equalsIgnoreCase(actualStatus);
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
}