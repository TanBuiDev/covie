# Social V2 — invalid video, mixed Edit and read surfaces

Date:2026-10-17. Whole objective ACTIVE. Previous turn delivered real MP4/WebM Create/publish/native playback/moderation and current web build. This turn targets missing invalid-upload and Edit/read evidence without changing application code or replaying those successful Create/moderation suites.

## Real invalid-video API/storage boundaries

`.local/social-v2-video-invalid-run.log` exit0, fixture `.local/social-v2-video-chain-17442f1dced3/{result.json,invalid-evidence.json,browser.log}`. Current packaged API image/source1140, fresh disposable PG18/MinIO, real generated keys/password login. This targeted **HTTP/DB/storage test does not start Next or Chrome**. It uses a genuinely issued Bearer token through the existing supported API contract, not mocked actors or fabricated JWTs. Separate native cookie-CSRF flows remain in earlier proofs.

Request denials:

| Submitted request | Actual result |
| --- | --- |
| video/quicktime,16bytes |422 MEDIA_TYPE_NOT_SUPPORTED |
| video/mp4,0bytes |422 VALIDATION_FAILED |
| video/mp4,52428801bytes |422 MEDIA_UPLOAD_VERIFICATION_FAILED |

Four actual signedPUT200 objects have matching registered type/length but invalid content: PNG bytes claimed as MP4; valid MP4 container with unsupported MPEG4 codec; first half of a real H264 MP4; real61-second H264 clip. Actual complete422 and same-key replay422 for each; authenticated opaque content404. Independent PG shows exactly4FAILED,0READY and4COMPLETED idempotency records each response_status422. Actual packaged S3 SDK independently HEADs all four owned keys after rejection/replay: four404, proving objects removed. This is not a count of delete calls, arbitrary malware/polyglot coverage or an interrupted HTTP-transfer test; prior transport-bound proofs remain separate.

Then21 concurrent actual presign requests from the same actor:20accepted201,1denied429, no other statuses. PG independently shows20PENDING_UPLOAD, not21; rejected completed assets do not consume pending slots. No uploaded object is created for these pending-only presigns. Fixtures contain no catalog associations and are removed afterward. No image/Ads/old quota suite rerun.

## Native mixed Edit and Admin read — narrower stage evidence

`.local/social-v2-video-edit-read.log` is **exit1**, not a passing aggregate. Fixture `.local/social-v2-video-chain-a567f9c787ff/edit-read-failure.json` and `admin-video.png` preserve the successful earlier stages:

- Existing valid ACTIVE/NOT_REQUIRED product/offer is seeded, not created/published again through already-proven Create UI.
- Actual Seller Edit selects image/MP4/image and moves video to first using its native reorder button. Actual signed uploads/completion verify the new clip; one PATCH204 with If-Match v0, actual CSRF and **only mediaIds** in the patch body.
- Reload shows video first. Actual keyboard ArrowRight shows next image, ArrowLeft returns to video. Persisted IDs match completed video first.
- Actual separately logged-in Admin opens `/admin/listings?item=...`; current gallery manually plays that same uploaded clip via actual206/46001bytes, time advances, pageErrors0. Screenshot opened/visually reviewed.

The final public-page assertion incorrectly required the item name as h1. Current UI intentionally uses generic “Chi tiết sản phẩm” as h1 and the actual item name as h2. This is a **helper locator defect**, not a product/rendering defect. Because that final assertion aborts, the orchestrator's post-browser full PG/protected-table comparison was not reached for this Edit fixture. Do not claim it was reached or call the aggregate green.

## Focused public-detail verification, no repeated Edit/Admin journey

Correct only the helper locator to actual h2 and run **only the missing public-read stage**: `.local/social-v2-video-public-read.log` exit0, fixture `.local/social-v2-video-chain-080be3227ed4/{result.json,edit-read-evidence.json,public-detail-video.png}`. No repeat of successful Edit, keyboard or Admin steps.

This isolated read fixture uses actual image/API runtime tools to probe/full-decode a generated160×120/4000ms H264 clip, stores actual private MP4 and PNG objects using packaged S3 SDK, then seeds READY metadata/ordered associations directly. It is **not another upload/READY application-pipeline proof**; that is supplied by the prior successful real chains. VIDEO0 is not primary, IMAGE1 is primary, IMAGE2 remains second image.

Guest opens actual `/items/{id}?lat=10.8&lng=106.7` at1280×1000, exact item-name h2 visible, native trusted Play, actual206/46001bytes and currentTime>0.1. PageErrors0; screenshot opened/visually reviewed. After read, actual PG item remains version0/ACTIVE/NOT_REQUIRED, unchanged description/caption and exact VIDEO0/IMAGE1(primary)/IMAGE2 links. Six protected tables match full pre/post JSONB: merchant profiles, memberships, locations, categories, variants and location variants. This comparison applies to the **read-only fixture**, not retroactively to the earlier Edit fixture.

## Preservation and acceptance limits

No production source/API/schema/client/migration/CSS/dependency change, no new app fix, no original `.next`, existing/recovered DB, normal restart, remote Git/commit/branch/merge, deploy or activation. Current API/web images remain current while their source hashes match; no rebuild or unchanged whole suite rerun needed for these ignored verification helpers. Normal flagsOFF, only owned fixture flagsON. All owned services/processes/networks and generated credential/env/key manifests cleaned; evidence/media retained only under ignored `.local`.

These are named G5 checks, not whole G0–G6 acceptance or a four-width complete-video-matrix claim. Existing component/real-probe/permission/flagOFF/cleanup and four-width native-player proofs remain separate, unchanged. Current Admin/native public proofs here cover PRODUCT; existing SERVICE/RESALE component proofs are not silently upgraded to browser journeys.

Next action: Measure the remaining local20–40-card/read/activity/comments workload and required query/browser/media metrics using disposable fixtures, then reconcile whole-plan acceptance and safe rollback limits.
Required from you: Nothing
Completion condition: Actual local measurements and requirement-by-requirement evidence close remaining scope; no invented staging/production SLO or completion claim.
