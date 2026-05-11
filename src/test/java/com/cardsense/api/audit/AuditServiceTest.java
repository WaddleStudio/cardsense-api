package com.cardsense.api.audit;

import com.cardsense.api.domain.CardRecommendation;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import com.cardsense.api.domain.RecommendationScenario;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTest {

    @Test
    void persistsRecommendationAuditWithRequestResponsePromoVersionsEngineVersionAndLatency() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService auditService = new AuditService(jdbc, new ObjectMapper(), "engine-test");
        RecommendationRequest request = RecommendationRequest.builder()
                .amount(1200)
                .category("ONLINE")
                .scenario(RecommendationScenario.builder()
                        .merchantName("momo")
                        .paymentMethod("LINE Pay")
                        .date(LocalDate.of(2026, 5, 11))
                        .build())
                .build();
        RecommendationResponse response = RecommendationResponse.builder()
                .requestId("request-123")
                .recommendations(List.of(
                        CardRecommendation.builder()
                                .promotionId("promo-a")
                                .promoVersionId("version-a")
                                .build(),
                        CardRecommendation.builder()
                                .promotionId("promo-b")
                                .promoVersionId("version-b")
                                .build()))
                .build();

        auditService.logRecommendation(request, response, 37);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(eq("""
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
                """), args.capture());

        Object[] values = args.getValue();
        assertEquals("request-123", values[0]);
        assertTrue(values[1].toString().contains("\"amount\":1200"));
        assertTrue(values[2].toString().contains("\"requestId\":\"request-123\""));
        assertArrayEquals(new String[]{"version-a", "version-b"}, (String[]) values[3]);
        assertEquals("engine-test", values[4]);
        assertEquals(37L, values[5]);
        assertEquals(true, values[6]);
        assertEquals(null, values[7]);
        assertEquals(null, values[8]);
    }

    @Test
    void persistsRecommendationAuditErrorWithoutResponse() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService auditService = new AuditService(jdbc, new ObjectMapper(), "engine-test");
        RecommendationRequest request = RecommendationRequest.builder()
                .amount(1200)
                .category("ONLINE")
                .build();
        IllegalStateException error = new IllegalStateException("engine unavailable");

        auditService.logRecommendationError("request-error", request, error, 41);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(eq("""
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
                """), args.capture());

        Object[] values = args.getValue();
        assertEquals("request-error", values[0]);
        assertTrue(values[1].toString().contains("\"amount\":1200"));
        assertEquals(null, values[2]);
        assertArrayEquals(new String[0], (String[]) values[3]);
        assertEquals("engine-test", values[4]);
        assertEquals(41L, values[5]);
        assertEquals(false, values[6]);
        assertEquals("IllegalStateException", values[7]);
        assertEquals("engine unavailable", values[8]);
    }
}
