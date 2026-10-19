# G3: real Social card, preview and storage browser evidence

## Actual bounded integration

New opt-in `SocialFeedBrowserIntegrationTest` and
`apps/web/e2e/social-feed-browser.mjs` exercise the real `/search` route with
Next dev/webpack, real API, issued HttpOnly buyer cookie, disposable PostgreSQL
and MinIO. The existing Seller test's isolated storage clients/container are
reused; its previous Seller scenario is not run. A valid coordinate browsing
preference is seeded in the context's real localStorage. GPS selection and login
forms are not claimed tested. HTTP/HMR forwarding preserves localhost:3000 while
targeting the owned isolated Next child; API/media responses are not mocked.

The fixture has two public PRODUCT/STANDARD items with actual offers at 25000:
one null-caption/no-media item, and one literal multiline long caption with eight
READY image links plus a ninth PENDING_UPLOAD image. Sixteen-by-twelve PNG bytes
are stored in MinIO. The sixth object is actually deleted, with authenticated SDK
HEAD independently confirming 404, while its metadata remains READY. This models
an unavailable object after verification, not a real Seller publication chain.

The disposable bucket alone permits anonymous GetObject, as in the earlier
storage browser fixture. It is not a production policy change or proof of private
origin/CDN authorization. Chrome's local-network permission is granted only to
the temporary context; web security is not disabled.

## What the final run proves

- Initial Catalog view issues no preview request. Switching through the actual
  `Khám phá` button uses the real batch response keyed by item ID. Each observed
  batch requests both actual IDs, returns both, contains the exact caption and
  first six ordered READY media IDs, and sends Cache-Control no-store. No raw
  `storageKey` response property appears; public URLs are not treated as proof
  of storage-object privacy.
- Catalog→Social does not change discovery filters or reissue discovery in that
  transition. The actual long caption is literal text with its newline preserved,
  no script node, and working expand state. Product kind, price, real detail CTA
  href and enabled Like control survive integration. This does not repeat or
  claim Like/Save write, comment, share or detail-navigation acceptance.
- Keyboard ArrowRight/ArrowLeft changes gallery position/counter. Actual browser
  image loads decode at 16×12; controls advance through the six preview entries.
  The deleted sixth object's native request fails with `net::ERR_BLOCKED_BY_ORB`,
  the UI displays its image-error fallback, and previous navigation restores a
  working fifth image. This is NOT a claim that Chrome exposed an HTTP 404:
  the SDK HEAD establishes absence, and the browser records its actual failure.
- The empty item shows its real no-image state, not the commerce description as
  caption. Returning to Catalog adds no preview request in the observed settled
  transition; reentering Social and reloading fetch authoritative preview again.
  The final run records four preview GET observations and two discovery requests
  across entry/reentry/reload, not a claim of exactly one React-dev fetch overall.
- No browser API write or uncaught page error occurs. Java compares all-column
  canonical JSONB snapshots of catalog items, variants, offers, links and assets:
  unchanged. Reaction rows remain zero. Stored caption/media/commerce are not
  rewritten by browsing, image failure, mode change or reload.

## Rendered observations and result

**One integration test PASS, 47.03 seconds; Maven 59.867 seconds, exit 0**, in
`.local/social-v2-g3-feed-browser-accepted.log`. Redacted evidence and four
screenshots are in `.local/social-v2-g3-feed-browser-accepted/`.

At viewport widths 360/768/1280/1440, document scroll widths are
345/753/1265/1425; client widths are 360/768/1280/1440. The existing stable
scrollbar gutter reserves 15px, so the correct no-horizontal-overflow check is
scrollWidth <= clientWidth/viewport, not equality to the viewport. Gallery bounds
are 311×233.25, 354×265.5 and 413×309.75 at both desktop widths: stable 4:3.
All four captures were visually inspected. They are contextual viewport captures
with parts above/below the viewport or under the fixed header; they do not prove
complete unclipped-card visual acceptance at all four widths. Keyboard focus is
visible, but native touch/swipe and a full accessibility audit remain unproved.

Script lint PASS. SearchResults, provider, card, shared content and stylesheet
tested-copy hashes were compared with worktree sources; the final run uses the
current SearchResults, including preserved existing pagination/filter changes.

## Harness corrections, not product fixes

The initial private copy had older SearchResults and incorrectly treated the
already-existing map response as an array. That attempt was not accepted as
evidence for current worktree behavior. After its terminal result, the source-only
web copy was synchronized (excluding node_modules, .next, env files and generated
test output) and current API sources copied into the isolated build. No Maven
builds overlapped.

Subsequent failures exposed incorrect probe assumptions: viewport equality despite
the existing scrollbar gutter, then assuming a missing object's HTTP 404 would be
visible to the browser. The final probe measures real client/scroll bounds and
captures native storage request failure, paired with SDK-confirmed object absence.
No production feature, CSS, query, schema or API fix was justified or made.

## Isolation and open gates

Owned browser/Next children stop, and the absolute token-manifest path is absent
after cleanup. Normal web/API PIDs remain unchanged. No recovered/runtime DB
read/write, runtime migration/restart, normal flag activation, remote change or
deployment occurred. The preview flags are ON only in the isolated API/Next
children. Unchanged full BE, old query-plan, component, client-generation and
typecheck gates were not rerun. Normal V2/video flags remain OFF.

This is local Chrome behavior on `/search`, not proof for Home/merchant surfaces,
native touch/vertical scrolling, every type/visibility transition, account switch,
hide/revoke concurrency, batch error/retry, production object privacy, load,
cross-browser or staging/release. No staging exists; the whole objective remains
active. Current project UI, not Figma matching, is the visual reference.

Next action: Verify actual touch swipe versus vertical scrolling on the Social gallery with a separate focused browser probe.
Required from you: Nothing
Completion condition: Native touch advances only horizontal swipes, preserves vertical page scrolling and does not accidentally navigate the item link.
