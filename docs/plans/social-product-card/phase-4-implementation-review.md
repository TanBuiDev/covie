# CoVie — Social Product Card Phase 4 acceptance and controlled rollout

PR: https://github.com/TanBuiDev/covie/pull/28
Branch: `feature/social-product-card-phase2`

## Implementation shipped to the PR branch

- `apps/web/e2e/social-phase4.staging.spec.ts`: four browser widths (360/768/1280/1440), Social/Catalog toggles, focus/Escape, comment lazy fetch and opt-in authenticated Like/Save/Comment CRUD.
- `infra/perf/scenarios.mjs`: bounded Social Summary and Comment read-load scenarios. `infra/perf/social-scenarios.test.mjs`: scenario contract checks.
- `infra/scripts/social-phase4-smoke.sh`: non-mutating public JSON and security checks against an explicit HTTPS staging origin.
- `infra/scripts/run-social-phase4-staging.sh`: fail-closed staging verification with evidence manifest, social smoke, performance measurements and Playwright output.
- Existing `infra/deploy/rehearse-release.sh`, `infra/deploy/rollback.sh` and `infra/scripts/run-staging-rehearsal.sh` remain canonical for disaster recovery. No parallel deployment engine was added.

## Reproducible staging acceptance

1. Review the PR commit and deploy its immutable digest-pinned API/Web images to isolated staging. Back up staging and apply Flyway V018/V019.
2. Seed a published catalog item under an active merchant and an exact search URL path with valid location; create independent buyer, seller and admin test accounts.
3. Supply `COVIE_SOCIAL_PHASE4_RELEASE_MANIFEST` (candidate immutable manifest) and `COVIE_SOCIAL_PHASE4_ACTIVE_MANIFEST` (operator-trusted capture from deployed staging). The runner checks byte equality, GIT_SHA against checkout, API/Web image digests and V019.
4. Supply `COVIE_STAGING_BASE_URL` (HTTPS staging, never production), `COVIE_SOCIAL_PHASE4_ITEM_ID`, `COVIE_SOCIAL_LOAD_ITEM_IDS` (1–40 distinct public UUIDs), `COVIE_SOCIAL_E2E_ITEM_NAME`, `COVIE_SOCIAL_E2E_SEARCH_PATH`, plus `STAGING_BUYER_EMAIL/PASSWORD`, `STAGING_SELLER_EMAIL/PASSWORD` and `STAGING_ADMIN_EMAIL/PASSWORD` via the runner environment, not committed files.
5. Use disposable test identities. Explicitly set `COVIE_SOCIAL_PHASE4_CONFIRM=STAGING_ONLY` and `COVIE_SOCIAL_E2E_MUTATIONS_OK=1`, then run:

```bash
bash infra/scripts/run-social-phase4-staging.sh
```

6. Verify the output `manifest.txt`, load JSON, security smoke logs, Playwright screenshots/traces and test execution count. Skips, missing credentials, release SHA mismatch or missing evidence are **not PASS**.
7. Manually validate admin report -> HIDE -> audit/outbox -> public comment count and visibility -> RESTORE, using the existing moderation case UI.
8. Execute the existing `pnpm --filter @covie/web test:e2e:staging` end-to-end buyer/seller/admin regression suite to cover cart, checkout, needs, merchant operations and reviews.
9. Perform the existing staging backup/restore and rollback drill with `infra/deploy/rehearse-release.sh`. Record the restore operator, evidence and immutable image digests. Do not destructively roll back V018/V019 or delete user-submitted social data.
10. Confirm all required GitHub backend, frontend/contract, CodeQL, source scanner and secret checks are green **for the same PR head SHA**. Obtain release owner sign-off before marking the PR mergeable.

## Performance targets and production ramp

The Phase 4 public social read scenarios initially use a provisional **p95 <= 400 ms** and **error ratio < 1%**, based on existing repository load patterns, *not* a measured CoVie baseline. Calibrate with production-like staging data before calling them approved SLOs. Review query plans and database pool pressure; don't infer production readiness from low-volume tests.

Use staging first. If the deployment infrastructure supports canary traffic, apply a small, monitored rollout; otherwise use a normal low-traffic deploy with immediately available digest rollback. Watch 5xx, 429 rates, p95, moderation throughput, DB saturation and social-table growth. Revert application manifests when required, leaving additive V018/V019 schema intact.

## Outstanding evidence — do not merge while any item is pending

| Gate | Status |
| --- | --- |
| Latest commit CI | PENDING latest head confirmation |
| Real staging Playwright at all four viewports | PENDING |
| Social API baseline load results | PENDING |
| Manual admin/CSRF/ownership and moderation workflow | PENDING |
| Staging P0 buyer/seller/admin regression | PENDING |
| Backup, restore and app rollback rehearsal | PENDING |
| Release owner sign-off | PENDING |

The original 702-line plan was not attached here. Reconcile its exact numerical SLOs and release restrictions with this evidence before final sign-off.
