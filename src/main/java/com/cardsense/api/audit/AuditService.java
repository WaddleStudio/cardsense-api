package com.cardsense.api.audit;

import com.cardsense.api.domain.CardRecommendation;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class AuditService {

    private static final String UPSERT_SQL = """
            INSERT INTO recommendation_audits (
                request_id, request_json, response_json, evaluated_promo_version_ids,
                engine_version, latency_ms, success, error_type, error_message
            ) VALUES (?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (request_id) DO UPDATE SET
                request_json = EXCLUDED.request_json,
                response_json = EXCLUDED.response_json,
                evaluated_promo_version_ids = EXCLUDED.evaluated_promo_version_ids,
                engine_version = EXCLUDED.engine_version,
                latency_ms = EXCLUDED.latency_ms,
                success = EXCLUDED.success,
                error_type = EXCLUDED.error_type,
                error_message = EXCLUDED.error_message
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final String engineVersion;

    public AuditService(
            @Qualifier("supabaseJdbcTemplate") JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${cardsense.engine.version:${spring.application.name:cardsense-api}}") String engineVersion
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.engineVersion = engineVersion;
    }

    @Async
    public void logRecommendation(RecommendationRequest request, RecommendationResponse response, long latencyMs) {
        String requestId = response.getRequestId() != null ? response.getRequestId() : UUID.randomUUID().toString();
        persist(requestId, request, response, latencyMs, true, null, null);
    }

    @Async
    public void logRecommendationError(String requestId, RecommendationRequest request, Throwable error, long latencyMs) {
        String resolvedRequestId = requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
        String errorType = error == null ? null : error.getClass().getSimpleName();
        String errorMessage = error == null ? null : error.getMessage();
        persist(resolvedRequestId, request, null, latencyMs, false, errorType, errorMessage);
    }

    private void persist(
            String requestId,
            RecommendationRequest request,
            RecommendationResponse response,
            long latencyMs,
            boolean success,
            String errorType,
            String errorMessage
    ) {
        try {
            Object[] values = {
                    requestId,
                    toJson(request),
                    response == null ? null : toJson(response),
                    promoVersionIds(response),
                    engineVersion,
                    latencyMs,
                    success,
                    errorType,
                    errorMessage
            };
            jdbc.update(UPSERT_SQL, values);
        } catch (JsonProcessingException | DataAccessException exception) {
            log.warn(
                    "Failed to persist recommendation audit requestId={} success={} promoVersionIds={}",
                    requestId,
                    success,
                    Arrays.toString(promoVersionIds(response)),
                    exception
            );
        }
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private String[] promoVersionIds(RecommendationResponse response) {
        if (response == null || response.getRecommendations() == null) {
            return new String[0];
        }
        return response.getRecommendations().stream()
                .map(CardRecommendation::getPromoVersionId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .toArray(String[]::new);
    }
}
