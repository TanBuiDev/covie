# G5 — manual Social gallery playback

Date:2026-10-17. Full goalACTIVE. Previous turn progressed mixed backend/contracts; this turn implements public gallery playback. Seller mixed uploader and completeG5 acceptance remain unfinished. Normal flagsOFF; no deployment.

## Changes

- Final exclusivity correction implements the literal one-playing-card rule across visible cards, not merely one player/slide. NativeonPlay pauses the previous component's player through one module-local reference, cleared on cleanup; no global content cache/event channel/user data.

- New `apps/web/features/marketplace-view/social-video.tsx`: explicit play button with Phosphor icon/current project colors as the supported no-poster fallback. No video element/src exists before the button is activated. Native controls, muted/playsInline, preload=none, no autoplay attribute; play promise rejection and media errors render a finite accessible failure state without retry loops.
- Player pauses out of view and when document becomes hidden; does not auto-resume. Slide change/unmount disconnects observer/listener, pauses, removes src and calls load to release the resource. Only the current slide mounts a player; no extra offscreen video elements.
- Existing `social-post-content.tsx` selects video by the generated media MIME field, keeps native controls outside detail links, excludes control touches from gallery swipe handling, and uses mixed-media navigation labels. Existing image-only labels, links and gallery behavior stay unchanged.
- New independent build-time frontend flag `NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED`, defaultfalse. OFF filters videos instead of sending their URLs to an image element. It does not activate backend video; that flag remains separate/defaultOFF. No normal environment file changed.
- Scoped CSS reuses existing gallery colors and provides video contain sizing plus44px minimum play control. No design-system/layout refactor or Figma dependency.

## Component evidence

`.local/social-v2-g5-video-ui-red.log`:4actual failures on missing mixed/manual/off behavior. First green attempt `.local/social-v2-g5-video-ui-green.log`:12PASS/1FAIL (all9existing content casesPASS; new pause case failed from test observer counter leaking across teardown). Test-only counter/cleanup fixes; no production change. Intermediate4PASS log still had jsdom media cleanup warnings because mocks were restored before unmount; not presented as clean evidence.

Final `.local/social-v2-g5-video-ui-final-clean.log`:5PASS,3.74s, no warnings/errors: lazy/manual/no linked controls, native attributes, observer/hidden-page pause and source release, finite media-error fallback, defaultOFF image behavior, rejected play promise. Media methods/IntersectionObserver mocked; this alone is not browser decoding evidence. Existing9content cases were not repeatedly rerun after their unchanged passing result.

Isolated current-source typecheck and targeted lintPASS: `.local/social-v2-g5-video-ui-typecheck.log`, `.local/social-v2-g5-video-ui-lint.log`. No BE/contract/client changes or BE test rerun this turn.

## Actual browser evidence

**Final current-source evidence superseding the initial five-case checkpoint:** added one focused two-card testRED (previous player not paused), `.local/social-v2-g5-video-exclusive-red.log`; minimalonPlay coordinator then all six affected player casesPASS4.07s, `.local/social-v2-g5-video-exclusive-green.log`. Final typecheck/lintPASS in `...video-ui-{typecheck,lint}-final.log`. Native rerun justified by this actual playback behavior change: `.local/social-v2-g5-native-video-final.log`, same four viewport/decode/no-preload/range/pause/release checks plus two simultaneously visible cards at1280×1200, second playing pauses the first while first remains in viewport/not ended. Six actual206 requests, pageerrors0, oneVisibleCardAtATime=true. All four final screenshots visually inspected. Final ownedNext26584/20936 stopped; four current source/test hashes rechecked. No claim of native audio-track/physical-mobile/API-storage integration.

`.local/social-v2-g5-native-video-checked.log` and `...native-video-evidence.json`: actual copiedNext component in an ignored isolated fixture page, actualFFmpeg-generated160×120H264 MP4/8seconds, installedChrome155.0.8059.84, viewport360/768/1280/1440. Browser-visible URL remainslocalhost:3000 through the existing-style HTTP/WebSocket bridge to ownedNext44077; real responses forwarded, not fabricated media bodies. StaticNext video delivery, **not** API/PG/S3/verification chain.

All four widths: no video request before play; trusted browser click then decoded videoWidth160/currentTime>0.1/playing; native muted/controls/playsInline/preload-none/no-autoplay properties; actual upstream206 `bytes=0-`; out-of-view pause, still paused after return, slide removes player; no horizontal overflow; play control345×258.75 at360 and480×360 at other widths. Screenshots `...native-video-{360,768,1280,1440}.png` all visually inspected, no new sizing defect. Pageerrors0. Video-only clip does not prove audible-track behavior beyond the actual muted property.

Initial browser failures were fixture transport problems: omittedNextHMR bridge left the page unhydrated (no play operations/requests), then an unguarded route callback failed during teardown. Only ignored helper changed; adding the establishedWebSocket bridge/closing guard yielded the successful run without a product fix. The dummy next-image fixture URL was404; image decode was not an assertion or acceptance claim. Its ignored fixture URL subsequently corrected to an existingSVG, without replaying already-passed video checks. Existing real image workflows remain separate evidence.

Four product source/test hashes match the tested runtime copy, `.local/social-v2-g5-video-ui-source-check.json`. OwnedNext27384/25536 stopped, no containers and no3000/8081/44077 listener afterward. Ignored fixture/helper/generated clip retained for reproduction; not production routes/assets. No original.next, existing/recoveredDB, normal restart, remoteGit/CI/commit/push, activation or deployment. Current production images remain historical.

## Remaining whole-plan work

Seller mixed uploader/validation/progress/retry/order/cover/draft and preview, seller video-only flag integration; real complete uploaded-video/probe/storage/authorized-content/browser workflow, actual moderation/privacy and playback delivery/error integration; full-transfer deadline, failed/pending/READY-orphan cleanup/race coordination; remaining local performance/build/finalG0–G6 audit. This checkpoint is not the finish line.

Next action: Implement Seller mixed upload and retry-safe verification while retaining existing image/draft behavior.
Required from you: Nothing
Completion condition: Seller can upload/order/retain a verified video with images and recover from errors; then continue combined real-data and remaining whole-plan acceptance.
