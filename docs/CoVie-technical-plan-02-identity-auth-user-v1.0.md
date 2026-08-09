# CoVie Plan 02 — Identity, Auth & User Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Use `superpowers:using-git-worktrees` before implementation. Every task ends with a reviewable verification gate and commit.

**Project:** CoVie  
**Plan:** 02 / 10  
**Target repo path:** `docs/superpowers/plans/2026-08-08-covie-auth-user.md`  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Ready for implementation review  

**Goal:** Implement CoVie V1 identity, authentication, account/session security, `/me`, contact methods and saved addresses on top of Plan 01 so that a new user can register only through Google, set a CoVie password, later log in by Google or Email/Password, rotate refresh tokens safely, manage multiple sessions, recover a password through fresh Google authentication, and expose a stable authenticated `CurrentActor` for all later plans.

**Architecture:** `users` owns account/profile/contact/address data. `auth` owns credentials, Google OIDC integration, access JWTs, refresh-token families, one-time auth action tickets and session lifecycle. Browser authentication uses HttpOnly access/refresh cookies plus Spring Security CSRF protection; the same JWT decoder accepts `Authorization: Bearer` for future non-browser clients. PostgreSQL remains authoritative for users and sessions. Temporary Spring `HttpSession` state is permitted only where Spring Security requires it for the Google authorization-code handshake and is invalidated on OAuth success/failure; CoVie JWT authentication is not persisted into that HTTP session.

**Tech Stack additions:** Spring Security 7.1 via Spring Boot 4.1, Spring OAuth2 Client, Spring OAuth2 Resource Server/JWT, Nimbus JOSE/JWT through Spring Security, Argon2id via `Argon2PasswordEncoder`, Bouncy Castle 1.84, Hibernate Spatial/JTS, PostgreSQL/PostGIS, Flyway, MockMvc + Spring Security Test, Testcontainers, Next.js 16.2, React 19, TypeScript, Vitest.

---

# Global Constraints

- Plan 01 acceptance evidence must be green before implementation begins.
- First registration is **Google OIDC only**. There is no Email/Password self-registration endpoint in V1.
- A newly discovered Google identity does not receive a full CoVie authenticated session until password onboarding succeeds.
- Google identity is keyed by Google `sub`; email is metadata/contact/login material and must never replace `sub` as the Google identity key.
- Google email must be verified before it can seed the local EMAIL identity.
- After onboarding, the same `user_id` has both GOOGLE and EMAIL identities.
- Future login supports Google or Email/Password and resolves to the same `user_id`.
- Google is the automated V1 recovery root. No email reset link, email OTP, phone OTP, Facebook, Apple or Zalo login is implemented.
- Password storage uses Argon2id with `saltLength=16`, `hashLength=32`, `memory=19456 KiB`, `iterations=2`, `parallelism=1` as the baseline. The encoded stored value must carry an algorithm/version prefix for future migration.
- Access tokens are RSA-signed JWTs with 15-minute TTL and claims `sub`, global roles, `sessionId`, `iat`, `exp`, `jti`, `iss`.
- Merchant OWNER/MANAGER/STAFF membership is never embedded in the JWT.
- Auth sessions have an absolute 30-day lifetime. The initial refresh token is opaque random 256-bit and expires with the session; every rotated successor inherits the same session expiry. Refresh never extends the session lifetime. Raw refresh tokens are never persisted or logged.
- Every refresh rotates the token. Reuse of a used/replaced refresh token revokes that auth session/token family.
- Browser access and refresh credentials are HttpOnly cookies. Refresh cookie path is restricted to auth endpoints.
- Unsafe browser mutations require CSRF. Bearer-header clients are exempt from cookie-CSRF protection.
- Spring Security `csrf.spa()` is the baseline SPA integration; the frontend explicitly refreshes the CSRF token after login and logout.
- Public login failures use `INVALID_CREDENTIALS` for unknown-email and wrong-password cases.
- Private resource IDOR protection remains: invisible private resources return 404; visible-but-forbidden actions return 403.
- Revoking an auth session must be effective for subsequent authenticated API requests, not only after JWT expiration. Plan 02 therefore validates the JWT `sessionId` against active PostgreSQL session state.
- `auth_sessions`, `auth_refresh_tokens` and one-time auth action tickets are PostgreSQL-authoritative; Redis is not authoritative for session validity.
- `users` does not store email, phone or password directly.
- JPA entities are never returned directly by controllers.
- `user_addresses.geo_point` uses PostGIS `GEOGRAPHY(Point,4326)`.
- `location_area_id` and `avatar_media_id` may exist as nullable UUID columns before their referenced Plan 03 tables exist; Plan 03 adds the foreign keys after those tables are created.
- No production OAuth client secret, JWT private key, raw refresh token or generated local secret is committed.

---

# Important Migration Ordering Correction

The Master Technical Plan originally reserved `V014__auth_sessions.sql` after several domain migrations. That numbering cannot be used if Plan 02 is executed before Plans 03–07: once an environment applies V014, adding V003–V013 later would require Flyway out-of-order execution and create avoidable deployment risk.

**Plan 02 corrects the migration sequence from this point forward.** Migration version numbers follow implementation dependency order, not the earlier conceptual grouping.

```text
V001__extensions_and_foundation.sql       # Plan 01, already exists
V002__identity_users_and_audit.sql        # Plan 02
V003__auth_sessions_and_tickets.sql       # Plan 02

V004+                                      # allocated sequentially by later plans
```

Rules:

```text
never edit V001 after it has shipped
never create V014 early and backfill lower versions later
later plans continue from the highest applied version
Flyway outOfOrder remains false
```

This plan therefore supersedes the migration-number list in the Master Technical Plan while preserving the same logical tables.

---

# Repository Map Added by Plan 02

```text
apps/api/src/main/java/vn/covie/
├── auth/
│   ├── api/
│   │   ├── AuthController.java
│   │   ├── AuthSessionController.java
│   │   ├── CsrfController.java
│   │   └── dto/
│   ├── application/
│   │   ├── AuthApplicationService.java
│   │   ├── EmailPasswordLoginService.java
│   │   ├── GoogleAuthService.java
│   │   ├── InitialSessionIssuer.java
│   │   ├── IssuedSession.java
│   │   ├── PasswordRecoveryService.java
│   │   ├── RefreshRotationOutcome.java
│   │   ├── RefreshRotationTransaction.java
│   │   ├── RefreshTokenService.java
│   │   └── SessionManagementService.java
│   ├── model/
│   │   ├── AuthActionPurpose.java
│   │   ├── AuthActionTicketEntity.java
│   │   ├── AuthSessionEntity.java
│   │   ├── AuthSessionRevocationReason.java
│   │   ├── AuthRefreshTokenEntity.java
│   │   └── DeviceMetadata.java
│   ├── oauth/
│   │   ├── GoogleAuthIntent.java
│   │   ├── GoogleOAuthSuccessHandler.java
│   │   └── GoogleOAuthFailureHandler.java
│   ├── persistence/
│   │   ├── AuthActionTicketRepository.java
│   │   ├── AuthSessionRepository.java
│   │   └── AuthRefreshTokenRepository.java
│   ├── security/
│   │   ├── AuthSecurityConfiguration.java
│   │   ├── ActiveSessionFilter.java
│   │   ├── ApiAccessDeniedHandler.java
│   │   ├── ApiAuthenticationEntryPoint.java
│   │   ├── BrowserCsrfRequestMatcher.java
│   │   ├── CookieOrHeaderBearerTokenResolver.java
│   │   ├── CurrentActorProvider.java
│   │   ├── CsrfTokenLifecycleService.java
│   │   ├── IssuedAccessToken.java
│   │   ├── JwtConfiguration.java
│   │   ├── AuthProperties.java
│   │   ├── JwtTokenService.java
│   │   └── SecurityCookieService.java
│   └── token/
│       ├── OpaqueTokenGenerator.java
│       └── TokenFingerprint.java
├── users/
│   ├── api/
│   │   ├── MeController.java
│   │   ├── UserAddressController.java
│   │   ├── UserContactMethodController.java
│   │   └── dto/
│   ├── application/
│   │   ├── UserProfileService.java
│   │   ├── UserAddressService.java
│   │   └── UserContactMethodService.java
│   ├── model/
│   │   ├── AccountStatus.java
│   │   ├── IdentityProvider.java
│   │   ├── ContactType.java
│   │   ├── PinSource.java
│   │   ├── UserEntity.java
│   │   ├── UserIdentityEntity.java
│   │   ├── UserContactMethodEntity.java
│   │   ├── UserAddressEntity.java
│   │   └── SystemRoleAssignmentEntity.java
│   ├── persistence/
│   │   ├── UserRepository.java
│   │   ├── UserIdentityRepository.java
│   │   ├── UserContactMethodRepository.java
│   │   ├── UserAddressRepository.java
│   │   └── SystemRoleAssignmentRepository.java
│   └── security/
│       ├── EmailNormalizer.java
│       └── PasswordConfiguration.java
├── audit/
│   ├── AuditLogEntity.java
│   ├── AuditLogRepository.java
│   └── AuditLogWriter.java
├── shared/security/
│   └── CurrentActor.java                 # produced by Plan 01, reused not redefined
└── shared/web/
    └── RequestIdAccessor.java
```

Frontend additions:

```text
apps/web/
├── app/(auth)/login/page.tsx
├── app/(auth)/onboarding/password/page.tsx
├── app/(auth)/reset-password/page.tsx
├── app/(buyer)/me/page.tsx
├── app/(buyer)/me/security/sessions/page.tsx
├── app/(buyer)/me/addresses/page.tsx
├── features/auth/
│   ├── auth-api.ts
│   ├── auth-session.ts
│   ├── csrf.ts
│   ├── login-form.tsx
│   ├── google-login-button.tsx
│   ├── password-form.tsx
│   └── session-list.tsx
├── features/profile/
│   ├── profile-api.ts
│   └── address-api.ts
└── lib/auth/
    └── require-user.ts
```

---

# Public Interfaces Produced by Plan 02

Later plans may rely on these exact Java interfaces/classes:

```text
vn.covie.auth.security.CurrentActorProvider.currentActor()
vn.covie.auth.security.JwtTokenService.issueAccessToken(...)
vn.covie.auth.application.SessionManagementService.requireActiveSession(UUID)
vn.covie.users.persistence.UserRepository
vn.covie.users.persistence.UserIdentityRepository
vn.covie.users.persistence.SystemRoleAssignmentRepository
vn.covie.audit.AuditLogWriter
vn.covie.shared.web.RequestIdAccessor
vn.covie.users.security.EmailNormalizer
```

Stable API resources introduced:

```text
GET  /api/v1/auth/google/start
GET  /api/v1/auth/google/callback/{registrationId}     # provider callback, browser-facing redirect target
GET  /api/v1/auth/csrf
POST /api/v1/auth/onboarding/set-password
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/password/reset-with-google

GET  /api/v1/me
PATCH /api/v1/me
GET  /api/v1/me/security/sessions
DELETE /api/v1/me/security/sessions/{sessionId}
POST /api/v1/me/security/sessions/revoke-others
GET/POST/PATCH/DELETE /api/v1/me/addresses...
GET/POST/PATCH/DELETE /api/v1/me/contact-methods...
```

---

# Task 1: Add Security/Spatial Dependencies and Create Identity/Auth Migrations

**Files:**
- Modify: `apps/api/pom.xml`
- Create: `apps/api/src/main/resources/db/migration/V002__identity_users_and_audit.sql`
- Create: `apps/api/src/main/resources/db/migration/V003__auth_sessions_and_tickets.sql`
- Create: `apps/api/src/test/java/vn/covie/auth/persistence/AuthSchemaMigrationTest.java`

**Interfaces:**
- Consumes: Plan 01 Flyway/PostGIS Testcontainer.
- Produces: database schema used by every remaining Plan 02 task.

- [ ] **Step 1: Write the migration integration test first**

```java
package vn.covie.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class AuthSchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void identityAndAuthTablesExistAfterFlyway() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "users")).isTrue();
            assertThat(tableExists(connection, "user_identities")).isTrue();
            assertThat(tableExists(connection, "system_role_assignments")).isTrue();
            assertThat(tableExists(connection, "user_addresses")).isTrue();
            assertThat(tableExists(connection, "audit_logs")).isTrue();
            assertThat(tableExists(connection, "auth_sessions")).isTrue();
            assertThat(tableExists(connection, "auth_refresh_tokens")).isTrue();
            assertThat(tableExists(connection, "auth_action_tickets")).isTrue();
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (var statement = connection.prepareStatement(
                "select exists(select 1 from information_schema.tables where table_schema='public' and table_name=?)")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

```bash
cd apps/api
./mvnw -Dtest=AuthSchemaMigrationTest test
```

Expected: FAIL because Plan 02 tables do not exist.

- [ ] **Step 3: Add Maven dependencies**

Add to `apps/api/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-spatial</artifactId>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.84</version>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Do not add Spring Authorization Server. CoVie is issuing its own application JWT and is not implementing a general OAuth authorization server.

- [ ] **Step 4: Create `V002__identity_users_and_audit.sql`**

```sql
create table users (
    id uuid primary key,
    display_name varchar(120) not null,
    avatar_media_id uuid null,
    account_status varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_users_status check (account_status in ('ACTIVE','SUSPENDED','BANNED','DELETED'))
);

create table user_identities (
    id uuid primary key,
    user_id uuid not null references users(id),
    provider varchar(30) not null,
    provider_subject varchar(255) null,
    email_normalized varchar(320) null,
    phone_normalized varchar(30) null,
    password_hash varchar(255) null,
    verified_at timestamptz null,
    created_at timestamptz not null,
    last_used_at timestamptz null,
    constraint ck_user_identity_provider check (provider in ('EMAIL','PHONE','GOOGLE','FACEBOOK','APPLE','ZALO')),
    constraint ck_email_identity_password check (
        provider <> 'EMAIL' or (email_normalized is not null and password_hash is not null)
    ),
    constraint ck_google_identity_subject check (
        provider <> 'GOOGLE' or provider_subject is not null
    )
);

create unique index ux_user_identity_provider_subject
    on user_identities(provider, provider_subject)
    where provider_subject is not null;

-- EMAIL is the local-login namespace. Google email is metadata and may change,
-- therefore it is deliberately not globally unique across all provider rows.
create unique index ux_user_identity_local_email
    on user_identities(email_normalized)
    where provider = 'EMAIL' and email_normalized is not null;

create index ix_user_identity_user on user_identities(user_id);
create index ix_user_identity_google_email
    on user_identities(email_normalized)
    where provider = 'GOOGLE' and email_normalized is not null;

create table system_role_assignments (
    id uuid primary key,
    user_id uuid not null references users(id),
    role varchar(30) not null,
    created_at timestamptz not null,
    constraint ck_system_role check (role in ('USER','ADMIN')),
    constraint ux_system_role_assignment unique (user_id, role)
);

create table user_contact_methods (
    id uuid primary key,
    user_id uuid not null references users(id),
    contact_type varchar(30) not null,
    value text not null,
    is_primary boolean not null default false,
    is_verified boolean not null default false,
    status varchar(20) not null,
    created_at timestamptz not null,
    constraint ck_user_contact_type check (contact_type in ('PHONE','ZALO','MESSENGER','OTHER')),
    constraint ck_user_contact_status check (status in ('ACTIVE','INACTIVE'))
);

create index ix_user_contact_user_status on user_contact_methods(user_id, status);
create unique index ux_user_primary_contact_type
    on user_contact_methods(user_id, contact_type)
    where is_primary = true and status = 'ACTIVE';

create table user_addresses (
    id uuid primary key,
    user_id uuid not null references users(id),
    label varchar(80) not null,
    recipient_name varchar(120) null,
    phone varchar(30) null,
    address_text text not null,
    location_area_id uuid null,
    geo_point geography(Point,4326) not null,
    pin_source varchar(30) not null,
    note text null,
    is_default boolean not null default false,
    status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_user_address_pin_source check (pin_source in ('GPS','GEOCODED','USER_ADJUSTED')),
    constraint ck_user_address_status check (status in ('ACTIVE','DELETED'))
);

create index ix_user_address_user_status on user_addresses(user_id, status);
create index ix_user_address_geo on user_addresses using gist(geo_point);
create unique index ux_user_default_address
    on user_addresses(user_id)
    where is_default = true and status = 'ACTIVE';

create table audit_logs (
    id uuid primary key,
    actor_user_id uuid null references users(id),
    actor_type varchar(20) not null,
    event_category varchar(30) not null,
    action varchar(100) not null,
    entity_type varchar(60) not null,
    entity_id uuid null,
    before_snapshot jsonb null,
    after_snapshot jsonb null,
    request_id varchar(100) null,
    ip_hash_or_metadata jsonb null,
    created_at timestamptz not null
);

create index ix_audit_actor_created on audit_logs(actor_user_id, created_at desc);
create index ix_audit_entity_created on audit_logs(entity_type, entity_id, created_at desc);
```

Do not add foreign keys from `users.avatar_media_id` or `user_addresses.location_area_id` yet because Plan 03 creates `media_assets` and `location_areas`.

- [ ] **Step 5: Create `V003__auth_sessions_and_tickets.sql`**

```sql
create table auth_sessions (
    id uuid primary key,
    user_id uuid not null references users(id),
    token_family_id uuid not null,
    device_name varchar(120) null,
    user_agent text null,
    created_at timestamptz not null,
    last_used_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz null,
    revocation_reason varchar(40) null,
    version bigint not null default 0,
    constraint ux_auth_session_family unique (token_family_id),
    constraint ck_auth_session_revocation_reason check (
        revocation_reason is null or revocation_reason in (
            'LOGOUT','USER_REVOKED','REVOKE_OTHERS','PASSWORD_RESET','REFRESH_REUSE','ADMIN_REVOKED','EXPIRED'
        )
    )
);

create index ix_auth_session_user_active
    on auth_sessions(user_id, expires_at desc)
    where revoked_at is null;

create table auth_refresh_tokens (
    id uuid primary key,
    session_id uuid not null references auth_sessions(id),
    token_hash char(64) not null,
    replaced_by_token_id uuid null,
    expires_at timestamptz not null,
    used_at timestamptz null,
    revoked_at timestamptz null,
    created_at timestamptz not null,
    constraint ux_auth_refresh_token_hash unique (token_hash),
    constraint fk_auth_refresh_replaced_by
        foreign key (replaced_by_token_id) references auth_refresh_tokens(id)
);

create index ix_refresh_session_created on auth_refresh_tokens(session_id, created_at desc);
create index ix_refresh_active_hash on auth_refresh_tokens(token_hash) where revoked_at is null;

create table auth_action_tickets (
    id uuid primary key,
    purpose varchar(30) not null,
    token_hash char(64) not null,
    user_id uuid null references users(id),
    google_subject varchar(255) not null,
    email_normalized varchar(320) not null,
    google_email_verified boolean not null,
    display_name varchar(120) null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    consumed_at timestamptz null,
    constraint ux_auth_action_ticket_hash unique (token_hash),
    constraint ck_auth_action_purpose check (purpose in ('ONBOARDING','PASSWORD_RESET')),
    constraint ck_auth_action_verified_email check (google_email_verified = true)
);

create index ix_auth_action_expiry on auth_action_tickets(expires_at) where consumed_at is null;
create index ix_auth_action_user on auth_action_tickets(user_id, purpose) where consumed_at is null;
```

- [ ] **Step 6: Run migration tests and inspect schema**

```bash
cd apps/api
./mvnw -Dtest=AuthSchemaMigrationTest test
```

Expected: PASS.

Then verify geography and indexes in the test container or local DB:

```sql
select column_name, udt_name
from information_schema.columns
where table_name = 'user_addresses' and column_name = 'geo_point';
```

Expected logical type: PostGIS geography.

- [ ] **Step 7: Commit**

```bash
git add apps/api/pom.xml apps/api/src/main/resources/db/migration/V002__identity_users_and_audit.sql apps/api/src/main/resources/db/migration/V003__auth_sessions_and_tickets.sql apps/api/src/test/java/vn/covie/auth/persistence/AuthSchemaMigrationTest.java
git commit -m "feat(auth): add identity and session schema"
```

---

# Task 2: Implement User, Identity, Role, Audit and Auth Persistence Models

**Files:**
- Create: `apps/api/src/main/java/vn/covie/users/model/AccountStatus.java`
- Create: `apps/api/src/main/java/vn/covie/users/model/IdentityProvider.java`
- Create: `apps/api/src/main/java/vn/covie/users/model/ContactType.java`
- Create: `apps/api/src/main/java/vn/covie/users/model/PinSource.java`
- Create: `apps/api/src/main/java/vn/covie/users/model/UserEntity.java`
- Create: `apps/api/src/main/java/vn/covie/users/model/UserIdentityEntity.java`
- Create: `apps/api/src/main/java/vn/covie/users/model/UserContactMethodEntity.java`
- Create: `apps/api/src/main/java/vn/covie/users/model/UserAddressEntity.java`
- Create: `apps/api/src/main/java/vn/covie/users/model/SystemRoleAssignmentEntity.java`
- Create: repositories under `vn.covie.users.persistence`
- Create: auth entities/repositories under `vn.covie.auth.model` and `vn.covie.auth.persistence`
- Create: `apps/api/src/main/java/vn/covie/audit/AuditLogEntity.java`
- Create: `apps/api/src/main/java/vn/covie/audit/AuditLogRepository.java`
- Create: `apps/api/src/main/java/vn/covie/audit/AuditLogWriter.java`
- Test: `apps/api/src/test/java/vn/covie/users/persistence/UserIdentityRepositoryTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/persistence/AuthSessionRepositoryTest.java`

**Produces:** Persistence primitives used by all auth/application services.

- [ ] **Step 1: Write identity uniqueness repository tests**

```java
class UserIdentityRepositoryTest extends PostgresIntegrationTest {

    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;

    @Test
    void googleSubjectIsCanonicalLookup() {
        UserEntity user = users.save(UserEntity.active(UUID.randomUUID(), "Minh", Instant.parse("2026-08-08T00:00:00Z")));
        identities.save(UserIdentityEntity.google(
                UUID.randomUUID(), user.getId(), "google-sub-123", "minh@example.com",
                Instant.parse("2026-08-08T00:00:00Z")));

        assertThat(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub-123"))
                .get().getUserId()
                .isEqualTo(user.getId());
    }

    @Test
    void localEmailIsUniqueWhileGoogleMayCarrySameEmailMetadata() {
        UserEntity user = users.save(UserEntity.active(UUID.randomUUID(), "Minh", Instant.parse("2026-08-08T00:00:00Z")));
        identities.save(UserIdentityEntity.google(
                UUID.randomUUID(), user.getId(), "google-sub-123", "minh@example.com",
                Instant.parse("2026-08-08T00:00:00Z")));
        identities.save(UserIdentityEntity.email(
                UUID.randomUUID(), user.getId(), "minh@example.com", "{argon2id-v1}hash",
                Instant.parse("2026-08-08T00:00:00Z")));

        assertThat(identities.findLocalEmail("minh@example.com")).isPresent();
    }
}
```

- [ ] **Step 2: Run RED**

```bash
cd apps/api
./mvnw -Dtest=UserIdentityRepositoryTest test
```

Expected: compilation FAIL because entities/repositories do not exist.

- [ ] **Step 3: Implement enums exactly**

```java
public enum AccountStatus { ACTIVE, SUSPENDED, BANNED, DELETED }
public enum IdentityProvider { EMAIL, PHONE, GOOGLE, FACEBOOK, APPLE, ZALO }
public enum ContactType { PHONE, ZALO, MESSENGER, OTHER }
public enum PinSource { GPS, GEOCODED, USER_ADJUSTED }
```

Auth enums:

```java
public enum AuthActionPurpose { ONBOARDING, PASSWORD_RESET }
public enum AuthSessionRevocationReason {
    LOGOUT, USER_REVOKED, REVOKE_OTHERS, PASSWORD_RESET, REFRESH_REUSE, ADMIN_REVOKED, EXPIRED
}
```

- [ ] **Step 4: Implement `UserEntity` with optimistic versioning**

Core mapping:

```java
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private AccountStatus accountStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    protected UserEntity() {}

    public static UserEntity active(UUID id, String displayName, Instant now) {
        UserEntity user = new UserEntity();
        user.id = id;
        user.displayName = displayName;
        user.accountStatus = AccountStatus.ACTIVE;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public boolean isActive() { return accountStatus == AccountStatus.ACTIVE; }
    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public long getVersion() { return version; }
}
```

Do not add password/email fields.

- [ ] **Step 5: Implement `UserIdentityEntity` factories**

Required invariant methods:

```java
public static UserIdentityEntity google(
        UUID id, UUID userId, String subject, String emailNormalized, Instant verifiedAt)

public static UserIdentityEntity email(
        UUID id, UUID userId, String emailNormalized, String passwordHash, Instant verifiedAt)

public void updatePasswordHash(String passwordHash)
public void markUsed(Instant now)
```

`google(...)` sets provider=`GOOGLE`, `providerSubject`, optional email metadata and `verifiedAt`; password hash is null. `email(...)` sets provider=`EMAIL`, email and password hash; provider subject is null.

- [ ] **Step 6: Implement `UserAddressEntity` using Hibernate Spatial geography**

```java
@JdbcTypeCode(SqlTypes.GEOGRAPHY)
@Column(name = "geo_point", nullable = false, columnDefinition = "geography(Point,4326)")
private Point geoPoint;
```

Coordinates are constructed in longitude/latitude order:

```java
GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
Point point = factory.createPoint(new Coordinate(longitude, latitude));
point.setSRID(4326);
```

Do not reverse latitude and longitude.

- [ ] **Step 7: Implement repositories with explicit auth lookup methods**

```java
public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {
    Optional<UserIdentityEntity> findByProviderAndProviderSubject(IdentityProvider provider, String subject);

    @Query("""
        select i from UserIdentityEntity i
        where i.provider = vn.covie.users.model.IdentityProvider.EMAIL
          and i.emailNormalized = :email
        """)
    Optional<UserIdentityEntity> findLocalEmail(@Param("email") String email);

    List<UserIdentityEntity> findAllByUserId(UUID userId);
}
```

`AuthRefreshTokenRepository` must support immutable-hash lookup plus ID locking:

```java
Optional<AuthRefreshTokenEntity> findByTokenHash(String tokenHash);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select t from AuthRefreshTokenEntity t where t.id = :id")
Optional<AuthRefreshTokenEntity> findByIdForUpdate(@Param("id") UUID id);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select t from AuthRefreshTokenEntity t
    where t.sessionId = :sessionId
    order by t.createdAt, t.id
    """)
List<AuthRefreshTokenEntity> findAllBySessionIdForUpdate(@Param("sessionId") UUID sessionId);
```

The token hash is immutable and unique. Transactional mutation follows a **session-first lock order**: resolve token hash without locking, lock its `auth_sessions` row, then lock the refresh-token row(s). Session revocation/password reset use the same order to avoid refresh-vs-revoke deadlocks.

`AuthSessionRepository` must include:

```java
Optional<AuthSessionEntity> findByIdAndRevokedAtIsNull(UUID id);
List<AuthSessionEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

@Query("""
    select s from AuthSessionEntity s
    where s.id = :id
      and s.revokedAt is null
      and exists (
          select 1 from UserEntity u
          where u.id = s.userId
            and u.accountStatus = vn.covie.users.model.AccountStatus.ACTIVE
      )
    """)
Optional<AuthSessionEntity> findUsableById(@Param("id") UUID id);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from AuthSessionEntity s where s.id = :id")
Optional<AuthSessionEntity> findByIdForUpdate(@Param("id") UUID id);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select s from AuthSessionEntity s
    where s.userId = :userId and s.revokedAt is null
    order by s.id
    """)
List<AuthSessionEntity> findAllActiveByUserIdForUpdate(@Param("userId") UUID userId);

@Modifying
@Query("""
    update AuthSessionEntity s set s.lastUsedAt = :now
    where s.id = :sessionId
      and s.revokedAt is null
      and s.lastUsedAt <= :cutoff
    """)
int touchLastUsedIfBefore(UUID sessionId, Instant now, Instant cutoff);

@Modifying
@Query("""
    update AuthSessionEntity s
       set s.revokedAt = :now, s.revocationReason = :reason
     where s.userId = :userId and s.revokedAt is null
    """)
int revokeAllActive(UUID userId, Instant now, AuthSessionRevocationReason reason);
```

- [ ] **Step 8: Implement `AuditLogWriter` as the single write service**

```java
@Service
public class AuditLogWriter {
    private final AuditLogRepository repository;
    private final UuidV7Generator ids;
    private final AppClock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void securityEvent(
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            String requestId,
            Map<String, Object> metadata) {
        repository.save(AuditLogEntity.security(
                ids.generate(), actorUserId, action, entityType, entityId,
                requestId, metadata, clock.instant()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void securityFailure(
            String action,
            String requestId,
            Map<String, Object> metadata) {
        repository.save(AuditLogEntity.security(
                ids.generate(), null, action, "AUTH", null,
                requestId, metadata, clock.instant()));
    }
}
```

Use `securityEvent(...)` for success/domain changes that must commit atomically with their business transaction. Use `securityFailure(...)` for rejected login/security attempts that still need an audit row even though the caller will throw; the separate transaction prevents the audit record from being rolled back with the failed authentication request.

Never put passwords, raw tokens, OAuth codes or full private request bodies into `metadata`.

- [ ] **Step 9: Run persistence tests**

```bash
cd apps/api
./mvnw -Dtest=UserIdentityRepositoryTest,AuthSessionRepositoryTest test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add apps/api/src/main/java/vn/covie/users apps/api/src/main/java/vn/covie/auth/model apps/api/src/main/java/vn/covie/auth/persistence apps/api/src/main/java/vn/covie/audit apps/api/src/test/java/vn/covie/users/persistence apps/api/src/test/java/vn/covie/auth/persistence
git commit -m "feat(auth): add user and auth persistence models"
```

---

# Task 3: Implement Email Normalization, Argon2id Password Policy and Opaque Token Utilities

**Files:**
- Create: `apps/api/src/main/java/vn/covie/users/security/EmailNormalizer.java`
- Create: `apps/api/src/main/java/vn/covie/users/security/PasswordConfiguration.java`
- Create: `apps/api/src/main/java/vn/covie/auth/token/OpaqueTokenGenerator.java`
- Create: `apps/api/src/main/java/vn/covie/auth/token/TokenFingerprint.java`
- Test: `apps/api/src/test/java/vn/covie/users/security/EmailNormalizerTest.java`
- Test: `apps/api/src/test/java/vn/covie/users/security/PasswordConfigurationTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/token/OpaqueTokenGeneratorTest.java`

**Produces:** deterministic email normalization, versioned Argon2id hashes, secure 256-bit opaque tokens and SHA-256 fingerprints.

- [ ] **Step 1: Write normalization tests**

```java
@Test
void trimsAndLowercasesEmailUsingRootLocale() {
    assertThat(normalizer.normalize("  User.Name@Example.COM  "))
            .isEqualTo("user.name@example.com");
}

@Test
void rejectsBlankEmail() {
    assertThatThrownBy(() -> normalizer.normalize("  "))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Implement `EmailNormalizer`**

```java
@Component
public class EmailNormalizer {
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }
}
```

Do not use Gmail-specific dot removal or `+tag` removal. Those transform the user’s address semantics and are not CoVie rules.

- [ ] **Step 3: Write password tests**

```java
@Test
void hashesWithVersionedArgon2idPrefix() {
    String encoded = encoder.encode("correct horse battery staple");
    assertThat(encoded).startsWith("{argon2id-v1}");
    assertThat(encoder.matches("correct horse battery staple", encoded)).isTrue();
    assertThat(encoder.matches("wrong", encoded)).isFalse();
}
```

- [ ] **Step 4: Configure versioned Argon2id**

```java
@Configuration
public class PasswordConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        PasswordEncoder argon2 = new Argon2PasswordEncoder(
                16,
                32,
                1,
                19_456,
                2);

        return new DelegatingPasswordEncoder(
                "argon2id-v1",
                Map.of("argon2id-v1", argon2));
    }
}
```

Password request validation baseline:

```text
minimum 10 Java `CharSequence` characters as enforced by Bean Validation `@Size`
maximum 200 characters
no composition rule requiring uppercase/symbol/digit
reject blank-only value
```

Do not silently truncate passwords.

- [ ] **Step 5: Implement opaque token generation**

```java
@Component
public class OpaqueTokenGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate256BitToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

- [ ] **Step 6: Implement token fingerprinting**

```java
@Component
public class TokenFingerprint {
    public String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

Because the raw token has 256 bits of cryptographic entropy, a fast one-way fingerprint is appropriate; do not use Argon2 for refresh-token lookup.

- [ ] **Step 7: Verify token tests**

Tests must assert:

```text
two generated tokens differ
Base64URL token decodes to 32 bytes
same raw token -> same fingerprint
different raw token -> different fingerprint
```

Run:

```bash
cd apps/api
./mvnw -Dtest=EmailNormalizerTest,PasswordConfigurationTest,OpaqueTokenGeneratorTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/users/security apps/api/src/main/java/vn/covie/auth/token apps/api/src/test/java/vn/covie/users/security apps/api/src/test/java/vn/covie/auth/token
git commit -m "feat(auth): add credential and token primitives"
```

---

# Task 4: Implement RSA JWT Issuance, Validation and Security Cookies

**Files:**
- Create: `apps/api/src/main/java/vn/covie/auth/security/AuthProperties.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/JwtConfiguration.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/JwtTokenService.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/IssuedAccessToken.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/SecurityCookieService.java`
- Create: `apps/api/src/test/java/vn/covie/auth/security/JwtTokenServiceTest.java`
- Create: `apps/api/src/test/java/vn/covie/auth/security/SecurityCookieServiceTest.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/src/main/resources/application-local.yml`
- Modify: `.env.example`
- Create: `infra/scripts/generate-dev-jwt-keys.sh`

**Produces:** signed access JWTs and consistent cookie issuance/clearing.

- [ ] **Step 1: Add auth configuration properties**

`application.yml`:

```yaml
covie:
  auth:
    issuer: ${COVIE_AUTH_ISSUER:https://covie.vn}
    access-token-ttl: PT15M
    refresh-token-ttl: P30D
    action-ticket-ttl: PT10M
    jwt:
      key-id: ${COVIE_JWT_KEY_ID:covie-dev-1}
      private-key-path: ${COVIE_JWT_PRIVATE_KEY_PATH:}
      public-key-path: ${COVIE_JWT_PUBLIC_KEY_PATH:}
    cookies:
      secure: ${COVIE_COOKIE_SECURE:true}
      same-site: Lax
      access-name: covie_at
      refresh-name: covie_rt
      action-name: covie_auth_action
```

`application-local.yml` sets only:

```yaml
covie:
  auth:
    cookies:
      secure: false
```

Production never defaults to an embedded private key.

Bind it with one root properties type:

```java
@ConfigurationProperties(prefix = "covie.auth")
public record AuthProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration actionTicketTtl,
        Jwt jwt,
        Cookies cookies) {

    public record Jwt(String keyId, Path privateKeyPath, Path publicKeyPath) {}

    public record Cookies(
            boolean secure,
            String sameSite,
            String accessName,
            String refreshName,
            String actionName) {}
}
```

Register it with `@EnableConfigurationProperties(AuthProperties.class)` in `JwtConfiguration`. All auth services use this single typed source; do not re-read auth env variables directly.

- [ ] **Step 2: Write JWT round-trip test**

Test requirements:

```text
subject = user UUID
sessionId claim exists
roles contains USER
issuer matches configuration
jti exists
expiration - issuedAt = 15 minutes
signature validates with configured public key
```

- [ ] **Step 3: Implement `JwtConfiguration` with Spring Security `JwtEncoder`/`JwtDecoder`**

Use `NimbusJwtEncoder` and `NimbusJwtDecoder` with RSA keys and RS256. Decoder validators include default timestamp checks plus issuer validation.

Core bean shape:

```java
@Bean
JwtDecoder jwtDecoder(RSAPublicKey publicKey, AuthProperties props) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
    return decoder;
}
```

Encoder uses a `JWKSource<SecurityContext>` containing an `RSAKey` with `keyID` from configuration.

- [ ] **Step 4: Implement `JwtTokenService`**

Create `IssuedAccessToken.java`:

```java
public record IssuedAccessToken(String value, Instant expiresAt) {}
```

Then implement the service:

```java
@Service
public class JwtTokenService {
    public IssuedAccessToken issueAccessToken(
            UUID userId,
            UUID sessionId,
            Set<SystemRole> roles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(ids.generate().toString())
                .claim("sessionId", sessionId.toString())
                .claim("roles", roles.stream().map(Enum::name).sorted().toList())
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build(), claims));
        return new IssuedAccessToken(jwt.getTokenValue(), expiresAt);
    }
}
```

- [ ] **Step 5: Implement cookies**

`SecurityCookieService` methods:

```text
writeAccessCookie(response, rawJwt, expiresAt)
writeRefreshCookie(response, rawRefresh, expiresAt)
writeActionTicketCookie(response, rawTicket, expiresAt)
clearAccessCookie(response)
clearRefreshCookie(response)
clearActionTicketCookie(response)
clearAllAuthCookies(response)
```

Cookie rules:

```text
access:  HttpOnly, Secure(prod), SameSite=Lax, Path=/, Max-Age <= 15m
refresh: HttpOnly, Secure(prod), SameSite=Lax, Path=/api/v1/auth, Max-Age <= 30d
action:  HttpOnly, Secure(prod), SameSite=Lax, Path=/api/v1/auth, Max-Age <= 10m
```

Use Spring `ResponseCookie` and add `Set-Cookie` headers. Do not manually concatenate user input into cookie headers.

- [ ] **Step 6: Add local key generation script**

`infra/scripts/generate-dev-jwt-keys.sh` generates RSA 3072-bit PEM keys into ignored directory `infra/secrets/dev/` and refuses to overwrite existing files unless explicitly removed first.

```bash
#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT_DIR/infra/secrets/dev"
mkdir -p "$OUT"
if [[ -e "$OUT/jwt-private.pem" || -e "$OUT/jwt-public.pem" ]]; then
  echo "dev JWT keys already exist; refusing to overwrite" >&2
  exit 1
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$OUT/jwt-private.pem"
openssl rsa -pubout -in "$OUT/jwt-private.pem" -out "$OUT/jwt-public.pem"
chmod 600 "$OUT/jwt-private.pem"
```

Ensure `.gitignore` contains:

```gitignore
infra/secrets/
```

- [ ] **Step 7: Run tests**

```bash
cd apps/api
./mvnw -Dtest=JwtTokenServiceTest,SecurityCookieServiceTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/auth/security apps/api/src/test/java/vn/covie/auth/security apps/api/src/main/resources/application.yml apps/api/src/main/resources/application-local.yml .env.example .gitignore infra/scripts/generate-dev-jwt-keys.sh
git commit -m "feat(auth): issue JWT access tokens and secure cookies"
```

---

# Task 5: Configure Spring Security, Cookie/Bearer Authentication, Active Session Checks and CSRF

**Files:**
- Create: `apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/CookieOrHeaderBearerTokenResolver.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/BrowserCsrfRequestMatcher.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/ActiveSessionFilter.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/CurrentActorProvider.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/CsrfTokenLifecycleService.java`
- Create: `apps/api/src/main/java/vn/covie/shared/web/RequestIdAccessor.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/ApiAuthenticationEntryPoint.java`
- Create: `apps/api/src/main/java/vn/covie/auth/security/ApiAccessDeniedHandler.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Create: `apps/api/src/main/java/vn/covie/auth/api/CsrfController.java`
- Create: `apps/api/src/main/java/vn/covie/auth/api/dto/CsrfTokenResponse.java`
- Test: `apps/api/src/test/java/vn/covie/auth/security/AuthSecurityConfigurationTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/security/ActiveSessionFilterTest.java`

**Produces:** authenticated `CurrentActor` for later modules and browser CSRF enforcement.

- [ ] **Step 1: Write security behavior tests before configuration**

MockMvc matrix:

```text
GET  /api/v1/health                             -> 200 anonymous
GET  /api/v1/auth/csrf                          -> 200 anonymous
GET  /api/v1/me                                 -> 401 anonymous
POST /api/v1/auth/login without CSRF            -> 403
POST /api/v1/auth/login invalid CSRF            -> 403
POST /api/v1/auth/login with valid CSRF         -> reaches controller
POST /api/v1/auth/refresh + stale access cookie  -> stale access cookie ignored; reaches refresh controller
POST /api/v1/auth/logout + stale access cookie   -> stale access cookie ignored; reaches logout controller
CSRF lifecycle invalidation clears COVIE-XSRF-TOKEN cookie after auth boundary
unsafe request with Authorization: Bearer ...   -> CSRF exempt
JWT with revoked/nonexistent sessionId           -> 401
JWT/session for SUSPENDED/BANNED/DELETED user     -> 401
```

- [ ] **Step 2: Extend shared error codes and add request-id accessor**

Add the Plan 02 codes listed in the dedicated Error Codes section to `ErrorCode.java`. Add:

```java
@Component
public final class RequestIdAccessor {
    public String current() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
```

Security filters run before MVC exception advice, so implement `ApiAuthenticationEntryPoint` and `ApiAccessDeniedHandler` that serialize `ApiErrorResponse` directly with Jackson and preserve the current request ID. `ApiAccessDeniedHandler` returns `CSRF_INVALID` for `MissingCsrfTokenException`/`InvalidCsrfTokenException`; other authorization failures use `ACCESS_DENIED`.

- [ ] **Step 3: Implement cookie-or-header bearer resolution**

Rules:

```text
1. if Authorization header is present, resolve it as Bearer and never fall back silently
2. for `/api/v1/auth/**`, do NOT resolve `covie_at` from cookies; login/refresh/logout/OAuth/recovery must survive a stale access cookie
3. outside `/api/v1/auth/**`, use `covie_at` when no Authorization header exists
4. never accept access token from query parameters
```

The auth-namespace exception applies only to automatic **cookie** resolution. An explicit `Authorization: Bearer ...` header is still authenticated normally.

Class:

```java
@Component
public final class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {
    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            return headerResolver.resolve(request);
        }
        if (request.getRequestURI().startsWith("/api/v1/auth/")) {
            return null;
        }
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "covie_at".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 4: Implement CSRF matcher**

`BrowserCsrfRequestMatcher` returns `false` for GET/HEAD/OPTIONS/TRACE and returns `false` for requests carrying an `Authorization: Bearer ...` header. All other unsafe requests return `true`.

Do not exempt an unsafe request merely because it has an arbitrary `Authorization` header.

- [ ] **Step 5: Configure Spring Security SPA CSRF**

Expose the CSRF repository as a bean so auth success/logout code can explicitly invalidate the old token:

```java
@Bean
CookieCsrfTokenRepository csrfTokenRepository(AuthProperties authProperties) {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieName("COVIE-XSRF-TOKEN");
    repository.setHeaderName("X-CSRF-Token");
    repository.setCookiePath("/");
    repository.setCookieCustomizer(cookie -> cookie
            .sameSite("Lax")
            .secure(authProperties.cookies().secure()));
    return repository;
}
```

Create `CsrfTokenLifecycleService`:

```java
@Component
public final class CsrfTokenLifecycleService {
    private final CookieCsrfTokenRepository repository;

    public void invalidate(HttpServletRequest request, HttpServletResponse response) {
        repository.saveToken(null, request, response);
    }
}
```

Call `invalidate(...)` **after a successful** onboarding/login/refresh/password-reset authentication boundary and during logout, and from the existing-Google-user OAuth success handler. The frontend then calls `GET /api/v1/auth/csrf` to mint a new token. Do not invalidate before an unsafe request has passed CSRF validation.

Security chain core:

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/auth/csrf", "/api/v1/auth/google/start").permitAll()
        .requestMatchers("/api/v1/auth/google/callback/**").permitAll()
        .requestMatchers("/api/v1/auth/oauth2/authorization/**").permitAll()
        .requestMatchers(HttpMethod.POST,
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/onboarding/set-password",
                "/api/v1/auth/password/reset-with-google").permitAll()
        .requestMatchers("/api/v1/**").authenticated()
        .anyRequest().permitAll())
    .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
    .csrf(csrf -> csrf
        .csrfTokenRepository(csrfRepository)
        .requireCsrfProtectionMatcher(browserCsrfRequestMatcher)
        .spa())
    .oauth2ResourceServer(oauth -> oauth
        .bearerTokenResolver(cookieOrHeaderBearerTokenResolver)
        .jwt(Customizer.withDefaults())
        .authenticationEntryPoint(apiAuthenticationEntryPoint))
    .exceptionHandling(exceptions -> exceptions
        .authenticationEntryPoint(apiAuthenticationEntryPoint)
        .accessDeniedHandler(apiAccessDeniedHandler));
```

`IF_REQUIRED` is intentional: Google Authorization Code/OIDC needs a temporary `HttpSession` to correlate the authorization request. Do not switch the whole chain to `STATELESS`. At the same time, do **not** configure `BearerTokenAuthenticationFilter` to save JWT authentication into an `HttpSession`; Spring Resource Server's bearer filter is used request-by-request, while CoVie session validity remains in `auth_sessions`.

Place the active-session filter explicitly after bearer authentication:

```java
http.addFilterAfter(activeSessionFilter, BearerTokenAuthenticationFilter.class);
```

The filter ignores non-`Jwt` principals, so the temporary Google OIDC callback authentication is not treated as a CoVie API session.

Do not call `csrf.disable()`.

- [ ] **Step 6: Implement `/api/v1/auth/csrf`**

```java
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {
    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
```

Response is not cached:

```http
Cache-Control: no-store
```

- [ ] **Step 7: Implement active-session validation after JWT authentication**

`ActiveSessionFilter` runs once per request after bearer authentication. For authenticated JWT:

```text
read sessionId claim
parse UUID
query auth_sessions by id where revoked_at is null
reject if absent
reject if expires_at <= now
reject if session.user_id != JWT sub
use `AuthSessionRepository.findUsableById(sessionId)` so the same DB read also requires the referenced user account to remain ACTIVE
update last_used_at only when stored value is at least 5 minutes old; use one conditional UPDATE so normal requests do not write every time
```

For V1 correctness, session existence/status is checked in PostgreSQL on every authenticated request. A later hardening plan may add a short Redis cache with explicit revocation invalidation, but DB remains authoritative.

On failure, clear auth cookies and return the standard API error:

```json
{
  "error": {
    "code": "AUTH_SESSION_INVALID",
    "message": "Authentication session is no longer active.",
    "requestId": "req_..."
  }
}
```

- [ ] **Step 8: Implement `CurrentActorProvider`**

```java
@Component
public final class CurrentActorProvider {
    public CurrentActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(
                    ErrorCode.AUTHENTICATION_REQUIRED,
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required.");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID sessionId = UUID.fromString(jwt.getClaimAsString("sessionId"));
        Set<SystemRole> roles = jwt.getClaimAsStringList("roles").stream()
                .map(SystemRole::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentActor(userId, roles, sessionId);
    }
}
```

Do not add merchant permissions here.

- [ ] **Step 9: Verify security tests**

```bash
cd apps/api
./mvnw -Dtest=AuthSecurityConfigurationTest,ActiveSessionFilterTest test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add apps/api/src/main/java/vn/covie/auth/security apps/api/src/main/java/vn/covie/auth/api/CsrfController.java apps/api/src/main/java/vn/covie/auth/api/dto/CsrfTokenResponse.java apps/api/src/test/java/vn/covie/auth/security
git commit -m "feat(auth): enforce JWT sessions and browser csrf"
```

---
# Task 6: Implement Google OIDC Start/Callback, Existing-User Login and Auth Action Tickets

**Files:**
- Create: `apps/api/src/main/java/vn/covie/auth/oauth/GoogleAuthIntent.java`
- Create: `apps/api/src/main/java/vn/covie/auth/application/GoogleAuthService.java`
- Create: `apps/api/src/main/java/vn/covie/auth/application/InitialSessionIssuer.java`
- Create: `apps/api/src/main/java/vn/covie/auth/application/IssuedSession.java`
- Create: `apps/api/src/main/java/vn/covie/auth/model/DeviceMetadata.java`
- Create: `apps/api/src/main/java/vn/covie/auth/oauth/GoogleOAuthSuccessHandler.java`
- Create: `apps/api/src/main/java/vn/covie/auth/oauth/GoogleOAuthFailureHandler.java`
- Create: `apps/api/src/main/java/vn/covie/auth/api/GoogleAuthController.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `.env.example`
- Test: `apps/api/src/test/java/vn/covie/auth/application/GoogleAuthServiceTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/oauth/GoogleOAuthSuccessHandlerTest.java`

**Produces:** one Google entry point that either logs an existing CoVie user in, creates an onboarding action ticket for a new user, or creates a password-recovery ticket when recovery was explicitly requested.

- [ ] **Step 1: Add Google client configuration**

`application.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            provider: google
            client-id: ${GOOGLE_OAUTH_CLIENT_ID:disabled-local-client}
            client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET:disabled-local-secret}
            authorization-grant-type: authorization_code
            scope: openid,profile,email
            redirect-uri: ${GOOGLE_OAUTH_REDIRECT_URI:http://localhost:3000/api/v1/auth/google/callback/google}
```

`.env.example`:

```dotenv
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
GOOGLE_OAUTH_REDIRECT_URI=http://localhost:3000/api/v1/auth/google/callback/google
```

Do not place real values in the file. The non-secret `disabled-local-*` defaults exist only so tests/context startup do not require real Google credentials; Plan 10 production startup validation must reject those defaults when the production profile is active. `GOOGLE_OAUTH_REDIRECT_URI` is explicit rather than inferred from proxy `Host` headers: local browser flow uses `http://localhost:3000/api/v1/auth/google/callback/google`; production uses `https://covie.vn/api/v1/auth/google/callback/google`. Test profile supplies its own deterministic value.

- [ ] **Step 2: Define explicit intent**

```java
public enum GoogleAuthIntent {
    LOGIN,
    PASSWORD_RESET
}
```

`GET /api/v1/auth/google/start` accepts optional query parameter `intent`, default `LOGIN`.

- [ ] **Step 3: Implement the public start controller**

```java
@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {

    public static final String SESSION_INTENT = "covie.google.auth.intent";

    @GetMapping("/start")
    public void start(
            @RequestParam(defaultValue = "LOGIN") GoogleAuthIntent intent,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        request.getSession(true).setAttribute(SESSION_INTENT, intent.name());
        response.sendRedirect("/api/v1/auth/oauth2/authorization/google");
    }
}
```

The OAuth `state` mechanism remains Spring Security’s responsibility. The CoVie intent is server-side handshake state and is never trusted from the callback query string. Keeping the Spring authorization endpoint under `/api/v1` is mandatory because Plan 01 development rewrites and production ingress route `/api/v1/*` to Spring Boot; do not restore the default root-level `/oauth2/authorization/*` path.

- [ ] **Step 4: Configure Google redirection endpoint and success/failure handlers**

Extend `AuthSecurityConfiguration`:

```java
.oauth2Login(oauth -> oauth
    .authorizationEndpoint(authorization -> authorization
        .baseUri("/api/v1/auth/oauth2/authorization"))
    .redirectionEndpoint(redirection -> redirection
        .baseUri("/api/v1/auth/google/callback/*"))
    .successHandler(googleOAuthSuccessHandler)
    .failureHandler(googleOAuthFailureHandler))
```

Do not use Google access tokens for CoVie authorization. Google is only an identity provider here.

- [ ] **Step 5: Write `GoogleAuthServiceTest` for canonical identity behavior**

Required cases:

```text
existing GOOGLE subject -> existing user login result
new GOOGLE subject + verified email -> ONBOARDING ticket result
new GOOGLE subject + email_verified=false -> reject
GOOGLE subject absent -> reject
existing EMAIL with same email but different/no GOOGLE subject -> IDENTITY_LINK_CONFLICT, never auto-link by email
PASSWORD_RESET + known GOOGLE subject -> PASSWORD_RESET ticket result
PASSWORD_RESET + unknown GOOGLE subject -> PASSWORD_RECOVERY_NOT_AVAILABLE
suspended/banned/deleted user -> no authenticated session
```

The test proving no email-based auto-link is mandatory.

- [ ] **Step 6: Implement OIDC claim extraction**

Success handler reads only validated `OidcUser` claims supplied by Spring Security:

```java
String subject = oidcUser.getSubject();
String email = oidcUser.getEmail();
Boolean emailVerified = oidcUser.getClaim("email_verified");
String displayName = oidcUser.getFullName();
```

Validation:

```text
sub: required and non-blank
email: required for CoVie V1 onboarding
email_verified: must equal true for onboarding/recovery
name: optional; fallback to normalized email local part for initial display name
```

- [ ] **Step 7: Implement action ticket creation**

`GoogleAuthService` application methods that write identity metadata, action tickets, sessions or audit rows use short database transactions **after** Spring Security has already validated the OIDC callback. Do not hold a database transaction open across the redirect to Google or Google token exchange.

`GoogleAuthService` uses `OpaqueTokenGenerator` and stores only its fingerprint:

```java
public record IssuedAuthActionTicket(
        String rawToken,
        AuthActionPurpose purpose,
        Instant expiresAt,
        UUID userId) {}
```

Creation algorithm:

```text
generate 32 random bytes
fingerprint SHA-256
persist auth_action_tickets row
TTL = 10 minutes
never reuse an unconsumed ticket when a fresh Google callback occurs
```

For `ONBOARDING`, `user_id` is null. For `PASSWORD_RESET`, `user_id` is the resolved existing user.

- [ ] **Step 8: Implement existing-Google-user login branch**

For `LOGIN` intent and an existing GOOGLE identity:

```text
resolve by GOOGLE subject, never by email
optionally refresh GOOGLE-row email/display metadata from verified claims
do not silently rename/re-key the local EMAIL login identity if Google email changed
load user
require ACTIVE
update identity.last_used_at
create auth session + initial refresh token through InitialSessionIssuer
issue access JWT
write access + refresh cookies
invalidate old CSRF token cookie so frontend must fetch a fresh one
security audit GOOGLE_LOGIN
redirect browser to /
```

`InitialSessionIssuer` is implemented in this task and is the single real path for creating a new auth session plus its first refresh token. It loads global roles, persists `auth_sessions` + the first `auth_refresh_tokens` row, and returns `IssuedSession`. Task 7/8/10 reuse it; do not introduce a production no-op.

- [ ] **Step 9: Implement `InitialSessionIssuer` and device metadata**

`DeviceMetadata` is a bounded record:

```java
public record DeviceMetadata(String deviceName, String userAgent) {
    public static DeviceMetadata from(HttpServletRequest request) {
        String ua = Optional.ofNullable(request.getHeader("User-Agent")).orElse("Unknown");
        String boundedUa = ua.length() <= 500 ? ua : ua.substring(0, 500);
        String platform = Optional.ofNullable(request.getHeader("Sec-CH-UA-Platform")).orElse("Browser");
        String device = platform.replace("\"", "");
        if (device.length() > 120) device = device.substring(0, 120);
        return new DeviceMetadata(device, boundedUa);
    }
}
```

Create the shared output type in `IssuedSession.java`:

```java
public record IssuedSession(
        UUID sessionId,
        String rawRefreshToken,
        Instant refreshExpiresAt,
        IssuedAccessToken accessToken) {}
```

`InitialSessionIssuer.issue(userId, DeviceMetadata)` creates a fresh `auth_sessions` row with a new UUIDv7 session ID and random token-family UUID, creates the first 256-bit refresh token row using only its SHA-256 fingerprint, loads `SystemRole` assignments, and issues an access JWT. V1 uses an **absolute 30-day auth-session lifetime**: `auth_sessions.expires_at = login_time + 30 days`, and every refresh token in that family must expire no later than that same session expiry. Refreshing does not extend the session indefinitely.

- [ ] **Step 10: Implement new-user onboarding branch**

For a new subject:

```text
ensure no local EMAIL identity collision exists
create ONBOARDING action ticket
write covie_auth_action HttpOnly cookie
redirect /onboarding/password
```

Do **not** create a `users` row at callback time.

- [ ] **Step 11: Implement password-reset intent branch**

For `PASSWORD_RESET`:

```text
require existing GOOGLE identity
require user ACTIVE
create PASSWORD_RESET action ticket bound to user_id + google_subject
write action cookie
redirect /reset-password
```

Do not create login access/refresh cookies yet; the password reset command creates the new post-reset session.

- [ ] **Step 12: Invalidate temporary HTTP session after OAuth completes**

Success and failure handlers must remove the intent and invalidate the temporary handshake session after extracting necessary state. CoVie must not rely on that `HttpSession` for authenticated API requests.

- [ ] **Step 13: Implement generic OAuth failure behavior**

`GoogleOAuthFailureHandler` must:

```text
remove CoVie Google intent from temporary session
invalidate the temporary OAuth HttpSession
never expose Google exception text, authorization code or token in the redirect
record GOOGLE_LOGIN_FAILED through AuditLogWriter.securityFailure with requestId and a coarse failure category
redirect to /login?error=google_auth_failed
```

There is no client-provided return URL in V1, so the handler must not implement an arbitrary redirect target.

- [ ] **Step 14: Verify handlers**

```bash
cd apps/api
./mvnw -Dtest=GoogleAuthServiceTest,GoogleOAuthSuccessHandlerTest test
```

Expected: PASS.

- [ ] **Step 15: Commit**

```bash
git add apps/api/src/main/java/vn/covie/auth/oauth apps/api/src/main/java/vn/covie/auth/application/GoogleAuthService.java apps/api/src/main/java/vn/covie/auth/application/InitialSessionIssuer.java apps/api/src/main/java/vn/covie/auth/application/IssuedSession.java apps/api/src/main/java/vn/covie/auth/model/DeviceMetadata.java apps/api/src/main/java/vn/covie/auth/api/GoogleAuthController.java apps/api/src/main/java/vn/covie/auth/security/AuthSecurityConfiguration.java apps/api/src/main/resources/application.yml .env.example apps/api/src/test/java/vn/covie/auth/application/GoogleAuthServiceTest.java apps/api/src/test/java/vn/covie/auth/oauth/GoogleOAuthSuccessHandlerTest.java
git commit -m "feat(auth): add Google identity entry flow"
```

---

# Task 7: Complete First Registration Through Password Onboarding

**Files:**
- Create: `apps/api/src/main/java/vn/covie/auth/api/AuthController.java`
- Create: `apps/api/src/main/java/vn/covie/auth/api/dto/SetOnboardingPasswordRequest.java`
- Create: `apps/api/src/main/java/vn/covie/auth/api/dto/AuthSessionResponse.java`
- Create: `apps/api/src/main/java/vn/covie/auth/application/AuthApplicationService.java`
- Test: `apps/api/src/test/java/vn/covie/auth/application/AuthOnboardingIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/api/AuthOnboardingControllerTest.java`

**Produces:** atomic `Google -> password -> User + GOOGLE identity + EMAIL identity + USER role + session` registration.

- [ ] **Step 1: Define onboarding request**

```java
public record SetOnboardingPasswordRequest(
        @NotBlank
        @Size(min = 10, max = 200)
        String password) {}
```

Never accept email, Google subject, user ID or provider from this request. Those values come from the server-side action ticket.

- [ ] **Step 2: Write the full onboarding integration test first**

Arrange an unconsumed ONBOARDING ticket and raw action cookie. Call:

```http
POST /api/v1/auth/onboarding/set-password
X-CSRF-Token: ...
Cookie: covie_auth_action=...
Content-Type: application/json

{"password":"correct horse battery staple"}
```

Assert in one test:

```text
201/200 success response
one users row ACTIVE
one GOOGLE identity with original Google sub
one EMAIL identity with same normalized email and Argon2id hash
one USER system role assignment
one auth_sessions row
one current auth_refresh_tokens row
action ticket consumed_at set
access + refresh cookies issued
action cookie cleared
security audit event recorded
raw password/token are absent from audit rows
```

- [ ] **Step 3: Run RED**

```bash
cd apps/api
./mvnw -Dtest=AuthOnboardingIntegrationTest test
```

Expected: FAIL because onboarding service/controller is missing.

- [ ] **Step 4: Implement action ticket lookup/consume with row lock**

Repository method:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select t from AuthActionTicketEntity t where t.tokenHash = :hash")
Optional<AuthActionTicketEntity> findByTokenHashForUpdate(@Param("hash") String hash);
```

Validation order:

```text
ticket exists
purpose == ONBOARDING
consumed_at == null
expires_at > now
google_email_verified == true
Google subject still not registered
local EMAIL still not registered
```

If the uniqueness state changed after ticket creation, reject with `IDENTITY_LINK_CONFLICT` instead of creating a second account.

- [ ] **Step 5: Implement one transaction**

`AuthApplicationService.completeOnboarding(...)` is `@Transactional` and performs:

```text
lock ticket
validate ticket
hash password
create users row
create GOOGLE identity and flush it so a provider-subject uniqueness race is detected inside the transaction
create EMAIL identity and flush it so a local-email uniqueness race is detected inside the transaction
create USER role assignment
mark ticket consumed
create auth session
create initial refresh token
write audit log intent in same transaction
return raw refresh token + session result to controller
```

Cookie writes occur only after the transaction returns successfully. Because GOOGLE identity and local EMAIL inserts use `saveAndFlush`, a uniqueness race is raised before the transactional method returns. Catch that `DataIntegrityViolationException` only to translate it into `ApiException(IDENTITY_LINK_CONFLICT, 409, ...)` and immediately rethrow; the whole transaction must roll back. Never catch and continue after a uniqueness failure.

- [ ] **Step 6: Reuse the shared initial-session output**

Reuse `IssuedSession` and `InitialSessionIssuer` created in Task 6. Do not redefine either type in the onboarding task. Google existing-user login, Email login, onboarding completion and password reset must all share this session-issuance path.

- [ ] **Step 7: Add duplicate-submit protection through ticket single-use**

A second call with the same action ticket after successful onboarding must return:

```http
409 Conflict
```

with:

```text
AUTH_ACTION_ALREADY_CONSUMED
```

It must not create a second `users` row. Also add an integration case with two distinct ONBOARDING tickets carrying the same Google `sub`: database uniqueness on `(provider, provider_subject)` is the final guard, and the losing request must be translated to `409 IDENTITY_LINK_CONFLICT` with its transaction rolled back completely. Never leave an orphan `users` row.

- [ ] **Step 8: Implement controller and cookie response**

```java
@PostMapping("/onboarding/set-password")
public ResponseEntity<AuthSessionResponse> setOnboardingPassword(
        @CookieValue(name = "covie_auth_action") String rawTicket,
        @Valid @RequestBody SetOnboardingPasswordRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse response) {
    IssuedSession issued = service.completeOnboarding(rawTicket, request.password(), requestId.current());
    cookies.writeAccessCookie(response, issued.accessToken().value(), issued.accessToken().expiresAt());
    cookies.writeRefreshCookie(response, issued.rawRefreshToken(), issued.refreshExpiresAt());
    cookies.clearActionTicketCookie(response);
    csrfLifecycle.invalidate(servletRequest, response);
    return ResponseEntity.ok(AuthSessionResponse.from(issued));
}
```

The response does not contain raw access or refresh token for browser V1.

- [ ] **Step 9: Verify controller/security cases**

Required:

```text
missing CSRF -> 403
missing action cookie -> 401/422 AUTH_ACTION_REQUIRED
expired ticket -> 401 AUTH_ACTION_EXPIRED
wrong ticket purpose -> 409 AUTH_ACTION_PURPOSE_MISMATCH
weak/short password -> 422 VALIDATION_FAILED
successful onboarding -> authenticated cookies
```

Run:

```bash
cd apps/api
./mvnw -Dtest=AuthOnboardingIntegrationTest,AuthOnboardingControllerTest test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add apps/api/src/main/java/vn/covie/auth/api apps/api/src/main/java/vn/covie/auth/application/AuthApplicationService.java apps/api/src/test/java/vn/covie/auth/application/AuthOnboardingIntegrationTest.java apps/api/src/test/java/vn/covie/auth/api/AuthOnboardingControllerTest.java
git commit -m "feat(auth): complete Google password onboarding"
```

---

# Task 8: Implement Email/Password Login Without Account Enumeration

**Files:**
- Create: `apps/api/src/main/java/vn/covie/auth/application/EmailPasswordLoginService.java`
- Create: `apps/api/src/main/java/vn/covie/auth/api/dto/EmailPasswordLoginRequest.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/api/AuthController.java`
- Test: `apps/api/src/test/java/vn/covie/auth/application/EmailPasswordLoginServiceTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/api/EmailPasswordLoginControllerTest.java`

**Produces:** local login for users who completed Google onboarding.

- [ ] **Step 1: Define request**

```java
public record EmailPasswordLoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 200) String password) {}
```

- [ ] **Step 2: Write enumeration-safe tests**

Both cases must return the same public error code/status/message shape:

```text
unknown email
known email + wrong password
```

Expected:

```http
401 Unauthorized
```

```json
{
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid email or password.",
    "requestId": "req_..."
  }
}
```

Do not return `EMAIL_NOT_FOUND`.

- [ ] **Step 3: Implement authentication algorithm**

```text
normalize email
lookup provider=EMAIL
if absent -> execute bounded dummy Argon2 match and return INVALID_CREDENTIALS
if present -> passwordEncoder.matches
if false -> INVALID_CREDENTIALS
load user
if ACTIVE -> continue
if SUSPENDED/BANNED/DELETED after valid password -> ACCOUNT_NOT_ACTIVE
update identity last_used_at
load global roles
create new independent auth session + refresh token
issue JWT
security audit LOGIN_SUCCESS
```

A dummy hash is generated at application startup once, not per failed login.

- [ ] **Step 4: Keep multi-device semantics**

Successful login does not revoke existing sessions. A laptop and phone/browser may remain active independently.

- [ ] **Step 5: Implement controller**

```java
@PostMapping("/login")
public AuthSessionResponse login(
        @Valid @RequestBody EmailPasswordLoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse response) {
    IssuedSession issued = loginService.login(
            request.email(), request.password(),
            DeviceMetadata.from(servletRequest), requestId.current());
    cookies.writeAccessCookie(response, issued.accessToken().value(), issued.accessToken().expiresAt());
    cookies.writeRefreshCookie(response, issued.rawRefreshToken(), issued.refreshExpiresAt());
    return AuthSessionResponse.from(issued);
}
```

Login remains CSRF-protected for browser clients. After cookies are issued, call `csrfLifecycle.invalidate(servletRequest, response)` so the pre-login CSRF token cannot be reused as the post-login token.

- [ ] **Step 6: Audit failure without leaking secrets and without rollback loss**

Call `AuditLogWriter.securityFailure(...)` before returning/throwing `INVALID_CREDENTIALS`; it runs in `REQUIRES_NEW`, so the audit row survives the failed login path. Record `LOGIN_FAILED` metadata only with:

```text
requestId
coarse reason category INVALID_CREDENTIALS
optional rate-limit key reference only after Plan 10 introduces a keyed/HMAC email fingerprint
```

Do not put clear email or submitted password in the generic security audit row. Plan 10 may add a keyed/HMAC email fingerprint for rate-limit correlation without making a plain SHA-256 dictionary target.

- [ ] **Step 7: Verify**

```bash
cd apps/api
./mvnw -Dtest=EmailPasswordLoginServiceTest,EmailPasswordLoginControllerTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/auth/application/EmailPasswordLoginService.java apps/api/src/main/java/vn/covie/auth/api apps/api/src/test/java/vn/covie/auth/application/EmailPasswordLoginServiceTest.java apps/api/src/test/java/vn/covie/auth/api/EmailPasswordLoginControllerTest.java
git commit -m "feat(auth): add email password login"
```

---

# Task 9: Implement Refresh Rotation, Strict Reuse Detection, Logout and Multi-Device Session Management

**Files:**
- Create: `apps/api/src/main/java/vn/covie/auth/application/RefreshTokenService.java`
- Create: `apps/api/src/main/java/vn/covie/auth/application/RefreshRotationTransaction.java`
- Create: `apps/api/src/main/java/vn/covie/auth/application/RefreshRotationOutcome.java`
- Create: `apps/api/src/main/java/vn/covie/auth/application/SessionManagementService.java`
- Create: `apps/api/src/main/java/vn/covie/auth/api/AuthSessionController.java`
- Create: DTOs under `apps/api/src/main/java/vn/covie/auth/api/dto/`
- Modify: `apps/api/src/main/java/vn/covie/auth/api/AuthController.java`
- Test: `apps/api/src/test/java/vn/covie/auth/application/RefreshTokenServiceTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/application/RefreshTokenConcurrencyTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/api/AuthSessionControllerTest.java`

**Produces:** strict rotating refresh-token families bounded by an absolute 30-day auth-session lifetime, plus user-visible session control.

- [ ] **Step 1: Write rotation test**

Arrange one active session with refresh token `R1`. Refresh once and assert:

```text
R1.used_at set
R1.replaced_by_token_id points to R2 row
R2 raw value differs from R1
R2 hash stored, raw value absent from DB
same auth session id retained
new access JWT references same sessionId
session last_used_at advances
R2.expires_at == auth_sessions.expires_at
auth_sessions.expires_at does not move during refresh
```

- [ ] **Step 2: Write strict reuse test**

Present `R1` again after rotation:

```text
request fails with REFRESH_TOKEN_REUSE_DETECTED
session.revoked_at set
session.revocation_reason = REFRESH_REUSE
R2.revoked_at set
all other active refresh rows in same session revoked
security audit REFRESH_TOKEN_REUSE_DETECTED recorded
```

No new access token is issued.

- [ ] **Step 3: Implement refresh transaction with row locking and committed reuse revocation**

Do **not** revoke and then throw a normal runtime exception from the same `@Transactional` method, because the exception would roll back the revocation. Split transaction outcome from HTTP error mapping.

```java
public sealed interface RefreshRotationOutcome {
    record Rotated(IssuedSession session) implements RefreshRotationOutcome {}
    record ReuseDetected(UUID sessionId) implements RefreshRotationOutcome {}
}
```

Use a private helper value for the successor:

```java
private record NewRefresh(UUID id, String rawToken, Instant expiresAt) {}
```

`createSuccessor(session, now)` generates a new 256-bit raw token, stores only its fingerprint, and sets `expiresAt = session.getExpiresAt()`. Rotation **does not slide** the 30-day session lifetime.

```java
@Service
public class RefreshRotationTransaction {
    @Transactional
    public RefreshRotationOutcome rotate(String rawRefreshToken, String requestId) {
        String hash = fingerprint.sha256Hex(rawRefreshToken);
        AuthRefreshTokenEntity candidate = tokens.findByTokenHash(hash)
                .orElseThrow(invalidRefresh());
        AuthSessionEntity session = sessions.findByIdForUpdate(candidate.getSessionId())
                .orElseThrow(invalidRefresh());
        AuthRefreshTokenEntity token = tokens.findByIdForUpdate(candidate.getId())
                .orElseThrow(invalidRefresh());

        if (token.isUsedOrReplaced()) {
            revokeFamily(session, REFRESH_REUSE, requestId);
            return new RefreshRotationOutcome.ReuseDetected(session.getId());
        }
        requireActiveAndUnexpired(token, session);
        token.markUsed(now);
        NewRefresh successor = createSuccessor(session, now);
        token.replaceWith(successor.id());
        session.markUsed(now);
        return new RefreshRotationOutcome.Rotated(issueSameSession(session, successor.rawToken()));
    }
}
```

The outer, non-transactional `RefreshTokenService.refresh(...)` calls `rotate(...)`. If it receives `ReuseDetected`, the revocation transaction has already committed; only then does it throw the API exception `REFRESH_TOKEN_REUSE_DETECTED`. Lock order is always **session row first, refresh-token row second** after the initial immutable hash lookup. Session revoke, revoke-others and password reset must follow the same order.

- [ ] **Step 4: Define strict concurrency behavior**

Two simultaneous uses of the exact same R1 must result in:

```text
first transaction rotates R1 -> R2
second transaction observes R1 used -> revokes session family
```

This strict policy intentionally treats concurrent duplicate refresh as possible theft. Frontend must therefore single-flight refresh requests.

Test with two threads and a barrier against real PostgreSQL/Testcontainers; do not mock this test.

- [ ] **Step 5: Implement `/api/v1/auth/refresh`**

Reads `covie_rt` HttpOnly cookie, requires valid CSRF for browser flow, rotates, rewrites both cookies, then invalidates the old CSRF token so the frontend obtains a fresh token.

Errors clear access/refresh cookies when the refresh credential is invalid/reused/expired.

- [ ] **Step 6: Implement logout**

`POST /api/v1/auth/logout` behavior:

```text
if access session is known -> revoke it LOGOUT
else if refresh cookie resolves -> revoke its session LOGOUT
clear access, refresh and action cookies regardless
invalidate CSRF token cookie
return 204
```

Logout is idempotent: already-revoked/missing session still returns 204 after clearing cookies.

- [ ] **Step 7: Implement session list**

```http
GET /api/v1/me/security/sessions
```

Return active, non-expired sessions only, ordered by `last_used_at DESC`; historical revoked rows remain in PostgreSQL for audit/retention but are not part of the normal device list.

Response item:

```java
public record AuthSessionSummaryResponse(
        UUID id,
        String deviceName,
        boolean current,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt) {}
```

Do not return refresh-token hashes, token family IDs or full IP metadata.

- [ ] **Step 8: Implement revoke-one and revoke-others**

```http
DELETE /api/v1/me/security/sessions/{sessionId}
POST   /api/v1/me/security/sessions/revoke-others
```

`SessionManagementService` also exposes:

```java
@Transactional(readOnly = true)
public AuthSessionEntity requireActiveSession(UUID sessionId)
```

Later plans may use this method for explicit session-aware application checks; request authentication itself remains enforced by `ActiveSessionFilter`.

Rules:

```text
session must belong to current user or return 404
revoking another session does not revoke current
revoke-others preserves current session
revoked session becomes invalid on its next API request because ActiveSessionFilter checks PostgreSQL
```

Revoking any session first locks that session row, then locks its refresh-token rows in deterministic order, and marks every unrevoked token under that session revoked in the same transaction. `revoke-others` locks target sessions ordered by UUID and applies the same session-first/token-second order. If user deletes the current session, clear current browser auth cookies.

- [ ] **Step 9: Verify concurrency and API**

```bash
cd apps/api
./mvnw -Dtest=RefreshTokenServiceTest,RefreshTokenConcurrencyTest,AuthSessionControllerTest test
```

Expected: PASS; concurrency test proves only one initial rotation occurs and duplicate reuse revokes the family.

- [ ] **Step 10: Commit**

```bash
git add apps/api/src/main/java/vn/covie/auth/application/RefreshTokenService.java apps/api/src/main/java/vn/covie/auth/application/RefreshRotationTransaction.java apps/api/src/main/java/vn/covie/auth/application/RefreshRotationOutcome.java apps/api/src/main/java/vn/covie/auth/application/SessionManagementService.java apps/api/src/main/java/vn/covie/auth/api apps/api/src/test/java/vn/covie/auth/application/RefreshTokenServiceTest.java apps/api/src/test/java/vn/covie/auth/application/RefreshTokenConcurrencyTest.java apps/api/src/test/java/vn/covie/auth/api/AuthSessionControllerTest.java
git commit -m "feat(auth): rotate refresh tokens with reuse detection"
```

---

# Task 10: Implement Google-Verified Password Recovery and Global Session Revocation

**Files:**
- Create: `apps/api/src/main/java/vn/covie/auth/application/PasswordRecoveryService.java`
- Create: `apps/api/src/main/java/vn/covie/auth/api/dto/ResetPasswordWithGoogleRequest.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/api/AuthController.java`
- Test: `apps/api/src/test/java/vn/covie/auth/application/PasswordRecoveryIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/api/PasswordRecoveryControllerTest.java`

**Produces:** the V1 “forgot password” flow with Google as recovery root.

- [ ] **Step 1: Write full recovery integration test**

Arrange:

```text
existing ACTIVE user
GOOGLE identity subject G
EMAIL identity with old password hash
session S1 browser A
session S2 browser B
fresh PASSWORD_RESET action ticket bound to user + G
```

Call reset with new password and assert:

```text
EMAIL identity password hash changes
old password no longer matches
new password matches
PASSWORD_RESET ticket consumed
S1 and S2 revoked with PASSWORD_RESET
all old refresh tokens revoked
new S3 created
new access + refresh cookies issued
old access JWT for S1 rejected on next API request
PASSWORD_RESET audit event exists
```

- [ ] **Step 2: Define request**

```java
public record ResetPasswordWithGoogleRequest(
        @NotBlank @Size(min = 10, max = 200) String newPassword) {}
```

No email/userId/googleSub in request.

- [ ] **Step 3: Implement one transaction**

```text
lock PASSWORD_RESET action ticket by hash
require unconsumed + unexpired + user_id present
load GOOGLE identity and verify provider_subject still matches ticket.google_subject
load EMAIL identity for same user
lock all active auth_sessions for user in deterministic id order
for each locked session, lock its refresh-token rows in createdAt/id order
encode new password
update EMAIL password hash
revoke all locked sessions PASSWORD_RESET
revoke all locked refresh rows under those sessions
consume action ticket
create new session + initial refresh token
security audit PASSWORD_RESET
```

The new session is created **after** old-session revocation within the same transaction so `revokeAllActive()` cannot accidentally revoke the newly created session.

- [ ] **Step 4: Enforce fresh Google action-ticket TTL**

A recovery ticket older than 10 minutes is rejected even if still present in the browser cookie. No CoVie access token alone can authorize password reset.

- [ ] **Step 5: Implement controller**

```http
POST /api/v1/auth/password/reset-with-google
```

Requires action cookie + CSRF. On success:

```text
clear action cookie
replace access cookie
replace refresh cookie
invalidate old CSRF token cookie
return AuthSessionResponse
```

- [ ] **Step 6: Verify negative cases**

```text
ONBOARDING ticket used on reset endpoint -> 409
expired ticket -> 401
Google subject no longer linked -> 409
EMAIL identity unexpectedly absent -> 409 recovery unavailable
reused consumed ticket -> 409
```

Run:

```bash
cd apps/api
./mvnw -Dtest=PasswordRecoveryIntegrationTest,PasswordRecoveryControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/vn/covie/auth/application/PasswordRecoveryService.java apps/api/src/main/java/vn/covie/auth/api apps/api/src/test/java/vn/covie/auth/application/PasswordRecoveryIntegrationTest.java apps/api/src/test/java/vn/covie/auth/api/PasswordRecoveryControllerTest.java
git commit -m "feat(auth): reset password through Google recovery"
```

---
# Task 11: Implement `/me` Profile and User Contact Methods

**Files:**
- Create: `apps/api/src/main/java/vn/covie/users/application/UserProfileService.java`
- Create: `apps/api/src/main/java/vn/covie/users/application/UserContactMethodService.java`
- Create: `apps/api/src/main/java/vn/covie/users/api/MeController.java`
- Create: `apps/api/src/main/java/vn/covie/users/api/UserContactMethodController.java`
- Create: DTOs under `apps/api/src/main/java/vn/covie/users/api/dto/`
- Test: `apps/api/src/test/java/vn/covie/users/api/MeControllerTest.java`
- Test: `apps/api/src/test/java/vn/covie/users/application/UserContactMethodServiceTest.java`

**Produces:** stable authenticated personal account read model and external contact-method management for later order/service flows.

- [ ] **Step 1: Define `MeResponse`**

```java
public record MeResponse(
        UUID id,
        String displayName,
        String email,
        String accountStatus,
        long version,
        List<String> globalRoles) {}
```

`email` is resolved from the user’s EMAIL identity. Do not expose password hash, Google subject, refresh/session internals or admin-only audit data.

- [ ] **Step 2: Write authorization test**

```text
anonymous GET /api/v1/me -> 401
authenticated ACTIVE user -> 200 own account only
revoked session JWT -> 401 before controller
```

- [ ] **Step 3: Implement profile read/update**

Endpoints:

```http
GET   /api/v1/me
PATCH /api/v1/me
```

Update request:

```java
public record UpdateMeRequest(
        @NotBlank @Size(max = 120) String displayName) {}
```

Service obtains `userId` only from `CurrentActorProvider`; request has no user ID.

Update rules:

```text
trim display name
reject blank after trim
persist updated_at from AppClock
keep account_status unchanged
never update email through PATCH /me
```

Email/password identity changes belong to auth/security use cases, not profile patch.

- [ ] **Step 4: Define contact API**

```http
GET    /api/v1/me/contact-methods
POST   /api/v1/me/contact-methods
PATCH  /api/v1/me/contact-methods/{contactMethodId}
DELETE /api/v1/me/contact-methods/{contactMethodId}
```

Create request:

```java
public record CreateContactMethodRequest(
        @NotNull ContactType type,
        @NotBlank @Size(max = 500) String value,
        boolean primary) {}
```

Response:

```java
public record ContactMethodResponse(
        UUID id,
        ContactType type,
        String value,
        boolean primary,
        boolean verified,
        String status,
        Instant createdAt) {}
```

- [ ] **Step 5: Implement primary-contact transaction**

When creating/updating `primary=true`:

```text
load current user's active contacts of same type
set previous primary false
flush if necessary
set target primary true
commit
```

This ordering respects the partial unique index.

`is_verified` cannot be set true by the client in V1. PHONE/ZALO/MESSENGER contacts created by users remain unverified unless a later verification feature exists.

- [ ] **Step 6: Enforce ownership**

Contact lookup is always:

```text
contactMethodId + current userId
```

If another user owns the ID, return 404 rather than 403.

- [ ] **Step 7: Soft-deactivate on DELETE**

DELETE sets:

```text
status = INACTIVE
is_primary = false
```

and returns 204. Historical Orders later rely on order snapshots rather than live contact rows.

- [ ] **Step 8: Run tests**

```bash
cd apps/api
./mvnw -Dtest=MeControllerTest,UserContactMethodServiceTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/api/src/main/java/vn/covie/users/application apps/api/src/main/java/vn/covie/users/api apps/api/src/test/java/vn/covie/users
git commit -m "feat(users): add profile and contact management"
```

---

# Task 12: Implement Saved Addresses on PostGIS Geography

**Files:**
- Create: `apps/api/src/main/java/vn/covie/users/application/UserAddressService.java`
- Create: `apps/api/src/main/java/vn/covie/users/api/UserAddressController.java`
- Create: `apps/api/src/main/java/vn/covie/users/api/dto/CreateUserAddressRequest.java`
- Create: `apps/api/src/main/java/vn/covie/users/api/dto/UpdateUserAddressRequest.java`
- Create: `apps/api/src/main/java/vn/covie/users/api/dto/UserAddressResponse.java`
- Test: `apps/api/src/test/java/vn/covie/users/application/UserAddressServiceTest.java`
- Test: `apps/api/src/test/java/vn/covie/users/persistence/UserAddressPostgisIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/users/api/UserAddressControllerTest.java`

**Produces:** authenticated saved addresses that Plan 03 discovery and Plan 04 checkout can reference by `addressId`.

- [ ] **Step 1: Define address create request without premature LocationArea coupling**

```java
public record CreateUserAddressRequest(
        @NotBlank @Size(max = 80) String label,
        @Size(max = 120) String recipientName,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 1000) String addressText,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull PinSource pinSource,
        @Size(max = 1000) String note,
        boolean defaultAddress) {}
```

Do not accept `locationAreaId` in Plan 02. Plan 03 adds area resolution and the FK when `location_areas` exists.

- [ ] **Step 2: Write PostGIS persistence test**

Persist:

```text
latitude=10.8412
longitude=106.8374
```

Read it back and assert:

```text
point.x == 106.8374  # longitude
point.y == 10.8412   # latitude
SRID == 4326
```

Also run native SQL:

```sql
select ST_X(geo_point::geometry), ST_Y(geo_point::geometry)
from user_addresses
where id = ?;
```

This test catches accidental coordinate reversal.

- [ ] **Step 3: Implement address CRUD**

```http
GET    /api/v1/me/addresses
POST   /api/v1/me/addresses
GET    /api/v1/me/addresses/{addressId}
PATCH  /api/v1/me/addresses/{addressId}
DELETE /api/v1/me/addresses/{addressId}
```

Every repository query is scoped to current `userId`.

- [ ] **Step 4: Implement default-address invariant**

Creating/updating an address with `defaultAddress=true` runs in one transaction:

```text
find existing ACTIVE default for user
set old default false
flush
set target default true
```

If the user creates their first address with `defaultAddress=false`, service automatically marks it default. This avoids a user with addresses but no default.

- [ ] **Step 5: Implement soft delete**

DELETE:

```text
status = DELETED
deleted_at = now
is_default = false
```

If the deleted address was default and other active addresses remain, promote the most recently updated active address to default in the same transaction.

- [ ] **Step 6: Define response**

```java
public record UserAddressResponse(
        UUID id,
        String label,
        String recipientName,
        String phone,
        String addressText,
        Double latitude,
        Double longitude,
        PinSource pinSource,
        String note,
        boolean defaultAddress,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
```

`locationAreaId` is not exposed until Plan 03 can resolve it consistently.

- [ ] **Step 7: Verify ownership/validation**

Required tests:

```text
other user's address ID -> 404
latitude 91 -> 422
longitude 181 -> 422
first address becomes default
default switch leaves exactly one active default
soft delete of default chooses replacement
```

Run:

```bash
cd apps/api
./mvnw -Dtest=UserAddressServiceTest,UserAddressPostgisIntegrationTest,UserAddressControllerTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/java/vn/covie/users/application/UserAddressService.java apps/api/src/main/java/vn/covie/users/api/UserAddressController.java apps/api/src/main/java/vn/covie/users/api/dto apps/api/src/test/java/vn/covie/users
git commit -m "feat(users): add postgis saved addresses"
```

---

# Task 13: Implement Minimal Next.js Auth UX and CSRF-Aware Browser Client

**Files:**
- Modify: `apps/web/lib/api/api-client.ts`
- Modify: `apps/web/lib/api/api-error.ts`
- Create: `apps/web/features/auth/csrf.ts`
- Create: `apps/web/features/auth/auth-api.ts`
- Create: `apps/web/features/auth/auth-session.ts`
- Create: `apps/web/features/auth/login-form.tsx`
- Create: `apps/web/features/auth/google-login-button.tsx`
- Create: `apps/web/features/auth/password-form.tsx`
- Create: `apps/web/app/(auth)/login/page.tsx`
- Create: `apps/web/app/(auth)/onboarding/password/page.tsx`
- Create: `apps/web/app/(auth)/reset-password/page.tsx`
- Test: `apps/web/tests/auth/csrf.test.ts`
- Test: `apps/web/tests/auth/login-form.test.tsx`

**Produces:** a functional browser authentication path that never reads HttpOnly access/refresh cookies.

- [ ] **Step 1: Implement CSRF token loader**

`features/auth/csrf.ts`:

```ts
export type CsrfToken = {
  headerName: string;
  token: string;
};

let cached: Promise<CsrfToken> | null = null;

export function invalidateCsrfToken() {
  cached = null;
}

export function getCsrfToken(): Promise<CsrfToken> {
  if (!cached) {
    cached = fetch("/api/v1/auth/csrf", {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
    }).then(async (response) => {
      if (!response.ok) throw new Error("Unable to obtain CSRF token");
      return (await response.json()) as CsrfToken;
    });
  }
  return cached;
}
```

Do not read access or refresh cookies in JavaScript.

Extend Plan 01 `lib/api/api-error.ts` with one reusable raw-fetch error reader used by refresh/logout helpers:

```ts
export class CovieApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly requestId?: string,
  ) {
    super(message);
  }
}

export async function readApiError(response: Response): Promise<CovieApiError> {
  const value: unknown = await response.json().catch(() => null);
  if (isApiErrorShape(value)) {
    return new CovieApiError(
      response.status,
      value.error.code,
      value.error.message ?? value.error.code,
      value.error.requestId,
    );
  }
  return new CovieApiError(response.status, "HTTP_ERROR", `HTTP ${response.status}`);
}
```

Do not branch application behavior on the human message; callers use `code`.

- [ ] **Step 2: Add CSRF middleware to browser API client**

Modify `createApiClient()` so browser unsafe methods obtain the token and set the returned header. Safe GET/HEAD/OPTIONS do not trigger a CSRF fetch.

Conceptual middleware:

```ts
const unsafe = new Set(["POST", "PUT", "PATCH", "DELETE"]);

client.use({
  async onRequest({ request }) {
    if (unsafe.has(request.method.toUpperCase()) && !request.headers.has("Authorization")) {
      const csrf = await getCsrfToken();
      request.headers.set(csrf.headerName, csrf.token);
    }
    return request;
  },
});
```

On a CSRF-specific 403 response, invalidate the cached token and surface the backend error. Do not automatically replay arbitrary non-idempotent mutations.

- [ ] **Step 3: Implement Google login button**

```tsx
export function GoogleLoginButton({ intent = "LOGIN" }: { intent?: "LOGIN" | "PASSWORD_RESET" }) {
  const href = `/api/v1/auth/google/start?intent=${encodeURIComponent(intent)}`;
  return <a href={href}>Tiếp tục với Google</a>;
}
```

No OAuth client secret or Google access token exists in frontend code.

- [ ] **Step 4: Implement Email/Password login form**

Behavior:

```text
submit POST /api/v1/auth/login
on success invalidate CSRF token, fetch fresh token, navigate to /
INVALID_CREDENTIALS -> one generic Vietnamese message
ACCOUNT_NOT_ACTIVE -> account-state message
field validation -> render fields map
never store access/refresh token in localStorage/sessionStorage
```

`auth-api.ts` also exposes `logout()`: POST `/api/v1/auth/logout` with the CSRF-aware client, then `invalidateCsrfToken()` and navigate to `/login`. It never tries to read or delete HttpOnly auth cookies itself.

- [ ] **Step 5: Implement onboarding password page**

The page does not read an onboarding token. It relies on HttpOnly `covie_auth_action` cookie set by backend Google callback.

Submit:

```http
POST /api/v1/auth/onboarding/set-password
```

On success, refresh CSRF and navigate to `/`.

If action ticket is missing/expired, render CTA to start Google login again.

- [ ] **Step 6: Implement reset-password page**

Page entry CTA from login screen:

```text
Quên mật khẩu?
-> /api/v1/auth/google/start?intent=PASSWORD_RESET
-> Google
-> backend redirect /reset-password
```

Reset page submits only `newPassword` to `/api/v1/auth/password/reset-with-google`.

- [ ] **Step 7: Implement single-flight refresh coordination in `auth-session.ts`**

Because the backend intentionally treats reuse of a rotated refresh token as compromise, the browser must never launch parallel refresh requests from one tab/process. Implement one shared in-flight promise:

```ts
let refreshPromise: Promise<void> | null = null;

export async function refreshSession(): Promise<void> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    const csrf = await getCsrfToken();
    const response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      credentials: "include",
      headers: { [csrf.headerName]: csrf.token },
    });
    if (!response.ok) throw await readApiError(response);
    invalidateCsrfToken();
  })().finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
}
```

Authenticated-request retry rules:

```text
GET/HEAD 401 outside /api/v1/auth/** -> refresh once -> retry once
all POST/PUT/PATCH/DELETE -> never auto-replay in Plan 02, even with Idempotency-Key; surface the original 401 so the owning feature can explicitly decide whether to refresh and re-submit
/api/v1/auth/** request -> never recursively trigger refresh
two simultaneous GET/HEAD callers -> exactly one POST /auth/refresh
```

Install one response middleware on the browser `createApiClient()` instance:

```ts
client.use({
  async onResponse({ request, response }) {
    if (response.status !== 401) return response;

    const method = request.method.toUpperCase();
    const url = new URL(request.url, window.location.origin);
    if (url.pathname.startsWith("/api/v1/auth/")) return response;
    if (method !== "GET" && method !== "HEAD") return response;

    await refreshSession();
    const retry = new Request(request.url, {
      method: request.method,
      headers: new Headers(request.headers),
      credentials: "include",
      cache: "no-store",
    });
    return fetch(retry);
  },
});
```

The raw retry is intentionally limited to bodyless safe reads, so it cannot duplicate a business mutation. Later feature plans may implement operation-aware idempotent retry where they own the Idempotency-Key lifecycle.

The server remains authoritative; this coordination prevents accidental self-triggered refresh reuse but is not a security boundary.

- [ ] **Step 8: Write frontend tests**

Test at least:

```text
safe request does not fetch CSRF
first unsafe request fetches CSRF and attaches header
subsequent unsafe request reuses cached token
invalidateCsrfToken forces refresh
login form maps INVALID_CREDENTIALS without checking message text
Google recovery link uses PASSWORD_RESET intent
two simultaneous refreshSession callers issue exactly one refresh request
GET retries once after successful refresh
unsafe mutation is never auto-replayed by the generic Plan 02 client
```

- [ ] **Step 9: Verify**

```bash
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add apps/web/lib/api/api-client.ts apps/web/lib/api/api-error.ts apps/web/features/auth apps/web/app/'(auth)' apps/web/tests/auth
git commit -m "feat(web): add secure authentication flows"
```

---

# Task 14: Generate Auth/User OpenAPI Client and Add `/me`, Sessions and Address Web Reads

**Files:**
- Modify: backend controllers/DTO OpenAPI annotations where necessary
- Regenerate: `packages/api-client/openapi/openapi.json`
- Regenerate: `packages/api-client/src/generated/schema.ts`
- Create: `apps/web/lib/auth/require-user.ts`
- Create: `apps/web/lib/api/server-api-client.ts`
- Create: `apps/web/features/profile/profile-api.ts`
- Create: `apps/web/features/profile/address-api.ts`
- Create: `apps/web/features/auth/session-list.tsx`
- Create: `apps/web/app/(buyer)/me/page.tsx`
- Create: `apps/web/app/(buyer)/me/security/sessions/page.tsx`
- Create: `apps/web/app/(buyer)/me/addresses/page.tsx`
- Test: `apps/web/tests/auth/session-list.test.tsx`
- Test: `apps/api/src/test/java/vn/covie/openapi/AuthOpenApiContractTest.java`

**Produces:** generated type coverage for every Plan 02 endpoint and minimal authenticated account surfaces.

- [ ] **Step 1: Add OpenAPI contract test**

Test exported spec contains at minimum:

```text
/api/v1/auth/csrf
/api/v1/auth/login
/api/v1/auth/refresh
/api/v1/auth/logout
/api/v1/auth/onboarding/set-password
/api/v1/auth/password/reset-with-google
/api/v1/me
/api/v1/me/security/sessions
/api/v1/me/addresses
/api/v1/me/contact-methods
```

Also assert security scheme declares Bearer JWT and endpoint documentation notes cookie/CSRF behavior.

- [ ] **Step 2: Document auth headers/cookies without exposing secret values**

OpenAPI descriptions must state:

```text
browser mutations use X-CSRF-Token
browser auth cookies are HttpOnly
Bearer JWT is accepted through Authorization header
refresh response rotates refresh cookie
```

Do not model raw refresh token as a response body property.

- [ ] **Step 3: Regenerate typed client**

```bash
./infra/scripts/export-openapi.sh
pnpm api-client:generate
pnpm api-client:check
```

Expected: generated schema contains all new request/response DTOs.

- [ ] **Step 4: Implement an authenticated server-read client and `require-user.ts`**

Server Components call Spring Boot through `API_INTERNAL_BASE_URL`, so they must explicitly forward the browser's incoming `Cookie` header. Use the original request header rather than trying to read HttpOnly cookie values in client JavaScript. Personal server reads are always `cache: "no-store"`.

`apps/web/lib/api/server-api-client.ts`:

```ts
import { headers } from "next/headers";
import { createApiClient } from "@/lib/api/api-client";
import { serverEnv } from "@/lib/env/server";

export async function createServerReadClient() {
  const incoming = await headers();
  const cookie = incoming.get("cookie") ?? "";
  const client = createApiClient(serverEnv.API_INTERNAL_BASE_URL);

  client.use({
    async onRequest({ request }) {
      const forwarded = new Headers(request.headers);
      if (cookie) forwarded.set("cookie", cookie);
      return new Request(request, {
        headers: forwarded,
        cache: "no-store",
      });
    },
  });

  return client;
}
```

`require-user.ts` calls `GET /api/v1/me` with this client. On `401`, it redirects to `/login`; other API failures are surfaced through the normal error boundary.

Rules:

```text
server helper is read-only in Plan 02
never forward cookies to a host other than configured API_INTERNAL_BASE_URL
never expose the Cookie header to Client Components
never cache /me, sessions, addresses or contact methods
unsafe mutations remain browser/client-side and use CSRF middleware
```

- [ ] **Step 5: Implement minimal `/me` page**

Render:

```text
display name
email
account status
link to addresses
link to security sessions
```

Do not display Google subject/token/session-family IDs.

- [ ] **Step 6: Implement session list**

Current session is visually identified by `current=true`. Revoke-one uses backend DELETE; revoke-others uses explicit button. UI refetches after successful command.

Do not optimistically mark a session revoked before backend success.

- [ ] **Step 7: Implement address list/create baseline**

Use generated DTOs. Keep map picker/geocoding out of Plan 02. The Plan 02 address create screen uses explicit numeric latitude and longitude inputs with client-side range validation; Plan 03 may replace those inputs with map/geocoding UX without changing this API contract.

- [ ] **Step 8: Verify contract drift**

```bash
./infra/scripts/export-openapi.sh
pnpm api-client:generate
pnpm api-client:check
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web test
```

Expected: PASS and no handwritten duplicate API response types for Plan 02 endpoints.

- [ ] **Step 9: Commit**

```bash
git add packages/api-client apps/api/src/test/java/vn/covie/openapi apps/web/lib/auth apps/web/lib/api/server-api-client.ts apps/web/features/profile apps/web/features/auth/session-list.tsx apps/web/app/'(buyer)' apps/web/tests/auth/session-list.test.tsx
git commit -m "feat(contract): expose typed auth and user client"
```

---

# Task 15: Add Full Authentication Security, Transaction and IDOR Test Matrix

**Files:**
- Create: `apps/api/src/test/java/vn/covie/auth/acceptance/AuthAcceptanceTest.java`
- Create: `apps/api/src/test/java/vn/covie/auth/acceptance/AuthCookieCsrfAcceptanceTest.java`
- Create: `apps/api/src/test/java/vn/covie/auth/acceptance/AuthIdorAcceptanceTest.java`
- Create: `apps/api/src/test/java/vn/covie/auth/acceptance/AuthAuditSafetyTest.java`
- Create: `apps/api/src/test/java/vn/covie/auth/acceptance/AuthDatabaseInvariantTest.java`

**Produces:** evidence for the API Design authentication testing contract.

- [ ] **Step 1: Test complete first-registration path**

Without calling Google externally, mock Spring Security OIDC authentication at the success-handler boundary and verify:

```text
new Google subject
-> onboarding ticket
-> set password
-> one ACTIVE user
-> GOOGLE + EMAIL identity
-> USER role
-> active session
-> JWT/current actor works
```

Do not mock repositories in this acceptance test; use Testcontainers PostgreSQL.

- [ ] **Step 2: Test returning login paths resolve same user**

```text
existing Google login -> same user ID
Email/Password login -> same user ID
neither creates duplicate users/identities
```

- [ ] **Step 3: Test refresh/session contract**

```text
refresh rotates
rotated successor expiry equals fixed auth-session expiry
refresh does not extend auth-session expiry
used token cannot refresh again
reuse revokes session
two simultaneous uses of R1 exercise real PostgreSQL row locks and end with family revoked after reuse detection
multi-device sessions independent
revoke one does not revoke another
revoke others preserves current
logout current invalidates next API request
```

- [ ] **Step 4: Test password recovery contract**

```text
fresh Google recovery ticket resets password
old sessions revoked
new session created
old password rejected
new password accepted
old access JWT rejected because session is revoked
```

- [ ] **Step 5: Test CSRF matrix**

```text
browser cookie-auth POST missing CSRF -> 403
browser cookie-auth POST invalid CSRF -> 403
browser cookie-auth POST valid CSRF -> normal result
Authorization Bearer POST without CSRF -> allowed through CSRF layer
GET never requires CSRF
CSRF token cookie is invalidated at login/onboarding/refresh/reset/logout and the next /auth/csrf returns a fresh token
stale/expired covie_at cookie does not block /auth/login, /auth/refresh, /auth/logout or Google recovery flow
```

- [ ] **Step 6: Test private-resource IDOR**

User A attempts:

```text
DELETE User B session
GET User B address
PATCH User B address
DELETE User B contact
```

Expected: 404 for each.

- [ ] **Step 7: Test audit/log safety**

Scan captured logs/audit JSON after login/reset/refresh and assert they do not contain:

```text
submitted password
raw access JWT
raw refresh token
raw action ticket
OAuth authorization code
CSRF token
```

Use distinctive fixture secrets so accidental leakage is detectable.

- [ ] **Step 8: Test account-status and onboarding-race enforcement**

```text
previously valid JWT + active session + user becomes SUSPENDED -> next /me request is 401
BANNED/DELETED behaves the same
two distinct onboarding tickets for same Google sub -> exactly one user commits; losing request is 409 IDENTITY_LINK_CONFLICT
no orphan users/roles/sessions remain from losing transaction
```

- [ ] **Step 9: Test database invariants**

Assert directly:

```text
one EMAIL identity per normalized email
GOOGLE email metadata may equal EMAIL identity email for same user
one active default address/user
one active primary contact/type/user
auth_refresh_tokens.token_hash unique
raw refresh value not found in any text/varchar DB column
auth action ticket is single-use
```

- [ ] **Step 10: Run acceptance suite**

```bash
cd apps/api
./mvnw -Dtest='vn.covie.auth.acceptance.*' test
```

Expected: PASS with zero failures.

- [ ] **Step 11: Commit**

```bash
git add apps/api/src/test/java/vn/covie/auth/acceptance
git commit -m "test(auth): cover authentication security contract"
```

---

# Task 16: CI/Documentation/Acceptance Gate and Plan 03 Handoff

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `.env.example`
- Create: `docs/runbooks/google-oauth-local.md`
- Create: `docs/runbooks/auth-key-management.md`
- Create: `docs/architecture/auth-v1.md`
- Modify/regenerate: `packages/api-client/openapi/openapi.json`
- Modify/regenerate: `packages/api-client/src/generated/schema.ts`

**Produces:** reproducible Plan 02 verification and exact contracts Plan 03 can consume.

- [ ] **Step 1: Extend CI without real Google credentials**

CI runs unit/integration/acceptance tests using test OIDC fixtures and generated RSA test keys. It must not need a Google network call.

Backend CI core:

```bash
cd apps/api
./mvnw verify
```

Frontend/contract:

```bash
pnpm install --frozen-lockfile
./infra/scripts/export-openapi.sh
pnpm api-client:generate
pnpm api-client:check
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web test
pnpm --filter @covie/web build
```

- [ ] **Step 2: Document local Google OAuth setup**

Runbook must specify redirect URI exactly:

```text
http://localhost:3000/api/v1/auth/google/callback/google
```

for the normal Next.js dev-rewrite flow, plus `https://covie.vn/api/v1/auth/google/callback/google` for production. The browser initiation path is `/api/v1/auth/oauth2/authorization/google`, never the Spring default root-level path.

Document scopes:

```text
openid
profile
email
```

and explain that CoVie uses Google `sub` as identity key.

- [ ] **Step 3: Document JWT key handling**

`auth-key-management.md` states:

```text
private key loaded from secret file/secret manager
public key may be distributed to API instances
kid is included in JWT header
never commit production key
local keys are disposable
key rotation procedure is finalized in Plan 10 hardening
```

- [ ] **Step 4: Document auth architecture decisions**

`auth-v1.md` records:

```text
Google-only first registration
password required after Google onboarding
Google + EMAIL identities map to same user
strict refresh rotation/reuse detection
PostgreSQL active-session validation
browser HttpOnly cookies + CSRF
Bearer-header path reserved for non-browser clients
OAuth HttpSession is transient handshake state only
```

- [ ] **Step 5: Fresh backend verification**

Run from a clean test state:

```bash
cd apps/api
./mvnw clean verify
```

Expected:

```text
BUILD SUCCESS
0 test failures
Flyway clean database applies V001,V002,V003 in order
```

- [ ] **Step 6: Fresh frontend/contract verification**

```bash
cd ../..
pnpm install --frozen-lockfile
./infra/scripts/export-openapi.sh
pnpm api-client:generate
pnpm api-client:check
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web test
pnpm --filter @covie/web build
```

Expected: all commands exit 0.

- [ ] **Step 7: Verify migration ordering**

On a fresh PostGIS database:

```sql
select version, description, success
from flyway_schema_history
order by installed_rank;
```

Expected:

```text
1  extensions and foundation          true
2  identity users and audit           true
3  auth sessions and tickets          true
```

No V014 is present at this stage.

- [ ] **Step 8: Verify secrets are not tracked**

```bash
git ls-files | grep -E 'infra/secrets|jwt-private|\.env\.local' && exit 1 || true
git grep -nE 'GOOGLE_OAUTH_CLIENT_SECRET=.+|BEGIN (RSA )?PRIVATE KEY' -- ':!*.md' ':!.env.example' && exit 1 || true
```

Expected: no tracked secret match.

- [ ] **Step 9: Commit docs/CI/contract**

```bash
git add .github/workflows/ci.yml README.md .env.example docs/runbooks docs/architecture packages/api-client
git commit -m "docs(auth): finalize identity implementation gate"
```

---

# Plan 02 Acceptance Gate

Plan 02 is complete only when fresh evidence proves all of the following:

```text
new Google user -> onboarding password -> authenticated
no users row exists before onboarding succeeds
Google subject is canonical identity key
same user can later log in by Google
Google authorization initiation and callback both stay under /api/v1 routing
same user can later log in by Email/Password
unknown email and wrong password share INVALID_CREDENTIALS response
Argon2id hashes use versioned prefix and configured parameters
access JWT carries sub/global roles/sessionId/jti and validates RSA signature
access token TTL is 15 minutes
initial refresh/session lifetime is 30 days; rotation never extends session expiry
refresh rotates every time
reuse revokes whole auth session family
multi-device sessions remain independent
revoked session JWT fails on next authenticated request
non-ACTIVE user account fails on next authenticated request even if JWT/session were previously valid
Google password recovery revokes old sessions and creates one new session
browser unsafe mutation requires CSRF
CSRF token is actively invalidated across authentication/logout boundaries and reacquired before later unsafe calls
stale access cookie does not block login/refresh/logout/recovery under /api/v1/auth/**
Bearer-header mutation is CSRF-exempt
/me resolves only current user
other user's session/address/contact IDs return 404
saved address round-trips longitude/latitude correctly through PostGIS geography
one default active address invariant holds
OpenAPI is regenerated and typed client compiles
web login/onboarding/reset pages compile and tests pass
no raw password/access/refresh/action/CSRF secret appears in audit/log tests
no real OAuth or JWT private secret is tracked
```

---

# Error Codes Added by Plan 02

Extend Plan 01 `vn.covie.shared.web.ErrorCode` with these machine-readable values:

```text
AUTHENTICATION_REQUIRED
AUTH_SESSION_INVALID
CSRF_INVALID
INVALID_CREDENTIALS
ACCOUNT_NOT_ACTIVE
IDENTITY_LINK_CONFLICT
GOOGLE_IDENTITY_INVALID
GOOGLE_EMAIL_NOT_VERIFIED
PASSWORD_RECOVERY_NOT_AVAILABLE
AUTH_ACTION_REQUIRED
AUTH_ACTION_EXPIRED
AUTH_ACTION_ALREADY_CONSUMED
AUTH_ACTION_PURPOSE_MISMATCH
REFRESH_TOKEN_INVALID
REFRESH_TOKEN_EXPIRED
REFRESH_TOKEN_REUSE_DETECTED
```

Recommended status mapping:

```text
AUTHENTICATION_REQUIRED               401
AUTH_SESSION_INVALID                  401
CSRF_INVALID                          403
INVALID_CREDENTIALS                   401
ACCOUNT_NOT_ACTIVE                    403
IDENTITY_LINK_CONFLICT                409
GOOGLE_IDENTITY_INVALID               401
GOOGLE_EMAIL_NOT_VERIFIED             422
PASSWORD_RECOVERY_NOT_AVAILABLE       409
AUTH_ACTION_REQUIRED                  401
AUTH_ACTION_EXPIRED                   401
AUTH_ACTION_ALREADY_CONSUMED          409
AUTH_ACTION_PURPOSE_MISMATCH           409
REFRESH_TOKEN_INVALID                 401
REFRESH_TOKEN_EXPIRED                 401
REFRESH_TOKEN_REUSE_DETECTED          401
```

Security-filter failures must still use the same `ApiErrorResponse` contract and current `X-Request-Id` established by Plan 01.

---

# Transaction Boundaries

The following methods are explicitly transactional:

```text
AuthApplicationService.completeOnboarding              WRITE transaction
InitialSessionIssuer.issue                             caller transaction or REQUIRED
EmailPasswordLoginService.login                        WRITE transaction
RefreshRotationTransaction.rotate                     WRITE + pessimistic locks; returns committed outcome
RefreshTokenService.refresh                            maps outcome to HTTP/domain error outside transaction
SessionManagementService.revokeSession                 WRITE transaction
SessionManagementService.revokeOthers                  WRITE transaction
PasswordRecoveryService.resetWithGoogle                WRITE transaction
UserProfileService.update                              WRITE transaction
UserContactMethodService.create/update/delete          WRITE transaction
UserAddressService.create/update/delete                 WRITE transaction
```

Read-only methods use `@Transactional(readOnly = true)` where repository access requires a stable persistence context.

Do not wrap Google network redirects/token exchange in a database transaction. The database transaction begins only after Spring Security has completed OIDC validation and the success handler invokes CoVie application logic.

---

# Deferred Operational Hardening

Plan 02 implements the security semantics and testable persistence behavior, but production-wide rate-limit policy, long-term cleanup scheduling, monitoring and multi-instance OAuth-handshake strategy are completed in Plan 10. In particular:

```text
AUTH_LOGIN / AUTH_GOOGLE_START / AUTH_REFRESH rate limits -> Plan 10 Redis-backed limiter
expired/revoked auth_refresh_tokens retention cleanup       -> Plan 10 scheduled cleanup
auth_action_tickets retention cleanup                       -> Plan 10 scheduled cleanup
multi-instance OAuth HttpSession strategy                    -> Plan 10 deployment decision (sticky session or shared Spring Session)
production startup rejects disabled-local Google credentials   -> Plan 10 config/secret validation gate
```

This deferral does not weaken Plan 02's authentication correctness: PostgreSQL session validity, token rotation/reuse detection, CSRF and authorization are implemented here.

---

# Security Decisions Locked by Plan 02

## Google account linking

```text
GOOGLE provider identity -> provider_subject = Google sub
email is not Google primary key
never auto-link an existing local account based only on matching email
verified Google email seeds local EMAIL identity only during new onboarding
```

## Auth action ticket

```text
opaque 256-bit token
raw value only in HttpOnly cookie
SHA-256 fingerprint in DB
10-minute TTL
single-use
purpose-bound ONBOARDING or PASSWORD_RESET
```

## Session lifetime

```text
auth session lifetime = absolute 30 days from login/session creation
rotating refresh tokens inherit the same auth_sessions.expires_at
refresh does not slide/extend session lifetime
expired session requires login again
```

## Authenticated browser

```text
covie_at    -> access JWT, HttpOnly
covie_rt    -> opaque refresh token, HttpOnly
COVIE-XSRF-TOKEN -> CSRF token readable by JS
X-CSRF-Token     -> unsafe request header
```

## Non-browser path

```text
Authorization: Bearer <access JWT>
CSRF not required
sessionId still checked against PostgreSQL
```

Plan 02 does not create a public refresh-token-in-body API for mobile. Native/mobile token transport is deferred until such a client is actually in scope.

---

# Data Retention Baseline

```text
auth_sessions:
  retain revoked/expired session metadata for security/audit window; purge policy finalized in Plan 10

auth_refresh_tokens:
  keep used/revoked rows long enough for reuse detection and security investigation; purge policy finalized in Plan 10

auth_action_tickets:
  expired/consumed tickets may be hard-deleted by a later scheduled cleanup job after audit-safe retention

audit_logs:
  append-only; retention policy is an admin/production hardening concern
```

Plan 02 does not create cleanup schedulers; scheduled-job locking/cleanup orchestration belongs to the later infrastructure/hardening plan.

---

# Source/Framework Compatibility Notes

The implementation is intentionally aligned with current framework behavior used by this project:

- Spring Security OAuth2 Login uses the Authorization Code flow and supports customized authorization/redirection endpoints.
- Google OIDC provides a stable `sub` claim and explicitly advises applications not to use email as the account identifier.
- Spring Security 7 provides SPA-specific CSRF integration through `csrf.spa()` and requires obtaining a fresh CSRF token after authentication/logout.
- Spring Security `JwtEncoder`/`NimbusJwtEncoder` and `JwtDecoder` are used instead of introducing a separate JWT framework.
- Spring Security `Argon2PasswordEncoder` supports explicit Argon2 parameters and currently requires Bouncy Castle.
- Hibernate Spatial provides PostgreSQL/PostGIS geography support, including a Hibernate `GEOGRAPHY` JDBC type code.

These are implementation dependencies, not business rules; if a later library upgrade changes an API, preserve the behavioral contracts above.

---

# Self-Review Checklist

Before approving Plan 02 for execution, verify:

- [ ] Plan 01 interfaces are reused rather than duplicated.
- [ ] Migration numbering no longer jumps to V014 before later domain migrations.
- [ ] `users` contains no email/password fields.
- [ ] Google `sub` is the identity key.
- [ ] New Google callback alone cannot create a fully authenticated user.
- [ ] Onboarding/recovery raw ticket is never placed in URL or DB.
- [ ] Local EMAIL uniqueness does not incorrectly block a GOOGLE row carrying the same email metadata.
- [ ] Password hash has algorithm/version prefix.
- [ ] No plaintext password/token is logged/audited.
- [ ] JWT global roles contain only USER/ADMIN.
- [ ] Merchant roles remain deferred to Plan 03 DB membership checks.
- [ ] Refresh/revoke/password-reset mutations use the same session-first, token-second lock order.
- [ ] Strict reuse detection has a real concurrent PostgreSQL test.
- [ ] Logout and session revoke invalidate future requests through active-session validation.
- [ ] CSRF is enabled for browser unsafe requests.
- [ ] Bearer-header clients do not need browser CSRF.
- [ ] `/me` and child resources derive user ID from `CurrentActor`.
- [ ] IDOR returns 404 for another user's private IDs.
- [ ] PostGIS address test catches lat/lng reversal.
- [ ] `location_area_id`/`avatar_media_id` FKs are intentionally deferred until Plan 03 tables exist.
- [ ] OpenAPI/client generation remains deterministic.
- [ ] Plan 02 contains no merchant/catalog/order business implementation.

---

# Dependency Handoff to Plan 03

Plan 03 — Merchant, Location, Media & Catalog may assume:

```text
users/user_identities/system_role_assignments exist
CurrentActorProvider resolves authenticated user/session/global roles
Google/Email login flows are complete
active-session revocation is enforced
/me works
saved addresses have PostGIS geo points
user contact methods exist
AuditLogWriter exists
OpenAPI/client generation includes auth/user contracts
Flyway highest version after Plan 02 is V003
```

Plan 03 must add, among other domain migrations:

```text
location_areas
merchant_profiles
merchant_memberships
merchant_member_locations
merchant_locations
media_assets
categories/catalog/options/variants/location_variants
```

After `location_areas` and `media_assets` exist, Plan 03 adds foreign keys:

```text
user_addresses.location_area_id -> location_areas.id
users.avatar_media_id -> media_assets.id
```

Plan 03 authorization must use `CurrentActor.userId()` plus database merchant membership/location scope; it must not add merchant roles into JWT claims.

---

# Execution Handoff

After this plan is reviewed and approved:

1. Verify Plan 01 Task 12 acceptance evidence is green.
2. Create an isolated worktree with `superpowers:using-git-worktrees`.
3. Execute Task 1 through Task 16 in order.
4. Recommended: `superpowers:subagent-driven-development` with a fresh agent and review gate per task.
5. Alternative: `superpowers:executing-plans` in small batches with checkpoints.
6. Do not begin Plan 03 until Task 16 acceptance evidence is fresh and green.
