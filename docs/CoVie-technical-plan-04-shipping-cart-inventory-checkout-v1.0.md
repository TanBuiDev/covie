# CoVie Plan 04 — Shipping, Cart, Inventory & Checkout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Use `superpowers:using-git-worktrees` before implementation. Every task ends with a fresh verification gate and a reviewable commit.

**Project:** CoVie  
**Plan:** 04 / 10  
**Target repo path:** `docs/superpowers/plans/2026-08-08-covie-checkout-inventory.md`  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Ready for implementation review  

**Goal:** Build the complete pre-fulfillment transaction slice: MerchantLocation delivery coverage and shipping policies, tracked/untracked inventory, multi-cart behavior, unified CART/DIRECT checkout Preview/Commit, short-lived server-side checkout sessions, atomic Order creation, and PostgreSQL row-locking that prevents overselling.

**Architecture:** Plan 04 adds `delivery`, `shipping`, `inventory`, `cart`, `checkout`, and the creation-only foundation of `orders`. PostgreSQL remains authoritative for coverage, shipping, cart state, stock, checkout sessions and Order creation. Preview is read-heavy and never reserves stock; Commit uses one short `READ COMMITTED` transaction that locks the checkout session and tracked inventory rows in deterministic order, revalidates all authoritative values, creates the Order and immutable snapshots, reserves stock, consumes the checkout session, clears selected Cart items and appends outbox intent atomically.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1.0, Spring Data JPA 4.1, PostgreSQL 18 + PostGIS 3.6, Flyway, Jackson JSONB, MockMvc + Spring Security Test, Testcontainers/PostGIS, Next.js 16.2, React 19, TypeScript strict, Vitest, generated OpenAPI client.

---

# Global Constraints

- Plan 01–03 acceptance gates must be green before implementation starts.
- `/api/v1` remains the only public API base path.
- Default DB isolation remains PostgreSQL `READ COMMITTED`; do not raise global isolation to `SERIALIZABLE`.
- Critical stock consistency uses explicit row locks, not cache locks and not JVM mutexes.
- `LocationVariant` from Plan 03 remains the only purchasable offer identity and authoritative price source.
- `PRODUCT` LocationVariant has `inventoryMode = TRACKED | UNTRACKED`; `SERVICE` has `inventoryMode = null`.
- `TRACKED` stock has a materialized balance plus append-only movements and per-OrderItem reservations.
- `UNTRACKED` Product never creates a stock reservation and cannot use stock adjustment APIs.
- Service has no inventory and checkout quantity is always exactly `1`.
- A Cart belongs to exactly one `(user, MerchantLocation, cartType)` and never mixes Product/Service or locations.
- Add-to-cart receives `locationVariantId`; backend never silently switches to another location.
- Cart snapshots are convenience/display metadata only. They do not lock price.
- Checkout Preview does not lock price, reserve stock, or guarantee future availability.
- Checkout Commit always resolves and revalidates current Catalog/Variant/LocationVariant/location/coverage/shipping/inventory state.
- Preview and Commit use one checkout engine with source abstractions. Plan 04 implements `CART` and `DIRECT`; Plan 06 adds `NEED_REQUEST` as an additive resolver without replacing the engine.
- `checkoutToken` is opaque, random, short-lived, bound to one user and one preview, and single-use.
- Raw checkout tokens are never stored; only SHA-256 token fingerprints are persisted.
- Plan 04 locks checkout-session TTL to **15 minutes**. This is an application property, not a magic constant in services.
- Payment V1 in this plan supports `COD` and `BANK_TRANSFER_NEGOTIATED` only. No payment verification, escrow, refund or bank-account storage.
- Product fulfillment methods in Plan 04: `PICKUP` and `SELLER_DELIVERY`.
- Service checkout in Plan 04 uses `fulfillmentMethod = SERVICE`, requires a buyer service address, validates `SERVICE_AREA` coverage, and never evaluates shipping. No booking slots/calendar engine.
- Product seller delivery requires `sellerDeliveryEnabled`, PRODUCT_DELIVERY coverage and shipping evaluation.
- Product pickup requires `pickupEnabled`; shipping fee is zero and shipping policy is not evaluated.
- Service coverage uses `SERVICE_AREA`; shipping policy never applies to Service.
- `minimumDeliveryOrder` is validated only for Product `SELLER_DELIVERY` and is denominated in the MerchantLocation currency.
- Shipping modes are `FREE`, `FIXED`, `NEGOTIATED`, `CONDITIONAL`.
- `NEGOTIATED` means shipping fee is `null`, total is `null`, knownTotal equals subtotal. Never coerce unknown shipping to zero.
- Conditional shipping V1 supports `ORDER_AMOUNT_MIN` and `DISTANCE_MAX`, action `FREE_SHIPPING`, and `ANY | ALL` match mode.
- Coverage can enable radius, selected areas, or both; eligibility is OR across enabled modes.
- Selected-area matching includes descendants in the location-area tree.
- Coverage and shipping seller resources use `@Version`, ETag and required `If-Match` for edit/delete/replace commands.
- Seller operational permission remains DB-resolved using `MerchantAccessPolicy`; STAFF must have location access.
- Inventory writes require merchant/location permission and idempotency for adjustment commands.
- Absolute `set-stock` never directly overwrites without a ledger entry; it computes the delta and appends `ADJUSTMENT_IN` or `ADJUSTMENT_OUT` in the same transaction.
- `stockOnHand >= reservedQuantity >= 0` must hold after every transaction.
- Bulk stock set is per-item independent. One failed item must not roll back successful siblings.
- Checkout Commit is idempotent. Same actor + operation + key + body returns the stored result; a reused key with a different body conflicts.
- Commit does not trust subtotal, shippingFee, total, merchantId, locationId, item name or price supplied by the client.
- Order creation snapshots immutable names, variant data, unit prices, buyer address/contact, MerchantLocation pickup data, fulfillment and shipping policy result.
- `COMPLETED`, cancellation/rejection/issue transitions are Plan 05 concerns. Plan 04 only creates `PENDING_CONFIRMATION` Orders and initial `ORDER_CREATED` event.
- Initial tracked inventory reservations are created at Order creation, before seller confirmation.
- Initial Order creation writes outbox intent in the same transaction; Plan 07 still owns outbox dispatch/notification/SSE consumption.
- JPA entities are never serialized directly.
- Money is integer minor units + ISO currency; V1 MerchantLocation currency remains VND unless existing Plan 03 data explicitly allows another supported code.
- No external network call is allowed inside the checkout Commit transaction.
- Redis is non-authoritative and is not required for checkout/inventory correctness.
- No production credentials or personally sensitive buyer data are written to generic request logs.

---

# Migration Ordering Locked by Plan 04

Plan 03 ended at:

```text
V001__extensions_and_foundation.sql
V002__identity_users_and_audit.sql
V003__auth_sessions_and_tickets.sql
V004__location_merchant_media.sql
V005__catalog_and_search.sql
V006__idempotency_and_outbox.sql
```

Plan 04 continues sequentially:

```text
V007__delivery_shipping_inventory.sql
V008__carts_and_checkout_sessions.sql
V009__order_creation_foundation.sql
```

Plan 05 starts at **V010**.

`V009` intentionally creates Order creation structures now because Plan 04 acceptance requires a real `PENDING_CONFIRMATION` Order. It includes:

```text
orders
order_items
product_fulfillments
service_fulfillments
order_events
```

Plan 05 adds cancellation/issues and implements lifecycle commands. Plan 06 may add Need-source foreign-key constraints after Need tables exist.

Flyway rules:

```text
outOfOrder=false
never edit applied V001-V009
all tests start from an empty PostgreSQL/PostGIS database when validating migration order
```

---

# Plan 04 Domain Boundary Map

```text
Catalog / MerchantLocation (Plan 03)
          |
          +----------------------+--------------------+
          |                      |                    |
          v                      v                    v
      Delivery               Inventory              Cart
      Coverage                Balance               Active cart
          |                    Ledger                Items
          v                      |                    |
      Shipping                   +---------+----------+
      Policy                               |
          |                                v
          +--------------------------> Checkout
                                      Preview
                                         |
                                  checkout_session
                                         |
                                      Commit
                                         |
                 +-----------------------+-----------------------+
                 |                       |                       |
                 v                       v                       v
             Order foundation      Reservations/ledger       Outbox
                 |
                 v
          Plan 05 lifecycle
```

---

# Repository Map Added by Plan 04

```text
apps/api/src/main/java/vn/covie/
├── delivery/
│   ├── api/
│   │   ├── MerchantDeliveryCoverageController.java
│   │   └── dto/
│   ├── application/
│   │   ├── DeliveryCoverageService.java
│   │   ├── DeliveryCoverageEvaluator.java
│   │   ├── CoverageEvaluation.java
│   │   └── CoverageMatchSource.java
│   ├── model/
│   │   ├── DeliveryCoverageEntity.java
│   │   ├── DeliveryCoverageAreaEntity.java
│   │   ├── DeliveryCoverageAreaId.java
│   │   ├── CoverageType.java
│   │   └── CoverageStatus.java
│   └── persistence/
│       ├── DeliveryCoverageRepository.java
│       └── DeliveryCoverageAreaRepository.java
├── shipping/
│   ├── api/
│   │   ├── MerchantShippingPolicyController.java
│   │   └── dto/
│   ├── application/
│   │   ├── ShippingPolicyService.java
│   │   ├── ShippingEvaluator.java
│   │   ├── ShippingEvaluation.java
│   │   └── ShippingPolicySnapshotFactory.java
│   ├── model/
│   │   ├── ShippingPolicyEntity.java
│   │   ├── ShippingRuleEntity.java
│   │   ├── ShippingMode.java
│   │   ├── ShippingRuleMatchMode.java
│   │   ├── ShippingConditionType.java
│   │   ├── ShippingConditionOperator.java
│   │   ├── ShippingActionType.java
│   │   └── ShippingPolicyStatus.java
│   └── persistence/
│       ├── ShippingPolicyRepository.java
│       └── ShippingRuleRepository.java
├── inventory/
│   ├── api/
│   │   ├── MerchantInventoryController.java
│   │   └── dto/
│   ├── application/
│   │   ├── InventoryQueryService.java
│   │   ├── InventoryAdjustmentService.java
│   │   ├── InventoryReservationService.java
│   │   ├── InventoryModeCoordinator.java
│   │   ├── InventoryLockService.java
│   │   └── InventoryAvailability.java
│   ├── model/
│   │   ├── InventoryBalanceEntity.java
│   │   ├── InventoryReservationEntity.java
│   │   ├── InventoryMovementEntity.java
│   │   ├── InventoryReservationStatus.java
│   │   └── InventoryMovementType.java
│   └── persistence/
│       ├── InventoryBalanceRepository.java
│       ├── InventoryReservationRepository.java
│       └── InventoryMovementRepository.java
├── cart/
│   ├── api/
│   │   ├── MeCartController.java
│   │   └── dto/
│   ├── application/
│   │   ├── CartService.java
│   │   ├── CartReadService.java
│   │   ├── CartPricingViewService.java
│   │   └── CartItemSnapshotFactory.java
│   ├── model/
│   │   ├── CartEntity.java
│   │   ├── CartItemEntity.java
│   │   ├── CartType.java
│   │   └── CartStatus.java
│   └── persistence/
│       ├── CartRepository.java
│       └── CartItemRepository.java
├── checkout/
│   ├── api/
│   │   ├── CheckoutController.java
│   │   └── dto/
│   ├── application/
│   │   ├── CheckoutPreviewService.java
│   │   ├── CheckoutCommitService.java
│   │   ├── CheckoutCandidate.java
│   │   ├── CheckoutCandidateResolver.java
│   │   ├── CheckoutCandidateResolverRegistry.java
│   │   ├── CartCheckoutCandidateResolver.java
│   │   ├── DirectCheckoutCandidateResolver.java
│   │   ├── CheckoutValidator.java
│   │   ├── CheckoutPricingService.java
│   │   ├── CheckoutComparisonService.java
│   │   ├── CheckoutSessionService.java
│   │   ├── CheckoutTokenGenerator.java
│   │   └── CheckoutTokenHasher.java
│   ├── model/
│   │   ├── CheckoutSessionEntity.java
│   │   ├── CheckoutSourceType.java
│   │   └── FulfillmentMethod.java
│   └── persistence/
│       └── CheckoutSessionRepository.java
├── orders/
│   ├── application/
│   │   ├── OrderCreationService.java
│   │   ├── OrderSnapshotFactory.java
│   │   └── OrderCodeGenerator.java
│   ├── model/
│   │   ├── OrderEntity.java
│   │   ├── OrderItemEntity.java
│   │   ├── ProductFulfillmentEntity.java
│   │   ├── ServiceFulfillmentEntity.java
│   │   ├── OrderEventEntity.java
│   │   ├── OrderStatus.java
│   │   ├── OrderType.java
│   │   ├── OrderSourceType.java
│   │   ├── OrderShippingFeeType.java
│   │   ├── ProductFulfillmentMethod.java
│   │   └── OrderEventType.java
│   └── persistence/
│       ├── OrderRepository.java
│       ├── OrderItemRepository.java
│       ├── ProductFulfillmentRepository.java
│       ├── ServiceFulfillmentRepository.java
│       └── OrderEventRepository.java
├── shared/
│   └── commerce/
│       └── PaymentMethod.java
└── catalog/
    ├── application/
    │   ├── CatalogCreateService.java       # modified to call InventoryModeCoordinator
    │   └── CatalogEditService.java         # modified to coordinate TRACKED/UNTRACKED transition
    └── persistence/
        └── LocationVariantRepository.java  # extended read/lock queries
```

Frontend additions:

```text
apps/web/
├── app/(buyer)/cart/page.tsx
├── app/(buyer)/checkout/page.tsx
├── app/(buyer)/checkout/success/[orderId]/page.tsx
├── app/(seller)/seller/[merchantId]/inventory/page.tsx
├── app/(seller)/seller/[merchantId]/locations/[locationId]/delivery/page.tsx
├── features/cart/
│   ├── cart-api.ts
│   ├── cart-store.ts
│   ├── cart-list.tsx
│   └── cart-item-row.tsx
├── features/checkout/
│   ├── checkout-api.ts
│   ├── checkout-state.ts
│   ├── checkout-preview.tsx
│   └── checkout-change-dialog.tsx
├── features/inventory/
│   ├── inventory-api.ts
│   └── inventory-table.tsx
└── features/shipping/
    ├── shipping-api.ts
    ├── coverage-form.tsx
    └── shipping-policy-form.tsx
```

---

# Public Interfaces Produced by Plan 04

Later plans may rely on these exact backend types:

```text
vn.covie.delivery.application.DeliveryCoverageEvaluator
vn.covie.delivery.application.CoverageEvaluation
vn.covie.shipping.application.ShippingEvaluator
vn.covie.shipping.application.ShippingEvaluation
vn.covie.inventory.application.InventoryReservationService
vn.covie.inventory.application.InventoryLockService
vn.covie.inventory.application.InventoryAvailability
vn.covie.cart.application.CartService
vn.covie.checkout.application.CheckoutCandidate
vn.covie.checkout.application.CheckoutCandidateResolver
vn.covie.checkout.application.CheckoutPreviewService
vn.covie.checkout.application.CheckoutCommitService
vn.covie.orders.application.OrderCreationService
vn.covie.orders.model.OrderEntity
vn.covie.orders.persistence.OrderRepository
```

Stable API surface introduced by this plan:

```text
GET   /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages
POST  /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages
PATCH /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId}
DELETE /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId}

GET   /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies
POST  /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies
PATCH /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies/{policyId}
DELETE /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies/{policyId}

GET  /api/v1/merchants/{merchantId}/inventory
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/set-stock
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-in
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-out
POST /api/v1/merchants/{merchantId}/inventory/bulk-set-stock

GET    /api/v1/me/carts
GET    /api/v1/me/carts/{cartId}
POST   /api/v1/me/carts/items
PATCH  /api/v1/me/carts/{cartId}/items/{cartItemId}
DELETE /api/v1/me/carts/{cartId}/items/{cartItemId}

POST /api/v1/checkouts/preview
POST /api/v1/checkouts/commit
```

Plan 04 does **not** publish canonical Order detail or Order lifecycle command endpoints; Plan 05 owns those.

---

# Transaction and Lock Order Contract

To avoid lock-order drift across implementations, use this exact order for Checkout Commit:

```text
1. Idempotency advisory xact lock (IdempotencyExecutor outer transaction)
2. checkout_sessions row FOR UPDATE
3. source Cart row FOR UPDATE if source=CART, identified from server-owned session source_context
4. tracked inventory_balances rows FOR UPDATE ordered by location_variant_id ASC
5. create Order/OrderItems/fulfillment/events/reservations/movements/outbox
6. mark checkout_session consumed
7. remove selected Cart items / close empty cart
8. commit
```

Inventory adjustment command lock order:

```text
1. Idempotency advisory xact lock
2. inventory_balances target row FOR UPDATE
3. append movement + mutate balance
4. commit
```

Never acquire inventory locks in request item order. Sort UUIDs first.

---

# Task 1: Add Delivery, Shipping, Inventory, Cart, Checkout and Order-Creation Migrations

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V007__delivery_shipping_inventory.sql`
- Create: `apps/api/src/main/resources/db/migration/V008__carts_and_checkout_sessions.sql`
- Create: `apps/api/src/main/resources/db/migration/V009__order_creation_foundation.sql`
- Test: `apps/api/src/test/java/vn/covie/checkout/persistence/Plan04SchemaMigrationTest.java`

**Interfaces:**
- Consumes: V001–V006 schema, `merchant_locations`, `location_areas`, `location_variants`, `users`, `idempotency_records`, `outbox_events`.
- Produces: all Plan 04 tables and constraints required by Tasks 2–18.

- [ ] **Step 1: Write migration integration test before migrations exist**

```java
@Test
void appliesPlan04MigrationsInOrderAndEnforcesCoreConstraints() {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("009");

    assertTableExists("delivery_coverages");
    assertTableExists("shipping_policies");
    assertTableExists("inventory_balances");
    assertTableExists("inventory_reservations");
    assertTableExists("inventory_movements");
    assertTableExists("carts");
    assertTableExists("cart_items");
    assertTableExists("checkout_sessions");
    assertTableExists("orders");
    assertTableExists("order_items");
    assertTableExists("product_fulfillments");
    assertTableExists("service_fulfillments");
    assertTableExists("order_events");
}
```

Add SQL assertions that invalid stock balance, duplicate active cart and duplicate active reservation fail at the DB layer.

- [ ] **Step 2: Run the migration test and verify red**

```bash
cd apps/api
./mvnw -Dtest=Plan04SchemaMigrationTest test
```

Expected: FAIL because V007–V009 do not exist.

- [ ] **Step 3: Create `V007__delivery_shipping_inventory.sql`**

Core SQL shape:

```sql
create table delivery_coverages (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    coverage_type varchar(30) not null,
    radius_enabled boolean not null default false,
    radius_meters integer null,
    area_enabled boolean not null default false,
    coverage_status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_delivery_coverage_type
        check (coverage_type in ('PRODUCT_DELIVERY','SERVICE_AREA')),
    constraint ck_delivery_coverage_status
        check (coverage_status in ('ACTIVE','INACTIVE')),
    constraint ck_delivery_coverage_modes
        check (radius_enabled or area_enabled),
    constraint ck_delivery_coverage_radius
        check ((not radius_enabled and radius_meters is null)
            or (radius_enabled and radius_meters is not null and radius_meters > 0))
);

create table delivery_coverage_areas (
    delivery_coverage_id uuid not null references delivery_coverages(id) on delete cascade,
    location_area_id uuid not null references location_areas(id),
    primary key (delivery_coverage_id, location_area_id)
);
```

Create one active coverage maximum per `(merchant_location_id, coverage_type)`:

```sql
create unique index ux_delivery_coverage_active_type
    on delivery_coverages(merchant_location_id, coverage_type)
    where coverage_status = 'ACTIVE';
```

Shipping schema:

```sql
create table shipping_policies (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    mode varchar(30) not null,
    base_fee bigint null,
    currency_code char(3) not null,
    rule_match_mode varchar(10) null,
    policy_status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_shipping_mode check (mode in ('FREE','FIXED','NEGOTIATED','CONDITIONAL')),
    constraint ck_shipping_match_mode check (rule_match_mode is null or rule_match_mode in ('ANY','ALL')),
    constraint ck_shipping_status check (policy_status in ('ACTIVE','INACTIVE')),
    constraint ck_shipping_base_fee check (base_fee is null or base_fee >= 0),
    constraint ck_shipping_mode_fields check (
        (mode = 'FREE' and base_fee is null and rule_match_mode is null)
        or (mode = 'FIXED' and base_fee is not null and rule_match_mode is null)
        or (mode = 'NEGOTIATED' and base_fee is null and rule_match_mode is null)
        or (mode = 'CONDITIONAL' and base_fee is not null and rule_match_mode is not null)
    )
);

create unique index ux_shipping_policy_active_location
    on shipping_policies(merchant_location_id)
    where policy_status = 'ACTIVE';
```

Create `shipping_rules` explicitly:

```sql
create table shipping_rules (
    id uuid primary key,
    shipping_policy_id uuid not null references shipping_policies(id) on delete cascade,
    condition_type varchar(30) not null,
    operator varchar(10) not null,
    threshold_value bigint not null,
    action_type varchar(30) not null,
    priority integer not null,
    enabled boolean not null default true,
    constraint ck_shipping_rule_condition check (condition_type in ('ORDER_AMOUNT_MIN','DISTANCE_MAX')),
    constraint ck_shipping_rule_operator check (operator in ('GTE','LTE')),
    constraint ck_shipping_rule_action check (action_type in ('FREE_SHIPPING')),
    constraint ck_shipping_rule_threshold check (threshold_value >= 0),
    constraint ck_shipping_rule_priority check (priority >= 0),
    constraint ck_shipping_rule_operator_pair check (
        (condition_type = 'ORDER_AMOUNT_MIN' and operator = 'GTE')
        or (condition_type = 'DISTANCE_MAX' and operator = 'LTE')
    ),
    unique(shipping_policy_id, priority)
);
```

Inventory schema:

```sql
create table inventory_balances (
    location_variant_id uuid primary key references location_variants(id),
    stock_on_hand bigint not null default 0,
    reserved_quantity bigint not null default 0,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_inventory_nonnegative check (stock_on_hand >= 0 and reserved_quantity >= 0),
    constraint ck_inventory_reserved_lte_stock check (reserved_quantity <= stock_on_hand)
);
```

Create reservation and movement tables explicitly. Because `orders` does not exist until V009, Order references are UUID columns here and gain foreign keys in V009:

```sql
create table inventory_reservations (
    id uuid primary key,
    location_variant_id uuid not null references location_variants(id),
    order_id uuid not null,
    order_item_id uuid not null,
    quantity bigint not null,
    reservation_status varchar(20) not null,
    expires_at timestamptz null,
    created_at timestamptz not null,
    committed_at timestamptz null,
    released_at timestamptz null,
    constraint ck_inventory_reservation_quantity check (quantity > 0),
    constraint ck_inventory_reservation_status check (reservation_status in ('ACTIVE','COMMITTED','RELEASED')),
    constraint ck_inventory_reservation_terminal_time check (
        (reservation_status = 'ACTIVE' and committed_at is null and released_at is null)
        or (reservation_status = 'COMMITTED' and committed_at is not null and released_at is null)
        or (reservation_status = 'RELEASED' and committed_at is null and released_at is not null)
    )
);

create unique index ux_inventory_reservation_active_order_item
    on inventory_reservations(order_item_id)
    where reservation_status = 'ACTIVE';

create table inventory_movements (
    id uuid primary key,
    location_variant_id uuid not null references location_variants(id),
    movement_type varchar(40) not null,
    quantity bigint not null,
    order_id uuid null,
    order_item_id uuid null,
    reservation_id uuid null references inventory_reservations(id),
    actor_user_id uuid null references users(id),
    reason_code varchar(60) null,
    metadata jsonb null,
    created_at timestamptz not null,
    constraint ck_inventory_movement_type check (movement_type in (
        'STOCK_IN','STOCK_OUT','RESERVE','RELEASE_RESERVATION','SALE','ADJUSTMENT_IN','ADJUSTMENT_OUT'
    )),
    constraint ck_inventory_movement_quantity check (quantity > 0)
);
```

Backfill zero balances for existing tracked Product offers:

```sql
insert into inventory_balances(location_variant_id, stock_on_hand, reserved_quantity, updated_at, version)
select lv.id, 0, 0, now(), 0
from location_variants lv
join variants v on v.id = lv.variant_id
join catalog_items ci on ci.id = v.catalog_item_id
where ci.item_type = 'PRODUCT'
  and lv.inventory_mode = 'TRACKED'
  and lv.deleted_at is null
on conflict (location_variant_id) do nothing;
```

- [ ] **Step 4: Create `V008__carts_and_checkout_sessions.sql`**

```sql
create table carts (
    id uuid primary key,
    user_id uuid not null references users(id),
    merchant_location_id uuid not null references merchant_locations(id),
    cart_type varchar(20) not null,
    cart_status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_cart_type check (cart_type in ('PRODUCT','SERVICE')),
    constraint ck_cart_status check (cart_status in ('ACTIVE','CHECKED_OUT','ABANDONED'))
);

create unique index ux_cart_active_user_location_type
    on carts(user_id, merchant_location_id, cart_type)
    where cart_status = 'ACTIVE';
```

`cart_items` must store `location_variant_id` directly in addition to `variant_id`; the API purchase identity is location-specific and storing only Variant would make later validation ambiguous:

```sql
create table cart_items (
    id uuid primary key,
    cart_id uuid not null references carts(id) on delete cascade,
    location_variant_id uuid not null references location_variants(id),
    variant_id uuid not null references variants(id),
    quantity bigint not null,
    price_snapshot bigint not null,
    currency_code char(3) not null,
    item_name_snapshot varchar(220) not null,
    variant_snapshot jsonb null,
    added_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_cart_item_quantity check (quantity >= 1),
    unique(cart_id, location_variant_id)
);
```

Checkout session:

```sql
create table checkout_sessions (
    id uuid primary key,
    token_hash char(64) not null unique,
    user_id uuid not null references users(id),
    source_type varchar(30) not null,
    source_context jsonb not null,
    cart_version bigint null,
    request_snapshot jsonb not null,
    preview_snapshot jsonb not null,
    expires_at timestamptz not null,
    consumed_at timestamptz null,
    created_at timestamptz not null,
    constraint ck_checkout_source_type check (source_type in ('CART','DIRECT'))
);

create index ix_checkout_sessions_user_created on checkout_sessions(user_id, created_at desc);
create index ix_checkout_sessions_expiry on checkout_sessions(expires_at) where consumed_at is null;
```

Plan 06 expands the source constraint to include `NEED_REQUEST` in its migration.

- [ ] **Step 5: Create `V009__order_creation_foundation.sql`**

Create `orders` with creation-time fields needed by API/DB design. Include nullable Need source columns without FK yet:

```sql
create table orders (
    id uuid primary key,
    order_code varchar(32) not null unique,
    order_type varchar(20) not null,
    buyer_user_id uuid not null references users(id),
    merchant_profile_id uuid not null references merchant_profiles(id),
    merchant_location_id uuid not null references merchant_locations(id),
    status varchar(60) not null,
    source_type varchar(20) not null,
    source_need_request_id uuid null,
    source_need_response_item_id uuid null,
    payment_method varchar(40) not null,
    subtotal bigint not null,
    shipping_fee_type varchar(30) null,
    shipping_fee bigint null,
    known_total bigint not null,
    total_amount bigint null,
    currency_code char(3) not null,
    confirmation_expires_at timestamptz null,
    confirmed_at timestamptz null,
    completed_at timestamptz null,
    closed_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_order_type check (order_type in ('PRODUCT','SERVICE')),
    constraint ck_order_source check (source_type in ('MARKETPLACE','NEED_REQUEST')),
    constraint ck_order_status_creation check (status in ('PENDING_CONFIRMATION')),
    constraint ck_order_payment check (payment_method in ('COD','BANK_TRANSFER_NEGOTIATED')),
    constraint ck_order_shipping_fee_type check (
        shipping_fee_type is null or shipping_fee_type in ('FREE','FIXED','CONDITIONAL','NEGOTIATED','NOT_APPLICABLE')
    ),
    constraint ck_order_money_nonnegative check (
        subtotal >= 0 and known_total >= 0
        and (shipping_fee is null or shipping_fee >= 0)
        and (total_amount is null or total_amount >= 0)
    ),
    constraint ck_order_negotiated_total check (
        shipping_fee_type <> 'NEGOTIATED'
        or (shipping_fee is null and total_amount is null and known_total = subtotal)
    ),
    constraint ck_order_service_shipping check (
        order_type <> 'SERVICE'
        or (shipping_fee_type = 'NOT_APPLICABLE' and shipping_fee is null and total_amount = subtotal and known_total = subtotal)
    )
);
```

The status check is intentionally creation-minimal in V009. Plan 05 replaces/expands it when lifecycle statuses are implemented.

Create the rest of the creation aggregate explicitly:

```sql
create table order_items (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete restrict,
    catalog_item_id uuid null references catalog_items(id),
    variant_id uuid null references variants(id),
    location_variant_id uuid null references location_variants(id),
    item_type varchar(20) not null,
    quantity bigint not null,
    unit_price bigint not null,
    line_total bigint not null,
    currency_code char(3) not null,
    item_snapshot jsonb not null,
    created_at timestamptz not null,
    constraint ck_order_item_type check (item_type in ('PRODUCT','SERVICE')),
    constraint ck_order_item_quantity check (quantity > 0),
    constraint ck_order_item_money check (unit_price >= 0 and line_total >= 0)
);

create table product_fulfillments (
    order_id uuid primary key references orders(id) on delete restrict,
    fulfillment_method varchar(30) not null,
    pickup_location_id uuid null references merchant_locations(id),
    buyer_address_id uuid null references user_addresses(id),
    distance_meters integer null,
    pickup_location_snapshot jsonb not null,
    delivery_address_snapshot jsonb null,
    buyer_contact_snapshot jsonb not null,
    merchant_contact_snapshot jsonb not null,
    shipping_policy_snapshot jsonb null,
    seller_marked_ready_at timestamptz null,
    seller_marked_delivered_at timestamptz null,
    awaiting_buyer_confirmation_at timestamptz null,
    constraint ck_product_fulfillment_method check (fulfillment_method in ('PICKUP','SELLER_DELIVERY')),
    constraint ck_product_fulfillment_payload check (
        (fulfillment_method = 'PICKUP' and buyer_address_id is null and delivery_address_snapshot is null)
        or (fulfillment_method = 'SELLER_DELIVERY' and buyer_address_id is not null and delivery_address_snapshot is not null)
    )
);

create table service_fulfillments (
    order_id uuid primary key references orders(id) on delete restrict,
    service_address_id uuid null references user_addresses(id),
    service_address_snapshot jsonb not null,
    provider_location_snapshot jsonb not null,
    buyer_contact_snapshot jsonb not null,
    merchant_contact_snapshot jsonb not null,
    provider_marked_performed_at timestamptz null,
    awaiting_buyer_confirmation_at timestamptz null,
    created_at timestamptz not null
);

create table order_events (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete restrict,
    event_type varchar(50) not null,
    actor_user_id uuid null references users(id),
    metadata jsonb null,
    created_at timestamptz not null,
    constraint ck_order_event_type_v009 check (event_type in ('ORDER_CREATED'))
);
```

Order-item `line_total` must equal `unit_price * quantity` in application code using overflow-safe arithmetic (`Math.multiplyExact`). PostgreSQL keeps the non-negative backstop; it does not attempt a cross-column overflow-prone generated expression.

After tables exist, add reservation/movement FKs:

```sql
alter table inventory_reservations
    add constraint fk_inventory_reservation_order foreign key (order_id) references orders(id),
    add constraint fk_inventory_reservation_order_item foreign key (order_item_id) references order_items(id);

alter table inventory_movements
    add constraint fk_inventory_movement_order foreign key (order_id) references orders(id),
    add constraint fk_inventory_movement_order_item foreign key (order_item_id) references order_items(id);
```

- [ ] **Step 6: Add indexes required by Plan 04 queries**

```text
delivery_coverages(merchant_location_id, coverage_type, coverage_status)
delivery_coverage_areas(location_area_id)
shipping_rules(shipping_policy_id, enabled, priority)
inventory_reservations(order_id, reservation_status)
inventory_reservations(location_variant_id, reservation_status)
inventory_movements(location_variant_id, created_at desc)
cart_items(cart_id)
orders(buyer_user_id, created_at desc)
orders(merchant_location_id, created_at desc)
order_items(order_id)
order_events(order_id, created_at, id)
```

- [ ] **Step 7: Run migration tests and verify green**

```bash
cd apps/api
./mvnw -Dtest=Plan04SchemaMigrationTest test
```

Expected: PASS with V001–V009 applied in order.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/resources/db/migration/V007__delivery_shipping_inventory.sql apps/api/src/main/resources/db/migration/V008__carts_and_checkout_sessions.sql apps/api/src/main/resources/db/migration/V009__order_creation_foundation.sql apps/api/src/test/java/vn/covie/checkout/persistence/Plan04SchemaMigrationTest.java
git commit -m "feat(checkout): add delivery inventory cart and order schema"
```

---

# Task 2: Implement Seller Delivery Coverage Management

**Files:**
- Create: `apps/api/src/main/java/vn/covie/delivery/model/DeliveryCoverageEntity.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/model/DeliveryCoverageAreaEntity.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/model/DeliveryCoverageAreaId.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/model/CoverageType.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/model/CoverageStatus.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/persistence/DeliveryCoverageRepository.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/persistence/DeliveryCoverageAreaRepository.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/application/DeliveryCoverageService.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/api/MerchantDeliveryCoverageController.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/api/dto/DeliveryCoverageUpsertRequest.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/api/dto/DeliveryCoverageResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/delivery/application/DeliveryCoverageServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/delivery/api/MerchantDeliveryCoverageControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `MerchantAccessPolicy`, `MerchantLocationRepository`, `VersionPrecondition`, location-area hierarchy.
- Produces: versioned coverage management and `DeliveryCoverageRepository` used by evaluator/checkout.

- [ ] **Step 1: Write service tests**

Required cases:

```java
@Test void ownerCreatesRadiusProductCoverage() { }
@Test void staffWithoutLocationAccessGets404() { }
@Test void selectedAreaMustExistAndBeActive() { }
@Test void atLeastOneCoverageModeMustBeEnabled() { }
@Test void serviceAreaMayExistIndependentOfProductDeliveryCoverage() { }
@Test void staleIfMatchReturnsResourceVersionConflict() { }
```

- [ ] **Step 2: Implement entities and repository**

`DeliveryCoverageEntity` uses `@Version long version`. Selected areas are separate rows; do not use `@ElementCollection` against the location-area table.

Repository methods:

```java
List<DeliveryCoverageEntity> findByMerchantLocationIdOrderByCoverageType(UUID locationId);

Optional<DeliveryCoverageEntity> findByIdAndMerchantLocationId(UUID id, UUID locationId);

Optional<DeliveryCoverageEntity> findFirstByMerchantLocationIdAndCoverageTypeAndCoverageStatus(
        UUID locationId, CoverageType type, CoverageStatus status);
```

- [ ] **Step 3: Implement create/update/delete transaction**

Create request:

```java
public record DeliveryCoverageUpsertRequest(
        CoverageType coverageType,
        boolean radiusEnabled,
        Integer radiusMeters,
        boolean areaEnabled,
        Set<UUID> locationAreaIds,
        CoverageStatus status) {}
```

Validation:

```text
radiusEnabled -> radiusMeters > 0
!radiusEnabled -> radiusMeters null
areaEnabled -> locationAreaIds non-empty
!areaEnabled -> locationAreaIds empty
location areas must exist and be ACTIVE
PRODUCT_DELIVERY location must have sellerDeliveryEnabled=true before ACTIVE
SERVICE_AREA location must have serviceEnabled=true before ACTIVE
```

Do not permit changing `coverageType` of an existing record. Require delete/create for type change.

- [ ] **Step 4: Implement controller with ETag**

```http
GET   /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages
POST  /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages
PATCH /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId}
DELETE /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId}
```

`PATCH/DELETE` require `If-Match`. Response includes `ETag: "v{version}"`.

- [ ] **Step 5: Add stable errors**

```text
DELIVERY_COVERAGE_INVALID
DELIVERY_COVERAGE_DUPLICATE_ACTIVE
DELIVERY_COVERAGE_LOCATION_NOT_ENABLED
```

- [ ] **Step 6: Run focused tests**

```bash
cd apps/api
./mvnw -Dtest=DeliveryCoverageServiceIntegrationTest,MerchantDeliveryCoverageControllerIntegrationTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/delivery apps/api/src/test/java/vn/covie/delivery apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java
git commit -m "feat(delivery): manage location coverage"
```

---

# Task 3: Implement Radius + Area Delivery Coverage Evaluation

**Files:**
- Create: `apps/api/src/main/java/vn/covie/delivery/application/DeliveryCoverageEvaluator.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/application/CoverageEvaluation.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/application/CoverageMatchSource.java`
- Create: `apps/api/src/main/java/vn/covie/delivery/persistence/JdbcCoverageEvaluationRepository.java`
- Test: `apps/api/src/test/java/vn/covie/delivery/application/DeliveryCoverageEvaluatorIntegrationTest.java`

**Interfaces:**
- Consumes: active `DeliveryCoverage`, `MerchantLocation.geoPoint`, normalized `LocationContext`.
- Produces: deterministic `CoverageEvaluation` for Checkout and discovery filters.

- [ ] **Step 1: Write geographic behavior tests**

```java
@Test void radiusMatchIncludesBoundaryPoint() { }
@Test void selectedAreaMatchesExactArea() { }
@Test void selectedAreaMatchesDescendantArea() { }
@Test void radiusOrAreaMatchIsEnoughWhenBothModesEnabled() { }
@Test void disabledCoverageReturnsNotEligible() { }
@Test void productAndServiceCoverageAreEvaluatedSeparately() { }
```

Use real PostGIS Testcontainers fixtures; do not mock distance math.

- [ ] **Step 2: Define immutable result**

```java
public record CoverageEvaluation(
        boolean eligible,
        CoverageType coverageType,
        Long distanceMeters,
        Set<CoverageMatchSource> matchedBy,
        UUID coverageId) {

    public static CoverageEvaluation notEligible(CoverageType type, Long distanceMeters) {
        return new CoverageEvaluation(false, type, distanceMeters, Set.of(), null);
    }
}
```

- [ ] **Step 3: Implement SQL for distance and descendant area**

Use PostGIS geography distance:

```sql
ST_Distance(ml.geo_point, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
```

Selected-area matching must walk the `location_areas.parent_id` chain with a recursive CTE from buyer area upward and compare ancestors against `delivery_coverage_areas.location_area_id`.

Do not load the entire area tree into Java for each request.

- [ ] **Step 4: Implement evaluator**

```java
public CoverageEvaluation evaluate(
        UUID merchantLocationId,
        CoverageType type,
        LocationContext buyerLocation) {
    // load one ACTIVE coverage, compute distance once,
    // evaluate enabled radius/area modes, return explicit match sources
}
```

If buyer context has coordinates but no resolved `locationAreaId`, area mode cannot match; radius can still match.

- [ ] **Step 5: Run tests**

```bash
cd apps/api
./mvnw -Dtest=DeliveryCoverageEvaluatorIntegrationTest test
```

Expected: PASS including descendant-area case.

- [ ] **Step 6: Commit**

```bash
git add apps/api/src/main/java/vn/covie/delivery/application apps/api/src/main/java/vn/covie/delivery/persistence/JdbcCoverageEvaluationRepository.java apps/api/src/test/java/vn/covie/delivery/application/DeliveryCoverageEvaluatorIntegrationTest.java
git commit -m "feat(delivery): evaluate radius and area coverage"
```

---

# Task 4: Implement Seller Shipping Policy Management

**Files:**
- Create: `apps/api/src/main/java/vn/covie/shipping/model/ShippingPolicyEntity.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/model/ShippingRuleEntity.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/model/ShippingMode.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/model/ShippingRuleMatchMode.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/model/ShippingConditionType.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/model/ShippingConditionOperator.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/model/ShippingActionType.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/model/ShippingPolicyStatus.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/persistence/ShippingPolicyRepository.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/persistence/ShippingRuleRepository.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/application/ShippingPolicyService.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/api/MerchantShippingPolicyController.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/api/dto/ShippingPolicyUpsertRequest.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/api/dto/ShippingRuleRequest.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/api/dto/ShippingPolicyResponse.java`
- Test: `apps/api/src/test/java/vn/covie/shipping/application/ShippingPolicyServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/shipping/api/MerchantShippingPolicyControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Merchant location authorization, `Money`, `VersionPrecondition`.
- Produces: active shipping policy/rules for Checkout evaluator.

- [ ] **Step 1: Write validation tests for all four modes**

```java
@Test void freeRejectsBaseFeeAndRules() { }
@Test void fixedRequiresNonNegativeBaseFee() { }
@Test void negotiatedHasNullFeeConfiguration() { }
@Test void conditionalRequiresBaseFeeMatchModeAndAtLeastOneEnabledRule() { }
@Test void conditionalRejectsWrongOperatorForCondition() { }
@Test void onlyOneActivePolicyPerLocation() { }
```

V1 canonical operator pairing:

```text
ORDER_AMOUNT_MIN -> GTE
DISTANCE_MAX     -> LTE
```

- [ ] **Step 2: Implement enums and JPA mappings**

```java
public enum ShippingMode { FREE, FIXED, NEGOTIATED, CONDITIONAL }
public enum ShippingRuleMatchMode { ANY, ALL }
public enum ShippingConditionType { ORDER_AMOUNT_MIN, DISTANCE_MAX }
public enum ShippingConditionOperator { GTE, LTE }
public enum ShippingActionType { FREE_SHIPPING }
public enum ShippingPolicyStatus { ACTIVE, INACTIVE }
```

- [ ] **Step 3: Implement replace-rules transaction**

A PATCH of a policy treats `rules[]` as the complete ordered rule set for CONDITIONAL. Inside one transaction:

```text
lock/load policy
require version
validate request
update policy fields
delete existing rules
insert normalized new rules by priority
commit
```

Do not partially merge rule lists by index.

- [ ] **Step 4: Implement controller and errors**

Errors:

```text
SHIPPING_POLICY_INVALID
SHIPPING_POLICY_DUPLICATE_ACTIVE
SHIPPING_RULE_INVALID
```

Require location operational access for STAFF and ETag for mutating existing policies.

- [ ] **Step 5: Run tests**

```bash
cd apps/api
./mvnw -Dtest=ShippingPolicyServiceIntegrationTest,MerchantShippingPolicyControllerIntegrationTest test
```

- [ ] **Step 6: Commit**

```bash
git add apps/api/src/main/java/vn/covie/shipping apps/api/src/test/java/vn/covie/shipping apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java
git commit -m "feat(shipping): manage location shipping policies"
```

---

# Task 5: Implement Shipping Evaluation and Immutable Snapshot

**Files:**
- Create: `apps/api/src/main/java/vn/covie/shipping/application/ShippingEvaluator.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/application/ShippingEvaluation.java`
- Create: `apps/api/src/main/java/vn/covie/shipping/application/ShippingPolicySnapshotFactory.java`
- Test: `apps/api/src/test/java/vn/covie/shipping/application/ShippingEvaluatorTest.java`
- Test: `apps/api/src/test/java/vn/covie/shipping/application/ShippingEvaluatorIntegrationTest.java`

**Interfaces:**
- Consumes: active shipping policy, Product subtotal, distance meters.
- Produces: fee/known-total semantics and JSON-safe immutable snapshot for preview/order.

- [ ] **Step 1: Write pure evaluation tests**

Required cases:

```text
FREE -> fee 0, total=subtotal
FIXED -> fee=baseFee, total=subtotal+fee
NEGOTIATED -> fee null, total null, knownTotal=subtotal
CONDITIONAL ANY one matching rule -> free
CONDITIONAL ANY no match -> baseFee
CONDITIONAL ALL all match -> free
CONDITIONAL ALL one miss -> baseFee
boundary ORDER_AMOUNT_MIN exact threshold matches
boundary DISTANCE_MAX exact threshold matches
```

- [ ] **Step 2: Define result type**

```java
public record ShippingEvaluation(
        ShippingMode mode,
        Long shippingFeeMinor,
        Long totalMinor,
        long knownTotalMinor,
        String currency,
        long distanceMeters,
        List<UUID> matchedRuleIds,
        JsonNode policySnapshot) {}
```

- [ ] **Step 3: Implement evaluator as deterministic pure logic after policy load**

Do not let `ShippingEvaluator` query Cart or Order tables. It takes normalized values and policy/rules.

- [ ] **Step 4: Build snapshot with exact evaluation inputs**

Snapshot contains:

```json
{
  "mode": "CONDITIONAL",
  "baseFee": 10000,
  "currency": "VND",
  "ruleMatchMode": "ANY",
  "rules": [],
  "subtotalUsed": 150000,
  "distanceMetersUsed": 420,
  "matchedRuleIds": [],
  "effectiveShippingFee": 0
}
```

No mutable entity is serialized into snapshot. Build a dedicated map/record then `ObjectMapper.valueToTree()`.

- [ ] **Step 5: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=ShippingEvaluatorTest,ShippingEvaluatorIntegrationTest test
git add apps/api/src/main/java/vn/covie/shipping/application apps/api/src/test/java/vn/covie/shipping/application
git commit -m "feat(shipping): evaluate fees and create snapshots"
```

---

# Task 6: Implement Inventory Persistence, Locking and TRACKED Balance Coordination

**Files:**
- Create: `apps/api/src/main/java/vn/covie/inventory/model/InventoryBalanceEntity.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/model/InventoryReservationEntity.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/model/InventoryMovementEntity.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/model/InventoryReservationStatus.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/model/InventoryMovementType.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/persistence/InventoryBalanceRepository.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/persistence/InventoryReservationRepository.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/persistence/InventoryMovementRepository.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/application/InventoryModeCoordinator.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/application/InventoryLockService.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/application/InventoryAvailability.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogCreateService.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogEditService.java`
- Test: `apps/api/src/test/java/vn/covie/inventory/application/InventoryModeCoordinatorIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/inventory/application/InventoryLockServiceIntegrationTest.java`

**Interfaces:**
- Consumes: Plan 03 LocationVariant/catalog aggregate transactions.
- Produces: exactly one balance for active TRACKED Product offer and stable multi-row lock API for checkout.

- [ ] **Step 1: Map inventory entities without entity cascades**

Balance:

```java
@Entity
@Table(name = "inventory_balances")
public class InventoryBalanceEntity {
    @Id
    private UUID locationVariantId;

    private long stockOnHand;
    private long reservedQuantity;
    private Instant updatedAt;

    @Version
    private long version;

    public long availableQuantity() {
        return stockOnHand - reservedQuantity;
    }
}
```

Movement and reservation are append-oriented. Do not expose repository delete methods for movements in normal application code.

- [ ] **Step 2: Add lock query**

Use native SQL so row order and lock semantics are explicit:

```java
@Query(value = """
    select * from inventory_balances
    where location_variant_id in (:ids)
    order by location_variant_id asc
    for update
    """, nativeQuery = true)
List<InventoryBalanceEntity> lockAllByLocationVariantIds(@Param("ids") Collection<UUID> ids);
```

`InventoryLockService.lockTrackedBalances(Set<UUID>)` sorts/deduplicates IDs before calling repository and verifies all requested tracked IDs resolved.

- [ ] **Step 3: Write TRACKED transition tests**

Required behavior:

```text
new TRACKED Product offer -> balance row 0/0 created in same catalog transaction
UNTRACKED -> TRACKED -> new zero balance allowed only when no conflicting stale balance exists
TRACKED -> UNTRACKED -> allowed only when reservedQuantity=0; retained balance row is removed only if stockOnHand=0
TRACKED -> UNTRACKED with physical stock >0 -> reject and require explicit stock adjustment to zero
Service never creates balance
```

- [ ] **Step 4: Implement `InventoryModeCoordinator`**

```java
public interface InventoryModeCoordinator {
    void onOfferCreated(LocationVariantEntity offer, CatalogItemType itemType);
    void beforeInventoryModeChange(
            LocationVariantEntity offer,
            InventoryMode oldMode,
            InventoryMode newMode,
            CatalogItemType itemType);
}
```

Implement as Spring bean in inventory module and inject into `CatalogCreateService` / `CatalogEditService`. This is the only Plan 03 service modification needed for inventory lifecycle.

- [ ] **Step 5: Run tests**

```bash
cd apps/api
./mvnw -Dtest=InventoryModeCoordinatorIntegrationTest,InventoryLockServiceIntegrationTest,CatalogCreateServiceIntegrationTest,CatalogEditServiceIntegrationTest test
```

Expected: Plan 03 regressions remain green.

- [ ] **Step 6: Commit**

```bash
git add apps/api/src/main/java/vn/covie/inventory apps/api/src/main/java/vn/covie/catalog/application/CatalogCreateService.java apps/api/src/main/java/vn/covie/catalog/application/CatalogEditService.java apps/api/src/test/java/vn/covie/inventory
git commit -m "feat(inventory): add tracked balance and lock foundation"
```

---

# Task 7: Implement Inventory Read and Adjustment Commands

**Files:**
- Create: `apps/api/src/main/java/vn/covie/inventory/application/InventoryQueryService.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/application/InventoryAdjustmentService.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/MerchantInventoryController.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/dto/SetStockRequest.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/dto/StockMovementRequest.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/dto/InventoryItemResponse.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/dto/InventoryCommandResult.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/inventory/application/InventoryAdjustmentServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/inventory/api/MerchantInventoryControllerIntegrationTest.java`

**Interfaces:**
- Consumes: merchant location authorization, idempotency executor, inventory locks.
- Produces: auditable seller stock management.

- [ ] **Step 1: Write set-stock/stock-in/stock-out tests**

```java
@Test void setStockComputesPositiveAdjustmentMovement() { }
@Test void setStockComputesNegativeAdjustmentMovement() { }
@Test void setStockSameValueIsIdempotentNoMovement() { }
@Test void cannotSetStockBelowReservedQuantity() { }
@Test void untrackedOfferRejectsInventoryCommand() { }
@Test void staffCanAdjustOnlyAssignedLocation() { }
@Test void repeatedIdempotencyKeyDoesNotDuplicateMovement() { }
```

- [ ] **Step 2: Implement single-row transaction**

For `setStock`, the reusable non-idempotency core has this exact signature and is always called inside an existing transaction:

```java
InventoryCommandResult setStockInternal(
        UUID actorUserId,
        UUID merchantId,
        UUID locationVariantId,
        long requestedStockOnHand,
        String reasonCode,
        String note)
```

Its transaction algorithm is:

```text
resolve LocationVariant + merchant/location ownership
require TRACKED Product
lock balance row FOR UPDATE
require requested >= reserved
compute delta
if delta > 0 append ADJUSTMENT_IN
if delta < 0 append ADJUSTMENT_OUT
update stockOnHand
return InventoryCommandResult
```

The HTTP single-item command wraps `setStockInternal` in `IdempotencyExecutor`; the bulk executor calls the same core from its own `REQUIRES_NEW` transaction.

`stock-in` appends `STOCK_IN`; `stock-out` appends `STOCK_OUT` and requires resulting `stockOnHand >= reservedQuantity`.

- [ ] **Step 3: Define request DTOs**

```java
public record SetStockRequest(
        @PositiveOrZero long stockOnHand,
        @NotBlank String reasonCode,
        @Size(max = 500) String note) {}

public record StockMovementRequest(
        @Positive long quantity,
        @NotBlank String reasonCode,
        @Size(max = 500) String note) {}
```

Reason code is stored as a bounded business code; note goes into JSON metadata. Do not store full HTTP body.

- [ ] **Step 4: Implement query DTO**

```java
public record InventoryItemResponse(
        UUID locationVariantId,
        UUID catalogItemId,
        String itemName,
        UUID variantId,
        String variantName,
        UUID locationId,
        String locationName,
        InventoryMode mode,
        Long stockOnHand,
        Long reservedQuantity,
        Long availableQuantity,
        long version) {}
```

UNTRACKED Product may appear with balance fields `null`; Service is excluded from inventory endpoint.

- [ ] **Step 5: Implement endpoints and errors**

Errors:

```text
INVENTORY_NOT_TRACKED
INVENTORY_BELOW_RESERVED_QUANTITY
INSUFFICIENT_INVENTORY
```

All three mutation commands require `Idempotency-Key`.

- [ ] **Step 6: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=InventoryAdjustmentServiceIntegrationTest,MerchantInventoryControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/inventory apps/api/src/test/java/vn/covie/inventory apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java
git commit -m "feat(inventory): add stock management commands"
```

---

# Task 8: Implement Per-Item Bulk Stock Set

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/inventory/application/InventoryAdjustmentService.java`
- Modify: `apps/api/src/main/java/vn/covie/inventory/api/MerchantInventoryController.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/dto/BulkSetStockRequest.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/dto/BulkSetStockItem.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/dto/BulkSetStockResult.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/api/dto/InventoryBulkItemResult.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/application/InventoryBulkService.java`
- Create: `apps/api/src/main/java/vn/covie/inventory/application/InventoryBulkItemExecutor.java`
- Test: `apps/api/src/test/java/vn/covie/inventory/application/InventoryBulkServiceIntegrationTest.java`

**Interfaces:**
- Consumes: single-item adjustment use case.
- Produces: selective bulk semantics without batch-wide rollback.

- [ ] **Step 1: Write partial-success test**

Fixture:

```text
A tracked, requested stock valid
B tracked, reserved=7, requested=5 -> conflict
C untracked -> invalid
```

Expected response preserves request order:

```json
{
  "results": [
    {"locationVariantId":"A","success":true,"stockOnHand":20},
    {"locationVariantId":"B","success":false,"errorCode":"INVENTORY_BELOW_RESERVED_QUANTITY"},
    {"locationVariantId":"C","success":false,"errorCode":"INVENTORY_NOT_TRACKED"}
  ]
}
```

- [ ] **Step 2: Implement independent per-item transactions with derived idempotency**

Do **not** wrap the whole batch in one `IdempotencyExecutor` transaction and then use `REQUIRES_NEW`; that can commit item mutations while the aggregate idempotency record rolls back.

Instead, the endpoint requires one client `Idempotency-Key`, and `InventoryBulkService` derives a stable per-item key:

```text
rawItemKey = batchIdempotencyKey + ":" + locationVariantId
operation  = INVENTORY_BULK_SET_STOCK_ITEM
```

Each item is executed through a dedicated bean so its `IdempotencyExecutor` transaction commits independently:

```java
@Service
public class InventoryBulkItemExecutor {
    private final IdempotencyExecutor idempotency;
    private final InventoryAdjustmentService adjustments;

    public InventoryBulkItemExecutor(
            IdempotencyExecutor idempotency,
            InventoryAdjustmentService adjustments) {
        this.idempotency = idempotency;
        this.adjustments = adjustments;
    }

    public InventoryBulkItemResult execute(
            UUID actorUserId,
            UUID merchantId,
            BulkSetStockItem item,
            String reasonCode,
            String note,
            String batchIdempotencyKey) {
        String itemKey = batchIdempotencyKey + ":" + item.locationVariantId();
        var request = new BulkSetStockItemCommand(
                merchantId, item.locationVariantId(), item.stockOnHand(), reasonCode, note);

        return idempotency.execute(
                actorUserId,
                "INVENTORY_BULK_SET_STOCK_ITEM",
                itemKey,
                request,
                200,
                InventoryBulkItemResult.class,
                () -> executeKnownBusinessOutcome(actorUserId, request));
    }

    private InventoryBulkItemResult executeKnownBusinessOutcome(
            UUID actorUserId,
            BulkSetStockItemCommand request) {
        try {
            InventoryCommandResult result = adjustments.setStockInternal(
                    actorUserId,
                    request.merchantId(),
                    request.locationVariantId(),
                    request.stockOnHand(),
                    request.reasonCode(),
                    request.note());
            return InventoryBulkItemResult.success(
                    request.locationVariantId(), result.stockOnHand());
        } catch (ApiException ex) {
            if (ex.code() != ErrorCode.INVENTORY_BELOW_RESERVED_QUANTITY
                    && ex.code() != ErrorCode.INVENTORY_NOT_TRACKED
                    && ex.code() != ErrorCode.MERCHANT_ACTION_NOT_ALLOWED) {
                throw ex;
            }
            return InventoryBulkItemResult.failure(
                    request.locationVariantId(), ex.code().name());
        }
    }
}
```

Add the internal immutable command record in `InventoryBulkItemExecutor.java` or as a package-private top-level record in the same file:

```java
record BulkSetStockItemCommand(
        UUID merchantId,
        UUID locationVariantId,
        long stockOnHand,
        String reasonCode,
        String note) {}
```

Known business failures above must be detected before balance/movement mutation. Unexpected exceptions propagate so the item idempotency transaction rolls back.

Define result factories:

```java
public record InventoryBulkItemResult(
        UUID locationVariantId,
        boolean success,
        Long stockOnHand,
        String errorCode) {

    public static InventoryBulkItemResult success(UUID id, long stock) {
        return new InventoryBulkItemResult(id, true, stock, null);
    }

    public static InventoryBulkItemResult failure(UUID id, String code) {
        return new InventoryBulkItemResult(id, false, null, code);
    }
}
```

A retry of the batch replays already-completed item results and executes only items whose derived idempotency record does not yet exist.

- [ ] **Step 3: Enforce V1 batch bound**

Plan 04 baseline:

```text
max 100 items/request
no duplicate locationVariantId in one request
```

Keep max as `covie.inventory.bulk-max-items=100` application property so Plan 08 can later source effective settings without controller changes.

- [ ] **Step 4: Run test and commit**

```bash
cd apps/api
./mvnw -Dtest=InventoryBulkServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/inventory apps/api/src/test/java/vn/covie/inventory
git commit -m "feat(inventory): add partial-success bulk stock set"
```

---

# Task 9: Implement Multi-Cart Add-to-Cart Semantics

**Files:**
- Create: `apps/api/src/main/java/vn/covie/cart/model/CartEntity.java`
- Create: `apps/api/src/main/java/vn/covie/cart/model/CartItemEntity.java`
- Create: `apps/api/src/main/java/vn/covie/cart/model/CartType.java`
- Create: `apps/api/src/main/java/vn/covie/cart/model/CartStatus.java`
- Create: `apps/api/src/main/java/vn/covie/cart/persistence/CartRepository.java`
- Create: `apps/api/src/main/java/vn/covie/cart/persistence/CartItemRepository.java`
- Create: `apps/api/src/main/java/vn/covie/cart/application/CartItemSnapshotFactory.java`
- Create: `apps/api/src/main/java/vn/covie/cart/application/CartService.java`
- Create: `apps/api/src/main/java/vn/covie/cart/api/MeCartController.java`
- Create: `apps/api/src/main/java/vn/covie/cart/api/dto/AddCartItemRequest.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/cart/application/CartServiceIntegrationTest.java`

**Interfaces:**
- Consumes: current actor, LocationVariant/catalog status/location state.
- Produces: one ACTIVE Cart per user/location/type and stable item identity for checkout.

- [ ] **Step 1: Write cart-routing tests**

```java
@Test void firstProductCreatesProductCartForResolvedLocation() { }
@Test void secondProductSameLocationUsesSameCart() { }
@Test void sameMerchantDifferentLocationCreatesAnotherCart() { }
@Test void serviceCreatesSeparateServiceCartAtSameLocation() { }
@Test void serviceQuantityOtherThanOneIsRejected() { }
@Test void addingSameLocationVariantIncrementsProductQuantity() { }
@Test void unavailableOrInactiveOfferCannotBeAdded() { }
```

- [ ] **Step 2: Map cart entities**

`CartEntity` uses `@Version`. `CartItemEntity` stores `locationVariantId`, `variantId`, quantity, item/variant snapshot and last-seen price.

Do not place `@Version` on individual CartItem; cart aggregate version is the mutation precondition.

- [ ] **Step 3: Implement race-safe find-or-create active Cart**

`CartRepository` must also expose the later Commit lock query:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select c from CartEntity c
    where c.id = :cartId and c.userId = :userId and c.cartStatus = 'ACTIVE'
    """)
Optional<CartEntity> findActiveByIdAndUserIdForUpdate(UUID cartId, UUID userId);
```

Two concurrent add requests may both not see a cart. Use the partial unique index as final arbiter:

```text
try find active cart
if absent insert cart
if unique violation -> reload active cart
then add/update item within transaction
```

Do not synchronize in JVM; it fails in multi-instance deployments.

- [ ] **Step 4: Validate offer before adding**

Required current state:

```text
CatalogItem ACTIVE and publicly sellable
Variant ACTIVE
LocationVariant not deleted and available
MerchantLocation ACTIVE
Product quantity >=1
Service quantity=1
```

Tracked inventory may be zero and still be allowed into Cart only if product UX wants saved carts; however response must include `OUT_OF_STOCK` warning. Checkout later blocks it. Do not reserve on add-to-cart.

- [ ] **Step 5: Implement endpoint**

```http
POST /api/v1/me/carts/items
```

No `cartId` is supplied. Backend resolves location/type from `locationVariantId`.

- [ ] **Step 6: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=CartServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/cart apps/api/src/test/java/vn/covie/cart apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java
git commit -m "feat(cart): add location scoped multi-cart"
```

---

# Task 10: Implement Cart Read, Quantity Update and Remove with ETag

**Files:**
- Create: `apps/api/src/main/java/vn/covie/cart/application/CartReadService.java`
- Create: `apps/api/src/main/java/vn/covie/cart/application/CartPricingViewService.java`
- Create: `apps/api/src/main/java/vn/covie/cart/api/dto/CartResponse.java`
- Create: `apps/api/src/main/java/vn/covie/cart/api/dto/CartItemResponse.java`
- Create: `apps/api/src/main/java/vn/covie/cart/api/dto/CartMerchantResponse.java`
- Create: `apps/api/src/main/java/vn/covie/cart/api/dto/CartLocationResponse.java`
- Create: `apps/api/src/main/java/vn/covie/cart/api/dto/UpdateCartItemRequest.java`
- Modify: `apps/api/src/main/java/vn/covie/cart/api/MeCartController.java`
- Test: `apps/api/src/test/java/vn/covie/cart/api/MeCartControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Plan 03 media/catalog read data, current inventory balances.
- Produces: current-price cart view and optimistic Cart mutations.

- [ ] **Step 1: Write read/current-price tests**

```text
cart snapshot price 45k, LocationVariant current price 50k
-> response unitPrice=50k
-> previousPriceSnapshot=45k
-> warning PRICE_CHANGED
-> subtotal uses 50k
```

Also test unavailable and tracked out-of-stock warning without deleting the Cart item.

- [ ] **Step 2: Implement read DTO**

```java
public record CartResponse(
        UUID id,
        CartType type,
        long version,
        CartMerchantResponse merchant,
        CartLocationResponse location,
        List<CartItemResponse> items,
        Money subtotal,
        List<String> warnings) {}
```

Response header contains `ETag` for detail.

- [ ] **Step 3: Implement PATCH/DELETE aggregate precondition**

```http
PATCH /api/v1/me/carts/{cartId}/items/{cartItemId}
If-Match: "v7"

DELETE /api/v1/me/carts/{cartId}/items/{cartItemId}
If-Match: "v8"
```

Load Cart by `(id,userId,status=ACTIVE)`, return 404 for another user's Cart, require Cart version before mutation, and increment aggregate version.

When last item is removed, retain empty ACTIVE Cart or mark `ABANDONED`? Plan 04 chooses **mark ABANDONED** to keep one active-cart unique index clean and avoid empty cart clutter. A future add creates a new ACTIVE Cart.

- [ ] **Step 4: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=MeCartControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/cart apps/api/src/test/java/vn/covie/cart
git commit -m "feat(cart): add current-price cart read and mutations"
```

---

# Task 11: Define Unified Checkout Candidate Contracts and CART/DIRECT Resolvers

**Files:**
- Create: `apps/api/src/main/java/vn/covie/checkout/model/CheckoutSourceType.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/model/FulfillmentMethod.java`
- Create: `apps/api/src/main/java/vn/covie/shared/commerce/PaymentMethod.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutCandidate.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutCandidateItem.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutCandidateResolver.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutCandidateResolverRegistry.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CartCheckoutCandidateResolver.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/DirectCheckoutCandidateResolver.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/api/dto/CheckoutPreviewRequest.java`
- Test: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutCandidateResolverIntegrationTest.java`

**Interfaces:**
- Consumes: Cart, LocationVariant/catalog, owned address/location context.
- Produces: normalized source-independent candidate for Preview and Commit.

- [ ] **Step 1: Define candidate contract**

```java
public record CheckoutCandidate(
        CheckoutSourceType sourceType,
        UUID buyerUserId,
        UUID merchantProfileId,
        UUID merchantLocationId,
        CatalogItemType itemType,
        List<CheckoutCandidateItem> items,
        Long sourceCartVersion,
        JsonNode sourceContext) {}

public record CheckoutCandidateItem(
        UUID locationVariantId,
        UUID variantId,
        UUID catalogItemId,
        long quantity) {}

public enum CheckoutSourceType { CART, DIRECT }
public enum FulfillmentMethod { PICKUP, SELLER_DELIVERY, SERVICE }
public enum PaymentMethod { COD, BANK_TRANSFER_NEGOTIATED }
```

- [ ] **Step 2: Define resolver SPI for Plan 06 extension**

```java
public interface CheckoutCandidateResolver {
    CheckoutSourceType supports();
    CheckoutCandidate resolve(UUID buyerUserId, CheckoutPreviewRequest request);
}
```

Registry fails startup if two beans claim the same source type.

- [ ] **Step 3: Implement CART resolver**

Requirements:

```text
cart belongs to actor
cart ACTIVE and non-empty
all items resolve to same Cart merchant location/type
sourceCartVersion = current cart.version
request must not send items[] for CART
```

- [ ] **Step 4: Implement DIRECT resolver**

Requirements:

```text
items[] required and 1..50
all LocationVariants share one MerchantLocation
all CatalogItems share one type
Product quantities >=1
Service quantities exactly 1
no duplicate locationVariantId; normalize duplicates only if request schema explicitly allows it (Plan 04 rejects duplicates)
```

- [ ] **Step 5: Keep NEED_REQUEST additive**

Do not include `NEED_REQUEST` in the Plan 04 request schema or DB checkout-session source check. Plan 06 adds it. The Java enum may include only `CART, DIRECT` now so generated clients do not advertise a nonfunctional source.

- [ ] **Step 6: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=CheckoutCandidateResolverIntegrationTest test
git add apps/api/src/main/java/vn/covie/checkout apps/api/src/test/java/vn/covie/checkout
git commit -m "feat(checkout): normalize cart and direct sources"
```

---

# Task 12: Implement Checkout Validation, Pricing and Preview

**Files:**
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutValidator.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutPricingService.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/persistence/CheckoutOfferReadRepository.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/persistence/JdbcCheckoutOfferReadRepository.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutPreview.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutPreviewService.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/api/dto/CheckoutPreviewResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutPreviewServiceIntegrationTest.java`

**Interfaces:**
- Consumes: candidate, catalog/location current state, inventory availability, coverage, shipping, address.
- Produces: authoritative point-in-time Preview without stock reservation.

- [ ] **Step 1: Write preview rule matrix tests**

Required cases:

```text
Product PICKUP: pickupEnabled required, no address required, shipping fee 0
Product SELLER_DELIVERY: address required, coverage required, minimum order validated, shipping evaluated
Service: buyer address + SERVICE_AREA coverage required, shipping null/not applicable
Service: provider location must have serviceEnabled=true and no shipping policy is evaluated
tracked inventory available >= quantity
untracked Product skips inventory balance
Service quantity=1
inactive item/variant/offer/location rejects
price uses current LocationVariant, not Cart snapshot
preview never changes reservedQuantity
```

- [ ] **Step 2: Define normalized preview result**

`CheckoutPreview` contains exact current item/unit prices, subtotal, fulfillment context, buyer address snapshot candidate, coverage/distance, shipping evaluation and source version.

- [ ] **Step 3: Implement current-state validation in bulk**

Avoid N+1 LocationVariant/item loads. Add a repository read model query that resolves all candidate `locationVariantId`s with:

```text
CatalogItem status/moderation/type/name
Variant status/name/selected options
LocationVariant current price/currency/available/inventoryMode
MerchantLocation status/capability/currency/minimumDeliveryOrder
MerchantProfile identity/name
```

Validate every requested ID is present exactly once.

- [ ] **Step 4: Validate minimum order after current subtotal**

```java
if (fulfillment == SELLER_DELIVERY
        && location.minimumDeliveryOrder() != null
        && subtotal < location.minimumDeliveryOrder()) {
    throw new ApiException(
            ErrorCode.MINIMUM_ORDER_NOT_MET,
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Minimum delivery order is not met.",
            Map.of("minimumAmount", location.minimumDeliveryOrder(), "currentSubtotal", subtotal));
}
```

- [ ] **Step 5: Add errors**

```text
CHECKOUT_SOURCE_INVALID
CHECKOUT_ITEMS_INVALID
CHECKOUT_ITEM_UNAVAILABLE
CHECKOUT_LOCATION_UNAVAILABLE
CHECKOUT_FULFILLMENT_NOT_SUPPORTED
DELIVERY_NOT_COVERED
MINIMUM_ORDER_NOT_MET
INSUFFICIENT_INVENTORY
PAYMENT_METHOD_NOT_SUPPORTED
```

- [ ] **Step 6: Run preview tests and verify no reservation**

```bash
cd apps/api
./mvnw -Dtest=CheckoutPreviewServiceIntegrationTest test
```

In the tracked-stock test assert `reserved_quantity` remains unchanged after Preview.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/checkout apps/api/src/test/java/vn/covie/checkout apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java
git commit -m "feat(checkout): validate and price preview"
```

---

# Task 13: Implement Opaque Single-Use Checkout Sessions

**Files:**
- Create: `apps/api/src/main/java/vn/covie/checkout/model/CheckoutSessionEntity.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/persistence/CheckoutSessionRepository.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutTokenGenerator.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutTokenHasher.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutSessionService.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CreatedCheckoutSession.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/config/CheckoutProperties.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Test: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutSessionServiceIntegrationTest.java`

**Interfaces:**
- Consumes: Preview result and `AppClock`.
- Produces: `chk_...` token to client and locked session lookup for Commit.

- [ ] **Step 1: Write token/security tests**

```java
@Test void rawTokenIsNotPersisted() { }
@Test void sessionExpiresAfterConfiguredFifteenMinutes() { }
@Test void tokenCannotBeUsedByAnotherUser() { }
@Test void consumedSessionCannotBeConsumedAgain() { }
@Test void repositoryCanLockSessionByTokenHash() { }
```

- [ ] **Step 2: Implement 256-bit opaque token**

```java
@Component
public final class CheckoutTokenGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "chk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

Hash the **entire raw token** with SHA-256 before DB lookup/storage.

- [ ] **Step 3: Configure TTL**

```yaml
covie:
  checkout:
    session-ttl: 15m
```

Bind through validated `@ConfigurationProperties`.

- [ ] **Step 4: Add locked lookup**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from CheckoutSessionEntity s where s.tokenHash = :tokenHash")
Optional<CheckoutSessionEntity> findByTokenHashForUpdate(String tokenHash);
```

Commit validates actor, expiry and `consumedAt == null` after acquiring the lock.

- [ ] **Step 5: Persist canonical request/preview snapshots**

Use dedicated snapshot records/JSON nodes. Never serialize JPA entities.

The stored Preview snapshot must contain fields used for change detection:

```text
source identity/version
location ID
item locationVariantId + quantity + current unit price + currency + availability-relevant data
fulfillment method
address identity/snapshot fingerprint
coverage ID/distance/result
minimum order used
shipping policy version + evaluated result
payment method
```

- [ ] **Step 6: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=CheckoutSessionServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/checkout apps/api/src/main/resources/application.yml apps/api/src/test/java/vn/covie/checkout
git commit -m "feat(checkout): add opaque server-side checkout sessions"
```

---

# Task 14: Wire `POST /checkouts/preview` and Preview API Contract

**Files:**
- Create: `apps/api/src/main/java/vn/covie/checkout/api/CheckoutController.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutPreviewService.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/api/dto/CheckoutPreviewResponse.java`
- Test: `apps/api/src/test/java/vn/covie/checkout/api/CheckoutPreviewControllerIntegrationTest.java`

**Interfaces:**
- Consumes: source resolver, validation/pricing, session service.
- Produces: approved API preview endpoint.

- [ ] **Step 1: Write controller tests**

Test CART Product delivery and DIRECT Product pickup request JSON. Assert:

```text
200 OK
checkoutToken begins chk_
expiresAt UTC ISO-8601
resolved merchant/location returned
pricing uses amount/currency objects
NEGOTIATED -> shippingFee null + total null + knownTotal=subtotal
Cache-Control: private, no-store
```

- [ ] **Step 2: Implement orchestration**

```text
actor
-> resolverRegistry.resolve(request.source.type)
-> checkoutValidator/CheckoutPricingService
-> CheckoutSessionService.create(actor.userId(), request, candidate, preview)
-> response DTO
```

Do not start a long transaction around the whole Preview. Read validation may use one read-only transaction; session insert is a short write transaction.

- [ ] **Step 3: Return resolved LocationContext/fulfillment details**

For delivery/service-at-customer, response echoes a safe resolved address/location summary so UI can show what was evaluated.

- [ ] **Step 4: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=CheckoutPreviewControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/checkout/api apps/api/src/main/java/vn/covie/checkout/application/CheckoutPreviewService.java apps/api/src/test/java/vn/covie/checkout/api
git commit -m "feat(checkout): expose preview endpoint"
```

---

# Task 15: Implement Order Creation Foundation and Immutable Snapshot Factory

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderEntity.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderItemEntity.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/ProductFulfillmentEntity.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/ServiceFulfillmentEntity.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderEventEntity.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderStatus.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderType.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderSourceType.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderShippingFeeType.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/ProductFulfillmentMethod.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderEventType.java`
- Create: `apps/api/src/main/java/vn/covie/orders/persistence/OrderRepository.java`
- Create: `apps/api/src/main/java/vn/covie/orders/persistence/OrderItemRepository.java`
- Create: `apps/api/src/main/java/vn/covie/orders/persistence/ProductFulfillmentRepository.java`
- Create: `apps/api/src/main/java/vn/covie/orders/persistence/ServiceFulfillmentRepository.java`
- Create: `apps/api/src/main/java/vn/covie/orders/persistence/OrderEventRepository.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderCodeGenerator.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderSnapshotFactory.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/CreatedOrder.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderCreationService.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderCreationServiceIntegrationTest.java`

**Interfaces:**
- Consumes: authoritative revalidated checkout result.
- Produces: one `PENDING_CONFIRMATION` Order aggregate + initial event, consumed by Plan 05.

- [ ] **Step 1: Map creation-only Order enum set**

```java
public enum OrderStatus { PENDING_CONFIRMATION }
public enum OrderType { PRODUCT, SERVICE }
public enum OrderSourceType { MARKETPLACE, NEED_REQUEST }
public enum OrderShippingFeeType { FREE, FIXED, CONDITIONAL, NEGOTIATED, NOT_APPLICABLE }
public enum ProductFulfillmentMethod { PICKUP, SELLER_DELIVERY }
public enum OrderEventType { ORDER_CREATED }
```

Plan 05 extends status/event enum files additively when implementing lifecycle.

- [ ] **Step 2: Implement collision-safe display Order code**

Canonical commands/read continue to use UUID. Display code example:

```text
CV-260808-7K3M9P
```

Generate random suffix with sufficient entropy and retry on unique constraint collision in a bounded loop. Do not derive canonical identity from display code.

- [ ] **Step 3: Implement immutable snapshot records**

Factory input is revalidated checkout data, not entities exposed to controller.

Snapshots include:

```text
item name
variant name/options
unit price/currency
merchant display identity
MerchantLocation name/address/geo/contact needed for fulfillment
buyer address/contact where fulfillment needs it
shipping policy evaluation snapshot
payment method
```

For Product pickup, buyer delivery address is null. For seller delivery and Service, the buyer/service address snapshot is required.

Order money mapping is exact:

```text
Product PICKUP:
  shippingFeeType = NOT_APPLICABLE
  shippingFee = null
  knownTotal = subtotal
  totalAmount = subtotal

Product SELLER_DELIVERY + FREE:
  shippingFeeType = FREE
  shippingFee = 0
  knownTotal = subtotal
  totalAmount = subtotal

Product SELLER_DELIVERY + FIXED/CONDITIONAL:
  shippingFeeType = policy mode
  shippingFee = evaluated fee
  knownTotal = subtotal + fee
  totalAmount = subtotal + fee

Product SELLER_DELIVERY + NEGOTIATED:
  shippingFeeType = NEGOTIATED
  shippingFee = null
  knownTotal = subtotal
  totalAmount = null

SERVICE:
  shippingFeeType = NOT_APPLICABLE
  shippingFee = null
  knownTotal = subtotal
  totalAmount = subtotal
```

- [ ] **Step 4: Implement creation transaction helper with mandatory propagation**

`OrderCreationService.createPending` uses this exact public signature:

```java
@Transactional(propagation = Propagation.MANDATORY)
public CreatedOrder createPending(RevalidatedCheckout checkout)
```

The method body must execute these writes in order inside the caller transaction:

```text
generate UUIDv7 Order ID + collision-safe orderCode
map CART/DIRECT source to MARKETPLACE
calculate confirmation_expires_at from the injected order-confirmation timeout policy if configured; null only until Plan 05 activates timeout policy
insert Order
insert OrderItems ordered by locationVariantId
insert exactly one ProductFulfillment or ServiceFulfillment
insert ORDER_CREATED event
return CreatedOrder containing Order ID, code and OrderItem IDs mapped to locationVariantId
```

It inserts Order, OrderItems, one fulfillment subtype and `ORDER_CREATED` event. CART and DIRECT Checkout sources both map to `OrderSourceType.MARKETPLACE`; Plan 06 maps Need-origin checkout to `NEED_REQUEST`. It does **not** reserve inventory itself; caller performs reservation after item IDs exist in the same outer Commit transaction.

- [ ] **Step 5: Write event assertion**

Every created Order has exactly one initial event with actor user ID = buyer and snapshot-safe metadata. No controller can create Order rows directly.

- [ ] **Step 6: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=OrderCreationServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders apps/api/src/test/java/vn/covie/orders
git commit -m "feat(orders): add pending order creation foundation"
```

---

# Task 16: Implement Reservation/Movement Write Path for Checkout

**Files:**
- Create: `apps/api/src/main/java/vn/covie/inventory/application/InventoryReservationService.java`
- Modify: `apps/api/src/main/java/vn/covie/inventory/model/InventoryBalanceEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/inventory/model/InventoryReservationEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/inventory/model/InventoryMovementEntity.java`
- Test: `apps/api/src/test/java/vn/covie/inventory/application/InventoryReservationServiceIntegrationTest.java`

**Interfaces:**
- Consumes: locked balances and newly created OrderItem IDs.
- Produces: ACTIVE reservation + reserved balance increment + RESERVE movement atomically.

- [ ] **Step 1: Write reservation tests**

```java
@Test void trackedItemReserveCreatesReservationMovementAndIncrementsReserved() { }
@Test void multipleTrackedItemsReserveInStableOrder() { }
@Test void untrackedItemCreatesNoReservation() { }
@Test void serviceCreatesNoReservation() { }
@Test void insufficientAvailableQuantityFailsBeforeAnyPartialReservationPersists() { }
```

- [ ] **Step 2: Implement balance mutation methods**

```java
public void reserve(long quantity, Instant now) {
    if (quantity <= 0 || availableQuantity() < quantity) {
        throw new ApiException(
                ErrorCode.INSUFFICIENT_INVENTORY,
                HttpStatus.CONFLICT,
                "Insufficient inventory.");
    }
    this.reservedQuantity += quantity;
    this.updatedAt = now;
}
```

Do not expose `setReservedQuantity` publicly.

- [ ] **Step 3: Implement reservation service with MANDATORY transaction**

Caller passes balances already locked by `InventoryLockService`. Assert lock coverage by mapping requested tracked LocationVariant IDs to locked balance map.

For each tracked OrderItem:

```text
balance.reserve(quantity)
insert ACTIVE inventory_reservation
insert RESERVE movement(quantity positive)
```

- [ ] **Step 4: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=InventoryReservationServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/inventory apps/api/src/test/java/vn/covie/inventory
git commit -m "feat(inventory): reserve tracked stock for orders"
```

---

# Task 17: Implement Atomic Checkout Commit + Change Detection

**Files:**
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutCommitService.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutComparisonService.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutComparableSnapshot.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutChange.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutChangeType.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/application/RevalidatedCheckout.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/api/dto/CheckoutCommitRequest.java`
- Create: `apps/api/src/main/java/vn/covie/checkout/api/dto/OrderCreatedResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/api/CheckoutController.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/checkout/application/CheckoutCommitServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/checkout/api/CheckoutCommitControllerIntegrationTest.java`

**Interfaces:**
- Consumes: IdempotencyExecutor, locked checkout session, source resolver, validation/pricing, inventory locks, Order creation, reservation, Outbox writer.
- Produces: real Order creation endpoint with exactly-once effect for client retries.

- [ ] **Step 1: Write commit happy-path test**

Tracked Product fixture:

```text
stockOnHand=5
reserved=0
cart quantity=2
preview succeeds
commit succeeds
```

Assert after Commit:

```text
Order PENDING_CONFIRMATION exists
2 quantity OrderItem snapshot correct
ACTIVE reservation quantity=2
balance reserved=2
one RESERVE movement
ORDER_CREATED event
outbox ORDER_CREATED intent
checkout_session.consumed_at non-null
source Cart item removed
empty source Cart status CHECKED_OUT
```

- [ ] **Step 2: Write change-detection tests**

Between Preview and Commit mutate one at a time:

```text
price
LocationVariant availability
CatalogItem lifecycle
tracked stock below required quantity
shipping policy fee/rule
coverage eligibility
minimumDeliveryOrder
Cart version/items
buyer delivery address content/version
```

Expected authoritative changes that alter the accepted preview return:

```http
409 CHECKOUT_CHANGED
```

Response details use stable change types, never raw SQL/entity diffs:

```text
PRICE_CHANGED
ITEM_UNAVAILABLE
INVENTORY_CHANGED
CART_CHANGED
DELIVERY_COVERAGE_CHANGED
SHIPPING_CHANGED
MINIMUM_ORDER_CHANGED
ADDRESS_CHANGED
```

For stock simply becoming insufficient, `CHECKOUT_CHANGED` details may include `INVENTORY_CHANGED`; direct inventory adjustment endpoints still use `INSUFFICIENT_INVENTORY`.

- [ ] **Step 3: Implement comparison as canonical semantic comparison**

Do not compare JSON string serialization byte-for-byte. Normalize Preview and revalidated state into a typed `CheckoutComparableSnapshot` with stable lists ordered by `locationVariantId`.

- [ ] **Step 4: Implement outer IdempotencyExecutor call**

Controller:

```java
@PostMapping("/api/v1/checkouts/commit")
public ResponseEntity<OrderCreatedResponse> commit(
        @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody CheckoutCommitRequest request) {
    CurrentActor actor = actors.currentActor();
    OrderCreatedResponse result = idempotency.execute(
            actor.userId(),
            "CHECKOUT_COMMIT",
            key,
            request,
            201,
            OrderCreatedResponse.class,
            () -> commitService.commit(actor.userId(), request.checkoutToken()));
    return ResponseEntity.created(URI.create("/api/v1/orders/" + result.id())).body(result);
}
```

Because `IdempotencyExecutor` is transactional, `CheckoutCommitService.commit` participates in the same transaction. Do not introduce `REQUIRES_NEW` inside Commit business steps.

- [ ] **Step 5: Implement exact Commit lock/order sequence**

Inside transaction:

```text
hash token
lock checkout_session FOR UPDATE
validate user / expiry / unconsumed
if CART: read cartId from server-owned checkout_session.source_context
if CART: lock source Cart row FOR UPDATE and require expected version
resolve source again from the locked Cart; DIRECT resolves from stored request snapshot
load current authoritative offer/location/address data
collect TRACKED LocationVariant IDs
lock balances ordered UUID ASC
revalidate all checkout rules using locked stock
compare typed snapshot against Preview
create Order + OrderItems + fulfillment + initial event
reserve tracked inventory
append outbox ORDER_CREATED
mark checkout session consumed
remove source Cart items; mark Cart CHECKED_OUT if empty
return OrderCreatedResponse
```

Do not mark session consumed before all business writes succeed.

- [ ] **Step 6: Handle expired/consumed token**

Errors:

```text
CHECKOUT_SESSION_INVALID
CHECKOUT_SESSION_EXPIRED
CHECKOUT_SESSION_CONSUMED
CHECKOUT_CHANGED
```

A consumed token with the **same Checkout Commit idempotency key** never reaches session validation on retry because IdempotencyExecutor returns stored result first.

A consumed token with a new key returns conflict and cannot create another Order.

- [ ] **Step 7: Implement response**

```java
public record OrderCreatedResponse(
        UUID id,
        String orderCode,
        OrderStatus status,
        Instant createdAt) {}
```

Status is `PENDING_CONFIRMATION`. No full Order detail or `allowedActions` yet; Plan 05 introduces canonical read model.

- [ ] **Step 8: Run tests and commit**

```bash
cd apps/api
./mvnw -Dtest=CheckoutCommitServiceIntegrationTest,CheckoutCommitControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/checkout apps/api/src/test/java/vn/covie/checkout apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java
git commit -m "feat(checkout): atomically create pending orders"
```

---

# Task 18: Prove Last-Stock Concurrency and Rollback Invariants on Real PostgreSQL

**Files:**
- Create: `apps/api/src/test/java/vn/covie/checkout/concurrency/CheckoutLastStockConcurrencyTest.java`
- Create: `apps/api/src/test/java/vn/covie/checkout/concurrency/CheckoutRollbackInvariantTest.java`

**Interfaces:**
- Consumes: full Checkout Commit path.
- Produces: fresh evidence that Plan 04 cannot oversell and rollback is all-or-nothing.

- [ ] **Step 1: Write two-buyer last-stock test**

Fixture:

```text
one TRACKED LocationVariant
stockOnHand = 1
reservedQuantity = 0
Buyer A has valid preview for qty 1
Buyer B has valid preview for qty 1
```

Use two real threads, independent transactions and a barrier so both Commit operations start concurrently.

Expected:

```text
exactly one 201-equivalent successful service result
exactly one CHECKOUT_CHANGED/INVENTORY_CHANGED conflict
exactly one Order row
exactly one ACTIVE reservation qty 1
reservedQuantity = 1
stockOnHand = 1
availableQuantity = 0
exactly one RESERVE movement
```

Do not weaken test by serially invoking the two commits.

- [ ] **Step 2: Write multi-item deadlock-order test**

Two buyers request the same two tracked offers but requests list items in reverse order. Run concurrently. The lock service must sort UUIDs so PostgreSQL does not deadlock because of request ordering.

Set a reasonable test timeout and fail on deadlock SQL state.

- [ ] **Step 3: Write rollback injection test**

Inject a test-only failure **after Order rows are inserted but before session consume** using a package-private hook/test configuration. Assert transaction rollback removes:

```text
Order
OrderItems
fulfillment
ORDER_CREATED event
reservation
RESERVE movement
reserved balance increment
outbox event
cart deletion/status change
```

and checkout session remains unconsumed.

Do not ship a production feature flag that can trigger this failure; use a test bean/spy around a port invoked only in tests.

- [ ] **Step 4: Run concurrency suite repeatedly**

```bash
cd apps/api
for i in 1 2 3 4 5; do
  ./mvnw -Dtest=CheckoutLastStockConcurrencyTest,CheckoutRollbackInvariantTest test || exit 1
done
```

Expected: all five runs pass with no deadlocks and no oversell.

- [ ] **Step 5: Commit**

```bash
git add apps/api/src/test/java/vn/covie/checkout/concurrency
git commit -m "test(checkout): prove stock concurrency invariants"
```

---

# Task 19: Extend Public Catalog Discovery with Fulfillment Filters

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/dto/CatalogDiscoveryQuery.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/persistence/JdbcCatalogDiscoveryRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogDiscoveryService.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/CatalogItemSummaryResponse.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/application/CatalogDiscoveryShippingFilterIntegrationTest.java`

**Interfaces:**
- Consumes: delivery coverage and shipping policy data.
- Produces: API Design filters deferred by Plan 03.

- [ ] **Step 1: Add typed filters**

Support:

```text
canServe=true
pickup=true
shipping=FREE|FIXED|NEGOTIATED|CONDITIONAL
```

Do not add a generic predicate DSL.

- [ ] **Step 2: Preserve one resolved MerchantLocation rule**

Filtering/ranking must happen before final resolved offer selection so the result cannot say `pickup=true` but resolve to a different non-pickup location.

For `canServe=true`:

```text
PRODUCT -> pickup at resolved location OR seller-delivery coverage to LocationContext
SERVICE -> SERVICE_AREA coverage (or provider-location mode where product UX explicitly permits it)
```

- [ ] **Step 3: Avoid shipping evaluator per result N+1**

Discovery filter `shipping=...` checks active policy mode at candidate location. It does **not** compute conditional effective fee for every search result. Checkout remains authoritative for actual shipping fee.

- [ ] **Step 4: Run search tests + explain plan**

```bash
cd apps/api
./mvnw -Dtest=CatalogDiscoveryShippingFilterIntegrationTest,CatalogDiscoveryServiceIntegrationTest test
```

Run representative `EXPLAIN (ANALYZE, BUFFERS)` and ensure coverage/policy joins do not destroy keyset query behavior.

- [ ] **Step 5: Commit**

```bash
git add apps/api/src/main/java/vn/covie/catalog apps/api/src/test/java/vn/covie/catalog
git commit -m "feat(catalog): filter discovery by fulfillment"
```

---

# Task 20: Add Minimal Buyer/Seller Web Slices for Cart, Checkout, Inventory and Shipping

**Files:**
- Create: `apps/web/app/(buyer)/cart/page.tsx`
- Create: `apps/web/app/(buyer)/checkout/page.tsx`
- Create: `apps/web/app/(buyer)/checkout/success/[orderId]/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/inventory/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/locations/[locationId]/delivery/page.tsx`
- Create: `apps/web/features/cart/cart-api.ts`
- Create: `apps/web/features/cart/cart-store.ts`
- Create: `apps/web/features/cart/cart-list.tsx`
- Create: `apps/web/features/cart/cart-item-row.tsx`
- Create: `apps/web/features/checkout/checkout-api.ts`
- Create: `apps/web/features/checkout/checkout-state.ts`
- Create: `apps/web/features/checkout/checkout-preview.tsx`
- Create: `apps/web/features/checkout/checkout-change-dialog.tsx`
- Create: `apps/web/features/inventory/inventory-api.ts`
- Create: `apps/web/features/inventory/inventory-table.tsx`
- Create: `apps/web/features/shipping/shipping-api.ts`
- Create: `apps/web/features/shipping/coverage-form.tsx`
- Create: `apps/web/features/shipping/shipping-policy-form.tsx`
- Test: `apps/web/features/cart/cart-list.test.tsx`
- Test: `apps/web/features/checkout/checkout-preview.test.tsx`
- Test: `apps/web/features/inventory/inventory-table.test.tsx`
- Test: `apps/web/features/shipping/shipping-policy-form.test.tsx`

**Interfaces:**
- Consumes: generated OpenAPI client, Plan 02 auth/CSRF wrapper, Plan 03 location context.
- Produces: testable UI surfaces sufficient to manually exercise Plan 04 APIs; Plan 09 later completes product UX/polish.

- [ ] **Step 1: Implement cart API wrapper without duplicating DTOs**

Use generated types. Wrapper responsibilities only:

```text
credentials/cookies
CSRF header
If-Match header
Idempotency-Key generation where command needs it
ApiError mapping
```

- [ ] **Step 2: Implement cart list grouped by MerchantLocation/type**

Cart header shows merchant/location and current warnings. Quantity controls send current ETag and refetch on `RESOURCE_VERSION_CONFLICT`.

- [ ] **Step 3: Implement Checkout Preview state**

State stores only request choices and returned `checkoutToken`; it never calculates authoritative totals locally.

On `CHECKOUT_CHANGED` from Commit:

```text
discard old token
show stable change categories
request fresh Preview
require buyer to press final commit again
```

Never auto-recommit after a changed Preview.

- [ ] **Step 4: Implement seller inventory table**

Support single `set-stock` and display:

```text
stockOnHand
reservedQuantity
availableQuantity
TRACKED/UNTRACKED
```

Do not make reserved quantity editable.

- [ ] **Step 5: Implement coverage and shipping forms**

UI dynamically shows fields by mode but backend validation remains authoritative.

For NEGOTIATED display copy must communicate "phí giao hàng thỏa thuận" rather than zero.

- [ ] **Step 6: Add frontend tests**

Required:

```text
service Cart quantity cannot increment above 1
negotiated preview renders unknown shipping/total
checkout changed requires fresh Preview
inventory reserved quantity is read-only
stale ETag refetch path works
```

- [ ] **Step 7: Run frontend quality gate and commit**

```bash
pnpm web:lint
pnpm web:typecheck
pnpm web:test
pnpm web:build

git add apps/web
git commit -m "feat(web): add cart checkout and inventory slices"
```

---

# Task 21: Refresh OpenAPI Client and Run Full Plan 04 Acceptance Gate

**Files:**
- Modify generated OpenAPI artifact/client as produced by Plan 01 workflow.
- Modify: `README.md` only if local manual flow commands changed.
- Test: all Plan 04 backend/frontend suites.

**Interfaces:**
- Consumes: Tasks 1–20.
- Produces: verified Plan 04 milestone ready for Plan 05.

- [ ] **Step 1: Generate OpenAPI and TypeScript client**

```bash
cd apps/api
./mvnw verify
cd ../..
pnpm api-client:generate
./infra/scripts/verify-generated-client.sh
pnpm api-client:check
```

Expected: no generated-client drift after generation is committed.

- [ ] **Step 2: Run all backend tests**

```bash
cd apps/api
./mvnw clean verify
```

Expected: exit 0.

- [ ] **Step 3: Run concurrency suite again after full integration**

```bash
cd apps/api
for i in 1 2 3; do
  ./mvnw -Dtest=CheckoutLastStockConcurrencyTest,CheckoutRollbackInvariantTest test || exit 1
done
```

Expected: all passes.

- [ ] **Step 4: Run frontend gates**

```bash
cd ../..
pnpm web:lint
pnpm web:typecheck
pnpm web:test
pnpm web:build
```

- [ ] **Step 5: Validate clean database migration V001 -> V009**

```bash
pnpm infra:down
docker compose -f infra/compose/compose.local.yml down -v
pnpm infra:up
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Verify Flyway applies V001–V009 in order and `/api/v1/health` returns 200.

- [ ] **Step 6: Manual smoke flow**

Using local UI/API:

```text
seller creates/uses Product LocationVariant TRACKED
seller set stock = 2
seller enables Product delivery coverage
seller creates FIXED shipping policy
buyer adds qty 1 to Cart
buyer Preview delivery checkout
assert Preview does not increment reserved
buyer Commit
assert Order PENDING_CONFIRMATION
assert reserved increments 1
assert cart selected item removed/checked out
seller inventory view shows stockOnHand 2, reserved 1, available 1
```

- [ ] **Step 7: Commit generated contract/acceptance updates**

```bash
git add packages/api-client README.md apps/api/src/test apps/web
git commit -m "test(checkout): verify plan 04 transaction milestone"
```

---

# Plan 04 Acceptance Checklist

Plan 04 is complete only when fresh evidence proves every item below:

```text
[ ] Flyway clean DB applies V001 -> V009 in order
[ ] one active PRODUCT_DELIVERY coverage per location/type
[ ] one active SERVICE_AREA coverage per location/type
[ ] coverage radius uses PostGIS geography
[ ] selected area matches descendants correctly
[ ] coverage modes combine with OR
[ ] shipping FREE/FIXED/NEGOTIATED/CONDITIONAL semantics pass tests
[ ] NEGOTIATED shipping fee and total remain null
[ ] conditional ANY/ALL boundaries are tested
[ ] every existing/new TRACKED Product offer has one inventory balance
[ ] Service never gets inventory balance
[ ] TRACKED -> UNTRACKED cannot discard reserved or physical stock silently
[ ] set-stock always writes adjustment ledger when delta != 0
[ ] stock cannot fall below reservedQuantity
[ ] bulk stock returns per-item independent results
[ ] one active Cart exists per user/location/type
[ ] same Merchant different location gets separate Cart
[ ] Product/Service never share Cart
[ ] add-to-cart uses exact locationVariantId
[ ] Service cart quantity is exactly 1
[ ] Cart current view uses latest price and warns on change
[ ] Cart mutations enforce If-Match
[ ] Preview supports CART and DIRECT through common CheckoutCandidate
[ ] Preview never reserves stock
[ ] Preview never locks price
[ ] checkout token is opaque, hashed at rest, 15-minute TTL and single-use
[ ] expired token is rejected
[ ] consumed token with new idempotency key cannot create another Order
[ ] Commit revalidates item/price/location/coverage/minimum/shipping/inventory
[ ] changed authoritative Preview data returns 409 CHECKOUT_CHANGED
[ ] Commit idempotency retry creates exactly one Order
[ ] Commit creates real PENDING_CONFIRMATION Order + OrderItems + fulfillment + ORDER_CREATED event
[ ] Order stores immutable pricing/location/address/shipping snapshots
[ ] tracked stock reservation is created at Order creation
[ ] untracked Product and Service create no reservation
[ ] source Cart selected items are cleared only after successful transaction
[ ] Order creation and Outbox intent are atomic
[ ] rollback injection leaves no partial Order/reservation/movement/cart mutation
[ ] last-stock concurrency test yields exactly one success + one conflict
[ ] reverse item ordering concurrency test does not deadlock
[ ] public catalog fulfillment filters preserve resolved-location correctness
[ ] generated OpenAPI/TypeScript client is in sync
[ ] backend clean verify passes
[ ] frontend lint/typecheck/test/build passes
```

---

# Error Codes Added by Plan 04

Add stable machine-readable codes only when the corresponding use case is implemented:

```text
DELIVERY_COVERAGE_INVALID
DELIVERY_COVERAGE_DUPLICATE_ACTIVE
DELIVERY_COVERAGE_LOCATION_NOT_ENABLED
SHIPPING_POLICY_INVALID
SHIPPING_POLICY_DUPLICATE_ACTIVE
SHIPPING_RULE_INVALID
INVENTORY_NOT_TRACKED
INVENTORY_BELOW_RESERVED_QUANTITY
INSUFFICIENT_INVENTORY
CART_ITEM_INVALID
CART_STATE_CONFLICT
CHECKOUT_SOURCE_INVALID
CHECKOUT_ITEMS_INVALID
CHECKOUT_ITEM_UNAVAILABLE
CHECKOUT_LOCATION_UNAVAILABLE
CHECKOUT_FULFILLMENT_NOT_SUPPORTED
DELIVERY_NOT_COVERED
MINIMUM_ORDER_NOT_MET
PAYMENT_METHOD_NOT_SUPPORTED
CHECKOUT_SESSION_INVALID
CHECKOUT_SESSION_EXPIRED
CHECKOUT_SESSION_CONSUMED
CHECKOUT_CHANGED
CATALOG_FILTER_INVALID
```

`RESOURCE_VERSION_CONFLICT`, `IDEMPOTENCY_KEY_REQUIRED`, and `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST` already come from Plan 03 and must be reused rather than duplicated.

---

# Checkout Change Detail Contract

`CHECKOUT_CHANGED` uses stable detail objects:

```json
{
  "error": {
    "code": "CHECKOUT_CHANGED",
    "details": {
      "changes": [
        {
          "type": "PRICE_CHANGED",
          "locationVariantId": "0198..."
        }
      ]
    },
    "requestId": "req_..."
  }
}
```

Allowed Plan 04 change types:

```text
PRICE_CHANGED
ITEM_UNAVAILABLE
INVENTORY_CHANGED
CART_CHANGED
DELIVERY_COVERAGE_CHANGED
SHIPPING_CHANGED
MINIMUM_ORDER_CHANGED
ADDRESS_CHANGED
FULFILLMENT_CHANGED
```

Never return internal entity field names such as `reservedQuantity`, `version`, or SQL column names as the change type contract unless they are already intentionally public fields.

---

# Inventory Movement Sign Convention

To keep ledger queries consistent, movement `quantity` is always stored as a positive magnitude. Direction comes from `movement_type`:

```text
STOCK_IN            + physical stock
STOCK_OUT           - physical stock
ADJUSTMENT_IN       + physical stock
ADJUSTMENT_OUT      - physical stock
RESERVE              reserved +
RELEASE_RESERVATION  reserved -
SALE                stock - and reserved - during Plan 05 completion
```

Do not mix signed and unsigned movement quantities.

Plan 05 implements reservation release/commit and `SALE` transitions.

---

# Order Foundation Handoff to Plan 05

Plan 04 guarantees every Order entering Plan 05 starts in this state:

```text
status = PENDING_CONFIRMATION
immutable Order/Item/Fulfillment snapshots exist
ORDER_CREATED event exists
tracked Product reservations ACTIVE
Order source = MARKETPLACE for CART/DIRECT checkout
outbox ORDER_CREATED intent exists
```

Plan 05 may rely on:

```text
OrderRepository lock-by-id query
OrderItemRepository by orderId
ProductFulfillmentRepository
ServiceFulfillmentRepository
OrderEventRepository append-only behavior
InventoryReservationService extension point
```

Plan 05 must not redesign Order creation or move reservation to seller confirmation.

---

# Query and Index Review Notes

Before Plan 04 acceptance, run `EXPLAIN (ANALYZE, BUFFERS)` for representative queries and confirm:

```text
coverage radius uses merchant_locations GIST geo index where applicable
selected-area recursive CTE uses location_areas parent/index path efficiently for pilot depth
active Cart lookup uses ux_cart_active_user_location_type
cart items use cart_id index
inventory lock lookup resolves by PK and ordered UUID
active shipping policy uses partial unique/index path
checkout session token lookup uses unique token_hash
buyer Order creation/read preparation indexes are present
```

Do not add speculative indexes without a known query/constraint need.

---

# Security and Privacy Notes

```text
Cart/Checkout/Inventory endpoints: Cache-Control private, no-store
buyer addressId always resolved by ownership
another user's Cart/address -> 404
seller never receives buyer delivery address through Plan 04 seller APIs
checkout snapshots contain private address/contact data but are never put in generic logs/outbox payloads
Outbox ORDER_CREATED payload contains IDs/status only; Plan 07 can fetch authorized data for notification projection
Idempotency stored response may contain order ID/code but must not contain raw checkout token after completion
checkout token is treated as credential-like opaque secret and never logged
```

---

# Explicit Plan 04 Non-Goals

```text
Order confirm/reject/cancel commands
Order canonical detail/allowedActions
Order expiration/auto-completion
reservation release/commit-on-sale
seller delivery progress
pickup ready/no-show
service performed/issue handling
NeedRequest/NeedResponse and NEED_REQUEST checkout source
reviews/reputation
notification inbox/SSE dispatcher
moderation/settings resolution
payment verification/escrow/refunds
service booking slots
courier/runner marketplace
real-time GPS tracking
multi-location Order
split shipment
partial fulfillment
external message broker
```

---

# Execution Handoff

After this plan is saved to `docs/superpowers/plans/2026-08-08-covie-checkout-inventory.md`, implementation has two supported modes:

1. **Subagent-Driven (recommended)** — use `superpowers:subagent-driven-development`, one fresh implementation worker per task, review between tasks.
2. **Inline Execution** — use `superpowers:executing-plans`, execute task batches with explicit checkpoints.

Do not begin Plan 05 implementation until the Plan 04 Acceptance Checklist has fresh evidence.

