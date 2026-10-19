# Social Product Card 2.0 — G3 shared caption/gallery checkpoint

## Implemented locally

The existing `MarketplaceProductCard` now consumes provider previews only when
Social mode and the independent V2 flag are enabled. Its old Catalog/default-OFF
media path, commerce data, community controls, comments and CTA remain intact.

- Shared `SocialPostContent` uses generated media types. Caption is literal
  React text with preserved newlines, never HTML. Long captions use a four-line
  clamp with an accessible expand/collapse button. The presentation considers
  text over 240 characters or four newline-separated lines long; this is not a
  backend caption restriction or truncation of stored text.
- Gallery uses API order and dynamic URLs, a fixed 4:3 frame, meaningful image
  labels, counter and native previous/next buttons. One image has no controls;
  no media has a placeholder, without falling back to a potentially stale
  discovery cover. A failed image has a local placeholder; another image still
  works. Gallery state resets when its media data changes.
- Arrow keys work on the focusable multi-image region; controls are native
  keyboard buttons. Horizontal swipe moves one image, while predominantly
  vertical gestures do not. The gallery uses `touch-action: pan-y` and does not
  actively cancel vertical touch events. Incidental link clicks immediately
  after a swipe are suppressed; explicit image links retain detail navigation.
- Controls are outside the detail link, use Phosphor icons, and have minimum
  44px targets/focus outlines. No animation/autoplay was added.
- While preview is loading or failed, a scoped status/retry replaces the stale
  Card content. Successful batch omission removes the Card rather than showing
  an old caption/image from discovery. This is a UI safeguard after a successful
  authoritative read, not proof of a concurrent hide/read or storage privacy gate.

The component is reusable for the seller draft preview but has not yet been
wired into Seller Studio. Backend and generated contracts were not changed.

## Design evidence and limits

Retrieved high-fidelity context **and screenshot** for the exact frozen Home
frame [282:290](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie?node-id=282-290)
before UI edits, following `figma-design-to-code`. That frame contains the V1
Grid Card, not a caption/carousel Social V2 variant. It is evidence for inherited
Inter/emerald/white-card foundations, **not an exact Social V2 visual target**.
No dedicated Social V2 frame has been verified in this checkpoint.

The implementation retains the existing Social Card layout and tokens, adding
only the approved plan's caption/gallery behavior. No static image assets,
temporary Figma URLs, new icon system or surrounding Home layout were introduced.
Figma's sample image illustrations remain out of scope; product media stays
data-driven. No pixel-match, screenshot geometry or rendered visual acceptance
is claimed from the design-context read or component tests.

## Verification

- `.local/social-v2-g3-card-red.log`: **4 failures and 1 passing Catalog case**,
  demonstrating absent caption/gallery/retry behavior before implementation.
- `.local/social-v2-g3-card-green.log`: **15 tests passed, 4.22 s**: initial five
  Card cases, five existing social interaction/comment regressions and five
  provider preview cases.
- `.local/social-v2-g3-card-final.log`: **6 focused tests passed, 3.83 s** after
  adding kind-badge preservation and direct horizontal/vertical swipe checks.
  Cases cover literal caption, gallery order/keyboard/buttons/link semantics,
  expand/collapse, per-image failure, single/empty media, unchanged Catalog
  request/cover behavior, preview retry with authoritative omission, and swipe
  incidental-click cancellation. Tests use mocked transport and DOM touch events.
- Final web typecheck and targeted ESLint exited zero, no lint findings:
  `.local/social-v2-g3-card-typecheck-final.log` and
  `.local/social-v2-g3-card-lint-final.log`.

No whole backend/web suite or unchanged BE/client checks were repeated. No
recovered-data write, runtime environment edit, original service restart,
feature activation, commit or deployment occurred.

## Remaining scope

Real browser caption clamping, native touch/keyboard/focus behavior, image
loading/layout stability and 360/768/1280/1440 viewport checks remain unverified.
The existing Home/Search layout and ad ordering were not redesigned or validated
anew. Long-text, actual network/state synchronization and visibility/storage
acceptance still need end-to-end evidence, not just mocked DOM checks.

Seller Studio shared draft preview remains unfinished, along with the other
previously recorded G1/G2 gates. G4 activity, G5 verified video and G6 staging,
load/rollback/release evidence also remain open. There is no staging. All V2
flags remain OFF and the complete objective remains active.
