package com.cardsense.api.service;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void listCardsSupportsBankAndStatusFilters() {
        Promotion activeCtbc = Promotion.builder()
            .cardCode("CTBC_DEMO_ONLINE")
            .cardName("中國信託 示例網購卡")
                .cardStatus("ACTIVE")
                .annualFee(1800)
                .bankCode("CTBC")
                .bankName("中國信託")
                .build();

        Promotion inactiveCube = Promotion.builder()
            .cardCode("CATHAY_DEMO_LIFESTYLE")
            .cardName("國泰世華 示例生活卡")
                .cardStatus("DISCONTINUED")
                .annualFee(1800)
                .bankCode("CATHAY")
                .bankName("國泰世華")
                .build();

        when(promotionRepository.findAllPromotions()).thenReturn(List.of(activeCtbc, inactiveCube));

        assertEquals(1, catalogService.listCards("CTBC", "ACTIVE").size());
        assertTrue(catalogService.listCards(null, "ACTIVE").stream()
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
}