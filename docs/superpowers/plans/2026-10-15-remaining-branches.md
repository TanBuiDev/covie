# Remaining Branches Review and Handling Plan

> **For agentic workers:** Use the executing-plans skill when implementation is requested. This document authorizes no merge, deletion, commit or deployment by itself. Review concrete results at each checkpoint.

**Goal:** Reduce stale local/remote branches without losing unpublished work or reintroducing obsolete behavior into `main`.

**Architecture:** Treat current `origin/main` as the integration baseline. Preserve unique history and dirty worktrees first; classify individual changes as retained, superseded, deferred or deliberately retired before deleting their branch references.

**Tech Stack:** Git, GitHub PR metadata, Maven, pnpm, PostgreSQL/Flyway.

## Execution status — 2026-10-15

User authorized execution after reviewing this plan.

- [x] Task 1: verified complete-history bundle, actual restore into a bare repository,
  object connectivity and 28 file backups; staged/unstaged patches captured.
- [x] Task 2: retired four remote temporary/duplicate pointers and the duplicate
  local banner-template pointer using recorded SHAs.
- [x] Task 3: preserved optional Social report UX/banner gallery in the archive;
  reconciled the two database/demo policy documents; retired their source refs.
- [x] Task 4: mapped capability replacements and genuinely unintegrated historical
  features, preserved both unpublished local commits, and retired remaining remote
  and obsolete local integration/banner refs. No old migration or application code
  was merged. Deferred features are explicitly recorded, not claimed complete.
- [x] Worktree disposition: retain all existing worktrees. The demo branch remains
  active because it has four dirty files and ignored forensic data; its worktree
  is not removed. Detached review worktrees remain available to their owners.

Current outcome: one remote branch (`main`) and two local branches (`main`, active
`codex/demo-seed-regeneration`). Detailed evidence, deferred capabilities and
recovery instructions: `docs/operations/branch-retirement-2026-10-15.md`.
The detailed steps below preserve the original proposed workflow. This execution
status and retirement ledger supersede that checklist where content was deferred
or a worktree was deliberately retained rather than removed.

## Chunk 1: Evidence and branch decisions

### Baseline

- Reviewed on 2026-10-15 against `origin/main` / local `main` at `803b25b89f4686b4135b0de5ef101aabcc84b614` after fetch/prune.
- Remaining: 10 actual remote branches and 5 local branches, including `main` in both counts. `origin/HEAD` is a symbolic reference, not another branch.
- GitHub currently reports no open PRs. PR #6 (integrated UI) and #7 (animated ads) are closed **without merge**; closure is not proof that their changes were integrated.
- The working checkout contains uncommitted social pagination repair, catalog/barcode work and documents; `next-env.d.ts` is also currently modified. Preserve all of this.
- Review is read-only except for creating this plan. No tests were rerun: the task is branch disposition, and no application code changed during this review.
- Previous cleanup reference snapshot: `.local/branch-cleanup-20261015-refs.txt`.

Commit counts below are branch-side commits absent from main's ancestry, not counts of missing features. File counts compare changes introduced since the merge base. Missing paths can have replacement implementations under other names.

| Branch / tip | Evidence | Recommended handling |
| --- | --- | --- |
| `main` / `803b25b8` (local + remote) | Local and remote match; primary checkout has uncommitted work | Keep as baseline; preserve pending work separately from cleanup |
| `codex/plan-10-seller-desktop-ci` / `0b1468ba` (remote) | 1 unique commit; only adds `.github/workflows/seller-desktop-web-verify.yml`, triggered only on this old branch. Main CI already runs web typecheck/lint/test/build | Archive then delete; do not restore a redundant branch-specific workflow |
| `codex/plan-10-seller-mobile-ci` / `ac699ed8` (remote) | 1 unique commit; only adds `docs/seller-mobile-ci-trigger.md`, explicitly a temporary marker with no runtime effect | Archive then delete |
| `feature/ad-banner-templates-only` / `be29612f` (local + remote) | 6 unique commits; all are ancestors of `feature/animated-ad-banner-demo`. The six original demo paths are absent from main, but current `ad-creative.tsx`/CSS implements the four flash/nearby/spotlight/hero variants against real ad data | Consolidate preservation into the animated-ad archive, then delete this duplicate branch; do not infer that the isolated `/banner-demo` page shipped |
| `feature/animated-ad-banner-demo` / `962ad6a1` (local + remote) | 9 unique commits; PR #7 closed without merge. Original preview page remains absent. Main retains the `mkdir -p build` scanner fix, and uses strict generated-contract drift checking instead of this branch's semantic checker | Archive and retire after recording the preview page as deferred/retired. Do not replace current ad API integration or weaken current CI merely to merge this branch |
| `feature/social-product-card-phase3` / `bbe3e421` (remote) | 2 unique commits; 11 introduced paths: 2 identical to main, 9 different, none missing. Main already has comment reporting, moderation, V019, public OpenAPI and a guarded admin comment preview. This branch additionally has report-reason selection and per-comment reported state; main submits SPAM directly | Review these specific UX differences against an approved requirement. Preserve current BE/V019; do not merge the old branch wholesale. If optional differences are retired, archive/delete; if required, port only that FE behavior with focused tests |
| `docs/database-environment-guidance` / `df124e37` (remote) | 6 unique commits; `docs/operations/database-environments.md` and `demo-data.md` absent from main; four other files diverge. Contains environment isolation and destructive-operation guidance | Preserve for a targeted documentation reconciliation. Port only guidance still consistent with current deployment/restore scripts; fix obsolete paths/variables. Do not overwrite current AGENTS/README wholesale |
| `feature/admin-mobile-figma-ui` / `2371fd7f` (remote) | 2 unique commits / 7 introduced files. Three mobile CSS/filter-sheet files absent; four admin files diverge substantially. Main admin shell/tables have subsequent implementations (`297947e3`, `f470f477`, `67e03490`) | Keep pending a mobile UI comparison. Map old drawer/filter/card behaviors to current screens; absence of the old filenames alone is not a defect. Port only confirmed missing required behavior, otherwise archive/retire |
| `demo-data-v1` / `ee5280d0` (remote) | 7 unique commits; six demo documentation/script/data paths absent from main. Every commit is already reachable from remote integrated UI | Consolidate into the integrated-UI/demo archive. Delete the duplicate branch only after the containing archive is verified; do not run the old seed/import commands |
| `codex/integrated-ui-completion` / `d6a89e4e` (remote) | 41 branch-side commits; 200 introduced paths: 3 identical, 154 different, 43 absent from main. PR #6 closed without merge. Some contracts have current replacements: Need eligible-item service, review eligibility and reputation endpoints | Preserve and audit by capability, not full merge. Old `V014__integrated_ui_contracts.sql` creates merchant kind; current `V014__service_fulfillment_distance.sql` uses the same version for a different migration. A direct merge is unsafe |
| `codex/integrated-ui-completion` / `f0261631` (local) | Contains remote tip plus unpublished `a45b5536` (brand assets/review findings) and `f0261631` (WIP address form/tests/docs). Address form differs substantially from current main; commit message explicitly says known findings unresolved | Preserve the two local commits in the archive. Review assets independently; do not cherry-pick the WIP form or old generated types into current main without a proven gap |
| `codex/demo-seed-regeneration` / `d6a89e4e` (local worktree) | Same commit as remote integrated UI; four dirty files: AGENTS, compressed seed, generator, generator test | Keep until dirty work is separately preserved and demo disposition is decided. Archiving the committed tip alone does not preserve these edits |

### Worktree evidence

| Worktree | Current state | Handling |
| --- | --- | --- |
| Primary `E:/WebApp/CoVie` | `main`; dirty | Retain unchanged |
| `.local/demo-seed-regeneration` | Named demo branch; four tracked modifications | Preserve modifications before worktree removal or branch deletion |
| `C:/Users/tanbu/.codex/worktrees/public-security-hotfix/CoVie` | Detached `8df8c516`; clean | Candidate for later cleanup once ownership/need is checked; not part of branch deletion |
| `.local/pr13-review`, `.local/pr16-review`, `.local/pr20-review` | Detached; clean | Candidate for later cleanup once no task uses them |
| `.local/pr22-review` | Detached; untracked `.local-debug-original.txt` | Preserve/inspect ownership before cleanup; do not delete just because its PR is closed |

## Chunk 2: Ordered implementation

### Task 1: Preserve and establish a review ledger

**Artifacts:** `.local/branch-retirement-20261015.bundle`, a branch-to-SHA inventory, and separate backups for dirty/untracked worktrees. Keep archives outside tracked application sources.

- [ ] Refresh refs and PR state. Record exact SHA for every candidate; reclassify a branch if its tip changed.
- [ ] Before bundling, verify each detached worktree HEAD is reachable from a ref that will be archived. If not, preserve it under a local `refs/archive/branch-retirement-20261015/<worktree-name>` ref so `--all` includes it. Do not assume detached worktree HEADs are automatically included; record these refs for recovery and do not publish them.
- [ ] Run `git bundle create .local/branch-retirement-20261015.bundle --all` and `git bundle verify .local/branch-retirement-20261015.bundle`.
- [ ] Use `git bundle list-heads .local/branch-retirement-20261015.bundle` to confirm the 9 remote candidates, 4 non-main local branches and both unpublished local UI commits are recoverable from archived refs/history.
- [ ] Preserve the primary checkout's unstaged and staged tracked changes as separate binary patches. Inventory and copy its untracked files, including barcode components/tests and new documents, into the backup outside tracked application sources. Include this plan itself. Verify files/patches before cleanup; do not rely on the bundle to capture WIP.
- [ ] Preserve demo tracked edits with a binary patch and copies of the four named files. Preserve `.local/pr22-review/.local-debug-original.txt` separately. A bundle stores committed objects only.
- [ ] Check for needed ignored files and running/task-owned worktrees before scheduling their removal. Do not create a commit merely to archive WIP.
- [ ] Record one outcome per distinct capability: keep current implementation, selectively port, defer in archive, or retire deliberately. Do not treat missing path counts as missing-feature counts.

**Checkpoint:** Nothing is deleted until exact refs and unpublished files have a verified recovery path. No application tests are needed for archive creation.

### Task 2: Retire temporary CI branches and duplicate branch pointers

- [ ] After Task 1, delete the two temporary CI remote branches using their observed SHA as deletion leases; current main CI remains the canonical verifier.
- [ ] After verifying containing archives, delete `demo-data-v1` and local/remote `feature/ad-banner-templates-only`, which are already contained in the archived integrated/animated histories.
- [ ] Fetch/prune and verify only the intended refs disappeared. Do not force-delete a branch checked out in any worktree.

Example remote deletion shape (substitute the recorded full SHA):

```powershell
git push origin --force-with-lease=refs/heads/<branch>:<recorded-full-sha> :refs/heads/<branch>
```

**Checkpoint:** Four remote candidate refs can be retired without introducing application code. Duplicate pointer removal does not imply their features are merged into main.

### Task 3: Resolve small independent differences

- [ ] **Social Phase 3:** Decide report-reason/per-comment feedback disposition from approved requirements. If retained, adapt only `apps/web/features/marketplace-view/social-api.ts`, `social-comments.tsx` and their existing focused tests. Keep current moderation/auth APIs, V019 and pagination repair. If retired, record the decision and delete the archived branch.
- [ ] **Animated ads:** Record whether the isolated preview gallery is still wanted. Current four real-data creative variants remain canonical. Do not resurrect the branch's alternative generated-contract checker; main's current strict gate is intentional. Archive/delete when remaining demo artifacts are deferred or retired.
- [ ] **Database guidance:** Reconcile the two missing policy docs with `infra/deploy`, `infra/backup`, current pilot checklist and staging runbook. Copy only current guidance, update links, and review the resulting document diff. No Maven run for documentation-only changes.
- [ ] Retire each source branch only after its specific remaining content has a recorded disposition; recheck remote SHA before deletion.

**Checkpoint:** Any retained behavior must have a focused passing check; no unrelated full backend/frontend run. This task does not authorize adding report UX as new business requirements.

### Task 4: Resolve large historical UI/demo branches last

- [ ] Build a capability map for integrated UI: admin reads, seller catalog/contact, Need eligible items, review/reputation, addresses, demo tooling and brand assets. Identify current replacements before considering ports.
- [ ] Separate the local brand assets commit from the WIP address-form commit. Retain only assets still needed and behavior with a concrete current gap; preserve abandoned WIP in the verified archive.
- [ ] Compare admin mobile drawer/filter/cards with current responsive screens. Only a confirmed gap against existing requirements justifies a port or targeted browser check.
- [ ] Decide whether old demo tooling is wanted. If retained, review its schema/target guards against current V019 before any execution; adapt required pieces rather than restoring stale seed SQL. Never seed/reset the existing recovered database as part of branch cleanup.
- [ ] Never copy or rename the old V014 migration into the applied migration chain. If an approved capability requires a new schema change, design an additive migration using the next available version and focused migration verification at that time.
- [ ] Once capabilities and dirty demo edits are preserved/settled, release the demo worktree using the appropriate managed-worktree lifecycle where applicable, then delete its local branch. Retire both integrated-UI refs after preserving the two unpublished local commits.
- [ ] Final audit: enumerate refs/worktrees, confirm main SHA and pending working files unchanged, verify archives and the disposition ledger. Keep any genuinely active branches with an explicit owner/purpose.

**Checkpoint:** The target is `main` plus genuinely active work, with historical content recoverable in local archives. A small branch count is not justification to merge obsolete implementations or lose uncommitted files.

## Decisions and boundaries

- Proposed first batch: the two CI branches and two duplicate remote pointers, after verified archival.
- Social report UX, isolated banner preview, historical admin UI and demo reuse require requirement/disposition decisions; they are not presumed bugs in main.
- No staging exists per the user's earlier confirmation. This branch review neither provisions staging nor claims deployment acceptance.
- Execution was subsequently authorized and performed. See the execution status
  and retirement record for completed actions. Optional archived features were
  deferred, and the active dirty demo worktree/branch was retained.
