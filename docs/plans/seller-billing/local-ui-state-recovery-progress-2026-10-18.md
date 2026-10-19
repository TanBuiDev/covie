# Billing initial-read recovery — 2026-10-18

Whole goal ACTIVE / NOT_READY. BIL025 remains partial, not section9.2 or whole-plan completion. Normal money/release/reconciliation/enforcement flags OFF. No E checkout/recovered-data/normal runtime modification, commit, branch, remote write, merge, deployment or real provider payment.

## Confirmed minimal repairs

- Admin initial read failures exposed unknown totals as zero without retry; Seller initial503 exposed false enforcement-OFF text and invoice403 lacked safe recovery. Component3FAIL6filteredSKIP6.09s `.local/billing-ui-initial-recovery-red.log`. Initial failure gates and guarded GET-only retry preserve mutations/idempotency/contracts. Affected9PASS11.17s `.local/billing-ui-initial-recovery-green.log`.
- Seller history undefined table class/missing keyboard region:1FAIL6filteredSKIP5.67s `.local/billing-seller-history-readability-red.log`. Reuse shared table/scroll classes, labelled focusable region and column headers; no new CSS. Affected10PASS11.31s `.local/billing-ui-initial-recovery-final.log`.
- Successful partial Admin403 still presented unknown finance as zero/empty.1FAIL3filteredSKIP1.30s `.local/billing-ui-finance-denied-red.log`; preserve pricing/policy and show unavailable finance. Affected11PASS11.20s `.local/billing-ui-states-final.log`.
- Required empty/no-plan/six server-status and independent server-canSell assertions: new8PASS11filteredSKIP6.19s `.local/billing-ui-required-state-matrix.log`. No invented19-pass aggregate across runs.

Typecheck/focusedlint exit0. Production Next build `.local/billing-ui-states-build.log`:compile1.120s/TypeScript4.9s/38routes. BE source unchanged since full1056PASS1HotQuerySKIP9m16s18:53:54; no full-BE replay for UI-only changes.

## Actual native verification

Initial native failed only because new alert selector also matched empty Next route announcer. Actual product alert contained expected text. `.local/billing-browser-1792326113959/browser.log`. Only Python locator scoped to actual paragraph, no product repair/rebuild for harness failure.

Final `.local/billing-ui-states-native-final.log`:1PASS54.50s/Maven57.763s19:23:46ICT; independent `.local/billing-ui-states-native-final-http.xml`. Actual productionNext/Chrome155/issued OWNER+READER+OPERATOR/fresh PG18/API/signed loopback fake provider. `.local/billing-browser-1792326194665/result.json` completedTRUE/pageErrors0.

At360/390/1366, browser transport explicitly injects one503 into Reader overview and Seller invoices, not an actual backend outage. Native Enter retries then read actual API. Seller reload and actual navigate/back preserve pending invoice, disable duplicate checkout and do not show PAID before real reconciliation. Actual server finance403 preserves pricing/hides CSV without false zero/empty claims. Existing lost-successful-reconciliation-response retry retains same key; exactly3providercreates+3signedGET/three shops/one settlement each. Seller polling observes server PAID afterwards. Native Tab/ArrowRight and single-line table measurements retained.

Five new screenshots VIEWED: Reader360 error, Seller390 error, Seller360 recovered pending, Seller1366 paid, finance-denied390. Accessibility snapshots expose alert/named retry button; not physical assistive-device certification. Owned cleanup leaves normal3000/8081 absent.

Full Web `.local/billing-ui-states-web-full.log`:1FAIL518PASS12SKIP92.92s. Unchanged Ads rotation test counted2 rather than1, also seen historically. Isolated unchanged Ads5PASS6.43s `.local/billing-ui-ads-isolated.log`, root cause UNESTABLISHED. Unchanged full confirmation12093 TERMINALexit0:519PASS12SKIP/122filesPASS1fileSKIP82.07s `.local/billing-ui-states-web-confirmation.log`. This is evidence of a passing unchanged rerun, not an Ads production repair or root-cause claim. Subsequent notification-navigation/price-notice source changes need their own final gates.

## Remaining approved scope

Section9.2 explicitly requires QR/reference-copy/countdowns and onboarding stepper/terms, not merely current hosted link/policy banner. Existing MerchantForm already supplies preview/validation; do not duplicate. Billing notification payload `billingUrl` is not accepted by browser `deepLink/href/path/route`: source incompatibility, not yet tested/fixed. Price-publication outbox lacks owner notice consumer; actual dispatch RED required before minimal A11 correction, no invented founder prices/dates. BIL028/security/performance/separateJVMrestart/pilot remain.

Next action: Collect full Web confirmation, then prove and fix Billing notification navigation and remaining approved local requirements.
Required from you: Nothing
Completion condition: Actual acceptance for the whole original local plan, not partial UI or a green-suite-only completion claim.
