# G3: trusted Chrome touch, vertical scroll and incidental click guard

## Focused actual browser scenario

The existing isolated Next/API/PostgreSQL/MinIO fixture was reused with a NEW
`e2e/social-gallery-touch-browser.mjs` script override. The previous full feed
mode/keyboard/storage-fallback scenario was not rerun. Only fixture setup and
its read-only persistence checks are shared; the Java method name still mentions
feed previews and should not be mistaken for the earlier script's scenario.

Chrome 155 runs with a 360×900 mobile-emulated viewport and touch enabled, actual
issued buyer cookie and seeded real coordinate preference. It reads the real
Social card and image from the actual isolated services. Inputs use
[Chrome DevTools Protocol Input](https://chromedevtools.github.io/devtools-protocol/tot/Input/),
not DOM `dispatchEvent`: all 30 observed touch events have `isTrusted=true`.
This is browser-generated trusted input, not a physical phone/OS touch sensor or
Firefox/Safari test. Local-network permission remains scoped to the disposable
context; the fixture bucket's GetObject permission is not production privacy
evidence.

Observed sequence:

1. Horizontal left swipe advances counter 1/6→2/6. Immediately afterward a
   deliberately sent trusted Chrome mouse click on the actual image link is
   prevented by the existing post-swipe guard. Its final `defaultPrevented` value
   is sampled after event dispatch, and the actual item URL is not opened. This
   tests an incidental-click input, not a claim the swipe automatically generated
   such a click.
2. Horizontal right swipe returns 2/6→1/6 without URL navigation.
3. Mostly vertical upward finger movement (8px horizontal delta) increases real
   `window.scrollY` from 900 to 1055, while gallery stays 1/6 and URL stays on
   search. Vertical scrolling is not replaced by a gallery swipe. This observed
   sequence includes touchend, not touchcancel; cancellation behavior is not
   independently proved here.

No browser API mutation or uncaught page error occurs. The Java fixture verifies
all-column JSONB of items, variants, offers, media links and assets is unchanged,
and no reaction rows were created. Existing gesture/axis/click-guard production
code passed without changes; no bug fix or refactor was justified.

## Result and visual limits

**One test PASS, 48.46 seconds; Maven 51.940 seconds, exit 0**, in
`.local/social-v2-g3-gallery-touch.log`. Redacted evidence:
`.local/social-v2-g3-gallery-touch/evidence.json`. Targeted script lint and
whitespace checks PASS. Tested shared-content and stylesheet hashes match the
worktree. No unchanged BE suite, component suite, client generation/typecheck,
query-plan or previous gallery scenario was repeated.

Four full-page captures at 360/768/1280/1440 were inspected; scroll/client widths
match each viewport in this mobile-emulated context. These are not four physical
device acceptances. Mobile full-page captures also show fixed UI at intermediate
positions after the native scroll sequence, so they are NOT accepted as a fully
faithful unclipped mobile visual gate. The actual touch/scroll/URL assertions
above are separate and passed. Desktop captures show the complete Social cards.

Visual inspection additionally exposes a narrow mobile mode/sort toolbar where
`Sắp xếp` appears compressed into individual characters. Source inspection finds
the actual shared toggle and `.sort` inside nonwrapping `.toolbarRight`; its
rendered text bounds still need an isolated measurement before changing CSS.
The 768px global footer also appears compressed, but that is outside the Social
gallery change and was not refactored as part of this task. Neither observation
is hidden behind a claim that the whole responsive/accessibility gate is done.

## Isolation and remaining scope

The absolute fixture-token manifest is absent after cleanup, owned browser/Next
children stop, and normal web/API PIDs remain unchanged. No recovered/runtime DB
access, runtime migration/restart, normal feature flag activation, remote change
or deployment occurs. Only the isolated API/Next preview flags are ON; normal
V2/video flags stay OFF.

Physical device/cross-browser input, full mobile visual/a11y acceptance, Home/
merchant surfaces, hide/revoke concurrency, batch failure/retry, production
object/CDN privacy, load and staging/release gates remain open. No staging exists;
the whole objective remains active. Current project UI, not Figma matching,
continues as the owner's visual reference.

Next action: Measure the actual mobile mode/sort toolbar text and control bounds before any layout correction.
Required from you: Nothing
Completion condition: Reproduce or rule out the observed compressed label with a focused current-source browser measurement, without rerunning passed gestures.
