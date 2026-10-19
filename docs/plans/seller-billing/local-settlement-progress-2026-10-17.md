# Local signed settlement and reconciliation — 2026-10-17

Whole Seller Billing goal remains **ACTIVE / NOT_READY**. Original BIL IDs remain authoritative. No real payOS connection,transfer,refund,release flag,enforcement,remote Git change or recovered database operation occurred.

## Previous full-gate result

The prior goal turn made actual progress:request fingerprint regression fixed and pilot test given an isolated database. Its full combined verify terminated exit0:965 reported,964 passed,1 skipped,0 failures/errors,5m35s (`.local/billing-current-verify-isolated-pilot.log`,summary JSON of the same prefix). Both SSR-browser opt-ins and the pilot fixture ran. The sole skipped case is `HotQueryPlanIntegrationTest.productionLikeDatabaseHasExpectedMigrationFixtureAndHealthyHotPlans`,which requires external JDBC/query-file environment. The pilot test separately checks actual repository query plans at pilot fixture scale. Do not count the skipped case as executed.

That full gate predates the new settlement test class and audit correction below;it is not their full-suite proof.

## Genuine missing audit

Approved plan section6.2 step6 requires audit alongside atomic settlement,outbox and entitlement. `BillingWebhookService` previously wrote the outbox but no audit event. New real-PG/Spring test executed seven scenarios:6 passed,1 failed;the failing scenario expected one invoice audit and found0 (`.local/billing-settlement-integration-red.log`,37.701s). This is a product gap,not a mocked-JDBC assertion or speculative refactor.

Minimal correction adds existing mandatory-transaction `AuditLogWriter.securityEvent` before the existing outbox append. Event is `BILLING_PAYMENT_SETTLED`,entity `BILLING_INVOICE`,with merchantId,provider and amountVnd only. Verified external payment is not attributed to a Seller/Admin user. No raw reference,signature,account data,provider body or secret is retained in audit metadata. Existing request ID is captured when available. Deduplication returns before creating another audit. No schema,API,pricing,entitlement duration,signature algorithm or provider contract changed.

## Actual test boundaries

`BillingSettlementIntegrationTest` starts its own PostGIS18 database and real Spring API on a random port;Flyway001–033,JPA/JDBC transaction manager,membership repository,checkout,signature verifier,settlement,reconciliation,audit and outbox persistence are real. OWNER identity lookup is mocked;this does not prove issued-session Seller authorization. Its test-only primary `FixtureProvider` overrides link creation and lookup in-process,returns reserved `.invalid` URLs,and inherits actual production response-signature verification. No requests reach payOS or a fake outbound HTTP server. Production payment flags are false;only this fixture overrides gateway availability.

Fixtures independently construct alphabetically sorted HMAC-SHA256 payloads using JCE,not the production signing helper. Primary references consulted: [payOS payment-request signature](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/) and [webhook envelope](https://payos.vn/docs/du-lieu-tra-ve/webhook/). These support signature/envelope construction,not a real-provider acceptance claim. Timestamp tampering is tested as signed-data tampering;no invented provider freshness window is enforced or claimed.

Scenarios:

1. Actual anonymous HTTP signed webhook settles;HTTP duplicate and reordered distinct reference do not extend twice;ACK has no-store.
2. Timestamp tampering without re-signing rejects401;independently signed underpayment,overpayment,wrong currency and wrong link reject409/422 without changing invoice or granting entitlement.
3. Lost webhook recovers via signed provider data through actual reconciliation;late webhook is duplicate.
4. Callback and reconciliation actually wait simultaneously at the held PostgreSQL invoice lock (`pg_stat_activity` asserts2 lock waits),then commit one grant,one successful attempt,one outbox and one audit.
5. Real PostgreSQL trigger fails outbox insertion at commit:invoice,grant,subscription,inbox and audit roll back;same signed payload retries successfully after the test trigger is removed. This is DB failure/transaction recovery,not OS-process restart proof.
6. Early renewal uses the previous paid-through instant;two payments create two grants and advance the subscription without overlapping the reserved periods. Late/calendar cases remain separate.
7. One privacy-minimal invoice audit persists after success/replay.
8. A signed HTTP callback arrives inside the fixture's create-link call before the response is returned/persisted. The existing binding/persistence-race path returns PAID and retains exactly one grant/attempt/outbox/audit.
9. Fixture loses the create-link response after reservation;attempt has no local provider link. Independently signed lookup data binds and settles that attempt through reconciliation,without another checkout.

Focused corrected run terminated exit0:14 passed,0 failures/errors/skips,39.711s (`.local/billing-settlement-integration-green.log`):7 integration cases plus7 existing recovery/reconciliation unit cases. After adding the two explicit early/lost-response cases,the affected integration class passed9/9,38.177s (`.local/billing-settlement-early-recovery.log`). These are separate runs,not one aggregated16-test result.

Original BIL-009 and BIL-011 are now PASS for local acceptance in the original-ID matrix,with their own evidence column. Other rows retain NOT_TESTED where complete scenario acceptance is missing;partial proof is listed honestly. No live-provider acceptance is implied.

Full verify including SSR-browser/pilot-fixture opt-ins terminated **exit0**:974 reported,973 passed,1 skipped,0 failures/errors,6m03s (`.local/billing-current-verify-settlement-audit.log`,same-prefix summary JSON). Session99253 is terminal;do not poll/restart it. Sole skip is environment-driven hot-query-plan gate. Production source/test hashes for this change are in `.local/billing-settlement-source-hashes.json`. Later test additions/results are in `local-authorization-provider-progress-2026-10-17.md`;they are not included in this full count. Frontend/contract gates are not rerun for this BE-only,contract-preserving change. E-drive original checkout rechecked:384 hashes,0 changed.

Next action: Finish focused settlement verification,then current full BE gate and remaining original BIL HTTP/browser/migration/recovery acceptance.
Required from you: Nothing
Completion condition: Scoped cases have executed evidence and any discovered gap is corrected;whole objective is not complete until all local requirements are proven.
