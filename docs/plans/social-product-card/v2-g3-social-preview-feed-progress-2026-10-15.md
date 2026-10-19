# Social Product Card 2.0 — G3 preview transport/provider checkpoint

## Scope

Implemented the generated-client preview adapter and data flow in the existing
`SocialFeedProvider`. The existing Home and Search providers already receive
`enabled={mode === "social"}`; those routes, their layout and Card markup were
not changed. This is data plumbing, not completion of the shared Social Card.
No new visual/Figma acceptance is claimed.

The independent web flag is
`NEXT_PUBLIC_COVIE_SOCIAL_V2_PREVIEWS_ENABLED === "true"`, default OFF.
It is a Next.js public **build-time** flag, not a secret or runtime toggle;
changing it requires rebuilding the web application. No environment file was
edited and no running feature was activated. Backend preview gating remains
independent and OFF. Both layers must satisfy their technical activation gates
before enabling the feature. Tests can supply the provider's explicit optional
`previewsEnabled` prop without changing runtime configuration.

## Minimal implementation

- The new adapter consumes generated `SocialItemPreview` and generated GET
  parameter types. Empty batches do no work; more than 40 raw IDs are rejected
  before transport; IDs are deduplicated and requests use `cache: "no-store"`.
- Provider reuses its existing stable unique ID scope, splits requests into
  chunks of at most 40, and requests previews only when Social and V2 are both
  enabled. Catalog mode does not issue a preview request.
- Preview loading/error/data/retry are separate from Summary. Preview transport
  failure does not discard confirmed Like/Save/count state. Its retry does not
  refetch successful summaries. Existing tab/online reconciliation also refreshes
  previews through the existing attempt value.
- Preview scope changes reset data before it can render, including when leaving
  Social and returning to the same items. Effect cleanup ignores pending old
  completions. There is no localStorage or cross-account persistent cache.
- One actual test exposed error-code loss when the generated client had already
  consumed response JSON: 429 became generic `HTTP_ERROR`. Only the new adapter
  now uses the existing `parsedApiError(response, error)` helper. Existing API
  functions and server behavior remain unchanged.

## Verification

1. `.local/social-v2-g3-preview-feed-red.log`: six failures before implementation,
   from the missing adapter/provider behavior.
2. `.local/social-v2-g3-preview-feed-green.log`: **11 tests passed, 3.99 s**,
   including five existing server-confirmed reaction, duplicate-card sync and
   lazy-comment/report regressions alongside the initial new preview cases.
3. `.local/social-v2-g3-preview-parsed-error-red.log`: one failure reproducing
   consumed response-body error loss, one adapter case passed.
4. `.local/social-v2-g3-preview-feed-final.log`: **7 focused tests passed,
   1.07 s**. Coverage includes no default-OFF/Catalog requests; 41 distinct IDs
   plus a duplicate → batches 40+1; independent failure/retry retaining Summary;
   ignored completion after Catalog switch followed by new-scope loading;
   enabled build-flag behavior and clearing prior caption before Social re-entry;
   generated GET/no-store/raw bound; preserved parsed 429 code/message.
5. Final web typecheck and targeted ESLint both exited zero, with no lint output:
   `.local/social-v2-g3-preview-feed-typecheck-final.log` and
   `.local/social-v2-g3-preview-feed-lint-final.log`.

Tests use mocked transport and a context probe. The earlier existing Card tests
exercise their unchanged UI with mocked API; none of these is a real browser
network trace or a web-to-backend/storage integration test. No full backend,
full web suite or unchanged client generation was rerun. No backend/schema,
recovered database, service restart, environment activation, commit or deployment
change occurred.

## Remaining work

`MarketplaceProductCard` does not yet consume/render these preview fields.
The shared caption/gallery with image controls, error/retry affordance and
seller draft preview remains to implement against the exact verified Figma
frame. Until then, the plumbing must not be mistaken for a complete user-facing
feature or an activation-ready flag.

Actual rendered Social/Catalog network behavior, image failures, accessibility,
responsive viewports and hide/read/storage privacy remain unverified. The local
backend query plans are documented separately; they do not prove staging load.
G1/G2 remaining gates, G4 activity, G5 video and G6 release evidence remain open.
No staging exists and release remains unverified. Whole Social V2 stays active.
