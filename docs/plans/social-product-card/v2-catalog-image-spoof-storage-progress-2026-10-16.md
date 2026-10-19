# Social V2 — CatalogImage MIME/magic verification with actual storage

Date:2026-10-16. Scoped11.4 MIME-spoof boundary. Existing verifier passed; no production correction/refactor.

## Source and focused fixture

Current MediaUploadService verifies stored Content-Length, normalized Content-Type and actual prefix magic before READY. Failure is persisted as FAILED with cached422 completion outcome; nonreplayed failed object cleanup runs after that transaction. MediaAttachmentPolicy independently requires an owned READY allowed-purpose asset. Existing unit/integration tests used shared mocked storage/USER_AVATAR for negative bytes; prior Catalog MinIO/browser cases uploaded valid media. This new case fills that modality gap, not another valid upload/gallery rerun.

Added opt-in `CatalogImageSpoofStorageIntegrationTest` under existing catalog test package. Reuses real cached MinIO container/client/presigner fixture and disposable PostgreSQL, random real OWNER/session/merchant/category/DRAFT item. Isolated caption-authoring flagON only for the attachment guard; normal flags unchanged. Actual Spring HTTP with issuer Bearer JWT, not mocked actor/storage or a browser/Next/CSRF-cookie flow. No public bucket policy installed; this is not an anonymous object-access/CDN acceptance claim.

## Actual adversarial chain and assertions

1. Real presign POST201 for CATALOG_IMAGE/image/png, declared16bytes, returns actual signed PUT URL/required headers.
2. Real HTTP PUT200 writes16 deliberately non-PNG bytes through that URL, with required signature headers. Authenticated S3 HEAD confirms image/png and exact16length; authenticated GET confirms exact invalid bytes. Thus metadata/length are valid and magic is the mismatching boundary, not an accidentally rejected size/header request.
3. Actual complete POST422 MEDIA_UPLOAD_VERIFICATION_FAILED, same-key replay also422. Error body does not expose stored object key. Asset is FAILED, never READY; every other media row column unchanged from pending snapshot. Exactly one COMPLETED media.complete idempotency record with response_status422, no duplicate record.
4. After failure/replay sequence, authenticated real S3 HEAD404 proves the object is absent. No delete-call-count/immediate-first-response timing claim or cleanup-failure behavior is inferred.
5. Actual OWNER PATCH on existing DRAFT item with If-Matchv0 and that failed media ID returns422 MEDIA_NOT_ATTACHABLE. All14 protected business tables full JSONB unchanged: Catalog item/variant/offer/gallery, merchant/profile/membership/location/category, reactions/comments, moderation cases/actions/audit/outbox. The single intentionally created media row remains FAILED; zero READY media. No successful-upload audit or attachment effect is manufactured.

The test deliberately never logs signed URLs/JWTs or writes a credential manifest. Current verifier/attachment code is retained unchanged. This checks PNG wrong-magic rejection, not every format/polyglot/decompression/decoder threat, a new deep-decoding business requirement, quota load, or video pipeline. Existing quota/replay/owner tests remain separate and are not repeated.

## Verification

First **1PASS21.46s**, Maven**33.615s**,0skip, exit0: `.local/social-v2-catalog-image-spoof-storage.log`. Source SHA256 matches validation copies for upload service, sniffer, S3 storage, attachment policy and new Java test. Focused whitespace check PASS. Owned API61764 closed; normal3000/8081 absent/untouched. No Next/browser launched or credential manifest created.

```text
mvnw.cmd -o -Dtest=CatalogImageSpoofStorageIntegrationTest -Dcovie.test.catalog-image-spoof-storage=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 test
```

No production BE/FE/API/schema/client/CSS/migration change, full suite, old successful upload/browser/quota/race tests, typecheck/build rerun, recovered/runtime DB access, normal restart, remote/commit/branch/push, activation or deployment. Whole objective ACTIVE, normal V2/video flags OFF. Staging/private object/CDN/load/recovery/release and final stable-source build remain unproved.

Next action: Trace a focused native browser offline/retry scenario and the HTTP bridge's behavior under network emulation before executing it.
Required from you: Nothing
Completion condition: Define a real transport-error/recovery check without mocked success bodies, bypassed offline emulation or replaying unrelated successful flows.
