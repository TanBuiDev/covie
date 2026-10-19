# Social Product Card 2.0 — G5 baseline and storage verifier correction

## Scope and technical prerequisites

All owner approvals persist, including separate approvals. G5 nevertheless says
to begin video only after G1–G3 and storage/content-safety prerequisites. Their
remaining technical and browser gates are not proved by approval. Catalog video
is not implemented or activated by this checkpoint; existing AD_BANNER video
behavior is preserved. All V2 flags remain OFF; no staging exists.

Source inspection confirms the plan's baseline:

- MediaPurpose has no CATALOG_VIDEO. Catalog create/edit attach READY, owned
  CATALOG_IMAGE assets; the relation's media_type constraint only accepts IMAGE.
- MP4/WebM upload currently belongs to AD_BANNER, with a 50 MiB constant from
  StorageProperties.maxVideoBytes(), not an independently configurable setting.
- Upload completion checks storage HEAD size/type and container prefix magic.
  These are not codec, duration, playable-content or full-decoding proof. Existing
  ad-video tests include a short MP4 header fixture, not a playable video fixture.
- ObjectStorage exposes presignPut/head/readPrefix/delete, not a trusted metadata
  probe pipeline. MediaAssetEntity has no duration/codec/poster provenance model.
  Existing DB width/height columns do not establish a working probe.
- No ffprobe/ffmpeg command was found on this workstation's PATH. The API Dockerfile
  has no explicit probe installation step; this does not inspect deployed images.
- Pending cleanup and multipart lifecycle policy exist. They do not establish a
  READY orphan/video-job cleanup contract or actual storage quota behavior.
- MediaUrlResolver constructs URLs from the trusted configured public base and
  READY asset key. URL configuration validation is not evidence that a CDN/object
  can no longer be retrieved after item hide or permission revocation. No live
  bucket, CDN access policy or deployment environment was inspected.

Existing unchanged media/ad-video tests were inspected, not rerun or duplicated.
No catalog video schema, purpose, uploader or playback was added before its gates.

## Proven verifier defect and minimal fix

The hardening apply script enables four public-access-block fields. Previously,
verify-s3-hardening.sh only checked whether get-public-access-block succeeded,
discarding its response. A response with a disabled field therefore still printed
the success message. AWS documents these as four separate boolean controls:
[get-public-access-block](https://docs.aws.amazon.com/cli/latest/reference/s3api/get-public-access-block.html).

The verifier now queries those four fields and requires each to be True; missing,
extra or multiline output fails closed. Existing encryption/versioning/CORS/
lifecycle checks and invocation arguments remain unchanged. This does not upgrade
those other checks into comprehensive policy validation.

Added one focused regression script, scripts/tests/verify-s3-hardening.test.sh.
It exports a stub aws function and uses dummy bucket/endpoint values: no cloud
credentials, network requests or bucket modifications occur.

## Verification evidence

- RED: .local/social-v2-storage-public-block-red.log, exit 1. The original verifier
  prints success and the regression detects acceptance with BlockPublicAcls=False.
- GREEN: .local/social-v2-storage-public-block-green.log, exit 0. All four true
  fields pass; each individual false field, a missing field and failed CLI read
  are rejected. The test runs in real Git Bash with stubbed CLI responses.
- bash -n was run separately for both shell files and passed. Targeted
  git diff --check passed for the verifier.

No Maven/BE suite, regenerated client, runtime migration, recovered-data write,
restart, flag activation, commit, deployment or live S3 verification occurred.

## Figma input and remaining work

After the owner opened Figma desktop, the connected metadata request without a
guessed node returned only page 0:1, “00 — Cover & Index”, and no selected nodes.
This does not prove private profile/activity designs are absent. Requested Copy
link to selection for the intended exact frame; G4 UI is still unfinished.

Next: obtain G1–G3 storage/privacy evidence and exact private activity design;
continue approved work without substituting mocked checks for live storage,
staging or browser gates. The whole Social V2 objective remains active.
