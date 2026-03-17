# CardSense API — VIBE_SPEC
### Aligned with CardSense-Spec.md v1.0 | Updated: 2026-03-18

## Purpose
This repository implements the **external decision API**
for CardSense.

It reads normalized promotions and returns
**deterministic, explainable credit card recommendations**.

---

## Scope (DO)
- REST API with deterministic decision engine
- API Key authentication + rate limiting
- Audit logging for every recommendation
- Explainable responses with traceable promo_version_ids
- Legal disclaimer in every recommendation response

## Out of Scope (DO NOT)
- No crawling or extraction
- No LLM in request path (zero tolerance)
- No user transaction ingestion (MVP)
- No promotion full-text API (source_text stays in extractor staging)

---

## Technology Stack

| Component | Choice | Reason |
|-----------|--------|--------|
| Language | Java 21 | Financial domain convention; type safety |
| Framework | Spring Boot 4 | Enterprise stability; Security + JPA built-in |
| DB | PostgreSQL (Supabase) | Shared with Extractor; JSONB for conditions |
| ORM | Spring Data JPA | Deep Spring ecosystem integration |
| Auth | JWT + API Key | B2B uses API Key; future B2C uses JWT |
| Deployment | Railway | Spring Boot friendly; PostgreSQL addon |
| CI/CD | GitHub Actions | Unified pipeline across 3 repos |

**Dependency:** `cardsense-contracts` via Maven Local / GitHub Packages

---

## Required Endpoints

```
POST /v1/recommendations/card    ← Core: deterministic recommendation
GET  /v1/cards                   ← List available cards (with filters)
GET  /v1/banks                   ← List supported banks
GET  /health                     ← Health check (DB connectivity)
```

---

## Required Project Structure

```
cardsense-api/
├── src/main/java/com/cardsense/api/
│   ├── controller/
│   │   ├── RecommendationController.java
│   │   ├── CardController.java
│   │   └── HealthController.java
│   ├── service/
│   │   ├── DecisionEngine.java         ← Core recommendation logic
│   │   └── RewardCalculator.java       ← Cashback calculation
│   ├── domain/
│   │   └── ScoredPromotion.java        ← Internal scoring model
│   ├── repository/
│   │   ├── PromotionRepository.java
│   │   ├── CardRepository.java
│   │   └── BankRepository.java
│   ├── audit/
│   │   ├── AuditService.java
│   │   └── AuditRecord.java
│   ├── security/
│   │   ├── ApiKeyFilter.java
│   │   └── RateLimitFilter.java
│   └── config/
│       ├── SecurityConfig.java
│       └── RateLimitConfig.java
├── src/main/resources/
│   └── application.yml
├── src/test/
│   └── ...
├── build.gradle.kts
└── README.md
```

---

## Recommendation Logic (Deterministic)

### Input: RecommendationRequest
```java
public record RecommendationRequest(
    String category,        // "DINING", "TRANSPORT", "ONLINE"...
    Integer amount,         // Transaction amount (TWD)
    List<String> cardCodes, // User's cards (optional, empty = all)
    String location,        // Location (optional)
    LocalDate date          // Date (optional, default = today)
) {}
```

### Processing Steps (all deterministic, no randomness)

```java
public List<CardRecommendation> recommend(RecommendationRequest req) {

    // 1. FILTER: Active promotions matching criteria
    List<Promotion> eligible = promotionRepo.findActive(
        category = req.category(),
        date = req.date() != null ? req.date() : LocalDate.now(),
        minAmount <= req.amount()
    );

    // 2. SCOPE: Only user's cards (if specified)
    if (req.cardCodes() != null && !req.cardCodes().isEmpty()) {
        eligible = eligible.stream()
            .filter(p -> req.cardCodes().contains(p.card().code()))
            .toList();
    }

    // 3. CALCULATE: Estimated reward per promotion
    List<ScoredPromotion> scored = eligible.stream()
        .map(p -> new ScoredPromotion(
            promotion = p,
            estimatedReturn = calculateReturn(p, req.amount()),
            cappedReturn = Math.min(
                calculateReturn(p, req.amount()),
                p.maxCashback() != null ? p.maxCashback() : Integer.MAX_VALUE
            )
        ))
        .toList();

    // 4. SORT: Deterministic three-tier sort
    scored.sort(Comparator
        .comparing(ScoredPromotion::cappedReturn).reversed()   // Highest reward first
        .thenComparing(s -> s.promotion().validUntil())        // Expiring sooner first
        .thenComparing(s -> s.promotion().card().annualFee())  // Lower annual fee first
    );

    // 5. RESPOND: Top 5 with auditable reasons
    return scored.stream()
        .limit(5)
        .map(this::toRecommendation)
        .toList();
}
```

### RewardCalculator

```java
int calculateReturn(Promotion p, int amount) {
    return switch (p.cashbackType()) {
        case PERCENT -> (int) (amount * p.cashbackValue() / 100);
        case FIXED   -> p.cashbackValue().intValue();
        case POINTS  -> (int) (amount * p.cashbackValue() / 100); // Points as equivalent %
    };
}
```

### Output: RecommendationResponse
```java
public record RecommendationResponse(
    String requestId,
    List<CardRecommendation> recommendations,
    LocalDateTime generatedAt,
    String disclaimer
) {}

public record CardRecommendation(
    String cardName,
    String bankName,
    String cashbackType,
    BigDecimal cashbackValue,
    Integer estimatedReturn,
    String reason,
    String promotionId,
    String promoVersionId,      // Audit: immutable version ID
    LocalDate validUntil,
    List<String> conditions,
    String applyUrl             // Affiliate link (nullable)
) {}
```

**Reason Generation (template-based, NOT LLM):**
```
"{bankName} {cardName} — {category} 消費享 {cashbackValue}% 回饋，
 預估回饋 ${estimatedReturn} 元，優惠至 {validUntil}"
```

---

## API Key Authentication & Rate Limiting

### Clients Table (from contracts DB schema)
```sql
CREATE TABLE clients (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    api_key     VARCHAR(64) UNIQUE NOT NULL,
    plan        VARCHAR(20) DEFAULT 'FREE',
    daily_limit INTEGER DEFAULT 100,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

### Plans
| Plan | Daily Limit | Price |
|------|-------------|-------|
| FREE | 100 calls | $0 |
| STARTER | 5,000 calls | $29/mo |
| GROWTH | 50,000 calls | $99/mo |
| ENTERPRISE | Unlimited + SLA | Custom |

### Rate Limiting
- Per API Key, rolling 24h window
- Return `429 Too Many Requests` with `Retry-After` header
- Rate limit check MUST NOT add > 5ms latency

---

## Audit Requirements

Every recommendation request MUST be logged:

```sql
CREATE TABLE api_calls (
    id          UUID PRIMARY KEY,
    client_id   UUID NOT NULL,
    endpoint    VARCHAR(100) NOT NULL,
    request     JSONB NOT NULL,
    response    JSONB NOT NULL,
    latency_ms  INTEGER NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

Audit record includes:
- `request_id` (from response)
- Input summary (category, amount, card count)
- Selected `promo_version_ids` (for traceability)
- Calculation result (estimated returns)
- `latency_ms`
- Timestamp

**Audit writes are async** — do not block the response path.

---

## Non-Functional Requirements

| Requirement | Target | Notes |
|-------------|--------|-------|
| P50 Latency | < 100ms | Measured at API gateway |
| P99 Latency | < 300ms | Including DB query |
| Determinism | 100% | Same input → same output, always |
| Availability | 99.5% | Railway SLA baseline |
| DB writes in request path | Audit only (async) | No sync writes |
| Explainability | Every field traceable | reason + promoVersionId |

---

## Legal Disclaimer (Required)

Every `RecommendationResponse` MUST include:

```
「CardSense 提供信用卡優惠比較資訊，不構成金融建議。
實際回饋依各銀行公告為準，請以銀行官網資訊為最終依據。」
```

This is defined as a constant in `cardsense-contracts`.

---

## Copyright & Legal Compliance

| Risk | Level | Mitigation |
|------|-------|------------|
| Financial info provision license | 🔴 HIGH | Consult lawyer; CardSense is "data comparison" not "investment advice" |
| LLM in request path | ✅ NONE | Deterministic rules only — no LLM API ToS risk |
| Affiliate link disclosure | 🟢 LOW | API response explicitly marks `applyUrl` as affiliate |
| PDPA personal data | 🟡 MED | api_calls table has no user PII; client_id is B2B entity |

### Legal TODOs
- [ ] Consult lawyer: does "credit card comparison" require financial license in Taiwan?
- [ ] PDPA: define api_calls retention and deletion policy
- [ ] Affiliate disclosure: comply with Taiwan equivalent of FTC guidelines

---

## Success Criteria
- `POST /v1/recommendations/card` returns correct results with sample DB
- Recommendation output is **reproducible** — same request always returns same ranking
- Removing a promotion from DB predictably changes recommendation result
- Latency P50 < 100ms with 5 banks / 50 promotions in DB
- Audit trail: every recommendation traceable to specific promo_version_ids

---

## Agent Instructions
- You may only modify files in this repository
- You MUST respect cardsense-contracts schema — import types, don't redefine
- Determinism > optimization > cleverness
- Zero LLM usage in this repo — not even for reason generation
- Every response must include legal disclaimer
- Audit logging must be async (do not block response)
