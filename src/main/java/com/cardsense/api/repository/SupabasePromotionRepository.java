package com.cardsense.api.repository;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
import com.cardsense.api.domain.PromotionStackability;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Supabase (PostgreSQL) implementation of PromotionRepository.
 * Active when cardsense.repository.mode=supabase (prod profile).
 *
 * Uses the same promotion_current view/table schema as SqlitePromotionRepository,
 * but with PostgreSQL-compatible SQL (::date cast instead of SQLite's date() function).
 */
@Repository
@ConditionalOnProperty(name = "cardsense.repository.mode", havingValue = "supabase")
public class SupabasePromotionRepository implements PromotionRepository {

    private static final String BASE_SELECT = """
            SELECT promo_id, promo_version_id, title, card_code, card_name, card_status, annual_fee, apply_url,
                   bank_code, bank_name, category, channel, valid_from, valid_until, min_amount,
                   cashback_type, cashback_value, max_cashback, frequency_limit, requires_registration,
                   recommendation_scope, eligibility_type, plan_id, conditions_json, excluded_conditions_json, status, raw_payload_json
            FROM promotion_current
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SupabasePromotionRepository(
            @Qualifier("supabaseJdbcTemplate") JdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Promotion> findActivePromotions(LocalDate date) {
        String sql = BASE_SELECT + """
                WHERE (valid_from IS NULL OR valid_from::date <= ?::date)
                  AND (valid_until IS NULL OR valid_until::date >= ?::date)
                  AND upper(COALESCE(status, 'ACTIVE')) = 'ACTIVE'
                ORDER BY bank_code, card_code, promo_id
                """;
        return jdbc.query(sql, rowMapper(), date.toString(), date.toString());
    }

    @Override
    public List<Promotion> findAllPromotions() {
        String sql = BASE_SELECT + " ORDER BY bank_code, card_code, promo_id";
        return jdbc.query(sql, rowMapper());
    }

    @Override
    public List<Promotion> findPromotionsByCardCode(String cardCode, LocalDate date) {
        return findActivePromotions(date).stream()
                .filter(p -> cardCode.equalsIgnoreCase(p.getCardCode()))
                .toList();
    }

    private RowMapper<Promotion> rowMapper() {
        return (rs, rowNum) -> mapPromotion(rs);
    }

    private Promotion mapPromotion(ResultSet rs) throws SQLException {
        try {
            return Promotion.builder()
                    .promoId(rs.getString("promo_id"))
                    .promoVersionId(rs.getString("promo_version_id"))
                    .title(rs.getString("title"))
                    .cardCode(rs.getString("card_code"))
                    .cardName(rs.getString("card_name"))
                    .cardStatus(rs.getString("card_status"))
                    .annualFee((Integer) rs.getObject("annual_fee"))
                    .applyUrl(rs.getString("apply_url"))
                    .bankCode(rs.getString("bank_code"))
                    .bankName(rs.getString("bank_name"))
                    .category(rs.getString("category"))
                    .channel(rs.getString("channel"))
                    .validFrom(parseDate(rs.getString("valid_from")))
                    .validUntil(parseDate(rs.getString("valid_until")))
                    .minAmount((Integer) rs.getObject("min_amount"))
                    .cashbackType(rs.getString("cashback_type"))
                    .cashbackValue(parseDecimal(rs.getString("cashback_value")))
                    .maxCashback((Integer) rs.getObject("max_cashback"))
                    .frequencyLimit(rs.getString("frequency_limit"))
                    .requiresRegistration(Boolean.TRUE.equals(rs.getObject("requires_registration")))
                    .recommendationScope(rs.getString("recommendation_scope"))
                    .eligibilityType(rs.getString("eligibility_type"))
                    .planId(rs.getString("plan_id"))
                    .stackability(parseStackability(rs.getString("raw_payload_json")))
                    .conditions(parseConditions(rs.getString("conditions_json")))
                    .excludedConditions(parseConditions(rs.getString("excluded_conditions_json")))
                    .status(rs.getString("status"))
                    .build();
        } catch (Exception exception) {
            throw new RuntimeException("Failed to map promotion from Supabase", exception);
        }
    }

    private List<PromotionCondition> parseConditions(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<PromotionCondition>>() {});
    }

    private PromotionStackability parseStackability(String rawPayloadJson) throws Exception {
        if (rawPayloadJson == null || rawPayloadJson.isBlank()) {
            return null;
        }
        var root = objectMapper.readTree(rawPayloadJson);
        var stackabilityNode = root.get("stackability");
        if (stackabilityNode == null || stackabilityNode.isNull()) {
            return null;
        }
        return objectMapper.treeToValue(stackabilityNode, PromotionStackability.class);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    }

    private BigDecimal parseDecimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
