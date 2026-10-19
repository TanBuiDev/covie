# Seller Billing Ads expiry progress — 2026-10-18

Whole original Billing/payOS goal remains ACTIVE/NOT_READY. Original27/30local PASS before this chunk;025/028/030 plus wider security/operations/pilot requirements remain open. Normal payment/release/reconcile/enforcement flags OFF, no remote writes/commit/merge/deploy/real PSP/recovered data operation. Prior Social work remains preserved.

## Proven defect and bounded correction

Read-only plan review Approved; plan `docs/superpowers/plans/2026-10-18-billing-ads-expiry.md`. Actual issued-JWT/HTTP/PostgreSQL/owned signed-provider fixture `.local/billing-ads-expiry-http-red.log` reports7FAIL0errors30.791s18:21:01ICT. Precisely four Admin merchant/item target cases are meaningful product RED: unavailable grace/expired destinations still receive delivery, old token click204 instead of422 and event insertion. Three Seller/near-expiry cases failed first at the new fixture's incorrect Ads If-Match format, not product behavior. Do not claim seven product regressions.

Only AdService production changes: remove SQL grace-selling branch; retain moderation checks while resolving destination merchants; one active-entitlement expiry batch protects campaign and destination merchant; omit unavailable delivery and reject its old events; bound token expiry by campaign and entitlement as well as30minutes. No campaign/finance/schema/REST mutation. Technical pause ADR `docs/architecture/adr-seller-billing-ads-expiry.md` does not invent credit/refund policy.

Initial affected-class run `.local/billing-ads-expiry-green.log`:19run16PASS3fixtureFAIL0errors41.395s, not a green aggregate. Existing Ads12PASS and four meaningful new Admin cases PASS. New helper then changed only its Ads version headers to VersionPrecondition.format; no production weakening. Expanded final actualHTTP7PASS0skip0fail/errors21.07s/Maven30.700s18:23:41 `.local/billing-ads-expiry-http-final.log`: all Seller/Admin merchant/Admin item × grace/expired, old-click rejection, financial/campaign preservation, expired Seller submit409/old read200, unrelated `/search` ad, renewal resumption and OFF fallback. Near-expiry delivery respects actual current entitlement and a shorter campaign end after real Admin PATCH. Fixture term changes are explicit test preparation, not a production renewal or campaign mutation by a read.

Targeted existing-plus-new AdSlot8PASS5s `.local/billing-ads-expiry-ui.log`: known delivery expiry removes link before next refresh; no Web production changes. No whole accessibility/native/full-backend acceptance claimed from this component result.

## Native/full gates in progress

Standalone native fixture17664 RUNNING `.local/billing-ads-expiry-native.log`: extends existing real manual-reconciliation native acceptance with actual public home Ads at360/390/1366, real12second entitlement expiry, hidden after reload and renewed delivery; production Next already built from unchanged Web production source. Do not claim success until terminal output/result/screens inspected. Current full1037PASS1HotQuerySKIP predates AdService production changes; a new sequential full gate is required after final source/focused evidence.

## Native acceptance and subsequent review findings

Native17664 terminal1PASS75.08s/Maven1m24s18:26:13ICT. Actual built Next/Chrome155/JWT/API/PG/ownedprovider; `.local/billing-browser-1792322716767/ads-expiry-result.json` completedTRUE/pageErrors0. Each360/390/1366 shows actual merchant link, removes it at real12second paid-entitlement expiry, keeps it absent on reload and resumes after explicit fixture term renewal. Six active/expired screenshots VIEWED. Finance/campaign JSONB remain identical after the last explicit fixture term update; Ads adds no PSP create/lookup. These controlled time fixtures are not actual production renewal transfers. Owned processes/containers/normal3000/8081 absent after terminal.

Subsequent read-only implementation review found two concrete remaining source risks: eager target resolution of up to100candidates instead of stopping after10deliveries (even OFF), and absolute canonical-origin merchant/item destinations treated as external. BIL030 stays unaccepted. New actual counter10vs20 and canonical-origin HTTP cases have been added but are NOTYETRUN; no product-failure claim from these unexecuted tests. Current65324full was already compiled before these additions and remains RUNNING `.local/billing-ads-expiry-full-verify.log`; do not overlap Maven/restart on timeout. Preserve final XML before new tests and repair only after actual RED. No production changes since that full started.

Fresh18:29ICT `git fetch --prune origin` succeeds: feature/head41abfce unchanged/mainbb2e06b4 unchanged; additional dependabot refs fetched, not merged. Scoped10file Gitleaks8.30.1 exit0/no findings591550bytes before these subsequent test additions; snapshot manifest retained, not a current full-tree scan or later-test hash claim.

## Review-driven actual regressions

Full65324 terminated1045reported1044PASS1HotQuerySKIP0fail/errors8m38s18:35:43ICT; independent `.local/billing-ads-expiry-full-verify-summary.json` and fullHTTP XML saved before next Maven. It predates final review repairs/new test cases, not current-source final acceptance.

Actual `.local/billing-ads-review-findings-red.log`:12run6PASS6FAIL0errors42.536s18:36:50ICT. OFF10→20 candidates SQL23→33, ON44→54: confirmed new read amplification. Four Admin canonical-origin merchant/item cases still deliver unavailable offers and accept old clicks. Minimal fix restores lazy OFF, ON ten-row batches/stopafter10, canonical origin scheme/host/effective-port resolution. Affected25PASS0skip0fail/errors55.410s18:38:19 `.local/billing-ads-review-findings-green.log` includes13actualHTTP +12existingAds; no invented count aggregation.

Reviewer then identified browser dot-segment equivalence. Actual `.local/billing-ads-normalized-target-red.log`:14run10PASS4FAIL0errors42.167s18:40:26ICT. Relative `/search/../merchants/...` and canonical encoded-dot item URLs still bypass expiry/event guard. Final minimal normalization decodes dot escapes for path normalization and handles above-root segments before existing UUID classification; external origins are not normalized into internal origin. Final read-only review found no further concrete defects; no reviewer tests/edits.

Final HTTP/native64424 RUNNING `.local/billing-ads-final-http-native.log`:17expiry/query cases,2external compatibility cases and existing native fixture with additional actual Ads/time/browser-dot proof. Do not mark BIL030 from source/reviewer approval; collect terminal results/screens and then current-source sequential full. Web production source remains unchanged, so prior Web build/503PASS12SKIP remains scoped historical evidence rather than invented504PASS aggregate. Targeted Ads8PASS/lint0 retained.

Additional original-scope source audit (not completed acceptance): BIL028 price publication appends BILLING_PRICE_PUBLISHED but BillingNotificationHandler only supports reminder/settlement/expiry; no price-change recipient notification path found. Reproduce before scoped correction, and preserve approved effective-date/nonretroactive policy; do not invent founder discounts or dates. BIL025 real native finance tables show dense line wrapping on mobile; use exact DOM/legibility evidence before targeted UI repair, not global redesign. HotQuery opt-in and separate-process restart/security/pilot preparation remain local work, not automatic external blockers.

## Final focused/native outcome

64424 terminal20PASS0skip0fail/errors87.48s/Maven1m44s18:42:49ICT `.local/billing-ads-final-http-native.log`, independent HTTP XML retained. Includes14merchant-target expiry variants,2query-count cases,1entitlement/campaign-bound case,2genuinely external host/different-port compatibility cases and1combined native fixture. Current `.local/billing-browser-1792323711235/ads-expiry-result.json` completedTRUE/pageErrors0; all three widths prove active/remove/reload-hidden/renewed plus actual Chrome literal/encoded-dot URL normalization. Six final active/expired screenshots VIEWED. Finance/campaign unchanged after explicit fixture-term updates; no extra PSP request from Ads. Native helper touched only test-controlled fixtures, not normal environment. Same-origin host casing/default443 and external444/foreign-host behavior covered by actual HTTP. Last reviewer reports no further concrete defects; source review is not a security completeness claim.

Current-source final full92036 RUNNING `.local/billing-ads-final-full-verify.log` after final20PASS and final read-only review. SSR/pilot/manual-native opt-ins retained; extra Ads native option is not replayed needlessly because its final standalone evidence is current. Poll same handle; no app source edits since start, no concurrent Maven/restart on observation timeout or guessed totals. BIL030 remains pending this final gate; no whole-goal completion.

Final11file scoped source snapshot `.local/billing-ads-final-source-manifest.json`, Gitleaks8.30.1 exit0/no findings620112bytes `.local/billing-ads-final-gitleaks.log`. No allowlist change. This covers the final application edits and listed metadata at scan time, not image/CVE/license/CodeQL readiness or later metadata bytes. Next independent UI plan `docs/superpowers/plans/2026-10-18-billing-ui-local-acceptance.md` received read-only Approved; no UI app/test changes yet. Current API application source remains frozen for full92036.

## Final full gate

92036 TERMINALexit0 at18:53:54ICT:1057reported1056PASS1HotQueryopt-inSKIP0fail/errors9m16s. All-suite independent `.local/billing-ads-final-full-verify-summary.json` and `.local/billing-ads-final-full-http.xml` saved before another Maven. Current final application code matches its scoped source hashes. BIL030 accepted locally; original matrix28/30PASS,025/028 and wider security/operations/process-restart/pilot requirements remain open. Do not turn one skipped test into a pass or this local scope into real-provider/release readiness.

Next action: Execute approved scoped BIL025 UI plan, reproduce/fix BIL028 notice gap, then remaining local operations/security/pilot work.
Required from you: Nothing
Completion condition: Entire original local goal acceptance, not only Ads fixes; external money/provider gates remain explicit.
