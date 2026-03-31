package com.cardsense.api.repository;

import com.cardsense.api.domain.Promotion;
import com.cardsense.api.domain.PromotionCondition;
import com.cardsense.api.domain.PromotionStackability;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "cardsense.repository.mode", havingValue = "sqlite")
public class SqlitePromotionRepository implements PromotionRepository {

    private static final String BASE_SELECT = """
            SELECT promo_id, promo_version_id, title, card_code, card_name, card_status, annual_fee, apply_url,
                   bank_code, bank_name, category, channel, valid_from, valid_until, min_amount,
                   cashback_type, cashback_value, max_cashback, frequency_limit, requires_registration,
                                         recommendation_scope, eligibility_type, plan_id, conditions_json, excluded_conditions_json, status, raw_payload_json
            FROM promotion_current
            """;

    private final String dbPath;
    private final ObjectMapper objectMapper;

    public SqlitePromotionRepository(
            @Value("${cardsense.repository.sqlite.path:}") String dbPath,
            ObjectMapper objectMapper
    ) {
        this.dbPath = dbPath;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void validateConfiguration() {
        if (dbPath == null || dbPath.isBlank()) {
            throw new IllegalStateException("cardsense.repository.sqlite.path must be set when cardsense.repository.mode=sqlite");
        }
    }

    @Override
    public List<Promotion> findActivePromotions(LocalDate date) {
        String sql = BASE_SELECT + """
                WHERE (valid_from IS NULL OR date(valid_from) <= date(?))
                  AND (valid_until IS NULL OR date(valid_until) >= date(?))
                  AND upper(COALESCE(status, 'ACTIVE')) = 'ACTIVE'
                ORDER BY bank_code, card_code, promo_id
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date.toString());
            statement.setString(2, date.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapPromotions(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to query active promotions from SQLite", exception);
        }
    }

    @Override
    public List<Promotion> findAllPromotions() {
        String sql = BASE_SELECT + " ORDER BY bank_code, card_code, promo_id";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return mapPromotions(resultSet);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to query promotions from SQLite", exception);
        }
    }

    @Override
    public List<Promotion> findPromotionsByCardCode(String cardCode, LocalDate date) {
        return findActivePromotions(date).stream()
                .filter(p -> cardCode.equalsIgnoreCase(p.getCardCode()))
                .toList();
    }

    private Connection openConnection() throws SQLException {
        String jdbcUrl = dbPath.startsWith("jdbc:sqlite:") ? dbPath : "jdbc:sqlite:" + dbPath;
        return DriverManager.getConnection(jdbcUrl);
    }

    private List<Promotion> mapPromotions(ResultSet resultSet) throws SQLException {
        try {
            List<Promotion> promotions = new java.util.ArrayList<>();
            while (resultSet.next()) {
                promotions.add(Promotion.builder()
                        .promoId(resultSet.getString("promo_id"))
                        .promoVersionId(resultSet.getString("promo_version_id"))
                    .title(resultSet.getString("title"))
                        .cardCode(resultSet.getString("card_code"))
                        .cardName(resultSet.getString("card_name"))
                        .cardStatus(resultSet.getString("card_status"))
                        .annualFee((Integer) resultSet.getObject("annual_fee"))
                        .applyUrl(resultSet.getString("apply_url"))
                        .bankCode(resultSet.getString("bank_code"))
                        .bankName(resultSet.getString("bank_name"))
                        .category(resultSet.getString("category"))
                        .channel(resultSet.getString("channel"))
                        .validFrom(parseDate(resultSet.getString("valid_from")))
                        .validUntil(parseDate(resultSet.getString("valid_until")))
                        .minAmount((Integer) resultSet.getObject("min_amount"))
                        .cashbackType(resultSet.getString("cashback_type"))
                        .cashbackValue(parseDecimal(resultSet.getString("cashback_value")))
                        .maxCashback((Integer) resultSet.getObject("max_cashback"))
                        .frequencyLimit(resultSet.getString("frequency_limit"))
                        .requiresRegistration(resultSet.getInt("requires_registration") == 1)
                        .recommendationScope(resultSet.getString("recommendation_scope"))
                        .eligibilityType(resultSet.getString("eligibility_type"))
                        .planId(resultSet.getString("plan_id"))
                        .stackability(parseStackability(resultSet.getString("raw_payload_json")))
                        .conditions(parseConditions(resultSet.getString("conditions_json")))
                        .excludedConditions(parseConditions(resultSet.getString("excluded_conditions_json")))
                        .status(resultSet.getString("status"))
                        .build());
            }
            return promotions;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to map promotions from SQLite", exception);
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
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private BigDecimal parseDecimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}