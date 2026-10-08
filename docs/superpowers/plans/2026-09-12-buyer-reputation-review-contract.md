# Buyer Reputation and Review Eligibility Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Expose the signed-in buyer's authoritative reputation and server-derived per-order-item review eligibility, then wire BUY-009 and BUY-010 without inferring eligibility from order status in the web client.

**Architecture:** A new authenticated `/api/v1/me/reputation` controller delegates to the existing `ReputationReadService.buyer(userId)` projection and returns private, non-cacheable account data. A shared pure `ReviewEligibilityEvaluator` owns reason precedence; both transactional review creation and batch order-read eligibility invoke it, with creation revalidating after locking the order and retaining the database uniqueness guard. `OrderReviewEligibilityService` loads existing-review membership once for all order items, and `OrderReadService` appends results only to Buyer-view items. The generated client remains the only web contract source, and Buyer screens render the reason returned by the server.

**Tech Stack:** Spring Boot 4, Java 25, Spring Security/JPA, OpenAPI, Next.js 16, React 19, TypeScript, Vitest, Testing Library.

---

## Chunk 1: Authoritative API contracts

### Task 1: Specify authenticated self-reputation

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reputation/api/MeReputationController.java`
- Create: `apps/api/src/test/java/vn/covie/reputation/api/MeReputationControllerIntegrationTest.java`
- Reuse unchanged: `apps/api/src/main/java/vn/covie/reputation/application/ReputationReadService.java`
- Reuse unchanged: `apps/api/src/main/java/vn/covie/reputation/api/dto/BuyerReputationSummary.java`

- [x] RED: add a Spring Security MockMvc test proving an unauthenticated `GET /api/v1/me/reputation` returns `401` and no aggregate payload.
- [x] RED: seed one buyer projection and prove an authenticated request returns only that JWT subject's `eligibleOrders`, `completedOrders`, nullable `completionRateBasisPoints` and server-derived `warning`.
- [x] RED: seed a different user's stronger projection and prove it cannot influence or be selected by the self endpoint; the endpoint accepts no user-id parameter.
- [x] RED: assert `Cache-Control: private, no-store` because the payload is authenticated account data.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=MeReputationControllerIntegrationTest test`; expect missing-route failures.
- [x] GREEN: implement `MeReputationController` at `/api/v1/me/reputation`, resolve the subject through `CurrentActorProvider`, and call `ReputationReadService.buyer` without adding a second projection calculation.
- [x] Rerun the focused test and require all cases green.

### Task 2: Specify per-item review eligibility

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reviews/api/dto/ReviewEligibilityReason.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/dto/ReviewEligibilityResponse.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/application/ReviewEligibilityEvaluator.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/application/OrderReviewEligibilityService.java`
- Modify: `apps/api/src/main/java/vn/covie/reviews/persistence/ReviewRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/reviews/application/ReviewService.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderItemResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderReadService.java`
- Create: `apps/api/src/test/java/vn/covie/reviews/application/OrderReviewEligibilityServiceTest.java`
- Modify: `apps/api/src/test/java/vn/covie/reviews/application/ReviewServiceTest.java`
- Modify: `apps/api/src/test/java/vn/covie/orders/application/OrderReadServiceTest.java`
- Verify authorization: `apps/api/src/test/java/vn/covie/orders/security/OrderAccessPolicyTest.java`

Use the fixed reason vocabulary `ELIGIBLE`, `ORDER_NOT_COMPLETED`, `ALREADY_REVIEWED`, `ITEM_NOT_REVIEWABLE`, and `CREATION_DISABLED`. `eligible` is true only for `ELIGIBLE`; the reason is always present when the Buyer view receives the object. After item existence and buyer ownership have been established, precedence is `CREATION_DISABLED` → `ORDER_NOT_COMPLETED` → `ITEM_NOT_REVIEWABLE` → `ALREADY_REVIEWED` → `ELIGIBLE`, matching the current transactional creation behavior.

- [x] RED: prove a completed, buyer-owned, catalog-backed item with no review is `ELIGIBLE` and includes its authoritative `orderItemId`.
- [x] RED: separately prove incomplete order, existing review, null catalog item and disabled creation return the matching ineligible reason.
- [x] RED: prove the batch service resolves reviewed order-item IDs with one repository call for the complete item set; forbid per-item `existsByOrderItemId` calls in order reads.
- [x] RED: prove `OrderReadService` attaches eligibility only for `OrderActorType.BUYER`; Merchant/Admin item responses return null review eligibility.
- [x] RED: retain the existing unrelated-actor `404 RESOURCE_NOT_FOUND` boundary; do not expose eligibility or an order payload to a nonparticipant.
- [x] RED: add `ReviewServiceTest` parity cases proving creation maps each shared decision to its existing API error and still rechecks after locking; retain `DataIntegrityViolationException` handling for concurrent uniqueness races.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=OrderReviewEligibilityServiceTest,ReviewServiceTest,OrderReadServiceTest,OrderAccessPolicyTest test`; expect missing-type/service failures.
- [x] GREEN: implement the pure evaluator from creation-enabled, order/item facts and already-reviewed membership; make both `ReviewService` and `OrderReviewEligibilityService` invoke it instead of maintaining parallel rule trees.
- [x] GREEN: add a single batch repository query returning reviewed order-item IDs for the supplied set, then evaluate all items in memory.
- [x] GREEN: extend `OrderItemResponse` and map results in `OrderReadService` after `OrderAccessPolicy.requireVisible` has established the actor view.
- [x] Rerun the focused tests and require all cases green.

### Task 3: Export and lock the generated contract

**Files:**
- Regenerate: `packages/api-client/openapi/openapi.json`
- Regenerate: `packages/api-client/src/generated/schema.ts`

- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=OpenApiExportTest "-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json" test`.
- [x] Run `pnpm api-client:generate` and `pnpm api-client:check` from the repository root.
- [x] Inspect the generated operation `/api/v1/me/reputation`, `BuyerReputationSummary`, `ReviewEligibilityResponse`, reason enum and `OrderItemResponse.reviewEligibility`; no handwritten client type is allowed.

## Chunk 2: BUY-009 and BUY-010 web flows

### Task 4: Implement BUY-009 reputation route from the generated operation

**Files:**
- Create: `apps/web/app/(buyer)/me/reputation/page.tsx`
- Create: `apps/web/features/reputation/buyer-reputation-screen.tsx`
- Create: `apps/web/features/reputation/reputation-api.ts`
- Create: `apps/web/tests/reputation/buyer-reputation.test.tsx`
- Modify: `apps/web/features/profile/profile-overview.tsx`
- Modify if required by exact frames: `apps/web/app/buyer-trust.css`

Exact frozen references: [mobile 390×844 `348:6173`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=348-6173), [mobile 360×800 `348:6259`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=348-6259), and [desktop 1440×900 `566:8794`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=566-8794).

- [x] Before UI edits, load the exact Figma frames above and map their tokens/layout to existing Buyer components; record any contract-backed deviation.
- [x] RED: test loading, aggregate present, zero-history/null-rate, warning and retryable API error states using the generated response type.
- [x] RED: test the Profile link navigates to `/me/reputation` and unauthenticated route access remains owned by `requireUser`.
- [x] GREEN: implement the route and component using only `GET /api/v1/me/reputation`; never derive self reputation from an arbitrary order response.
- [x] Run the focused Vitest files, `pnpm web:typecheck`, and `pnpm web:lint`.

### Task 5: Mount BUY-010 from server eligibility

**Files:**
- Modify: `apps/web/features/orders/order-detail.tsx`
- Modify: `apps/web/features/orders/order-detail-mobile.tsx`
- Modify: `apps/web/features/reviews/review-form.tsx`
- Modify: `apps/web/features/reviews/review-api.ts`
- Create: `apps/web/tests/reviews/order-review-eligibility.test.tsx`
- Modify: `apps/web/features/reviews/review-form.module.css`
- Modify if required by exact frames: `apps/web/app/buyer-trust.css`

Exact frozen references: [mobile eligible `348:6032`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=348-6032), [mobile 360×800 `348:6085`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=348-6085), [mobile state stack `348:6127`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=348-6127), [desktop eligible `566:8427`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=566-8427), [submitted `566:8534`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=566-8534), [already reviewed `566:8626`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=566-8626), [system disabled `566:8714`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=566-8714), and [compact 1024×900 `566:9076`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=566-9076).

- [x] Before UI edits, load the exact Figma frames above and map eligible/submitted/already-reviewed/disabled states to current order-detail and review components.
- [x] RED: prove the form mounts only when `item.reviewEligibility.eligible` is true and submits the server-returned `orderItemId`.
- [x] RED: prove `ALREADY_REVIEWED`, `CREATION_DISABLED`, `ORDER_NOT_COMPLETED` and `ITEM_NOT_REVIEWABLE` render truthful non-action states without checking `order.status` locally.
- [x] RED: prove successful submission refreshes order detail so the server becomes the source of the submitted/already-reviewed transition.
- [x] RED: prove stale `REVIEW_ALREADY_EXISTS` and `REVIEW_CREATION_DISABLED` POST responses trigger an authoritative order refresh and render its returned ineligible state instead of leaving only the generic submission error.
- [x] GREEN: wire the generated eligibility object into Buyer desktop/mobile order detail and retain ReviewForm idempotency behavior.
- [x] Run `pnpm --filter @covie/web exec vitest run tests/reviews/order-review-eligibility.test.tsx tests/reviews/review-form.test.tsx` plus web typecheck and lint.

## Final verification and evidence

- [x] Run the focused API suites, OpenAPI export/client generation, API-client check, web focused suites, typecheck and lint.
- [x] Run `Set-Location apps/api; .\mvnw.cmd clean verify` and record exact pass/skip totals.
- [x] Run `pnpm web:test` and `pnpm web:build` from the repository root.
- [x] Run `git diff --check` and inspect all contract/generated/UI changes; revert generated `apps/web/next-env.d.ts` churn.
- [x] Update `docs/buyer-mobile-remaining-coverage-matrix.md`, `docs/CoVie-ui-handoff-matrix.md`, `docs/CoVie-project-status.md` and `AGENTS.md` with contract/runtime evidence. Keep visual acceptance pending until the exact viewports are rendered and compared.
- [x] Mark master-plan Task 8 complete only when authorization, ownership, already-reviewed, generated-client and web gates are all green.
