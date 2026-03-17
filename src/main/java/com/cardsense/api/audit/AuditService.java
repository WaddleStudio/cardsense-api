package com.cardsense.api.audit;

import com.cardsense.api.domain.AuditLog;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class AuditService {

    public void logRecommendation(RecommendationRequest request, RecommendationResponse response, long latencyMs) {
        String requestId = UUID.randomUUID().toString();
        
        AuditLog auditLog = AuditLog.builder()
                .requestId(requestId)
                .input(request)
                .output(response)
                .timestamp(LocalDateTime.now())
                .latencyMs(latencyMs)
                .build();

        // In a real system, this would write to a DB or specific audit log system.
        // For MVP, we log to SLF4J as INFO.
        log.info("AUDIT: {}", auditLog);
    }
}
