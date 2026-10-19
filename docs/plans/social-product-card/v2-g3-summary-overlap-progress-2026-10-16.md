# Social V2 — pending Like versus summary revalidation

## Concrete defect

Plan G3 requires server-confirmed reactions, synchronized duplicate cards and no stale overwrite when returning to a tab. The existing adapter serializes same-item mutations, but not an overlapping summary GET.

Targeted React regression: two independent providers have the same item; hold a Like response, dispatch the existing focus reload, hold both older summary reads, deliver confirmed liked=true/likes5, then deliver older liked=false/likes4 reads. Before the fix both cards revert to Thích. `.local/social-v2-g3-summary-overlap-red.log`: 1 failure/5 skipped. This is mocked transport/component evidence, not a BE mutation failure.

Root cause: focus changes request stamp. An old card callback can update its old-stamp result while the other current-stamp listener rejects that event; finally the pending new-stamp GET replaces the whole map with its older snapshot.

## Minimal provider-only fix

During each summary read, collect the existing validated SOCIAL_SYNC confirmations for IDs in that request. When the response arrives, merge those confirmations over entries actually present in that returned map. Remove the request-local listener on settle or effect cleanup. Existing live/stamp guards, mutation serialization, 40-ID chunks, failure/retry behavior and preview pipeline remain unchanged.

The confirmation buffer is request-local, not a permanent override/cache, account store, optimistic mutation or new API/version. No added HTTP requests, polling, TTL, auth logic, BE code, schema/client or CSS change. The common enabled Social summary path is repaired, including legacy Social; this is not V2 feature activation. Disabled Catalog behavior stays unchanged.

An extra regression exposed an unsafe initial merge: it could add a confirmation even if the authoritative batch omitted that item. `.local/social-v2-g3-summary-omission-red.log`: omission fails, original overlap passes. The final merge overrides only returned keys; omission remains omission. This refinement preserves the preexisting fail-closed missing-item behavior rather than inventing client visibility state.

## Component verification

```text
pnpm --filter @covie/web exec vitest run tests/marketplace-view/marketplace-social.test.tsx tests/marketplace-view/social-previews.test.tsx tests/marketplace-view/social-card-content.test.tsx
```

Final `.local/social-v2-g3-summary-overlap-final.log`: **21 PASS**,4.72s. Covers the duplicate-provider overlap (one mutation), omitted summary remains unavailable/disabled, and a later fresh focus read can change count5→6 rather than being blocked by a sticky old confirmation. Existing provider/card/preview/Catalog/error/retry checks also pass.

Final web typecheck and targeted provider/test/helper lint exit0 (`-typecheck.log`, `-lint.log`, `-browser-lint.log`). Diff check passes. Intermediate20-pass run preceded the added omission guard; it is not substituted for the final run.

## Actual Next/API proof

New `social-summary-overlap-browser.mjs` reuses the isolated real feed fixture through a separate opt-in `covie.test.social-feed-reaction-overlap=true` assertion branch. Old readonly/HIDE fixture behavior remains the default and its scenarios are not repeated.

Actual issued buyer cookie, Next/API/disposable PG/MinIO, native Like button:

1. Hold delivery of the real PUT request before forwarding it to the API.
2. Explicit synthetic focus triggers the existing revalidation path. Fetch the actual summary response (liked=false/likes0) and hold only its delivery; do not mock/replace body/status.
3. Release PUT; actual API returns200/liked=true/likes1. Observe the application's own confirmation event to prove client processing occurred **before** releasing the old read.
4. Release old summary. Final enabled button is Bỏ thích, aria-pressed=true, count1.

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-reaction-overlap=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-summary-overlap-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-g3-summary-overlap-browser test
```

`.local/social-v2-g3-summary-overlap-browser.log`: **1 PASS**,test50.42s/Maven59.592s. Evidence JSON records old0, confirmed1, confirmation-before-release and finalLiked=true, pageErrors0; Chrome155.0.8059.84. Java independently verifies exactly one LIKE for the actual actor/item and no other reaction row; full five catalog/media table JSONB snapshots unchanged. This is an intentional reaction write, not a whole-readonly scenario.

Source/private provider hashes match; token manifest deleted and owned children closed by finally. No normal service restart, recovered/runtime DB access, full BE/web suite, unchanged gallery/HIDE/auth browser run, migration/client generation, commit/branch/remote operation, activation or deployment. Whole V2 ACTIVE; normal V2/video flags OFF.

## Limits and next gate

Actual browser case is one provider and a synthetic focus event. Duplicate providers and omission/fresh-read controls are component evidence. This does not prove physical/native focus, Home layout, every Like/Save/comment completion ordering, account/cross-tab changes, failed batch plus pending mutation, older mutation arriving after a settled omission, CDN/cache or staging/load. Keep those claims separate; do not refactor them without evidence.

Next: verify the configured production frontend build in an isolated source copy, without touching the normal app's .next or supplying/deploying production configuration. This is an explicit remaining build gate, not another broad test rerun.
