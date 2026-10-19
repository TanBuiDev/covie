# Social Product Card 2.0 — shared Seller draft preview checkpoint

## Changes

Create/Edit now use `CatalogSocialPreview` behind the existing default-OFF
caption-authoring flag. The wrapper reuses the feed's `SocialPostContent`, not
a second caption/carousel implementation or a public preview API request.

- Create replaces only its enabled-flag legacy preview; its OFF sidebar remains
  unchanged. Edit adds a separate preview panel without changing form commands,
  offer/variant permissions or the media editor.
- Caption state comes from the field callback, including clear and undo. Initial
  Edit values come from the management response. Text is trimmed like the
  existing write path, with newlines preserved; description is not used as caption.
- Current ordered gallery entries supply the preview. It uses up to six READY
  stored assets or selected local files. Local images reuse the gallery's existing
  Blob URLs and cleanup ownership; no extra URLs, upload or mutation are created.
- Reordering/removing a cover updates the shared gallery. A larger legacy gallery
  is not truncated or rewritten: six is a display bound only. Pending stored
  media do not become public-ready preview assets.
- The preview explicitly says it is local, not confirmation of persistence or
  publication. It contains no fake public URL, checkout/share/reaction action or
  viewer count. The shared gallery accepts an optional detail href; feed links
  retain their previous semantics, while Seller preview images are non-links.

The callback now supplies its current string, without changing the caption
field's FormData/dirty-warning contract. Existing authoring commands, idempotency
and role checks remain unchanged. On successful save the existing page refresh
and editor-key mechanism still owns confirmed reload; no new save flow was added.

## Design evidence

Reused the previously retrieved exact Seller editor
[370:745](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie?node-id=370-745)
high-fidelity context and screenshot before editing. It shows the V1 editor and
white-panel/token foundation, not a dedicated Social V2 preview layout. The new
panel composes existing Seller and Social components; no surrounding redesign,
new static asset or exact V2 pixel-match claim is made. Actual browser geometry
and responsive/visual acceptance remain pending.

## Verification

- `.local/social-v2-g2-draft-preview-red.log`: **2 failures**, both missing preview
  region; 33 unrelated cases unselected.
- `.local/social-v2-g2-draft-preview-green.log`: **2 focused passes, 4.22 s**.
  Create changes caption/name, adds two local files, makes the second the cover
  and sees the new order without upload/Create. Edit starts with confirmed
  caption/image, changes and undoes caption, removes media and sees the empty
  state without PATCH. Both previews have no navigation link.
- `.local/social-v2-g2-draft-preview-regression.log`: **42 passes, 5.98 s** in four
  changed/dependent test files, including existing authoring/permission/retry,
  caption controls and shared Social Card behavior.
- `.local/social-v2-g2-draft-preview-six.log`: **1 focused pass, 3.97 s**. Eight
  legacy associations, first pending: preview shows six READY assets in order,
  while the eighth remains editable and no PATCH occurs.
- Final web typecheck passed:
  `.local/social-v2-g2-draft-preview-typecheck-final.log`. Targeted lint exited
  zero with three pre-existing legacy `<img>` warnings, no errors:
  `.local/social-v2-g2-draft-preview-lint.log`; the final additional test's lint
  also passed in `.local/social-v2-g2-draft-preview-test-lint.log`.

The tests use DOM rendering, mocked APIs/uploads and synthetic Blob URLs. They
do not prove native image loading/storage, real browser preview behavior, visual
matching or a live save-to-confirmed-refresh round trip. No backend, schema or
client generation changed, so those gates were not rerun. No recovered-data
write, original service restart, environment activation, commit or deploy occurred.

## Remaining work

This shares caption/gallery rendering, not a full interactive public commerce
Card in the editor. Real browser and mobile/desktop parity, dirty in-app navigation
protection and remaining media/moderation concurrency/visibility gates are open.
The existing editor refresh mechanism is preserved but not a newly observed
live persistence round trip in this checkpoint. G1/G3 remaining gates, G4 activity,
G5 video and G6 staging/release evidence remain unfinished. No staging exists.
All V2 flags stay OFF and the full objective stays active.
