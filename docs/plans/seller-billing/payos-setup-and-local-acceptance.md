# payOS — CoVie Seller Billing local setup & acceptance (WIP)

**Reference:** https://payos.vn/docs/api/ and https://payos.vn/docs/moi-truong-test/  
**Status:** Integration source code only; NOT READY to accept live fees.

## Current verified situation (2026-10-15)

- User has created a dedicated payment channel named CoVie; it appears active and linked to a bank in payOS.
- payOS docs currently state **there is no separate sandbox**. Requests to the official API can move real money. Fake webhook fixtures must be used for automated tests; do not send real transfers without explicit authorization.
- GitHub branch `feat/seller-billing-subscription` has a partial payOS implementation; initial prices are versioned database values; billing enforcement and payment creation are OFF by default. Existing buyers and merchants are unaffected by billing until a separate rollout.
- payOS credentials are NEVER to be transmitted through chat, committed to git, placed in JavaScript/browser APIs, or logged.

## Backend environment (configure locally/secrets manager)

```env
# Copy names only; fill with credentials from payOS channel CoVie in a private local secret source.
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
# Gate: leave false until approved for a SMALL, REAL-MONEY test.
PAYOS_PAYMENT_ENABLED=false
# Must be the externally reachable HTTPS origin of the CoVie web app (no trailing slash).
COVIE_PUBLIC_APP_URL=https://your-deployed-covie-domain.example
# Additional ops gate, ALSO leave unset/false until signed-off rollout:
COVIE_BILLING_RELEASE_APPROVED=false
```

**Do not check in a populated env file.** Webhook URL to register in the payOS channel only after TLS/reverse proxy works:

```text
https://your-deployed-covie-domain.example/api/v1/billing/webhooks/payos
```

Ensure reverse proxy routes that path to the Spring Boot API. The public webhook route accepts only POST JSON and validates the payOS signature against `PAYOS_CHECKSUM_KEY`; browsers, payOS redirect URLs, screenshots and query parameters **cannot** mark invoices PAID.

## Data & endpoints currently implemented

- Flyway V020/V021/V022 seller billing tables; payOS orderCode sequence and exact linkId binding.
- `GET /api/v1/billing/plans`: public current published pricing.
- `POST /api/v1/merchants/{merchantId}/billing/checkout`: OWNER + `Idempotency-Key`; returns hosted checkout link only if payOS enabled and configured.
- `GET /api/v1/merchants/{merchantId}/billing/invoices`: member-scoped read.
- `GET /api/v1/merchants/{merchantId}/billing/invoices/{invoiceId}`: member-scoped polling state.
- `POST /api/v1/billing/webhooks/payos`: signature-authenticated settlement to invoice/payment/grant/subscription/outbox; no manual success setter.
- `/seller/{merchantId}/billing`: seller plan, payment link and invoice history; `/admin/billing`: admin price/policy scheduling; payOS return page never claims paid based on redirect.

## Runbook to complete before enabling payment

1. Pull branch and compare with latest main and active feature branches (especially Social); do not overwrite local edits.
2. Prepare disposable PostgreSQL/PostGIS and run clean Flyway migration through latest. Existing DB upgrade V019→V022 must not delete or mutate buyer checkout data.
3. Java 25: run backend verification and real Spring Boot wiring; Next.js stack per README. Export OpenAPI and regenerate `packages/api-client` artifacts: `./infra/scripts/export-openapi.sh` then `pnpm api-client:generate`; commit generated files only after reviewing diff.
4. Run web typecheck, lint, tests and production build; check responsive UI against the approved exact Figma frames or request new frames for Billing routes.
5. Payment integration tests must mock payOS responses and signed callbacks. Cover invalid HMAC, duplicate events, wrong amount/currency/linkId, two users/merchants, seller role, quoted version, early/late renewals, DB crash between callback and settlement, invoice expiry and refund/reconcile handling.
6. **Important:** provider reconciliation, refunds/chargebacks and production-grade idempotency of ambiguous external HTTP failures are incomplete. Do not enable payment until implemented and tested.
7. Current GitHub CI may report generated contract drift while the new API shapes have not yet been exported and committed. There may be unrelated or historic secret-history scanner findings: inspect redacted reports locally; do not leak any findings or credentials in PR comments.
8. Configure public HTTPS callback and confirm payOS webhook in the CoVie channel ONLY after safe deployment and security audit. The payOS confirmation sample must not grant entitlements. Record webhook delivery proof without raw account data.
9. An end-to-end real-money test requires owner approval; because payOS has no sandbox, use an agreed small amount only after verifying provider terms, invoice/tax responsibility, receipt reconciliation, and merchant/legacy protection.
10. Rollout: `billing.enforcement_enabled` stays OFF until legacy grants, buyer checkout guards, catalog/discovery/Need/ads enforcement and release acceptance are complete.

## Known incomplete production requirements

- Backend requires complete entitlement integration at merchant publish, active offers, buyer checkout transaction commit, discovery/Need/ads with legacy grandfather grants and migration.
- No renewal/reminder scheduler, merchant billing cancellation, provider status reconciliation worker, refund workflow or high-risk Admin RBAC yet.
- Generated contract, unit/integration/E2E and Figma visual acceptance still pending until CI/local verification. 
- In particular, merely creating a working payOS link does not establish that CoVie is safe to charge sellers.

## Local test safety

There is **no requirement to provide API secrets to ChatGPT**. Configure backend secrets on your own machine or deployment secret manager. Never share a screenshot where the Client ID, API Key, or Checksum Key is visible.

## CoVie Windows local environment (verified from README and scripts/dev.ps1)

The repository root `.env.local` is ignored by Git and imported into the API process by
`pnpm dev` (scripts/dev.ps1). Set `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`,
`PAYOS_CHECKSUM_KEY` in that file; leave `PAYOS_PAYMENT_ENABLED=false`.
No code change, GitHub secret commit, screenshot or ChatGPT sharing is needed.

Do not assume that Spring Boot automatically reads the root .env.local if you run
`apps/api/mvnw.cmd` directly: follow the README PowerShell environment import.
When deploying production, give payment secrets ONLY to API containers, not web or
public build-time NEXT_PUBLIC_* configuration. The current production Compose uses one
shared `COVIE_ENV_FILE` for both API and web: **split API-only secrets into a separate
env_file or secret injection before enabling payOS**.

### Optional read-only reconciliation (V023)

`COVIE_BILLING_RECONCILE_ENABLED=false` by default. If enabled only after
provider credentials and operational controls are verified, an opt-in scheduled
worker checks up to 20 pending invoices every 15 minutes against authenticated
payOS GET link information. It stores minimal observations in
`billing_reconciliation_observations` and logs invoice ID plus a discrepancy code.
Even when payOS says PAID, this worker does NOT mark invoices PAID or grant
entitlements; a paid-without-webhook discrepancy needs authenticated recovery,
human reconciliation and idempotent settlement flow before enabling charging users.
Do not confuse this observer with a complete recovery and refund workflow.

**Operator warning:** payOS API calls use the real provider endpoint (no separate
sandbox). Read-only GET queries do not initiate payments, but still need an approved
account and rate-limit precautions. Keep the worker disabled until configured.

## Legacy seller migration gate

Before activating billing enforcement for old shops, Admin must preview and explicitly issue complimentary legacy grants with a reviewed cutoff and expiry. The server environment toggle `COVIE_BILLING_LEGACY_GRANTS_APPROVED` defaults false and should only be enabled during supervised migration, with enforcement OFF. APIs:
- `GET /api/v1/admin/billing/legacy/preview?createdBefore=...`
- `POST /api/v1/admin/billing/legacy/grant`, body has `createdBefore`, `validUntil`, `reason`

These calls are authenticated as Admin and leave audit entries. Do not set an arbitrary hardcoded grandfather date or assume old merchants have been granted.

## Release safety of Admin finance operations and production Compose

- `COVIE_BILLING_REFUND_CONFIRM_APPROVED=false` by default. Only an Admin with explicit ops approval may mark a **previously externally refunded** invoice REFUNDED and revoke its associated paid grant; no automatic bank payout is issued.
- `COVIE_BILLING_ADMIN_GRANTS_APPROVED=false` by default. Admin promotional/manual grants and revocations require explicit ops approval and leave audit entries.
- `COVIE_BILLING_LEGACY_GRANTS_APPROVED=false` by default. Existing sellers are never migrated or billed merely by applying migrations.
- `billing.reminders_enabled=false` by default, configured in Admin System Settings. When enabled, reminders are sent at 7/3/1 days before a subscription period's end.
- Production Compose currently has a legacy shared `COVIE_ENV_FILE` used by API and Web. Web now explicitly overwrites `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, and `PAYOS_CHECKSUM_KEY` to empty strings so the web container will not inherit these keys. For a future hardening deployment, **split the shared env file into API-only and Web-only files**; do not place secrets in frontend build arguments.
- payOS financial refunds must be reconciled with the receiving bank/processor by a human before an Admin confirms them in CoVie. Never represent the in-app case workflow as a money transfer.

## Seller-only voucher and finance roles settings

- New Admin-generated vouchers are `FIXED` integer VND or `PERCENT` integer basis points, 1–9999, and are never applied to buyers.
- Pending payment invoices continue to reserve quota until payOS confirms cancellation/expiry and the invoice is safely VOID. A local 15-minute link TTL **alone** does not free a voucher. FINANCE REFUNDED invoices do not hold redeemed voucher quota.
- The voucher discount and immutable original price are recorded in the invoice pricing snapshot, while the payOS amount equals discounted `billing_invoices.amount_vnd`.
- Admin can adjust checkout attempts per merchant per hour through System Setting `billing.checkout_hourly_limit`, default 6.
- Fine-grained finance RBAC is opt-in via Admin System Setting `billing.finance_roles_enabled=false`, **not automatically enabled on migration**. Before enabling, provision at least one APPROVER and an OPERATOR/READER using the Admin Billing screen. Changes require a *different* global Admin account and transient env `COVIE_BILLING_FINANCE_ROLE_ASSIGNMENT_APPROVED=true`; keep false outside supervised role assignment.
- Finance role assignment never grants a global ADMIN role to a user. Only already provisioned system Admin accounts can access the Admin finance APIs. Do not attempt to bootstrap access by editing JWT role claims manually.
- When fine-grained roles are enabled: READER can read finance cases/revenue and legacy preview; OPERATOR can additionally review/reject cases; APPROVER can additionally confirm independently verified external refunds and control legacy/manual grants, still subject to their separate ops approval flags.

## Final pre-release rules: signed response, independent refund, manual renewal

1. payOS credentials remain only in the API runtime; **both** `PAYOS_PAYMENT_ENABLED=true` and `COVIE_BILLING_RELEASE_APPROVED=true` plus verified HTTPS app URL are needed to issue *new* payment links. Confirm real payment only after independently signed callback or signed provider reconciliation.
2. Return to the merchant Billing screen to renew manually; there is **no automatic debit**. Opt-out/undo changes metadata only and never transfers money.
3. A refund case must be explicitly marked REVIEWING by an Admin reviewer before a **different** global Admin approver may mark the externally executed refund CONFIRMED; the initial requester cannot approve their own case. Migration V030 stores reviewer and review time.
4. For `secret-history`, the only allowlisted new finding is a specifically reviewed false positive in a historical implementation-plan checklist. Do not disable Gitleaks or add wildcard ignores. If additional findings appear, treat them independently.
5. Test the nine-character `CV`+base36 bank description and verify payOS signs its `data` response in the user's actual channel before enabling any real charge. Any response mismatch must leave invoices unpaid for safe manual review.
