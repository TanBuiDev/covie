# Checkout Geography and Immutable Snapshots Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make checkout enforce authoritative PostGIS delivery/service coverage, propagate the measured distance into shipping, and persist fulfillment-safe order snapshots that remain truthful after source records change.

**Architecture:** `CheckoutPreviewService` remains the transaction orchestrator. It resolves the authenticated active address once, constructs a `LocationContext`, asks `DeliveryCoverageEvaluator` for `PRODUCT_DELIVERY` or `SERVICE_AREA`, and rejects an ineligible destination before pricing. `ShippingEvaluator` remains calculation-only; `ShippingPolicySnapshotFactory` owns policy/rule outcome serialization. A focused `CheckoutSnapshotFactory` serializes versioned item, address, location and contact records from entities resolved by repositories. Order creation stores those JSON values unchanged. Pickup deliberately bypasses geographic coverage.

**Tech Stack:** Spring Boot 4, Java 25, JPA/Hibernate, PostgreSQL/PostGIS, Flyway, Jackson, JUnit 5, Mockito, Testcontainers.

---

## Chunk 1: Checkout coverage and actual distance

### Task 1: Specify fulfillment geography at the service boundary

**Files:**
- Modify: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutPreviewSnapshotTest.java`

- [x] RED: add separate tests for eligible and ineligible `SELLER_DELIVERY` using `CoverageType.PRODUCT_DELIVERY`.
- [x] RED: add separate tests for eligible and ineligible `SERVICE` using `CoverageType.SERVICE_AREA`.
- [x] RED: capture the `ShippingEvaluator` argument and assert the exact `CoverageEvaluation.distanceMeters()` value, never `0` unless PostGIS actually returns zero.
- [x] RED: prove `PICKUP` does not invoke `DeliveryCoverageEvaluator`, requires no buyer address, and stores a null distance.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=CheckoutPreviewSnapshotTest test`; observe each new behavior fail against the current service.

### Task 2: Implement coverage enforcement without duplicate distance math

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutPreviewService.java`
- Use unchanged: `apps/api/src/main/java/vn/covie/delivery/application/DeliveryCoverageEvaluator.java`
- Use unchanged: `apps/api/src/main/java/vn/covie/delivery/persistence/JdbcCoverageEvaluationRepository.java`
- Use unchanged: `apps/api/src/main/java/vn/covie/location/application/LocationContext.java`

- [x] Inject `DeliveryCoverageEvaluator` into checkout and construct `LocationContext(SAVED_ADDRESS, point, locationAreaId, addressId, Asia/Ho_Chi_Minh)` from the already ownership/status-checked `UserAddressEntity`.
- [x] Map `SELLER_DELIVERY` to `PRODUCT_DELIVERY` and `SERVICE` to `SERVICE_AREA`; throw `CHECKOUT_FULFILLMENT_NOT_SUPPORTED` when coverage is absent/ineligible.
- [x] Pass the eligible coverage distance into `ShippingEvaluator`; store it in `FulfillmentSnapshots`; keep pickup independent with null distance.
- [x] GREEN: rerun `CheckoutPreviewSnapshotTest` and both checkout concurrency tests.

### Task 3: Prove the PostGIS boundary

**Files:**
- Create: `apps/api/src/test/java/vn/covie/checkout/persistence/CheckoutDeliveryCoverageIntegrationTest.java`
- Verify unchanged: `apps/api/src/main/java/vn/covie/delivery/persistence/JdbcCoverageEvaluationRepository.java`

- [x] RED/GREEN: seed active radius and area coverage against real migrated PostGIS tables and assert eligible/ineligible `PRODUCT_DELIVERY` and `SERVICE_AREA`, ancestor-area matching, and a non-zero rounded distance.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=CheckoutDeliveryCoverageIntegrationTest test`.

## Chunk 2: Versioned snapshot factories

### Task 4: Specify complete item and fulfillment-safe contact snapshots

**Files:**
- Modify: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutPreviewSnapshotTest.java`
- Create: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutSnapshotFactoryTest.java`
- Modify: `apps/api/src/test/java/vn/covie/shipping/application/ShippingPolicySnapshotFactoryTest.java`

- [x] RED: assert item snapshot fields: `schemaVersion`, catalog item id/name/type/listing kind, variant id/name/SKU, offer id/unit price/currency/preparation time, and source entity versions.
- [x] RED: assert buyer-address/location snapshots contain only fulfillment fields plus `schemaVersion`, ids, labels/names, address text, coordinates, area/timezone/capabilities and source versions.
- [x] RED: define buyer contact as the selected delivery/service recipient name and phone; pickup stores an empty versioned buyer-contact snapshot. Do not copy unrelated account contacts.
- [x] RED: define merchant contact as active public contacts applicable to the selected location (location-specific plus merchant-wide), including only type/value/QR id/primary; exclude inactive/private contacts.
- [x] RED: expand policy assertions to include `schemaVersion`, policy id/version/status/mode/base fee/currency/rule mode, serialized enabled state/rules, subtotal, distance, matched rule ids and effective fee for FREE, FIXED, NEGOTIATED and CONDITIONAL.
- [x] Run focused tests and record the expected missing-field failures.

### Task 5: Implement focused snapshot ownership

**Files:**
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutSnapshotFactory.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutPreviewService.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/FulfillmentSnapshots.java` only if typed accessors/nullability need clarification; retain JSON persistence compatibility.
- Modify: `apps/api/src/main/java/vn/covie/shipping/application/ShippingPolicySnapshotFactory.java`
- Verify unchanged calculation behavior: `apps/api/src/main/java/vn/covie/shipping/application/ShippingEvaluator.java`
- Inject/read: `apps/api/src/main/java/vn/covie/catalog/persistence/CatalogItemRepository.java`
- Inject/read: `apps/api/src/main/java/vn/covie/catalog/persistence/VariantRepository.java`
- Inject/read: `apps/api/src/main/java/vn/covie/users/persistence/UserContactMethodRepository.java` only if the final test-approved contact model needs it; default design uses selected-address recipient data instead.
- Inject/read: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantContactMethodRepository.java`

`CheckoutSnapshotFactory.java` owns these nested versioned records in the same file: `ItemSnapshot`, `AddressSnapshot`, `LocationSnapshot`, `BuyerContactSnapshot`, `MerchantContactSnapshot`, and `MerchantContactEntrySnapshot`. `ShippingPolicySnapshotFactory.java` similarly owns nested `PolicySnapshot` and `RuleSnapshot` records. No anonymous `Map` is used for these persisted schemas. `CheckoutPreviewSnapshotTest` directly constructs the service and therefore must add mocks/instances for `DeliveryCoverageEvaluator`, `CheckoutSnapshotFactory`, `CatalogItemRepository`, `VariantRepository`, `MerchantContactMethodRepository`, and `ShippingPolicySnapshotFactory`; no concurrency-test constructor change is required because those tests autowire/mock the Spring bean.

- [x] Resolve each item/variant by the candidate ids and fail with `CHECKOUT_ITEM_UNAVAILABLE` if the entity chain is inconsistent.
- [x] Serialize immutable JSON through `CheckoutSnapshotFactory`; do not let controllers or order read models reconstruct snapshots later.
- [x] Filter merchant contacts to active, public and either merchant-wide or matching the selected location.
- [x] Invoke `ShippingPolicySnapshotFactory.snapshot(policy, rules, evaluation)` after calculation and pass that JSON through `FulfillmentSnapshots`; do not add snapshot creation to `ShippingEvaluator`.
- [x] GREEN: run `CheckoutSnapshotFactoryTest`, `ShippingPolicySnapshotFactoryTest`, and `CheckoutPreviewSnapshotTest`.

## Chunk 3: Persistence and mutation independence

### Task 6: Persist service distance where order reads can expose it

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V014__service_fulfillment_distance.sql`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/ServiceFulfillmentEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/ProductFulfillmentEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCreationService.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderReadService.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderFulfillmentResponse.java`
- Modify: `apps/api/src/test/java/vn/covie/orders/application/OrderReadServiceTest.java`
- Modify: `apps/api/src/test/java/vn/covie/checkout/persistence/Plan04SchemaMigrationTest.java`
- Modify: `infra/scripts/verify-clean-migration.sh`
- Create: `scripts/tests/verify-clean-migration.test.sh`
- Modify: `docs/operations/staging-rehearsal.md`
- Modify: `docs/operations/pilot-launch-checklist.md`

- [x] RED: assert a service order read returns its measured `distanceMeters`, product delivery returns its shipping-policy snapshot, and pickup keeps both fields null.
- [x] Add nullable `distance_meters` plus `check (distance_meters is null or distance_meters >= 0)` to `service_fulfillments`; update its entity factory/getter and read mapper.
- [x] Add `ProductFulfillmentEntity.getShippingPolicySnapshot()`, append `JsonNode shippingPolicy` to `OrderFulfillmentResponse`, and map it for product orders while returning null for service orders.
- [x] Update `Plan04SchemaMigrationTest` to require Flyway version >= `014`, inspect service distance column nullability/type and the non-negative constraint, and reject a negative service distance fixture.
- [x] Change `verify-clean-migration.sh` default to 14, add a stub-`psql` shell regression in `scripts/tests/verify-clean-migration.test.sh`, and update the staging rehearsal/checklist from V013/next V014 to V014/next V015.
- [x] GREEN: run `Set-Location apps/api; .\mvnw.cmd -Dtest=OrderReadServiceTest,Plan04SchemaMigrationTest test` and run the shell regression through Git Bash.
- [x] Export OpenAPI with `Set-Location apps/api; .\mvnw.cmd -Dtest=OpenApiExportTest "-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json" test`; from root run `pnpm api-client:generate`, `pnpm api-client:check`, and `pnpm web:typecheck`.

### Task 7: Prove stored orders are independent of mutable sources

**Files:**
- Create: `apps/api/src/test/java/vn/covie/orders/OrderSnapshotImmutabilityIntegrationTest.java`
- Modify only if the test exposes a defect: `apps/api/src/main/java/vn/covie/orders/application/OrderCreationService.java`
- Verify: `apps/api/src/main/java/vn/covie/orders/application/OrderReadService.java`

- [x] Create product-delivery and service orders through checkout/commit using deterministic catalog, address, contact, coverage and shipping records.
- [x] Mutate catalog name/variant/offer price, user address/phone, merchant location/contact and shipping policy/rules after order creation.
- [x] Read the orders as authorized buyer/merchant and assert item, address, contact, location, distance and policy values remain the original captured values.
- [x] Assert Buyer, authorized Merchant and Admin receive the intended snapshot fields. Assert an unrelated actor receives the existing 404 with no order payload; do not describe that path as field redaction.
- [x] Run `Set-Location apps/api; .\mvnw.cmd -Dtest=OrderSnapshotImmutabilityIntegrationTest test`.

## Final verification and documentation

- [x] Run `Set-Location apps/api; .\mvnw.cmd clean verify` and record exact pass/skip totals.
- [x] From repository root run `pnpm api-client:check`, `pnpm web:typecheck`, `pnpm web:lint`, `pnpm web:test`, and `pnpm web:build` because constructor/API read changes cross module boundaries.
- [x] Run `git diff --check` and inspect the complete diff; do not include generated `apps/web/next-env.d.ts` churn.
- [x] Update `AGENTS.md` and `docs/CoVie-project-status.md` with the migration, exact test evidence and resolved limitations; keep E2E/visual/staging explicitly separate.
- [x] Mark Chunk 2 Tasks 6–7 in `2026-09-12-project-issue-completion.md` complete only after all focused and full gates pass.
