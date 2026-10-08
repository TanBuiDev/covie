# Public Merchant Capabilities and Catalog Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace PUB-009's contract-gap placeholder with an anonymous, moderation-safe public merchant catalog and server-owned Product/Service capability summary, without inventing or persisting a Shop/Provider discriminator.

**Architecture:** Public visibility is one invariant shared by the merchant profile, capability summary, and catalog page: the merchant is active and approved/not-required; an item is active and approved/not-required; and it has an active variant with an available, non-deleted offer at an active, non-deleted location owned by that merchant. A dedicated JDBC read repository applies that invariant once for capability aggregation and cursor-paged catalog rows. Capabilities are the ordered set of actually public `CatalogItemType` values (`PRODUCT`, `SERVICE`), not a merchant type. The new anonymous `/api/v1/catalog/merchants/{merchantId}/items` operation returns compact cards ordered newest-first with an opaque, merchant-scoped cursor. The generated OpenAPI client remains the web source of truth.

**Tech Stack:** Spring Boot 4, Java 25, Spring Security, JDBC/PostgreSQL, OpenAPI, Next.js 16, React 19, TypeScript, Vitest, Testing Library.

---

## Chunk 1: Lock the public read contract

### Task 1: Specify merchant capability visibility

**Files:**
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantCapabilitiesResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/PublicMerchantCatalogRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/JdbcPublicMerchantCatalogRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/PublicMerchantViewService.java`
- Modify: `apps/api/src/test/java/vn/covie/catalog/api/PublicCatalogDetailControllerIntegrationTest.java`

The capability shape is `catalogTypes: [PRODUCT, SERVICE]`, kept in enum order and never exposed as `SHOP` or `PROVIDER`. Empty public inventory produces an empty array. A type is present only when at least one item of that type satisfies the complete public-availability invariant above.

- [x] RED: prove an anonymous active Product-only merchant returns `capabilities.catalogTypes == [PRODUCT]`.
- [x] RED: prove a Service-only merchant returns `[SERVICE]`, and a merchant with both returns `[PRODUCT, SERVICE]` in stable enum order.
- [x] RED: independently prove draft/paused/deleted listings, pending/rejected/hidden moderation, inactive/deleted variants, unavailable/deleted offers, paused/deleted locations, and offers at another merchant's location do not contribute a capability.
- [x] RED: prove a visible merchant with no qualifying item returns `catalogTypes: []`; do not turn absence of inventory into a 404.
- [x] RED: retain the existing boundary that paused, deleted, pending, rejected, or hidden merchants return the same `404 RESOURCE_NOT_FOUND` to anonymous and authenticated callers.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=PublicCatalogDetailControllerIntegrationTest test`; expect missing-schema/repository failures.
- [x] GREEN: implement one repository capability query using the exact public-availability joins and inject it into `PublicMerchantViewService`.
- [x] GREEN: append the capability object to `PublicMerchantResponse`; do not add a persisted merchant discriminator or infer capability from location flags alone.
- [x] Rerun the focused integration test and require all cases green.

### Task 2: Specify the public merchant catalog page

**Files:**
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantCatalogItemResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantCatalogResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantCatalogCursor.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/application/PublicMerchantCatalogCursorCodec.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/application/PublicMerchantCatalogService.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/persistence/PublicMerchantCatalogRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/persistence/JdbcPublicMerchantCatalogRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/PublicCatalogController.java`
- Create: `apps/api/src/test/java/vn/covie/catalog/api/PublicMerchantCatalogControllerIntegrationTest.java`
- Create: `apps/api/src/test/java/vn/covie/catalog/application/PublicMerchantCatalogCursorCodecTest.java`

`GET /api/v1/catalog/merchants/{merchantId}/items?limit=&cursor=` is anonymous. `limit` defaults to 12 and accepts 1..50. `PublicMerchantCatalogResponse` owns one `page: CursorPageResponse<PublicMerchantCatalogItemResponse>` field; the nested page owns `items`, `hasMore`, and nullable `nextCursor`. Rows are newest-first with `itemId` as the deterministic tie-breaker; cursor payloads carry `createdAt`, `itemId`, and merchant scope. Each card contains exactly `id`, `type`, `listingKind`, `name`, `slug`, nullable `primaryMedia: PublicMediaResponse`, and `priceFrom: Money { amount, currency }`, calculated across qualifying offers. It deliberately omits distance, open-now, delivery eligibility, and a resolved location because the route has no buyer location context.

- [x] RED: prove the endpoint is anonymous and returns only qualifying items belonging to the path merchant.
- [x] RED: prove all item, moderation, variant, offer, location, and cross-merchant boundaries from Task 1 also prevent catalog-row leakage.
- [x] RED: prove `priceFrom` is the lowest qualifying offer price, pending media is omitted, and ready primary media exposes the public URL without a storage key.
- [x] RED: prove newest-first stable pagination has no duplicates or omissions across two pages and returns `page.items`, `page.hasMore`, and `page.nextCursor` in the exact wrapper shape.
- [x] RED: prove limits outside 1..50, malformed cursors, and cursors replayed against another merchant return `422 CATALOG_FILTER_INVALID`.
- [x] RED: prove an active visible merchant with no qualifying rows returns `200` with an empty page, while a non-public or missing merchant returns `404 RESOURCE_NOT_FOUND`.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=PublicMerchantCatalogControllerIntegrationTest,PublicMerchantCatalogCursorCodecTest test`; expect missing-route/type failures.
- [x] GREEN: implement the opaque codec and service normalization, require the merchant through `PublicMerchantViewService.requireVisible`, and fetch `limit + 1` rows from the dedicated repository.
- [x] GREEN: keep the repository's visibility predicate textually centralized and reused by both capability and page queries so the two public surfaces cannot drift.
- [x] GREEN: expose the controller route with explicit `limit` and `cursor` parameters and no authentication-dependent behavior.
- [x] Rerun the focused suites and require all cases green.

### Task 3: Export and lock the generated operation

**Files:**
- Regenerate: `packages/api-client/openapi/openapi.json`
- Regenerate: `packages/api-client/src/generated/schema.ts`

- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=OpenApiExportTest "-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json" test`.
- [x] Run `pnpm api-client:generate` and `pnpm api-client:check` from the repository root.
- [x] Inspect the generated `/api/v1/catalog/merchants/{merchantId}/items` operation, capability and page schemas; no handwritten client type is allowed.

## Chunk 2: Replace PUB-009's safe gap state

### Required prerequisite: reviewed local-runtime/visual plan

Before Task 4 or Task 5 changes any UI file, create and obtain reviewer approval for `docs/superpowers/plans/2026-09-12-local-runtime-visual-acceptance.md` as required by the master plan. It must inventory existing `.local/dev/visual-review/chunk3/` evidence, name PUB-009 and all remaining Seller/Admin/Buyer routes and states, exact Figma nodes including PUB-009 acceptance `312:3743`, deterministic fixture/setup scripts, Playwright specs, 360×800/390×844/1280×900 captures, screenshot filenames, tolerances, and pass criteria. This prerequisite plans evidence; it does not claim visual acceptance before runtime captures exist.

### Task 4: Read exact frozen PUB-009 frames

**Files:**
- Reference: `apps/web/features/merchant/merchant-profile.tsx`
- Reference: `apps/web/features/merchant/merchant-profile.module.css`
- Reference: `apps/web/tests/merchant-profile.test.tsx`

Exact frozen references: [Shop full `312:3076`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=312-3076), [Provider `312:3289`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=312-3289), [Shop 360 `312:3491`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=312-3491), [state stack `312:3689`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=312-3689), [loading `312:3692`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=312-3692), [safe unavailable `312:3715`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=312-3715), and [acceptance `312:3743`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=312-3743).

- [x] Before any UI edit, retrieve the exact design context for every applicable frame above and map its card anatomy, type wording, empty/error states, spacing, tokens and responsive behavior to existing CoVie components.
- [x] Record the deliberate contract rule: Product-only may use Shop wording, Service-only may use Provider wording, and mixed/empty merchants use neutral “Đơn vị cung cấp”; this is presentation derived from returned capabilities, never a stored discriminator.

### Task 5: Wire profile capability and catalog rendering

**Files:**
- Modify: `apps/web/features/merchant/merchant-server-api.ts`
- Modify: `apps/web/app/(public)/merchants/[merchantId]/page.tsx`
- Modify: `apps/web/features/merchant/merchant-profile.tsx`
- Modify: `apps/web/features/merchant/merchant-profile.module.css`
- Modify: `apps/web/tests/merchant-profile.test.tsx`
- Create: `apps/web/tests/merchant-profile-page.test.tsx`

- [x] RED: prove Product-only, Service-only, mixed, and empty capability arrays select truthful headings/badges without any client-side item guessing.
- [x] RED: prove generated catalog cards link Product, Service, and Resale items through the repository's single existing route `/items/{id}`, render ready media and server price, and never fabricate availability/open/delivery claims.
- [x] RED: prove empty results replace the old contract-unavailable message and catalog fetch failure renders a retry link while merchant identity remains available.
- [x] RED: prove the server page calls the generated merchant-items operation with `searchParams.catalogCursor`, passes the returned page into `MerchantProfile`, and retains review failure isolation.
- [x] RED: prove `page.hasMore` always emits a next-page link containing exactly the server-returned opaque cursor and the current merchant path; following it requests page 2 and replaces (does not append to) the current page's cards. Prove the retry link preserves the same cursor and merchant scope.
- [x] GREEN: add the server read using only the generated operation, fetch merchant/catalog/reviews concurrently, and isolate optional catalog/review failures from the public identity response.
- [x] GREEN: render capability-backed neutral/Shop/Provider presentation and compact reusable cards; remove the old “Chưa thể tải…” contract-gap copy only after generated types compile.
- [x] GREEN: implement unconditional URL/search-parameter pagination in the server page. Read `catalogCursor` from Next.js `searchParams`; next and retry links preserve the merchant route and pass the opaque cursor exactly as returned/received. Page navigation replaces the visible card page; no browser API helper, client-side cursor decoding, or append state is introduced.
- [x] Run `pnpm --filter @covie/web exec vitest run tests/merchant-profile.test.tsx tests/merchant-profile-page.test.tsx`, then `pnpm web:typecheck` and `pnpm web:lint`.

## Final verification and evidence

- [x] Run both focused API suites and the cursor-codec suite, then OpenAPI export/client generation and `pnpm api-client:check`.
- [x] Run the focused web suites, `pnpm web:typecheck`, and `pnpm web:lint`.
- [x] Run `Set-Location apps/api; .\mvnw.cmd clean verify`, `pnpm web:test`, and `pnpm web:build`; record exact totals: API 491 tests / 1 skipped; web 58 files / 139 tests.
- [x] Run `git diff --check` and inspect all API/generated/web changes; no generated `apps/web/next-env.d.ts` churn was present.
- [x] Update `docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md`, `docs/buyer-mobile-remaining-coverage-matrix.md`, `docs/CoVie-ui-handoff-matrix.md`, `docs/CoVie-project-status.md`, `AGENTS.md`, this child plan, and the master plan with contract/runtime evidence. Keep visual acceptance pending until exact local viewports are rendered and compared.
- [x] Mark master-plan Task 9 complete only when public visibility/moderation, pagination, Product/Service/mixed summaries, empty results, generated-client use, and web gates are all green.
