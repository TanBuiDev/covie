# Social V2 — recovery snapshot order

Date: 2026-10-16. Previous turn made progress with actual production-profile API boot/health on disposable infrastructure. This turn follows plan12.1.7/12.2/12.4 and the existing recovery runbooks; it does not mark recovery or staging complete.

## Concrete defect

`infra/scripts/run-staging-rehearsal.sh` captured invariant counts before the normal-auth P0 journey, but created the database backup after that journey. The required P0 flow creates Orders and Reviews. A correct backup/restore of the post-P0 database therefore differed from the pre-P0 baseline. Source `docs/operations/staging-rehearsal.md` explicitly requires those mutations, not a read-only/guest substitute.

New focused `infra/scripts/tests/staging-rehearsal-snapshot-gate.sh` copies the actual runner and actual invariant verifier into an owned temporary fixture. Stub psql returns source/restored counts; stub P0 changes Order/Review counts1→2; stub backup and restore preserve exactly that post-P0 state. Other network/security/load/migration/provider hooks are stubs. No live database, Docker, accounts, HTTPS staging or real credentials are involved.

RED exit1 `.local/social-v2-recovery-snapshot-red.log`: unchanged runner rejects the exact restore because baselineorders=1/reviews=1 versus restored2/2. This proves a runner ordering defect, not actual pg_dump data loss or a backend business bug.

## Minimal correction and verification

- Moved the existing snapshot invocation to immediately before the existing backup invocation, after P0 and controlled rate-limit checks. No change to invariant SQL, backup/restore implementation, production protections, command hooks, manifest checks or API/business behavior.
- Updated the staging runbook's sequence. Added the focused gate to the existing production-artifact wrapper; preserved earlier Web/API artifact-gate additions in the dirty tree.
- GREEN exit0 `.local/social-v2-recovery-snapshot-green.log`: exact post-P0 restore now passes and baseline explicitly recordsorders=2/reviews=2. A second branch deliberately restores pre-P0 counts1/1 and must fail the actual verifier without writing a PASS summary. This guards against merely disabling/moving the comparison incorrectly.
- Bash syntax of runner/new test/wrapper and focused whitespace checks PASS. Fixture cleanup is constrained to its resolved owned `.local/staging-rehearsal-snapshot-gate.*` path; no fixture directories remain.

No actual staging/full rehearsal, production-artifact wrapper, old successful tests, API/Web build, Maven/browser suite, existing runtime/recovered DB, normal service restart, remote Git/CI/registry push, activation or deployment. Current API/Web application source and the existing image evidence remain unchanged. Whole V2 remains ACTIVE and normal V2/video flagsOFF.

## Remaining scope

This closes only the demonstrated snapshot-order false failure. Backup checksums/dump contents/actual restored reads and application B→A→B rollback remain separate. The seven legacy count invariants do not themselves prove caption/gallery/reaction/comment preservation; the documented count-only verifier was not expanded or refactored without a specific demonstrated requirement gap. Snapshot-before-backup also does not provide an atomic snapshot during arbitrary concurrent writes; the controlled rehearsal must coordinate its writers. No new whole-system consistency claim is made.

Next action: Prepare one owned disposable Social-data backup/restore check using the canonical backup/restore scripts and independent content comparison, without running the staging wrapper.
Required from you: Nothing
Completion condition: Actual local Social-data preservation evidence, or a concrete script failure, with existing databases and staging claims untouched.
