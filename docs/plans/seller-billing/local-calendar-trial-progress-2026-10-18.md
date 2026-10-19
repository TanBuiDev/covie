# Seller Billing — calendar renewal, trial and ICT display

Status: IN PROGRESS / NOT_READY for the entire original Seller Billing goal. Scope is original BIL-013/014, not a replacement acceptance matrix. Normal payment/release/reconciliation/enforcement flags remain OFF. No real provider request, transfer, commit, push, merge, deployment or restored-data operation.

## Confirmed failures and minimal corrections

### Actual trial creation failed on the merchant foreign key

`.local/billing-calendar-trial-http-first.log` executes nine real-HTTP cases: seven calendar/late-invoice checks pass; the two trial cases fail. With actual Admin policy TRIAL/7days and fixture enforcement enabled, POST `/api/v1/me/merchants` returns500. PostgreSQL reports `billing_trial_claims_merchant_id_fkey`: JPA has scheduled the merchant/membership writes but `BillingTrialService` uses JDBC before they are flushed. Both concurrent registration requests also fail500.

`MerchantApplicationService.create` now flushes the existing repository immediately before calling the existing trial service. Everything remains in the same transaction; no new transaction, rule, migration or provider call. OFF-policy creation and OWNER identity rules are preserved.

### Late renewal retained an expired current-period start

The initial late test proved the new invoice started at current server time but did not yet inspect the current subscription start. The stronger actual-HTTP/PG assertion fails in `.local/billing-late-current-period-red.log`: one failure,zero errors; subscription start still belongs to the expired term while the new invoice/grant starts now. Cause: unconditional `coalesce(old.period_start,new.period_start)` in settlement.

The existing subscription UPSERT now uses the new start only when the previous period ends strictly before the incoming period starts. Back-to-back/continuous early renewal keeps the existing behavior. Immutable old invoices and grant history are not rewritten; no schema/contract change.

### A UTC device displayed the wrong ICT billing date

The approved plan stores UTC and displays ICT. The native calendar fixture uses a real UTC-timezone Chrome Seller context with a known cross-midnight future paid-through endpoint. API period values are correct but Seller initially displays `28/2/27` rather than the ICT date `1/3/27`; quote-clock text also uses the UTC device hour. Initial screenshot `.local/billing-browser-1792263332919/diagnostic.png` was viewed.

The first new assertion hardcoded a zero-padded locale date; this was corrected to the browser's explicit `Asia/Ho_Chi_Minh` formatter plus a calibration assertion that the expected ICT date differs from the UTC-device date. The corrected native test still fails at the date assertion: `.local/billing-calendar-browser-confirmed-red.log`, `.local/billing-browser-1792263418172/browser.log`. This confirmed reproduction precedes the UI fix.

Only Seller Billing's existing date formatter and quote-expiry time formatter now specify `Asia/Ho_Chi_Minh`. No visual redesign, global formatter rewrite, date storage change or API field change. Test-only datetime-local input normalization removes zero seconds to avoid the previously observed Chrome fill serialization failure.

## Focused proof

- `.local/billing-calendar-trial-http-green.log`: nine cases PASS,zero failures/errors/skips,49.969seconds. Actual issued OWNER/Admin JWTs, real HTTP/Spring/PostGIS, immutable invoice snapshots, real adapter/owned fake-provider HTTP/HMAC and actual settlement. No calendar/entitlement/authorization mock.
- Six explicit month-end cases cover28/29/30/31, non-leap February, leap February29 and a UTC/ICT date-boundary pair. Test-only prepared paid-through timestamps establish the starting term; the application actually creates/settles each renewal. Invoice, paid grant and subscription endpoints match explicit expected dates; old invoice JSONB is identical. The existing UTC calendar anchor is preserved, not changed to an invented ICT billing rule. ICT conversion is checked separately.
- The late renewal case proves new invoice/current subscription start equals the bounded current server instant rather than old expiry, new endpoint is one UTC calendar month, and one new paid grant exists.
- Trial OFF consumes no owner claim/grant; Admin ON/7days grants exactly seven days plus the configured three-day grace to the first eligible new shop, not a second shop. Two concurrent real registrations are observed blocked at PostgreSQL owner-row locks, then both201 with one owner claim/one trial grant. No payOS POST occurs. This is per authenticated OWNER identity, not a claim to prevent every form of multi-account abuse.
- `.local/billing-calendar-panel-regressions.log`: existing three panel cases PASS,13.07seconds. `.local/billing-calendar-web-build.log` succeeds. Final frontend client check/typecheck/lint all exit0; lint zero errors/28 existing warnings. `.local/billing-calendar-web-test.log`:496PASS/12SKIP/zero failures,94.71seconds.
- `.local/billing-calendar-browser-green.log`: one combined fixture PASS,1m13s. `.local/billing-browser-1792263597774/result.json` completed=true, UTC-device ICT date/quote clock verified, actual signed payment and duplicate callback/server polling, three paid15000invoices with one succeeded attempt/grant/audit/outbox each. All three changed Seller screenshots at360/390/1366 viewed; existing capture helper asserts destination/landmark/no document overflow. Admin context remains ICT, external hosted-provider pages are not opened.

## Final gate and provenance

After the focused runs, test-only calendar fixtures select an appropriate future leap/non-leap year so the checks do not expire in2027. The native fixture passes that selected year privately to its driver. The trial case additionally verifies a published TRIAL policy creates no claim/grant while enforcement is OFF, then grants normally after fixture-only re-enable. These extra fixture assertions await the current full run, not retroactively included in the focused nine-case result.

Full Maven verify terminated exit1 with SSRbrowser, pilot fixture and calendar-native billing opt-ins in `.local/billing-calendar-trial-full-verify.log`:1005reported/1001PASS/1FAIL/2ERROR/1SKIP,7m45s. Independent XML totals and exact failed/skipped cases are saved in `.local/billing-calendar-trial-full-verify-summary.json`. This is NOT a green gate. The sole skip is the configured external-JDBC/query-file hot-plan test; a disposable local configuration remains possible, so it is not declared externally blocked.

The new trial fixture failure is scheduling contamination: the same class's concurrent-publication case legitimately leaves a policy scheduled60seconds ahead; the trial helper proposed now+2seconds, violating the existing monotonic publication rule. Only the test helper now schedules after both the current server instant and latest published effective time, with a bounded wait for the actual policy to become effective. Production validation is unchanged. Focused verification includes both trial cases and the publication race, rather than hiding the dependency with an isolated trial-only run.

The two errors occur in unchanged Auth acceptance setup `TRUNCATE ... users CASCADE`, not in a Billing assertion. Actual owned PostgreSQL logs in `.local/billing-calendar-auth-fixture-deadlocks.log` show the conflicting query reading `system_settings`; actual relation lookup identifies22030=`merchant_subscriptions` and21634=`system_settings`. The hourly lifecycle scheduler updates subscriptions before reading settings; its19:05UTC schedule coincides with these failures. This supports fixture/scheduler interference, not a demonstrated business-request deadlock or a repaired production defect. These unchanged classes are included in the focused rerun; no unrelated Auth or scheduler production rewrite is made.

Focused rerun TERMINAL exit0 `.local/billing-calendar-fixture-final.log`:11PASS/zero failures/errors/skips,1m58s. The policy-race plus both trial cases execute together (three Billing cases,75.41seconds), so the future-publication overlap is exercised with the corrected helper. The two unchanged Auth classes execute all eight cases successfully (7+1); this is evidence of an unchanged rerun, not a production-deadlock fix or a replacement1005green aggregate. Original BIL010/013/014 are now locally accepted based on their scoped actual evidence; matrix17/30,not whole goal complete.

The full run's native calendar fixture completed successfully: `.local/billing-browser-1792263961980/result.json`,completed=true/UTC-device ICT=true/pageErrors0/all six captures noOverflow. This proves the future-year/private fixture refinement at all three sizes; production UI is unchanged since the three Seller screenshots already reviewed. Older989-case full results predate the new BE changes and remain historical. The fresh Next build/frontend gate is current for the unchanged final production UI source.

Five changed source files matched their owned-copy SHA256 after the full run in `.local/billing-calendar-post-full-source-check.json`. Gitleaks scanned those inputs with zero findings/exit0 in `.local/billing-calendar-security.log`; no ignore/allowlist change. The subsequent test-helper scheduling correction requires its own updated delta scan. This is not an exhaustive dependency/image/CodeQL security gate. Original384dirty-file hashes remain identical in `.local/billing-calendar-original-preservation.json`. Previously documented security limitations and remaining original requirements remain active.

## Admin registration switch: original BIL024

One additional real-JWT/HTTP/PostgreSQL case uses the actual Admin setting API (not a direct fixture-only switch) to disable new-shop registration. Both an existing OWNER and a new account receive409/MERCHANT_ACTION_NOT_ALLOWED; profile/membership counts, full financial snapshots and nine commerce-table snapshots stay identical through the denials. Existing Seller management/entitlement reads work; Buyer reads the old order and creates a new order, and Seller confirms the old order. Financial snapshots remain unchanged and provider creation count remains one. Actual Admin If-Match re-enable allows registration201, with two setting audit/history entries.

The first new test failed before testing the toggle: backend NON_NULL serialization omits `effectiveSource` when no override exists, but the harness treated a missing node as a version0 override. Only that test's null check changed to `hasNonNull`; no production setting behavior changed. `.local/billing-registration-switch-http-final.log` TERMINAL exit0,onePASS/zero failures/errors/skips,38.332seconds. This is additional scoped evidence, not a new full aggregate. Original matrix18/30PASS; remaining twelve include partial work and are not accepted.

Updated five-file source copies/hashes in `.local/billing-calendar-final-source-manifest.json` include the scheduling correction and registration test. Gitleaks8.30.1 delta scan exit0/no findings,132503bytes, `.local/billing-calendar-final-security.log` and redacted JSON. No allowlist change; all wider security limitations remain.

The sequential full verify with the same SSRbrowser/pilot/calendar-browser opt-ins terminated exit1 in `.local/billing-calendar-registration-full-verify.log`:1006reported/1004PASS/1FAIL/zero errors/1SKIP,8m47s. Independent XML totals are preserved in `.local/billing-calendar-registration-full-verify-summary.json`; Billing HTTP26cases allPASS in `.local/billing-calendar-registration-full-http.xml`. The former trial failure and two Auth setup errors do not recur. The full gate is still NOT green: unchanged `OrderCancellationControllerIntegrationTest.buyerCompletionAndMarkDeliveredOverlapWithoutDuplicateSale()[2]` finishes its dispatch helper with one unpublished outbox row rather than zero. Exact XML is preserved in `.local/billing-calendar-order-race-full.xml`; its root cause is not yet established or claimed repaired. The unchanged repeated method is included in the next focused run, without modifying unrelated production Order code.

Full native `.local/billing-browser-1792264774237/result.json` completed=true/ICT=true/pageErrors0. No new visual review or real-provider claim. Subsequent new Buyer/Social/discovery/Need acceptance test methods were added after this full run compiled; they are not retroactively included in1006 or its source-manifest proof. No production source changed during this full run. The new targeted run must finish before any inferred discovery defect is treated as confirmed.

Next action: Finish the current full backend gate, then continue original Buyer-free/discovery/Social and remaining financial/recovery/security/UI acceptance.
Required from you: Nothing
Completion condition: Full output is terminal with truthful pass/fail/skip counts and each remaining local requirement receives its own appropriate evidence.
