# G5 — Seller mixed uploader and retry state

Date:2026-10-17. Whole goalACTIVE. This checkpoint implements Seller upload/editor/form behavior; real uploaded-video browser/API/PG/storage/probe acceptance is still required. All approvals persist; staging is not a blocker; normal flagsOFF.

## Implemented

- New `direct-catalog-video-upload.ts` uses existing generated media contracts and API client. MP4/WebM only, nonempty/maximum50MiB. One job preserves received upload ID/URL and completion Idempotency-Key across explicit retries. SuccessfulPUT is not repeated after a retryable verification error. An ambiguousPUT failure followed by immutable-object412 proceeds to server verification, not an invented success. Completion must return the same mediaID and existing verifiedAt field. No assumed `status` field or API change.
- Gallery supports images plus one video, total10, atomic invalid-selection rejection, ordered files and existing media. Video cannot become cover; first image remains cover. Video row uses Phosphor placeholder, not an image decoder. File/blob/draft survives failure; successful IDs and unfinished video jobs are cached separately, removed/revoked on removal/unmount as appropriate. Existing image upload helper is unchanged.
- Stage progress distinguishes upload and verification, not a fabricated byte percentage. Failure gives retry/codec/duration/reselection guidance without throwing away selection. Server remains the duration/codec authority; browser MIME/size checks do not prove valid media.
- Create/Edit gallery and hydration guards activate independently for videoON/captionOFF, while caption controls/payload remain gated by the caption flag. Existing command/discard/stale behavior also covers video-only drafts. Local preview preserves stored MIME, requested order and first-image cover, uses the existing manual player for video, and clearly remains a draft preview.
- No backend, migration, API/OpenAPI/generated-client or normal environment changes in this checkpoint.

## Targeted evidence

- `.local/social-v2-g5-uploader-red.log`: missing helper module, not three executed assertion failures. New helper3PASS in `...uploader-green.log` proves verification retry identity/no repeatedPUT, ambiguousPUT→412→verification, invalid MIME/empty/oversize rejection using mocked transport/API.
- `.local/social-v2-g5-editor-red.log`: two actual missing mixed-control failures/one existingOFF behaviorPASS. After editor wiring `...editor-green.log`:6PASS3.96s (editor3/helper3), including retained selections/reused job/order, cover prohibition, second-video rejection andOFF image input. Transport/upload functions mocked; not a real storage test.
- `.local/social-v2-g5-video-forms-red.log`:2actual failures for missing independent videoON/captionOFF gallery paths;44 other cases deliberately skipped by filter. After form changes, `...seller-uploader-green.log`:51PASS/1FAIL across four affected files; **not** a green aggregate. One existingResale serialization case did not issue its mockedCreate call in the combined run. It passed unchanged in isolation (`...resale-regression-check.log`,1PASS4.67s), then entireCreate file plus changed helper passed (`...seller-uploader-final-tests.log`,13PASS5.83s). No Resale production/test workaround; failure not reproducible in these follow-ups, root cause of the initial run not established.
- Typecheck caught this implementation's initial nonexistent completion `status` assumption and two missing testURL override modifiers. Corrected against the actual generated contract to use verifiedAt; no contract expansion to fit the helper. Final isolated typecheckPASS, `...seller-uploader-typecheck-final.log`. Targeted lint0errors/3unchanged legacyimg warnings, `...seller-uploader-lint-final.log`.
- Edit's36cases and editor's3cases passed in the affected run and those product paths did not change afterward; not repeatedly rerun. Existing public player/browser proofs retained without rerun. No full BE/web suite or production build.

Nine changed source/test hashes match validation copy, `.local/social-v2-g5-seller-uploader-source-check.json`. Normal3000/8081 absent/untouched; no browser/server started for this checkpoint, no existing/recoveredDB, normal restart, credentials, remoteGit/CI/commit/push, activation or deployment. Images remain historical until final stable-source build.

## Remaining verified gaps

Source trace shows public `catalog-detail.tsx` andAdmin `admin-catalog-screen.tsx` still pass video media to image-only renderers; Seller non-editable fallback similarly needs a video-aware branch. These are actual consumers of the new mixed DTO, not hypothetical additions. Finish those renderers before claiming complete video UI. Then exercise real Seller upload/verification/mixed save/read/playback/moderation with API/PG/S3/probe/browser, complete bounded transfer and failed/pending/READY-orphan cleanup/race coordination, and perform final localG0–G6 acceptance/build audit. No staging/approval request needed.

Next action: Finish image-only remaining mixed-media consumers and then verify the complete real video pipeline.
Required from you: Nothing
Completion condition: Public detail/Admin/Seller read-only media render verified video correctly, followed by real integration and remaining whole-plan acceptance.
