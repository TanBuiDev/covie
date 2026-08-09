# CoVie Plan 08 — Moderation, System Settings & Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement CoVie V1 runtime business settings, moderation/report workflows, admin operations/read models, safe suspension, maintenance diagnostics, and the policy adapters that replace bootstrap configuration from Plans 03–07 without introducing Admin Order force-state or dispute arbitration.

**Architecture:** Keep Admin/Moderation inside the existing Spring Boot modular monolith. `system_settings` is a small allowlisted policy store with deterministic scope resolution; business modules continue consuming their existing provider interfaces. Moderation is a separate case/action system whose target handlers change visibility/suspension state without rewriting Order/Need/Catalog business lifecycles. PostgreSQL remains authoritative; every important admin/moderation mutation writes audit + outbox in the same transaction and Plan 07 dispatches resulting notifications/SSE signals.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC/Security, Spring Data JPA, PostgreSQL 18, Flyway, Jackson JSONB, Testcontainers/PostGIS, Jakarta Validation, springdoc/OpenAPI, Next.js 16.2, TypeScript strict, generated API client, Vitest/Playwright where established.

## Global Constraints

- Plans 01–07 are prerequisites and are not redesigned here.
- Public API base remains `/api/v1`.
- Plan 07 ends at `V012__trust_notifications_outbox.sql`; Plan 08 starts at `V013__moderation_system_settings_admin.sql`.
- Flyway `outOfOrder=false`; never edit/renumber already-applied `V001`–`V012`.
- Business IDs use UUIDv7. JPA entities are never serialized directly.
- Absolute timestamps are UTC through `AppClock`; UI localization remains frontend-owned.
- Admin authorization is a global `ADMIN` role from JWT/`CurrentActor.globalRoles`; merchant OWNER/MANAGER/STAFF never substitutes for ADMIN.
- `/api/v1/admin/*` is not a persistence bypass. Application/domain invariants still apply.
- Normal Admin API never force-confirms, force-completes, force-cancels, force-rejects, or directly PATCHes Order status.
- V1 Admin does not arbitrate buyer/seller transaction disputes.
- Moderation state is separate from business lifecycle state.
- `SystemSetting` exposes only allowlisted business policy. Secrets/config credentials never enter `system_settings`.
- Existing policy interfaces remain stable wherever possible: `ReviewPolicyProvider`, `ReputationPolicyProvider`, `NeedPolicyProvider`, `OrderPolicyProvider`, `CatalogPublishPolicy`.
- `review.enabled` is GLOBAL-only. Existing reviews remain stored/visible according to moderation state when creation is disabled.
- System setting resolution is deterministic per key; a key declares supported scopes and resolution precedence in code.
- `moderation.mode` supports `MERCHANT`, `CATEGORY`, `LOCATION_AREA`, `GLOBAL`. Missing context dimensions are skipped, never guessed.
- `AUTO_PUBLISH`, `PRE_APPROVAL`, `HYBRID` are the only moderation modes.
- V1 HYBRID rule is deterministic: content requires pre-approval when its owner has an adverse moderation action in the configured lookback window; otherwise it auto-publishes.
- Adverse actions for HYBRID are `REJECT`, `HIDE`, or `SUSPEND`.
- Catalog pre-approval remains `listing_status=DRAFT + moderation_status=PENDING` until admin approval.
- Need business lifecycle remains `ACTIVE|FULFILLED|EXPIRED|CANCELLED`; Plan 08 adds separate Need moderation visibility.
- Review visibility transitions must call Plan 07 `RatingProjectionService.onVisibilityTransition(...)` in the same transaction.
- Merchant suspension blocks new discovery/publish/Need response/checkout but does not remove merchant memberships or historical Order access/processing.
- User suspension revokes active sessions with existing `ADMIN_REVOKED`; restore never resurrects revoked sessions.
- Database-backed notifications and REST remain authoritative. SSE is a best-effort post-commit signal only.
- Important Admin/moderation commands require a reason where specified and create audit metadata.
- Admin tables use page/size/whitelisted sort + totals. Public/personal cursor contracts are unchanged.
- Sensitive admin endpoints return `Cache-Control: private, no-store`.
- Plan 09 owns broad UI completion/polish. Plan 08 adds only functional Admin screens needed to operate the new APIs.

---

# Deliverable Gate

Plan 08 implementation is complete only when all of these are independently verified:

```text
V013 applies cleanly after V012
system setting registry rejects unknown keys/types/scopes
setting resolver obeys per-key deterministic precedence
existing Review/Reputation/Need/Order policy interfaces resolve through SystemSetting
Catalog/Need AUTO_PUBLISH/PRE_APPROVAL/HYBRID paths are deterministic
pre-approval opens exactly one OPEN moderation case per target
Catalog/Need/Review/Merchant hide/restore preserves business lifecycle state
Review hide/restore updates rating aggregate exactly once
user report creates a USER_REPORT case idempotently
ADMIN-only authorization is enforced for every /admin endpoint
user suspension revokes all active sessions and restore requires new login
merchant suspension blocks new commerce but existing Order processing still works
Admin Order surface is read/audit-only; no force-state route exists
SystemSetting mutation writes history + audit + outbox atomically
moderation action writes case action + target mutation + audit + outbox atomically
Plan 07 Outbox dispatch handles new events idempotently
OpenAPI/generated client has no drift
focused backend, frontend, migration, authorization and regression suites pass
Plan 09 migration handoff is V014
```

---

# Inputs and Stable Interfaces

Plan 08 consumes these existing types without parallel replacements:

```text
vn.covie.shared.security.CurrentActor
vn.covie.auth.security.CurrentActorProvider
vn.covie.shared.security.SystemRole.ADMIN
vn.covie.auth.persistence.AuthSessionRepository.revokeAllActive(...)
vn.covie.auth.model.AuthSessionRevocationReason.ADMIN_REVOKED
vn.covie.audit.AuditLogWriter
vn.covie.shared.web.RequestIdAccessor
vn.covie.shared.id.UuidV7Generator
vn.covie.shared.time.AppClock
vn.covie.shared.concurrency.VersionPrecondition
vn.covie.shared.idempotency.IdempotencyExecutor
vn.covie.shared.outbox.OutboxEventWriter
vn.covie.shared.outbox.OutboxEventIntent
vn.covie.outbox.application.OutboxEventHandler
vn.covie.notifications.application.NotificationService
vn.covie.notifications.application.NeedNotificationHandler
vn.covie.notifications.persistence.JdbcNeedAudienceRepository
vn.covie.realtime.SseSignalPublisher
vn.covie.checkout.application.CheckoutValidator

vn.covie.catalog.policy.CatalogPublishPolicy
vn.covie.catalog.policy.CatalogPublishDecision
vn.covie.catalog.application.CatalogPublishValidator
vn.covie.catalog.application.CatalogLifecycleService
vn.covie.catalog.model.CatalogItemEntity
vn.covie.catalog.model.CatalogModerationStatus

vn.covie.needs.application.NeedPolicyProvider
vn.covie.needs.application.NeedPolicySnapshot
vn.covie.needs.application.NeedMatchingService
vn.covie.needs.model.NeedRequestEntity
vn.covie.needs.model.NeedStatus

vn.covie.orders.application.OrderPolicyProvider
vn.covie.orders.application.OrderPolicySnapshot
vn.covie.orders.application.OrderReadService
vn.covie.orders.persistence.OrderRepository

vn.covie.reviews.application.ReviewPolicyProvider
vn.covie.reviews.application.RatingProjectionService
vn.covie.reviews.application.RatingRebuildService
vn.covie.reviews.model.ReviewEntity
vn.covie.reviews.model.ReviewStatus

vn.covie.reputation.application.ReputationPolicyProvider
vn.covie.reputation.application.ReputationRebuildService
```

Plan 08 adds stable boundaries for Plan 09/10:

```text
vn.covie.settings.application.SystemSettingResolver
vn.covie.settings.application.SystemSettingRegistry
vn.covie.settings.application.SystemSettingContext
vn.covie.settings.application.SystemSettingService
vn.covie.settings.model.SettingScopeType
vn.covie.settings.model.SettingValueType

vn.covie.moderation.application.ModerationCaseService
vn.covie.moderation.application.ModerationCommandService
vn.covie.moderation.application.UserReportService
vn.covie.moderation.application.ModerationDecisionService
vn.covie.moderation.application.HybridModerationRiskService
vn.covie.moderation.model.ModerationTargetType
vn.covie.moderation.model.ModerationCaseType
vn.covie.moderation.model.ModerationCaseStatus
vn.covie.moderation.model.ModerationActionType

vn.covie.admin.security.AdminAccessPolicy
vn.covie.admin.application.AdminUserService
vn.covie.admin.application.AdminMerchantService
vn.covie.admin.application.AdminReadService
vn.covie.admin.application.AdminMaintenanceService
```

---

# Repository Map Added/Modified by Plan 08

```text
apps/api/src/main/java/vn/covie/
├── settings/
│   ├── api/
│   │   ├── AdminSystemSettingController.java
│   │   └── dto/
│   │       ├── SystemSettingDefinitionResponse.java
│   │       ├── SystemSettingDetailResponse.java
│   │       ├── SystemSettingOverrideResponse.java
│   │       ├── PatchSystemSettingRequest.java
│   │       └── SystemSettingHistoryResponse.java
│   ├── application/
│   │   ├── SystemSettingRegistry.java
│   │   ├── SystemSettingDefinition.java
│   │   ├── SystemSettingContext.java
│   │   ├── SystemSettingResolver.java
│   │   ├── SystemSettingService.java
│   │   └── SystemSettingValueCodec.java
│   ├── model/
│   │   ├── SystemSettingEntity.java
│   │   ├── SystemSettingHistoryEntity.java
│   │   ├── SettingScopeType.java
│   │   └── SettingValueType.java
│   └── persistence/
│       ├── SystemSettingRepository.java
│       └── SystemSettingHistoryRepository.java
├── moderation/
│   ├── api/
│   │   ├── AdminModerationController.java
│   │   ├── ReportController.java
│   │   └── dto/
│   ├── application/
│   │   ├── ModerationCaseService.java
│   │   ├── ModerationCommandService.java
│   │   ├── UserReportService.java
│   │   ├── ModerationDecisionService.java
│   │   ├── HybridModerationRiskService.java
│   │   ├── JpaModerationCaseWriter.java
│   │   ├── CatalogModerationTargetHandler.java
│   │   ├── NeedModerationTargetHandler.java
│   │   ├── ReviewModerationTargetHandler.java
│   │   └── MerchantModerationTargetHandler.java
│   ├── model/
│   │   ├── ModerationCaseEntity.java
│   │   ├── ModerationActionEntity.java
│   │   ├── ModerationTargetType.java
│   │   ├── ModerationCaseType.java
│   │   ├── ModerationCaseStatus.java
│   │   ├── ModerationActionType.java
│   │   └── ContentModerationStatus.java
│   └── persistence/
│       ├── ModerationCaseRepository.java
│       └── ModerationActionRepository.java
├── shared/moderation/
│   ├── ModerationCaseWriter.java
│   ├── ModerationTargetRef.java
└── admin/
    ├── api/
    │   ├── AdminUserController.java
    │   ├── AdminMerchantController.java
    │   ├── AdminOrderController.java
    │   ├── AdminAuditController.java
    │   ├── AdminMaintenanceController.java
    │   └── dto/
    ├── application/
    │   ├── AdminUserService.java
    │   ├── AdminMerchantService.java
    │   ├── AdminReadService.java
    │   ├── AdminMaintenanceService.java
    │   └── AdminPageRequestValidator.java
    ├── persistence/
    │   ├── AdminUserReadRepository.java
    │   ├── AdminMerchantReadRepository.java
    │   └── AdminOrderReadRepository.java
    └── security/
        └── AdminAccessPolicy.java
```

Frontend functional additions:

```text
apps/web/app/(admin)/admin/layout.tsx
apps/web/app/(admin)/admin/page.tsx
apps/web/app/(admin)/admin/settings/page.tsx
apps/web/app/(admin)/admin/moderation/page.tsx
apps/web/app/(admin)/admin/moderation/[caseId]/page.tsx
apps/web/app/(admin)/admin/users/page.tsx
apps/web/app/(admin)/admin/merchants/page.tsx
apps/web/app/(admin)/admin/orders/page.tsx
apps/web/features/admin/admin-api.ts
apps/web/features/admin/settings-editor.tsx
apps/web/features/admin/moderation-actions.tsx
```

---

# Task 1: Create V013 System Settings, Moderation and Admin-support Schema

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V013__moderation_system_settings_admin.sql`
- Test: `apps/api/src/test/java/vn/covie/adminmoderation/persistence/Plan08SchemaMigrationTest.java`

**Interfaces:**
- Consumes: schema through `V012`.
- Produces: `system_settings`, `system_setting_history`, `moderation_cases`, `moderation_actions`, Need moderation visibility, merchant suspension restoration snapshot, and Admin read indexes.
- Migration handoff: Plan 09 starts at `V014`.

- [ ] **Step 1: Write migration RED test**

The test extends the existing real PostgreSQL/PostGIS harness and asserts these structures exist:

```java
@Test
void v013CreatesAdminModerationAndSettingsSchema() {
    assertTableExists("system_settings");
    assertTableExists("system_setting_history");
    assertTableExists("moderation_cases");
    assertTableExists("moderation_actions");
    assertColumnExists("need_requests", "moderation_status");
    assertColumnExists("merchant_profiles", "status_before_suspension");
}
```

Run:

```bash
cd apps/api
./mvnw -Dtest=Plan08SchemaMigrationTest test
```

Expected: FAIL because V013 does not exist.

- [ ] **Step 2: Create `system_settings` with scope integrity**

Use:

```sql
create table system_settings (
    id uuid primary key,
    setting_key varchar(160) not null,
    value_type varchar(30) not null,
    value_json jsonb not null,
    scope_type varchar(30) not null,
    location_area_id uuid null references location_areas(id) on delete restrict,
    category_id uuid null references categories(id) on delete restrict,
    merchant_profile_id uuid null references merchant_profiles(id) on delete restrict,
    version bigint not null default 0,
    updated_by_user_id uuid not null references users(id) on delete restrict,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_system_setting_value_type
        check (value_type in ('BOOLEAN','INTEGER','STRING','ENUM')),
    constraint ck_system_setting_scope_type
        check (scope_type in ('GLOBAL','LOCATION_AREA','CATEGORY','MERCHANT')),
    constraint ck_system_setting_scope_shape check (
        (scope_type = 'GLOBAL' and location_area_id is null and category_id is null and merchant_profile_id is null)
        or (scope_type = 'LOCATION_AREA' and location_area_id is not null and category_id is null and merchant_profile_id is null)
        or (scope_type = 'CATEGORY' and category_id is not null and location_area_id is null and merchant_profile_id is null)
        or (scope_type = 'MERCHANT' and merchant_profile_id is not null and location_area_id is null and category_id is null)
    )
);

create unique index ux_system_setting_global
    on system_settings(setting_key)
    where scope_type = 'GLOBAL';
create unique index ux_system_setting_area
    on system_settings(setting_key, location_area_id)
    where scope_type = 'LOCATION_AREA';
create unique index ux_system_setting_category
    on system_settings(setting_key, category_id)
    where scope_type = 'CATEGORY';
create unique index ux_system_setting_merchant
    on system_settings(setting_key, merchant_profile_id)
    where scope_type = 'MERCHANT';
create index ix_system_setting_key_scope
    on system_settings(setting_key, scope_type);
```

Do not seed secrets or environment-backed credentials.

- [ ] **Step 3: Create append-only setting history**

```sql
create table system_setting_history (
    id uuid primary key,
    setting_id uuid not null references system_settings(id) on delete restrict,
    old_value_json jsonb null,
    new_value_json jsonb not null,
    old_version bigint not null,
    new_version bigint not null,
    changed_by_user_id uuid not null references users(id) on delete restrict,
    changed_at timestamptz not null
);
create index ix_system_setting_history_setting
    on system_setting_history(setting_id, changed_at desc, id desc);
```

- [ ] **Step 4: Create moderation case/action schema**

```sql
create table moderation_cases (
    id uuid primary key,
    case_type varchar(30) not null,
    reporter_user_id uuid null references users(id) on delete restrict,
    target_type varchar(30) not null,
    target_user_id uuid null references users(id) on delete restrict,
    target_merchant_profile_id uuid null references merchant_profiles(id) on delete restrict,
    target_catalog_item_id uuid null references catalog_items(id) on delete restrict,
    target_need_request_id uuid null references need_requests(id) on delete restrict,
    target_review_id uuid null references reviews(id) on delete restrict,
    reason_code varchar(60) null,
    description text null,
    case_status varchar(30) not null,
    assigned_admin_user_id uuid null references users(id) on delete restrict,
    opened_at timestamptz not null,
    resolved_at timestamptz null,
    closed_at timestamptz null,
    constraint ck_moderation_case_type
        check (case_type in ('USER_REPORT','PRE_APPROVAL','SYSTEM_FLAG')),
    constraint ck_moderation_target_type
        check (target_type in ('USER','MERCHANT','CATALOG_ITEM','NEED_REQUEST','REVIEW')),
    constraint ck_moderation_case_status
        check (case_status in ('OPEN','RESOLVED','CLOSED')),
    constraint ck_moderation_exactly_one_target check (
        num_nonnulls(target_user_id, target_merchant_profile_id, target_catalog_item_id,
                     target_need_request_id, target_review_id) = 1
    ),
    constraint ck_moderation_target_matches_type check (
        (target_type='USER' and target_user_id is not null)
        or (target_type='MERCHANT' and target_merchant_profile_id is not null)
        or (target_type='CATALOG_ITEM' and target_catalog_item_id is not null)
        or (target_type='NEED_REQUEST' and target_need_request_id is not null)
        or (target_type='REVIEW' and target_review_id is not null)
    )
);

create table moderation_actions (
    id uuid primary key,
    moderation_case_id uuid not null references moderation_cases(id) on delete restrict,
    action_type varchar(30) not null,
    actor_admin_user_id uuid not null references users(id) on delete restrict,
    reason_code varchar(60) null,
    note text null,
    metadata jsonb null,
    created_at timestamptz not null,
    constraint ck_moderation_action_type check (
        action_type in ('APPROVE','REJECT','HIDE','RESTORE','CLOSE','SUSPEND','REINSTATE')
    )
);

create index ix_moderation_cases_admin
    on moderation_cases(case_status, opened_at desc, id desc);
create index ix_moderation_cases_target
    on moderation_cases(target_type, opened_at desc);
create index ix_moderation_actions_case
    on moderation_actions(moderation_case_id, created_at, id);
```

Prevent duplicate pre-approval work:

```sql
create unique index ux_open_preapproval_catalog
    on moderation_cases(target_catalog_item_id)
    where case_type='PRE_APPROVAL' and case_status='OPEN' and target_catalog_item_id is not null;
create unique index ux_open_preapproval_need
    on moderation_cases(target_need_request_id)
    where case_type='PRE_APPROVAL' and case_status='OPEN' and target_need_request_id is not null;
```

- [ ] **Step 5: Add Need moderation visibility without rewriting Need lifecycle**

```sql
alter table need_requests
    add column moderation_status varchar(30) not null default 'NOT_REQUIRED';
alter table need_requests
    add constraint ck_need_moderation_status
    check (moderation_status in ('NOT_REQUIRED','PENDING','APPROVED','REJECTED','HIDDEN'));
create index ix_need_active_moderation_expiry
    on need_requests(request_status, moderation_status, expires_at, created_at desc);
```

Existing Need rows become `NOT_REQUIRED`.

- [ ] **Step 6: Add suspension restoration state and Admin read indexes**

```sql
alter table merchant_profiles
    add column status_before_suspension varchar(30) null;
alter table merchant_profiles
    add constraint ck_merchant_status_before_suspension
    check (status_before_suspension is null or status_before_suspension in ('ACTIVE','PAUSED'));

create index if not exists ix_users_admin_state
    on users(account_status, created_at desc, id desc);
create index if not exists ix_merchant_profiles_admin_state
    on merchant_profiles(merchant_status, created_at desc, id desc);
create index if not exists ix_orders_admin_state_created
    on orders(status, created_at desc, id desc);
```

- [ ] **Step 7: Run migration test and inspect constraints**

```bash
./mvnw -Dtest=Plan08SchemaMigrationTest test
```

Expected: PASS and Flyway applies V001–V013 sequentially.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/resources/db/migration/V013__moderation_system_settings_admin.sql \
        apps/api/src/test/java/vn/covie/adminmoderation/persistence/Plan08SchemaMigrationTest.java
git commit -m "feat(admin): add moderation and system settings schema"
```

---

# Task 2: Map System Setting and Moderation Persistence Models

**Files:**
- Create: `apps/api/src/main/java/vn/covie/settings/model/SettingScopeType.java`
- Create: `apps/api/src/main/java/vn/covie/settings/model/SettingValueType.java`
- Create: `apps/api/src/main/java/vn/covie/settings/model/SystemSettingEntity.java`
- Create: `apps/api/src/main/java/vn/covie/settings/model/SystemSettingHistoryEntity.java`
- Create: `apps/api/src/main/java/vn/covie/settings/persistence/SystemSettingRepository.java`
- Create: `apps/api/src/main/java/vn/covie/settings/persistence/SystemSettingHistoryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/model/ModerationTargetType.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/model/ModerationCaseType.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/model/ModerationCaseStatus.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/model/ModerationActionType.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/model/ContentModerationStatus.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/model/ModerationCaseEntity.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/model/ModerationActionEntity.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/persistence/ModerationCaseRepository.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/persistence/ModerationActionRepository.java`
- Test: `apps/api/src/test/java/vn/covie/settings/persistence/SystemSettingRepositoryIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/persistence/ModerationCaseRepositoryIntegrationTest.java`

**Interfaces:**
- Produces typed JPA persistence primitives used by all following tasks.

- [ ] **Step 1: Define enums exactly**

```java
public enum SettingScopeType { GLOBAL, LOCATION_AREA, CATEGORY, MERCHANT }
public enum SettingValueType { BOOLEAN, INTEGER, STRING, ENUM }

public enum ModerationTargetType { USER, MERCHANT, CATALOG_ITEM, NEED_REQUEST, REVIEW }
public enum ModerationCaseType { USER_REPORT, PRE_APPROVAL, SYSTEM_FLAG }
public enum ModerationCaseStatus { OPEN, RESOLVED, CLOSED }
public enum ModerationActionType { APPROVE, REJECT, HIDE, RESTORE, CLOSE, SUSPEND, REINSTATE }
public enum ContentModerationStatus { NOT_REQUIRED, PENDING, APPROVED, REJECTED, HIDDEN }
```

- [ ] **Step 2: Write repository RED tests**

Tests prove:

```java
@Test void oneGlobalOverridePerKeyIsDatabaseEnforced() { }
@Test void sameKeyMayHaveDifferentMerchantOverrides() { }
@Test void settingHistoryReadsNewestFirst() { }
@Test void preApprovalCatalogHasAtMostOneOpenCase() { }
@Test void oneModerationCaseHasExactlyOneTypedTarget() { }
@Test void moderationActionsAreAppendOnlyByRepositorySurface() { }
```

Run focused tests and observe failure before entities/repositories exist.

- [ ] **Step 3: Map `SystemSettingEntity` with optimistic versioning**

Required fields:

```java
@Id UUID id;
String settingKey;
@Enumerated(EnumType.STRING) SettingValueType valueType;
@JdbcTypeCode(SqlTypes.JSON) JsonNode valueJson;
@Enumerated(EnumType.STRING) SettingScopeType scopeType;
UUID locationAreaId;
UUID categoryId;
UUID merchantProfileId;
@Version long version;
UUID updatedByUserId;
Instant createdAt;
Instant updatedAt;
```

Do not expose mutators that can change `settingKey`, `valueType` or scope identity after creation. Only `replaceValue(JsonNode value, UUID actorId, Instant now)` changes the value/audit fields.

- [ ] **Step 4: Map append-only `SystemSettingHistoryEntity`**

No repository update/delete method is introduced. Required fields mirror V013 exactly.

- [ ] **Step 5: Map moderation entities with explicit FK IDs**

`ModerationCaseEntity` stores nullable typed target IDs rather than a generic polymorphic FK string. Factory methods:

```java
public static ModerationCaseEntity preApprovalCatalog(UUID id, UUID catalogItemId, Instant now)
public static ModerationCaseEntity preApprovalNeed(UUID id, UUID needRequestId, Instant now)
public static ModerationCaseEntity userReport(
        UUID id, UUID reporterUserId, ModerationTargetRef target,
        String reasonCode, String description, Instant now)
```

Transitions:

```java
public void resolve(Instant now)
public void close(Instant now)
public boolean isOpen()
```

`ModerationActionEntity` has a creation factory only; no update/delete lifecycle.

- [ ] **Step 6: Add lock/read repository methods**

`SystemSettingRepository`:

```java
Optional<SystemSettingEntity> findBySettingKeyAndScopeType(String key, SettingScopeType scope);
Optional<SystemSettingEntity> findBySettingKeyAndScopeTypeAndMerchantProfileId(String key, SettingScopeType scope, UUID merchantId);
Optional<SystemSettingEntity> findBySettingKeyAndScopeTypeAndCategoryId(String key, SettingScopeType scope, UUID categoryId);
Optional<SystemSettingEntity> findBySettingKeyAndScopeTypeAndLocationAreaId(String key, SettingScopeType scope, UUID areaId);
```

Add pessimistic-write variants for mutation.

`ModerationCaseRepository` exposes lock by ID and pageable admin filters; `ModerationActionRepository` exposes ordered `findAllByModerationCaseIdOrderByCreatedAtAscIdAsc`.

- [ ] **Step 7: Run tests and commit**

```bash
./mvnw -Dtest=SystemSettingRepositoryIntegrationTest,ModerationCaseRepositoryIntegrationTest test
git add apps/api/src/main/java/vn/covie/settings apps/api/src/main/java/vn/covie/moderation/model apps/api/src/main/java/vn/covie/moderation/persistence apps/api/src/test/java/vn/covie/settings apps/api/src/test/java/vn/covie/moderation
git commit -m "feat(admin): map settings and moderation persistence"
```

---

# Task 3: Implement Allowlisted System Setting Registry and Deterministic Resolver

**Files:**
- Create: `apps/api/src/main/java/vn/covie/settings/application/SystemSettingDefinition.java`
- Create: `apps/api/src/main/java/vn/covie/settings/application/SystemSettingContext.java`
- Create: `apps/api/src/main/java/vn/covie/settings/application/SystemSettingRegistry.java`
- Create: `apps/api/src/main/java/vn/covie/settings/application/SystemSettingValueCodec.java`
- Create: `apps/api/src/main/java/vn/covie/settings/application/SystemSettingResolver.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/settings/application/SystemSettingRegistryTest.java`
- Test: `apps/api/src/test/java/vn/covie/settings/application/SystemSettingResolverIntegrationTest.java`

**Interfaces:**
- Produces the single runtime setting resolution engine used by every policy adapter.

- [ ] **Step 1: Define context and definition contracts**

```java
public record SystemSettingContext(
        UUID merchantId,
        UUID categoryId,
        UUID locationAreaId) {
    public static SystemSettingContext globalOnly() {
        return new SystemSettingContext(null, null, null);
    }
}
```

```java
public record SystemSettingDefinition(
        String key,
        SettingValueType valueType,
        JsonNode defaultValue,
        Set<SettingScopeType> allowedScopes,
        List<SettingScopeType> resolutionOrder,
        Predicate<JsonNode> validator) {}
```

- [ ] **Step 2: Register exact V1 keys**

Registry contains only:

```text
review.enabled
reputation.warning_threshold_basis_points
reputation.minimum_eligible_orders_for_warning
need_request.max_response_items_per_location
order.confirmation_timeout_minutes
order.auto_complete_hours
order.long_issue_timeout_hours
pickup.no_show_grace_minutes
moderation.mode
moderation.hybrid_adverse_lookback_days
bulk.max_items
```

Definitions:

```text
review.enabled
  BOOLEAN, default true, scopes [GLOBAL]

reputation.warning_threshold_basis_points
  INTEGER, default 8000, range 0..10000, scopes [GLOBAL]

reputation.minimum_eligible_orders_for_warning
  INTEGER, default 3, range 0..100000, scopes [GLOBAL]

need_request.max_response_items_per_location
  INTEGER, default 3, range 1..10
  scopes [MERCHANT,CATEGORY,LOCATION_AREA,GLOBAL]
  precedence MERCHANT > CATEGORY > LOCATION_AREA > GLOBAL

order.confirmation_timeout_minutes
  INTEGER, default 30, range 5..1440
  scopes [MERCHANT,LOCATION_AREA,GLOBAL]
  precedence MERCHANT > LOCATION_AREA > GLOBAL

order.auto_complete_hours
  INTEGER, default 24, range 1..168
  scopes [MERCHANT,LOCATION_AREA,GLOBAL]
  precedence MERCHANT > LOCATION_AREA > GLOBAL

order.long_issue_timeout_hours
  INTEGER, default 48, range 1..720
  scopes [MERCHANT,LOCATION_AREA,GLOBAL]
  precedence MERCHANT > LOCATION_AREA > GLOBAL

pickup.no_show_grace_minutes
  INTEGER, default 120, range 1..1440
  scopes [MERCHANT,LOCATION_AREA,GLOBAL]
  precedence MERCHANT > LOCATION_AREA > GLOBAL

moderation.mode
  ENUM {AUTO_PUBLISH,PRE_APPROVAL,HYBRID}, default AUTO_PUBLISH
  scopes [MERCHANT,CATEGORY,LOCATION_AREA,GLOBAL]
  precedence MERCHANT > CATEGORY > LOCATION_AREA > GLOBAL

moderation.hybrid_adverse_lookback_days
  INTEGER, default 90, range 1..3650, scopes [GLOBAL]

bulk.max_items
  INTEGER, default 100, range 1..500, scopes [GLOBAL]
```

Unknown key returns `422 SYSTEM_SETTING_KEY_NOT_ALLOWED`.

- [ ] **Step 3: Implement strict value codec**

`SystemSettingValueCodec` must never coerce strings such as `"true"` into boolean. Exact methods:

```java
boolean booleanValue(String key, JsonNode value)
int integerValue(String key, JsonNode value)
String enumValue(String key, JsonNode value)
JsonNode encodeBoolean(boolean value)
JsonNode encodeInteger(int value)
JsonNode encodeString(String value)
```

Type/range failure returns `422 SYSTEM_SETTING_VALUE_INVALID`.

- [ ] **Step 4: Implement resolver precedence without implicit dimensions**

```java
public JsonNode resolveRaw(String key, SystemSettingContext context) {
    SystemSettingDefinition def = registry.require(key);
    for (SettingScopeType scope : def.resolutionOrder()) {
        Optional<SystemSettingEntity> row = switch (scope) {
            case MERCHANT -> context.merchantId() == null ? Optional.empty()
                    : repository.findBySettingKeyAndScopeTypeAndMerchantProfileId(key, scope, context.merchantId());
            case CATEGORY -> context.categoryId() == null ? Optional.empty()
                    : repository.findBySettingKeyAndScopeTypeAndCategoryId(key, scope, context.categoryId());
            case LOCATION_AREA -> context.locationAreaId() == null ? Optional.empty()
                    : repository.findBySettingKeyAndScopeTypeAndLocationAreaId(key, scope, context.locationAreaId());
            case GLOBAL -> repository.findBySettingKeyAndScopeType(key, scope);
        };
        if (row.isPresent()) return row.get().valueJson();
    }
    return def.defaultValue();
}
```

The resolver validates persisted `value_type` against the registry before decoding. A corrupted row fails closed with an internal invariant exception and structured server log; it must not silently coerce.

- [ ] **Step 5: Add typed convenience methods**

```java
public boolean bool(String key, SystemSettingContext context)
public int integer(String key, SystemSettingContext context)
public String enumName(String key, SystemSettingContext context)
```

- [ ] **Step 6: Test precedence**

Fixtures explicitly prove:

```text
merchant beats category beats area beats global for moderation.mode
category beats area/global when merchant context absent
area beats global when merchant/category absent
global-only key ignores merchant/category/area overrides because registry rejects their creation
no persisted row -> code-registry default
```

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=SystemSettingRegistryTest,SystemSettingResolverIntegrationTest test
git add apps/api/src/main/java/vn/covie/settings/application apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java apps/api/src/test/java/vn/covie/settings
git commit -m "feat(settings): resolve allowlisted scoped policies"
```

---

# Task 4: Implement Admin System Setting Read, Mutation, History and Audit

**Files:**
- Create: `apps/api/src/main/java/vn/covie/admin/security/AdminAccessPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/settings/application/SystemSettingService.java`
- Create: `apps/api/src/main/java/vn/covie/settings/api/AdminSystemSettingController.java`
- Create: `apps/api/src/main/java/vn/covie/settings/api/dto/SystemSettingDefinitionResponse.java`
- Create: `apps/api/src/main/java/vn/covie/settings/api/dto/SystemSettingDetailResponse.java`
- Create: `apps/api/src/main/java/vn/covie/settings/api/dto/SystemSettingOverrideResponse.java`
- Create: `apps/api/src/main/java/vn/covie/settings/api/dto/PatchSystemSettingRequest.java`
- Create: `apps/api/src/main/java/vn/covie/settings/api/dto/SystemSettingHistoryResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java`
- Test: `apps/api/src/test/java/vn/covie/settings/api/AdminSystemSettingControllerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/settings/application/SystemSettingServiceIntegrationTest.java`

**Interfaces:**
- Produces the approved Admin setting surface with optimistic concurrency and effective-value diagnostics.

- [ ] **Step 1: Implement ADMIN policy**

```java
@Service
public final class AdminAccessPolicy {
    private final CurrentActorProvider actors;

    public CurrentActor requireAdmin() {
        CurrentActor actor = actors.currentActor();
        if (!actor.globalRoles().contains(SystemRole.ADMIN)) {
            throw new ApiException(
                    ErrorCode.ADMIN_ROLE_REQUIRED,
                    HttpStatus.FORBIDDEN,
                    "Administrator role is required.");
        }
        return actor;
    }
}
```

All Admin application services call this policy even if controller/method security also restricts routes.

- [ ] **Step 2: Define mutation request**

```java
public record PatchSystemSettingRequest(
        @NotNull SettingScopeType scopeType,
        UUID scopeId,
        @NotNull JsonNode value) {}
```

Scope shape:

```text
GLOBAL -> scopeId must be null
MERCHANT/CATEGORY/LOCATION_AREA -> scopeId required and referenced resource must exist
```

- [ ] **Step 3: Implement create-or-update semantics explicitly**

`PATCH /api/v1/admin/settings/{settingKey}`:

```text
resolve registry definition
validate requested scope allowed for key
validate scope target exists
validate JSON type/range
lookup exact persisted override
if absent:
    If-Match must be absent
    insert row version=0
    append history old=null new=value oldVersion=0 newVersion=0
    return 201
if present:
    require If-Match matching current version
    capture old value/version
    replace value
    flush to obtain new @Version
    append history with exact versions
    return 200
write AuditLog + SYSTEM_SETTING_CHANGED Outbox in same transaction
```

A concurrent create losing the unique constraint maps to `409 SYSTEM_SETTING_OVERRIDE_ALREADY_EXISTS`.

- [ ] **Step 4: Implement effective read diagnostics**

```http
GET /api/v1/admin/settings
GET /api/v1/admin/settings/{settingKey}?merchantId=&categoryId=&locationAreaId=
GET /api/v1/admin/settings/{settingKey}/history?scopeType=GLOBAL&scopeId=&page=0&size=50
PATCH /api/v1/admin/settings/{settingKey}
```

Detail response contains:

```java
public record SystemSettingDetailResponse(
        String key,
        SettingValueType valueType,
        JsonNode defaultValue,
        Set<SettingScopeType> allowedScopes,
        JsonNode effectiveValue,
        SystemSettingOverrideResponse effectiveSource,
        List<SystemSettingOverrideResponse> persistedOverrides) {}
```

No secret/environment key appears because registry enumeration is the only list source.

- [ ] **Step 5: Write audit/outbox atomically**

Audit action: `SYSTEM_SETTING_CHANGED`.

Outbox:

```java
Map<String, Object> payload = new HashMap<>();
payload.put("settingKey", setting.settingKey());
payload.put("scopeType", setting.scopeType().name());
payload.put("version", setting.version());
if (setting.merchantProfileId() != null) payload.put("scopeId", setting.merchantProfileId().toString());
if (setting.categoryId() != null) payload.put("scopeId", setting.categoryId().toString());
if (setting.locationAreaId() != null) payload.put("scopeId", setting.locationAreaId().toString());

outbox.append(new OutboxEventIntent(
        "SYSTEM_SETTING_CHANGED",
        "SYSTEM_SETTING",
        setting.id(),
        payload));

audit.securityEvent(
        actor.userId(),
        "SYSTEM_SETTING_CHANGED",
        "SYSTEM_SETTING",
        setting.id(),
        requestIds.current(),
        Map.copyOf(payload));
```

Do not put secrets/free-form credentials in payload.

- [ ] **Step 6: Add controller/cache/security tests**

Assert:

```text
anonymous -> 401
USER-only -> 403 ADMIN_ROLE_REQUIRED
ADMIN -> success
existing row PATCH without If-Match -> 428/IF_MATCH_REQUIRED using existing VersionPrecondition contract
stale If-Match -> 409 RESOURCE_VERSION_CONFLICT
unknown key -> 422 SYSTEM_SETTING_KEY_NOT_ALLOWED
invalid scope/value -> 422
response Cache-Control private,no-store
```

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=SystemSettingServiceIntegrationTest,AdminSystemSettingControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/settings apps/api/src/main/java/vn/covie/admin/security apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java apps/api/src/test/java/vn/covie/settings
git commit -m "feat(settings): add admin runtime policy management"
```

---

# Task 5: Replace Review, Reputation, Need and Order Bootstrap Policy Adapters

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reviews/application/SystemSettingReviewPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/reputation/application/SystemSettingReputationPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/needs/application/SystemSettingNeedPolicyProvider.java`
- Create: `apps/api/src/main/java/vn/covie/orders/application/SystemSettingOrderPolicyProvider.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantLocationRepository.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Test: `apps/api/src/test/java/vn/covie/settings/application/BusinessPolicyAdapterIntegrationTest.java`

**Interfaces:**
- Keeps existing business provider interfaces intact; only adapter implementation changes.

- [ ] **Step 1: Replace Review policy**

```java
@Component
@Primary
public final class SystemSettingReviewPolicyProvider implements ReviewPolicyProvider {
    private final SystemSettingResolver settings;

    @Override
    public boolean creationEnabled() {
        return settings.bool("review.enabled", SystemSettingContext.globalOnly());
    }
}
```

All four new SystemSetting adapters in this task are annotated `@Component @Primary`. Existing property/config adapters remain beans for focused legacy tests but are non-primary, so production injection selects the SystemSetting adapters deterministically.

- [ ] **Step 2: Mark all four new adapters as production-primary**

Each new provider class has:

```java
@Component
@Primary
```

Do not annotate the older property/config provider classes as primary.

- [ ] **Step 3: Replace Reputation warning policy**

```java
@Override
public int buyerWarningThresholdBasisPoints() {
    return settings.integer("reputation.warning_threshold_basis_points", SystemSettingContext.globalOnly());
}

@Override
public long minimumEligibleOrdersForWarning() {
    return settings.integer("reputation.minimum_eligible_orders_for_warning", SystemSettingContext.globalOnly());
}
```

- [ ] **Step 4: Replace Need policy using its existing context signature**

```java
@Override
public NeedPolicySnapshot resolve(UUID merchantId, UUID categoryId, UUID locationAreaId) {
    SystemSettingContext ctx = new SystemSettingContext(merchantId, categoryId, locationAreaId);
    return new NeedPolicySnapshot(
            settings.integer("need_request.max_response_items_per_location", ctx));
}
```

- [ ] **Step 5: Replace Order policy using MerchantLocation area**

`OrderPolicyProvider.resolve(merchantId, merchantLocationId)` loads the exact location owned by merchant, reads `locationAreaId`, then resolves:

```java
Duration confirmationTimeout = Duration.ofMinutes(settings.integer("order.confirmation_timeout_minutes", ctx));
Duration autoCompleteGrace = Duration.ofHours(settings.integer("order.auto_complete_hours", ctx));
Duration issueCloseTimeout = Duration.ofHours(settings.integer("order.long_issue_timeout_hours", ctx));
Duration pickupNoShowGrace = Duration.ofMinutes(settings.integer("pickup.no_show_grace_minutes", ctx));
return new OrderPolicySnapshot(
        confirmationTimeout,
        autoCompleteGrace,
        pickupNoShowGrace,
        issueCloseTimeout);
```

Use `SystemSettingContext(merchantId, null, location.locationAreaId())`.

- [ ] **Step 6: Preserve deadline snapshot behavior**

Plan 05 already snapshots deadlines when entering timed states. A later setting change affects new transitions only; it never recomputes deadlines on already-timed Orders.

Write an integration test:

```text
setting auto_complete_hours = 24
mark Order delivered -> autoCompleteAt = T+24h
change setting to 1
existing Order remains T+24h
newly delivered Order gets T+1h
```

- [ ] **Step 7: Remove production reliance on bootstrap business properties**

Delete only the now-unused production properties under:

```text
covie.reviews.creation-enabled
covie.reputation.buyer-warning-threshold-basis-points
covie.reputation.minimum-eligible-orders-for-warning
covie.needs.max-response-items
covie.orders.confirmation-timeout
covie.orders.auto-complete-grace
covie.orders.pickup-no-show-grace
covie.orders.issue-close-timeout
```

Keep unrelated technical scheduler polling properties.

- [ ] **Step 8: Run regression tests and commit**

```bash
./mvnw -Dtest=BusinessPolicyAdapterIntegrationTest,'*OrderPolicy*Test,*NeedPolicy*Test,*Review*Test,*Reputation*Test' test
git add apps/api/src/main/java/vn/covie/reviews apps/api/src/main/java/vn/covie/reputation apps/api/src/main/java/vn/covie/needs apps/api/src/main/java/vn/covie/orders apps/api/src/main/java/vn/covie/merchants/persistence/MerchantLocationRepository.java apps/api/src/main/resources/application.yml apps/api/src/test/java/vn/covie/settings
git commit -m "feat(settings): drive business policies from scoped settings"
```

---

# Task 6: Implement Shared Moderation Case Writer and User-report Case Creation Core

**Files:**
- Create: `apps/api/src/main/java/vn/covie/shared/moderation/ModerationCaseWriter.java`
- Create: `apps/api/src/main/java/vn/covie/shared/moderation/ModerationTargetRef.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/JpaModerationCaseWriter.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/application/ModerationCaseWriterIntegrationTest.java`

**Interfaces:**
- Gives Catalog/Need a low-level case-open port without depending on Admin controllers.

- [ ] **Step 1: Define typed target reference**

```java
public record ModerationTargetRef(ModerationTargetType type, UUID id) {
    public ModerationTargetRef {
        Objects.requireNonNull(type);
        Objects.requireNonNull(id);
    }
}
```

- [ ] **Step 2: Define writer port**

```java
public interface ModerationCaseWriter {
    UUID openPreApproval(ModerationTargetRef target, String reasonCode);
}
```

Both methods require an existing transaction using `Propagation.MANDATORY`.

- [ ] **Step 3: Implement target-specific factory mapping**

`JpaModerationCaseWriter` maps only supported preapproval targets:

```text
CATALOG_ITEM -> target_catalog_item_id
NEED_REQUEST -> target_need_request_id
```

Attempting PRE_APPROVAL for USER/MERCHANT/REVIEW throws an internal contract error because those flows do not preapprove in V1.

- [ ] **Step 4: Handle concurrent pre-approval opening without aborting PostgreSQL transaction**

Do not catch a unique-constraint violation and continue using the same transaction. Add native repository inserts using `INSERT ... ON CONFLICT DO NOTHING RETURNING id` for Catalog and Need pre-approval indexes.

Flow:

```text
try native insert with generated case UUID
if RETURNING id present:
    append MODERATION_CASE_OPENED outbox
    return inserted id
if no row returned:
    query existing OPEN PRE_APPROVAL case for exact target
    return existing id
```

This makes two concurrent publish/create transactions converge on one case without poisoning either transaction with a handled PostgreSQL constraint error. Only the transaction that actually inserted the case emits the case-open outbox event.

- [ ] **Step 5: Emit one case-open outbox intent**

```java
outbox.append(new OutboxEventIntent(
        "MODERATION_CASE_OPENED",
        "MODERATION_CASE",
        caseId,
        Map.of("targetType", target.type().name(), "targetId", target.id().toString())));
```

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=ModerationCaseWriterIntegrationTest test
git add apps/api/src/main/java/vn/covie/shared/moderation apps/api/src/main/java/vn/covie/moderation/application apps/api/src/test/java/vn/covie/moderation
git commit -m "feat(moderation): persist preapproval cases"
```

---

# Task 7: Implement Moderation Mode, HYBRID Risk Rule, Catalog Publish and Need Visibility Integration

**Files:**
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationMode.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationDecision.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationSubject.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/HybridModerationRiskService.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/persistence/ModerationRiskHistoryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/persistence/JdbcModerationRiskHistoryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationDecisionService.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/policy/SystemSettingCatalogPublishPolicy.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/policy/AutoPublishCatalogPolicy.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogLifecycleService.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/model/NeedRequestEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedCommandService.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/persistence/JdbcNeedDiscoveryRepository.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/application/ModerationDecisionServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/application/CatalogModeratedPublishIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/needs/application/NeedModerationVisibilityIntegrationTest.java`

**Interfaces:**
- Produces deterministic AUTO/PRE/HYBRID behavior while preserving Catalog/Need business-state machines.

- [ ] **Step 1: Define mode and decision**

```java
public enum ModerationMode { AUTO_PUBLISH, PRE_APPROVAL, HYBRID }
public enum ModerationDecision { PUBLISH_NOW, REQUIRE_MODERATION }
```

`ModerationSubject`:

```java
public record ModerationSubject(
        ModerationTargetType targetType,
        UUID ownerUserId,
        UUID merchantId,
        UUID categoryId,
        UUID locationAreaId) {}
```

- [ ] **Step 2: Implement exact HYBRID rule**

Resolve `moderation.hybrid_adverse_lookback_days` globally. `HybridModerationRiskService.requiresPreApproval(subject, now)` queries moderation case/action history in `[now-lookback, now]` and returns true if any action is `REJECT`, `HIDE`, or `SUSPEND`.

`JdbcModerationRiskHistoryRepository` uses exact owner queries. Merchant-owned Catalog risk:

```sql
select exists (
    select 1
    from moderation_actions ma
    join moderation_cases mc on mc.id = ma.moderation_case_id
    left join catalog_items ci on ci.id = mc.target_catalog_item_id
    where ma.created_at >= :since
      and ma.action_type in ('REJECT','HIDE','SUSPEND')
      and (
          mc.target_merchant_profile_id = :merchantId
          or ci.merchant_profile_id = :merchantId
      )
)
```

Buyer-owned Need risk:

```sql
select exists (
    select 1
    from moderation_actions ma
    join moderation_cases mc on mc.id = ma.moderation_case_id
    left join need_requests n on n.id = mc.target_need_request_id
    where ma.created_at >= :since
      and ma.action_type in ('REJECT','HIDE','SUSPEND')
      and (
          mc.target_user_id = :buyerUserId
          or n.buyer_user_id = :buyerUserId
      )
)
```

No NLP/AI risk scoring is introduced.

- [ ] **Step 3: Implement mode resolution**

```java
public ModerationDecision decide(ModerationSubject subject) {
    SystemSettingContext ctx = new SystemSettingContext(
            subject.merchantId(), subject.categoryId(), subject.locationAreaId());
    ModerationMode mode = ModerationMode.valueOf(
            settings.enumName("moderation.mode", ctx));
    return switch (mode) {
        case AUTO_PUBLISH -> ModerationDecision.PUBLISH_NOW;
        case PRE_APPROVAL -> ModerationDecision.REQUIRE_MODERATION;
        case HYBRID -> risk.requiresPreApproval(subject, clock.now())
                ? ModerationDecision.REQUIRE_MODERATION
                : ModerationDecision.PUBLISH_NOW;
    };
}
```

- [ ] **Step 4: Replace Catalog publish policy adapter**

`SystemSettingCatalogPublishPolicy` is `@Component @Primary`; remove `@Primary` from the existing `AutoPublishCatalogPolicy`. `SystemSettingCatalogPublishPolicy.decide(item)` creates a subject with merchant + category; Catalog has no single authoritative location area when one item has offers at multiple locations, so `locationAreaId=null`. This deliberately skips LOCATION_AREA for Catalog publish while still allowing Merchant > Category > Global.

Map:

```text
ModerationDecision.PUBLISH_NOW -> CatalogPublishDecision.PUBLISH_NOW
ModerationDecision.REQUIRE_MODERATION -> CatalogPublishDecision.REQUIRE_MODERATION
```

- [ ] **Step 5: Open pre-approval case from Catalog lifecycle**

Plan 03 behavior remains:

```text
PUBLISH_NOW -> ACTIVE + NOT_REQUIRED/APPROVED-visible
REQUIRE_MODERATION -> DRAFT + PENDING
```

When the second branch occurs, call the shared writer in the same transaction:

```java
moderationCases.openPreApproval(
        new ModerationTargetRef(ModerationTargetType.CATALOG_ITEM, item.id()),
        "PUBLISH_REQUIRES_MODERATION");
``` The unique partial index prevents duplicate OPEN pre-approval cases.

- [ ] **Step 6: Add Need moderation state mapping**

Map the new column as `ContentModerationStatus moderationStatus` in `NeedRequestEntity`.

Creation flow:

```text
resolve Need location/category
resolve ModerationDecision using owner user + category + locationArea
PUBLISH_NOW -> Need ACTIVE + moderation NOT_REQUIRED
REQUIRE_MODERATION -> Need ACTIVE + moderation PENDING + PRE_APPROVAL case
```

Need business `request_status` remains ACTIVE in both branches; pending moderation only controls visibility.

- [ ] **Step 7: Filter Need discovery and seller response eligibility**

Every seller Need discovery/response check now additionally requires:

```sql
n.moderation_status in ('NOT_REQUIRED','APPROVED')
```

Plan 07 `NEED_CREATED` notification audience must use the same visibility requirement after Task 16 updates handlers.

- [ ] **Step 8: Run tests and commit**

```bash
./mvnw -Dtest=ModerationDecisionServiceIntegrationTest,CatalogModeratedPublishIntegrationTest,NeedModerationVisibilityIntegrationTest test
git add apps/api/src/main/java/vn/covie/moderation/application apps/api/src/main/java/vn/covie/catalog apps/api/src/main/java/vn/covie/needs apps/api/src/test/java/vn/covie/moderation apps/api/src/test/java/vn/covie/catalog apps/api/src/test/java/vn/covie/needs
git commit -m "feat(moderation): apply scoped publish policy"
```

---

# Task 8: Implement Moderation Target Handlers for Catalog, Need, Review and Merchant

**Files:**
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationTargetHandler.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationTargetHandlerRegistry.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationTargetSnapshot.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationTargetMutationResult.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/CatalogModerationTargetHandler.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/NeedModerationTargetHandler.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ReviewModerationTargetHandler.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/MerchantModerationTargetHandler.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/model/NeedRequestEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/reviews/model/ReviewEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/model/MerchantProfileEntity.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/application/ModerationTargetHandlerIntegrationTest.java`

**Interfaces:**
- Provides one registry-backed target mutation path used by Admin moderation commands.

- [ ] **Step 1: Define handler contract**

```java
public record ModerationTargetSnapshot(
        ModerationTargetType type,
        UUID targetId,
        String businessStatus,
        String moderationStatus) {}

public record ModerationTargetMutationResult(
        ModerationTargetSnapshot before,
        ModerationTargetSnapshot after) {}

public interface ModerationTargetHandler {
    ModerationTargetType supports();
    ModerationTargetSnapshot inspect(UUID targetId);
    ModerationTargetMutationResult apply(
            UUID targetId,
            ModerationActionType action,
            Instant now);
}
```

Registry fails application startup if zero/multiple handlers claim a supported content target.

- [ ] **Step 2: Catalog handler semantics**

```text
APPROVE: require moderation PENDING; rerun CatalogPublishValidator; moderation APPROVED; listing ACTIVE
REJECT: require moderation PENDING; moderation REJECTED; listing remains DRAFT
HIDE: require currently visible NOT_REQUIRED|APPROVED; moderation HIDDEN; listing status unchanged
RESTORE: require HIDDEN|REJECTED; moderation APPROVED; listing status unchanged except DRAFT remains DRAFT
```

No handler may archive/delete an item.

- [ ] **Step 3: Need handler semantics**

```text
APPROVE: PENDING -> APPROVED
REJECT: PENDING -> REJECTED
HIDE: NOT_REQUIRED|APPROVED -> HIDDEN
RESTORE: HIDDEN|REJECTED -> APPROVED
```

`request_status`, expiry, fulfillment and buyer cancellation fields are untouched. An expired/cancelled/fulfilled Need can be inspected/audited but APPROVE/RESTORE must not make it seller-discoverable because business status is not ACTIVE.

- [ ] **Step 4: Review handler semantics with rating projection**

Supported actions:

```text
HIDE: ACTIVE -> HIDDEN
RESTORE: HIDDEN -> ACTIVE
```

Within the same transaction:

```java
ReviewStatus old = review.reviewStatus();
review.changeModerationStatus(newStatus, now);
ratingProjection.onVisibilityTransition(review, old, newStatus);
```

APPROVE/REJECT are unsupported for Review V1 and return `422 MODERATION_ACTION_NOT_SUPPORTED` at command layer.

- [ ] **Step 5: Merchant visibility handler semantics**

```text
HIDE: moderation NOT_REQUIRED|APPROVED -> HIDDEN
RESTORE: HIDDEN|REJECTED -> APPROVED
```

`merchant_status` is untouched; suspension is Task 12. Public Catalog discovery/detail must require merchant moderation visible after this task.

- [ ] **Step 6: Add public/new-commerce regression filters**

Update public Catalog query/detail and Need matching/response eligibility to require both:

```text
merchant_status = ACTIVE
merchant moderation visible (NOT_REQUIRED|APPROVED)
```

Existing Order read/command authorization must not add this requirement.

- [ ] **Step 7: Test business-state preservation**

Explicit cases:

```text
Catalog ACTIVE hidden -> listing remains ACTIVE, public disappears
Catalog restored -> public visible again
Need ACTIVE hidden -> request_status stays ACTIVE, seller feed disappears
Review 5-star hidden -> rating count/sum decreases once
Review restore -> rating contribution returns once
Merchant hidden -> catalog disappears but seller can still process existing Order
```

- [ ] **Step 8: Run and commit**

```bash
./mvnw -Dtest=ModerationTargetHandlerIntegrationTest,'*CatalogDiscovery*Test,*NeedDiscovery*Test,*Checkout*Test,*Order*Test' test
git add apps/api/src/main/java/vn/covie/moderation/application apps/api/src/main/java/vn/covie/catalog apps/api/src/main/java/vn/covie/needs apps/api/src/main/java/vn/covie/reviews apps/api/src/main/java/vn/covie/merchants apps/api/src/test/java/vn/covie/moderation
git commit -m "feat(moderation): apply content visibility actions"
```

---

# Task 9: Implement Admin Moderation Case Read and Command API

**Files:**
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationCaseService.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationCommandService.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationBulkService.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ModerationBulkItemExecutor.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/AdminModerationController.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/ModerationCaseSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/ModerationCaseDetailResponse.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/ModerationActionResponse.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/ModerationActionRequest.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/ModerationCommandResult.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/AdminModerationPageResponse.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/BulkModerationRequest.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/BulkModerationResult.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/BulkModerationItemResult.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/api/AdminModerationControllerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/application/ModerationCommandServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/application/ModerationBulkServiceIntegrationTest.java`

**Interfaces:**
- Produces approved `/api/v1/admin/moderation-cases` read/command surface.

- [ ] **Step 1: Define command body**

```java
public record ModerationActionRequest(
        @Size(max = 60) String reasonCode,
        @Size(max = 2000) String note) {}
```

`REJECT`, `HIDE`, and `CLOSE` require nonblank `reasonCode`. APPROVE/RESTORE may omit reason but audit still records action.

Successful single-case commands return this small DTO:

```java
public record ModerationCommandResult(
        UUID caseId,
        ModerationCaseStatus status,
        ModerationActionType action,
        Instant resolvedAt) {}
```

- [ ] **Step 2: Implement page-based reads**

```http
GET /api/v1/admin/moderation-cases?page=0&size=50&status=OPEN&caseType=&targetType=&sort=OPENED_DESC
GET /api/v1/admin/moderation-cases/{caseId}
```

Allowed sort values:

```text
OPENED_DESC
OPENED_ASC
RESOLVED_DESC
```

Page size `1..100`; invalid sort/size returns structured validation error.

- [ ] **Step 3: Define command/state matrix**

```text
PRE_APPROVAL + OPEN:
  approve -> target APPROVE + case RESOLVED
  reject  -> target REJECT + case RESOLVED
  close   -> target unchanged + case CLOSED

USER_REPORT|SYSTEM_FLAG + OPEN:
  hide    -> target HIDE + case RESOLVED
  close   -> target unchanged + case CLOSED

RESOLVED case whose last target-changing action was HIDE or REJECT:
  restore -> target RESTORE + append RESTORE action; case remains RESOLVED
```

Unsupported case/action combination -> `422 MODERATION_ACTION_NOT_SUPPORTED`.

- [ ] **Step 4: Implement atomic command transaction**

Every command:

```text
require ADMIN
IdempotencyExecutor scope moderation.<action>:<caseId>
lock moderation_case
validate case/action matrix
lock/mutate target through handler
append moderation_actions row
update case status/timestamps
audit.securityEvent(
    actor.userId(),
    "MODERATION_" + action.name(),
    "MODERATION_CASE",
    caseId,
    requestIds.current(),
    Map.of("targetType", target.type().name(), "targetId", target.id().toString()))
append MODERATION_ACTION_APPLIED outbox
flush
return small ModerationCommandResult
```

Same idempotency key retry must not apply Rating projection twice.

- [ ] **Step 5: Expose exact commands**

```http
POST /api/v1/admin/moderation-cases/{caseId}/approve
POST /api/v1/admin/moderation-cases/{caseId}/reject
POST /api/v1/admin/moderation-cases/{caseId}/hide-content
POST /api/v1/admin/moderation-cases/{caseId}/restore-content
POST /api/v1/admin/moderation-cases/{caseId}/close
```

- [ ] **Step 6: Test authorization/concurrency**

Two admins racing `approve` vs `reject` on one OPEN case: exactly one commits; loser receives `409 MODERATION_CASE_STATE_CONFLICT`. Target has one resulting state and one effective target-changing action.

- [ ] **Step 7: Implement limited bulk moderation with per-item idempotency**

Expose only the safe V1 bulk surface:

```http
POST /api/v1/admin/moderation-cases/bulk-actions
Idempotency-Key: <client-generated-key>
```

Request/response contracts:

```java
public record BulkModerationRequest(
        @NotNull ModerationActionType action,
        @NotEmpty List<UUID> caseIds,
        @NotBlank @Size(max = 60) String reasonCode,
        @Size(max = 2000) String note) {}

public record BulkModerationItemResult(
        UUID caseId,
        boolean success,
        ModerationCaseStatus status,
        String errorCode) {
    public static BulkModerationItemResult success(UUID caseId, ModerationCaseStatus status) {
        return new BulkModerationItemResult(caseId, true, status, null);
    }
    public static BulkModerationItemResult failure(UUID caseId, String errorCode) {
        return new BulkModerationItemResult(caseId, false, null, errorCode);
    }
}

public record BulkModerationResult(List<BulkModerationItemResult> results) {}
```

Only these actions are accepted in bulk:

```text
HIDE
CLOSE
```

`APPROVE`, `REJECT`, `RESTORE`, `SUSPEND`, `REINSTATE` are rejected with `422 MODERATION_BULK_ACTION_NOT_SUPPORTED`, because they require target-specific review or have broader account/business effects. `caseIds` must be unique and the request size must be `1..effective bulk.max_items`, resolved globally through `SystemSettingResolver`; duplicate IDs return validation failure before any item executes.

Do **not** wrap the batch in one outer `IdempotencyExecutor` transaction and then invoke `REQUIRES_NEW` item mutations. Follow the Plan 04 per-item pattern exactly. `ModerationBulkService` derives a stable raw key for each case:

```text
rawItemKey = batchIdempotencyKey + ":" + caseId
operation  = "moderation.bulk." + action.name().toLowerCase() + ":" + caseId
```

`ModerationBulkItemExecutor` is a separate Spring bean so every item commits independently:

```java
@Service
public final class ModerationBulkItemExecutor {
    private final IdempotencyExecutor idempotency;
    private final ModerationCommandService commands;

    public BulkModerationItemResult execute(
            UUID actorUserId,
            ModerationActionType action,
            UUID caseId,
            String reasonCode,
            String note,
            String batchIdempotencyKey) {
        String itemKey = batchIdempotencyKey + ":" + caseId;
        var request = new ModerationActionRequest(reasonCode, note);
        return idempotency.execute(
                actorUserId,
                "moderation.bulk." + action.name().toLowerCase() + ":" + caseId,
                itemKey,
                Map.of(
                        "caseId", caseId,
                        "action", action.name(),
                        "reasonCode", reasonCode,
                        "note", note == null ? "" : note),
                200,
                BulkModerationItemResult.class,
                () -> executeKnownOutcome(action, caseId, request));
    }

    private BulkModerationItemResult executeKnownOutcome(
            ModerationActionType action,
            UUID caseId,
            ModerationActionRequest request) {
        try {
            ModerationCommandResult result = switch (action) {
                case HIDE -> commands.hideForBulk(caseId, request);
                case CLOSE -> commands.closeForBulk(caseId, request);
                default -> throw new IllegalArgumentException("Unsupported bulk action");
            };
            return BulkModerationItemResult.success(caseId, result.status());
        } catch (ApiException ex) {
            if (ex.code() != ErrorCode.MODERATION_CASE_STATE_CONFLICT
                    && ex.code() != ErrorCode.MODERATION_ACTION_NOT_SUPPORTED
                    && ex.code() != ErrorCode.RESOURCE_NOT_FOUND) {
                throw ex;
            }
            return BulkModerationItemResult.failure(caseId, ex.code().name());
        }
    }
}
```

`hideForBulk` / `closeForBulk` are package-visible/internal command entry points that execute the **same** locked case/target transition, moderation action append, audit and outbox logic as the single-case endpoints, but do not nest another `IdempotencyExecutor`. Unexpected exceptions propagate and roll back only that item. Known business failures above must occur before any target mutation.

Tests must prove:

```text
3 cases: HIDE -> 2 success + 1 state conflict, successful cases remain committed
retry same batch key -> no duplicate moderation_actions/audit/outbox rows
new batch request containing duplicate caseId -> rejected before mutation
size > bulk.max_items -> 422 before mutation
APPROVE as bulk action -> 422 before mutation
concurrent single-case command vs bulk item -> one valid locked outcome, no duplicate target transition
```

- [ ] **Step 8: Run and commit**

```bash
./mvnw -Dtest=ModerationCommandServiceIntegrationTest,ModerationBulkServiceIntegrationTest,AdminModerationControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/moderation apps/api/src/test/java/vn/covie/moderation
git commit -m "feat(moderation): add admin case commands and safe bulk actions"
```

---

# Task 10: Implement Authenticated User Report Endpoint

**Files:**
- Create: `apps/api/src/main/java/vn/covie/moderation/application/UserReportService.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/application/ReportTargetVisibilityPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/ReportController.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/CreateReportRequest.java`
- Create: `apps/api/src/main/java/vn/covie/moderation/api/dto/ReportCreatedResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedMatchingService.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/api/ReportControllerIntegrationTest.java`

**Interfaces:**
- Produces `POST /api/v1/reports` with idempotency and target visibility protection.

- [ ] **Step 1: Define request contract**

```java
public record CreateReportRequest(
        @NotNull ModerationTargetType targetType,
        @NotNull UUID targetId,
        UUID contextMerchantId,
        @NotBlank @Size(max = 60) String reasonCode,
        @Size(max = 2000) String note) {}
```

Public reports support `MERCHANT`, `CATALOG_ITEM`, `NEED_REQUEST`, `REVIEW`. `USER` is reserved for system/support-created moderation cases in V1 and returns `422 REPORT_TARGET_TYPE_NOT_SUPPORTED` here.

`contextMerchantId` is required only for `NEED_REQUEST` so a seller report can prove the Need was visible in that merchant context.

- [ ] **Step 2: Implement target visibility policy**

```text
MERCHANT -> target is public-visible merchant
CATALOG_ITEM -> target is public-visible item
REVIEW -> target ReviewStatus.ACTIVE
NEED_REQUEST -> actor must manage contextMerchantId and NeedMatchingService.canViewNeed(contextMerchantId, needId) is true
```

Invisible target returns 404; the report API never becomes an existence oracle.

- [ ] **Step 3: Add `NeedMatchingService.canViewNeed` using existing matching rules**

It reuses merchant allowed-location scope, category/type/text/location/coverage/inventory and Need moderation filters. It returns a boolean only; it must not serialize exact buyer location.

- [ ] **Step 4: Implement idempotent report creation**

Operation:

```text
report.create:<targetType>:<targetId>
```

Inside the transaction:

```text
validate target visibility
create USER_REPORT moderation case with reporter
append MODERATION_CASE_OPENED outbox
return 201 ReportCreatedResponse(caseId,status=OPEN)
```

The same key/body replays one case. A new key represents a new report intent and may create another case; Plan 10 rate limiting limits abuse.

- [ ] **Step 5: Test no internal control fields**

Request DTO has no priority, assignee, caseStatus, action or moderation outcome field.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=ReportControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/moderation apps/api/src/main/java/vn/covie/needs/application/NeedMatchingService.java apps/api/src/test/java/vn/covie/moderation
git commit -m "feat(moderation): accept authenticated user reports"
```

---

# Task 11: Implement Admin User Suspension and Restore

**Files:**
- Create: `apps/api/src/main/java/vn/covie/admin/application/AdminUserService.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/AdminUserController.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/AdminReasonRequest.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/AdminUserCommandResult.java`
- Modify: `apps/api/src/main/java/vn/covie/users/model/UserEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/users/persistence/UserRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/users/persistence/SystemRoleAssignmentRepository.java`
- Test: `apps/api/src/test/java/vn/covie/admin/application/AdminUserServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/admin/api/AdminUserControllerIntegrationTest.java`

**Interfaces:**
- Produces explicit user safety commands; no generic Admin PATCH of account status.

- [ ] **Step 1: Define endpoints**

```http
POST /api/v1/admin/users/{userId}/suspend
POST /api/v1/admin/users/{userId}/restore
```

Both require `Idempotency-Key`. Suspend requires reason.

- [ ] **Step 2: Implement user entity transitions**

```java
public void suspend(Instant now) {
    if (accountStatus != AccountStatus.ACTIVE) throw new IllegalStateException("User is not ACTIVE");
    accountStatus = AccountStatus.SUSPENDED;
    updatedAt = now;
}

public void restore(Instant now) {
    if (accountStatus != AccountStatus.SUSPENDED) throw new IllegalStateException("User is not SUSPENDED");
    accountStatus = AccountStatus.ACTIVE;
    updatedAt = now;
}
```

BANNED/DELETED are not restored by these V1 commands.

- [ ] **Step 3: Add safety checks**

Suspend rejects:

```text
target == current admin -> 422 ADMIN_CANNOT_SUSPEND_SELF
target has ADMIN and is the last ACTIVE ADMIN -> 409 LAST_ADMIN_CANNOT_BE_SUSPENDED
```

Repository query counts ADMIN role assignments joined to `users.account_status='ACTIVE'`.

- [ ] **Step 4: Revoke sessions atomically on suspend**

After locking/suspending user:

```java
authSessions.revokeAllActive(
        userId,
        now,
        AuthSessionRevocationReason.ADMIN_REVOKED);
```

Restore does not create or un-revoke sessions; the user must log in again.

- [ ] **Step 5: Audit/outbox**

Write `ADMIN_USER_SUSPENDED` / `ADMIN_USER_RESTORED` audit action and corresponding outbox event in same transaction.

- [ ] **Step 6: Test auth effect**

A pre-suspension JWT/session fails after suspend. After restore, the old revoked session still fails; a new login session succeeds.

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=AdminUserServiceIntegrationTest,AdminUserControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/admin apps/api/src/main/java/vn/covie/users apps/api/src/test/java/vn/covie/admin
git commit -m "feat(admin): suspend and restore user accounts"
```

---

# Task 12: Implement Admin Merchant Suspension and New-commerce Gating

**Files:**
- Create: `apps/api/src/main/java/vn/covie/admin/application/AdminMerchantService.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/AdminMerchantController.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/AdminMerchantCommandResult.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/model/MerchantProfileEntity.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantProfileRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/application/CatalogPublishValidator.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/persistence/JdbcCatalogDiscoveryRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/application/CheckoutValidator.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/persistence/JdbcCheckoutOfferReadRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/persistence/JdbcNeedDiscoveryRepository.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseEligibilityPolicy.java`
- Test: `apps/api/src/test/java/vn/covie/admin/application/AdminMerchantSuspensionIntegrationTest.java`

**Interfaces:**
- Stops new commerce for suspended merchants while preserving historical Order fulfillment/access.

- [ ] **Step 1: Define explicit commands**

```http
POST /api/v1/admin/merchants/{merchantId}/suspend
POST /api/v1/admin/merchants/{merchantId}/restore
```

Both use `Idempotency-Key`; suspend requires reason.

- [ ] **Step 2: Preserve previous seller business status**

```java
public void suspend(Instant now) {
    if (merchantStatus != MerchantStatus.ACTIVE && merchantStatus != MerchantStatus.PAUSED) throw new IllegalStateException("Merchant cannot be suspended from " + merchantStatus);
    statusBeforeSuspension = merchantStatus;
    merchantStatus = MerchantStatus.SUSPENDED;
    updatedAt = now;
}

public void restore(Instant now) {
    if (merchantStatus != MerchantStatus.SUSPENDED || statusBeforeSuspension == null) throw new IllegalStateException("Merchant is not restorable");
    merchantStatus = statusBeforeSuspension;
    statusBeforeSuspension = null;
    updatedAt = now;
}
```

- [ ] **Step 3: Gate public discovery and publish**

Catalog publish already requires ACTIVE merchant. Public catalog SQL now requires:

```sql
mp.merchant_status = 'ACTIVE'
and mp.moderation_status in ('NOT_REQUIRED','APPROVED')
```

- [ ] **Step 4: Gate Need response/matching**

Need seller matching and `NeedResponseEligibilityPolicy` require merchant ACTIVE + moderation visible before seller can respond.

- [ ] **Step 5: Gate Checkout Preview and Commit**

Authoritative checkout validation resolves the offer merchant and rejects new checkout if:

```text
merchant_status != ACTIVE
or merchant moderation not visible
```

Return `409 CHECKOUT_CHANGED` at Commit when a merchant becomes suspended/hidden after Preview. Preview returns `422 OFFER_NOT_AVAILABLE` for already-suspended merchant.

- [ ] **Step 6: Preserve existing Order processing**

No change is made to `OrderAccessPolicy.requireMerchantProcessor` to require merchant ACTIVE. Existing confirmed/pending Orders remain readable/processable by valid merchant members.

Integration test:

```text
merchant ACTIVE -> create pending Order
admin suspends merchant
new catalog discovery/Need response/checkout blocked
seller still confirms/processes the existing Order
admin restores merchant -> new commerce follows pre-suspension ACTIVE/PAUSED state
```

- [ ] **Step 7: Audit/outbox and commit**

```bash
./mvnw -Dtest=AdminMerchantSuspensionIntegrationTest,'*Checkout*Test,*Order*Test,*Need*Test,*CatalogDiscovery*Test' test
git add apps/api/src/main/java/vn/covie/admin apps/api/src/main/java/vn/covie/merchants apps/api/src/main/java/vn/covie/catalog apps/api/src/main/java/vn/covie/checkout apps/api/src/main/java/vn/covie/needs apps/api/src/test/java/vn/covie/admin
git commit -m "feat(admin): suspend merchants without breaking fulfillment"
```

---

# Task 13: Implement Admin User, Merchant and Read-only Order Read Models

**Files:**
- Create: `apps/api/src/main/java/vn/covie/admin/application/AdminReadService.java`
- Create: `apps/api/src/main/java/vn/covie/admin/application/AdminPageRequestValidator.java`
- Create: `apps/api/src/main/java/vn/covie/admin/persistence/AdminUserReadRepository.java`
- Create: `apps/api/src/main/java/vn/covie/admin/persistence/AdminMerchantReadRepository.java`
- Create: `apps/api/src/main/java/vn/covie/admin/persistence/AdminOrderReadRepository.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/AdminOrderController.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/AdminUserSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/AdminMerchantSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/AdminOrderSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/AdminPageResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/admin/api/AdminUserController.java`
- Modify: `apps/api/src/main/java/vn/covie/admin/api/AdminMerchantController.java`
- Test: `apps/api/src/test/java/vn/covie/admin/api/AdminReadControllerIntegrationTest.java`

**Interfaces:**
- Produces page-based Admin tables while Order detail remains business-state read-only.

- [ ] **Step 1: Define page constraints**

```java
public record AdminPageRequest(int page, int size, String sort) {}

private static final Set<String> ALLOWED_SORTS = Set.of(
        "CREATED_DESC", "CREATED_ASC", "UPDATED_DESC");

public AdminPageRequest validate(int page, int size, String sort) {
    if (page < 0 || size < 1 || size > 100) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Admin page or size is invalid.");
    }
    String normalized = (sort == null || sort.isBlank()) ? "CREATED_DESC" : sort;
    if (!ALLOWED_SORTS.contains(normalized)) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Admin sort is not allowed.");
    }
    return new AdminPageRequest(page, size, normalized);
}
```

No generic client field/expression sorting.

- [ ] **Step 2: User list/detail**

```http
GET /api/v1/admin/users?page=0&size=50&status=&q=&sort=CREATED_DESC
GET /api/v1/admin/users/{userId}
```

Admin response may include normalized email, account status, global roles, created/updated timestamps and session count. It never returns password hash, Google subject, refresh token hashes or auth action tickets.

- [ ] **Step 3: Merchant list/detail**

```http
GET /api/v1/admin/merchants?page=0&size=50&status=&moderationStatus=&q=&sort=CREATED_DESC
GET /api/v1/admin/merchants/{merchantId}
```

Return business/moderation state, owner/member counts, location count and public trust summary. No raw private buyer transaction history.

- [ ] **Step 4: Admin Order list/detail/events**

```http
GET /api/v1/admin/orders?page=0&size=50&status=&type=&merchantId=&buyerUserId=&orderCode=&sort=CREATED_DESC
GET /api/v1/admin/orders/{orderId}
GET /api/v1/admin/orders/{orderId}/events
```

Detail delegates to the existing authorization-aware Order read model using ADMIN actor semantics. List uses a dedicated page/count query.

- [ ] **Step 5: Prove forbidden Admin Order mutations do not exist**

Controller mapping test asserts 404/405 for attempted routes:

```text
POST /api/v1/admin/orders/{id}/confirm
POST /api/v1/admin/orders/{id}/cancel
POST /api/v1/admin/orders/{id}/complete
PATCH /api/v1/admin/orders/{id}
```

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=AdminReadControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/admin apps/api/src/test/java/vn/covie/admin
git commit -m "feat(admin): add operational read models"
```

---

# Task 14: Implement Admin Audit Log Read Surface

**Files:**
- Create: `apps/api/src/main/java/vn/covie/admin/api/AdminAuditController.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/AdminAuditLogResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/audit/AuditLogRepository.java`
- Test: `apps/api/src/test/java/vn/covie/admin/api/AdminAuditControllerIntegrationTest.java`

**Interfaces:**
- Exposes append-only operational audit data; never treats audit rows as Order source of truth.

- [ ] **Step 1: Add page-based audit query**

Filters:

```text
actorUserId
action
entityType
entityId
requestId
createdFrom
createdTo
```

Sort only `CREATED_DESC|CREATED_ASC`; page size max 100.

- [ ] **Step 2: Expose endpoint**

```http
GET /api/v1/admin/audit-logs?page=0&size=50&action=&entityType=&entityId=&requestId=
```

Response includes redacted safe snapshots as already written by `AuditLogWriter`; it must not expose fields that writer intentionally excluded.

- [ ] **Step 3: Test ADMIN-only and no-store**

USER gets 403; ADMIN gets 200; response carries `Cache-Control: private, no-store`.

- [ ] **Step 4: Run and commit**

```bash
./mvnw -Dtest=AdminAuditControllerIntegrationTest test
git add apps/api/src/main/java/vn/covie/admin/api apps/api/src/main/java/vn/covie/audit/AuditLogRepository.java apps/api/src/test/java/vn/covie/admin/api/AdminAuditControllerIntegrationTest.java
git commit -m "feat(admin): expose audit log reads"
```

---

# Task 15: Add Explicit Admin Maintenance Diagnostics for Rating/Reputation Rebuild

**Files:**
- Create: `apps/api/src/main/java/vn/covie/admin/application/AdminMaintenanceService.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/AdminMaintenanceController.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/RatingRebuildRequest.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/ReputationRebuildRequest.java`
- Create: `apps/api/src/main/java/vn/covie/admin/api/dto/MaintenanceCommandResult.java`
- Test: `apps/api/src/test/java/vn/covie/admin/application/AdminMaintenanceServiceIntegrationTest.java`

**Interfaces:**
- Orchestrates existing Plan 07 source-of-truth rebuild services without introducing arbitrary SQL repair APIs.

- [ ] **Step 1: Expose targeted rating rebuild only**

```http
POST /api/v1/admin/maintenance/ratings/rebuild
```

Request is exactly one target:

```java
public record RatingRebuildRequest(UUID catalogItemId, UUID merchantId) {
    public RatingRebuildRequest {
        if ((catalogItemId == null) == (merchantId == null)) {
            throw new IllegalArgumentException("Exactly one rating rebuild target is required");
        }
    }
}
```

It calls `RatingRebuildService.rebuildCatalogItem` or `rebuildMerchant`.

- [ ] **Step 2: Expose reputation rebuild modes**

```http
POST /api/v1/admin/maintenance/reputation/rebuild
```

Request:

```java
public record ReputationRebuildRequest(UUID orderId, boolean rebuildAll) {}
```

Valid shapes:

```text
orderId set + rebuildAll=false -> rebuildOrder(orderId)
orderId null + rebuildAll=true -> rebuildAllForMaintenance()
```

Full rebuild still obeys Plan 07 technical property `covie.reputation.allow-maintenance-rebuild=true`; otherwise return `403 MAINTENANCE_REBUILD_DISABLED`.

- [ ] **Step 3: Require reason + Idempotency-Key at controller**

Maintenance commands include `X-Admin-Reason` header, max 500 chars, and `Idempotency-Key`. Audit records actor, command scope and result; no raw SQL endpoint exists.

- [ ] **Step 4: Ensure no Order mutation capability**

Service imports `ReputationRebuildService`/`RatingRebuildService`, never `OrderTransitionService` or Order command mutation methods.

- [ ] **Step 5: Run and commit**

```bash
./mvnw -Dtest=AdminMaintenanceServiceIntegrationTest test
git add apps/api/src/main/java/vn/covie/admin apps/api/src/test/java/vn/covie/admin
git commit -m "feat(admin): add projection maintenance diagnostics"
```

---

# Task 16: Wire Plan 08 Events into Outbox Notifications and SSE After Commit

**Files:**
- Create: `apps/api/src/main/java/vn/covie/moderation/outbox/ModerationOutboxHandler.java`
- Create: `apps/api/src/main/java/vn/covie/settings/outbox/SystemSettingOutboxHandler.java`
- Create: `apps/api/src/main/java/vn/covie/admin/outbox/AdminSafetyOutboxHandler.java`
- Modify: `apps/api/src/main/java/vn/covie/notifications/model/NotificationType.java`
- Modify: `apps/api/src/main/java/vn/covie/notifications/application/NeedNotificationHandler.java`
- Modify: `apps/api/src/main/java/vn/covie/notifications/persistence/JdbcNeedAudienceRepository.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/outbox/ModerationOutboxHandlerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/settings/outbox/SystemSettingOutboxHandlerIntegrationTest.java`

**Interfaces:**
- Uses Plan 07 processor/receipt/retry semantics; does not create a second message bus.

- [ ] **Step 1: Register event names**

```text
MODERATION_CASE_OPENED
MODERATION_ACTION_APPLIED
SYSTEM_SETTING_CHANGED
ADMIN_USER_SUSPENDED
ADMIN_USER_RESTORED
ADMIN_MERCHANT_SUSPENDED
ADMIN_MERCHANT_RESTORED
```

- [ ] **Step 2: Define notification audience**

```text
Catalog moderation -> merchant OWNER/MANAGER + target creator if available
Need moderation -> Need buyer
Review moderation -> Review buyer author
Merchant moderation/suspension -> merchant OWNER/MANAGER
User suspension/restore -> target user only when account state allows persisted notification; suspension notification may be visible on next successful login after restore
SystemSetting change -> no normal end-user notification; Admin SSE signal only
```

Use Plan 07 `MerchantNotificationRecipientResolver` for merchant recipients and its dedupe key `(sourceOutboxEventId,userId,notificationType)`.

- [ ] **Step 3: Emit only lightweight SSE signals after commit**

Examples:

```text
moderation.updated {targetType,targetId}
admin.setting.updated {settingKey}
merchant.updated {merchantId}
```

No SSE payload carries full moderation notes, buyer address, email or audit snapshot.

- [ ] **Step 4: Update Need-created audience for moderation visibility**

Plan 07 `NeedNotificationHandler`/`JdbcNeedAudienceRepository` must skip Need rows whose `moderation_status` is not `NOT_REQUIRED|APPROVED`, even if an old `NEED_CREATED` outbox event is retried after Plan 08 deployment.

- [ ] **Step 5: Test at-least-once dedupe**

Process the same Plan 08 outbox event twice; assert one notification row per recipient/type and projection/target state unchanged.

- [ ] **Step 6: Run and commit**

```bash
./mvnw -Dtest=ModerationOutboxHandlerIntegrationTest,SystemSettingOutboxHandlerIntegrationTest,'*Outbox*Test,*Notification*Test' test
git add apps/api/src/main/java/vn/covie/moderation/outbox apps/api/src/main/java/vn/covie/settings/outbox apps/api/src/main/java/vn/covie/admin/outbox apps/api/src/main/java/vn/covie/notifications apps/api/src/main/java/vn/covie/outbox apps/api/src/main/java/vn/covie/notifications apps/api/src/test/java/vn/covie/moderation apps/api/src/test/java/vn/covie/settings
git commit -m "feat(admin): dispatch moderation and safety events"
```

---

# Task 17: Update OpenAPI, Generated Client and Functional Admin Settings UI

**Files:**
- Modify: Spring controller/DTO OpenAPI annotations created in Tasks 4, 9, 11–15
- Regenerate: `packages/api-client/src/schema.d.ts`
- Create: `apps/web/app/(admin)/admin/layout.tsx`
- Create: `apps/web/app/(admin)/admin/page.tsx`
- Create: `apps/web/app/(admin)/admin/settings/page.tsx`
- Create: `apps/web/features/admin/admin-api.ts`
- Create: `apps/web/features/admin/settings-editor.tsx`
- Test: `apps/web/features/admin/settings-editor.test.tsx`

**Interfaces:**
- Gives Admin a functional settings screen using generated types/client; Plan 09 owns broad UX polish.

- [ ] **Step 1: Document Admin API contracts in OpenAPI**

Include:

```text
ADMIN auth requirement
If-Match for existing setting override updates
Idempotency-Key for moderation/suspension/maintenance commands
page/size/sort limits
stable error codes
setting value JSON semantics
```

- [ ] **Step 2: Regenerate TypeScript client**

Use repository Plan 01 generation command and verify no handwritten duplicate DTO enums are introduced.

- [ ] **Step 3: Build admin route guard**

Admin layout fetches `/api/v1/me` and requires `globalRoles` containing `ADMIN`. Client routing guard is UX only; backend remains authoritative.

- [ ] **Step 4: Implement settings list/editor**

UI:

```text
lists allowlisted keys from GET /admin/settings
shows default, effective value and effective source
allows selecting only supported scopes
loads current override/version before edit
sends If-Match for existing override
handles 409 RESOURCE_VERSION_CONFLICT by refetching
handles validation code without parsing message text
```

No form renders a control for unregistered keys.

- [ ] **Step 5: Test settings editor branches**

Vitest covers boolean/integer/enum field rendering, stale-version error, unsupported scope hidden, and server error-code display mapping.

- [ ] **Step 6: Run and commit**

```bash
pnpm --filter web test -- settings-editor
pnpm --filter web typecheck
git add packages/api-client apps/web/app/'(admin)' apps/web/features/admin
git commit -m "feat(web): add admin settings operations"
```

---

# Task 18: Add Functional Moderation, User, Merchant and Order Admin Screens

**Files:**
- Create: `apps/web/app/(admin)/admin/moderation/page.tsx`
- Create: `apps/web/app/(admin)/admin/moderation/[caseId]/page.tsx`
- Create: `apps/web/app/(admin)/admin/users/page.tsx`
- Create: `apps/web/app/(admin)/admin/merchants/page.tsx`
- Create: `apps/web/app/(admin)/admin/orders/page.tsx`
- Create: `apps/web/features/admin/moderation-actions.tsx`
- Create: `apps/web/features/admin/admin-table.tsx`
- Test: `apps/web/features/admin/moderation-actions.test.tsx`

**Interfaces:**
- Provides minimum operational UI to use Plan 08 APIs; no Order force-status controls exist.

- [ ] **Step 1: Implement moderation queue/detail**

Queue uses page-based server state and filters OPEN/PRE_APPROVAL/USER_REPORT. Detail renders target summary, reporter-safe data, case actions and action history.

Action buttons are derived from case type/status:

```text
PRE_APPROVAL OPEN -> Approve, Reject, Close
USER_REPORT/SYSTEM_FLAG OPEN -> Hide, Close
resolved HIDE/REJECT -> Restore when backend target state supports it
```

Backend errors always win over optimistic UI assumptions.

- [ ] **Step 2: Implement suspend/restore dialogs**

User/Merchant screens require a reason before suspend, generate a fresh Idempotency-Key per intent, and refetch after result.

- [ ] **Step 3: Implement read-only Orders screen**

Orders page contains filters/detail navigation only. It intentionally has no lifecycle mutation buttons.

- [ ] **Step 4: Test forbidden UI actions**

Component test verifies Admin Order row/detail does not render confirm/cancel/complete/force-status controls.

- [ ] **Step 5: Run frontend verification and commit**

```bash
pnpm --filter web test -- moderation-actions
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web build
git add apps/web/app/'(admin)' apps/web/features/admin
git commit -m "feat(web): add moderation and admin operations"
```

---

# Task 19: Add Security, Concurrency, Visibility and Cross-module Regression Tests

**Files:**
- Test: `apps/api/src/test/java/vn/covie/admin/AdminAuthorizationMatrixIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/admin/AdminConcurrencyIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/moderation/ModerationVisibilityRegressionTest.java`
- Test: `apps/api/src/test/java/vn/covie/settings/SystemSettingCrossModuleRegressionTest.java`
- Test: `apps/api/src/test/java/vn/covie/admin/AdminOrderReadOnlyContractTest.java`

**Interfaces:**
- Produces the security/regression gate before Plan 08 acceptance.

- [ ] **Step 1: Build authorization matrix**

Explicit table-driven tests:

```text
anonymous -> every /admin endpoint 401
USER -> every /admin endpoint 403
OWNER/MANAGER/STAFF without ADMIN -> 403
ADMIN without merchant membership -> Admin read/settings/moderation allowed
ADMIN merchant action through seller API -> still must satisfy seller membership; ADMIN is not merchant bypass
```

- [ ] **Step 2: Race settings mutation**

Two admins PATCH same existing setting with identical If-Match version: exactly one succeeds; other receives `409 RESOURCE_VERSION_CONFLICT`; history has one new committed version.

- [ ] **Step 3: Race moderation action**

Two conflicting commands against one OPEN case produce one target state/action and one state conflict. For Review, rating stats change exactly once.

- [ ] **Step 4: Test visibility gates end-to-end**

```text
hidden Catalog -> no public discovery/detail/checkout
restored Catalog -> visible if seller lifecycle ACTIVE
pending/hidden Need -> no seller discovery/Need response
approved Need -> visible if ACTIVE/unexpired
hidden Review -> public feed/rating excludes it
restored Review -> public/rating includes it
hidden/suspended Merchant -> no new discovery/checkout
existing Merchant Orders remain processable
```

- [ ] **Step 5: Test setting propagation without server restart**

In one running Spring test context:

```text
review.enabled true -> review create succeeds
Admin setting false -> next review create fails
Need max=3 -> 4 items fails
set category override max=5 -> matching category allows 4
Order timeout setting change affects next timed transition only
moderation.mode PRE_APPROVAL -> next Catalog publish creates pending case
```

- [ ] **Step 6: Prove Admin Order read-only contract**

Use Spring mapping introspection plus HTTP negative cases; assert no handler method under `/api/v1/admin/orders/**` performs lifecycle command.

- [ ] **Step 7: Run and commit**

```bash
./mvnw -Dtest=AdminAuthorizationMatrixIntegrationTest,AdminConcurrencyIntegrationTest,ModerationVisibilityRegressionTest,SystemSettingCrossModuleRegressionTest,AdminOrderReadOnlyContractTest test
git add apps/api/src/test/java/vn/covie/admin apps/api/src/test/java/vn/covie/moderation apps/api/src/test/java/vn/covie/settings
git commit -m "test(admin): cover policy moderation and safety boundaries"
```

---

# Task 20: Final Acceptance, OpenAPI Drift, Full Regression and Plan 09 Handoff

**Files:**
- Modify only if evidence requires: `README.md`
- Test: repository-wide suites; no new production file is required by this task.

**Interfaces:**
- Produces fresh evidence that Plan 08 is ready for implementation handoff to Plan 09.

- [ ] **Step 1: Run schema verification on clean database**

```bash
cd apps/api
./mvnw -Dtest=Plan08SchemaMigrationTest test
```

Verify Flyway shows V001 through V013 in order and no checksum mismatch.

- [ ] **Step 2: Run focused Plan 08 backend suite**

```bash
./mvnw -Dtest='*SystemSetting*Test,*Moderation*Test,*Admin*Test,*Report*Test' test
```

Expected: 0 failures/errors.

- [ ] **Step 3: Run cross-module regressions**

```bash
./mvnw -Dtest='*Catalog*Test,*Checkout*Test,*Order*Test,*Need*Test,*Review*Test,*Reputation*Test,*Outbox*Test,*Notification*Test' test
```

Expected: 0 failures/errors.

- [ ] **Step 4: Run complete backend verification**

```bash
./mvnw verify
```

Expected: exit 0.

- [ ] **Step 5: Run frontend verification**

```bash
cd ../..
pnpm --filter web test
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web build
```

Expected: all exit 0.

- [ ] **Step 6: Regenerate/check OpenAPI client**

Run the repository OpenAPI generation command from Plan 01 and then:

```bash
git diff --exit-code -- packages/api-client
```

Expected: no generated drift.

- [ ] **Step 7: Inspect database invariants**

```sql
select setting_key, count(*)
from system_settings
where scope_type='GLOBAL'
group by setting_key
having count(*) > 1;
```

Expected: no rows.

```sql
select id
from moderation_cases
where num_nonnulls(target_user_id, target_merchant_profile_id, target_catalog_item_id,
                    target_need_request_id, target_review_id) <> 1;
```

Expected: no rows.

```sql
select target_catalog_item_id, count(*)
from moderation_cases
where case_type='PRE_APPROVAL' and case_status='OPEN' and target_catalog_item_id is not null
group by target_catalog_item_id
having count(*) > 1;
```

Expected: no rows. Repeat for `target_need_request_id`.

- [ ] **Step 8: Verify required behavior checklist**

```text
[ ] allowlisted settings only
[ ] per-key scope validation and deterministic precedence
[ ] Review/Reputation/Need/Order providers use SystemSetting
[ ] Catalog publish uses SystemSetting moderation mode
[ ] Need moderation visibility is separate from Need lifecycle
[ ] HYBRID adverse-history rule is deterministic and tested
[ ] preapproval case uniqueness works under concurrency
[ ] Review hide/restore updates rating exactly once
[ ] user reports hide private target existence with 404
[ ] Admin-only authorization enforced
[ ] user suspend revokes sessions and restore does not revive them
[ ] merchant suspend blocks new commerce but not historical Order processing
[ ] Admin Order surface is read-only
[ ] settings/moderation/suspension write audit + outbox atomically
[ ] new Outbox handlers are retry/idempotency safe
[ ] no secret setting key is exposed
[ ] OpenAPI/client/frontend builds pass
```

- [ ] **Step 9: Confirm migration handoff**

Plan 08 ends at:

```text
V013__moderation_system_settings_admin.sql
```

Plan 09 must start at:

```text
V014
```

Plan 09 must not renumber or edit V013.

- [ ] **Step 10: Commit documentation-only adjustment if needed**

Only if README changed based on verified behavior:

```bash
git add README.md
git commit -m "docs(admin): document moderation operations"
```

Do not create an empty commit.

---

# Error Contract Added by Plan 08

New stable codes:

```text
ADMIN_ROLE_REQUIRED
ADMIN_CANNOT_SUSPEND_SELF
LAST_ADMIN_CANNOT_BE_SUSPENDED
ADMIN_USER_STATE_CONFLICT
ADMIN_MERCHANT_STATE_CONFLICT
SYSTEM_SETTING_KEY_NOT_ALLOWED
SYSTEM_SETTING_SCOPE_NOT_ALLOWED
SYSTEM_SETTING_SCOPE_INVALID
SYSTEM_SETTING_VALUE_INVALID
SYSTEM_SETTING_OVERRIDE_ALREADY_EXISTS
MODERATION_CASE_STATE_CONFLICT
MODERATION_ACTION_NOT_SUPPORTED
MODERATION_BULK_ACTION_NOT_SUPPORTED
MODERATION_TARGET_STATE_CONFLICT
REPORT_TARGET_TYPE_NOT_SUPPORTED
MAINTENANCE_REBUILD_DISABLED
OFFER_NOT_AVAILABLE
```

Existing codes reused:

```text
AUTHENTICATION_REQUIRED
RESOURCE_NOT_FOUND
VALIDATION_FAILED
IF_MATCH_REQUIRED
RESOURCE_VERSION_CONFLICT
IDEMPOTENCY_KEY_REQUIRED
IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
CHECKOUT_CHANGED
RATE_LIMIT_EXCEEDED
```

Guidance:

```text
403 -> authenticated actor lacks ADMIN or maintenance feature permission
404 -> report target/private resource intentionally hidden
409 -> current mutable state/version/checkout race conflict
422 -> unsupported action/key/scope/value/business validation
```

No stack trace, SQL constraint name or internal JPA exception leaks into response.

---

# Setting Resolution Matrix

| Setting key | Allowed scopes | Resolution order | Default |
|---|---|---|---|
| `review.enabled` | GLOBAL | GLOBAL | `true` |
| `reputation.warning_threshold_basis_points` | GLOBAL | GLOBAL | `8000` |
| `reputation.minimum_eligible_orders_for_warning` | GLOBAL | GLOBAL | `3` |
| `need_request.max_response_items_per_location` | MERCHANT,CATEGORY,LOCATION_AREA,GLOBAL | M > C > A > G | `3` |
| `order.confirmation_timeout_minutes` | MERCHANT,LOCATION_AREA,GLOBAL | M > A > G | `30` |
| `order.auto_complete_hours` | MERCHANT,LOCATION_AREA,GLOBAL | M > A > G | `24` |
| `order.long_issue_timeout_hours` | MERCHANT,LOCATION_AREA,GLOBAL | M > A > G | `48` |
| `pickup.no_show_grace_minutes` | MERCHANT,LOCATION_AREA,GLOBAL | M > A > G | `120` |
| `moderation.mode` | MERCHANT,CATEGORY,LOCATION_AREA,GLOBAL | M > C > A > G | `AUTO_PUBLISH` |
| `moderation.hybrid_adverse_lookback_days` | GLOBAL | GLOBAL | `90` |
| `bulk.max_items` | GLOBAL | GLOBAL | `100` |

`M/C/A/G` means Merchant/Category/LocationArea/Global. A missing context dimension is skipped.

---

# Moderation Action Matrix

| Target | APPROVE | REJECT | HIDE | RESTORE | Business state preserved? |
|---|---:|---:|---:|---:|---|
| CatalogItem | PENDING only | PENDING only | visible | hidden/rejected | Yes, except approval activates DRAFT publish |
| NeedRequest | PENDING only | PENDING only | visible | hidden/rejected | Yes (`request_status` unchanged) |
| Review | No | No | ACTIVE | HIDDEN | Yes; rating projection adjusted |
| Merchant | No | No | visible | hidden/rejected | Yes (`merchant_status` unchanged) |
| User | No generic content action | No | No | No | Suspension uses explicit Admin user commands |

Moderation case action does not delete data or alter Order state.

---

# Admin Safety Invariants

```text
ADMIN role is global and never inferred from merchant role
no generic PATCH user.account_status or merchant.merchant_status endpoint
no generic PATCH moderation target status endpoint
no Admin Order state mutation route
user suspend revokes every active session with ADMIN_REVOKED
merchant suspend keeps memberships/order history intact
merchant restore returns to exact pre-suspension ACTIVE/PAUSED state
hidden merchant/catalog/Need/review remains stored for audit/history
Review rating source remains reviews; aggregates remain rebuildable
Reputation source remains orders/order_events; aggregates remain rebuildable
SystemSetting history is append-only
Moderation actions are append-only
Settings/Moderation/Admin writes are auditable with requestId through existing AuditLogWriter
```

---

# Plan 08 Non-goals

Do not add these in Plan 08:

```text
Admin force Order confirmation/completion/cancellation/status repair
transaction dispute arbitration
refund/payment settlement/escrow
arbitrary database editor or generic CRUD console
secret/environment variable management through SystemSetting
generic rule scripting/JSON expression engine
AI moderation/content classifier
external moderation SaaS
email/push delivery channel implementation
WebSocket chat
Kafka/RabbitMQ
Elasticsearch/OpenSearch
full case assignment/SLA workforce system
appeals workflow
per-admin granular RBAC beyond global ADMIN
permanent account deletion tooling
production rate-limit tuning/dashboard
broad responsive UI polish and P0 end-to-end completion -> Plan 09/10
```

---

# Self-review Checklist Before Declaring Plan 08 Implemented

## Spec coverage

```text
Admin namespace/global ADMIN role                          -> Tasks 4, 9, 11-15, 19
Admin Order read/audit-only                               -> Tasks 13, 19
No force Order state                                      -> Tasks 13, 15, 19
Moderation cases/actions                                  -> Tasks 1, 2, 6, 8-9
Limited safe bulk moderation                              -> Task 9
AUTO/PRE/HYBRID                                           -> Task 7
Catalog moderation                                        -> Tasks 7-9
Need moderation                                           -> Tasks 1, 7-9
Review hide/restore + rating                              -> Tasks 8, 9
Merchant moderation                                       -> Tasks 8, 9
User report API                                           -> Task 10
System setting allowlist/scopes/history                   -> Tasks 1-4
Review/Reputation settings adapters                       -> Task 5
Need/Order settings adapters                              -> Task 5
Catalog publish setting adapter                           -> Task 7
User suspension/session revocation                        -> Task 11
Merchant suspension/new-commerce gating                   -> Task 12
Admin users/merchants/orders reads                        -> Task 13
Audit log reads                                           -> Task 14
Projection maintenance                                    -> Task 15
Outbox/notification/SSE                                   -> Task 16
OpenAPI/generated client                                  -> Task 17
Functional Admin UI                                       -> Tasks 17-18
Authorization/concurrency/regression                      -> Task 19
Acceptance/migration handoff                              -> Task 20
```

## Type consistency

Use exactly:

```text
SettingScopeType
SettingValueType
SystemSettingDefinition
SystemSettingContext
SystemSettingRegistry
SystemSettingResolver
SystemSettingService
ModerationTargetType
ModerationCaseType
ModerationCaseStatus
ModerationActionType
ContentModerationStatus
ModerationCaseWriter
ModerationTargetRef
ModerationCaseService
ModerationCommandService
ModerationBulkService
ModerationBulkItemExecutor
ModerationDecisionService
HybridModerationRiskService
AdminAccessPolicy
AdminUserService
AdminMerchantService
AdminReadService
AdminMaintenanceService
```

Do not create parallel `ConfigRule`, `PolicySetting`, `AbuseCase`, `AdminOrderCommand`, `TrustScore` or second Audit/Outbox hierarchies.

## Migration handoff

Plan 08 ends at:

```text
V013__moderation_system_settings_admin.sql
```

Plan 09 starts at:

```text
V014
```

Plan 09 must not renumber/edit V013.

---

# Execution Handoff

When Plan 08 implementation begins, use the isolated worktree required by the repository Superpowers workflow.

Recommended execution:

```text
1. Subagent-Driven Development
   - fresh subagent per Task
   - specification review after each Task
   - code-quality review after specification passes
   - fresh focused verification before completion claims

2. Inline Executing Plans
   - execute Tasks 1 -> 20 in dependency order
   - stop at review checkpoints
   - preserve TDD RED/GREEN evidence
   - never skip fresh verification
```

Do not start Plan 09 implementation until Task 20 acceptance evidence is green.
