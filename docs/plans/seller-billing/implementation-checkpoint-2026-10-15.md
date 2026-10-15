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
