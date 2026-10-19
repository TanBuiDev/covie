# G5 — mixed catalog attachment and read models

Date:2026-10-17. Full goalACTIVE; previous goal turn made implementation/test progress on authorized content/ranges. This turn continues actual mixed-media backend work, not staging gating. Normal flagsOFF; no project/G5 completion claim.

## Implemented

- Create/Edit accept ownedREADY CATALOG_VIDEO only with video flagON, enforce one distinct video/ten distinct total assets independently of caption flag, preserve requested mixed order and deduplication. Existing caption gating stays separate. Existing image-only/defaultOFF behavior and unrelated commands remain intact.
- Catalog association factory now recordsVIDEO and never makes it the cover. First image remains cover, including `[video,image,image]`; image-only ordering retains the legacy primary-first policy. New mixed gallery repository ordering uses stored sort order without moving the cover ahead of video.
- New video attachment rechecks ownedREADY/undeleted state under the existing assetFORUPDATE lock after bounded prevalidation/quota checks. Create/Edit retain existing membership/merchant/item/version transaction behavior; video-only authoring also uses the established membership mutation guard. Lock coordination with future orphan cleanup still needs actual implementation/race evidence; no claim it is completed by the attachment lock alone.
- Video-only media changes re-enter the existing PRE_APPROVAL path when current policy requires it; captionOFF does not bypass moderation. No new policy or permission domain.
- Seller, public detail, Social preview andAdmin media reads use ordered mixed entries and opaque authorized video URLs, not raw storage keys. Public preview retains40-ID/6-media bounds and current item/merchant/READY predicates. Image-only queries retain primary ordering; if its cover is unavailable, preview chooses a ready image fallback, never video. DefaultOFF omits stored videos from Seller/public media reads and denies new video attachment without deleting stored associations during unrelated edits.
- Optional `video` metadata object (width/height/durationMillis/codec) added to the four existing media schemas. Existing MIME fields distinguish image/video; image JSON omits the new object. No poster generator/URL is claimed: explicit UI fallback is still pending.
- No new migration this turn;V023/V024 remain untouched.

## Evidence

- `.local/social-v2-g5-mixed-red.log`:2actual test failures: valid mixedCreate returned422 instead of201; videoON/captionOFF allowed11 distinct images instead of rejecting. This establishes the missing feature/quota paths.
- `.local/social-v2-g5-mixed-green.log`: quota/foreign/pending rejection casePASS, gallery case reached publish but failed on fixture's invalid If-Match spelling (`"0"`, existing contract requires`"v0"`). Test-only correction; no production version-contract change. Only that failed method rerun, `.local/social-v2-g5-mixed-order-green.log`:1PASS19.61s/30.319sMaven.
- Final changed-query/flag/compatibility run `.local/social-v2-g5-mixed-regressions.log`:6PASS/0skip,41.660sMaven: mixedCreate/replay→actualPublish→reorder→Seller/public detail/preview/Admin ordering and metadata, unavailable-image cover fallback; new videoOFF attachment/read/unrelated-edit preservation; existing captionOFF11-image regression; two existing preview visibility/bounds/order checks; one actual OpenAPI export. Reruns justified by changed Create/Edit/URL mapper/preview SQL, not a broad suite.
- New policy case `.local/social-v2-g5-mixed-moderation.log`:1PASS19.22s/30.131sMaven. With videoON/captionOFF, actualCreate/image→Publish→merchantPRE_APPROVAL→PATCHvideo creates oneOPEN case/PENDING; guest public detail/contentHEAD404, ownerHEAD200. Rejected two-video replacement leaves full item/link JSONB and case count unchanged. No additional production change for this case.

Tests use real disposablePG, SpringAPI/security and issuedJWTs; no mocked actor/access/storage/probe is used in the new mixed tests. READY media is explicitly fixture-seeded;24-byte assets are not actual decoded clips, and contentHEAD does not access storage. Therefore this is **not** a real uploaded-video/probe/S3/browser end-to-end proof.

## Contract and verification

Actual export `.local/social-v2-g5-mixed-openapi.json`:148paths/212schemas. Full semantic diff `.local/social-v2-g5-mixed-contract-diff.log` plus exact-path guard allows only four optional `video` properties and one new `CatalogVideoMetadata` schema; existing paths/fields unchanged. Canonical bytes match actual export. Client generation/checkPASS, `.local/social-v2-g5-mixed-client-{generate,check}.log`; isolated synchronized web typecheckPASS, `.local/social-v2-g5-mixed-web-typecheck.log`, with generated-client link verified to current canonical source. No original.next mutation or web implementation change.

Nineteen touched API source/test hashes match tested copy, `.local/social-v2-g5-mixed-source-check.json`. Scoped whitespace checksPASS. Owned containers gone; normal3000/8081 absent/untouched. No fullsuite, unchanged races/browser flows, existing/recoveredDB, service restart, remoteGit/CI/commit/push, activation or deploy. Earlier production images remain historical.

## Remaining

Seller mixed uploader/progress/order/draft/error handling and public manual playback/pause/resource-release/poster fallback; real complete upload/probe/storage/content/browser chain; full-transfer deadline, retryable failed/pending/READY-orphan cleanup and race coordination; remaining local performance/security/build/finalG0–G6 audit. Continue automatically; no renewed approval or staging request.

Next action: Implement Seller mixed-media input and public manual video playback using these generated contracts and current UI.
Required from you: Nothing
Completion condition: Mixed upload/order/playback/fallback work in targeted component tests, followed by actual valid-video integration/browser evidence and remaining whole-plan acceptance.
