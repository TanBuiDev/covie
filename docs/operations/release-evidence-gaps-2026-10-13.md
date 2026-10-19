# Release evidence gaps — 2026-10-13

Scope: committed main 2f9888c2 and current release workflow; source review, not deployment approval.

## CI skips and available evidence

| Check | Main CI | Existing focused evidence | Remaining scope |
|---|---|---|---|
| SsrRefreshBrowserIntegrationTest (2 cases) | Opt-in property absent; skipped | .local/backend-ssr-current-20261012.log: 2 passed, no skips | Production HTTPS/proxy cookie behavior is not proved by local browser test |
| PilotFixtureIntegrationTest | Opt-in property absent; skipped | .local/backend-pilot-current-20261012.log: fixture case passed plus 4 plan-validator cases | Does not establish deployed API latency/concurrency objectives |
| HotQueryPlanIntegrationTest | No COVIE_PERF_JDBC_URL/query file; skipped | No direct execution log found in the focused hot/pilot/performance log search; pilot fixture executes repository plans separately | Explicit release DB/query-file gate remains unproven |

No changed Java/auth source was found in the working tree. Do not repeat the full backend suite to eliminate intentional skips. Main CI counts 911 executed/reported cases including 4 skips, not 911 successful assertions.

## Release workflow gap

GitHub `gh run list --workflow release.yml` returned no runs on this inspection. This is distinct from the successful ordinary CI run 38283925907.

`.github/workflows/release.yml` makes production depend on `build-release` and `deploy-staging`. The staging job invokes `deploy.sh`, whose public smoke covers health, catalog, headers, anonymous rejection and exposed ports. It does not invoke `run-staging-rehearsal.sh` or the normal-auth P0, load, restore, rollback and alert hooks required by `docs/operations/staging-rehearsal.md` and the pilot checklist. Therefore the label "staging-approved digest" alone is not evidence of the required rehearsal. Environment approval does not automatically run those checks.

Next required source action: connect staging rehearsal to the release dependency before production, using a reviewed staging-only host configuration and retaining evidence tied to the exact release digest. Do not substitute a marker, guest-only smoke, or an arbitrary successful command for the committed rehearsal. Real execution requires the specified staging database, separate restore DB, Redis, bucket, HTTPS domain, normal accounts and controlled hooks; these are not currently supplied. No workflow dispatch, provider call, database writes or live deployment was performed.

## Current local preflight repairs

- verify-runtime-env.sh now requires API_INTERNAL_BASE_URL; missing-variable fixture rejected, example accepted.
- rollback.sh validates runtime environment and readable TLS files before pulling images; missing-env Docker-spy fixture rejected before Docker invocation.

These are local changes, not published release artifacts. They do not replace the staging rehearsal.

## Source repair: staging rehearsal now blocks promotion

The release workflow invokes infra/deploy/rehearse-release.sh after staging deployment. Production already depends on deploy-staging, so failure propagates through that dependency. The wrapper requires an external operator-managed staging configuration, executes the existing run-staging-rehearsal.sh and compares a snapshotted candidate manifest with release-state current.env before and after the drill. Source configuration details are in staging-rehearsal.md; no credentials or synthetic PASS marker were added.

Focused persisted regression: infra/scripts/tests/release-rehearsal-gate.sh. Actual Git Bash fixture verifies success, failed rehearsal, wrong initial manifest and drift after rehearsal (PASS). Its rehearsal runner is mocked: these assertions prove gate orchestration only, not P0/load/restore/alerts. Shell syntax and diff checks pass; static workflow dependency check passes. YAML parser validation was attempted but unavailable (neither root Node yaml nor Python PyYAML installed); no package downloaded. GitHub workflow execution remains unverified. Actual staging configuration, deployment/rehearsal and immutable runtime release evidence still required; no workflow dispatch, remote push or live mutation performed.

## Workflow parser verification completed

Resolved the parser limitation using the already installed node_modules/.pnpm/js-yaml@4.3.2/node_modules/js-yaml (no downloads). Parsed .github/workflows/release.yml successfully; asserted that staging deploy exists, rehearsal follows it, and deploy-production depends on deploy-staging. This is local YAML/structure verification, not GitHub Actions execution or deployment evidence. The earlier missing-parser note is historical and no longer an outstanding source verification gap.

## Current-state checkpoint after merge and SSR verification

Main8df8c516 CI38388224661 and CodeQL workflow38388224862 are completedSUCCESS. Existing SSR opt-in2cases executed on clean-mainNext16.4.0 build with disposable PG/Redis/headlessChrome:2PASS0skip at2026-10-13 18:13:11+07. No source fix required. Braces/micromatch/fast-glob absent from203parsed standalone package manifests including symlink traversal; immutable runtime image scan still required.

Remaining release evidence depends on a real staging configuration: HTTPS/domain/certificates and proxy cookie checks, normal Google onboarding/accounts, private object storage/CORS, deployed latency/load, off-host backup and separate restore DB, digest rollback and monitoring notification delivery. No VPS/domain/staging resources have been supplied in this session; services on the ordinary local environment remain stopped. Do not substitute ordinary CI, standalone package inspection or local browser routing for these requirements. No current Java/API mismatch is established by these missing environment checks. Preserve verified business modules and leave the overall completion claim unproven.
