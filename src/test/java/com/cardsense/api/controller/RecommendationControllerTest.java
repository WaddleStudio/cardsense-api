package com.cardsense.api.controller;

import com.cardsense.api.audit.AuditService;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.service.DecisionEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationControllerTest {

    @Test
    void auditsRecommendationErrorsBeforeRethrowing() {
        DecisionEngine decisionEngine = mock(DecisionEngine.class);
        AuditService auditService = mock(AuditService.class);
        RecommendationController controller = new RecommendationController(decisionEngine, auditService);
        RecommendationRequest request = RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .build();
        IllegalStateException error = new IllegalStateException("engine unavailable");
        when(decisionEngine.recommend(request)).thenThrow(error);

        assertThrows(IllegalStateException.class, () -> controller.recommendCard(request));

        verify(auditService).logRecommendationError(eq(null), eq(request), eq(error), anyLong());
    }
}
