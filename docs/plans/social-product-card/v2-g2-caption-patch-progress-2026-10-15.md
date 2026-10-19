# Social Product Card 2.0 — G2 caption PATCH checkpoint

This is partial local implementation of G2, not G2 or V2 completion. Owner
approvals remain recorded in AGENTS.md and the approved ADR. Features stay OFF.

## Implemented

- Additive forward-only V020 adds nullable `catalog_items.social_caption` without
  a default/backfill/drop. No description, ownership, price, stock or identity
  migration. UTF-16 length is enforced in Java; PostgreSQL character-count
  constraints are deliberately not presented as the same Unicode boundary.
- Existing PATCH request retains its six original fields and semantics. Only
  caption has setter-presence tracking: omitted retains, null/blank clears,
  nonblank replaces with edge trimming and inner newlines preserved. Client input
  cannot set the internal presence flag.
- Max 3000 UTF-16 units, optional plaintext. Bean validation rejects 3001 before
  writing; service also enforces the normalized length. This is data storage, not
  HTML rendering or a claim of frontend XSS verification.
- Default-OFF backend `covie.social-v2.caption-authoring-enabled` rejects direct
  caption PATCH with 404 while retaining existing patches. Existing merchant
  capability and version/If-Match checks stay authoritative.
- Seller management detail returns optional socialCaption. Null remains omitted
  under existing non-null JSON inclusion. Public payloads are not extended here.
- Material caption/media changes on ACTIVE (and now PAUSED), visible-moderation items reevaluate
  the actual CatalogPublishPolicy. PRE_APPROVAL changes item moderation to PENDING
  and opens/reuses the existing pre-approval case in the versioned transaction.
  AUTO_PUBLISH does not reset APPROVED or unrelated commerce state. Hidden/rejected
  content is not restored by caption editing. Unchanged caption/media does not
  introduce a new moderation transition.

## Actual verification

| Check | Evidence |
| --- | --- |
| DTO RED | `.local/social-v2-g2-caption-request-red.log`: required presence/accessor methods absent |
| DTO GREEN | `.local/social-v2-g2-caption-request-green.log`: 2 tests pass, 11.529 s; absent/null/empty/text, newline/HTML string, 3000/3001 ASCII and supplementary Unicode |
| HTTP RED | `.local/social-v2-g2-caption-patch-red.log`: actual owner JWT PATCH/GET lacks socialCaption |
| Adjacent edit regressions | `.local/social-v2-g2-caption-patch-green.log`: 13 existing edit tests pass; new test fails solely because its stale-ETag expectation was 412 instead of existing 409; this run is NOT a full green gate |
| Corrected HTTP + OFF | `.local/social-v2-g2-caption-patch-final.log`: 2 tests pass, 27.122 s, actual JWT/PostgreSQL writes and disabled-feature preservation |
| Schema/DTO final | `.local/social-v2-g2-caption-schema-final.log`: 4 tests pass, 29.911 s |
| Typed client | regenerated client and `pnpm api-client:check`: exit 0 |
| Web typecheck | `.local/social-v2-g2-caption-web-typecheck.log`: exit 0 |

The actual owner HTTP regression verifies trimmed multiline caption readback,
omitted caption retention, null clearing, max-length rejection with exact unchanged
row, existing 409 stale-version behavior, preserved description and exact unchanged
offer row. A merchant-scoped real PRE_APPROVAL setting causes editing an approved
ACTIVE item to produce PENDING, one OPEN pre-approval case and public detail 404.
The disabled-feature regression rejects a direct caption write without changing
the item and then succeeds with an old name-only PATCH.

The initial DTO class conversion exposed an OpenAPI drift: writeOnly markers and
lost old size metadata. Annotated accessors restored the previous schema fields.
Recursive semantic comparison now confirms all pre-V2 operations and original
schema fields unchanged, excluding only the two approved caption additions. No
generated-schema manual patch or old API status change was used to hide a failure.

Tests/builds run in the isolated `.local/social-v2-api-validation/api` copy with
disposable PostgreSQL. V020 has been applied only there; no recovered database
write, original API restart, commit, branch/PR change or deployment occurred.

## Remaining G2 and whole goal

Create request/service and response caption support are now implemented locally;
see `v2-g2-caption-create-progress-2026-10-15.md` for evidence and limitations.
Seller UI/preview/media limits/order acceptance, Admin caption readback and public
batch preview remain pending. The sequential paused-item edit/approve/activate
boundary is now repaired and verified; see `v2-g2-paused-caption-progress-2026-10-15.md`.
Concurrent
caption/moderation writes and additional
PRODUCT/SERVICE/RESALE persistence combinations still need targeted evidence.
Caption merchant denial and next-request membership revocation are now verified
in `v2-g2-caption-authorization-progress-2026-10-15.md`; concurrent revocation is not.

Populated local upgrade evidence is now recorded in
`v2-g2-caption-migration-progress-2026-10-15.md`. It does not prove representative
production/staging duration or lock behavior. Real responsive/browser,
storage privacy and G3–G6 release requirements remain unverified. No staging exists;
the complete V2 objective remains active.
