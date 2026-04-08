# CardSense API Implementation Checklist

Updated: 2026-04-08

## Next

### High Priority
- Implement high-end `MILES` value calculation and `POINTS` point-to-cash normalization per bank.
- **Exchange Rate Engine**: Extract hardcoded rates from `RewardCalculator` into `ExchangeRateService` + `exchange-rates.json` config.
- **Exchange Rate Engine**: Add `customExchangeRates` optional field to `RecommendationRequest`; pipe through `DecisionEngine` → `RewardCalculator`.
- **Exchange Rate Engine**: Add `rewardDetail` (rawReward, rawUnit, exchangeRate, ntdEquivalent) to `CardRecommendation` response.
- **Exchange Rate Engine**: Implement `GET /v1/exchange-rates` endpoint returning the default rate table.
- Prepare Discord webhook integration endpoint for frontend user feedback/correction reports.
- Implement `My Wallet` mode card filtering logic in `DecisionEngine` to only rank user-held cards.
- Persist `stackability` as explicit columns instead of relying on `raw_payload_json` fallback parsing.

### Medium Priority
- Add explainability notes for why a promotion was excluded from recommendation.

## Frontend Integration

Frontend is live and consuming these stable endpoints:
- `GET /health`
- `GET /v1/banks`
- `GET /v1/cards?bank=...&scope=...&eligibilityType=...`
- `GET /v1/cards/{cardCode}/promotions`
- `GET /v1/cards/{cardCode}/plans`
- `POST /v1/recommendations/card`

Stable response fields: `recommendations[].estimatedReturn`, `promotionBreakdown`, `disclaimer`, `activePlan`.
