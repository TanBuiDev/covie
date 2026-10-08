# CoVie Project Issue Completion Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every issue enumerated in the 2026-09-12 status response: web test/lint regressions, checkout geography and snapshot defects, named API-contract gaps, pending local/visual acceptance, and the external staging/release gate—without inventing frontend business rules.

**Architecture:** Work in independently verifiable chunks. First restore the web quality gates while preserving backend-owned navigation and state. Then correct checkout distance/coverage and immutable order snapshots in the API. Add missing public/account capability contracts only where the backend can authoritatively supply them; CoVie deliberately unifies Shop and Provider and must not gain a guessed binary merchant type. Finish local runtime/visual evidence before a separate staging task that requires real environment access.

**Tech Stack:** Next.js 16, React 19, TypeScript, Vitest, Playwright, Spring Boot 4, Java 25, PostgreSQL/PostGIS, Maven, OpenAPI.

---

## Chunk 1: Restore current web quality gates

Before modifying Chunk 1 production code or tests, create and review `docs/superpowers/plans/2026-09-12-web-quality-gates.md`. It must name every focused test file and command, record the observed baseline failure for each task, use explicit RED → minimal change → GREEN steps, and include a commit checkpoint after test-contract alignment and after lint/data-flow repair.

### Task 1: Align notification deep-link tests with the secure backend-owned contract

**Files:**
- Modify: `apps/web/tests/notifications/deep-link.test.ts`
- Verify: `apps/web/features/notifications/notification-deep-link.ts`

- [x] Replace inferred type/id route expectations with explicit safe `deepLink`/`href` payload cases and malicious/external path rejection cases.
- [x] Run `pnpm --filter @covie/web exec vitest run tests/notifications/deep-link.test.ts` and verify the existing implementation passes the corrected contract.

### Task 2: Repair test harnesses affected by router and CSRF behavior

**Files:**
- Modify: `apps/web/tests/profile/address-form.test.tsx`
- Modify: `apps/web/tests/notifications/notification-inbox.test.tsx`
- Modify: `apps/web/tests/media/direct-image-upload.test.ts`
- Verify: `apps/web/features/profile/address-form.tsx`
- Verify: `apps/web/features/notifications/notification-inbox.tsx`
- Verify: `apps/web/features/media/direct-image-upload.ts`

- [x] Add explicit `next/navigation` mocks and assert navigation side effects where relevant.
- [x] Model both mutation CSRF reads in the direct-upload fetch sequence, then assert create, object PUT, and completion requests.
- [x] Run each focused test file and confirm the original failures are gone.

### Task 3: Make responsive component tests target one presentation surface

**Files:**
- Modify: `apps/web/tests/catalog-detail.test.tsx`
- Modify: `apps/web/tests/merchant-profile.test.tsx`
- Modify: `apps/web/features/checkout/checkout-preview.test.tsx`
- Modify: `apps/web/tests/profile/address-list.test.tsx`
- Modify only if accessibility inspection finds a real DOM issue: the matching production components and CSS modules.

- [x] Scope assertions to the mobile app bar, desktop container, mobile commit bar, or desktop dialog rather than assuming only one responsive copy exists in jsdom.
- [x] Preserve assertions on business behavior, API payloads, conflict handling, and destructive confirmation.
- [x] Run the four focused suites and confirm they pass.

### Task 4: Resolve checkout-success wording drift

**Files:**
- Modify: `apps/web/tests/checkout/checkout-success.test.tsx`
- Modify if the documented product requirement remains authoritative: `apps/web/features/checkout/checkout-success.tsx`
- Update if behavior changes: `docs/CoVie-ui-handoff-matrix.md`

- [x] Confirm the intended semantic promise from the order state contract and Figma handoff.
- [x] Keep the screen truthful: order creation must not imply confirmation; status remains server-owned.
- [x] Run the focused test and record the accepted wording.

### Task 5: Remove React/Next lint errors without suppressions

**Files:**
- Modify: `apps/web/features/admin/admin-audit-log.tsx`
- Modify: `apps/web/features/admin/admin-entity-table.tsx`
- Modify: `apps/web/features/admin/admin-moderation-detail.tsx`
- Modify: `apps/web/features/admin/admin-order-history.tsx`
- Modify: `apps/web/features/admin/admin-orders-explorer.tsx`
- Modify: `apps/web/features/admin/admin-readonly-collection.tsx`
- Modify: `apps/web/features/admin/admin-reports-queue.tsx`
- Modify: `apps/web/features/admin/admin-settings-screen.tsx`
- Modify: `apps/web/features/admin/settings-editor.tsx`
- Modify: `apps/web/features/needs/need-list.tsx`
- Modify: `apps/web/features/profile/address-form.tsx`
- Add/update focused tests beside each changed feature.

- [x] For each effect failure, first add or identify a test covering initial load, dependency change, stale response cancellation, and retry.
- [x] Move synchronous state initialization out of effects or schedule it inside the async boundary while retaining cancellation guards.
- [x] Replace the fragile address coordinate memoization with a stable derivation that preserves edit values and browse-location fallback.
- [x] Run focused tests after each component group.
- [x] Run `pnpm web:lint`, `pnpm web:test`, `pnpm web:typecheck`, and `pnpm web:build`.

## Chunk 2: Correct checkout geography and immutable snapshots

Before changing Chunk 2 production code, create and review `docs/superpowers/plans/2026-09-12-checkout-geography-snapshots.md`. That child plan must name every constructor/repository change and use one red → green cycle per behavior below.

### Task 6: Compute and enforce product-delivery and service-area geography

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutPreviewService.java`
- Use: `apps/api/src/main/java/vn/covie/delivery/application/DeliveryCoverageEvaluator.java`
- Use: `apps/api/src/main/java/vn/covie/delivery/persistence/JdbcCoverageEvaluationRepository.java`
- Add/modify: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutPreviewSnapshotTest.java`
- Add integration coverage under: `apps/api/src/test/java/vn/covie/checkout/`

- [x] RED: add separate failing tests for eligible/ineligible `PRODUCT_DELIVERY`, eligible/ineligible `SERVICE_AREA`, exact distance propagation into `ShippingEvaluator`, and pickup remaining coverage-independent.
- [x] Run each focused test and verify it fails for missing checkout coverage/distance behavior.
- [x] GREEN: construct the location context from the authenticated user's active `UserAddressEntity` and evaluate through the PostGIS-backed coverage repository/evaluator; do not copy Haversine helpers from Catalog or Need code.
- [x] Run focused unit tests plus a Testcontainers/PostGIS integration test and verify every case passes.
- [x] Update `AGENTS.md` and `docs/CoVie-project-status.md` in the same change with the exact evidence and any remaining limitation.

### Task 7: Persist complete order snapshots

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutPreviewService.java`
- Verify calculation behavior in: `apps/api/src/main/java/vn/covie/shipping/application/ShippingEvaluator.java`
- Use: `apps/api/src/main/java/vn/covie/shipping/application/ShippingPolicySnapshotFactory.java`
- Modify tests: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutPreviewSnapshotTest.java`
- Modify tests: `apps/api/src/test/java/vn/covie/shipping/application/ShippingPolicySnapshotFactoryTest.java`
- Add order-creation/read-model regression tests under `apps/api/src/test/java/vn/covie/orders/`.

- [x] RED: add failing assertions for item name/type/variant/offer details, buyer and merchant contact fields allowed for fulfillment, location, address, distance, and shipping policy/rule outcome.
- [x] RED: add mutation-independence tests proving order reads retain the captured values after catalog/address/contact/policy records change.
- [x] GREEN: introduce versioned typed item/contact/fulfillment snapshot records and focused factories; define fulfillment-specific nullability and exclude secrets/private fields unrelated to fulfillment.
- [x] GREEN: invoke `ShippingPolicySnapshotFactory` from checkout orchestration after evaluation; keep `ShippingEvaluator` responsible only for calculation to avoid circular snapshot ownership.
- [x] Verify FREE, FIXED, NEGOTIATED, and CONDITIONAL modes, matched rule IDs, actual distance, and effective fee.
- [x] Update `AGENTS.md` and `docs/CoVie-project-status.md` with the completed snapshot contract and test evidence.

## Chunk 3: Close authoritative contract gaps

Chunk 3 is split into four independently reviewed child plans before production changes:

- `docs/superpowers/plans/2026-09-12-buyer-reputation-review-contract.md`
- `docs/superpowers/plans/2026-09-12-public-merchant-capabilities-catalog.md`
- `docs/superpowers/plans/2026-09-12-seller-catalog-management-detail.md`
- `docs/superpowers/plans/2026-09-12-need-response-candidates.md`

Each child plan must enumerate exact DTO/controller/service/test/generated-client/page/component files after a read-only symbol and route audit. Each behavior is implemented as: write failing API/web test, run and observe the expected failure, implement the minimum authoritative contract, run focused tests green, regenerate/check OpenAPI, then update its coverage matrix and `AGENTS.md`.

### Task 8: Expose buyer reputation and review eligibility

**Files:**
- Modify/add controller under `apps/api/src/main/java/vn/covie/reputation/api/` for authenticated `/api/v1/me/reputation`.
- Modify order DTO/service under `apps/api/src/main/java/vn/covie/orders/api/dto/` and `apps/api/src/main/java/vn/covie/orders/application/` to expose per-item review eligibility.
- Add API tests under `apps/api/src/test/java/vn/covie/reputation/` and `apps/api/src/test/java/vn/covie/orders/`.
- Regenerate: `packages/api-client/openapi/openapi.json` and `packages/api-client/src/generated/`.
- Add/wire Buyer routes/components under `apps/web/app/(buyer)/me/` and `apps/web/features/reviews/`.

- [x] Specify failing authorization/ownership/already-reviewed tests before adding endpoints.
- [x] Expose only authenticated buyer-owned aggregates and server-derived eligibility.
- [x] Wire BUY-009 and BUY-010 without deriving eligibility from raw status.

### Task 9: Expose authoritative merchant capabilities and public catalog

**Files:**
- Modify `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantResponse.java`.
- Modify `apps/api/src/main/java/vn/covie/catalog/application/PublicMerchantViewService.java`.
- Modify `apps/api/src/main/java/vn/covie/catalog/api/PublicCatalogController.java`.
- Add focused API/integration tests named exactly by the child-plan audit for visibility, moderation, pagination, Product/Service/mixed capability summaries, and empty results.
- Regenerate OpenAPI/client.
- Wire: `apps/web/features/merchant/merchant-profile.tsx` and its tests.

- [x] Add failing tests for every visibility and authorization boundary.
- [x] Return server-owned Product/Service/mixed capabilities derived from authoritative catalog/location data; do not add a persisted Shop/Provider discriminator.
- [x] Replace the public profile safe contract-gap state only after the generated client exposes the tested operation.

### Task 10: Add merchant catalog management detail/version and edit UI

- [x] Follow the reviewed `2026-09-12-seller-catalog-management-detail.md` child plan; runtime/visual acceptance remains Task 13.
- [x] Specify response schema, ownership/moderation visibility, ETag/version semantics, not-found behavior, and empty media/offer states with failing API tests.
- [x] Add the generated operation and wire existing-item edit without reconstructing state from list summaries.
- [x] Record focused API/web green evidence in `docs/seller-mobile-coverage-matrix.md`, `docs/seller-desktop-coverage-matrix.md`, and `AGENTS.md`.

### Task 11: Add Need eligible-candidate read contract and response UI

- [x] Follow the reviewed `2026-09-16-need-response-candidates.md` child plan for code-level delivery; runtime/visual acceptance remains Task 13.
- [x] Reuse `NeedResponseEligibilityPolicy`; specify ownership, category, moderation, coverage, stock, pagination, and empty-state behavior with failing API tests.
- [x] Wire only returned `locationVariantId` candidates; remove the safe unavailable state after API and web tests pass.
- [x] Record evidence in Seller matrices and `AGENTS.md`.

### Task 12: Reconcile Buyer verification gaps

- [x] Verify BUY-004 mobile create/edit at Figma nodes `327:4086` and backend-error `327:4316`; verify desktop create/edit at `564:5448`, backend-error `564:5613`, and compact desktop `564:5873`. Populated create/edit/backend-error evidence is recorded separately at 390×844, 360×800, 1024×900 and 1280×900 under ignored `.local/dev/visual-review/` with checksum manifest `chunk3/buy-004-evidence.json` (2026-09-16).
- [x] Add authoritative cancel eligibility for NEED-003. `NeedDetailResponse.allowedActions` exposes `CANCEL` only while the same command predicate can accept it; the web renders and retries the destructive confirmation only from that server-owned action.
- [x] Reconcile `docs/CoVie-project-status.md` with `docs/buyer-mobile-remaining-coverage-matrix.md`: BUY-004 retains its fresh populated visual evidence, while NEED-003 advances from contract-blocked to code/test verified with runtime/visual acceptance still explicit.

### OpenAPI gate for every Chunk 3 contract

- [x] From `apps/api`, run `.\mvnw.cmd -Dtest=OpenApiExportTest "-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json" test`.
- [x] From the repository root, run `pnpm api-client:generate` and `pnpm api-client:check`.
- [ ] After the generated artifacts are intentionally staged/committed, run `git diff --exit-code -- packages/api-client/openapi/openapi.json packages/api-client/src/generated/schema.ts`.

## Chunk 4: Acceptance, documentation, and release readiness

Before any Chunk 3 UI change or Chunk 4 screenshot work, create and review `docs/superpowers/plans/2026-09-12-local-runtime-visual-acceptance.md`. The child plan must first inventory `.local/dev/visual-review/chunk3/` to avoid duplicate evidence, then name exact Seller/Admin fixture scripts, Playwright specs, routes, state setup, Figma node IDs, viewport sizes, screenshot filenames, comparison tolerances, and pass criteria.

### Task 13: Run full local verification and visual acceptance

**Files:**
- Update screenshots under ignored `.local/dev/visual-review/`.
- Update: `docs/CoVie-ui-handoff-matrix.md`
- Update role-specific coverage matrices and engineering handoffs.

- [x] Run API `./mvnw clean verify` from `apps/api` (532 tests, 0 failures/errors, 1 skipped on 2026-09-16).
- [x] Run API client check, web typecheck, zero-warning lint, 64-file/178-test Vitest suite, and production build (all exit `0` on 2026-09-16).
- [x] Run `pnpm --filter @covie/web test:e2e` (3 passed, 5 intentional local-fixture skips) and the complete serial `pnpm ui:verify-local` sequence on 2026-09-17. Populated Buyer, complete Buyer/Public, Seller, Admin and the 81-case/201-file manifest gate each pass with one worker.
- [x] Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1`. A RED run exposed accumulated BUY-004 addresses; the seed now retires non-canonical addresses belonging only to the disposable fixture buyer, and the regression passes.
- [x] Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1` (exit `0`).
- [x] Before every UI change, retrieve its exact Figma frame URL. The authoritative catalog records screen ID, node ID, route, applicable viewport, state and deterministic filename; the manual ledger records review mode, hashes and deviations.
- [x] Extend deterministic fixtures and Playwright coverage for Buyer/Public, Seller and Admin. Buyer/Public passes 27 cases / 42 captures, Seller 36 / 111 and Admin 18 / 48; the combined `pnpm ui:verify-local` and manifest validation pass. All 201 records have current runtime/Figma hash-bound manual `PASS` as of 2026-09-19.
- [x] Run the final isolated gates on 2026-09-19: Maven 538 tests (0 failures/errors, 1 skip), API-client check, typecheck, zero-warning lint, 76 Vitest files / 210 tests, production build, standard Playwright 3 passed / 6 environment/fixture-gated skips, seed/launcher/orchestration regressions and the complete serial visual verifier.

### Task 14: Synchronize operational truth

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/CoVie-project-status.md`
- Modify relevant technical plans/runbooks under `docs/operations/`.

- [x] Record the 2026-09-17 current-worktree gates and complete 81-case / 201-capture automated evidence in `AGENTS.md` and `docs/CoVie-project-status.md`; manual Figma comparison and staging remain explicit blockers.
- [x] Remove resolved current `CONTRACT_BLOCKED`, `RUNTIME_PENDING`, and `VISUAL_PENDING` labels where direct evidence exists; preserve intentional `CONTRACT_GAP`/safe-state qualifications and dated historical checkpoints.
- [x] Keep staging/production marked incomplete until real credentials, infrastructure, deployment, smoke tests, rollback rehearsal, and monitoring evidence exist.

### Task 15: Execute staging and release rehearsal

- Authoritative procedure: `docs/operations/staging-rehearsal.md`.
- Deployment/rehearsal script: `infra/scripts/run-staging-rehearsal.sh`.
- Auth fixture: `apps/web/e2e/fixtures/staging-auth-fixture.ts`.
- Staging journey: `apps/web/e2e/staging-p0.spec.ts`.
- Acceptance checklist: `docs/operations/pilot-launch-checklist.md`.

- [x] Implement and locally validate the guarded normal-auth staging fixture/spec, canonical rehearsal command, production-origin refusal and staging data preconditions without storing secret values.
- [ ] Obtain the real HTTPS staging domain, deployment credentials, OAuth/storage/database/Redis secrets, and alert destination from the user/operator.
- [ ] After topology and access are known, create and review an environment-specific child plan that references the five authoritative assets above and records no secret values.
- [ ] Deploy migrations and application artifacts using the documented pipeline and immutable image digests.
- [ ] Run authenticated Buyer, Seller, and Admin smoke journeys; verify media, notifications/SSE, authorization, and concurrency behavior.
- [ ] Run backup/restore, failure injection, alert delivery, and a basic load baseline.
- [ ] Rehearse digest rollback A → B → A and capture timestamps/logs/results in the release runbook.
- [ ] Only after every check passes, update `AGENTS.md` and `docs/CoVie-project-status.md` from “not complete” to the exact evidenced release state.
