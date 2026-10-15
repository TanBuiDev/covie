# Seller Billing & payOS implementation checkpoint — 2026-10-15

**Branch:** `feat/seller-billing-subscription`. **Status:** WIP. No production payment, no merge/deployment, no entitlement enforcement enabled.

## Implemented in source

- V020–V022 Flyway migrations: plan/policy revisions, merchant subscriptions, immutable invoice snapshots, payment attempts, payOS order numbers, webhook inbox and entitlement grants.
- Admin-owned versioned pricing/policies and billing setting flags, security-gated Admin API, audit/outbox for publishing versions.
- payOS hosted checkout gateway using backend-only environment credentials and HMAC signature.
- Seller-only invoice creation with OWNER permission, per-merchant idempotency and prevention of simultaneous active payment links.
- Signed webhook entry point with exact invoice, amount, currency and payment-link ID checks; idempotent granting with transaction-scoped locks; callback does not rely on returnUrl.
- Seller billing page with plans, payment links, polling, invoice history; Admin billing settings page; safe return route; nav links added.
- Public plan, Seller billing summary, invoice history, invoice status; migration and payOS signature unit-test sources.
- OpenAPI client schema regenerated from CI artifact and committed (contract matches the new backend at observed CI run).
- Billing enforcement flag OFF; payment creation flag `PAYOS_PAYMENT_ENABLED=false`.

## Tests and gaps

- GitHub CI at earlier commit: backend compiled and **921 tests ran, 1 failure** due to outdated migration assertion (V019 vs V022). Test updated in subsequent commit. New CI recheck required.
- Frontend contract drift detected at older commit and subsequently corrected by committing regenerated OpenAPI/client. New CI recheck required.
- CI secret-history scanner reported one match while scanning full Git history. **Do not dismiss.** Inspect sensitive details only in a private security workflow, determine historical/current scope and rotate any real exposed credential if applicable. Never put secret value into discussions.
- payOS officially **has no separate sandbox**; avoid real-money testing until approved. See `payos-setup-and-local-acceptance.md`.

**Incomplete:** full provider reconciliation and refund workflow (an opt-in read-only observation worker has been added); rate limiting, seller registration gating, trial/grace scheduler; backend enforcement across catalog/checkout/Need/discovery/ads; legacy merchant migration; fully audited Admin finance RBAC; contract-level verification with payOS test transfer; Figma/responsive/UI validation; complete end-to-end tests and security release approval. These are release blockers.

## Resume instruction

Continue on this branch without force push or resetting local work. Read AGENTS/RULES and latest main before editing. First inspect current CI errors and resolve focused regressions. Then implement reconciliation, retention/upgrade safety and entitlement enforcement with feature flag OFF; write tests. Do not enable live payments or buyer-side fees.

**Never claim this feature complete solely because a branch compiles, CI passes or admin pages render.**

## Progress update — 16/10/2026 (source-only; CI status not assumed)

Implemented in subsequent commits on this feature branch (not merged):
- Added seller entitlement gates in CheckoutTradingPolicy, CatalogLifecycleService publish/activate, NeedResponseService create/replace and CatalogEditService re-enable location offers. The production Spring constructors/setter resolve BillingEntitlementService; the global enforcement switch still defaults OFF.
- Added SQL-level entitlement predicates to global discovery and merchant catalog listing, so expired/unpaid merchant offers are filtered without per-item N+1 queries once enforcement is enabled.
- Added operator-gated, audited and idempotent legacy grants with preview. Requires `COVIE_BILLING_LEGACY_GRANTS_APPROVED=true` and global billing enforcement OFF; **never auto-enables enforcement**.
- Added seller-registration switch at merchant creation and database-enforced at-most-once trial claims for new shops when both enforcement and Admin TRIAL policy are active.
- Added grace window snapshots to invoices/subscriptions (V025) and an hourly lifecycle transition scheduler; only past-due/expired metadata changes, never delete old merchant/order/social records.
- Separated payOS provider requests from the database reservation transaction using TransactionTemplate to preserve orphan-payment recovery metadata.
- Expanded read-only reconciliation to include previously reserved payOS orders with missing local links.
- Added Admin overview metrics, paginated invoice read endpoint and summary table in Admin Billing UI.
- Updated generated OpenAPI/TypeScript contract for the new Admin routes. Backend and frontend builds need rechecking at the latest head.

Outstanding before claiming **all code complete**: verified orphan-link repair and authenticated recovery settlement, refund/chargeback workflow, seller renewal cancellation and manual extension workflow, reminder/notification integration, full Admin multi-plan/yearly/discount UI + RBAC, full Need/Ads/public detail and item guard matrix, quote TTL with price-version race handling, full BIL-001..030 test coverage, real-money payOS verification with user-operated secrets, local/Figma E2E, legal invoice/tax operations. CI gitleaks history scanner still reports one match in full repository history and requires private investigation. Do not merge or enable payment.

For payOS keys: root `.env.local` is untracked and loaded by `pnpm dev`, with `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`. Never commit credentials or switch on `PAYOS_PAYMENT_ENABLED` by default.

## Continuation — 16/10/2026: substantial implementation added (unmerged, release flags OFF)

**Do not assume PRODUCTION READY.** The code below was pushed to `feat/seller-billing-subscription`; GitHub PR #31 remains Draft. No real key was committed and no payOS charge was initiated by ChatGPT.

### Newly implemented code
- Hosted payOS link/webhook early-callback race fixed: signed webhook can bind a previously unbound provider link ID, and post-request persistence handles a concurrent PAID callback.
- Provider reconciliation now verifies signed payOS GET data; a single, unambiguous paid transaction with exact orderCode, amount and provider reference can enter the **same transaction-safe, idempotent settlement path** as the webhook. Unverified/multiple/partial transactions require review. Pending links can be restored only from strongly validated, signed provider IDs. Reconciliation worker remains opt-in and bounded.
- A **second release approval gate** `COVIE_BILLING_RELEASE_APPROVED=false` was added in addition to `PAYOS_PAYMENT_ENABLED=false`. New payment links require both; independently signed callbacks and read-only reconciliation for existing orders remain possible when checkout creation is disabled.
- Admin can create independent plans (monthly, quarterly, yearly or any 1–120 month cycle), retire plans, publish immutable versioned prices and policies, and see revenues/settlement alerts.
- Seller can see current subscription, period, invoices, create payment links, opt out of renewal and submit REFUND/DISPUTE cases for PAID invoices.
- Added `billing_finance_cases` (V027): Admin finance case reviews, operator-gated `CONFIRMED` status for externally verified refunds, audit, revocation of only the specific refunded invoice grant. **No bank payout API**.
- Added Admin discretionary time-bound entitlement grants with audit, max-one-year validity and separate `COVIE_BILLING_ADMIN_GRANTS_APPROVED=false` ops gate. Legacy grants still require separate `COVIE_BILLING_LEGACY_GRANTS_APPROVED=false`.
- Added 7/3/1-day reminder scheduling with database dedupe + outbox, owner in-app notifications, payment-success messages and expiry messages. Controlled by Admin's `billing.reminders_enabled` (OFF by default). Billing lifecycle respects snapshotted grace and cancellation.
- Unified paid/trial grace eligibility across `BillingEntitlementService`, buyer Checkout, SQL discovery, merchant catalog and ad delivery. Added public item detail and Ad submission checks. Expired subscriptions never delete existing orders, products, likes or comments.
- Backend payOS credentials are explicitly overridden to **empty** on the production Web service where a legacy shared env file is used. In production plan to split API-only and Web-only env files.
- Added targeted Java unit regression tests covering signed recovery, invalid financial action, default-safe flags and seller checkout/catalog eligibility; updated Flyway upgrade test through V027.
- Synced `packages/api-client/openapi/openapi.json` and `packages/api-client/src/generated/schema.ts` after adding finance/Admin/Seller APIs and notification types.

### Not-yet-complete code and testing
- New code needs latest-head CI green and local E2E with Java 25/Postgres/MinIO/Redis; latest-head job status is **not assumed**. Occasional unrelated ads UI test flakiness is being tracked separately.
- GitHub `secret-history` has **one persistent redacted finding** from repository history; must privately investigate, rotate any exposed credential, and approve remediation before merge. Do not bypass this security check.
- Promotions/vouchers and customer-facing discount selection with quota/misuse protection remain unimplemented; Seller onboarding's full guided fee/trial experience and role-specific finance management UI still need UI review.
- The finance case feature records and confirms *manually executed* external refunds but intentionally never issues a payout from CoVie. Provider-controlled real refund APIs require business/account eligibility and separate authorization; refund fraud and chargeback handling require further acceptance tests.
- Full BIL-001 through BIL-030 test matrix, concurrency/live payOS webhook registration and real bank transfer proof, monitoring/retry/operational runbook and data-protection/regulatory acceptance are not completed.
- Admin finance roles are still global Admin, not separate accounting/approval roles. Advanced reporting, rate limits, promotions and Figma/browser/mobile visual acceptance remain pending.
- The code contains no authorized live payout or deployment action.

### Safe local configuration
Place `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` in ignored root `.env.local` loaded by `pnpm dev`. For local review keep `PAYOS_PAYMENT_ENABLED=false`, `COVIE_BILLING_RELEASE_APPROVED=false`, `COVIE_BILLING_RECONCILE_ENABLED=false`, and Admin's `billing.enforcement_enabled=false`. Never send credentials in chat.

### Follow-on execution
Resolve final CI against latest head (both API client and UI), finish promotions and comprehensive BIL acceptance cases, review end-to-end entitlement paths, finish separate Admin finance roles, investigate secret-history privately, and only then hand off a release candidate for locally supervised real-money acceptance. Do not merge by default.

## Continuation — 16/10/2026: configurable vouchers, onboarding, finance RBAC (V028–V029)

Source changes pushed on feature branch (still DRAFT):
- Added seller-subscription-only vouchers: `billing_vouchers`, `billing_voucher_redemptions` (Flyway V028), Admin create/list/disable, optional plan-scoped FIXED or PERCENT discounts (basis points), quotas per merchant and total usage. The voucher row is locked `FOR UPDATE` in the same DB transaction as the immutable invoice reservation, then a payOS link is created for the reduced **integer-VND** amount. Buyer checkout is unchanged.
- Seller Billing UI has optional voucher input, server-side quote and clear final price. The idempotency fingerprint includes a normalized voucher code, but remains backward-compatible for invoices without a voucher. No zero-value payOS payments.
- Added PostgreSQL voucher integration tests and Seller onboarding UI tests; backend regression suites must be reverified on latest head.
- Public `GET /api/v1/billing/onboarding` shows current Admin-owned price cycles, pay-before-sell/trial mode, grace days and registration/enforcement status. Seller Center displays this before initial/additional shop creation, buyer remains free. New shops created with active pay-before-sell policy navigate to billing; policy TRIAL/disabled keep their prior flow.
- Added `billing_finance_permissions` (V029): Admin finance READER/OPERATOR/APPROVER roles with explicit operator-gated assignment, second-Admin restriction, and audit. Finance role enforcement is **OFF** by default via `billing.finance_roles_enabled=false`. Financial reporting/refund/grants/legacy operations check roles when enabled.
- Added Admin Billing UI for role assignment/listing/revocation and graceful finance-read denial without blocking price/policy administration.
- Added Admin-configurable `billing.checkout_hourly_limit` (default 6; allowed 1–60) enforced under merchant DB lock to throttle payment link reservations.
- Updated OpenAPI/client from GitHub CI generated contract and acceptance matrix `docs/plans/seller-billing/acceptance-matrix-v1.md`.

**Outstanding:** latest-head CI verification, source-only vs full BIL-001–030 test coverage, concurrency verification, real payOS operator acceptance, ads UI nondeterminism, finance special-account bootstrapping, refund settlement independent proof, secrets-history finding (do not bypass), responsive/browser checks, manual deployment and tax/compliance. Do not merge or claim fully finished.
