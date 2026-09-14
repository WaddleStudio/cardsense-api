# CardSense Archive Decision

## Status

**Commercial No-Go**

**Technical Asset Retained**

**Archived: 2026-09**

Active consumer-product development, commercial validation, and promotion-data maintenance have stopped. This is the canonical decision for cardsense-api, cardsense-extractor, cardsense-contracts, and cardsense-web; fleet-command records the same status.

> Product thesis was invalidated before further investment; engineering assets are intentionally retained.

## Why development stopped

The owner closed the product thesis on these grounds; this record does not reopen market evaluation:

- Taiwan's consumer credit-card optimization market is mature and competitive.
- Recommendation, wallet, reward-cap tracking, registration, and benefit-plan switching offer insufficient differentiation.
- Public promotion data does not provide a defensible data moat.
- Bank redesigns, anti-bot controls, APIs, and changing reward conditions impose high extractor maintenance costs.
- Cross-bank transaction ingestion introduces structural consumer-onboarding friction, including email, encrypted statements, PDFs, screenshots, and notifications.
- Expected product value does not justify the opportunity cost of further investment. Reward leakage alone does not establish a CardSense competitive advantage.

## What was successfully built

### Extractor

Multi-bank heterogeneous extraction for E.SUN, CATHAY, TAISHIN, FUBON, and CTBC; normalization and validation; stable promotion identity and versioning through `promotion_versions` and `promotion_current`; source URLs and text hashes for traceability; structured conditions; benefit-plan inference; atomic publishing and Supabase synchronization.

Reference: [Extractor README](https://github.com/WaddleStudio/cardsense-extractor#readme), `extractor/normalize.py`, `versioning.py`, `db_store.py`, `supabase_store.py`, and retained tests. The local-only CATHAY source-audit design is historical, not an implemented capability.

### API

Deterministic recommendation and explainable ranking with minimum spend, maximum cashback/caps, registration requirements, reported benefit usage, eligibility, stackability, benefit-plan switching, and break-even analysis. Repository adapters preserve mock/SQLite/Supabase separation.

Reference: [API README](README.md), `src/main/java/com/cardsense/api/service/DecisionEngine.java`, `RewardCalculator.java`, and tests. Existing recommendation request/response audit logging is retained engineering infrastructure; it is not a consumer reward-leakage audit or a reason to resume the abandoned pivot.

### Contracts

Cross-repository schemas, enums, promotion/recommendation contracts, taxonomies, merchant registry, stackability metadata, and benefit-plan definitions.

Reference: [Contracts README](https://github.com/WaddleStudio/cardsense-contracts#readme).

### Web

Scenario calculator, My Wallet, reward ranking, card catalog, promotion explanations, benefit-plan controls, and responsive UI.

Reference: [Web README](https://github.com/WaddleStudio/cardsense-web#readme). These describe retained implementations, not a maintained live service.

## What will NOT be built

- Transaction ingestion, transaction ledger, or Gmail/email ingestion.
- PDF/encrypted statement parsing, CSV import, or screenshot/notification ingestion.
- Reward leakage audit or merchant transaction reconciliation/mapping expansion.
- Reminder/guardian features or additional bank coverage.
- Production promotion refresh, including Q3/Q4 catch-up or the CATHAY source-audit PoC.
- Commercial monetization work or renewed product validation.
- Recommendation-engine rewrites or architecture redesign to make the portfolio appear more complete.

## Reuse value

The deterministic rules engine, heterogeneous extraction patterns, normalization/versioning pipeline, cross-repo contracts, and explainable decision engine remain reusable. Existing source, tests, fixtures, schemas, deployment scripts, and historical designs are retained. Reuse requires its own scope and validation; archived promotion values must not be represented as current financial guidance.

## Portfolio context

**Problem:** Taiwan credit-card promotions have highly heterogeneous conditions that are difficult to compare mechanically.

**Engineering challenge:** Convert different banks' unstructured material into normalized, versioned, traceable promotion rules.

**Solution:** Extractor → Contracts → deterministic decision engine → Web UI.

**Engineering strengths:** Heterogeneous extraction, schema normalization, deterministic domain rules, versioning, explainability, multi-repo contracts, and full-stack delivery.

**Product lesson:** Substantial engineering investment does not replace evidence about competition, data-acquisition friction, and maintenance economics when deciding to stop a product.

## Reopening criteria

Reassessment requires concrete evidence of one or more of the following, together with actual user or partner demand:

- A proprietary/privileged data source CardSense can access that major competitors cannot.
- Low-friction, lawful, reliable cross-bank transaction ingestion.
- A specific distribution wedge that existing competitors do not address.
- Actual user/partner demand rather than another feature idea.

Absent that evidence, remain archived. A new feature idea or the presence of reward leakage is insufficient.

> No feature work should resume unless the reopening criteria in ARCHIVED.md are met.

## Operations and historical data

Datasets, `ACTIVE` flags, valid-until dates, examples, and past test/production reports are historical snapshots; none guarantees current bank offers. Do not run real extraction, refresh, sync, or production migrations as an archive verification step.

Repository configuration is only part of shutdown. External services, billing, jobs, integrations, and existing deployments have not been stopped by this local change. See [shutdown checklist](docs/ARCHIVE_SHUTDOWN_CHECKLIST.md) and [closure report](docs/2026-09-cardsense-closure.md). Cross-repository GitHub links resolve after the owner publishes this closure; in this multi-root workspace the canonical file is `../cardsense-api/ARCHIVED.md`.
