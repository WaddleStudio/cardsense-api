package com.cardsense.api.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlitePromotionRepositoryTest {

    @Test
    void findsActivePromotionsFromSqliteCurrentTable() throws Exception {
        Path databasePath = Files.createTempFile("cardsense-api-test", ".db");
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
                        max_cashback, frequency_limit, requires_registration, valid_from, valid_until,
                        conditions_json, excluded_conditions_json, source_url, raw_text_hash, summary,
                        extractor_version, extracted_at, confidence, status, raw_payload_json
                    ) VALUES (
                        'promo-1', 'version-1', '玉山熊本熊卡 日本一般消費', 'ESUN', '玉山銀行', 'ESUN_CARD', '玉山熊本熊卡', 'ACTIVE',
                        3000, 'https://example.com/apply', 'OVERSEAS', 'ONLINE', 'PERCENT', 10.0, 0,
                        500, 'MONTHLY', 1, '2026-01-01', '2026-12-31',
                        '[{"type":"TEXT","value":"JP","label":"日本適用"}]', '[]', 'https://example.com/source', 'hash', 'summary',
                        'extractor-0.2.0', '2026-03-19T00:00:00Z', 1.0, 'ACTIVE', '{}'
                    )
                    """);
        }

        SqlitePromotionRepository repository = new SqlitePromotionRepository(databasePath.toString(), new ObjectMapper());
        repository.validateConfiguration();

        assertEquals(1, repository.findAllPromotions().size());
        assertEquals(1, repository.findActivePromotions(LocalDate.of(2026, 3, 19)).size());
        assertEquals("ESUN_CARD", repository.findActivePromotions(LocalDate.of(2026, 3, 19)).get(0).getCardCode());
        assertEquals(1, repository.findActivePromotions(LocalDate.of(2026, 3, 19)).get(0).getConditions().size());

        Files.deleteIfExists(databasePath);
    }
}