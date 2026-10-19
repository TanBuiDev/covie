# Seller Billing — effective prices and concurrent publication

Status: IN PROGRESS / NOT_READY for the complete Seller Billing goal. Original BIL-001–030 remain authoritative; this report covers BIL-005/006 and the changed-price portion of BIL-007. No real provider, transfer, production activation, staging acceptance, remote write or restored-data operation is performed.

## Concrete reproduction

The existing issued-session/real HTTP/PostGIS/fake-provider fixture now creates an independent10000VND plan, reserves a real invoice/payment attempt, schedules15000VND revision2, verifies revision1 remains public before its effective time, waits for revision2 through the actual public API, rejects the original unconsumed quote without creating a payment, and purchases a fresh15000VND quote. The original invoice and attempt full JSONB values remain identical and the original plan-version row remains10000VND. This case passed before production changes; no price/invoice behavior was rewritten.

For each publication race, a separate PostgreSQL connection holds the existing plan/oldest-policy row lock; two actual authenticated Admin HTTP requests reach `wait_event_type='Lock'`, explicitly observed in `pg_stat_activity` before releasing the blocker. Both send the same quoted revision and identical bodies. Before the fix, the loser could receive422 instead of the contracted409 because monotonic-effective-time validation ran before revision validation.

- Initial `.local/billing-price-concurrency-red.log`: three cases, one passes/two fail. Price race exposes actual `[200,422]`; the first policy failure is an incorrect new-test expected error code (`BILLING_PRICE_INVALID` instead of existing `VALIDATION_FAILED`), not itself product proof. Nanosecond request values versus PostgreSQL microsecond rounding could also change which validation runs.
- Corrected fixture uses millisecond-exact timestamps and the actual existing error-code contract. `.local/billing-price-concurrency-confirmed-red.log`: two actual races both fail with `[200,422]`, zero errors,33.464seconds. No production fix precedes this confirmed reproduction.

## Minimal repair

`BillingCatalogService.publishPrice` and `publishPolicy` now check the current published revision immediately after acquiring their existing row lock, before checking the effective-time ordering against published history. Eight existing lines move; no new lock, business rule, model, migration, API field or exception code is introduced. Initial request validation, authorization, immutable versions, monotonic scheduling, audit and outbox remain unchanged.

`.local/billing-price-concurrency-green.log`: seven cases pass/zero failures/errors/skips,44.013seconds. Three real-HTTP cases plus the existing If-Match parser and three quote-validation cases. Each race yields one200/one409, one new version, one audit and one outbox event. The new tests subsequently also check a current-revision request with the same invalid schedule still returns422; those extra assertions pass inside the final full gate's nine-case actual-HTTP fixture, not retroactively included in the focused seven-case run.

## Browser and artifact scope

New opt-in `billing-local-pricing.py` uses the existing owned Spring/PG/provider/builtNext/nativeChrome fixture, not mocked response data. At360/390/1366 it exercises a Seller10000VND quote, native Admin15000VND publication, a stale second Admin tab, actual Seller changed-price409/no providerPOST, fresh quote and payment followed by signed callback and server polling. `.local/billing-price-browser-final.log` terminates exit0: one combined case,zero failures/errors/skips,1m21s. `.local/billing-browser-1792261186842/result.json` completed=true/pageErrors=0/three providerPOSTs/no providerGET. All six Seller/stale-Admin screenshots viewed; destination/landmark/no-document-overflow asserted before capture. Java verifies exactlythree paid15000invoices with one succeeded attempt/grant/audit/outbox each. No browser opens real hostedpayOS.

First native attempt `.local/billing-price-browser.log` completed360,then failed inside Playwright filling `datetime-local` with a zero-seconds value ending`:00` (before any corresponding API request). Only the new Python driver normalizes that browser-canonical value by removing the zero-seconds suffix; no UI/application code is changed. This was a harness failure, not an application bug or a skipped assertion.

Frontend production/client inputs are unchanged:502 checkedSHA256 values/zero changes, `.local/billing-price-unchanged-web-inputs.json`. Reuse the already successful build and frontend gates from the prior report rather than replaying unaffected tests.

## Final backend gate and remaining scope

`.local/billing-price-full-verify.log` terminates exit0,7m47s:989reported/988passed/one skipped/zero failures/errors. Independent SurefireXML aggregation agrees in `.local/billing-price-full-verify-summary.json`. SSRbrowser, pilot fixture and billing-pricing-browser opt-ins execute. Sole HotQueryPlan case requires a configured JDBC/query-file fixture; it is skipped, not PASS and not automatically an external blocker. The actual-HTTP fixture passes all nine cases, including both locked races and the additional current-revision422 validation checks (`.local/billing-price-actual-http-cases.json`). Its combined native pricing result is `.local/billing-browser-1792261387839/result.json`,completed=true.

The original matrix accepts BIL-005/006/007 in addition to the earlier002/004/009/011/012. Whole-goal acceptance is not implied. Work continues into grace/read-only and commit-time entitlement HTTP boundaries; process restart, backup/recovery and the other unaccepted original rows remain in scope. No active/source security, real-provider, load, Figma or production claim is inferred from the full suite.

## Additional unpaid-draft acceptance after the full gate

`BillingAuthorizationHttpIntegrationTest#unpaidOwnerCreatesDraftButCannotPublishActivateOrSellThroughDirectHttp` verifies an actual issued OWNER/Buyer/HTTP/PostGIS journey with enforcement enabled only inside its owned fixture. An unpaid merchant creates a DRAFT201 with a server-default variant/location offer. Both direct publish/activate and actual Buyer checkout preview reject409/`BILLING_SUBSCRIPTION_REQUIRED`. Item fullJSONB and scoped outbox count remain unchanged after denial; there are no orders, billing invoices, grants or providerPOSTs. Production code already behaves correctly and is not modified.

The first new-test run wrongly expected preview422; the actual409 is the correct billing contract, not an application defect. After correcting the test and asserting the exact error code, `.local/billing-unpaid-draft-http-final.log` passes one case/zero failures/errors/skips,36.651seconds. This focused case was added after the full989-case gate and must not be folded into an invented990-case aggregate. No production/FE/API/migration changes follow the full gate. BIL-003 is additionally accepted:9/30 original rows PASS,21 still NOT_TESTED (some have partial evidence). The fixture flag is removed and owned database/container stopped after the test.

Gitleaks scans all three new/changed source files (catalog service, actual-HTTP fixture and new native driver) from a matching owned copy: zero findings/exit0, `.local/billing-price-security.log`, `.local/billing-price-source-manifest.json`. This delta scan does not replace whole-goal security acceptance; prior documented scanner/dependency/CodeQL limitations remain.

After adding the unpaid-draft case, only the test file differs from that three-file snapshot. Its updated matching owned copy is recorded in `.local/billing-unpaid-source-manifest.json` and separately scanned with zero findings/exit0 (`.local/billing-unpaid-security.log`). No new allowlist/ignore, application change or dependency update is introduced.

Next action: Verify grace/read-only and checkout commit-time entitlement boundaries through actual HTTP/PostgreSQL; retain the complete remaining original BIL scope.
Required from you: Nothing
Completion condition: Each next boundary has actual invariant-specific evidence; unchanged completed gates are not replayed without a relevant change.
