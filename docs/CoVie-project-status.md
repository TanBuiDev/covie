# CoVie V1 — Project Status Dashboard

**Last verified:** 2026-08-09  
**Overall status:** Planning, UI design and Plan 01 foundation complete; Plan 02 is ready to begin.
**Current implementation plan:** Plan 02 — Identity, Auth & User.

## Purpose

This is the single progress dashboard for CoVie V1. It records completed work only when there is reviewable evidence: a commit or pull request, fresh verification output, and the relevant plan acceptance gate. Detailed task checklists remain in the individual technical plans.

## Current snapshot

| Area | Status | Evidence / note |
|---|---|---|
| Product, UX, database, API and technical planning | Complete | Approved documents in `docs/` |
| Figma foundations, components and P0 screens | Complete | Figma cover records 37/37 production units `FROZEN`; cross-screen QA and critical prototypes `PASS` |
| Repository implementation | Foundation complete | Plan 01 acceptance passed on `codex/plan-01-foundation` (commit `4590072`) |
| Production / pilot | Not started | Depends on successful completion of Plans 01–10 |

## Delivery checklist

| Plan | Scope | Status | Required evidence before marking complete |
|---|---|---|---|
| 01 | Foundation & Repository | Complete | Local infra, API/web boot, OpenAPI client and CI acceptance gate green (2026-08-09) |
| 02 | Identity, Auth & User | Ready | Authentication testing contract green |
| 03 | Merchant, Location, Media & Catalog | Blocked by Plan 02 | Guest discovery and seller catalog management work end-to-end |
| 04 | Shipping, Cart, Inventory & Checkout | Blocked by Plan 03 | Atomic order creation; concurrent last-stock test has one success and one conflict |
| 05 | Orders & Fulfillment | Blocked by Plan 04 | Product delivery, pickup and service critical transitions pass |
| 06 | Need Requests & Matching | Blocked by Plan 05 | Need fulfillment rules verified through source orders |
| 07 | Reviews, Reputation, Outbox, Notifications & SSE | Blocked by Plan 06 | Outbox retry cannot duplicate projection or notification |
| 08 | Moderation, Settings & Admin | Blocked by Plan 07 | Runtime policy change works; admin cannot force order state |
| 09 | Web Completion / PWA | Blocked by Plan 08 | Figma P0 journeys use generated API client and Playwright E2E passes |
| 10 | Production Hardening & Pilot Launch | Blocked by Plan 09 | Clean staging rehearsal, backup restore, smoke and rollback evidence pass |

## Update protocol

1. Update the detailed checklist in its technical plan.
2. Add the commit/PR reference and exact verification commands/results to the relevant plan handoff.
3. Update this dashboard's plan status and evidence summary.
4. Record blocked dependencies or source-of-truth conflicts in the relevant plan handoff.
5. Mark a plan complete only after its acceptance gate is green; then advance the current plan.
