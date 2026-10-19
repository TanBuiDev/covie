# Social Product Card 2.0 — G3 batch preview backend checkpoint

## Scope and authorization

All plan approvals, including separate-approval requirements, remain granted by
the owner. No approval is requested again. Infrastructure and technical gates
still need actual evidence. This is a local backend checkpoint, not completion
of G3 or the full plan. All runtime V2 flags and video remain OFF.

## Implementation

- Added `GET /api/v1/catalog/social-previews`, independently gated by default-OFF
  `covie.social-v2.previews-enabled`. OFF returns 404 and does not disable the
  existing Social Summary API.
- Requires 1–40 raw UUID values before deduplication. Full hexadecimal UUID text
  is validated on this new endpoint only; existing global conversion and older
  endpoints are untouched. Missing, malformed and over-limit input returns 422.
- Reuses the existing Social visibility predicate: active, undeleted item and
  merchant, with public moderation states. Unknown and nonpublic IDs are omitted
  without disclosing their status. Response keys follow deduplicated input order.
- Returns the independent caption and at most six READY, nondeleted,
  CATALOG_IMAGE assets per item. Existing cover/order are read without rewriting
  rows; the first eligible returned image is the cover when the stored primary
  is ineligible. Equal order values use asset UUID as a deterministic tiebreaker.
- One bounded item/lateral-media query and one batch asset lookup (at most 240
  IDs, skipped for no media) replace per-item detail calls. No schema/index
  migration was introduced without measurement.
- Uses the existing media URL resolver. No upload link, storage-key property,
  commerce description or viewer Like/Save state is added to the response.
  Cache-Control is `no-store`; existing Summary caching remains unchanged.
- The existing `/api/v1/catalog` GET rate-limit routing covers this path by
  source inspection. No new HTTP rate-limit saturation evidence is claimed.
- Generated OpenAPI and TypeScript client include required UUID-array bounds.
  New media schema is named `SocialPreviewMedia`, avoiding changes to the
  pre-existing Admin `Media` schema.

## Evidence-driven corrections

1. Missing route: `.local/social-v2-g3-preview-red.log` recorded two failures.
   Initial targeted implementation: five tests passed in 32.440 s, including
   default-OFF compatibility with the existing summary.
2. An actual exported-schema collision between nested DTOs named `Media` changed
   Admin media fields. A dedicated failing OpenAPI assertion reproduced it;
   uniquely naming only the new schema restored Admin fields. Required/bounded
   query metadata was then added and asserted after an export comparison showed
   it missing.
3. `.local/social-v2-g3-preview-strict-uuid.log` recorded 422 expected versus 200
   for `1-1-1-1-1`: Spring's UUID conversion accepts shortened groups. The new
   route now validates raw text before UUID conversion; no global converter was
   changed.

Final `.local/social-v2-g3-preview-final.log`: **5 tests pass**, zero
failures/errors/skips, **31.637 s**. Two HTTP/PostgreSQL tests cover visibility,
request ordering/deduplication, 40 distinct IDs, over-limit input, malformed and
shortened UUIDs, caption literal readback, six ordered eligible images, fallback
cover, empty media, no-store and unchanged catalog/media rows. Three OpenAPI
tests include the media-schema separation and UUID-array contract.

Client generation, client typecheck and web typecheck passed; logs are
`.local/social-v2-g3-preview-client-generate.log`,
`.local/social-v2-g3-preview-client-check.log` and
`.local/social-v2-g3-preview-web-typecheck.log`.
A semantic Node comparison against `.local/social-openapi-20261015.json`
confirmed all **142 pre-V2 paths and 200 schemas** unchanged except the four
previously approved optional caption additions. This is a pre-V2 baseline,
not a newly captured pre-preview snapshot. Admin media compatibility also has
the dedicated OpenAPI assertion described above.

Tests ran sequentially in the isolated API copy with disposable PostgreSQL.
No full backend suite, recovered-data write, runtime restart, feature activation,
migration, commit or deployment occurred.

## Remaining gates

Actual SQL query plans/counts and representative batch/load measurements have
not yet been captured. The source has bounded query paths but that is not a
measured performance gate. Object-storage privacy, hide/read races, live cache
invalidation and end-to-end public visibility are also unverified.

The frontend does not yet request this batch or render the new shared Social
Card. Social-only network behavior, catalog-mode isolation, shared seller draft
preview, gallery browser/responsive acceptance and remaining G1/G2 gates remain
open. G4 activity, G5 verified video and G6 operational evidence remain unfinished.
No staging is available; staging and production release stay unverified.
