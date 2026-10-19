# Local JWT authorization and payOS adapter HTTP evidence — 2026-10-17

Whole goal remains **ACTIVE / NOT_READY**. Previous goal turn was progress:actual missing settlement audit corrected and nine integration scenarios verified. This continuation completes its full-gate observation and adds required provider-transport/authorization evidence without changing working application code.

Later combined boundary/native browser evidence and a scoped Admin select-overflow correction are in `local-combined-browser-progress-2026-10-17.md`;the separate-boundary limitations below describe this earlier checkpoint,not the newer combined happy path.

## Terminal full BE gate

`.local/billing-current-verify-settlement-audit.log` terminated exit0:974 reported,973 passed,1 skipped,0 failures/errors,6m03s. Actual executable JAR produced. Both SSR-browser cases,pilot fixture and nine settlement integration cases ran. Remaining skip:environment-driven `HotQueryPlanIntegrationTest` JDBC/query-file case,not counted as passed. Summary:`.local/billing-current-verify-settlement-audit-summary.json`.

This full gate includes the current production audit correction but predates the two test-class additions below. Do not add their counts to974 and call that one full run. No full suite or unchanged frontend gates are replayed just for these test-only additions.

## payOS adapter HTTP boundary

`PayosGatewayHttpIntegrationTest`:4 passed,0 failures/errors/skips,14.060s (`.local/billing-payos-http-integration.log`). Actual production request serialization,headers,request signing,response parsing,response HMAC and link validation run against an owned loopback HTTP server. The server independently verifies dummy client/API headers and canonical request HMAC,then signs its JSON response using JCE.

The test captures only the gateway's `HttpClient` construction with a scoped Mockito static mock and relays its constructed HTTPS request to loopback using a real Java HTTP client. It asserts the original official origin/path and timeout before rewriting the destination. Static mock closes before requests;server closes after each case. Only the new-payment availability guard is overridden in this unit fixture. Ordinary release/payment process flags remain false. No production URL override,trust-all TLS,real key,real payOS request or actual checkout navigation is introduced.

Verified:successful create plus signed lookup;forged response signature;independently signed wrong amount/order;unsafe checkout host;HTTP200 with forged lookup data remains unverified;HTTP503 exception excludes raw provider body and dummy secrets. This proves the adapter's HTTP contract through a test transport,not payOS TLS/live account/provider acceptance. The DB-backed settlement fixture from the preceding report still uses an in-process provider;these are not yet one combined DB-to-outbound-HTTP-to-browser journey.

## Actual issued-session HTTP authorization

`BillingAuthorizationHttpIntegrationTest` owns a separate PostGIS18 database,actual Flyway001–033,real Spring HTTP API,real membership/role/session persistence,real `InitialSessionIssuer`,RS256 token validation,active-session checks and real `CurrentActorProvider`. No actor,authentication,authorization,repository or billing SQL is mocked. The test-only primary provider overrides link creation in-process;callback HMAC is independently generated and submitted through actual anonymous HTTP.

- Initial3 cases passed,33.351s (`.local/billing-authorization-http.log`).
- The role case then explicitly seeds the foreign actor's own shop and adds subscription/foreign-summary checks;only that affected case reruns:1 passed,33.206s (`.local/billing-authorization-roles-final.log`). The other2 unchanged cases are not replayed;this is not a combined4-test aggregate.
- MANAGER/STAFF receive403 and foreign OWNER receives404 for invoice list/detail,subscription,quote,checkout,cancel/resume renewal and valid refund request. Ordinary USER actors cannot read Admin invoices. Legitimate members can read summary;revoking their membership makes the next request with the same still-valid JWT return404. Guest invoice access returns401. Full JSONB snapshots of invoices,attempts,grants,subscriptions,quotes and finance cases remain identical;provider create count stays1.
- One actual owner purchases/settles for two shops;each invoice list returns exactly its own invoice with no-store. Configured enforcement exists only in this isolated fixture and is removed after the case. Expired grant/subscription state is seeded for the first shop only:its canSell=false while the second staystrue;cross-shop invoice lookup404 and historic first invoice JSONB stays identical. This is isolation across an expired fixture,not a scheduler/calendar expiration proof.
- Actual owner HTTP same-key replay succeeds after quote consumption/expiry,returns the original invoice and makes no additional provider call;changed quote returns409 with the expected idempotency error. Concurrent click and historical exact-body limitations remain separate.

Original BIL-002 and BIL-004 now PASS for local acceptance. Together with previously accepted009/011,this is **4 of30** original scenarios accepted,not whole-goal completion. Remaining rows retain honest partial evidence/NOT_TESTED statuses.

## Preservation and next work

No production source/schema/contract/UI/dependency change in this continuation. Current full gate is not a production image/deployment proof. Security scans from earlier reports predate these new test files;no new allowlist or security-gate suppression added. Real-money/release flags and normal enforcement stay OFF;no Git commit,push,merge,normal-stack restart or recovered-data use.

Next action: Join the actual adapter HTTP fixture with DB-backed issued-session acceptance,then run Seller/Admin browser journeys at360/390/1366 and remaining original-BIL/migration/recovery requirements.
Required from you: Nothing
Completion condition: One combined local journey has authoritative HTTP/DB/browser evidence without provider traffic;remaining scope stays active until proven.
