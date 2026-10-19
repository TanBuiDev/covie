# Social V2 G5 — bounded object download

Date:2026-10-16. Owner explicitly directs skipping staging and continuing the full implementation. This supersedes the prior staging-based implementation stop; no repeat approval, fake staging PASS or removal of video from scope. Execution plan: `v2-g5-local-implementation-plan-2026-10-16.md`.

## Implemented increment

G5.2 requires actual server-side metadata probing before catalog video READY; a header/prefix is insufficient input for the complete local-file probe. Existing `ObjectStorage` exposed only presign/HEAD/prefix/delete. Added an additive `downloadTo(key,target,expectedBytes)` capability, with default explicit unsupported/fail-closed behavior for other existing adapters and a real S3 implementation. No caller/image/Ads upload behavior was changed.

The S3 implementation streams into a newly created local file with an8192-byte buffer, reads only the expected length plus the one-byte overrun check, rejects overlong/truncated data, aborts failed SDK reads, and deletes only partial output created by this operation. Existing destination content is neither overwritten nor deleted. No external URL download or50MiB heap buffering. This is a byte-bound foundation, not yet subprocess/network timeout, codec or valid-video verification.

## Targeted evidence

New `S3ObjectDownloadTest`: exact20000bytes/owned bucket-key/no range; overlong; truncated; preexisting target preservation; nonpositive bound denied before storage access. Actual RED compilation fails only because `downloadTo` is not implemented yet, `.local/social-v2-g5-download-red.log` (not five executed failing tests). The earlier wrong-working-directory wrapper attempt failed before Maven and was corrected; it is not a product failure or a successful dependency download.

GREEN5tests,0failure/error/skip,2.526s/Maven25.241s: `.local/social-v2-g5-download-green.log`. The SDK input stream's abort behavior is asserted on failed copies.

New single method `S3ObjectStorageTest#downloadsCompleteOwnedObjectForProbeWithExactSizeBounds` uses actual disposable MinIO/S3 SDK PUT then full GETs, not response mocks:20000exactbytes recovered, smaller/larger expected-size bounds fail and leave no partial files. **1PASS8.642s/Maven22.217s**,0skip: `.local/social-v2-g5-download-minio.log`. Only that new method runs; the unchanged older presign/overwrite test is not replayed. Payload is arbitrary binary storage data, **not** a verified MP4/WebM, upload-permission or codec test.

No completeVideo/catalog-purpose/READY/attachment/API/schema/client/UI/feature-flag change in this increment. No full Maven/browser suite, current-image rebuild, recovered data access, normal service restart, remote Git/CI/push, activation or deploy. Normal V2/video flags remainOFF. The previous API image is historical after these input changes; do not call it a current-tree build. Web source/image inputs are unchanged.

## Next implementation

Subsequent same-turn progress adds `MediaProbeProcess`: ProcessBuilder argument-list execution (no implicit shell), closed stdin, positive wall deadline, concurrent capped32KiB stdout/stderr collection, nonzero/timeout/oversized-output failures and cleanup of the owned process. `MediaProbeProcessTest` first fails compilation for the missing class (`.local/social-v2-g5-process-red.log`), then **4PASS0.772s/Maven16.215s**,0skip (`.local/social-v2-g5-process-green.log`). Those tests execute actual short-lived Java child processes for JSON output/nonzero exit/deadline/output overflow, not mocked Process objects. They do not run ffprobe/ffmpeg or prove codec/video validation; the runner is not yet called by upload completion. Previous five download unit tests and MinIO method are not replayed because their code did not change.

Task2 is actual bounded local-file probe plus trustworthy metadata validation, followed by catalog purpose/permission/flag, mixed associations/read contract, uploader/playback and local acceptance. Do not stop at this storage helper or mark the whole project complete. Staging no longer blocks these tasks under the owner's new direction; unrun external operating/deployment facts remain unproved rather than fabricated.

Next action: Implement and verify the bounded media-probe subprocess/metadata path against real MP4/WebM fixtures.
Required from you: Nothing
Completion condition: Genuine fail-closed video probe evidence, then continued purpose/mixed-media integration under the approved full plan.
