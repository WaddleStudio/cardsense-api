# CardSense API — VIBE_SPEC
### Updated: 2026-03-19

## Purpose
This repository implements the external deterministic recommendation API for CardSense.

## Input DTO
```java
public record RecommendationRequest(
    String category,
    Integer amount,
    List<String> cardCodes,
    List<String> registeredPromotionIds,
    List<BenefitUsage> benefitUsage,
    String location,
    LocalDate date
) {}

public record BenefitUsage(
    String promoVersionId,
    Integer consumedAmount
) {}
```

## Output DTO
```java
public record CardRecommendation(
    String cardName,
    String bankName,
    String cashbackType,
    BigDecimal cashbackValue,
    Integer estimatedReturn,
    String reason,
    String promotionId,
    String promoVersionId,
    LocalDate validUntil,
    List<Condition> conditions,
    String applyUrl
) {}
```

## Deterministic Rules
- Filter by category, amount, optional card scope, date
- Exclude inactive cards
- Exclude zero-reward promotions
- Respect `LOCATION_ONLY` and exclusion conditions
- If `requiresRegistration=true`, require the promotion id in `registeredPromotionIds`
- If caller reports exhausted usage in `benefitUsage`, exclude the promotion
- Rank by capped return desc, validUntil asc, annualFee asc, then stable tie-breakers
- Collapse to the single best promotion per card
- Return top 5

## Condition Shape
```java
public record Condition(
    String type,
    String value,
    String label
) {}
```
