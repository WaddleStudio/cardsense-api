# CardSense API Implementation Checklist

Updated: 2026-04-06

## Next

### High Priority
- Persist `stackability` as explicit columns instead of relying on `raw_payload_json` fallback parsing.

### Medium Priority
- Define point-to-cash normalization rules for `POINTS` reward types per bank.
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
