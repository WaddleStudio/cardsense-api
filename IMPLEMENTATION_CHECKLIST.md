# CardSense API Implementation Checklist

Updated: 2026-04-01

## Done

### Core Recommendation
- Resolve scenario-driven recommendation requests with backward-compatible top-level fields.
- Support card-level comparison modes `BEST_SINGLE_PROMOTION` and `STACK_ALL_ELIGIBLE`.
- Return `scenario`, `comparison`, `promotionBreakdown`, and break-even metadata in recommendation responses.
- Filter out `CATALOG_ONLY` promotions from recommendation ranking while keeping `/v1/cards` scope-aware.
- Read `promotion.stackability` metadata from SQLite `raw_payload_json` and use it in `DecisionEngine` for deterministic stacking.
- Provide importable Postman collection assets for health, cards, and recommendation smoke tests.

### Supabase Migration
- `SupabasePromotionRepository` with PostgreSQL JSONB syntax (no `json_extract`).
- `cardsense.repository.mode` property + Spring profile switching (`local`=SQLite, `prod`=Supabase).
- `application-prod.properties` + Dockerfile with `SPRING_PROFILES_ACTIVE=prod`.
- HikariCP connection pooling for Supabase JDBC.

### Benefit Plan Switching
- `BenefitPlan` entity + `JsonBenefitPlanRepository` (reads `benefit-plans.json`).
- `DecisionEngine` plan-aware grouping: selects best plan per card within `exclusiveGroup`.
- `GET /v1/cards/{cardCode}/plans` endpoint.
- `CardRecommendation.activePlan` in response DTO.
- Filter expired benefit plans from scoring.
- CATHAY CUBE (7 plans) + TAISHIN RICHART plan configs.

### Bug Fixes (Resolved)
- **POINTS rate semantics**: `RewardCalculator` uses `POINTS_FIXED_BONUS_THRESHOLD = 30` to distinguish percentage (< 30) from fixed bonus (>= 30). Extractor 可考慮明確區分 `POINTS_RATE` vs `POINTS_BONUS`。
- **Bitmask >15 promotion degradation**: Replaced silent rank-#1 fallback with smart truncation (sort by `cappedReturn` desc, take top 5, O(2^5)=32).

## Next

### High Priority
- Extractor 寫入 Supabase 完成後，確認 `promotion_current` 表已存在於 Supabase prod 環境。

### Medium Priority
- Persist `stackability` as explicit columns instead of relying on `raw_payload_json` fallback parsing.
- Extend integration coverage with real ESUN and CATHAY stacked-promotion fixtures that mirror extractor output.
- Define point-to-cash normalization rules for `POINTS` reward types per bank.

### Low Priority
- Add explainability notes for why a promotion was excluded from a stack set beyond the current generic breakdown message.
- Decide whether `compareCardCodes` should enforce ranking scope only or also constrain break-even candidate pairs.

## Frontend Integration Notes

Frontend is live and consuming these stable endpoints:
- `GET /health`
- `GET /v1/cards?bank=...&scope=...`
- `GET /v1/cards/{cardCode}/promotions`
- `GET /v1/cards/{cardCode}/plans`
- `POST /v1/recommendations/card`

Stable response fields: `comparison.mode`, `recommendations[].estimatedReturn`, `promotionBreakdown`, `disclaimer`, `activePlan`.

`/calc` page constraints (implemented):
- Reuses existing endpoints only; no dedicated calculator endpoint or new DB table.
- Annual loss math, card validation, sharing flow, CTA param forwarding all in frontend layer.

Advanced UI (break-even visualization, multi-promotion stacking display) behind feature flag until fixtures locked.
