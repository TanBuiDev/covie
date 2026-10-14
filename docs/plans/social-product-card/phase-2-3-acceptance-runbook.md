# CoVie — Phase 2 + Phase 3 acceptance runbook

Scope: PR [#28](https://github.com/TanBuiDev/covie/pull/28). This is an evidence ledger, **not** a claim that staging was tested.
Only change a row to PASS when the corresponding observed result and evidence exist.

## 1. Before QA

1. Use a dedicated staging environment with the V018 and V019 Flyway migrations applied. Back up staging before creating reports or other test data.
2. Pin evidence to the **exact PR head SHA**. CI for an older commit is insufficient after another push.
3. Record the staging app build SHA and API build SHA. Never paste passwords, bearer tokens, or session cookies into screenshots or artifacts.
4. Prepare four identities: guest, standard buyer, standard seller, and admin. Use separate browser contexts (not just tabs); do not reuse credentials or reaction state.
5. Prepare a published item under an active merchant and a second published item under a different merchant; prepare hidden and suspended-merchant fixtures via supported admin controls.

## 2. Automated checks (required)

- [ ] GitHub PR: backend, frontend-and-contract, release-security-source, secret-history and CodeQL all pass on the same SHA.
- [ ] Confirm Maven output reports 0 failures and 0 errors, including `CatalogSocialControllerIntegrationTest` and `PopulatedMigrationUpgradeTest`.
- [ ] Confirm OpenAPI JSON and generated TypeScript client show no Git diff after generation.
- [ ] Confirm frontend lint, typecheck, unit tests and production build pass.
- [ ] Run the existing staging P0 suite in an isolated environment with configured staging test credentials, as documented in `apps/web/e2e/fixtures/staging-auth-fixture.ts`:

```sh
# Run from repo root. Set COVIE_STAGING_BASE_URL and the staging fixture credentials
# in the runner environment, never in committed files.
pnpm --filter @covie/web test:e2e:staging
```

Preserve output, Playwright screenshots/traces on failure, runner identity, SHA and timestamp. The normal CI workflow does **not** execute staging P0 Playwright tests.

## 3. Responsive and keyboard QA

For each of 360 × 800, 768 × 1024, 1280 × 800, and 1440 × 900:

- [ ] Open Home and Search. Toggle "Khám phá" (Social) -> "Mua sắm nhanh" (Catalog) -> Social. Layout must not reflow unexpectedly or overflow horizontally.
- [ ] Verify long item/merchant names, empty images, long price and distance strings, in-feed ads, location selector and item detail links.
- [ ] Observe shared item appearing in two sections. Like in one and verify both representations receive **server-confirmed** state. Repeat Save. Refresh and verify state.
- [ ] Open comments: first page loads without a second click; empty/loading/error/next-page states are distinguishable.
- [ ] Submit a 2-character comment, then a 1000-character comment; block shorter/longer input, preserve text on server failure, do not duplicate on double-submit.
- [ ] Navigate entirely with Tab/Shift+Tab/Enter/Space and close comment panel with Escape. Verify visible focus, disabled busy controls and understandable labels.

Record browser, viewport, URL, item ID and screenshot/trace for each failed case.

## 4. Security, authorization, data correctness

- [ ] Guest can read public Social counts and public comments, but cannot Like/Save/Comment/Report. Expired sessions are handled without exposing private flags.
- [ ] Buyer Like/Save PUT and DELETE are idempotent. No cross-account leak of `liked` or `saved` (two independent contexts).
- [ ] A different user cannot delete another user's comment (404); author can delete their own; deleted/hidden comments do not contribute to count.
- [ ] Suspended/hidden merchants and inactive/unpublished catalog items are excluded from social summary, comments and mutations.
- [ ] Comment rate limit under rapid submissions returns 429; never show a success state for rejected requests.
- [ ] Existing CSRF requirements are enforced for cookie-authenticated writes; bearer authorization does not weaken the site's security boundary.
- [ ] Guest/non-admin cannot read `GET /api/v1/admin/catalog-social/comments/{commentId}`, including an existing hidden comment.
- [ ] Admin can preview the reported comment and decide HIDE/RESTORE through the existing case UI; check audit history and notifications, and verify public count changes accordingly.
- [ ] Retest cart/checkout, Search filters, locations, ads and merchant detail with both view modes.

## 5. Merge gate

Record these fields before changing PR from Draft:

| Field | Recorded evidence |
| --- | --- |
| GitHub head SHA | PENDING |
| Backend CI / unit + integration | PENDING (verify latest head) |
| Frontend CI / contract drift | PENDING (verify latest head) |
| Security checks | PENDING (verify latest head) |
| Staging P0 Playwright | PENDING |
| Responsive screenshots (all 4 widths) | PENDING |
| Auth/CSRF/manual moderation test | PENDING |
| Product/Cart/Checkout/Ads regression | PENDING |
| Rollback owner and rehearsal | PENDING |

**Do not merge or deploy** while any required field is PENDING/FAIL or when CI green refers to an older SHA.
If a database migration has already run, roll back application code using the normal deployment procedure; never destructively reverse V018/V019 or delete user-submitted comments.
