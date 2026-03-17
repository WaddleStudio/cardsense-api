# CardSense API — VIBE_SPEC

## Purpose
This repository implements the **external decision API**
for CardSense.

It reads normalized promotions and returns
**deterministic, explainable credit card recommendations**.

---

## Scope (DO)
- REST API
- Deterministic decision engine
- API key authentication
- Audit logging
- Explainable responses

## Out of Scope (DO NOT)
- No crawling or extraction
- No LLM in request path
- No user transaction ingestion (MVP)
- No promotion full-text API

---

## Required Endpoints

POST /v1/recommendations/card
GET /v1/cards
GET /health


---

## Recommendation Rules

- Only consider active promotions
- Filter by:
  - date
  - category
  - channel
  - min_amount
- Calculate:
  - expected_reward
  - effective_rate
- Sort deterministically
- Return:
  - best recommendation
  - alternatives
  - reasons[]
  - requirements[]
  - evidence[promo_version_id]

---

## Required Project Structure

controller/
service/
├─ DecisionEngine.java
└─ RewardCalculator.java
domain/
repository/
audit/
security/


---

## Non-Functional Rules

- No database writes in request path (except audit)
- Same input MUST produce same output
- Responses MUST be explainable
- Local latency target < 300ms

---

## Audit Requirements

Each recommendation must record:
- request_id
- input summary
- selected promo_version_ids
- calculation result
- timestamp

---

## Success Criteria
- `/v1/recommendations/card` works with sample DB
- Recommendation output is reproducible
- Removing a promotion predictably changes result

---

## Agent Instructions
- You may only modify files in this repository
- You must respect cardsense-contracts schema
- Determinism > optimization > cleverness