# payOS — CoVie Seller Billing local setup & acceptance

**Reference:** https://payos.vn/docs/api/ and https://payos.vn/docs/moi-truong-test/  
**Status (2026-10-19):** Local implementation/verification is closed by
`local-final-acceptance-2026-10-19.md` and the whole-numbered-plan matrix. Current
backend1069PASS+1skip, Web553PASS+12skip, separate unchanged opt-in proofs and current
native/package/security receipts are recorded there. Real payment pilot NOT_READY
until actual provider/HTTPS/legal/operator prerequisites and separate money approval
pass. No live provider, money, staging or production acceptance.
See `acceptance-original-bil-v1-local-2026-10-17.md` and
`local-security-observability-progress-2026-10-19.md` for current evidence and gaps.

## Historical provider/account observations (2026-10-15; not reverified live)

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

Local launcher isolation: `pnpm dev` loads the private root environment for API,
but removes every `PAYOS_*` variable and `COVIE_BILLING_RELEASE_APPROVED` while
spawning Web, restoring the parent environment afterwards, including startup
failure. Do not place provider variables in `apps/web/.env*` or `NEXT_PUBLIC_*`.
For separate manual Web startup use the README's isolated Web terminal commands,
not the API environment-import block. No actual keys are needed for the launcher
regression test `scripts/tests/dev-web-environment.test.ps1`.

```text
https://your-deployed-covie-domain.example/api/v1/billing/webhooks/payos
```

Ensure reverse proxy routes that path to the Spring Boot API. The public webhook route accepts only POST JSON and validates the payOS signature against `PAYOS_CHECKSUM_KEY`; browsers, payOS redirect URLs, screenshots and query parameters **cannot** mark invoices PAID.

## Data & endpoints currently implemented

- Billing migrations V020–V034 in this isolated Billing checkout; payOS orderCode sequence and exact linkId binding. Do not merge colliding Social migration numbers or run these against recovered data without a separate reviewed migration reconciliation.
- `GET /api/v1/billing/plans`: public current published pricing.
- `POST /api/v1/merchants/{merchantId}/billing/checkout`: OWNER + `Idempotency-Key`; returns hosted checkout link only if payOS enabled and configured.
- `GET /api/v1/merchants/{merchantId}/billing/invoices`: member-scoped read.
- `GET /api/v1/merchants/{merchantId}/billing/invoices/{invoiceId}`: member-scoped polling state.
- `POST /api/v1/billing/webhooks/payos`: signature-authenticated settlement to invoice/payment/grant/subscription/outbox; no manual success setter.
- `/seller/{merchantId}/billing`: seller plan, payment link and invoice history; `/admin/billing`: admin price/policy scheduling; payOS return page never claims paid based on redirect.

## Runbook to complete before enabling payment

1. Pull branch and compare with latest main and active feature branches (especially Social); do not overwrite local edits.
2. Prepare disposable PostgreSQL/PostGIS and run clean Flyway migration through latest. Populated V013/V019/V030 upgrades and recovery must preserve buyer and financial data; current local evidence is in the migration/recovery progress reports, not a production DB rehearsal.
3. Java 25: run backend verification and real Spring Boot wiring; Next.js stack per README. Export OpenAPI and regenerate `packages/api-client` artifacts: `./infra/scripts/export-openapi.sh` then `pnpm api-client:generate`; commit generated files only after reviewing diff.
4. Run web typecheck, lint, tests and production build; check the current UI at360/390/1366px with keyboard and named landmarks. Owner waived matching the old Figma UI; do not block on obsolete frames or claim exact Figma acceptance.
5. Payment integration tests must mock payOS responses and signed callbacks. Cover invalid HMAC, duplicate events, wrong amount/currency/linkId, two users/merchants, seller role, quoted version, early/late renewals, DB crash between callback and settlement, invoice expiry and refund/reconcile handling.
6. Local signed-provider recovery, ambiguous create retry, finance-case workflow and concurrency have actual evidence in the original BIL matrix. They do not prove compatibility with a real account/channel. The in-app refund workflow never initiates a bank payout. Keep payment OFF until final local gates and operator/provider approval.
7. Generate/check the current contract and inspect immutable remote CI separately. Local dirty-source checks are not remote CI proof. Inspect secret scan findings privately; never publish findings or credentials in PR comments.
8. Configure public HTTPS callback and confirm payOS webhook in the CoVie channel ONLY after safe deployment and security audit. The payOS confirmation sample must not grant entitlements. Record webhook delivery proof without raw account data.
9. An end-to-end real-money test requires owner approval; because payOS has no sandbox, use an agreed small amount only after verifying provider terms, invoice/tax responsibility, receipt reconciliation, and merchant/legacy protection.
10. Rollout: `billing.enforcement_enabled` stays OFF until legacy grants, buyer checkout guards, catalog/discovery/Need/ads enforcement and release acceptance are complete.

## Current local versus production boundaries

- Entitlement guards, existing-order preservation, discovery/Need/ads, controlled legacy grants/shadow, reminders, cancellation, provider recovery, manual reconciliation, finance roles/audit and manual refund cases now have local implementation and mapped evidence. Older claims that these classes do not exist are superseded, not production acceptance.
- Remaining whole-scope local work includes final current-artifact/backend security checks, exact owned-stack UI orchestration and full-plan operational evidence audit. Follow the latest progress report; do not infer completion from30 BIL rows.
- Provider channel compatibility, HTTPS delivery, legal/tax/invoicing, actual alert destination/on-call and an explicit human real-money pilot remain separate operator/external gates. Normal flags stay OFF.
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
public build-time NEXT_PUBLIC_* configuration. Current `compose.prod.yml` requires
separate `COVIE_API_ENV_FILE` and `COVIE_WEB_ENV_FILE`. The latter must contain no API
keys, DB credentials or JWT keys. Existing deploy/rollback scripts also validate
`COVIE_ENV_FILE`; point that legacy validation variable at the same private API file,
and explicitly supply both Compose file variables. No actual deployment is included.

### Optional provider reconciliation (V023 and subsequent recovery implementation)

`COVIE_BILLING_RECONCILE_ENABLED=false` by default. If enabled only after
provider credentials and operational controls are verified, an opt-in scheduled
worker checks up to 20 pending invoices every 15 minutes against authenticated
payOS GET link information. It stores minimal observations in
`billing_reconciliation_observations` and logs invoice ID plus a discrepancy code.
Authenticated HMAC-verified PAID observations now use the same atomic idempotent
settlement path as callbacks, including amount/order/link checks. Unsigned or
contradictory observations never authorize settlement. Manual Admin inspection of a
terminal invoice does not rewrite it. See the recovery/manual reconciliation reports;
an ordinary HTTP200 provider GET is not payment evidence. The worker is therefore
not merely a read-only DB observer, although its provider request is a GET.

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
- Production Compose requires separate API/Web environment files as described above; launcher regression removes all `PAYOS_*` values from Web startup. Do not put API secrets in the Web file or frontend build arguments. Actual deployment secret-manager configuration still requires operator verification.
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

Current manual finance operation steps and boundaries are in [Seller Billing finance cases](../../runbooks/seller-billing-finance-cases.md). Actual disposable HTTP/PG role, independent-refund, dispute and audit-failure evidence is recorded in [local finance/audit progress](local-finance-audit-progress-2026-10-18.md); it is not a real bank refund or production activation. Admin manual reconciliation API/UI remains missing at that checkpoint; the existing worker and alert count do not substitute for it.

1. payOS credentials remain only in the API runtime; **both** `PAYOS_PAYMENT_ENABLED=true` and `COVIE_BILLING_RELEASE_APPROVED=true` plus verified HTTPS app URL are needed to issue *new* payment links. Confirm real payment only after independently signed callback or signed provider reconciliation.
2. Return to the merchant Billing screen to renew manually; there is **no automatic debit**. Opt-out/undo changes metadata only and never transfers money.
3. A refund case must be explicitly marked REVIEWING by an Admin reviewer before a **different** global Admin approver may mark the externally executed refund CONFIRMED; the initial requester cannot approve their own case. Migration V030 stores reviewer and review time.
4. For `secret-history`, the only allowlisted new finding is a specifically reviewed false positive in a historical implementation-plan checklist. Do not disable Gitleaks or add wildcard ignores. If additional findings appear, treat them independently.
5. Test the nine-character `CV`+base36 bank description and verify payOS signs its `data` response in the user's actual channel before enabling any real charge. Any response mismatch must leave invoices unpaid for safe manual review.

## Updated source status (2026-10-16) — overrides early WIP snapshot

- Source migrations now extend to V034. V031 contains escaped newlines inside its SQL comment and does not create its intended CHECK; V033 removes the legacy constraint. V034 restores the intended1..60 reminder-offset bound without modifying either applied migration or dispatch rows. V032 adds server-bound quotes. Before V034, read-only check `select count(*) from billing_reminder_dispatches where days_before not between 1 and 60`; if nonzero, stop for operator review rather than deleting history. See `local-payment-recovery-migration-progress-2026-10-18.md` for disposable populatedV013/V019/V030 upgrade evidence. Do not manually modify existing applied Flyway migrations.
- HMAC-verified authenticated payOS GET can recover a paid invoice through the same idempotent webhook settlement path. Earlier claims that reconciliation is only observation are outdated.
- Manual Admin recheck is now available at POST `/api/v1/admin/billing/invoices/{invoiceId}/reconciliation` and the current Admin billing ledger UI, with current finance permissions, required reason/idempotency and audit. See `local-manual-reconciliation-progress-2026-10-18.md` and the finance-cases runbook; native proof uses only an owned fake provider, not real payOS acceptance.
- Admin finance reviewer/approver roles and vouchers are implemented; actual bank refunds remain manual. Do not interpret early WIP statements as current code status.
- Seller POST /api/v1/merchants/{merchantId}/billing/quote returns quoteId, quoteExpiresAt, amountDueVnd and planRevision. POST /checkout requires the one-use quoteId, exact price/version and Idempotency-Key. Quote identity is an opaque unpredictable DB-bound token, not a client-signed assertion.
- Admin POST /api/v1/admin/billing/plans/{planCode}/prices and POST /api/v1/admin/billing/policy require quoted published revision in If-Match, e.g. "3". Stale revisions are rejected.
- Production Compose uses separate COVIE_API_ENV_FILE (API-only keys) and COVIE_WEB_ENV_FILE (web-only values), not the earlier common COVIE_ENV_FILE. Never put payOS keys in Web env.
- Live pilot: verify provider's minimum positive VND amount for your channel before Admin creates a dedicated low-cost pilot plan. PAYOS_PAYMENT_ENABLED and COVIE_BILLING_RELEASE_APPROVED default false and only operator may temporarily enable them after approval; billing.enforcement_enabled remains false for pilot.
- Original approved BIL-001 through BIL-030 scenarios are restored in CoVie-Seller-Billing-Subscription-Implementation-Plan-v1.0.md. New acceptance-matrix-v1.md reuses BIL IDs inconsistently and cannot substitute for original criteria.
