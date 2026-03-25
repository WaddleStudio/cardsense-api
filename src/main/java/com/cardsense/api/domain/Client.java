package com.cardsense.api.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * B2B client record from Supabase clients table.
 */
public record Client(
    UUID id,
    String name,
    String email,
    String apiKeyHash,
    String apiKeyPrefix,
    String plan,
    int dailyLimit,
    String stripeCustomerId,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean canMakeRequests() {
        return isActive() || "PAYMENT_FAILED".equals(status);
    }
}
