# CoVie Plan 05 — Orders & Fulfillment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Closed — locally verified

> **Execution closure — 2026-08-25:** Implemented and locally verified. The post-confirmation merchant-cancel carry-over is covered by unit and HTTP/integration tests. The task checklist below is retained as the original implementation specification, not a live completion tracker. See `docs/CoVie-plan-closure-audit-2026-08-24.md`.

**Goal:** Implement the complete CoVie V1 Order lifecycle on top of Plan 04's real `PENDING_CONFIRMATION` Order foundation: authorization-aware read models, explicit state commands, cancellation, Product delivery/pickup, Service completion, issues/no-show, scheduled transitions, reservation release/commit, append-only events, outbox intents, and minimal buyer/seller web flows.

**Architecture:** Keep Orders inside the Spring Boot modular monolith. Every state-changing command enters through one canonical `/api/v1/orders/{orderId}/...` command surface, executes inside an idempotent transaction, locks the Order row first, revalidates actor + current state, performs inventory/cancellation/issue side effects, appends an immutable `order_events` record, and writes an outbox intent atomically. REST/database remain authoritative; Plan 07 will dispatch outbox events to Notifications/SSE and Plan 08 will replace bootstrap order-policy properties with dynamic `SystemSetting` resolution.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Data JPA, PostgreSQL 18, Flyway, Testcontainers/PostGIS, Jackson, Jakarta Validation, springdoc/OpenAPI, Next.js 16.2, TypeScript, generated API client, Vitest/Playwright where already established by Plan 01.

## Global Constraints

- Plan 01–04 are prerequisites and are not redesigned here.
- Flyway is sequential with `outOfOrder=false`; Plan 05 starts at `V010` and never edits applied `V001`–`V009`.
- Plan 04 already creates real `orders`, `order_items`, `product_fulfillments`, `service_fulfillments`, `order_events`, ACTIVE tracked-inventory reservations, and initial `ORDER_CREATED` outbox intent.
- Never move inventory reservation from Checkout Commit to seller confirmation.
- The canonical V1 Order status set is: `PENDING_CONFIRMATION`, `CONFIRMED`, `OUT_FOR_DELIVERY`, `READY_FOR_PICKUP`, `AWAITING_BUYER_CONFIRMATION`, `CANCELLATION_REQUESTED`, `DELIVERY_ISSUE_REPORTED`, `SERVICE_ISSUE_REPORTED`, `BUYER_NO_SHOW`, `NO_SHOW_CONTESTED`, `COMPLETED`, `EXPIRED`, `REJECTED_BY_SELLER`, `CANCELLED_BEFORE_CONFIRMATION`, `CANCELLED_BY_BUYER_AFTER_CONFIRMATION`, `CANCELLED_BY_SELLER_AFTER_CONFIRMATION`, `CLOSED_WITH_ISSUE`.
- `REJECTED_BY_SELLER` is canonical for both PRODUCT seller and SERVICE provider; do not introduce the older `REJECTED_BY_PROVIDER` storage/API state.
- Clients never PATCH `orders.status`; all lifecycle changes are explicit command endpoints.
- `COMPLETED` is terminal and irreversible.
- Buyer may confirm Product received or Service completed directly from `CONFIRMED`; missed seller/provider progress clicks must not block completion.
- Buyer cancellation request is allowed only after confirmation and before seller/provider has declared delivery/service completion; preserve `previous_order_status` and return there on rejection.
- Delivery issue is allowed only after seller marked delivered. Service issue is allowed only after provider marked performed.
- Issues stop normal auto-completion. Long unresolved issue/no-show contest closes as `CLOSED_WITH_ISSUE`.
- V1 has no transaction-dispute arbitration and no admin force-status command.
- Tracked Product reservation lifecycle is: checkout creates ACTIVE; reject/expire/cancel/no-show releases; normal completion commits + SALE. `DELIVERY_ISSUE_REPORTED -> CLOSED_WITH_ISSUE` commits any still-ACTIVE reservation + SALE because seller already declared delivery; it never auto-restocks. Service has no inventory; no-show contest inventory was already released.
- Once an inventory reservation is `RELEASED`, Plan 05 never revives it to `ACTIVE` or `COMMITTED`. A later completion after `BUYER_NO_SHOW -> NO_SHOW_CONTESTED` records the Order completion but does not synthesize a second stock deduction; seller reconciles with the existing inventory adjustment API if physical stock differs.
- Order commands use `Idempotency-Key` through Plan 03 `IdempotencyExecutor`.
- Same actor + operation + key + body returns the stored result; same key + different body returns conflict.
- Every lifecycle transition appends `order_events`; normal application code never updates/deletes old events.
- Every meaningful lifecycle transition writes an `outbox_events` intent in the same database transaction. Plan 05 does not dispatch notifications/SSE; Plan 07 owns dispatch/consumption.
- Merchant authorization is DB-resolved on every request using Plan 03 `MerchantAccessPolicy`; STAFF remains location-scoped.
- Private Order existence is hidden with 404 when actor cannot view it. A visible Order with a forbidden action returns 403 `ORDER_ACTION_NOT_ALLOWED`.
- State races return 409 `ORDER_STATE_CONFLICT`; commands must re-read/lock current state inside the transaction.
- Order command responses are small `OrderCommandResult` DTOs; read endpoints use dedicated Order detail/list DTOs; JPA entities are never serialized.
- Money remains integer minor units + ISO currency. Timestamps are ISO-8601 UTC.
- No external network call is allowed inside an Order mutation transaction.
- Redis is never authoritative for state transitions, scheduler correctness, inventory outcomes, or idempotency.
- Scheduled lifecycle work uses PostgreSQL locking/state checks and calls application/domain services; no scheduler directly updates business status with arbitrary SQL.
- Plan 06 later adds Need foreign keys + Need `FULFILLED` reaction. Plan 05 only emits stable `ORDER_CONFIRMED` outbox/domain intent for Need-aware work later.
- Plan 07 later computes reputation/review eligibility and sends notifications. Plan 05 only emits the source events/outbox intents.
- Plan 08 later replaces bootstrap timeout properties with scoped `SystemSetting`; Plan 05 must consume an `OrderPolicyProvider` interface rather than read configuration directly in controllers/state-machine code.

---

# Inputs and Dependency Corrections

Plan 04 ends at:

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
```

Plan 04's Order creation boundary is preserved:

```java
@Transactional(propagation = Propagation.MANDATORY)
public CreatedOrder createPending(RevalidatedCheckout checkout)
```

Plan 05 may rely on these established interfaces/classes:

```text
vn.covie.shared.id.UuidV7Generator
vn.covie.shared.time.AppClock
vn.covie.shared.security.CurrentActorProvider
vn.covie.shared.idempotency.IdempotencyExecutor
vn.covie.shared.outbox.OutboxEventWriter
vn.covie.shared.outbox.OutboxEventIntent
vn.covie.merchants.security.MerchantAccessPolicy
vn.covie.merchants.security.MerchantCapability
vn.covie.inventory.application.InventoryLockService
vn.covie.inventory.application.InventoryReservationService
vn.covie.orders.application.OrderCreationService
vn.covie.orders.model.OrderEntity
vn.covie.orders.model.OrderItemEntity
vn.covie.orders.model.ProductFulfillmentEntity
vn.covie.orders.model.ServiceFulfillmentEntity
vn.covie.orders.model.OrderEventEntity
vn.covie.orders.persistence.OrderRepository
vn.covie.orders.persistence.OrderItemRepository
vn.covie.orders.persistence.ProductFulfillmentRepository
vn.covie.orders.persistence.ServiceFulfillmentRepository
vn.covie.orders.persistence.OrderEventRepository
```

Plan 05 adds the following stable boundary for later plans:

```text
vn.covie.orders.application.OrderReadService
vn.covie.orders.application.OrderCommandService
vn.covie.orders.application.OrderCompletionService
vn.covie.orders.application.OrderPolicyProvider
vn.covie.orders.application.OrderAllowedActionService
vn.covie.orders.application.OrderTransitionService
vn.covie.orders.application.OrderScheduledTransitionService
vn.covie.orders.security.OrderAccessPolicy
vn.covie.orders.model.OrderStatus
vn.covie.orders.model.OrderAction
vn.covie.orders.model.OrderEventType
```

---

# Repository Map Added by Plan 05

```text
apps/api/src/main/java/vn/covie/
├── merchants/
│   └── security/
│       └── MerchantCapability.java                  # extend VIEW_ORDERS, PROCESS_ORDERS
├── inventory/
│   └── application/
│       └── InventoryReservationService.java         # extend release/commit outcomes
└── orders/
    ├── api/
    │   ├── OrderController.java
    │   ├── MeOrderController.java
    │   ├── MerchantOrderController.java
    │   └── dto/
    │       ├── OrderCommandResult.java
    │       ├── OrderDetailResponse.java
    │       ├── OrderSummaryResponse.java
    │       ├── OrderTimelineEventResponse.java
    │       ├── OrderPageResponse.java
    │       ├── OrderPartyResponse.java
    │       ├── OrderPricingResponse.java
    │       ├── OrderFulfillmentResponse.java
    │       ├── OrderCancellationResponse.java
    │       ├── OrderIssueResponse.java
    │       ├── ReasonRequest.java
    │       ├── CancelOrderRequest.java
    │       ├── CancellationRequestBody.java
    │       └── CancellationResponseBody.java
    ├── application/
    │   ├── OrderReadService.java
    │   ├── OrderCommandService.java
    │   ├── OrderTransitionService.java
    │   ├── OrderCompletionService.java
    │   ├── OrderCancellationService.java
    │   ├── OrderIssueService.java
    │   ├── OrderEventService.java
    │   ├── OrderAllowedActionService.java
    │   ├── OrderPolicyProvider.java
    │   ├── ConfigOrderPolicyProvider.java
    │   ├── OrderPolicySnapshot.java
    │   ├── OrderScheduledTransitionService.java
    │   ├── OrderScheduler.java
    │   ├── OrderCursorCodec.java
    │   ├── OrderMapper.java
    │   └── LockedOrder.java
    ├── security/
    │   ├── OrderAccessPolicy.java
    │   ├── OrderAccess.java
    │   └── OrderActorType.java
    ├── model/
    │   ├── OrderStatus.java                         # extend Plan 04 enum
    │   ├── OrderAction.java
    │   ├── OrderEventType.java                      # extend Plan 04 enum
    │   ├── OrderActorContext.java
    │   ├── CancellationRequestStatus.java
    │   ├── OrderCancellationRequestEntity.java
    │   ├── OrderIssueType.java
    │   ├── OrderIssueStatus.java
    │   └── OrderIssueEntity.java
    └── persistence/
        ├── OrderRepository.java                     # extend lock/list/due queries
        ├── OrderCancellationRequestRepository.java
        └── OrderIssueRepository.java

apps/api/src/main/resources/db/migration/
└── V010__order_lifecycle.sql

apps/api/src/test/java/vn/covie/orders/
├── persistence/Plan05SchemaMigrationTest.java
├── security/OrderAccessPolicyIntegrationTest.java
├── application/OrderAllowedActionServiceTest.java
├── application/OrderReadServiceIntegrationTest.java
├── application/OrderConfirmRejectIntegrationTest.java
├── application/OrderCancellationIntegrationTest.java
├── application/ProductDeliveryOrderIntegrationTest.java
├── application/ProductPickupOrderIntegrationTest.java
├── application/ServiceOrderIntegrationTest.java
├── application/OrderIssueIntegrationTest.java
├── application/OrderCompletionIntegrationTest.java
├── application/OrderScheduledTransitionIntegrationTest.java
├── application/OrderCommandIdempotencyIntegrationTest.java
├── application/OrderConcurrencyIntegrationTest.java
└── api/OrderControllerIntegrationTest.java

apps/web/
├── app/(buyer)/orders/page.tsx
├── app/(buyer)/orders/[orderId]/page.tsx
├── app/(seller)/seller/[merchantId]/orders/page.tsx
├── app/(seller)/seller/[merchantId]/orders/[orderId]/page.tsx
└── features/orders/
    ├── order-api.ts
    ├── order-actions.tsx
    ├── order-status.tsx
    ├── order-timeline.tsx
    └── order-detail.tsx
```

---

# State Machine Locked by Plan 05

## Product — Seller Delivery

```text
PENDING_CONFIRMATION
├── merchant CONFIRM               -> CONFIRMED
├── merchant REJECT                -> REJECTED_BY_SELLER
├── buyer CANCEL                   -> CANCELLED_BEFORE_CONFIRMATION
└── system timeout                 -> EXPIRED

CONFIRMED
├── merchant MARK_OUT_FOR_DELIVERY -> OUT_FOR_DELIVERY
├── merchant MARK_DELIVERED        -> AWAITING_BUYER_CONFIRMATION
├── buyer CONFIRM_RECEIVED         -> COMPLETED
├── buyer REQUEST_CANCELLATION     -> CANCELLATION_REQUESTED
└── merchant CANCEL                -> CANCELLED_BY_SELLER_AFTER_CONFIRMATION

OUT_FOR_DELIVERY
├── merchant MARK_DELIVERED        -> AWAITING_BUYER_CONFIRMATION
├── buyer CONFIRM_RECEIVED         -> COMPLETED
├── buyer REQUEST_CANCELLATION     -> CANCELLATION_REQUESTED
└── merchant CANCEL                -> CANCELLED_BY_SELLER_AFTER_CONFIRMATION

CANCELLATION_REQUESTED
├── merchant ACCEPT_CANCELLATION   -> CANCELLED_BY_BUYER_AFTER_CONFIRMATION
└── merchant REJECT_CANCELLATION   -> previous_order_status

AWAITING_BUYER_CONFIRMATION
├── buyer CONFIRM_RECEIVED         -> COMPLETED
├── buyer REPORT_DELIVERY_ISSUE    -> DELIVERY_ISSUE_REPORTED
└── system auto-complete           -> COMPLETED

DELIVERY_ISSUE_REPORTED
├── buyer CONFIRM_RECEIVED         -> COMPLETED
└── system issue timeout           -> CLOSED_WITH_ISSUE
```

## Product — Pickup

```text
PENDING_CONFIRMATION
├── merchant CONFIRM               -> CONFIRMED
├── merchant REJECT                -> REJECTED_BY_SELLER
├── buyer CANCEL                   -> CANCELLED_BEFORE_CONFIRMATION
└── system timeout                 -> EXPIRED

CONFIRMED
├── merchant MARK_READY_FOR_PICKUP -> READY_FOR_PICKUP
├── buyer CONFIRM_RECEIVED         -> COMPLETED
├── buyer REQUEST_CANCELLATION     -> CANCELLATION_REQUESTED
└── merchant CANCEL                -> CANCELLED_BY_SELLER_AFTER_CONFIRMATION

READY_FOR_PICKUP
├── buyer CONFIRM_RECEIVED         -> COMPLETED
├── buyer REQUEST_CANCELLATION     -> CANCELLATION_REQUESTED
├── merchant CANCEL                -> CANCELLED_BY_SELLER_AFTER_CONFIRMATION
└── merchant REPORT_BUYER_NO_SHOW  -> BUYER_NO_SHOW only after grace

BUYER_NO_SHOW
└── buyer CONTEST_NO_SHOW          -> NO_SHOW_CONTESTED

NO_SHOW_CONTESTED
├── buyer CONFIRM_RECEIVED         -> COMPLETED
└── system issue timeout           -> CLOSED_WITH_ISSUE
```

## Service

```text
PENDING_CONFIRMATION
├── merchant CONFIRM               -> CONFIRMED
├── merchant REJECT                -> REJECTED_BY_SELLER
├── buyer CANCEL                   -> CANCELLED_BEFORE_CONFIRMATION
└── system timeout                 -> EXPIRED

CONFIRMED
├── provider MARK_SERVICE_PERFORMED -> AWAITING_BUYER_CONFIRMATION
├── buyer CONFIRM_COMPLETED         -> COMPLETED
├── buyer REQUEST_CANCELLATION      -> CANCELLATION_REQUESTED
└── provider CANCEL                 -> CANCELLED_BY_SELLER_AFTER_CONFIRMATION

CANCELLATION_REQUESTED
├── provider ACCEPT_CANCELLATION    -> CANCELLED_BY_BUYER_AFTER_CONFIRMATION
└── provider REJECT_CANCELLATION    -> CONFIRMED

AWAITING_BUYER_CONFIRMATION
├── buyer CONFIRM_COMPLETED         -> COMPLETED
├── buyer REPORT_SERVICE_ISSUE      -> SERVICE_ISSUE_REPORTED
└── system auto-complete            -> COMPLETED

SERVICE_ISSUE_REPORTED
├── buyer CONFIRM_COMPLETED         -> COMPLETED
└── system issue timeout            -> CLOSED_WITH_ISSUE
```

`COMPLETED`, `EXPIRED`, `REJECTED_BY_SELLER`, `CANCELLED_*`, and `CLOSED_WITH_ISSUE` are terminal for client business commands.

---

# Transaction and Lock Order

All client commands follow this transaction sequence:

```text
IdempotencyExecutor advisory lock
        ↓
DB transaction
        ↓
lock orders row by orderId FOR UPDATE
        ↓
authorization against locked Order + current actor
        ↓
validate current status/order type/fulfillment/time policy
        ↓
lock dependent cancellation/issue rows if needed
        ↓
lock inventory balances by sorted locationVariantId if inventory outcome needed
        ↓
mutate Order + fulfillment/cancellation/issue
        ↓
mutate reservation/balance/ledger if required
        ↓
append order_event
        ↓
append outbox intent
        ↓
commit
```

Rules:

```text
never lock inventory before the Order row in Plan 05 lifecycle commands
never call another network service while locks are held
never call controller-level authorization outside the transaction as the only check
never read current status before locking and assume it stayed current
```

---

### Task 1: Add V010 Order Lifecycle Schema Migration

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V010__order_lifecycle.sql`
- Create: `apps/api/src/test/java/vn/covie/orders/persistence/Plan05SchemaMigrationTest.java`

**Interfaces:**
- Consumes: V009 Order creation schema.
- Produces: full V1 status/event constraints, cancellation/issues tables, lifecycle indexes; Plan 06 must start at V011.

- [ ] **Step 1: Write migration test before creating V010**

```java
package vn.covie.orders.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class Plan05SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired DataSource dataSource;

    @Test
    void v010CreatesLifecycleTablesAndExpandsConstraints() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(tableExists(c, "order_cancellation_requests")).isTrue();
            assertThat(tableExists(c, "order_issues")).isTrue();
            assertThat(columnLength(c, "order_events", "event_type")).isGreaterThanOrEqualTo(80);
            assertThat(constraintExists(c, "orders", "ck_order_status_v010")).isTrue();
        }
    }

    private boolean tableExists(Connection c, String table) throws Exception {
        try (ResultSet rs = c.getMetaData().getTables(null, null, table, new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private int columnLength(Connection c, String table, String column) throws Exception {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, table, column)) {
            if (!rs.next()) throw new IllegalStateException("missing column");
            return rs.getInt("COLUMN_SIZE");
        }
    }

    private boolean constraintExists(Connection c, String table, String constraint) throws Exception {
        try (var ps = c.prepareStatement("""
                select 1 from pg_constraint pc
                join pg_class t on t.oid = pc.conrelid
                where t.relname = ? and pc.conname = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, constraint);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
}
```

- [ ] **Step 2: Run migration test and verify expected failure**

```bash
cd apps/api
./mvnw -Dtest=Plan05SchemaMigrationTest test
```

Expected: FAIL because V010/tables/full status constraint do not exist.

- [ ] **Step 3: Expand Order status + event storage in V010**

```sql
alter table orders drop constraint ck_order_status_creation;

alter table orders add constraint ck_order_status_v010 check (status in (
    'PENDING_CONFIRMATION',
    'CONFIRMED',
    'OUT_FOR_DELIVERY',
    'READY_FOR_PICKUP',
    'AWAITING_BUYER_CONFIRMATION',
    'CANCELLATION_REQUESTED',
    'DELIVERY_ISSUE_REPORTED',
    'SERVICE_ISSUE_REPORTED',
    'BUYER_NO_SHOW',
    'NO_SHOW_CONTESTED',
    'COMPLETED',
    'EXPIRED',
    'REJECTED_BY_SELLER',
    'CANCELLED_BEFORE_CONFIRMATION',
    'CANCELLED_BY_BUYER_AFTER_CONFIRMATION',
    'CANCELLED_BY_SELLER_AFTER_CONFIRMATION',
    'CLOSED_WITH_ISSUE'
));

alter table product_fulfillments
    add column out_for_delivery_at timestamptz null,
    add column auto_complete_at timestamptz null,
    add column no_show_eligible_at timestamptz null;

alter table service_fulfillments
    add column auto_complete_at timestamptz null;

alter table order_events drop constraint ck_order_event_type_v009;
alter table order_events alter column event_type type varchar(80);
alter table order_events
    add column actor_context varchar(30) null,
    add column from_status varchar(60) null,
    add column to_status varchar(60) null,
    add column reason_code varchar(60) null,
    add column note text null;

alter table order_events add constraint ck_order_event_actor_context check (actor_context is null or actor_context in ('BUYER','MERCHANT','SYSTEM','ADMIN'));

alter table order_events add constraint ck_order_event_type_v010 check (event_type in (
    'ORDER_CREATED',
    'SELLER_CONFIRMED',
    'SELLER_REJECTED',
    'BUYER_CANCELLED_BEFORE_CONFIRMATION',
    'BUYER_REQUESTED_CANCELLATION',
    'SELLER_ACCEPTED_CANCELLATION',
    'SELLER_REJECTED_CANCELLATION',
    'SELLER_CANCELLED_AFTER_CONFIRMATION',
    'SELLER_MARKED_OUT_FOR_DELIVERY',
    'SELLER_MARKED_DELIVERED',
    'SELLER_MARKED_READY_FOR_PICKUP',
    'SELLER_MARKED_BUYER_NO_SHOW',
    'BUYER_CONTESTED_NO_SHOW',
    'PROVIDER_MARKED_PERFORMED',
    'BUYER_CONFIRMED_RECEIVED',
    'BUYER_CONFIRMED_SERVICE_COMPLETED',
    'BUYER_REPORTED_NOT_RECEIVED',
    'BUYER_REPORTED_SERVICE_NOT_COMPLETED',
    'ORDER_EXPIRED',
    'ORDER_AUTO_COMPLETED',
    'ORDER_CLOSED_WITH_ISSUE'
));
```

Do not delete/transform the existing V009 `ORDER_CREATED` rows; new columns remain null for historical creation events where not populated.

- [ ] **Step 4: Create cancellation request table**

```sql
create table order_cancellation_requests (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete restrict,
    requested_by_user_id uuid not null references users(id),
    reason_code varchar(60) not null,
    reason_text text null,
    request_status varchar(20) not null,
    previous_order_status varchar(60) not null,
    responded_by_user_id uuid null references users(id),
    response_note text null,
    created_at timestamptz not null,
    responded_at timestamptz null,
    constraint ck_order_cancel_request_status check (request_status in ('PENDING','ACCEPTED','REJECTED')),
    constraint ck_order_cancel_previous_status check (previous_order_status in ('CONFIRMED','OUT_FOR_DELIVERY','READY_FOR_PICKUP')),
    constraint ck_order_cancel_response_consistency check (
        (request_status = 'PENDING' and responded_by_user_id is null and responded_at is null)
        or
        (request_status in ('ACCEPTED','REJECTED') and responded_by_user_id is not null and responded_at is not null)
    )
);

create unique index ux_order_cancel_one_pending
    on order_cancellation_requests(order_id)
    where request_status = 'PENDING';

create index ix_order_cancel_order_created
    on order_cancellation_requests(order_id, created_at desc);
```

- [ ] **Step 5: Create Order issue table**

```sql
create table order_issues (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete restrict,
    issue_type varchar(50) not null,
    reported_by_user_id uuid not null references users(id),
    reason_text text null,
    issue_status varchar(30) not null,
    opened_at timestamptz not null,
    close_after_at timestamptz not null,
    resolved_at timestamptz null,
    closed_at timestamptz null,
    constraint ck_order_issue_type check (issue_type in (
        'DELIVERY_NOT_RECEIVED',
        'SERVICE_NOT_COMPLETED',
        'BUYER_NO_SHOW_CONTESTED'
    )),
    constraint ck_order_issue_status check (issue_status in (
        'OPEN',
        'RESOLVED_BY_PARTIES',
        'CLOSED_BY_TIMEOUT'
    )),
    constraint ck_order_issue_deadline check (close_after_at >= opened_at),
    constraint ck_order_issue_resolution check (
        (issue_status = 'OPEN' and resolved_at is null and closed_at is null)
        or (issue_status = 'RESOLVED_BY_PARTIES' and resolved_at is not null and closed_at is null)
        or (issue_status = 'CLOSED_BY_TIMEOUT' and closed_at is not null)
    )
);

create unique index ux_order_issue_one_open
    on order_issues(order_id)
    where issue_status = 'OPEN';

create index ix_order_issue_opened
    on order_issues(issue_status, opened_at, order_id);
```

- [ ] **Step 6: Add lifecycle query indexes**

```sql
create index ix_orders_buyer_created
    on orders(buyer_user_id, created_at desc, id desc);

create index ix_orders_merchant_created
    on orders(merchant_profile_id, merchant_location_id, created_at desc, id desc);

create index ix_orders_pending_expiry
    on orders(confirmation_expires_at, id)
    where status = 'PENDING_CONFIRMATION' and confirmation_expires_at is not null;

create index if not exists ix_order_items_order_id
    on order_items(order_id, id);

create index if not exists ix_order_events_order_created
    on order_events(order_id, created_at, id);

create index ix_product_fulfillment_auto_complete
    on product_fulfillments(auto_complete_at, order_id)
    where auto_complete_at is not null;

create index ix_product_fulfillment_no_show
    on product_fulfillments(no_show_eligible_at, order_id)
    where no_show_eligible_at is not null;

create index ix_service_fulfillment_auto_complete
    on service_fulfillments(auto_complete_at, order_id)
    where auto_complete_at is not null;

create index ix_order_issue_close_after
    on order_issues(close_after_at, order_id)
    where issue_status = 'OPEN';
```

- [ ] **Step 7: Run migration test from empty DB**

```bash
./mvnw -Dtest=Plan05SchemaMigrationTest test
```

Expected: PASS with Flyway applying V001 through V010 in order.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/resources/db/migration/V010__order_lifecycle.sql apps/api/src/test/java/vn/covie/orders/persistence/Plan05SchemaMigrationTest.java
git commit -m "feat(order): add lifecycle persistence schema"
```

---

### Task 2: Extend Order Enums, Entities and Locking Repositories

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/orders/model/OrderStatus.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/OrderEventType.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/OrderEventEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/OrderEntity.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderAction.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderActorContext.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/CancellationRequestStatus.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderCancellationRequestEntity.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderIssueType.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderIssueStatus.java`
- Create: `apps/api/src/main/java/vn/covie/orders/model/OrderIssueEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/persistence/OrderRepository.java`
- Create: `apps/api/src/main/java/vn/covie/orders/persistence/OrderCancellationRequestRepository.java`
- Create: `apps/api/src/main/java/vn/covie/orders/persistence/OrderIssueRepository.java`
- Test: `apps/api/src/test/java/vn/covie/orders/persistence/OrderPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: Plan 04 Order entities.
- Produces: complete lifecycle model + mandatory lock-by-ID primitives.

- [ ] **Step 1: Extend enums exactly**

```java
public enum OrderStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    OUT_FOR_DELIVERY,
    READY_FOR_PICKUP,
    AWAITING_BUYER_CONFIRMATION,
    CANCELLATION_REQUESTED,
    DELIVERY_ISSUE_REPORTED,
    SERVICE_ISSUE_REPORTED,
    BUYER_NO_SHOW,
    NO_SHOW_CONTESTED,
    COMPLETED,
    EXPIRED,
    REJECTED_BY_SELLER,
    CANCELLED_BEFORE_CONFIRMATION,
    CANCELLED_BY_BUYER_AFTER_CONFIRMATION,
    CANCELLED_BY_SELLER_AFTER_CONFIRMATION,
    CLOSED_WITH_ISSUE
}
```

```java
public enum OrderAction {
    CONFIRM,
    REJECT,
    CANCEL,
    REQUEST_CANCELLATION,
    ACCEPT_CANCELLATION,
    REJECT_CANCELLATION,
    MARK_OUT_FOR_DELIVERY,
    MARK_DELIVERED,
    MARK_READY_FOR_PICKUP,
    REPORT_BUYER_NO_SHOW,
    CONTEST_NO_SHOW,
    MARK_SERVICE_PERFORMED,
    CONFIRM_RECEIVED,
    CONFIRM_COMPLETED,
    REPORT_DELIVERY_ISSUE,
    REPORT_SERVICE_ISSUE
}
```

```java
public enum OrderActorContext { BUYER, MERCHANT, SYSTEM, ADMIN }

public enum OrderEventType {
    ORDER_CREATED,
    SELLER_CONFIRMED,
    SELLER_REJECTED,
    BUYER_CANCELLED_BEFORE_CONFIRMATION,
    BUYER_REQUESTED_CANCELLATION,
    SELLER_ACCEPTED_CANCELLATION,
    SELLER_REJECTED_CANCELLATION,
    SELLER_CANCELLED_AFTER_CONFIRMATION,
    SELLER_MARKED_OUT_FOR_DELIVERY,
    SELLER_MARKED_DELIVERED,
    SELLER_MARKED_READY_FOR_PICKUP,
    SELLER_MARKED_BUYER_NO_SHOW,
    BUYER_CONTESTED_NO_SHOW,
    PROVIDER_MARKED_PERFORMED,
    BUYER_CONFIRMED_RECEIVED,
    BUYER_CONFIRMED_SERVICE_COMPLETED,
    BUYER_REPORTED_NOT_RECEIVED,
    BUYER_REPORTED_SERVICE_NOT_COMPLETED,
    ORDER_EXPIRED,
    ORDER_AUTO_COMPLETED,
    ORDER_CLOSED_WITH_ISSUE
}
```

- [ ] **Step 2: Map cancellation and issue entities without cascade-delete**

Use explicit FK IDs. Do not add `CascadeType.REMOVE` to Order relationships.

```java
@Entity
@Table(name = "order_cancellation_requests")
public class OrderCancellationRequestEntity {
    @Id private UUID id;
    private UUID orderId;
    private UUID requestedByUserId;
    private String reasonCode;
    private String reasonText;
    @Enumerated(EnumType.STRING) private CancellationRequestStatus requestStatus;
    @Enumerated(EnumType.STRING) private OrderStatus previousOrderStatus;
    private UUID respondedByUserId;
    private String responseNote;
    private Instant createdAt;
    private Instant respondedAt;
}
```

```java
@Entity
@Table(name = "order_issues")
public class OrderIssueEntity {
    @Id private UUID id;
    private UUID orderId;
    @Enumerated(EnumType.STRING) private OrderIssueType issueType;
    private UUID reportedByUserId;
    private String reasonText;
    @Enumerated(EnumType.STRING) private OrderIssueStatus issueStatus;
    private Instant openedAt;
    private Instant closeAfterAt;
    private Instant resolvedAt;
    private Instant closedAt;
}
```

- [ ] **Step 3: Extend Order event mapping with lifecycle fields**

`OrderEventEntity` maps:

```text
actorContext
fromStatus
toStatus
reasonCode
note
metadata
```

`metadata` stays JSONB and never contains raw auth tokens or mutable entity serialization.

- [ ] **Step 4: Add lock-by-ID query**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select o from OrderEntity o where o.id = :id")
Optional<OrderEntity> lockById(@Param("id") UUID id);
```

Also add read query without lock:

```java
Optional<OrderEntity> findById(UUID id);
```

- [ ] **Step 5: Add cancellation/issue lock queries**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select r from OrderCancellationRequestEntity r
    where r.orderId = :orderId and r.requestStatus = vn.covie.orders.model.CancellationRequestStatus.PENDING
    """)
Optional<OrderCancellationRequestEntity> lockPendingByOrderId(UUID orderId);
```

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select i from OrderIssueEntity i
    where i.orderId = :orderId and i.issueStatus = vn.covie.orders.model.OrderIssueStatus.OPEN
    """)
Optional<OrderIssueEntity> lockOpenByOrderId(UUID orderId);
```

- [ ] **Step 6: Test pessimistic lock behavior with two transactions**

Test one transaction locks Order A while a second transaction attempting `lockById(A)` blocks until the first releases, then sees the updated status.

- [ ] **Step 7: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=OrderPersistenceIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/model apps/api/src/main/java/vn/covie/orders/persistence apps/api/src/test/java/vn/covie/orders/persistence
git commit -m "feat(order): add lifecycle model and locking repositories"
```

---

### Task 3: Add Order Policy Provider and Bootstrap Timeouts

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderPolicySnapshot.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/ConfigOrderPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderLifecycleProperties.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCreationService.java`
- Modify: `apps/api/src/test/java/vn/covie/orders/application/OrderCreationServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/ConfigOrderPolicyProviderTest.java`

**Interfaces:**
- Produces one interface used by state/scheduler logic. Plan 08 can replace/decorate the adapter with scoped settings without changing callers.

- [ ] **Step 1: Define immutable policy snapshot**

```java
public record OrderPolicySnapshot(
        Duration confirmationTimeout,
        Duration autoCompleteGrace,
        Duration pickupNoShowGrace,
        Duration issueCloseTimeout) {

    public OrderPolicySnapshot {
        requirePositive(confirmationTimeout, "confirmationTimeout");
        requirePositive(autoCompleteGrace, "autoCompleteGrace");
        requirePositive(pickupNoShowGrace, "pickupNoShowGrace");
        requirePositive(issueCloseTimeout, "issueCloseTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
```

- [ ] **Step 2: Define provider interface**

```java
public interface OrderPolicyProvider {
    OrderPolicySnapshot resolve(UUID merchantId, UUID merchantLocationId);
}
```

- [ ] **Step 3: Configure bootstrap defaults**

```yaml
covie:
  orders:
    confirmation-timeout: PT30M
    auto-complete-grace: PT24H
    pickup-no-show-grace: PT2H
    issue-close-timeout: P2D
```

These are technical bootstrap values, not permanent product policy. Plan 08 moves effective values to `SystemSetting` resolution.

When an Order enters a timed state, snapshot the effective deadline into persistence. Later setting changes do not silently move deadlines of already-entered states.

- [ ] **Step 4: Activate confirmation expiry in `OrderCreationService`**

At creation time:

```java
OrderPolicySnapshot policy = orderPolicyProvider.resolve(checkout.merchantId(), checkout.merchantLocationId());
Instant createdAt = clock.now();
Instant confirmationExpiresAt = createdAt.plus(policy.confirmationTimeout());
```

Persist `confirmation_expires_at` on every new V1 Order. Remove the Plan 04 temporary behavior that allowed new Orders to keep this null.

- [ ] **Step 5: Test deadline snapshot behavior**

Test all four positive durations, invalid/non-positive configuration, and an Order creation assertion:

```text
createdAt = 10:00Z
confirmationTimeout = 30m
confirmationExpiresAt = 10:30Z
```

Changing the configured timeout after the Order is created must not mutate that existing deadline.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=ConfigOrderPolicyProviderTest,OrderCreationServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/application apps/api/src/main/resources/application.yml apps/api/src/test/java/vn/covie/orders/application/ConfigOrderPolicyProviderTest.java apps/api/src/test/java/vn/covie/orders/application/OrderCreationServiceIntegrationTest.java
git commit -m "feat(order): add lifecycle policy provider"
```

---

### Task 4: Extend Merchant Capabilities and Implement Order Access Policy

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/merchants/security/MerchantCapability.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/security/MerchantAccessPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/orders/security/OrderActorType.java`
- Create: `apps/api/src/main/java/vn/covie/orders/security/OrderAccess.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/security/MerchantLocationScope.java`
- Create: `apps/api/src/main/java/vn/covie/orders/security/OrderAccessPolicy.java`
- Test: `apps/api/src/test/java/vn/covie/orders/security/OrderAccessPolicyIntegrationTest.java`
- Modify: `apps/api/src/test/java/vn/covie/merchants/security/MerchantAccessPolicyIntegrationTest.java`

**Interfaces:**
- Consumes: Plan 02 actor/global role, Plan 03 membership/location authorization.
- Produces: authorization-aware Order identity shared by reads and commands.

- [ ] **Step 1: Extend merchant capabilities**

```java
public enum MerchantCapability {
    VIEW_MERCHANT,
    EDIT_PROFILE,
    MANAGE_MEMBERS,
    GRANT_OWNER,
    MANAGE_LOCATIONS,
    MANAGE_HOURS_CONTACTS,
    MANAGE_CATALOG_STRUCTURE,
    PUBLISH_CATALOG,
    MANAGE_LOCATION_OFFER,
    UPLOAD_MERCHANT_MEDIA,
    VIEW_ORDERS,
    PROCESS_ORDERS
}
```

OWNER/MANAGER receive both Order capabilities. STAFF receives them only subject to Plan 03 location scoping; `requireLocation(...)` remains the location gate.

- [ ] **Step 2: Define Order access result**

```java
public enum OrderActorType { BUYER, MERCHANT, ADMIN }

public record OrderAccess(
        UUID actorUserId,
        OrderActorType actorType,
        UUID orderId,
        UUID merchantId,
        UUID merchantLocationId) {}

public record MerchantLocationScope(boolean allLocations, Set<UUID> locationIds) {
    public static MerchantLocationScope all() { return new MerchantLocationScope(true, Set.of()); }
    public static MerchantLocationScope restricted(Set<UUID> ids) { return new MerchantLocationScope(false, Set.copyOf(ids)); }
}
```

- [ ] **Step 3: Implement authorization against an already loaded/locked Order**

```java
public OrderAccess requireVisible(OrderEntity order) {
    CurrentActor actor = actors.currentActor();

    if (actor.userId().equals(order.buyerUserId())) {
        return new OrderAccess(actor.userId(), OrderActorType.BUYER,
                order.id(), order.merchantProfileId(), order.merchantLocationId());
    }

    if (actor.globalRoles().contains(SystemRole.ADMIN)) {
        return new OrderAccess(actor.userId(), OrderActorType.ADMIN,
                order.id(), order.merchantProfileId(), order.merchantLocationId());
    }

    try {
        merchantAccess.requireLocation(
                order.merchantProfileId(),
                order.merchantLocationId(),
                MerchantCapability.VIEW_ORDERS);
        return new OrderAccess(actor.userId(), OrderActorType.MERCHANT,
                order.id(), order.merchantProfileId(), order.merchantLocationId());
    } catch (ApiException e) {
        if (e.status() == HttpStatus.FORBIDDEN || e.status() == HttpStatus.NOT_FOUND) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
        }
        throw e;
    }
}
```

For merchant command processing, use `MerchantCapability.PROCESS_ORDERS` and preserve 404 for wrong merchant/location membership.

Add explicit command-role gates so a visible-but-wrong actor gets 403 rather than being reclassified as invisible:

```java
public OrderAccess requireBuyer(OrderEntity order) {
    OrderAccess access = requireVisible(order);
    if (access.actorType() != OrderActorType.BUYER) {
        throw new ApiException(ErrorCode.ORDER_ACTION_NOT_ALLOWED, HttpStatus.FORBIDDEN,
                "Order action is not allowed.");
    }
    return access;
}

public OrderAccess requireMerchantProcessor(OrderEntity order) {
    OrderAccess access = requireVisible(order);
    if (access.actorType() != OrderActorType.MERCHANT) {
        throw new ApiException(ErrorCode.ORDER_ACTION_NOT_ALLOWED, HttpStatus.FORBIDDEN,
                "Order action is not allowed.");
    }
    merchantAccess.requireLocation(order.merchantProfileId(), order.merchantLocationId(),
            MerchantCapability.PROCESS_ORDERS);
    return access;
}
```

For merchant list reads, extend `MerchantAccessPolicy` with a location-scope helper: OWNER/MANAGER return `MerchantLocationScope.all()`, STAFF return the exact assigned location ID set. `OrderReadService` must filter STAFF lists to that set.

- [ ] **Step 4: Write IDOR matrix tests**

Required cases:

```text
buyer owns Order -> visible
other buyer -> 404
OWNER same merchant/location -> visible
MANAGER same merchant/location -> visible
STAFF assigned location -> visible
STAFF same merchant but not assigned location -> 404
revoked membership -> 404
ADMIN -> visible
buyer invoking seller-only action on visible own order -> later returns 403, not 404
```

- [ ] **Step 5: Run Plan 03 regression + Order access tests**

```bash
./mvnw -Dtest=MerchantAccessPolicyIntegrationTest,OrderAccessPolicyIntegrationTest test
```

- [ ] **Step 6: Commit**

```bash
git add apps/api/src/main/java/vn/covie/merchants/security apps/api/src/main/java/vn/covie/orders/security apps/api/src/test/java/vn/covie/merchants/security apps/api/src/test/java/vn/covie/orders/security
git commit -m "feat(order): authorize buyer and merchant order access"
```

---

### Task 5: Implement Order Event Append Service and Outbox Naming Contract

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderEventService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderOutboxEvents.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/OrderEventEntity.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderEventServiceIntegrationTest.java`

**Interfaces:**
- Produces append-only status history + stable event intents consumed by Plan 06/07.

- [ ] **Step 1: Define one append signature**

```java
@Transactional(propagation = Propagation.MANDATORY)
public OrderEventEntity append(
        OrderEntity order,
        OrderEventType eventType,
        UUID actorUserId,
        OrderActorContext actorContext,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String reasonCode,
        String note,
        Object metadata)
```

- [ ] **Step 2: Define stable outbox event names**

```java
public final class OrderOutboxEvents {
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_REJECTED = "ORDER_REJECTED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_EXPIRED = "ORDER_EXPIRED";
    public static final String ORDER_CANCELLATION_REQUESTED = "ORDER_CANCELLATION_REQUESTED";
    public static final String ORDER_CANCELLATION_RESPONDED = "ORDER_CANCELLATION_RESPONDED";
    public static final String ORDER_FULFILLMENT_UPDATED = "ORDER_FULFILLMENT_UPDATED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String ORDER_ISSUE_REPORTED = "ORDER_ISSUE_REPORTED";
    public static final String ORDER_CLOSED_WITH_ISSUE = "ORDER_CLOSED_WITH_ISSUE";

    private OrderOutboxEvents() {}
}
```

Outbox payload is lightweight and stable:

```java
public record OrderChangedPayload(UUID orderId, String orderCode, OrderStatus status) {}
```

Plan 07 notification workers refetch authoritative Order data instead of treating outbox payload as a full Order snapshot.

- [ ] **Step 3: Test append-only behavior**

Create Order, append two events, assert row count increases and no application repository method exists to delete old events.

- [ ] **Step 4: Run and commit**

```bash
./mvnw -Dtest=OrderEventServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/application apps/api/src/main/java/vn/covie/orders/model/OrderEventEntity.java apps/api/src/test/java/vn/covie/orders/application/OrderEventServiceIntegrationTest.java
git commit -m "feat(order): append lifecycle events and outbox intents"
```

---

### Task 6: Implement Pure Allowed-Action Matrix

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderAllowedActionService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderActionContext.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderAllowedActionServiceTest.java`

**Interfaces:**
- Consumes: actor type, Order type/status, Product fulfillment method, persisted lifecycle deadlines and current time.
- Produces: actor-specific `Set<OrderAction>` for DTOs/UI only; command service still validates independently.

- [ ] **Step 1: Define action context**

```java
public record OrderActionContext(
        OrderActorType actorType,
        OrderType orderType,
        OrderStatus status,
        ProductFulfillmentMethod productFulfillmentMethod,
        Instant confirmationExpiresAt,
        Instant noShowEligibleAt,
        Instant now) {}
```

- [ ] **Step 2: Write exhaustive parameterized buyer matrix tests**

Examples that must exist explicitly:

```java
@ParameterizedTest
@MethodSource("buyerCases")
void buyerAllowedActionsMatchContract(OrderActionContext context, Set<OrderAction> expected) {
    assertThat(service.allowedActions(context)).containsExactlyInAnyOrderElementsOf(expected);
}
```

Include at least:

```text
PENDING before confirmationExpiresAt -> CANCEL
PENDING at/after confirmationExpiresAt -> empty
CONFIRMED PRODUCT delivery -> CONFIRM_RECEIVED + REQUEST_CANCELLATION
OUT_FOR_DELIVERY -> CONFIRM_RECEIVED + REQUEST_CANCELLATION
AWAITING product -> CONFIRM_RECEIVED + REPORT_DELIVERY_ISSUE
DELIVERY_ISSUE_REPORTED -> CONFIRM_RECEIVED
CONFIRMED pickup -> CONFIRM_RECEIVED + REQUEST_CANCELLATION
READY pickup -> CONFIRM_RECEIVED + REQUEST_CANCELLATION
BUYER_NO_SHOW -> CONTEST_NO_SHOW
NO_SHOW_CONTESTED -> CONFIRM_RECEIVED
CONFIRMED service -> CONFIRM_COMPLETED + REQUEST_CANCELLATION
AWAITING service -> CONFIRM_COMPLETED + REPORT_SERVICE_ISSUE
SERVICE_ISSUE_REPORTED -> CONFIRM_COMPLETED
terminal -> empty
```

- [ ] **Step 3: Write seller/provider matrix**

```text
PENDING before confirmationExpiresAt -> CONFIRM + REJECT
PENDING at/after confirmationExpiresAt -> empty
CONFIRMED delivery -> MARK_OUT_FOR_DELIVERY + MARK_DELIVERED + CANCEL
OUT_FOR_DELIVERY -> MARK_DELIVERED + CANCEL
CONFIRMED pickup -> MARK_READY_FOR_PICKUP + CANCEL
READY pickup before grace -> CANCEL only
READY pickup after grace -> CANCEL + REPORT_BUYER_NO_SHOW
CONFIRMED service -> MARK_SERVICE_PERFORMED + CANCEL
CANCELLATION_REQUESTED -> ACCEPT_CANCELLATION + REJECT_CANCELLATION
AWAITING/issue/terminal -> no seller business action in V1
ADMIN -> empty
```

- [ ] **Step 4: Run pure unit tests**

```bash
./mvnw -Dtest=OrderAllowedActionServiceTest test
```

- [ ] **Step 5: Commit**

```bash
git add apps/api/src/main/java/vn/covie/orders/application/OrderAllowedActionService.java apps/api/src/main/java/vn/covie/orders/application/OrderActionContext.java apps/api/src/test/java/vn/covie/orders/application/OrderAllowedActionServiceTest.java
git commit -m "feat(order): calculate actor-specific allowed actions"
```

---

### Task 7: Implement Canonical Order Read Models and Cursor Lists

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderReadService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderMapper.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderCursorCodec.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderDetailResponse.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderTimelineEventResponse.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderPageResponse.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderPartyResponse.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderPricingResponse.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderFulfillmentResponse.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderCancellationResponse.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderIssueResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/persistence/OrderRepository.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderReadServiceIntegrationTest.java`

**Interfaces:**
- Produces canonical authorization-aware Order detail, buyer list and merchant list.

- [ ] **Step 1: Define detail DTO without JPA entities**

```java
public record OrderDetailResponse(
        UUID id,
        String orderCode,
        OrderType type,
        OrderStatus status,
        long version,
        OrderSourceResponse source,
        OrderPartyResponse buyer,
        OrderPartyResponse merchant,
        List<OrderItemResponse> items,
        OrderPricingResponse pricing,
        OrderFulfillmentResponse fulfillment,
        PaymentMethod paymentMethod,
        OrderCancellationResponse cancellation,
        OrderIssueResponse issue,
        List<OrderTimelineEventResponse> timeline,
        Set<OrderAction> allowedActions,
        Instant createdAt,
        Instant confirmedAt,
        Instant completedAt,
        Instant closedAt) {}
```

Sensitive buyer phone/address fields are only populated for BUYER herself, permitted MERCHANT fulfillment actor, or ADMIN. Public endpoints never expose this DTO.

- [ ] **Step 2: Use keyset cursor `(created_at, id)`**

Cursor decoded structure:

```java
public record OrderCursor(Instant createdAt, UUID id) {}
```

Repository buyer query uses:

```sql
where buyer_user_id = :buyerId
  and (:cursorCreatedAt is null
       or created_at < :cursorCreatedAt
       or (created_at = :cursorCreatedAt and id < :cursorId))
order by created_at desc, id desc
limit :limitPlusOne
```

Merchant list additionally filters by `merchant_profile_id` and optional permitted `merchant_location_id` set for STAFF.

- [ ] **Step 3: Build canonical detail with actor filtering**

```text
load Order
requireVisible(Order)
load items
load exactly one fulfillment subtype
load latest cancellation if any
load open/latest issue if any
load ordered events
resolve policy + allowedActions
map contact fields according to access
return DTO
```

- [ ] **Step 4: Test 404/field visibility and cursor determinism**

Required:

```text
buyer sees own delivery address
wrong buyer gets 404
correct merchant location sees fulfillment contact
wrong STAFF location gets 404
merchant list only contains assigned locations for STAFF
same created_at tie ordered by UUID cursor without duplicates/skips
Order Detail contains allowedActions for current actor
```

- [ ] **Step 5: Run and commit**

```bash
./mvnw -Dtest=OrderReadServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/application apps/api/src/main/java/vn/covie/orders/api/dto apps/api/src/main/java/vn/covie/orders/persistence/OrderRepository.java apps/api/src/test/java/vn/covie/orders/application/OrderReadServiceIntegrationTest.java
git commit -m "feat(order): add canonical detail and actor order lists"
```

---

### Task 8: Extend Inventory Reservation Service with Release and Commit Outcomes

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/inventory/application/InventoryReservationService.java`
- Modify: `apps/api/src/main/java/vn/covie/inventory/model/InventoryBalanceEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/inventory/model/InventoryReservationEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/inventory/model/InventoryMovementEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/inventory/persistence/InventoryReservationRepository.java`
- Test: `apps/api/src/test/java/vn/covie/inventory/application/InventoryReservationOutcomeIntegrationTest.java`

**Interfaces:**
- Consumes: ACTIVE reservations created by Plan 04.
- Produces: MANDATORY transaction methods for release/commit with stable lock order.

- [ ] **Step 1: Add repository query for active reservation rows sorted by locationVariantId**

```java
@Query("""
    select r from InventoryReservationEntity r
    where r.orderId = :orderId and r.reservationStatus = vn.covie.inventory.model.InventoryReservationStatus.ACTIVE
    order by r.locationVariantId asc
    """)
List<InventoryReservationEntity> findActiveByOrderIdOrdered(UUID orderId);
```

- [ ] **Step 2: Add release signature**

```java
@Transactional(propagation = Propagation.MANDATORY)
public void releaseActiveForOrder(
        UUID orderId,
        UUID actorUserId,
        String reasonCode,
        Instant now)
```

Implementation:

```text
load ACTIVE reservations ordered by locationVariantId
lock corresponding balances using InventoryLockService in same sorted order
for each reservation:
  balance.releaseReserved(quantity)
  reservation.markReleased(now)
  append RELEASE_RESERVATION movement with order/orderItem/reservation IDs
```

Calling release twice is a safe no-op after the first committed result because there are no ACTIVE reservations left.

- [ ] **Step 3: Add normal commit signature**

```java
@Transactional(propagation = Propagation.MANDATORY)
public void commitActiveForOrder(
        UUID orderId,
        UUID actorUserId,
        Instant now)
```

Implementation for each ACTIVE reservation:

```text
lock balance
reservedQuantity -= quantity
stockOnHand -= quantity
reservation ACTIVE -> COMMITTED
append SALE movement
assert stockOnHand >= reservedQuantity >= 0
```

- [ ] **Step 4: Explicitly handle already-released no-show completion**

`commitActiveForOrder` commits only ACTIVE reservations. It never changes RELEASED to COMMITTED and never decrements stock for a RELEASED reservation.

The Order completion caller records metadata:

```json
{
  "inventoryOutcome": "ALREADY_RELEASED_AFTER_NO_SHOW"
}
```

when completing from `NO_SHOW_CONTESTED` with zero ACTIVE reservations but historical RELEASED reservations present.

- [ ] **Step 5: Test ledger invariants**

Required tests:

```text
release -> reserved decreases, stockOnHand unchanged, RELEASE_RESERVATION appended
commit -> reserved decreases, stockOnHand decreases, SALE appended
second release -> no duplicate movement
second commit -> no duplicate SALE
released reservation cannot be recommitted
all balance invariants remain true
```

- [ ] **Step 6: Run Plan 04 inventory regressions + commit**

```bash
./mvnw -Dtest=InventoryReservationServiceIntegrationTest,InventoryReservationOutcomeIntegrationTest test
git add apps/api/src/main/java/vn/covie/inventory apps/api/src/test/java/vn/covie/inventory
git commit -m "feat(inventory): finalize order reservation outcomes"
```

---

### Task 9: Implement Shared Order Transition and Completion Services

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/LockedOrder.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderTransitionService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderCompletionService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderCommandResult.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderCompletionIntegrationTest.java`

**Interfaces:**
- Produces reusable atomic transition primitives. Later command services must not set `order.status` directly outside these helpers.

- [ ] **Step 1: Add Order-specific error codes**

```java
ORDER_ACTION_NOT_ALLOWED,
ORDER_STATE_CONFLICT,
ORDER_CANCELLATION_ALREADY_PENDING,
ORDER_CANCELLATION_NOT_PENDING,
ORDER_NO_SHOW_GRACE_NOT_REACHED,
ORDER_ISSUE_NOT_ALLOWED
```

- [ ] **Step 2: Implement lock + state guard**

```java
@Transactional(propagation = Propagation.MANDATORY)
public LockedOrder lock(UUID orderId) {
    OrderEntity order = orders.lockById(orderId)
            .orElseThrow(() -> new ApiException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "Resource not found."));
    return new LockedOrder(order);
}
```

State guard:

```java
public void requireStatus(OrderEntity order, OrderStatus... allowed) {
    if (Arrays.stream(allowed).noneMatch(s -> s == order.status())) {
        throw new ApiException(
                ErrorCode.ORDER_STATE_CONFLICT,
                HttpStatus.CONFLICT,
                "The order can no longer perform this action.",
                Map.of("currentStatus", order.status().name()));
    }
}
```

- [ ] **Step 3: Centralize completion**

```java
@Transactional(propagation = Propagation.MANDATORY)
public OrderCommandResult complete(
        OrderEntity order,
        UUID actorUserId,
        OrderActorContext actorContext,
        OrderEventType eventType,
        boolean automatic,
        Object metadata)
```

Flow:

```text
assert status is a supported completion source
if tracked Product and ACTIVE reservations exist -> commitActiveForOrder
if completing from NO_SHOW_CONTESTED and reservations already RELEASED -> no second stock mutation
status -> COMPLETED
completedAt = now
updatedAt = now
append buyer/system completion event
append ORDER_COMPLETED outbox
flush persistence context so JPA @Version is incremented
return small result with the post-flush version; terminal allowedActions is empty
```

- [ ] **Step 4: Ensure command results never expose a stale `@Version`**

Inject `EntityManager` or use a repository `flush()` before mapping `OrderCommandResult`. The `IdempotencyExecutor` stores the returned response body, so the DTO must contain the post-flush Order version rather than the pre-flush in-memory version.

`OrderTransitionService` exposes one result helper used by **every client command**, not only completion:

```java
@Transactional(propagation = Propagation.MANDATORY)
public OrderCommandResult resultAfterFlush(OrderEntity order, OrderActorType actorType) {
    entityManager.flush();
    return commandResultMapper.map(order, actorType);
}
```

The mapper reloads/uses the already-managed fulfillment state to calculate `allowedActions` for the command actor. System scheduler transitions do not need a command response.

- [ ] **Step 5: Test terminal behavior**

Required:

```text
COMPLETED -> any second completion/state command returns conflict or idempotent replay only if same key
normal Product completion commits reservation exactly once
Service completion touches no inventory
completion from NO_SHOW_CONTESTED does not revive RELEASED reservation
```

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=OrderCompletionIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/application apps/api/src/main/java/vn/covie/orders/api/dto/OrderCommandResult.java apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java apps/api/src/test/java/vn/covie/orders/application/OrderCompletionIntegrationTest.java
git commit -m "feat(order): centralize lifecycle transitions and completion"
```

---

### Task 10: Implement Confirm, Reject and Pre-confirm Buyer Cancel

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/ReasonRequest.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/CancelOrderRequest.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderConfirmRejectIntegrationTest.java`

**Interfaces:**
- Produces first state-changing Order commands using Plan 03 idempotency infrastructure.

- [ ] **Step 1: Define command request**

```java
public record ReasonRequest(
        @NotBlank @Size(max = 60) String reasonCode,
        @Size(max = 1000) String note) {}
```

Canonical `/cancel` body supports buyer no-reason cancel and seller reason-required cancel:

```java
public record CancelOrderRequest(
        @Size(max = 60) String reasonCode,
        @Size(max = 1000) String note) {}
```

Controller normalizes an absent request body to `new CancelOrderRequest(null, null)`; seller post-confirm cancellation validates `reasonCode` is nonblank inside the business service.

- [ ] **Step 2: Implement seller/provider confirm**

Public service signature:

```java
public OrderCommandResult confirm(UUID orderId, String idempotencyKey)
```

Flow inside `IdempotencyExecutor.execute(...)`:

```text
lock Order
requireMerchantProcessor(Order) for exact Order merchant/location
require PENDING_CONFIRMATION
require now < confirmationExpiresAt; at/after deadline return ORDER_STATE_CONFLICT and let scheduler persist EXPIRED
PENDING_CONFIRMATION -> CONFIRMED
confirmedAt = now
append SELLER_CONFIRMED event
append ORDER_CONFIRMED outbox
return resultAfterFlush(Order, MERCHANT)
```

Use an Order-scoped operation string consistently with Task 17:

```text
order.confirm:<orderId>
```

- [ ] **Step 3: Implement merchant reject before confirmation**

```text
operation = order.reject:<orderId>
require reason
lock Order
requireMerchantProcessor(Order)
require PENDING_CONFIRMATION
require now < confirmationExpiresAt
release ACTIVE tracked reservations
status -> REJECTED_BY_SELLER
closedAt = now
append SELLER_REJECTED with reason
append ORDER_REJECTED outbox
```

- [ ] **Step 4: Implement buyer cancel before confirmation**

`POST /orders/{id}/cancel` is actor-sensitive. For buyer in `PENDING_CONFIRMATION`:

```text
operation = order.cancel:<orderId>
lock Order
requireBuyer(Order)
require now < confirmationExpiresAt
release reservation
status -> CANCELLED_BEFORE_CONFIRMATION
closedAt = now
append BUYER_CANCELLED_BEFORE_CONFIRMATION
append ORDER_CANCELLED outbox
```

Seller cancellation on confirmed states is added in Task 11 using the same route/service method dispatch.

- [ ] **Step 5: Test races**

Two threads from the same initial PENDING state:

```text
seller confirm vs buyer cancel
seller confirm vs seller reject
```

Expected: one transition wins; loser receives `409 ORDER_STATE_CONFLICT`; one inventory outcome only.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=OrderConfirmRejectIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java apps/api/src/main/java/vn/covie/orders/api/dto/ReasonRequest.java apps/api/src/test/java/vn/covie/orders/application/OrderConfirmRejectIntegrationTest.java
git commit -m "feat(order): confirm reject and cancel pending orders"
```

---

### Task 11: Implement Post-confirm Cancellation Request and Seller Cancellation

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderCancellationService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/CancellationRequestBody.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/CancellationResponseBody.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderCancellationIntegrationTest.java`

**Interfaces:**
- Preserves exact previous operational state and provides accept/reject transitions.

- [ ] **Step 1: Define buyer cancellation request body**

```java
public record CancellationRequestBody(
        @NotBlank @Size(max = 60) String reasonCode,
        @Size(max = 1000) String note) {}
```

Merchant response:

```java
public record CancellationResponseBody(
        @Size(max = 1000) String note) {}
```

- [ ] **Step 2: Request cancellation**

Allowed sources:

```text
CONFIRMED
OUT_FOR_DELIVERY
READY_FOR_PICKUP
```

Not allowed from `AWAITING_BUYER_CONFIRMATION`, because after declared delivery/performance buyer must use issue/confirmation flow.

Transaction:

```text
lock Order
require BUYER
assert no pending cancellation
insert PENDING order_cancellation_requests with previousOrderStatus=current status
Order current status -> CANCELLATION_REQUESTED
append BUYER_REQUESTED_CANCELLATION
append ORDER_CANCELLATION_REQUESTED outbox
```

- [ ] **Step 3: Accept cancellation**

```text
lock Order
requireMerchantProcessor(Order)
require CANCELLATION_REQUESTED
lock pending cancellation
request -> ACCEPTED + responder/time
release ACTIVE reservation
Order -> CANCELLED_BY_BUYER_AFTER_CONFIRMATION
closedAt = now
append SELLER_ACCEPTED_CANCELLATION
append ORDER_CANCELLATION_RESPONDED outbox with final status CANCELLED_BY_BUYER_AFTER_CONFIRMATION
```

- [ ] **Step 4: Reject cancellation**

```text
lock Order
requireMerchantProcessor(Order)
require CANCELLATION_REQUESTED
lock pending cancellation
validate previous status is still one of CONFIRMED/OUT_FOR_DELIVERY/READY_FOR_PICKUP
request -> REJECTED
Order -> previousOrderStatus
append SELLER_REJECTED_CANCELLATION
append ORDER_CANCELLATION_RESPONDED outbox with restored status
```

No reservation mutation occurs on rejection.

- [ ] **Step 5: Implement merchant cancel after confirmation through canonical `/cancel` route**

Allowed merchant sources:

```text
CONFIRMED
OUT_FOR_DELIVERY
READY_FOR_PICKUP
```

Transaction:

```text
reason required
release ACTIVE reservation
Order -> CANCELLED_BY_SELLER_AFTER_CONFIRMATION
closedAt = now
append SELLER_CANCELLED_AFTER_CONFIRMATION with reason
append ORDER_CANCELLED outbox
```

Merchant cannot cancel `AWAITING_BUYER_CONFIRMATION` or an issue state in V1.

- [ ] **Step 6: Test previous-status restoration and concurrent response**

Two merchant responses racing on one `CANCELLATION_REQUESTED` order: exactly one accepts/rejects; the other sees 409.

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=OrderCancellationIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/application/OrderCancellationService.java apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java apps/api/src/main/java/vn/covie/orders/api/dto/CancellationRequestBody.java apps/api/src/main/java/vn/covie/orders/api/dto/CancellationResponseBody.java apps/api/src/test/java/vn/covie/orders/application/OrderCancellationIntegrationTest.java
git commit -m "feat(order): add post-confirm cancellation workflow"
```

---

### Task 12: Implement Product Seller-delivery Commands

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/ProductFulfillmentEntity.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/ProductDeliveryOrderIntegrationTest.java`

**Interfaces:**
- Adds `MARK_OUT_FOR_DELIVERY`, `MARK_DELIVERED`, and buyer direct `CONFIRM_RECEIVED` for seller-delivery Product Orders.

- [ ] **Step 1: Add entity transition methods**

```java
public void markOutForDelivery(Instant now) {
    this.outForDeliveryAt = now;
}

public void markDelivered(Instant now) {
    this.sellerMarkedDeliveredAt = now;
    this.awaitingBuyerConfirmationAt = now;
}
```

`out_for_delivery_at`, `auto_complete_at`, and `no_show_eligible_at` are added by Task 1's V010 migration and mapped here; do not create a second migration or alter V010 after it has been applied.

- [ ] **Step 2: Implement mark-out-for-delivery**

```text
lock Order
requireMerchantProcessor(Order)
require PRODUCT + SELLER_DELIVERY
require CONFIRMED
set fulfillment.outForDeliveryAt = now
Order -> OUT_FOR_DELIVERY
append SELLER_MARKED_OUT_FOR_DELIVERY
append ORDER_FULFILLMENT_UPDATED outbox
```

- [ ] **Step 3: Implement mark-delivered from two sources**

Allowed:

```text
CONFIRMED
OUT_FOR_DELIVERY
```

Flow:

```text
resolve effective OrderPolicySnapshot
set sellerMarkedDeliveredAt + awaitingBuyerConfirmationAt
set autoCompleteAt = now + policy.autoCompleteGrace
Order -> AWAITING_BUYER_CONFIRMATION
append SELLER_MARKED_DELIVERED
outbox update
```

- [ ] **Step 4: Implement buyer confirm-received from all contract sources**

Allowed Product delivery sources:

```text
CONFIRMED
OUT_FOR_DELIVERY
AWAITING_BUYER_CONFIRMATION
DELIVERY_ISSUE_REPORTED
```

Use `OrderCompletionService.complete(...)` with `actorUserId=buyer` and `actorContext=BUYER`; if completion resolves an OPEN delivery issue, mark it `RESOLVED_BY_PARTIES` and set `resolvedAt` before final status.

- [ ] **Step 5: Test direct completion**

Explicitly prove buyer can go `CONFIRMED -> COMPLETED` without seller pressing delivery progress buttons and reservation becomes COMMITTED + SALE exactly once.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=ProductDeliveryOrderIntegrationTest,OrderCompletionIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders apps/api/src/test/java/vn/covie/orders/application/ProductDeliveryOrderIntegrationTest.java
git commit -m "feat(order): add product delivery fulfillment commands"
```

---

### Task 13: Implement Product Pickup and Buyer No-show Flow

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderIssueService.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/ProductFulfillmentEntity.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/ProductPickupOrderIntegrationTest.java`

**Interfaces:**
- Adds READY_FOR_PICKUP, grace-enforced no-show, contest, and late buyer completion behavior.

- [ ] **Step 1: Implement mark-ready-for-pickup**

```text
lock Order
requireMerchantProcessor(Order)
require PRODUCT + PICKUP
require CONFIRMED
sellerMarkedReadyAt = now
resolve effective OrderPolicySnapshot
noShowEligibleAt = now + policy.pickupNoShowGrace
Order -> READY_FOR_PICKUP
append SELLER_MARKED_READY_FOR_PICKUP
outbox update
```

- [ ] **Step 2: Enforce no-show grace on server**

```java
Instant earliest = fulfillment.noShowEligibleAt();
if (earliest == null || clock.now().isBefore(earliest)) {
    throw new ApiException(
            ErrorCode.ORDER_NO_SHOW_GRACE_NOT_REACHED,
            HttpStatus.CONFLICT,
            "Buyer no-show grace period has not elapsed.",
            Map.of("eligibleAt", earliest));
}
```

- [ ] **Step 3: Report buyer no-show**

```text
lock Order
require MERCHANT PROCESS_ORDERS
require READY_FOR_PICKUP
verify grace elapsed
release ACTIVE inventory reservation immediately
Order -> BUYER_NO_SHOW
append SELLER_MARKED_BUYER_NO_SHOW
append ORDER_FULFILLMENT_UPDATED outbox
```

- [ ] **Step 4: Contest no-show**

```text
lock Order
requireBuyer(Order)
require BUYER_NO_SHOW
resolve effective OrderPolicySnapshot
insert OPEN order_issue type BUYER_NO_SHOW_CONTESTED
  openedAt = now
  closeAfterAt = now + policy.issueCloseTimeout
Order -> NO_SHOW_CONTESTED
append BUYER_CONTESTED_NO_SHOW
append ORDER_ISSUE_REPORTED outbox
```

Do not re-reserve stock.

- [ ] **Step 5: Late completion after contest**

`CONFIRM_RECEIVED` from `NO_SHOW_CONTESTED`:

```text
mark OPEN no-show issue RESOLVED_BY_PARTIES
Order -> COMPLETED through OrderCompletionService
leave prior RELEASED reservation unchanged
include event metadata inventoryOutcome=ALREADY_RELEASED_AFTER_NO_SHOW
```

- [ ] **Step 6: Test time boundary and inventory behavior**

Use fixed `AppClock` to assert one millisecond before grace is rejected and exact grace boundary is accepted.

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=ProductPickupOrderIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders apps/api/src/test/java/vn/covie/orders/application/ProductPickupOrderIntegrationTest.java
git commit -m "feat(order): add pickup and no-show lifecycle"
```

---

### Task 14: Implement Service Fulfillment Commands

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/model/ServiceFulfillmentEntity.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/ServiceOrderIntegrationTest.java`

**Interfaces:**
- Adds provider performed + buyer completion; no inventory, booking slot, shipping, or internal chat.

- [ ] **Step 1: Implement provider mark-service-performed**

```text
lock Order
requireMerchantProcessor(Order)
require SERVICE
require CONFIRMED
providerMarkedPerformedAt = now
awaitingBuyerConfirmationAt = now
resolve effective OrderPolicySnapshot
autoCompleteAt = now + policy.autoCompleteGrace
Order -> AWAITING_BUYER_CONFIRMATION
append PROVIDER_MARKED_PERFORMED
append ORDER_FULFILLMENT_UPDATED outbox
```

- [ ] **Step 2: Implement buyer confirm-completed**

Allowed sources:

```text
CONFIRMED
AWAITING_BUYER_CONFIRMATION
SERVICE_ISSUE_REPORTED
```

Use `OrderCompletionService.complete` with `actorUserId=buyer` and `actorContext=BUYER`; resolve open service issue as `RESOLVED_BY_PARTIES` when applicable.

- [ ] **Step 3: Test Product-only commands are rejected on Service Orders**

Examples:

```text
mark-delivered on SERVICE -> 409 ORDER_STATE_CONFLICT or action-specific forbidden state
mark-ready-for-pickup on SERVICE -> 409
confirm-received route on SERVICE -> 409
```

Likewise `confirm-completed` on PRODUCT is rejected.

- [ ] **Step 4: Run and commit**

```bash
./mvnw -Dtest=ServiceOrderIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders apps/api/src/test/java/vn/covie/orders/application/ServiceOrderIntegrationTest.java
git commit -m "feat(order): add service fulfillment lifecycle"
```

---

### Task 15: Implement Delivery and Service Issue Reporting

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderIssueService.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderIssueIntegrationTest.java`

**Interfaces:**
- Creates OPEN transaction issues without arbitration and blocks auto-completion.

- [ ] **Step 1: Define issue report request using existing reason/text convention**

Reuse `ReasonRequest` with a route-specific allowlist of reason codes. At minimum V1 accepts a stable generic reason code plus optional note; do not let arbitrary client reason code become an admin action.

- [ ] **Step 2: Report delivery issue**

```text
lock Order
requireBuyer(Order)
require PRODUCT + SELLER_DELIVERY
require AWAITING_BUYER_CONFIRMATION
assert sellerMarkedDeliveredAt != null
assert no OPEN issue
resolve effective OrderPolicySnapshot
insert OPEN DELIVERY_NOT_RECEIVED with closeAfterAt = now + policy.issueCloseTimeout
Order -> DELIVERY_ISSUE_REPORTED
append BUYER_REPORTED_NOT_RECEIVED
append ORDER_ISSUE_REPORTED outbox
```

- [ ] **Step 3: Report service issue**

```text
lock Order
requireBuyer(Order)
require SERVICE
require AWAITING_BUYER_CONFIRMATION
assert providerMarkedPerformedAt != null
assert no OPEN issue
resolve effective OrderPolicySnapshot
insert OPEN SERVICE_NOT_COMPLETED with closeAfterAt = now + policy.issueCloseTimeout
Order -> SERVICE_ISSUE_REPORTED
append BUYER_REPORTED_SERVICE_NOT_COMPLETED
append ORDER_ISSUE_REPORTED outbox
```

- [ ] **Step 4: Prove issue prevents normal auto-complete**

Scheduler candidate query/process must exclude issue statuses from auto-completion and route them only to issue-timeout closure.

- [ ] **Step 5: Test duplicate issue + early issue rejection**

```text
buyer reports delivery issue before seller marked delivered -> conflict
buyer reports service issue before provider performed -> conflict
second OPEN issue for same order -> conflict/unique constraint safely translated
merchant cannot file buyer's delivery/service issue -> 403
```

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=OrderIssueIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/application/OrderIssueService.java apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java apps/api/src/test/java/vn/covie/orders/application/OrderIssueIntegrationTest.java
git commit -m "feat(order): record transaction issues"
```

---

### Task 16: Implement Scheduled Expiry, Auto-completion and Issue Closure

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderScheduledTransitionService.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/OrderScheduler.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/persistence/OrderRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/persistence/OrderIssueRepository.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderScheduledTransitionIntegrationTest.java`

**Interfaces:**
- Produces multi-replica-safe system transitions without public system endpoints.

- [ ] **Step 1: Add one-row `SKIP LOCKED` candidate queries**

Pending expiry:

```java
@Query(value = """
    select * from orders
    where status = 'PENDING_CONFIRMATION'
      and confirmation_expires_at is not null
      and confirmation_expires_at <= :now
    order by confirmation_expires_at asc, id asc
    for update skip locked
    limit 1
    """, nativeQuery = true)
Optional<OrderEntity> lockNextExpiredPending(Instant now);
```

Awaiting auto-complete query locks only the Order row and uses the persisted subtype deadline:

```java
@Query(value = """
    select o.* from orders o
    left join product_fulfillments pf on pf.order_id = o.id
    left join service_fulfillments sf on sf.order_id = o.id
    where o.status = 'AWAITING_BUYER_CONFIRMATION'
      and coalesce(pf.auto_complete_at, sf.auto_complete_at) <= :now
      and not exists (
          select 1 from order_issues i
          where i.order_id = o.id and i.issue_status = 'OPEN'
      )
    order by coalesce(pf.auto_complete_at, sf.auto_complete_at) asc, o.id asc
    for update of o skip locked
    limit 1
    """, nativeQuery = true)
Optional<OrderEntity> lockNextAutoComplete(Instant now);
```

Issue timeout query also locks the Order first:

```java
@Query(value = """
    select o.* from orders o
    join order_issues i on i.order_id = o.id
    where o.status in ('DELIVERY_ISSUE_REPORTED','SERVICE_ISSUE_REPORTED','NO_SHOW_CONTESTED')
      and i.issue_status = 'OPEN'
      and i.close_after_at <= :now
    order by i.close_after_at asc, o.id asc
    for update of o skip locked
    limit 1
    """, nativeQuery = true)
Optional<OrderEntity> lockNextIssueClose(Instant now);
```

After `lockNextIssueClose` returns, lock the OPEN issue row with `OrderIssueRepository.lockOpenByOrderId(order.id())`. This preserves Order-first lock order.

- [ ] **Step 2: Process one expiry in `REQUIRES_NEW` transaction**

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean expireNext(Instant now)
```

Flow:

```text
lock one due PENDING Order SKIP LOCKED
release ACTIVE reservation
Order -> EXPIRED
closedAt = now
append ORDER_EXPIRED
append ORDER_EXPIRED outbox payload with status EXPIRED
return true
```

- [ ] **Step 3: Process one auto-completion**

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean autoCompleteNext(Instant now)
```

Only `AWAITING_BUYER_CONFIRMATION` and only when:

```text
persisted autoCompleteAt <= now
no OPEN issue
```

Call `OrderCompletionService.complete(order, null, OrderActorContext.SYSTEM, OrderEventType.ORDER_AUTO_COMPLETED, true, Map.of("source", "AUTO_COMPLETE"))`.

- [ ] **Step 4: Process one issue/no-show-contest timeout**

For:

```text
DELIVERY_ISSUE_REPORTED
SERVICE_ISSUE_REPORTED
NO_SHOW_CONTESTED
```

when OPEN issue `closeAfterAt <= now`:

```text
issue -> CLOSED_BY_TIMEOUT, closedAt=now
if previous Order status = DELIVERY_ISSUE_REPORTED and Product has ACTIVE reservation:
  commitActiveForOrder -> COMMITTED + SALE (conservative: seller declared delivery; do not restock)
if previous status = SERVICE_ISSUE_REPORTED:
  no inventory action
if previous status = NO_SHOW_CONTESTED:
  reservation must already be RELEASED; no second inventory action
Order -> CLOSED_WITH_ISSUE
closedAt=now
append ORDER_CLOSED_WITH_ISSUE
append ORDER_CLOSED_WITH_ISSUE outbox
```

For Product delivery issue, committing rather than releasing avoids inventing stock after the seller declared delivery. If the item later returns physically, seller reconciles with the existing inventory adjustment API. Service has no inventory. For no-show contest, inventory was already RELEASED at `BUYER_NO_SHOW`.

- [ ] **Step 5: Scheduler loops with bounded work**

```java
@Scheduled(fixedDelayString = "${covie.orders.scheduler-delay:PT5S}")
public void runLifecycleJobs() {
    Instant now = clock.now();
    drain(() -> transitions.expireNext(now), 50);
    drain(() -> transitions.autoCompleteNext(now), 50);
    drain(() -> transitions.closeIssueNext(now), 50);
}
```

`drain` stops when supplier returns false or after max batch count. Each row is its own transaction so one bad Order does not roll back the entire scheduler batch.

- [ ] **Step 6: Test two concurrent scheduler workers**

Run two executor threads against the same due Order. Expected:

```text
one transition/event/inventory outcome
second worker finds nothing or sees post-transition state
no duplicate SALE/RELEASE movement
```

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=OrderScheduledTransitionIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/application apps/api/src/main/java/vn/covie/orders/persistence apps/api/src/test/java/vn/covie/orders/application/OrderScheduledTransitionIntegrationTest.java
git commit -m "feat(order): add scheduled lifecycle transitions"
```

---

### Task 17: Expose Canonical Order HTTP APIs and Idempotent Commands

**Files:**
- Create: `apps/api/src/main/java/vn/covie/orders/api/OrderController.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/MeOrderController.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/MerchantOrderController.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java`
- Test: `apps/api/src/test/java/vn/covie/orders/api/OrderControllerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderCommandIdempotencyIntegrationTest.java`

**Interfaces:**
- Exposes approved V1 endpoint catalog with direct DTOs and standardized errors.

- [ ] **Step 1: Expose read endpoints**

```http
GET /api/v1/orders/{orderId}
GET /api/v1/orders/{orderId}/events
GET /api/v1/me/orders?limit=20&cursor=...
GET /api/v1/merchants/{merchantId}/orders?limit=20&cursor=...&locationId=...
```

No universal `{success,data}` wrapper.

- [ ] **Step 2: Expose all approved command routes**

```http
POST /api/v1/orders/{orderId}/confirm
POST /api/v1/orders/{orderId}/reject
POST /api/v1/orders/{orderId}/cancel
POST /api/v1/orders/{orderId}/request-cancellation
POST /api/v1/orders/{orderId}/accept-cancellation
POST /api/v1/orders/{orderId}/reject-cancellation
POST /api/v1/orders/{orderId}/mark-out-for-delivery
POST /api/v1/orders/{orderId}/mark-delivered
POST /api/v1/orders/{orderId}/mark-ready-for-pickup
POST /api/v1/orders/{orderId}/report-buyer-no-show
POST /api/v1/orders/{orderId}/contest-no-show
POST /api/v1/orders/{orderId}/mark-service-performed
POST /api/v1/orders/{orderId}/confirm-received
POST /api/v1/orders/{orderId}/confirm-completed
POST /api/v1/orders/{orderId}/report-delivery-issue
POST /api/v1/orders/{orderId}/report-service-issue
```

- [ ] **Step 3: Require Idempotency-Key on every state-changing Order command**

Controller example:

```java
@PostMapping("/{orderId}/confirm")
public OrderCommandResult confirm(
        @PathVariable UUID orderId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return commands.confirm(orderId, idempotencyKey);
}
```

`OrderCommandService` calls:

```java
idempotency.execute(
        actor.userId(),
        "order.confirm:" + orderId,
        idempotencyKey,
        Map.of("orderId", orderId),
        200,
        OrderCommandResult.class,
        () -> executeConfirm(orderId));
```

Operation scope always includes the Order ID using `order.<command>:<orderId>` (for example `order.confirm:0198...`). The same raw key may therefore be reused on a different Order without colliding, although recommended frontend behavior is still a fresh UUID key per button intent.

For commands with a request body (`reject`, seller `cancel`, `request-cancellation`, cancellation response notes, issue reports), pass the **normalized actual DTO** to `IdempotencyExecutor` as `requestBody`; never hash only `{orderId}`. For a bodyless command, hash a stable value such as `Map.of("orderId", orderId)`. For `/cancel` with an absent buyer body, normalize first to `new CancelOrderRequest(null, null)` so retries hash identically. This is what makes same-key/different-reason requests return the required idempotency conflict.

- [ ] **Step 4: Keep `/cancel` actor-aware but unambiguous**

```text
BUYER + PENDING -> CANCELLED_BEFORE_CONFIRMATION; `CancelOrderRequest.reasonCode` optional
MERCHANT + confirmed operational state -> CANCELLED_BY_SELLER_AFTER_CONFIRMATION; `CancelOrderRequest.reasonCode` required
BUYER + confirmed operational state -> 403/allowedAction says REQUEST_CANCELLATION; buyer cannot direct-cancel
```

- [ ] **Step 5: Test HTTP semantics**

Required:

```text
unauthed -> 401
private other-user Order -> 404
buyer calls seller confirm on own visible Order -> 403 ORDER_ACTION_NOT_ALLOWED
seller confirm cancelled state -> 409 ORDER_STATE_CONFLICT
missing Idempotency-Key -> contract error
same key/body -> same result, no duplicate event/outbox
same key/different body -> 409 idempotency conflict
validation error -> 422
command success -> 200 small result
```

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=OrderControllerIntegrationTest,OrderCommandIdempotencyIntegrationTest test
git add apps/api/src/main/java/vn/covie/orders/api apps/api/src/main/java/vn/covie/orders/application/OrderCommandService.java apps/api/src/test/java/vn/covie/orders/api apps/api/src/test/java/vn/covie/orders/application/OrderCommandIdempotencyIntegrationTest.java
git commit -m "feat(order): expose canonical lifecycle API"
```

---

### Task 18: Add Full State/Concurrency Contract Test Matrix

**Files:**
- Create: `apps/api/src/test/java/vn/covie/orders/application/OrderStateMatrixIntegrationTest.java`
- Create: `apps/api/src/test/java/vn/covie/orders/application/OrderConcurrencyIntegrationTest.java`
- Create: `apps/api/src/test/java/vn/covie/orders/support/OrderScenarioFactory.java`

**Interfaces:**
- Proves lifecycle invariants independent of controller happy paths.

- [ ] **Step 1: Create explicit scenario factory**

Factory methods create real checkout-produced Orders, not direct status-mutated fake rows:

```java
OrderScenario pendingProductDelivery();
OrderScenario pendingProductPickup();
OrderScenario pendingService();
OrderScenario confirmedProductDelivery();
OrderScenario confirmedProductPickup();
OrderScenario confirmedService();
```

Use Plan 04 test fixtures/Checkout Commit where practical so tracked reservation behavior remains realistic.

- [ ] **Step 2: Parameterize all allowed transitions**

For every state diagram edge in this plan, assert:

```text
initial status
actor
command
final status
expected event
expected outbox type
expected inventory reservation status
```

- [ ] **Step 3: Parameterize forbidden transitions**

At minimum:

```text
COMPLETED cannot reopen/cancel/report issue
seller cannot confirm cancelled Order
buyer cannot issue-report before declared fulfillment
buyer cannot direct-cancel after confirmation
seller cannot mark delivered on pickup
seller cannot mark ready on seller-delivery
provider cannot use Product commands
Product seller cannot use service-performed
issue prevents auto-completion
no-show before grace fails
```

- [ ] **Step 4: Add concurrent actor race tests**

Use `CountDownLatch` so requests actually overlap:

```text
confirm vs buyer cancel pending
confirm vs reject pending
buyer complete vs seller cancel confirmed
buyer complete vs seller mark-delivered
accept vs reject cancellation
buyer complete vs auto-complete
issue-report vs auto-complete at grace boundary
```

Expected invariant: one serializable domain outcome through row lock, no duplicate inventory/event side effects.

- [ ] **Step 5: Run full Order matrix**

```bash
./mvnw -Dtest='vn.covie.orders.**' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/api/src/test/java/vn/covie/orders
git commit -m "test(order): cover lifecycle and concurrency matrix"
```

---

### Task 19: Regenerate OpenAPI Client and Add Minimal Buyer/Seller Order UI

**Files:**
- Modify/generated: `packages/api-client/openapi/openapi.json`
- Modify/generated: `packages/api-client/src/generated/schema.ts`
- Create: `apps/web/features/orders/order-api.ts`
- Create: `apps/web/features/orders/order-actions.tsx`
- Create: `apps/web/features/orders/order-status.tsx`
- Create: `apps/web/features/orders/order-timeline.tsx`
- Create: `apps/web/features/orders/order-detail.tsx`
- Create: `apps/web/app/(buyer)/orders/page.tsx`
- Create: `apps/web/app/(buyer)/orders/[orderId]/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/orders/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/orders/[orderId]/page.tsx`
- Test: `apps/web/features/orders/order-actions.test.tsx`

**Interfaces:**
- Produces a working vertical slice; Plan 09 later polishes full Buyer/Seller UX.

- [ ] **Step 1: Regenerate client from backend OpenAPI**

Use the exact Plan 01 generation command already established in the repo, then run TypeScript build to catch enum/DTO drift.

Expected generated contracts include:

```text
OrderStatus
OrderAction
OrderDetailResponse
OrderSummaryResponse
OrderCommandResult
```

- [ ] **Step 2: Render status with unknown-enum fallback**

```tsx
export function OrderStatusLabel({ status }: { status: string }) {
  const known: Record<string, string> = {
    PENDING_CONFIRMATION: 'Chờ xác nhận',
    CONFIRMED: 'Đã xác nhận',
    OUT_FOR_DELIVERY: 'Đang giao',
    READY_FOR_PICKUP: 'Sẵn sàng nhận',
    AWAITING_BUYER_CONFIRMATION: 'Chờ bạn xác nhận',
    CANCELLATION_REQUESTED: 'Đang yêu cầu hủy',
    COMPLETED: 'Hoàn thành',
    EXPIRED: 'Hết hạn',
    REJECTED_BY_SELLER: 'Người bán từ chối',
    CANCELLED_BEFORE_CONFIRMATION: 'Đã hủy',
    CANCELLED_BY_BUYER_AFTER_CONFIRMATION: 'Đã hủy',
    CANCELLED_BY_SELLER_AFTER_CONFIRMATION: 'Người bán đã hủy',
    DELIVERY_ISSUE_REPORTED: 'Đang có vấn đề giao hàng',
    SERVICE_ISSUE_REPORTED: 'Đang có vấn đề dịch vụ',
    BUYER_NO_SHOW: 'Không đến nhận hàng',
    NO_SHOW_CONTESTED: 'Đang phản hồi no-show',
    CLOSED_WITH_ISSUE: 'Đóng với vấn đề',
  };
  return <span>{known[status] ?? 'Trạng thái mới'}</span>;
}
```

- [ ] **Step 3: Render buttons only from backend `allowedActions`**

`order-actions.tsx` never reconstructs the state machine from `status`.

```tsx
const actions = new Set(order.allowedActions ?? []);
return (
  <>
    {actions.has('CONFIRM_RECEIVED') && <ConfirmReceivedButton orderId={order.id} />}
    {actions.has('REQUEST_CANCELLATION') && <RequestCancellationButton orderId={order.id} />}
    {actions.has('MARK_DELIVERED') && <MarkDeliveredButton orderId={order.id} />}
  </>
);
```

Every button generates a fresh UUID `Idempotency-Key`; on 409 it refetches canonical Order detail.

- [ ] **Step 4: Add buyer list/detail and seller list/detail pages**

Pages use generated client wrappers and preserve server-side auth cookie forwarding conventions from Plan 02. Do not expose buyer private fulfillment data in client caches shared across users.

- [ ] **Step 5: Test action rendering + conflict refetch**

```text
button absent when action absent even if status text suggests it
unknown status renders fallback without crashing
409 command triggers detail refetch and shows current status
same button double-click disables while first command is pending
```

- [ ] **Step 6: Run frontend checks and commit**

```bash
pnpm --filter web test
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web build
git add packages/api-client apps/web
git commit -m "feat(web): add buyer and seller order lifecycle views"
```

---

### Task 20: Plan 05 Acceptance Gate and Regression Verification

**Files:**
- Modify: `README.md` only if Order-specific local test/run instructions are missing.
- Test only; no feature implementation should be introduced in this task.

**Interfaces:**
- Produces verified Plan 05 baseline for Plan 06, which starts Flyway at V011.

- [ ] **Step 1: Verify migration chain from empty database**

```bash
cd apps/api
./mvnw -Dtest=Plan05SchemaMigrationTest test
```

Expected Flyway history ends at:

```text
V010__order_lifecycle.sql
```

- [ ] **Step 2: Run backend Order + Inventory + Checkout regression suites**

```bash
./mvnw -Dtest='vn.covie.orders.**,vn.covie.inventory.**,vn.covie.checkout.**' test
```

Expected: PASS. Checkout remains able to create `PENDING_CONFIRMATION` Orders and reserve tracked stock.

- [ ] **Step 3: Run full backend test suite**

```bash
./mvnw test
```

Expected: PASS with no Plan 01–04 regression.

- [ ] **Step 4: Run frontend verification**

```bash
cd ../..
pnpm --filter web test
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web build
```

Expected: all commands exit 0.

- [ ] **Step 5: Run generated API-client drift check**

Run the repository's Plan 01 OpenAPI generation/check command and verify `git diff --exit-code -- packages/api-client` after generation.

Expected: no uncommitted generated drift.

- [ ] **Step 6: Manually inspect representative DB invariants after integration tests**

Execute SQL assertions in an integration/debug database:

```sql
select count(*)
from inventory_balances
where stock_on_hand < 0
   or reserved_quantity < 0
   or reserved_quantity > stock_on_hand;
```

Expected: `0`.

```sql
select order_id, count(*)
from order_cancellation_requests
where request_status = 'PENDING'
group by order_id
having count(*) > 1;
```

Expected: no rows.

```sql
select order_id, count(*)
from order_issues
where issue_status = 'OPEN'
group by order_id
having count(*) > 1;
```

Expected: no rows.

- [ ] **Step 7: Verify required behavior checklist**

```text
[ ] buyer reads only own private Orders
[ ] seller sees only merchant/location Orders they may access
[ ] STAFF location restriction works
[ ] canonical Order detail returns actor-specific allowedActions
[ ] seller/provider confirms/rejects pending Order
[ ] buyer cancels pending freely
[ ] buyer post-confirm cancellation requires seller decision
[ ] cancellation rejection returns exact previous operational status
[ ] seller post-confirm cancellation requires reason and releases reservation
[ ] seller-delivery progress works
[ ] pickup/no-show grace enforced by server time
[ ] buyer can contest no-show
[ ] service performed flow works
[ ] buyer can complete directly from CONFIRMED
[ ] issue only allowed after declared fulfillment
[ ] issue blocks auto-complete
[ ] pending timeout expires Order and releases inventory
[ ] normal completion commits tracked reservation + SALE
[ ] no-show releases reservation
[ ] no-show-contested late completion never recommits RELEASED inventory
[ ] delivery CLOSED_WITH_ISSUE commits ACTIVE stock instead of restocking; no-show contest remains RELEASED
[ ] COMPLETED is terminal
[ ] all important transitions append OrderEvent + outbox intent atomically
[ ] Idempotency-Key retry does not duplicate event/inventory/outbox
[ ] concurrent commands resolve to one valid state
[ ] no admin force-status endpoint exists
```

- [ ] **Step 8: Commit any documentation-only acceptance adjustment**

If and only if README was changed:

```bash
git add README.md
git commit -m "docs(order): document lifecycle verification"
```

Do not create an empty commit.

---

# Error Contract Added by Plan 05

The following machine-readable error codes become part of `/api/v1`:

```text
ORDER_ACTION_NOT_ALLOWED
ORDER_STATE_CONFLICT
ORDER_CANCELLATION_ALREADY_PENDING
ORDER_CANCELLATION_NOT_PENDING
ORDER_NO_SHOW_GRACE_NOT_REACHED
ORDER_ISSUE_NOT_ALLOWED
```

Examples:

```json
{
  "error": {
    "code": "ORDER_STATE_CONFLICT",
    "message": "The order can no longer perform this action.",
    "details": {
      "currentStatus": "CANCELLED_BEFORE_CONFIRMATION"
    },
    "requestId": "req_..."
  }
}
```

```json
{
  "error": {
    "code": "ORDER_NO_SHOW_GRACE_NOT_REACHED",
    "details": {
      "eligibleAt": "2026-08-08T12:00:00Z"
    },
    "requestId": "req_..."
  }
}
```

---

# Order Command Result Contract

Every successful lifecycle command returns a small current-state DTO:

```java
public record OrderCommandResult(
        UUID id,
        String orderCode,
        OrderStatus status,
        long version,
        Instant updatedAt,
        Instant confirmedAt,
        Instant completedAt,
        Instant closedAt,
        Set<OrderAction> allowedActions) {}
```

No lifecycle command returns the whole Order aggregate. The frontend refetches `GET /api/v1/orders/{orderId}` when it needs the complete detail/timeline.

---

# Event and Inventory Outcome Matrix

| Transition | Event | Reservation outcome |
|---|---|---|
| PENDING -> CONFIRMED | `SELLER_CONFIRMED` | keep ACTIVE |
| PENDING -> REJECTED_BY_SELLER | `SELLER_REJECTED` | RELEASE |
| PENDING -> CANCELLED_BEFORE_CONFIRMATION | `BUYER_CANCELLED_BEFORE_CONFIRMATION` | RELEASE |
| PENDING -> EXPIRED | `ORDER_EXPIRED` | RELEASE |
| confirmed operational -> CANCELLATION_REQUESTED | `BUYER_REQUESTED_CANCELLATION` | keep ACTIVE |
| cancellation accepted | `SELLER_ACCEPTED_CANCELLATION` | RELEASE |
| cancellation rejected | `SELLER_REJECTED_CANCELLATION` | keep ACTIVE |
| seller post-confirm cancel | `SELLER_CANCELLED_AFTER_CONFIRMATION` | RELEASE |
| delivery/pickup/service progress | fulfillment event | keep ACTIVE |
| READY_FOR_PICKUP -> BUYER_NO_SHOW | `SELLER_MARKED_BUYER_NO_SHOW` | RELEASE |
| BUYER_NO_SHOW -> NO_SHOW_CONTESTED | `BUYER_CONTESTED_NO_SHOW` | remain RELEASED |
| normal COMPLETED | buyer/system completion | COMMIT ACTIVE + SALE |
| NO_SHOW_CONTESTED -> COMPLETED | buyer completion | no second inventory mutation |
| DELIVERY_ISSUE_REPORTED -> CLOSED_WITH_ISSUE | `ORDER_CLOSED_WITH_ISSUE` | COMMIT ACTIVE + SALE; never restock |
| SERVICE_ISSUE_REPORTED -> CLOSED_WITH_ISSUE | `ORDER_CLOSED_WITH_ISSUE` | none |
| NO_SHOW_CONTESTED -> CLOSED_WITH_ISSUE | `ORDER_CLOSED_WITH_ISSUE` | remain RELEASED |

---

# Scheduler Safety Notes

Do not use a JVM-only mutex or assume one API replica.

Correctness is database-based:

```text
fixed-delay trigger may fire on N replicas
        ↓
each worker calls one-row REQUIRES_NEW processor
        ↓
Postgres FOR UPDATE SKIP LOCKED selects at most one candidate per worker
        ↓
state is revalidated under lock
        ↓
transition + side effects + event + outbox commit atomically
```

A process crash before commit releases row locks and another worker retries later. A crash after commit sees post-transition state and cannot reapply the transition.

---

# Plan 05 Non-goals

Do not add these in Plan 05:

```text
Need Request / Need Response tables or FULFILLED state mutation        -> Plan 06
review creation / rating aggregation                                  -> Plan 07
reputation aggregation                                                 -> Plan 07
outbox dispatcher                                                      -> Plan 07
notification persistence/delivery/SSE                                  -> Plan 07
admin Order force state                                                -> never in V1 normal Admin API
dynamic scoped SystemSetting UI/resolver                               -> Plan 08
transaction dispute arbitration                                       -> not V1
payment verification/refund/escrow                                    -> not V1
service booking calendar                                               -> not V1
internal realtime chat                                                 -> not V1
WebSocket Order state                                                  -> not V1
Kafka/RabbitMQ                                                         -> not V1 requirement
```

---

# Self-review Checklist Before Declaring Plan 05 Implemented

## Spec coverage

```text
Canonical Order detail/list                            -> Tasks 4, 6, 7, 17
404 vs 403 private resource rules                      -> Tasks 4, 17
allowedActions                                         -> Task 6
explicit commands / no PATCH status                    -> Tasks 10–17
seller confirm/reject                                  -> Task 10
pre-confirm buyer cancel                               -> Task 10
post-confirm cancellation request                      -> Task 11
seller accept/reject cancellation                      -> Task 11
seller post-confirm cancel                             -> Task 11
Product seller delivery                                -> Task 12
Product pickup                                         -> Task 13
buyer no-show/contest                                  -> Task 13
Service performed/completed                            -> Task 14
buyer direct completion from CONFIRMED                 -> Tasks 12, 14
Delivery/Service issues                                -> Task 15
auto-completion                                        -> Task 16
pending expiration                                     -> Task 16
issue timeout                                          -> Task 16
inventory release/commit                               -> Tasks 8–16
append-only Order events                               -> Task 5
transactional outbox intent                            -> Tasks 5, 9–16
idempotency                                            -> Task 17
concurrency/race tests                                 -> Task 18
OpenAPI/generated client                               -> Task 19
minimal buyer/seller UI                                -> Task 19
```

## Type consistency

The implementation must use exactly:

```text
OrderStatus
OrderAction
OrderEventType
OrderActorType
OrderAccess
OrderPolicyProvider
OrderPolicySnapshot
OrderCommandResult
```

Do not invent parallel status/action enums in controller or frontend handwritten types.

## Migration handoff

Plan 05 ends at:

```text
V010__order_lifecycle.sql
```

Plan 06 must start at:

```text
V011
```

and may then:

```text
create Need tables
expand checkout_sessions.source_type to NEED_REQUEST
add FK constraints from orders.source_need_request_id/source_need_response_item_id
react to ORDER_CONFIRMED to mark source Need FULFILLED
```

---

# Execution Handoff

When Plan 05 implementation begins, execute it in an isolated worktree created by the repository's established superpowers workflow.

Recommended execution mode:

```text
1. Subagent-Driven Development
   - fresh subagent per Task
   - specification review after each Task
   - code-quality review after spec review passes
   - run focused tests before every completion claim

2. Inline Executing Plans
   - acceptable when subagents are not used
   - execute Tasks in dependency order
   - stop at review checkpoints
   - never skip fresh verification
```

Do not start Plan 06 implementation until Task 20 acceptance evidence is green.
