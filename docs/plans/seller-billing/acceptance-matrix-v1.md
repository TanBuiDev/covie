# CoVie Seller Billing — BIL-001–030 acceptance matrix

**Scope:** seller pays, buyer remains free. **Branch:** `feat/seller-billing-subscription`. **Release status:** DRAFT / NOT APPROVED. No live money tests have been authorized.

Legend: `SOURCE` means implementation exists but **does not mean E2E passed**; `UNIT/PG` means a corresponding test source exists and needs latest-head CI confirmation; `LOCAL` requires operator-approved local/staging tests; `BLOCKED` requires work/approval before release.

| ID | Scenario and required invariant | Evidence target | State |
|---|---|---|---|
| BIL-001 | Buyer registers, browses, comments, likes, saves and checks out without billing charge | Buyer regression suite | SOURCE |
| BIL-002 | Seller creates a shop; payment-before-selling prevents new publications only after controlled enforcement rollout | Merchant creation + entitlement HTTP | SOURCE |
| BIL-003 | Admin trial policy issues at most one trial per OWNER, not per browser/email | V024 uniqueness + Spring transaction | SOURCE |
| BIL-004 | Admin sets another cycle/price; historical invoice price is immutable | V020 invoice snapshots | SOURCE |
| BIL-005 | Independent monthly/yearly plans can be added/retired without new Java deployment | Admin Billing UI/API | SOURCE |
| BIL-006 | Voucher FIXED VND discount preserves strictly positive payOS total | BillingVoucherServiceTest | UNIT/PG |
| BIL-007 | Voucher percent basis points rounds down and cannot discount 100% | BillingVoucherServiceTest | UNIT/PG |
| BIL-008 | Two concurrent merchants cannot exceed voucher's total cap | Voucher row FOR UPDATE; add concurrent Postgres test | SOURCE |
| BIL-009 | One merchant cannot redeem voucher beyond per-merchant limit | BillingVoucherIntegrationTest | UNIT/PG |
| BIL-010 | Local invoice, voucher reservation, amount and payOS expected amount match | V028/Flyway + checkout reservation | UNIT/PG |
| BIL-011 | Seller idempotency key retry returns the original invoice and refuses a changed voucher | Checkout reserve fingerprint | SOURCE |
| BIL-012 | payOS disabled requires zero external POSTs or real money | PAYOS_PAYMENT_ENABLED and COVIE_BILLING_RELEASE_APPROVED | SOURCE |
| BIL-013 | payOS POST succeeds but DB/persistence fails; recovery finds provider order | Reconciliation recovery | LOCAL |
| BIL-014 | Valid signed callback completes exactly once | BillingWebhookService/PayosSignatureTest | SOURCE |
| BIL-015 | Forged/tampered webhook and payOS GET response cannot grant access | BillingProviderRecoveryTest | UNIT/PG |
| BIL-016 | Signed callback racing local payment link persistence settles exactly once | Concurrent callback/checkout integration | SOURCE |
| BIL-017 | Lost callback + signed GET with one full paid transaction can settle safely | Reconciliation recovery | LOCAL |
| BIL-018 | Partial/multiple provider transactions are held for manual review | BillingProviderRecoveryTest | UNIT/PG |
| BIL-019 | Provider-cancelled or expired link with no money received becomes VOID | Signed provider finality + live/replayed GET | LOCAL |
| BIL-020 | Seller extends a valid subscription without losing unused paid period | BillingPeriodCalculator/Checkout | SOURCE |
| BIL-021 | Trial/grace time is snapshotted and honored consistently | V025 entitlement and SQL tests | SOURCE |
| BIL-022 | 7/3/1-day reminders have one outbox notification per period/threshold | V026 reminder DB lock/dedupe test | SOURCE |
| BIL-023 | Seller expiry blocks new catalog publish, Need response, ad submission | Backend entitlement guards | SOURCE |
| BIL-024 | Discovery, shop catalog, commercial detail and ad delivery hide unavailable offers without N+1 | SQL/HTTP query integration | SOURCE |
| BIL-025 | Existing paid orders and social content remain intact through seller expiry | Legacy merchant/checkout data regression | LOCAL |
| BIL-026 | Admin Reader/Operator/Approver powers are distinct when explicitly enabled | V029 + finance access API | SOURCE |
| BIL-027 | Refund/dispute case never initiates bank transfer by itself; confirmation needs independent proof | V027 + operator gate | SOURCE |
| BIL-028 | Admin grant and legacy migration never silently charge old merchants | Grant gating, preview, audit | SOURCE |
| BIL-029 | Local Spring/Next.js/Admin/Seller UX works with real payOS HTTPS webhook and keys injected API-only | Operator-approved acceptance | BLOCKED |
| BIL-030 | Complete CI, secret-history investigation, migration rollback and staged enforcement pilot signoff | Security/release checklist | BLOCKED |

**Current status:** This matrix is a QA checklist, not a statement that all 30 tests have run. Some are source-only and several must be implemented as focused concurrency/HTTP integration cases before final acceptance. Do not flip on charge/enforcement flags or merge based only on this document.

## Guardrails for testing

- Do not test a payOS payment with real funds until the operator has approved a public HTTPS endpoint, budget, callback verification and recovery workflow.
- Test voucher quota expiration against **provider-final** VOID, not client-side expiry. Client TTL alone never releases a held voucher.
- Test expired seller/catalog behavior without deleting product, user reactions or historic orders.
- Use fixed test-only credentials; never commit payOS Client ID, API Key or Checksum Key.
- A green backend/frontend build does not clear the separate repository-history secret detection.
