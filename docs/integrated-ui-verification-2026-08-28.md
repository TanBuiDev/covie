# Integrated UI Verification — 2026-08-28

## Scope

This report records both the pre-change integration baseline and the completed Chunk 1 verification. It distinguishes local functional integration from visual, staging, and production acceptance.

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

## Chunk 1 implementation

Implementation checkpoint: `aa5517d` on `codex/integrated-ui-completion`.

- Responsive Buyer surfaces expose one semantic heading/control/dialog owner instead of parallel CSS-hidden DOM trees.
- Checkout exposes one commit owner, guards repeated submission, discards stale state after `CHECKOUT_CHANGED`, and uses lifecycle-neutral success copy.
- Notification navigation accepts only backend-supplied safe same-origin relative paths.
- Buyer/Seller Need and Order presentation keeps actor boundaries and gates commands with backend `actionable`/`allowedActions` data.
- Admin and Need loaders avoid synchronous effect state cascades and reject stale responses.
- Address deletion returns keyboard focus to the initiating action after Escape; dialog labels and description targets are unique in the active composition.

## Final Chunk 1 verification

Fresh commands were run on 2026-08-28 after implementation checkpoint `aa5517d`:

| Gate | Result |
| --- | --- |
| `pnpm api-client:check` | PASS; generated client is synchronized, no regeneration required |
| `pnpm web:typecheck` | PASS |
| `pnpm web:lint` | PASS; 0 errors, 1 documented `@next/next/no-img-element` warning in `features/catalog/catalog-card.tsx` |
| `pnpm web:test` | PASS; 54 files, 131 tests |
| `pnpm web:build` | PASS; optimized production build, 34/34 static pages generated |
| `apps/api/./mvnw clean verify` with JDK 25.0.2 | PASS; 460 tests, 0 failures, 0 errors, 1 skipped; 727 source and 180 test files compiled |
| Focused address dialog and Buyer Need contract tests | PASS; 2 files, 8 tests |
| Integrated runtime/Figma review | NOT RUN; owned by later visual-acceptance chunks |
| Staging/production | NOT RUN; owned by later release-acceptance work |

The first Maven attempt stopped before compilation because the shell lacked `JAVA_HOME`. The verified run set `JAVA_HOME=C:\Program Files\Java\jdk-25.0.2` for that process only; no system configuration was changed.

## P0 outcome

Chunk 1 is locally complete: its web/API gates and contract regressions are green. This does not mark exact-frame visual fidelity, authenticated runtime journeys, staging, production, push, or pull-request review complete.
