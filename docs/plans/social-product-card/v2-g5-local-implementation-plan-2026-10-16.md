# Social V2 G5 Local Implementation Plan

**Local implementation/acceptance closed2026-10-17:** `v2-final-local-acceptance-2026-10-17.md` and `v2-local-acceptance-matrix-2026-10-17.md` cover the entire G0–G6 scope, including video/mixed media. Earlier remaining-work paragraphs below are chronological checkpoints, superseded by that final matrix. Normal flags remain OFF; staging/remote CI/production/released rollback pairing not performed and not called PASS.

> **For agentic workers:** Follow the executing-plans and test-driven-development workflows. Execute the approved scope without new business-approval requests; preserve dirty work and use targeted verification.

**Goal:** Implement the remaining approved catalog video/mixed-media feature and local acceptance, without treating unavailable staging as an implementation blocker.

**Architecture:** Extend the existing media upload/verification and catalog associations; do not create an independent post/video domain. Verify the owned storage object completely before READY, preserve ordering and existing moderation/merchant authorization, expose only verified public media through the generated contract. Keep video defaultOFF and reuse current project UI/tokens.

**Tech Stack:** Java25/Spring Boot, S3 SDK, PostgreSQL/Flyway, bounded local media probe tooling, Next/React and generated API client.

## Owner direction and invariant boundaries

Owner explicitly waives the staging prerequisite for continued implementation on2026-10-16. Older BLOCKED/entry reports are historical and superseded for sequencing. All prior ADR/limits approvals persist. No new approval question, fabricated staging, normal-service restart, destructive DB operation, remote Git/commit or production deploy. Existing APIs/image/Ads behavior remain compatible; additive contract/schema changes are verified when actually introduced. No broad suite or image rebuild for every incremental step; previous API image becomes historical when its inputs change, web image remains valid while its inputs are unchanged.

## Ordered tasks

### 1. Complete owned-object access for trustworthy probing

Files: `apps/api/src/main/java/vn/covie/media/storage/ObjectStorage.java`, `S3ObjectStorage.java`; focused new storage unit tests and one method in existing `S3ObjectStorageTest.java`.

- [x] Prove the missing bounded full-object download capability with a targeted failing test.
- [x] Implement streamed exact-size download to a new owned temp path, reject overlong/truncated streams, abort failed reads and clean only partial output created by this operation. Do not download arbitrary external URLs or buffer50MiB in heap.
- [x] Verify exact bytes and failure boundaries, then one actual disposable MinIO object download. Leave existing prefix/presign/AD behavior unchanged. Evidence: `v2-g5-bounded-object-download-progress-2026-10-16.md`.

### 2. Reliable catalog video probe and verification

Actual current API runtime tooling/packaging and packaged-JAR MP4/WebM probe+decode verified in `v2-g5-runtime-video-image-progress-2026-10-17.md`. Fresh prod boot applies25migrations and health passes. This closes missing runtime binaries, not real upload/storage/browser integration or whole-plan acceptance. Normal flagsOFF.

Files: new focused media application probe/properties/result classes; `MediaUploadService`, `MediaAssetEntity`, storage adapter as required; bounded subprocess tests and actual small generated MP4/WebM fixtures.

- [x] Implement real local-file metadata probing with finite timeout/output, controlled executable/arguments and no network protocols; missing executable or invalid output fails closed. Actual local MP4/WebM probe+full-decode evidence: `v2-g5-real-video-probe-progress-2026-10-16.md`.

  The prior4 Java-child tests prove only process bounds. Subsequent actual ffprobe/ffmpeg tests cover H264/AAC MP4, VP9/Opus WebM, VP8/Vorbis WebM and concrete invalid-file denials. Upload READY/persistence/flags remain to be wired; no full G5 claim.
- [x] Verify container/codec/dimensions/duration from actual probe output. Apply approved60s only after the reliable probe is wired; retain50MiB. Generate/validate a poster or explicit supported fallback; do not claim arbitrary MP4 transcoding support. Real MP4/WebM READY/native chains plus actual61s/unsupported-codec complete422 in `v2-g5-invalid-video-edit-read-progress-2026-10-17.md`; explicit no-poster manual fallback retained, no transcoding/generation claim.
- [x] Integrate HEAD+size+magic+full-object/probe before READY, with idempotent completion/cleanup. Persist necessary metadata via a new additive migration, reusing existing width/height columns where suitable. V023 and service wiring tested in `v2-g5-video-upload-wiring-progress-2026-10-16.md`; real full HTTP/S3/production-tool-chain acceptance is still separate, not supplied by mocks.
- [x] Implement and target-test the full S3-transfer bound plus failed/pending/READY-orphan retry cleanup, preserving old image behavior and coordinating new video attachment locks. `v2-g5-video-cleanup-progress-2026-10-17.md`: actualPG/issuedSellerPATCH two orders, lease/bookkeeping/fresh reference guard, actualMinIO deletion and populatedV024→V025 sample. This implementation checkbox is not actual cloud/CDN/load/full video-chain acceptance; those remain task6.

  Full-object transfer deadline now implemented/verified, `v2-g5-video-transfer-deadline-progress-2026-10-17.md`: actualHTTP stalls before headers/within body RED→GREEN, actualMinIO deadline-bearing success, existing size/cleanup/caller regressions15PASS. Positive configurablePT20S operational default, not a whole-endpointSLO. This checkbox stays open because expired/failed/READY-orphan retry cleanup/race coordination is still required; old pending best-effort method alone does not supply that feature.

Primary implementation reference: [ffprobe documentation](https://ffmpeg.org/ffprobe.html) for machine-readable stream/container output and [FFmpeg protocol controls](https://ffmpeg.org/ffmpeg-protocols.html) for restricted input protocols. Checking those references is not evidence of a working installed/runtime probe.

### 3. Purpose, permission and mixed associations

Files: `MediaPurpose`, upload/catalog create/edit/access policies and `CatalogItemMediaEntity`; new forward-only migration; existing media/catalog integration fixtures.

- [x] Add separate defaultOFF video flag andCATALOG_VIDEO MIME allowlist; use existing Seller capabilities, not invented roles. Existing upload request has no merchantId: preserved; active authoring membership is revalidated with the existing mutation guard. Actual merchant attachment permission remains part of the next mixed-attachment transaction work.
- [x] Allow only owned verified catalog video, one video/ten total, ordered mixed attachments. Preserve legacy galleries/unrelated edits and existing membership/merchant/moderation locking. `v2-g5-mixed-backend-progress-2026-10-17.md`: actual Create/replay/Publish/Edit/read tests use seededREADY video; actual upload/probe/storage chain and cleanup race proof remain separate requirements.
- [x] Verify targeted ON/OFF, invalid attachment/quota, ownership and actual upload/probe flow; keep normal flagsOFF. Retain previous unchanged scoped ownership/OFF/mixed tests; actual signed-object invalid-completion/replay/deletion and21-concurrent pending-quota evidence in `v2-g5-invalid-video-edit-read-progress-2026-10-17.md` complements actual valid upload/probe chain, not a full matrix/load proof.

### 4. Additive read contract and generated client

Files: `SocialItemPreview`, preview/detail/Seller/Admin media mappers, existing OpenAPI export and generated client.

- [x] Add only required optional video metadata fields; existing MIME is the media-kind discriminator. Preserve image fields/semantics and bounded40-ID/6-media batching. No generated poster claimed; explicit supported UI fallback remains task5.
- [x] Enforce public/moderation/READY eligibility and avoid exposing storage keys or accepting external storage URLs. Opaque content authorization plus public/Seller/Admin mixed mappers implemented; actual storage/CDN/integration acceptance remains separate.

  Current image `MediaUrlResolver` emits publicBase/storageKey. Preserve that existing image contract, but do not reuse it unmodified for the new private catalog video path. Implement an opaque media-ID content route with byte ranges, existing owner/Seller/Admin permissions or current public-item eligibility, and no-store headers; do not expose an unconditional storage-object URL for an unpublished/hidden clip.

  OpaqueGET/HEAD/ranges now implemented and locally verified: `v2-g5-video-content-range-progress-2026-10-16.md`. Current eligibility rechecked per request; exactS3 ranges separately verified against realMinIO. V024 allowsVIDEO associations but authoring integration/readDTOs remain pending. Current contract148paths/211schemas, only newGET/HEAD path; generated client/checkPASS. This does not close the whole read-contract task or supply a combined real upload/probe/browser chain.
- [x] Export/compare OpenAPI and regenerate/check client after actual contract changes; targeted compatibility/hidden-media checks, not a full unchanged BE rerun. Latest148paths/212schemas: only four optional video properties/new metadata schema; client/check and isolated web typecheckPASS. Repeat for any subsequent actual contract changes, not unchanged checkpoints.

  First purpose-only export completed:147paths/211schemas, onlyCATALOG_VIDEO added to two purpose enums; generated client/check and web typecheckPASS. Export again for actual new content/read DTO routes when introduced, not for unchanged checkpoints.

### 5. Seller authoring and public playback

Actual validMP4/WebM combined Seller→signedPUT→realAPI/S3/probe→READY→mixedCreate/reload/Publish→guest native206 playback now verified, with actual WebM AdminHIDE/RESTORE/private-object/public-visibility/audit/outbox proof: `v2-g5-real-video-chain-progress-2026-10-17.md`. Desktop1280 actual chain, not four-width full-chain/Admin-click proof. No production change needed. Current598-source web image build/runtime alsoPASS; final named boundary/Edit/read-surface/performance/whole-plan acceptance remains task6.

Remaining image-only consumers now have targeted video branches: `v2-g5-video-consumers-progress-2026-10-17.md`, publicProduct/Service/Resale mixed detail, authorizedAdminREADY/nonREADY gallery and non-editableSeller. Existing all-image paths preserved; typecheck/lint checksPASS with one legacy warning. These are component/read-routing proofs, not actual full-page/native authorized delivery acceptance. Finish transfer/cleanup/runtime tools then the real combined chain; whole task5 remains unaccepted until those actual workflows are verified.

Seller uploader/editor/form paths now implemented, `v2-g5-seller-video-uploader-progress-2026-10-17.md`: retry preserves mediaID/completion key, no repeated successfulPUT, mixed quota/order/cover/draft/stage progress, independent videoON/captionOFF and generated verifiedAt contract. Component/mocked-transport evidence only; real combined chain remains open. Actual remaining consumers `catalog-detail.tsx`, Admin gallery and Seller read-only fallback are still image-only and must be completed; do not mark the whole UI task done yet.

Public manual player now implemented with defaultOFF `NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED`; component tests and actualChrome/staticMP4/4-width pause/decode proof in `v2-g5-manual-video-playback-progress-2026-10-17.md`. Explicit no-poster play fallback supported. Seller mixed uploader/preview and actual complete API/storage/probe/browser chain remain open; do not count a private fixture page as that combined chain.

Files: existing Seller caption/gallery inputs, marketplace `social-post-content.tsx`/styles and feature flag helpers; existing current UI components and targeted tests.

- [x] Read applicable web AGENTS/Next docs. Add mixed upload/progress/order/cover/draft/error behavior using generated types; cover image stays distinct from video poster. Implementation/component evidence retained in Seller uploader report; actual valid upload/publish chain above.
- [x] Manual-play controls, muted/playsInline, no autoplay/audio, pause on slide change/unmount/out-of-view and one playing video per card. Bounded preload/lazy poster and finite error fallback. Previous targeted lifecycle/exclusivity/four-width evidence retained; new actualAPI/S3/probe native playback complements it.
- [x] Targeted component tests followed by actual isolated API/PG/storage browser chain and necessary viewport/keyboard checks. Preserve already-passed unchanged image/commerce flows. Real MP4/WebM chain and targeted Edit/Admin/public stages plus previous native-player four-width/keyboard evidence retained. Current production-mode loading-layout defect confirmed/fixed and four-width regression verified in `v2-social-loading-layout-stability-progress-2026-10-17.md`; not a whole-plan or exhaustive browser cross-product claim.

### 6. Final local reconciliation

New working full-plan matrix `v2-local-acceptance-matrix-2026-10-17.md`, current corrections `v2-final-metadata-migration-recovery-progress-2026-10-17.md`: populated migration stage pin022/explicit025 and canonical mixed-metadata recovery each1PASS. Real missing G1.1 video indicator now optionalboolean/SQLexists/Adminlabel with focused API/UI RED→14API13Web2query-guard PASS and semantic-only-field/client/tsc/lint. API/Web images now historical after enumerated6/4inputdeltas; final stable packaging pending after remaining12.3 monitoring audit. No unchanged old journeys/full-suite rerun or scope reduction.

Actual rollback checkpoint `v2-local-video-rollback-compatibility-progress-2026-10-17.md`: currentON/OFF/resumed preserves73tables; old binary boots but Seller500 on CATALOG_VIDEO. Rollback now fails closed for V023+ without source-bound verified target/evidence metadata; two targeted shell gates/syntax PASS, not real deployment. Keep compatible binary/flagsOFF, establish compatible-reader rollback floor before activation. Not legacy compatibility PASS. Continue whole-plan matrix.

Production-mode layout follow-up `v2-social-loading-layout-stability-progress-2026-10-17.md`: confirmedCLS0.42694 caused by undersized preview-loading cards, minimal loading-only footprint/privacy-preserving fix. Affected17runtime testsPASS, test-only optional-CSS-class typing fix then new1PASS/tsc/lintPASS; actual four-width production-mode CLS0.0166–0.0248. New normalOFF598-source image979344... actual build/runtimePASS; old58371... image now historical. No BE/contract/provider/ready-state business change. Continue whole-plan acceptance/rollback reconciliation, not another unchanged API load run.

Actual performance checkpoint `v2-local-read-load-browser-metrics-progress-2026-10-17.md`:6existing API scenarios75739requests/0errors, short local400ms pilot checksPASS, actual SQL/pool/heap/CPU/body sizes and current preview/cleanup plans. RealdevSearch20→40 card browser/network/LCP/CLS/heap measured. **CLS0.427 is not a passing frontend gate**; next production-mode featureON reproduction/attribution and minimal evidence-led correction only if confirmed, then whole-plan acceptance. Keep normal OFF image/source unchanged; no repeat of successful API load/old functional scope.

Latest targeted evidence `v2-g5-invalid-video-edit-read-progress-2026-10-17.md`: actual bad MIME/zero/oversize, four invalid signed objects/replay/real deletion,21concurrent pending quota; successful native Edit/reorder/keyboard/Admin stages in an aggregate ending with wrong-h1 helper failure, followed by focused corrected actual public read only. No app fix or repeat of passed Edit/Admin stages. Remaining measurement/final whole-plan reconciliation stays open; no aggregate green claim for that initial journey.

- [x] Verify namedG5 malformed/MIME/size/codec/probe/explicit poster fallback/quota/ordering/moderation/cleanup and flagOFF boundaries proportionately. Real valid/invalid upload/probe/S3/native/moderation and cleanup/race evidence is indexed in the final matrix; no poster generation/transcoding claim.
- [x] Current contract/client/typecheck/targetedlint/affected tests and stable-source API/Web builds/runtime verified. Exact1141API/598Web source/context/inventory hashes match; final current image identities and narrowly scoped security scans recorded in `v2-observability-final-artifacts-progress-2026-10-17.md`. No aggregate full-suite fiction or unchanged replay.
- [x] FullG0–G6 local matrix/final acceptance handoff completed; staging waived only as local blocker, never falsely markedPASS. All external release/activation/operating facts remain separate; flagsOFF/no remote Git/deploy.

No new post/blog entity, no Catalog/Checkout redesign, no speculative global refactor or mass tests. This plan retains the entireG5 scope; individual steps are implementation increments, not a narrower completion definition.
