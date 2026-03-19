package com.cardsense.api.service;

import com.cardsense.api.domain.BankSummary;
import com.cardsense.api.domain.CardSummary;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final PromotionRepository promotionRepository;

    public List<CardSummary> listCards(String bank, String status) {
        return distinctPromotionsByCard().values().stream()
                .map(this::toCardSummary)
                .filter(card -> matchesBank(card, bank))
                .filter(card -> matchesStatus(card.getCardStatus(), status))
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

    private Map<String, Promotion> distinctPromotionsByCard() {
        return promotionRepository.findAllPromotions().stream()
                .collect(LinkedHashMap<String, Promotion>::new,
                        (map, promotion) -> map.putIfAbsent(promotion.getCardCode(), promotion),
                        Map::putAll);
    }

    private CardSummary toCardSummary(Promotion promotion) {
        return CardSummary.builder()
                .cardCode(promotion.getCardCode())
                .cardName(promotion.getCardName())
                .cardStatus(promotion.getCardStatus())
                .annualFee(promotion.getAnnualFee())
                .applyUrl(promotion.getApplyUrl())
                .bankCode(promotion.getBankCode())
                .bankName(promotion.getBankName())
                .build();
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
}