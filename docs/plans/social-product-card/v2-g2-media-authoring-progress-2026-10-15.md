# Social Product Card 2.0 — image authoring limit/order checkpoint

Local G2 backend implementation behind the existing default-OFF
`covie.social-v2.caption-authoring-enabled` authoring gate. Video remains OFF;
this checkpoint covers catalog images, not mixed/video readiness.

## Evidence before changes

`.local/social-v2-g2-media-limit-red.log`: two targeted failures, 29.953 s.

- Create accepted 11 distinct READY images (201 instead of approved limit 422).
- Resubmitting an unchanged legacy 12-image list rewrote its association sort
  values from 0,2,…,22 to 0,1,…,11, despite no requested gallery order change.

The pending process handle from the prior continuation was no longer available;
its terminal BUILD FAILURE log and absence of a running Java build were checked
before starting another build. It was not restarted on an observation timeout.

## Minimal changes

- V2 Create rejects more than 10 distinct media IDs inside the existing
  idempotent creation transaction. Existing cached replay remains handled before
  the command; the new quota is not a global pre-replay rejection. No change to
  actor/merchant access or request hashing.
- V2 PATCH enforces the same cap only when the requested ordered unique IDs differ
  from the current gallery. Caption-only edits and an identical submitted legacy
  gallery do not force removal of existing images or rewrite association rows.
- An explicit changed gallery exceeding 10 is rejected; there is no silent
  truncation. Reducing/reordering to at most 10 uses the existing association
  implementation: input order persists and the first image becomes the cover.
- Duplicate IDs retain the existing stable deduplication behavior. The cap counts
  attached assets, not raw array length. No incompatible OpenAPI maxItems=10
  constraint was added to reject previously supported duplicate input.
- With the authoring flag OFF, original media authoring remains unchanged. READY,
  purpose and ownership policy for new attachments is not weakened or refactored.

## Verification

`.local/social-v2-g2-media-limit-green.log`: **3 tests pass**, zero
failures/errors/skips, **37.862 s**.

1. Actual owner JWT Create: 11 distinct images → 422, unchanged item count and no
   retained idempotency record. Reuse that key for 10 distinct images plus a
   duplicate → 201. Exact persisted ID order, sort values 0–9, unique first cover,
   seller management GET order/primary, and 25000 VND offer are checked.
2. Actual owner JWT PATCH: existing 12-image gallery with gapped sort values stays
   exactly unchanged after caption-only edit and after identical media submission.
   Changing it to 11 → 422 with exact item/media rows unchanged. Explicit reverse
   order of 10 → versioned 204, persisted/readback order and first cover correct,
   exact commerce offer unchanged.
3. Default-OFF compatibility: caption Create/PATCH still denied, old name-only
   PATCH succeeds, and old Create with 11 valid images succeeds. This extends the
   existing OFF fixture; it is not an enabled-flag over-limit acceptance.

Tests run sequentially in `.local/social-v2-api-validation/api` with disposable
PostgreSQL. New assets are preverified READY fixture rows; no live storage upload,
HEAD/magic verification or real browser is exercised here. The quota's placement
preserves the idempotency command boundary by source inspection; switching a live
application from OFF to ON and replaying a historical 11-image response was not
tested.

No schema/API shape change, generated-client edit, migration, whole backend run,
recovered-data write, original service restart, feature activation, commit or
deployment occurred. Unchanged schema/client/UI gates were not rerun.

## Remaining full objective

G2 Seller Studio still needs caption controls, media UI ordering/cover, safe upload
recovery, preview and real responsive/browser verification. Media-content changes
under moderation and concurrent authoring still need targeted end-to-end evidence.
Public feed/gallery acceptance belongs to G3, mixed video to G5. G1 browser/query/
storage privacy, G4 activity and G6 operational/release evidence remain unfinished.
No staging exists. The original complete V2 objective stays active and flags OFF.
