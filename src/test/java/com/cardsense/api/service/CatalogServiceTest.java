package com.cardsense.api.service;

import com.cardsense.api.domain.CardSummary;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class CatalogServiceTest {

    private PromotionRepository promotionRepository;
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        promotionRepository = Mockito.mock(PromotionRepository.class);
        catalogService = new CatalogService(promotionRepository);
    }

    @Test
    void listCardsSupportsBankStatusAndScopeFilters() {
        Promotion activeCtbc = Promotion.builder()
            .cardCode("CTBC_DEMO_ONLINE")
            .cardName("中國信託 示例網購卡")
                .cardStatus("ACTIVE")
                .annualFee(1800)
                .bankCode("CTBC")
                .bankName("中國信託")
                .recommendationScope("RECOMMENDABLE")
                .build();

        Promotion activeCtbcCatalog = Promotion.builder()
            .cardCode("CTBC_DEMO_ONLINE")
            .cardName("中國信託 示例網購卡")
                .cardStatus("ACTIVE")
                .annualFee(1800)
                .bankCode("CTBC")
                .bankName("中國信託")
                .recommendationScope("CATALOG_ONLY")
                .build();

        Promotion inactiveCube = Promotion.builder()
            .cardCode("CATHAY_DEMO_LIFESTYLE")
            .cardName("國泰世華 示例生活卡")
                .cardStatus("DISCONTINUED")
                .annualFee(1800)
                .bankCode("CATHAY")
                .bankName("國泰世華")
                .recommendationScope("CATALOG_ONLY")
                .build();

        Promotion activeCatalogOnly = Promotion.builder()
                .cardCode("ESUN_CATALOG")
                .cardName("ESUN Catalog Card")
                .cardStatus("ACTIVE")
                .annualFee(0)
                .bankCode("ESUN")
                .bankName("ESUN")
                .recommendationScope("CATALOG_ONLY")
                .build();

        when(promotionRepository.findAllPromotions()).thenReturn(List.of(activeCtbc, activeCtbcCatalog, inactiveCube, activeCatalogOnly));

        assertEquals(1, catalogService.listCards("CTBC", "ACTIVE", null, null).size());
        assertTrue(catalogService.listCards(null, "ACTIVE", null, null).stream()
                .allMatch(card -> "ACTIVE".equals(card.getCardStatus())));
        assertEquals(1, catalogService.listCards(null, null, "CATALOG_ONLY", null).size());
        assertEquals(List.of("RECOMMENDABLE"), catalogService.listCards("CTBC", null, null, null).get(0).getRecommendationScopes());
        assertTrue(catalogService.listCards(null, null, null, null).stream()
            .allMatch(card -> "ACTIVE".equals(card.getCardStatus())));
    }

    @Test
    void listBanksReturnsDistinctBanks() {
        Promotion first = Promotion.builder().bankCode("CTBC").bankName("中國信託").cardCode("A").build();
        Promotion second = Promotion.builder().bankCode("CTBC").bankName("中國信託").cardCode("B").build();
        Promotion third = Promotion.builder().bankCode("CATHAY").bankName("國泰世華").cardCode("C").build();

        when(promotionRepository.findAllPromotions()).thenReturn(List.of(first, second, third));

        assertEquals(2, catalogService.listBanks().size());
    }
    @Test
    void listCardsAggregatesNonGeneralEligibilityAtCardLevel() {
        Promotion generalPromo = Promotion.builder()
                .cardCode("CTBC_DOCTOR")
                .cardName("CTBC Doctor Card")
                .cardStatus("ACTIVE")
                .annualFee(0)
                .bankCode("CTBC")
                .bankName("CTBC")
                .recommendationScope("RECOMMENDABLE")
                .eligibilityType("GENERAL")
                .build();

        Promotion professionPromo = Promotion.builder()
                .cardCode("CTBC_DOCTOR")
                .cardName("CTBC Doctor Card")
                .cardStatus("ACTIVE")
                .annualFee(0)
                .bankCode("CTBC")
                .bankName("CTBC")
                .recommendationScope("RECOMMENDABLE")
                .eligibilityType("PROFESSION_SPECIFIC")
                .build();

        when(promotionRepository.findAllPromotions()).thenReturn(List.of(generalPromo, professionPromo));

        assertEquals(1, catalogService.listCards(null, null, null, "PROFESSION_SPECIFIC").size());
        assertEquals("PROFESSION_SPECIFIC", catalogService.listCards(null, null, null, null).get(0).getEligibilityType());
    }

    @Test
    void listCardsCollapsesMixedScopesIntoSingleCardLevelScope() {
        Promotion recommendablePromo = Promotion.builder()
                .cardCode("ESUN_CARD")
                .cardName("ESUN Mixed Scope Card")
                .cardStatus("ACTIVE")
                .annualFee(0)
                .bankCode("ESUN")
                .bankName("ESUN")
                .recommendationScope("RECOMMENDABLE")
                .build();

        Promotion catalogOnlyPromo = Promotion.builder()
                .cardCode("ESUN_CARD")
                .cardName("ESUN Mixed Scope Card")
                .cardStatus("ACTIVE")
                .annualFee(0)
                .bankCode("ESUN")
                .bankName("ESUN")
                .recommendationScope("CATALOG_ONLY")
                .build();

        Promotion futureScopePromo = Promotion.builder()
                .cardCode("ESUN_CARD")
                .cardName("ESUN Mixed Scope Card")
                .cardStatus("ACTIVE")
                .annualFee(0)
                .bankCode("ESUN")
                .bankName("ESUN")
                .recommendationScope("FUTURE_SCOPE")
                .build();

        when(promotionRepository.findAllPromotions()).thenReturn(List.of(recommendablePromo, catalogOnlyPromo, futureScopePromo));

        assertEquals(List.of("RECOMMENDABLE"), catalogService.listCards(null, null, null, null).get(0).getRecommendationScopes());
        assertEquals(1, catalogService.listCards(null, null, "RECOMMENDABLE", null).size());
        assertEquals(0, catalogService.listCards(null, null, "CATALOG_ONLY", null).size());
    }

    @Test
    void listCardsExposesReviewSignalsForSparseGeneralOnlyCoBrandCards() {
        Promotion generalPromo = Promotion.builder()
                .cardCode("CTBC_CO_BRAND")
                .cardName("CTBC Demo 聯名卡")
                .cardStatus("ACTIVE")
                .annualFee(0)
                .bankCode("CTBC")
                .bankName("CTBC")
                .category("ONLINE")
                .subcategory("GENERAL")
                .recommendationScope("RECOMMENDABLE")
                .build();

        Promotion catalogPromo = Promotion.builder()
                .cardCode("CTBC_CO_BRAND")
                .cardName("CTBC Demo 聯名卡")
                .cardStatus("ACTIVE")
                .annualFee(0)
                .bankCode("CTBC")
                .bankName("CTBC")
                .category("OTHER")
                .recommendationScope("CATALOG_ONLY")
                .requiresRegistration(true)
                .build();

        when(promotionRepository.findAllPromotions()).thenReturn(List.of(generalPromo, catalogPromo));

        CardSummary card = catalogService.listCards(null, null, null, null).get(0);
        assertEquals(2, card.getTotalPromotionCount());
        assertEquals(1, card.getRecommendablePromotionCount());
        assertEquals(1, card.getCatalogOnlyPromotionCount());
        assertTrue(card.isGeneralRewardsOnly());
        assertTrue(card.isSparsePromotionCard());
        assertTrue(card.isCoBrandCard());
        assertFalse(card.getCatalogReviewHint().isBlank());
    }
}
