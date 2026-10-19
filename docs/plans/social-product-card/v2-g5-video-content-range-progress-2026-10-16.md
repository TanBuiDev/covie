# G5 — opaque authorized video content and byte ranges

Date:2026-10-16. Whole project goal remainsACTIVE. This is implemented/local-tested content delivery, not completedG5/project acceptance. Staging remains waived as a local blocker; normal flagsOFF, no deployment.

## Implementation

- New `CatalogVideoContentService`/`CatalogVideoContentController` implementGET/HEAD at the existing opaque `/api/v1/media/{mediaId}/content` URL. Only flagON, undeletedREADY CATALOG_VIDEO assets are readable; metadata remains guarded byV023.
- Every request checks uploader ownership, existingAdmin role, a currently public associated item/merchant, or existingSeller `VIEW_MERCHANT` permission for a linked undeleted item. Reusing the existing policy retains active membership and STAFF assignment rules. This checkpoint directly tests owner/manager/revocation/Admin/foreign/guest, not every role/assignment race.
- Public predicates match Social preview and require aVIDEO association. Later requests afterHIDE or merchant suspension cannot reuse prior public eligibility. Uploader/Admin private access remains separate; HIDE is not claimed to deny them.
- Single inclusive byte ranges, suffix/open-ended ranges,206 Content-Range/length,416 invalid/multipart rejection, Accept-Ranges and no-store. HEAD authorizes and returns registered metadata without storage-body access. If-Range without a supported validator falls back to full200. No redirects/presignedGET/storage-key fields.
- S3 adapter opens only configured bucket/key with an exact range, checks returned Content-Range/length against registered total, aborts the owned stream on close. Servlet uses8192-byte buffer/expected-byte bound/EOF check. Precommit short reads become503. Postcommit transport-failure behavior is not yet socket-tested; no claim that status can change after commitment.
- Security permits onlyGET/HEAD on this path, not media writes. Existing authentication/session/CSRF filters remain.
- Forward-onlyV024 extends association constraintIMAGE/VIDEO without editing applied migrations or rewriting rows. Create/Edit mixed support remains pending; the read fixture inserts itsVIDEO link directly, not through a completed authoring workflow.

## Focused verification

1. `.local/social-v2-g5-content-red.log`: compileRED for absent storage range method, not an executed endpoint assertion failure.
2. `.local/social-v2-g5-content-green.log`: newAPI/realPG/issuedJWT fixture3PASS,26.16s test/40.957sMaven. Storage mocked; ten fixture bytes are not a decoded video. Full/single/suffix/open ranges, HEAD/no storage call,416, guest/foreign denial, public→HIDE denial, manager→revoked denial, merchant suspension, uploader access, nonREADY/deleted/AD-purpose denial. Item JSONB compared around public reads.
3. `.local/social-v2-g5-content-guards.log`: two new/extended content cases and exportPASS, aggregate **3PASS/1FAIL**, not green. DefaultOFF fixture incorrectly expected401 before satisfying existingCSRF; actual403 was not an application defect. Test-only validCSRF then confirms guest writes still401. Only failed test rerun, `.local/social-v2-g5-content-off-green.log`:1PASS21.37s/33.525sMaven. Other cases prove issuedAdmin read and precommit unavailable/truncated mocked-storage503.
4. `.local/social-v2-g5-range-red.log`: three adapter casesRED from unsupported range streaming. Initial attempt `.local/social-v2-g5-range-green.log` repeatedly received registry401 for the unavailable defaultQuay image; verified ownedJava workers explicitly cancelled. That log is **not a successful aggregate**. Retry uses already cachedMinIO digest, not new infrastructure/credentials.
5. `.local/social-v2-g5-range-final-green.log`:4PASS/0skip,6.995sMaven: adapter3cases (exact request/early-close abort/mismatched response/invalid bounds) plus actualMinIO1case,2.870s, middle8201/full20000bytes and wrong-total rejection. No old presign/download test replay. MinIO digest `a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1`.

API/PG mocked-storage and actualS3 proofs are separate, not one realHTTP/PG/S3/probe/browser chain. No fullsuite, unrelated flow reruns or staging evidence.

## Contract and provenance

- Actual export `.local/social-v2-g5-content-openapi.json`; complete semantic guard `.local/social-v2-g5-content-contract-diff.log`: only one added path withGET/HEAD, all147old paths/211schemas unchanged. Current148paths/211schemas.
- Canonical export updated, client regenerated/checkPASS: `.local/social-v2-g5-content-client-generate.log`, `.local/social-v2-g5-content-client-check.log`. Normal consumers unchanged; final web/build acceptance waits for stable mixed-media implementation.
- Eleven API source/test/migration hashes match validation copy, `.local/social-v2-g5-content-source-check.json`. Canonical export bytes match. Focused whitespace checks pass, Git normalization notices only.
- Owned containers removed; normal3000/8081 absent/untouched. No existing/recoveredDB, normal restart, remoteGit/CI/commit/push, activation/deploy. EarlierAPI/Web image snapshots remain historical.

## Remaining scope

Mixed Create/Edit one-video/ten-total ordering, first-image cover distinct from video, existing transaction guards and attachment/cleanup lock coordination; video-aware public/Seller/Admin metadata/generated client; mixed uploader/manual gallery/fallback; full-transfer deadline and failed/pending/READY-orphan cleanup; actual combined valid-video flow and finalG0–G6 local acceptance. Do not stop at this checkpoint or request staging/approvals again.

Next action: Implement mixed attachment/ordering and video-aware read models through existing guarded Create/Edit paths.
Required from you: Nothing
Completion condition: Owned verified video and images persist/read in requested order with approved quotas, unchanged legacy behavior and focused evidence; continue the remaining project scope afterward.
