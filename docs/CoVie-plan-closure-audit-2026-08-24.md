# CoVie V1 — Plan Closure Audit

**Date:** 2026-08-24
**Scope:** Plans 01–10
**Closure type:** planning closure and carry-over handoff. Local verification is recorded below; **not** a staging-acceptance or release-ready declaration.

## Integrated implementation

The integration branch `integration/plans-05-10` combines the remote Plan 09 and Plan 10 branches with the completed local Plan 05, Plan 07 and Plan 08 branches. The database migration chain is now sequential and complete from `V001` through `V013`.

| Plan | Planning status | Evidence / carry-over |
| --- | --- | --- |
| 01 Foundation | Closed — historically accepted | Existing foundation acceptance commit and repository baseline. |
| 02 Identity/Auth/User | Closed — historically accepted | Existing Plan 02 acceptance evidence. |
| 03 Merchant/Catalog | Closed — locally verified | Covered by the fresh full backend regression gate. |
| 04 Checkout/Inventory | Closed — locally verified | Covered by the fresh full backend regression, clean migration and PostgreSQL concurrency gates. |
| 05 Orders/Fulfillment | Closed — carry-over implemented and verified | `V010` and the order lifecycle are integrated. Post-confirmation merchant cancellation is implemented and covered by unit and HTTP/integration tests. |
| 06 Need matching | Closed — locally verified | `V011` Need checkout/fulfillment and focused PostgreSQL concurrency/query gates are covered by the fresh regression. |
| 07 Trust/Notifications/SSE | Closed — locally verified | `V012__trust_notifications_outbox.sql`, notification/outbox behavior and regression coverage are green locally. |
| 08 Moderation/Settings/Admin | Closed — locally verified | `V013__moderation_system_settings_admin.sql`, authorization and contract coverage are green locally. |
| 09 Web completion | Closed — local verification passed; staging carry-over | Typecheck, lint, build, unit and guest Playwright flows are green. Normal-auth/privileged staging P0 remains required. |
| 10 Production hardening/pilot | Closed — local verification passed; staging carry-over | Local migration, restore, image, security-source, load/soak, observability and rollback assets are verified. The real HTTPS rehearsal/release decision remains required. |

## Remote reconciliation — 2026-08-25

`git fetch --prune --tags` found no commit on `origin/main` that is absent from this integration branch. The only remote-only Plan 06 commits (`6a00980`, `5756bb7`, `96f438c`) add and then remove a temporary status-note workflow and add a progress note; their net code diff is documentation only. Their listed local gates are superseded by the verification record below. Remote Plan 09 and Plan 10 branches contain no commit absent from the integration branch.

## Release rule

Do not merge this integration branch into `main`, call any plan acceptance-complete, or start pilot deployment until the listed verification gates are green and their command output is recorded.

## Verification update — local staging-equivalent, 2026-08-25

This update records observed command results. It does not replace the HTTPS staging rehearsal or operator approval required by Plan 10.

| Area | Result | Evidence |
| --- | --- | --- |
| Backend and migrations | Pass | `apps/api/mvnw clean verify`: 457 tests, 0 failures/errors, 1 intentionally skipped; clean V001–V013 sequence and Flyway validation pass on restored DB. The test profile caps Hikari at four connections with no idle floor so the shared Testcontainers database cannot exhaust its client limit across cached Spring contexts. |
| API contract | Pass | OpenAPI was exported and `pnpm api-client:check` passed. |
| Web quality | Pass | Typecheck, lint, production build, 70 unit tests and 2 guest Playwright journeys pass. |
| Runtime images | Pass | API and web images build as non-root; runtime contracts pass. The rebuilt API image `covie-api:local-20260825-secure4` started against the restored database, reported health `UP` and returned catalog discovery `200`. Both images have no HIGH/CRITICAL finding when limited to findings with an available fix. |
| Backup/restore | Pass | A custom-format dump with checksum restored into a separate empty database. Invariant counts and Flyway V013 matched the source; a temporary API started against that restored DB, reported health `UP`, returned discovery data with a location context, and rejected anonymous SSE. |
| Source security | Pass | Trivy source misconfiguration/secret scan on a tracked-source snapshot found no Dockerfile misconfiguration or secret; `pnpm audit --prod --audit-level=high` passed. |
| Local load-harness smoke | Pass, non-acceptance evidence | Against the restored local database: public discovery at concurrency 10 for 10 seconds completed 19,650 requests, 0 errors, p95 14 ms. The scenario now supplies the required location context. |
| Local concurrency soak | Pass | `CheckoutSoakConcurrencyTest` completed 100 two-buyer last-stock checkout races and one batch of 20 independent checkout commits. `OrderCommandSoakConcurrencyTest` completed 100 concurrent cancellation accept/reject, buyer-complete vs seller-cancel, and buyer-issue vs auto-complete races. Every race records one final valid state and exactly one persistent Order event and outbox event. |
| Seller post-confirmation cancellation | Pass | `OrderCommandServiceTest` verifies a merchant can cancel a confirmed Order and a buyer receives `ORDER_ACTION_NOT_ALLOWED` for direct cancellation after confirmation. `OrderCancellationControllerIntegrationTest` verifies merchant HTTP success/replay with one event/outbox, one ACTIVE-reservation release movement, missing-reason 422 and buyer 403. The exported OpenAPI and generated client type-check pass. |

### Remaining release blockers

1. The strict HIGH/CRITICAL image gate still reports base-OS findings without an upstream fix. Per `docs/operations/security-release-gate.md`, this requires a dated, operator-reviewed exception with CVE/advisory, exploitability analysis, mitigation, owner, expiry and target fix version; it must not be replaced with a blanket `--ignore-unfixed` flag.
2. Run the Plan 10 staging-only HTTPS rehearsal using a disposable real staging domain: normal-auth P0 browser journeys, controlled rate-limit/Redis failure, load baseline, alert trigger/resolve and digest rollback A → B → A.
3. Complete authenticated Plan 09 staging P0 journeys. The current guest Playwright coverage is intentionally not proof of normal Google/password onboarding or privileged flows.
