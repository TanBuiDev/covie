# CoVie V1 — Project Status Dashboard

**Last verified:** 2026-08-09  
**Overall status:** Planning and UI design complete; implementation not started.  
**Current implementation plan:** Plan 01 — Foundation & Repository.

## Purpose

This is the single progress dashboard for CoVie V1. It records completed work only when there is reviewable evidence: a commit or pull request, fresh verification output, and the relevant plan acceptance gate. Detailed task checklists remain in the individual technical plans.

## Source-of-truth order

1. Business scope and user flows: Product Plan, User Flow, and IA/UI Flow.
2. Data and HTTP contracts: Database Design and API Design.
3. Engineering sequence and acceptance: Master Technical Plan and Plan 01–10.
4. Visual implementation reference: the frozen CoVie V1 Figma file.

If these artifacts conflict, follow the precedence rules in `CoVie-START-HERE-v1.0.md`; record the resolution below before implementation continues.

## Current snapshot

| Area | Status | Evidence / note |
|---|---|---|
| Product, UX, database, API and technical planning | Complete | Approved documents in `docs/` |
| Figma foundations, components and P0 screens | Complete | Figma cover records 37/37 production units `FROZEN`; cross-screen QA and critical prototypes `PASS` |
| Repository implementation | Not started | Repository currently contains design/planning documents only; no `apps/`, `packages/` or `infra/` implementation tree |
| Current work | Ready | Plan 01 is the first executable plan |
| Production / pilot | Not started | Depends on successful completion of Plans 01–10 |

## Delivery checklist

| Plan | Scope | Status | Required evidence before marking complete |
|---|---|---|---|
| 01 | Foundation & Repository | Not started | Plan 01 acceptance gate green: local infra, API/web boot, OpenAPI client, CI |
| 02 | Identity, Auth & User | Blocked by Plan 01 | Authentication testing contract green |
| 03 | Merchant, Location, Media & Catalog | Blocked by Plan 02 | Guest discovery and seller catalog management work end-to-end |
| 04 | Shipping, Cart, Inventory & Checkout | Blocked by Plan 03 | Atomic order creation; concurrent last-stock test has one success and one conflict |
| 05 | Orders & Fulfillment | Blocked by Plan 04 | Product delivery, pickup and service critical transitions pass |
| 06 | Need Requests & Matching | Blocked by Plan 05 | Need fulfillment rules verified through source orders |
| 07 | Reviews, Reputation, Outbox, Notifications & SSE | Blocked by Plan 06 | Outbox retry cannot duplicate projection or notification |
| 08 | Moderation, Settings & Admin | Blocked by Plan 07 | Runtime policy change works; admin cannot force order state |
| 09 | Web Completion / PWA | Blocked by Plan 08 | Figma P0 journeys use generated API client and Playwright E2E passes |
| 10 | Production Hardening & Pilot Launch | Blocked by Plan 09 | Clean staging rehearsal, backup restore, smoke and rollback evidence pass |

## Cross-plan quality gates

- [ ] Every completed plan has a recorded commit/PR and fresh verification evidence.
- [ ] Flyway migrations are append-only and do not alter applied migrations.
- [ ] Backend OpenAPI and generated TypeScript client have no unreviewed drift.
- [ ] Frontend uses generated API types and does not duplicate business rules.
- [ ] Critical authorization, inventory concurrency, idempotency and order-state invariants are covered by automated tests.
- [ ] Figma P0 visual acceptance is completed during Plan 09.
- [ ] Security, backup/restore, monitoring, load and rollback gates are complete before pilot release.

## Active blockers and decisions

| Item | Impact | Owner / next action | Status |
|---|---|---|---|
| No implementation baseline exists | Plans 02–10 cannot start | Execute Plan 01 tasks 1–12 in order | Open |
| Figma cover references “Development Handoff → Plan 09” while technical docs set Plan 01 as current | Could cause sequencing confusion | Treat it as the UI implementation target; update Figma handoff wording when convenient | Open, non-blocking |
| Figma Code Connect requires a Dev or Full Figma seat | Automated component-to-code mapping is unavailable | Do not make Code Connect a prerequisite; create a manual screen/route/component/API handoff matrix during Plan 01 | Open, non-blocking |

## Update protocol

When a task or plan changes state:

1. Update the detailed checklist in its technical plan.
2. Add the commit/PR reference and exact verification commands/results to the relevant plan handoff.
3. Update this dashboard's plan status and evidence summary.
4. Record any blocked dependency or source-of-truth conflict in **Active blockers and decisions**.
5. Mark a plan complete only after its acceptance gate is green; then advance the current plan.

## References

- `CoVie-START-HERE-v1.0.md`
- `CoVie-technical-plan-v1.0.md`
- `CoVie-technical-plan-01-foundation-repository-v1.0.md` through `CoVie-technical-plan-10-production-hardening-pilot-launch-v1.0.md`
- `CoVie-figma-ui-design-system-implementation-plan-v1.0.md`
