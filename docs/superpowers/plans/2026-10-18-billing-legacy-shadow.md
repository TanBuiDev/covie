# Billing Legacy Shadow Implementation Plan

> **For agentic workers:** Use executing-plans and focused TDD. This executes original BIL020/section11; the whole Seller Billing goal remains active.

**Goal:** Prove idempotent legacy migration and provide genuine billing-only shadow decisions without affecting commerce traffic.

**Architecture:** Extend existing explicitly requested, authorized read-only Admin legacy preview with a consistent cohort shadow snapshot and bounded aggregate metrics. Do not instrument or refactor working commerce/entitlement hot paths, add automatic jobs, turn enforcement on, invent a production cutoff or grant deadline, or treat enforcement-OFF alone as shadow evidence. This is an operator-invoked cohort shadow run, not a claim of live request replay or complete commerce eligibility.

**Tech Stack:** Existing Spring/JDBC/PostgreSQL/Micrometer and generated OpenAPI/client.

## Chunk 1: Reproduction and implementation

Files: `apps/api/src/main/java/vn/covie/billing/application/BillingLegacyMigrationService.java`; new `apps/api/src/test/java/vn/covie/billing/application/BillingLegacyShadowPreviewTest.java`; existing actual-JWT fixture `apps/api/src/test/java/vn/covie/billing/api/BillingAuthorizationHttpIntegrationTest.java`.

- [x] Observe current full80543 terminal and save independent XML before the next Maven command. Do not overlap Maven or assume a timeout is terminal.
- [x] Add a focused RED showing that current preview has no computed shadow result.
- [x] Add optional `shadow` to existing Preview response, preserving eligibleMerchants/previouslyGranted/createdBefore. Snapshot contains decisionScope=BILLING_ENTITLEMENT_ONLY, observedAt, enforcementEnabled, evaluatedMerchants, currentlyAllowed, wouldAllow, wouldDeny.
- [x] Use exactly the current canSell grant predicate (unrevoked, valid_from<=now, valid_until>now). Grace is READ_ONLY, not a selling grant. Snapshot is explicitly billing-only, never a substitute for merchant moderation, offer readiness, stock or order permissions.
- [x] Existing explicit cutoff controls the cohort: nondeleted merchants created before it. With enforcement OFF current billing allows the cohort; wouldAllow/wouldDeny compute the enforcement-on result. With enforcement ON current billing result matches wouldAllow. No policy mutation.
- [x] Keep existing legacy count semantics and make preview reads snapshot-consistent. Add only aggregate Micrometer preview/ALLOW/DENY counters with fixed labels, no merchant/user IDs, reasons, raw payloads or secrets. No financial/merchant/setting/outbox/audit DB writes from preview.

## Chunk 2: Real local acceptance

- [x] In disposable issued-JWT/HTTP/PG fixture ONLY, approve legacy command through private Spring test property. Use explicit relative fixture dates, never production dates.
- [x] Assert Admin finance READER can preview but cannot grant; APPROVER can grant only with enforcement OFF. Invalid future cutoff/deadline HTTP tested; reason remains existing validated/audited contract, no weaker gate.
- [x] Prepare old eligible shops plus newer/deleted exclusions. Preview identifies would-deny before grants while actual pre-existing checkout remains allowed. Compare protected financial/commerce/merchant snapshots around preview.
- [x] Apply same grant request twice via actual HTTP: first creates only target rows, second creates0; original grant JSONB/dates/status/history unchanged. Audit actor/reason/cutoff/created count for both actual requests. No provider charges or invoice creation.
- [x] Preview again has matching existing counts, wouldDeny0 for the granted fixture cohort, still no data mutation. Verify metrics deltas and actual unaffected commerce request, not just an OFF helper result.

## Chunk 3: Contract and reporting

- [x] Export canonical OpenAPI and regenerate client with official scripts; inspect additive semantic diff only. Typecheck/clientcheck; no unrelated UI rebuild/test replay when UI source is unchanged.
- [x] Run focused existing/new regression coverage. Full legacy-shadow verify exited0 at08:21:44:1026reported/1025PASS/1HotQuerySKIP/0fail/errors8m18s; independent XML summary saved before focused backup tests. This gate predates the new populated-backup and database-crash test extensions. Keep primary dirty/recovered data and all normal activation flags unchanged.
- [x] Update original BIL020 traceability and runbook with operator-invoked shadow limits and exact evidence. Continue remaining original requirements; no whole-goal completion claim from this capability.
