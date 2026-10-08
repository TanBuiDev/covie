# CoVie Plan 03 — Merchant, Location, Media & Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Use `superpowers:using-git-worktrees` before implementation. Every task ends with a reviewable verification gate and commit.

**Project:** CoVie  
**Plan:** 03 / 10  
**Target repo path:** `docs/superpowers/plans/2026-08-08-covie-merchant-catalog.md`  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Closed — locally verified

> **Execution closure — 2026-08-25:** Implemented and locally verified by the fresh backend regression and the closure audit. The task checklist below is retained as the original implementation specification, not a live completion tracker. See `docs/CoVie-plan-closure-audit-2026-08-24.md`.

> **Public merchant read-contract checkpoint — 2026-09-14:** `GET /api/v1/catalog/merchants/{merchantId}` now includes server-derived `capabilities.catalogTypes`, and anonymous `GET /api/v1/catalog/merchants/{merchantId}/items` returns a merchant-scoped cursor page of public Product, Service, and Resale cards. One JDBC visibility predicate covers merchant, item moderation/lifecycle, active variants, available offers, owned active locations, media readiness, and minimum qualifying price. Capabilities remain an ordered Product/Service summary rather than a persisted Shop/Provider type. Focused API coverage passes 15 tests; the full backend gate passes 491 tests with 1 intentional skip. Generated OpenAPI/client check, web typecheck, zero-warning lint, 58 Vitest files / 139 tests, production build, and `git diff --check` pass. PUB-009 consumes only the generated operation and supports isolated catalog errors plus opaque cursor navigation. Exact frozen Figma contexts were read before the UI change; fresh viewport comparison remains pending and is not claimed by this checkpoint.

> **Seller existing-item checkpoint — 2026-09-15:** The authenticated `GET /api/v1/merchants/{merchantId}/catalog-items/{itemId}` returns a merchant-scoped management aggregate with item ETag/version, nested variant/offer versions and role-scoped server-owned actions. The generated client backs `/seller/[merchantId]/catalog/[itemId]`; the form edits category-defined attributes (including missing draft values and single/multi-select), media, variants, offers and lifecycle through granular commands. The focused management-detail/edit/lifecycle/publish API run passes 21 tests; fresh Maven `clean verify` exits `0` with 500 total tests, 0 failures/errors and 1 skip. The latest web-only attribute changes pass 61 Vitest files / 158 tests, typecheck, zero-warning lint and production build. Existing-item contract-gap wording is resolved, but Seller populated runtime, exact 360/390/desktop Figma captures and Seller/Admin E2E are not yet accepted; see the Task 10 child plan.

> **Nullable offer-preparation contract — 2026-09-16:** `UpdateLocationOfferRequest.preparationTimeMinutes` now exports an integer/null union instead of number-only: omission retains the stored time, explicit `null` clears it. The generated client accepts `number | null`, and the existing-item Seller form sends `null` when an existing value is cleared. API export, generated-client check and focused 16-test form suite pass after red/green regressions. Fresh Maven `clean verify` passes with 500 total tests (0 failures/errors, 1 skip); web tests pass 61 files / 159 tests, with typecheck, lint and build exiting `0`. Runtime/visual acceptance remains separate.

> **Task 10 detail-order and Service-safety follow-up — 2026-09-16:** Selected variant option IDs now follow option-group order, not UUID order. The management-detail suite passes 11 tests, including direct typed-attribute and pending-media assertions. Service offer PATCH omits `inventoryMode` rather than serializing an undefined Product-only field; focused Seller form coverage passes 17 tests. Fresh Maven `clean verify` passes with 503 total tests (0 failures/errors, 1 skip), and latest web gates pass 61 files / 160 tests, typecheck, lint and build. Seller runtime/visual acceptance remains pending.

> **Task 10 authoritative-refresh follow-up — 2026-09-16:** The existing-item page remounts its client editor when the item ETag, a variant version or a location-offer version changes. Red/green route coverage caught stale client input after nested-only mutations; focused route/form suites pass 4/20 tests respectively. Archive confirmation, pending-command duplicate prevention and ordinary-failure retry have direct form coverage. Fresh full web tests pass 61 files / 166 tests; typecheck, zero-warning lint and build pass. The earlier Maven 503-test gate remains current because this follow-up changed only web code/tests. Seller runtime/visual and E2E acceptance remain pending.

**Goal:** Build the first marketplace slice on top of Plan 01–02: merchant ownership/staff/location authorization, location hierarchy and schedules, direct-to-object-storage media uploads, categories, catalog/options/variants/location offers, seller lifecycle commands, and public location-aware catalog discovery/detail where every purchasable result resolves to one concrete `MerchantLocation` and `locationVariantId`.

**Architecture:** Plan 03 adds four business modules—`location`, `merchants`, `media`, and `catalog`—while preserving the modular-monolith boundary. PostgreSQL/PostGIS remains authoritative for merchant access, locations, catalog and offer visibility. Object bytes live only in S3-compatible storage; Spring Boot issues/validates upload metadata and presigned requests. Public discovery uses PostgreSQL full-text + PostGIS keyset queries. `LocationVariant` is the only purchasable offer identity and the only location-specific price source.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1.0, Spring Data JPA 4.1, Hibernate Spatial, PostgreSQL 18 + PostGIS 3.6, Flyway, AWS SDK for Java 2.x (`S3Client` + `S3Presigner`), MinIO local S3-compatible storage, Jackson, MockMvc + Spring Security Test, Testcontainers/PostGIS, Next.js 16.2, React 19, TypeScript strict, Vitest, generated OpenAPI client.

---

# Global Constraints

- Plan 01 and Plan 02 acceptance gates must be green before implementation starts.
- `/api/v1` remains the only public API base path.
- Spring Boot is authoritative for authorization, visibility, price, location resolution and lifecycle transitions.
- A user may own/manage multiple `MerchantProfile`s; ownership exists only through `merchant_memberships`.
- `OWNER` and `MANAGER` have merchant-wide operational access; `STAFF` has operational access only to explicitly assigned `MerchantLocation`s.
- Only `OWNER` can manage membership roles that include `OWNER`; `OWNER` and `MANAGER` can add/revoke MANAGER/STAFF subject to last-owner protection.
- Plan 03 does not implement email invitations. Adding a member resolves an already-registered CoVie user by normalized local EMAIL identity and activates the membership immediately. `INVITED` remains schema-reserved for a later version.
- Merchant JWT claims remain identity/global-role only; OWNER/MANAGER/STAFF are loaded from PostgreSQL on every merchant-authorized request.
- Invisible merchant/private resources return 404; a visible resource with a forbidden action returns 403.
- `MerchantLocation` is the operational boundary for offers, later inventory, delivery, orders, Need responses and STAFF permission.
- `geo_point` uses `GEOGRAPHY(Point,4326)`; application construction is longitude first, latitude second.
- Location timezone is an IANA zone and is validated with `ZoneId.of(...)`.
- Every `CatalogItem` has at least one `Variant`. An item without visible options gets exactly one hidden default variant.
- `LocationVariant = MerchantLocation + Variant` is the authoritative purchasable offer.
- Authoritative price never exists on `CatalogItem` or `Variant`.
- Product offers require `inventoryMode = TRACKED | UNTRACKED`; Service offers require `inventoryMode = null`.
- Service scale/package is represented by Variant; later cart/order quantity for Service remains 1.
- Initial catalog creation is one transaction and supports temporary `clientKey` references.
- Existing catalog structures use granular edit APIs; clients do not replace the whole aggregate to change one field.
- Mutable seller resources use `@Version` + response `ETag` + request `If-Match` where specified.
- Listing lifecycle is command-based. Clients never PATCH `listingStatus` or `moderationStatus` directly.
- Plan 03 uses an `AutoPublishCatalogPolicy` implementation behind a port. Plan 08 replaces that policy with Settings/Moderation-aware behavior without changing catalog controllers.
- Public discovery uses one `/api/v1/catalog/items` endpoint; Product/Service/Resale are typed filters, not separate APIs.
- Search requests require explicit LocationContext (`lat+lng` or owned `addressId`). Catalog detail may additionally use an explicit `locationId`; without context it resolves a deterministic active merchant location and reports that resolution source.
- Every public catalog response with a buyable variant exposes a concrete `locationVariantId`; add-to-cart in Plan 04 must never silently change it to another location.
- Public catalog discovery in Plan 03 implements `q`, `type`, `listingKind`, `categoryId`, location/radius, `pickup`, `openNow`, `minPrice`, `maxPrice`, and sorts `RELEVANCE`, `DISTANCE`, `PRICE_ASC`, `PRICE_DESC`, `NEWEST`. Rating and delivery/shipping filters are added only after Plans 04/07 provide those data sources.
- Media upload accepts only JPEG, PNG and WebP in V1. SVG is rejected.
- Media raw bytes never pass through Spring MVC. Browser/client uploads directly to object storage with a short-lived presigned PUT URL.
- Media completion verifies object existence, exact size, content type and image magic bytes before `READY`.
- Arbitrary external image URLs cannot be attached as authoritative resource media.
- The public media URL is derived from configured `publicBaseUrl + storageKey`; storage bucket/key details are not separately exposed in public DTOs.
- `idempotency_records` and `outbox_events` are pulled forward into Plan 03 because Plan 03 is the first plan with commands whose approved API contract already requires idempotency/outbox-ready domain transitions.
- Redis remains non-authoritative and is not required for Plan 03 correctness.
- PostgreSQL search remains the V1 search engine; no Elasticsearch/OpenSearch/Meilisearch is introduced.
- JPA entities are never serialized directly.
- Money remains integer minor units + ISO currency code.
- No inventory balance, cart, shipping coverage, checkout or Order tables are implemented in this plan.
- No production storage keys/secrets are committed.

---

# Dependency and Migration Ordering Correction

Plan 02 established the authoritative Flyway sequence:

```text
V001__extensions_and_foundation.sql
V002__identity_users_and_audit.sql
V003__auth_sessions_and_tickets.sql
```

Plan 03 continues sequentially:

```text
V004__location_merchant_media.sql
V005__catalog_and_search.sql
V006__idempotency_and_outbox.sql
```

This plan intentionally pulls `idempotency_records` and `outbox_events` forward from the Master Plan's later conceptual grouping. The first approved API commands that need them already exist in Plan 03:

```text
POST /api/v1/media/{mediaId}/complete        # Idempotency-Key
POST /api/v1/merchants/{merchantId}/catalog-items  # Idempotency-Key
catalog lifecycle transitions                 # outbox-ready events
```

Rules:

```text
Flyway outOfOrder stays false
never rename or edit V001-V003 after they have been applied
later plans continue from V007
checkout_sessions remains Plan 04
outbox consumer/notification projection remains Plan 07
```

---

# Role and Capability Matrix Locked by Plan 03

```text
Capability                              OWNER   MANAGER   STAFF assigned location
--------------------------------------------------------------------------------
Read merchant management view            yes      yes       yes
Edit MerchantProfile                     yes      yes       no
Create/edit/delete MerchantLocation      yes      yes       no
Manage weekly hours/exceptions            yes      yes       no
Manage merchant/location contacts         yes      yes       no
Add MANAGER/STAFF member                  yes      yes       no
Grant/revoke OWNER role                   yes      no        no
Revoke MANAGER/STAFF                      yes      yes       no
Assign STAFF locations                    yes      yes       no
Create/edit global CatalogItem structure  yes      yes       no
Publish/pause/archive CatalogItem          yes      yes       no
Edit LocationVariant at assigned location yes      yes       yes
Upload media                              yes      yes       yes
Attach merchant logo/cover                yes      yes       no
Attach catalog media                      yes      yes       no
Read assigned operational offer           yes      yes       yes
```

A STAFF user with no assigned locations has no merchant operational location access. Promotion from STAFF to MANAGER immediately grants merchant-wide operational access. Demotion from MANAGER to STAFF grants zero location access until explicit assignments are written.

---

# Repository Map Added by Plan 03

```text
apps/api/src/main/java/vn/covie/
├── location/
│   ├── api/
│   │   ├── LocationAreaController.java
│   │   └── dto/
│   ├── application/
│   │   ├── LocationAreaService.java
│   │   ├── LocationContext.java
│   │   ├── LocationContextResolver.java
│   │   └── LocationResolutionSource.java
│   ├── model/
│   │   ├── LocationAreaEntity.java
│   │   ├── LocationAreaStatus.java
│   │   └── LocationAreaType.java
│   └── persistence/
│       └── LocationAreaRepository.java
├── merchants/
│   ├── api/
│   │   ├── MeMerchantController.java
│   │   ├── MerchantController.java
│   │   ├── MerchantMemberController.java
│   │   ├── MerchantLocationController.java
│   │   ├── MerchantHoursController.java
│   │   ├── MerchantContactController.java
│   │   └── dto/
│   ├── application/
│   │   ├── MerchantApplicationService.java
│   │   ├── MerchantMembershipService.java
│   │   ├── MerchantLocationService.java
│   │   ├── MerchantHoursService.java
│   │   └── MerchantContactService.java
│   ├── model/
│   │   ├── MerchantProfileEntity.java
│   │   ├── MerchantMembershipEntity.java
│   │   ├── MerchantMemberLocationEntity.java
│   │   ├── MerchantLocationEntity.java
│   │   ├── MerchantContactMethodEntity.java
│   │   ├── MerchantLocationHourEntity.java
│   │   ├── MerchantLocationHourExceptionEntity.java
│   │   ├── MerchantRole.java
│   │   ├── MembershipStatus.java
│   │   ├── MerchantStatus.java
│   │   ├── MerchantModerationStatus.java
│   │   ├── MerchantLocationStatus.java
│   │   ├── PinSource.java
│   │   ├── ContactType.java
│   │   ├── ContactStatus.java
│   │   └── OperationType.java
│   ├── persistence/
│   │   ├── MerchantProfileRepository.java
│   │   ├── MerchantMembershipRepository.java
│   │   ├── MerchantMemberLocationRepository.java
│   │   ├── MerchantLocationRepository.java
│   │   ├── MerchantContactMethodRepository.java
│   │   ├── MerchantLocationHourRepository.java
│   │   └── MerchantLocationHourExceptionRepository.java
│   └── security/
│       ├── MerchantAccessPolicy.java
│       ├── MerchantCapability.java
│       └── MerchantAccess.java
├── media/
│   ├── api/
│   │   ├── MediaController.java
│   │   └── dto/
│   ├── application/
│   │   ├── MediaUploadService.java
│   │   ├── MediaAttachmentPolicy.java
│   │   ├── MediaUrlResolver.java
│   │   └── MediaMagicSniffer.java
│   ├── model/
│   │   ├── MediaAssetEntity.java
│   │   ├── MediaStatus.java
│   │   └── MediaPurpose.java
│   ├── persistence/
│   │   └── MediaAssetRepository.java
│   └── storage/
│       ├── ObjectStorage.java
│       ├── S3ObjectStorage.java
│       ├── S3StorageConfiguration.java
│       ├── StorageProperties.java
│       ├── PresignedUpload.java
│       └── StoredObjectMetadata.java
├── catalog/
│   ├── api/
│   │   ├── PublicCatalogController.java
│   │   ├── CategoryController.java
│   │   ├── MerchantCatalogController.java
│   │   └── dto/
│   ├── application/
│   │   ├── CatalogCreateService.java
│   │   ├── CatalogEditService.java
│   │   ├── CatalogLifecycleService.java
│   │   ├── CatalogPublishValidator.java
│   │   ├── CatalogSearchDocumentService.java
│   │   ├── CatalogDiscoveryService.java
│   │   ├── CatalogDetailService.java
│   │   └── CatalogCursorCodec.java
│   ├── model/
│   │   ├── CategoryEntity.java
│   │   ├── CategoryAttributeEntity.java
│   │   ├── CategoryAttributeOptionEntity.java
│   │   ├── CatalogItemEntity.java
│   │   ├── OptionGroupEntity.java
│   │   ├── OptionValueEntity.java
│   │   ├── VariantEntity.java
│   │   ├── VariantOptionValueEntity.java
│   │   ├── LocationVariantEntity.java
│   │   ├── CatalogItemMediaEntity.java
│   │   ├── CatalogItemType.java
│   │   ├── ListingKind.java
│   │   ├── ListingStatus.java
│   │   ├── CatalogModerationStatus.java
│   │   ├── VariantStatus.java
│   │   ├── InventoryMode.java
│   │   ├── CategoryCatalogType.java
│   │   ├── CategoryStatus.java
│   │   └── CategoryAttributeType.java
│   ├── persistence/
│   │   ├── CategoryRepository.java
│   │   ├── CategoryAttributeRepository.java
│   │   ├── CategoryAttributeOptionRepository.java
│   │   ├── CatalogItemRepository.java
│   │   ├── CatalogItemAttributeValueRepository.java
│   │   ├── TagRepository.java
│   │   ├── OptionGroupRepository.java
│   │   ├── OptionValueRepository.java
│   │   ├── VariantRepository.java
│   │   ├── VariantOptionValueRepository.java
│   │   ├── LocationVariantRepository.java
│   │   ├── CatalogItemMediaRepository.java
│   │   ├── CatalogDiscoveryRepository.java
│   │   └── JdbcCatalogDiscoveryRepository.java
│   └── policy/
│       ├── CatalogPublishPolicy.java
│       ├── AutoPublishCatalogPolicy.java
│       └── CatalogPublishDecision.java
└── shared/
    ├── concurrency/
    │   └── VersionPrecondition.java
    ├── idempotency/
    │   ├── IdempotencyRecordEntity.java
    │   ├── IdempotencyRecordRepository.java
    │   ├── IdempotencyExecutor.java
    │   └── IdempotencyHasher.java
    └── outbox/
        ├── OutboxEventEntity.java
        ├── OutboxEventRepository.java
        └── JpaOutboxEventWriter.java
```

Frontend additions:

```text
apps/web/
├── app/(public)/explore/page.tsx
├── app/(public)/items/[itemId]/page.tsx
├── app/(seller)/seller/page.tsx
├── app/(seller)/seller/[merchantId]/page.tsx
├── app/(seller)/seller/[merchantId]/locations/page.tsx
├── app/(seller)/seller/[merchantId]/catalog/page.tsx
├── app/(seller)/seller/[merchantId]/catalog/new/page.tsx
├── features/location/
│   ├── location-context.ts
│   └── location-input.tsx
├── features/merchant/
│   ├── merchant-api.ts
│   ├── merchant-form.tsx
│   └── location-form.tsx
├── features/media/
│   ├── media-api.ts
│   └── direct-image-upload.ts
└── features/catalog/
    ├── catalog-api.ts
    ├── catalog-card.tsx
    ├── catalog-create-form.tsx
    └── catalog-detail.tsx
```

---

# Public Interfaces Produced by Plan 03

Later plans may rely on these exact backend interfaces/classes:

```text
vn.covie.location.application.LocationContext
vn.covie.location.application.LocationContextResolver
vn.covie.merchants.security.MerchantAccessPolicy
vn.covie.merchants.security.MerchantCapability
vn.covie.merchants.persistence.MerchantLocationRepository
vn.covie.catalog.persistence.LocationVariantRepository
vn.covie.catalog.model.LocationVariantEntity
vn.covie.catalog.application.CatalogDetailService
vn.covie.media.application.MediaUrlResolver
vn.covie.shared.idempotency.IdempotencyExecutor
vn.covie.shared.outbox.OutboxEventWriter              # Plan 01 interface
vn.covie.shared.outbox.JpaOutboxEventWriter           # Plan 03 implementation
vn.covie.shared.concurrency.VersionPrecondition
```

Stable API surface introduced by this plan:

```text
GET  /api/v1/location-areas
GET  /api/v1/categories

GET  /api/v1/me/merchants
POST /api/v1/me/merchants

GET   /api/v1/merchants/{merchantId}
PATCH /api/v1/merchants/{merchantId}
GET    /api/v1/merchants/{merchantId}/members
POST   /api/v1/merchants/{merchantId}/members
PATCH  /api/v1/merchants/{merchantId}/members/{memberId}
DELETE /api/v1/merchants/{merchantId}/members/{memberId}
PUT    /api/v1/merchants/{merchantId}/members/{memberId}/locations
GET    /api/v1/merchants/{merchantId}/locations
POST   /api/v1/merchants/{merchantId}/locations
GET    /api/v1/merchants/{merchantId}/locations/{locationId}
PATCH  /api/v1/merchants/{merchantId}/locations/{locationId}
DELETE /api/v1/merchants/{merchantId}/locations/{locationId}
PUT    /api/v1/merchants/{merchantId}/locations/{locationId}/hours
GET    /api/v1/merchants/{merchantId}/contacts
POST   /api/v1/merchants/{merchantId}/contacts
PATCH  /api/v1/merchants/{merchantId}/contacts/{contactId}
DELETE /api/v1/merchants/{merchantId}/contacts/{contactId}

POST /api/v1/media/uploads
POST /api/v1/media/{mediaId}/complete

GET  /api/v1/merchants/{merchantId}/catalog-items
POST /api/v1/merchants/{merchantId}/catalog-items
GET  /api/v1/merchants/{merchantId}/catalog-items/{itemId}
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId}
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}/location-offers/{locationVariantId}
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/pause
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/activate
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/archive

GET /api/v1/catalog/items
GET /api/v1/catalog/items/{itemId}
GET /api/v1/catalog/merchants/{merchantId}
```

---

# Task 1: Add Plan 03 Dependencies and Create V004–V006 Migrations

**Files:**
- Modify: `apps/api/pom.xml`
- Create: `apps/api/src/main/resources/db/migration/V004__location_merchant_media.sql`
- Create: `apps/api/src/main/resources/db/migration/V005__catalog_and_search.sql`
- Create: `apps/api/src/main/resources/db/migration/V006__idempotency_and_outbox.sql`
- Test: `apps/api/src/test/java/vn/covie/merchantcatalog/persistence/Plan03SchemaMigrationTest.java`

**Interfaces:**
- Consumes: Plan 02 schema through V003 and the shared `OutboxEventWriter` port from Plan 01.
- Produces: all Plan 03 database structures and migration baseline for Plan 04, which must start at V007.

- [ ] **Step 1: Write the migration integration test before creating the migrations**

```java
package vn.covie.merchantcatalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class Plan03SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired DataSource dataSource;

    @Test
    void createsMerchantCatalogMediaAndReliabilityTables() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(tableExists(c, "location_areas")).isTrue();
            assertThat(tableExists(c, "merchant_profiles")).isTrue();
            assertThat(tableExists(c, "merchant_locations")).isTrue();
            assertThat(tableExists(c, "media_assets")).isTrue();
            assertThat(tableExists(c, "catalog_items")).isTrue();
            assertThat(tableExists(c, "variants")).isTrue();
            assertThat(tableExists(c, "location_variants")).isTrue();
            assertThat(tableExists(c, "idempotency_records")).isTrue();
            assertThat(tableExists(c, "outbox_events")).isTrue();
        }
    }

    @Test
    void locationAndCatalogGeoSearchIndexesExist() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(indexExists(c, "ix_merchant_locations_geo")).isTrue();
            assertThat(indexExists(c, "ix_catalog_items_search_vector")).isTrue();
        }
    }

    private boolean tableExists(Connection c, String name) throws Exception {
        try (var ps = c.prepareStatement("select to_regclass('public.' || ?) is not null")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
        }
    }

    private boolean indexExists(Connection c, String name) throws Exception {
        try (var ps = c.prepareStatement("select to_regclass('public.' || ?) is not null")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
        }
    }
}
```

- [ ] **Step 2: Add AWS SDK S3 dependency through one pinned BOM property**

Add to `apps/api/pom.xml`:

```xml
<properties>
    <!-- keep existing properties -->
    <aws.sdk.version>2.42.37</aws.sdk.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws.sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- keep existing dependencies -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3</artifactId>
    </dependency>
</dependencies>
```

Do not add a second MinIO Java SDK. Production and local development share the AWS SDK S3 contract; local MinIO is reached through endpoint override + path-style access.

- [ ] **Step 3: Create `V004__location_merchant_media.sql`**

```sql
create table location_areas (
    id uuid primary key,
    parent_id uuid null references location_areas(id),
    area_type varchar(40) not null,
    name varchar(180) not null,
    slug varchar(200) not null,
    path_cache text null,
    geo_boundary geography(MultiPolygon,4326) null,
    status varchar(20) not null,
    sort_order integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_location_area_status check (status in ('ACTIVE','INACTIVE','DELETED'))
);

create unique index ux_location_area_parent_slug
    on location_areas(coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), slug)
    where status <> 'DELETED';
create index ix_location_area_parent_sort on location_areas(parent_id, sort_order, name);
create index ix_location_area_boundary on location_areas using gist(geo_boundary);

create table media_assets (
    id uuid primary key,
    owner_user_id uuid null references users(id),
    storage_provider varchar(30) not null,
    storage_key text not null,
    upload_purpose varchar(40) not null,
    mime_type varchar(120) not null,
    size_bytes bigint not null,
    width integer null,
    height integer null,
    checksum varchar(128) null,
    media_status varchar(20) not null,
    upload_expires_at timestamptz not null,
    verified_at timestamptz null,
    created_at timestamptz not null,
    deleted_at timestamptz null,
    constraint ck_media_status check (media_status in ('PENDING_UPLOAD','READY','FAILED','DELETED')),
    constraint ck_media_size check (size_bytes > 0)
);

create unique index ux_media_storage_key on media_assets(storage_key);
create index ix_media_owner_status_created on media_assets(owner_user_id, media_status, created_at desc);

alter table users
    add constraint fk_users_avatar_media
    foreign key (avatar_media_id) references media_assets(id);

create table merchant_profiles (
    id uuid primary key,
    name varchar(180) not null,
    slug varchar(200) not null,
    description text null,
    logo_media_id uuid null references media_assets(id),
    cover_media_id uuid null references media_assets(id),
    merchant_status varchar(30) not null,
    moderation_status varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_merchant_status check (merchant_status in ('ACTIVE','PAUSED','SUSPENDED','DELETED')),
    constraint ck_merchant_moderation_status check (moderation_status in ('NOT_REQUIRED','PENDING','APPROVED','REJECTED','HIDDEN'))
);

create unique index ux_merchant_profile_slug on merchant_profiles(slug) where deleted_at is null;

create table merchant_memberships (
    id uuid primary key,
    merchant_profile_id uuid not null references merchant_profiles(id),
    user_id uuid not null references users(id),
    role varchar(20) not null,
    membership_status varchar(20) not null,
    joined_at timestamptz null,
    created_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_merchant_membership_role check (role in ('OWNER','MANAGER','STAFF')),
    constraint ck_merchant_membership_status check (membership_status in ('ACTIVE','INVITED','REVOKED'))
);

create unique index ux_merchant_membership_user
    on merchant_memberships(merchant_profile_id, user_id)
    where membership_status in ('ACTIVE','INVITED');
create index ix_merchant_membership_user_status
    on merchant_memberships(user_id, membership_status, merchant_profile_id);

create table merchant_locations (
    id uuid primary key,
    merchant_profile_id uuid not null references merchant_profiles(id),
    name varchar(150) not null,
    address_text text not null,
    location_area_id uuid null references location_areas(id),
    geo_point geography(Point,4326) not null,
    pin_source varchar(30) not null,
    timezone varchar(60) not null,
    location_status varchar(30) not null,
    pickup_enabled boolean not null default false,
    seller_delivery_enabled boolean not null default false,
    service_enabled boolean not null default false,
    minimum_delivery_order bigint null,
    currency_code char(3) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_merchant_location_status check (location_status in ('ACTIVE','PAUSED','DELETED')),
    constraint ck_merchant_location_pin_source check (pin_source in ('GPS','GEOCODED','USER_ADJUSTED')),
    constraint ck_merchant_location_min_order check (minimum_delivery_order is null or minimum_delivery_order >= 0)
);

create index ix_merchant_locations_profile_status
    on merchant_locations(merchant_profile_id, location_status);
create index ix_merchant_locations_area on merchant_locations(location_area_id);
create index ix_merchant_locations_geo on merchant_locations using gist(geo_point);

create table merchant_member_locations (
    id uuid primary key,
    membership_id uuid not null references merchant_memberships(id),
    merchant_location_id uuid not null references merchant_locations(id),
    created_at timestamptz not null,
    unique(membership_id, merchant_location_id)
);

create table merchant_contact_methods (
    id uuid primary key,
    merchant_profile_id uuid not null references merchant_profiles(id),
    merchant_location_id uuid null references merchant_locations(id),
    contact_type varchar(30) not null,
    value text null,
    qr_media_id uuid null references media_assets(id),
    is_primary boolean not null default false,
    is_public boolean not null default true,
    status varchar(20) not null,
    created_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_merchant_contact_type check (contact_type in ('ZALO_LINK','ZALO_QR','MESSENGER_LINK','PHONE','OTHER')),
    constraint ck_merchant_contact_status check (status in ('ACTIVE','INACTIVE')),
    constraint ck_merchant_contact_payload check (value is not null or qr_media_id is not null)
);

create index ix_merchant_contact_profile_location
    on merchant_contact_methods(merchant_profile_id, merchant_location_id, status);

create table merchant_location_hours (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    day_of_week smallint not null,
    operation_type varchar(20) not null,
    open_time time null,
    close_time time null,
    is_closed boolean not null default false,
    sort_order integer not null default 0,
    constraint ck_merchant_hour_day check (day_of_week between 1 and 7),
    constraint ck_merchant_hour_operation check (operation_type in ('SHOP','DELIVERY','PICKUP')),
    constraint ck_merchant_hour_window check (
        (is_closed and open_time is null and close_time is null)
        or
        (not is_closed and open_time is not null and close_time is not null and open_time < close_time)
    )
);

create index ix_merchant_hours_location_day
    on merchant_location_hours(merchant_location_id, day_of_week, operation_type, sort_order);

create table merchant_location_hour_exceptions (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    local_date date not null,
    operation_type varchar(20) not null,
    open_time time null,
    close_time time null,
    is_closed boolean not null default false,
    note text null,
    sort_order integer not null default 0,
    constraint ck_merchant_hour_exception_operation check (operation_type in ('SHOP','DELIVERY','PICKUP')),
    constraint ck_merchant_hour_exception_window check (
        (is_closed and open_time is null and close_time is null)
        or
        (not is_closed and open_time is not null and close_time is not null and open_time < close_time)
    )
);

create index ix_merchant_hour_exception_location_date
    on merchant_location_hour_exceptions(merchant_location_id, local_date, operation_type, sort_order);

alter table user_addresses
    add constraint fk_user_addresses_location_area
    foreign key (location_area_id) references location_areas(id);
```

- [ ] **Step 4: Create `V005__catalog_and_search.sql`**

```sql
create table categories (
    id uuid primary key,
    parent_id uuid null references categories(id),
    name varchar(160) not null,
    slug varchar(180) not null,
    catalog_type varchar(20) not null,
    status varchar(20) not null,
    sort_order integer not null default 0,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_category_catalog_type check (catalog_type in ('PRODUCT','SERVICE','BOTH')),
    constraint ck_category_status check (status in ('ACTIVE','INACTIVE','DELETED'))
);
create unique index ux_categories_parent_slug
    on categories(coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), slug)
    where deleted_at is null;
create index ix_categories_parent_sort on categories(parent_id, sort_order, name);

create table category_attributes (
    id uuid primary key,
    category_id uuid not null references categories(id),
    name varchar(120) not null,
    attribute_key varchar(100) not null,
    data_type varchar(30) not null,
    is_required boolean not null default false,
    is_filterable boolean not null default false,
    sort_order integer not null default 0,
    unique(category_id, attribute_key),
    constraint ck_category_attribute_type check (data_type in ('TEXT','NUMBER','BOOLEAN','SINGLE_SELECT','MULTI_SELECT'))
);

create table category_attribute_options (
    id uuid primary key,
    category_attribute_id uuid not null references category_attributes(id),
    option_value varchar(180) not null,
    sort_order integer not null default 0,
    unique(category_attribute_id, option_value)
);

create table tags (
    id uuid primary key,
    name varchar(100) not null,
    normalized_name varchar(100) not null unique,
    created_at timestamptz not null
);

create table catalog_items (
    id uuid primary key,
    merchant_profile_id uuid not null references merchant_profiles(id),
    item_type varchar(20) not null,
    listing_kind varchar(20) not null,
    primary_category_id uuid not null references categories(id),
    name varchar(220) not null,
    slug varchar(240) not null,
    description text null,
    listing_status varchar(30) not null,
    moderation_status varchar(30) not null,
    search_vector tsvector null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_catalog_item_type check (item_type in ('PRODUCT','SERVICE')),
    constraint ck_catalog_listing_kind check (listing_kind in ('STANDARD','RESALE')),
    constraint ck_catalog_resale_product check (listing_kind <> 'RESALE' or item_type = 'PRODUCT'),
    constraint ck_catalog_listing_status check (listing_status in ('DRAFT','ACTIVE','PAUSED','DELETED')),
    constraint ck_catalog_moderation_status check (moderation_status in ('NOT_REQUIRED','PENDING','APPROVED','REJECTED','HIDDEN'))
);
create unique index ux_catalog_item_merchant_slug
    on catalog_items(merchant_profile_id, slug) where deleted_at is null;
create index ix_catalog_items_merchant_state
    on catalog_items(merchant_profile_id, listing_status, moderation_status);
create index ix_catalog_items_category_type_state
    on catalog_items(primary_category_id, item_type, listing_status);
create index ix_catalog_items_search_vector on catalog_items using gin(search_vector);

create table catalog_item_attribute_values (
    id uuid primary key,
    catalog_item_id uuid not null references catalog_items(id),
    category_attribute_id uuid not null references category_attributes(id),
    value_text text null,
    value_number numeric(20,6) null,
    value_boolean boolean null,
    attribute_option_id uuid null references category_attribute_options(id)
);
create index ix_catalog_attribute_values_item on catalog_item_attribute_values(catalog_item_id);

create table catalog_item_tags (
    catalog_item_id uuid not null references catalog_items(id),
    tag_id uuid not null references tags(id),
    primary key(catalog_item_id, tag_id)
);

create table option_groups (
    id uuid primary key,
    catalog_item_id uuid not null references catalog_items(id),
    name varchar(120) not null,
    sort_order integer not null default 0,
    version bigint not null default 0
);
create index ix_option_groups_item_sort on option_groups(catalog_item_id, sort_order);

create table option_values (
    id uuid primary key,
    option_group_id uuid not null references option_groups(id),
    value varchar(160) not null,
    sort_order integer not null default 0,
    unique(option_group_id, value)
);

create table variants (
    id uuid primary key,
    catalog_item_id uuid not null references catalog_items(id),
    sku varchar(120) null,
    variant_name varchar(240) not null,
    is_default boolean not null default false,
    variant_status varchar(20) not null,
    sort_order integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_variant_status check (variant_status in ('ACTIVE','PAUSED','DELETED'))
);
create index ix_variants_item_status on variants(catalog_item_id, variant_status, sort_order);
create unique index ux_variant_default_per_item
    on variants(catalog_item_id) where is_default and deleted_at is null;

create table variant_option_values (
    variant_id uuid not null references variants(id),
    option_value_id uuid not null references option_values(id),
    primary key(variant_id, option_value_id)
);

create table location_variants (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    variant_id uuid not null references variants(id),
    price bigint not null,
    currency_code char(3) not null,
    is_available boolean not null default true,
    inventory_mode varchar(20) null,
    preparation_time_minutes integer null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_location_variant_price check (price >= 0),
    constraint ck_location_variant_inventory_mode check (inventory_mode is null or inventory_mode in ('TRACKED','UNTRACKED')),
    constraint ck_location_variant_prep_time check (preparation_time_minutes is null or preparation_time_minutes >= 0)
);
create unique index ux_location_variant_active
    on location_variants(merchant_location_id, variant_id) where deleted_at is null;
create index ix_location_variant_location_available
    on location_variants(merchant_location_id, is_available) where deleted_at is null;

create table catalog_item_media (
    catalog_item_id uuid not null references catalog_items(id),
    media_asset_id uuid not null references media_assets(id),
    media_type varchar(20) not null,
    sort_order integer not null default 0,
    is_primary boolean not null default false,
    primary key(catalog_item_id, media_asset_id),
    constraint ck_catalog_item_media_type check (media_type in ('IMAGE'))
);

create table merchant_media (
    merchant_profile_id uuid not null references merchant_profiles(id),
    media_asset_id uuid not null references media_assets(id),
    media_type varchar(30) not null,
    sort_order integer not null default 0,
    primary key(merchant_profile_id, media_asset_id)
);
```

The cross-table Product/Service inventory-mode rule is enforced in the Catalog application service because PostgreSQL CHECK constraints cannot directly inspect the referenced CatalogItem type without triggers. Plan 03 adds integration tests for that invariant.

- [ ] **Step 5: Create `V006__idempotency_and_outbox.sql`**

```sql
create table idempotency_records (
    id uuid primary key,
    actor_id uuid not null references users(id),
    operation varchar(120) not null,
    idempotency_key_hash char(64) not null,
    request_hash char(64) not null,
    status varchar(20) not null,
    response_status integer null,
    response_body jsonb null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    constraint ck_idempotency_status check (status in ('IN_PROGRESS','COMPLETED')),
    unique(actor_id, operation, idempotency_key_hash)
);
create index ix_idempotency_expiry on idempotency_records(expires_at);

create table outbox_events (
    id uuid primary key,
    event_type varchar(120) not null,
    aggregate_type varchar(80) not null,
    aggregate_id uuid not null,
    payload jsonb not null,
    occurred_at timestamptz not null,
    status varchar(20) not null,
    attempts integer not null default 0,
    next_attempt_at timestamptz null,
    published_at timestamptz null,
    last_error text null,
    constraint ck_outbox_status check (status in ('PENDING','PROCESSING','PUBLISHED','FAILED')),
    constraint ck_outbox_attempts check (attempts >= 0)
);
create index ix_outbox_dispatch
    on outbox_events(status, next_attempt_at, occurred_at)
    where status in ('PENDING','FAILED');
```

- [ ] **Step 6: Run the migration test and validate Flyway order**

```bash
cd apps/api
./mvnw -Dtest=Plan03SchemaMigrationTest test
```

Expected: PASS, and Flyway applies exactly `V001` through `V006` without `outOfOrder`.

- [ ] **Step 7: Commit**

```bash
git add apps/api/pom.xml apps/api/src/main/resources/db/migration/V004__location_merchant_media.sql apps/api/src/main/resources/db/migration/V005__catalog_and_search.sql apps/api/src/main/resources/db/migration/V006__idempotency_and_outbox.sql apps/api/src/test/java/vn/covie/merchantcatalog/persistence/Plan03SchemaMigrationTest.java
git commit -m "feat(catalog): add merchant catalog media schema"
```

---

# Task 2: Implement Shared ETag/If-Match, Idempotency and Outbox Persistence

**Files:**
- Create: `apps/api/src/main/java/vn/covie/shared/concurrency/VersionPrecondition.java`
- Create: `apps/api/src/main/java/vn/covie/shared/idempotency/IdempotencyRecordEntity.java`
- Create: `apps/api/src/main/java/vn/covie/shared/idempotency/IdempotencyRecordRepository.java`
- Create: `apps/api/src/main/java/vn/covie/shared/idempotency/IdempotencyHasher.java`
- Create: `apps/api/src/main/java/vn/covie/shared/idempotency/IdempotencyExecutor.java`
- Create: `apps/api/src/main/java/vn/covie/shared/outbox/OutboxEventEntity.java`
- Create: `apps/api/src/main/java/vn/covie/shared/outbox/OutboxEventRepository.java`
- Create: `apps/api/src/main/java/vn/covie/shared/outbox/JpaOutboxEventWriter.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/GlobalExceptionHandler.java`
- Test: `apps/api/src/test/java/vn/covie/shared/concurrency/VersionPreconditionTest.java`
- Test: `apps/api/src/test/java/vn/covie/shared/idempotency/IdempotencyExecutorIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/shared/outbox/JpaOutboxEventWriterTest.java`

**Produces:** reusable optimistic-concurrency, idempotency and transactional-outbox infrastructure used by Plan 03 and every later plan.

- [ ] **Step 1: Write ETag parsing tests**

```java
@Test
void parsesQuotedVersionEtag() {
    assertThat(VersionPrecondition.parseRequired("\"v12\"")).isEqualTo(12L);
}

@Test
void formatsVersionEtag() {
    assertThat(VersionPrecondition.format(12L)).isEqualTo("\"v12\"");
}

@Test
void rejectsMissingIfMatch() {
    assertThatThrownBy(() -> VersionPrecondition.parseRequired(null))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).code())
                    .isEqualTo(ErrorCode.IF_MATCH_REQUIRED));
}
```

- [ ] **Step 2: Implement `VersionPrecondition`**

```java
public final class VersionPrecondition {
    private static final Pattern ETAG = Pattern.compile("\\\"v(\\d+)\\\"");

    private VersionPrecondition() {}

    public static String format(long version) {
        return "\"v" + version + "\"";
    }

    public static long parseRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(
                    ErrorCode.IF_MATCH_REQUIRED,
                    HttpStatus.PRECONDITION_REQUIRED,
                    "If-Match is required.");
        }
        Matcher matcher = ETAG.matcher(raw.strip());
        if (!matcher.matches()) {
            throw new ApiException(
                    ErrorCode.INVALID_IF_MATCH,
                    HttpStatus.BAD_REQUEST,
                    "Invalid If-Match value.");
        }
        return Long.parseLong(matcher.group(1));
    }

    public static void require(long expected, long current) {
        if (expected != current) {
            throw new ApiException(
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    HttpStatus.CONFLICT,
                    "Resource version has changed.",
                    Map.of("expectedVersion", expected, "currentVersion", current));
        }
    }
}
```

Add error codes:

```text
IF_MATCH_REQUIRED
INVALID_IF_MATCH
RESOURCE_VERSION_CONFLICT
IDEMPOTENCY_KEY_REQUIRED
IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
```

Map `ObjectOptimisticLockingFailureException` / `OptimisticLockException` to `409 RESOURCE_VERSION_CONFLICT` in `GlobalExceptionHandler`.

- [ ] **Step 3: Write idempotency behavior tests**

Required cases:

```java
@Test
void sameActorOperationKeyAndBodyReturnsStoredResultWithoutRunningCommandTwice() { }

@Test
void sameKeyWithDifferentBodyReturnsConflict() { }

@Test
void sameKeyForDifferentActorsIsIndependent() { }

@Test
void rollbackOfBusinessCommandDoesNotLeaveCompletedIdempotencyRecord() { }
```

The first test uses an `AtomicInteger` command counter and asserts it remains `1` after two calls.

- [ ] **Step 4: Implement canonical request hashing**

```java
@Component
public final class IdempotencyHasher {
    private final ObjectMapper objectMapper;

    public IdempotencyHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    public String sha256(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash idempotency payload", e);
        }
    }

    public long advisoryLockKey(UUID actorId, String operation, String rawKey) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest((actorId + "\n" + operation + "\n" + rawKey).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    }
}
```

- [ ] **Step 5: Implement `IdempotencyExecutor` using a PostgreSQL transaction advisory lock**

```java
@Service
public class IdempotencyExecutor {
    private final JdbcTemplate jdbc;
    private final IdempotencyRecordRepository repository;
    private final IdempotencyHasher hasher;
    private final ObjectMapper mapper;
    private final UuidV7Generator ids;
    private final AppClock clock;

    @Transactional
    public <T> T execute(
            UUID actorId,
            String operation,
            String rawKey,
            Object requestBody,
            int successStatus,
            Class<T> responseType,
            Supplier<T> command) {

        if (rawKey == null || rawKey.isBlank()) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key is required.");
        }

        String keyHash = hasher.sha256(rawKey);
        String requestHash = hasher.sha256(requestBody);
        long lockKey = hasher.advisoryLockKey(actorId, operation, rawKey);
        jdbc.query(
                "select pg_advisory_xact_lock(?)",
                ps -> ps.setLong(1, lockKey),
                rs -> { });

        var existing = repository.findByActorIdAndOperationAndIdempotencyKeyHash(
                actorId, operation, keyHash);

        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.requestHash().equals(requestHash)) {
                throw new ApiException(
                        ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST,
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used with another request.");
            }
            if (record.status() == IdempotencyStatus.COMPLETED) {
                return mapper.convertValue(record.responseBody(), responseType);
            }
        }

        var record = existing.orElseGet(() -> repository.save(
                IdempotencyRecordEntity.inProgress(
                        ids.generate(), actorId, operation, keyHash, requestHash,
                        clock.instant(), clock.instant().plus(Duration.ofHours(24)))));

        T result = command.get();
        record.complete(successStatus, mapper.valueToTree(result));
        return result;
    }
}
```

The transaction advisory lock serializes the exact actor+operation+key namespace without holding a global JVM mutex. If `command.get()` throws, the idempotency insert/update rolls back with the business transaction. No `IN_PROGRESS` tombstone survives a failed business transaction.

Every caller passes its exact success status to `execute(...)`: catalog aggregate creation passes `201`, while media completion passes `200`. Replay returns the stored body and the controller keeps the route's same status contract. The integration tests assert both stored status values.

- [ ] **Step 6: Implement outbox persistence against the Plan 01 port**

```java
@Component
public final class JpaOutboxEventWriter implements OutboxEventWriter {
    private final OutboxEventRepository repository;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final ObjectMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(OutboxEventIntent intent) {
        UUID id = ids.generate();
        repository.save(OutboxEventEntity.pending(
                id,
                intent.eventType(),
                intent.aggregateType(),
                intent.aggregateId(),
                mapper.valueToTree(intent.payload()),
                clock.instant()));
        return id;
    }
}
```

Plan 03 does not implement an outbox dispatcher. It only guarantees domain write + outbox intent atomicity. Plan 07 owns claiming/retry/notification/SSE consumption.

- [ ] **Step 7: Run shared infrastructure tests**

```bash
cd apps/api
./mvnw -Dtest=VersionPreconditionTest,IdempotencyExecutorIntegrationTest,JpaOutboxEventWriterTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/shared apps/api/src/test/java/vn/covie/shared
git commit -m "feat(api): add idempotency outbox and etag infrastructure"
```

---

# Task 3: Implement Location Areas and Explicit LocationContext Resolution

**Files:**
- Create: `apps/api/src/main/java/vn/covie/location/model/LocationAreaEntity.java`
- Create: `apps/api/src/main/java/vn/covie/location/model/LocationAreaStatus.java`
- Create: `apps/api/src/main/java/vn/covie/location/model/LocationAreaType.java`
- Create: `apps/api/src/main/java/vn/covie/location/persistence/LocationAreaRepository.java`
- Create: `apps/api/src/main/java/vn/covie/location/application/LocationAreaService.java`
- Create: `apps/api/src/main/java/vn/covie/location/application/LocationContext.java`
- Create: `apps/api/src/main/java/vn/covie/location/application/LocationContextResolver.java`
- Create: `apps/api/src/main/java/vn/covie/location/application/LocationResolutionSource.java`
- Create: `apps/api/src/main/java/vn/covie/location/application/GeoPointFactory.java`
- Create: `apps/api/src/main/java/vn/covie/location/api/LocationAreaController.java`
- Create: `apps/api/src/main/java/vn/covie/location/api/dto/LocationAreaResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java`
- Test: `apps/api/src/test/java/vn/covie/location/application/LocationContextResolverTest.java`
- Test: `apps/api/src/test/java/vn/covie/location/api/LocationAreaControllerTest.java`

**Interfaces:**
- Consumes: `UserAddressRepository`, `CurrentActorProvider`, Hibernate Spatial/JTS from Plan 02.
- Produces: the location context used by catalog discovery and later checkout/Need/shipping plans.

- [ ] **Step 1: Define the canonical `LocationContext` value**

```java
public record LocationContext(
        LocationResolutionSource source,
        Point point,
        UUID locationAreaId,
        UUID addressId,
        ZoneId timezone) {

    public LocationContext {
        Objects.requireNonNull(source);
        Objects.requireNonNull(point);
        Objects.requireNonNull(timezone);
    }
}
```

Sources:

```java
public enum LocationResolutionSource {
    COORDINATES,
    SAVED_ADDRESS,
    EXPLICIT_MERCHANT_LOCATION,
    DEFAULT_MERCHANT_LOCATION
}
```

- [ ] **Step 2: Write resolver tests first**

Test at least:

```text
lat without lng -> 422 LOCATION_CONTEXT_INVALID
lng without lat -> 422 LOCATION_CONTEXT_INVALID
lat/lng + addressId together -> 422 LOCATION_CONTEXT_AMBIGUOUS
valid guest coordinates -> COORDINATES
addressId unauthenticated -> 401
addressId owned by another user -> 404
owned address -> SAVED_ADDRESS using persisted geo point
latitude/longitude bounds are enforced
```

- [ ] **Step 3: Implement geometry creation once**

```java
@Component
public final class GeoPointFactory {
    private final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326);

    public Point point(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ApiException(
                    ErrorCode.LOCATION_CONTEXT_INVALID,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Latitude or longitude is out of range.");
        }
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
}
```

Place this class under `vn.covie.location.application` and use it for merchant locations too. Do not construct coordinates independently in multiple modules.

- [ ] **Step 4: Implement `LocationContextResolver`**

```java
@Component
public final class LocationContextResolver {
    private final CurrentActorProvider actors;
    private final UserAddressRepository addresses;
    private final GeoPointFactory points;

    public LocationContext resolveForDiscovery(
            Double lat, Double lng, UUID addressId) {
        boolean hasCoordinates = lat != null || lng != null;
        if (hasCoordinates && (lat == null || lng == null)) {
            throw new ApiException(
                    ErrorCode.LOCATION_CONTEXT_INVALID,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Latitude and longitude must be provided together.");
        }
        if (hasCoordinates && addressId != null) {
            throw new ApiException(
                    ErrorCode.LOCATION_CONTEXT_AMBIGUOUS,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Use coordinates or addressId, not both.");
        }
        if (addressId != null) {
            CurrentActor actor = actors.currentActor();
            UserAddressEntity address = addresses.findByIdAndUserIdAndDeletedAtIsNull(addressId, actor.userId())
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            HttpStatus.NOT_FOUND,
                            "Resource not found."));
            return new LocationContext(
                    LocationResolutionSource.SAVED_ADDRESS,
                    address.geoPoint(), address.locationAreaId(), address.id(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
        if (lat != null) {
            return new LocationContext(
                    LocationResolutionSource.COORDINATES,
                    points.point(lat, lng), null, null, ZoneId.of("Asia/Ho_Chi_Minh"));
        }
        throw new ApiException(
                ErrorCode.LOCATION_CONTEXT_REQUIRED,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Discovery requires a location context.");
    }
}
```

Do not allow a caller to supply both coordinates and address ID and then silently prefer one.

- [ ] **Step 5: Implement public location-area tree read**

```http
GET /api/v1/location-areas?parentId={uuid}
```

Return only ACTIVE areas ordered by `sortOrder,name`. A missing `parentId` returns root areas. No admin mutation endpoint is introduced in Plan 03.

- [ ] **Step 6: Extend Plan 02 security rules for public marketplace GET routes**

Add these matchers before the catch-all `/api/v1/**.authenticated()` rule:

```java
.requestMatchers(HttpMethod.GET,
        "/api/v1/location-areas",
        "/api/v1/categories",
        "/api/v1/categories/**",
        "/api/v1/catalog/**").permitAll()
```

`permitAll()` does not disable bearer authentication when credentials are present. A catalog request using `addressId` still calls `CurrentActorProvider.currentActor()` and therefore returns 401 for a guest and 404 for an address owned by another user.

- [ ] **Step 7: Run focused tests**

```bash
cd apps/api
./mvnw -Dtest=LocationContextResolverTest,LocationAreaControllerTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/location apps/api/src/test/java/vn/covie/location apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java
git commit -m "feat(location): add explicit marketplace location context"
```

---

# Task 4: Implement Merchant Persistence and Database-Resolved Access Policy

**Files:**
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantProfileEntity.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantMembershipEntity.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantMemberLocationEntity.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantLocationEntity.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantContactMethodEntity.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantLocationHourEntity.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantLocationHourExceptionEntity.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantRole.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MembershipStatus.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantStatus.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantModerationStatus.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/MerchantLocationStatus.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/PinSource.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/ContactType.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/ContactStatus.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/model/OperationType.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantProfileRepository.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantMembershipRepository.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantMemberLocationRepository.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantLocationRepository.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantContactMethodRepository.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantLocationHourRepository.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/persistence/MerchantLocationHourExceptionRepository.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/security/MerchantCapability.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/security/MerchantAccess.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/security/MerchantAccessPolicy.java`
- Test: `apps/api/src/test/java/vn/covie/merchants/security/MerchantAccessPolicyIntegrationTest.java`

**Interfaces:**
- Consumes: `CurrentActorProvider`, users, UUIDv7, AppClock.
- Produces: one authorization policy all later seller modules must call instead of reimplementing role checks.

- [ ] **Step 1: Model merchant enums exactly**

```java
public enum MerchantRole { OWNER, MANAGER, STAFF }
public enum MembershipStatus { ACTIVE, INVITED, REVOKED }
public enum MerchantStatus { ACTIVE, PAUSED, SUSPENDED, DELETED }
public enum MerchantModerationStatus { NOT_REQUIRED, PENDING, APPROVED, REJECTED, HIDDEN }
public enum MerchantLocationStatus { ACTIVE, PAUSED, DELETED }
public enum PinSource { GPS, GEOCODED, USER_ADJUSTED }
```

- [ ] **Step 2: Implement `@Version` on mutable resources**

`MerchantProfileEntity`, `MerchantMembershipEntity`, `MerchantLocationEntity` and `MerchantContactMethodEntity` include:

```java
@Version
@Column(nullable = false)
private long version;
```

Never manually increment this field.

- [ ] **Step 3: Add access-oriented repository queries**

```java
public interface MerchantMembershipRepository extends JpaRepository<MerchantMembershipEntity, UUID> {
    @Query("""
        select m from MerchantMembershipEntity m
        where m.merchantProfileId = :merchantId
          and m.userId = :userId
          and m.membershipStatus = vn.covie.merchants.model.MembershipStatus.ACTIVE
        """)
    Optional<MerchantMembershipEntity> findActive(UUID merchantId, UUID userId);

    long countByMerchantProfileIdAndRoleAndMembershipStatus(
            UUID merchantId, MerchantRole role, MembershipStatus status);
}
```

`MerchantMemberLocationRepository` exposes:

```java
boolean existsByMembershipIdAndMerchantLocationId(UUID membershipId, UUID locationId);
List<MerchantMemberLocationEntity> findAllByMembershipId(UUID membershipId);
void deleteAllByMembershipId(UUID membershipId);
```

- [ ] **Step 4: Define capabilities instead of scattering role comparisons**

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
    UPLOAD_MERCHANT_MEDIA
}
```

- [ ] **Step 5: Implement `MerchantAccessPolicy.require(...)`**

```java
@Service
public final class MerchantAccessPolicy {
    private final CurrentActorProvider actors;
    private final MerchantMembershipRepository memberships;
    private final MerchantMemberLocationRepository memberLocations;
    private final MerchantLocationRepository locations;

    public MerchantAccess require(UUID merchantId, MerchantCapability capability) {
        CurrentActor actor = actors.currentActor();
        MerchantMembershipEntity membership = memberships.findActive(merchantId, actor.userId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Resource not found."));
        if (!allows(membership.role(), capability)) {
            throw new ApiException(
                    ErrorCode.MERCHANT_ACTION_NOT_ALLOWED,
                    HttpStatus.FORBIDDEN,
                    "Merchant action is not allowed.");
        }
        return new MerchantAccess(actor.userId(), merchantId, membership.id(), membership.role());
    }

    public MerchantAccess requireLocation(
            UUID merchantId, UUID locationId, MerchantCapability capability) {
        MerchantAccess access = require(merchantId, capability);
        MerchantLocationEntity location = locations.findByIdAndMerchantProfileIdAndDeletedAtIsNull(locationId, merchantId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Resource not found."));
        if (access.role() == MerchantRole.STAFF
                && !memberLocations.existsByMembershipIdAndMerchantLocationId(access.membershipId(), location.id())) {
            throw new ApiException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "Resource not found.");
        }
        return access;
    }
}
```

The `allows(...)` method encodes the matrix at the start of this plan and is unit-tested exhaustively.

- [ ] **Step 6: Write authorization tests that prove DB revocation takes effect next request**

Required integration cases:

```text
OWNER can edit profile
MANAGER can edit profile but cannot grant OWNER
STAFF cannot edit profile
STAFF assigned Location A can manage offer A
STAFF cannot discover/manage Location B through private seller endpoints -> 404
REVOKED membership -> next request 404
merchantId from another merchant cannot be used to access a location by ID -> 404
```

- [ ] **Step 7: Run tests**

```bash
cd apps/api
./mvnw -Dtest=MerchantAccessPolicyIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/merchants/model apps/api/src/main/java/vn/covie/merchants/persistence apps/api/src/main/java/vn/covie/merchants/security apps/api/src/test/java/vn/covie/merchants/security
git commit -m "feat(merchant): add merchant access model"
```

---

# Task 5: Implement Merchant Profile and Membership APIs

**Files:**
- Create: `apps/api/src/main/java/vn/covie/merchants/application/MerchantApplicationService.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/application/MerchantMembershipService.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/MeMerchantController.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/MerchantController.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/MerchantMemberController.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/CreateMerchantRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/UpdateMerchantRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/MerchantSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/MerchantManagementResponse.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/AddMerchantMemberRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/UpdateMerchantMemberRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/MerchantMemberResponse.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/application/MerchantSlugService.java`
- Test: `apps/api/src/test/java/vn/covie/merchants/api/MerchantControllerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/merchants/api/MerchantMembershipControllerIntegrationTest.java`

**Produces:** create/list/edit merchant and manage registered-user memberships with last-owner protection.

- [ ] **Step 1: Define merchant create request/response**

```java
public record CreateMerchantRequest(
        @NotBlank @Size(max = 180) String name,
        @Size(max = 5000) String description) {}

public record MerchantSummaryResponse(
        UUID id,
        String name,
        String slug,
        MerchantStatus status,
        MerchantRole currentUserRole,
        long version) {}
```

`merchantType` is deliberately not stored. Shop and Provider are unified; actual Product/Service capability comes from catalog and location flags.

- [ ] **Step 2: Implement slug normalization and collision handling**

`MerchantSlugService`:

```text
normalize Unicode NFD
remove combining marks
map đ/Đ -> d/D
lowercase ROOT locale
replace non [a-z0-9]+ with '-'
trim '-'
if empty use "merchant"
try base slug
on collision retry base + '-' + first 6 chars of merchant UUID without dashes
```

Unit-test Vietnamese names such as `Tạp hóa Minh` -> `tap-hoa-minh`.

- [ ] **Step 3: Implement merchant creation atomically**

```java
@Transactional
public MerchantSummaryResponse create(CreateMerchantRequest request) {
    CurrentActor actor = actors.currentActor();
    Instant now = clock.instant();
    UUID merchantId = ids.generate();
    String slug = slugs.uniqueSlug(request.name(), merchantId);

    MerchantProfileEntity merchant = MerchantProfileEntity.create(
            merchantId, request.name().strip(), slug, cleanNullable(request.description()), now);
    merchants.save(merchant);

    MerchantMembershipEntity owner = MerchantMembershipEntity.activeOwner(
            ids.generate(), merchantId, actor.userId(), now);
    memberships.save(owner);

    audit.securityEvent(actor.userId(), "MERCHANT_CREATED", "MERCHANT", merchantId,
            requestIds.current(), Map.of());

    return mapper.summary(merchant, MerchantRole.OWNER);
}
```

The same transaction must create both profile and OWNER membership. A merchant profile without an owner must never be observable.

- [ ] **Step 4: Implement list/current-role endpoint**

```http
GET /api/v1/me/merchants
```

Returns active/non-deleted memberships only, ordered by merchant name + ID. Use direct DTO projection rather than N+1 loading.

- [ ] **Step 5: Implement `GET/PATCH /api/v1/merchants/{merchantId}` with ETag**

GET returns:

```http
ETag: "v3"
Cache-Control: private, no-store
```

PATCH requires:

```http
If-Match: "v3"
```

Request may change only:

```text
name
description
logoMediaId
coverMediaId
```

Logo/cover IDs are validated through `MediaAttachmentPolicy` once Task 8 is implemented. Until that task lands, the service method accepts the port but tests use a fake implementation. Do not bypass media ownership/status validation.

- [ ] **Step 6: Define member-add request using existing local email identity**

```java
public record AddMerchantMemberRequest(
        @Email @NotBlank String email,
        @NotNull MerchantRole role) {}
```

Rules:

```text
role OWNER -> only current OWNER may add
role MANAGER/STAFF -> OWNER or MANAGER
resolved user must already exist as a local EMAIL identity
cannot add same active/invited user twice
no invite email or acceptance workflow in V1
new membership is ACTIVE immediately
```

- [ ] **Step 7: Implement last-owner protection**

Before demoting/revoking an OWNER:

```java
long owners = memberships.countByMerchantProfileIdAndRoleAndMembershipStatus(
        merchantId, MerchantRole.OWNER, MembershipStatus.ACTIVE);
if (owners <= 1) {
    throw new ApiException(
            ErrorCode.MERCHANT_LAST_OWNER_REQUIRED,
            HttpStatus.CONFLICT,
            "Merchant must keep at least one active owner.");
}
```

Use a pessimistic lock on all active OWNER membership rows when changing OWNER membership so two owners cannot concurrently demote each other and leave zero owners.

- [ ] **Step 8: Implement role update/revoke semantics**

```text
PATCH member role -> If-Match required
STAFF -> MANAGER: delete staff location assignments
MANAGER -> STAFF: delete any assignments and leave zero until PUT /locations
OWNER grant/revoke: current actor must be OWNER
DELETE member: soft transition ACTIVE -> REVOKED, never hard-delete
```

- [ ] **Step 9: Run controller tests**

```bash
cd apps/api
./mvnw -Dtest=MerchantControllerIntegrationTest,MerchantMembershipControllerIntegrationTest test
```

Expected: PASS including 404/403/last-owner cases.

- [ ] **Step 10: Commit**

```bash
git add apps/api/src/main/java/vn/covie/merchants/application apps/api/src/main/java/vn/covie/merchants/api apps/api/src/test/java/vn/covie/merchants/api
git commit -m "feat(merchant): add merchant and membership APIs"
```

---

# Task 6: Implement Merchant Locations, STAFF Assignments, Hours and Contacts

**Files:**
- Create: `apps/api/src/main/java/vn/covie/merchants/application/MerchantLocationService.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/application/MerchantHoursService.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/application/MerchantContactService.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/MerchantLocationController.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/MerchantHoursController.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/MerchantContactController.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/MerchantLocationUpsertRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/MerchantLocationResponse.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/ReplaceMemberLocationsRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/ReplaceLocationHoursRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/DayScheduleRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/TimeIntervalRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/MerchantHoursResponse.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/MerchantContactRequest.java`
- Create: `apps/api/src/main/java/vn/covie/merchants/api/dto/MerchantContactResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/application/MerchantMembershipService.java` for `PUT member locations`.
- Test: `apps/api/src/test/java/vn/covie/merchants/api/MerchantLocationControllerIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/merchants/application/MerchantHoursServiceTest.java`
- Test: `apps/api/src/test/java/vn/covie/merchants/api/MerchantContactControllerIntegrationTest.java`

**Produces:** operational location boundaries needed by Catalog and all later transaction plans.

- [ ] **Step 1: Define location create/update contract**

```java
public record MerchantLocationUpsertRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 1000) String addressText,
        UUID locationAreaId,
        @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
        @NotNull PinSource pinSource,
        @NotBlank String timezone,
        boolean pickupEnabled,
        boolean sellerDeliveryEnabled,
        boolean serviceEnabled,
        Long minimumDeliveryOrder,
        @Pattern(regexp = "^[A-Z]{3}$") String currency) {}
```

V1 requires `currency = VND` even though the API keeps ISO currency shape for future extension.

- [ ] **Step 2: Validate timezone and area**

```java
ZoneId zone;
try {
    zone = ZoneId.of(request.timezone());
} catch (DateTimeException ex) {
    throw new ApiException(
            ErrorCode.VALIDATION_FAILED,
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Timezone is invalid.");
}
```

If `locationAreaId` is supplied, it must reference ACTIVE `location_areas`. Do not accept DELETED/INACTIVE areas for new locations.

- [ ] **Step 3: Implement create/update/delete with ETag**

Create requires `MANAGE_LOCATIONS`. PATCH requires `If-Match`. `GET /locations` is authorization-aware: OWNER/MANAGER receive all non-deleted merchant locations; STAFF receives only explicitly assigned non-deleted locations. A STAFF caller cannot infer an unassigned location from the private seller list. DELETE sets:

```text
location_status = DELETED
deleted_at = now
```

It does not hard-delete hours, contacts or future historical references.

- [ ] **Step 4: Implement STAFF location assignment endpoint**

```http
PUT /api/v1/merchants/{merchantId}/members/{memberId}/locations
If-Match: "v{membershipVersion}"
```

Request:

```json
{
  "locationIds": [
    "0198f5b5-0a72-7c10-a111-111111111111",
    "0198f5b5-0a72-7c10-a222-222222222222"
  ]
}
```

Rules:

```text
target membership must belong to merchant
target membership must be ACTIVE STAFF
all locations must belong to same merchant and not be DELETED
duplicate IDs rejected with 422
replace assignments atomically
OWNER/MANAGER target -> 422 MEMBERSHIP_ROLE_NOT_LOCATION_SCOPED
```

- [ ] **Step 5: Define weekly hours replace contract**

```java
public record ReplaceLocationHoursRequest(List<DayScheduleRequest> days) {}
public record DayScheduleRequest(
        @Min(1) @Max(7) int dayOfWeek,
        @NotNull OperationType operationType,
        boolean closed,
        List<TimeIntervalRequest> intervals) {}
public record TimeIntervalRequest(LocalTime openTime, LocalTime closeTime) {}
```

Validation:

```text
closed=true -> intervals empty
closed=false -> at least one interval
open < close
intervals for same day/operation cannot overlap
SHOP/PICKUP/DELIVERY only
```

`PUT /hours` replaces the weekly schedule for the location transactionally.

- [ ] **Step 6: Implement exception precedence and `isOpen(...)`**

```java
public boolean isOpen(UUID locationId, OperationType operation, Instant instant) {
    MerchantLocationEntity location = requireLocation(locationId);
    ZoneId zone = ZoneId.of(location.timezone());
    ZonedDateTime local = instant.atZone(zone);

    List<HourWindow> exceptions = exceptionRepository.findWindows(
            locationId, local.toLocalDate(), operation);
    if (!exceptions.isEmpty()) {
        return matches(exceptions, local.toLocalTime());
    }
    return matches(hourRepository.findWindows(
            locationId, local.getDayOfWeek().getValue(), operation), local.toLocalTime());
}
```

An explicit closed exception is represented by a row with `is_closed=true`; repository mapping must distinguish “no exception rows” from “one closed exception row”.

- [ ] **Step 7: Implement contacts**

Endpoints:

```text
GET  /api/v1/merchants/{merchantId}/contacts
POST /api/v1/merchants/{merchantId}/contacts
PATCH /api/v1/merchants/{merchantId}/contacts/{contactId}
DELETE /api/v1/merchants/{merchantId}/contacts/{contactId}
```

Request may optionally contain `merchantLocationId`. A location contact must belong to the same merchant. `ZALO_QR` requires `qrMediaId` with purpose `MERCHANT_CONTACT_QR`; `PHONE/ZALO_LINK/MESSENGER_LINK/OTHER` require a nonblank value.

At most one ACTIVE primary contact per `(merchantId, locationId, contactType)`; promoting a new primary demotes the previous primary in the same transaction.

- [ ] **Step 8: Run location tests**

```bash
cd apps/api
./mvnw -Dtest=MerchantLocationControllerIntegrationTest,MerchantHoursServiceTest,MerchantContactControllerIntegrationTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/api/src/main/java/vn/covie/merchants apps/api/src/test/java/vn/covie/merchants
git commit -m "feat(merchant): add locations schedules and contacts"
```

---

# Task 7: Implement S3-Compatible Object Storage and Presigned Upload Infrastructure

**Files:**
- Create: `apps/api/src/main/java/vn/covie/media/storage/StorageProperties.java`
- Create: `apps/api/src/main/java/vn/covie/media/storage/ObjectStorage.java`
- Create: `apps/api/src/main/java/vn/covie/media/storage/S3ObjectStorage.java`
- Create: `apps/api/src/main/java/vn/covie/media/storage/S3StorageConfiguration.java`
- Create: `apps/api/src/main/java/vn/covie/media/storage/PresignedUpload.java`
- Create: `apps/api/src/main/java/vn/covie/media/storage/StoredObjectMetadata.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/src/main/resources/application-local.yml`
- Modify: `.env.example`
- Test: `apps/api/src/test/java/vn/covie/media/storage/S3ObjectStorageTest.java`

**Produces:** one storage abstraction that supports AWS S3 in production and MinIO locally.

- [ ] **Step 1: Add storage configuration properties**

```java
@ConfigurationProperties(prefix = "covie.storage")
public record StorageProperties(
        String bucket,
        String region,
        URI endpoint,
        URI publicBaseUrl,
        boolean forcePathStyle,
        Duration uploadUrlTtl,
        long maxImageBytes,
        int maxPendingUploadsPerUser) {}
```

Defaults in `application.yml`:

```yaml
covie:
  storage:
    bucket: ${COVIE_STORAGE_BUCKET:covie-media}
    region: ${COVIE_STORAGE_REGION:us-east-1}
    endpoint: ${COVIE_STORAGE_ENDPOINT:}
    public-base-url: ${COVIE_MEDIA_PUBLIC_BASE_URL:}
    force-path-style: ${COVIE_STORAGE_FORCE_PATH_STYLE:false}
    upload-url-ttl: 10m
    max-image-bytes: 10485760
    max-pending-uploads-per-user: 20
```

Local profile points to MinIO and sets `force-path-style=true`.

- [ ] **Step 2: Define the storage port**

```java
public interface ObjectStorage {
    PresignedUpload presignPut(
            String key, String contentType, long contentLength, Duration ttl);
    StoredObjectMetadata head(String key);
    byte[] readPrefix(String key, int maxBytes);
    void deleteIfExists(String key);
}
```

- [ ] **Step 3: Configure `S3Client` and `S3Presigner` with the same endpoint/style**

```java
@Bean
S3Configuration s3Configuration(StorageProperties properties) {
    return S3Configuration.builder()
            .pathStyleAccessEnabled(properties.forcePathStyle())
            .build();
}

@Bean
S3Client s3Client(StorageProperties p, S3Configuration s3Config) {
    var builder = S3Client.builder()
            .region(Region.of(p.region()))
            .serviceConfiguration(s3Config);
    if (p.endpoint() != null) builder.endpointOverride(p.endpoint());
    return builder.build();
}

@Bean
S3Presigner s3Presigner(StorageProperties p, S3Configuration s3Config) {
    var builder = S3Presigner.builder()
            .region(Region.of(p.region()))
            .serviceConfiguration(s3Config);
    if (p.endpoint() != null) builder.endpointOverride(p.endpoint());
    return builder.build();
}
```

Use standard AWS credential-provider resolution; local Compose supplies MinIO credentials through environment variables. Do not hard-code them in Java.

- [ ] **Step 4: Implement presigned PUT with signed content type**

```java
PutObjectRequest objectRequest = PutObjectRequest.builder()
        .bucket(properties.bucket())
        .key(key)
        .contentType(contentType)
        .contentLength(contentLength)
        .build();

PutObjectPresignRequest signRequest = PutObjectPresignRequest.builder()
        .signatureDuration(ttl)
        .putObjectRequest(objectRequest)
        .build();

PresignedPutObjectRequest signed = presigner.presignPutObject(signRequest);
```

Return URL, method `PUT`, expiry and only the headers the client must send. Never return AWS credentials.

- [ ] **Step 5: Implement metadata/head and prefix read**

`head` uses `HeadObjectRequest`. `readPrefix` uses:

```java
GetObjectRequest request = GetObjectRequest.builder()
        .bucket(properties.bucket())
        .key(key)
        .range("bytes=0-31")
        .build();
byte[] bytes = s3.getObjectAsBytes(request).asByteArray();
```

The range length is bounded and never downloads the whole user image merely to sniff type.

- [ ] **Step 6: Write a MinIO integration test**

The test must:

```text
create test bucket
presign PUT
HTTP PUT a small valid PNG using the returned URL
HEAD the object
read first bytes
assert content length/type and PNG prefix
```

Use the existing local/Testcontainers infrastructure. If the Plan 01 test base only starts PostgreSQL, create a focused MinIO Testcontainer base class rather than coupling every backend test to MinIO.

- [ ] **Step 7: Run storage test**

```bash
cd apps/api
./mvnw -Dtest=S3ObjectStorageTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/media/storage apps/api/src/main/resources/application.yml apps/api/src/main/resources/application-local.yml .env.example apps/api/src/test/java/vn/covie/media/storage
git commit -m "feat(media): add s3 compatible presigned storage"
```

---

# Task 8: Implement Media Upload, Verification and Attachment Policy

**Files:**
- Create: `apps/api/src/main/java/vn/covie/media/model/MediaAssetEntity.java`
- Create: `apps/api/src/main/java/vn/covie/media/model/MediaStatus.java`
- Create: `apps/api/src/main/java/vn/covie/media/model/MediaPurpose.java`
- Create: `apps/api/src/main/java/vn/covie/media/persistence/MediaAssetRepository.java`
- Create: `apps/api/src/main/java/vn/covie/media/application/MediaUploadService.java`
- Create: `apps/api/src/main/java/vn/covie/media/application/MediaAttachmentPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/media/application/MediaUrlResolver.java`
- Create: `apps/api/src/main/java/vn/covie/media/application/MediaMagicSniffer.java`
- Create: `apps/api/src/main/java/vn/covie/media/application/MediaCleanupService.java`
- Create: `apps/api/src/main/java/vn/covie/media/api/MediaController.java`
- Create: `apps/api/src/main/java/vn/covie/media/api/dto/CreateMediaUploadRequest.java`
- Create: `apps/api/src/main/java/vn/covie/media/api/dto/MediaUploadResponse.java`
- Create: `apps/api/src/main/java/vn/covie/media/api/dto/MediaResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Test: `apps/api/src/test/java/vn/covie/media/application/MediaUploadServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/media/api/MediaControllerIntegrationTest.java`

**Produces:** `PENDING_UPLOAD -> READY` media lifecycle, direct upload contract, and reusable attachment authorization.

- [ ] **Step 1: Define V1 media purposes and statuses**

```java
public enum MediaPurpose {
    USER_AVATAR,
    MERCHANT_LOGO,
    MERCHANT_COVER,
    MERCHANT_CONTACT_QR,
    CATALOG_IMAGE
}

public enum MediaStatus {
    PENDING_UPLOAD,
    READY,
    FAILED,
    DELETED
}
```

- [ ] **Step 2: Define create upload contract**

```java
public record CreateMediaUploadRequest(
        @NotNull MediaPurpose purpose,
        @NotBlank String contentType,
        @Positive long fileSize) {}

public record MediaUploadResponse(
        UUID mediaId,
        String method,
        URI uploadUrl,
        Map<String, String> requiredHeaders,
        Instant expiresAt) {}
```

Allowed content types:

```text
image/jpeg
image/png
image/webp
```

Reject SVG, GIF, PDFs and arbitrary binary data in Plan 03.

- [ ] **Step 3: Create deterministic storage keys without user filenames**

```java
String extension = switch (contentType) {
    case "image/jpeg" -> "jpg";
    case "image/png" -> "png";
    case "image/webp" -> "webp";
    default -> throw new ApiException(
            ErrorCode.MEDIA_TYPE_NOT_SUPPORTED,
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Media type is not supported.");
};
String key = "users/%s/%s.%s".formatted(userId, mediaId, extension);
```

Do not include original filename in storage key.

- [ ] **Step 4: Enforce upload quota before presigning**

Repository method:

```java
long countByOwnerUserIdAndMediaStatus(UUID userId, MediaStatus status);
```

If PENDING count >= configured max, return `429 MEDIA_UPLOAD_LIMIT_REACHED`. This is a business/resource guard and may later be combined with general request-rate limiting.

- [ ] **Step 5: Implement image magic sniffing**

```text
JPEG: FF D8 FF
PNG:  89 50 4E 47 0D 0A 1A 0A
WebP: bytes 0..3 = RIFF and bytes 8..11 = WEBP
```

`MediaMagicSniffer.matches(contentType, prefix)` must be pure and unit-tested.

- [ ] **Step 6: Implement upload completion**

Controller:

```http
POST /api/v1/media/{mediaId}/complete
Idempotency-Key: 8f6a7120-68d8-4cb5-b9f8-87a9ea6c1111
```

Application flow:

```text
require authenticated actor
load media by id + owner; invisible -> 404
if READY -> return current READY view
HEAD object
verify contentLength == expected size
verify normalized contentType == expected mime type
read first 32 bytes
verify magic signature
execute DB transition through IdempotencyExecutor
PENDING_UPLOAD -> READY
verified_at = now
write audit event MEDIA_UPLOAD_COMPLETED
return MediaResponse
```

If verification fails:

```text
mark media FAILED in a committed transaction
delete object best-effort after the DB outcome
return 422 MEDIA_UPLOAD_VERIFICATION_FAILED
```

Do not throw inside the same transaction before persisting FAILED if the desired outcome must survive. Use a small transaction method that returns a verification outcome, then translate to HTTP error after commit, mirroring the refresh-reuse lesson from Plan 02.

- [ ] **Step 7: Implement `MediaAttachmentPolicy`**

```java
public MediaAssetEntity requireReadyOwned(
        UUID mediaId, UUID userId, Set<MediaPurpose> allowedPurposes) {
    MediaAssetEntity media = repository.findByIdAndOwnerUserId(mediaId, userId)
            .orElseThrow(() -> new ApiException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "Resource not found."));
    if (media.status() != MediaStatus.READY || !allowedPurposes.contains(media.purpose())) {
        throw new ApiException(
                ErrorCode.MEDIA_NOT_ATTACHABLE,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Media cannot be attached to this resource.");
    }
    return media;
}
```

- [ ] **Step 8: Implement public URL resolver**

```java
public URI publicUrl(MediaAssetEntity media) {
    if (media.status() != MediaStatus.READY) {
        throw new IllegalStateException("Only READY media has public URL");
    }
    String base = properties.publicBaseUrl().toString().replaceAll("/+$", "");
    return URI.create(base + "/" + media.storageKey());
}
```

Public DTOs get URL only; they do not get bucket name, storage provider credentials or presigned PUT data.

- [ ] **Step 9: Implement orphan cleanup use case for the later scheduler**

`MediaCleanupService.cleanupExpiredPending(int limit)` selects PENDING rows older than 24 hours in UUID order, marks them DELETED in a short transaction, then calls `ObjectStorage.deleteIfExists(storageKey)` for each claimed row. The method is callable but is not annotated `@Scheduled`; the cross-cutting scheduled-job runner is wired in the later scheduled-jobs/hardening work.

The repository query is bounded by `limit`; do not load every historical orphan in one transaction.

- [ ] **Step 10: Run media tests**

```bash
cd apps/api
./mvnw -Dtest=MediaUploadServiceIntegrationTest,MediaControllerIntegrationTest test
```

Expected: PASS including wrong-size, wrong-magic, wrong-owner, duplicate complete and idempotency-replay cases.

- [ ] **Step 11: Commit**

```bash
git add apps/api/src/main/java/vn/covie/media apps/api/src/test/java/vn/covie/media apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java
git commit -m "feat(media): add direct verified image upload flow"
```

---

# Task 9: Implement Category and Catalog Persistence Models

**Files:**
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CategoryEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CategoryAttributeEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CategoryAttributeOptionEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemAttributeValueEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/TagEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemTagEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemTagId.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/OptionGroupEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/OptionValueEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/VariantEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/VariantOptionValueEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/VariantOptionValueId.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/LocationVariantEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemMediaEntity.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemMediaId.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemType.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/ListingKind.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/ListingStatus.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CatalogModerationStatus.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/VariantStatus.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/InventoryMode.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CategoryCatalogType.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CategoryStatus.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/model/CategoryAttributeType.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/CategoryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/CategoryAttributeRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/CategoryAttributeOptionRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/CatalogItemRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/CatalogItemAttributeValueRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/TagRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/CatalogItemTagRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/OptionGroupRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/OptionValueRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/VariantRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/VariantOptionValueRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/LocationVariantRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/CatalogItemMediaRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/CategoryController.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CategoryQueryService.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/persistence/CatalogPersistenceTest.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/api/CategoryControllerTest.java`

**Produces:** catalog aggregate persistence and read-only category metadata.

- [ ] **Step 1: Model catalog enums**

```java
public enum CatalogItemType { PRODUCT, SERVICE }
public enum ListingKind { STANDARD, RESALE }
public enum ListingStatus { DRAFT, ACTIVE, PAUSED, DELETED }
public enum CatalogModerationStatus { NOT_REQUIRED, PENDING, APPROVED, REJECTED, HIDDEN }
public enum VariantStatus { ACTIVE, PAUSED, DELETED }
public enum InventoryMode { TRACKED, UNTRACKED }
public enum CategoryCatalogType { PRODUCT, SERVICE, BOTH }
public enum CategoryStatus { ACTIVE, INACTIVE, DELETED }
public enum CategoryAttributeType { TEXT, NUMBER, BOOLEAN, SINGLE_SELECT, MULTI_SELECT }
```

- [ ] **Step 2: Map join tables with explicit composite IDs and mutable resources with `@Version`**

Use `@EmbeddedId` records/classes for the three composite-key joins:

```text
CatalogItemTagId(catalogItemId, tagId)
VariantOptionValueId(variantId, optionValueId)
CatalogItemMediaId(catalogItemId, mediaAssetId)
```

Do not add synthetic UUID columns that are absent from V005.

Map `CatalogItemEntity`, `VariantEntity`, `LocationVariantEntity` with `@Version`:

`LocationVariantEntity` exposes domain-safe methods:

```java
public void changeOffer(long priceMinor, String currency, boolean available,
                        InventoryMode inventoryMode, Integer preparationMinutes) {
    this.price = priceMinor;
    this.currencyCode = currency;
    this.available = available;
    this.inventoryMode = inventoryMode;
    this.preparationTimeMinutes = preparationMinutes;
}
```

It does not expose a public status setter that can bypass Product/Service validation.

- [ ] **Step 3: Implement repository mappings for composite joins without cascade surprises**

Map each join entity with `@EmbeddedId` plus explicit `@MapsId`/foreign-key fields where navigation is useful. Repositories expose targeted delete/exists operations; do not enable `CascadeType.REMOVE` from tags, media assets, or merchant locations into shared resources. Add persistence assertions that deleting a catalog-item association removes only the join row, not the referenced tag/media/location.

```java
public interface CatalogItemMediaRepository extends JpaRepository<CatalogItemMediaEntity, CatalogItemMediaId> {
    boolean existsByIdCatalogItemIdAndIdMediaAssetId(UUID catalogItemId, UUID mediaAssetId);
    void deleteByIdCatalogItemIdAndIdMediaAssetId(UUID catalogItemId, UUID mediaAssetId);
}
```

- [ ] **Step 4: Keep search vector outside JPA entity mutation**

Omit `search_vector` from `CatalogItemEntity` and update it only through `CatalogSearchDocumentService` JDBC. Do not attempt to treat PostgreSQL `tsvector` as a normal String column.

- [ ] **Step 5: Add repository methods later plans need**

`LocationVariantRepository` must include:

```java
Optional<LocationVariantEntity> findByIdAndDeletedAtIsNull(UUID id);

@Query("""
    select lv from LocationVariantEntity lv
    join VariantEntity v on v.id = lv.variantId
    join CatalogItemEntity i on i.id = v.catalogItemId
    where lv.id = :id
      and lv.deletedAt is null
      and v.deletedAt is null
      and i.deletedAt is null
    """)
Optional<LocationVariantEntity> findUsableById(UUID id);
```

Plan 04 will extend this repository with inventory locking; do not add inventory tables here.

- [ ] **Step 6: Implement category public read**

```http
GET /api/v1/categories?parentId={uuid}&type=PRODUCT|SERVICE
```

Rules:

```text
only ACTIVE categories
PRODUCT matches PRODUCT or BOTH
SERVICE matches SERVICE or BOTH
root when parentId omitted
ordered sortOrder,name,id
include structured attribute definitions + select options in category detail DTO only when requested through GET /categories/{id}
```

Add:

```http
GET /api/v1/categories/{categoryId}
```

as an additive metadata endpoint because seller catalog creation needs attribute definitions.

- [ ] **Step 7: Run persistence/category tests**

```bash
cd apps/api
./mvnw -Dtest=CatalogPersistenceTest,CategoryControllerTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/catalog/model apps/api/src/main/java/vn/covie/catalog/persistence apps/api/src/main/java/vn/covie/catalog/api/CategoryController.java apps/api/src/main/java/vn/covie/catalog/application/CategoryQueryService.java apps/api/src/test/java/vn/covie/catalog
git commit -m "feat(catalog): add catalog persistence model"
```

---

# Task 10: Implement Aggregate Catalog Creation with Variants, Attributes, Media and Location Offers

**Files:**
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogCreateService.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/create/CreateCatalogItemRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/create/AttributeValueRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/create/OptionGroupCreateRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/create/OptionValueCreateRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/create/VariantCreateRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/create/LocationOfferCreateRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/CatalogItemManagementResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/MerchantCatalogController.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogAggregateValidator.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogSearchDocumentService.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/application/CatalogCreateServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/api/MerchantCatalogCreateControllerIntegrationTest.java`

**Produces:** one idempotent transaction that creates a complete DRAFT catalog aggregate.

- [ ] **Step 1: Define explicit aggregate request DTOs**

```java
public record CreateCatalogItemRequest(
        @NotNull CatalogItemType type,
        @NotNull ListingKind listingKind,
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 220) String name,
        @Size(max = 10000) String description,
        List<AttributeValueRequest> attributes,
        List<String> tags,
        List<UUID> mediaIds,
        List<OptionGroupCreateRequest> options,
        List<VariantCreateRequest> variants,
        @NotEmpty List<LocationOfferCreateRequest> locationOffers) {}

public record OptionGroupCreateRequest(
        @NotBlank String clientKey,
        @NotBlank @Size(max = 120) String name,
        List<OptionValueCreateRequest> values) {}

public record OptionValueCreateRequest(
        @NotBlank String clientKey,
        @NotBlank @Size(max = 160) String value) {}

public record VariantCreateRequest(
        @NotBlank String clientKey,
        @Size(max = 120) String sku,
        @NotBlank @Size(max = 240) String name,
        List<String> optionValueKeys) {}

public record LocationOfferCreateRequest(
        @NotNull UUID merchantLocationId,
        @NotBlank String variantClientKey,
        @PositiveOrZero long price,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        boolean available,
        InventoryMode inventoryMode,
        @PositiveOrZero Integer preparationTimeMinutes) {}
```

If `options` is empty and `variants` is empty, the service creates one internal default variant with a reserved server-only client key; callers do not need to invent one.

- [ ] **Step 2: Write validation tests before implementation**

Required cases:

```text
SERVICE + RESALE -> 422
PRODUCT category used by SERVICE -> 422
required category attribute missing -> 422
select option from another attribute -> 422
duplicate option group clientKey -> 422
duplicate option value clientKey across request -> 422
variant references unknown optionValueKey -> 422
variant chooses two values from same option group -> 422
duplicate variant combination -> 422
location offer references unknown variant clientKey -> 422
location belongs another merchant -> 404
PRODUCT offer inventoryMode null -> 422
SERVICE offer inventoryMode non-null -> 422
SERVICE offer location.serviceEnabled=false -> 422
PRODUCT pickup/delivery flags do not need to be enabled merely to create a DRAFT offer
media owned by another user -> 404; media not READY or wrong purpose -> 422 MEDIA_NOT_ATTACHABLE
same Idempotency-Key/body -> one item only
same Idempotency-Key/different body -> 409
```

- [ ] **Step 3: Implement category/attribute validation**

`CatalogAggregateValidator` loads the category and active attribute definitions once, then validates all request values in memory. It returns a normalized structure; it does not repeatedly query per attribute.

For `MULTI_SELECT`, multiple rows are allowed. For all other attribute types, at most one row per attribute key is allowed.

- [ ] **Step 4: Implement variant-combination normalization**

For each variant:

```text
resolve option value client keys -> option group + option value
assert at most one value per group
sort resolved optionValue UUIDs by UUID text
join into canonical combination key
assert canonical key unique within item
```

If there are N visible option groups, each non-default variant must select exactly one value from each group. Partial combinations are rejected in V1.

- [ ] **Step 5: Implement DRAFT aggregate transaction through `IdempotencyExecutor`**

Controller:

```http
POST /api/v1/merchants/{merchantId}/catalog-items
Idempotency-Key: 8f6a7120-68d8-4cb5-b9f8-87a9ea6c1111
```

Service flow inside the idempotency transaction:

```text
require MANAGE_CATALOG_STRUCTURE
validate merchant ACTIVE
validate category/item type
validate attributes/tags/media
create CatalogItem DRAFT + NOT_REQUIRED moderation baseline
create option groups/values
create variants OR hidden default variant
create variant-option joins
create LocationVariants
create catalog media joins
rebuild search document
append CATALOG_ITEM_CREATED outbox intent
return CatalogItemManagementResponse
```

- [ ] **Step 6: Implement default variant exactly once**

```java
VariantEntity variant = VariantEntity.createDefault(
        ids.generate(), itemId, "Default", now);
```

`isDefault=true`; no option-value joins. In the public detail DTO, an internal default variant returns `name = null`, `isDefault = true`, and an empty selected-option list so the UI does not render a meaningless “Default” label.

- [ ] **Step 7: Implement offer invariants**

```java
private void validateOfferType(
        CatalogItemType type,
        MerchantLocationEntity location,
        LocationOfferCreateRequest offer) {
    if (type == CatalogItemType.PRODUCT && offer.inventoryMode() == null) {
        rejectOffer("Product offer requires TRACKED or UNTRACKED inventory mode.");
    }
    if (type == CatalogItemType.SERVICE && offer.inventoryMode() != null) {
        rejectOffer("Service offer must not define inventory mode.");
    }
    if (type == CatalogItemType.SERVICE && !location.serviceEnabled()) {
        rejectOffer("Merchant location is not enabled for services.");
    }
    if (!"VND".equals(offer.currency())) {
        rejectOffer("V1 location offer currency must be VND.");
    }
}

private void rejectOffer(String message) {
    throw new ApiException(
            ErrorCode.CATALOG_LOCATION_OFFER_INVALID,
            HttpStatus.UNPROCESSABLE_ENTITY,
            message);
}
```

Do not create an `inventory_balances` row for TRACKED offers yet; Plan 04 owns inventory creation semantics.

- [ ] **Step 8: Implement search-document rebuild**

Use `NamedParameterJdbcTemplate` and update `catalog_items.search_vector` with weighted text:

```sql
update catalog_items ci
set search_vector =
      setweight(to_tsvector('simple', unaccent(coalesce(ci.name, ''))), 'A')
   || setweight(to_tsvector('simple', unaccent(coalesce(ci.description, ''))), 'B')
   || setweight(to_tsvector('simple', unaccent(coalesce(mp.name, ''))), 'B')
   || setweight(to_tsvector('simple', unaccent(coalesce(c.name, ''))), 'B')
   || setweight(to_tsvector('simple', unaccent(coalesce((
        select string_agg(t.name, ' ')
        from catalog_item_tags cit
        join tags t on t.id = cit.tag_id
        where cit.catalog_item_id = ci.id
      ), ''))), 'C')
from merchant_profiles mp,
     categories c
where ci.id = :itemId
  and mp.id = ci.merchant_profile_id
  and c.id = ci.primary_category_id
```

`unaccent` is applied while building the stored vector; public search applies it to the query text too. Plan 08 category rename and merchant-profile rename must call the same rebuild service for affected items.

- [ ] **Step 9: Run create tests**

```bash
cd apps/api
./mvnw -Dtest=CatalogCreateServiceIntegrationTest,MerchantCatalogCreateControllerIntegrationTest test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add apps/api/src/main/java/vn/covie/catalog/application apps/api/src/main/java/vn/covie/catalog/api apps/api/src/test/java/vn/covie/catalog
git commit -m "feat(catalog): add aggregate draft creation"
```

---

# Task 11: Implement Granular Catalog Editing and Offer-Level STAFF Operations

**Files:**
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogEditService.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/edit/UpdateCatalogItemRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/edit/UpdateVariantRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/edit/CreateVariantRequest.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/edit/UpdateLocationOfferRequest.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/MerchantCatalogController.java`.
- Test: `apps/api/src/test/java/vn/covie/catalog/application/CatalogEditServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/api/MerchantCatalogEditControllerIntegrationTest.java`

**Produces:** safe post-create editing without aggregate replacement.

- [ ] **Step 1: Implement item PATCH**

```http
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}
If-Match: "v{version}"
```

Editable fields:

```text
name
description
primaryCategoryId
attributes
tags
mediaIds
```

Rules:

```text
MANAGE_CATALOG_STRUCTURE required
item must belong merchant
category change must remain compatible with item type
required attributes revalidated after category change
changing item type or listingKind after creation is forbidden in V1
search document rebuilt when name/description/category/tags change
```

- [ ] **Step 2: Implement variant PATCH**

```http
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId}
If-Match: "v{version}"
```

Editable:

```text
sku
variantName
variantStatus
```

Option combination is immutable in Plan 03. Changing a variant combination requires adding a new variant and archiving the old one. Add both endpoints in this task:

```http
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants
DELETE /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId}
```

Do not allow deletion of the final non-deleted variant.

- [ ] **Step 3: Implement location-offer PATCH**

```http
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}/location-offers/{locationVariantId}
If-Match: "v{version}"
```

Editable:

```text
price
available
inventoryMode only while no Plan-04 inventory balance/reservation exists
preparationTimeMinutes
```

Authorization:

```text
OWNER/MANAGER -> any location in merchant
STAFF -> only explicitly assigned offer location
```

After Plan 04 introduces tracked balances, changing `inventoryMode` must delegate to an inventory mode transition use case; Plan 03 tests encode only pre-inventory behavior.

- [ ] **Step 4: Do not use item ETag to edit a child offer**

Each endpoint compares the `If-Match` version of the resource it mutates:

```text
CatalogItem PATCH -> catalog_items.version
Variant PATCH -> variants.version
LocationVariant PATCH -> location_variants.version
```

This prevents unrelated child writes from causing unnecessary item-level conflicts.

- [ ] **Step 5: Map concurrent JPA conflict to the same 409 contract**

Integration test with two transactions:

```text
both read LocationVariant version 4
transaction A changes price -> commit version 5
transaction B attempts stale entity update -> 409 RESOURCE_VERSION_CONFLICT
```

- [ ] **Step 6: Run edit tests**

```bash
cd apps/api
./mvnw -Dtest=CatalogEditServiceIntegrationTest,MerchantCatalogEditControllerIntegrationTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/catalog apps/api/src/test/java/vn/covie/catalog
git commit -m "feat(catalog): add granular item variant and offer editing"
```

---

# Task 12: Implement Catalog Publish/Pause/Activate/Archive State Machine

**Files:**
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogPublishValidator.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogLifecycleService.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/policy/CatalogPublishPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/policy/AutoPublishCatalogPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/policy/CatalogPublishDecision.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/MerchantCatalogController.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/application/CatalogLifecycleServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/api/CatalogLifecycleControllerIntegrationTest.java`

**Produces:** backend-owned listing lifecycle with a future moderation policy seam.

- [ ] **Step 1: Define publish policy port**

```java
public interface CatalogPublishPolicy {
    CatalogPublishDecision decide(CatalogItemEntity item);
}

public enum CatalogPublishDecision {
    PUBLISH_NOW,
    REQUIRE_MODERATION
}

@Component
@Primary
public final class AutoPublishCatalogPolicy implements CatalogPublishPolicy {
    @Override
    public CatalogPublishDecision decide(CatalogItemEntity item) {
        return CatalogPublishDecision.PUBLISH_NOW;
    }
}
```

Plan 08 replaces/wraps this policy using SystemSettings and moderation cases; no controller rewrite is allowed then.

- [ ] **Step 2: Define lifecycle transitions**

```text
DRAFT --publish/PUBLISH_NOW--> ACTIVE + moderation NOT_REQUIRED
DRAFT --publish/REQUIRE_MODERATION--> remains DRAFT + moderation PENDING
PAUSED --activate--> ACTIVE if publish validator still passes and moderation visible
ACTIVE --pause--> PAUSED
DRAFT|ACTIVE|PAUSED --archive--> DELETED + deletedAt
DELETED -> terminal through public seller API
```

Because Plan 03 has no moderation-case persistence, the default policy never returns `REQUIRE_MODERATION`. The branch is still tested with a fake policy so Plan 08 can enable it safely.

- [ ] **Step 3: Implement `CatalogPublishValidator`**

Publish requires:

```text
merchant ACTIVE
item not deleted
category ACTIVE and compatible
all required structured attributes present
>= 1 ACTIVE non-deleted variant
>= 1 available non-deleted LocationVariant
all offer locations ACTIVE
all service offers are at serviceEnabled locations
all attached media READY
```

Inventory quantity is not a publish requirement because Product can be UNTRACKED and TRACKED balances are Plan 04.

- [ ] **Step 4: Implement command endpoints**

```http
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/pause
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/activate
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/archive
```

Require `PUBLISH_CATALOG`. These command endpoints do not accept a desired status field.

- [ ] **Step 5: Append outbox intents in the same transaction**

Examples:

```java
outbox.append(new OutboxEventIntent(
        "CATALOG_ITEM_PUBLISHED", "CATALOG_ITEM", item.id(),
        Map.of("merchantId", item.merchantProfileId().toString())));
```

Events:

```text
CATALOG_ITEM_PUBLISHED
CATALOG_ITEM_PAUSED
CATALOG_ITEM_ACTIVATED
CATALOG_ITEM_ARCHIVED
```

- [ ] **Step 6: Run lifecycle tests**

```bash
cd apps/api
./mvnw -Dtest=CatalogLifecycleServiceIntegrationTest,CatalogLifecycleControllerIntegrationTest test
```

Expected: PASS including forbidden transition and outbox atomicity assertions.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/catalog/application/CatalogLifecycleService.java apps/api/src/main/java/vn/covie/catalog/application/CatalogPublishValidator.java apps/api/src/main/java/vn/covie/catalog/policy apps/api/src/main/java/vn/covie/catalog/api/MerchantCatalogController.java apps/api/src/test/java/vn/covie/catalog
git commit -m "feat(catalog): add listing lifecycle commands"
```

---

# Task 13: Implement Public Catalog Detail with Concrete Location Resolution

**Files:**
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogDetailService.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/application/PublicMerchantViewService.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/PublicCatalogController.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/CatalogItemDetailResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMerchantSummary.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/ResolvedMerchantLocationResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicMediaResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicOptionGroupResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicVariantResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/publicview/PublicOfferResponse.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/application/CatalogDetailServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/api/PublicCatalogDetailControllerIntegrationTest.java`

**Produces:** shareable catalog detail that always identifies the concrete location/offer being shown.

- [ ] **Step 1: Define detail location precedence**

```text
1. locationId supplied -> exact active merchant location with active offer
2. addressId supplied -> nearest active offer location to owned saved address
3. lat+lng supplied -> nearest active offer location to coordinates
4. no location context -> earliest-created active merchant location that has at least one available active offer; tie-break UUID
```

Response always reports:

```text
resolvedLocation.id
resolvedLocation.distanceMeters nullable
resolvedLocation.resolutionSource
```

The fallback is deterministic but does not pretend to be “nearest”.

- [ ] **Step 2: Define a public detail response without JPA leakage**

```java
public record CatalogItemDetailResponse(
        UUID id,
        String slug,
        CatalogItemType type,
        ListingKind listingKind,
        String name,
        String description,
        PublicMerchantResponse merchant,
        ResolvedMerchantLocationResponse resolvedLocation,
        List<PublicMediaResponse> media,
        List<PublicOptionGroupResponse> options,
        List<PublicVariantResponse> variants) {}
```

Each variant contains an offer only if that variant has a visible `LocationVariant` at the resolved location:

```java
public record PublicOfferResponse(
        UUID locationVariantId,
        boolean available,
        Money price,
        Integer preparationTimeMinutes) {}
```

Do not expose `inventoryMode` or future stock count in public detail unless Plan 04 explicitly adds a public availability field.

- [ ] **Step 3: Implement visibility**

Public item must satisfy:

```text
catalog listingStatus = ACTIVE
catalog moderationStatus in (NOT_REQUIRED, APPROVED)
merchant status = ACTIVE
merchant moderation visible
resolved location status = ACTIVE
variant status = ACTIVE
locationVariant not deleted
```

If the item exists but is not public, return 404.

- [ ] **Step 4: Expose public merchant detail**

```http
GET /api/v1/catalog/merchants/{merchantId}
```

Returns public profile, public contacts, READY logo/cover, active locations with coarse/public address text, and no member/staff data.

- [ ] **Step 5: Ensure contact privacy**

Public merchant endpoint only includes `merchant_contact_methods.is_public=true` and `status=ACTIVE`. Buyer private address/contact data is never involved.

- [ ] **Step 6: Run detail tests**

```bash
cd apps/api
./mvnw -Dtest=CatalogDetailServiceIntegrationTest,PublicCatalogDetailControllerIntegrationTest test
```

Expected: PASS including explicit-location ownership, nearest resolution and no-context deterministic fallback.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/catalog/api/PublicCatalogController.java apps/api/src/main/java/vn/covie/catalog/api/dto/publicview apps/api/src/main/java/vn/covie/catalog/application/CatalogDetailService.java apps/api/src/main/java/vn/covie/catalog/application/PublicMerchantViewService.java apps/api/src/test/java/vn/covie/catalog
git commit -m "feat(catalog): add public location resolved detail"
```

---

# Task 14: Implement PostgreSQL/PostGIS Public Catalog Discovery and Opaque Cursor Pagination

**Files:**
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogDiscoveryService.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/application/CatalogCursorCodec.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/CatalogDiscoveryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/persistence/JdbcCatalogDiscoveryRepository.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/discovery/CatalogDiscoveryQuery.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/discovery/CatalogSort.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/discovery/CatalogCursor.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/discovery/CatalogItemSummaryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/discovery/CatalogDiscoveryResponse.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/discovery/CursorPageResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/PublicCatalogController.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/application/CatalogCursorCodecTest.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/persistence/CatalogDiscoveryRepositoryIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/catalog/api/PublicCatalogDiscoveryControllerIntegrationTest.java`

**Produces:** hyperlocal unified discovery where each item is resolved to one concrete nearest active MerchantLocation.

- [ ] **Step 1: Define typed query object and whitelist**

```java
public record CatalogDiscoveryQuery(
        String q,
        CatalogItemType type,
        ListingKind listingKind,
        UUID categoryId,
        Double lat,
        Double lng,
        UUID addressId,
        Integer radiusMeters,
        Boolean pickup,
        Boolean openNow,
        Long minPrice,
        Long maxPrice,
        CatalogSort sort,
        Integer limit,
        String cursor) {}
```

Sort enum:

```java
RELEVANCE,
DISTANCE,
PRICE_ASC,
PRICE_DESC,
NEWEST
```

Reject unknown sort/filter values with 422. Do not pass raw sort column names to SQL.

- [ ] **Step 2: Normalize bounds**

```text
default radiusMeters = 5000
min radius = 100
max radius = 50000
default limit = 20
min limit = 1
max limit = 50
minPrice/maxPrice >= 0
minPrice <= maxPrice
RELEVANCE without q -> 422 CATALOG_FILTER_INVALID
```

- [ ] **Step 3: Define cursor payload per sort and sign its shape, not its content**

V1 cursor is opaque Base64URL JSON, not cryptographically trusted. Every decoded field is parsed/validated and then used only as bind parameters.

```java
record CatalogCursor(
        CatalogSort sort,
        BigDecimal rank,
        Long distanceMeters,
        Long price,
        Instant createdAt,
        UUID itemId) {}
```

Codec rejects a cursor whose embedded sort does not match the current query sort.

- [ ] **Step 4: Implement nearest-location candidate CTE**

Use a native JDBC query with bind parameters. Core shape:

```sql
with candidate_locations as (
  select ml.id,
         ml.merchant_profile_id,
         st_distance(
             ml.geo_point,
             st_setsrid(st_makepoint(:lng, :lat), 4326)::geography
         )::bigint as distance_meters
  from merchant_locations ml
  join merchant_profiles mp on mp.id = ml.merchant_profile_id
  where ml.location_status = 'ACTIVE'
    and ml.deleted_at is null
    and mp.merchant_status = 'ACTIVE'
    and mp.deleted_at is null
    and st_dwithin(
        ml.geo_point,
        st_setsrid(st_makepoint(:lng, :lat), 4326)::geography,
        :radiusMeters
    )
),
resolved as (
  select distinct on (ci.id)
         ci.id as item_id,
         cl.id as location_id,
         cl.distance_meters
  from catalog_items ci
  join variants v on v.catalog_item_id = ci.id
      and v.variant_status = 'ACTIVE' and v.deleted_at is null
  join location_variants lv on lv.variant_id = v.id
      and lv.deleted_at is null and lv.is_available
  join candidate_locations cl on cl.id = lv.merchant_location_id
      and cl.merchant_profile_id = ci.merchant_profile_id
  where ci.listing_status = 'ACTIVE'
    and ci.moderation_status in ('NOT_REQUIRED','APPROVED')
    and ci.deleted_at is null
  order by ci.id, cl.distance_meters, cl.id
)
 , resolved_pricing as (
  select r.item_id,
         r.location_id,
         r.distance_meters,
         min(lv.price)::bigint as min_price
  from resolved r
  join variants v on v.catalog_item_id = r.item_id
      and v.variant_status = 'ACTIVE' and v.deleted_at is null
  join location_variants lv on lv.variant_id = v.id
      and lv.merchant_location_id = r.location_id
      and lv.is_available and lv.deleted_at is null
  group by r.item_id, r.location_id, r.distance_meters
),
query_term as (
  select case
      when :q is null or btrim(:q) = '' then null
      else websearch_to_tsquery('simple', unaccent(:q))
  end as query
)
select ci.id as item_id,
       ci.item_type,
       ci.listing_kind,
       ci.name,
       ci.slug,
       ci.created_at,
       ci.merchant_profile_id,
       rp.location_id,
       rp.distance_meters,
       rp.min_price,
       case when qt.query is null then null
            else round(ts_rank_cd(ci.search_vector, qt.query)::numeric, 8)
       end as rank_key
from resolved_pricing rp
join catalog_items ci on ci.id = rp.item_id
cross join query_term qt
where (qt.query is null or ci.search_vector @@ qt.query)
```

This resolves each item to one concrete nearest location before list sorting. `PRICE_ASC` sorts the minimum active offer price at that resolved location; it does not silently choose a farther location merely because it is cheaper.

- [ ] **Step 5: Add FTS relevance predicate/ranking**

When `q` is present:

```sql
websearch_to_tsquery('simple', unaccent(:q)) as query
```

Filter:

```sql
ci.search_vector @@ query
```

Rank:

```sql
round(ts_rank_cd(ci.search_vector, query)::numeric, 8)
```

`RELEVANCE` ordering:

```text
rank DESC
distanceMeters ASC
itemId ASC
```

- [ ] **Step 6: Add remaining filters as bind-parameter predicates**

```text
type -> ci.item_type
listingKind -> ci.listing_kind
categoryId -> exact ci.primary_category_id match in V1
pickup=true -> resolved ml.pickup_enabled=true
min/max price -> resolved-location min price
openNow=true -> evaluate weekly/exception hours in the discovery SQL using the merchant location timezone
```

For `openNow`, implement a repository SQL predicate using `timezone(ml.timezone, :now)` plus weekly/exception tables so filtering remains database-side. Add an integration fixture covering an exception day.

- [ ] **Step 7: Implement keyset predicates per sort**

Examples:

```text
DISTANCE: (distance_meters, item_id) > (:cursorDistance, :cursorItemId)
PRICE_ASC: (min_price, item_id) > (:cursorPrice, :cursorItemId)
PRICE_DESC: (min_price, item_id) < (:cursorPrice, :cursorItemId) with matching DESC order
NEWEST: (created_at, item_id) < (:cursorCreatedAt, :cursorItemId)
RELEVANCE: rank < cursorRank OR (rank = cursorRank AND distance/itemId after cursor)
```

Fetch `limit + 1`, return `limit`, and derive `hasMore/nextCursor` from the extra row. Never execute an unbounded COUNT for public cursor lists.

- [ ] **Step 8: Define summary response**

```java
public record CatalogItemSummaryResponse(
        UUID id,
        CatalogItemType type,
        ListingKind listingKind,
        String name,
        String slug,
        PublicMediaResponse primaryMedia,
        PublicMerchantSummary merchant,
        ResolvedMerchantLocationResponse resolvedLocation,
        Money priceFrom) {}
```

`priceFrom` is the minimum active offer price for this item at the **resolved location**. It is display metadata; later checkout revalidates authoritative current price.

- [ ] **Step 9: Run search/cursor tests**

Required fixtures/assertions:

```text
same item offered by Location A 300m and Location B 900m -> result resolves A
price sort does not switch to farther B
radius excludes outside location
PRODUCT/SERVICE filter works
RESALE only returns PRODUCT fixtures
q matches accent-insensitive stored search vector
inactive merchant/location/item/variant/offer hidden
second cursor page has no duplicate or skipped tie IDs
cursor from PRICE_ASC rejected when reused with DISTANCE
owned addressId works
other user's addressId -> 404
```

Run:

```bash
cd apps/api
./mvnw -Dtest=CatalogCursorCodecTest,CatalogDiscoveryRepositoryIntegrationTest,PublicCatalogDiscoveryControllerIntegrationTest test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add apps/api/src/main/java/vn/covie/catalog/application/CatalogDiscoveryService.java apps/api/src/main/java/vn/covie/catalog/application/CatalogCursorCodec.java apps/api/src/main/java/vn/covie/catalog/persistence/CatalogDiscoveryRepository.java apps/api/src/main/java/vn/covie/catalog/persistence/JdbcCatalogDiscoveryRepository.java apps/api/src/main/java/vn/covie/catalog/api apps/api/src/test/java/vn/covie/catalog
git commit -m "feat(catalog): add location aware public discovery"
```

---

# Task 15: Add Seller Management Read Models and Basic Seller Web Slice

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/merchants/api/MeMerchantController.java`
- Modify: `apps/api/src/main/java/vn/covie/merchants/api/MerchantController.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/MerchantCatalogController.java`
- Create: `apps/api/src/main/java/vn/covie/catalog/api/dto/CatalogItemManagementSummaryResponse.java`
- Create: `apps/web/app/(seller)/seller/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/locations/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/catalog/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/catalog/new/page.tsx`
- Create: `apps/web/features/merchant/merchant-api.ts`
- Create: `apps/web/features/merchant/merchant-form.tsx`
- Create: `apps/web/features/merchant/location-form.tsx`
- Create: `apps/web/features/catalog/catalog-api.ts`
- Create: `apps/web/features/catalog/catalog-create-form.tsx`
- Test: `apps/web/tests/seller/merchant-management.test.tsx`
- Test: `apps/web/tests/seller/catalog-create.test.tsx`

**Produces:** minimal non-polished seller vertical slice proving generated API contract usability before Plan 09 UX completion.

- [ ] **Step 1: Regenerate API client after backend endpoints compile**

```bash
cd apps/api
./mvnw -Dtest=OpenApiExportTest -Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json test
cd ../..
pnpm api-client:generate
pnpm api-client:check
```

Expected: generated schema includes merchant/media/catalog endpoints.

- [ ] **Step 2: Implement merchant API wrapper using generated client only**

```ts
export async function listMyMerchants() {
  const { data, error } = await api.GET("/api/v1/me/merchants");
  if (error) {
    if (isApiErrorShape(error)) {
      throw new Error(error.error.code);
    }
    throw new Error("API_REQUEST_FAILED");
  }
  return data;
}
```

Do not hand-write duplicate response interfaces that already exist in generated OpenAPI types.

- [ ] **Step 3: Implement minimal seller pages**

Required page behavior:

```text
/seller -> list my merchants + create merchant form
/seller/[merchantId] -> merchant management summary
/seller/[merchantId]/locations -> create/list locations
/seller/[merchantId]/catalog -> list own catalog items/status
/seller/[merchantId]/catalog/new -> aggregate create form supporting one default variant path first
```

Plan 03 web UI implements the default-variant creation path plus a simple structured option/variant row editor sufficient to exercise the backend aggregate contract. Plan 09 owns visual polish, drag/reorder UX and richer variant editing.

- [ ] **Step 4: Carry ETag in edit forms**

API wrappers return `{data, etag}` from fetch headers for resources that require optimistic writes. PATCH calls send the same value as `If-Match`.

On `RESOURCE_VERSION_CONFLICT`, show a refresh/reload action; do not auto-resubmit stale edits.

- [ ] **Step 5: Write frontend tests**

Test:

```text
merchant list renders generated API data
create merchant submits and refreshes list
catalog create default variant submits request with Idempotency-Key
version conflict renders reload message
```

- [ ] **Step 6: Run frontend checks**

```bash
pnpm web:lint
pnpm web:typecheck
pnpm web:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/web packages/api-client
git commit -m "feat(web): add merchant and catalog management slice"
```

---

# Task 16: Add Direct Media Upload and Public Discovery Web Slice

**Files:**
- Create: `apps/web/app/(public)/explore/page.tsx`
- Create: `apps/web/app/(public)/items/[itemId]/page.tsx`
- Create: `apps/web/features/location/location-context.ts`
- Create: `apps/web/features/location/location-input.tsx`
- Create: `apps/web/features/media/media-api.ts`
- Create: `apps/web/features/media/direct-image-upload.ts`
- Create: `apps/web/features/catalog/catalog-card.tsx`
- Create: `apps/web/features/catalog/catalog-detail.tsx`
- Modify: `apps/web/features/catalog/catalog-api.ts`
- Test: `apps/web/tests/media/direct-image-upload.test.ts`
- Test: `apps/web/tests/catalog/public-discovery.test.tsx`

**Produces:** browser direct-to-storage image upload and minimal public discovery/detail pages.

- [ ] **Step 1: Implement direct upload orchestration**

```ts
export async function uploadImage(file: File, purpose: MediaPurpose) {
  const create = await createUpload({
    purpose,
    contentType: file.type,
    fileSize: file.size,
  });

  const upload = await fetch(create.uploadUrl, {
    method: "PUT",
    headers: create.requiredHeaders,
    body: file,
  });
  if (!upload.ok) throw new Error("OBJECT_UPLOAD_FAILED");

  return completeUpload(create.mediaId, crypto.randomUUID());
}
```

The binary PUT must go to `uploadUrl`, not `/api/v1`.

- [ ] **Step 2: Validate client file before requesting presign**

Client-side validation mirrors server limits for UX only:

```text
JPEG/PNG/WebP
<= 10 MiB
```

Backend remains authoritative; client validation is not a security boundary.

- [ ] **Step 3: Implement explicit location state for discovery**

`features/location/location-context.ts` stores one of:

```ts
type DiscoveryLocation =
  | { kind: "coordinates"; lat: number; lng: number }
  | { kind: "saved-address"; addressId: string };
```

Do not create a hidden server session “current location”. Every API call derives query parameters from this state.

- [ ] **Step 4: Implement minimal `/explore`**

Required behavior:

```text
requires/asks for location context
calls GET /catalog/items with explicit context
renders resolved merchant location + distance + priceFrom
supports q/type/category/sort basic controls
loads next cursor without parsing it
```

- [ ] **Step 5: Implement `/items/[itemId]`**

Pass current location context if present. Render:

```text
item name/media
merchant
resolved location
variants
locationVariantId-backed offer price
```

Do not implement cart button behavior beyond preserving the `locationVariantId`; Plan 04 owns add-to-cart.

- [ ] **Step 6: Run web tests and build**

```bash
pnpm web:lint
pnpm web:typecheck
pnpm web:test
pnpm web:build
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/web
git commit -m "feat(web): add media upload and public catalog discovery"
```

---

# Task 17: Verify OpenAPI, Security Matrix, Search Invariants and Plan 03 Acceptance Gate

**Files:**
- Modify: `apps/api/src/test/java/vn/covie/openapi/OpenApiExportTest.java`
- Create: `apps/api/src/test/java/vn/covie/merchantcatalog/Plan03AcceptanceIntegrationTest.java`
- Create: `apps/api/src/test/java/vn/covie/merchantcatalog/CatalogAuthorizationMatrixTest.java`
- Create: `apps/api/src/test/java/vn/covie/merchantcatalog/CatalogInvariantTest.java`
- Modify: `infra/scripts/verify-generated-client.sh` to assert the generated schema contains the Plan 03 catalog/media paths.
- Modify: `README.md` with Plan 03 local MinIO/media test instructions.

**Produces:** evidence that the marketplace discovery milestone is genuinely usable and safe to build Plan 04 on.

- [ ] **Step 1: Extend OpenAPI assertions**

Assert generated OpenAPI contains:

```text
/api/v1/me/merchants
/api/v1/merchants/{merchantId}/locations
/api/v1/media/uploads
/api/v1/media/{mediaId}/complete
/api/v1/merchants/{merchantId}/catalog-items
/api/v1/catalog/items
/api/v1/catalog/items/{itemId}
```

Also assert documented headers:

```text
Idempotency-Key on catalog create and media complete
If-Match on mutable PATCH resources
ETag on relevant GET responses
```

- [ ] **Step 2: Write the end-to-end backend marketplace acceptance test**

Test flow:

```text
create authenticated seller user from Plan 02 fixture
POST /me/merchants
POST merchant location at known coordinates
create READY media through MediaUploadService backed by the MinIO test container
create DRAFT Product CatalogItem with default Variant + one LocationVariant
publish item
perform guest GET /catalog/items with nearby lat/lng
assert result contains item and concrete resolvedLocation
assert result price comes from LocationVariant
GET /catalog/items/{itemId} with same location
assert variant.offer.locationVariantId equals the created LocationVariant ID
```

This is the M2 gate: seller can manage a purchasable offer; guest can discover that exact offer.

- [ ] **Step 3: Write authorization matrix test**

Create OWNER, MANAGER, STAFF assigned A, STAFF assigned B and unrelated user. Assert every capability from the plan matrix. The test must explicitly prove:

```text
JWT has no merchant role claim dependency
revoking membership takes effect on next API request
STAFF cannot access unassigned location even if they know UUID
unrelated user gets 404 for private merchant resources
```

- [ ] **Step 4: Write cross-table invariant tests**

Required:

```text
SERVICE LocationVariant cannot have inventory mode
PRODUCT LocationVariant must have inventory mode
LocationVariant merchant location belongs same merchant as CatalogItem
Variant belongs CatalogItem in URL
category compatible with CatalogItem type
last variant cannot be deleted
public item requires active merchant/item/location/variant/offer
media attachment requires READY + owner + purpose
```

- [ ] **Step 5: Run the complete backend suite**

```bash
cd apps/api
./mvnw clean verify
```

Expected: BUILD SUCCESS with all unit/integration tests green.

- [ ] **Step 6: Re-export and verify generated API client**

```bash
cd apps/api
./mvnw -Dtest=OpenApiExportTest -Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json test
cd ../..
pnpm api-client:generate
./infra/scripts/verify-generated-client.sh
pnpm api-client:check
```

Expected: no generated-client drift.

- [ ] **Step 7: Run full frontend quality gate**

```bash
pnpm web:lint
pnpm web:typecheck
pnpm web:test
pnpm web:build
```

Expected: all commands exit 0.

- [ ] **Step 8: Validate migrations from an empty database**

```bash
pnpm infra:down
docker volume rm covie_postgres_data 2>/dev/null || true
pnpm infra:up
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

In another shell, verify Flyway reports V001–V006 applied in order and `/api/v1/health` returns 200. Stop the app after verification.

If Compose uses a project-prefixed volume name different from `covie_postgres_data`, use `docker compose -f infra/compose/compose.local.yml down -v` instead; do not delete unrelated Docker volumes.

- [ ] **Step 9: Run static secret scan on changed files**

```bash
git grep -n -E '(AKIA[0-9A-Z]{16}|BEGIN (RSA |EC |)PRIVATE KEY|COVIE_GOOGLE_CLIENT_SECRET=.*[^}]|AWS_SECRET_ACCESS_KEY=.*[^}])' -- . ':!*.md' || true
```

Expected: no committed secret values.

- [ ] **Step 10: Commit acceptance artifacts/docs**

```bash
git add apps/api/src/test apps/web/tests packages/api-client README.md infra/scripts/verify-generated-client.sh
git commit -m "test(catalog): verify marketplace discovery milestone"
```

---

# Plan 03 Acceptance Checklist

Plan 03 is complete only when fresh verification proves every item below:

```text
[ ] Flyway clean DB applies V001 -> V006 in order
[ ] location_areas exists and PostGIS GIST indexes are active
[ ] user_addresses.location_area_id FK is installed
[ ] media upload uses presigned direct PUT, not Spring binary proxy
[ ] JPEG/PNG/WebP magic mismatch is rejected
[ ] media wrong owner/PENDING/FAILED cannot be attached
[ ] merchant creation atomically creates OWNER membership
[ ] last active OWNER cannot be removed
[ ] membership revoke changes authorization on next request
[ ] STAFF can access only explicitly assigned MerchantLocation
[ ] MerchantLocation coordinates are lng/lat-correct and timezone validated
[ ] hours exception overrides weekly schedule
[ ] every CatalogItem has >=1 Variant
[ ] Product/Service LocationVariant inventory-mode invariant holds
[ ] price source is LocationVariant only
[ ] aggregate catalog create is idempotent
[ ] same idempotency key with different body returns 409
[ ] mutable seller resources honor ETag / If-Match
[ ] stale concurrent update returns RESOURCE_VERSION_CONFLICT
[ ] publish/pause/activate/archive cannot be done through arbitrary status PATCH
[ ] catalog lifecycle writes Outbox intent in same transaction
[ ] public hidden/inactive resources return 404
[ ] search requires explicit LocationContext
[ ] another user's addressId returns 404
[ ] one discovery result resolves one concrete nearest MerchantLocation
[ ] public variant offer exposes exact locationVariantId
[ ] price sort does not silently resolve to a farther merchant location
[ ] cursor is opaque and keyset pagination has no duplicates on tie values
[ ] generated OpenAPI/TypeScript client is in sync
[ ] backend clean verify passes
[ ] frontend lint/typecheck/test/build passes
```

---

# Error Codes Added by Plan 03

Add these stable machine-readable codes to `ErrorCode` as they are introduced by the corresponding tasks:

```text
IF_MATCH_REQUIRED
INVALID_IF_MATCH
RESOURCE_VERSION_CONFLICT
IDEMPOTENCY_KEY_REQUIRED
IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
LOCATION_CONTEXT_REQUIRED
LOCATION_CONTEXT_INVALID
LOCATION_CONTEXT_AMBIGUOUS
MERCHANT_ACTION_NOT_ALLOWED
MERCHANT_LAST_OWNER_REQUIRED
MERCHANT_MEMBER_ALREADY_EXISTS
MERCHANT_MEMBER_USER_NOT_FOUND
MEMBERSHIP_ROLE_NOT_LOCATION_SCOPED
INVALID_TIMEZONE
MEDIA_TYPE_NOT_SUPPORTED
MEDIA_TOO_LARGE
MEDIA_UPLOAD_LIMIT_REACHED
MEDIA_UPLOAD_VERIFICATION_FAILED
MEDIA_NOT_ATTACHABLE
CATEGORY_NOT_COMPATIBLE
CATALOG_ATTRIBUTE_INVALID
CATALOG_VARIANT_COMBINATION_INVALID
CATALOG_LOCATION_OFFER_INVALID
CATALOG_PUBLISH_VALIDATION_FAILED
CATALOG_STATE_CONFLICT
CATALOG_FILTER_INVALID
CATALOG_CURSOR_INVALID
```

Use `VALIDATION_FAILED` with field codes for ordinary bean/input validation; use the specific codes above when frontend behavior depends on the business failure class.

---

# Transaction Boundaries Locked by Plan 03

```text
Merchant create:
  MerchantProfile + OWNER membership + audit -> one transaction

Membership role/revoke:
  membership lock + last-owner invariant + assignment cleanup + audit -> one transaction

Replace STAFF locations:
  validate all locations + delete old mappings + insert mappings -> one transaction

Catalog aggregate create:
  advisory idempotency lock + idempotency row + item/options/variants/offers/media/search doc/outbox -> one transaction

Catalog lifecycle command:
  item state + outbox -> one transaction

Media create upload:
  DB PENDING row commit first -> generate/use presigned URL from deterministic metadata

Media complete:
  S3 HEAD/prefix verification outside long DB transaction
  then idempotent short DB transaction PENDING -> READY

Media verification failure:
  commit FAILED state first
  best-effort object delete afterward
```

Do not keep a database transaction open while the browser uploads bytes or while a large object is transferred.

---

# Query/Index Review Notes

Before Plan 03 is accepted, run `EXPLAIN (ANALYZE, BUFFERS)` against representative discovery fixtures and confirm:

```text
ST_DWithin can use ix_merchant_locations_geo
catalog full-text predicate can use ix_catalog_items_search_vector
location_variants lookup uses ux_location_variant_active / ix_location_variant_location_available
public query does not issue N+1 entity loads
cursor query uses LIMIT + 1 and no COUNT(*)
```

Do not add speculative indexes without an explain plan or a known constraint/query need.

---

# Explicit Plan 03 Non-Goals

```text
inventory_balances/reservations/movements
shipping coverage/policies
cart
checkout_sessions
orders
Need requests/responses
review/rating/reputation
notification worker/SSE delivery
moderation cases/admin settings UI
catalog rating filter/sort
shipping/canServe discovery filters
external search engine
merchant invitation email acceptance
image transformations/thumbnails/virus scanning pipeline
multi-currency settlement
native mobile app
```

Plan 04 consumes the concrete `LocationVariant`, MerchantLocation authorization, `LocationContext`, idempotency and outbox infrastructure produced here.

---

# Execution Handoff

After this plan is saved to `docs/superpowers/plans/2026-08-08-covie-merchant-catalog.md`, implementation has two supported modes:

1. **Subagent-Driven (recommended)** — use `superpowers:subagent-driven-development`, one fresh implementation worker per task, with review between tasks.
2. **Inline Execution** — use `superpowers:executing-plans`, execute task batches with explicit verification checkpoints.

Do not start Plan 04 until the Plan 03 Acceptance Checklist has fresh evidence.

---

# Technical References Verified While Writing This Plan

- AWS SDK for Java 2.x supports S3 presigned PUT requests through `S3Presigner` / `PutObjectPresignRequest`.
- AWS S3 client/presigner support endpoint override and S3 service configuration; path-style access is used for MinIO/local compatibility.
- Spring Data JPA 4.1 supports repository lock metadata through `@Lock`; Plan 03 uses explicit locking only for ownership/invariant transitions that require it.
- PostgreSQL/PostGIS remains the source of geospatial filtering; no cache is authoritative for location resolution.
