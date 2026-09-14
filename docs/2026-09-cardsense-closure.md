# CardSense Closure — 2026-09

## Decision

**Commercial No-Go / Technical Asset Retained**. Inspected 2026-09-14; verification and closeout continued into 2026-09-15 (Asia/Taipei).

[ARCHIVED.md](../ARCHIVED.md) is the canonical decision. Product development, commercial validation, and promotion maintenance are stopped as project policy. Runtime source, data snapshots, and engineering designs are retained. External infrastructure shutdown remains pending; no production shutdown is claimed.

## Initial workspace inventory

The provided `D:/Projects/fleet-command` directory did not exist. The actual control-plane repository is `D:/Projects/cardsense-workspace/fleet-command`; the four CardSense repos are siblings. The workspace root itself is not a common Git repository, so the canonical archive and this report live in cardsense-api.

`git fetch origin` refreshed remote refs without pruning, merging, or changing remote branches. `gh pr list --state open --json number,title` and `gh issue list --state open --json number,title`, with `--repo WaddleStudio/<repo>`, returned `[]` for each of the four primary repositories. Provider runtimes/billing were not inspected.

| Repo | Initial branch / local HEAD | Default branch / fetched HEAD | Initial working tree / divergence |
|------|-----------------------------|-------------------------------|------------------------------------|
| cardsense-api | master / 854d4ce | origin/master / 1035b4f | Clean; local master behind 1 commit |
| cardsense-extractor | master / b71aabe | origin/master / 8d0aef0 | Local master ahead 1, behind 1; untracked CATHAY PoC plan and test_qwen.py |
| cardsense-web | master / cb82b21 | origin/master / 9f594f6 | Clean; local master behind 1 commit; two additional clean worktrees |
| cardsense-contracts | master / bcce879 | origin/master / bcce879 | Clean; aligned |
| fleet-command | main / 7e20499 | origin/main / 7e20499 | Untracked handoffs/; one additional clean worktree |

Work began on new local `chore/cardsense-commercial-closure` branches from these existing local HEADs. Default branches were not advanced; remote data/feature commits were not merged. Verification therefore describes these retained local snapshots, not the latest remote runtime.

The extractor's local-only commit `b71aabe` contains the CATHAY source-audit design. Its design document is preserved and marked historical/cancelled. The pre-existing untracked `docs/superpowers/plans/2026-06-02-cathay-source-audit-poc.md`, `test_qwen.py`, and Fleet `handoffs/` are untouched and excluded from closure commits. The untracked plan is covered by the project-wide archive rule, not an active implementation commitment.

## Changes made

| Repo | Changes |
|------|---------|
| cardsense-api | Canonical ARCHIVED decision, shutdown checklist, this report; README status/stale-data banner; historical spec/checklist labels; generated archive context. Existing recommendation audit logging and SQL retained. |
| cardsense-extractor | README status and historical operations warning; historical design/plan labels, including cancelled CATHAY source-audit PoC; generated archive context. Extractors, refresh/import/sync entry points and tests retained. |
| cardsense-web | README status and deployment explanation; `vercel.json` disables Git auto-deployment for revisions adopting this setting; historical plan labels and generated archive context. UI and build/rewrite configuration retained. |
| cardsense-contracts | README status/stale-data banner, static historical VIBE_SPEC label and generated archive context. Schemas, examples and taxonomy retained. |
| fleet-command | Status/README record the archive; primary product review/spec/operations entry documents mark linked plans historical. Dashboard marks CardSense archived, preserves delivered work/history, cancels unfinished product roadmap items, and retains only external shutdown/credential follow-up actions. Manifest distributes the archive rule to generated contexts and corrects its missing status-document reference. Existing dashboard validation accepts archived/cancelled states. Agent log and Chrome evidence added. |

Dashboard classification: **status_update**, **open_queue_update**, **workspace_rule_update**. Other Fleet projects and generic workspace skills/rules are not retired.

## Infrastructure requiring manual shutdown

See [ARCHIVE_SHUTDOWN_CHECKLIST.md](ARCHIVE_SHUTDOWN_CHECKLIST.md) for service, evidence, suggested action, potential cost, and shutdown consequences.

- Confirm Vercel hosting/analytics/deploy hooks, Render or Railway API runtime, Supabase database/storage/functions/webhooks, and Cloudflare Browser Rendering.
- Inspect actual OS/agent cron, Task Scheduler, k3s CronJobs/runtime, keep-alive monitors and external integrations. Repository evidence does not prove any are active or stopped.
- Four root Actions workflows contain only event-driven secret scans; no refresh/deploy/scheduled sync workflow or application scheduler was found. Source protection remains enabled. Vendored tool workflows are not root Actions workflows.
- Manual extractor `refresh_and_deploy.py` still defaults to live extraction and potential Supabase publishing. No repository scheduler calls it. Its code is retained; any outside callers must be stopped in their actual scheduler.
- At initial local delivery the Vercel configuration was unpublished; publishing it still cannot stop existing deployments or billing. Nothing was deployed, pushed, merged, remotely archived, or deleted by this task. No credentials or production data were accessed.

## Product-direction search classification

Searched tracked first-party docs/source for `audit`, `leakage`, `transaction`, `Gmail`, `statement`, `PDF`, `CSV`, `reminder`, `upcoming`, `TODO`, `roadmap`, `planned`, and future-planning language. Vendored tools, generated output and unrelated Fleet projects were excluded from product-roadmap editing.

- **A — implemented technical assets retained:** request/response audit persistence, expiry auditing, transaction/scenario calculation, source traceability, SQL statement handling, schema/condition parsing, wallet/UI code, and their tests. A keyword match was not treated as a deletion request.
- **B — historical documents retained:** API/extractor/contracts VIBE_SPEC snapshots, API implementation checklist, tracked extractor/Web designs/plans, product reviews, source-review workflow and feedback integration instructions. Central Fleet entry notices also supersede linked historical CardSense plans. The June CATHAY design explicitly says implementation is cancelled.
- **C — future product commitments cancelled:** README future claims, 31–60/61–90-day plans, reward ledger/leakage/ingestion/reminder ideas, and active product action-queue entries. These remain historical descriptions where useful. No TODO was implemented; no pivot, bank, parser, ingestion flow, or engine rewrite was added.

## Branches safe to delete

No branches or worktrees were deleted. Decisions below compare fetched default refs with each branch, after checking working trees. `git rev-list --count <base>..<branch>` identifies unique ancestry; `git cherry <base> <branch>` distinguishes patch-equivalent commits. Default branches and the new closure branches are retained.

**cardsense-api `feat/recommendation-audits`: safe to delete.** Both local and remote-tracking refs point to `868b03f`; unique commits relative to local master and fetched origin/master are both zero. The root working tree was initially clean. Its implemented audit infrastructure is already retained in master; no audit pivot needs rescue.

**Keep / investigate:** contracts `origin/chore/secret-scanning-guardrail` has an independent `d3c3606` patch. Compared with origin/master it changes Gitleaks history-scan behavior and allowlist syntax; it is not marked safe to delete. Extractor's local-only June design commit and untracked work are preserved. Web's two non-ancestor fixes are patch-equivalent (`git cherry` reports `-`), so they add no independent patch. Checked-out legacy worktrees are clean; detach/remove those worktrees only in a separately authorized cleanup.

### Complete local / remote-tracking branch inventory

Refs below are a local snapshot after fetch. Remote-tracking refs may include old remote-deleted branches because fetch deliberately did not prune. A safe classification is about saved content, not permission to delete a remote branch.

#### cardsense-api

| Ref | Unique ancestry vs origin/master | Classification |
|-----|--------------------------|----------------|
| `chore/cardsense-commercial-closure` | 0 | retain default/closure branch |
| `chore/cube-japan-rewards` | 0 | safe to delete (saved commits included) |
| `chore/sync-merchant-registry-mobile-pay` | 0 | safe to delete (saved commits included) |
| `chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `chore/workspace-context-policy-refresh` | 0 | safe to delete (saved commits included) |
| `feat/payment-classification` | 0 | safe to delete (saved commits included) |
| `feat/recommendation-audits` | 0 | safe to delete (saved commits included) |
| `fix/p0-p1-decision-wedge` | 0 | safe to delete (saved commits included) |
| `master` | 0 | retain default/closure branch |
| `origin/chore/cube-japan-rewards` | 0 | safe to delete (saved commits included) |
| `origin/chore/secret-scanning-guardrail` | 0 | safe to delete (saved commits included) |
| `origin/chore/sync-merchant-registry-mobile-pay` | 0 | safe to delete (saved commits included) |
| `origin/chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `origin/chore/workspace-context-policy-refresh` | 0 | safe to delete (saved commits included) |
| `origin/feat/benefit-plan-switching` | 0 | safe to delete (saved commits included) |
| `origin/feat/payment-classification` | 0 | safe to delete (saved commits included) |
| `origin/feat/recommendation-audits` | 0 | safe to delete (saved commits included) |
| `origin/feature/todos-execution` | 0 | safe to delete (saved commits included) |
| `origin/fix/p0-p1-decision-wedge` | 0 | safe to delete (saved commits included) |
| `origin/master` | 0 | retain default/closure branch |

Stashes: stash@{0}; retained, contents not inspected.

#### cardsense-extractor

| Ref | Unique ancestry vs origin/master | Classification |
|-----|--------------------------|----------------|
| `chore/cardsense-commercial-closure` | 1 | retain default/closure branch |
| `chore/cube-japan-rewards` | 0 | safe to delete (saved commits included) |
| `chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `chore/workspace-context-policy-refresh` | 0 | safe to delete (saved commits included) |
| `feat/payment-classification` | 0 | safe to delete (saved commits included) |
| `fix/p0-p1-decision-wedge` | 0 | safe to delete (saved commits included) |
| `master` | 1 | retain default/closure branch |
| `origin/chore/cube-japan-rewards` | 0 | safe to delete (saved commits included) |
| `origin/chore/secret-scanning-guardrail` | 0 | safe to delete (saved commits included) |
| `origin/chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `origin/chore/workspace-context-policy-refresh` | 0 | safe to delete (saved commits included) |
| `origin/feat/benefit-plan-switching` | 0 | safe to delete (saved commits included) |
| `origin/feat/payment-classification` | 0 | safe to delete (saved commits included) |
| `origin/feature/supabase-sync` | 0 | safe to delete (saved commits included) |
| `origin/fix/p0-p1-decision-wedge` | 0 | safe to delete (saved commits included) |
| `origin/master` | 0 | retain default/closure branch |

Stashes: none.

#### cardsense-web

| Ref | Unique ancestry vs origin/master | Classification |
|-----|--------------------------|----------------|
| `agent/calc-wallet-rate-ui` | 0 | safe to delete (saved commits included) |
| `agent/my-wallet-remaining` | 0 | safe to delete (saved commits included); clean linked worktree still checked out |
| `bugfix/exchange-rates-panel-crash` | 0 | safe to delete (saved commits included) |
| `chore/cardsense-commercial-closure` | 0 | retain default/closure branch |
| `chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `chore/workspace-context-policy-refresh` | 0 | safe to delete (saved commits included) |
| `copy/localize-ui-text` | 0 | safe to delete (saved commits included) |
| `feat/merchant-first-calculator-flow` | 0 | safe to delete (saved commits included) |
| `feat/merchant-search-category-facet` | 0 | safe to delete (saved commits included) |
| `feature/calc-settings-two-column-layout` | 0 | safe to delete (saved commits included); clean linked worktree still checked out |
| `fix/checkout-result-receipts` | 1 | safe to delete (patch-equivalent) |
| `fix/p0-p1-decision-wedge` | 0 | safe to delete (saved commits included) |
| `fix/reward-gap-checkout-style` | 1 | safe to delete (patch-equivalent) |
| `master` | 0 | retain default/closure branch |
| `origin/agent/calc-wallet-rate-ui` | 0 | safe to delete (saved commits included) |
| `origin/bugfix/exchange-rates-panel-crash` | 0 | safe to delete (saved commits included) |
| `origin/chore/secret-scanning-guardrail` | 0 | safe to delete (saved commits included) |
| `origin/chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `origin/chore/workspace-context-policy-refresh` | 0 | safe to delete (saved commits included) |
| `origin/copy/localize-ui-text` | 0 | safe to delete (saved commits included) |
| `origin/feat/benefit-plan-switching` | 0 | safe to delete (saved commits included) |
| `origin/feat/checkout-mode-decision-ui` | 0 | safe to delete (saved commits included) |
| `origin/feat/merchant-search-category-facet` | 0 | safe to delete (saved commits included) |
| `origin/feature/calc-settings-two-column-layout` | 0 | safe to delete (saved commits included) |
| `origin/fix/checkout-result-receipts` | 0 | safe to delete (saved commits included) |
| `origin/fix/p0-p1-decision-wedge` | 0 | safe to delete (saved commits included) |
| `origin/fix/reward-gap-checkout-style` | 1 | safe to delete (patch-equivalent) |
| `origin/master` | 0 | retain default/closure branch |

Stashes: none.

#### cardsense-contracts

| Ref | Unique ancestry vs origin/master | Classification |
|-----|--------------------------|----------------|
| `chore/cardsense-commercial-closure` | 0 | retain default/closure branch |
| `chore/cube-japan-rewards` | 0 | safe to delete (saved commits included) |
| `chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `chore/workspace-context-policy-refresh` | 0 | safe to delete (saved commits included) |
| `feat/payment-classification` | 0 | safe to delete (saved commits included) |
| `fix/p0-p1-decision-wedge` | 0 | safe to delete (saved commits included) |
| `master` | 0 | retain default/closure branch |
| `origin/chore/cube-japan-rewards` | 0 | safe to delete (saved commits included) |
| `origin/chore/secret-scanning-guardrail` | 1 | retain: independent patch/history requires review |
| `origin/chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `origin/chore/workspace-context-policy-refresh` | 0 | safe to delete (saved commits included) |
| `origin/feat/benefit-plan-switching` | 0 | safe to delete (saved commits included) |
| `origin/feat/payment-classification` | 0 | safe to delete (saved commits included) |
| `origin/fix/p0-p1-decision-wedge` | 0 | safe to delete (saved commits included) |
| `origin/master` | 0 | retain default/closure branch |

Stashes: none.

#### fleet-command

| Ref | Unique ancestry vs origin/main | Classification |
|-----|--------------------------|----------------|
| `agent/my-wallet-remaining` | 0 | safe to delete (saved commits included); clean linked worktree still checked out |
| `chore/cardsense-commercial-closure` | 0 | retain default/closure branch |
| `chore/cube-japan-rewards` | 0 | safe to delete (saved commits included) |
| `chore/fleet-dashboard-closeout-flow` | 0 | safe to delete (saved commits included) |
| `chore/kernel-skeleton` | 0 | safe to delete (saved commits included) |
| `chore/kernel-spec` | 0 | safe to delete (saved commits included) |
| `chore/manifest-schema-v2` | 0 | safe to delete (saved commits included) |
| `chore/status-update-payment-classification` | 0 | safe to delete (saved commits included) |
| `chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `docs/cardsense-icard-ai-product-direction` | 0 | safe to delete (saved commits included) |
| `docs/cardsense-p0-p1-pr-status` | 0 | safe to delete (saved commits included) |
| `feat/bootstrap-teardown` | 0 | safe to delete (saved commits included) |
| `feat/merchant-first-calculator-flow` | 0 | safe to delete (saved commits included) |
| `feat/merchant-search-category-facet` | 2 | retain: independent patch/history requires review |
| `main` | 0 | retain default/closure branch |
| `origin/chore/cube-japan-rewards` | 0 | safe to delete (saved commits included) |
| `origin/chore/fleet-dashboard-closeout-flow` | 0 | safe to delete (saved commits included) |
| `origin/chore/kernel-skeleton` | 0 | safe to delete (saved commits included) |
| `origin/chore/kernel-spec` | 0 | safe to delete (saved commits included) |
| `origin/chore/manifest-schema-v2` | 0 | safe to delete (saved commits included) |
| `origin/chore/secret-scanning-guardrail` | 0 | safe to delete (saved commits included) |
| `origin/chore/status-update-payment-classification` | 0 | safe to delete (saved commits included) |
| `origin/chore/workspace-context-manifest` | 0 | safe to delete (saved commits included) |
| `origin/docs/cardsense-icard-ai-product-direction` | 0 | safe to delete (saved commits included) |
| `origin/docs/cardsense-p0-p1-pr-status` | 0 | safe to delete (saved commits included) |
| `origin/feat/bootstrap-teardown` | 0 | safe to delete (saved commits included) |
| `origin/feat/checkout-mode-decision-ui` | 0 | safe to delete (saved commits included) |
| `origin/feat/merchant-search-category-facet` | 2 | retain: independent patch/history requires review |
| `origin/main` | 0 | retain default/closure branch |

Stashes: none.

## Tests executed

Checks used local fixtures, mocks and temporary databases. No bank extraction, live recommendation request, production sync or webhook message was executed.

| Repo | Command / scope | Result |
|------|-----------------|--------|
| API | `mvn -B -Dtest=AuditServiceTest,HealthControllerTest,RecommendationControllerTest,JsonBenefitPlanRepositoryTest,SqlitePromotionRepositoryTest,CatalogServiceTest,DecisionEngineBenefitPlanTest,DecisionEngineTest,ExchangeRateServiceTest,RewardCalculatorTest test` | BUILD SUCCESS; 93 tests, 0 failures/errors/skips. Maven also ran compile/testCompile phases. |
| Extractor | `uv run --frozen python <temporary offline test wrapper>`; wrapper runs `pytest.main(["tests", "-q", "--tb=short"])` with dotenv disabled and socket connections blocked | 170 passed in 2.70s. Existing lockfile dependencies installed into a temporary Windows venv; no lockfile change. |
| Web | `npm.cmd run lint` | Existing failure: 17 errors. Five `react-refresh/only-export-components` violations in current `src/components/ui/{badge,button,form,tabs,toggle}.tsx`; twelve in two old worktrees (same five plus AnnualLossBox effect in each). Application source/ESLint config unchanged. |
| Web | `npm.cmd run build` | PASS: TypeScript and Vite production build. No deployment performed. |
| Contracts | `node -` with JSON.parse and existing Web `ajv` dependency; validateSchema/addSchema/getSchema for every tracked schema | PASS: 15 JSON files parsed; all 4 Draft-07 schemas validated and compiled. No runtime/test/build script is provided by the contracts repo. |
| Fleet | `uv run python -m unittest tests.test_dashboard_data tests.test_render_workspace_assets` | 10 tests passed. |
| Fleet | `uv run python scripts/render_workspace_assets.py --check` | PASS: generated workspace assets up to date. |
| Fleet | `uv run python -m http.server 5177 --bind 127.0.0.1` from Fleet root; installed Google Chrome headless via Playwright `executable_path`, local traffic only | PASS: HTTP 200; title correct; loadError hidden; all 4 JSON files load; 5 repo cards, 4 archived CardSense cards, 3 historical roadmap columns, 2 shutdown actions, zero console/page errors. |

Browser evidence: [Chrome report](https://github.com/WaddleStudio/fleet-command/blob/main/reviews/2026-09-cardsense-closure/chrome-smoke.json) and [screenshot](https://github.com/WaddleStudio/fleet-command/blob/main/reviews/2026-09-cardsense-closure/dashboard.png). Local equivalents are under `../fleet-command/reviews/2026-09-cardsense-closure/` relative to the API repository root. These default-branch links resolve after the respective archive PRs merge; use task-branch links in the PR descriptions before merge.

### Verification exclusions and environment limits

The API `SqliteApiSmokeTest` and `CathaySqliteApiIntegrationTest` boot the full Spring context. `SupabaseDataSourceConfig` unconditionally creates a PostgreSQL pool for the default Supabase endpoint, so these were excluded to avoid external credentials/connections. Unit-level SQLite repository coverage still ran against temporary data.

Extractor tests were explicitly limited to `tests/`; real-fetch jobs and the user's untracked `test_qwen.py` were not collected. An initial `uv run --no-sync` attempt could not use the existing `.venv` and failed on its `lib64` entry with Windows access-denied; no manual recursive cleanup was attempted. The successful run used `$env:UV_PROJECT_ENVIRONMENT = Join-Path $env:TEMP 'cardsense-archive-venv-20260914'`. The original environment is not certified usable by this closure.

The temporary wrapper was:

```python
import os, socket
from unittest.mock import patch
os.environ["PYTHON_DOTENV_DISABLED"] = "1"
import pytest
with patch.object(socket.socket, "connect", side_effect=RuntimeError("Network disabled for archive verification")):
    raise SystemExit(pytest.main(["tests", "-q", "--tb=short"]))
```

Web build succeeds despite the existing lint debt; the report does not claim a clean full lint/test suite. The source-only archive changes do not fix external integration, current promotions, or old product defects.

## Technical assets retained

- Multi-bank extraction/normalization, stable IDs/hashes, structured conditions, benefit-plan inference, versioned SQLite storage and atomic Supabase publish.
- Deterministic minimum-spend/cap/registration/usage/eligibility/stackability rules, explainable ranking, plan switching, break-even math and existing recommendation audit infrastructure.
- Cross-repository contracts, enums, taxonomies, schemas and examples.
- Scenario calculator, My Wallet, reward ranking, catalog, promotion explanation and responsive UI.
- Tests, fixtures, architecture/design history, SQL and deployment scripts; no runtime source or production dataset was deleted.

## Known stale data

Promotion datasets are no longer guaranteed current. The historical May 16 counts in Fleet documentation describe a past local refresh; no fresh DB count or live-bank validation was performed here. Hardcoded dates, bank rules, ACTIVE flags, cached examples, valid-until values, and prior smoke results are snapshots. Local master branches can also differ from fetched remote heads as recorded above. Do not treat them as maintained Q3/Q4 bank offers.

## Future rule

> No feature work should resume unless the reopening criteria in ARCHIVED.md are met.

## Initial local delivery boundary

Changes are retained on local `chore/cardsense-commercial-closure` branches. Verified archive changes are committed separately by repository; pre-existing untracked work is excluded. Commit IDs and final worktree status are reported in the final handoff. No push, merge, PR creation, branch deletion, resource deletion, or production action is part of this delivery. External dashboard items remain pending owner action.

## Publication follow-up — 2026-09-15

After reviewing the local handoff, the owner authorized pushing the five task branches and creating cross-referenced PRs. The initial inventory, branch ancestry table, and test snapshot above remain historical evidence. Publication does not merge the PRs, stop external services, change secrets/data, or include pre-existing untracked work.

Pre-push verification repeated: API 93 tests passed; extractor 170 tests passed with network blocked; Web build passed; Fleet 10 tests and renderer check passed. Existing Web lint debt and full-context external API tests remain disclosed as above. No runtime source changed.

Task branches pushed and PRs created on 2026-09-15:

- [cardsense-api](https://github.com/WaddleStudio/cardsense-api/pull/9)
- [cardsense-extractor](https://github.com/WaddleStudio/cardsense-extractor/pull/7)
- [cardsense-web](https://github.com/WaddleStudio/cardsense-web/pull/16)
- [cardsense-contracts](https://github.com/WaddleStudio/cardsense-contracts/pull/6)
- [fleet-command](https://github.com/WaddleStudio/fleet-command/pull/14)

PRs remain open for review. This publication record does not certify CI results or external shutdown.
