package com.cardsense.api.controller;

import com.cardsense.api.domain.BankSummary;
import com.cardsense.api.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/banks")
@RequiredArgsConstructor
public class BankController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<List<BankSummary>> listBanks() {
        return ResponseEntity.ok(catalogService.listBanks());
    }
}