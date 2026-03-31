package com.cardsense.api;

import com.cardsense.api.domain.CardSummary;
import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.domain.RecommendationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CathaySqliteApiIntegrationTest {

    private static final Path DATABASE_PATH = prepareDatabase();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("cardsense.repository.mode", () -> "sqlite");
        registry.add("cardsense.repository.sqlite.path", () -> DATABASE_PATH.toString());
    }

    @Test
    void healthCatalogAndRecommendationReflectCathaySqliteData() {
        ResponseEntity<Map<String, String>> healthResponse = restTemplate.exchange(
                url("/health"),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
        assertNotNull(healthResponse.getBody());
        assertEquals("UP", healthResponse.getBody().get("status"));
        assertEquals("sqlite", healthResponse.getBody().get("repository"));

        HttpEntity<Void> authenticatedRequest = new HttpEntity<>(apiHeaders());

        ResponseEntity<List<CardSummary>> allCardsResponse = restTemplate.exchange(
                url("/v1/cards?bank=CATHAY"),
                HttpMethod.GET,
                authenticatedRequest,
                new ParameterizedTypeReference<>() {}
        );
        ResponseEntity<List<CardSummary>> activeCardsResponse = restTemplate.exchange(
                url("/v1/cards?bank=CATHAY&status=ACTIVE"),
                HttpMethod.GET,
                authenticatedRequest,
                new ParameterizedTypeReference<>() {}
        );
        ResponseEntity<List<CardSummary>> catalogCardsResponse = restTemplate.exchange(
                url("/v1/cards?bank=CATHAY&status=ACTIVE&scope=CATALOG_ONLY"),
                HttpMethod.GET,
                authenticatedRequest,
                new ParameterizedTypeReference<>() {}
        );
        ResponseEntity<List<CardSummary>> recommendableCardsResponse = restTemplate.exchange(
                url("/v1/cards?bank=CATHAY&status=ACTIVE&scope=RECOMMENDABLE"),
                HttpMethod.GET,
                authenticatedRequest,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, allCardsResponse.getStatusCode());
        assertEquals(2, allCardsResponse.getBody().size());
        assertEquals(2, activeCardsResponse.getBody().size());
        assertEquals(2, catalogCardsResponse.getBody().size());
        assertEquals(1, recommendableCardsResponse.getBody().size());
        assertEquals(List.of("CATHAY_CUBE", "CATHAY_FORMOSA"), catalogCardsResponse.getBody().stream().map(CardSummary::getCardCode).toList());
        assertEquals(List.of("CATHAY_FORMOSA"), recommendableCardsResponse.getBody().stream().map(CardSummary::getCardCode).toList());

        RecommendationRequest request = RecommendationRequest.builder()
                .amount(1000)
                .category("ONLINE")
                .cardCodes(List.of("CATHAY_CUBE", "CATHAY_FORMOSA"))
                .date(LocalDate.of(2026, 3, 20))
                .build();

        ResponseEntity<RecommendationResponse> recommendationResponse = restTemplate.exchange(
                url("/v1/recommendations/card"),
                HttpMethod.POST,
                new HttpEntity<>(request, apiHeaders()),
                RecommendationResponse.class
        );

        assertEquals(HttpStatus.OK, recommendationResponse.getStatusCode());
        assertNotNull(recommendationResponse.getBody());
        assertEquals(1, recommendationResponse.getBody().getRecommendations().size());
        assertNotNull(recommendationResponse.getBody().getScenario());
        assertNotNull(recommendationResponse.getBody().getComparison());
        assertEquals("台塑聯名卡", recommendationResponse.getBody().getRecommendations().get(0).getCardName());
        assertEquals("formosa-reco", recommendationResponse.getBody().getRecommendations().get(0).getPromotionId());
        assertTrue(recommendationResponse.getBody().getRecommendations().stream()
                .noneMatch(recommendation -> "CUBE信用卡".equals(recommendation.getCardName())));
    }

    private HttpHeaders apiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Api-Key", "dummy-api-key");
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static Path prepareDatabase() {
        try {
            Path databasePath = Files.createTempFile("cardsense-cathay-sqlite", ".db");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE promotion_current (
                            promo_id TEXT PRIMARY KEY,
                            promo_version_id TEXT NOT NULL,
                            title TEXT NOT NULL,
                            bank_code TEXT NOT NULL,
                            bank_name TEXT NOT NULL,
                            card_code TEXT NOT NULL,
                            card_name TEXT NOT NULL,
                            card_status TEXT,
                            annual_fee INTEGER,
                            apply_url TEXT,
                            category TEXT NOT NULL,
                            channel TEXT,
                            cashback_type TEXT NOT NULL,
                            cashback_value NUMERIC NOT NULL,
                            min_amount INTEGER DEFAULT 0,
                            max_cashback INTEGER,
                            frequency_limit TEXT,
                            requires_registration INTEGER NOT NULL DEFAULT 0,
                            recommendation_scope TEXT NOT NULL DEFAULT 'RECOMMENDABLE',
                            valid_from TEXT NOT NULL,
                            valid_until TEXT NOT NULL,
                            conditions_json TEXT NOT NULL,
                            excluded_conditions_json TEXT NOT NULL,
                            source_url TEXT NOT NULL,
                            raw_text_hash TEXT NOT NULL,
                            summary TEXT NOT NULL,
                            extractor_version TEXT NOT NULL,
                            extracted_at TEXT NOT NULL,
                            confidence REAL NOT NULL,
                            status TEXT NOT NULL,
                            run_id TEXT,
                            raw_payload_json TEXT NOT NULL
                        )
                        """);

                statement.executeUpdate(insertPromotion(
                        "cube-catalog",
                        "cube-v1",
                        "CUBE信用卡 海外禮遇",
                        "CATHAY_CUBE",
                        "CUBE信用卡",
                        "CATALOG_ONLY",
                        "OVERSEAS",
                        3.5,
                        7800,
                        "2026-01-01",
                        "2026-06-30"
                ));
                statement.executeUpdate(insertPromotion(
                        "cube-future",
                        "cube-v2",
                        "CUBE信用卡 新戶首刷禮",
                        "CATHAY_CUBE",
                        "CUBE信用卡",
                        "FUTURE_SCOPE",
                        "ONLINE",
                        500,
                        500,
                        "2026-01-01",
                        "2026-03-31"
                ));
                statement.executeUpdate(insertPromotion(
                        "formosa-reco",
                        "formosa-v1",
                        "台塑聯名卡 加油降價天天享",
                        "CATHAY_FORMOSA",
                        "台塑聯名卡",
                        "RECOMMENDABLE",
                        "ONLINE",
                        1,
                        null,
                        "2026-01-01",
                        "2026-03-31"
                ));
                statement.executeUpdate(insertPromotion(
                        "formosa-catalog",
                        "formosa-v2",
                        "台塑聯名卡 加油禮遇",
                        "CATHAY_FORMOSA",
                        "台塑聯名卡",
                        "CATALOG_ONLY",
                        "OTHER",
                        3,
                        300,
                        "2026-01-01",
                        "2026-06-30"
                ));
                statement.executeUpdate(insertPromotion(
                        "world-inactive",
                        "world-v1",
                        "世界卡 停發舊活動",
                        "CATHAY_WORLD",
                        "世界卡",
                        "CATALOG_ONLY",
                        "DINING",
                        10,
                        null,
                        "2026-01-01",
                        "2026-12-31",
                        "DISCONTINUED",
                        "INACTIVE"
                ));
            }
            return databasePath;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to prepare Cathay SQLite integration database", exception);
        }
    }

    private static String insertPromotion(
            String promoId,
            String promoVersionId,
            String title,
            String cardCode,
            String cardName,
            String recommendationScope,
            String category,
            Number cashbackValue,
            Integer maxCashback,
            String validFrom,
            String validUntil
    ) {
        return insertPromotion(
                promoId,
                promoVersionId,
                title,
                cardCode,
                cardName,
                recommendationScope,
                category,
                cashbackValue,
                maxCashback,
                validFrom,
                validUntil,
                "ACTIVE",
                "ACTIVE"
        );
    }

    private static String insertPromotion(
            String promoId,
            String promoVersionId,
            String title,
            String cardCode,
            String cardName,
            String recommendationScope,
            String category,
            Number cashbackValue,
            Integer maxCashback,
            String validFrom,
            String validUntil,
            String cardStatus,
            String status
    ) {
        String maxCashbackValue = maxCashback == null ? "NULL" : maxCashback.toString();
        return """
                INSERT INTO promotion_current (
                    promo_id, promo_version_id, title, bank_code, bank_name, card_code, card_name, card_status,
                    annual_fee, apply_url, category, channel, cashback_type, cashback_value, min_amount,
                    max_cashback, frequency_limit, requires_registration, recommendation_scope, valid_from, valid_until,
                    conditions_json, excluded_conditions_json, source_url, raw_text_hash, summary,
                    extractor_version, extracted_at, confidence, status, raw_payload_json
                ) VALUES (
                    '%s', '%s', '%s', 'CATHAY', '國泰世華', '%s', '%s', '%s',
                    0, NULL, '%s', 'ONLINE', 'PERCENT', %s, 0,
                    %s, 'NONE', 0, '%s', '%s', '%s',
                    '[]', '[]', 'https://example.com/source', '%s-hash', 'summary',
                    'extractor-0.4.0', '2026-03-20T00:00:00Z', 1.0, '%s', '{}'
                )
                """.formatted(
                promoId,
                promoVersionId,
                title,
                cardCode,
                cardName,
                cardStatus,
                category,
                cashbackValue,
                maxCashbackValue,
                recommendationScope,
                validFrom,
                validUntil,
                promoId,
                status
        );
    }
}