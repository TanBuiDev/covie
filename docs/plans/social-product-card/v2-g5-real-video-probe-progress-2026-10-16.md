# Social V2 G5 — actual video metadata and decode

Date:2026-10-16, Asia/Saigon. Read the complete new goal attachment before work. Owner's staging waiver and all prior approvals persist; the full G0–G6 objective remains incomplete and active. Previous storage/process helper evidence is retained, not replayed or called a complete video feature.

## Implementation

Added `CatalogVideoProbe` for owned local files. It invokes actual configured ffprobe then ffmpeg using the existing bounded process runner, one shared timeout, file-only protocol and MOV/Matroska/WebM demuxer allowlists, capped process output, decoder threads1 and64MiB maximum individual allocation. That allocation setting is not a claim of total-process memory isolation. Empty/nonregular/symlink paths and nonvideo MIME are denied before invoking tools.

The metadata parser requires one actual video stream, positive dimensions and trustworthy positive duration≤60s. Milliseconds round upward, not down across the duration boundary. Supported initial playback profiles are H264/AAC MP4 and VP8/VP9 with Opus/Vorbis WebM (audio optional); incompatible container/codec/stream metadata is rejected rather than claiming arbitrary MP4 support. After metadata checks, ffmpeg decodes the full selected video/audio with exit-on-error, error threshold0 and empty-output rejection. No remote URL is downloaded, input rewritten/transcoded, poster generated or original file modified.

Tool option semantics were checked against primary [ffprobe documentation](https://ffmpeg.org/ffprobe.html) and [FFmpeg documentation](https://ffmpeg.org/ffmpeg.html). Those references alone are not execution evidence; real execution follows.

## Targeted evidence

New `CatalogVideoProbeMetadataTest` first fails compilation because the probe is absent (`.local/social-v2-g5-metadata-red.log`), then **4PASS0.739s/Maven15.582s**,0skip (`.local/social-v2-g5-metadata-green.log`). Checks include dimensions/precision, unsupported codec/container, missing/NaN/invalid dimensions, exact60s and60.0001s rejection. This is parser evidence, not an upload test.

Built an owned local tooling fixture image, not a production application build: `.local/social-v2-g5-video-tools/Dockerfile`, `.local/social-v2-g5-video-tools-build.log`. It adds Alpine FFmpeg8.1.2-r0 to the previously built Java25 image solely as a test runtime. Actual `ffprobe -version` is recorded in `.local/social-v2-g5-probe-tool-version.log`. No application sources/env/keys copied into that build context. Local imageID`sha256:3ac5efb25d4abef3cb2c178efd7faac710d30f28daccd22a6c7f7dd703e13774`, not a release artifact.

New opt-in `CatalogVideoProbeIntegrationTest` copies only the newly compiled probe/result/process classes, its standalone fixture class and three Jackson jars into an owned nonroot container with networknone,512MiB/1CPU, no host ports. It generates real64×48 video/audio fixtures and runs current Java probe code, not the old application JAR or mocked subprocesses:

- H264/AAC MP4, VP9/Opus WebM and VP8/Vorbis WebM: metadata matches codec/dimensions/duration and full decode succeeds.
- Actual MPEG4-codec MP4,61s H264 MP4, physically half-truncated fast-start MP4, empty file and MIME/container mismatch: rejected.
- Missing configured ffprobe executable: fails closed.

**First1PASS3.958s/Maven16.213s**,0skip, `.local/social-v2-g5-probe-real.log`. One executed integration method covers those fixture assertions; do not call it multiple independent test passes. Command:

```text
mvnw.cmd -o -Dtest=CatalogVideoProbeIntegrationTest -Dcovie.test.catalog-video-probe=true test
```

The metadata test is separately targeted. No old download/MinIO/process tests or full BE/browser suites rerun because their source did not change. No live DB/S3/auth/Next pipeline is exercised by this probe-container test.

## Boundaries and next work

Probe is not yet wired to upload completion; CATALOG_VIDEO purpose, flag/permission, metadata persistence, mixed associations/read contract, Seller uploader and actual browser playback remain implementation work. Duration is verified by this probe, **not yet enforced by the existing upload API**. Poster fallback and runtime capacity/timeout-to-retry behavior also remain integration work. Do not mark the helper or G5 COMPLETE.

Owned test containers removed; normal3000/8081 absent/untouched. Source/test hashes match the isolated validation copy; focused whitespace checks PASS. No credentials/recovered data, normal restart, applied-migration edit, remote Git/CI/push, activation or deployment. Normal flagsOFF; previous API production image remains historical after code changes, web inputs unchanged.

Next action: Wire trusted verification into the catalog-video upload/purpose/permission path with additive metadata persistence and target tests, then continue mixed-media/read/UI work.
Required from you: Nothing
Completion condition: Actual owned upload cannot become READY without successful verification; full G5 and the remaining plan continue afterward without staging blocking.
