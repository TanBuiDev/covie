# Seller Billing — local entitlement, moderation and rollback evidence

Status: IN PROGRESS / NOT_READY for the complete original Seller Billing objective. This continuation adds actual evidence for BIL-015/016/017/026/027. Existing production behavior passes and is preserved: no application, API, migration, frontend, dependency, deployment or normal-environment flag change.

## Environment and fixture scope

`BillingAuthorizationHttpIntegrationTest` owns a fresh PostGIS18 database, Flyway001–034, real Spring HTTP API, real issued OWNER/Buyer JWTs and memberships, real checkout/orders/inventory/entitlement transactions and the existing real-adapter/owned-loopback fake-provider transport. No entitlement, trading-policy, repository, inventory lock or authorization behavior is mocked. No request reaches real payOS or a bank.

New test-only fixture helpers seed approved active products/location offers with tracked inventory. Payment setup uses actual checkout and an independently signed fake-provider callback. Enforcement is enabled only inside this disposable fixture. To exercise expiry safely without waiting a month, `fixtureTerm` prepares only that fixture's paid grant/subscription term and grace timestamps; it does not edit the original paid invoice/snapshot. These term preparations are not evidence of monthly calendar arithmetic, scheduler transitions or production data migration.

## Executed invariants

### BIL-015 — grace READ_ONLY

`graceReadOnlyDeniesNewPreviewAndCommitButExistingOrderCanComplete` creates a paid merchant, a real existing tracked-inventory order and a second checkout preview while entitled. Fixture term preparation moves the merchant into PAST_DUE with the same expired grant/subscription endpoint and a three-day grace window. Actual billing summary returns `canSell=false`, `GRACE_PERIOD_READ_ONLY`. Both a new Buyer preview and commit of the previously issued token reject409/`BILLING_SUBSCRIPTION_REQUIRED`.

Full-row JSONB snapshots of nine commerce tables remain identical after denial: orders, items, product fulfillments, order events, balances, reservations, inventory movements, checkout sessions and outbox. The Seller can still read/confirm/mark-ready the old order; the Buyer confirms received and reads COMPLETED. Exactly one old-order reservation becomes COMMITTED and one SALE exists; no new order is created and the original paid invoice JSONB remains identical.

### BIL-016 — real expiry while commit waits on inventory

`entitlementExpiresDuringActualInventoryLockWaitAndCommitPreservesExistingOrder` creates a paid merchant, old order and new preview, then prepares the test term to expire ten seconds later. An independent PostgreSQL transaction holds the actual inventory-balance row lock. Actual Buyer HTTP commit passes its initial entitlement validation and is observed in `pg_stat_activity` waiting on that inventory lock while wall-clock time is still before expiry. No clock mock is used. Only after actual wall-clock expiry does the fixture release the lock.

Commit rejects409/`BILLING_SUBSCRIPTION_REQUIRED` on revalidation. The same nine commerce-table JSONB snapshots remain identical; the old order stays PENDING_CONFIRMATION and total merchant orders stay one. This proves the named inventory-lock wait boundary, not every theoretical instruction-level timing or unrelated transaction race.

### BIL-017 — payment cannot override moderation

Parameterized `settlementCannotOverwriteModerationOrEnableBuyerCheckout` covers merchant SUSPENDED and moderation HIDDEN. It creates the billing payment request before moderation changes, snapshots the moderated merchant's complete row, then sends the actual signed settlement. Invoice becomes PAID, exactly one live paid grant exists, and the billing-only summary reports entitlement active. Nevertheless Buyer checkout rejects422/`OFFER_NOT_AVAILABLE`. Merchant fullJSONB and the commerce snapshots remain identical after denied checkout: financial entitlement is not permission to bypass moderation.

### BIL-027 — Buyer methods are not Seller payOS

`buyerCodAndNegotiatedBankOrdersNeverBecomeSellerPayosPayments` creates one actual fake-provider Seller subscription payment, then Buyer preview/commit/reads for both COD and BANK_TRANSFER_NEGOTIATED. Each order is201, stores its own method/merchant/10000VND total and remains readable by its Buyer. All six existing financial snapshot tables remain identical after each Buyer order; there are two commerce orders but exactly one Seller invoice/PAYOS attempt/providerPOST. No bank transfer is performed or inferred from the negotiated method metadata.

### BIL-026 — enforcement rollback only changes the gate

`disablingEnforcementRollsBackOnlyTheGateWithoutChangingFinanceOrExistingOrders` creates a paid merchant and old order, prepares an expired term with no remaining grace, and proves new Buyer preview denied. Removing only the fixture enforcement setting yields the real summary `enforced=false`, `canSell=true`, `ENFORCEMENT_DISABLED`. Finance and all nine commerce-table snapshots remain equal across the switch. Buyer creates a new order, the old order's complete row remains unchanged by that creation, and the old fulfillment/receipt flow still reaches COMPLETED. Financial snapshots remain equal after both flows, with no additional providerPOST. This is logical feature-flag rollback evidence, not application-release rollback or backup/restore evidence.

## Actual runs and limits

- `.local/billing-entitlement-boundary-http.log`: four cases PASS,zero failures/errors/skips,55.959seconds. Includes grace, real inventory-lock expiry and the original two moderation cases.
- Only the moderation assertions then change: add exact error code, positive active-billing-entitlement control and commerce preservation. `.local/billing-moderation-paid-http-final.log`: two affected cases PASS,36.621seconds. The unchanged grace/expiry tests are not replayed.
- `.local/billing-buyer-payment-separation-http.log`: one case PASS,38.146seconds; both Buyer methods execute within that case.
- `.local/billing-enforcement-rollback-http.log`: one case PASS,37.095seconds.
- Six distinct final scoped cases are covered across these runs; there is no invented combined six-case Maven run or merged whole-suite count. No failing application behavior or production repair is claimed.
- Production/frontend/build inputs remain unchanged:1244 matching hashes using the prior snapshot plus the already-verified catalog-service overlay, `.local/billing-boundary-production-provenance.json`. Reuse prior full Maven989reported/988passed/one skip and prior frontend496passed/12skipped/build/typecheck/lint evidence for their actual scope; these new test methods are not retroactively part of those full counts. No unnecessary full-suite/build/browser replay.
- The only changed test source has a matching owned copy/SHA256 in `.local/billing-boundary-source-manifest.json`; Gitleaks scans it with zero findings/exit0 (`.local/billing-boundary-security.log`). No new exception/ignore or security-gate waiver. Prior whole-goal dependency/image/CodeQL limitations remain outstanding.

The original matrix reaches14/30 accepted local BIL cases, not whole-goal completion. Remaining original rows, calendar/trial/legacy behavior, broad discovery/Social/Ads integration, finance/recovery/security and full UI acceptance remain active where unproved. Normal payOS/release/reconciliation/enforcement flags remain OFF; existing dirty work and recovered data are untouched.

Next action: Exercise original BIL-013/014 calendar/renewal and trial eligibility with actual persisted invariants, then continue the remaining original scope.
Required from you: Nothing
Completion condition: Named calendar/timezone and trial/abuse requirements have scoped authoritative evidence without changing already-correct business behavior.
