# Social V2 — current-source API production packaging/static image

Date:2026-10-16. Safe local G6 packaging evidence, not runtime health, test-suite or staging acceptance.

## Source and configured build

Fresh whitelist `.local/social-v2-api-production-current-20261016`: configured API Dockerfile/ignore, Linux Maven wrapper/pom, `.mvn` and `src`, recursively excluding runtime directories, dependencies/target/cache/results, `.env*`, PEM/key and tsbuildinfo. No existing environment, private key or recovered/runtime database is copied. Unexpected reparse points fail preparation. Host original target and normal services are not used by Docker layers.

**1116source/context files SHA256 match before/after build and actual static inspection.** Independent current path enumeration via ripgrep and separate context file enumeration both match1116manifest entries, zero extra/missing. Generated evidence `.local/social-v2-api-production-current-source-manifest.json` and `...-source-check.json`. This is a current dirty-source snapshot, not a new immutable commit/release identity.

```text
docker build --progress=plain -f <fresh-context>/apps/api/Dockerfile -t covie-api:social-v2-current-verification-20261016 <fresh-context>
COVIE_VERIFY_API_RUNTIME=false bash infra/scripts/verify-api-image.sh covie-api:social-v2-current-verification-20261016
```

Existing Dockerfile unchanged: Temurin25JDK build/25JRE-alpine runner. Configured dependency go-offline stage succeeds (Maven59.763s). `-DskipTests package` compiles803main and275test source files and produces the repackaged executable Spring Boot JAR (Maven34.990s), **build exit0** in `.local/social-v2-api-production-current-build.log`. It explicitly says **Tests are skipped**: do not call this275passing tests or a full/current acceptance suite. No package/plugin/version change or fallback toolchain used.

Local image inspect: ID`sha256:43b150d64a54649e98034583110730879132a5efa1d71473287d7b064d3b5b9d`, size502206904bytes, user10001:10001. Local RepoDigests contains corresponding `covie-api@sha256:43b150d64a54649e98034583110730879132a5efa1d71473287d7b064d3b5b9d`; not registry push, signing, deployment or approved release proof.

## Actual corrected static verifier

Corrected verifier runs against the real new image, exports/lists its filesystem once and applies the known forbidden-file/directory checks. Exit0, `.local/social-v2-api-production-current-static.log`:

```text
API image static contract OK (user=10001:10001).
Runtime startup/health check skipped; set COVIE_VERIFY_API_RUNTIME=true with disposable DB/Redis/JWT production-shaped env to execute it.
```

Runtime was explicitly disabled, not silently passed or failed due missing configuration. No Java API container process, health request, production-profile boot, Flyway apply or database service was started here. Known filesystem path inspection is not an exhaustive embedded-JAR/source/secret/CVE audit. Prior stub negative regression remains separate and unchanged, not rerun to inflate evidence.

Verifier-owned created container removed; Docker listing for the new image has no remaining containers. Normal3000/8081 and verifier ports33000/38080 absent/untouched. All1116source/context hashes and both independent path sets still match. Bash syntax/focused whitespace checks PASS.

No BE/FE/API/schema/client/CSS/Dockerfile/ignore/verifier business change, full suites/old successful targeted tests/browser/web-image rerun, recovered/runtime DB access, normal restart, actual credentials or secret files accessed, remote Git/commit/branch/CI/registry push, activation or deploy. Existing current web593file build remains valid; API packaging does not modify its inputs. Whole objective ACTIVE; normal V2/video flagsOFF.

## Remaining scope and next action

Current API **packaging/static artifact gate LOCAL PASS only**. Boot/runtime health needs a separate controlled fixture with fresh DB/Redis and generated disposable keys/env. Use no existing runtime containers/volumes/credentials or normal ports. It must distinguish actual production-profile boot/migration/health from staging/populated-data/load/recovery/full-auth acceptance. The latter remain unproved; G5 work and its entry/activation restrictions remain intact.

Next action: Inspect production configuration requirements and prepare an owned disposable DB/Redis/generated-key API runtime-health fixture.
Required from you: Nothing
Completion condition: Actual boot/health proof can run safely without existing data/credentials, normal service changes or static-only/staging mislabeling.
