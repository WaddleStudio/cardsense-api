package com.cardsense.api.audit;

import com.cardsense.api.domain.AuditLog;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class AuditService {

    @Async
    public void logRecommendation(RecommendationRequest request, RecommendationResponse response, long latencyMs) {
        String requestId = response.getRequestId() != null ? response.getRequestId() : UUID.randomUUID().toString();
        
        AuditLog auditLog = AuditLog.builder()
                .requestId(requestId)
                .input(request)
                .output(response)
                .timestamp(LocalDateTime.now())
                .latencyMs(latencyMs)
                .build();

        log.info("AUDIT: {}", auditLog);
    }
}
