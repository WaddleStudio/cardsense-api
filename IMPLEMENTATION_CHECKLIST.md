# CardSense API Implementation Checklist

Updated: 2026-03-20

## Done
- Resolve scenario-driven recommendation requests with backward-compatible top-level fields.
- Support card-level comparison modes `BEST_SINGLE_PROMOTION` and `STACK_ALL_ELIGIBLE`.
- Return `scenario`, `comparison`, `promotionBreakdown`, and break-even metadata in recommendation responses.
- Filter out `CATALOG_ONLY` promotions from recommendation ranking while keeping `/v1/cards` scope-aware.
- Read `promotion.stackability` metadata from SQLite `raw_payload_json` and use it in `DecisionEngine` for deterministic stacking.
- Provide importable Postman collection assets for health, cards, and recommendation smoke tests.

## Next API Tasks
- Persist `stackability` as explicit SQLite columns or normalized JSON columns instead of relying on `raw_payload_json` fallback parsing.
- Extend integration coverage with real ESUN and CATHAY stacked-promotion fixtures that mirror extractor output.
- Add explainability notes for why a promotion was excluded from a stack set beyond the current generic breakdown message.
- Define point-to-cash normalization rules for `POINTS` reward types per bank.
- Decide whether `compareCardCodes` should enforce ranking scope only or also constrain break-even candidate pairs.

## Frontend Readiness Gate
- Frontend can start once ESUN and CATHAY recommendation responses are treated as contract-stable for the first slice.
- Minimum frontend-ready scope:
  - `GET /health`
  - `GET /v1/cards?bank=...&scope=...`
  - `POST /v1/recommendations/card`
  - stable `comparison.mode`, `recommendations[].estimatedReturn`, `promotionBreakdown`, and disclaimer fields
- Recommended timing:
  - Start frontend now for catalog browsing, recommendation form, and result explanation UI.
  - Keep advanced UI for break-even analysis and multi-promotion visualization behind a feature flag until ESUN/CATHAY fixtures are locked.

## PostgreSQL / Supabase Timing
- Do not block frontend on PostgreSQL.
- Move to PostgreSQL / Supabase when at least one of these becomes true:
  - multiple frontend environments need shared read data
  - recommendation audit logs must be queryable outside the local app process
  - extractor imports need centralized storage instead of local SQLite files
  - hosted frontend or API deployment needs shared persistence
- Recommended sequence:
  1. Freeze the first frontend-facing API contract.
  2. Finish ESUN/CATHAY end-to-end smoke coverage.
  3. Introduce PostgreSQL schema parity with `promotion_current` and audit tables.
  4. Add repository abstraction tests that run against both SQLite and PostgreSQL.
  5. Only then switch hosted environments to Supabase-backed reads.

## Delivery Slice Recommendation
1. Finish stackability rollout tests for ESUN and CATHAY fixtures.
2. Start frontend with the current API contract and Postman collection as the shared smoke baseline.
3. Plan PostgreSQL / Supabase after frontend contract feedback, not before.