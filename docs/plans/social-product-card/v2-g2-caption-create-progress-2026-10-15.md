# Social Product Card 2.0 — G2 caption Create checkpoint

Local implementation on the existing dirty tree. This extends the approved G2
caption contract; it is not completion of G2 or the complete V2 objective.

## Implemented

- Optional socialCaption in existing CreateCatalogItemRequest, max 3000 UTF-16
  units. Existing fields, routes, roles and create-as-DRAFT semantics stay intact.
- The same default-OFF `covie.social-v2.caption-authoring-enabled` switch protects
  non-null caption input before idempotency processing. A denied request cannot
  consume a create key or insert an item. Absent/null caption remains compatible
  with legacy creation. Empty/blank captions normalize to null when enabled.
- Store caption separately using the existing nullable V020 column. Trim edges,
  preserve inner newlines, and do not copy into description, offers or stock.
- Create response includes optional socialCaption; replay deserializes the stored
  response through the existing IdempotencyExecutor. Caption participates in
  request hashing when non-null. Null is explicitly excluded for the new property
  only, preserving the old request hash even with a null-including ObjectMapper.
  No global hasher/refactor or idempotency table rewrite was introduced.
- Optional response caption is excluded when null, including legacy cached
  responses whose JSON has no new field. Create does not publish or grant approval;
  existing lifecycle publish policy remains authoritative.

## Evidence

| Check | Actual result |
| --- | --- |
| RED | `.local/social-v2-g2-caption-create-red.log`: over-limit new caption was ignored; expected 422, got 201 |
| Create regressions + OpenAPI | `.local/social-v2-g2-caption-create-green.log`: 13 tests pass, zero failures/errors/skips, 32.201 s |
| OFF/hash focused gates | `.local/social-v2-g2-caption-create-guards.log`: 4 tests pass, 26.877 s |
| Generated client | regeneration and client typecheck pass |
| Web typecheck | `.local/social-v2-g2-caption-create-web-typecheck.log`: exit 0 |
| Contract parity | recursive comparison: old operations and original schema fields unchanged except four approved optional caption additions across Create/PATCH/management responses |

One new integration case traverses PRODUCT/STANDARD, SERVICE/STANDARD and
PRODUCT/RESALE with independently issued owner JWTs. Actual HTTP create/replay
returns the same full JSON, stores trimmed multiline/emoji caption and original
description, retains DRAFT and the 25000 VND offer, and creates one item, default
variant and creation outbox event. Changing only caption under the same key returns
409. A 3001 UTF-16-unit supplementary-Unicode input returns 422 before item creation.
The other unchanged create regressions run because their shared create service and
DTO changed; no whole backend suite was rerun.

The default-OFF regression also checks denied caption Create leaves item count
and operation idempotency records unchanged; an old Create without caption succeeds
using that same key. The unit compatibility check compares hashes against an
explicit snapshot map of all eleven legacy request fields for omitted/null caption,
and proves a non-null caption changes the hash. This is not a claim that an actual
pre-upgrade production idempotency record was replayed.

All Maven builds use `.local/social-v2-api-validation/api` and disposable PostgreSQL.
No additional migration, recovered-data write, original service restart, commit,
PR change, feature activation or staging/production deployment occurred.

## Remaining

Both Create and PATCH caption persistence now exist. G2 still needs Seller UI and
media limits/order/preview, Admin caption readback, existing-data migration evidence,
additional caption authorization/revocation and concurrency verification. Source
inspection identified the paused-edit boundary, now repaired and verified in
`v2-g2-paused-caption-progress-2026-10-15.md`: edits recheck PAUSED as well as ACTIVE,
and approval preserves PAUSED until explicit Seller activation. Concurrent races
on that boundary remain unverified.

G1 browser/query/storage-privacy gates, G3 feed previews/gallery, G4 private activity,
G5 verified video and G6 release evidence remain in scope and unfinished. Features
stay OFF. No staging exists; the complete goal remains active.
