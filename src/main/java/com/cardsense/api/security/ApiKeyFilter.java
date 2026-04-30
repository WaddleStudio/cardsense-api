package com.cardsense.api.security;

import com.cardsense.api.domain.Client;
import com.cardsense.api.repository.ClientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key authentication and rate limiting filter.
 *
 * Public endpoints (no key required):
 *   /health, /v1/banks, /v1/cards/**, /v1/recommendations/**
 *
 * Protected endpoints (key required):
 *   /v1/billing/**, and any future endpoints not in the public list
 *
 * When a valid key is provided on ANY endpoint (including public ones),
 * the call is tracked for usage analytics.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final String API_KEY_HEADER = "X-Api-Key";

    // Cache TTLs
    private static final long CLIENT_CACHE_TTL_MS = 5 * 60 * 1000;   // 5 minutes
    private static final long USAGE_CACHE_TTL_MS = 60 * 1000;        // 1 minute

    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    @Value("${cardsense.auth.enabled:false}")
    private boolean authEnabled;

    @Value("${cardsense.public-recommendations.rate-limit-per-minute:60}")
    private int publicRecommendationLimitPerMinute;

    @Value("${cardsense.public-recommendations.max-body-bytes:16384}")
    private long publicRecommendationMaxBodyBytes;

    // Simple in-memory caches (sufficient for single-instance Railway)
    private final ConcurrentHashMap<String, CachedClient> clientCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedUsage> usageCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> publicRecommendationCache = new ConcurrentHashMap<>();

    public ApiKeyFilter(ClientRepository clientRepository, ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isPublicRecommendationPost(request)) {
            if (request.getContentLengthLong() > publicRecommendationMaxBodyBytes) {
                sendError(response, 413, "REQUEST_TOO_LARGE",
                    "Recommendation request body is too large.");
                return;
            }
            if (!allowPublicRecommendation(request)) {
                sendError(response, 429, "PUBLIC_RATE_LIMIT_EXCEEDED",
                    "Too many recommendation requests. Please wait a minute and try again.");
                return;
            }
        }

        // Auth disabled → pass through (existing behavior)
        if (!authEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // CORS preflight → always pass through
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String apiKey = request.getHeader(API_KEY_HEADER);
        boolean isPublic = isPublicEndpoint(path);

        // --- Public endpoint ---
        if (isPublic) {
            // If a key IS provided on a public endpoint, track usage (optional analytics)
            if (apiKey != null && !apiKey.isBlank()) {
                trackIfValidKey(apiKey, request, response, filterChain);
            } else {
                filterChain.doFilter(request, response);
            }
            return;
        }

        // --- Protected endpoint: key is required ---
        if (apiKey == null || apiKey.isBlank()) {
            sendError(response, 401, "MISSING_API_KEY",
                "請在 X-Api-Key header 中提供 API Key。");
            return;
        }

        // Look up client by hashed key
        String keyHash = sha256(apiKey);
        Optional<Client> clientOpt = lookupClient(keyHash);

        if (clientOpt.isEmpty()) {
            sendError(response, 403, "INVALID_API_KEY", "API Key 無效或已停用。");
            return;
        }

        Client client = clientOpt.get();

        if (!client.canMakeRequests()) {
            sendError(response, 403, "ACCOUNT_SUSPENDED",
                "帳戶已停用 (status: " + client.status() + ")。");
            return;
        }

        // Rate limit check
        int currentUsage = getCurrentUsage(client.id().toString());
        if (currentUsage >= client.dailyLimit()) {
            setRateLimitHeaders(response, client.dailyLimit(), 0);
            sendError(response, 429, "RATE_LIMIT_EXCEEDED",
                String.format("每日 API 呼叫次數已達上限 (%d/%d)。",
                    currentUsage, client.dailyLimit()));
            return;
        }

        // Set client info for downstream controllers
        request.setAttribute("cardsense.client.id", client.id());
        request.setAttribute("cardsense.client.plan", client.plan());

        // Execute request
        long startTime = System.currentTimeMillis();
        filterChain.doFilter(request, response);
        int latencyMs = (int) (System.currentTimeMillis() - startTime);

        // Record call + update usage
        int newUsage = recordCall(client, path, request.getMethod(),
            response.getStatus(), latencyMs);
        setRateLimitHeaders(response, client.dailyLimit(),
            Math.max(0, client.dailyLimit() - newUsage));
    }

    // ---- Public endpoint check (matches your existing logic) ----

    private boolean isPublicEndpoint(String uri) {
        return "/health".equals(uri)
                || uri.startsWith("/v1/health")
                || uri.startsWith("/v1/banks")
                || uri.startsWith("/v1/cards")
                || uri.startsWith("/v1/recommend")
                || uri.startsWith("/webhooks/");
    }

    private boolean isPublicRecommendationPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/v1/recommendations/card".equals(request.getRequestURI());
    }

    private boolean allowPublicRecommendation(HttpServletRequest request) {
        if (publicRecommendationLimitPerMinute <= 0) {
            return true;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        long minute = System.currentTimeMillis() / 60_000;
        String key = "public-rec:" + clientIp + ":" + minute;
        int count = publicRecommendationCache.merge(key, 1, Integer::sum);
        publicRecommendationCache.keySet().removeIf(existingKey -> !existingKey.endsWith(":" + minute));
        return count <= publicRecommendationLimitPerMinute;
    }

    // ---- Track usage on public endpoints (optional, for analytics) ----

    private void trackIfValidKey(String apiKey, HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        String keyHash = sha256(apiKey);
        Optional<Client> clientOpt = lookupClient(keyHash);

        if (clientOpt.isPresent() && clientOpt.get().canMakeRequests()) {
            Client client = clientOpt.get();
            request.setAttribute("cardsense.client.id", client.id());
            request.setAttribute("cardsense.client.plan", client.plan());

            long startTime = System.currentTimeMillis();
            filterChain.doFilter(request, response);
            int latencyMs = (int) (System.currentTimeMillis() - startTime);

            recordCall(client, request.getRequestURI(), request.getMethod(),
                response.getStatus(), latencyMs);
        } else {
            // Invalid key on public endpoint → still allow, just don't track
            filterChain.doFilter(request, response);
        }
    }

    // ---- Client lookup with cache ----

    private Optional<Client> lookupClient(String keyHash) {
        CachedClient cached = clientCache.get(keyHash);
        if (cached != null && !cached.isExpired()) {
            return Optional.of(cached.client);
        }

        try {
            Optional<Client> client = clientRepository.findByApiKeyHash(keyHash);
            client.ifPresent(c -> clientCache.put(keyHash, new CachedClient(c)));
            return client;
        } catch (Exception e) {
            log.error("Failed to look up client: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ---- Usage tracking with cache ----

    private int getCurrentUsage(String clientId) {
        String cacheKey = clientId + ":" + LocalDate.now();
        CachedUsage cached = usageCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.count;
        }

        try {
            int usage = clientRepository.getDailyUsage(java.util.UUID.fromString(clientId));
            usageCache.put(cacheKey, new CachedUsage(usage));
            return usage;
        } catch (Exception e) {
            log.error("Failed to get daily usage: {}", e.getMessage());
            return 0;  // fail open
        }
    }

    private int recordCall(Client client, String endpoint, String method,
                           int statusCode, int latencyMs) {
        try {
            clientRepository.recordApiCall(client.id(), endpoint, method, statusCode, latencyMs);
            int newCount = clientRepository.incrementDailyUsage(client.id());

            String cacheKey = client.id().toString() + ":" + LocalDate.now();
            usageCache.put(cacheKey, new CachedUsage(newCount));
            return newCount;
        } catch (Exception e) {
            log.error("Failed to record API call for client {}: {}", client.id(), e.getMessage());
            return 0;  // don't fail the request
        }
    }

    // ---- Response helpers ----

    private void setRateLimitHeaders(HttpServletResponse response, int limit, int remaining) {
        ZonedDateTime resetAt = LocalDate.now().plusDays(1)
            .atTime(LocalTime.MIDNIGHT).atZone(ZoneOffset.UTC);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", resetAt.toString());
    }

    private void sendError(HttpServletResponse response, int status,
                           String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
            "error", error,
            "message", message
        )));
    }

    // ---- Crypto ----

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ---- Cache records ----

    private record CachedClient(Client client, long cachedAt) {
        CachedClient(Client client) { this(client, System.currentTimeMillis()); }
        boolean isExpired() { return System.currentTimeMillis() - cachedAt > CLIENT_CACHE_TTL_MS; }
    }

    private record CachedUsage(int count, long cachedAt) {
        CachedUsage(int count) { this(count, System.currentTimeMillis()); }
        boolean isExpired() { return System.currentTimeMillis() - cachedAt > USAGE_CACHE_TTL_MS; }
    }
}
