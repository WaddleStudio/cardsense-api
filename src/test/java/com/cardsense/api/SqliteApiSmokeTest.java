package com.cardsense.api;

import com.cardsense.api.domain.RecommendationRequest;
import com.cardsense.api.repository.PromotionRepository;
import com.cardsense.api.repository.SqlitePromotionRepository;
import com.cardsense.api.service.DecisionEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
class SqliteApiSmokeTest {

    private static final Path DATABASE_PATH = prepareDatabase();

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private DecisionEngine decisionEngine;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("cardsense.repository.mode", () -> "sqlite");
        registry.add("cardsense.repository.sqlite.path", () -> DATABASE_PATH.toString());
    }

    @Test
    void startsApplicationAndReadsRecommendationFromSqlite() {
        assertInstanceOf(SqlitePromotionRepository.class, promotionRepository);

        RecommendationRequest request = RecommendationRequest.builder()
                .amount(1000)
                .category("OVERSEAS")
                .date(LocalDate.of(2026, 3, 19))
                .build();

        var response = decisionEngine.recommend(request);

        assertEquals(1, response.getRecommendations().size());
        assertEquals("ESUN_CARD", response.getRecommendations().get(0).getPromotionId());
    }

    private static Path prepareDatabase() {
        try {
            Path databasePath = Files.createTempFile("cardsense-api-smoke", ".db");
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
                statement.executeUpdate("""
                        INSERT INTO promotion_current (
                            promo_id, promo_version_id, title, bank_code, bank_name, card_code, card_name, card_status,
                            annual_fee, apply_url, category, channel, cashback_type, cashback_value, min_amount,
                            max_cashback, frequency_limit, requires_registration, recommendation_scope, valid_from, valid_until,
                            conditions_json, excluded_conditions_json, source_url, raw_text_hash, summary,
                            extractor_version, extracted_at, confidence, status, raw_payload_json
                        ) VALUES (
                            'ESUN_CARD', 'version-1', '玉山熊本熊卡 日本一般消費10%回饋', 'ESUN', '玉山銀行', 'ESUN_KUMAMON_CARD', '玉山熊本熊卡', 'ACTIVE',
                            3000, 'https://example.com/apply', 'OVERSEAS', 'ONLINE', 'PERCENT', 10.0, 0,
                            500, 'MONTHLY', 0, 'RECOMMENDABLE', '2026-01-01', '2026-12-31',
                            '[{"type":"TEXT","value":"JP","label":"日本適用"}]', '[]', 'https://example.com/source', 'hash', 'summary',
                            'extractor-0.4.0', '2026-03-19T00:00:00Z', 1.0, 'ACTIVE', '{}'
                        )
                        """);
            }
            return databasePath;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to prepare SQLite smoke test database", exception);
        }
    }
}