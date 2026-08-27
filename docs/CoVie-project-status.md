# CoVie V1 — Project Status Dashboard

**Last verified:** 2026-08-28
**Overall status:** Buyer Mobile/Desktop, Seller Mobile/Desktop and Admin Desktop remote implementations are integrated locally on `codex/integrated-ui-completion`. The integration is not yet remotely reviewed: the baseline has 13 Vitest failures and 11 lint errors, while typecheck and production build pass. Exact-frame runtime acceptance, authenticated staging journeys and release acceptance remain open.
**Current implementation plan:** Integrated UI completion — see `superpowers/plans/2026-08-28-complete-integrated-ui.md`.

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
| Web UI implementation against Figma | Integration hardening in progress | All submitted Buyer Mobile/Desktop, Seller Mobile/Desktop and Admin Desktop UI branches are present locally. The earlier Public Buyer evidence remains valid only for its recorded frames; integrated responsive semantics, Seller/Admin runtime acceptance and contract-blocked units remain open. The fresh baseline is documented in `integrated-ui-verification-2026-08-28.md`. |
| Repository implementation | Integrated locally through all submitted UI branches | `codex/integrated-ui-completion` contains Buyer tip `c69061f`, Seller tip `3aa53ae`, and Admin tip `e81809e`; it is 240 commits ahead of `origin/main`. No integration push or PR has been performed. Flyway migrations remain sequential from `V001` through `V013`. |
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
| 09 | Web Completion / PWA | Integrated UI hardening in progress | All submitted role/viewport UI branches are local. Fresh integrated evidence: typecheck/build pass; 106/119 Vitest tests pass with 13 failures; lint has 11 errors and 1 warning. Chunk 1 must close these P0 gates before visual acceptance proceeds. |
| 10 | Production Hardening & Pilot Launch | Closed — carry-over required | Staging rehearsal and release evidence are open |

## Public Buyer P0 local acceptance — 2026-08-26

- `pnpm ui:verify-local`: 1/1 populated Playwright journey passed on the first checkout commit; the generated credential remains only in ignored `.local/dev/ui-fixture.json`.
- Visual evidence: `.local/dev/visual-review/` contains Home, Results, Product Detail, Shop Profile, Address List, Service Detail, Resale Detail, Multi-cart, Cart Detail, Checkout Preview and Checkout Success at 390×844, 360×800 and 1280×900; Shop has a bottom-scrolled review and Address List has an inline-delete review at 390×844.
- Recurrence guards added for CSRF rotation, pre-hydration credential submission, server-side catalog URL resolution, checkout navigation overlap, first-page order queries and the fixture's UTF-8/category/cart invariants.
- Fresh repository gates: API `460` passed / `1` skipped before the UI-only batches; web typecheck, `103/103` tests and production build pass; standard E2E `2` passed / `1` fixture-gated skip; populated E2E `1/1`; launcher and fixture regression scripts exited `0`.

## Update protocol

1. Treat the Plan 01–10 checklists as historical specifications; do not tick them retroactively.
2. Add the commit/PR reference and exact verification commands/results to the relevant handoff or closure audit.
3. Update this dashboard and `AGENTS.md` in the same change whenever implementation status changes.
4. Record blocked dependencies and source-of-truth conflicts in the relevant handoff.
5. Do not label a plan release-accepted until its required environment gate is green and evidence is recorded.
