# Billing Publication Impact Preview Implementation Plan

> **For agentic workers:** Use executing-plans for implementation and a read-only plan review. Preserve this dirty worktree; no commit, branch, remote write, activation, money operation or recovered-data use.

**Goal:** Fulfil approved original Seller Billing plan4.2.5 with a server-observed
publication preview before existing Admin price/policy confirmation.

**Architecture:** Add authenticated, no-store, read-only preview operations and latest-publication Admin reads beside
the existing publication endpoints. Reuse current request DTOs, If-Match parsing,
Admin permission and publication validation semantics. Return previous published
version, proposed values, server observation time and explicitly named current
referencing-merchant count. Integrate these values into the existing confirmation;
keep actual publication and its final revision check unchanged.

**Tech Stack:** Spring Boot/Java25, JdbcTemplate/PostgreSQL18, issued-session HTTP
Testcontainers fixture, generated OpenAPI/TypeScript client, existing Admin React UI.
No new dependency, schema, pricing policy or feature activation.

## Chunk1: Read-only preview and guarded confirmation

###1. Confirmed requirement and files

Source: `docs/plans/seller-billing/CoVie-Seller-Billing-Subscription-Implementation-Plan-v1.0.md`4.1/4.2.5/10/G3;
gap: `docs/plans/seller-billing/local-whole-plan-gap-audit-2026-10-19.md`.
Current `save` in AdminBillingScreen only asks a generic confirm, while
BillingCatalogService has no publication preview (legacy migration preview differs).

Modify:
- `apps/api/src/main/java/vn/covie/billing/application/BillingCatalogService.java`
- `apps/api/src/main/java/vn/covie/billing/api/BillingController.java`
- `apps/api/src/test/java/vn/covie/billing/api/BillingAuthorizationHttpIntegrationTest.java`
- `apps/web/features/admin/admin-billing-screen.tsx` and its existing focused tests
- Official generated OpenAPI/client outputs after reviewed semantic diff
- Current evidence/gap matrix and runbook only as needed

###2. Contracts and semantic limits

- `POST /api/v1/admin/billing/plans/{planCode}/prices/preview`, existing PriceChange
  body and If-Match. Response previous PlanOffer, proposed PriceChange,
  referencingMerchants, observedAt.
- `POST /api/v1/admin/billing/policy/preview`, existing PolicyChange body and If-Match.
  Response previous Policy, proposed PolicyChange, referencingMerchants, observedAt.
- Add `GET /api/v1/admin/billing/plans` and `GET /api/v1/admin/billing/policy/latest`
  returning latest published revisions including scheduled versions. Preserve public
  effective-now plans and existing effective-now Admin policy GET contracts. Admin
  load uses these new reads and labels versions as latest published, possibly scheduled.
  Verify two sequential future publications can be reviewed without waiting for activation.
- Count current merchant_subscriptions rows referencing the selected plan for price;
  all current subscription rows for global policy. One row/merchant is DB-enforced.
  Label literally current subscription records, all statuses; includes expired/cancelled/
  revoked subscriptions, excludes merchants without subscriptions, and policy does not
  rewrite existing snapshotted trial/grace terms. Count is an advisory cohort, NOT exact
  affected eligibility, a promise to charge all merchants, exact
  future revenue or immediate change to paid terms. Explain this in confirmation.
- Previous version is latest published revision, including scheduled publication;
  label it as published, not necessarily the currently effective/charged price.
- Require Admin, existing valid fields/future monotonic schedule/revision. Deny stale
  previews with existing409; unknown/retired plan404; bad values422. No mutation,
  reservation, invoice, provider call, financial audit/outbox or entitlement grant.
- Preview is informational; existing publication independently rechecks If-Match
  under existing lock, so a later conflicting publication is still rejected.
- Capture URL, revision and proposed body before awaiting preview. Confirmation shows
  server-returned values; publication sends precisely that captured request even if
  fields change during preview. Stale preview/publication409 does not silently rebase
  a draft: offer explicit reload/review, preserve typed fields until that choice.
- Do not refactor working publication/settlement paths. Do not invent draft storage,
  eligibility, VAT, cutoff, forecasting, refund or renewal policy for this task.

###3. Targeted RED→GREEN

- [x] Read-only advisory plan review, address concrete concerns (scheduled reads, cohort limits, captured request); revised plan approved.
- [x] Add actual issued-JWT/PG HTTP regression for both populated previews; preserve
  full financial/config/audit/outbox snapshots and provider creation count.
- [x] Add focused authorization/revision/invalid-input checks for these new paths.
- [x] Cover inactive subscription statuses, another plan, merchant without subscription,
  zero cohort and policy-only changes; disclose advisory rather than exact impact in evidence.
- [x] Run `./mvnw.cmd -Dtest=BillingAuthorizationHttpIntegrationTest#adminPublicationPreviewsObservePopulationWithoutChangingFinancialState+publicationPreviewRejectsNonAdminStaleOrInvalidRequests test`
  with established Java25/Maven environment. Record real missing-operation RED;
  fixture/typing failures are not product RED. Never overlap Maven or UI seed.
- [x] Implement minimal read-only service/controller methods and typed responses;
  rerun affected methods, preserve unchanged publication behavior.
- [x] Add focused UI RED for preview-before-confirm, displayed old/new/count/date,
  cancellation and failed/stale preview never publishing. Implement in existing save
  flow, keep saving guard active through preview/confirmation/publication.
- [x] Delayed-preview field-edit regression binds confirmation/publication to captured
  values; final publication409 remains visible and requires explicit reload/review.
- [x] Official OpenAPI export/generate/check; verify only additive Admin latest-read/preview paths and
  schemas, no old fields/contracts removed. Typecheck and affected lint/tests.
- [x] Real local Admin native confirmation at360/390/1366 against API/PG, including
  cancel/read-only and existing final revision protection. No real provider call.

###4. Whole-goal gates, not feature-only completion

- [x] Once source stable, proportionate final full BE/Web and artifact/security gates;
  retain unchanged existing scenario evidence rather than repeated focused replays.
- [x] Final owned `pnpm ui:verify-local -CurrentDesign -PostgresContainer <owned>`;
  this preserves all automated201 artifact checks and explicitly records waived
  Figma matching NOT_APPLICABLE/manual comparisons NOT_PERFORMED, not fake passes.
- [x] Close requirement-by-requirement original-plan audit, not just30BIL rows.
- [x] Keep real-provider/HTTPS/channel/fees/legal/on-call/money/release evidence
  external and all normal flags OFF. All owned resources/keys removed after runs.

Final local execution is recorded in `docs/plans/seller-billing/local-final-acceptance-2026-10-19.md`.
The actual full CurrentDesign command is retained on unchanged mapped paths; new
Billing reads have separate current native/packaged-pair proof, not fabricated201manual reviews.

Next action: Preserve completed local evidence; no further unchanged implementation work.
Required from you: Nothing
Completion condition: Local acceptance remains recorded; real pilot/release requires separate actual authorization and evidence.
