# Social V2 — actual API production-profile boot/health

Date: 2026-10-16. Previous goal turn only acknowledged the UI direction: no implementation progress. This turn closes the separately recorded API local boot/health evidence gap, not the whole G6/release gate. Current project UI is authoritative; no Figma dependency or new approval request.

## Scope and isolation

Used the already-built, unchanged local API image `sha256:43b150d64a54649e98034583110730879132a5efa1d71473287d7b064d3b5b9d`, user10001:10001. No rebuild or old successful business/browser/full-suite rerun. All1116 API source-manifest hashes match before and after the verified runtime run.

Ignored local helper `.local/social-v2-api-runtime-health-20261016.ps1` creates a uniquely named Docker network and three owned containers only: fresh PostGIS18/3.6, Redis8-alpine, and the API. PG and Redis have tmpfs data, no published ports and no reused volumes/data. API bindings are loopback-only127.0.0.1:38080/39091. Every run generates new RSA3072 PKCS8/X509 PEMs, password and rate-limit HMAC key; no existing env/credentials/key file is read. Provider settings use explicitly disposable `.example.invalid` values, not working Google/S3 credentials. Redis authentication is configured in the final fixture, with AOF/appendfsync=always/noeviction; this is not a durable deployment because storage is deliberately ephemeral.

Production profile and its safety validators remain enabled; no `test`/`e2e` profile, feature flag, schema-validate or rate-limit bypass. Normal application flags/configuration are unchanged.

## Evidence and harness investigation

Initial helpers failed to recognize readiness, **not a product RED**. Actual PowerShell `Invoke-WebRequest` returns `byte[]` for the actuator vendor JSON content type; piping it directly to `ConvertFrom-Json` failed. The first poll also lacked a delay for a non-UP decoded response. A direct live probe independently confirmed HTTP200/UP while the second helper was still running; saved in `.local/social-v2-api-runtime-health-final-20261016/actual-probes.json`. Fixed only the ignored helper: explicitly decode byte arrays as UTF8, retain polling diagnostics and delay, clean up API before its dependencies. First fixture dependency-first teardown caused expected connection-loss log noise, not a runtime product failure. Prior failed logs are retained, not relabeled success.

Final helper command exits0 in `.local/social-v2-api-production-runtime-health-verified.log`. Structured evidence: `.local/social-v2-api-runtime-health-verified-20261016/result.json`; per-container logs are in that same ignored directory.

- Actual boot log: one active profile `prod`; `Started CovieApplication in 12.929 seconds`.
- Flyway validates22 and applies22 migrations to the **fresh empty** database, latestv022. Direct database history confirms001–022 all`true`. This is not populated migration/backward compatibility proof.
- HTTP200 `/api/v1/health`: `status=ok`.
- HTTP200 management `/actuator/health/readiness`, `/liveness`, and aggregate `/health`: `status=UP`. Readiness includesDB; aggregate health is stronger than the custom static `ok` controller alone. Production health details remain hidden.
- Actual container process UID10001. Disposable Redis authenticatedPING=PONG; actualCONFIG GET confirms appendonly=yes, appendfsync=always, maxmemory-policy=noeviction. This is not Redis outage/rate-limit behavior or persisted-AOF recovery proof.
- Final result `completed=true`, source hashes1116before/after, generated secret files removed. Initial connection failures during startup are retained as diagnostics, not final health failures.

Afterward independently checked no owned `covie-v2-*` containers or `covie-v2-health-*` networks remain and normal3000/8081 plus owned38080/39091 are not listening. All three runs' generated env/PEM/Redis config files have been removed. No recovered/runtime DB access or normal service start/restart/stop. No production BE/FE/API/schema/client/Dockerfile/verifier changes, remote Git/CI/registry push, activation or deploy.

## Remaining requirements

API packaging/static inspection and actual production-profile boot/empty migration/health are now locally verified. Not staging, real OAuth/storage/CDN, populated migration, whole auth/business acceptance, load/security scan, backup/restore/application rollback, signed release or owner release sign-off. Current web593-file build is untouched. Whole Social V2 remains ACTIVE, including missing G5 video work and its actual entry/activation restrictions; normal V2/video flagsOFF.

Next safe candidate comes from the literal12.1.7/12.2/12.4 recovery requirement: current `verify-restored-invariants.sh` checks seven legacy table counts and Flyway only, not Social tables/caption/media. This is a source coverage observation, not yet proof that backup/restore loses data or authorization to expand unrelated infrastructure. Inspect the recovery runbook/fixture requirements and demonstrate any actual acceptance gap before changing it. Do not run the staging rehearsal or destructive restore against existing data.

Next action: Trace Social recovery requirements against the existing invariant verifier and design one owned disposable recovery check if a concrete gap is established.
Required from you: Nothing
Completion condition: A scoped evidence-based recovery next step, preserving existing data and distinguishing local preparation from actual staging rehearsal.
