package com.cardsense.api.controller;

import com.cardsense.api.repository.PromotionRepository;
import com.cardsense.api.repository.SqlitePromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final PromotionRepository promotionRepository;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "repository", promotionRepository instanceof SqlitePromotionRepository ? "sqlite" : "mock"
        ));
    }
}