package com.cardsense.api.repository;

import com.cardsense.api.domain.Client;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Client data access from Supabase PostgreSQL.
 * Handles API key lookup, usage tracking, and rate limit checks.
 */
@Repository
public class ClientRepository {

    private final JdbcTemplate jdbc;

    public ClientRepository(@Qualifier("supabaseJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Look up client by API key hash (SHA-256).
     */
    public Optional<Client> findByApiKeyHash(String apiKeyHash) {
        var clients = jdbc.query(
            "SELECT * FROM clients WHERE api_key_hash = ? AND status != 'CANCELED'",
            CLIENT_MAPPER,
            apiKeyHash
        );
        return clients.isEmpty() ? Optional.empty() : Optional.of(clients.get(0));
    }

    /**
     * Get current daily usage count. Returns 0 if no record for today.
     */
    public int getDailyUsage(UUID clientId) {
        var result = jdbc.queryForObject(
            "SELECT get_daily_usage(?::uuid)",
            Integer.class,
            clientId.toString()
        );
        return result != null ? result : 0;
    }

    /**
     * Atomically increment daily usage, return new count.
     * Uses PostgreSQL function with UPSERT — thread-safe.
     */
    public int incrementDailyUsage(UUID clientId) {
        var result = jdbc.queryForObject(
            "SELECT increment_daily_usage(?::uuid)",
            Integer.class,
            clientId.toString()
        );
        return result != null ? result : 1;
    }

    /**
     * Record an API call for billing and analytics.
     */
    public void recordApiCall(UUID clientId, String endpoint, String method,
                              int statusCode, int latencyMs) {
        jdbc.update(
            "INSERT INTO api_calls (client_id, endpoint, method, status_code, latency_ms) " +
            "VALUES (?::uuid, ?, ?, ?, ?)",
            clientId.toString(), endpoint, method, statusCode, latencyMs
        );
    }

    /**
     * Update plan + daily limit (called from Stripe webhook).
     */
    public void updatePlan(UUID clientId, String plan, int dailyLimit) {
        jdbc.update(
            "UPDATE clients SET plan = ?, daily_limit = ? WHERE id = ?::uuid",
            plan, dailyLimit, clientId.toString()
        );
    }

    /**
     * Update client status (called from Stripe webhook).
     */
    public void updateStatus(UUID clientId, String status) {
        jdbc.update(
            "UPDATE clients SET status = ? WHERE id = ?::uuid",
            status, clientId.toString()
        );
    }

    /**
     * Set Stripe customer ID after first checkout.
     */
    public void setStripeCustomerId(UUID clientId, String stripeCustomerId) {
        jdbc.update(
            "UPDATE clients SET stripe_customer_id = ? WHERE id = ?::uuid",
            stripeCustomerId, clientId.toString()
        );
    }

    // ---- Row Mapper ----

    private static final RowMapper<Client> CLIENT_MAPPER = (rs, rowNum) -> new Client(
        UUID.fromString(rs.getString("id")),
        rs.getString("name"),
        rs.getString("email"),
        rs.getString("api_key_hash"),
        rs.getString("api_key_prefix"),
        rs.getString("plan"),
        rs.getInt("daily_limit"),
        rs.getString("stripe_customer_id"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );
}
