# Social V2 — native offline failure / retry / online recovery

Date:2026-10-16. Scoped11.4 network evidence. Existing UI/provider correct; no production correction.

## Why bridge calibration matters

New `apps/web/e2e/social-offline-recovery-browser.mjs` reuses the existing disposable Next/API/PostgreSQL/MinIO guest fixture. First perform an actual explicit preview GET while browser network emulation is offline but normal unmodified route.fetch bridge is still active. Observed navigator.onLine=false **and actual preview200 including target**. This directly demonstrates that browser offline state alone would be false proof when the test bridge supplies a successful response externally.

For the actual offline data-boundary stage, helper does not fetch/fulfill API routes at all: it calls route.continue and lets Chrome's offline transport produce the error. Success after restoration again uses actual unmodified Next/API responses. No fake success body, fabricated429/500 or manually dispatched online/focus event. Calibration's optional abort fallback was not used in the accepted run.

The app document/assets/RSC/HMR bridge remains available to operate the already hydrated isolated page. This is an actual Chrome-emulated **API transport outage/recovery** on that page, not whole-site offline startup, WAN/device loss, physical OS networking or staging availability proof.

## Actual accepted chain

Chrome155.0.8059.84/1280, no AT/RT/Authorization/issued actor session, seeded coordinate preference (not GPS):

1. Actual Search Catalog loads its eligible items. Calibration above observes actual preview200 while browserOffline.
2. Switch API routing to native offline continuation, then native Social toggle. Chrome reports `net::ERR_INTERNET_DISCONNECTED` for preview and summary. Both cards show existing preview failure/retry UI, no Social gallery.
3. Native retry button while still offline causes another actual preview request with the same Chrome-native disconnected error. Existing failure/retry view remains usable; no mutation or unhandled exception.
4. Restore routing and browser network. Chrome emits trusted native online event; existing provider online handler automatically revalidates. Actual preview200/no-store restores exact stored literal caption and real16px image from MinIO; summary reload enables normal Like control. Retry controls disappear.
5. Observer records trusted offline and online events; none is dispatched by test code. Actual failures total3: preview, summary, retried preview. Successful preview reads2: calibration and recovery. API writes0, comment reads0, pageErrors=[] after context close.

This proves native retry under outage and automatic online recovery. It does not claim a successful manual retry after online restoration—the product already auto-revalidates then, so forcing it to wait would invent a requirement. Java independently passes all11 business full-JSONB readonly snapshots and guest actor sessions0/reactions0. No old gallery gestures, mutations, Admin/account flows or component suites replayed.

## Verification

First **1PASS40.86s**, Maven**43.764s**,0skip: `.local/social-v2-offline-recovery-browser.log`, same-basename evidence directory. Targeted helper ESLint/node syntax/focused whitespace checks PASS. Provider/card/adapter and Java fixture hashes match validation copies. Manifest deleted; owned API64251/Next64254 stopped; normal3000/8081 absent/untouched.1280 recovery screenshot captured, not full visual/a11y acceptance.

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-guest=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-offline-recovery-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-offline-recovery-browser test
```

No production BE/FE/API/schema/client/CSS/migration/normal-config change, full suites, unchanged successful workflows/typecheck/build rerun, recovered/runtime DB access, normal restart, remote/commit/branch/push, activation or deploy. Whole objective ACTIVE; normal V2/video flags OFF. Prior production build predates Search-label/CTA corrections; fresh stable-source build still required. Staging/load/private object/CDN/recovery/release and G5 entry conditions remain unproved.

Next action: Prepare the fresh whitelisted exact-source production web build/runtime verification after the two current UI corrections.
Required from you: Nothing
Completion condition: Current stable sources match isolated build input/output checks, without touching original .next, secrets, normal services or claiming a release deployment.
