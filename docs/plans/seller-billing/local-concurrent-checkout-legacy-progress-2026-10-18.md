# Seller Billing concurrent checkout and legacy replay — 2026-10-18

Whole original Seller Billing/payOS local goal remains ACTIVE / NOT_READY. Original matrix25/30 currently accepted; BIL008 awaits the final owner-approved fail-closed gate. Remaining025/028/029/030 plus security/operations/pilot are not dropped. No Social work, real provider payment, normal feature activation, existing/recovered database writes, branch, commit, remote write or deployment.

## Baseline and actual reproduction

Fresh08:38 origin fetch: detached HEAD and feature both41abfce04276950db2b680d44cd1b6daad0106e3;mainbb2e06b41282c8e9205c35a13e7b6adec68ceaa2. No reset/merge; primary dirty checkout preserved.

Initial targeted HTTP run3cases had1PASS/2FAIL:actual weak historical hash accepted changed amount200 instead409; one new test incorrectly required exact nanosecond equality after PostgreSQL microsecond rounding. Only new test comparison changed to exact all other checkout fields and expiry absolute difference<1000ns, matching existing canonical replay precision. This was not an application timestamp fix.

Confirmed RED `.local/billing-concurrent-historical-checkout-confirmed-red.log`:5run/2PASS/3FAIL0errors34.554s. Both actual concurrent cases already pass. Three historical cases independently demonstrate changed amount/revision/quote returning200 instead409. Issued OWNER JWT, real HTTP/security/PG/latestFlyway34, real gateway protocol relayed only to owned loopback provider; no external payOS call.

Both HTTP requests are observed simultaneously waiting on the same merchant FOR UPDATE lock before release. Same key/body produces one invoice/attempt/provider POST, valid replay or safe intermediate409, then successful exact checkout replay. Different quote body with same key gives one winner and409loser, consumes one quote and rejects subsequent loser replay. Actual signed settlement creates one grant. No speculative concurrency refactor was necessary.

## Superseded partial fix

An intermediate minimal legacy check compared persisted amount/revision and surviving quote ownership/consumption. Focused11PASS1m23s `.local/billing-concurrent-historical-checkout-green.log`:7HTTPcases/4PGreplaycases. Full58616 subsequently terminal exit0 at08:44:55ICT:1033reported/1032PASS/1HotQuerySKIP/0failures/errors7m27s, independent `.local/billing-concurrent-historical-full-verify-summary.json` saved before new Maven. SSR/pilot/native manual-reconciliation opt-ins ran. This full is historical for the later owner decision, not the final fail-closed source gate.

## Owner-approved final behavior

Owner explicitly chose safe409 when historical request body cannot be fully verified, without a new invoice/payment link/entitlement. Status reads must let Seller continue safely; current fully verified request replay remains successful. See `docs/architecture/adr-seller-billing-legacy-replay.md`. This supersedes WIP weak-hash compatibility, not an arbitrary policy change.

New policy RED `.local/billing-legacy-fail-closed-red.log`:4FAIL0errors1m12s at17:41:53ICT. Three actual HTTP cases return200 for unverifiable same legacy body; canonical historical PG test expected conflict but no exception. No false declaration that previous intermediate green proves the new decision.

Final implementation removes the intermediate comparison/join/extra quote query: a legacy plan/voucher hash cannot verify the full original request even when a quote survives. It now returns existing BILLING_PAYMENT_UNAVAILABLE409 with owner-scoped invoiceId/statusPath in existing generic error details. No mutation/backfill, new API path, contract schema, migration, provider call or pricing change. Current versioned full-body fingerprint replay and changed-body guard remain unchanged.

Focused final API11535 TERMINALexit0 at17:43:20ICT:11PASS0skip0fail/errors1m06s `.local/billing-legacy-fail-closed-green.log`. Independent `.local/billing-legacy-fail-closed-focused-summary.json` preserves7HTTPcases20.230s and4PGreplaycases5.561s. Old requests before/after quote cleanup409 leave exact financial snapshots/no grant/no new provider POST; actual authorized no-store GET recovers original invoice/amount/status/link. Current consumed/expired quote replay and concurrent changed-body cases pass. No aggregate with earlier superseded11 is claimed.

Seller component targeted4PASS13.20s `.local/billing-legacy-fail-closed-seller-ui.log`:existing UI already reloads invoices and polls original payment after503 or409, recovers original checkout link, disables new checkout and makes no second POST. No frontend production change was needed. This component proof is not a new native browser acceptance claim.

Final38-source copied SHA manifest `.local/billing-legacy-fail-closed-source-manifest.json` and independent source-check show38checked/0delta. Gitleaks8.30.1 actualexit0/no findings946762bytes `.local/billing-legacy-fail-closed-security.log`; no ignore changes or wholeCVE/image/CodeQL claim. Webtypecheck/targeted test-file lint exit0. No frontend production source changed, so unchanged broaderWeb/native evidence is retained within its proven scope.

Final fail-closed full35583 TERMINALexit0 at17:52:31ICT:1033reported/1032PASS/1HotQuerySKIP/0failures/errors8m03s `.local/billing-legacy-fail-closed-full-verify.log`; independent `.local/billing-legacy-fail-closed-full-verify-summary.json` and HTTP XML preserved before another Maven. SSRbrowser/pilot/native-manual opt-ins ran. BIL008 accepted, original matrix26/30; no whole-goal or production-readiness claim.

Production source was unchanged during this full. Only a future CSV HTTP test was added after compilation; that new test is not covered by the full and explains the test-file-only delta from38manifest. CSV endpoint was subsequently actually RED404 `.local/billing-invoice-csv-http-red.log`1FAIL0errors32.517s at17:53:26ICT. Its separate plan was reviewed Approved; server/controller/renderer implementation and focused test work started only after full35583 ended. This later CSV production source is not retroactively accepted by full35583. BIL029 remains incomplete until full export/UI/privacy requirements are executed.

Next action: Implement and verify missing authorized invoice export/privacy and remaining original requirements.
Required from you: Nothing
Completion condition: BIL008 has current final-source evidence; all other original requirements retain individual acceptance gates.
