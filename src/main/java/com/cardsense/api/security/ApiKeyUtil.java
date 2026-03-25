package com.cardsense.api.security;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * API Key generation and hashing utility.
 *
 * Key format: cs_live_ + 32 hex chars = 40 chars total
 * Storage: only SHA-256 hash stored in DB, raw key shown once.
 *
 * Run main() to generate a key + INSERT SQL:
 *   mvn exec:java -Dexec.mainClass="com.cardsense.api.security.ApiKeyUtil"
 */
public final class ApiKeyUtil {

    private static final String PREFIX = "cs_live_";
    private static final int RANDOM_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyUtil() {}

    public static String generateKey() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    public static String hash(String rawKey) {
        return ApiKeyFilter.sha256(rawKey);
    }

    public static String prefix(String rawKey) {
        return rawKey.substring(0, Math.min(12, rawKey.length()));
    }

    public static void main(String[] args) {
        String key = generateKey();
        System.out.println("=== New CardSense API Key ===");
        System.out.println("Raw key (show ONCE):     " + key);
        System.out.println("Hash (DB api_key_hash):  " + hash(key));
        System.out.println("Prefix (DB api_key_prefix): " + prefix(key));
        System.out.println();
        System.out.println("-- Supabase SQL:");
        System.out.printf(
            "INSERT INTO clients (name, email, api_key_hash, api_key_prefix, plan, daily_limit)%n" +
            "VALUES ('Client Name', 'client@example.com', '%s', '%s', 'FREE', 100);%n",
            hash(key), prefix(key));
    }
}
