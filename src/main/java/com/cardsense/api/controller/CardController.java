package com.cardsense.api.controller;

import com.cardsense.api.domain.CardSummary;
import com.cardsense.api.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<List<CardSummary>> listCards(
            @RequestParam(required = false) String bank,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope) {
        return ResponseEntity.ok(catalogService.listCards(bank, status, scope));
    }
}