# CoVie Social Product Card — Phase 4 execution and release gate

Implementation branch: `feature/social-product-card-phase2`, PR #28.

## Delivered automation

- `apps/web/e2e/social-phase4.staging.spec.ts`: Playwright browser test at 360/768/1280/1440; Social/Catalog toggle; keyboard Escape, lazy comments and no premature comment reads; opt-in authenticated Like/Save/Comment CRUD with best-effort cleanup.
- `infra/perf/scenarios.mjs`: bounded public-social-summary and public-social-comments load scenarios, with provisional p95 target 400ms and request error ratio less than 1%. `infra/perf/social-scenarios.test.mjs` checks paths, UUID validation and the 40-ID API limit.
- `infra/scripts/social-phase4-smoke.sh`: read-only HTTPS staging checks for public social JSON response, anonymous viewer flags, comments and protected mutation/admin preview endpoints.
- `infra/scripts/run-social-phase4-staging.sh`: operator-controlled end-to-end staging runner that emits manifest, logs, perf JSON and Playwright output; **never run on production**.
- Existing `infra/deploy/rehearse-release.sh`, `infra/deploy/rollback.sh`, `infra/scripts/run-staging-rehearsal.sh` remain the canonical release/migration/rollback machinery. This phase intentionally does not invent a separate deployment engine.

## How to execute

1. Deploy the candidate **from a reviewed SHA** to isolated staging with V018 and V019 applied. Record the image digest release manifest and restore point.
2. Populate test data: published item and active merchant, known discovery search path including location, independent buyer/seller/admin staging accounts. Never commit real credentials.
3. Set `COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST` to the reviewed immutable candidate release manifest, and `COVIE_SOCIAL_PHASE4_ACTIVE_MANIFEST` to the manifest retrieved from the running staging host. The script requires byte-identical manifests, digest-pinned images, V019, and GIT_SHA equal to the local checkout. File equality relies on an operator-trusted staging manifest capture; independently review the deployment source.\n4. Set `COVIE_STAGING_BASE_URL` (HTTPS staging only), `COVIE_SOCIAL_PHASE4_ITEM_ID`, `COVIE_SOCIAL_LOAD_ITEM_IDS`, `COVIE_SOCIAL_E2E_ITEM_NAME`, `COVIE_SOCIAL_E2E_SEARCH_PATH` and the 6 staging authentication variables described in `apps/web/e2e/fixtures/staging-auth-fixture.ts`.
5. Set `COVIE_SOCIAL_E2E_MUTATIONS_OK=1` only for disposable staging test accounts and `COVIE_SOCIAL_PHASE4_CONFIRM=STAGING_ONLY`. Then run:

```sh
bash infra/scripts/run-social-phase4-staging.sh
```

6. Review the emitted manifest, performance JSON, browser screenshots and Playwright traces. Ensure every test actually executed; a skip is not a PASS. Do not change the environment and claim older results represent a newer head.
7. In admin staging, manually exercise report -> moderation case -> HIDE -> public comment removal/count decrease -> audit/outbox/notification -> RESTORE -> count recovery. Protect admin access and verify anonymous/non-admin rejection.
9. Run existing `pnpm --filter @covie/web test:e2e:staging` P0 buyer/seller/admin order regression suite. Record PASS evidence.
8. Run existing `infra/deploy/rehearse-release.sh` staging restore/digest rollback drill with an operator-provided staging config and immutable manifest. Confirm that rolling back application code **does not remove V018/V019 tables, user comments or reports**.
10. Confirm CodeQL, Trivy, Gitleaks, Maven, frontend/contract all green against the same PR head; review monitoring and release rollback owner; only then mark merge eligible.

## Production rollout strategy

- Prepare immutable digest-pinned app manifests. Confirm backup, migration and rollback prerequisites first.
- Prefer staging -> guarded low-traffic rollout -> full traffic after error/latency and moderation health are verified. If traffic splitting is unsupported by the currently deployed platform, use a normal controlled deployment with immediate rollback access rather than pretending a canary is present.
- Measure health checks, social API 5xx/429 ratio, p95 latency, DB pool and social table growth. **No fabricated performance result is acceptable.** The provisional 400ms targets are not approved SLOs until a production-like baseline is captured.
- Revert to previous application image manifests on regression; Flyway V018/V019 are additive, forward-only, and should not be destructively reverted.
- Never execute load tests on production without a separate explicit authorization and rate limit.

## Evidence status

- GitHub CI: must be checked again on latest PR head.
- Playwright real staging run: **PENDING** until screenshots/traces or authenticated runner logs exist.
- API load measurements: **PENDING** until representative staging JSON outputs exist.
- Manual moderation + rollback rehearsal + checkout regression: **PENDING** until recorded.
- Merge main: **BLOCKED while any required gate remains pending or failing**.

This phase builds the verification tools, but it does **not** claim staging measurements, manual browser review or deployment without actual evidence. The original 702-line plan was not attached in this workspace; any more specific numeric limits must be reconciled with that source before the final sign-off.
