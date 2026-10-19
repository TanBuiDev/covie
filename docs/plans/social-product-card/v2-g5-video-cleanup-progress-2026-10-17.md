# G5 — retryable video object cleanup

Date:2026-10-17. Whole goalACTIVE; staging waiver retained. Previous turn progressed bounded download. This checkpoint implements cleanup, not fullG5/project acceptance. Normal media/video flagsOFF; no normal runtime started.

## Implementation

- New forward-onlyV025 adds nullable `storage_cleanup_attempted_at` and `storage_deleted_at`; applied migrationsV023/V024 untouched. Media rows/metadata retained, not physically removed. Old image/Ads cleanup behavior unchanged.
- `CatalogVideoCleanupService` claims only expiredCATALOG_VIDEO with no successful-delete bookmark and no catalog association, plusREADY verified older than the same cutoff. Uses existing technical expiredRecordGrace (source default7days), not a new business lifetime. Still-live presigned uploads and recently verified media are protected.
- Bounded passes claim one row per short independentREAD_COMMITTED transaction withFORUPDATE SKIP LOCKED. Fresh reference check after acquiring the asset lock precedes tombstoning. New attachment already takes that same asset lock and rechecksREADY/undeleted state. No storage network call holds the claim transaction/DB connection.
- Before deletion commitDELETED/attempt timestamp; readers and new attachments fail closed. Lease is twice configureddelete timeout (default20seconds→40seconds), prevents immediate concurrent retries. S3 deletion runs outside the transaction with finite SDK call/attempt timeouts; only matching attempt token can record successful deletion. Failure retains tombstone/lease for later retry, stops the current pass with a sanitized503; persisted success excludes future sweeps. No object keys/credentials in job logs or metric labels.
- Scheduler reuses existing technical-retention enabled/batch/initial-delay/interval controls; cleanup is not gated by public video activation, so historical expired orphans can still be cleaned after publication is disabled. Separate finite scheduler metric label `catalog-video-cleanup`, object-delete counter dataset constant. Existing technical retention success gauge is not repurposed. No normal job activation/deployment performed.
- Additive duration-bearingObjectStorage delete capability fails closed for unsupported adapters. Existing delete signature unchanged; actualS3 adapter uses configured bucket/key and SDK timeouts.

## Verification

- `.local/social-v2-g5-cleanup-red.log`: compileRED for missing service/delete overload, not three executed failures. First implementation realPG/API/issuedJWT fixture3PASS22.49s/35.821sMaven, `...cleanup-green.log`.
- The candidate scan'sNOT EXISTS cannot alone be treated as a fresh post-lock reference check. This is an implementation inference from [PostgreSQL18 snapshot semantics](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED), not a claimed reproduced narrow MVCC schedule. New mock-selector unit deliberately supplies a stale candidate with an existing reference: actualRED cleanup1vs0 (`...cleanup-claim-red.log`), then separate fresh reference read/explicit transaction settings fix it. Unit does not masquerade as a real concurrent selector test.
- `.local/social-v2-g5-cleanup-final.log`:9PASS/1FAIL, not a green aggregate. Passed: updated realPG/API cleanup3(18.53s), stale-candidate/disabled-scheduler guard1(.924s), existing image cleanup2(2.931s), actualMinIO deadline-delete1(1.518s), two other scheduler metric cases. Sole failure was existing hardcoded five-job expectation after adding the sixth finite job. Test-only5→6 correction; reran only failed case, `...cleanup-metrics-final.log`:1PASS. No unchanged passing cases replayed after this fix.
- ActualPG cases: bounded2+1 cleanups across oldPENDING/FAILED/READY-orphan; full protected assetJSONB unchanged for attached/recent/live-presign/image; no repeated completed deletion. Failed storage deletion is mocked, persists retry state; immediate retry excluded, fixture explicitly backdates lease to simulate expiry, successful retry excludes further work.
- Actual issuedSellerPATCH races both orders: independent itemNOKEYUPDATE blocker lets real PATCH hold video asset lock and visibly wait inpg_stat_activity; GC skips it, release→PATCH204/link survives/subsequentGC0. GC-first commits tombstone then holds mockedS3 delete; another sweep0, actualPATCH422/no link/full itemJSONB unchanged, release→GC success1. No token logging. This proves those schedules, not every possible interleaving.
- ActualMinIO case writes one owned object, zero-timeout refusal preserves it, deadline delete yieldsHEAD404, repeated delete remains idempotent. Not live cloud/CDN/version-history erasure proof.
- `...cleanup-migration.log`: actual populatedV024→V025 sample1PASS5.480s/16.847sMaven, three legacyREADY image/AD-video/catalog-video rows' complete oldJSONB unchanged; both new columnsNULL. Small sample, not volume migration/lock-load proof. V025 is now applied/immutable.

Twelve source/test/migration hashes match validation copy, `.local/social-v2-g5-cleanup-source-check.json`; scoped whitespacePASS. Owned containers gone, normal3000/8081 absent/untouched. No fullsuite, old upload/browser/probe/range reruns, existing/recoveredDB, normal restart, remoteGit/CI/commit/push, public activation/deploy, REST/generated contract change or image build. No speculative index added; query scale acceptance remains separate.

## Next work

Actual current runtime still lacks confirmedffprobe/ffmpeg packaging; hostPATH lookup finds neither. Prior tool fixture image is not current production application. Package tools in the realAPI image, validate exact source/runtime, then complete actual Seller upload/verification/mixed save/read/playback/moderation/cleanup with API/PG/S3/probe/browser, necessary query/performance and finalG0–G6 local acceptance/build audit. No new approval/staging request.

Next action: Package and verify real probe tools in the current API runtime, then run the complete real video workflow.
Required from you: Nothing
Completion condition: Current application image executes real probing and the integrated video pipeline is verified; continue remaining whole-plan acceptance afterward.
