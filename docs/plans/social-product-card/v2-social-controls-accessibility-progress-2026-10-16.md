# Social V2 — measured control targets and reduced motion

Date:2026-10-16. Scoped11.4 actual-browser measurements; not whole-page accessibility/staging acceptance. Current project UI retained.

## Measurement and actual defect

Added `apps/web/e2e/social-controls-accessibility-browser.mjs`, reusing existing disposable Next/API/PostgreSQL/MinIO feed fixture with guest flag. No new Java scenario, business mutation, gesture/mode/filter/Admin/activity workflow or broad suite. Actual guest no AT/RT/Authorization/session; seeded coordinates are not GPS. Browser reduced-motion preference is explicitly emulated, not an OS/device settings claim.

At each360/768/1280/1440 CSS viewport, sample ten controls: caption expand, two gallery buttons, four Social interaction buttons, primary detail CTA and two view toggles. Record rendered bounds, aria-label/text names, document/client/scroll widths, reduced-motion media match and computed card/toggle transition durations. The inspected set is not every inline title/merchant link or every control in the entire application; no full touch/WCAG acceptance claimed.

Authoritative RED: `.local/social-v2-controls-a11y-bounds.log` with all four-width evidence/captures in same-basename directory. **Only “Xem & Đặt” violates the44px minimum:279×38 at360,322×38 at768,381×38 at1280/1440.** Other sampled buttons/toggles meet minimums. Reduced media matches and computed card/toggle durations0s; no horizontal overflow, browser API writes0/pageErrors0. This is a measured existing CTA defect, not a predicted CSS risk. Source has display/padding/text but no minimum height.

Minimal production fix: one `min-height:44px` declaration on the existing shared `.cta` class in `marketplace-view.module.css`. No display/padding/type/font changes, generic button rewrite, API/React/business/state change or unrelated global stylesheet fix. Shared Catalog/other-kind primary CTA uses the same class; no business action/navigation contract altered.

## Accepted GREEN

Final **1PASS38.36s**, Maven**40.968s**,0skip: `.local/social-v2-controls-a11y-green.log`. Forty sampled control observations pass44px dimensions/nonempty reported aria/text names. CTA heights exactly44 at all widths, same respective widths279/322/381/381. Reduced-motion media=true and card/toggle transition durations0s at all four widths. Native component screenshots captured at each width;360 card capture inspected contextually. Not full-page/four-width visual signoff, real screen-reader session, physical mobile/Safari proof or all inline link-target acceptance.

Existing Java fixture independently passes full11 Catalog/social/moderation/audit/outbox JSONB readonly snapshots, reactions0 and guest actor sessions0. Manifest deleted; owned API54554/Next54557 closed; normal3000/8081 absent/untouched. Relevant CSS, card/shared-content module and Java fixture hashes match validation copies. Helper ESLint/node syntax/focused whitespace checks PASS. No unchanged TypeScript, full suites, old gestures/keyboard/gallery fallback/mutations/Admin/activity tests or production build repeated for a CSS-only declaration.

Earlier harness-only attempts are not product RED:
- `.local/social-v2-controls-a11y-red.log`: cleanup of an earlier assertion failure caused a pending route to throw context-disposed and prevent evidence writing. Helper now marks teardown before browser close and ignores only expected closing-transport errors; active errors still fail.
- `...-measured.log`: helper incorrectly required scrollWidth==requested viewport. Actual360 viewport/client with345 scroll width is not overflow; assertion was inappropriate. Correct check requires innerWidth=requested and scrollWidth≤clientWidth, retaining actual dimensions. No application scrollbar/global layout workaround.
- Actual defect evidence is the subsequent complete `...-bounds` run above, not these harness failures.

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-guest=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-controls-accessibility-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-controls-a11y-green test
```

No BE/API/schema/client/migration/normal-config change, recovered/runtime DB access, normal restart, remote/commit/branch/push, activation or deploy. Whole objective ACTIVE; normal V2/video flags OFF. Previous production image predates Search labels and this CSS declaration; fresh exact stable-source build remains required, not a reason to rebuild after each unused helper.

Next action: Trace actual CatalogImage completion/magic-verification/storage fixtures before a focused adversarial real-object check.
Required from you: Nothing
Completion condition: Establish a safe disposable proof for the named MIME-spoof boundary without repeating valid upload/gallery flows or changing a working verifier without failure evidence.
