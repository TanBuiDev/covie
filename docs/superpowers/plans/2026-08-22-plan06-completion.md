# Plan 06 Completion Checklist

> This is a concise implementation checklist for the remaining Plan 06 work.

**Goal:** Complete Need response, checkout attribution, fulfillment, expiration, API/client/UI, and acceptance coverage before integrating Plans 05 and 06 into `main`.

**Architecture:** Continue the existing `needs` module from Tasks 7–8, preserve database lock order, and wire Need-origin checkout through the established checkout/order extension points. Verify backend and frontend after every vertical slice, then rebase the Plan 06 branch on the merged Plan 05 branch and merge in dependency order.

**Tech Stack:** Spring Boot, JPA/JdbcTemplate/PostGIS, Flyway, JUnit/Testcontainers, Next.js, TypeScript, generated OpenAPI client.

---

## Chunk 1: Seller response lifecycle

### Task 1: Complete response mutation APIs

**Files:** `apps/api/src/main/java/vn/covie/needs/{api,application,model,persistence}` and focused tests.

- [ ] Add replace and withdraw contracts with lock/order/authorization checks.
- [ ] Preserve retired response-item history and assert `If-Match` behavior.
- [ ] Verify focused Need response tests and commit.

## Chunk 2: Buyer checkout and lifecycle completion

### Task 2: Wire buyer response reads and Need checkout

**Files:** Need read services, checkout candidate resolver/commit attribution, Order confirm participant, and tests.

- [ ] Add private buyer response read model and source-chain validation.
- [ ] Add Need-origin checkout preview/commit attribution and confirmation fulfillment.
- [ ] Add expiration scheduler and concurrency/end-to-end coverage.

### Task 3: Publish and integrate

**Files:** OpenAPI/client, `apps/web/features/needs`, buyer/seller pages, acceptance tests.

- [ ] Regenerate API contracts and implement minimal need UI.
- [ ] Run full backend/frontend verification and inspect generated drift.
- [ ] Rebase Plan 06 on merged Plan 05, merge Plan 05 then Plan 06 into `main`, and push `origin/main`.
