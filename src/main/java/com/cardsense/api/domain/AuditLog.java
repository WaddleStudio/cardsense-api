package com.cardsense.api.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    private String requestId;
    private RecommendationRequest input;
    private RecommendationResponse output;
    private LocalDateTime timestamp;
    private long latencyMs;
}
