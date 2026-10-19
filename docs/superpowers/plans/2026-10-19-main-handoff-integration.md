# Main handoff integration implementation plan

**Execution status2026-10-19:** Source integration and original branch retirement
completed through PR39; the concrete completion receipt and verification limits are
in `docs/operations/main-handoff-2026-10-19.md`. The unchecked items below are the
original execution checkpoint snapshot, not outstanding implementation instructions.
Final documentation-only receipt merge/branch retirement is recorded on its PR.

> For agentic workers: execute this approved handoff in isolated worktrees with verified backups and review checkpoints.

**Goal:** Publish only reviewed project sources, integrate approved functionality into main, and retire non-main branches without losing private or unpublished work.

**Architecture:** Preserve the existing dirty Social and Billing checkouts. Assemble reviewed source snapshots on an isolated integration branch, reconcile actual conflicts, test the combined application, scan the exact publication set, then update GitHub and main without force pushes. Financial activation and deployment remain out of scope.

**Tech Stack:** Git/GitHub, Java25/Spring/Flyway/PostgreSQL18, pnpm/Next.js, Gitleaks/Trivy.

## Chunk 1: Preservation and integration

- [x] Inventory remote heads, all PRs, eight worktrees and both stashes using read-only operations.
- [x] Save all changed/untracked files, staged/unstaged binary patches, detached-head archive refs and a verified all-ref bundle in an ACL-restricted directory outside the repository. Validate each copied file SHA256.
- [x] Fetch origin only after preservation. Main and Billing remain at the reviewed tips.
- [x] Read scoped instructions and copy the171 reviewed Billing changes onto its remote base in the isolated integration branch. Do not stage private runtime evidence or generated keys.
- [x] Integrate the384 Social checkout changes by three-way comparison against main. Combine14 shared modified paths; regenerate the merged API contract rather than selecting one generated artifact.
- [x] Add `vn/covie/database/MigrationLineageConfiguration.java` as a Spring Flyway customizer. Read the configured schema/history using the configured datasource; exact successful V020 Billing selects the canonical tree, exact successful V020 Social selects the Social tree, and pre-V020/fresh uses canonical. Reject failed/unknown history and SQL errors; retain full validate-on-migrate, never repair or baseline.
- [x] Keep canonical `db/migration` common001–019/Billing020–034 unchanged; copy Social SQL into035–040, including V037's nontransactional companion. Add `db/migration-social` with exact common001–019/Social020–025 and byte-identical Billing copies026–040. Both trees include shared future migrations under `db/migration/future`; canonical scanning is recursive. Future version numbers start041.
- [x] Add `MigrationLineageIntegrationTest`: fresh/common019, partialBilling020, populatedBilling034, populatedSocial025, unknown020, and changed checksum. Compare old business rows and history, schema columns/indexes/constraints, and zero migrations on retry. Keep the old Social populated progression in `SocialPopulatedMigrationUpgradeTest`; combine both backup fixtures in `PostgresBackupRestoreTest`. Only disposable PostgreSQL18 is allowed.
- [x] Run `./mvnw.cmd -o -Dtest=MigrationLineageIntegrationTest,PopulatedMigrationUpgradeTest,SocialPopulatedMigrationUpgradeTest,PostgresBackupRestoreTest test` with Java25 and the existing Maven cache; require zero failures. Document exact lineage mapping and fail-closed upgrade behavior in `docs/operations/migration-lineages.md`.
- [x] Record and verify each source decision and migration lineage before committing or pushing. Existing recovered databases and normal stacks remain untouched.

## Chunk 2: Verification and publication

- [x] Run focused combined migration/schema/data-preservation tests first, then affected API/UI checks; fix only demonstrated integration failures.
- [x] Review PR30's identical plan blob as superseded by PR31. Review PR32–38 individually, including major TypeScript/Actions compatibility, exact source diff and required CI. Merge only supported updates; document specific reasons for any retirement.
- [ ] Run the combined backend full verification once source is stable, official OpenAPI export/client drift checks, Web tests/typecheck/lint/build and current Billing/Social browser smoke. Retain unchanged scoped proofs instead of claiming old artifact evidence covers new source.
- [ ] Inspect an explicit publication allowlist. Exclude local machine metadata, secrets, data dumps/demo databases, logs, screenshots and test output. Run default secret detection on current sources and staged changes; review each finding narrowly without disabling detectors. Validate production dependency/artifact scans.
- [ ] Commit only the reviewed allowlist, publish a reviewable integration PR and attach it. Keep normal Social/video/money/reconciliation/enforcement flags OFF. Wait for required checks; do not bypass branch protections or force-push.
- [ ] Merge approved PRs into main. Refresh remote state before each merge; do not overwrite concurrent changes. Record exact resulting main SHA.

## Chunk 3: Retirement and local synchronization

- [ ] Preserve demo41 branch-only commits and its4 dirty files privately; do not import the old200-file divergent implementation or compressed seed into main. Record retirement rationale and recovery receipt.
- [ ] Verify each non-main remote branch is merged or explicitly retired with a recoverable snapshot, then close superseded PRs and remove only those exact branches. Keep refs/archive and private backups; they are not active branches.
- [ ] Synchronize the primary checkout to final main only after its unpublished source has been integrated or separately retained and its private files/data are proven preserved. Never use an indiscriminate clean/reset.
- [ ] Remove local non-main branch refs only after preserving attached worktree state and detaching any owned branch checkout safely. Do not remove unrelated/ignored runtime data or task-owned worktrees merely to reduce branch counts.
- [ ] Verify local and remote have main as their sole active branch, main SHAs match, PR disposition is complete and secret/data exclusions hold. Report remaining preserved private files honestly; do not equate a clean branch list with production readiness.

Owner approved this sequence on2026-10-19. Progress and concrete receipts are recorded separately; a checked task requires actual evidence, not intent.
