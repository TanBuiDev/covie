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
