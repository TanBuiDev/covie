# Seller Billing — manual Admin reconciliation, 2026-10-18

## Scope and current status

Whole original Seller Billing/payOS goal remains **ACTIVE / NOT_READY**. This closes the missing manual recheck capability, not the whole project or production acceptance. Original BIL021 now has local API/permissions/audit/native UI evidence; original matrix is23/30PASS. Remaining008/020/023/025/028/029/030 and operational/security/recovery/pilot requirements remain in scope.

Fresh origin fetch at07:33 ICT succeeded: detached local HEAD and remote feature both41abfce04276950db2b680d44cd1b6daad0106e3; origin/mainbb2e06b41282c8e9205c35a13e7b6adec68ceaa2. No remote write, merge, branch, commit or deployment. Billing work stays in the isolated managed checkout; original384primary dirty file hashes still match `.local/billing-manual-reconcile-original-preservation.json`. Normal payment/release/reconcile/enforcement flags stay OFF. No real provider, money, refund, recovered database or normal runtime operation.

## Implemented

- New POST `/api/v1/admin/billing/invoices/{invoiceId}/reconciliation`, mandatory reason and Idempotency-Key, no-store response. Uses existing global Admin fallback or current finance OPERATOR/APPROVER, checks permission before replay. Reader/Owner denied; revoked sameJWT denied.
- Dedicated namespace in existing idempotency table, actor/invoice/full-body fingerprint. Short advisory-lock claim transaction, provider I/O outside transaction, short fenced completion transaction. REQUESTED audit commits before provider I/O and retains reason in restricted audit; COMPLETED audit/result commit atomically. No new migration or generic executor transaction across network.
- Active lease409; expired lease retryable; stale completion409 without replacing reclaimed result. Technical lease30s exceeds existing provider12s request timeout.24h existing operation retention, not a business payment expiry change.
- Pending invoices reuse authenticated/HMAC-verified provider recovery and shared settlement. Terminal invoice inspection never mutates financial tables; contradictory state or missing/nonzero received funds on final unpaid provider status requires manual review. Original worker/queryProvider semantics preserved.
- Admin invoice list adds server-owned allowedActions computed once per request. Existing Admin UI uses generated Invoice and reconciliation types. A scoped form accepts invoice/reason, blocks overlapping submissions, preserves retry key after uncertain failure and displays server result/time in Asia/Ho_Chi_Minh. No provider payload, credentials, browser-controlled amount or localStorage.

## Exact verification

Prior finance full session96046 terminalexit0:1017reported,1016PASS,1HotQuerySKIP,0failures/errors,7m42s; `.local/billing-finance-audit-full-verify.log` and independently parsed `.local/billing-finance-audit-full-verify-summary.json`. This predates the new reconciliation production source and is not its final full gate.

| Evidence | Actual result |
|---|---|
|`.local/billing-manual-reconcile-red.log`|1FAIL29.898s, absent endpoint404instead of required Reader403|
|`.local/billing-manual-reconcile-green.log`|1PASS32.248s, real issuedJWT/HTTP/PG/owned provider/signed recovery/replay/audit|
|`.local/billing-manual-reconcile-focused.log`|4PASS34.119s: recovery, terminal/unsigned, concurrent network transaction separation, audit failure/resume|
|`.local/billing-manual-reconcile-actions-red.log`|1FAIL28.702s, allowedActions missing|
|`.local/billing-manual-reconcile-stale-red.log`|1FAIL32.793s, stale completion500instead of409|
|`.local/billing-manual-reconcile-contract-green.log`|18PASS1m10s:5manualHTTP,4finance access,4provider recovery,3comparison,2OpenAPI. Not full BE|
|`.local/billing-manual-reconcile-final-funds-red.log`|1FAIL29.192s: signed EXPIRED/amountPaid5000 incorrectly reported NONE for VOID|
|`.local/billing-manual-reconcile-final-funds-green.log`|1PASS33.859s: signed EXPIRED/CANCELLED partial/missing/zero funds; terminal financial JSONB unchanged|
|`.local/billing-manual-reconcile-ui-red.log`|Missing module import,0tests executed; not three behavioral failures|
|`.local/billing-manual-reconcile-ui-green.log`|3PASS1.21s: server permissions, single in-flight submission, pending server result, uncertain retry key and new operation after completion|
|`.local/billing-manual-reconcile-web-typecheck.log` and `-web-lint.log`|typecheck and targeted3file lint exit0|
|`.local/billing-manual-reconcile-web-build.log`|Next production build exit0|
|`.local/billing-manual-reconcile-native.log`|1PASS41.119s, actual Chrome/native production Next/API/PG/owned provider at360/390/1366|

Native `.local/billing-browser-1792284088280/result.json` completedtrue,pageErrors0,three15kPAID invoices,threeproviderPOST/threeproviderGET. No webhook sent: only Admin signed recheck settles. Each first successful response was deliberately lost after actual server commit; native retry uses identical key and cached result, no extra providerGET/audit. Reader sees ledger but sends no mutation; Operator uses keyboard Enter and sees confirmed result/updated row. Six reader/operator screenshots were opened and visually reviewed; routes/unique heading/no-horizontal-overflow asserted before capture. Existing narrow mobile ledger wrapping retained, not a UI redesign or whole a11y acceptance. Java independently asserts one attempt/grant/settlement audit/outbox plus one manualREQUESTED/COMPLETED per invoice. Provider transport is loopback fake, not bank/payOS/TLS/WAN evidence.

OpenAPI semantic diff `.local/billing-manual-reconcile-openapi-diff.json` adds one path/two schemas and extends only Invoice.allowedActions; no old path removed/modified. Official generation and client check exit0. Source31copied files hash-matched `.local/billing-manual-reconcile-source-manifest.json`; Gitleaks8.30.1 exit0/no findings843193bytes `.local/billing-manual-reconcile-security.log`. No ignore/allowlist change; not whole dependency/image/CodeQL security acceptance.

## Running final gates / next scope

Full79016 is now **terminalexit1**,1023reported/1021PASS/1FAIL/1HotQuerySKIP/0errors,9m54s `.local/billing-manual-reconcile-full-verify.log`. Independent XML totals/HTTP XML were saved in `.local/billing-manual-reconcile-full-verify-failed-summary.json` and `-full-failed-http.xml` before another run. The only failure is native fixture final aggregate: provider creates3/lookups9 versus assumed3, after all three native role/keyboard/paid/retry checks succeeded. Its provider is shared with earlier tests, so lookups had baseline6; this is a counter-isolation assertion error, not a product failure or permission/payment regression. Failed browser result `.local/billing-browser-1792284433303/result.json` remains completedfalse; it is not relabelled PASS.

Only the new native Python driver changed: capture actual baseline stats before the scenario and assert exact3create/3lookup **delta**. No application/contract/BE test/source change. Whole43case BillingAuthorization class with native opt-in41942 is now **terminalexit0,43PASS,0skip,2m12s** `.local/billing-manual-reconcile-class-final.log`; actual suite129.1s. Native `.local/billing-browser-1792284906877/result.json` completedtrue/pageErrors0 confirms shared baseline6lookups and exact3lookup/3create delta, not merely a fresh isolated counter. Three-width Reader/Operator/keyboard/lost-response replay and Java financial/manualauditpair assertions reached. XML saved `-class-final-http.xml`, summary `.local/billing-manual-reconcile-native-final-summary.json`.

Full Web37205 terminalexit0:499PASS/12SKIP/0FAIL,107.18s `.local/billing-manual-reconcile-full-web-test.log`. New31file snapshot `.local/billing-manual-reconcile-final-source-manifest.json` and `-final-source-check.json` all hashes match. New Gitleaks8.30.1 scan exit0/no findings843558bytes `.local/billing-manual-reconcile-final-security.log`; no ignore change and still not whole security acceptance.

Subsequent full80543 is now terminalexit1:1023reported/1021PASS/1FAIL/1HotQuerySKIP/0errors8m47s. All43BillingHTTP/native passed109.8s; sole failure is unchanged SSR browser pathname assertion. Isolated unchangedSSR2PASS38.012s, root cause not established or claimed fixed. Independent failed summary and BillingXML retained. See `local-legacy-shadow-progress-2026-10-18.md` for the next required production change/current full59846, not a1023green aggregate assembled from reruns. Older41942/79016/37205/80543 are terminal.

After terminal gates, record independent XML totals before another focused Maven run overwrites them. Continue remaining original requirements, especially actual Billing backup/restore/restart, checkout concurrency/historical replay limitation, reminder batching, whole UI/a11y and security/pilot readiness. Do not stop at this endpoint, mark the full goal complete or request real credentials while safe local work remains.

Next BIL020 source audit: `BillingLegacyMigrationServiceTest` currently contains only two cutoff-validation units, not actual preview/apply/rerun proof. `BillingEntitlementService.evaluate` returns ENFORCEMENT_DISABLED immediately when enforcement is OFF; a source comment calling this OBSERVE is not evidence of the original section11 shadow requirement (compute would-deny decision and observe it without affecting traffic). No shadow implementation was located in the billing source search. Reproduce and implement the missing approved shadow behavior with default-OFF isolation before accepting BIL020; do not label simple enforcement-OFF as shadow PASS or invent a production legacy cutoff.

Next action: Collect these exact full gate results, then execute the next remaining original local acceptance requirement.
Required from you: Nothing
Completion condition: Current gates have terminal evidence and all remaining local requirements are closed without crossing the real-money boundary.
