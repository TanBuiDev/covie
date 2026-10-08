# Seller Catalog Management Detail and Edit Implementation Plan

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` task-by-task. Use test-driven development for every behavior change and obtain plan review before implementation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the SEL-010/011/012 existing-item contract gap by adding an authenticated, ownership-safe catalog management detail read with explicit resource versions and server-owned actions, then wire a generated-client Seller editor that never reconstructs mutable state from list summaries.

**Architecture:** `GET /api/v1/merchants/{merchantId}/catalog-items/{itemId}` is the authoritative aggregate read for Seller editing. It requires an active merchant membership, returns 404 for a foreign/deleted merchant item, and includes the item's moderation/lifecycle state plus editable attributes, tags, media, option groups, variants, and location offers. OWNER/MANAGER receive all non-deleted offers; STAFF receive only offers at their assigned active locations, matching the existing location-list visibility boundary. The item response carries its item version and HTTP `ETag`; each mutable variant and offer carries its own version. Actions are scoped explicitly: item-level actions live on the aggregate, variant actions live on each variant, and offer actions live on each visible offer. A shared policy uses the same merchant capability, lifecycle, publishability, final-variant, and location-assignment checks as the command handlers. The web reads only generated OpenAPI types, mounts `/seller/[merchantId]/catalog/[itemId]`, and refreshes authoritative detail after every mutation or version conflict.

**Hard visual prerequisite:** The reviewed `docs/superpowers/plans/2026-09-12-local-runtime-visual-acceptance.md` remains authoritative for deterministic fixtures, routes, viewports, capture names and comparison criteria. Design context must be retrieved before any Task 4–6 UI edit; code-level completion must retain `VISUAL_PENDING` until that later runtime plan produces accepted captures.

**Completion audit — 2026-09-20:** Task 10 is complete for the tracked local scope. Fresh focused evidence passes 24 API tests across management detail, edit, lifecycle and publish readiness and 28 web tests across list navigation, generated server reads, the edit route and form. OpenAPI export, client regeneration and client typecheck pass; the generated contract contains the aggregate GET, response/action enums, nested versions and `ETag`. SEL-010…013 and lifecycle evidence contributes 20 captures across 360/390/1024/1280/1440, all automated and hash-bound manual `PASS`. The later full local gate remains Maven 538 tests (1 intentional skip), 76 Vitest files / 210 tests, typecheck, zero-warning lint, production build and the complete 81-case / 201-capture verifier. Staging remains a separate Task 15 gate.

**Tech Stack:** Spring Boot 4, Java 25, Spring Data JPA, PostgreSQL, MockMvc/Testcontainers, OpenAPI, Next.js 16, React 19, TypeScript, Vitest, Testing Library.

---

## Chunk 1: Lock the management-detail contract

### Task 1: Specify aggregate detail, ownership and empty states

**Files:**

- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/CatalogItemManagementDetailResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogManagementQueryService.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/MerchantCatalogController.java`
- Create: `apps/api/src/test/java/vn/covie/catalog/api/MerchantCatalogManagementDetailControllerIntegrationTest.java`

The response fields are fixed as follows:

- Aggregate: `id`, `type`, `listingKind`, `categoryId`, `name`, nullable `description`, `listingStatus`, `moderationStatus`, `version`, `allowedActions: ItemAction[]`. `ItemAction` is nested in the response and has stable declaration/response order `UPDATE`, `CREATE_VARIANT`, `PUBLISH`, `PAUSE`, `ACTIVATE`, `ARCHIVE`.
- Attribute: `categoryAttributeId`, canonical `attributeKey`, and exactly one applicable typed value among nullable `valueText`, `valueNumber`, `valueBoolean`, `optionId`; this is directly reusable in `UpdateCatalogItemRequest.attributes` without key reconstruction.
- Tag: ordered `String` names matching the accepted update payload.
- Media association: `id`, `contentType`, `status`, nullable `url` only when safely resolvable, `sortOrder`, `primary`; storage provider/key and owner are never exposed.
- Option group: `id`, `name`, `sortOrder`, with ordered values `id`, `value`, `sortOrder`.
- Variant: `id`, nullable `sku`, `name`, `defaultVariant`, `status`, `sortOrder`, ordered `selectedOptionIds`, `version`, `allowedActions: VariantAction[]`. Nested `VariantAction` has stable order `UPDATE`, `DELETE`.
- Offer: `id`, `merchantLocationId`, `merchantLocationName`, `variantId`, `price`, `currency`, `available`, nullable `inventoryMode`, nullable `preparationTimeMinutes`, `version`, `allowedActions: OfferAction[]`. Nested `OfferAction` has the sole member `UPDATE`.

Empty attributes, tags, media, options, variants, and offers are explicit empty arrays. OWNER/MANAGER see all non-deleted offers for the verified item. STAFF see only non-deleted offers at assigned active locations; unassigned offer data and actions are omitted entirely.

- [x] RED: prove an authenticated OWNER and MANAGER can read a DRAFT, ACTIVE, PAUSED, moderation-pending, rejected, or hidden item belonging to their merchant, including exact moderation and listing values.
- [x] RED: prove anonymous access is 401; absent membership, foreign merchant/item pairs, missing items, and soft-deleted items return the same `404 RESOURCE_NOT_FOUND` without leaking ownership.
- [x] RED: prove the body item `version` matches HTTP `ETag`, and every variant/offer exposes its independent version used by the existing granular mutations.
- [x] RED: prove attributes retain canonical keys plus text/number/boolean/option values, tag names are stable, media ordering/primary/readiness is represented without exposing a storage key, and option/variant combinations are preserved losslessly.
- [x] RED: prove OWNER/MANAGER receive every non-deleted offer belonging to the item, while STAFF receive only assigned-active-location offers; an unassigned offer's data and action are both absent.
- [x] RED: prove no-media, no-option and no-offer records return successful detail with empty arrays; do not convert edit incompleteness into 404.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=MerchantCatalogManagementDetailControllerIntegrationTest test` and observe the missing route/schema failure.
- [x] GREEN: add the minimal aggregate query and `GET /{itemId}` controller response with `ETag`; reuse current repositories and `MediaUrlResolver`, adding only deterministic repository reads that are actually missing.
- [x] GREEN: apply merchant membership before item lookup and scope every nested record through the already verified item ID/merchant pair.
- [x] Rerun the focused integration suite green.

### Task 2: Centralize server-owned edit and lifecycle actions

**Files:**

- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogManagementActionPolicy.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/security/MerchantAccessPolicy.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogPublishValidator.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogManagementQueryService.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogEditService.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogLifecycleService.java`
- Modify: `apps/api/src/test/java/vn/covie/catalog/api/MerchantCatalogManagementDetailControllerIntegrationTest.java`
- Modify: `apps/api/src/test/java/vn/covie/catalog/api/CatalogLifecycleControllerIntegrationTest.java`

- [x] RED: prove OWNER/MANAGER receive item `UPDATE`/`CREATE_VARIANT`, variant `UPDATE` and offer `UPDATE`; STAFF receives no item/variant action and receives offer `UPDATE` only on returned assigned-location offers.
- [x] RED: prove the exact lifecycle truth table below and stable action ordering.

| Item action | Required state and policy |
| --- | --- |
| `PUBLISH` | Role allows `PUBLISH_CATALOG`; item is `DRAFT`; the exact shared publishability check passes, including active/public merchant, active compatible category, required attributes, active variant, available offer at valid location, and ready media. |
| `ACTIVATE` | Role allows `PUBLISH_CATALOG`; item is `PAUSED`; moderation is `NOT_REQUIRED` or `APPROVED`; the same full publishability check passes. |
| `PAUSE` | Role allows `PUBLISH_CATALOG`; item is `ACTIVE`. Merchant visibility and item moderation do not add a new restriction. |
| `ARCHIVE` | Role allows `PUBLISH_CATALOG`; item is non-deleted. Merchant visibility and item moderation do not add a new restriction. |

Every command revalidates its identical row transactionally; the read contract is advisory and never replaces command authorization/concurrency checks.
- [x] RED: prove variant `DELETE` is absent for the final non-deleted variant and present only when the same count/permission predicate used by delete command succeeds.
- [x] RED: prove a stale item/variant/offer `If-Match` still returns `409 RESOURCE_VERSION_CONFLICT`, while a missing precondition remains the existing required-precondition error.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=MerchantCatalogManagementDetailControllerIntegrationTest,MerchantCatalogEditControllerIntegrationTest,CatalogLifecycleControllerIntegrationTest,CatalogPublishReadinessIntegrationTest test` and observe action/predicate assertions fail before implementation.
- [x] GREEN: expose `MerchantAccessPolicy.allows` as the shared role/capability decision, add a non-mutating publishability result backed by the exact checks in `CatalogPublishValidator`, and introduce one action policy used by detail reads and corresponding edit/lifecycle command preconditions. Command handlers must still revalidate transactionally.
- [x] GREEN: use the same variant-count predicate and `requireLocation` assignment boundary in both returned actions and mutation handlers; no action may promise more than its command accepts at read time.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=MerchantCatalogManagementDetailControllerIntegrationTest,MerchantCatalogEditControllerIntegrationTest,CatalogLifecycleControllerIntegrationTest,CatalogPublishReadinessIntegrationTest test` and require green.

### Task 3: Export and verify the generated contract

**Files:**

- Regenerate: `packages/api-client/openapi/openapi.json`
- Regenerate: `packages/api-client/src/generated/schema.ts`

- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=OpenApiExportTest "-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json" test`.
- [x] Run `pnpm api-client:generate` and `pnpm api-client:check`.
- [x] Inspect the generated GET operation, response, action enum, nested versions, empty-array shapes and `ETag` header; no handwritten web DTO is allowed.

---

## Chunk 2: Build the existing-item Seller editor

### Task 4: Read exact frozen Seller catalog frames

**Files:**

- Reference: `docs/visual-acceptance-cases.csv`
- Reference: `apps/web/features/catalog/catalog-create-form.tsx`
- Reference: `apps/web/app/seller-catalog.css`
- Reference: `apps/web/app/seller-catalog-desktop.css`

Exact frozen references: [SEL-010 Product editor `368:854`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=368-854), [SEL-011 Service `368:1026`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=368-1026), [SEL-012 variants `368:1139`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=368-1139), [SEL-013 Resale `368:1271`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=368-1271), [lifecycle state `368:1535`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=368-1535), [media error `368:1578`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=368-1578), and [Seller Desktop editor `370:745`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=370-745). The deterministic edit route is `/seller/33333333-3333-4333-8333-333333333333/catalog/66666666-6666-4666-8666-666666666661`.

- [x] Before any UI edit, retrieve exact design context for every applicable frame and map fields, hierarchy, status/action controls, 44px touch targets, responsive columns, loading/error/conflict states and desktop composition to existing CoVie primitives.
- [x] Record any sample-only field absent from the generated contract and omit it rather than inventing data.

### Task 5: Add generated-client reads and item navigation

**Files:**

- Modify: `apps/web/features/catalog/catalog-server-api.ts`
- Modify: `apps/web/features/catalog/catalog-management-list.tsx`
- Modify: `apps/web/tests/seller/catalog-management-list.test.tsx`
- Modify: `apps/web/tests/catalog-server-api.test.ts`

- [x] RED: prove each valid catalog row links to `/seller/{merchantId}/catalog/{itemId}` while preserving Product/Service/Resale filters and empty state.
- [x] RED: prove the server helper calls only the generated management-detail GET and returns both body and `ETag`.
- [x] Run `pnpm --filter @covie/web exec vitest run tests/seller/catalog-management-list.test.tsx tests/catalog-server-api.test.ts` and observe missing-link/helper failures.
- [x] GREEN: add `getCatalogManagementDetailForServer` and item links without changing list-summary business meaning.
- [x] Rerun `pnpm --filter @covie/web exec vitest run tests/seller/catalog-management-list.test.tsx tests/catalog-server-api.test.ts`, then `pnpm web:typecheck` and `pnpm web:lint` green.

### Task 6: Implement versioned item, offer and lifecycle mutations

**Files:**

- Modify: `apps/web/features/catalog/catalog-api.ts`
- Create: `apps/web/features/catalog/catalog-edit-form.tsx`
- Create: `apps/web/features/catalog/catalog-edit-form.test.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/catalog/[itemId]/page.tsx`
- Create: `apps/web/tests/seller/catalog-edit-page.test.tsx`
- Modify: `apps/web/features/seller/seller-page-title.tsx`
- Modify: `apps/web/app/seller-catalog.css`
- Modify: `apps/web/app/seller-catalog-desktop.css`

- [x] RED: prove the edit route renders exact item/category/moderation state, attributes/tags/media, variants and location offers from detail rather than list reconstruction.
- [x] RED: prove item PATCH uses the item `ETag`; variant and offer PATCH use their own versions; each success refreshes the route and disables duplicate submission while pending.
- [x] RED: prove lifecycle buttons are rendered only from `allowedActions`, call the matching command with the item `ETag`, require confirmation for archive, and refresh authoritative state after success.
- [x] RED: prove OWNER/MANAGER/STAFF controls mount only from the relevant item-, variant-, or offer-level returned action. Presence of response data alone must never create an edit button.
- [x] RED: prove `RESOURCE_VERSION_CONFLICT` retains unsaved input, disables further mutation, and presents an explicit “Tải dữ liệu mới” action; ordinary validation/network failures remain retryable without clearing input.
- [x] RED: prove missing media/offers/options have truthful editable empty states; Product/Resale inventory controls never appear for Service; existing option combinations are read-only when no safe option-structure mutation exists.
- [x] Run `pnpm --filter @covie/web exec vitest run features/catalog/catalog-edit-form.test.tsx tests/seller/catalog-edit-page.test.tsx` and observe missing form/route/mutation behavior failures.
- [x] GREEN: implement the smallest separate edit form using generated schemas and existing upload/category/location helpers. Do not reuse create submission, replace the aggregate, accept raw UUID input, or locally decode permission/state.
- [x] Rerun `pnpm --filter @covie/web exec vitest run features/catalog/catalog-edit-form.test.tsx tests/seller/catalog-edit-page.test.tsx`, then typecheck and lint.

---

## Final verification and evidence

- [x] Run management-detail, catalog-edit and lifecycle focused API suites; export OpenAPI, regenerate the client and run `pnpm api-client:check`.
- [x] Run focused web suites, `pnpm web:typecheck`, and `pnpm web:lint`.
- [x] Run `Set-Location apps/api; .\mvnw.cmd clean verify`, `pnpm web:test`, and `pnpm web:build`; record exact totals.
- [x] Run `git diff --check` and inspect all API/generated/web changes; revert unrelated generated churn.
- [x] Update `docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md`, `docs/seller-mobile-coverage-matrix.md`, `docs/seller-desktop-coverage-matrix.md`, `docs/CoVie-ui-handoff-matrix.md`, `docs/CoVie-project-status.md`, `AGENTS.md`, this child plan, and the master plan. Remove only the existing-item contract-gap labels proven by tests; keep runtime/visual acceptance pending until exact local captures pass.
- [x] Mark master-plan Task 10 complete only when the management detail/ETag/action contract, generated client, existing-item route, version-conflict behavior and all local quality gates are green.
