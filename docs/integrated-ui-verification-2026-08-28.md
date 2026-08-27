# Integrated UI Verification — 2026-08-28

## Scope

This checkpoint records the local integration baseline before Chunk 1 changes. It distinguishes branch integration from functional, visual, staging, and production acceptance.

## Git baseline

- Execution branch: `codex/integrated-ui-completion`.
- Integrated starting commit: `c477f30` (`test(admin): align coverage with desktop contracts`).
- `origin/feature/buyer-mobile-finish-ui` is an ancestor of the branch; it includes Public Buyer and Buyer Desktop work.
- `origin/feature/seller-desktop-figma-ui` is an ancestor of the branch; it includes Seller Mobile work.
- `origin/feature/admin-desktop-figma-ui` is an ancestor of the branch.
- `git rev-list --left-right --count origin/main...HEAD` returned `0 240` before this checkpoint commit.
- No integrated changes have been pushed or opened as a pull request.

## Preserved user work

The following pre-existing changes are intentionally excluded from integration commits:

- `apps/web/features/profile/address-form.module.css`
- `apps/web/features/profile/address-form.tsx`
- `apps/web/tests/media/direct-image-upload.test.ts`
- `apps/web/tests/profile/address-form.test.tsx`
- `docs/CoVie-plan-closure-audit-2026-08-24.md`
- untracked local agent files under `apps/web/`

Safety stash `codex-local-work-before-ui-integration-2026-08-28` remains available. Ignored `.local/` runtime evidence and secrets are excluded.

## Baseline verification

| Gate | Result |
| --- | --- |
| `pnpm web:typecheck` | PASS |
| `pnpm web:build` | PASS; 34 static pages generated |
| Address form focused test | PASS; 5/5 |
| Admin focused tests | PASS; 3/3 |
| `pnpm web:test` | FAIL; 106 passed, 13 failed, 119 total |
| `pnpm web:lint` | FAIL; 11 errors, 1 warning |
| Integrated runtime/Figma review | NOT RUN |
| Staging/production | NOT RUN |

## P0 ownership

Chunk 1 owns duplicate responsive semantics, Checkout commit/success behavior, backend-owned notification links, Buyer/Seller Need and Order regressions, React effect lint errors, API-client drift verification, full web/API gates, and updated exact counts. The branch must not advance to visual acceptance based on branch-era test counts.
