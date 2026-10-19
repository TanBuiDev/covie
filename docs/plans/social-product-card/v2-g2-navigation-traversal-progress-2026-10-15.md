# Social Product Card 2.0 — G2 native traversal checkpoint

## Safe scope derived from code and platform evidence

Current Next `dist/client/components/app-router.js` updates history inside its
commit effect after the router state is already being committed. Cancelling a
late `pushState` event is not evidence that the earlier React transition was
cancelled; it risks a URL/UI mismatch. No history monkey-patch, router replacement
or blanket push/replace listener was added.

The [HTML Navigation specification](https://html.spec.whatwg.org/dev/nav-history-apis.html)
provides a cancellable navigate event for some traversals, with explicit cases
where cancellation is unavailable. It also distinguishes same-document
destinations. This checkpoint adds only a feature-detected listener for
**cancelable same-document `traverse` events to a different route**. It asks the
existing unsaved-draft confirmation and prevents that traversal when rejected.
Push/replace/reload events are deliberately not intercepted. Same-route hash
changes, noncancelable traversal and cross-document destinations are not falsely
treated as cancellable by this guard.

Caption/gallery still share a single registration. The listener is installed
only with the first dirty source and removed after the last source clears.
Existing link and hard-unload warnings remain. Structural compatibility types
avoid assuming the repository's TypeScript DOM definitions include Navigation
API declarations; no global Window type or API DTO was modified.

## Focused verification

- `.local/social-v2-g2-navigation-traverse-red.log`: missing listener reproduced
  an uncancelled traversal before the change.
- `.local/social-v2-g2-navigation-traverse-green.log`: **5 tests pass, 1.02 s**,
  including existing link coordination/cleanup and caption undo/unload. New
  assertions cover cancel/allow, no interception of push/replace, noncancelable
  and cross-document events, and removing the native listener on unmount.
- Initial typecheck identified absent `NavigateEvent`/`window.navigation` DOM
  declarations. After the local structural compatibility adjustment,
  `.local/social-v2-g2-navigation-traverse-typecheck-final.log` and
  `.local/social-v2-g2-navigation-traverse-lint.log` both exit zero.

An isolated native browser experiment also passed in installed **Chrome
155.0.8059.40**. The bundled Playwright headless-shell binary was absent; using
the already installed Chrome channel avoided a download or app restart.

`.local/social-v2-navigation-probe.cjs` intercepts only synthetic
`http://localhost:3000/__social-navigation-probe` HTML. It transpiles the actual
production hook source and uses a minimal React-effect setup/cleanup shim, not
a separately copied guard predicate. Native browser history/navigation execute
normally; `confirm` is deterministic for the experiment.

Observed assertions:

1. After actual input interaction, native Back emits a cancelable same-document
   traversal. Rejecting it keeps the draft URL and exact input value.
2. Allowing Back then Forward changes the native URL normally and retains the
   same-document input value.
3. Effect cleanup removes the guard; subsequent Back adds no confirmation.

Evidence: `.local/social-v2-native-navigation-probe-final.log` and
`.local/social-v2-native-navigation-probe.json`. The fixture is route-fulfilled
inside an isolated browser context, not an application route or recovered-data
request. No backend, runtime environment or database was changed.

## Limits and next work

The native experiment does not render React/Next Seller pages, test native
confirmation UI or establish Firefox/Safari/mobile support. Its effect shim is
explicitly not full React lifecycle evidence; those lifecycle checks use the
separate component tests. Unsupported and forced/noncancelable traversals still
need an honest supported-browser/recovery decision; they are not silently
declared safe. Cross-document navigation retains the existing browser-controlled
unload warning.

Programmatic router transitions remain open. In particular, an independent
offer/variant command can refresh an editor while its separate caption/gallery
draft is dirty. Those actual call sites need targeted investigation and guards
before their actions, not cancellation after Next commits. Successful save of
the very draft must not be blocked as though it were an unrelated transition.

No full suite, BE/client rerun, recovered-data write, original service restart,
feature activation, commit or deploy occurred. Remaining G1/G2/G3 gates and
G4–G6 work stay open. No staging exists; flags stay OFF and the full goal stays
active.
