package com.cardsense.api.controller;

import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.service.DecisionEngine;
import com.cardsense.api.audit.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final DecisionEngine decisionEngine;
    private final AuditService auditService;

    @PostMapping("/recommendations/card")
    public ResponseEntity<RecommendationResponse> recommendCard(@Valid @RequestBody RecommendationRequest request) {
        long startTime = System.currentTimeMillis();
        RecommendationResponse response = decisionEngine.recommend(request);
        long latency = System.currentTimeMillis() - startTime;

        auditService.logRecommendation(request, response, latency);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
