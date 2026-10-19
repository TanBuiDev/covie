# Social Product Card 2.0 — G2 independent command refresh guard

## Confirmed risk

`CatalogEditForm.mutate` refreshes after successful variant, offer and lifecycle
commands. The server page keys the editor by item ETag plus variant/offer
versions. Therefore an independent successful command can remount the editor,
discarding a separate unsaved caption/gallery even when item ETag is unchanged.
This is grounded in current source, not a hypothetical route change.

`.local/social-v2-g2-refresh-guard-red.log` reproduces two missing confirmations:
dirty caption followed by an offer command, and gallery-only edits followed by
variant/lifecycle commands.

## Minimal change

- Exported a confirmation helper from the existing shared dirty-source registry.
  It does not duplicate caption/gallery dirty inference and prompts once, only
  when a registered Social draft exists.
- With Social authoring enabled, independent commands now ask **before** the
  mutation/refresh. Cancellation leaves API calls, router and draft untouched.
  Approval permits the existing command with its existing resource version.
- Item-info submission explicitly identifies itself as the Social draft save.
  It does not ask the unrelated-command discard question, preserving normal
  caption/gallery persistence, validation, failure and conflict behavior.
- Explicit stale-data reload also asks before refresh. It remains available
  after a version conflict, but the user can first cancel and retain the draft.
- Default-OFF paths, server permissions, business transitions and request
  payloads are unchanged. No history/Next commit cancellation was introduced.

The warning describes a data reload and possible unsaved caption/image loss,
rather than claiming an offer command necessarily leaves the page. No visual
layout changed or new Figma evidence was required/claimed in this checkpoint.

## Verification

- Initial focused post-fix result: **2 passes, 4.03 s** in
  `.local/social-v2-g2-refresh-guard-green.log`.
- The first related regression run found an existing enabled-caption conflict
  test clicking reload without confirming. It now explicitly approves and checks
  that one confirmation occurred. The production guard was not weakened to keep
  that old expectation.
- Final `.local/social-v2-g2-refresh-guard-final-green.log`: **35 tests pass,
  5.65 s**, zero failures. Includes three targeted command/refresh cases plus
  existing Edit, dirty-link/traversal and caption controls:
  - Reject offer command: no API/refresh and exact caption retained. Approve:
    original offer command/refresh executes. Save the caption afterward: no
    additional discard prompt and exact caption PATCH preserved.
  - Gallery-only dirty state rejects variant and Publish before either API call.
  - Main caption PATCH conflict retains text. Reject explicit reload: no refresh;
    approve reload: one refresh. Main save itself did not ask to discard.
- `.local/social-v2-g2-refresh-guard-typecheck.log`: web typecheck exits zero.
- `.local/social-v2-g2-refresh-guard-lint.log`: targeted lint exits zero, zero
  errors and one existing legacy image-element warning. No unrelated cleanup.

These use mocked API/router confirmation and the actual component/registry. They
do not demonstrate a native dialog or a real streamed Next refresh/remount. No
full BE/web suite, unchanged API/client check, recovered-data write, environment
edit, service restart, feature activation, commit or deploy occurred.

## Remaining objective

Navigation safety now covers the editor's identified independent mutation/reload
paths, in addition to links and supported native traversal. Native unsupported/
noncancelable navigation and cross-browser/real Next Seller acceptance are not
declared complete. Other G1/G2/G3 visual, concurrency and storage/visibility
gates remain open. G4 activity backend/UI is still unimplemented; G5 verified
video and G6 staging/release evidence remain unfinished. No staging exists.
Flags remain OFF and the full goal remains active. Next work is the approved
G4 current-state private activity contract/source audit, without repeating
unchanged successful gates.
