# G2: early Social input before hydration

## Evidence and minimal correction

The preceding native-navigation run established post-hydration behavior only.
A separate delayed-JavaScript probe reproduced actual draft loss in both Seller
Create and Edit, using the real isolated Next/API, issued OWNER cookie and
disposable PostgreSQL. Five real Next JavaScript responses were held per page;
the SSR caption accepted trusted input while the React state was not ready.
After releasing JavaScript and allowing a client rerender, Edit reverted to
`Persisted caption`, Create reverted to empty, and neither registered dirty state.
There were no browser API writes or uncaught page errors.

RED evidence: `.local/social-v2-g2-hydration-browser-red/evidence.json` and
`.local/social-v2-g2-hydration-browser-red.log` (one expected browser assertion
failure). This is a reproduced client readiness defect, not a backend defect.

Only `catalog-create-form.tsx` and `catalog-edit-form.tsx` production code changed:
reuse the project's existing `useSyncExternalStore` hydration pattern, supplying
false for the server snapshot and true for the client snapshot. While Social
authoring is ON but not hydrated, existing disabled props protect the caption,
gallery and own form submit button. After hydration the existing pending/stale
and submission rules resume. Flag-OFF behavior, permissions, API contract,
commerce fields, moderation and backend code remain unchanged.

## Targeted verification

- Browser GREEN: one test, 66.08 seconds, Maven 01:09, exit 0, in
  `.local/social-v2-g2-hydration-browser-green.log`.
- Both forms have caption/gallery/submit disabled in SSR with the five scripts
  still held. After scripts load, initial caption values are intact, actual
  typing registers caption dirty state and selecting a local file registers
  gallery dirty state. Browser writes and page errors are empty.
- Evidence: `.local/social-v2-g2-hydration-browser-green/evidence.json`.
- The existing `CatalogSocialNavigationBrowserIntegrationTest` fixture was reused
  with the NEW `e2e/catalog-social-hydration-browser.mjs` script override. This run
  is hydration evidence, not another execution of the previous native navigation
  sequence. Its final all-column JSONB snapshots of catalog items, variants,
  location variants and gallery links are unchanged, with no owner media created.
- Create/Edit component regression: 39 tests in two files PASS, 8.78 seconds,
  `.local/social-v2-g2-hydration-forms.log`.
- Web typecheck PASS; targeted lint has zero errors and three existing legacy
  image warnings. Logs: `.local/social-v2-g2-hydration-typecheck.log` and
  `.local/social-v2-g2-hydration-lint.log`.
- Both tested-copy form hashes match their worktree sources. Owned processes
  stop and the token manifest is deleted; normal web/API PIDs remain unchanged.

No broad BE suite, old upload/navigation sequence, generated-client operation,
recovered-data access, normal runtime restart, migration, flag activation,
remote change or deployment was performed. The test child alone enabled Social
authoring. No staging exists; normal V2/video flags remain OFF.

## Remaining scope

This verifies the two flagged Social controls and form submit readiness in local
Chrome 155; it is not protection of all legacy commerce controls, cross-browser
acceptance, actual production asset timing, full G2 completion or release proof.
Independent-command browser confirmation/refresh and other outstanding G1/G3
privacy, concurrency, load and staging gates remain open. Figma matching is no
longer required per the owner's explicit direction; current project UI is the
reference. The whole Social V2 objective remains active.

Next action: Verify independent Seller command refresh against a dirty Social draft in the real isolated browser.
Required from you: Nothing
Completion condition: Cancel preserves the draft without a command write; accept uses the existing command and authoritative refresh.
