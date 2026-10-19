# Social V2 — revalidate rendered previews after confirmed reaction 404

## Reproduced UI defect

Plan G3.4 requires correct handling when an item is hidden while the feed is open, including duplicate cards. The gate reconciliation identified that the reaction handler showed a 404 error but retained the already-rendered caption/gallery.

One focused component regression renders the same item in two independent real SocialFeedProviders, with successful initial summaries/previews, then makes the existing reaction adapter reject with actual application error class `CovieApiError(404, RESOURCE_NOT_FOUND)`. Before the fix both providers kept the old caption/media and neither revalidated: expected four preview calls (two initial plus two fresh), actual two. Evidence: `.local/social-v2-g3-unavailable-red.log`, 1 failed/6 skipped. Transport is mocked; this is not a real browser/Admin HIDE/API chain claim.

## Minimal fix

- Card: only Social V2 reaction errors of type CovieApiError/status404 request preview revalidation.
- Provider: a nonpersistent window event carries only the public item ID. Only active V2 providers containing that ID retire their current preview scope and fetch their existing bounded preview batches again. Independent duplicate providers receive the same invalidation.
- Existing loading presentation immediately replaces the old content while the server rechecks. The fresh authoritative preview map decides whether the item is still visible; omission uses the existing null-card behavior. This is not a permanent client tombstone or invented hidden status.
- Existing scope/live guards prevent retired preview responses being accepted. No new TTL, polling, realtime service, storage cache, API contract, endpoint, BE mutation or business permission is added.
- Summary state is not discarded/refetched by this preview-only operation. Catalog and flag-OFF Social behavior stay unchanged. A 429 does not trigger invalidation.

## Targeted verification

```text
pnpm --filter @covie/web exec vitest run tests/marketplace-view/social-card-content.test.tsx tests/marketplace-view/social-previews.test.tsx tests/marketplace-view/marketplace-social.test.tsx
```

`.local/social-v2-g3-unavailable-green.log`: **17 PASS**, 5.00 s. The new regression confirms both duplicate captions and image nodes are absent while fresh requests are held; resolving fresh maps without the item removes both cards. Exactly one reaction request, two initial summary calls (no extra summary query). Existing gallery/caption/fallback/Catalog/provider/error/retry/old interaction tests also pass.

Two additional guard cases then run without repeating unchanged production checks:

```text
pnpm --filter @covie/web exec vitest run tests/marketplace-view/social-card-content.test.tsx -t "preserves existing content"
```

`.local/social-v2-g3-unavailable-guards.log`: **2 PASS**, 7 skipped, 4.14 s. V2 429 keeps caption and does not requery; V1/flag-OFF 404 keeps its original cover/error and issues no preview request. Production files were unchanged between these runs; the counts are separate runs, not one full-suite result.

- Final web typecheck exits0: `.local/social-v2-g3-unavailable-typecheck.log`.
- Targeted ESLint for provider/card/test exits0: `.local/social-v2-g3-unavailable-lint.log`.
- Targeted `git diff --check` passes. No CSS/design change, full web suite, BE test, client generation, runtime/recovered DB access, normal restart, activation, commit/remote operation or deploy.
- Normal web/API PIDs 7544/25008 remain alive. V2/video normal flags OFF; whole objective ACTIVE.

## Remaining evidence

This does not prove actual Next/browser + issued JWT + Admin HIDE + reaction404 + fresh batch omission as one chain. Existing BE HIDE/visibility evidence is separate. Comment-specific404 cannot automatically mean the entire item is hidden, so that handler was not broadly changed. Cross-tab/account switching, eventless invalidation, object/CDN cache, load, physical devices and staging remain unverified.

Next: use the existing isolated feed/browser fixture for a focused actual HIDE/unavailable chain, rather than repeat the successful gallery/toolbar/feed happy paths. Keep readonly fixture assertions for its old scripts; any mutation-specific fixture must check exact moderation effects and unrelated-row preservation.
