# Branch retirement record — 2026-10-15

Baseline: `main` and `origin/main` at
`803b25b89f4686b4135b0de5ef101aabcc84b614`.

Execution follows [the reviewed plan](../superpowers/plans/2026-10-15-remaining-branches.md).
Archive/defer means content is recoverable, not that the feature shipped or was
proved equivalent to the current application. No historical branch was merged.

## Preservation evidence

- Complete history bundle: `.local/branch-retirement-20261015.bundle`.
- SHA-256: `3EA4E855DDCA1399488BD8E6FF09DB6676F8A2CAD91BF63C75DD32C022516CFC`.
- `git bundle verify` passes. All refs were fetched from the bundle into
  `.local/branch-retirement-20261015/restore-check.git`; `git fsck --connectivity-only`
  passes there. The two unpublished integrated-UI commits and all candidate branch
  tips are preserved. Detached review tips have local preservation refs.
- 28 changed/untracked files from the primary, demo and PR22 worktrees were copied
  and checked against SHA-256. Manifest:
  `.local/branch-retirement-20261015/files-manifest.json`.
- Each worktree has separate staged/unstaged binary patches and its initial status
  under `worktree-0` through `worktree-6`. Initial refs/worktrees are also recorded.
- Archives stay local and ignored. Copy them to an operator-controlled backup
  location if protection against loss of this workstation is needed.

## Disposition ledger

| Branch | Outcome and preserved differences |
| --- | --- |
| Two `codex/plan-10-seller-*-ci` branches | Retired temporary CI workflow/marker; current main CI retained |
| `demo-data-v1` | Retired duplicate pointer; its commits remain in the archived integrated history |
| `feature/ad-banner-templates-only` | Retired duplicate pointer; commits contained in the archived animated-ad history |
| `feature/animated-ad-banner-demo` | Archived isolated preview gallery/tests; current real-data ad creatives and strict contract CI retained. Build-directory scanner fix already exists in main |
| `feature/social-product-card-phase3` | Current reporting/moderation/V019 retained. Historical report-reason selector and per-comment submitted state deferred in archive; no new UX requirement inferred |
| `docs/database-environment-guidance` | Selected environment/demo guidance reconciled into current documentation; old AGENTS/README changes not imported wholesale |
| `feature/admin-mobile-figma-ui` | Historical drawer/filter-sheet/mobile-card variants deferred in archive. Current responsive admin implementation retained; no claim of full visual parity |
| Remote `codex/integrated-ui-completion` | Current eligible-item, review/reputation and catalog/contact implementations retained. Historical admin shops/listings/needs read APIs, merchant-kind schema and demo tooling deferred in archive; these are not falsely classified as integrated |
| Local `codex/integrated-ui-completion` | Both extra commits archived: brand assets/review notes and unresolved address WIP. Current referenced branding and address implementation retained |
| Local `codex/demo-seed-regeneration` | Retained active unpublished worktree/branch with four tracked edits and ignored forensic data. No worktree/data deletion |

The historical merchant-kind V014 conflicts with the current service-distance V014.
It was not restored, renamed or applied. No database operation was performed.

## Recovery

Recover a specific archived branch into a new local review branch when needed:

```powershell
git fetch .local/branch-retirement-20261015.bundle refs/remotes/origin/feature/social-product-card-phase3:refs/heads/codex/recovered-social-phase3
```

For unpublished integrated work, use the bundle's
`refs/heads/codex/integrated-ui-completion` rather than its older remote ref.
Create a separate checkout for review; do not apply WIP patches over a different
schema/API version without inspection. File copies and binary patches recover
uncommitted data independently of Git history.

## Execution boundary

Final verification: `git ls-remote --heads origin` returns only `main`; local
branches are `main` and active `codex/demo-seed-regeneration`. Nine remote and
three local historical refs were retired in this execution. All seven worktrees
remain. Main SHA is unchanged. The 27 original files other than the intentionally
updated plan still match their backup hashes; the original plan copy is retained.
Nonempty unstaged patches pass `git apply --reverse --check` against their original
worktrees. Bundle SHA-256 remains unchanged, and `git diff --check` passes.

Cleanup removes historical branch references after verified preservation. It does
not publish application changes, create commits, deploy services, stop running
processes, remove worktrees or claim staging acceptance. Existing pending code
changes remain in the primary checkout. Only reconciliation/retirement documents
are added or updated by this operation.
