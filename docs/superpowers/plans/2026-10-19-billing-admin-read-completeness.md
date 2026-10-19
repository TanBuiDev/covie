# Billing Admin Read Completeness Implementation Plan

> **For agentic workers:** Use executing-plans and read-only plan review. Preserve dirty work; no branch/commit/remote write, money, activation, deploy or recovered data.

**Goal:** Close confirmed original4.1/10.2 version-history and10.4 basic subscription lookup UI gaps without changing financial decisions.

**Architecture:** Add bounded Admin no-store history reads over existing immutable rows,
and a read-only Admin section consuming existing scoped subscription reads. Preserve
current/public/latest contracts and all publish/settle/grant behavior. Do not invent
historical transitions, draft lifecycle, owner identity history or new eligibility rules.

**Tech Stack:** Existing Java25/JdbcTemplate/PG18, issued-JWT HTTP fixture, generated
OpenAPI client, React AdminSurface/table styles, current native browser fixture.

## Chunk1: Bounded immutable publication history

Evidence: BillingController currently exposes current/latest publications only;
AdminBillingScreen has no history request/view. Original4.1 expressly requires history.

Files: BillingCatalogService.java, BillingController.java, existing
BillingAuthorizationHttpIntegrationTest.java, generated contract/client, a focused
Admin history component/test integrated into AdminBillingScreen.

- [x] Add issued-JWT HTTP RED for price history including retired plan and policy
  history, ordered revision DESC with explicit limit1..100/offset0..500000, Admin
  permission/no-store, invalid bounds, unknown plan404, full snapshot/provider0.
- [x] Proposed additive GET `/api/v1/admin/billing/plans/{planCode}/prices/history`
  and GET `/api/v1/admin/billing/policy/history`. Return existing values plus stored
  publication timestamp (`published_at` for price, `created_at` for policy), never
  label effective date as publication time. History includes future versions.
- [x] Choose explicit retired-plan entry: history component accepts a validated plan
  code rather than claiming latest nonretired list includes retired plans. No edit/
  restore/reopen action from history. Unknown and empty states remain distinct.
- [x] Minimal read-only service/controller implementation; focused GREEN and unchanged
  existing revision/zero-price/schedule guards. No SQL migration/new dependency.
- [x] Official export/generate/check and reviewed additive semantic diff; backend
  response records expose stored timestamps, no handwritten frontend contract duplicates.
- [x] Component RED→GREEN for pagination, loading/empty/error/403/retry, visible
  revision/values/publication/effective times; existing keyboard-focusable table region.
- [x] Delayed plan-switch/page-response regression never shows another plan's history
  under a new code; history GETs independently require Admin, not finance-reader scope.

Read-only review: Chunk1 ready with these ordering/type/race clarifications.
Chunk2/whole plan are not yet execution-ready until the stated investigation finishes.

## Chunk2: Subscription lookup, not a fabricated state timeline

Evidence: existing GET `/api/v1/admin/billing/subscriptions` is bounded and requires
finance reader, but no Web consumer exists. Original10 suggests page arrangement;
implement a section, not seven new pages or analytics/entitlement-limit expansion.

Files: existing AdminBillingReadService/Controller only if additive merchantId filter
is needed; dedicated Admin subscription component/test; AdminBillingScreen integration;
existing actual authorization/native fixtures and generated output as needed.

- [x] Component RED for actual read section, bounded pagination, merchant/plan/stored
  status/period end/grace end/cancellation flag, finance403/loading/empty/error/retry.
- [x] Support exact merchant UUID lookup server-side with optional merchantId query
  parameter; keep existing default/limit/offset and authorization semantics unchanged.
  Test another merchant exclusion and page boundaries against actual PG before implementation.
- [x] Display stored status separately from commercial eligibility; do not derive new
  canSell from this list or treat cancellation as immediate entitlement revocation.
- [x] Investigate existing authorized merchant-detail navigation: generic merchants
  page has no scoped OWNER deep-link; repository only computes owner_count. Do not
  guess a query URL. Read-only reviewed safe alternative below.
- [x] Add optional Subscription.currentOwnerUserIds: distinct sorted UUIDs from
  ACTIVE OWNER memberships only. Explicit label current active OWNER memberships,
  not active user accounts/immutable historical owner. No names/email/phone. Keep
  finance-reader authorization; aggregate within the bounded subscription query,
  never join raw memberships before pagination. Empty list if none.
- [x] Where original10.4 history is not stored as complete state events, label available
  invoice/audit/grant records accurately and document the limitation. Do not backfill
  invented transitions or assert a complete historical ledger from current status.
- [x] Existing merchant-scoped invoice GET supplies associated invoices with separate
  bounded pagination; clear old merchant results immediately and guard delayed replies.
  This is not a complete state-event timeline. No new financial action.
- [x] Typed minimal UI implementation, no financial mutation or change to existing
  manual grant/reconciliation/price confirmation paths. Focused GREEN/type/lint/native.

Read-only Chunk2 review accepted these concrete semantics: preserve reader scope,
deleted-merchant exclusion/default sort/page bounds, exact optional UUID filter;
test multiple/revoked/invited/no owners, page cardinality, role403 and rapid switches.

## Stable whole-goal verification order

- [x] Close actual local source gaps first, then run ONE stable full BE verify rather
  than repeating the unchanged full suite after each read-only UI step.
- [x] Stable final Web gates and exact owned `ui:verify-local -CurrentDesign`; preserve
  all automated capture checks and waived/unperformed Figma distinctions.
- [x] Final scoped security/artifact/whole-numbered-plan evidence and owned cleanup.
  Preserve already proved unchanged migration/recovery/financial cases; real provider,
  HTTPS/legal/alert destination/field operations/release approvals remain external.

Final local execution is recorded in `docs/plans/seller-billing/local-final-acceptance-2026-10-19.md`.
Both chunks' earlier design questions were resolved and reviewed before implementation.
Exact CurrentDesign source retention and separate current Billing native/package proof
are distinguished; no state/ownership history or manual review was invented.

Next action: Preserve completed local evidence; no further unchanged implementation work.
Required from you: Nothing
Completion condition: Local acceptance remains recorded; any real pilot/release requires separate actual authorization and evidence.
