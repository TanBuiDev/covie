# Main handoff — 2026-10-19

Status: IN PROGRESS. Owner approved source publication, reviewed integration and
branch retirement after the read-only inventory. This is not deployment or payment
activation approval.

## Preservation

All eight original worktree heads and both stashes are recoverable in a verified
private Git bundle. Each changed/untracked file was separately copied with matching
SHA256, and staged/unstaged binary patches were retained. The backup is outside the
repository with restricted filesystem permissions; no backup, keys, databases or
runtime evidence are publication candidates. Original dirty worktrees remain intact.

The primary main checkout contained384 changed/untracked paths, isolated Billing171,
demo4, and the old PR22 review one private debugging file. The integration worktree
is separate. Billing's170 non-metadata files were transferred exactly; two automatic
line-ending conversions were restored before hash comparison. Its local acceptance
commit is c4e8d2a8; the existing remote Billing tip is41abfce0.

## Integration decisions

- Both approved Social and Billing functionality are retained. Generated contracts
  are regenerated from the combined controllers, not overwritten by one snapshot.
- Four actual three-way conflicts were identified: CatalogEditService, populated
  migration test, backup/restore test and lockfile. Retain Billing enforcement and
  Social moderation/media behavior; combine backup fixtures and preserve the old
  Social migration test separately. Resolve lockfile from combined package inputs.
- Applied migration histories collide at V020–025. Preserve both exact histories
  using the validated lineage strategy in `migration-lineages.md`, not repair/reset.
  One PostgreSQL18 lineage acceptance test passed38.97s (Maven58.178s): five initial
  stages, equivalent combined schema, unchanged old rows/history, repeat migrate0,
  and unknown/checksum-drift refusal with no new writes.
- The first broader migration run exposed a real concurrent-index advisory-lock
  wait in direct test Flyway calls. Disposable PostgreSQL showed CREATE INDEX waiting
  on virtualxid while another connection retained the migration transaction. Align
  these direct calls with the application's existing transactional-lock=false;
  preserve the nontransactional SQL and companion. An interrupted run is not PASS.

## Pull request disposition

| PR | Reviewed scope | Disposition |
| --- | --- | --- |
|30 | Original Billing plan; blob identical to31 | Close as superseded after integration retains that exact document |
|31 |125 commits plus verified local Billing fixes | Integrate all reviewed source; current remote draft alone is not final acceptance |
|32 | AWS SDK BOM2.55.12→2.56.0 | Candidate; require combined storage/media/payment backend verification |
|33 | upload-artifact4→7 | Candidate; validate generated-contract artifact workflow |
|34/37 | Next16.4.0→16.4.1 and eslint-config-next16.3.8→16.4.1 | Review together; require combined typecheck/lint/test/build |
|35 | TypeScript5.9→7.0.2 | Retire: actual PR frontend job fails in openapi-typescript7.13.0 with undefined ts.factory.createKeywordTypeNode; do not rewrite client tooling merely for branch cleanup |
|36 | Playwright1.62.1→1.64.1 | Candidate; require current native browser checks |
|38 | Zod4.6.5→4.6.7 | Candidate; require combined form/API client checks |

PR32–38's backend/release-source checks passed on their original candidates;35's
frontend failed. All seven had historical secret-scan failures on the older main
configuration; this is not permission to bypass scanning. The integration inherits
Billing's already reviewed commit-specific documentation fingerprints. An actual
default Gitleaks history scan across HEAD and current remote heads passed959commits,
20.04MB with0 findings. No detector or ignore configuration was weakened here.

## Demo and data

The demo branch has41 branch-only commits and an obsolete200-file divergent tree,
including incompatible historical migration naming. Preserve it and its four dirty
files privately; do not merge the old implementation or compressed seed into main.
Retiring a branch is not deleting its data or private recovery archive.

## Remaining gates

Combined migration/backup/contract checks passed11/11. The separate lineage test
passed1/1. Current Web typecheck/build passed (Next16.4.1,39 routes); lint has0 errors
and28 existing warnings. The initial default16-worker Web run had1 intermittent
unchanged Ads assertion failure; a second had2 UI wait timeouts. The unchanged Ads
component/test match main exactly and passed5 focused tests; the two timeout suites
passed47 focused tests. A full resource-bounded run (maxWorkers2, unchanged assertions
and timeouts) passed685, with12 Redis opt-in skips, in352.85s. A separate durable,
non-evicting Redis fixture passed those12 tests in47.30s. These are distinct runs.

The first combined backend full verify executed1176 reported cases in14m03s:
1157 passed,4 failed,15 skipped,0 errors. It is NOT a green full run. Failures were:

- Billing populated-upgrade snapshots compared new nullable Social columns with old
  row JSON. Project only the known original fields; retain all business values.
- Social Edit/Hide asserted PostgreSQL lock wake-up order. Both observed real outcomes
  were atomic but Seller won both runs. A bounded test-only Hibernate statement gate
  now controls the second stale UPDATE while the first real UPDATE waits in PostgreSQL;
  both real HTTP winner branches and all existing rollback/audit assertions remain.
- One Order cancellation/reception repeat asserted publication after200 immediate
  processOne calls while schedulers may also own SKIP LOCKED events. Await the exact
  fixture's published state with a15-second deadline and short pauses when no work is
  acquired. A201-event backlog-only experiment passed and did NOT reproduce the
  failure; that speculative additional case was removed. No production dispatch change.

All three corrected test classes then passed17/17 (including both migration stages,
both controlled Edit/Hide outcomes and14 order cases); Maven package passed49.262s.
Do not add this result to the failed full run and label the aggregate a green verify.
The current source still requires fresh GitHub full backend/contract/security checks
before main merge; another unchanged local full backend run is not needed for these
test-only corrections. Current package boot/read and library vulnerability checks
are being completed independently.

The six supported dependency PR heads are integrated locally with explicit merge
commits. Preserve Billing's Jackson/Tomcat security versions and removal of the old
Netty override when resolving the AWS BOM conflict; preserve QR-code dependencies in
the Next conflict. TypeScript remains5.9 and PR35 was actually closed without merge
after its incompatibility comment. No ignore/detector configuration was changed.

Current production dependency audit reports0 findings at every severity. The full
development tree has1 high Braces advisory and0 critical; production exposure is not
claimed for this dev-only finding and it is not hidden. The configured source gate's
dev threshold remains critical. Actual Trivy0.72 source misconfiguration/secret scan
with network disabled passed0 high/critical findings, using the existing configured
Dockerfile.dockerignore exclusion (these are ignore files, not Dockerfiles). Actual
Gitleaks history scan passed960 commits/22.23MB/0 findings using unchanged reviewed
historical fingerprints. Raw current-source Gitleaks reports7 reviewed unchanged
documentation matches,0 unresolved; it is not relabeled as raw0.

Publication, required GitHub checks, merge, branch deletion and primary synchronization
remain uncompleted. Do not infer them from isolated or candidate local acceptance.

Normal Social/video/money/release/reconciliation/enforcement flags remain OFF.
Existing/recovered data and normal stacks have not been started, reset or migrated.
