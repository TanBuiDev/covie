# Social Product Card 2.0 — G2 actual Next navigation checkpoint

## Verification-only scope

Added one opt-in `CatalogSocialNavigationBrowserIntegrationTest` with durable
`apps/web/e2e/catalog-social-navigation-browser.mjs`. Existing application code
passed this case unchanged. No backend/frontend behavior repair or refactor is
justified by these post-hydration results.

The test uses a real issued OWNER JWT HttpOnly cookie, actual Next dev/webpack,
random-port API and disposable PostgreSQL. It seeds one DRAFT item with a stored
caption and offer. It does not repeat the already-passing upload/Create chain
or require MinIO: a selected PNG stays a local unsaved File/Blob only.
Browser HTTP/HMR forwarding is confined to this context's localhost:3000 origin
and the isolated Next listener. No API body or navigation event is fabricated.

## Actual browser outcomes

In Chrome 155.0.8059.40, after the real Editor is hydrated:

1. Change caption and select a gallery file. Decline the real Next back-link's
   confirmation: URL, caption/newline and unsaved gallery stay unchanged.
2. Invoke browser history Back. Decline its confirmation: the same-document
   traversal is cancelled and the draft remains.
3. Accept Back: reach the real catalog page. Forward returns to Edit with the
   persisted server caption and no discarded gallery draft.
4. Modify caption again. Decline the native reload beforeunload dialog: local
   text remains. Accept the next reload: real SSR/client loading restores the
   stored caption, not unsaved text.
5. Change caption and gallery, then undo only caption. The remaining gallery
   still causes a link warning; decline retains it. Remove the last dirty file:
   the clean back-link proceeds without another dialog.

Observed six dialogs: four confirm and two beforeunload, with the expected
accept/decline decisions. Actual Navigation API records contain trusted,
cancelable, same-document traverse events, including defaultPrevented=true for
the declined Back and false for accepted Back/Forward. These are real browser
history/reload APIs, not physical toolbar testing or cross-browser evidence.

No write request was observed. Canonical JSONB snapshots of every column in
catalog_items, variants, location_variants and catalog_item_media are unchanged;
the owner has zero media assets, proving file selection did not start an upload.

## Results and harness corrections

`.local/social-v2-g2-navigation-browser.log`: **1 pass, 59.448 s Maven total**,
test elapsed **56.05 s**, no failures/errors/skips. Final evidence JSON, redacted
browser/Next logs and screenshot are in `.local/social-v2-g2-navigation-browser/`.
Targeted browser-helper ESLint passes in the matching `-lint.log`.

Initial harness attempts required corrections, not product changes:

- The item name also appears in the shared preview; the route heading selector
  now requires level 1 instead of matching both h1 and h3.
- An early microtask observer sampled the cancellation flag before the guard's
  later callback. A post-dispatch task records its final value; actual URL/draft
  cancellation had already worked.
- SSR text alone was not proof of interactivity after reload. The final case
  waits for the category fieldset populated by the Editor's real client effect
  before typing. An earlier fast-automation attempt did not retain interactions
  performed before hydration; this case **does not establish protection of early
  input before JavaScript/hydration is ready**. A controlled timing probe remains
  the next investigation, not an assumed product fix or a waived gate.

The 360px Edit caption screenshot was visually inspected with the full legend,
input, counter and clear/revert controls in frame. Capture asserts the exact Edit
URL and the legend/input in the viewport. This improves the earlier mobile
framing evidence for the shared field; it does not retroactively replace the
partial Create capture or claim complete mobile interaction acceptance.

Relevant tested-copy hashes match the worktree navigation hook, Edit form,
caption field and gallery. No unchanged TypeScript/client generation, old unit
suite, upload chain or broad BE suite was repeated. Existing image sizing
warnings can appear; no uncaught page error was recorded.

## Isolation and remaining gates

Owned web/browser children stop and the fixture token manifest is deleted in
finally. Normal 3000/8081 processes retain their original PIDs. No recovered DB
read/write, runtime migration/restart, normal V2 flag activation, commit, remote
change or deployment occurred. Only the isolated fixture authoring flag is ON.

Chrome post-hydration Next link/Back/Forward/reload behavior is now evidenced.
Pre-hydration input, unsupported/noncancelable traversal paths, Firefox/Safari,
independent-command browser checks, remaining G1/G3/privacy/load and actual
staging/release gates remain open. No staging exists; all normal V2/video flags
stay OFF and the full objective remains active.

From the isolated API copy, after preparing the source-only isolated web copy:

```powershell
.\mvnw.cmd -o '-Dtest=CatalogSocialNavigationBrowserIntegrationTest' '-Dcovie.test.social-navigation-browser=true' '-Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web' '-Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/catalog-social-navigation-browser.mjs' '-Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-g2-navigation-browser' test
```
