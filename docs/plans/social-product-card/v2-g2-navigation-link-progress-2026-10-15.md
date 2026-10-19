# Social Product Card 2.0 — G2 dirty-link navigation checkpoint

## Evidence and minimal fix

Caption/gallery previously registered only `beforeunload`. Client-side links can
change route without unloading the document, so this did not cover the plan's
unsaved-change warning. A focused test reproduced a dirty caption proceeding
through an internal link with no confirmation: expected one confirmation, got
zero, in `.local/social-v2-g2-navigation-red.log`.

New `useSocialDraftNavigation` coordinates dirty caption/gallery sources with
one document capture handler and the existing native unload-warning behavior.
For an ordinary same-origin, different-route link, cancellation prevents the
click before the Next Link handler and leaves the draft in place; approval
permits the existing handler. It does not replace Next routing or change URLs.

Multiple dirty sources share one confirmation, not one per field. Listeners stay
until the last source becomes clean/unmounts. Same-document/hash navigation,
modifier/new-tab gestures and downloads do not discard the current document's
draft and are not intercepted by this link handler. External full navigation
continues to use the unload warning rather than a new external-link policy.

The field's FormData, caption callback, gallery upload/order/retry and save/refresh
paths remain unchanged. No visual markup/layout was added and no new Figma
acceptance is claimed.

## Verified locally

- `.local/social-v2-g2-navigation-green.log`: **38 related tests pass, 5.80 s**,
  including existing Create/Edit authoring cases and caption hard-unload/undo.
- `.local/social-v2-g2-navigation-link-final.log`: **4 focused tests pass,
  1.04 s**, using actual Next Link components with controlled click handlers.
  Covers cancel/approve preserving the caption, undo, two dirty sources with one
  prompt, one remaining dirty source, cleanup, new-tab/modifier/download/hash
  exceptions and the existing hard-unload warning.
- `.local/social-v2-g2-navigation-typecheck.log`: web typecheck exits zero.
- `.local/social-v2-g2-navigation-lint-final.log`: targeted lint exits zero, no
  output. Initial test-only plain anchors triggered the Next link lint rule;
  changing fixtures to the actual Link component resolved it without weakening
  lint configuration or changing production behavior.

These are DOM/component checks with mocked `window.confirm` and route callbacks,
not a native browser dialog or a completed App Router transition. No full BE/web
suite, unchanged API/client check, recovered-data write, runtime environment edit,
service restart, activation, commit or deploy occurred.

## Not complete

This click handler does **not** protect browser Back/Forward, arbitrary
programmatic `router.push/replace/refresh`, or other non-link same-document route
changes. It deliberately does not monkey-patch history or Next internals, because
that could corrupt routing or interfere with successful saves. Native unload
behavior remains browser-controlled; a raw non-Next same-origin full-navigation
link may also trigger that existing unload warning after the confirmation.

Remaining navigation coverage and real browser acceptance must be addressed
before calling the G2 warning gate complete. Native gestures/dialogs, successful
save/reload behavior, responsive rendering and remaining G1/G2/G3 concurrency/
visibility gates are still open, along with G4 activity, G5 video and G6 release
evidence. No staging exists. Whole V2 remains active and all flags stay OFF.
