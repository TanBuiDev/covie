# CoVie Web Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a zero-error/zero-warning web lint gate and all Vitest tests while preserving secure notification routing, responsive behavior, CSRF rotation, and server-owned order state.

**Architecture:** Treat the 2026-09-12 full Vitest/lint outputs as RED evidence. Correct stale test contracts where production behavior is intentionally safer, repair missing mocks for real dependencies, and refactor effect-driven loads so state initialization occurs at mount/event boundaries and responses update state asynchronously. Do not disable lint rules.

**Tech Stack:** Next.js 16.3.2, React 19.2, TypeScript 5.9, Vitest 3.2, Testing Library, ESLint 9.

---

## Chunk 1: Test-contract and harness repair

### Task 1: Notification deep-link contract

**Files:**
- Modify: `apps/web/tests/notifications/deep-link.test.ts`
- Verify unchanged: `apps/web/features/notifications/notification-deep-link.ts`

- [x] RED: run `pnpm --filter @covie/web exec vitest run tests/notifications/deep-link.test.ts --reporter=verbose`; expected failure was inferred type/id routes returning `null`.
- [x] Replace inferred-route assertions with explicit `deepLink`/`href`/`path`/`route` cases and external, protocol-relative, backslash, and inferred-route rejection.
- [x] GREEN: rerun the focused command; expected and observed 2/2 passing.

### Task 2: CSRF-aware direct media upload

**Files:**
- Modify: `apps/web/tests/media/direct-image-upload.test.ts`
- Verify unchanged: `apps/web/features/media/direct-image-upload.ts`
- Verify unchanged: `apps/web/features/auth/csrf.ts`

- [x] RED: run `pnpm --filter @covie/web exec vitest run tests/media/direct-image-upload.test.ts --reporter=verbose`; expected failure was an exhausted four-response mock before completion because create and complete each fetch a current CSRF token.
- [x] Add the second CSRF response and assert fetch sequence: CSRF → create → object PUT → CSRF → complete.
- [x] GREEN: rerun the focused command; expected and observed 2/2 passing.

### Task 3: Router/location test dependencies

**Files:**
- Modify: `apps/web/tests/profile/address-form.test.tsx`
- Modify: `apps/web/tests/notifications/notification-inbox.test.tsx`
- Verify unchanged: `apps/web/features/profile/address-form.tsx` except the lint-specific derivation in Task 6.
- Verify unchanged: `apps/web/features/notifications/notification-inbox.tsx`

- [x] RED: run `pnpm --filter @covie/web exec vitest run tests/profile/address-form.test.tsx tests/notifications/notification-inbox.test.tsx --reporter=verbose`; expected failures were missing App Router and BrowseLocation provider.
- [x] Add explicit router and browse-location mocks; update address labels/required fields to the current accessible form contract.
- [x] Assert successful address navigation/refresh and notification read behavior.
- [x] GREEN: rerun the focused command; expected and observed 3/3 passing.

### Task 4: Responsive duplicate surfaces in jsdom

**Files:**
- Modify: `apps/web/tests/catalog-detail.test.tsx`
- Modify: `apps/web/tests/merchant-profile.test.tsx`
- Modify: `apps/web/features/checkout/checkout-preview.test.tsx`
- Modify: `apps/web/tests/profile/address-list.test.tsx`

- [x] RED: run the four files together; expected failures were duplicate responsive headings/actions because jsdom does not apply CSS media-query visibility.
- [x] Assert both intentional responsive surfaces where appropriate and scope delete-flow assertions/actions to the unique mobile flow.
- [x] Preserve API payload, purchase, conflict, and destructive-confirmation assertions.
- [x] GREEN: rerun the four files; expected and observed 17/17 passing.

### Task 5: Checkout-created copy

**Files:**
- Modify: `apps/web/features/checkout/checkout-success.tsx`
- Verify: `apps/web/tests/checkout/checkout-success.test.tsx`
- Update with final evidence: `docs/CoVie-ui-handoff-matrix.md`

- [x] RED: run `pnpm --filter @covie/web exec vitest run tests/checkout/checkout-success.test.tsx --reporter=verbose`; expected failure was missing documented pending-seller-confirmation copy.
- [x] Restore truthful copy: the order is created and awaiting seller confirmation, without claiming confirmation.
- [x] GREEN: rerun the focused command; expected and observed 2/2 passing.

- [ ] **Commit checkpoint:** review `git diff`, stage only Tasks 1–5, and commit `test(web): align responsive and secure client contracts` if the user requests commits.

## Chunk 2: Lint-safe data flow

### Task 6: Address derivation and settings reset

**Files:**
- Modify: `apps/web/features/profile/address-form.tsx`
- Modify: `apps/web/features/admin/settings-editor.tsx`
- Verify: `apps/web/tests/profile/address-form.test.tsx`
- Verify: `apps/web/features/admin/settings-editor.test.tsx`

- [x] RED: `pnpm web:lint` reported two `react-hooks/preserve-manual-memoization` errors and one synchronous settings reset error.
- [x] Derive coordinates directly from props/context and key a settings row by authoritative effective value so refreshed server state remounts the local editor.
- [x] GREEN: focused tests and lint no longer report these errors.

### Task 7: Effect-driven collection loads

**Files:**
- Modify: `apps/web/features/admin/admin-audit-log.tsx`
- Modify: `apps/web/features/admin/admin-entity-table.tsx`
- Modify: `apps/web/features/admin/admin-moderation-detail.tsx`
- Modify: `apps/web/features/admin/admin-order-history.tsx`
- Modify: `apps/web/features/admin/admin-orders-explorer.tsx`
- Modify: `apps/web/features/admin/admin-readonly-collection.tsx`
- Modify: `apps/web/features/admin/admin-reports-queue.tsx`
- Modify: `apps/web/features/admin/admin-settings-screen.tsx`
- Modify: `apps/web/features/needs/need-list.tsx`
- Create: `apps/web/features/admin/admin-async-loaders.test.tsx`
- Create: `apps/web/tests/needs/need-list-loading.test.tsx`

- [x] RED: `pnpm web:lint` reported synchronous effect state cascades in each file.
- [x] Preserve initial loading state, move retry/pagination/filter loading transitions to their user events, and ensure effect-triggered network work/state transitions begin asynchronously with stale/unmount guards.
- [x] RED: add focused tests in `admin-async-loaders.test.tsx` covering initial load for all eight admin loaders, dependency/filter/page changes for the parameterized loaders, retry for error-capable loaders, and stale/unmount completion suppression; the stale guard exposed that six callback-based loaders only guarded request start, not completion.
- [x] GREEN: run `pnpm --filter @covie/web exec vitest run features/admin/admin-async-loaders.test.tsx --reporter=verbose`; observed 1 file / 3 tests passing.
- [x] RED: add focused tests in `tests/needs/need-list-loading.test.tsx` covering initial fetch, status-filter reload, retry after failure, and an unmounted/stale request that must not update the rendered state; retry coverage exposed that the list had no non-authentication retry action.
- [x] GREEN: run `pnpm --filter @covie/web exec vitest run tests/needs/need-list-loading.test.tsx --reporter=verbose`; observed 1 file / 3 tests passing.
- [x] Run `pnpm web:typecheck`; observed exit 0.
- [x] Run `pnpm web:test`; observed 54 files / 122 tests passing.
- [x] Run `pnpm web:lint`; observed exit 0 with no warnings.

### Task 8: Catalog image optimization warning

**Files:**
- Modify: `apps/web/features/catalog/catalog-card.tsx`
- Verify: `apps/web/tests/home.test.tsx`
- Verify: `apps/web/tests/search.test.tsx`

- [x] RED: `pnpm web:lint` reported the remaining `@next/next/no-img-element` warning.
- [x] Use `next/image` with the existing fixed dimensions and `unoptimized` remote media semantics used elsewhere in the project.
- [x] GREEN: Home/Search passed within the full suite; lint, typecheck, and production build exited 0.

- [ ] **Commit checkpoint:** review `git diff`, stage only Chunk 2, and commit `fix(web): restore current quality gates` if the user requests commits.

## Final verification

- [x] `pnpm api-client:check` → exit 0.
- [x] `pnpm web:typecheck` → exit 0.
- [x] `pnpm web:lint` → exit 0, zero warnings.
- [x] `pnpm web:test` → 54 files / 122 tests passing.
- [x] `pnpm web:build` → exit 0.
- [x] Update `AGENTS.md`, `docs/CoVie-project-status.md`, and `docs/CoVie-ui-handoff-matrix.md` with the date, HEAD/branch, exact results, and the fact that backend/E2E/staging gates remain separate.
