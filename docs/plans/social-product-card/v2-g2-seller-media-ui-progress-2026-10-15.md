# Social Product Card 2.0 — Seller media UI checkpoint

Partial local G2 implementation. The full objective remains active; no flags were
enabled and no staging/browser acceptance is claimed.

## Implemented

- Shared V2 gallery for Create/Edit, using actual management media and locally
  selected files. Existing and new images share one ordered list. Drag/drop,
  keyboard-accessible earlier/later buttons, explicit first-cover selection and
  removal update that order. Save resolves and sends IDs in the selected order.
- Reuses the existing image validator and uploader (JPEG/PNG/WebP, 10 MiB), existing
  Seller tokens/buttons and Phosphor icons. Dynamic image previews use Next Image;
  no new static asset. Exact Catalog frame 370:745 retained context/screenshot was
  reviewed before editing; it is not a rendered visual comparison.
- Before adding files, reject selections that would exceed 10 and retain the
  current gallery rather than truncate it. Existing over-limit legacy galleries
  remain visible; caption-only edits omit mediaIds. Changed over-limit galleries
  cannot be saved until reduced to the approved limit.
- Successful upload IDs are retained in component memory. A failed file can be
  retried without reuploading earlier successful files. Retrying a failed Create
  transport keeps the same IDs, payload and existing idempotency key when the user
  has not changed the intent. User changes invalidate the Create intent as before.
- Per-file status distinguishes selected/uploaded/error stages. Upload failure
  preserves selection and other form data; no item PATCH/Create runs with only a
  partially resolved gallery. Pending/stale controls remain disabled.
- Dirty gallery adds a hard-navigation warning and removes it when order returns
  to the saved state. Blob URLs are retained through reorder/retry and revoked on
  removal/unmount. No upload URL/token is persisted in localStorage or URLs.
- All new behavior is behind the existing authoring UI flag. The old OFF uploader
  path and ordinary commerce fields remain unchanged. Backend ownership/readiness,
  count, moderation, ETag and merchant permissions remain authoritative.

## Actual verification

| Check | Evidence |
| --- | --- |
| RED | `.local/social-v2-g2-seller-media-ui-red.log`: new Social picker/cover controls absent; 2 fail |
| Changed forms regression | `.local/social-v2-g2-seller-media-ui-green.log`: 32 pass, 5.44 s |
| Final changed scenarios | `.local/social-v2-g2-seller-media-ui-final.log`: 3 selected pass, 30 unselected, 4.35 s |
| Drag/keyboard checkpoint | `.local/social-v2-g2-seller-media-drag-keyboard.log`: one selected pass, 3.90 s |
| Web typecheck | `.local/social-v2-g2-seller-media-typecheck.log`: exit 0 |
| Targeted lint | `.local/social-v2-g2-seller-media-lint.log`: exit 0, zero errors; three existing img warnings in unchanged legacy callsites |

The final Create component scenario selects two files, changes cover, fails the
second upload, retries it, then fails/retries the Create transport. It verifies
upload order B,A,A (not B,A,B,A), stable final media IDs and identical create
arguments/idempotency key across transport retry, preserved name and two revoked
preview URLs on unmount.

The Edit scenario checks cover selection, keyboard restoration to the original
order (removing the dirty warning), HTML drag/drop with a plain-text transfer,
and a media-only PATCH with the selected order. The legacy scenario retains all
12 original entries when a further addition is rejected and sends only caption
for an unrelated edit. Mocked transport/upload/router are used; the validator is
mocked in the Create fixture. Real direct-storage upload, native browser drag,
mobile touch behavior, network recovery and web-to-backend acceptance remain
unverified. Backend gates were not rerun because backend code did not change.

No API/schema/client change, migration, recovered-data write, original service
restart, runtime configuration edit, commit or deployment occurred.

## Remaining full objective

G2 still needs shared Social Card preview reflecting the actual saved/draft model,
complete in-app navigation protection, media-content moderation/concurrency and
360/768/1280/1440 real browser verification. The current legacy commerce preview
sidebar is not yet the new shared Social preview. G1 query/storage privacy/browser,
G3 public batch preview/feed gallery, G4 activity, G5 verified mixed video and G6
operational/release evidence remain unfinished. No staging exists. Flags stay OFF.
