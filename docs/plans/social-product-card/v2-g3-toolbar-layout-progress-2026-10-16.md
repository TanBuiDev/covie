# G3: measured mobile mode/sort toolbar correction

The preceding gallery touch run exposed compressed `Sắp xếp` text, but did not
justify changing CSS on appearance alone. New focused
`e2e/social-toolbar-layout-browser.mjs` measures the actual label's per-word text
ranges and control bounds on the real `/search` route, using the existing
disposable Next/API/PostgreSQL/MinIO fixture. It does not replay gallery gestures,
the full feed lifecycle, Admin flow or old component suites.

## RED evidence and root cause

At both 360 and 768px in both Social and Catalog modes, the label measured
18.46875×64px. Both `Sắp` and `xếp` each occupied two lines, confirming actual
intra-word splitting rather than a harmless two-word wrap. At 1280/1440px the
same text measured 50.515625×16px. All eight observations and element screenshots
are retained in `.local/social-v2-g3-toolbar-red/`; the targeted assertion fails
in `.local/social-v2-g3-toolbar-red.log` (one expected test failure).

Source: the actual shared mode toggle and sort selector occupy a flex
`.toolbarRight` without wrapping; the sort label can shrink under the inherited
anywhere overflow wrap. No filter, sorting or backend contract defect is inferred.

## Minimal correction and GREEN

Only `features/discovery/search-results.module.css` production code changes:

- Permit `.toolbarRight` to wrap its existing control groups.
- Keep `.sort > span` on one line.

The narrow layout now uses two rows, while desktop remains one row. No React,
filter/pagination logic, generic dropdown, global styles, footer, permissions,
API, schema or generated client changes are made. In particular, the unrelated
768px global footer observation from the previous capture is not refactored here.

Final targeted integration: **one test PASS, 45.45 seconds; Maven 48.467 seconds,
exit 0**, `.local/social-v2-g3-toolbar-green.log`. Across all eight mode/viewport
combinations, the label is 50.515625×16px and every word occupies one line. Mode
and sort button bounds stay inside the toolbar; document width does not exceed
client width. All eight element captures in `.local/social-v2-g3-toolbar-green/`
were visually inspected, with no fixed-header screenshot substitution. They
prove this toolbar's layout, not whole-page responsive/accessibility acceptance.
The existing sort button's other sizing/behavior is unchanged and not claimed
newly accepted by these text-bound measurements.

Browser API writes and uncaught page errors are zero. The reused Java fixture's
all-column JSONB snapshots of items, variants, offers, links and assets remain
unchanged, with reaction rows still zero. Its Java method name still mentions
feed previews; this invocation used the new toolbar-script override only.
Targeted script lint and whitespace checks PASS. The tested CSS hash matches the
worktree. Unchanged TypeScript/client generation, old feature/performance/full BE
tests were not repeated.

Owned child cleanup completes and the absolute fixture-token manifest is absent.
Normal web/API PIDs remain unchanged. No recovered/runtime DB access, normal
restart, runtime migration, flag activation, remote change or deployment occurred.
Only disposable fixture preview flags are ON; normal V2/video flags stay OFF.
No staging exists; the whole Social V2 objective remains active.

## Next backend gate, from actual code

The approved plan explicitly requires Seller caption/media edit versus Admin hide
consistency. Source review finds that `CatalogEditService.updateItem` reads the
item normally with a version precondition and eventually saves/flushed it.
`ModerationCommandService` locks its case, while `CatalogModerationTargetHandler`
reads/mutates the item normally. Catalog items have optimistic versioning; this
does not, by itself, prove a lost update or justify replacing working locks.

The remaining evidence is a concurrent actual issued Seller/Admin JWT command
pair with controlled database contention, valid version inputs, final public
visibility and persisted item/media/case/audit/outbox outcomes. Sequential tests
and the earlier Admin case-opening pair do not prove this different race.

Next action: Build one focused actual-JWT Seller Social edit versus Admin hide concurrency check from these existing transaction paths.
Required from you: Nothing
Completion condition: Committed outcomes and public preview visibility remain consistent, with no leaked new content or partial gallery/audit/outbox writes; fix only a reproduced failure.
