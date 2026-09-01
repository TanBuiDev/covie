# CoVie Integrated UI Completion Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize the integrated Buyer Mobile/Desktop, Seller Mobile/Desktop, and Admin Desktop UI, close every safely implementable Figma unit, add the missing authoritative API contracts, and produce runtime, visual, staging, and release evidence without inventing business state.

**Architecture:** Keep one responsive Next.js application and one generated OpenAPI contract. Buyer, Seller, and Admin presentation may compose differently by viewport, but API types, authorization, eligibility, pricing, inventory, lifecycle transitions, and moderation remain server-owned. Work proceeds in three gates: integration correctness, contract-backed completion, then rendered Figma/release acceptance.

**Tech Stack:** Next.js 16.3, React 19, TypeScript, CSS Modules, Vitest/Testing Library, Playwright, Spring Boot 4, Java 25, PostgreSQL/PostGIS, Flyway, OpenAPI-generated TypeScript client, pnpm, Maven.

---

## Current audited baseline (2026-08-28)

### Integrated Git topology

- Local `main` started at `42693a6` and matched `origin/main`.
- Buyer tip merged: `origin/feature/buyer-mobile-finish-ui` at `c69061f`; it already contains Public Buyer and Buyer Desktop.
- Seller tip merged: `origin/feature/seller-desktop-figma-ui` at `3aa53ae`; it already contains Seller Mobile.
- Admin tip merged: `origin/feature/admin-desktop-figma-ui` at `e81809e`.
- Local integration merge commits: `2563cc6`, `4620fce`, `8df5957`; Admin test-contract repair: `c477f30`.
- Local `main` is 240 commits ahead of `origin/main`. Nothing from this audit has been pushed.
- User work present before sync is preserved in the working tree and in safety stash `codex-local-work-before-ui-integration-2026-08-28` until final reconciliation is accepted.

### Conflict resolutions already applied

- `apps/web/features/needs/need-api.ts`: Buyer category/address types retained; Seller cursor page, URL encoding, limit, and stable idempotency support retained.
- `apps/web/features/orders/order-list.tsx`: Buyer mobile cards remain separate from Seller location filter/cursor pagination.
- `apps/web/features/orders/order-detail.tsx`: Buyer mobile/desktop and Seller desktop compositions dispatch by actor context; all commands still use `allowedActions`.
- `apps/web/features/orders/order-timeline.tsx`: one shared typed event mapping with reason/note and timestamp semantics.
- `apps/web/app/globals.css`: Buyer/Seller foundation retained; Admin tokens and layout scoped under `.workspace-shell--admin`.
- `apps/web/features/profile/address-form.tsx`: local GPS/manual coordinate, `onSaved`, pending, and field-error behavior combined with responsive remote styles.

### Fresh verification evidence

- `pnpm web:typecheck`: PASS.
- `pnpm web:build`: PASS; 34 static pages generated and all expected Buyer/Seller/Admin routes emitted.
- Address form focused test: PASS, 5/5.
- Admin focused tests: PASS, 3/3.
- Full `pnpm web:lint`: FAIL, 11 errors and 1 warning.
- Full `pnpm web:test`: FAIL, 106 passed and 13 failed across 8 files (119 tests total).
- Runtime browser/Figma comparison on the integrated tree: NOT RUN.
- Staging/production deployment: NOT RUN.

### P0 failing test inventory

1. `apps/web/tests/catalog-detail.test.tsx` — three duplicate responsive heading failures.
2. `apps/web/tests/merchant-profile.test.tsx` — duplicate responsive heading failure.
3. `apps/web/features/checkout/checkout-preview.test.tsx` — duplicate `Đặt hàng` controls.
4. `apps/web/tests/checkout/checkout-success.test.tsx` — lifecycle-neutral copy expectation is stale or implementation copy regressed.
5. `apps/web/tests/needs/buyer-needs.test.tsx` — integrated Need list presentation regression.
6. `apps/web/tests/notifications/deep-link.test.ts` — tests still infer routes from notification type/IDs while implementation accepts only backend-owned safe paths.
7. `apps/web/tests/notifications/notification-inbox.test.tsx` — Next router harness missing after the mobile app-bar integration.
8. `apps/web/tests/profile/address-list.test.tsx` — duplicate responsive headings and duplicate delete confirmation DOM.

### P0 lint inventory

- `apps/web/features/admin/admin-audit-log.tsx`
- `apps/web/features/admin/admin-entity-table.tsx`
- `apps/web/features/admin/admin-moderation-detail.tsx`
- `apps/web/features/admin/admin-order-history.tsx`
- `apps/web/features/admin/admin-orders-explorer.tsx` (two findings)
- `apps/web/features/admin/admin-readonly-collection.tsx`
- `apps/web/features/admin/admin-reports-queue.tsx`
- `apps/web/features/admin/admin-settings-screen.tsx`
- `apps/web/features/admin/settings-editor.tsx`
- `apps/web/features/needs/need-list.tsx`
- Existing non-blocking image warning: `apps/web/features/catalog/catalog-card.tsx`.

### Contract-blocked product inventory

- Public Buyer: no authoritative Shop/Provider discriminator, merchant catalog/capability/open-state read, account buyer reputation endpoint, review eligibility with `orderItemId`, or Need cancel eligibility.
- Seller: no authoritative existing catalog management detail + version contract, per-contact detail/version for edit, eligible Need response candidate read, or canonical geocoder/pin-picker contract.
- Admin: no dedicated shop/location read, listing read, or Admin Need read/moderation contract; zone/category mutations are also absent and correctly read-only.
- All Seller Desktop and Admin Desktop routes still need integrated runtime and visual evidence. Buyer screens added after the earlier Public Buyer P0 run also need fresh integrated visual evidence.

## File responsibility map

| Responsibility | Primary files |
| --- | --- |
| Responsive semantic ownership | `apps/web/features/catalog/catalog-detail.tsx`, `apps/web/features/merchant/merchant-profile.tsx`, `apps/web/features/profile/address-list.tsx`, `apps/web/features/checkout/checkout-preview.tsx`, matching CSS Modules |
| Buyer orders/needs/trust | `apps/web/features/orders/*`, `apps/web/features/needs/*`, `apps/web/features/reviews/*`, `apps/web/features/reports/*`, `apps/web/features/notifications/*` |
| Seller operations | `apps/web/features/seller/*`, `apps/web/features/catalog/*`, `apps/web/features/inventory/*`, `apps/web/features/shipping/*` |
| Admin operations | `apps/web/features/admin/*`, `apps/web/app/(admin)/admin/*` |
| Public catalog/merchant contract | `apps/api/src/main/java/vn/covie/catalog/**`, `apps/api/src/main/java/vn/covie/merchant/**`, `packages/api-client/openapi/openapi.json` |
| Buyer reputation/review/Need eligibility | `apps/api/src/main/java/vn/covie/reputation/**`, `apps/api/src/main/java/vn/covie/reviews/**`, `apps/api/src/main/java/vn/covie/needs/**`, `apps/api/src/main/java/vn/covie/orders/**` |
| Seller Need candidate contract | `apps/api/src/main/java/vn/covie/needs/api/NeedResponseController.java`, `apps/api/src/main/java/vn/covie/needs/application/NeedMatchingService.java`, matching DTO/tests |
| Admin contracts | `apps/api/src/main/java/vn/covie/admin/**`, `apps/api/src/main/java/vn/covie/moderation/**`, matching integration tests |
| Generated contract | `packages/api-client/openapi/openapi.json`, `packages/api-client/src/generated/**` |
| Browser acceptance | `apps/web/e2e/**`, `scripts/seed-ui-fixture.ps1`, `scripts/verify-ui-flow.ps1`, `.local/dev/visual-review/` (ignored evidence) |
| Delivery truth | `AGENTS.md`, `docs/CoVie-project-status.md`, `docs/CoVie-ui-handoff-matrix.md`, actor coverage matrices/handoffs |

---

## Chunk 1: Integration correctness and a green local web gate

### Task 1: Establish a reproducible integration checkpoint

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/CoVie-project-status.md`
- Create: `docs/integrated-ui-verification-2026-08-28.md`

- [x] **Step 1: Preserve the dirty working tree**

Run `git status --short --branch` and `git stash list`. Record the exact user-owned paths and do not include `.local`, secrets, or unrelated user changes in an integration commit.

- [x] **Step 2: Record branch ancestry**

Run `git merge-base --is-ancestor origin/feature/buyer-mobile-finish-ui HEAD`, the equivalent Seller/Admin commands, and `git rev-list --left-right --count origin/main...HEAD`.

Expected: all three ancestry checks exit 0; local `main` remains ahead of `origin/main`.

- [x] **Step 3: Create an isolated execution branch/worktree**

Use `codex/integrated-ui-completion` from the current integrated commit. Include the working-tree state only after explicitly separating user work from integration work.

- [x] **Step 4: Commit only the checkpoint documentation**

```bash
git add AGENTS.md docs/CoVie-project-status.md docs/integrated-ui-verification-2026-08-28.md
git commit -m "docs(ui): record integrated remote baseline"
```

### Task 2: Make responsive variants semantically single-owner

**Files:**
- Modify: `apps/web/features/catalog/catalog-detail.tsx`
- Modify: `apps/web/features/catalog/catalog-detail.module.css`
- Modify: `apps/web/features/merchant/merchant-profile.tsx`
- Modify: `apps/web/features/merchant/merchant-profile.module.css`
- Modify: `apps/web/features/profile/address-list.tsx`
- Modify: `apps/web/features/profile/address-list.module.css`
- Test: `apps/web/tests/catalog-detail.test.tsx`
- Test: `apps/web/tests/merchant-profile.test.tsx`
- Test: `apps/web/tests/profile/address-list.test.tsx`

- [x] **Step 1: Lock the accessibility regression with tests**

For each route, assert exactly one accessible page heading and exactly one destructive confirmation dialog. Do not weaken the tests with `getAllBy*` merely because CSS hides one copy.

- [x] **Step 2: Run the tests and confirm the current failure**

```bash
pnpm --filter @covie/web exec vitest run tests/catalog-detail.test.tsx tests/merchant-profile.test.tsx tests/profile/address-list.test.tsx
```

Expected: FAIL with duplicate heading/dialog queries.

- [x] **Step 3: Refactor to one semantic tree**

Prefer one heading/control whose layout changes through CSS. Where desktop and mobile structure truly differs, extract shared content and mount only one actor/viewport composition using a server-known composition boundary; do not use both live DOM trees hidden only by media queries.

- [x] **Step 4: Verify keyboard and screen-reader ownership**

Assert one `h1`, one dialog, unique label IDs, unique `aria-labelledby` targets, and no duplicate action name in the active composition.

- [x] **Step 5: Run focused tests**

Expected: all catalog, merchant profile, and address-list tests pass.

- [x] **Step 6: Commit**

```bash
git add apps/web/features/catalog apps/web/features/merchant apps/web/features/profile/address-list* apps/web/tests/catalog-detail.test.tsx apps/web/tests/merchant-profile.test.tsx apps/web/tests/profile/address-list.test.tsx
git commit -m "fix(web): make responsive buyer surfaces semantic singletons"
```

### Task 3: Reconcile Checkout responsive controls and success truth

**Files:**
- Modify: `apps/web/features/checkout/checkout-preview.tsx`
- Modify: `apps/web/features/checkout/checkout-preview.module.css`
- Modify: `apps/web/features/checkout/checkout-success.tsx`
- Test: `apps/web/features/checkout/checkout-preview.test.tsx`
- Test: `apps/web/tests/checkout/checkout-success.test.tsx`

- [x] **Step 1: Add a single-submit invariant**

Test that only one accessible `Đặt hàng` control exists and that a double interaction cannot call commit twice.

- [x] **Step 2: Preserve lifecycle-neutral success copy**

Choose one truthful sentence backed by the actual API response. Do not claim Seller confirmation; update the stale test if it incorrectly requires that claim.

- [x] **Step 3: Verify `CHECKOUT_CHANGED` recovery**

Assert the checkout token and idempotency intent are discarded, preview becomes required again, and the commit control cannot use stale data.

- [x] **Step 4: Run focused tests and commit**

```bash
pnpm --filter @covie/web exec vitest run features/checkout/checkout-preview.test.tsx tests/checkout/checkout-success.test.tsx
git add apps/web/features/checkout apps/web/tests/checkout/checkout-success.test.tsx
git commit -m "fix(checkout): unify responsive commit and success semantics"
```

### Task 4: Reconcile notification navigation with the backend-owned contract

**Files:**
- Modify: `apps/web/tests/notifications/deep-link.test.ts`
- Modify: `apps/web/tests/notifications/notification-inbox.test.tsx`
- Review: `apps/web/features/notifications/notification-deep-link.ts`
- Review: `apps/web/features/notifications/notification-inbox.tsx`

- [x] **Step 1: Freeze the security rule**

Tests must accept only safe same-origin relative paths supplied by backend payload fields and reject external URLs, protocol-relative URLs, malformed strings, and client-inferred destinations.

- [x] **Step 2: Remove stale type-to-route expectations**

Replace expectations such as `ORDER + id => /orders/id` with `deepLink: /orders/id => /orders/id`; unknown/missing paths should yield the deliberately selected safe fallback behavior.

- [x] **Step 3: Mount a router mock/provider in inbox tests**

Keep production app-bar navigation intact; repair the test harness instead of adding router exceptions to production code.

- [x] **Step 4: Run and commit**

```bash
pnpm --filter @covie/web exec vitest run tests/notifications/deep-link.test.ts tests/notifications/notification-inbox.test.tsx
git add apps/web/tests/notifications
git commit -m "test(notifications): align coverage with backend-owned links"
```

### Task 5: Repair Buyer Need and integrated Order regressions

**Files:**
- Modify: `apps/web/features/needs/need-list.tsx`
- Modify: `apps/web/features/needs/need-detail.tsx`
- Modify: `apps/web/features/needs/need-response-list.tsx`
- Review: `apps/web/features/orders/order-list.tsx`
- Review: `apps/web/features/orders/order-detail.tsx`
- Review: `apps/web/features/orders/order-timeline.tsx`
- Test: `apps/web/tests/needs/buyer-needs.test.tsx`
- Test: `apps/web/tests/needs/seller-needs.test.tsx`
- Test: `apps/web/features/orders/order-actions.test.tsx`

- [x] **Step 1: Reproduce the exact Buyer Need failure**

Run the single test with verbose reporter and identify whether copy, duplicate DOM, cursor state, or status presentation diverged.

- [x] **Step 2: Add actor-dispatch tests**

Assert Buyer does not render Seller filters/actions and Seller does not render Buyer mobile CTA/navigation. Assert both share typed status/timeline without deriving permissions.

- [x] **Step 3: Implement the smallest actor-safe repair**

Keep Need cancel hidden until explicit eligibility exists; keep response checkout gated by backend `actionable`; keep order commands gated by `allowedActions`.

- [x] **Step 4: Run focused tests and commit**

```bash
pnpm --filter @covie/web exec vitest run tests/needs/buyer-needs.test.tsx tests/needs/seller-needs.test.tsx features/orders/order-actions.test.tsx
git add apps/web/features/needs apps/web/features/orders apps/web/tests/needs
git commit -m "fix(web): preserve buyer and seller need order contracts"
```

### Task 6: Remove React effect lint violations without disabling the rule

**Files:**
- Modify the 10 Admin files and `apps/web/features/needs/need-list.tsx` listed in the lint inventory.
- Test: colocated Admin tests plus new focused loader tests as needed.

- [x] **Step 1: Write tests around initial load, filter change, pagination, and stale response cancellation**

Cover loading/error/data state and ensure a late response cannot overwrite a newer query.

- [x] **Step 2: Move user-triggered resets into event handlers**

Filter/search/page handlers should clear stale state before changing the query key. Avoid synchronous reset calls in the effect body.

- [x] **Step 3: Start asynchronous work from effects without synchronous state cascades**

Use an async callback/microtask or a focused resource hook consistent with existing React 19 patterns. Do not add blanket ESLint disables.

- [x] **Step 4: Replace `settings-editor` value synchronization with keyed ownership**

Key the editor by setting identity/version or derive a draft boundary at the parent so `setValue(effective)` is not required in an effect.

- [x] **Step 5: Run lint and Admin/Need tests**

```bash
pnpm web:lint
pnpm --filter @covie/web exec vitest run features/admin tests/needs/buyer-needs.test.tsx tests/needs/seller-needs.test.tsx
```

Expected: 0 lint errors. Decide separately whether to migrate `catalog-card.tsx` to `next/image` or document the one warning.

- [x] **Step 6: Commit**

```bash
git add apps/web/features/admin apps/web/features/needs/need-list.tsx
git commit -m "refactor(web): make admin and need loading effect-safe"
```

### Task 7: Close the P0 web gate and generated-client drift

**Files:**
- Modify if drift is real: `packages/api-client/openapi/openapi.json`
- Regenerate if drift is real: `packages/api-client/src/generated/**`
- Modify: `docs/integrated-ui-verification-2026-08-28.md`

- [x] **Step 1: Run all web checks**

```bash
pnpm api-client:check
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
```

- [x] **Step 2: If API drift fails, export and regenerate from the integrated API tree**

Use the repository's existing OpenAPI export/generation scripts; inspect the diff and never hand-edit generated TypeScript.

- [x] **Step 3: Run API verification**

```powershell
Set-Location apps/api
./mvnw clean verify
```

- [x] **Step 4: Record exact counts and commit**

Do not reuse branch-era counts. Record the new test count, warnings, build result, commit SHA, and date.

---

## Chunk 2: Authoritative contracts for currently blocked Figma flows

### Task 8: Add public merchant kind, catalog, and capabilities

**Files:**
- Modify/Create under: `apps/api/src/main/java/vn/covie/merchant/**`
- Modify/Create under: `apps/api/src/main/java/vn/covie/catalog/**`
- Test: public merchant/catalog controller integration tests.
- Regenerate: `packages/api-client/openapi/openapi.json`, `packages/api-client/src/generated/**`
- Modify: `apps/web/features/merchant/merchant-server-api.ts`
- Modify: `apps/web/features/merchant/merchant-profile.tsx`
- Test: `apps/web/tests/merchant-profile.test.tsx`

- [x] **Step 1: Write failing API tests** for `merchantKind`, public catalog page, location fulfillment capabilities, and explicit availability/open state.
- [x] **Step 2: Define DTOs that disclose only public fields**; do not expose private Seller configuration or raw internal identifiers unnecessarily.
- [x] **Step 3: Implement repository/service/controller reads** with pagination and moderation visibility policy.
- [x] **Step 4: Export OpenAPI and regenerate the client.**
- [x] **Step 5: Implement PUB-009 Shop/Provider routing from the discriminator** and render real catalog/capability states.
- [x] **Step 6: Run API/web tests and commit** with `feat(catalog): expose public merchant profile capabilities`.

### Task 9: Add Buyer reputation, review eligibility, and Need cancel eligibility

**Files:**
- Modify/Create: `apps/api/src/main/java/vn/covie/reputation/api/**`
- Modify: `apps/api/src/main/java/vn/covie/reputation/application/ReputationReadService.java`
- Modify/Create: order/review DTO assemblers under `apps/api/src/main/java/vn/covie/orders/**` and `reviews/**`
- Modify: Need detail DTO/service under `apps/api/src/main/java/vn/covie/needs/**`
- Regenerate API client.
- Create: `apps/web/app/(buyer)/me/reputation/page.tsx`
- Create/Modify: Buyer reputation, order detail, review, and Need components/tests.

- [x] **Step 1: Write failing authorization and policy tests** for `GET /api/v1/me/reputation`.
- [x] **Step 2: Expose explicit review targets/eligibility** including `orderItemId`; reject already-reviewed/ineligible items server-side.
- [x] **Step 3: Expose Need `allowedActions` or `canCancel`** computed by the same policy used by the command endpoint.
- [x] **Step 4: Regenerate the client and mount BUY-009/010 and Need cancel UI only from these fields.**
- [x] **Step 5: Add loading/empty/error/unauthorized/conflict tests.**
- [x] **Step 6: Commit** with separate API and web commits.

### Task 10: Add authoritative Seller Need response candidates

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/needs/api/NeedResponseController.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedMatchingService.java`
- Create: a candidate response DTO and controller/service tests.
- Modify: `apps/web/features/needs/need-api.ts`
- Modify: `apps/web/features/needs/merchant-need-response-form.tsx`
- Test: `apps/web/tests/needs/seller-needs.test.tsx`

- [x] **Step 1: Write a failing policy-equivalence integration test** proving candidate read and submit validation use the same category, moderation, coverage, ownership, availability, and stock rules.
- [x] **Step 2: Implement a paginated candidate read endpoint** scoped to merchant, Need, and location.
- [x] **Step 3: Return display-safe catalog/variant labels and current availability**, not UUID-only rows.
- [x] **Step 4: Regenerate the client and replace NEED-006 safe fallback with a real selector.**
- [x] **Step 5: Test empty, stale candidate, submit conflict, and existing response states.**
- [x] **Step 6: Commit** with `feat(needs): expose eligible seller response candidates`.

### Task 11: Add Seller catalog/contact edit concurrency contracts

**Files:**
- Modify/Create under merchant catalog API/application/persistence packages.
- Modify/Create contact detail/version endpoint under merchant/location contact packages.
- Modify: `apps/web/features/catalog/catalog-create-form.tsx` or split into focused create/edit owners.
- Modify: `apps/web/features/seller/contact-settings.tsx`.
- Add API and web tests.

- [x] **Step 1: Specify management detail DTO and ETag/version behavior** for existing catalog aggregate, variants, offers, media, and lifecycle capability.
- [x] **Step 2: Specify contact detail/version read + PATCH contract.**
- [x] **Step 3: Write concurrency tests** for stale `If-Match`, lost update prevention, ownership, and moderation restrictions.
- [x] **Step 4: Implement endpoints and regenerate the client.**
- [x] **Step 5: Add edit routes/modes without duplicating create validation.**
- [x] **Step 6: Verify Seller Mobile at 360/390/768 and Desktop at 1024/1280/1440.**

### Task 12: Add Admin read contracts for Shops, Listings, and Needs

**Files:**
- Create/Modify: `apps/api/src/main/java/vn/covie/admin/api/**`
- Modify: `apps/api/src/main/java/vn/covie/admin/application/AdminMaintenanceService.java` or split focused read services.
- Reuse moderation visibility policy where applicable.
- Replace safe-state pages: `apps/web/app/(admin)/admin/shops/page.tsx`, `listings/page.tsx`, `needs/page.tsx`.
- Add Admin feature components and tests.

- [x] **Step 1: Write Admin role/permission integration tests** for paginated/filterable reads.
- [x] **Step 2: Define minimum safe DTOs** for shop/location, listing/moderation link, and Need status; suppress private buyer/contact data not required by Figma.
- [x] **Step 3: Implement read-only APIs first.** Do not add mutation merely because the Figma sample shows a control.
- [x] **Step 4: Regenerate the client and replace `AdminCapabilityPage` safe states with real tables/details.**
- [x] **Step 5: Preserve unknown-state fallbacks and no-force-status rule.**
- [x] **Step 6: Decide zone/category mutation scope separately.** Keep ADM-015/016 read-only until explicit product approval and API design exist.

---

## Chunk 3: Integrated Figma, runtime, accessibility, and release acceptance

### Task 13: Build deterministic actor fixtures

**Files:**
- Modify: `scripts/seed-ui-fixture.ps1`
- Modify: `scripts/tests/seed-ui-fixture.test.ps1`
- Modify/Create: `apps/web/e2e/fixtures/**`
- Keep generated credentials/evidence only under ignored `.local/`.

- [x] Seed one Buyer, one Seller/Provider, and one Admin with representative public listings, carts, addresses, orders, Needs, reviews, reports, settings, catalog variants, locations, shipping, inventory, and moderation states.
- [x] Include empty, error-safe, restricted, negotiated shipping, stale version/action, and long-content scenarios.
- [x] Make the script idempotent and local-only; never commit credentials.
- [x] Add a smoke assertion for each seeded actor and commit.

### Task 14: Buyer Mobile/Desktop visual acceptance

**Exact Figma source:** page `03 — Buyer Mobile` (`3:4`) and the Buyer Desktop production frames recorded in the remote handoff.

- [x] Retrieve exact route frame context with `@figma:figma-design-to-code`; never use only a page node as implementation evidence.
- [x] Compare 360×800, 390×844, 768 tablet where specified, 1024, 1280×900, and the canonical desktop frame.
- [x] Cover PUB-001…009, AUTH, BUY-001…011, ORD-001…012, NEED-001…004, notifications, profile, sessions, loading/empty/error/unauthorized/pending/conflict states.
- [x] Assert destination URL and a route-unique landmark before every screenshot.
- [x] Store screenshot evidence under ignored `.local/dev/visual-review/` and record names/results in the handoff matrix.

### Task 15: Seller Mobile/Desktop visual acceptance

**Exact Figma source:** Seller Mobile page `3:5`; Seller Desktop page `3:6`.

- [x] Verify SEL-001…019 and NEED-005/006 at 360, 390, 768, 1024, 1280, and 1440×900.
- [x] Confirm the five-item IA, no horizontal overflow, location filter/cursor behavior, Catalog create/edit, inventory, order actions, Need response, reputation, and notices.
- [x] Exercise long labels, missing summaries, restricted merchant, stale ETag, revoked action, destructive confirmations, and keyboard navigation.
- [x] Record exact frame IDs and visual deltas before calling any unit visually verified.

### Task 16: Admin Desktop visual/runtime acceptance

**Exact Figma source:** Admin page `3:7`, ADM-001…017 canonical frames.

- [x] Verify 1024 minimum usable desktop, 1280 intermediate, and 1440×900 canonical layout.
- [x] Exercise every real Admin read/mutation, pagination/filtering, 409/412 refresh-required state, unknown code mapping, sensitive audit metadata suppression, and read-only boundaries.
- [x] Verify sidebar keyboard navigation, sticky table headers, dialog focus/escape/backdrop behavior, and no page overflow.
- [x] Keep ADM-004/005/008 safe states until Task 12 contracts ship; do not visually pass sample-data substitutes.

### Task 17: Run the integrated acceptance suite

```powershell
pnpm api-client:check
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
pnpm --filter @covie/web test:e2e
pnpm ui:verify-local
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1
Set-Location apps/api
./mvnw clean verify
```

- [x] Run accessibility checks for keyboard, focus, labels, landmark uniqueness, contrast, and reduced motion.
- [x] Run responsive overflow checks at every target width.
- [x] Record exact pass counts, warnings, skipped tests, commit SHA, browser versions, and evidence paths.
- [x] Do not convert a local pass into a staging or production claim.

### Task 18: Update source-of-truth documentation and integrate remotely

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/CoVie-project-status.md`
- Modify: `docs/CoVie-ui-handoff-matrix.md`
- Modify: Buyer/Seller/Admin coverage matrices and engineering handoffs.
- Modify only if behavior changed: relevant technical plans and operations runbooks.

- [x] Mark separately: implemented, test-verified, runtime-verified, visually verified, staging-verified, production-deployed.
- [ ] Reconcile or close superseded UI PRs only after the integrated branch is pushed and reviewed.
- [ ] Push `codex/integrated-ui-completion` without rewriting remote feature history.
- [ ] Open one integration PR to `main` with conflict rationale, gate evidence, known contract gaps, and rollback guidance.
- [ ] Require green CI and human visual/product review before merge.
- [ ] After merge, run staging rehearsal and authenticated P0 journeys; production remains blocked until release checklist evidence is complete.

## Definition of done

- [ ] `origin/main` contains all five UI branches through a reviewed integration PR; no feature history was force-rewritten.
- [ ] No unmerged paths or conflict markers remain; user-owned pre-sync work is preserved and explicitly accounted for.
- [ ] API-client drift, typecheck, lint, all Vitest, production build, API `clean verify`, and required Playwright suites pass from a clean checkout.
- [ ] Every Figma unit has one of: visually verified implementation, explicit authoritative contract blocker, or approved out-of-scope decision.
- [ ] Responsive DOM exposes one semantic heading/control/dialog per active surface.
- [ ] Buyer/Seller/Admin commands are mounted only from server permissions/capabilities; no status-derived business state is introduced.
- [ ] Required viewport screenshots and route assertions exist for all implemented units.
- [ ] Staging and production status are reported only from direct environment evidence.
