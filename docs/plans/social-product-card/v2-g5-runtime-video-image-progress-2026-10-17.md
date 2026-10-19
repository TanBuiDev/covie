# Social V2 — current API runtime video tooling

Date:2026-10-17. Whole G0–G6 objective remains ACTIVE. The preceding prompt-only response was not implementation progress; this checkpoint closes an observed runtime dependency gap, not the full video chain.

## Defect and minimal correction

`CatalogVideoConfiguration` defaults to PATH executable names `ffprobe` and `ffmpeg`. Actual previous API image execution fails exit127: `ffprobe: executable file not found in $PATH`, `.local/social-v2-g5-runtime-probe-red.log`. Previously prepared separate video-tool image did not prove API runtime support.

Only production change: `apps/api/Dockerfile` installs Alpine `ffmpeg` with `apk add --no-cache ffmpeg` before existing non-root user. Both executables are supplied. JDK25/JRE25, executable-JAR build, entrypoint, ports, business code, API/generated client/migrations and normal OFF defaults unchanged.

## Current-source packaging and provenance

Fresh whitelist context `.local/social-v2-g5-runtime-image-71e00a9516ed4821922aca121dac5fbc`: Dockerfile/ignore, wrapper/pom, `.mvn`, API `src`; no existing runtime environment, dependencies, original target, keys or recovered data. Independent ripgrep source paths and context enumeration both match1140 SHA256 manifest entries; zero missing/extra paths, source/context hash differences or source reparse points after runtime checks.

Evidence `.local/social-v2-g5-runtime-image-source-manifest.json`, `...-source-check.json`, `...-result.json`, `...-inspect.json`, `.local/social-v2-g5-runtime-final-provenance.json`.

Actual Docker build exit0, `.local/social-v2-g5-runtime-image-build.log`. `-DskipTests package` compiles application/test sources and packages executable JAR; tests explicitly skipped, not passing full suite. Authenticated Flyway repository metadata warnings were nonfatal; no supplied credentials/workaround.

Image `covie-api:social-v2-video-runtime-20261017`, localID `sha256:c3159a643cc38c10468d53ce5e2cdf150211f0312b21753a73d47edd1d6ad5cb`, size685692953bytes, user10001:10001. Previous image502206904bytes; observed packaging increase, not cost/bandwidth benchmark. Local identity is not pushed/signed release evidence.

Actual existing static verifier exit0, `.local/social-v2-g5-runtime-static.log`: known forbidden paths absent, configured non-root retained. Its runtime mode explicitly disabled; independent actual boot below. Not exhaustive embedded-secret/CVE/licensing audit.

## Packaged-JAR probe/decode

`.local/social-v2-g5-runtime-probe-green.log`, exit0. Actual API-image disposable container, networkNONE,512MiB/1CPU, UID10001, installedFFmpeg/ffprobe8.1.2. Generate actual one-second64×48 H264/AAC MP4 and VP9/Opus WebM. Ignored Java assertion fixture calls production classes/dependencies extracted from **this image's `/app/app.jar`**, not copied replacement application classes or old video-tool image.

Packaged `CatalogVideoProbe.inspect` actually probes and fully decodes both: MP41000ms/h264 and WebM1008ms/vp9, expected dimensions checked. Same PATH executable names as default configuration. Changed runtime packaging justifies this focused repeat; unchanged broader invalid-file matrix not replayed. This is not upload/READY/S3/browser proof, arbitrary MP4/transcoding/poster/audio-playback support or whole-endpoint latency proof.

## Actual prod-profile boot

`.local/social-v2-g5-runtime-health.log` exit0; `.local/social-v2-g5-runtime-health-20261017/result.json` completedTRUE. Existing ignored runtime helper parametrized with explicit current image/digest/manifest, fresh directory and expected25migrations, retaining its historical defaults for previous callers.

Fresh disposable PG18/Redis8, generated RSA3072/password/HMAC, invalid external-provider placeholders, actual prod validators enabled. Application starts14.657s; Flyway validates/applies001–025 on empty disposable DB. Custom health200/ok, readiness/liveness/aggregate management200/UP; UID10001. Redis PONG, appendonlyYES/appendfsyncALWAYS/noeviction observed. Initial not-ready connection polling error retained, not terminal failure. Normal V2/video flagsOFF.

Not populated migration, real OAuth/S3, outage durability, load, staging or production proof. All owned probe/boot/static containers and boot network removed; generated secrets/env removed. Normal3000/8081 untouched/absent after checks; no original `.next`, full suites, existing/recovered DB, remoteGit/commit/branch/merge, push, activation or deploy. Dockerfile whitespace checkPASS. Web image remains historical after previous UI changes.

Next action: Run genuine combined Seller upload/signedPUT/probe/READY/mixed save-publish/authorized playback/moderation using current API image, disposable PG/MinIO and isolated Next/browser.
Required from you: Nothing
Completion condition: Actual combined video-chain evidence closes remaining integration gaps without mocked storage/probe/delivery, followed by remaining whole-plan local acceptance.
