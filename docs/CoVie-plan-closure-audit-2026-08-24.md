# CoVie V1 — Plan Closure Audit

**Date:** 2026-08-24
**Scope:** Plans 01–10
**Closure type:** planning closure and carry-over handoff; **not** an acceptance-complete or release-ready declaration.

## Integrated implementation

The integration branch `integration/plans-05-10` combines the remote Plan 09 and Plan 10 branches with the completed local Plan 05, Plan 07 and Plan 08 branches. The database migration chain is now sequential and complete from `V001` through `V013`.

| Plan | Planning status | Evidence / carry-over |
| --- | --- | --- |
| 01 Foundation | Closed — historically accepted | Existing foundation acceptance commit and repository baseline. |
| 02 Identity/Auth/User | Closed — historically accepted | Existing Plan 02 acceptance evidence. |
| 03 Merchant/Catalog | Closed — implementation integrated | Must be covered by the fresh full backend regression gate. |
| 04 Checkout/Inventory | Closed — implementation integrated | Must be covered by the fresh full backend regression and clean-migration gates. |
| 05 Orders/Fulfillment | Closed — implementation integrated, verification pending | `V010` and order lifecycle implementation are integrated. |
| 06 Need matching | Closed — implementation integrated, verification pending | `V011` and Need checkout/fulfillment implementation are integrated. |
| 07 Trust/Notifications/SSE | Closed — implementation integrated, verification pending | `V012__trust_notifications_outbox.sql` is integrated. |
| 08 Moderation/Settings/Admin | Closed — implementation integrated, verification pending | `V013__moderation_system_settings_admin.sql` is integrated. |
| 09 Web completion | Closed — carry-over required | Implementation is integrated, but its final acceptance gate is red. |
| 10 Production hardening/pilot | Closed — carry-over required | Implementation is integrated, but rehearsal and release gates have not been executed. |

## Fresh verification record

- `pnpm api-client:check`: passed.
- `pnpm web:typecheck`: failed. `app/(public)/explore/page.tsx` redirects without returning a JSX-compatible value, so tests importing `ExplorePage` fail type checking.
- `pnpm web:lint`: failed with six errors: JSX constructed in a `try/catch` in seller layout, and synchronous effect state updates in cart, discovery, browse-location and order-action components.
- `pnpm web:test`: failed: **9 test files / 10 tests**. Confirmed failure groups include public-discovery redirect handling, leaking DOM between address/shipping tests, stale-cart refetch assertion, selected-area coverage fixture/loading, and seller dashboard copy asserting against the word “doanh thu”.
- `apps/api/mvnw clean verify`: not executable on this workstation because `JAVA_HOME` is unset. Backend, migration, OpenAPI export and Plan 10 rehearsal gates therefore have no fresh result.

## Required next plan backlog

1. Provision JDK 25 and set `JAVA_HOME`; run `apps/api/mvnw clean verify` against PostgreSQL, then export OpenAPI and regenerate/check the API client.
2. Fix the Plan 09 web typecheck, lint and failing test suites; make test cleanup/fixtures deterministic.
3. Complete Plan 09 carry-over surfaces now unblocked by Plans 07–08: review/report, notification inbox/SSE invalidation, admin control plane, generated Need client migration, TanStack Query/RHF integration and Playwright P0 journeys.
4. Run Plan 04–08 focused concurrency, idempotency, migration and authorization acceptance gates after backend verification is restored.
5. Execute Plan 10 staging rehearsal: clean V001–V013 migration, image/security/SBOM checks, backup-restore, performance/query-plan gates, deploy/rollback smoke and pilot launch checklist.

## Release rule

Do not merge this integration branch into `main`, call any plan acceptance-complete, or start pilot deployment until the listed verification gates are green and their command output is recorded.
