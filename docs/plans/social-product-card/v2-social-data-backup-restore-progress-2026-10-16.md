# Social V2 — actual disposable Social-data backup/restore

Date: 2026-10-16. Previous turn made progress: reproduced/fixed the staging runner's pre-P0 snapshot/post-P0 backup ordering defect with focused stub RED/GREEN. This checkpoint now executes the real backup/restore scripts against a fresh populated Social fixture; it does not execute the staging wrapper or invent a B→A→B application rollback.

## Safe fixture and actual commands

Ignored helper `.local/social-v2-api-runtime-health-20261016.ps1 -RecoveryHook E:/WebApp/CoVie/.local/social-v2-recovery-hook-20261016.ps1` creates a new isolated network, disposable PostGIS18/3.6 and Redis8 tmpfs containers, generated password/RSA/HMAC/provider placeholders, and uses the unchanged current API image `sha256:43b150d64a54649e98034583110730879132a5efa1d71473287d7b064d3b5b9d`. Bootstrap boots that actual image under `prod` to obtain the real Flyway001–022 schema/history; previous boot/health checks here are fixture setup, not new business/full-suite acceptance. No copied existing database, runtime env, credentials, volume or recovery data.

`.local/social-v2-recovery-seed-20261016.sql` populates only this new DB: separate commercial description and literal multiline Unicode/HTML-looking caption; approved and hidden items; two READY media links inserted opposite their display order; LIKE+SAVE; ACTIVE/HIDDEN/DELETED comments; two resolved cases/actions/audit records and two already-PUBLISHED outbox rows. These are explicit fixture records, not authenticated moderation-command/effect proof or real storage objects.

Executed the actual repository scripts inside the owned PG container with real psql/pg_dump/pg_restore, no executable stubs:

- `infra/backup/postgres-backup.sh`: custom compressed archive, checksum and metadata.
- `infra/backup/verify-dump.sh`: checksum and archive listing verification.
- `infra/backup/postgres-restore.sh`: explicitRESTORE confirmation, a separate `covie_recovery_restored` database created from template0, actual empty-target check and restore.
- `infra/scripts/verify-clean-migration.sh`: explicit expected22, source and restored complete successful sequence1..22.

The helper copies only these whitelisted scripts and fixture SQL; Windows CRLF is normalized to LF for Linux execution, no logic changes. Canonical source SHA256 is recorded and checked unchanged afterward. Generated database password is not printed or saved in the report. Dump/checksum/metadata have actual Linux mode0600; copies of disposable fixture artifacts remain ignored under `.local/`, never published.

## Actual results

First run exits0: `.local/social-v2-recovery-actual.log`. Structured evidence `.local/social-v2-recovery-actual-20261016/recovery/recovery-result.json`; backup/verify/restore/migration and restored API logs are in that directory. BackupSHA256 `961794749ff27117ebb8cbac9e85b5beb5678f9710a07cbc09ec16e4e94cf90f`, highestFlyway022. Existing backup/restore implementation worked; **no production fix, schema change or extra backend test added**.

Independent SQL snapshots compare every row/all columns as ordered JSONB for all73 public tables except the PostGIS-managed static `spatial_ref_sys`. This includes22 Flyway records, both captions/descriptions/items, two media assets/links, two reactions, three comment states, two cases/actions/audit/outbox records and their actual IDs/JSONB/timestamps. Sixteen tables are nonempty; legacy order/review tables are empty in this Social-focused fixture, not populated-commerce recovery proof. Four snapshots have identical SHA256 `97DAF15B8D55BF4CE60331EDBDB88DB5DDAD4E6606D7BBADA2153DD332941013`:

1. Source before backup.
2. Restored DB before restored-API startup.
3. Restored DB after startup and public reads.
4. Source after the complete backup/restore/read sequence.

Thus source rows remain untouched and the restored content exactly matches the populated fixture, not merely seven legacy counts. This is independent evidence; the documented legacy count verifier is preserved unchanged rather than refactored speculatively.

A second container of the same actual production image boots against the restored DB under `prod`, validates22 migrations and starts in12.18s. Only its isolated preview flag is explicitly ON; normal/source-runtime flags remainOFF. No host port is published for this restored container. Real loopback HTTP inside the container proves:

- readinessUP; actual public preview returns exactly the approved item, exact literal multiline caption and media IDs in intended first/second order, omitting the HIDDEN item;
- public comments returns only the same ACTIVE UUID and exact literal multiline body, excluding retained HIDDEN/DELETED rows;
- guest summary haslikes=1/comments=1/liked=false/saved=false and omits the HIDDEN item.

The final full-row snapshots also prove these startup/GET operations introduce no business/auth row changes. API responses/presigned URLs/tokens are not retained; boolean/ID/order/content checks are recorded without media URLs. No real upload/CDN/object bytes, account login, activity authorization or new moderation mutations are claimed.

## Cleanup and remaining gates

All owned source/restored API/PG/Redis containers and network removed; generated env/PEM/Redis config files deleted. Normal3000/8081 and owned38080/39091 absent/untouched. API1116 source hashes still match before/after; web source/build unchanged. No normal service start/restart/stop, recovered data access, Maven/browser/full-suite/old runner regression rerun, remote Git/CI/registry push, activation or deploy.

Local populated Social backup→separate restore→same-image API read is now verified. Not off-host encrypted backup, physical storage-object recovery, populated production migration, sustained-load/RPO/RTO, rollback to a different previously released application digest, actual staging rehearsal or release sign-off. G5 video work and external G5/G6 gates remain in the full objective; normal V2/video flagsOFF, whole objective ACTIVE.

Next action: Reconcile the newly closed local recovery gap with remaining literal G5/G6 prerequisites, separating actionable local work from the confirmed absence of staging/release infrastructure.
Required from you: Nothing
Completion condition: A concrete remaining-work decision without rerunning unchanged successes, inventing prior release digests or claiming staging acceptance.
