package com.cardsense.api.controller;

import com.cardsense.api.audit.AuditService;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.service.DecisionEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private final DecisionEngine decisionEngine;
    private final AuditService auditService;

    @PostMapping("/v1/recommendations/card")
    public ResponseEntity<RecommendationResponse> recommendCard(@Valid @RequestBody RecommendationRequest request) {
        long startTime = System.currentTimeMillis();
        RecommendationResponse response = decisionEngine.recommend(request);
        long latency = System.currentTimeMillis() - startTime;

        auditService.logRecommendation(request, response, latency);

        return ResponseEntity.ok(response);
    }
}
