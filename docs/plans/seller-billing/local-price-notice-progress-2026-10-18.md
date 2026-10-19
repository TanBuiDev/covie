# Billing price-publication notice progress — 2026-10-18

Whole goal ACTIVE / NOT_READY, original28/30 accepted,025/028 and broader section9.2/security/operations/separate-JVM/pilot work remain. No real payOS/bank/refund, feature activation, production deployment, branch/commit/remote write/merge, original E checkout or recovered-data modification.

## Actual missing behavior and focused correction

The existing BILLING_PRICE_PUBLISHED outbox event had no notice consumer. Approved A11 requires advance notice and no retroactive paid-period change. Reviewed plan `docs/superpowers/plans/2026-10-18-billing-price-notice.md` adds no founder discounts, eligibility cutoff or automatic charge.

Actual issued Admin HTTP publishes an immutable future version. Real outbox processor published the event but produced zero notices for101eligible owners: meaningful1FAIL `.local/billing-price-notice-red.log` (Maven39.300s19:30:59ICT), not fixture/auth failure. Fixture includes an owner with two subscribed shops,100other owners across the100-owner database-page boundary, STAFF/revoked-owner/unrelated owner exclusions.

Minimal handler branch reads facts from immutable version aggregate ID, not event payload. DISTINCT active OWNER memberships joined to matching plan subscriptions are paged by UUID100 at a time without truncation; existing NotificationService source/event/user/type dedup and transaction are reused. One owner/multiple shops gets one public notice linking `/seller`. Paging bounds each database result, not total transaction/memory. No schema migration, grant/payment/invoice/price calculation changes.

Focused actual HTTP/outbox/PG101owner exact-set/inbox authorization/dedup/financial JSONB/provider0 regression1PASS23.46s/Maven40.010s `.local/billing-price-notice-green.log`. Replay deliberately supplies incorrect event planCode and still uses immutable DB facts; no duplicate notification/SSE. Three excluded actors' actual authenticated inboxes empty. Financial snapshots identical.

## Browser payload incompatibility and public facts

Historical Billing notifications carry `billingUrl`, but browser recognized only deepLink/href/path/route. Actual helper+inbox-click regression2FAIL11PASS7.02s `.local/billing-notification-navigation-confirmed-red.log`. Single existing helper-key fallback addition preserves priority and existing internal-path validation. Affected17PASS6.47s `.local/billing-notification-navigation-green.log`; external/protocol-relative/backslash destinations remain rejected.

New price notice UI2FAIL10filteredSKIP7.94s `.local/billing-price-notice-ui-red.log`; fixed label and validated public plan/revision/amount/cycle/effective-time summary, no raw provider/server title/body fallback. Actual affected19PASS7.02s `.local/billing-price-notice-ui-green.log`. Targetlint/typecheck exit0.

Initial representation adds BILLING_PRICE_CHANGED enum. Official OpenAPI export2PASS29.81s/Maven41.882s `.local/billing-price-notice-contract.log`;173paths231schemas, semantic diff solely NotificationResponse.type additive enum `.local/billing-price-notice-contract-diff.json`. Official client generate/check/typecheck exit0. Build compile1.465s/TS13.3s/38routes `.local/billing-price-notice-build.log`.

Native18228 TERMINALexit0:1PASS53.08s/Maven56.475s21:21:54ICT `.local/billing-price-notice-native.log`, independent `.local/billing-price-notice-native-http.xml`. Actual current productionNext/Chrome155/API/PG durable owner inbox360/390/1366, public facts in Vietnamese time, native Enter→real `/seller` switcher, back retains same notice, pageErrors0, no overflow, financial JSONB unchanged/providercreates0. `.local/billing-browser-1792333296776/result.json` completedTRUE; three full screenshots VIEWED. Actual accessibility snapshots retained, no physical assistive-device certification.

Current full Web86588 TERMINALexit0:523PASS12SKIP/122filesPASS1fileSKIP92.88s `.local/billing-price-notice-web-full.log`. Earlier unchanged UI-state confirmation519PASS12SKIP82.07s retained; initial518PASS1AdsFAIL12SKIP and isolated5PASS remain historical truthful flakiness evidence, not an Ads root-cause repair.

Sixteen exact-source snapshot files776484bytes, Gitleaks8.30.1 exit0/no findings `.local/billing-price-notice-gitleaks.log` plus manifest. Not full dependency/CodeQL/image/security release acceptance.

## Current pending compatibility correction and gate

Reviewer found no current handler/UI defect, but the new persisted Java enum prevents an old API reader from deserializing those rows. The original user explicitly requires compatibility; a new enum is not required by A11. Adopt existing BILLING_RENEWAL_REMINDER plus public payload `noticeKind=PRICE_CHANGED`, with same distinct publication event/immutable facts/recipient scope/dedup/UI. This preserves full advance-notice scope and ordinary reminders, without rewriting history or creating an avoidable old-reader floor. Plan updated accordingly. Production discriminator correction NOT YET applied: do not describe current enum source as compatible.

Full BE18508 RUNNING `.local/billing-price-notice-full-verify.log` (actual Java25 offline verify with SSR/pilot/native opt-ins). Poll SAME handle; do not edit compiled source mid-run or overlap Maven. After terminal, save independent XML before any Maven. Then meaningful compatibility/discriminator regression and minimal representation adjustment, regenerate contract/client back to pre-notice enum, focused/current-source final gates. This already-running enum-based full cannot prove the future adjusted source. BIL028 not yet accepted solely from focused/native success.

21:26ICT actual full-run environment issue: invocation omitted the existing `covie.test.minio-image` system property used by previous successful full gates. Testcontainers attempts the default quay.io image and receives401UNAUTHORIZED; the known cached digest is `cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1`, confirmed by actual `docker image ls` and previous full logs. This is invocation/environment failure, not a new storage product defect or permission to retag/substitute production images. Do not claim full green. Let the specific live18508 terminate and preserve results. The next necessary full after discriminator correction must include `-Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1` alongside SSR/pilot/native opt-ins. No overlapping Maven or restart merely because a poll is slow.

21:28ICT partial compatibility progress while Java gate remains live: only Web source/tests adjusted, not Java compilation or the already-built artifact used by current Maven. Existing notification inbox price fixtures changed to BILLING_RENEWAL_REMINDER + noticeKind=PRICE_CHANGED: actual2FAIL10filteredSKIP7.71s `.local/billing-price-discriminator-ui-red.log`. Minimal label/summary selector now requires both existing type and discriminator; ordinary reminder continues generic label/navigation with no price summary. Affected19PASS6.40s `.local/billing-price-discriminator-ui-green.log`. BE still emits initial new enum and has not yet been changed; current source pair is intentionally transitional, NOT an integration pass. Do not restart normal runtime or build/test current combined pair as accepted. After18508 terminal, update actual Java regression expected type + discriminator/8publickeys first, prove RED against current BE, then change handler to existing enum + noticeKind and remove only newly introduced enum value. Canonical/client must regenerate to exact pre-notice contract; current16file scan/build/native/fullWeb are historical for subsequent Web changes and need final refresh after combined source is coherent.

Next action: Collect current full18508 terminal evidence, then remove unnecessary persisted enum incompatibility using the reviewed discriminator representation.
Required from you: Nothing
Completion condition: Same complete advance-price notice works with existing reader enum/contract, followed by whole original local acceptance rather than stopping at this feature.

## Compatible representation and final backend evidence

The obsolete/misconfigured18508 was explicitly terminated at21:34:50ICT after confirming exact owned Maven18476/Surefire3024 command identities and repeated registry401. This was a known invalid invocation, not a timeout-based restart. Wrapper exit0 after termination is NOT success. `.local/billing-price-notice-initial-aborted-status.json` and copied available XML preserve this fact; independent Billing HTTP73PASS140.822s is scoped evidence only, not a full aggregate. Ryuk cleanup left no running containers/normal3000/8081.

Actual compatibility regression expected the existing enum and failed with the new enum:1FAIL22.74s/Maven34.799s `.local/billing-price-discriminator-http-red.log`. Minimal handler now emits existing BILLING_RENEWAL_REMINDER with public `noticeKind=PRICE_CHANGED`; only the newly introduced enum was removed. Existing distinct price-publication event and all recipient/facts/dedup behavior remain. No stored history rewritten. UI requires both existing type and discriminator, preserving ordinary reminders.

Actual HTTP101owner+official OpenAPI3PASS (23.38s+8.417s)/Maven47.925s21:37:29 `.local/billing-price-discriminator-http-contract-green.log`. Exact SHA256 of regenerated canonical OpenAPI equals pre-notice snapshot; official client regeneration/check succeeds, no notice-related REST/enum/schema drift. This proves the representation does not introduce an enum old readers lack; not a whole historical binary/release rollback rehearsal.

Native78077 TERMINALexit0:1PASS28.911s/Maven31.873s21:39:23 `.local/billing-price-discriminator-native.log`; independent XML retained. `.local/billing-browser-1792334356607/result.json` completedTRUE/errors0/3603901366 facts+keyboard/switcher/back. All three current screenshots VIEWED. Same full notice, finance unchanged/provider0, now compatible enum/discriminator. Initial enum-based native/screens remain historical.

Correctly configured full77436 TERMINALexit0:1058reported1057PASS1HotQueryopt-inSKIP,0failures/errors,9m24s21:49:53 `.local/billing-price-discriminator-full-verify.log`. Independent277suite XML snapshot and matching `.local/billing-price-discriminator-full-summary.json` retained BEFORE later native Maven. SSR/pilot/native opt-ins run with exact existing cached MinIO digest. No -DskipTests/retag/policy disabling. BE production source unchanged by subsequent QR/reference/countdown frontend work; no new full-BE replay solely for that UI.

One additional actual BIL025 manual-refresh failure was confirmed:1FAIL14filteredSKIP plus1unhandled rejection `.local/billing-manual-status-refresh-red.log`. Existing refresh discarded `load()` rejection without displaying error. Minimal existing button catches/display error and clears it on explicit retry, preserving invoice/pending guard/no checkout. Affected Seller15PASS11.28s `.local/billing-manual-status-refresh-green.log`; this is frontend-only, not a BE defect.

Whole goal remains ACTIVE; BIL028 final acceptance mapping still needs update against original renewal/calendar/legacy evidence. QR presentation plan is being executed separately; current final Web/build/security snapshot for all frontend changes is not inferred from old523PASS or initial16file scan.

Next action: Collect actual current QR/reference/countdown native and Web gates, then update original whole-plan traceability and continue remaining local requirements.
Required from you: Nothing
Completion condition: Original requirements each have matching evidence, with real-provider/activation boundaries explicitly retained.
