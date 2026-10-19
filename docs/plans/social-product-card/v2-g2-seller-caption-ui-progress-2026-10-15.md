# Social Product Card 2.0 — Seller caption controls checkpoint

Partial local G2 UI implementation; Seller Studio and the complete V2 goal remain
unfinished. No feature activation or real browser/staging acceptance is claimed.

## Implemented

- Existing Create/Edit pages pass server-only
  `COVIE_SOCIAL_V2_CAPTION_AUTHORING_ENABLED === "true"` to their forms. Default
  false hides caption controls and omits the new request field. The independent
  backend authoring gate remains authoritative; no runtime configuration changed.
- Shared caption field in the existing Seller form style: clearly separated from
  commercial description, multiline textarea, 3000 UTF-16-unit counter/maxlength,
  clear and revert controls, accessible supporting text and unsaved indicator.
- Create sends trimmed caption independently of description. Edit sends caption
  only when changed; clear sends explicit null, matching approved PATCH semantics.
  Unchanged commercial fields/media are not added to a caption-only patch. Uses
  the generated request types already consumed by the forms.
- Controls follow existing UPDATE permissions and pending/stale states. A version
  conflict retains the typed caption and disables mutation until authoritative
  reload. A new server ETag remounts caption state from server-confirmed data.
- Closing/hard navigation warns while caption is dirty; undo removes the warning,
  and unmount cleans up its listener. This does not yet cover every in-app Link,
  browser history or router navigation path.
- Create clear/revert/input changes invalidate the existing create intent key;
  no auth token, caption or idempotency key is stored in localStorage or URLs.

No price, variant, offer, inventory, shipping, lifecycle command or barcode flow
was redesigned. Existing user WIP remains untouched outside these additions.

## Design scope

The exact registered Catalog editor frame **370:745** from file
`4LcMXFQz3BnU1EXzBk69t2` was retrieved with design-to-code guidance and screenshot.
It supplies the existing form/media/card/field composition reused here. The
initial historical reference 357:407 was identified as Location editing, not
Catalog, and was not used to implement this feature. No new static asset or
sidebar/icon reconstruction was introduced. Actual responsive rendered comparison
against the Catalog frame remains unverified.

## Actual evidence

| Check | Result |
| --- | --- |
| Caption UI RED | `.local/social-v2-g2-seller-caption-ui-red.log`: Create/Edit lack caption controls, 2 fail, 28 unselected |
| Changed forms regressions | `.local/social-v2-g2-seller-caption-ui-green.log`: 30 pass, 5.19 s |
| Final Create interaction | `.local/social-v2-g2-seller-caption-create-final.log`: one selected pass, 3.94 s; actual enabled submit button and required price fixture, no programmatic submission bypass |
| Caption controls | `.local/social-v2-g2-seller-caption-controls-final.log`: one pass, 942 ms; UTF-16 count, hard-navigation cancellation, undo/listener cleanup |
| Permission/conflict | `.local/social-v2-g2-seller-caption-safety.log`: two selected pass, 3.97 s; no controls without UPDATE, caption retained/disabled after 409 |
| Web typecheck | `.local/social-v2-g2-seller-caption-typecheck.log`: exit 0 |
| Targeted lint | `.local/social-v2-g2-seller-caption-lint.log`: exit 0, zero errors; three existing img-element warnings in unchanged image callsites |

The controls test initially queried a unique status but both the output counter
and unsaved message have status semantics. The test was corrected to select the
explicit unsaved message; production semantics were not weakened to satisfy it.
Strengthening Create to click its real submit button exposed a missing required
price in the new fixture; the fixture was corrected, not the business validation.

Transport/upload/router are mocked component tests, not end-to-end web→BE evidence.
No backend/schema change or BE rerun, recovered-data write, original service
restart, environment edit, commit or deployment occurred in this checkpoint.

## Remaining whole objective

Seller media selection/order/cover controls and safe upload retry integration,
shared real Social Card preview, full unsaved in-app navigation handling and
360/768/1280/1440 browser acceptance remain pending. G1 browser/query/storage
privacy and G3–G6 remain in the original scope. Video and other V2 flags stay OFF;
staging is unavailable and release remains unverified.
