# Seller Mobile — CI Verification

- Pull request: `#5`
- Head branch: `feature/seller-mobile-figma-ui`
- PR target: `main`
- PR state: open, ready for review, unmerged
- Verification state: `CI_TRIGGER_BLOCKED`

## Workflow expected by the repository

`.github/workflows/ci.yml` runs on `pull_request` and on pushes to `main` or `codex/plan-10-*`. Its jobs cover backend verify, OpenAPI/client drift, API client checks, web typecheck/lint/test/build, secret hygiene and source security scanning.

## Trigger attempts from this connector session

1. Opened PR `#5` as Draft from `feature/seller-mobile-figma-ui` to `main`.
   - result: no workflow run/check suite created.
2. Marked PR `#5` Ready for review and pushed normal `synchronize` commits.
   - result: no workflow run/check suite created.
3. Verified PR mergeability separately; GitHub reported the PR as mergeable/clean, so missing runs were not caused by a merge conflict.
4. Created temporary verification branch `codex/plan-10-seller-mobile-ci` from the Seller implementation head and pushed a docs-only trigger marker. This branch name matches the workflow's explicit push trigger.
   - result: no push workflow run created.
5. Historical repository Actions runs are visible for other branches/PRs, confirming the repository has functioning Actions history.
6. The connected GitHub tool surface exposes run inspection/rerun actions but no workflow-dispatch action, so a fresh run cannot be forced from this session.

## Conclusion

Connector-created GitHub mutations do not produce the workflow events needed to start this repository's Actions workflow in the current environment. This is an execution/connector limitation. It is **not** evidence that CI passed, failed, or that the code is runtime-valid.

The temporary branch `codex/plan-10-seller-mobile-ci` may remain because the connector does not expose a delete-ref operation. It contains no product-code divergence of interest; it was created only to test the workflow's documented push trigger.

## Remote/static checks completed instead

- Figma production frames were mapped from page `04 — Seller Mobile` (`3:5`).
- Generated OpenAPI and backend services/validators were cross-checked for the interactive Seller flows.
- Seller Order commands remain `allowedActions`-owned; command header/body contracts were inspected against generated schema.
- Catalog category attributes and service variant offers were cross-checked against backend validators/services.
- Current-code searches found no Seller `window.location.reload()`, `window.location.assign()`, manual production Category/LocationVariant ID fields, or Unicode back-arrow approximation.
- Accessibility/touch/focus cleanup was applied after the static sweep.

## Required executable verification after pull

From repo root (Node 24, pnpm 10):

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm api-client:check
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
pnpm --filter @covie/web test:e2e
```

Backend:

```bash
cd apps/api
./mvnw verify
```

The root repo currently has no `format:check` script. Do not report `pnpm format:check` as passed unless a script is added.

After executable checks, run the local app and compare Seller pages at `360`, `390`, `768` and desktop widths against the exact Figma frames listed in `docs/seller-mobile-coverage-matrix.md`.
