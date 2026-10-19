# Seller Billing — BIL020 legacy/shadow local evidence, 2026-10-18

## Scope / status

Whole original Seller Billing/payOS goal remains **ACTIVE / NOT_READY**,24/30 originalBIL accepted locally, not production completion. Remaining008/023/025/028/029/030 plus recovery/security/operational/pilot requirements remain in scope. Social local completion is preserved, not resumed. No real money/provider, normal stack/recovered database, remote write, branch, commit, merge, rollout or flag activation.

Fresh08:11originfetch succeeded: isolated detached HEAD=remote feature41abfce04276950db2b680d44cd1b6daad0106e3;origin/mainbb2e06b41282c8e9205c35a13e7b6adec68ceaa2. Original384primary dirty file hashes still match `.local/billing-legacy-shadow-original-preservation.json`.

## Prior full / SSR fact

Update08:21:44: subsequent production-shadow full59846 is terminal exit0,1026reported/1025PASS/1HotQuerySKIP/0failures/errors8m18s. Independent `.local/billing-legacy-shadow-full-verify-summary.json` was saved before focused Maven runs. SSR browser and Billing native opt-ins passed in this full invocation; the earlier intermittent SSR root cause is still unestablished, not a claimed auth fix. This full does not cover the populated Billing backup and DB-crash test edits added after its compilation.

Manual-reconciliation full80543 is terminalexit1,1023reported/1021PASS/1FAIL/1HotQuerySKIP/0errors,8m47s `.local/billing-manual-reconcile-full-final.log`. All43BillingHTTP/native cases passed109.8s, including the corrected shared counter. Independent XML summary `.local/billing-manual-reconcile-full-final-failed-summary.json` and HTTP XML saved before further Maven runs.

Sole failure was unchanged SSR browser test at existing `ssr-refresh-browser.mjs:35` pathname-after-reload assertion. No Billing source touches this auth/SSR path. Unchanged isolated `SsrRefreshBrowserIntegrationTest` rerun2PASS38.012s `.local/billing-manual-reconcile-ssr-isolated.log`, native output confirms f5Refreshes2/revokedAndExpiredlogin/cookiePathspreserved. Root cause of this intermittent full-run failure remains **unestablished**; not labelled fixed and not silently ignored. New full gate below will include it again alongside the required production shadow change. No speculative auth fix or blanket full-suite replay solely to hide it.

## Reproduction / minimal implementation

- Original section11.4/BIL020 requires computed shadow decisions. Existing enforcement-OFF comment/fast-return was not evidence; legacy tests had only cutoff validation and preview lacked any shadow result.
- Unit RED `.local/billing-legacy-shadow-red.log`1FAIL3.723s: shadow object absent. Actual issuedJWT/HTTP/PG RED `.local/billing-legacy-shadow-http-red.log`1FAIL34.945s: expected evaluatedMerchants5,actual0 because field absent. Earlier fixture setup, real checkout and old counts succeeded; not an invented compiler failure.
- Only production change is existing `BillingLegacyMigrationService.preview`: keep its existing fields/criteria/count semantics, add optional billing-only shadow snapshot using the same active-grant canSell predicate, consistent REPEATABLE_READ reads and fixed-label aggregate Micrometer counters. No modification of working entitlement/public SQL/commerce paths, scheduler, flag, migration, dependencies, pricing or grant mutation semantics.
- Preview is explicitly operator-invoked, not automatic live-traffic sampling; failures cannot poison commerce transactions because no shadow queries are inserted into those transactions. It does not establish complete moderation/stock/order rights, production false-denial correctness, §11 inventory/notifications/rollout or live cohort approval.

## Actual local evidence

|Evidence|Result and scope|
|---|---|
|`.local/billing-legacy-shadow-green.log`|4PASS39.447s:1realHTTP grant/shadow case,2oldcutoffunits,1newshadowunit|
|`.local/billing-legacy-shadow-final.log`|4PASS46.570s:1HTTP strengthened guards/metrics,1realPG exact-boundary case,2OpenAPI; not an8case aggregate with prior run|
|`.local/billing-legacy-shadow-audit-final.log`|ONLYaffectedHTTP1PASS34.736s after adding exact audit metadata and untouched non-grant financial-table assertions|

HTTP fixture owns PG18/latestFlyway34, real issuedReader/Approver/Owner/BuyerJWT, real API/transactions; no actor mocks. Only private fixture enables LEGACY_GRANTS_APPROVED. Five old eligible shops cover active, future, expired, revoked and missing grants; newer/deleted exclusions are explicit. Before migration shadow reports5currentlyAllowed/1wouldAllow/4wouldDeny while actual BuyerCOD preview/commit succeeds. Full6finance/9commerce table JSONB plus merchant/audit snapshots are unchanged by preview. First actual Admin grant creates5LEGACY; repeated identical request creates0 and exact grant/financial JSONB remains unchanged. Non-LEGACY grant history and other five financial tables remain unchanged; no provider POST or invoice creation. Both audit records preserve actual approver/reason/cutoff/deadline and created5/0. Post-grant preview shows5previouslyGranted/5wouldAllow/0wouldDeny, and a second actual checkout succeeds. Reader grant403; invalid deadline/future cutoff422; grant with enforcementON422 and read-only shadow matches5current allowances. Normal flags remain OFF outside this disposable fixture.

Actual server-wired MeterRegistry ALLOW/DENY deltas6/4 match the two OFF previews. This proves in-process metric wiring, not HTTP Prometheus scrape/Grafana/alert delivery. Separate realPostgres/fixedAppClock SQL fixture proves exact valid_from inclusion, exact valid_until exclusion, future/expired/revoked exclusion, no-grant denial and overlapping grants counted once:7merchantdelta/2allow/5deny. Only this boundary fixture mocks Admin access; its SQL is real. HTTP role/traffic proof comes from the separate real issuedJWT case.

## Contract / preservation

Canonical export and official client generation/check plus Web typecheck exit0. Semantic diff `.local/billing-legacy-shadow-openapi-diff.json` only adds LegacyBillingShadowPreview schema and optional Preview.shadow;172paths unchanged,230→231schemas. Independent removing-shadow comparison leaves oldPreview exactly equal `.local/billing-legacy-shadow-contract-summary.json`.

No UI source/runtime behavior changed this phase; retain previous fullWeb499PASS12SKIP107.18s/typecheck/lint/build/native UI proofs within their unchanged scope. Do not replay whole frontend or rebuild normal `.next` just for erased generated-type additions.

Source34copied files SHA256 manifest `.local/billing-legacy-shadow-source-manifest.json`; Gitleaks8.30.1 exit0/no findings867189bytes `.local/billing-legacy-shadow-security.log`, no ignore/allowlist change. Not a whole image/CVE/license/CodeQL/remote CI acceptance claim. No new migration, private env disclosure or production legacy cutoff/deadline invented.

## Running / next

Full verify session59846 `.local/billing-legacy-shadow-full-verify.log` is terminal exit0:1026reported/1025PASS/1HotQuerySKIP/0failures/errors8m18s with SSRbrowser/pilot/manual-reconciliation native opt-ins. Independent XML summary is preserved. Subsequent populated-backup and DB-crash test extensions are tracked separately in `local-populated-backup-recovery-progress-2026-10-18.md`; they are not retroactively covered by this full gate.

Next action: Execute remaining original recovery/backup/restart and acceptance requirements.
Required from you: Nothing
Completion condition: All remaining local requirements have actual evidence, with provider/real-money steps separately bounded and no fabricated completion.
