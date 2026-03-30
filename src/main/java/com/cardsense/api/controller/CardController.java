package com.cardsense.api.controller;

import com.cardsense.api.domain.BenefitPlan;
import com.cardsense.api.domain.CardSummary;
import com.cardsense.api.domain.Promotion;
import com.cardsense.api.repository.BenefitPlanRepository;
import com.cardsense.api.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CatalogService catalogService;
    private final BenefitPlanRepository benefitPlanRepository;

    @GetMapping
    public ResponseEntity<List<CardSummary>> listCards(
            @RequestParam(required = false) String bank,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String eligibilityType) {
        return ResponseEntity.ok(catalogService.listCards(bank, status, scope, eligibilityType));
    }

    @GetMapping("/{cardCode}/promotions")
    public ResponseEntity<List<Promotion>> listCardPromotions(@PathVariable String cardCode) {
        List<Promotion> promotions = catalogService.listCardPromotions(cardCode);
        return ResponseEntity.ok(promotions);
    }

    @GetMapping("/{cardCode}/plans")
    public ResponseEntity<List<BenefitPlan>> listCardPlans(@PathVariable String cardCode) {
        List<BenefitPlan> plans = benefitPlanRepository.findByCardCode(cardCode);
        return ResponseEntity.ok(plans);
    }
}