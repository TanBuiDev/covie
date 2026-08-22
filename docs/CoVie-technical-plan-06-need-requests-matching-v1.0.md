
# CoVie — Plan 06: Need Requests & Matching

> Technical specification and implementation checklist. This document defines product behavior, data contracts, and verification criteria; it does not prescribe an agent, tool, or execution framework.

**Plan:** 06 / 10  
**Source document:** `docs/CoVie-technical-plan-06-need-requests-matching-v1.0.md`  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** In implementation

**Goal:** Implement CoVie V1 structured Need Requests, PostgreSQL/PostGIS seller matching, MerchantLocation-scoped catalog responses, Direct Checkout attribution, Order-confirmation fulfillment, Need expiration, OpenAPI/client integration, and minimal buyer/seller web slices without introducing AI matching, chat, or a separate search service.

**Architecture:** Need is a first-class domain module in the existing Spring Boot modular monolith. PostgreSQL/PostGIS remains authoritative for Need state, geospatial filtering, response uniqueness, source attribution and scheduled expiration. Matching uses category/type, PostgreSQL FTS/trigram signals, distance, delivery/service coverage, current LocationVariant availability and tracked inventory; the application layer owns final eligibility. Need-origin checkout plugs into the Plan 04 `CheckoutCandidateResolver` SPI and reuses the existing Preview → Commit engine. Need fulfillment is a synchronous transaction participant in Plan 05 seller/provider confirmation so `ACTIVE -> FULFILLED` is committed atomically with the confirming Order.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1.0, Spring MVC, Spring Data JPA, JdbcTemplate/native PostgreSQL queries, Flyway, PostgreSQL 18 + PostGIS 3.6, `pg_trgm`, `unaccent`, JUnit 5, Testcontainers, Springdoc OpenAPI 3.x, Next.js 16.2, React 19, TypeScript strict, Vitest/RTL, generated `openapi-typescript` + `openapi-fetch` client.

---

# Global Constraints

- Public API base path remains `/api/v1`.
- Spring Boot remains the single authoritative business layer; Next.js must not reimplement matching, Need lifecycle, checkout validation or fulfillment rules.
- Business IDs use UUIDv7.
- JPA entities are never serialized directly.
- Money remains integer minor units plus ISO currency code; Need response price snapshots are audit values, never authoritative checkout prices.
- Absolute timestamps are UTC and business logic uses injected `AppClock`.
- Flyway is the only schema-change path. Hibernate stays `ddl-auto=validate`.
- Plan 05 ended at `V010__order_lifecycle.sql`; Plan 06 starts at `V011__need_matching.sql`; Plan 07 must start at `V012`.
- Never edit already-applied `V001`–`V010`.
- `NeedRequest` has exactly one `PRODUCT | SERVICE` type and a required compatible category.
- API-level Need status is `ACTIVE | FULFILLED | EXPIRED | CANCELLED`. This plan intentionally uses `CANCELLED` rather than the older database draft name `CLOSED_BY_BUYER`, because the approved API contract is newer.
- Need exact buyer address/location snapshot is private. Seller discovery exposes only safe area/distance summaries required to decide whether to respond.
- One ACTIVE `NeedResponse` is allowed per `(need_request_id, merchant_location_id)`.
- The same MerchantProfile may respond from several eligible MerchantLocations.
- A response item always references an existing `LocationVariant`; seller cannot submit arbitrary price/name text as the purchasable offer.
- Editing a response must not delete historical `NeedResponseItem` rows. Old items are retired so an already-created Order can continue to reference `source_need_response_item_id`.
- Need response max items comes from `NeedPolicyProvider`; Plan 06 ships a bootstrap property-backed implementation, and Plan 08 later replaces resolution with scoped `SystemSetting`.
- Need matching is PostgreSQL/PostGIS-based. No AI requirement, vector database, OpenSearch/Elasticsearch, Kafka or microservice is added.
- Need Direct Checkout is not a Cart. It uses `CheckoutSourceType.NEED_REQUEST`.
- One Need-origin checkout refers to exactly one current `NeedResponseItem`; the buyer may choose Product quantity >= 1, while Service quantity is exactly 1.
- Preview never locks price, Need or inventory and never reserves inventory.
- Commit locks/revalidates the Need source chain before inventory locks and Order creation.
- Need-origin Orders use `source_type = NEED_REQUEST`, `source_need_request_id`, and `source_need_response_item_id`.
- Creating a PENDING Order never fulfills a Need.
- Only Order confirmation can transition a source Need from ACTIVE to FULFILLED.
- If a Need is already FULFILLED by another confirmed sourced Order, another explicitly placed sourced Order may still confirm; `fulfilled_order_id` remains the first confirming Order and no duplicate Need transition/outbox event is emitted.
- If Need is CANCELLED or EXPIRED before a previously created Order confirms, that Order remains independently valid; confirmation does not resurrect the Need.
- If Need `expires_at` has passed but scheduler has not yet run, the confirmation participant treats the Need as expired rather than silently fulfilling stale demand.
- Seller response creation/edit/withdraw and buyer cancel use a common lock order: `NeedRequest` row first, then `NeedResponse`, then response items.
- Need Checkout Commit lock order is `checkout_session -> NeedRequest -> NeedResponse -> response item -> inventory balances -> Order creation`.
- No external network call is allowed while Need/checkout/order transaction locks are held.
- Every meaningful Need mutation writes outbox intent in the same business transaction. Plan 07 dispatches outbox/notifications/SSE.
- Redis is not authoritative for Need status, response uniqueness, matching eligibility, checkout attribution or expiration.
- Browser mutation remains protected by the Plan 02 auth/cookie/CSRF policy.
- Merchant permission is resolved from DB per request through Plan 03 `MerchantAccessPolicy`; STAFF remains MerchantLocation-scoped.
- Private buyer Need detail uses owner authorization. Seller access is only through the seller discovery/response read models, not the buyer-private `/me` resource.
- All list/search endpoints use opaque cursor pagination.
- No generic query DSL is introduced.

---

# Approved Business Contract Carried Into Plan 06

```text
Buyer:
Create structured Need
→ ACTIVE
→ seller/provider responses
→ buyer chooses one response item
→ Checkout Preview
→ Checkout Commit
→ Order PENDING_CONFIRMATION
→ Need stays ACTIVE
→ seller/provider confirms Order
→ Need becomes FULFILLED

Seller/provider:
Discover matching Need
→ choose one eligible MerchantLocation
→ respond with 1..max LocationVariant references
→ optionally edit response while Need ACTIVE
→ optionally withdraw response

System:
Need expires at expires_at
→ EXPIRED
unless already FULFILLED/CANCELLED
```

Need response history:

```text
NeedResponse ACTIVE
├── edit -> same response version increments
│           old response items RETIRED
│           replacement response items ACTIVE
└── withdraw -> WITHDRAWN

A withdrawn response stays in audit history.
An Order created from an earlier response item stays valid.
```

Need source chain:

```text
NeedRequest
   ↓
NeedResponse
   ↓
NeedResponseItem
   ↓
LocationVariant
   ↓
Variant
   ↓
CatalogItem
   ↓
MerchantLocation
```

Every Preview/Commit from `NEED_REQUEST` validates the complete chain.

---

# Migration Ordering Locked by Plan 06

Existing:

```text
V001__extensions_and_foundation.sql
V002__identity_users_and_audit.sql
V003__auth_sessions_and_tickets.sql
V004__location_merchant_media.sql
V005__catalog_and_search.sql
V006__idempotency_and_outbox.sql
V007__delivery_shipping_inventory.sql
V008__carts_and_checkout_sessions.sql
V009__order_creation_foundation.sql
V010__order_lifecycle.sql
```

Plan 06:

```text
V011__need_matching.sql
```

Plan 07 starts at:

```text
V012
```

`outOfOrder=false` remains mandatory.

---

# Stable Interfaces Consumed From Plans 01–05

```text
vn.covie.shared.id.UuidV7Generator
vn.covie.shared.time.AppClock
vn.covie.shared.security.CurrentActorProvider
vn.covie.shared.idempotency.IdempotencyExecutor
vn.covie.shared.outbox.OutboxEventWriter
vn.covie.shared.outbox.OutboxEventIntent
vn.covie.location.application.LocationContext
vn.covie.location.application.LocationContextResolver
vn.covie.merchants.security.MerchantAccessPolicy
vn.covie.merchants.security.MerchantCapability
vn.covie.merchants.persistence.MerchantLocationRepository
vn.covie.catalog.persistence.LocationVariantRepository
vn.covie.catalog.model.LocationVariantEntity
vn.covie.delivery.application.DeliveryCoverageEvaluator
vn.covie.inventory.persistence.InventoryBalanceRepository
vn.covie.checkout.application.CheckoutCandidate
vn.covie.checkout.application.CheckoutCandidateItem
vn.covie.checkout.application.CheckoutCandidateResolver
vn.covie.checkout.application.CheckoutCandidateResolverRegistry
vn.covie.checkout.application.CheckoutPreviewService
vn.covie.checkout.application.CheckoutCommitService
vn.covie.checkout.application.CheckoutChange
vn.covie.checkout.application.CheckoutChangeType
vn.covie.checkout.application.RevalidatedCheckout
vn.covie.checkout.model.CheckoutSourceType
vn.covie.orders.application.OrderCommandService
vn.covie.orders.application.OrderTransitionService
vn.covie.orders.model.OrderEntity
vn.covie.orders.model.OrderSourceType
vn.covie.orders.model.OrderStatus
vn.covie.orders.persistence.OrderRepository
```

Plan 06 adds stable boundaries for later plans:

```text
vn.covie.needs.application.NeedPolicyProvider
vn.covie.needs.application.NeedReadService
vn.covie.needs.application.NeedCommandService
vn.covie.needs.application.NeedMatchingService
vn.covie.needs.application.NeedResponseService
vn.covie.needs.application.NeedCheckoutCandidateResolver
vn.covie.needs.application.NeedFulfillmentOnOrderConfirm
vn.covie.needs.application.NeedScheduledTransitionService
vn.covie.needs.model.NeedRequestEntity
vn.covie.needs.model.NeedResponseEntity
vn.covie.needs.model.NeedResponseItemEntity
vn.covie.needs.model.NeedStatus
vn.covie.needs.model.NeedType
```

---

# Repository Map Added by Plan 06

```text
apps/api/src/main/java/vn/covie/
├── needs/
│   ├── api/
│   │   ├── MeNeedController.java
│   │   ├── NeedResponseController.java
│   │   ├── MerchantNeedDiscoveryController.java
│   │   └── dto/
│   │       ├── CreateNeedRequest.java
│   │       ├── NeedSummaryResponse.java
│   │       ├── NeedDetailResponse.java
│   │       ├── NeedLocationSummaryResponse.java
│   │       ├── CreateNeedResponseRequest.java
│   │       ├── ReplaceNeedResponseRequest.java
│   │       ├── NeedResponseResponse.java
│   │       ├── NeedResponseItemResponse.java
│   │       ├── MerchantNeedMatchResponse.java
│   │       └── NeedCursorPage.java
│   ├── application/
│   │   ├── NeedPolicyProvider.java
│   │   ├── NeedPolicySnapshot.java
│   │   ├── PropertyNeedPolicyProvider.java
│   │   ├── NeedLocationSnapshotFactory.java
│   │   ├── NeedReadService.java
│   │   ├── NeedCommandService.java
│   │   ├── NeedMatchingService.java
│   │   ├── NeedResponseEligibilityPolicy.java
│   │   ├── NeedResponseService.java
│   │   ├── NeedResponseItemFactory.java
│   │   ├── NeedCheckoutCandidateResolver.java
│   │   ├── NeedSourceContext.java
│   │   ├── NeedFulfillmentOnOrderConfirm.java
│   │   └── NeedScheduledTransitionService.java
│   ├── model/
│   │   ├── NeedRequestEntity.java
│   │   ├── NeedResponseEntity.java
│   │   ├── NeedResponseItemEntity.java
│   │   ├── NeedType.java
│   │   ├── NeedStatus.java
│   │   ├── NeedResponseStatus.java
│   │   └── NeedResponseItemStatus.java
│   ├── persistence/
│   │   ├── NeedRequestRepository.java
│   │   ├── NeedResponseRepository.java
│   │   ├── NeedResponseItemRepository.java
│   │   ├── NeedDiscoveryRepository.java
│   │   ├── JdbcNeedDiscoveryRepository.java
│   │   ├── NeedMatchRow.java
│   │   └── CategoryHierarchyRepository.java
│   └── scheduling/
│       └── NeedExpirationScheduler.java
├── checkout/
│   ├── model/CheckoutSourceType.java
│   ├── application/CheckoutCandidateResolver.java
│   ├── application/CheckoutCommitService.java
│   ├── application/CheckoutComparisonService.java
│   ├── application/CheckoutChangeType.java
│   ├── application/RevalidatedCheckout.java
│   └── api/dto/CheckoutPreviewRequest.java
└── orders/
    └── application/
        └── OrderConfirmedParticipant.java

apps/api/src/main/resources/
├── application.yml
└── db/migration/
    └── V011__need_matching.sql

apps/api/src/test/java/vn/covie/needs/
├── persistence/
├── application/
├── api/
├── scheduling/
└── support/

apps/web/
├── app/(buyer)/needs/page.tsx
├── app/(buyer)/needs/new/page.tsx
├── app/(buyer)/needs/[needId]/page.tsx
├── app/(seller)/seller/[merchantId]/needs/page.tsx
├── features/needs/
│   ├── need-api.ts
│   ├── need-create-form.tsx
│   ├── need-list.tsx
│   ├── need-detail.tsx
│   ├── need-response-list.tsx
│   ├── merchant-need-list.tsx
│   └── merchant-need-response-form.tsx
└── tests/needs/
    ├── buyer-needs.test.tsx
    └── seller-needs.test.tsx
```

---

# Need Locking Rules

All Need business mutations use database locks, not JVM mutexes.

```text
Buyer cancel:
NeedRequest FOR UPDATE
→ validate buyer/status
→ mutate status
→ outbox
→ commit

Seller create response:
NeedRequest FOR UPDATE
→ merchant/location authorization
→ eligibility/current offers
→ insert NeedResponse + items
→ outbox
→ commit

Seller edit/withdraw:
NeedRequest FOR UPDATE
→ NeedResponse FOR UPDATE
→ response items
→ mutate
→ outbox
→ commit

Need checkout Commit:
checkout_session FOR UPDATE
→ NeedRequest FOR UPDATE
→ NeedResponse FOR UPDATE
→ NeedResponseItem current check
→ inventory balance locks sorted by LocationVariant UUID
→ Order + reservation + outbox
→ commit

Order confirm:
Order FOR UPDATE             # Plan 05
→ Order status -> CONFIRMED
→ NeedRequest FOR UPDATE     # only if source=NEED_REQUEST
→ ACTIVE and not expired ? FULFILLED : no resurrection
→ Order event/outbox + Need outbox
→ commit
```

No Need operation locks inventory before the Need source rows.

---

# Task 1: Add `V011__need_matching.sql` and Lock the Schema Contract

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V011__need_matching.sql`
- Create: `apps/api/src/test/java/vn/covie/needs/persistence/Plan06SchemaMigrationTest.java`

**Interfaces:**
- Consumes: Flyway baseline through V010, existing `orders` and `checkout_sessions`.
- Produces: Need tables, Need indexes, checkout source expansion, Order source FKs/integrity constraints, migration baseline V011.

- [ ] **Step 1: Write the migration test before creating V011**

The test boots a clean PostGIS Testcontainer and asserts all prior migrations plus V011 apply in order.

```java
package vn.covie.needs.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class Plan06SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired DataSource dataSource;

    @Test
    void createsNeedSchemaAndSourceConstraints() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(tableExists(c, "need_requests")).isTrue();
            assertThat(tableExists(c, "need_responses")).isTrue();
            assertThat(tableExists(c, "need_response_items")).isTrue();
            assertThat(indexExists(c, "ux_need_response_active_location")).isTrue();
            assertThat(indexExists(c, "ix_need_requests_geo")).isTrue();
        }
    }
}
```

Run:

```bash
cd apps/api
./mvnw -Dtest=Plan06SchemaMigrationTest test
```

Expected: FAIL because V011/tables do not exist.

- [ ] **Step 2: Create `need_requests`**

Use exactly:

```sql
create table need_requests (
    id uuid primary key,
    buyer_user_id uuid not null references users(id) on delete restrict,
    need_type varchar(20) not null,
    category_id uuid not null references categories(id) on delete restrict,
    title varchar(220) not null,
    query_text text not null,
    description text null,
    requested_quantity bigint null,
    location_area_id uuid null references location_areas(id) on delete restrict,
    geo_point geography(Point,4326) not null,
    location_snapshot jsonb not null,
    search_radius_meters integer not null,
    request_status varchar(30) not null,
    expires_at timestamptz not null,
    fulfilled_order_id uuid null references orders(id) on delete restrict,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    closed_at timestamptz null,
    constraint ck_need_type check (need_type in ('PRODUCT','SERVICE')),
    constraint ck_need_status check (
        request_status in ('ACTIVE','FULFILLED','EXPIRED','CANCELLED')
    ),
    constraint ck_need_requested_quantity check (
        requested_quantity is null or requested_quantity > 0
    ),
    constraint ck_need_radius check (search_radius_meters > 0),
    constraint ck_need_expiry_after_create check (expires_at > created_at),
    constraint ck_need_fulfilled_link check (
        (request_status = 'FULFILLED' and fulfilled_order_id is not null)
        or (request_status <> 'FULFILLED' and fulfilled_order_id is null)
    ),
    constraint ck_need_closed_at check (
        (request_status = 'ACTIVE' and closed_at is null)
        or (request_status <> 'ACTIVE' and closed_at is not null)
    ),
    constraint ck_need_service_quantity check (
        need_type <> 'SERVICE'
        or requested_quantity is null
        or requested_quantity = 1
    )
);
```

`CANCELLED` is the API V1 name. Do not preserve `CLOSED_BY_BUYER` as a second enum value.

- [ ] **Step 3: Create `need_responses`**

```sql
create table need_responses (
    id uuid primary key,
    need_request_id uuid not null references need_requests(id) on delete restrict,
    merchant_profile_id uuid not null references merchant_profiles(id) on delete restrict,
    merchant_location_id uuid not null references merchant_locations(id) on delete restrict,
    response_status varchar(20) not null,
    note text null,
    created_by_user_id uuid not null references users(id) on delete restrict,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    withdrawn_at timestamptz null,
    version bigint not null default 0,
    constraint ck_need_response_status check (
        response_status in ('ACTIVE','WITHDRAWN')
    ),
    constraint ck_need_response_withdrawn_at check (
        (response_status = 'ACTIVE' and withdrawn_at is null)
        or (response_status = 'WITHDRAWN' and withdrawn_at is not null)
    )
);

create unique index ux_need_response_active_location
    on need_responses(need_request_id, merchant_location_id)
    where response_status = 'ACTIVE';
```

`version` exists because the approved edit API requires `If-Match`.

- [ ] **Step 4: Create history-safe `need_response_items`**

```sql
create table need_response_items (
    id uuid primary key,
    need_response_id uuid not null references need_responses(id) on delete restrict,
    catalog_item_id uuid not null references catalog_items(id) on delete restrict,
    variant_id uuid not null references variants(id) on delete restrict,
    location_variant_id uuid not null references location_variants(id) on delete restrict,
    item_status varchar(20) not null,
    price_snapshot bigint not null,
    currency_code char(3) not null,
    availability_snapshot jsonb not null,
    created_at timestamptz not null,
    retired_at timestamptz null,
    constraint ck_need_response_item_status check (
        item_status in ('ACTIVE','RETIRED')
    ),
    constraint ck_need_response_item_retired_at check (
        (item_status = 'ACTIVE' and retired_at is null)
        or (item_status = 'RETIRED' and retired_at is not null)
    ),
    constraint ck_need_response_price check (price_snapshot >= 0)
);

create unique index ux_need_response_active_location_variant
    on need_response_items(need_response_id, location_variant_id)
    where item_status = 'ACTIVE';
```

Never hard-delete a row referenced by an Order source.

- [ ] **Step 5: Add hot-path indexes**

```sql
create index ix_need_requests_buyer_created
    on need_requests(buyer_user_id, created_at desc, id desc);

create index ix_need_requests_status_expiry
    on need_requests(request_status, expires_at, id)
    where request_status = 'ACTIVE';

create index ix_need_requests_category_type_state
    on need_requests(category_id, need_type, request_status, created_at desc);

create index ix_need_requests_geo
    on need_requests using gist(geo_point);

create index ix_need_responses_need_state
    on need_responses(need_request_id, response_status, created_at desc);

create index ix_need_responses_merchant_location
    on need_responses(merchant_profile_id, merchant_location_id, created_at desc);

create index ix_need_response_items_response_state
    on need_response_items(need_response_id, item_status, created_at);
```

- [ ] **Step 6: Expand Checkout source and add Order source FKs**

Drop the Plan 04 source check by its known name, then add `NEED_REQUEST`:

```sql
alter table checkout_sessions
    drop constraint ck_checkout_source_type;

alter table checkout_sessions
    add constraint ck_checkout_source_type
    check (source_type in ('CART','DIRECT','NEED_REQUEST'));
```

Add Order links:

```sql
alter table orders
    add constraint fk_orders_source_need_request
        foreign key (source_need_request_id)
        references need_requests(id)
        on delete restrict,
    add constraint fk_orders_source_need_response_item
        foreign key (source_need_response_item_id)
        references need_response_items(id)
        on delete restrict;

alter table orders
    add constraint ck_order_source_payload
    check (
        (
            source_type = 'MARKETPLACE'
            and source_need_request_id is null
            and source_need_response_item_id is null
        )
        or
        (
            source_type = 'NEED_REQUEST'
            and source_need_request_id is not null
            and source_need_response_item_id is not null
        )
    );
```

Cross-table source-chain correctness stays an application invariant and is tested at checkout.

- [ ] **Step 7: Add migration assertions for historical response items**

Create a fixture:

```text
Need ACTIVE
→ Response ACTIVE with item A
→ Order points to item A
→ item A is RETIRED
```

The FK must remain valid. A direct `DELETE need_response_items where id=:itemA` must fail while the Order references it.

- [ ] **Step 8: Run migration suite**

```bash
./mvnw -Dtest=Plan06SchemaMigrationTest test
```

Expected: PASS.

Then run the clean-database migration suite:

```bash
./mvnw -Dtest='*SchemaMigrationTest' test
```

Expected: V001 → V011 all PASS with `outOfOrder=false`.

- [ ] **Step 9: Commit**

```bash
git add apps/api/src/main/resources/db/migration/V011__need_matching.sql \
        apps/api/src/test/java/vn/covie/needs/persistence \
        apps/api/src/test/java/vn/covie/support
git commit -m "feat(needs): add need matching schema"
```

---

# Task 2: Map Need Entities, Enums and Locking Repositories

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/model/NeedType.java`
- Create: `apps/api/src/main/java/vn/covie/needs/model/NeedStatus.java`
- Create: `apps/api/src/main/java/vn/covie/needs/model/NeedResponseStatus.java`
- Create: `apps/api/src/main/java/vn/covie/needs/model/NeedResponseItemStatus.java`
- Create: `apps/api/src/main/java/vn/covie/needs/model/NeedRequestEntity.java`
- Create: `apps/api/src/main/java/vn/covie/needs/model/NeedResponseEntity.java`
- Create: `apps/api/src/main/java/vn/covie/needs/model/NeedResponseItemEntity.java`
- Create: `apps/api/src/main/java/vn/covie/needs/persistence/NeedRequestRepository.java`
- Create: `apps/api/src/main/java/vn/covie/needs/persistence/NeedResponseRepository.java`
- Create: `apps/api/src/main/java/vn/covie/needs/persistence/NeedResponseItemRepository.java`
- Test: `apps/api/src/test/java/vn/covie/needs/persistence/NeedPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: V011 schema, UUIDv7 and AppClock.
- Produces: persistence contracts used by every Plan 06 task.

- [ ] **Step 1: Write persistence tests first**

```java
@Test void locksNeedRequestByIdForMutation() { }
@Test void findsOnlyActiveResponseForLocation() { }
@Test void retiredResponseItemsRemainReadableById() { }
@Test void optimisticVersionIncrementsWhenResponseIsReplaced() { }
```

Run:

```bash
cd apps/api
./mvnw -Dtest=NeedPersistenceIntegrationTest test
```

Expected: FAIL because entities/repositories do not exist.

- [ ] **Step 2: Define enums exactly once**

```java
public enum NeedType {
    PRODUCT, SERVICE
}

public enum NeedStatus {
    ACTIVE, FULFILLED, EXPIRED, CANCELLED
}

public enum NeedResponseStatus {
    ACTIVE, WITHDRAWN
}

public enum NeedResponseItemStatus {
    ACTIVE, RETIRED
}
```

Do not create parallel API-only enums.

- [ ] **Step 3: Map `NeedRequestEntity`**

Required fields:

```java
@Entity
@Table(name = "need_requests")
public class NeedRequestEntity {

    @Id
    private UUID id;

    private UUID buyerUserId;

    @Enumerated(EnumType.STRING)
    private NeedType needType;

    private UUID categoryId;
    private String title;

    @Column(columnDefinition = "text")
    private String queryText;

    @Column(columnDefinition = "text")
    private String description;

    private Long requestedQuantity;
    private UUID locationAreaId;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point geoPoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode locationSnapshot;

    private int searchRadiusMeters;

    @Enumerated(EnumType.STRING)
    private NeedStatus requestStatus;

    private Instant expiresAt;
    private UUID fulfilledOrderId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
}
```

Add `org.hibernate.annotations.JdbcTypeCode` and `org.hibernate.type.SqlTypes` imports for JSONB. Keep the Plan 02/03 Hibernate Spatial `Point` mapping for `geoPoint`; do not serialize JTS types into HTTP DTOs.

No entity cascade may delete responses/items.

- [ ] **Step 4: Map response and item**

`NeedResponseEntity` carries `@Version`:

```java
@Version
private long version;
```

`NeedResponseItemEntity` has immutable IDs/catalog references and state transition methods:

```java
public void retire(Instant now) {
    if (itemStatus != NeedResponseItemStatus.ACTIVE) {
        return;
    }
    itemStatus = NeedResponseItemStatus.RETIRED;
    retiredAt = now;
}
```

Normal application code never deletes response items.

- [ ] **Step 5: Add explicit lock queries**

Need row:

```java
@Query(value = """
    select *
    from need_requests
    where id = :id
    for update
    """, nativeQuery = true)
Optional<NeedRequestEntity> lockById(@Param("id") UUID id);
```

Response row:

```java
@Query(value = """
    select *
    from need_responses
    where id = :responseId
      and need_request_id = :needId
    for update
    """, nativeQuery = true)
Optional<NeedResponseEntity> lockByNeedAndId(
        @Param("needId") UUID needId,
        @Param("responseId") UUID responseId);
```

Active response:

```java
Optional<NeedResponseEntity> findByNeedRequestIdAndMerchantLocationIdAndResponseStatus(
        UUID needRequestId,
        UUID merchantLocationId,
        NeedResponseStatus status);
```

Response items:

```java
List<NeedResponseItemEntity> findByNeedResponseIdAndItemStatusOrderByCreatedAtAsc(
        UUID responseId,
        NeedResponseItemStatus status);
```

- [ ] **Step 6: Do not expose destructive repository helpers**

Do not add service methods such as:

```text
deleteNeedResponse()
deleteNeedResponseItem()
deleteAllResponseItems()
```

The repository inherits framework methods, but normal application services must use status transitions only.

- [ ] **Step 7: Run tests**

```bash
./mvnw -Dtest=NeedPersistenceIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/needs/model \
        apps/api/src/main/java/vn/covie/needs/persistence \
        apps/api/src/test/java/vn/covie/needs/persistence
git commit -m "feat(needs): add need persistence model"
```

---

# Task 3: Add Need Policy, Category Hierarchy and Safe Location Snapshot Contracts

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedPolicySnapshot.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/PropertyNeedPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedLocationSnapshotFactory.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedLocationContextFactory.java`
- Create: `apps/api/src/main/java/vn/covie/needs/persistence/CategoryHierarchyRepository.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedPolicyProviderTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/persistence/CategoryHierarchyRepositoryIntegrationTest.java`

**Interfaces:**
- Consumes: Plan 03 categories/location context.
- Produces: scoped-policy seam for Plan 08, reusable category compatibility check, private snapshot/safe summary split.

- [ ] **Step 1: Write policy and category tests**

```java
@Test void bootstrapPolicyReturnsConfiguredResponseItemLimit() { }
@Test void productNeedAcceptsProductOrBothCategory() { }
@Test void serviceNeedAcceptsServiceOrBothCategory() { }
@Test void descendantCategoryMatchesRequestedAncestor() { }
@Test void unrelatedCategoryDoesNotMatch() { }
```

Run focused tests and observe failure.

- [ ] **Step 2: Define the Plan 08-compatible policy seam**

```java
public record NeedPolicySnapshot(int maxResponseItems) {
    public NeedPolicySnapshot {
        if (maxResponseItems < 1) {
            throw new IllegalArgumentException("maxResponseItems must be positive");
        }
    }
}

public interface NeedPolicyProvider {
    NeedPolicySnapshot resolve(
            UUID merchantId,
            UUID categoryId,
            UUID locationAreaId);
}
```

Plan 06 bootstrap adapter:

```java
@Component
@ConfigurationProperties(prefix = "covie.needs")
public final class PropertyNeedPolicyProvider implements NeedPolicyProvider {

    private int maxResponseItems = 3;

    @Override
    public NeedPolicySnapshot resolve(
            UUID merchantId,
            UUID categoryId,
            UUID locationAreaId) {
        return new NeedPolicySnapshot(maxResponseItems);
    }

    public void setMaxResponseItems(int maxResponseItems) {
        this.maxResponseItems = maxResponseItems;
    }
}
```

Configuration:

```yaml
covie:
  needs:
    max-response-items: 3
```

This is a business default, not a rate limit.

- [ ] **Step 3: Implement category hierarchy compatibility**

Use PostgreSQL recursive CTE because Plan 03 stores only `categories.parent_id`.

```sql
with recursive subtree as (
    select id, parent_id
    from categories
    where id = :requestedCategoryId
      and status = 'ACTIVE'
      and deleted_at is null

    union all

    select c.id, c.parent_id
    from categories c
    join subtree s on c.parent_id = s.id
    where c.status = 'ACTIVE'
      and c.deleted_at is null
)
select exists(
    select 1
    from subtree
    where id = :candidateCategoryId
);
```

Repository methods:

```java
boolean isSameOrDescendant(UUID requestedCategoryId, UUID candidateCategoryId);
boolean supportsNeedType(UUID categoryId, NeedType needType);
```

`catalog_type = BOTH` supports both Need types.

- [ ] **Step 4: Implement internal Need location snapshot**

`NeedLocationSnapshotFactory` receives the canonical `LocationContext` and stores only immutable information needed later:

```json
{
  "source": "SAVED_ADDRESS",
  "addressId": "0198...",
  "areaId": "0198...",
  "areaPath": ["Vinhomes Grand Park", "The Origami"],
  "latitude": 10.84,
  "longitude": 106.83,
  "timezone": "Asia/Ho_Chi_Minh",
  "addressText": "internal exact text if source address is owned"
}
```

The internal JSON may retain exact buyer address for audit. It is never serialized into seller discovery DTOs.

`NeedLocationContextFactory.from(NeedRequestEntity need)` reconstructs the existing Plan 03 value for coverage evaluation without creating a new geo abstraction:

```java
public LocationContext from(NeedRequestEntity need) {
    NeedLocationSnapshot snapshot = snapshots.read(need.locationSnapshot());
    return new LocationContext(
            LocationResolutionSource.COORDINATES,
            need.geoPoint(),
            need.locationAreaId(),
            null,
            ZoneId.of(snapshot.timezone()));
}
```

The factory reuses the already-persisted JTS `Point`; it does not parse latitude/longitude strings or call a geocoder. Add a focused test proving radius + selected-area coverage receives the same point/area as the Need snapshot.

- [ ] **Step 5: Define the seller-safe summary**

Only expose:

```text
area label/path
distanceMeters from the seller location
Need search radius
Need type/category/title/query/description requested by buyer
expiry
```

Never expose:

```text
buyer addressId
exact address text
raw latitude/longitude
geo_point WKT
buyer phone/contact
```

- [ ] **Step 6: Run tests and commit**

```bash
./mvnw -Dtest=NeedPolicyProviderTest,CategoryHierarchyRepositoryIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs \
        apps/api/src/main/resources/application.yml \
        apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): add policy category and location contracts"
```

---

# Task 4: Implement Buyer Need Creation with Idempotency

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/CreateNeedRequest.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/NeedDetailResponse.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedCommandService.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/MeNeedController.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedCommandServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/api/MeNeedCreateControllerIntegrationTest.java`

**Interfaces:**
- Consumes: CurrentActorProvider, LocationContextResolver, CategoryHierarchyRepository, IdempotencyExecutor, OutboxEventWriter.
- Produces: `POST /api/v1/me/need-requests` returning 201.

- [ ] **Step 1: Write failing creation tests**

Required cases:

```java
@Test void createsProductNeedFromOwnedAddress() { }
@Test void createsServiceNeedFromExplicitCoordinates() { }
@Test void rejectsCategoryIncompatibleWithNeedType() { }
@Test void rejectsPastExpiry() { }
@Test void rejectsIncompleteLatLngPair() { }
@Test void rejectsAddressOwnedByAnotherUser() { }
@Test void sameIdempotencyKeyAndBodyReturnsSameNeed() { }
@Test void sameIdempotencyKeyWithDifferentBodyConflicts() { }
```

- [ ] **Step 2: Define request DTO**

```java
public record CreateNeedRequest(
        @NotNull NeedType type,
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 220) String title,
        @NotBlank @Size(max = 500) String queryText,
        @Size(max = 4000) String description,
        @Positive Long requestedQuantity,
        UUID addressId,
        Double lat,
        Double lng,
        @Positive int radiusMeters,
        @NotNull Instant expiresAt) {}
```

Product quantity is optional. Service must not accept quantity greater than one as a pseudo booking scale:

```text
SERVICE requestedQuantity -> null or 1
PRODUCT requestedQuantity -> null or positive
```

Service package/scale remains Variant-defined later.

- [ ] **Step 3: Resolve location through the canonical Plan 03 resolver**

```java
LocationContext location = locationContextResolver.resolveForDiscovery(
        request.lat(),
        request.lng(),
        request.addressId());
```

Do not construct JTS Point directly in the Need module.

- [ ] **Step 4: Validate category and expiry**

```text
category ACTIVE
category catalog_type supports NeedType
expiresAt > AppClock.now()
radiusMeters > 0
title/queryText normalized for whitespace but original Unicode content preserved
```

Do not silently change the buyer-selected expiry.

- [ ] **Step 5: Implement idempotent create transaction**

Controller requires:

```http
POST /api/v1/me/need-requests
Idempotency-Key: <opaque-client-key>
```

Use:

```java
return idempotencyExecutor.execute(
        actor.userId(),
        "need.create",
        idempotencyKey,
        request,
        201,
        NeedDetailResponse.class,
        () -> createInCurrentTransaction(actor.userId(), request));
```

Inside the transaction:

```text
resolve/validate location
validate category/type
generate UUIDv7
snapshot location
insert ACTIVE Need
append outbox NEED_CREATED
return DTO
```

Outbox payload is minimal:

```json
{
  "needRequestId": "0198...",
  "buyerUserId": "0198...",
  "needType": "PRODUCT",
  "categoryId": "0198..."
}
```

- [ ] **Step 6: Return `201 Created` with Location header**

```http
Location: /api/v1/me/need-requests/{needId}
```

Never return the JPA entity.

- [ ] **Step 7: Add machine-readable errors**

Add at minimum:

```text
NEED_CATEGORY_TYPE_MISMATCH
NEED_EXPIRY_INVALID
NEED_LOCATION_INVALID
```

Location ownership uses the existing location/address visibility error semantics.

- [ ] **Step 8: Run tests**

```bash
./mvnw -Dtest=NeedCommandServiceIntegrationTest,MeNeedCreateControllerIntegrationTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/api/src/main/java/vn/covie/needs \
        apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java \
        apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): create structured buyer needs"
```

---

# Task 5: Implement Buyer Need List, Detail and Cancel

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/NeedSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/NeedLocationSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/NeedCursorPage.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedTimestampCursor.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedTimestampCursorCodec.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedReadService.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedCommandService.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/api/MeNeedController.java`
- Test: `apps/api/src/test/java/vn/covie/needs/api/MeNeedReadControllerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/api/MeNeedCancelControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Need repositories, actor identity, IdempotencyExecutor/outbox.
- Produces: buyer-private Need reads and `ACTIVE -> CANCELLED` command.

- [ ] **Step 1: Write owner/privacy tests**

```java
@Test void buyerListsOnlyOwnNeeds() { }
@Test void buyerReadsOwnNeedDetail() { }
@Test void anotherUserGets404ForPrivateNeedDetail() { }
@Test void cancelActiveNeedTransitionsToCancelled() { }
@Test void cancelFulfilledNeedConflicts() { }
@Test void cancelExpiredNeedConflicts() { }
@Test void cancelAndResponseCreateRaceHasOneSerializedOutcome() { }
```

- [ ] **Step 2: Treat past `expiresAt` as non-active in reads even before scheduler catches up**

For list/detail presentation, if a persisted row is still `ACTIVE` but `expiresAt <= AppClock.now()`, map its effective read status as `EXPIRED` and omit it from an `status=ACTIVE` query. The scheduler remains responsible for persisting the transition.

- [ ] **Step 3: Implement cursor list**

Endpoint:

```http
GET /api/v1/me/need-requests?status=ACTIVE&limit=20&cursor=...
```

Keyset order:

```text
created_at DESC, id DESC
```

Internal cursor value:

```java
public record NeedTimestampCursor(Instant createdAt, UUID id) {}
```

`NeedTimestampCursorCodec` serializes `{"v":1,"createdAt":...,"id":...}` with the shared Jackson mapper and encodes the bytes using URL-safe Base64 without padding. Decode rejects malformed Base64, unknown cursor version, missing fields and invalid timestamps/UUIDs with `422 VALIDATION_FAILED`. The client must treat the resulting string as opaque and must never derive paging behavior from its contents.

- [ ] **Step 4: Implement buyer detail**

```http
GET /api/v1/me/need-requests/{needRequestId}
```

Response may include exact owned location summary because this is the owner route, but do not expose raw PostGIS objects.

Include:

```text
id
type
status
category
title/queryText/description
requestedQuantity
locationSummary
radiusMeters
expiresAt
responseCount
fulfilledOrderId?
createdAt
closedAt?
```

- [ ] **Step 5: Implement cancel command under Need row lock**

```http
POST /api/v1/me/need-requests/{needRequestId}/cancel
Idempotency-Key: ...
```

Transaction:

```text
lock NeedRequest FOR UPDATE
→ if buyer mismatch: 404
→ if ACTIVE: CANCELLED + closedAt/updatedAt
→ append NEED_CANCELLED outbox
→ commit
```

Do not delete existing responses. They become non-actionable because parent Need is no longer ACTIVE.

- [ ] **Step 6: State conflict contract**

If owner can see the Need but it is not cancellable:

```http
409 Conflict
```

```json
{
  "error": {
    "code": "NEED_STATE_CONFLICT",
    "details": {
      "currentStatus": "FULFILLED"
    },
    "requestId": "req_..."
  }
}
```

- [ ] **Step 7: Run tests and commit**

```bash
./mvnw -Dtest=MeNeedReadControllerIntegrationTest,MeNeedCancelControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs \
        apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): add buyer need lifecycle"
```

---

# Task 6: Implement PostgreSQL/PostGIS Merchant Need Discovery

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/persistence/NeedMatchRow.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedDiscoveryCursor.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedDiscoveryCursorCodec.java`
- Create: `apps/api/src/main/java/vn/covie/needs/persistence/NeedDiscoveryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/needs/persistence/JdbcNeedDiscoveryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedMatchingService.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/MerchantNeedMatchResponse.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/MerchantNeedDiscoveryController.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/security/MerchantCapability.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/security/MerchantAccessPolicy.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantLocationRepository.java`
- Modify: `apps/api/src/test/java/vn/covie/merchants/security/MerchantAccessPolicyIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/persistence/NeedDiscoveryRepositoryIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/api/MerchantNeedDiscoveryControllerIntegrationTest.java`

**Interfaces:**
- Consumes: merchant membership/location scope, catalog/LocationVariant, PostGIS, delivery coverage, inventory.
- Produces: `GET /api/v1/merchants/{merchantId}/need-requests` seller discovery read model.

- [ ] **Step 1: Add merchant capabilities**

Extend the existing enum with:

```java
VIEW_NEEDS,
RESPOND_TO_NEEDS
```

Role matrix:

```text
OWNER   -> VIEW_NEEDS, RESPOND_TO_NEEDS
MANAGER -> VIEW_NEEDS, RESPOND_TO_NEEDS
STAFF   -> VIEW_NEEDS, RESPOND_TO_NEEDS, but `requireLocation(...)`
           must still enforce the assigned MerchantLocation set
```

Update `MerchantAccessPolicy.allows(...)` and its exhaustive integration/unit matrix in the same task. Do not put merchant roles into JWT.

Add one reusable scope method instead of duplicating STAFF logic in Need code:

```java
public Set<UUID> allowedLocationIds(
        UUID merchantId,
        MerchantCapability capability) {

    MerchantAccess access = require(merchantId, capability);

    if (access.role() == MerchantRole.STAFF) {
        return memberLocations.findAllByMembershipId(access.membershipId())
                .stream()
                .map(MerchantMemberLocationEntity::merchantLocationId)
                .collect(Collectors.toUnmodifiableSet());
    }

    return locations.findAllByMerchantProfileIdAndLocationStatusAndDeletedAtIsNull(
                    merchantId,
                    MerchantLocationStatus.ACTIVE)
            .stream()
            .map(MerchantLocationEntity::id)
            .collect(Collectors.toUnmodifiableSet());
}
```

Add the repository method with that exact name. `NeedMatchingService` calls `allowedLocationIds(...)` and intersects an optional `locationId` query filter with that set.

- [ ] **Step 2: Write matching tests with real PostGIS**

Fixtures must prove:

```java
@Test void productNeedMatchesAvailableProductOfferInsideRadius() { }
@Test void serviceNeedRequiresServiceAreaCoverage() { }
@Test void productNeedMayMatchPickupEnabledLocationInsideRadius() { }
@Test void trackedOutOfStockOfferDoesNotQualify() { }
@Test void inactiveOrHiddenCatalogDoesNotQualify() { }
@Test void categoryDescendantMatchesRequestedParent() { }
@Test void unrelatedCategoryDoesNotMatch() { }
@Test void expiredNeedIsNeverReturnedEvenBeforeSchedulerRuns() { }
@Test void staffSeesMatchesOnlyForAssignedLocations() { }
@Test void sellerDtoDoesNotContainExactBuyerCoordinatesOrAddress() { }
```

- [ ] **Step 3: Query candidate pairs, not arbitrary global feed**

Return one row per:

```text
(NeedRequest, eligible MerchantLocation)
```

This makes multi-location response behavior explicit.

Implement the candidate query in `JdbcNeedDiscoveryRepository` with `NamedParameterJdbcTemplate`; the predicate set and ordering below are part of the Plan 06 contract:

```sql
with recursive category_paths as (
    select
        c.id as descendant_id,
        c.id as ancestor_id
    from categories c
    where c.status = 'ACTIVE'
      and c.deleted_at is null

    union all

    select
        cp.descendant_id,
        parent.parent_id as ancestor_id
    from category_paths cp
    join categories parent on parent.id = cp.ancestor_id
    where parent.parent_id is not null
),
candidate as (
    select
        n.id as need_id,
        ml.id as merchant_location_id,
        st_distance(n.geo_point, ml.geo_point) as distance_meters,
        max(
            greatest(
                ts_rank(
                    ci.search_vector,
                    websearch_to_tsquery('simple', unaccent(n.query_text))
                ),
                similarity(
                    unaccent(lower(ci.name)),
                    unaccent(lower(n.query_text))
                )
            )
        ) as text_rank
    from need_requests n
    join merchant_locations ml
      on ml.merchant_profile_id = :merchantId
     and ml.id in (:allowedLocationIds)
     and ml.location_status = 'ACTIVE'
     and ml.deleted_at is null
    join location_variants lv
      on lv.merchant_location_id = ml.id
     and lv.is_available = true
     and lv.deleted_at is null
    join variants v
      on v.id = lv.variant_id
     and v.variant_status = 'ACTIVE'
     and v.deleted_at is null
    join catalog_items ci
      on ci.id = v.catalog_item_id
     and ci.item_type = n.need_type
     and ci.listing_status = 'ACTIVE'
     and ci.moderation_status in ('NOT_REQUIRED','APPROVED')
     and ci.deleted_at is null
    join category_paths cp
      on cp.descendant_id = ci.primary_category_id
     and cp.ancestor_id = n.category_id
    where n.request_status = 'ACTIVE'
      and n.expires_at > :now
      and st_dwithin(n.geo_point, ml.geo_point, n.search_radius_meters)
    group by n.id, ml.id
)
select *
from candidate;
```

Do not remove or weaken these predicates when refactoring the query. Do not implement a generic search DSL.

- [ ] **Step 4: Apply inventory eligibility in SQL or deterministic application filtering**

For Product:

```text
UNTRACKED -> eligible when offer available
TRACKED   -> require inventory_balance.stock_on_hand - reserved_quantity > 0
```

Service has no inventory.

- [ ] **Step 5: Apply coverage/pickup eligibility through the existing evaluator**

After the DB narrows candidate pairs, call `NeedLocationContextFactory.from(need)` and then the existing Plan 04 `DeliveryCoverageEvaluator.evaluate(locationId, coverageType, needLocation)`:

```text
PRODUCT:
  eligible if
  pickup_enabled == true
  OR PRODUCT_DELIVERY coverage evaluation is eligible

SERVICE:
  SERVICE_AREA coverage evaluation must be eligible
```

Checkout still validates the buyer-selected fulfillment method later.

- [ ] **Step 6: Implement stable keyset paging across coverage filtering**

The SQL candidate order is exactly:

```text
text_rank DESC
distance_meters ASC
need_id ASC
merchant_location_id ASC
```

Define:

```java
public record NeedDiscoveryCursor(
        double textRank,
        long distanceMeters,
        UUID needId,
        UUID merchantLocationId) {}
```

`NeedDiscoveryCursorCodec` uses the same versioned URL-safe Base64/Jackson convention as `NeedTimestampCursorCodec`. The repository method is:

```java
List<NeedMatchRow> findCandidates(
        UUID merchantId,
        Set<UUID> allowedLocationIds,
        UUID requestedLocationId,
        Instant now,
        NeedDiscoveryCursor after,
        int candidateLimit);
```

The final SELECT applies this lexicographic keyset predicate before the ORDER BY:

```sql
where :cursorAbsent
   or text_rank < :cursorTextRank
   or (text_rank = :cursorTextRank and distance_meters > :cursorDistance)
   or (text_rank = :cursorTextRank and distance_meters = :cursorDistance and need_id > :cursorNeedId)
   or (text_rank = :cursorTextRank and distance_meters = :cursorDistance and need_id = :cursorNeedId and merchant_location_id > :cursorLocationId)
order by text_rank desc, distance_meters asc, need_id asc, merchant_location_id asc
limit :candidateLimit
```

When `requestedLocationId` is non-null, reject it with 404 unless it is in `allowedLocationIds`, then narrow the repository set to exactly that location. Never use OFFSET for seller discovery.

Coverage is evaluated in the application layer, so `NeedMatchingService` uses bounded over-fetch rather than returning a short page accidentally:

```text
requested API limit = L (1..50)
SQL candidate batch size = min(max(L * 3, 30), 150)
process candidates in SQL order
for each candidate: apply pickup/coverage eligibility
append eligible rows until L outputs collected
if output still < L and SQL returned a full candidate batch:
    fetch next candidate batch after the last processed candidate cursor
stop after 5 candidate batches or repository exhaustion
nextCursor = cursor of the last processed candidate when more candidates may exist
hasMore = true only when another candidate batch may exist
```

The service must not skip unprocessed rows from a fetched batch: stop processing immediately when the L-th eligible row is appended, and set `nextCursor` to that processed row.

Do not return `totalElements`.

- [ ] **Step 7: Build safe seller DTO**

Example:

```json
{
  "need": {
    "id": "0198...",
    "type": "PRODUCT",
    "title": "Cần Coca-Cola 1.5L",
    "description": "Cần 2 chai",
    "categoryId": "0198...",
    "expiresAt": "2026-08-09T12:00:00Z"
  },
  "resolvedLocation": {
    "id": "0198...",
    "name": "S10.03"
  },
  "buyerArea": {
    "label": "The Origami"
  },
  "distanceMeters": 850,
  "alreadyResponded": false,
  "responseConstraints": {
    "maxItems": 3
  }
}
```

No exact buyer geo/address.

- [ ] **Step 8: Run tests**

```bash
./mvnw -Dtest=NeedDiscoveryRepositoryIntegrationTest,MerchantNeedDiscoveryControllerIntegrationTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/api/src/main/java/vn/covie/needs \
        apps/api/src/main/java/vn/covie/merchants/security/MerchantCapability.java \
        apps/api/src/main/java/vn/covie/merchants/security/MerchantAccessPolicy.java \
        apps/api/src/main/java/vn/covie/merchants/persistence/MerchantLocationRepository.java \
        apps/api/src/test/java/vn/covie/merchants/security/MerchantAccessPolicyIntegrationTest.java \
        apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): discover merchant need matches"
```

---

# Task 7: Implement Response Eligibility Policy and Snapshot Factory

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseEligibilityPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseItemFactory.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedResponseEligibilityPolicyIntegrationTest.java`

**Interfaces:**
- Consumes: locked Need, merchant/location authorization, CategoryHierarchyRepository, LocationVariant/catalog, coverage, inventory, NeedPolicyProvider.
- Produces: single reusable validation path for create/edit and defense against guessed Need IDs.

- [ ] **Step 1: Write negative eligibility tests**

```java
@Test void rejectsResponseWhenNeedNotActive() { }
@Test void rejectsResponseAfterExpiresAtEvenIfSchedulerHasNotRun() { }
@Test void rejectsLocationOutsideMerchant() { }
@Test void rejectsStaffWithoutLocationScope() { }
@Test void rejectsLocationOutsideNeedRadius() { }
@Test void rejectsWrongCatalogType() { }
@Test void rejectsUnrelatedCategory() { }
@Test void rejectsOfferFromDifferentLocation() { }
@Test void rejectsUnavailableOffer() { }
@Test void rejectsTrackedOfferWithNoAvailableStock() { }
@Test void rejectsServiceWithoutServiceCoverage() { }
@Test void rejectsTooManyResponseItemsFromPolicy() { }
```

- [ ] **Step 2: Define validation result**

```java
public record EligibleNeedResponseItem(
        UUID catalogItemId,
        UUID variantId,
        UUID locationVariantId,
        long currentPrice,
        String currency,
        JsonNode availabilitySnapshot) {}
```

No client-supplied merchant/category/price becomes authoritative.

- [ ] **Step 3: Lock/validate Need lifecycle**

The caller must already hold Need row lock.

```text
requestStatus must be ACTIVE
expiresAt must be > now
```

If `expiresAt <= now`, return `NEED_NOT_ACTIVE`; the expiration scheduler in Task 15 persists `EXPIRED`. Do not mutate the Need to a different state from response validation code.

- [ ] **Step 4: Validate merchant/location scope**

Call the existing Plan 03 location-scoped method directly:

```java
MerchantAccess access = merchantAccessPolicy.requireLocation(
        merchantId,
        merchantLocationId,
        MerchantCapability.RESPOND_TO_NEEDS);
```

Resource ownership or STAFF location-scope mismatch is `404`, not a cross-merchant information leak.

- [ ] **Step 5: Validate every `LocationVariant`**

For each unique ID:

```text
belongs to requested MerchantLocation
CatalogItem belongs to merchant
CatalogItem ACTIVE and moderation visible
Variant active
LocationVariant available
CatalogItem.itemType == Need.needType
CatalogItem.primaryCategory is Need category or descendant
```

For TRACKED Product require current available quantity > 0.

Reject duplicate LocationVariant IDs in one request.

- [ ] **Step 6: Validate serviceability**

```text
distance from MerchantLocation to Need point <= Need.searchRadiusMeters

PRODUCT:
  pickup enabled OR PRODUCT_DELIVERY coverage includes Need point

SERVICE:
  SERVICE_AREA coverage includes Need point
```

Do not claim a response is deliverable merely because the seller is geographically close.

- [ ] **Step 7: Enforce policy max**

```java
NeedPolicySnapshot policy =
        needPolicyProvider.resolve(
            merchantId,
            need.categoryId(),
            need.locationAreaId());

if (items.size() > policy.maxResponseItems()) {
    throw NEED_RESPONSE_ITEM_LIMIT_EXCEEDED;
}
```

- [ ] **Step 8: Snapshot response item audit values**

`NeedResponseItemFactory` stores:

```text
current price
currency
current availability/inventory mode summary
catalogItemId/variantId/locationVariantId
```

Checkout never trusts this price snapshot.

- [ ] **Step 9: Add errors**

```text
NEED_NOT_ACTIVE
NEED_RESPONSE_ITEM_LIMIT_EXCEEDED
NEED_RESPONSE_ITEM_INVALID
NEED_RESPONSE_LOCATION_NOT_ELIGIBLE
NEED_RESPONSE_ALREADY_ACTIVE
```

Use 409 for lifecycle/duplicate-active conflicts and 422 for structurally valid but invalid response contents.

- [ ] **Step 10: Run tests and commit**

```bash
./mvnw -Dtest=NeedResponseEligibilityPolicyIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs \
        apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java \
        apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): validate merchant need responses"
```

---

# Task 8: Implement Idempotent Need Response Creation

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/CreateNeedResponseRequest.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/NeedResponseResponse.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/NeedResponseItemResponse.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseService.java`
- Create: `apps/api/src/main/java/vn/covie/needs/api/NeedResponseController.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedResponseCreateIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/api/NeedResponseCreateControllerIntegrationTest.java`

**Interfaces:**
- Consumes: NeedResponseEligibilityPolicy, Need repositories, IdempotencyExecutor, Outbox.
- Produces: `POST /api/v1/need-requests/{needId}/responses`.

- [ ] **Step 1: Write response creation tests**

```java
@Test void createsOneActiveResponseForConcreteLocation() { }
@Test void derivesCatalogVariantAndPriceSnapshotFromLocationVariant() { }
@Test void sameIdempotencyKeyDoesNotDuplicateResponseOrItems() { }
@Test void concurrentDifferentKeysForSameNeedAndLocationProduceOneActiveResponse() { }
@Test void sameMerchantMayRespondFromTwoEligibleLocations() { }
```

- [ ] **Step 2: Define request**

```java
public record CreateNeedResponseRequest(
        @NotNull UUID merchantLocationId,
        @NotEmpty List<@NotNull UUID> locationVariantIds,
        @Size(max = 2000) String note) {}
```

`merchantId` comes from the authenticated merchant membership resolved from the selected location; do not accept a price.

The canonical endpoint remains:

```http
POST /api/v1/need-requests/{needRequestId}/responses
Idempotency-Key: ...
```

The controller/service always derives `merchantId` from the selected `MerchantLocation`; the request never carries `merchantId`. Authorize that exact merchant/location before any response row is inserted.

- [ ] **Step 3: Serialize creation using Need row lock**

Inside IdempotencyExecutor:

```text
lock NeedRequest
→ derive MerchantLocation + merchant
→ require RESPOND_TO_NEEDS/location scope
→ assert no ACTIVE response for Need+Location
→ run eligibility policy
→ create NeedResponse ACTIVE version=0
→ create ACTIVE response items
→ append NEED_RESPONSE_CREATED outbox
→ flush
→ return response with ETag/version
```

The partial unique index is the final concurrency guard.

Map duplicate index violation to:

```text
409 NEED_RESPONSE_ALREADY_ACTIVE
```

- [ ] **Step 4: Response shape**

Return:

```json
{
  "id": "0198...",
  "needRequestId": "0198...",
  "merchantId": "0198...",
  "merchantLocationId": "0198...",
  "status": "ACTIVE",
  "note": "Có thể giao tối nay.",
  "version": 0,
  "items": [
    {
      "id": "0198...",
      "locationVariantId": "0198...",
      "catalogItemId": "0198...",
      "variantId": "0198...",
      "priceAtResponse": {
        "amount": 18000,
        "currency": "VND"
      }
    }
  ]
}
```

Set:

```http
ETag: "v0"
```

- [ ] **Step 5: Write outbox**

```text
eventType = NEED_RESPONSE_CREATED
aggregateType = NEED_REQUEST
aggregateId = needRequestId
payload = needRequestId, responseId, merchantId, merchantLocationId
```

Plan 07 later turns this into buyer notification/SSE.

- [ ] **Step 6: Run tests and commit**

```bash
./mvnw -Dtest=NeedResponseCreateIntegrationTest,NeedResponseCreateControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs \
        apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): add merchant need responses"
```

---

# Task 9: Implement History-Safe Need Response Replacement with `If-Match`

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/api/dto/ReplaceNeedResponseRequest.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseService.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/api/NeedResponseController.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedResponseReplaceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/api/NeedResponseReplaceControllerIntegrationTest.java`

**Interfaces:**
- Consumes: VersionPrecondition, locked Need/Response, eligibility policy.
- Produces: full replacement edit while preserving source item history.

- [ ] **Step 1: Write replacement/history tests**

```java
@Test void replacesActiveItemsByRetiringOldRows() { }
@Test void oldItemIdRemainsReadableAfterReplacement() { }
@Test void orderSourceFkToRetiredItemRemainsValid() { }
@Test void staleIfMatchReturnsResourceVersionConflict() { }
@Test void cannotEditWithdrawnResponse() { }
@Test void cannotEditWhenNeedNoLongerActive() { }
```

- [ ] **Step 2: Endpoint contract**

```http
PUT /api/v1/need-requests/{needRequestId}/responses/{responseId}
If-Match: "v3"
```

Request:

```java
public record ReplaceNeedResponseRequest(
        @NotEmpty List<@NotNull UUID> locationVariantIds,
        @Size(max = 2000) String note) {}
```

This is full replacement of current response contents, not PATCH.

- [ ] **Step 3: Lock in standard order**

```text
lock NeedRequest
→ lock NeedResponse by needId/responseId
→ authorize creator's merchant/location scope
→ compare If-Match with response.version
→ validate Need ACTIVE/not expired
→ validate replacement items
```

Do not query/lock response before Need row.

- [ ] **Step 4: Retire old items instead of deleting**

```java
Instant now = clock.now();

for (NeedResponseItemEntity item : activeItems) {
    item.retire(now);
}

for (EligibleNeedResponseItem eligible : validatedItems) {
    responseItemRepository.save(
        NeedResponseItemEntity.active(
            ids.generate(),
            response.id(),
            eligible,
            now));
}
```

The response ID remains the same. New response items receive new UUIDv7 IDs.

- [ ] **Step 5: Update response metadata/version**

```text
response.note = request.note
response.updatedAt = now
JPA @Version increments
flush before formatting ETag/response
```

Append:

```text
NEED_RESPONSE_UPDATED
```

outbox with responseId and new version only; do not dump private Need snapshot.

- [ ] **Step 6: Return current active item set**

Old retired items are omitted from normal buyer/seller response DTOs.

- [ ] **Step 7: Run tests and commit**

```bash
./mvnw -Dtest=NeedResponseReplaceIntegrationTest,NeedResponseReplaceControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): replace response items without deleting history"
```

---

# Task 10: Implement Need Response Withdrawal

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseService.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/api/NeedResponseController.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedResponseWithdrawIntegrationTest.java`

**Interfaces:**
- Consumes: Need/Response locking, IdempotencyExecutor, authorization/outbox.
- Produces: `ACTIVE -> WITHDRAWN` without invalidating existing Orders.

- [ ] **Step 1: Write tests**

```java
@Test void withdrawsActiveResponse() { }
@Test void withdrawRetryWithSameIdempotencyKeyIsStable() { }
@Test void withdrawnResponseCannotBeUsedForNewCheckout() { }
@Test void existingOrderFromResponseRemainsValidAfterWithdraw() { }
@Test void newResponseMayBeCreatedLaterForSameLocationWhileNeedStillActive() { }
@Test void cannotWithdrawAfterNeedIsFulfilledOrExpired() { }
```

- [ ] **Step 2: Endpoint**

```http
POST /api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw
Idempotency-Key: ...
```

No request body is required.

- [ ] **Step 3: Transaction**

```text
IdempotencyExecutor
→ lock Need
→ lock Response
→ authorize merchant/location
→ require Need ACTIVE and expiresAt > now
→ require response ACTIVE
→ set WITHDRAWN + withdrawnAt/updatedAt
→ append NEED_RESPONSE_WITHDRAWN outbox
→ commit
```

Do not retire/delete response items solely because response is withdrawn. Their history remains intact.

- [ ] **Step 4: State conflict**

Visible response in wrong state:

```text
409 NEED_RESPONSE_STATE_CONFLICT
```

Unauthorized cross-merchant visibility remains 404.

- [ ] **Step 5: Run tests and commit**

```bash
./mvnw -Dtest=NeedResponseWithdrawIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): withdraw merchant responses"
```

---

# Task 11: Implement Buyer Read Model for Need Responses

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseReadService.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/api/MeNeedController.java`
- Test: `apps/api/src/test/java/vn/covie/needs/api/BuyerNeedResponsesControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Need owner authorization, response/item tables, live LocationVariant price/availability, merchant/location public metadata.
- Produces: `GET /api/v1/me/need-requests/{needId}/responses`.

- [ ] **Step 1: Write read-model tests**

```java
@Test void ownerSeesActiveResponsesOnly() { }
@Test void ownerSeesOnlyCurrentActiveResponseItems() { }
@Test void responsePriceUsesCurrentLocationVariantPriceForDisplay() { }
@Test void responseShowsUnavailableWhenOfferPausedOrStockGone() { }
@Test void retiredItemsAreNotReturnedInCurrentResponseList() { }
@Test void anotherBuyerGets404() { }
```

- [ ] **Step 2: Build current read model, not historical price as checkout authority**

Response item contains:

```text
needResponseItemId
catalogItemId
variantId
locationVariantId
item/variant display snapshots or current safe metadata
current price
currency
available
respondedAt
```

`price_snapshot` remains audit/history only.

- [ ] **Step 3: Availability computation**

```text
CatalogItem active/visible
Variant active
LocationVariant available
Product UNTRACKED -> available
Product TRACKED -> availableQuantity > 0
Service -> no inventory
Response ACTIVE
ResponseItem ACTIVE
```

An unavailable response item stays visible so buyer understands a seller responded, but its checkout CTA is disabled.

Also return/derive an `actionable` flag only when:

```text
parent Need effective status == ACTIVE
Need expiresAt > now
response ACTIVE
response item ACTIVE
current offer available
```

A FULFILLED/EXPIRED/CANCELLED Need may still display historical responses, but all checkout CTAs are disabled.

- [ ] **Step 4: Merchant/location response data**

Return:

```text
merchant public id/name
MerchantLocation id/name
distance from Need
response note
response createdAt
response version
```

Plan 07 later enriches with rating/reputation without changing the base response identity.

- [ ] **Step 5: Cursor pagination**

Order active responses by:

```text
created_at DESC, id DESC
```

Reuse `NeedTimestampCursor` + `NeedTimestampCursorCodec`; do not introduce a second timestamp/UUID cursor encoding.

- [ ] **Step 6: Run tests and commit**

```bash
./mvnw -Dtest=BuyerNeedResponsesControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): expose buyer response read model"
```

---

# Task 12: Extend Checkout Engine with `NEED_REQUEST` Candidate Resolution

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedSourceContext.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedCheckoutCandidateResolver.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/model/CheckoutSourceType.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutCandidateResolver.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/api/dto/CheckoutPreviewRequest.java`
- Modify: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutCandidateResolverIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedCheckoutCandidateResolverIntegrationTest.java`

**Interfaces:**
- Consumes: Plan 04 resolver SPI and Need source chain.
- Produces: Preview-compatible `CheckoutCandidate` for `NEED_REQUEST` and commit source-lock hook.

- [ ] **Step 1: Extend enum only additively**

```java
public enum CheckoutSourceType {
    CART,
    DIRECT,
    NEED_REQUEST
}
```

Do not create a separate Need checkout endpoint.

- [ ] **Step 2: Add commit-lock hook to the existing resolver SPI**

Plan 04 resolvers continue to work through a default method:

```java
public interface CheckoutCandidateResolver {

    CheckoutSourceType supports();

    CheckoutCandidate resolve(
            UUID buyerUserId,
            CheckoutPreviewRequest request);

    default void lockSourceForCommit(
            UUID buyerUserId,
            CheckoutPreviewRequest request) {
        // CART keeps its existing explicit cart lock in CheckoutCommitService.
        // DIRECT has no source row.
    }
}
```

`NeedCheckoutCandidateResolver` overrides it.

- [ ] **Step 3: Define typed Need source context**

```java
public record NeedSourceContext(
        UUID needRequestId,
        UUID needResponseId,
        UUID needResponseItemId,
        long responseVersion) {}
```

Serialize this to the existing `CheckoutCandidate.sourceContext` JSON. Do not accept `needResponseId` from the client if it can be derived from the item.

- [ ] **Step 4: Extend Preview request source**

Contract:

```json
{
  "source": {
    "type": "NEED_REQUEST",
    "needRequestId": "0198...",
    "needResponseItemId": "0198..."
  },
  "items": [
    {
      "locationVariantId": "0198...",
      "quantity": 2
    }
  ],
  "fulfillmentMethod": "SELLER_DELIVERY",
  "addressId": "0198...",
  "paymentMethod": "COD"
}
```

Need-source request rules:

```text
exactly one items[] entry
locationVariantId must equal current NeedResponseItem.locationVariantId
Product quantity >= 1
Service quantity == 1
Need belongs to current buyer
Need ACTIVE and expiresAt > now
Response ACTIVE
ResponseItem ACTIVE
response belongs to Need
response MerchantLocation owns the offer
Need type == CatalogItem type
```

- [ ] **Step 5: Preview resolution must not lock**

For Preview:

```text
read source chain
validate current state
build one CheckoutCandidate
sourceCartVersion = null
sourceContext = NeedSourceContext
```

No `FOR UPDATE`, no stock reservation, no price locking.

- [ ] **Step 6: Commit lock hook**

For Commit:

```text
lock NeedRequest
→ lock NeedResponse
→ read/validate NeedResponseItem ACTIVE
```

Use exactly the same domain predicates again after locking.

This serializes against buyer cancel, seller edit/withdraw and Need expiry.

- [ ] **Step 7: Do not allow arbitrary items from same response**

Because Orders hold one `source_need_response_item_id`, a Need checkout cannot bundle several response items under one source ID in V1.

If buyer wants another response item, it is another Direct Checkout/Order.

- [ ] **Step 8: Write source-chain tests**

```java
@Test void resolvesValidNeedResponseItem() { }
@Test void rejectsNeedOwnedByAnotherBuyer() { }
@Test void rejectsWithdrawnResponse() { }
@Test void rejectsRetiredResponseItem() { }
@Test void rejectsDifferentLocationVariantThanSourceItem() { }
@Test void rejectsMultipleItems() { }
@Test void rejectsServiceQuantityGreaterThanOne() { }
```

- [ ] **Step 9: Run tests and commit**

```bash
./mvnw -Dtest=NeedCheckoutCandidateResolverIntegrationTest,CheckoutCandidateResolverIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs \
        apps/api/src/main/java/vn/covie/checkout \
        apps/api/src/test/java/vn/covie/needs \
        apps/api/src/test/java/vn/covie/checkout
git commit -m "feat(checkout): resolve need request sources"
```

---

# Task 13: Extend Checkout Commit Revalidation and Order Attribution

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutCommitService.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutComparisonService.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutChangeType.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/RevalidatedCheckout.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderSourceAttribution.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCreationService.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/OrderEntity.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedCheckoutCommitIntegrationTest.java`

**Interfaces:**
- Consumes: locked Need source, existing Checkout Preview/Commit, OrderCreationService and inventory reservation.
- Produces: correctly attributed Need-origin Order with no Cart side effects.

- [ ] **Step 1: Write Commit tests before implementation**

```java
@Test void commitCreatesNeedAttributedPendingOrder() { }
@Test void commitDoesNotClearOrCreateCartForNeedSource() { }
@Test void pendingNeedOrderDoesNotFulfillNeed() { }
@Test void responseWithdrawAfterPreviewCausesCheckoutChanged() { }
@Test void responseEditAfterPreviewCausesCheckoutChanged() { }
@Test void needCancelAfterPreviewCausesCheckoutChanged() { }
@Test void priceChangeAfterPreviewCausesCheckoutChanged() { }
@Test void sameCommitIdempotencyKeyCreatesOneOrder() { }
```

- [ ] **Step 2: Extend comparison change types**

Add:

```java
NEED_SOURCE_CHANGED,
NEED_SOURCE_UNAVAILABLE
```

These appear inside existing:

```text
409 CHECKOUT_CHANGED
```

rather than inventing a second checkout conflict envelope.

- [ ] **Step 3: Ensure source lock occurs before inventory lock**

Commit order:

```text
lock checkout_session
→ reconstruct Preview request snapshot
→ resolver.lockSourceForCommit(...)
→ resolver.resolve(...) against locked source
→ revalidate price/coverage/shipping
→ compare with preview
→ lock tracked inventory balances
→ create Order
→ reserve inventory
→ consume checkout_session
→ commit
```

Need source rows must remain locked through Order creation.

- [ ] **Step 4: Carry typed attribution into `RevalidatedCheckout`**

Add a field/value that Order creation can consume without trusting client JSON:

```java
public record OrderSourceAttribution(
        OrderSourceType sourceType,
        UUID needRequestId,
        UUID needResponseItemId) {

    public static OrderSourceAttribution marketplace() {
        return new OrderSourceAttribution(
                OrderSourceType.MARKETPLACE,
                null,
                null);
    }

    public static OrderSourceAttribution need(
            UUID needRequestId,
            UUID needResponseItemId) {
        return new OrderSourceAttribution(
                OrderSourceType.NEED_REQUEST,
                Objects.requireNonNull(needRequestId),
                Objects.requireNonNull(needResponseItemId));
    }
}
```

`CART` and `DIRECT` map to `MARKETPLACE`.

`NEED_REQUEST` maps to `NEED_REQUEST` with both IDs.

- [ ] **Step 5: Update `OrderCreationService.createPending`**

Inside the existing mandatory outer transaction:

```text
source MARKETPLACE -> both Need IDs null
source NEED_REQUEST -> set both source Need IDs
```

Never read a NeedResponseItem from inside OrderCreationService to infer source. The Need resolver already produced validated attribution in the same Commit transaction.

- [ ] **Step 6: Ensure Need pending Order leaves parent Need untouched**

Immediately after successful commit:

```text
Order.status = PENDING_CONFIRMATION
Need.status = ACTIVE
Need.fulfilled_order_id = null
```

There is no Need mutation in checkout.

- [ ] **Step 7: Preserve existing inventory semantics**

Tracked Product reserves exactly as Plan 04. Service has no reservation. Need source does not create a new inventory code path.

- [ ] **Step 8: Run tests**

```bash
./mvnw -Dtest=NeedCheckoutCommitIntegrationTest,CheckoutCommitServiceIntegrationTest test
```

Expected: all Plan 04 checkout regressions remain green.

- [ ] **Step 9: Commit**

```bash
git add apps/api/src/main/java/vn/covie/checkout \
        apps/api/src/main/java/vn/covie/orders \
        apps/api/src/test/java/vn/covie/needs
git commit -m "feat(checkout): attribute need sourced orders"
```

---

# Task 14: Fulfill Need Atomically When Source Order Is Confirmed

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderConfirmedParticipant.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedFulfillmentOnOrderConfirm.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/model/NeedRequestEntity.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedFulfillmentOnOrderConfirmIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderConfirmNeedIntegrationTest.java`

**Interfaces:**
- Consumes: Plan 05 locked Order confirmation transaction and Need source attribution.
- Produces: synchronous `ACTIVE -> FULFILLED` side effect and first-confirmed Order link.

- [ ] **Step 1: Write fulfillment tests**

```java
@Test void marketplaceOrderConfirmDoesNotTouchNeeds() { }
@Test void needOrderPendingDoesNotFulfillNeed() { }
@Test void confirmingNeedOrderFulfillsActiveNeedInSameTransaction() { }
@Test void rejectedOrExpiredOrderNeverFulfillsNeed() { }
@Test void cancelledNeedIsNotResurrectedByLaterOrderConfirm() { }
@Test void expiredNeedIsNotResurrectedByLaterOrderConfirm() { }
@Test void firstOfTwoConfirmedSourcedOrdersOwnsFulfilledOrderId() { }
@Test void secondConfirmedSourcedOrderRemainsValidWithoutDuplicateNeedEvent() { }
@Test void rollbackOfOrderConfirmAlsoRollsBackNeedFulfillment() { }
```

- [ ] **Step 2: Add a narrow Order confirmation participant port**

Under `orders.application`:

```java
public interface OrderConfirmedParticipant {
    void onConfirmed(OrderEntity order, UUID confirmingActorUserId);
}
```

The interface is synchronous and transaction-bound. It must not perform network I/O.

Inject:

```java
List<OrderConfirmedParticipant> confirmedParticipants
```

into the Plan 05 confirm service.

- [ ] **Step 3: Invoke participants only after Order transition validation**

Order confirm sequence becomes:

```text
lock Order
→ authorize seller/provider
→ validate PENDING_CONFIRMATION
→ set CONFIRMED / confirmedAt
→ invoke participants
→ append SELLER_CONFIRMED OrderEvent
→ append ORDER_CONFIRMED outbox
→ flush
→ return OrderCommandResult
```

If a participant throws, the entire confirmation transaction rolls back.

- [ ] **Step 4: Implement Need participant**

```java
@Component
public final class NeedFulfillmentOnOrderConfirm
        implements OrderConfirmedParticipant {

    private final NeedRequestRepository needs;
    private final NeedResponseRepository responses;
    private final NeedResponseItemRepository responseItems;
    private final AppClock clock;
    private final OutboxEventWriter outbox;

    public NeedFulfillmentOnOrderConfirm(
            NeedRequestRepository needs,
            NeedResponseRepository responses,
            NeedResponseItemRepository responseItems,
            AppClock clock,
            OutboxEventWriter outbox) {
        this.needs = needs;
        this.responses = responses;
        this.responseItems = responseItems;
        this.clock = clock;
        this.outbox = outbox;
    }

    @Override
    public void onConfirmed(OrderEntity order, UUID actorUserId) {
        if (order.sourceType() != OrderSourceType.NEED_REQUEST) {
            return;
        }

        NeedRequestEntity need = needs.lockById(order.sourceNeedRequestId())
                .orElseThrow(() -> new IllegalStateException(
                        "NEED_REQUEST Order points to a missing NeedRequest: "
                                + order.sourceNeedRequestId()));

        NeedResponseItemEntity sourceItem = responseItems
                .findById(order.sourceNeedResponseItemId())
                .orElseThrow(() -> new IllegalStateException(
                        "NEED_REQUEST Order points to a missing response item: "
                                + order.sourceNeedResponseItemId()));
        NeedResponseEntity sourceResponse = responses
                .findById(sourceItem.needResponseId())
                .orElseThrow(() -> new IllegalStateException(
                        "NeedResponseItem points to a missing response: "
                                + sourceItem.needResponseId()));
        if (!sourceResponse.needRequestId().equals(need.id())) {
            throw new IllegalStateException(
                    "Order Need source chain does not resolve to the same NeedRequest");
        }

        Instant now = clock.now();

        if (need.requestStatus() == NeedStatus.ACTIVE
                && !need.expiresAt().isAfter(now)) {
            need.expire(now);
            outbox.append(new OutboxEventIntent(
                    "NEED_EXPIRED",
                    "NEED_REQUEST",
                    need.id(),
                    Map.of(
                            "needRequestId", need.id(),
                            "buyerUserId", need.buyerUserId())));
            return;
        }

        if (need.requestStatus() == NeedStatus.ACTIVE) {
            need.fulfill(order.id(), now);
            outbox.append(new OutboxEventIntent(
                    "NEED_FULFILLED",
                    "NEED_REQUEST",
                    need.id(),
                    Map.of(
                            "needRequestId", need.id(),
                            "fulfilledOrderId", order.id(),
                            "buyerUserId", need.buyerUserId())));
            return;
        }

        // FULFILLED/CANCELLED/EXPIRED are intentionally not resurrected.
    }
}
```

Do not overwrite an existing `fulfilled_order_id`.

- [ ] **Step 5: Define Need entity transitions**

```java
public void fulfill(UUID orderId, Instant now) {
    requireStatus(NeedStatus.ACTIVE);
    requestStatus = NeedStatus.FULFILLED;
    fulfilledOrderId = orderId;
    updatedAt = now;
    closedAt = now;
}

public void expire(Instant now) {
    requireStatus(NeedStatus.ACTIVE);
    requestStatus = NeedStatus.EXPIRED;
    updatedAt = now;
    closedAt = now;
}
```

- [ ] **Step 6: Outbox semantics**

On first fulfillment:

```text
NEED_FULFILLED
payload:
  needRequestId
  fulfilledOrderId
  buyerUserId
```

Do not emit `NEED_FULFILLED` for a later second confirmation when Need is already FULFILLED.

- [ ] **Step 7: Test the source integrity defense**

The participant code in Step 4 resolves:

```text
Order.source_need_response_item_id
→ NeedResponseItem (ACTIVE or RETIRED)
→ NeedResponse
→ NeedRequest
```

and requires that final Need ID equal `Order.source_need_request_id`. A RETIRED item is valid historical attribution and must not block confirmation. A missing/mismatched chain is an internal invariant failure and rolls back the Order confirmation transaction; it is not exposed as a buyer-editable business conflict. Add tests for both RETIRED-valid and mismatched-invalid chains.

- [ ] **Step 8: Run tests and commit**

```bash
./mvnw -Dtest=NeedFulfillmentOnOrderConfirmIntegrationTest,OrderConfirmNeedIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders \
        apps/api/src/main/java/vn/covie/needs \
        apps/api/src/test/java/vn/covie/needs \
        apps/api/src/test/java/vn/covie/orders
git commit -m "feat(needs): fulfill need on order confirmation"
```

---

# Task 15: Implement Need Expiration Scheduler with `SKIP LOCKED`

**Files:**
- Create: `apps/api/src/main/java/vn/covie/needs/application/NeedScheduledTransitionService.java`
- Create: `apps/api/src/main/java/vn/covie/needs/scheduling/NeedExpirationScheduler.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/persistence/NeedRequestRepository.java`
- Test: `apps/api/src/test/java/vn/covie/needs/scheduling/NeedExpirationSchedulerIntegrationTest.java`

**Interfaces:**
- Consumes: ACTIVE Need rows, AppClock, outbox.
- Produces: multi-replica-safe `ACTIVE -> EXPIRED` scheduled transition.

- [ ] **Step 1: Write scheduler tests**

```java
@Test void expiresActiveNeedPastDeadline() { }
@Test void doesNotExpireFulfilledOrCancelledNeed() { }
@Test void twoWorkersCannotApplyExpirationTwice() { }
@Test void expirationWritesOneOutboxIntent() { }
@Test void orderCreatedEarlierRemainsValidAfterNeedExpires() { }
```

- [ ] **Step 2: Add claim query**

Use one-row or small-batch `FOR UPDATE SKIP LOCKED`:

```sql
select *
from need_requests
where request_status = 'ACTIVE'
  and expires_at <= :now
order by expires_at asc, id asc
for update skip locked
limit :limit;
```

The processor revalidates state under lock before transition.

- [ ] **Step 3: Separate trigger from transaction processor**

Scheduler:

```java
@Scheduled(fixedDelayString = "${covie.jobs.need-expiration-delay:PT5S}")
public void tick() {
    scheduledTransitionService.expireDueBatch(50);
}
```

Each claimed Need transition must commit in a bounded transaction. Do not keep one transaction open across an unbounded result set.

- [ ] **Step 4: Transition**

```text
ACTIVE and expiresAt <= now
→ EXPIRED
→ closedAt = now
→ updatedAt = now
→ NEED_EXPIRED outbox
```

Responses stay in history.

- [ ] **Step 5: Concurrency with seller response/checkout/order confirm**

All paths lock NeedRequest first, so:

```text
response create vs expiry -> serialized
response edit vs expiry -> serialized
buyer cancel vs expiry -> serialized
Need checkout Commit vs expiry -> serialized
Order confirmation participant vs expiry -> serialized
```

No JVM-wide lock is needed.

- [ ] **Step 6: Run tests and commit**

```bash
./mvnw -Dtest=NeedExpirationSchedulerIntegrationTest test
git add apps/api/src/main/java/vn/covie/needs apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): expire stale needs safely"
```

---

# Task 16: Add Need Error Contract, Authorization Matrix and Structured Audit Metadata

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/GlobalExceptionHandler.java` only if a new detail mapping is required
- Test: `apps/api/src/test/java/vn/covie/needs/api/NeedAuthorizationMatrixIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/api/NeedErrorContractIntegrationTest.java`

**Interfaces:**
- Consumes: Plan 01 standard error envelope and Plan 02/03 authorization semantics.
- Produces: stable machine-readable Need error surface and 404/403 behavior.

- [ ] **Step 1: Lock error codes**

Plan 06 public error codes:

```text
NEED_CATEGORY_TYPE_MISMATCH
NEED_EXPIRY_INVALID
NEED_LOCATION_INVALID
NEED_STATE_CONFLICT
NEED_NOT_ACTIVE
NEED_RESPONSE_ALREADY_ACTIVE
NEED_RESPONSE_STATE_CONFLICT
NEED_RESPONSE_ITEM_LIMIT_EXCEEDED
NEED_RESPONSE_ITEM_INVALID
NEED_RESPONSE_LOCATION_NOT_ELIGIBLE
NEED_SOURCE_INVALID
```

Checkout source changes continue to surface as `CHECKOUT_CHANGED` with `NEED_SOURCE_CHANGED | NEED_SOURCE_UNAVAILABLE` detail types.

- [ ] **Step 2: HTTP mapping**

```text
404
buyer-private Need does not belong to caller
merchant/location/response resource is outside caller visibility

403
resource is visible in merchant context but actor lacks the action capability

409
Need/Response current state conflict
duplicate active response
stale If-Match
checkout source changed

422
category/type/input/response-item/eligibility validation
```

- [ ] **Step 3: Authorization matrix tests**

Cover:

```text
buyer A cannot read/cancel buyer B Need
seller cannot call /me Need owner endpoints
merchant member sees only matching seller discovery
STAFF cannot respond from unassigned location
manager/owner can respond from eligible location
cross-merchant response ID -> 404
visible response wrong state -> 409
```

- [ ] **Step 4: Structured logs**

Log:

```text
requestId
userId
merchantId when seller action
merchantLocationId
needRequestId
responseId when present
operation
result/errorCode
```

Do not log:

```text
exact buyer location snapshot
address text
free-form private notes in generic request logs
JWT/cookies
```

- [ ] **Step 5: Run tests and commit**

```bash
./mvnw -Dtest=NeedAuthorizationMatrixIntegrationTest,NeedErrorContractIntegrationTest test
git add apps/api/src/main/java/vn/covie/shared/web \
        apps/api/src/test/java/vn/covie/needs
git commit -m "feat(needs): standardize errors and authorization"
```

---

# Task 17: Publish OpenAPI and Regenerate the TypeScript Client

**Files:**
- Create: `apps/api/src/test/java/vn/covie/openapi/NeedOpenApiContractTest.java`
- Modify: generated `packages/api-client/openapi/openapi.json` via repository export command
- Generate: `packages/api-client/src/generated/schema.ts`
- Test: `packages/api-client` typecheck

**Interfaces:**
- Consumes: all Plan 06 controllers/DTOs.
- Produces: generated TypeScript contract for buyer/seller UI and Plan 09.

- [ ] **Step 1: Write OpenAPI contract test**

Assert these paths/methods exist:

```text
POST /api/v1/me/need-requests
GET  /api/v1/me/need-requests
GET  /api/v1/me/need-requests/{needRequestId}
POST /api/v1/me/need-requests/{needRequestId}/cancel
GET  /api/v1/me/need-requests/{needRequestId}/responses

GET  /api/v1/merchants/{merchantId}/need-requests

POST /api/v1/need-requests/{needRequestId}/responses
PUT  /api/v1/need-requests/{needRequestId}/responses/{responseId}
POST /api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw

POST /api/v1/checkouts/preview       # source enum includes NEED_REQUEST
POST /api/v1/checkouts/commit
```

Assert `Idempotency-Key` / `If-Match` headers are documented where required.

- [ ] **Step 2: Verify schemas**

OpenAPI must expose:

```text
NeedType = PRODUCT | SERVICE
NeedStatus = ACTIVE | FULFILLED | EXPIRED | CANCELLED
NeedResponseStatus = ACTIVE | WITHDRAWN
CheckoutSourceType includes NEED_REQUEST
```

Seller discovery DTO must not contain raw `geoPoint`, `latitude`, `longitude`, `addressId` or internal `locationSnapshot`.

- [ ] **Step 3: Export and regenerate**

Use the repository command established in Plan 01:

```bash
cd apps/api
./mvnw -Dtest=OpenApiExportTest -Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json test
cd ../..
pnpm api-client:generate
pnpm api-client:check
```

Expected: exit 0.

- [ ] **Step 4: Run drift check**

```bash
./infra/scripts/verify-generated-client.sh
```

Expected: no generated drift after committed generation.

- [ ] **Step 5: Commit**

```bash
git add apps/api/src/test/java/vn/covie/openapi \
        packages/api-client/openapi/openapi.json \
        packages/api-client/src/generated/schema.ts
git commit -m "feat(contract): publish need matching api"
```

---

# Task 18: Build Minimal Buyer Need Web Slice

**Files:**
- Create: `apps/web/features/needs/need-api.ts`
- Create: `apps/web/features/needs/need-create-form.tsx`
- Create: `apps/web/features/needs/need-list.tsx`
- Create: `apps/web/features/needs/need-detail.tsx`
- Create: `apps/web/features/needs/need-response-list.tsx`
- Create: `apps/web/app/(buyer)/needs/page.tsx`
- Create: `apps/web/app/(buyer)/needs/new/page.tsx`
- Create: `apps/web/app/(buyer)/needs/[needId]/page.tsx`
- Create: `apps/web/tests/needs/buyer-needs.test.tsx`

**Interfaces:**
- Consumes: generated API client, Plan 02 auth/CSRF, Plan 03 location input, Plan 04 checkout UI/API.
- Produces: minimal buyer journey Create Need → view responses → Direct Checkout.

- [ ] **Step 1: Write frontend branching tests**

```text
create form requires type/category/location/radius/expiry
API 422 field errors render on form
Need ACTIVE shows cancel action
FULFILLED/EXPIRED/CANCELLED hide cancel action
response item unavailable disables checkout CTA
available response item opens existing checkout flow with source NEED_REQUEST
409 CHECKOUT_CHANGED asks user to refresh Preview
```

- [ ] **Step 2: Use generated client types only**

`need-api.ts` may wrap generated calls for ergonomics, but do not manually duplicate DTO enums.

Example wrapper intent:

```ts
export async function createNeed(input: CreateNeedInput) {
  return api.POST("/api/v1/me/need-requests", {
    headers: {
      "Idempotency-Key": crypto.randomUUID(),
      "X-CSRF-Token": await getCsrfToken(),
    },
    body: input,
  });
}
```

Use the existing API wrapper conventions from Plans 01–04 rather than creating a second fetch stack.

- [ ] **Step 3: Create form**

Fields:

```text
PRODUCT | SERVICE
category
title
query text
description optional
requested quantity where applicable
saved address or coordinates/location picker
radius
expiry
```

Do not add target price if it is not part of the approved V1 API.

- [ ] **Step 4: Need detail responses**

For each response:

```text
merchant
location
distance
note
current offered item(s)
current price
availability
[Đặt món này] / [Đặt dịch vụ này]
```

CTA constructs:

```ts
{
  source: {
    type: "NEED_REQUEST",
    needRequestId,
    needResponseItemId
  },
  items: [{
    locationVariantId,
    quantity
  }],
  fulfillmentMethod,
  addressId,
  paymentMethod
}
```

Do not add it to Cart first.

- [ ] **Step 5: Run frontend tests**

```bash
pnpm --filter web test -- buyer-needs
pnpm --filter web typecheck
pnpm --filter web lint
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/web/features/needs apps/web/app/'(buyer)'/needs apps/web/tests/needs
git commit -m "feat(web): add buyer need flow"
```

---

# Task 19: Build Minimal Seller Need Discovery and Response Web Slice

**Files:**
- Create: `apps/web/features/needs/merchant-need-list.tsx`
- Create: `apps/web/features/needs/merchant-need-response-form.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/needs/page.tsx`
- Create: `apps/web/tests/needs/seller-needs.test.tsx`

**Interfaces:**
- Consumes: generated seller Need endpoints and existing Seller Center/merchant/location context.
- Produces: seller discovery and response management vertical slice.

- [ ] **Step 1: Write UI tests**

Cover:

```text
seller chooses/filters MerchantLocation
matching Need rows show safe area + distance
exact buyer address/coordinates never rendered
seller chooses only existing LocationVariants
form displays `responseConstraints.maxItems` from the selected Need match
successful response appears ACTIVE
edit sends If-Match
409 version conflict triggers refetch
withdraw removes buyer-actionable response
```

- [ ] **Step 2: Discovery UI**

Page:

```text
/seller/{merchantId}/needs
```

Controls:

```text
location filter
type
category
distance/relevance sort if exposed
cursor "load more"
```

Do not calculate matching locally.

- [ ] **Step 3: Response form**

Catalog picker is constrained to the resolved MerchantLocation and current Need type/category.

Client may prefilter for UX, but backend validation remains authoritative.

No arbitrary price field.

- [ ] **Step 4: Edit/withdraw**

Edit sends:

```http
If-Match: "v{response.version}"
```

Withdraw uses fresh Idempotency-Key.

Unknown/changed response state triggers REST refetch.

- [ ] **Step 5: Run tests**

```bash
pnpm --filter web test -- seller-needs
pnpm --filter web typecheck
pnpm --filter web lint
pnpm --filter web build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/web/features/needs \
        apps/web/app/'(seller)'/seller/'[merchantId]'/needs \
        apps/web/tests/needs
git commit -m "feat(web): add seller need matching flow"
```

---

# Task 20: Run Plan 06 Concurrency, Query-Plan and Acceptance Gate

**Files:**
- Create: `apps/api/src/test/java/vn/covie/needs/application/NeedConcurrencyIntegrationTest.java`
- Create: `apps/api/src/test/java/vn/covie/needs/application/NeedEndToEndIntegrationTest.java`
- Create: `apps/api/src/test/java/vn/covie/needs/persistence/NeedQueryPlanIntegrationTest.java`
- Modify: `README.md` only if local Need flow needs documented operator/developer instructions

**Interfaces:**
- Consumes: all Plan 06 work.
- Produces: fresh evidence that Need matching/source attribution is safe and Plan 07 may start at V012.

- [ ] **Step 1: Add concurrent response uniqueness test**

Two actors/requests with valid permission race to create an ACTIVE response for the same:

```text
NeedRequest + MerchantLocation
```

Expected:

```text
exactly one ACTIVE response
other command receives conflict
no duplicate active response items
DB unique partial index remains valid
```

- [ ] **Step 2: Add cancel/response race**

Run buyer cancel and seller create-response concurrently.

Expected one serialized outcome:

```text
response commits first → buyer cancel may then CANCEL Need; response remains history
cancel commits first   → response creation fails NEED_NOT_ACTIVE
```

Never end with a response created after observing CANCELLED Need under lock.

- [ ] **Step 3: Add edit/checkout race**

Start Need Preview from active response item, then race:

```text
seller replaces response
vs
buyer Checkout Commit
```

Expected:

```text
Commit locks source first → Order may succeed against old current item, edit waits
Edit locks source first   → old item RETIRED, Commit returns CHECKOUT_CHANGED
```

No Order points to a non-existent response item.

- [ ] **Step 4: Add expiry/confirm race**

Race:

```text
Need expiration
vs
seller confirming already-created sourced Order
```

Expected serialization under Need row lock:

```text
confirm obtains Need first before expiry deadline/state transition
→ FULFILLED

expiry wins / logical expiresAt already passed
→ Need EXPIRED
→ Order may still CONFIRM
→ Need is not resurrected
```

The exact result is determined under lock and `expiresAt`, not scheduler timing luck.

- [ ] **Step 5: Add two sourced Orders confirmation test**

Buyer explicitly creates two PENDING Orders from the same Need before fulfillment.

Then confirm concurrently.

Expected:

```text
both Orders may reach CONFIRMED
Need transitions ACTIVE -> FULFILLED exactly once
fulfilled_order_id points to first serialized confirming Order
exactly one NEED_FULFILLED outbox event
```

- [ ] **Step 6: Add end-to-end business journey**

```text
Buyer creates PRODUCT Need
→ seller discovery finds it
→ seller responds from eligible location
→ buyer sees response
→ buyer Preview NEED_REQUEST
→ buyer Commit
→ Order PENDING_CONFIRMATION
→ Need still ACTIVE
→ seller confirms Order
→ Order CONFIRMED
→ Need FULFILLED
→ source_need_request_id/source_need_response_item_id correct
```

Also run rejection path:

```text
Need -> Order PENDING -> seller reject
→ Order REJECTED_BY_SELLER
→ Need remains ACTIVE if not expired/cancelled
```

- [ ] **Step 7: Query/index review**

Use realistic fixtures and:

```sql
explain (analyze, buffers)
```

for:

```text
buyer Need list
active Need expiry scan
seller merchant/location Need discovery
Need geo radius filter
Need category/type filter
Catalog full-text/trigram relevance against Need query text
active response lookup
buyer response list
```

Confirm hot paths use:

```text
ix_need_requests_buyer_created
ix_need_requests_status_expiry
ix_need_requests_category_type_state
ix_need_requests_geo
ux_need_response_active_location
ix_need_responses_need_state
ix_need_response_items_response_state
```

Do not force an index hint; adjust SQL/indexes only when measured plan shows a bad path.

- [ ] **Step 8: Run focused backend suite**

```bash
cd apps/api
./mvnw -Dtest='*Need*Test,Plan06SchemaMigrationTest,MerchantNeed*Test,BuyerNeed*Test,OrderConfirmNeedIntegrationTest' test
```

Expected: 0 failures/errors. This class-name pattern is the Plan 06 focused suite; do not replace it with an H2-only or mocked persistence suite.

- [ ] **Step 9: Run relevant regression suite**

```bash
./mvnw -Dtest='*Checkout*Test,*Order*Test,*Need*Test' test
```

Expected: Plan 04 Checkout and Plan 05 Order behavior stays green.

- [ ] **Step 10: Run complete backend verification**

```bash
./mvnw verify
```

Expected: exit 0.

- [ ] **Step 11: Run frontend and generated-contract verification**

```bash
cd ../..
pnpm --filter @covie/api-client check
pnpm --filter web test
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web build
./infra/scripts/verify-generated-client.sh
```

Expected: all exit 0 and no generated drift.

- [ ] **Step 12: Inspect DB invariants**

```sql
select need_request_id, merchant_location_id, count(*)
from need_responses
where response_status = 'ACTIVE'
group by need_request_id, merchant_location_id
having count(*) > 1;
```

Expected: no rows.

```sql
select nri.id
from need_response_items nri
join orders o on o.source_need_response_item_id = nri.id
where nri.item_status = 'RETIRED'
  and o.source_type = 'NEED_REQUEST';
```

Rows are allowed and prove retained historical attribution.

```sql
select id
from orders
where source_type = 'NEED_REQUEST'
  and (source_need_request_id is null or source_need_response_item_id is null);
```

Expected: no rows.

```sql
select n.id
from need_requests n
where n.request_status = 'FULFILLED'
  and n.fulfilled_order_id is null;
```

Expected: no rows.

- [ ] **Step 13: Verify release blockers**

Checklist:

```text
[ ] Need category is required and compatible with type
[ ] buyer-private Need cannot be read by another buyer
[ ] seller discovery never leaks exact buyer address/coordinates
[ ] matching is type/category/location/coverage/availability aware
[ ] one active response per Need+MerchantLocation
[ ] same MerchantProfile may respond from multiple locations
[ ] response item always derives from current LocationVariant
[ ] response max item setting enforced
[ ] response edit retires old items; never deletes source history
[ ] response withdraw keeps existing Order attribution valid
[ ] NEED_REQUEST Preview uses same Checkout engine
[ ] Need checkout contains exactly one response item source
[ ] Preview does not reserve/lock
[ ] Commit locks and revalidates source chain
[ ] PENDING source Order leaves Need ACTIVE
[ ] seller/provider confirmation is the only fulfillment trigger
[ ] rejection/expiry/cancel-before-confirm never fulfills Need
[ ] expired/cancelled Need is never resurrected
[ ] first of multiple confirmed source Orders owns fulfilled_order_id
[ ] Need expiration is SKIP LOCKED/multi-replica safe
[ ] all meaningful Need mutations write outbox atomically
[ ] OpenAPI/generated client current
[ ] Plan 07 migration starts at V012
```

- [ ] **Step 14: Commit acceptance-only documentation if changed**

```bash
git add README.md
git commit -m "docs(needs): document need matching verification"
```

Only run the commit if README actually changed; never create an empty commit.

---

# Endpoint Catalog Added by Plan 06

Buyer:

```http
POST /api/v1/me/need-requests
GET  /api/v1/me/need-requests
GET  /api/v1/me/need-requests/{needRequestId}
POST /api/v1/me/need-requests/{needRequestId}/cancel
GET  /api/v1/me/need-requests/{needRequestId}/responses
```

Seller:

```http
GET  /api/v1/merchants/{merchantId}/need-requests

POST /api/v1/need-requests/{needRequestId}/responses
PUT  /api/v1/need-requests/{needRequestId}/responses/{responseId}
POST /api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw
```

Checkout reused:

```http
POST /api/v1/checkouts/preview
POST /api/v1/checkouts/commit
```

No separate:

```text
/need-checkouts
/need-orders
/need-cart
```

is introduced.

---

# Need Lifecycle Matrix

| Current | Actor/action | Result | Outbox | Order effect |
|---|---|---|---|---|
| new | buyer create | ACTIVE | `NEED_CREATED` | none |
| ACTIVE | buyer cancel | CANCELLED | `NEED_CANCELLED` | existing Orders unchanged |
| ACTIVE | system expires | EXPIRED | `NEED_EXPIRED` | existing Orders unchanged |
| ACTIVE | source Order confirm | FULFILLED | `NEED_FULFILLED` | confirming Order remains CONFIRMED |
| FULFILLED | later source Order confirm | unchanged | none | later Order may remain CONFIRMED |
| EXPIRED | earlier source Order confirm | unchanged | none | Order may remain CONFIRMED |
| CANCELLED | earlier source Order confirm | unchanged | none | Order may remain CONFIRMED |

Need is not a transaction arbiter after an Order has already been created.

---

# Response Lifecycle Matrix

| Current | Operation | Result | Response items |
|---|---|---|---|
| none | create | ACTIVE | new ACTIVE rows |
| ACTIVE | replace | ACTIVE, version++ | old RETIRED + new ACTIVE |
| ACTIVE | withdraw | WITHDRAWN | retained |
| WITHDRAWN | edit | conflict | unchanged |
| WITHDRAWN | create new response for same Need+Location while Need ACTIVE | new ACTIVE response row | new ACTIVE rows |

The partial unique index only limits **ACTIVE** responses.

---

# Checkout Source Integrity Contract

For a Need Preview/Commit, backend independently proves:

```text
current buyer owns NeedRequest
NeedRequest ACTIVE
NeedRequest expiresAt > now
NeedResponse belongs to NeedRequest
NeedResponse ACTIVE
NeedResponseItem belongs to NeedResponse
NeedResponseItem ACTIVE
NeedResponse MerchantLocation owns LocationVariant
request LocationVariant == response item LocationVariant
CatalogItem type == Need type
CatalogItem category matches Need category tree
LocationVariant currently available
tracked stock currently sufficient at Commit
fulfillment coverage/pickup currently valid
current price/shipping/minimum order valid
```

Only then can Commit create:

```text
Order.source_type = NEED_REQUEST
Order.source_need_request_id = NeedRequest.id
Order.source_need_response_item_id = NeedResponseItem.id
```

Client cannot create Need attribution by supplying arbitrary UUIDs.

---

# Matching Query Design

V1 matching has two layers.

Database candidate narrowing:

```text
Need ACTIVE + expiresAt > now
Need type
category subtree
Need radius / PostGIS distance
MerchantLocation active + merchant-scoped
CatalogItem active/moderation-visible
Variant active
LocationVariant available
Product tracked inventory > 0 where applicable
text relevance (PostgreSQL FTS/trigram)
```

Application final eligibility:

```text
Merchant membership/location scope
PRODUCT pickup or PRODUCT_DELIVERY coverage
SERVICE SERVICE_AREA coverage
response policy max
current domain invariants
```

`NeedMatchingService` uses the bounded over-fetch/keyset algorithm from Task 6 so application-side coverage filtering cannot corrupt cursor order or skip candidates. This avoids an N×market global scan while retaining domain-service reuse for coverage rules.

---

# Privacy Contract

Buyer owner routes may read their own location snapshot.

Seller discovery/response routes must not expose:

```text
buyer addressId
exact street/unit text
raw latitude/longitude
PostGIS geometry/geography representation
buyer phone
buyer contact methods
```

Seller sees enough to decide whether to respond:

```text
safe area label/path
distance from selected MerchantLocation
Need radius
Need content/category/type
expiry
```

Exact fulfillment address/contact enters the Order snapshot only through Checkout/Order rules.

---

# Idempotency Operation Namespace

Use stable operation strings:

```text
need.create
need.cancel:<needRequestId>
need.response.create:<needRequestId>:<merchantLocationId>
need.response.withdraw:<needRequestId>:<responseId>
```

`PUT` response replacement uses `If-Match` and a full deterministic replacement contract rather than `Idempotency-Key`.

Checkout Commit keeps the existing Plan 04 checkout operation namespace.

Same actor + operation + key + same body returns the stored result. Same actor + operation + key + different body conflicts.

---

# Outbox Events Produced by Plan 06

```text
NEED_CREATED
NEED_CANCELLED
NEED_RESPONSE_CREATED
NEED_RESPONSE_UPDATED
NEED_RESPONSE_WITHDRAWN
NEED_FULFILLED
NEED_EXPIRED
```

Plan 06 only persists outbox intents.

Plan 07 owns:

```text
outbox claiming/retry
notification row creation
SSE signal delivery
analytics/reputation consumers where applicable
```

Consumers must be idempotent.

---

# Error Contract Added by Plan 06

```text
NEED_CATEGORY_TYPE_MISMATCH
NEED_EXPIRY_INVALID
NEED_LOCATION_INVALID
NEED_STATE_CONFLICT
NEED_NOT_ACTIVE
NEED_RESPONSE_ALREADY_ACTIVE
NEED_RESPONSE_STATE_CONFLICT
NEED_RESPONSE_ITEM_LIMIT_EXCEEDED
NEED_RESPONSE_ITEM_INVALID
NEED_RESPONSE_LOCATION_NOT_ELIGIBLE
NEED_SOURCE_INVALID
```

Existing shared errors reused:

```text
RESOURCE_NOT_FOUND
MERCHANT_ACTION_NOT_ALLOWED
RESOURCE_VERSION_CONFLICT
IDEMPOTENCY_KEY_REQUIRED
IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
CHECKOUT_CHANGED
INSUFFICIENT_INVENTORY
RATE_LIMIT_EXCEEDED
VALIDATION_FAILED
```

---

# Plan 06 Non-goals

Do not add:

```text
AI/vector semantic matching
OpenSearch/Elasticsearch
seller free-text/arbitrary-price offers
internal buyer-seller chat
automatic seller outreach
automatic acceptance of a response
multi-response-item single Need Order
multi-seller checkout
booking/calendar engine for Service
payment settlement/bank verification
Need dispute arbitration
review/reputation calculation
outbox dispatcher/notification persistence/SSE fanout
Admin Need moderation/settings UI
generic bulk Need response API
```

Review/reputation/notifications are Plan 07. Admin settings/moderation are Plan 08. UI polish/E2E completion is Plan 09.

---

# Self-review Checklist Before Declaring Plan 06 Implemented

## Spec coverage

```text
Need create / required category/type/location           -> Tasks 1, 3, 4
Buyer list/detail/cancel                                -> Task 5
Need location privacy                                   -> Tasks 3, 6, 16
Seller discovery                                        -> Task 6
PostGIS/category/text/availability matching             -> Task 6
MerchantLocation-scoped response                        -> Tasks 7, 8
One active response per location                        -> Tasks 1, 8, 20
Max response items setting                              -> Tasks 3, 7
Existing LocationVariant response items                 -> Tasks 7, 8
Response replace / If-Match                             -> Task 9
Response history preservation                           -> Tasks 1, 9
Response withdraw                                       -> Task 10
Buyer response read model/current price                 -> Task 11
NEED_REQUEST Checkout source                            -> Task 12
Preview source-chain validation                         -> Task 12
Commit source lock/revalidation                         -> Task 13
Order source attribution/FKs                            -> Tasks 1, 13
Need stays ACTIVE while Order pending                   -> Task 13
Need FULFILLED only on Order CONFIRMED                  -> Task 14
No resurrection after expiry/cancel                     -> Tasks 14, 15
Need expiration                                         -> Task 15
Authorization/error contract                            -> Task 16
OpenAPI/client                                          -> Task 17
Buyer minimal web                                       -> Task 18
Seller minimal web                                      -> Task 19
Concurrency/end-to-end/query plan                       -> Task 20
```

## Type consistency

Use exactly:

```text
NeedType
NeedStatus
NeedResponseStatus
NeedResponseItemStatus
NeedPolicyProvider
NeedPolicySnapshot
NeedRequestEntity
NeedResponseEntity
NeedResponseItemEntity
NeedResponseEligibilityPolicy
NeedCheckoutCandidateResolver
NeedSourceContext
OrderSourceAttribution
NeedFulfillmentOnOrderConfirm
NeedScheduledTransitionService
CheckoutSourceType.NEED_REQUEST
OrderSourceType.NEED_REQUEST
```

Do not create a parallel `Demand*` type tree in Plan 06.

## Migration handoff

Plan 06 ends at:

```text
V011__need_matching.sql
```

Plan 07 starts at:

```text
V012
```

Plan 07 must not renumber or edit V011.

---

# Implementation and Handoff

Implement Tasks 1–20 in dependency order on an isolated feature branch. Keep each change reviewable, verify the focused tests for the affected task, then run the Task 20 acceptance suite before integration.

Plan 06 is complete only when Task 20 acceptance evidence is green. Plan 07 begins from migration `V012` and leaves `V011__need_matching.sql` unchanged.
