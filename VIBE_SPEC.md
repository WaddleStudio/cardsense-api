# CardSense API — VIBE_SPEC
### Updated: 2026-03-20

## Purpose
This repository implements the external deterministic recommendation API for CardSense, with scenario-driven comparison, explicit boundary handling, and a forward-compatible contract for break-even analysis plus multi-promotion coexistence.

## Request Contract
`RecommendationRequest` now supports both legacy top-level fields and a richer nested scenario/comparison structure.

```java
public class RecommendationRequest {
    Integer amount;
    String category;
    List<String> cardCodes;
    List<String> registeredPromotionIds;
    List<BenefitUsage> benefitUsage;
    String location;
    LocalDate date;

    RecommendationScenario scenario;
    RecommendationComparisonOptions comparison;
}

public class RecommendationScenario {
    Integer amount;
    String category;
    LocalDate date;
    String location;
    String channel;
    String merchantName;
    String merchantId;
    String paymentMethod;
    Integer installmentCount;
    Boolean newCustomer;
    String customerSegment;
    String membershipTier;
    List<String> tags;
    Map<String, String> attributes;
}

public class RecommendationComparisonOptions {
    ComparisonMode mode;
    Boolean includePromotionBreakdown;
    Boolean includeBreakEvenAnalysis;
    Integer maxResults;
    List<String> compareCardCodes;
}

public enum ComparisonMode {
    BEST_SINGLE_PROMOTION,
    STACK_ALL_ELIGIBLE
}

public class BenefitUsage {
    String promoVersionId;
    Integer consumedAmount;
}
```

## Response Contract
The response is now explicitly card-level. A recommendation may still expose a representative promotion for backward compatibility, but ranking is card-centric.

```java
public class RecommendationResponse {
    String requestId;
    RecommendationScenario scenario;
    RecommendationComparisonSummary comparison;
    List<CardRecommendation> recommendations;
    LocalDateTime generatedAt;
    String disclaimer;
}

public class CardRecommendation {
    String cardCode;
    String cardName;
    String bankCode;
    String bankName;
    String cashbackType;
    BigDecimal cashbackValue;
    Integer estimatedReturn;
    Integer matchedPromotionCount;
    String rankingMode;
    String reason;
    String promotionId;
    String promoVersionId;
    LocalDate validUntil;
    List<Condition> conditions;
    List<PromotionRewardBreakdown> promotionBreakdown;
    String applyUrl;
}

public class RecommendationComparisonSummary {
    ComparisonMode mode;
    Integer evaluatedPromotionCount;
    Integer eligiblePromotionCount;
    Integer rankedCardCount;
    Boolean breakEvenEvaluated;
    List<BreakEvenAnalysis> breakEvenAnalyses;
    List<String> notes;
}
```

## Deterministic Rules
- Resolve `scenario.amount/category/date/location` first; fall back to legacy top-level fields when nested values are absent.
- Filter by category, amount, optional card scope, optional channel, and date.
- Exclude inactive cards and promotions with zero effective reward.
- Respect `LOCATION_ONLY`, `CATEGORY_EXCLUDE`, and `LOCATION_EXCLUDE` conditions.
- If `requiresRegistration=true`, require the matching promotion version in `registeredPromotionIds`.
- If caller reports exhausted usage in `benefitUsage`, exclude that promotion from scoring.
- Compute reward in transaction-currency terms before ranking; never compare raw `cashbackValue` directly across reward types.
- Group eligible promotions by card before final ranking.
- `BEST_SINGLE_PROMOTION` uses the highest-ranked single promotion as the card score.
- `STACK_ALL_ELIGIBLE` sums all currently eligible promotions for the same card and returns a breakdown. Shared contracts now define explicit `promotion.stackability` metadata, but the current engine has not fully consumed those rules yet.
- Rank cards by effective return desc, validUntil asc, annualFee asc, then stable bank/card/promo tie-breakers.
- Return `comparison` metadata so callers know which mode and assumptions were applied.

## Boundary Rules
- `minAmount` gates eligibility. A promotion below threshold contributes `0` and is filtered out.
- `maxCashback` caps variable rewards and must be applied before card-level aggregation.
- `frequencyLimit=ONCE` and exhausted usage should remove the promotion entirely.
- `POINTS` currently follows the same computational path as `PERCENT`; if a bank later introduces point-to-cash conversion rules, the contract must add a normalization factor.
- `STACK_ALL_ELIGIBLE` is intentionally explicit because coexistence is a product decision, not an accidental side effect.

## Break-Even Rules
Break-even analysis exists to compare fixed rewards versus variable rewards in a scenario-aware way.

- For a fixed reward `F` and percentage reward `r%`, the uncapped break-even amount is `F / (r / 100)`.
- If the variable reward has `maxCashback`, compute the saturation amount as `maxCashback / (r / 100)`.
- If saturation happens before the break-even point, the response should say the variable reward caps first rather than pretending it will overtake indefinitely.
- `minAmount` for either side remains part of the interpretation and is included in the analysis payload.
- Break-even output is advisory metadata. It does not override deterministic ranking for the current request amount.

## Condition Shape
```java
public class Condition {
    String type;
    String value;
    String label;
}
```

## Product Direction
- The comparison unit is the card, not a standalone promotion.
- A representative promotion remains in the response for backward compatibility and explainability.
- Long term, the engine should consume the explicit stackability / exclusivity metadata already defined in shared contracts so `STACK_ALL_ELIGIBLE` can evolve from heuristic to deterministic coexistence.
