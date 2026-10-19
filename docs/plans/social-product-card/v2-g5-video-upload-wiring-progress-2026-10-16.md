# Social V2 G5 — catalog-video upload verification wiring

Date:2026-10-16, Asia/Saigon. Full goal attachment read; previous turn was progress with real MP4/WebM probe/decode. Staging remains waived as an implementation blocker. This is backend upload/persistence progress, not full video/gallery/API/UI acceptance.

## Implemented

- AddedCATALOG_VIDEO and a separate `covie.social-v2.video-enabled` defaultfalse. MP4/WebM only, existing50MiB and pending-upload quota retained. No request merchantId or new role added.
- Presign/completion require an active membership with existingMANAGE_CATALOG_STRUCTURE capability, revalidated using the existing shared mutation lock. Owner/Manager allowed; Staff/no authoring membership denied. No new blanket merchantACTIVE restriction is imposed on upload; actual item create/publish/attachment policy remains authoritative.
- `CatalogVideoUploadPolicy` downloads the owned exact-size object into an owned temporary workspace, invokes the real configured probe and removes local files. Configurable probe concurrency defaults2; capacity429 is retryable without successful verification or idempotency completion. Existing process20s deadline remains; total S3-transfer wall-time enforcement remains explicit follow-up, not a completed timeout claim.
- Before READY, completion retains HEAD/type/size/magic validation and requires successful full probe/decode. Persists width/height, video_duration_ms and video_codec; entity and SQL prevent unverified READY catalog video. Nonzero tool/decode/invalid metadata is distinct from unavailable/timeout infrastructure: malformed content follows existing cached422/FAILED/delete-once flow, transient processing503 leavesPENDING and rolls back the idempotency record for same-key retry.
- New immutableV023 adds only nullable metadata columns and the catalog-video READY check; existing width/height columns are reused. No applied migration edited. Existing image/AD_BANNER paths remain unchanged except constructor wiring.
- Video completion returns an absolute opaque media-ID content URL resolved against the existing public-site configuration, not a storage key/object URL. **That GET/range endpoint is not implemented yet**: it is the next read/privacy task, not a working playback claim. Existing image/Ads URL behavior is preserved.

## Targeted verification and actual limitations

`CatalogVideoUploadIntegrationTest` uses real disposable PostgreSQL/JPA/Flyway/idempotency/audit and real membership queries/locks. Actor provider, object storage and probe are mocked to isolate wiring; it checks the temporary bytes passed to the probe. Its24-byte MP4-looking fixture is **not** a valid decoded video; prior actual probe tests prove real formats separately. These are not authenticated HTTP/real S3 end-to-end results.

Feature RED compilation (`.local/social-v2-g5-upload-red.log`) proves purpose/metadata integration absent. First four service integration cases PASS in `.local/social-v2-g5-upload-green.log`. Additional concrete fixes:

1. New relative content URL conflicted with the existingURI response format. Focused1RED (`.local/social-v2-g5-video-uri-red.log`) → configured absolute opaqueURL, then feature/policy**6PASS**/Maven36.593s (`.local/social-v2-g5-video-uri-final.log`). Validates metadata64×48/1000ms/h264, temp cleanup, replay/no reprobe/audit duplication and no key leak; invalid content FAILED/delete once; unavailable toolPENDING→same-key success.
2. Actual reused HEAD/prefix catch incorrectly treated a new-video transient storage failure as terminal422. Targeted1RED503expected/422actual (`.local/social-v2-g5-storage-retry-red.log`) → new-video-only503 branch, preserving original image/Ads behavior. Subsequent7-case run has6PASS and one **test restubbing error**, not another product defect (`.local/social-v2-g5-upload-final.log`): Mockito`when` invoked the still-throwing stub during retry setup. Corrected only that restub to`doReturn`; reran only the affected case: **1PASS22.08s/Maven33.609s**,0skip (`.local/social-v2-g5-storage-retry-green.log`). Do not label the failed7-case invocation a green aggregate; other six unchanged successful cases were not rerun.

One scoped**18PASS/Maven49.469s**,0skip run (`.local/social-v2-g5-upload-regressions.log`), before the URL/storage-specific corrections above:
- Two existingAds unit cases and two existingAds/image completion integration cases, plus the new flagOFF denial case; no old concurrency/security suite replay.
- New V022→V023 populated migration test: legacyREADY image/AD-video all existing JSONB columns unchanged; raw unverified READY catalog-video insert rejected. Two-row legacy sample, not large-data lock/performance proof.
- New capacity/flag policy2cases; changed typed-failure runner4cases and metadata4cases; actual MP4/WebM container1case rerun because exception classes/wiring changed.
- One actual OpenAPI export. No full BE suite.

## Contract and client

Actual export remains147paths/211schemas. Guarded complete semantic diff allows onlyCATALOG_VIDEO added to CreateMediaUploadRequest/MediaResponse purpose enums, `.local/social-v2-g5-upload-contract-diff.log`; no other contract changes. Canonical export preserved prior source in ignored backup, updated from actual export; regenerated canonical client with configured Node24/pnpm and its generation/checkPASS. New client enum compatibility web typecheckPASS in isolated synchronized web source, `.local/social-v2-g5-upload-web-typecheck.log`. No original.next build/runtime mutation.

14changed API source/test/migration hashes match validation copy; canonical OpenAPI bytes match the actual export. Normal3000/8081 absent/untouched, owned test containers gone. No existing/recovered DB/credentials, full-suite/browser rerun, normal restart, remote Git/CI/push, activation or deployment. Normal flagsOFF.

API production image is historical after code/migration changes. Web application source is unchanged but its prior build input snapshot now differs in the two generated-client schema/OpenAPI files (`.local/social-v2-g5-web-build-input-delta.log`), so do not call that old image exact-current-source either. Defer fresh builds until the implementation settles; no stale build acceptance.

## Remaining implementation

Opaque video content GET/HEAD/ranges with current public/owner/Seller/Admin authorization; mixed attachment one-video/ten-media ordering and locking; public/Seller/Admin media DTOs; actual full upload/storage/probe/read/browser chain; uploader/manual playback/poster fallback; transfer deadline and failed/pending/READY orphan cleanup; final local acceptance remain required. Do not stop after upload wiring or request staging/approval again.

Next action: Implement authorized opaque video content/range reads and mixed-media attachment/read contracts, then continue Seller/gallery UI and combined real-data validation.
Required from you: Nothing
Completion condition: Verified video can be attached and read only under the correct policy with working byte ranges, followed by the rest of the complete local plan.
