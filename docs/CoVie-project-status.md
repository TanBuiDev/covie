# CoVie V1 — Project Status Dashboard

**Last verified:** 2026-08-26
**Overall status:** Plans 01–10 are integrated on `main` for planning closure. The requested Public Buyer P0 route chain has populated local flow and screenshot acceptance for PUB-001, PUB-003…007 and BUY-001/002/005/006; remaining Figma units, authenticated staging journeys and release acceptance remain open.
**Current implementation plan:** Verification and carry-over hardening — see `CoVie-plan-closure-audit-2026-08-24.md`.

## Purpose

This is the single progress dashboard for CoVie V1. It records completed work only when there is reviewable evidence: a commit or pull request, fresh verification output, and the relevant plan acceptance gate.

## Source-of-truth order

1. Business scope and user flows: Product Plan, User Flow, and IA/UI Flow.
2. Data and HTTP contracts: Database Design and API Design.
3. Engineering sequence and acceptance: Master Technical Plan and Plan 01–10.
4. Visual implementation reference: the frozen CoVie V1 Figma file.

## Current snapshot

| Area | Status | Evidence / note |
|---|---|---|
| Product, UX, database, API and technical planning | Complete | Approved documents in `docs/` |
| Figma design source | Complete | Figma cover records 37/37 production units `FROZEN`; cross-screen QA and critical prototypes `PASS` |
| Web UI implementation against Figma | In progress | PUB-001, PUB-003…007 and BUY-001/002/005/006 use exact frozen frames, exported SVG icons, Inter/Emerald tokens and buyer navigation. `pnpm ui:verify-local` passed the populated Home → Search → Product Detail → Service Detail → Cart → Checkout → Success → Orders journey and captured 390×844, 360×800 and 1280×900 comparisons on 2026-08-26. Other Figma units remain open. |
| Repository implementation | Integrated through Plan 10 | `main` and `origin/main` point to `42693a6` (`merge: close plans 01-10`); Flyway migrations are sequential from `V001` through `V013`. |
| Local development launcher | Fixed and cold-start verified | `pnpm dev` now runs Maven Wrapper from `apps/api` and derives `JAVA_HOME` from `java` on `PATH` when unset. Regression script passed; API and web returned HTTP 200 after a clean launcher start on 2026-08-25. |
| Identity, auth and user implementation | Complete | Plan 02 Task 16 fresh gate: backend `clean verify` (84 tests), regenerated OpenAPI/client, web lint/typecheck/test/build (20 web tests), Flyway V001–V003 and secret hygiene checks (2026-08-12) |
| Production / pilot | Not started | No staging or production environment has been provisioned or deployed. It depends on the carry-over verification and rehearsal gates. |

## Delivery checklist

| Plan | Scope | Status | Required evidence before marking complete |
|---|---|---|---|
| 01 | Foundation & Repository | Complete | Local infra, API/web boot, OpenAPI client and CI acceptance gate green (2026-08-09) |
| 02 | Identity, Auth & User | Complete | Task 16 fresh CI/documentation/acceptance gate is green (2026-08-12) |
| 03 | Merchant, Location, Media & Catalog | Closed — locally verified | Included in the integrated backend regression scope |
| 04 | Shipping, Cart, Inventory & Checkout | Closed — locally verified | Included in integrated regression, migration and concurrency scope |
| 05 | Orders & Fulfillment | Closed — locally verified | `V010` and lifecycle coverage are integrated |
| 06 | Need Requests & Matching | Closed — locally verified | `V011` and focused concurrency/query gates are integrated |
| 07 | Reviews, Reputation, Outbox, Notifications & SSE | Closed — locally verified | `V012`, notification/outbox behavior and regression coverage are integrated |
| 08 | Moderation, Settings & Admin | Closed — locally verified | `V013`, authorization and contract coverage are integrated |
| 09 | Web Completion / PWA | Closed for planning — UI carry-over in progress | Requested Public Buyer P0 through PUB-007/BUY-006 plus the tested PUB-008 first pass: API client/typecheck, build and 94 unit tests pass (2026-08-26); lint retains 2 pre-existing warnings and 0 errors. PUB-008 populated screenshot acceptance and staging P0 journeys remain open. |
| 10 | Production Hardening & Pilot Launch | Closed — carry-over required | Staging rehearsal and release evidence are open |

## Public Buyer P0 local acceptance — 2026-08-26

- `pnpm ui:verify-local`: 1/1 populated Playwright journey passed on the first checkout commit; the generated credential remains only in ignored `.local/dev/ui-fixture.json`.
- Visual evidence: `.local/dev/visual-review/` contains Home, Results, Product Detail, Service Detail, Multi-cart, Cart Detail, Checkout Preview and Checkout Success at 390×844, 360×800 and 1280×900.
- Recurrence guards added for CSRF rotation, pre-hydration credential submission, server-side catalog URL resolution, checkout navigation overlap, first-page order queries and the fixture's UTF-8/category/cart invariants.
- Fresh repository gates: API `460` passed / `1` skipped; web `94/94`; standard E2E `2` passed / `1` fixture-gated skip; populated E2E `1/1`; production build passed; launcher and fixture regression scripts exited `0`.

## Update protocol

1. Treat the Plan 01–10 checklists as historical specifications; do not tick them retroactively.
2. Add the commit/PR reference and exact verification commands/results to the relevant handoff or closure audit.
3. Update this dashboard and `AGENTS.md` in the same change whenever implementation status changes.
4. Record blocked dependencies and source-of-truth conflicts in the relevant handoff.
5. Do not label a plan release-accepted until its required environment gate is green and evidence is recorded.
