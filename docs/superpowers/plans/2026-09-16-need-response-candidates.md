# Need Response Eligible Candidates Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents are available and permitted) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove NEED-006's contract gap by exposing only server-validated, merchant-location-scoped `locationVariantId` candidates for an active Need, then let Seller respond using those returned IDs.

**Architecture:** Add `GET /api/v1/merchants/{merchantId}/need-requests/{needRequestId}/eligible-items?locationId=...` under active `RESPOND_TO_NEEDS` location access and the existing selected-location Need discovery visibility predicate. A coarse SQL query scans location offers in stable `(catalog_item_id, variant_id, location_variant_id)` order; every returned row is revalidated by the same `NeedResponseEligibilityPolicy` used transactionally by POST/PUT, so the read never promises an item the command's current predicate would reject. The read is advisory: mutations still lock the Need and revalidate. A context-bound opaque cursor resumes the coarse scan without leaking another location's records. The generated OpenAPI client supplies types to Seller; the web never accepts handwritten UUIDs or infers eligibility from management summaries.

**Tech Stack:** Spring Boot 4, Java 25, PostgreSQL/PostGIS, JDBC/JPA, MockMvc/Testcontainers, OpenAPI, Next.js 16, React 19, TypeScript, Vitest/Testing Library.

**Execution checkpoint — 2026-09-16:** The reviewed code-level plan is implemented and locally green. RED/GREEN coverage caught missing route/schema, Product/Service/category/stock/coverage predicates, PAUSED-location and hidden-Need command gaps, pagination/lookahead/500-row ceiling, malformed/cross-context cursors and OpenAPI nullability. `NeedEligibleItemsControllerIntegrationTest` passes 27 tests and `NeedQueryPlanIntegrationTest` passes three without a location-offer sequential scan. OpenAPI export passes 1/1; client generation/check pass with required `locationId` and nullable cursor. Exact NEED-005/006 mobile/state/desktop Figma contexts were retrieved before UI changes. The Seller expansion uses only generated candidates/server maximum, handles loading/empty/error/retry/load-more and stale Need/location results, and submits only returned IDs. Focused web suites pass 9/9; full web gates pass 63 files / 174 tests, typecheck, zero-warning lint and production build. Fresh Maven `clean verify` exits `0` with 531 tests (0 failures/errors, 1 skipped). Runtime Seller E2E and exact viewport/manual visual acceptance remain Task 13; existing-response replacement remains unmounted because discovery lacks an authoritative merchant response read.

**Prerequisites and scope:** The existing Seller Need page only renders a safe unavailable note because there is no candidate-read contract. `NeedResponseEligibilityPolicy.validate` checks active Need, `RESPOND_TO_NEEDS` location authorization, policy maximum/duplicates, search radius and Product pickup/delivery or Service area, active/moderation-safe item and variant, category descendants, available offer and tracked stock. Current command validation lacks an ACTIVE-location check although discovery requires one: add that check to the shared command/read predicate with RED/ GREEN tests, rather than making the GET advisory state disagree with POST/PUT. Direct Need ID access additionally requires `NOT_REQUIRED`/`APPROVED` Need moderation and current selected-location discovery visibility; hidden or non-discoverable Needs return non-leaking 404, not a 200 page revealing existence/policy. A selected-location-visible Need may return a successful empty candidate page when the subsequent eligibility scan accepts nothing; a Need absent from that location's feed returns 404. The coarse repository may reduce work but must not replace policy validation. Do not return buyer coordinates/address, media storage keys, inactive items or an eligibility reason for a rejected foreign item. Exact NEED-005/006 Figma contexts and the reviewed local runtime/visual plan are required before web UI edits; `RUNTIME_PENDING / VISUAL_PENDING` survives code-level checks.

---

## Chunk 1: Prove the read contract and safety boundary

### Task 1: Lock endpoint ownership, empty result and response shape

**Files:**

- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/NeedEligibleItemResponse.java` — only `locationVariantId`, `catalogItemId`, `variantId`, item/variant display names, current money, available flag; no buyer location or rejected-offer reason.
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/NeedEligibleItemCursorPage.java` — `items`, `hasMore`, nullable `nextCursor`, `maxResponseItems` from resolved policy.
- Modify: `apps/api/src/main/java/vn/covie/needs/api/MerchantNeedDiscoveryController.java` — `@GetMapping("/{needRequestId}/eligible-items")` with mandatory `locationId`, optional `limit` and `cursor`.
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedMatchingService.java` and `apps/api/src/main/java/vn/covie/needs/persistence/NeedDiscoveryRepository.java` / `JdbcNeedDiscoveryRepository.java` — add a selected-location/direct-Need visibility check using the same current discovery SQL and coverage predicate, not an unscoped `findById` guess.
- Create: `apps/api/src/test/java/vn/covie/needs/api/NeedEligibleItemsControllerIntegrationTest.java` — use the same Testcontainers/MockMvc fixture patterns as catalog management detail and Need persistence suites.

- [x] RED: write MockMvc tests for anonymous 401; non-member/inactive membership, foreign merchant/location, missing, expired/cancelled and PENDING/REJECTED/HIDDEN Need with the same non-leaking 404; missing `locationId` 400. Test a Need visible at one merchant location but not the selected second location returns 404. Do not test item filtering yet.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=NeedEligibleItemsControllerIntegrationTest test`; confirm the missing route/auth behavior fails for the intended reason, not fixture setup.
- [x] GREEN: add minimal controller/DTO/read boundary that authorizes `RESPOND_TO_NEEDS`, checks ACTIVE selected location, active/moderation-safe Need and selected-location discovery visibility before returning an explicit empty cursor page. Resolve `maxResponseItems` with the same merchant/category/Need area inputs as POST. This is an intermediate authorized boundary, not candidate completion.
- [x] Rerun the focused route/auth suite GREEN before adding any candidate-filtering assertions.

### Task 2: Reuse command policy while scanning candidates

**Files:**

- Create: `apps/api/src/main/java/vn/covie/needs/persistence/NeedEligibleCandidateRepository.java` — coarse, deterministic page interface returning merchant-owned location offer IDs plus display fields.
- Create: `apps/api/src/main/java/vn/covie/needs/persistence/JdbcNeedEligibleCandidateRepository.java` — SQL joins `location_variants`, `variants`, `catalog_items`; filter selected location, merchant, non-deleted ACTIVE item/variant, matching Need type and available offer. Sort by three IDs, seek after a typed cursor tuple and fetch `batch` rows. This query is an optimization only: category, moderation, stock, coverage and every command condition must still be checked by shared policy.
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseEligibilityPolicy.java` — expose a non-mutating single-offer decision that invokes the current common checks; keep `validate` for locked POST/PUT and make both paths share the same `validateItem`/serviceability predicates. Add an ACTIVE selected-location guard shared by GET and POST/PUT. Reject unauthenticated location/Need context before candidate filtering.
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedEligibleItemReadService.java` — require `RESPOND_TO_NEEDS` location access first, load active Need, resolve policy, scan bounded pages, invoke the common policy per row, map only accepted rows; candidate-level invalidity is filtered, but authorization/Need errors are not swallowed.
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedEligibleItemCursorCodec.java` — versioned base64url JSON cursor containing merchant ID, Need ID, location ID and the last scanned three-ID tuple; validate IDs/version/context before SQL. Invalid or wrong-context cursors return 422 `VALIDATION_FAILED`.
- Test: `apps/api/src/test/java/vn/covie/needs/api/NeedEligibleItemsControllerIntegrationTest.java`.

- [x] RED: add Product/Service eligible and ineligible-item tests from Task 1's response-shape requirements. Assert a selected-location-visible Need with a candidate scan accepting nothing returns 200 empty; if no offer made the Need discoverable at that location, assert 404 instead. Prove changing the selected location to PAUSED makes both GET and POST/PUT reject it consistently.
- [x] RED: prove a stale/forged cursor cannot cross Need/location context; `limit` clamps to 1–50 and default 20; pages contain no duplicate ID and preserve stable tuple order even when ineligible rows separate eligible ones. A same-context replay with a changed limit or policy/source state must remain safe, without promising old eligibility.
- [x] RED: prove a full coarse batch of rejected rows does not falsely return an empty terminal page while eligible rows remain later. Fetch one lookahead coarse row or equivalent to determine true continuation: `hasMore` reflects unscanned coarse rows, including exact-full terminal batches. `nextCursor` is non-null if and only if `hasMore`, and represents the last scanned tuple. At a rejection-scan ceiling, emit truthful continuation rather than a false terminal result.
- [x] RED: prove a candidate read and POST with its returned ID agree at unchanged database state, while changing stock/availability after the read is rejected by POST's transactional revalidation.
- [x] Run the focused suite and observe predicate/pagination failures before production edits.
- [x] GREEN: implement bounded scan with an explicit safety ceiling and one-row lookahead/equivalent truthful continuation. If the ceiling is reached with unscanned rows, return a continuation cursor rather than a false terminal result; never mark `hasMore` based solely on the returned-item count.
- [x] Rerun focused tests; run `Set-Location apps/api; .\mvnw.cmd -Dtest=NeedEligibleItemsControllerIntegrationTest,NeedQueryPlanIntegrationTest test` and inspect query-plan regression for the selected-location index.

## Chunk 2: Export, consume and verify the contract

### Task 3: Export OpenAPI and generated client

**Files:**

- Modify: `apps/api/src/test/java/vn/covie/openapi/OpenApiExportTest.java` — assert authenticated GET path, mandatory location ID, cursor/limit and exact candidate/page fields.
- Regenerate: `packages/api-client/openapi/openapi.json` and `packages/api-client/src/generated/schema.ts`.

- [x] RED: add OpenAPI assertions and run `Set-Location apps/api; .\mvnw.cmd -Dtest=OpenApiExportTest test`; require the missing operation failure.
- [x] GREEN: export with `Set-Location apps/api; .\mvnw.cmd -Dtest=OpenApiExportTest "-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json" test`.
- [x] Run from root `pnpm api-client:generate` and `pnpm api-client:check`; inspect the generated operation and page schema. No handwritten candidate DTO in web.

### Task 4: Wire Seller NEED-006 using only returned candidates

**Files:**

- Modify: `apps/web/features/needs/need-api.ts` — generated-path browser helper with encoded merchant/Need/location/cursor.
- Modify: `apps/web/features/needs/merchant-need-list.tsx` — load eligible page for the selected Need/location on deliberate card expansion, with loading/empty/error/retry and cursor load-more; cancel or ignore stale requests when location/card changes.
- Modify: `apps/web/features/needs/merchant-need-response-form.tsx` — use generated candidate type and accept only returned `locationVariantId`; retain `maxResponseItems` and idempotency-key behavior, clear selection when Need/location/candidate set changes.
- Create: `apps/web/tests/needs/seller-need-candidates.test.tsx` — route/list/form boundary tests.
- Update: `docs/seller-mobile-coverage-matrix.md`, `docs/seller-desktop-coverage-matrix.md`, `docs/CoVie-ui-handoff-matrix.md`.

- [x] Read exact frozen [NEED-005 mobile `387:2001`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=387-2001), [NEED-006 selection `387:2124`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=387-2124), [existing response `387:2277`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=387-2277), [state stack `387:2413`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=387-2413) and [Seller desktop `387:13665`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=387-13665) contexts. Map expansion/response hierarchy, touch targets, desktop layout and loading/empty/error states to existing Seller primitives before changing UI code. Record sample-only fields omitted from the generated contract.
- [x] RED: prove no response checkbox/form appears before the generated eligible GET succeeds; selected location and Need IDs are passed to that GET; no list-summary or raw UUID fallback exists.
- [x] RED: prove eligible results render exact server labels/prices and only their IDs enter POST; max-item selection, empty result, retryable error, load-more cursor and location switch discard stale selection/response.
- [x] Run `pnpm --filter @covie/web exec vitest run tests/needs/seller-need-candidates.test.tsx` and observe missing-contract/UI failure.
- [x] GREEN: implement minimal generated-client read and response surface, retaining safe unavailable messaging until the GET succeeds; do not infer category, coverage, stock or moderation in React.
- [x] Rerun focused web suite, then `pnpm web:typecheck`, `pnpm web:lint`, `pnpm web:test`, `pnpm web:build` sequentially.

## Chunk 3: Completion evidence

### Task 5: Run gates and reconcile status

**Files:** `AGENTS.md`, `docs/CoVie-project-status.md`, `docs/seller-mobile-coverage-matrix.md`, `docs/seller-desktop-coverage-matrix.md`, `docs/CoVie-ui-handoff-matrix.md`, `docs/superpowers/plans/2026-09-12-project-issue-completion.md`.

- [x] Run `Set-Location apps/api; .\mvnw.cmd clean verify`; run root `pnpm api-client:check`, `pnpm web:typecheck`, `pnpm web:lint`, `pnpm web:test`, `pnpm web:build` and `git diff --check`. Record exact totals/working-tree identity, not estimates.
- [x] Inspect API, generated and web diffs; keep Task 10 state accurate and preserve unrelated user changes. Remove NEED-006 `CONTRACT_GAP` only after a passing contract and generated-client UI; keep runtime/visual labels until accepted Seller captures and E2E exist.
- [x] Follow `docs/superpowers/plans/2026-09-12-local-runtime-visual-acceptance.md` for deterministic Seller/Admin fixtures, exact viewport names and comparisons; Seller NEED-005/006 evidence is included in the 111/111 Seller captures with automated and hash-bound manual `PASS`.
- [x] Mark master Task 11 code-level complete only when shared eligibility, ownership, cursor, generated client, Seller response UI and local gates all have direct evidence. Staging remains Task 15.
