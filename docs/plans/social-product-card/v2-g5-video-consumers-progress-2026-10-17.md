# G5 — remaining video read consumers

Date:2026-10-17. Full goalACTIVE. Previous turn implemented Seller uploader; this checkpoint closes source-confirmed image-renderer incompatibilities. Normal flagsOFF. Not fullG5 or project acceptance.

## Changes

- `catalog-detail.tsx`: when videoON and a mixed/video gallery is actually present, reuse existing `SocialPostContent` and its manual player for Product, Service andResale. Preserve media order/MIME/optional metadata and first-image cover. Existing all-image Product/Resale gallery and Service hero remain unchanged. OFF filters video from the old image branch. No commerce, pricing, cart, review or layout-domain refactor.
- `admin-catalog-screen.tsx`: authorized detail's mixed entries render through the existing manual player only whenREADY+URL and video flagON. NonREADY video gets an explicit unavailable placeholder; no image decoder receives its URL. Existing image renderer remains unchanged. No additional API requests/mutations; actual auth/no-store/content authorization remains backend-owned.
- `catalog-edit-form.tsx`: non-editable Seller media fallback gets the sameREADY/manual-video branch andOFF filtering. Retains image160×120 sizing and existing status labels. Local preview and read-only figure may both represent the same asset; the shared player exclusivity rule prevents simultaneous playback, rather than inventing an unrelated UI removal.
- Reuse the existing player cleanup/pause/no-autoplay/error handling; no duplicated player lifecycle or extra video fetch before play. No CSS, BE, migration, contract/client or environment changes.

## Targeted verification

Initial `.local/social-v2-g5-consumers-red.log`:5failures,55deliberate skips. Admin/Seller directly exposed missing manual-renderer assertions; the three newDetail cases instead had an incompleteReview mock and were **not** meaningful rendererRED yet. First implementation run `.local/social-v2-g5-consumers-green.log`:56PASS/4FAIL, not green. Detail still failed on that mock; Seller test incorrectly queried the entire form, finding both legitimate read-only and local-preview play buttons.

Corrected only the new test fixtures (existingReview response shape and figure-scoped selector). Removed this checkpoint's own newly addedDetail mixed branch, confirmed the three Product/Service/Resale missing-renderer assertion failures against the otherwise preserved source (`.local/social-v2-g5-detail-confirmed-red.log`), then restored the minimal branch. No cart/review production fix or old test workaround.

Final changed cases `.local/social-v2-g5-consumers-final.log`:5PASS/44deliberate skips,4.74s: Product/Service/Resale mixed ordering and image-vs-video routing, explicitDetailOFF guard, non-editable Seller player. The initial affected-file run already passed all12Admin cases (including newREADY/nonREADY/read-only-request coverage), eight existingDetail cases and36 otherSeller cases; those unchanged passing paths were not rerun. This is not a full suite or one green60-case aggregate.

All new playback assertions stop before pressing play; they establish correct data routing/lazy controls, not native decoding or live authorized delivery. Native shared-player decode/pause/range/exclusivity proof remains separately documented in `v2-g5-manual-video-playback-progress-2026-10-17.md`; no unchanged browser rerun here.

Isolated current-source typecheckPASS, `.local/social-v2-g5-consumers-typecheck.log`. Targeted lint0errors/one unchanged legacySellerimg warning, `.local/social-v2-g5-consumers-lint.log`. Six source/test hashes match validation copy, `.local/social-v2-g5-consumers-source-check.json`; focused whitespacePASS. No runtime/browser started, existing/recoveredDB touched, normal restart, remoteGit/CI/commit/push, activation/deploy or production build. Prior image snapshots remain historical.

## Remaining work

Bound/verify complete storage-transfer wall time, finish failed/pending/READY-orphan cleanup with attachment-lock coordination, and ensure actual runtime has the probe tools. Then verify actual Seller upload/verification/mixed save/read/playback/moderation with API/PG/S3/probe/browser and necessary rendered-page checks. Final local performance/security/contracts/build and fullG0–G6 acceptance still required. Component assertions do not waive these requirements or create new staging blockers.

Next action: Implement and verify bounded video transfer and retry-safe orphan cleanup before the combined real video chain.
Required from you: Nothing
Completion condition: Transfer cannot indefinitely occupy verification capacity; cleanup retries failures and cannot delete an attached video, with focused real transport/DB/storage evidence.
