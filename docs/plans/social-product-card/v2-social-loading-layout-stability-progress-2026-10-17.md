# Social V2 — confirmed loading-layout shift and minimal correction

Date:2026-10-17. Whole G0–G6 objective ACTIVE. Previous goal turn measured real local load/browser metrics; this checkpoint resolves its devCLS risk hypothesis using a separate production-mode artifact and an evidence-led loading-only change.

## Production-mode reproduction and attribution

Fresh598-source whitelist builds a **verification-only feature-ON** web image using ignored `.local/social-v2-web-feature-on.Dockerfile`. Compared with the real Dockerfile after stripping comments, the only behavioral overlay is build-stage `NEXT_PUBLIC_COVIE_SOCIAL_V2_PREVIEWS_ENABLED=true` and video flagtrue. Original Dockerfile, normal flags and defaultOFF artifact are not changed or overwritten. OverlaySHA256 `8D75FD6F9002E676AE33182843AF0ADE4D94CA94DE69A80CBB4FB3C604548354`.

Baseline image `covie-web:social-v2-feature-on-verification-20261017`, ID `sha256:5357320b4208b9bdbf881b82ab9c2c42845f3e3b6465ecfebcaec1b0aa3a246c`. Actual current API image, freshPG/MinIO/generated keys,40public products/3images each/800comments/80reactions; actual objects, production-mode web container, Chrome155.0.8059.84 at1280×1000. Not staging/production deployment. No artificial preview delay or invented response.

Initial fixture failed to find cards because its dev-only transport sent `/api/v1` to Next, while production rewrites intentionally do not proxy API. Repository `infra/docker/nginx.prod.conf` routes ordinary API to Spring separately. Correct fixture transport, not application/server routing. Actual routed baseline `.local/social-v2-production-browser-routed.log` exit0, fixture `.local/social-v2-video-chain-d0d3647def65/browser-metrics.json`:

- Production-mode CLS0.4269431958 reproduces the dev observation; initial LCP384ms, not dev3.8seconds.
- At270.1ms:20loading articles,0captions;345.6ms:20captions ready.
- At371.8ms, a single shift contributes0.40280625. Its sources are loading card rows previously186px high atY406/582/758, moved out of the viewport when full content replaces them. Unrelated header/category shifts contribute approximately0.02414.
- Current source renders loading as only a small status paragraph instead of reserving the ready card's media/text footprint. This confirms an actual loading-geometry defect, not a slow BE or a speculative optimization.

Primary measurement reference: [Google web.dev CLS documentation](https://web.dev/articles/cls) describes a0.1 good-score indicator and the largest session window (≤5seconds, gaps<1second). This is a **local regression indicator**, not a newly approved production SLA or field75th percentile result. All baseline shifts are within one short session, so correcting the helper from lifespan sum to session maximum does not change the recorded baseline value.

## Minimal production change

Only the `usePreview && previewsLoading` branch of `MarketplaceProductCard` and two CSS rules change:

- Keep its original loading status/aria-busy and apply the existing Social modifier.
- Reserve a neutral4:3 media block using the existing media class, plus estimated text/header/footer space (180px/240px). No image/video element, URL, item name, merchant details or interactive link is exposed before authoritative preview readiness.
- New reserved blocks are aria-hidden and have no animation/focus target. Ready, error/retry, Catalog, API calls, provider invalidation/privacy, caption limits, gallery, commerce and moderation logic are unchanged.

Files `apps/web/features/marketplace-view/marketplace-product-card.tsx`, `marketplace-view.module.css`; one focused case added to existing `tests/marketplace-view/social-card-content.test.tsx`. No refactor, caching change, suppressed observer, hidden ready content or key-reset metric workaround.

## Focused tests, with failures distinguished

An initial private-copy command used the wrong working directory and ran no matching new test; **not RED evidence**. Correct absolute copy yields actual1FAIL/9skipped in `.local/social-v2-loading-layout-unit-red-verified.log`: loading card lacks Social class/anonymous reserved structure.

After the minimal fix, affected card/content suites17PASS, `.local/social-v2-loading-layout-unit-green.log` (4.34s). Typecheck and first feature-ON rebuild then fail only because the new test's `toHaveClass` receives CSS-module type `string|undefined`. Production compilation itself succeeds; this is a new test typing error, not another app-layout defect. Change only that assertion to exact class-token membership. Rerun **only new case**1PASS/9skipped (3.77s), `.local/social-v2-loading-layout-unit-final.log`; final targeted TypeScript/lint both exit0, `...-typecheck-final.log`, `...-lint-final.log`. No repeat of the other16unchanged passed cases/full suite.

`.local/social-v2-loading-layout-production-red.log` asserts the **already recorded actual production baseline** against0.1 and exits1; it is not falsely described as a second runtime execution. Preserve the original baseline JSON/image identity as runtime evidence.

## Actual corrected production-mode four-width result

New distinct verification image `covie-web:social-v2-feature-on-verification-20261017-layout-green-typed`, ID `sha256:e0457f3bfbad4e0240334942dde761955f39ab218271cc716f81dbdd260c8ece`, user10001:10001. Build logs/manifests `.local/social-v2-feature-on-web-layout-green-typed-*`; failed earlier build evidence retained under separate `...-layout-green-*`.

`.local/social-v2-production-loading-layout-green.log` exit0, fixture `.local/social-v2-video-chain-8647f37aebf3`, result completedTRUE. Source change justifies rerunning **only the affected production loading/layout20→40-card scenario** at required360/768/1280/1440 widths; API load, video/commerce/auth/mutations are not replayed.

| CSS width | Initial/final session CLS | Initial-navigation LCP ms |
| --- | --- | --- |
|360 |0.01703076 |400 |
|768 |0.01659067 |364 |
|1280 |0.02474587 |356 |
|1440 |0.01964522 |352 |

At1280, captions still arrive asynchronously (loading255.5ms→ready319.7ms), but the corresponding shift is now0.00060892 rather than0.40280625. The unrelated header/category shifts remain; no unrelated UI change attempted. Measurements do not depend on accelerating the API or hiding content.

Each run renders20cards then native LoadMore appends40, actual PNG/API responses, no horizontal overflow, pageErrors0 and writes0. Six protected commerce tables remain full-JSONB identical. Four final screenshots have been opened and visually reviewed. These are responsive Chrome desktop contexts, not physical mobile/iOS, a full content/error cross-product, field Core Web Vitals or a WAN benchmark. PNGs are controlled fixtures, not a production image-size workload.

The guest benchmark observes expected unauthenticated cart/refresh401s and an existing `/privacy` prefetch404; no claim that every unrelated route returns200. It does not test authenticated SSR-refresh behavior. Subsequent helper routing explicitly preserves nginx's web-owned SSR-refresh exception; no layout-result claim is made about that unrelated authentication path.

## Fresh normal-OFF production artifact

Default source Dockerfile, no feature-ON overlay: `.local/social-v2-final-web-layout-final-run.log`/`...-build.log` exit0.598source/context files matchSHA256 before/after build; independent current-source and context path enumeration match, zero delta. Actual Next compile5.0s, TypeScript11.4s,37/37pages. New defaultOFF image `covie-web:social-v2-video-final-local-20261017-layout-final`, ID `sha256:97934471d446276d6c886641b724815ac86ce262d0749aa53c18c982c03f8ce0`,406055303bytes, user10001:10001.

Actual existing verifier `.local/social-v2-final-web-layout-final-runtime.log` exit0: known forbidden-path/non-root checks, homepage and four public assets200. Not exhaustive secret/CVE, auth-browser or release acceptance. Current artifact provenance `.local/social-v2-loading-layout-final-provenance.json`: both current ON and OFF snapshots598/598, zero path/hash mismatch. Current original/private three changed files match `.local/social-v2-loading-layout-source-check.json`. Older58371... defaultOFF web image is now **historical**, not current; API1140-source image unchanged.

## Preservation and remaining scope

No BE/API/OpenAPI/client/migration/dependency change or unchanged API load/old full functional suite rerun. No normal3000/8081 restart, original `.next`, existing/recovered data, remoteGit/commit/branch/merge, registry push, feature activation or deploy. Owned verification containers/networks/generated credentials removed; normal flagsOFF. Build-time ON flags exist only in clearly named local verification images.

Next action: Reconcile the full G0–G6 requirement evidence against current sources, including safe local rollback/compatibility limits and remaining named gates, without reopening passed unchanged scope.
Required from you: Nothing
Completion condition: A complete requirement-by-requirement local acceptance matrix is supported by authoritative evidence; resolve any genuinely missing implementation/verification before marking the whole goal complete.
