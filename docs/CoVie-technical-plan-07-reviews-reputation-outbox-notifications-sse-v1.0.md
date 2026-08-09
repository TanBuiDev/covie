# CoVie Plan 07 — Reviews, Reputation, Outbox, Notifications & SSE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement CoVie V1 trust and event-delivery layer: verified-purchase Reviews, rebuildable rating aggregates, outcome-based buyer/merchant Reputation, transactional Outbox claiming/retry, database-backed Notifications, best-effort SSE, and minimal web surfaces that refetch authoritative REST state.

**Architecture:** Keep Reviews, Reputation, Notifications and Outbox processing inside the Spring Boot modular monolith. Review creation is synchronous and idempotent; the review row and rating aggregate update commit together, then a lightweight `REVIEW_CREATED` outbox intent is appended. Reputation is an asynchronous projection from authoritative Order state: each relevant outbox event recomputes the current contribution for one Order and diffs it against a persisted `reputation_order_outcomes` row, so `BUYER_NO_SHOW -> NO_SHOW_CONTESTED -> COMPLETED` never double-counts. Outbox database side effects run under one PostgreSQL transaction per event using `FOR UPDATE SKIP LOCKED`; SSE is emitted only after commit and remains best-effort.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1, Spring MVC, Spring Data JPA, PostgreSQL 18, Flyway, Testcontainers/PostGIS, Jackson, Jakarta Validation, Spring `SseEmitter`, springdoc/OpenAPI, Next.js 16.2, React 19, TypeScript strict, generated `@covie/api-client`, Vitest/React Testing Library, Docker Compose.

## Global Constraints

- Plans 01–06 are prerequisites and are not redesigned here.
- Public API base path remains `/api/v1`.
- Spring Boot remains the authoritative business layer. Next.js never calculates review eligibility, rating aggregates, reputation formulas, notification recipients or Order trust rules.
- JPA entities are never serialized directly.
- Business IDs use UUIDv7.
- Absolute timestamps use UTC and application/domain code uses the injected `AppClock`.
- Flyway remains sequential with `outOfOrder=false`; Plan 06 ended at `V011__need_matching.sql`; Plan 07 creates `V012__trust_notifications_outbox.sql`; Plan 08 must start at `V013`.
- Never edit already-applied `V001`–`V011`.
- Reviews are allowed only for `COMPLETED` OrderItems owned by the current buyer.
- One `OrderItem` can have at most one Review.
- Review target merchant/catalog/variant is resolved from the authoritative OrderItem/Order. Client input never supplies authoritative review targets.
- Rating and Reputation are separate. Rating is subjective Review data; Reputation is objective transaction-outcome projection.
- Review rows and rating aggregates remain rebuildable. `reviews` is the rating source of truth.
- `orders + order_events` remain the reputation source of truth. `reputation_order_outcomes` and stats tables are projections only.
- Pre-confirm rejection, pre-confirm buyer cancellation and pre-confirm expiry do not enter completion-rate denominators.
- `COMPLETED` is success for buyer and merchant.
- `BUYER_NO_SHOW` is negative for buyer only.
- `CANCELLED_BY_BUYER_AFTER_CONFIRMATION` is negative for buyer only.
- `CANCELLED_BY_SELLER_AFTER_CONFIRMATION` is negative for merchant only.
- `CLOSED_WITH_ISSUE` and unresolved `NO_SHOW_CONTESTED` are excluded for both sides.
- Reputation projection must support contribution changes for one Order without double-counting.
- Reputation rate is stored/read in basis points (`10000 = 100.00%`); UI formatting is not authoritative.
- The buyer sees merchant completion summary. Seller/provider sees buyer completion summary plus a warning derived from backend policy. Neither side sees the other party's private transaction history.
- Plan 07 uses bootstrap property-backed `ReviewPolicyProvider` and `ReputationPolicyProvider`; Plan 08 replaces their business-setting resolution with scoped `SystemSetting` implementations.
- Existing Outbox rows from Plans 03–06 are consumed; Plan 07 does not replace `OutboxEventWriter` or change the guarantee that business state + outbox intent are written in one transaction.
- Outbox processing assumes at-least-once attempts. Database handlers must be idempotent.
- Outbox processing makes no external network call while a database Outbox row is locked.
- Outbox DB processing uses one event per `REQUIRES_NEW` transaction. A poll cycle may process up to a configured bounded count.
- `FOR UPDATE SKIP LOCKED` is the multi-replica correctness mechanism for Outbox claiming. No global JVM/distributed lock is required.
- SSE is best-effort and never authoritative. Missed or duplicate SSE signals are repaired by REST refetch.
- V1 does not provide durable `Last-Event-ID` replay.
- SSE payloads contain IDs/version/status hints only; never full private Order/Need/Review snapshots.
- Notification inbox rows are authoritative for notification history and read state.
- Notification UI text is localized by the frontend from stable `notificationType` + structured `data`. Database `title/body` are fallback content, not frontend branching keys.
- V1 implements IN_APP notification delivery. PUSH/EMAIL transport is not implemented here.
- No Kafka/RabbitMQ, WebSocket chat, service booking calendar, payment settlement, escrow/refund or transaction arbitration is introduced.
- Redis is not authoritative for reviews, reputation, notifications or Outbox progress. Cross-node SSE fanout may be added in Plan 10 without changing the REST/SSE contract.
- All privacy rules from Plans 02–06 remain in force.

---

# Deliverable Gate

Plan 07 is ready for implementation review only when the plan produces all of these independently testable outcomes:

```text
V012 applies cleanly after V011
Review create is idempotent and only accepts buyer-owned COMPLETED OrderItems
UNIQUE(order_item_id) prevents a second review
review-disable policy blocks new review rows without deleting old rows
rating aggregates update in the review transaction and can be rebuilt from reviews
public item/merchant review feeds expose safe reviewer identity only
reputation outcome classifier covers every canonical OrderStatus
BUYER_NO_SHOW -> NO_SHOW_CONTESTED -> COMPLETED produces negative -> excluded -> success by diff, never cumulative double-count
seller cancellation affects merchant only
buyer post-confirm cancellation affects buyer only
CLOSED_WITH_ISSUE excludes both sides
Order detail shows actor-appropriate trust summary
outbox worker uses PostgreSQL FOR UPDATE SKIP LOCKED
outbox handler DB effects and PUBLISHED status commit atomically
failed outbox processing records bounded retry without overwriting a concurrently PUBLISHED row
notifications are deduplicated by source event + recipient + type
notification list uses opaque cursor pagination
mark-read and mark-all-read are naturally idempotent
ORDER/NEED/REVIEW outbox events create the intended in-app notifications
GET /api/v1/me/events authenticates and streams lightweight SSE
SSE fires only after DB commit and reconnect starts with sync-required
frontend treats SSE as invalidation and refetches REST
OpenAPI/generated client contains Review + Notification contracts
Plan 08 migration handoff is V013
```

---

# Inputs and Stable Boundaries from Earlier Plans

Plan 03 already provides:

```text
vn.covie.shared.id.UuidV7Generator
vn.covie.shared.time.AppClock
vn.covie.shared.security.CurrentActorProvider
vn.covie.shared.idempotency.IdempotencyExecutor
vn.covie.shared.outbox.OutboxEventWriter
vn.covie.shared.outbox.OutboxEventIntent
vn.covie.shared.outbox.OutboxEventEntity
vn.covie.shared.outbox.OutboxEventRepository
vn.covie.shared.outbox.JpaOutboxEventWriter
vn.covie.media.application.MediaUrlResolver
vn.covie.merchants.persistence.MerchantMembershipRepository
vn.covie.merchants.persistence.MerchantMemberLocationRepository
vn.covie.merchants.security.MerchantAccessPolicy
```

Plan 04–05 provide:

```text
vn.covie.orders.model.OrderEntity
vn.covie.orders.model.OrderItemEntity
vn.covie.orders.model.OrderStatus
vn.covie.orders.model.OrderEventType
vn.covie.orders.persistence.OrderRepository
vn.covie.orders.persistence.OrderItemRepository
vn.covie.orders.persistence.OrderEventRepository
vn.covie.orders.application.OrderReadService
vn.covie.orders.security.OrderAccessPolicy
vn.covie.orders.application.OrderOutboxEvents
```

Stable Order outbox event names from Plan 05:

```text
ORDER_CONFIRMED
ORDER_REJECTED
ORDER_CANCELLED
ORDER_EXPIRED
ORDER_CANCELLATION_REQUESTED
ORDER_CANCELLATION_RESPONDED
ORDER_FULFILLMENT_UPDATED
ORDER_COMPLETED
ORDER_ISSUE_REPORTED
ORDER_CLOSED_WITH_ISSUE
```

Plan 06 provides:

```text
vn.covie.needs.application.NeedMatchingService
vn.covie.needs.application.NeedReadService
vn.covie.needs.persistence.NeedRequestRepository
vn.covie.needs.persistence.NeedResponseRepository
vn.covie.needs.persistence.NeedResponseItemRepository
vn.covie.needs.model.NeedRequestEntity
vn.covie.needs.model.NeedResponseEntity
vn.covie.needs.model.NeedStatus
```

Stable Need outbox event names from Plan 06:

```text
NEED_CREATED
NEED_CANCELLED
NEED_RESPONSE_CREATED
NEED_RESPONSE_UPDATED
NEED_RESPONSE_WITHDRAWN
NEED_FULFILLED
NEED_EXPIRED
```

Plan 07 adds the stable boundaries:

```text
vn.covie.reviews.application.ReviewService
vn.covie.reviews.application.ReviewReadService
vn.covie.reviews.application.RatingProjectionService
vn.covie.reviews.application.ReviewPolicyProvider
vn.covie.reputation.application.ReputationOutcomeClassifier
vn.covie.reputation.application.ReputationProjectionService
vn.covie.reputation.application.ReputationReadService
vn.covie.reputation.application.ReputationPolicyProvider
vn.covie.notifications.application.NotificationService
vn.covie.notifications.application.NotificationReadService
vn.covie.notifications.application.MerchantNotificationRecipientResolver
vn.covie.outbox.application.OutboxProcessor
vn.covie.outbox.application.OutboxEventHandler
vn.covie.realtime.SseConnectionRegistry
vn.covie.realtime.SseSignalPublisher
```

---

# Repository Map Added by Plan 07

```text
apps/api/src/main/java/vn/covie/
├── reviews/
│   ├── api/
│   │   ├── MeReviewController.java
│   │   ├── PublicReviewController.java
│   │   └── dto/
│   ├── application/
│   │   ├── ReviewService.java
│   │   ├── ReviewReadService.java
│   │   ├── ReviewPolicyProvider.java
│   │   ├── PropertyReviewPolicyProvider.java
│   │   ├── RatingProjectionService.java
│   │   ├── RatingRebuildService.java
│   │   └── ReviewCursorCodec.java
│   ├── model/
│   │   ├── ReviewEntity.java
│   │   ├── ReviewStatus.java
│   │   ├── CatalogItemRatingStatsEntity.java
│   │   └── MerchantRatingStatsEntity.java
│   └── persistence/
│       ├── ReviewRepository.java
│       ├── CatalogItemRatingStatsRepository.java
│       └── MerchantRatingStatsRepository.java
├── reputation/
│   ├── application/
│   │   ├── ReputationOutcomeClassifier.java
│   │   ├── ReputationProjectionService.java
│   │   ├── ReputationRebuildService.java
│   │   ├── ReputationReadService.java
│   │   ├── ReputationPolicyProvider.java
│   │   └── PropertyReputationPolicyProvider.java
│   ├── model/
│   │   ├── BuyerReputationOutcome.java
│   │   ├── MerchantReputationOutcome.java
│   │   ├── ReputationOrderOutcomeEntity.java
│   │   ├── UserReputationStatsEntity.java
│   │   └── MerchantReputationStatsEntity.java
│   └── persistence/
│       ├── ReputationOrderOutcomeRepository.java
│       ├── UserReputationStatsRepository.java
│       └── MerchantReputationStatsRepository.java
├── notifications/
│   ├── api/
│   │   ├── MeNotificationController.java
│   │   └── dto/
│   ├── application/
│   │   ├── NotificationService.java
│   │   ├── NotificationReadService.java
│   │   ├── NotificationCursorCodec.java
│   │   ├── MerchantNotificationRecipientResolver.java
│   │   ├── OrderNotificationHandler.java
│   │   ├── NeedNotificationHandler.java
│   │   └── ReviewNotificationHandler.java
│   ├── model/
│   │   ├── NotificationEntity.java
│   │   ├── NotificationDeliveryEntity.java
│   │   ├── NotificationType.java
│   │   ├── NotificationChannel.java
│   │   └── NotificationDeliveryStatus.java
│   └── persistence/
│       ├── NotificationRepository.java
│       ├── NotificationDeliveryRepository.java
│       ├── NeedAudienceRepository.java
│       └── JdbcNeedAudienceRepository.java
├── outbox/
│   └── application/
│       ├── OutboxEventContext.java
│       ├── OutboxEventHandler.java
│       ├── OutboxHandlerRegistry.java
│       ├── OutboxProcessor.java
│       ├── OutboxScheduler.java
│       ├── OutboxRetryPolicy.java
│       └── OutboxFailureService.java
└── realtime/
    ├── MeEventStreamController.java
    ├── SseConnectionRegistry.java
    ├── SseSignal.java
    ├── SseSignalPublisher.java
    └── SseHeartbeatScheduler.java

apps/api/src/main/resources/
├── application.yml
└── db/migration/
    └── V012__trust_notifications_outbox.sql

apps/web/
├── app/(buyer)/me/notifications/page.tsx
├── features/notifications/
│   ├── notification-api.ts
│   ├── notification-inbox.tsx
│   ├── covie-event-stream.ts
│   └── notification-types.ts
└── features/reviews/
    ├── review-api.ts
    ├── review-form.tsx
    └── review-list.tsx
```

---

# Task 1: Add V012 Trust, Projection and Notification Schema

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V012__trust_notifications_outbox.sql`
- Test: `apps/api/src/test/java/vn/covie/trust/persistence/Plan07SchemaMigrationTest.java`

**Interfaces:**
- Consumes: clean schema through V011.
- Produces: Review source tables, rating/reputation projections, database notification inbox, IN_APP delivery metadata, and indexes required by Plan 07.
- Migration handoff: Plan 08 starts at V013.

- [ ] **Step 1: Write the migration test first**

Create `Plan07SchemaMigrationTest` and assert the required tables exist:

```java
@Test
void createsPlan07Tables() {
    assertThat(tableNames()).contains(
            "reviews",
            "catalog_item_rating_stats",
            "merchant_rating_stats",
            "reputation_order_outcomes",
            "user_reputation_stats",
            "merchant_reputation_stats",
            "notifications",
            "notification_deliveries");
}
```

Also assert `flyway_schema_history` contains version `12` exactly once.

Run:

```bash
cd apps/api
./mvnw -Dtest=Plan07SchemaMigrationTest test
```

Expected: FAIL because V012/tables do not exist.

- [ ] **Step 2: Create `reviews` and rating projection tables**

Use this schema:

```sql
create table reviews (
    id uuid primary key,
    order_item_id uuid not null references order_items(id) on delete restrict,
    order_id uuid not null references orders(id) on delete restrict,
    buyer_user_id uuid not null references users(id) on delete restrict,
    catalog_item_id uuid not null references catalog_items(id) on delete restrict,
    variant_id uuid null references variants(id) on delete restrict,
    merchant_profile_id uuid not null references merchant_profiles(id) on delete restrict,
    rating smallint not null,
    comment text null,
    review_status varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    constraint ux_reviews_order_item unique(order_item_id),
    constraint ck_reviews_rating check (rating between 1 and 5),
    constraint ck_reviews_status check (review_status in ('ACTIVE','HIDDEN','DELETED'))
);

create index ix_reviews_catalog_visible
    on reviews(catalog_item_id, created_at desc, id desc)
    where review_status = 'ACTIVE' and deleted_at is null;

create index ix_reviews_merchant_visible
    on reviews(merchant_profile_id, created_at desc, id desc)
    where review_status = 'ACTIVE' and deleted_at is null;

create table catalog_item_rating_stats (
    catalog_item_id uuid primary key references catalog_items(id) on delete restrict,
    review_count bigint not null default 0,
    rating_sum bigint not null default 0,
    average_rating numeric(4,2) not null default 0,
    updated_at timestamptz not null,
    constraint ck_catalog_rating_count check (review_count >= 0),
    constraint ck_catalog_rating_sum check (rating_sum >= 0),
    constraint ck_catalog_average_rating check (average_rating between 0 and 5)
);

create table merchant_rating_stats (
    merchant_profile_id uuid primary key references merchant_profiles(id) on delete restrict,
    review_count bigint not null default 0,
    rating_sum bigint not null default 0,
    average_rating numeric(4,2) not null default 0,
    updated_at timestamptz not null,
    constraint ck_merchant_rating_count check (review_count >= 0),
    constraint ck_merchant_rating_sum check (rating_sum >= 0),
    constraint ck_merchant_average_rating check (average_rating between 0 and 5)
);
```

Do not add a review edit/delete endpoint in Plan 07. `HIDDEN/DELETED` exist so Plan 08 moderation can call the same projection service without a migration rewrite.

- [ ] **Step 3: Create reputation projection tables**

```sql
create table reputation_order_outcomes (
    order_id uuid primary key references orders(id) on delete restrict,
    buyer_user_id uuid not null references users(id) on delete restrict,
    merchant_profile_id uuid not null references merchant_profiles(id) on delete restrict,
    buyer_outcome varchar(40) not null,
    merchant_outcome varchar(40) not null,
    source_order_status varchar(60) not null,
    source_outbox_event_id uuid null references outbox_events(id) on delete set null,
    updated_at timestamptz not null,
    constraint ck_rep_buyer_outcome check (buyer_outcome in (
        'NONE','SUCCESS','NEGATIVE_NO_SHOW','NEGATIVE_CANCEL_AFTER_CONFIRM','EXCLUDED'
    )),
    constraint ck_rep_merchant_outcome check (merchant_outcome in (
        'NONE','SUCCESS','NEGATIVE_SELLER_CANCEL','EXCLUDED'
    ))
);

create table user_reputation_stats (
    user_id uuid primary key references users(id) on delete restrict,
    eligible_order_count bigint not null default 0,
    completed_order_count bigint not null default 0,
    buyer_no_show_count bigint not null default 0,
    buyer_cancel_after_confirm_count bigint not null default 0,
    excluded_issue_count bigint not null default 0,
    completion_rate_basis_points integer not null default 0,
    updated_at timestamptz not null,
    constraint ck_user_rep_nonnegative check (
        eligible_order_count >= 0
        and completed_order_count >= 0
        and buyer_no_show_count >= 0
        and buyer_cancel_after_confirm_count >= 0
        and excluded_issue_count >= 0
    ),
    constraint ck_user_rep_rate check (completion_rate_basis_points between 0 and 10000)
);

create table merchant_reputation_stats (
    merchant_profile_id uuid primary key references merchant_profiles(id) on delete restrict,
    eligible_order_count bigint not null default 0,
    completed_order_count bigint not null default 0,
    seller_cancel_after_confirm_count bigint not null default 0,
    excluded_issue_count bigint not null default 0,
    completion_rate_basis_points integer not null default 0,
    updated_at timestamptz not null,
    constraint ck_merchant_rep_nonnegative check (
        eligible_order_count >= 0
        and completed_order_count >= 0
        and seller_cancel_after_confirm_count >= 0
        and excluded_issue_count >= 0
    ),
    constraint ck_merchant_rep_rate check (completion_rate_basis_points between 0 and 10000)
);

create index ix_reputation_outcome_buyer on reputation_order_outcomes(buyer_user_id);
create index ix_reputation_outcome_merchant on reputation_order_outcomes(merchant_profile_id);
```

`source_outbox_event_id` is diagnostics only. Rebuilds are allowed to set it null.

- [ ] **Step 4: Create notifications + delivery tables with source-event dedupe**

```sql
create table notifications (
    id uuid primary key,
    user_id uuid not null references users(id) on delete restrict,
    notification_type varchar(60) not null,
    title varchar(220) not null,
    body text not null,
    related_entity_type varchar(40) null,
    related_entity_id uuid null,
    payload jsonb null,
    source_outbox_event_id uuid null references outbox_events(id) on delete set null,
    read_at timestamptz null,
    created_at timestamptz not null,
    expires_at timestamptz null
);

create unique index ux_notification_source_recipient_type
    on notifications(source_outbox_event_id, user_id, notification_type)
    where source_outbox_event_id is not null;

create index ix_notifications_user_cursor
    on notifications(user_id, created_at desc, id desc);

create index ix_notifications_user_unread
    on notifications(user_id, created_at desc)
    where read_at is null;

create table notification_deliveries (
    id uuid primary key,
    notification_id uuid not null references notifications(id) on delete cascade,
    channel varchar(20) not null,
    delivery_status varchar(20) not null,
    attempted_at timestamptz null,
    delivered_at timestamptz null,
    failure_code varchar(80) null,
    constraint ck_notification_channel check (channel in ('IN_APP','PUSH','EMAIL')),
    constraint ck_notification_delivery_status check (delivery_status in ('PENDING','DELIVERED','FAILED')),
    unique(notification_id, channel)
);
```

Plan 07 creates only `IN_APP + DELIVERED`. PUSH/EMAIL transports remain absent.

- [ ] **Step 5: Add constraint/invariant assertions**

Add SQL assertions in the migration test:

```sql
select count(*) from information_schema.table_constraints
where table_name='reviews' and constraint_type='UNIQUE';

select indexname from pg_indexes
where tablename='notifications' and indexname='ux_notification_source_recipient_type';
```

Also insert invalid fixtures and assert PostgreSQL rejects:
- rating `0` and `6`;
- unknown review status;
- completion rate `10001`;
- duplicate notification `(source_event,user,type)`.

- [ ] **Step 6: Run migration verification**

```bash
./mvnw -Dtest=Plan07SchemaMigrationTest test
```

Expected: PASS with Flyway `V001` through `V012`.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/resources/db/migration/V012__trust_notifications_outbox.sql \
        apps/api/src/test/java/vn/covie/trust/persistence/Plan07SchemaMigrationTest.java
git commit -m "feat(trust): add review reputation and notification schema"
```

---


# Task 2: Map Review and Rating Persistence Models

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reviews/model/ReviewStatus.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/model/ReviewEntity.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/model/CatalogItemRatingStatsEntity.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/model/MerchantRatingStatsEntity.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/persistence/ReviewRepository.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/persistence/CatalogItemRatingStatsRepository.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/persistence/MerchantRatingStatsRepository.java`
- Test: `apps/api/src/test/java/vn/covie/reviews/persistence/ReviewRepositoryIntegrationTest.java`

**Interfaces:**
- Produces persistence primitives for Review create/read and synchronous rating projection.

- [ ] **Step 1: Define exact status enum**

```java
public enum ReviewStatus {
    ACTIVE,
    HIDDEN,
    DELETED
}
```

- [ ] **Step 2: Write repository RED tests**

Tests must prove:

```java
@Test void orderItemCanOnlyHaveOneReview() { }
@Test void findsActiveCatalogReviewsNewestFirst() { }
@Test void hiddenReviewIsNotReturnedByPublicQuery() { }
@Test void findsActiveMerchantReviewsNewestFirst() { }
```

Run:

```bash
cd apps/api
./mvnw -Dtest=ReviewRepositoryIntegrationTest test
```

Expected: compilation FAIL because Review model/repositories do not exist.

- [ ] **Step 3: Map `ReviewEntity` without Order/Catalog object cascades**

Required fields/methods:

```java
@Entity
@Table(name = "reviews")
public class ReviewEntity {
    @Id
    private UUID id;
    private UUID orderItemId;
    private UUID orderId;
    private UUID buyerUserId;
    private UUID catalogItemId;
    private UUID variantId;
    private UUID merchantProfileId;
    private short rating;
    private String comment;

    @Enumerated(EnumType.STRING)
    private ReviewStatus reviewStatus;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    protected ReviewEntity() {}

    public static ReviewEntity active(
            UUID id,
            UUID orderItemId,
            UUID orderId,
            UUID buyerUserId,
            UUID catalogItemId,
            UUID variantId,
            UUID merchantProfileId,
            short rating,
            String comment,
            Instant now) {
        ReviewEntity r = new ReviewEntity();
        r.id = id;
        r.orderItemId = orderItemId;
        r.orderId = orderId;
        r.buyerUserId = buyerUserId;
        r.catalogItemId = catalogItemId;
        r.variantId = variantId;
        r.merchantProfileId = merchantProfileId;
        r.rating = rating;
        r.comment = comment;
        r.reviewStatus = ReviewStatus.ACTIVE;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public boolean contributesToRating() {
        return reviewStatus == ReviewStatus.ACTIVE && deletedAt == null;
    }
}
```

Use explicit column names matching V012. Do not add `CascadeType.REMOVE`.

- [ ] **Step 4: Map rating stats with lockable rows**

Both stats entities expose:

```java
void add(short rating, Instant now)
void remove(short rating, Instant now)
long reviewCount()
long ratingSum()
BigDecimal averageRating()
```

Calculation:

```java
private static BigDecimal average(long sum, long count) {
    if (count == 0) return BigDecimal.ZERO.setScale(2);
    return BigDecimal.valueOf(sum)
            .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
}
```

`remove(...)` throws `IllegalStateException` if applying it would make count/sum negative; this is an internal invariant failure, not a public validation path.

- [ ] **Step 5: Add repository queries**

`ReviewRepository` must have:

```java
Optional<ReviewEntity> findByOrderItemId(UUID orderItemId);
boolean existsByOrderItemId(UUID orderItemId);
```

Public feeds use keyset native/JPA queries equivalent to:

```sql
where catalog_item_id = :itemId
  and review_status = 'ACTIVE'
  and deleted_at is null
  and (
      :afterCreatedAt is null
      or (created_at, id) < (:afterCreatedAt, :afterId)
  )
order by created_at desc, id desc
limit :limitPlusOne
```

Rating repositories expose a `PESSIMISTIC_WRITE` lookup plus normal save.

- [ ] **Step 6: Run tests**

```bash
./mvnw -Dtest=ReviewRepositoryIntegrationTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/reviews/model \
        apps/api/src/main/java/vn/covie/reviews/persistence \
        apps/api/src/test/java/vn/covie/reviews/persistence
git commit -m "feat(reviews): map review and rating persistence"
```

---


# Task 3: Implement Review Policy and Transactional Rating Projection

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reviews/application/ReviewPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/application/PropertyReviewPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/application/RatingProjectionService.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/application/RatingRebuildService.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Test: `apps/api/src/test/java/vn/covie/reviews/application/RatingProjectionServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/reviews/application/RatingRebuildServiceIntegrationTest.java`

**Interfaces:**
- Produces the only rating formula used by Review create and later Plan 08 hide/restore moderation.

- [ ] **Step 1: Define policy boundary**

```java
public interface ReviewPolicyProvider {
    boolean creationEnabled();
}
```

Property adapter:

```yaml
covie:
  reviews:
    creation-enabled: true
```

`PropertyReviewPolicyProvider` reads only this bootstrap property. Plan 08 will replace the business-setting resolution behind the same interface.

- [ ] **Step 2: Write projection RED tests**

Fixtures:

```text
first active 5-star review -> item count=1 sum=5 avg=5.00; merchant same
then active 3-star review -> count=2 sum=8 avg=4.00
remove 5-star contribution -> count=1 sum=3 avg=3.00
remove final contribution -> count=0 sum=0 avg=0.00
```

Also race two reviews for different OrderItems of the same CatalogItem/Merchant and assert final counts are exactly two.

- [ ] **Step 3: Implement stable lock/create order**

`RatingProjectionService.add(ReviewEntity review)` and `remove(ReviewEntity review)` run with `Propagation.MANDATORY`.

Use this lock sequence every time:

```text
ensure catalog_item_rating_stats row with INSERT ... ON CONFLICT DO NOTHING
lock catalog stats row FOR UPDATE
ensure merchant_rating_stats row with INSERT ... ON CONFLICT DO NOTHING
lock merchant stats row FOR UPDATE
apply both deltas
```

Use repository native upsert helpers for the `ensure` step. Never create one stats row after locking the other in the opposite order.

Public methods:

```java
@Transactional(propagation = Propagation.MANDATORY)
public void add(ReviewEntity review)

@Transactional(propagation = Propagation.MANDATORY)
public void onVisibilityTransition(
        ReviewEntity review,
        ReviewStatus oldStatus,
        ReviewStatus newStatus)
```

`onVisibilityTransition` computes:
- non-contributing -> ACTIVE: `add`;
- ACTIVE -> non-contributing: `remove`;
- otherwise no delta.

- [ ] **Step 4: Implement rebuild from Review source of truth**

`RatingRebuildService` exposes:

```java
@Transactional
public void rebuildCatalogItem(UUID catalogItemId)

@Transactional
public void rebuildMerchant(UUID merchantProfileId)
```

Each method performs one aggregate query over `reviews where review_status='ACTIVE' and deleted_at is null`, then upserts the stats row to exact count/sum/average.

No public/admin endpoint is added in Plan 07. Plan 08 may call this service from admin tooling.

- [ ] **Step 5: Verify rebuild repairs intentionally corrupted stats**

Test:
1. create two active Reviews;
2. manually corrupt stats with `JdbcTemplate`;
3. call rebuild;
4. assert exact source-derived values.

- [ ] **Step 6: Run**

```bash
./mvnw -Dtest=RatingProjectionServiceIntegrationTest,RatingRebuildServiceIntegrationTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/reviews/application \
        apps/api/src/main/resources/application.yml \
        apps/api/src/test/java/vn/covie/reviews/application
git commit -m "feat(reviews): project rebuildable rating stats"
```

---


# Task 4: Implement Idempotent Review Creation

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reviews/application/ReviewService.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/application/ReviewOutboxEvents.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/dto/CreateReviewRequest.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/dto/ReviewResponse.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/MeReviewController.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/reviews/application/ReviewServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/reviews/api/MeReviewControllerIntegrationTest.java`

**Interfaces:**
- Produces `POST /api/v1/me/reviews`.
- Emits `REVIEW_CREATED` Outbox intent in the same transaction.

- [ ] **Step 1: Define request/response**

```java
public record CreateReviewRequest(
        @NotNull UUID orderItemId,
        @Min(1) @Max(5) int rating,
        @Size(max = 3000) String comment) {}
```

```java
public record ReviewResponse(
        UUID id,
        UUID orderItemId,
        int rating,
        String comment,
        String status,
        Instant createdAt) {}
```

- [ ] **Step 2: Write eligibility RED tests**

Required tests:

```java
@Test void completedOwnedOrderItemCanBeReviewed() {}
@Test void confirmedOrderCannotBeReviewed() {}
@Test void closedWithIssueCannotBeReviewed() {}
@Test void anotherBuyersOrderItemReturns404() {}
@Test void clientCannotSelectMerchantOrCatalogTarget() {}
@Test void secondReviewForSameOrderItemConflicts() {}
@Test void disabledReviewPolicyRejectsCreationAndPreservesExistingRows() {}
@Test void sameIdempotencyKeySameBodyReturnsSameReview() {}
@Test void sameIdempotencyKeyDifferentBodyConflicts() {}
```

- [ ] **Step 3: Define error codes**

Add:

```text
REVIEW_CREATION_DISABLED       -> 422
REVIEW_ORDER_NOT_COMPLETED     -> 409
REVIEW_ALREADY_EXISTS          -> 409
```

Private foreign OrderItem returns existing `RESOURCE_NOT_FOUND` 404.

- [ ] **Step 4: Implement one authoritative transaction**

Public signature:

```java
public ReviewResponse create(CreateReviewRequest request, String idempotencyKey)
```

Call:

```java
return idempotency.execute(
        actor.userId(),
        "review.create:" + request.orderItemId(),
        idempotencyKey,
        request,
        201,
        ReviewResponse.class,
        () -> createInsideTransaction(request));
```

Inside `createInsideTransaction`:

```text
require ReviewPolicyProvider.creationEnabled
load OrderItem + owning Order
if Order/buyer relation is not visible to current actor -> 404
require Order.status == COMPLETED
require no Review for orderItem
derive orderId, buyerUserId, catalogItemId, variantId, merchantProfileId from Order/OrderItem
insert ACTIVE Review
RatingProjectionService.add(review)
append REVIEW_CREATED outbox with reviewId/orderId/orderItemId/merchantProfileId
flush
return ReviewResponse
```

Never accept merchant/catalog/variant identifiers from request body.

- [ ] **Step 5: Handle database uniqueness race**

Two different idempotency keys racing to review the same OrderItem must not leak a raw constraint exception. Translate unique violation on `reviews.order_item_id` to `409 REVIEW_ALREADY_EXISTS`.

Integration test uses two executor threads and one completed OrderItem:
- one returns 201;
- one returns 409;
- exactly one Review row;
- rating count increments once;
- exactly one `REVIEW_CREATED` outbox row.

- [ ] **Step 6: Expose controller**

```java
@PostMapping("/api/v1/me/reviews")
@ResponseStatus(HttpStatus.CREATED)
public ReviewResponse create(
        @Valid @RequestBody CreateReviewRequest request,
        @RequestHeader("Idempotency-Key") String key) {
    return service.create(request, key);
}
```

Follow the existing CSRF/browser mutation policy from Plan 02.

- [ ] **Step 7: Run**

```bash
./mvnw -Dtest=ReviewServiceIntegrationTest,MeReviewControllerIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/reviews \
        apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java \
        apps/api/src/test/java/vn/covie/reviews
git commit -m "feat(reviews): create verified purchase reviews"
```

---


# Task 5: Implement Public Review Feeds and Safe Reviewer Identity

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reviews/application/ReviewReadService.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/application/ReviewCursor.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/application/ReviewCursorCodec.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/PublicReviewController.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/dto/PublicReviewResponse.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/dto/PublicReviewerResponse.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/dto/ReviewPageResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/users/model/UserEntity.java`
- Test: `apps/api/src/test/java/vn/covie/reviews/application/ReviewReadServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/reviews/api/PublicReviewControllerIntegrationTest.java`

**Interfaces:**
- Produces:
  - `GET /api/v1/catalog/items/{itemId}/reviews`
  - `GET /api/v1/merchants/{merchantId}/reviews`

- [ ] **Step 1: Define opaque cursor**

```java
public record ReviewCursor(Instant createdAt, UUID reviewId) {}
```

`ReviewCursorCodec` serializes a versioned JSON payload to URL-safe Base64. Invalid/malformed/version-mismatch cursors return `422 VALIDATION_FAILED`; client never parses cursor contents.

- [ ] **Step 2: Define safe public DTO**

```java
public record PublicReviewerResponse(
        String displayName,
        String avatarUrl) {}
```

```java
public record PublicReviewResponse(
        UUID id,
        int rating,
        String comment,
        PublicReviewerResponse reviewer,
        boolean verifiedPurchase,
        Instant createdAt) {}
```

Do not expose:
- buyer user ID;
- email/phone;
- Order ID/OrderItem ID;
- buyer addresses;
- buyer reputation history.

`verifiedPurchase` is hardcoded `true` only after loading a persisted Review whose source is an OrderItem.

- [ ] **Step 3: Implement cursor reads**

Rules:

```text
limit range = 1..50, default 20
query limit = requested + 1
sort = created_at DESC, id DESC
only ACTIVE + deleted_at IS NULL
no totalElements
```

Add the missing read-only accessor to the existing Plan 02 entity if it is not already present:

```java
public UUID getAvatarMediaId() {
    return avatarMediaId;
}
```

If `avatarMediaId` is non-null, resolve it through existing `MediaUrlResolver`; if missing/not READY, return null avatar URL rather than leaking storage keys.

- [ ] **Step 4: Add rating summary to page metadata**

`ReviewPageResponse`:

```java
public record ReviewPageResponse(
        List<PublicReviewResponse> items,
        ReviewPageInfo page,
        RatingSummary rating) {}
```

`RatingSummary` includes:

```java
public record RatingSummary(long reviewCount, BigDecimal averageRating) {}
```

Read from projection table; if no row, return `0 / 0.00`.

- [ ] **Step 5: Write privacy/visibility tests**

Assert:
- hidden/deleted Review absent;
- reviewer email/phone/address fields do not exist in serialized JSON;
- second cursor page has no duplicate under equal timestamps;
- merchant feed includes active Reviews from all its CatalogItems;
- item feed does not include Reviews of another item.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=ReviewReadServiceIntegrationTest,PublicReviewControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/reviews apps/api/src/test/java/vn/covie/reviews
git commit -m "feat(reviews): expose public verified review feeds"
```

---


# Task 6: Map Reputation Projection Persistence and Define Pure Outcome Classifier

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reputation/model/BuyerReputationOutcome.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/model/MerchantReputationOutcome.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/model/ReputationOrderOutcomeEntity.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/model/UserReputationStatsEntity.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/model/MerchantReputationStatsEntity.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/persistence/ReputationOrderOutcomeRepository.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/persistence/UserReputationStatsRepository.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/persistence/MerchantReputationStatsRepository.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/application/ReputationContribution.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/application/ReputationOutcomeClassifier.java`
- Test: `apps/api/src/test/java/vn/covie/reputation/application/ReputationOutcomeClassifierTest.java`

**Interfaces:**
- Produces a deterministic `OrderStatus -> buyer/merchant contribution` function with no database access.

- [ ] **Step 1: Define exact enums**

```java
public enum BuyerReputationOutcome {
    NONE,
    SUCCESS,
    NEGATIVE_NO_SHOW,
    NEGATIVE_CANCEL_AFTER_CONFIRM,
    EXCLUDED
}
```

```java
public enum MerchantReputationOutcome {
    NONE,
    SUCCESS,
    NEGATIVE_SELLER_CANCEL,
    EXCLUDED
}
```

- [ ] **Step 2: Define immutable contribution**

```java
public record ReputationContribution(
        BuyerReputationOutcome buyer,
        MerchantReputationOutcome merchant) {}
```

- [ ] **Step 3: Write exhaustive parameterized classifier test**

Every canonical Plan 05 `OrderStatus` must appear in a case. Expected mapping:

```text
PENDING_CONFIRMATION                         -> NONE / NONE
CONFIRMED                                    -> NONE / NONE
OUT_FOR_DELIVERY                             -> NONE / NONE
READY_FOR_PICKUP                             -> NONE / NONE
AWAITING_BUYER_CONFIRMATION                  -> NONE / NONE
CANCELLATION_REQUESTED                       -> NONE / NONE
DELIVERY_ISSUE_REPORTED                      -> NONE / NONE
SERVICE_ISSUE_REPORTED                       -> NONE / NONE
BUYER_NO_SHOW                                -> NEGATIVE_NO_SHOW / NONE
NO_SHOW_CONTESTED                            -> EXCLUDED / EXCLUDED
COMPLETED                                    -> SUCCESS / SUCCESS
EXPIRED                                      -> NONE / NONE
REJECTED_BY_SELLER                           -> NONE / NONE
CANCELLED_BEFORE_CONFIRMATION                -> NONE / NONE
CANCELLED_BY_BUYER_AFTER_CONFIRMATION        -> NEGATIVE_CANCEL_AFTER_CONFIRM / NONE
CANCELLED_BY_SELLER_AFTER_CONFIRMATION       -> NONE / NEGATIVE_SELLER_CANCEL
CLOSED_WITH_ISSUE                            -> EXCLUDED / EXCLUDED
```

Test must fail if a new enum constant is added without a classifier case. Implement with an explicit switch expression without `default`.

- [ ] **Step 4: Implement classifier**

```java
public ReputationContribution classify(OrderStatus status) {
    return switch (status) {
        case BUYER_NO_SHOW ->
                new ReputationContribution(NEGATIVE_NO_SHOW, MerchantReputationOutcome.NONE);
        case NO_SHOW_CONTESTED, CLOSED_WITH_ISSUE ->
                new ReputationContribution(BuyerReputationOutcome.EXCLUDED, MerchantReputationOutcome.EXCLUDED);
        case COMPLETED ->
                new ReputationContribution(BuyerReputationOutcome.SUCCESS, MerchantReputationOutcome.SUCCESS);
        case CANCELLED_BY_BUYER_AFTER_CONFIRMATION ->
                new ReputationContribution(BuyerReputationOutcome.NEGATIVE_CANCEL_AFTER_CONFIRM, MerchantReputationOutcome.NONE);
        case CANCELLED_BY_SELLER_AFTER_CONFIRMATION ->
                new ReputationContribution(BuyerReputationOutcome.NONE, MerchantReputationOutcome.NEGATIVE_SELLER_CANCEL);
        case PENDING_CONFIRMATION, CONFIRMED, OUT_FOR_DELIVERY, READY_FOR_PICKUP,
             AWAITING_BUYER_CONFIRMATION, CANCELLATION_REQUESTED,
             DELIVERY_ISSUE_REPORTED, SERVICE_ISSUE_REPORTED,
             EXPIRED, REJECTED_BY_SELLER, CANCELLED_BEFORE_CONFIRMATION ->
                new ReputationContribution(BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE);
    };
}
```

Use static imports only if they do not obscure buyer vs merchant enum types.

- [ ] **Step 5: Map projection entities**

`ReputationOrderOutcomeEntity` stores exact old contribution for diffing. Stats entities expose adjustment methods but never accept negative resulting counters.

Completion rate:

```java
private static int basisPoints(long completed, long eligible) {
    if (eligible == 0) return 0;
    return Math.toIntExact((completed * 10_000L) / eligible);
}
```

Counts are `long`; multiplication is safe for realistic V1 volume but tests include large values below overflow. Do not use floating-point persistence.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=ReputationOutcomeClassifierTest test
git add apps/api/src/main/java/vn/covie/reputation apps/api/src/test/java/vn/covie/reputation
git commit -m "feat(reputation): classify order outcomes"
```

---


# Task 7: Implement Reputation Projection by Old→New Contribution Diff

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reputation/application/ReputationProjectionService.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/application/ReputationStatsLockService.java`
- Test: `apps/api/src/test/java/vn/covie/reputation/application/ReputationProjectionServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/reputation/application/ReputationProjectionConcurrencyTest.java`

**Interfaces:**
- Consumes authoritative current `OrderEntity`.
- Produces idempotent, reversible contribution updates for user + merchant reputation stats.

- [ ] **Step 1: Write transition-sequence tests before implementation**

Required sequence:

```text
Order READY_FOR_PICKUP -> no contribution
BUYER_NO_SHOW          -> buyer eligible +1, buyer no-show +1
NO_SHOW_CONTESTED      -> remove no-show eligible/negative; excluded +1 both
COMPLETED              -> remove excluded; completed+eligible +1 both
```

After the final step, assert:
- buyer eligible=1, completed=1, no_show=0, excluded=0, rate=10000;
- merchant eligible=1, completed=1, excluded=0, rate=10000;
- exactly one `reputation_order_outcomes` row.

Other mandatory tests:

```text
buyer post-confirm cancel -> buyer eligible=1, cancel=1, rate=0; merchant eligible=0
seller post-confirm cancel -> merchant eligible=1, seller_cancel=1, rate=0; buyer eligible=0
closed-with-issue -> eligible=0 both; excluded=1 both
reprocessing same current status -> zero counter delta
```

- [ ] **Step 2: Implement stats-row ensure + lock order**

First ensure a projection row exists for the Order using the **current authoritative Order identifiers** and a neutral contribution:

```sql
insert into reputation_order_outcomes(
    order_id, buyer_user_id, merchant_profile_id,
    buyer_outcome, merchant_outcome,
    source_order_status, source_outbox_event_id, updated_at)
values (
    :orderId, :buyerUserId, :merchantProfileId,
    'NONE', 'NONE',
    :currentStatus, :sourceEventId, :now)
on conflict (order_id) do nothing;
```

Then lock in this exact order:

```text
reputation_order_outcomes(order_id) FOR UPDATE
user_reputation_stats(user_id) FOR UPDATE
merchant_reputation_stats(merchant_profile_id) FOR UPDATE
```

Ensure the user/merchant stats rows with `INSERT ... ON CONFLICT DO NOTHING` immediately before their respective `FOR UPDATE` lookup. The neutral outcome-row upsert serializes two events racing on the same Order without a unique-key leak.

The user-table lock always occurs before merchant-table lock for every Order to prevent inconsistent lock ordering across transactions.

- [ ] **Step 3: Define delta functions**

For buyer old outcome, subtract:

```text
NONE                          -> nothing
SUCCESS                       -> eligible -1, completed -1
NEGATIVE_NO_SHOW              -> eligible -1, no_show -1
NEGATIVE_CANCEL_AFTER_CONFIRM -> eligible -1, cancel_after_confirm -1
EXCLUDED                      -> excluded_issue -1
```

Then add new outcome using the inverse signs.

Merchant:

```text
NONE                    -> nothing
SUCCESS                 -> eligible +/-1, completed +/-1
NEGATIVE_SELLER_CANCEL  -> eligible +/-1, seller_cancel +/-1
EXCLUDED                -> excluded_issue +/-1
```

Recompute basis points only after old removal + new addition are both applied.

- [ ] **Step 4: Implement projection entrypoint**

```java
@Transactional(propagation = Propagation.MANDATORY)
public void projectOrder(UUID orderId, UUID sourceOutboxEventId)
```

Flow:

```text
load current Order by ID
classify current Order.status
ensure neutral reputation_order_outcomes row ON CONFLICT DO NOTHING
lock that outcome row FOR UPDATE
read previous contribution from the locked row
if previous contribution equals current -> update diagnostics timestamp/source event only; no stats delta
otherwise ensure+lock stats rows
remove previous contribution
add current contribution
validate all counters >= 0
recompute both rates
upsert outcome row with current status/contribution/source event
```

Do not infer outcome from outbox event name. Outbox is only a trigger; current Order state is authoritative.

- [ ] **Step 5: Run concurrency test**

Create two Orders for the same buyer/merchant and process `COMPLETED` projections concurrently. Expected:
- no deadlock after bounded retries from test executor;
- eligible=2/completed=2 on both stats;
- two outcome rows;
- rate=10000.

Then call both projections again; values remain exactly two.

- [ ] **Step 6: Run**

```bash
./mvnw -Dtest=ReputationProjectionServiceIntegrationTest,ReputationProjectionConcurrencyTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/reputation/application \
        apps/api/src/test/java/vn/covie/reputation/application
git commit -m "feat(reputation): project reversible order outcomes"
```

---


# Task 8: Implement Reputation Rebuild, Read Contract and Warning Policy

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reputation/application/ReputationRebuildService.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/application/ReputationReadService.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/application/ReputationPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/application/PropertyReputationPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/api/dto/BuyerReputationSummary.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/api/dto/MerchantReputationSummary.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderDetailResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderMapper.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/PublicMerchantViewService.java`
- Test: `apps/api/src/test/java/vn/covie/reputation/application/ReputationRebuildServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/orders/application/OrderReputationReadIntegrationTest.java`

**Interfaces:**
- Produces actor-appropriate reputation summaries without transaction-history exposure.

- [ ] **Step 1: Define bootstrap policy**

```java
public interface ReputationPolicyProvider {
    int buyerWarningThresholdBasisPoints();
    long minimumEligibleOrdersForWarning();
}
```

Properties:

```yaml
covie:
  reputation:
    buyer-warning-threshold-basis-points: 8000
    minimum-eligible-orders-for-warning: 3
```

Warning rule:

```java
warning =
    eligibleOrderCount >= minimumEligibleOrdersForWarning
    && completionRateBasisPoints < buyerWarningThresholdBasisPoints;
```

Plan 08 replaces setting resolution behind this interface.

- [ ] **Step 2: Define read DTOs**

```java
public record MerchantReputationSummary(
        long eligibleOrders,
        long completedOrders,
        Integer completionRateBasisPoints) {}
```

```java
public record BuyerReputationSummary(
        long eligibleOrders,
        long completedOrders,
        Integer completionRateBasisPoints,
        boolean warning) {}
```

When `eligibleOrders == 0`, API returns `completionRateBasisPoints = null`, even though projection storage uses `0`. This avoids showing a new account as "0% completion".

- [ ] **Step 3: Implement read service**

```java
public MerchantReputationSummary merchant(UUID merchantProfileId)
public BuyerReputationSummary buyer(UUID userId)
```

Missing stats row maps to zero counts/null rate, not 404.

No method returns cancellation/no-show raw counters to the opposite party.

- [ ] **Step 4: Extend Order Detail additively**

Add:

```java
OrderReputationContext reputation
```

to `OrderDetailResponse`.

Context:

```java
public record OrderReputationContext(
        MerchantReputationSummary merchant,
        BuyerReputationSummary buyer) {}
```

Mapping:
- BUYER actor -> `merchant` populated, `buyer=null`;
- MERCHANT actor -> `buyer` populated, `merchant=null`;
- ADMIN actor -> both may be populated for audit UI;
- unauthorized never reaches mapper.

Do not alter `allowedActions`.

- [ ] **Step 5: Extend public merchant view with merchant summary only**

`PublicMerchantResponse` gains `MerchantReputationSummary reputation`.

Do not expose buyer reputation in public catalog APIs.

- [ ] **Step 6: Implement rebuild**

Implement exactly these two internal service methods:

```java
@Transactional
public void rebuildOrder(UUID orderId)

@Transactional
public void rebuildAllForMaintenance()
```

`rebuildOrder(orderId)`:
1. loads the authoritative Order;
2. ensures/locks its outcome row and stats rows using Task 7's lock order;
3. diffs the stored contribution to `classifier.classify(order.status())`.

`rebuildAllForMaintenance()` must fail unless:

```yaml
covie:
  reputation:
    allow-maintenance-rebuild: false
```

is explicitly set to `true`.

When enabled, the method:
1. acquires one transaction-scoped advisory lock using a fixed application constant `0x434F564945525042` (`"COVIERPB"` encoded as a long);
2. executes `TRUNCATE reputation_order_outcomes, user_reputation_stats, merchant_reputation_stats`;
3. reads Order IDs in keyset pages of 500 using `where id > :afterId order by id limit 500`;
4. for every Order calls a package-private `projectCurrentOrderWithoutOutbox(OrderEntity)` that uses the **same classifier/delta helpers** as `projectOrder`;
5. commits once after the final page.

This V1 maintenance rebuild intentionally trades write availability for correctness/simplicity and must be run only during a maintenance window. No public/admin endpoint is added in Plan 07; Plan 08 may orchestrate it after explicit operator action.

- [ ] **Step 7: Test source-of-truth repair**

Corrupt stats/outcome rows, call `rebuildOrder(orderId)` for fixtures, assert exact expected projections.

- [ ] **Step 8: Run and commit**

```bash
./mvnw -Dtest=ReputationRebuildServiceIntegrationTest,OrderReputationReadIntegrationTest test
git add apps/api/src/main/java/vn/covie/reputation \
        apps/api/src/main/java/vn/covie/orders \
        apps/api/src/main/java/vn/covie/catalog \
        apps/api/src/main/resources/application.yml \
        apps/api/src/test/java/vn/covie/reputation \
        apps/api/src/test/java/vn/covie/orders
git commit -m "feat(reputation): expose completion trust summaries"
```

---


# Task 9: Map Notification Persistence and Source-event Deduplication

**Files:**
- Create: `apps/api/src/main/java/vn/covie/notifications/model/NotificationType.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/model/NotificationChannel.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/model/NotificationDeliveryStatus.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/model/NotificationEntity.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/model/NotificationDeliveryEntity.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/persistence/NotificationRepository.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/persistence/NotificationDeliveryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/application/NotificationService.java`
- Test: `apps/api/src/test/java/vn/covie/notifications/application/NotificationServiceIntegrationTest.java`

**Interfaces:**
- Produces idempotent database-backed IN_APP notification creation.

- [ ] **Step 1: Define stable notification types**

```java
public enum NotificationType {
    ORDER_NEW,
    ORDER_CONFIRMED,
    ORDER_REJECTED,
    ORDER_EXPIRED,
    ORDER_CANCELLED,
    ORDER_CANCELLATION_REQUESTED,
    ORDER_CANCELLATION_RESPONDED,
    ORDER_FULFILLMENT_UPDATED,
    ORDER_ISSUE_REPORTED,
    ORDER_COMPLETED,
    ORDER_CLOSED_WITH_ISSUE,
    NEED_MATCH_AVAILABLE,
    NEED_RESPONSE_RECEIVED,
    NEED_RESPONSE_UPDATED,
    NEED_RESPONSE_WITHDRAWN,
    NEED_FULFILLED,
    NEED_EXPIRED,
    REVIEW_RECEIVED
}
```

These are UI localization keys. Do not branch UI on fallback title/body.

- [ ] **Step 2: Write dedupe test**

Call `NotificationService.createFromOutbox(...)` twice with the same:

```text
sourceOutboxEventId
recipient userId
NotificationType
```

Expected:
- same notification ID returned;
- one notification row;
- one IN_APP delivery row;
- no unique constraint leak.

Different recipient or different type may create a distinct row.

- [ ] **Step 3: Implement notification creation**

Signature:

```java
@Transactional(propagation = Propagation.MANDATORY)
public NotificationCreationResult createFromOutbox(
        UUID sourceOutboxEventId,
        UUID userId,
        NotificationType type,
        String fallbackTitle,
        String fallbackBody,
        String relatedEntityType,
        UUID relatedEntityId,
        Map<String, Object> data)
```

Use `INSERT ... ON CONFLICT (source_outbox_event_id,user_id,notification_type) DO NOTHING RETURNING id`.

If insert did nothing, query the existing row and return `created=false`.

For a new row:
1. create UUIDv7 notification;
2. persist structured JSON payload;
3. create one `notification_deliveries` row:
   - channel `IN_APP`;
   - status `DELIVERED`;
   - attemptedAt=deliveredAt=now.

- [ ] **Step 4: Keep fallback content free of sensitive data**

Fallback title/body may contain:
- order code;
- generic status wording;
- Need title only when visible to recipient;
- merchant display name.

Do not embed:
- buyer exact address;
- phone/email;
- free-form issue note;
- auth/security data.

- [ ] **Step 5: Run and commit**

```bash
./mvnw -Dtest=NotificationServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/notifications apps/api/src/test/java/vn/covie/notifications
git commit -m "feat(notifications): persist deduplicated in-app notifications"
```

---


# Task 10: Implement Notification Inbox Read and Read-state Commands

**Files:**
- Create: `apps/api/src/main/java/vn/covie/notifications/application/NotificationCursor.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/application/NotificationCursorCodec.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/application/NotificationReadService.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/api/dto/NotificationResponse.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/api/dto/NotificationPageResponse.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/api/MeNotificationController.java`
- Test: `apps/api/src/test/java/vn/covie/notifications/api/MeNotificationControllerIntegrationTest.java`

**Interfaces:**
- Produces:
  - `GET /api/v1/me/notifications`
  - `POST /api/v1/me/notifications/{notificationId}/mark-read`
  - `POST /api/v1/me/notifications/mark-all-read`

- [ ] **Step 1: Define API response**

```java
public record NotificationResponse(
        UUID id,
        NotificationType type,
        Map<String, Object> data,
        boolean read,
        Instant createdAt) {}
```

Do not expose `sourceOutboxEventId`, delivery internals or fallback text as frontend logic requirements.

- [ ] **Step 2: Implement cursor contract**

Cursor `(createdAt,notificationId)` descending; URL-safe opaque encoding.

```text
default limit=20
min=1
max=50
query limit=requested+1
no totalElements
```

- [ ] **Step 3: Implement ownership-safe mark read**

Repository update:

```sql
update notifications
set read_at = coalesce(read_at, :now)
where id = :notificationId
  and user_id = :currentUserId
```

If affected rows = 0, perform an owned existence check only:
- not owned or not exists -> 404;
- owned already-read still returns success because `coalesce` update should affect/read safely according to JDBC row semantics; service may load current row if driver returns 0 for unchanged value.

Return the current `NotificationResponse` or `204`. Use **204 No Content** for this plan to keep mark-read small.

- [ ] **Step 4: Implement mark-all-read**

```sql
update notifications
set read_at = :now
where user_id = :currentUserId
  and read_at is null
```

Return 204 regardless of affected count.

- [ ] **Step 5: Add cache headers**

All notification endpoints:

```http
Cache-Control: private, no-store
```

- [ ] **Step 6: Test authorization and idempotence**

Tests:
- anonymous -> 401;
- user A never sees user B notifications;
- guessing B notification ID -> 404;
- mark same notification twice -> both successful, same `read_at`;
- mark-all affects only current user;
- cursor pages stable with tied timestamps.

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=MeNotificationControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/notifications apps/api/src/test/java/vn/covie/notifications
git commit -m "feat(notifications): add inbox and read state"
```

---


# Task 11: Extend Existing Outbox Repository with Safe Claim and Retry

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/shared/outbox/OutboxEventRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/outbox/OutboxEventEntity.java`
- Create: `apps/api/src/main/java/vn/covie/outbox/application/OutboxRetryPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/outbox/application/OutboxFailureService.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Test: `apps/api/src/test/java/vn/covie/outbox/application/OutboxClaimIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/outbox/application/OutboxRetryPolicyTest.java`

**Interfaces:**
- Consumes V006 `outbox_events`.
- Produces multi-replica-safe one-row claim and bounded failure retry.

- [ ] **Step 1: Write SKIP LOCKED concurrency RED test**

Insert two due Outbox rows. Run two `REQUIRES_NEW` workers concurrently, each calling `lockNextDue(now)`.

Assert:
- each worker locks a different row;
- neither sees the other's locked row;
- after transaction rollback, rows are claimable again.

- [ ] **Step 2: Add native lock query**

Repository query:

```sql
select *
from outbox_events
where
      status = 'PENDING'
   or (status = 'FAILED' and next_attempt_at is not null and next_attempt_at <= :now)
order by occurred_at asc, id asc
for update skip locked
limit 1
```

Expose:

```java
Optional<OutboxEventEntity> lockNextDue(Instant now);
```

Do not select terminal `FAILED` rows where `next_attempt_at is null`.

- [ ] **Step 3: Add entity transition methods**

```java
public void markPublished(Instant now) {
    status = OutboxStatus.PUBLISHED;
    publishedAt = now;
    nextAttemptAt = null;
    lastError = null;
}

public void markFailed(int newAttempts, Instant nextAttemptAt, String sanitizedError) {
    attempts = newAttempts;
    status = OutboxStatus.FAILED;
    this.nextAttemptAt = nextAttemptAt;
    lastError = sanitizedError;
}
```

Use the existing Plan 03 status enum if present; extend only if mapping is incomplete. Do not add a second outbox entity.

- [ ] **Step 4: Define retry policy**

Configuration:

```yaml
covie:
  outbox:
    poll-delay-ms: 500
    max-events-per-poll: 50
    max-attempts: 10
    initial-backoff-seconds: 1
    max-backoff-seconds: 300
```

Backoff:

```java
Duration delayForAttempt(int attempt) {
    long multiplier = 1L << Math.min(attempt - 1, 20);
    long seconds = Math.min(maxBackoffSeconds, initialBackoffSeconds * multiplier);
    return Duration.ofSeconds(seconds);
}
```

Attempt numbering starts at 1.

- [ ] **Step 5: Implement race-safe failure recording**

`OutboxFailureService.recordFailure(eventId, Throwable)` runs `REQUIRES_NEW` and locks by ID.

Flow:

```text
lock outbox row by id FOR UPDATE
if status == PUBLISHED -> return without mutation
attempt = attempts + 1
sanitize exception class + bounded message to max 1000 chars
if attempt >= maxAttempts:
    status=FAILED
    nextAttemptAt=null
else:
    status=FAILED
    nextAttemptAt=now+retryPolicy.delayForAttempt(attempt)
save
```

This prevents a slow failure recorder from overwriting a row that another worker successfully published after the failed transaction rolled back.

Never persist stack traces, tokens, request bodies or SQL bind values in `last_error`.

- [ ] **Step 6: Test retry boundaries**

Assert:
- attempt1 delay 1s;
- attempt2 2s;
- capped at 300s;
- attempt10 terminal `FAILED + nextAttemptAt=null`;
- published row is never moved back to FAILED.

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=OutboxClaimIntegrationTest,OutboxRetryPolicyTest test
git add apps/api/src/main/java/vn/covie/shared/outbox \
        apps/api/src/main/java/vn/covie/outbox \
        apps/api/src/main/resources/application.yml \
        apps/api/src/test/java/vn/covie/outbox
git commit -m "feat(outbox): claim and retry events safely"
```

---


# Task 12: Implement Outbox Handler Registry and Transactional Processor

**Files:**
- Create: `apps/api/src/main/java/vn/covie/outbox/application/OutboxEventContext.java`
- Create: `apps/api/src/main/java/vn/covie/outbox/application/OutboxEventHandler.java`
- Create: `apps/api/src/main/java/vn/covie/outbox/application/OutboxHandlerRegistry.java`
- Create: `apps/api/src/main/java/vn/covie/outbox/application/OutboxProcessingResult.java`
- Create: `apps/api/src/main/java/vn/covie/outbox/application/OutboxProcessor.java`
- Create: `apps/api/src/main/java/vn/covie/outbox/application/OutboxScheduler.java`
- Test: `apps/api/src/test/java/vn/covie/outbox/application/OutboxProcessorIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/outbox/application/OutboxMultiReplicaIntegrationTest.java`

**Interfaces:**
- Produces the single event-dispatch loop used by notification and reputation handlers.

- [ ] **Step 1: Define handler contract**

```java
public interface OutboxEventHandler {
    boolean supports(String eventType);

    List<SseSignal> handle(OutboxEventContext event);
}
```

Context:

```java
public record OutboxEventContext(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        JsonNode payload,
        Instant occurredAt) {}
```

Handler contract:
- called inside the locked Outbox transaction;
- may write PostgreSQL state;
- may refetch authoritative domain state;
- must not call external network services;
- returns zero or more lightweight SSE signals to emit after commit.

- [ ] **Step 2: Implement registry**

```java
public List<OutboxEventHandler> handlersFor(String eventType) {
    return handlers.stream()
            .filter(h -> h.supports(eventType))
            .toList();
}
```

Zero handlers is valid: some catalog/media outbox events are audit/integration intents without a V1 notification/reputation consumer. Processor still publishes them after successful no-op routing.

Add a startup test asserting no duplicate bean registration causes application failure; multiple distinct handlers for the same event is intentionally allowed.

- [ ] **Step 3: Implement one-event transaction**

Use a separate Spring bean/method so `REQUIRES_NEW` proxying is real:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public OutboxProcessingResult processOneTransaction() {
    Optional<OutboxEventEntity> next = repository.lockNextDue(clock.instant());
    if (next.isEmpty()) return OutboxProcessingResult.none();

    OutboxEventEntity event = next.get();
    List<SseSignal> signals = registry.handlersFor(event.eventType()).stream()
            .flatMap(handler -> handler.handle(context(event)).stream())
            .toList();

    event.markPublished(clock.instant());
    return OutboxProcessingResult.published(event.id(), signals);
}
```

Do not emit SSE inside this transaction.

- [ ] **Step 4: Emit SSE only after successful return/commit**

Outer `OutboxProcessor.processOne()`:

```text
call transactional bean
if none -> false
if published -> transaction has committed
publish returned signals through SseSignalPublisher
return true
if transactional bean throws:
    OutboxFailureService.recordFailure(eventId, error)
    return true
```

To know the failed event ID before transaction rollback, the transactional method wraps handler failures in `OutboxProcessingException(eventId,cause)`.

- [ ] **Step 5: Implement bounded scheduler loop**

```java
@Scheduled(fixedDelayString = "${covie.outbox.poll-delay-ms:500}")
public void poll() {
    for (int i = 0; i < properties.maxEventsPerPoll(); i++) {
        if (!processor.processOne()) {
            break;
        }
    }
}
```

No unbounded `while(true)`.

- [ ] **Step 6: Test transactional atomicity**

Handler test:
1. insert PENDING event;
2. handler creates a Notification then deliberately throws;
3. process event;
4. assert Notification rolled back;
5. assert event is FAILED with retry metadata from separate failure transaction;
6. disable throw and advance fixed AppClock to retry time;
7. process again;
8. assert one Notification + event PUBLISHED.

- [ ] **Step 7: Test two processor instances**

Create 100 due events; execute two processor beans/executors concurrently until drained.

Expected:
- all 100 PUBLISHED;
- each handler side effect dedupe key occurs once;
- no event remains PENDING/eligible FAILED;
- no duplicate notification for same event/recipient/type.

- [ ] **Step 8: Run and commit**

```bash
./mvnw -Dtest=OutboxProcessorIntegrationTest,OutboxMultiReplicaIntegrationTest test
git add apps/api/src/main/java/vn/covie/outbox apps/api/src/test/java/vn/covie/outbox
git commit -m "feat(outbox): dispatch transactional event handlers"
```

---


# Task 13: Implement Merchant Notification Recipient Resolution

**Files:**
- Create: `apps/api/src/main/java/vn/covie/notifications/application/MerchantNotificationRecipientResolver.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantMembershipRepository.java`
- Test: `apps/api/src/test/java/vn/covie/notifications/application/MerchantNotificationRecipientResolverIntegrationTest.java`

**Interfaces:**
- Produces recipient user IDs for seller/provider events while respecting STAFF location scope.

- [ ] **Step 1: Write recipient matrix RED tests**

Fixtures:

```text
OWNER active                         -> receives location event
MANAGER active                       -> receives location event
STAFF assigned Location A            -> receives Location A event
STAFF not assigned Location A        -> does not receive Location A event
REVOKED/INVITED membership           -> does not receive
duplicate role/join rows             -> distinct user IDs only
merchant member from other merchant  -> does not receive
```

- [ ] **Step 2: Add one repository query**

Use one SQL/JPQL query that returns distinct `user_id`:

```sql
select distinct mm.user_id
from merchant_memberships mm
where mm.merchant_profile_id = :merchantId
  and mm.membership_status = 'ACTIVE'
  and (
      mm.role in ('OWNER','MANAGER')
      or (
          mm.role = 'STAFF'
          and exists (
              select 1
              from merchant_member_locations mml
              where mml.membership_id = mm.id
                and mml.merchant_location_id = :locationId
          )
      )
  )
```

Do not impersonate a merchant actor or call controller authorization policy from system notification handling.

- [ ] **Step 3: Implement resolver**

```java
public Set<UUID> processorsForLocation(UUID merchantId, UUID locationId)
```

Return unmodifiable distinct set.

- [ ] **Step 4: Run and commit**

```bash
./mvnw -Dtest=MerchantNotificationRecipientResolverIntegrationTest test
git add apps/api/src/main/java/vn/covie/notifications \
        apps/api/src/main/java/vn/covie/merchants/persistence/MerchantMembershipRepository.java \
        apps/api/src/test/java/vn/covie/notifications
git commit -m "feat(notifications): resolve location scoped merchant recipients"
```

---


# Task 14: Implement Order Notification + Reputation Outbox Handlers

**Files:**
- Create: `apps/api/src/main/java/vn/covie/notifications/application/OrderNotificationHandler.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/application/OrderReputationOutboxHandler.java`
- Test: `apps/api/src/test/java/vn/covie/notifications/application/OrderNotificationHandlerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/reputation/application/OrderReputationOutboxHandlerIntegrationTest.java`

**Interfaces:**
- Consumes stable Order outbox events from Plan 05.
- Produces in-app notifications, reputation projection triggers and lightweight Order SSE signals.

- [ ] **Step 1: Define Order notification routing matrix**

The handler refetches current Order and, where actor distinction is required, the latest relevant `order_events` row. Outbox payload is not trusted as a full snapshot.

Recipient rules:

```text
ORDER_CREATED
  -> merchant processors: ORDER_NEW

ORDER_CONFIRMED
  -> buyer: ORDER_CONFIRMED

ORDER_REJECTED
  -> buyer: ORDER_REJECTED

ORDER_EXPIRED
  -> buyer: ORDER_EXPIRED

ORDER_CANCELLATION_REQUESTED
  -> merchant processors: ORDER_CANCELLATION_REQUESTED

ORDER_CANCELLATION_RESPONDED
  -> buyer: ORDER_CANCELLATION_RESPONDED

ORDER_CANCELLED
  current CANCELLED_BEFORE_CONFIRMATION
    -> merchant processors: ORDER_CANCELLED
  current CANCELLED_BY_BUYER_AFTER_CONFIRMATION
    -> merchant processors: ORDER_CANCELLED
  current CANCELLED_BY_SELLER_AFTER_CONFIRMATION
    -> buyer: ORDER_CANCELLED

ORDER_FULFILLMENT_UPDATED
  -> buyer: ORDER_FULFILLMENT_UPDATED
  when current status == BUYER_NO_SHOW also buyer receives the same stable type with status in data

ORDER_ISSUE_REPORTED
  -> merchant processors: ORDER_ISSUE_REPORTED
  if current status == NO_SHOW_CONTESTED merchant processors still receive it

ORDER_COMPLETED
  -> buyer + merchant processors: ORDER_COMPLETED
  dedupe current buyer if they are also a merchant processor user

ORDER_CLOSED_WITH_ISSUE
  -> buyer + merchant processors: ORDER_CLOSED_WITH_ISSUE
```

Every structured payload contains at minimum:

```json
{
  "orderId": "...",
  "orderCode": "...",
  "status": "..."
}
```

- [ ] **Step 2: Define SSE routing**

For every Order outbox event handled, signal authorized interested users:

```text
event = order.updated
data  = { orderId, status }
```

Also every newly-created Notification returns a targeted:

```text
event = notification.created
data  = { notificationId }
```

Do not send address/contact/order items in SSE.

- [ ] **Step 3: Implement notification handler idempotently**

Handler calls `NotificationService.createFromOutbox` per intended recipient. Use source Outbox event ID for dedupe.

If one user is both buyer and merchant processor, dedupe recipient set before writing.

- [ ] **Step 4: Define reputation event subscription**

`OrderReputationOutboxHandler.supports(...)` returns true for:

```text
ORDER_CANCELLATION_RESPONDED
ORDER_CANCELLED
ORDER_FULFILLMENT_UPDATED
ORDER_COMPLETED
ORDER_ISSUE_REPORTED
ORDER_CLOSED_WITH_ISSUE
```

On each:
```java
projection.projectOrder(event.aggregateId(), event.eventId());
return List.of();
```

Do not project from event name; `projectOrder` refetches current Order.

- [ ] **Step 5: Test no-show reversal through actual Outbox**

Process:
1. `ORDER_FULFILLMENT_UPDATED` at BUYER_NO_SHOW;
2. `ORDER_ISSUE_REPORTED` at NO_SHOW_CONTESTED;
3. `ORDER_COMPLETED`.

Assert projection final values are success-only and each Outbox row becomes PUBLISHED.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=OrderNotificationHandlerIntegrationTest,OrderReputationOutboxHandlerIntegrationTest test
git add apps/api/src/main/java/vn/covie/notifications/application \
        apps/api/src/main/java/vn/covie/reputation/application \
        apps/api/src/test/java/vn/covie/notifications \
        apps/api/src/test/java/vn/covie/reputation
git commit -m "feat(trust): consume order events for reputation and notifications"
```

---


# Task 15: Implement Need and Review Notification Outbox Handlers

**Files:**
- Create: `apps/api/src/main/java/vn/covie/notifications/application/NeedNotificationHandler.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/persistence/NeedAudienceRepository.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/persistence/JdbcNeedAudienceRepository.java`
- Create: `apps/api/src/main/java/vn/covie/notifications/application/ReviewNotificationHandler.java`
- Test: `apps/api/src/test/java/vn/covie/notifications/application/NeedNotificationHandlerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/notifications/application/ReviewNotificationHandlerIntegrationTest.java`

**Interfaces:**
- Consumes Plan 06 Need outbox events + Plan 07 `REVIEW_CREATED`.
- Produces buyer/seller notification rows and Need/Review SSE invalidations.

- [ ] **Step 1: Implement buyer-facing Need event routing**

```text
NEED_RESPONSE_CREATED
  -> Need buyer: NEED_RESPONSE_RECEIVED

NEED_RESPONSE_UPDATED
  -> Need buyer: NEED_RESPONSE_UPDATED

NEED_RESPONSE_WITHDRAWN
  -> Need buyer: NEED_RESPONSE_WITHDRAWN

NEED_FULFILLED
  -> Need buyer: NEED_FULFILLED

NEED_EXPIRED
  -> Need buyer: NEED_EXPIRED

NEED_CANCELLED
  -> no inbox row required for actor's own command; SSE may invalidate own list
```

Signals:

```text
need-response.created
need-response.updated
need-response.withdrawn
need-request.fulfilled
```

with IDs only.

- [ ] **Step 2: Add bounded Need-created seller audience query**

The User Flow requires seller/provider notification when a matching Need becomes available. Do not send to every merchant.

`NeedAudienceRepository.findCandidateLocations(needId, limit)` returns:

```java
public record NeedAudienceLocation(
        UUID merchantProfileId,
        UUID merchantLocationId,
        long distanceMeters) {}
```

`JdbcNeedAudienceRepository` uses `NamedParameterJdbcTemplate` and this exact candidate shape (the category recursion mirrors Plan 06's approved query, but the source Need is fixed by ID):

```sql
with recursive category_paths as (
    select c.id as descendant_id, c.id as ancestor_id
    from categories c
    where c.status = 'ACTIVE'
      and c.deleted_at is null

    union all

    select cp.descendant_id, parent.parent_id as ancestor_id
    from category_paths cp
    join categories parent on parent.id = cp.ancestor_id
    where parent.parent_id is not null
)
select distinct
    ml.merchant_profile_id,
    ml.id as merchant_location_id,
    st_distance(n.geo_point, ml.geo_point)::bigint as distance_meters
from need_requests n
join merchant_locations ml
  on ml.location_status = 'ACTIVE'
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
left join inventory_balances ib
  on ib.location_variant_id = lv.id
where n.id = :needId
  and n.request_status = 'ACTIVE'
  and n.expires_at > :now
  and st_dwithin(n.geo_point, ml.geo_point, n.search_radius_meters)
  and (
      n.need_type = 'SERVICE'
      or lv.inventory_mode = 'UNTRACKED'
      or (
          lv.inventory_mode = 'TRACKED'
          and ib.stock_on_hand - ib.reserved_quantity > 0
      )
  )
order by distance_meters asc, ml.merchant_profile_id asc, ml.id asc
limit :limit;
```

Repository SQL is only the coarse candidate narrowing. For every returned row, load the Need and MerchantLocation once. Inject and reuse the exact existing classes:

```text
vn.covie.needs.application.NeedLocationContextFactory
vn.covie.delivery.application.DeliveryCoverageEvaluator
```

Build the persisted location context with:

```java
LocationContext needLocation = needLocationContextFactory.from(need);
```

Then apply the same Plan 06 coverage rule through `DeliveryCoverageEvaluator`:

```text
PRODUCT:
  eligible when location.pickupEnabled == true
  OR deliveryCoverageEvaluator.evaluate(
         locationId, CoverageType.PRODUCT_DELIVERY, needLocation).eligible()

SERVICE:
  deliveryCoverageEvaluator.evaluate(
      locationId, CoverageType.SERVICE_AREA, needLocation).eligible()
```

Return only revalidated locations. This avoids a second coverage formula.

Candidate query cap:

```yaml
covie:
  notifications:
    need-match-max-locations: 100
```

This repository is audience fanout only. It does not become a second seller Need feed API.

- [ ] **Step 3: Resolve users for matching locations**

For each eligible location, call `MerchantNotificationRecipientResolver.processorsForLocation`.

Deduplicate user IDs across locations of the same merchant. A notification data payload may include one representative `merchantLocationId` and `needRequestId`; UI refetches seller Need feed.

Type:

```text
NEED_MATCH_AVAILABLE
```

Cap total recipient user IDs to 500 per source event after deterministic UUID ordering. If more exist, log a structured `needNotificationAudienceCapped=true` metric/log without exposing users.

- [ ] **Step 4: Implement Review notification**

`REVIEW_CREATED`:
- refetch Review + Order merchant/location;
- notify merchant processors with `REVIEW_RECEIVED`;
- data `{reviewId,catalogItemId,orderId,rating}`;
- do not include free-form review comment in SSE;
- SSE `review.created` contains `reviewId` only.

- [ ] **Step 5: Write privacy and dedupe tests**

Need tests prove:
- buyer exact address/coordinates never enter seller notification payload;
- STAFF receives only assigned eligible location notifications;
- same staff user eligible through two locations gets one notification for the event/type;
- out-of-stock tracked offer does not trigger Need match notification;
- response event retry creates one buyer notification.

Review tests prove retry creates one merchant notification.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=NeedNotificationHandlerIntegrationTest,ReviewNotificationHandlerIntegrationTest test
git add apps/api/src/main/java/vn/covie/notifications apps/api/src/test/java/vn/covie/notifications
git commit -m "feat(notifications): route need and review events"
```

---


# Task 16: Implement Best-effort Authenticated SSE Registry and Endpoint

**Files:**
- Create: `apps/api/src/main/java/vn/covie/realtime/SseSignal.java`
- Create: `apps/api/src/main/java/vn/covie/realtime/SseConnectionRegistry.java`
- Create: `apps/api/src/main/java/vn/covie/realtime/SseSignalPublisher.java`
- Create: `apps/api/src/main/java/vn/covie/realtime/SseHeartbeatScheduler.java`
- Create: `apps/api/src/main/java/vn/covie/realtime/MeEventStreamController.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java`
- Test: `apps/api/src/test/java/vn/covie/realtime/SseConnectionRegistryTest.java`
- Test: `apps/api/src/test/java/vn/covie/realtime/MeEventStreamControllerIntegrationTest.java`

**Interfaces:**
- Produces `GET /api/v1/me/events` with `text/event-stream`.
- Consumes post-commit `SseSignal`s from OutboxProcessor.

- [ ] **Step 1: Define lightweight signal**

```java
public record SseSignal(
        UUID userId,
        String eventName,
        Map<String, Object> data) {}
```

Allowed event names in Plan 07:

```text
sync-required
notification.created
order.updated
need-response.created
need-response.updated
need-response.withdrawn
need-request.fulfilled
review.created
```

- [ ] **Step 2: Implement in-memory connection registry**

Store:

```java
ConcurrentMap<UUID, Set<SseEmitter>>
```

Registration:
- emitter timeout = 30 minutes;
- onCompletion/onTimeout/onError remove emitter;
- send an immediate `sync-required` event after registration.

Sending:
- iterate a snapshot of emitters for user;
- catch `IOException/IllegalStateException`;
- remove broken emitters;
- never throw back into Outbox processor.

Configuration:

```yaml
covie:
  sse:
    emitter-timeout-ms: 1800000
    heartbeat-delay-ms: 25000
```

- [ ] **Step 3: Implement heartbeat**

Every 25 seconds, send an SSE comment:

```java
emitter.send(SseEmitter.event().comment("keepalive"));
```

Heartbeat carries no business state and is not logged per connection at INFO.

- [ ] **Step 4: Expose authenticated endpoint**

```java
@GetMapping(
    value = "/api/v1/me/events",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter events() {
    UUID userId = currentActorProvider.currentActor().userId();
    return registry.register(userId);
}
```

Headers:

```http
Cache-Control: private, no-store
X-Accel-Buffering: no
```

Anonymous request -> 401.

This GET does not require CSRF.

- [ ] **Step 5: Explicitly reject durable replay assumptions**

Do not implement a replay store. If `Last-Event-ID` is present, endpoint still registers normally and sends `sync-required`. The header may be accepted/ignored; never claim missed events were replayed.

- [ ] **Step 6: Test user isolation**

Register emitters for A and B; publish signal to A; only A receives it.

Test emitter error removes only that emitter.

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=SseConnectionRegistryTest,MeEventStreamControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/realtime \
        apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java \
        apps/api/src/main/resources/application.yml \
        apps/api/src/test/java/vn/covie/realtime
git commit -m "feat(realtime): add best effort authenticated sse"
```

---


# Task 17: Verify Outbox Commit→SSE Ordering and Failure Semantics

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/outbox/application/OutboxProcessor.java`
- Test: `apps/api/src/test/java/vn/covie/outbox/application/OutboxSseCommitOrderingIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/outbox/application/OutboxFailureRecoveryIntegrationTest.java`

**Interfaces:**
- Proves SSE never advertises a database side effect that subsequently rolls back.

- [ ] **Step 1: Write commit-order RED test**

Use a fake `SseSignalPublisher` that records signals.

Handler:
1. inserts Notification;
2. returns `notification.created`.

Case A: transaction commits:
- Notification exists before publisher observer is invoked;
- event PUBLISHED;
- one SSE signal observed.

Case B: handler inserts Notification then throws:
- Notification absent;
- no SSE signal;
- event becomes FAILED via failure recorder.

- [ ] **Step 2: Keep publisher outside transaction**

If `OutboxProcessor` currently calls publisher from inside the transactional bean, refactor to:

```java
Optional<OutboxProcessingResult> committed = txProcessor.processOneTransaction();
committed.ifPresent(result -> signalPublisher.publishAll(result.signals()));
```

Do not use `TransactionSynchronization` if the architecture can make post-commit ordering explicit through the proxy return boundary. Returning from the `REQUIRES_NEW` proxy is the commit boundary.

- [ ] **Step 3: Verify retry after transient handler failure**

Fixed clock:
- attempt 1 fails -> FAILED nextAttemptAt `+1s`;
- before +1s `processOne` finds none;
- at +1s handler succeeds;
- DB side effect created once;
- PUBLISHED;
- attempts remains historical count `1`;
- one SSE signal only from successful attempt.

- [ ] **Step 4: Verify terminal failure**

Force ten failures. Assert:
- final status FAILED;
- attempts=10;
- nextAttemptAt=null;
- poller skips it;
- no SSE.

- [ ] **Step 5: Run and commit**

```bash
./mvnw -Dtest=OutboxSseCommitOrderingIntegrationTest,OutboxFailureRecoveryIntegrationTest test
git add apps/api/src/main/java/vn/covie/outbox apps/api/src/test/java/vn/covie/outbox
git commit -m "test(outbox): prove commit before sse delivery"
```

---


# Task 18: Add Review/Notification OpenAPI Contracts and Regenerate TypeScript Client

**Files:**
- Test: `apps/api/src/test/java/vn/covie/openapi/Plan07OpenApiContractTest.java`
- Modify/Generate: `packages/api-client/openapi/openapi.json`
- Generate: `packages/api-client/src/generated/schema.ts`

**Interfaces:**
- Produces typed REST contracts for Plan 07. Native EventSource remains handwritten because generated REST clients do not manage browser SSE streams.

- [ ] **Step 1: Write OpenAPI assertions**

The exported spec must contain:

```text
POST /api/v1/me/reviews
GET  /api/v1/catalog/items/{itemId}/reviews
GET  /api/v1/merchants/{merchantId}/reviews
GET  /api/v1/me/notifications
POST /api/v1/me/notifications/{notificationId}/mark-read
POST /api/v1/me/notifications/mark-all-read
GET  /api/v1/me/events
```

Review create documents required `Idempotency-Key`.

SSE response documents `text/event-stream`.

Notification responses document `notificationType`, `data`, `read`, `createdAt`.

- [ ] **Step 2: Run backend OpenAPI tests**

```bash
cd apps/api
./mvnw -Dtest=Plan07OpenApiContractTest,OpenApiExportTest test
```

Expected: PASS.

- [ ] **Step 3: Export and regenerate**

```bash
cd ../..
./infra/scripts/export-openapi.sh
pnpm --filter @covie/api-client generate
pnpm --filter @covie/api-client check
```

- [ ] **Step 4: Verify generated drift is intentional**

```bash
git diff -- packages/api-client/openapi/openapi.json packages/api-client/src/generated/schema.ts
```

Inspect the diff. It must contain Plan 07 routes/types and no removal of Plans 01–06 routes.

- [ ] **Step 5: Commit**

```bash
git add apps/api/src/test/java/vn/covie/openapi/Plan07OpenApiContractTest.java \
        packages/api-client/openapi/openapi.json \
        packages/api-client/src/generated/schema.ts
git commit -m "feat(contract): publish trust and notification api"
```

---


# Task 19: Add Minimal Web Review, Notification Inbox and SSE Invalidation

**Files:**
- Create: `apps/web/features/reviews/review-api.ts`
- Create: `apps/web/features/reviews/review-form.tsx`
- Create: `apps/web/features/reviews/review-list.tsx`
- Create: `apps/web/features/notifications/notification-api.ts`
- Create: `apps/web/features/notifications/notification-inbox.tsx`
- Create: `apps/web/features/notifications/covie-event-stream.ts`
- Create: `apps/web/features/notifications/notification-types.ts`
- Create: `apps/web/app/(buyer)/me/notifications/page.tsx`
- Test: `apps/web/tests/reviews/review-form.test.tsx`
- Test: `apps/web/tests/notifications/notification-inbox.test.tsx`
- Test: `apps/web/tests/notifications/covie-event-stream.test.ts`

**Interfaces:**
- Consumes generated REST client.
- Uses native same-origin `EventSource` only for SSE.
- Produces functional, non-polished Plan 07 UI slices; Plan 09 owns UX completion.

- [ ] **Step 1: Implement Review API using generated client**

No handwritten response interfaces that duplicate generated schema.

Functions:

```ts
createReview(input, idempotencyKey)
listCatalogReviews(itemId, cursor?)
listMerchantReviews(merchantId, cursor?)
```

Map backend error codes:
- `REVIEW_CREATION_DISABLED`;
- `REVIEW_ORDER_NOT_COMPLETED`;
- `REVIEW_ALREADY_EXISTS`;
- `VALIDATION_FAILED`.

- [ ] **Step 2: Implement Review form**

Form:
- rating 1–5;
- optional comment;
- generates a UUID idempotency key once per submit intent;
- disables submit while in flight;
- on success shows created state and refetches relevant Review list/Order detail.

Never send merchant/catalog target IDs.

Component test asserts double click calls mutation once with the same intent key.

- [ ] **Step 3: Implement notification inbox**

Render from `notificationType + data`, localized in frontend mapping.

`notification-types.ts` contains exhaustive display mapping for Plan 07 enum values and a safe fallback:

```ts
export function notificationLabel(type: string): string {
  switch (type) {
    // all known Plan 07 types
    default:
      return "Có cập nhật mới";
  }
}
```

Do not display backend fallback `body` as trusted rich HTML.

Inbox supports:
- initial page;
- load-more cursor;
- mark one read;
- mark all read;
- loading/empty/error states.

- [ ] **Step 4: Implement same-origin EventSource**

`covie-event-stream.ts`:

```ts
const source = new EventSource("/api/v1/me/events", { withCredentials: true });
```

Listen for:
- `sync-required`;
- `notification.created`;
- `order.updated`;
- Need events;
- `review.created`.

On any relevant event, call supplied invalidation/refetch callbacks. Do not mutate authoritative Order/Need objects from SSE payload.

- [ ] **Step 5: Refresh auth then reconnect on SSE error**

On `error`:
1. close EventSource;
2. make generated-client `POST /api/v1/auth/refresh` request using browser cookies/CSRF rules already established by Plan 02;
3. if refresh succeeds, reconnect with bounded exponential delay;
4. if refresh fails 401, stop and let auth shell redirect/login;
5. cap reconnect delay at 30 seconds.

Do not read HttpOnly refresh token in JavaScript.

- [ ] **Step 6: Add page**

`/me/notifications` requires existing `require-user` guard from Plan 02.

- [ ] **Step 7: Test EventSource semantics**

Mock EventSource:
- `notification.created` calls notification refetch;
- `order.updated` calls Order invalidation;
- `sync-required` triggers all registered core refetch callbacks;
- SSE payload itself is never inserted as Order data;
- error closes old stream before reconnect.

- [ ] **Step 8: Run frontend suite**

```bash
pnpm --filter web test --run
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web build
```

Expected: all exit 0.

- [ ] **Step 9: Commit**

```bash
git add apps/web/features/reviews \
        apps/web/features/notifications \
        apps/web/app/'(buyer)'/me/notifications \
        apps/web/tests/reviews \
        apps/web/tests/notifications
git commit -m "feat(web): add reviews notifications and sse invalidation"
```

---


# Task 20: Run Plan 07 Acceptance, Projection Rebuild and Reliability Verification

**Files:**
- Test: `apps/api/src/test/java/vn/covie/trust/Plan07AcceptanceIntegrationTest.java`
- Modify: `README.md` only if the existing development runbook lacks Outbox/SSE instructions.

**Interfaces:**
- Produces the final evidence gate before Plan 08 may start.

- [ ] **Step 1: Add end-to-end backend acceptance fixture**

Use real PostgreSQL/Testcontainers and create:
- buyer;
- merchant OWNER + STAFF scoped to one location;
- Product Order and Service Order fixtures through existing factory/builders;
- Need + NeedResponse;
- completed OrderItems;
- pending Outbox events.

Test one complete flow:

```text
buyer completed Order
-> POST Review
-> Review + rating stats + REVIEW_CREATED outbox commit
-> Outbox processor
-> merchant REVIEW_RECEIVED notification
-> event PUBLISHED
-> public Review feed returns verified review
```

- [ ] **Step 2: Test complete Reputation lifecycle**

Fixture sequence:

```text
Order A COMPLETED
Order B BUYER_NO_SHOW
Order B NO_SHOW_CONTESTED
Order B COMPLETED
Order C CANCELLED_BY_SELLER_AFTER_CONFIRMATION
Order D CLOSED_WITH_ISSUE
```

Process matching Outbox triggers after each transition.

Expected final:

```text
Buyer:
  A success
  B success (old no-show/contest contributions fully replaced)
  C NONE
  D excluded
  eligible=2
  completed=2
  excluded=1
  rate=10000

Merchant:
  A success
  B success
  C negative seller cancel
  D excluded
  eligible=3
  completed=2
  seller_cancel=1
  excluded=1
  rate=floor(2*10000/3)=6666
```

- [ ] **Step 3: Verify rebuild equivalence**

Snapshot all rating/reputation projection rows.

Then:
- corrupt projection rows;
- run `RatingRebuildService` for affected item/merchant;
- run `ReputationRebuildService.rebuildOrder` for each source Order after resetting corresponding outcome/stats test fixture;
- compare source-derived expected values.

The acceptance test must prove projection values are derivable from source tables rather than only from historical in-memory events.

- [ ] **Step 4: Verify Outbox backlog invariants**

After draining:

```sql
select count(*)
from outbox_events
where status = 'PENDING'
   or (status = 'FAILED' and next_attempt_at is not null and next_attempt_at <= now());
```

Expected: `0` for test-created events.

Dedupe check:

```sql
select source_outbox_event_id, user_id, notification_type, count(*)
from notifications
where source_outbox_event_id is not null
group by source_outbox_event_id, user_id, notification_type
having count(*) > 1;
```

Expected: no rows.

- [ ] **Step 5: Verify reputation projection uniqueness/invariants**

```sql
select order_id, count(*)
from reputation_order_outcomes
group by order_id
having count(*) > 1;
```

Expected: no rows.

```sql
select count(*)
from user_reputation_stats
where eligible_order_count < 0
   or completed_order_count < 0
   or completion_rate_basis_points not between 0 and 10000;
```

Expected: `0`.

Equivalent merchant query expected `0`.

- [ ] **Step 6: Run focused backend suite**

```bash
cd apps/api
./mvnw -Dtest='*Review*Test,*Rating*Test,*Reputation*Test,*Notification*Test,*Outbox*Test,*Sse*Test,Plan07AcceptanceIntegrationTest,Plan07SchemaMigrationTest' test
```

Expected: 0 failures/errors.

- [ ] **Step 7: Run regression suite for source domains**

```bash
./mvnw -Dtest='*Checkout*Test,*Order*Test,*Need*Test,*Review*Test,*Reputation*Test,*Outbox*Test' test
```

Expected: Plans 04–06 source behavior stays green.

- [ ] **Step 8: Run complete backend verification**

```bash
./mvnw verify
```

Expected: exit 0.

- [ ] **Step 9: Run generated-contract drift verification**

```bash
cd ../..
./infra/scripts/verify-generated-client.sh
```

Expected: exit 0, no generated drift.

- [ ] **Step 10: Run frontend verification**

```bash
pnpm --filter web test --run
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web build
```

Expected: all exit 0.

- [ ] **Step 11: Run full repository verification**

Run the repository's established CI-equivalent commands from Plan 01/README. At minimum both backend `verify` and frontend build/typecheck/tests must pass from a clean working tree with local PostgreSQL/PostGIS available.

- [ ] **Step 12: Verify required behavior checklist**

```text
[ ] review only for buyer-owned COMPLETED OrderItem
[ ] one Review per OrderItem
[ ] Review target cannot be spoofed
[ ] Review disable blocks new rows, old Reviews remain
[ ] rating projection updates synchronously and rebuilds from Reviews
[ ] public Review feed leaks no buyer private data
[ ] buyer no-show can be reversed to excluded and then success without double-count
[ ] buyer post-confirm cancel affects buyer only
[ ] seller post-confirm cancel affects merchant only
[ ] CLOSED_WITH_ISSUE excluded for both
[ ] actor-specific Order trust summary has no private history
[ ] Outbox SKIP LOCKED works with concurrent workers
[ ] handler failure rolls back DB side effects before retry metadata is stored
[ ] terminal Outbox failure is not hot-looped
[ ] notification dedupe survives event retry
[ ] STAFF receives only location-scoped merchant notifications
[ ] Need seller audience does not expose buyer exact location
[ ] notification list/read ownership rules hold
[ ] SSE authenticates, is user-isolated and sends IDs only
[ ] SSE publish happens after DB commit
[ ] SSE reconnect uses REST refetch/sync-required, not replay assumptions
[ ] no WebSocket/Kafka/message broker added
[ ] V012 is latest Plan 07 migration
[ ] Plan 08 begins V013
```

- [ ] **Step 13: Commit acceptance test/runbook change**

```bash
git add apps/api/src/test/java/vn/covie/trust/Plan07AcceptanceIntegrationTest.java
git add README.md 2>/dev/null || true
git commit -m "test(trust): verify reviews reputation outbox and notifications"
```

Do not create an empty documentation commit if README did not change.

---

# Error Contract Added by Plan 07

New machine-readable errors:

```text
REVIEW_CREATION_DISABLED
REVIEW_ORDER_NOT_COMPLETED
REVIEW_ALREADY_EXISTS
```

Existing errors reused:

```text
RESOURCE_NOT_FOUND
VALIDATION_FAILED
IDEMPOTENCY_KEY_REQUIRED
IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
RATE_LIMIT_EXCEEDED
```

Outbox worker failures are operational state, not public HTTP errors.

---

# Review Transaction Contract

```text
Idempotency advisory lock
        ↓
load current buyer
        ↓
load OrderItem + Order
        ↓
authorize buyer ownership
        ↓
require COMPLETED
        ↓
require review policy enabled
        ↓
insert ACTIVE Review
        ↓
lock/update item rating stats
        ↓
lock/update merchant rating stats
        ↓
append REVIEW_CREATED outbox
        ↓
flush/store idempotent response
        ↓
COMMIT
```

A crash before commit leaves none of Review/rating/outbox changes. A client retry with the same Idempotency-Key returns the same Review.

---

# Reputation Outcome Matrix

| Current Order status | Buyer contribution | Merchant contribution |
|---|---|---|
| `COMPLETED` | SUCCESS | SUCCESS |
| `BUYER_NO_SHOW` | NEGATIVE_NO_SHOW | NONE |
| `NO_SHOW_CONTESTED` | EXCLUDED | EXCLUDED |
| `CANCELLED_BY_BUYER_AFTER_CONFIRMATION` | NEGATIVE_CANCEL_AFTER_CONFIRM | NONE |
| `CANCELLED_BY_SELLER_AFTER_CONFIRMATION` | NONE | NEGATIVE_SELLER_CANCEL |
| `CLOSED_WITH_ISSUE` | EXCLUDED | EXCLUDED |
| all pre-confirm/intermediate/other non-final states | NONE | NONE |

Projection rule:

```text
outbox event is a trigger
        ↓
refetch current Order
        ↓
classify current status
        ↓
lock old reputation_order_outcomes row
        ↓
remove old contribution from stats
        ↓
add new contribution to stats
        ↓
save current outcome
```

Never increment counters solely because an outbox event name appears.

---

# Outbox Processing Contract

Success:

```text
poller
  ↓
REQUIRES_NEW
  ↓
SELECT one due event FOR UPDATE SKIP LOCKED
  ↓
route all DB handlers
  ├── notification persistence
  └── reputation projection
  ↓
mark PUBLISHED
  ↓
COMMIT
  ↓
publish best-effort SSE signals
```

Failure:

```text
handler throws
  ↓
business handler transaction rolls back
  ↓
no Notification/projection partial write survives
  ↓
REQUIRES_NEW failure recorder locks event by ID
  ↓
increment attempts
  ↓
FAILED + nextAttemptAt
  or terminal FAILED + nextAttemptAt NULL
```

No external network call occurs while the Outbox row lock is held.

---

# Notification Recipient Matrix — V1 Baseline

```text
New Order                         -> merchant processors for Order location
Order confirmed/rejected/expired  -> buyer
Buyer cancel/request cancel       -> merchant processors when counterparty attention needed
Seller cancel/respond cancellation -> buyer
Fulfillment progress              -> buyer
Buyer issue/no-show contest       -> merchant processors
Order completed/closed issue      -> buyer + merchant processors
Matching Need created             -> bounded eligible merchant processors
Need response created/updated     -> Need buyer
Need fulfilled/expired            -> Need buyer
Review created                    -> merchant processors
```

All merchant recipients respect active membership and STAFF location assignment.

---

# SSE Contract

Endpoint:

```http
GET /api/v1/me/events
Accept: text/event-stream
```

V1 event names:

```text
sync-required
notification.created
order.updated
need-response.created
need-response.updated
need-response.withdrawn
need-request.fulfilled
review.created
```

Rules:

```text
authenticated cookie/Bearer identity only
same-user connections only
Cache-Control private,no-store
no durable Last-Event-ID replay
node-local emitter registry
best-effort delivery
payload contains IDs/status hints only
frontend refetches REST
```

If pilot deployment uses multiple API replicas, database Notifications remain correct. Plan 10 may add Redis Pub/Sub fanout so a signal produced on replica A reaches an SSE connection held by replica B; no REST/OpenAPI contract changes.

---

# Plan 07 Non-goals

Do not add:

```text
Review editing by buyer
seller deleting negative Reviews
Admin moderation UI/actions for Reviews          -> Plan 08
dynamic SystemSetting persistence/resolver       -> Plan 08
public transaction history
reputation based on star rating
AI trust scoring
fraud/graph risk scoring
push notification provider
email notification transport
SMS
durable SSE replay
WebSocket chat
Redis as notification source of truth
Kafka/RabbitMQ
transaction dispute arbitration
admin force Order status
payment/refund/escrow
```

---

# Self-review Checklist Before Declaring Plan 07 Implemented

## Spec coverage

```text
Review eligibility/one per OrderItem                         -> Tasks 1, 4
Review target derived from OrderItem                         -> Task 4
Review disable policy                                       -> Tasks 3, 4
Rating aggregates immediate + rebuildable                    -> Tasks 2, 3
Public item/merchant Review feeds                            -> Task 5
Safe reviewer identity                                      -> Task 5
Reputation source/outcome formula                           -> Tasks 6, 7
No-show contested reversible projection                     -> Task 7
Reputation rebuild/read/warning                             -> Task 8
Buyer sees merchant trust / seller sees buyer trust         -> Task 8
Notification persistence/dedupe                             -> Tasks 1, 9
Notification inbox/read state                               -> Task 10
Outbox claim/retry                                           -> Task 11
Outbox transactional dispatch                               -> Tasks 12, 17
Merchant location-scoped recipients                         -> Task 13
Order notifications + reputation handlers                   -> Task 14
Need/Review notifications                                   -> Task 15
Matching Need seller in-app notification                    -> Task 15
SSE endpoint/registry/heartbeat                             -> Task 16
SSE commit ordering                                         -> Task 17
OpenAPI/generated client                                    -> Task 18
Minimal web Review/Notification/SSE                         -> Task 19
Acceptance/rebuild/reliability                              -> Task 20
```

## Type consistency

Use exactly:

```text
ReviewStatus
ReviewPolicyProvider
ReviewService
ReviewReadService
RatingProjectionService
RatingRebuildService
BuyerReputationOutcome
MerchantReputationOutcome
ReputationContribution
ReputationOutcomeClassifier
ReputationProjectionService
ReputationReadService
ReputationPolicyProvider
NotificationType
NotificationService
NotificationReadService
MerchantNotificationRecipientResolver
OutboxEventContext
OutboxEventHandler
OutboxHandlerRegistry
OutboxProcessor
OutboxRetryPolicy
SseSignal
SseConnectionRegistry
SseSignalPublisher
```

Do not create parallel `TrustScore`, `Feedback`, `Alert`, `MessageBus` or second Outbox entity hierarchies.

## Migration handoff

Plan 07 ends at:

```text
V012__trust_notifications_outbox.sql
```

Plan 08 must start at:

```text
V013
```

Plan 08 must not renumber/edit V012. It may:
- create `system_settings`, history, moderation/report tables;
- replace `ReviewPolicyProvider` and `ReputationPolicyProvider` business-setting adapters;
- call `RatingProjectionService.onVisibilityTransition` for Review hide/restore;
- add admin rebuild/diagnostic commands without exposing arbitrary Order state mutation.

---

# Execution Handoff

When Plan 07 implementation begins, use the isolated worktree required by the repository's Superpowers workflow.

Recommended execution:

```text
1. Subagent-Driven Development
   - fresh subagent per Task
   - specification review after each Task
   - code-quality review after spec review passes
   - fresh focused verification before completion claims

2. Inline Executing Plans
   - execute Tasks 1 -> 20 in dependency order
   - stop at review checkpoints
   - preserve TDD red/green evidence
   - never skip fresh verification
```

Do not start Plan 08 implementation until Task 20 acceptance evidence is green.
