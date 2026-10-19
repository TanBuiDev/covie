# G5 — full-object download deadline

Date:2026-10-17. Full goalACTIVE. Previous turn completed targeted remaining media consumers; this turn implements an evidenced verification-transfer bound. Cleanup/runtime/full video-chain/final acceptance remain unfinished. Normal flagsOFF; no staging prerequisite.

## Evidence and implementation

New `S3VideoDownloadDeadlineTest` uses actualS3 SDK against an owned loopbackHTTP server, static disposable signing values, no mocked input stream. HEAD warms the client; GET either stalls before headers or sends one byte then stalls before the remaining fixed-length body. Test's finally releases/stops only its own server/workers, so no background hung transfer remains.

For the RED probe, an additive timeout overload temporarily delegated to the existing three-argument download, leaving its old behavior intact. Both realHTTP cases failed because transfer was still waiting after the1500ms observation window despite the supplied600ms deadline, `.local/social-v2-g5-transfer-deadline-red.log`,2actual assertion failures/0skip. This is not a fabricated socket failure or a skipped test.

Final implementation:
- `ObjectStorage` additive four-argument method fails closed by default; unsupported adapters cannot silently ignore the deadline. Existing three-argument capability remains compatible.
- `S3ObjectStorage` implements the new overload with positiveDuration validation, SDK apiCallTimeout/apiCallAttemptTimeout for request/header stages and an owned scheduled deadline that aborts the exactResponseInputStream during body transfer. A shared atomic expiry check also rejects a late-returned/completed stream. Timer cancelled/shut down after success/failure. No background download worker that can continue writing after return.
- Reuses existing exact-size/8192-buffer/CREATE_NEW/abort/owned-partial cleanup logic. Existing unbounded-signature behavior is preserved for unrelated callers, rather than globally changing image/Ads settings or range delivery.
- `CatalogVideoUploadPolicy` now exclusively uses the deadline overload with operational config `covie.social-v2.video-download-timeout`, defaultISO `PT20S`; validates positive/nanosecond-representable values. Existing semaphore/temp workspace/503 retryability remain unchanged. This is separate from the existing shared subprocess deadline, not a new business media limit or an approved latencySLO.
- Existing test storage stubs updated to the new duration-bearing signature, and direct policy constructors supply the new config argument. No API/schema/client/migration changes.

## Focused green verification

`.local/social-v2-g5-transfer-deadline-green.log`:15PASS/0skip,43.787sMaven:
- Two actualHTTP stalled-header/body cases,1.318s combined: future terminates exceptionally within the observation bound and no owned partial file remains.
- Existing bounded-download five cases,0.519s: old signature exact bytes, overlong/truncated rejection and existing-file preservation remain correct after the shared implementation change.
- Existing actualMinIO method now uses a5second deadline for the successful20000-byte full download,1.356s; its wrong-size checks remain. CachedMinIO digest unchanged. No old presign/range suite replay.
- Existing upload policy two cases,0.219s, and upload wiring five cases,26.36s: changed caller/stub signature, defaults, capacity/permissions, verified metadata/retry/idempotency behavior still pass. These are realPG with mocked storage/probe, not one integrated deadline→HTTP→DB chain.

No full BE suite, unrelated browser flows, probe process tests or contract export repeated. Seven source/test hashes match validation copy, `.local/social-v2-g5-transfer-source-check.json`; focused whitespacePASS. Owned containers gone, normal3000/8081 absent/untouched. No existing/recoveredDB, normal restart, runtime secrets, remoteGit/CI/commit/push, activation/deploy or image build. Earlier production images remain historical.

The measured bound covers the new full-object download call. It does not claim a20second total completion endpoint across HEAD/prefix, DB locks, download and probe, nor a new bound for range streaming, JVM/filesystem failure or all external storage implementations. ActualMinIO success and loopback failure proofs are distinct from the combined browser pipeline.

## Cleanup source findings and next work

Existing `MediaCleanupService` claims oldPENDING rows, marksDELETED, then best-effort deletes the object while discarding failures; noREADY orphan/FAILED retry state. Its method has no main-source application call site found; existing technical retention does auth/idempotency/outbox expiry, not media objects. Existing technical expiredRecordGrace property defaults7days; do not invent a separate business retention policy or treat its default as a measured production setting. Preserve old image cleanup behavior while implementing the approved video-specific expired/orphan path, bounded claims, successful-delete bookkeeping/retry and coordination with new-video attachmentFORUPDATE.

After cleanup proof, package actual probe tools in the real runtime, execute the complete valid-video Seller/API/PG/S3/probe/content/browser/moderation chain and final localG0–G6 acceptance/build audit. No staging request or scope reduction.

Next action: Implement retry-safe expired/failed/READY-orphan video cleanup and verify attachment-race safety.
Required from you: Nothing
Completion condition: Failed object deletion remains retryable, successful deletion stops recurring, and cleanup cannot delete an attached/attaching verified video; then continue the remaining whole plan.
