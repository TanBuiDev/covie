# Billing Published Price Notice Implementation Plan

> **For agentic workers:** Execute the reviewed plan in this existing isolated Billing worktree, using executing-plans and focused RED/GREEN verification. Read-only plan review uses the existing reviewer. Do not commit or create branches.

**Goal:** Close the evidenced missing A11 price-publication notice without changing renewal prices, founder eligibility, grants, invoices or payment behavior.

**Architecture:** Consume the existing durable BILLING_PRICE_PUBLISHED event. Resolve immutable version facts by aggregate ID, notify active OWNER memberships of subscriptions with that plan, deduplicated per source event/user through existing NotificationService. An owner with several subscribed shops receives one public plan notice linking to the existing Seller merchant switcher. No raw financial/account/provider details are exposed.

**Tech Stack:** Java25/Spring/JdbcTemplate/Postgres18/outbox, current Next/React/Vitest and generated API client.

**Compatibility review follow-up:** Initial implementation used a new BILLING_PRICE_CHANGED Java enum. This unnecessarily prevents old API readers from parsing persisted notifications. Reviewer recommends keeping existing BILLING_RENEWAL_REMINDER with additive public payload `noticeKind=PRICE_CHANGED`, retaining the distinct existing PRICE_PUBLISHED outbox event, immutable facts, exact UI summary and per-event/user dedup. Original plan requires advance notice, not a new enum. Adopt this full-scope compatible representation after the current running full18508 terminates; do not edit compiled source mid-gate. Ordinary renewal reminders and existing history must remain unchanged. Initial enum-based contract/native/full evidence becomes historical for this adjustment; targeted discriminator checks and final contract/current-source gates are required.

## Chunk 1: Evidence and implementation

### Task 1: Prove missing delivery

Files: existing `apps/api/src/test/java/vn/covie/billing/api/BillingAuthorizationHttpIntegrationTest.java`, existing BillingNotificationHandler/NotificationType and notification inbox helpers/tests.

- [ ] Add one actual HTTP price-publication → real outbox process → issued OWNER inbox regression, preserving all financial JSONB snapshots. Existing subscribed owner must see exact immutable revision/amount/cycle/effective time. Outsider/STAFF/revoked owner/unrelated-plan subscription must not receive it. Repeat the same event through the handler in a real transaction to prove notification dedup; no provider request.
- [ ] Execute only that method and confirm meaningful missing-notice failure, not a fixture/auth failure.
- [ ] Fixture must cross the100-owner page boundary with101eligible owners, including one owner with multiple subscribed shops; assert every eligible owner receives exactly one notice. Bounded database pages do not imply bounded total transaction/memory or unlimited-load acceptance.

### Task 2: Minimal durable handler

- [ ] Add `BILLING_PRICE_CHANGED` notification type (existing DB varchar, no changed migration). REST NotificationResponse uses the Java enum: regenerate official OpenAPI/client and verify the sole intended additive enum change.
- [ ] Extend BillingNotificationHandler only for existing publication event; resolve immutable version from DB, query distinct active subscribed owners, createFromOutbox with existing source event dedup, public price fields and `/seller` explicit internal link. Do not change previous merchant reminder/payment/expiry handling.
- [ ] Bound recipient DB pages with stable user-ID keyset, no truncation/500 cap. Existing transaction commits notifications and event status together; retry rolls back/reuses source ID. No new scheduler, external email, automatic charging, policy/grant mutation or recipient business segmentation.
- [ ] Verify real inbox authorization, immutable data, event retry and unchanged ledger/provider count. Reuse existing price/effective-date/early/late/legacy tests unless affected.

### Task 3: Seller-visible known public facts

- [ ] Add narrow helper for the new type's validated public payload, fixed Vietnamese label and price/cycle/ICT-effective-time summary. Do not render arbitrary backend title/body or invent private founder price guarantees. Include explicit existing paid periods unchanged/no automatic charge wording.
- [ ] Component/helper RED before implementation: known notice visible and navigates to `/seller`; malformed fields/raw private fallback remain hidden. Existing billingUrl navigation repair remains compatible.
- [ ] Focused notification tests, typecheck/lint, official contract/client drift, then current full Web/build and BE full verify after final source. Inspect skips and exact XML, no aggregated fictional counts.

## Chunk 2: Acceptance and limits

- [ ] Actual native Seller inbox keyboard navigation/public-facts screenshot at360/390/1366, no overflow/PII; generated fixture events only, no real provider.
- [ ] Review final source/SQL/fan-out/per-user dedup and update original BIL028 evidence only to actual scope. Owner notice is in-app durable delivery, not certified physical push/email/read acknowledgement.
- [ ] Update checkpoint/source manifests/security scans after final changes. Entire project remains ACTIVE until all original requirements, including section9.2/security/operations/pilot preparation, are resolved; no stopping at this notice.

Next action: Read-only reviewer checks recipient scope, immutable facts, dedup and compatibility; then execute targeted missing-delivery regression.
Required from you: Nothing
Completion condition: Actual durable owner notice and safe Seller rendering, with unchanged financial behavior and truthful whole-goal status.
