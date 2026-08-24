# CoVie Plan 01 — Foundation & Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Use `superpowers:using-git-worktrees` before implementation. Every task ends with a reviewable verification gate and commit.

**Project:** CoVie  
**Plan:** 01 / 10  
**Target repo path:** `docs/superpowers/plans/2026-08-08-covie-foundation.md`  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Closed — historically accepted

> **Execution closure — 2026-08-25:** Historically accepted and integrated. The task checklist below is retained as the original implementation specification, not a live completion tracker. See `docs/CoVie-plan-closure-audit-2026-08-24.md` for closure evidence.

**Goal:** Bootstrap the CoVie monorepo and create the smallest production-shaped technical foundation that every later plan can build on: Next.js web app, Spring Boot modular-monolith shell, PostgreSQL/PostGIS migrations, Redis/MinIO local infrastructure, shared primitives, standard API errors/request IDs, OpenAPI generation, typed TypeScript client, Nginx routing skeleton, and CI baseline.

**Architecture:** CoVie V1 remains a modular monolith. `apps/api` owns authoritative business behavior and `/api/v1`; `apps/web` consumes the generated API contract and must not duplicate backend state machines. PostgreSQL/PostGIS is the transactional source of truth; Redis is ephemeral infrastructure only; MinIO represents local S3-compatible storage. This plan creates infrastructure and shared contracts only—no merchant, catalog, cart, checkout, order, Need, review, or moderation feature is implemented here.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1.0, Spring MVC, Spring Data JPA, Flyway, PostgreSQL 18 + PostGIS 3.6, Redis 8, Springdoc OpenAPI 3.x, JUnit 5, Testcontainers, Node.js 24 LTS, pnpm, Next.js 16.2, React 19, TypeScript strict, Vitest, React Testing Library, `openapi-typescript`, `openapi-fetch`, Docker Compose, Nginx.

---

# Global Constraints

- Public API base path is `/api/v1`.
- Spring Boot is the business source of truth.
- JPA entities are never serialized directly as HTTP responses.
- Business IDs use UUIDv7; display codes/slugs are separate concepts.
- Money is represented as integer minor units plus ISO currency code.
- Absolute timestamps use UTC; tests use an injectable `AppClock` rather than calling `Instant.now()` in domain/application logic.
- Flyway is the only supported schema-change path. Hibernate uses `ddl-auto=validate`.
- PostgreSQL/PostGIS integration tests use a real PostgreSQL/PostGIS Testcontainer; H2 is not permitted for database correctness tests.
- Redis must not become authoritative storage for auth sessions, checkout, cart, order, inventory, or notification history.
- Media bytes will later upload directly to object storage; this plan only starts local MinIO and does not implement upload APIs.
- Browser production topology is same-domain: `/` -> Next.js and `/api/v1/*` -> Spring Boot.
- No microservices, Kafka/RabbitMQ, WebSocket chat, payment settlement, service booking calendar, or search cluster is introduced.
- No production secrets, private keys, OAuth secrets, access tokens, refresh tokens, or storage credentials are committed.
- Plan 01 must not create fake business APIs merely to make the project look complete.

---

# Deliverable Gate

Plan 01 is complete only when all of the following are independently verified:

```text
repo clone -> documented bootstrap path exists
Docker Compose config is valid
PostgreSQL/PostGIS, Redis and MinIO run locally
Spring Boot starts against a clean database
Flyway V001 enables postgis, pg_trgm and unaccent
/api/v1/health returns the standard success DTO and X-Request-Id
/actuator/health is available to internal infrastructure
malformed/validation test requests use the standard ApiErrorResponse shape
UUIDv7, Money and AppClock primitives have focused tests
OpenAPI JSON can be exported from the running/test application
packages/api-client regenerates TypeScript schema from OpenAPI
Next.js home renders and typecheck/tests/build pass
browser dev requests to /api/v1 can be proxied to Spring Boot
Nginx same-domain routing config passes nginx -t
CI runs backend, frontend and contract baseline checks
no generated secret or .env.local is tracked
```

---

# Repository Map Locked by This Plan

```text
covie/
├── apps/
│   ├── api/
│   │   ├── pom.xml
│   │   ├── mvnw
│   │   ├── mvnw.cmd
│   │   ├── .mvn/wrapper/
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/vn/covie/
│   │       │   │   ├── CovieApplication.java
│   │       │   │   └── shared/
│   │       │   │       ├── id/
│   │       │   │       ├── money/
│   │       │   │       ├── time/
│   │       │   │       ├── web/
│   │       │   │       ├── security/
│   │       │   │       └── outbox/
│   │       │   └── resources/
│   │       │       ├── application.yml
│   │       │       ├── application-local.yml
│   │       │       └── db/migration/V001__extensions_and_foundation.sql
│   │       └── test/
│   │           ├── java/vn/covie/
│   │           └── resources/application-test.yml
│   └── web/
│       ├── app/
│       ├── lib/
│       │   ├── api/
│       │   └── env/
│       ├── tests/
│       ├── next.config.ts
│       ├── package.json
│       ├── tsconfig.json
│       └── vitest.config.ts
├── packages/
│   ├── api-client/
│   │   ├── openapi/openapi.json
│   │   ├── src/generated/schema.ts
│   │   ├── src/client.ts
│   │   ├── src/index.ts
│   │   └── package.json
│   └── config/
│       └── typescript/base.json
├── infra/
│   ├── compose/compose.local.yml
│   ├── docker/nginx.conf
│   └── scripts/
│       ├── export-openapi.sh
│       └── verify-generated-client.sh
├── docs/
│   └── superpowers/plans/
├── .github/workflows/ci.yml
├── .editorconfig
├── .env.example
├── .gitignore
├── package.json
├── pnpm-workspace.yaml
└── README.md
```

`packages/ui` is intentionally deferred until a reusable UI package is actually needed. Do not create an empty package in Plan 01.

---

# Public Interfaces Produced by Plan 01

Later plans may rely on these exact Java names:

```text
vn.covie.shared.id.UuidV7Generator
vn.covie.shared.time.AppClock
vn.covie.shared.time.SystemAppClock
vn.covie.shared.money.Money
vn.covie.shared.web.ErrorCode
vn.covie.shared.web.ApiException
vn.covie.shared.web.ApiErrorResponse
vn.covie.shared.web.GlobalExceptionHandler
vn.covie.shared.web.RequestIdFilter
vn.covie.shared.security.SystemRole
vn.covie.shared.security.CurrentActor
vn.covie.shared.outbox.OutboxEventIntent
vn.covie.shared.outbox.OutboxEventWriter
```

Frontend contracts:

```text
@covie/api-client
apps/web/lib/api/api-client.ts
apps/web/lib/api/api-error.ts
apps/web/lib/env/server.ts
apps/web/lib/env/client.ts
```

No later plan should redefine these concepts under another package without an explicit architecture change.

---

# Task 1: Bootstrap the Monorepo and Toolchain Contract

**Files:**
- Create: `package.json`
- Create: `pnpm-workspace.yaml`
- Create: `.editorconfig`
- Create: `.gitignore`
- Create: `.env.example`
- Create: `packages/config/typescript/base.json`
- Create: `README.md` with only the initial repository heading; full bootstrap instructions are completed in Task 10.

**Produces:** Root workspace commands and toolchain metadata consumed by every frontend/package task.

- [ ] **Step 1: Create the root workspace manifest**

```json
{
  "name": "covie",
  "private": true,
  "engines": {
    "node": ">=24 <25",
    "pnpm": ">=10 <11"
  },
  "scripts": {
    "infra:config": "docker compose -f infra/compose/compose.local.yml config",
    "infra:up": "docker compose -f infra/compose/compose.local.yml up -d",
    "infra:down": "docker compose -f infra/compose/compose.local.yml down",
    "web:dev": "pnpm --filter @covie/web dev",
    "web:lint": "pnpm --filter @covie/web lint",
    "web:typecheck": "pnpm --filter @covie/web typecheck",
    "web:test": "pnpm --filter @covie/web test",
    "web:build": "pnpm --filter @covie/web build",
    "api-client:generate": "pnpm --filter @covie/api-client generate",
    "api-client:check": "pnpm --filter @covie/api-client check"
  }
}
```

- [ ] **Step 2: Create the pnpm workspace file**

```yaml
packages:
  - "apps/web"
  - "packages/*"
```

- [ ] **Step 3: Create `.editorconfig`**

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 2

[*.java]
indent_size = 4

[*.md]
trim_trailing_whitespace = false
```

- [ ] **Step 4: Create `.gitignore`**

```gitignore
# Environment and secrets
.env
.env.local
.env.*.local
*.pem
*.key

# Node
node_modules/
.next/
coverage/
.pnpm-store/

# Java/Maven
**/target/
.idea/
*.iml

# OS/editor
.DS_Store
Thumbs.db
.vscode/

# Local infrastructure
.minio/
.postgres/
.redis/
```

- [ ] **Step 5: Create `.env.example` as a shape contract, not a production secret file**

```dotenv
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:postgresql://localhost:5433/covie
DB_USERNAME=covie
DB_PASSWORD=covie_local
REDIS_URL=redis://localhost:6379
APP_PUBLIC_URL=http://localhost:3000
JWT_PRIVATE_KEY_PATH=/run/secrets/covie-jwt-private.pem
JWT_PUBLIC_KEY_PATH=/run/secrets/covie-jwt-public.pem
GOOGLE_CLIENT_ID=not-configured-in-foundation
GOOGLE_CLIENT_SECRET=not-configured-in-foundation
GOOGLE_REDIRECT_URI=http://localhost:8080/api/v1/auth/google/callback
S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_BUCKET=covie-local
S3_ACCESS_KEY=covie_local
S3_SECRET_KEY=covie_local_password
S3_PUBLIC_BASE_URL=http://localhost:9000/covie-local
NEXT_PUBLIC_APP_URL=http://localhost:3000
API_INTERNAL_BASE_URL=http://localhost:8080
```

- [ ] **Step 6: Create the shared TypeScript strict baseline**

```json
{
  "$schema": "https://json.schemastore.org/tsconfig",
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "noImplicitOverride": true,
    "forceConsistentCasingInFileNames": true,
    "skipLibCheck": true
  }
}
```

- [ ] **Step 7: Verify workspace parsing before any app exists**

Run:

```bash
node --version
pnpm --version
pnpm install
```

Expected:

```text
Node major version = 24
pnpm major version = 10
pnpm install exits 0 with an empty/minimal workspace
pnpm-lock.yaml is created
```

- [ ] **Step 8: Commit**

```bash
git add package.json pnpm-workspace.yaml pnpm-lock.yaml .editorconfig .gitignore .env.example packages/config README.md
git commit -m "chore: bootstrap covie monorepo"
```

---

# Task 2: Add Local PostgreSQL/PostGIS, Redis and MinIO Infrastructure

**Files:**
- Create: `infra/compose/compose.local.yml`

**Produces:** Local services used by backend integration and manual development.

- [ ] **Step 1: Write Compose configuration with explicit service names and local-only credentials**

```yaml
services:
  postgres:
    image: postgis/postgis:18-3.6
    environment:
      POSTGRES_DB: covie
      POSTGRES_USER: covie
      POSTGRES_PASSWORD: covie_local
    ports:
      - "5433:5432"
    volumes:
      - covie_postgres:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U covie -d covie"]
      interval: 5s
      timeout: 3s
      retries: 20

  redis:
    image: redis:8-alpine
    command: ["redis-server", "--appendonly", "no", "--save", ""]
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20

  minio:
    image: quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: covie_local
      MINIO_ROOT_PASSWORD: covie_local_password
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - covie_minio:/data

  minio-init:
    image: quay.io/minio/mc:RELEASE.2025-04-16T18-13-26Z
    depends_on:
      - minio
    restart: "no"
    entrypoint: >
      /bin/sh -c "
      until mc alias set local http://minio:9000 covie_local covie_local_password; do sleep 2; done;
      mc mb --ignore-existing local/covie-local;
      exit 0;
      "

volumes:
  covie_postgres:
  covie_minio:
```

For PostgreSQL 18 images, mount the named volume at `/var/lib/postgresql`; the image manages its major-version-specific data subdirectory below that path.

- [ ] **Step 2: Validate Compose syntax before starting anything**

Run:

```bash
pnpm infra:config
```

Expected: exit `0`; resolved services include `postgres`, `redis`, `minio`, and `minio-init`.

- [ ] **Step 3: Start infrastructure**

Run:

```bash
pnpm infra:up
docker compose -f infra/compose/compose.local.yml ps
```

Expected:

```text
postgres -> healthy
redis    -> healthy
minio    -> running
minio-init -> exits 0 after bucket creation
```

- [ ] **Step 4: Verify PostgreSQL and Redis from inside containers**

Run:

```bash
docker compose -f infra/compose/compose.local.yml exec postgres psql -U covie -d covie -c "select version();"
docker compose -f infra/compose/compose.local.yml exec redis redis-cli ping
```

Expected:

```text
PostgreSQL 18.x
PONG
```

- [ ] **Step 5: Commit**

```bash
git add infra/compose/compose.local.yml
git commit -m "chore(infra): add local postgres redis and minio"
```

---

# Task 3: Bootstrap the Spring Boot API Application

**Files:**
- Create: `apps/api/pom.xml`
- Create: `apps/api/.mvn/wrapper/maven-wrapper.properties`
- Create: `apps/api/.mvn/wrapper/maven-wrapper.jar`
- Create: `apps/api/mvnw`
- Create: `apps/api/mvnw.cmd`
- Create: `apps/api/src/main/java/vn/covie/CovieApplication.java`
- Create: `apps/api/src/main/resources/application.yml`
- Create: `apps/api/src/main/resources/application-local.yml`
- Create: `apps/api/src/test/resources/application-test.yml`
- Create: `apps/api/src/test/java/vn/covie/CovieApplicationTest.java`

**Produces:** A bootable backend shell with production-shaped dependencies and no business endpoints.

- [ ] **Step 1: Write the failing application-context test first**

```java
package vn.covie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class CovieApplicationTest {

    @Test
    void applicationEntrypointIsSpringBootApplication() {
        assertThat(CovieApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }
}
```

- [ ] **Step 2: Run the focused test and observe failure because the Maven project/app does not exist yet**

Run:

```bash
cd apps/api
./mvnw -Dtest=CovieApplicationTest test
```

Expected: FAIL because `pom.xml`, wrapper, or `CovieApplication` is not yet available.

- [ ] **Step 3: Create `pom.xml`**

Use Spring Boot `4.1.0`, Java `25`, and keep dependency surface limited to foundations needed by Plans 01–04.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>vn.covie</groupId>
    <artifactId>covie-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>covie-api</name>

    <properties>
        <java.version>25</java.version>
        <springdoc.version>3.0.3</springdoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Generate and commit the Maven Wrapper**

Run with a local Maven installation once:

```bash
cd apps/api
mvn wrapper:wrapper -Dtype=bin
```

Expected: `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/` are created.

- [ ] **Step 5: Add the Spring Boot entrypoint**

```java
package vn.covie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CovieApplication {

    public static void main(String[] args) {
        SpringApplication.run(CovieApplication.class, args);
    }
}
```

- [ ] **Step 6: Add base configuration**

`application.yml`:

```yaml
spring:
  application:
    name: covie-api
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/covie}
    username: ${DB_USERNAME:covie}
    password: ${DB_PASSWORD:covie_local}
  data:
    redis:
      url: ${REDIS_URL:redis://localhost:6379}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true
  jackson:
    default-property-inclusion: non_null

server:
  port: 8080
  forward-headers-strategy: framework

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      probes:
        enabled: true

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    enabled: false
```

`application-local.yml`:

```yaml
springdoc:
  swagger-ui:
    enabled: true

logging:
  level:
    vn.covie: INFO
```

`application-test.yml`:

```yaml
spring:
  main:
    banner-mode: off
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
springdoc:
  swagger-ui:
    enabled: false
```

- [ ] **Step 7: Run the focused test**

Run:

```bash
cd apps/api
./mvnw -Dtest=CovieApplicationTest test
```

Expected: PASS. This focused test proves the application entrypoint compiles without weakening database configuration. Full Spring context startup against real PostgreSQL is added in Task 4.

- [ ] **Step 8: Commit the backend skeleton**

```bash
git add apps/api
git commit -m "chore(api): bootstrap spring boot application"
```

---

# Task 4: Add Flyway Foundation Migration and Real PostgreSQL Test Harness

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V001__extensions_and_foundation.sql`
- Create: `apps/api/src/test/java/vn/covie/support/PostgresIntegrationTest.java`
- Create: `apps/api/src/test/java/vn/covie/database/FoundationMigrationTest.java`
- Create: `apps/api/src/test/java/vn/covie/CovieApplicationContextTest.java`

**Produces:** Reusable PostGIS Testcontainer base and verified Flyway foundation.

- [ ] **Step 1: Write the failing migration test**

```java
package vn.covie.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.support.PostgresIntegrationTest;

class FoundationMigrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void installsRequiredPostgresExtensions() {
        List<String> extensions = jdbcTemplate.queryForList(
                "select extname from pg_extension where extname in ('postgis', 'pg_trgm', 'unaccent') order by extname",
                String.class);

        assertThat(extensions).containsExactly("pg_trgm", "postgis", "unaccent");
    }
}
```

- [ ] **Step 2: Add the reusable PostGIS integration-test base**

```java
package vn.covie.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    org.testcontainers.utility.DockerImageName
                            .parse("postgis/postgis:18-3.6")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("covie_test")
                    .withUsername("covie")
                    .withPassword("covie_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

- [ ] **Step 3: Run the migration test before the migration exists**

Run:

```bash
cd apps/api
./mvnw -Dtest=FoundationMigrationTest test
```

Expected: FAIL because the required extensions are not all installed by Flyway.

- [ ] **Step 4: Create `V001__extensions_and_foundation.sql`**

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
```

Do not create business tables in V001.

- [ ] **Step 5: Add a full application-context test using the same real PostgreSQL harness**

```java
package vn.covie;

import org.junit.jupiter.api.Test;
import vn.covie.support.PostgresIntegrationTest;

class CovieApplicationContextTest extends PostgresIntegrationTest {

    @Test
    void applicationContextLoadsAgainstPostgres() {
    }
}
```

- [ ] **Step 6: Run migration and context tests**

Run:

```bash
cd apps/api
./mvnw -Dtest=FoundationMigrationTest,CovieApplicationContextTest test
```

Expected: PASS.

- [ ] **Step 7: Verify Flyway history explicitly**

Add this assertion to `FoundationMigrationTest`:

```java
@Test
void recordsFoundationMigrationInFlywayHistory() {
    Integer count = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '001' and success = true",
            Integer.class);

    assertThat(count).isEqualTo(1);
}
```

Run:

```bash
./mvnw -Dtest=FoundationMigrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/resources/db/migration apps/api/src/test
git commit -m "feat(api): add postgres foundation migration"
```

---

# Task 5: Implement UUIDv7, AppClock and Money Shared Primitives

**Files:**
- Create: `apps/api/src/main/java/vn/covie/shared/id/UuidV7Generator.java`
- Create: `apps/api/src/main/java/vn/covie/shared/time/AppClock.java`
- Create: `apps/api/src/main/java/vn/covie/shared/time/SystemAppClock.java`
- Create: `apps/api/src/main/java/vn/covie/shared/money/Money.java`
- Create: `apps/api/src/test/java/vn/covie/shared/id/UuidV7GeneratorTest.java`
- Create: `apps/api/src/test/java/vn/covie/shared/time/AppClockTest.java`
- Create: `apps/api/src/test/java/vn/covie/shared/money/MoneyTest.java`

**Produces:** Shared ID/time/money contracts used by all business plans.

- [ ] **Step 1: Write failing UUIDv7 tests**

```java
package vn.covie.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void generatesRfc4122VariantVersion7Uuid() {
        UUID id = new UuidV7Generator().generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsCurrentUnixMillisecondsInMostSignificantBits() {
        long before = System.currentTimeMillis();
        UUID id = new UuidV7Generator().generate();
        long after = System.currentTimeMillis();

        long encodedMillis = (id.getMostSignificantBits() >>> 16) & 0x0000FFFFFFFFFFFFL;
        assertThat(encodedMillis).isBetween(before, after);
    }
}
```

- [ ] **Step 2: Run and confirm UUID tests fail**

```bash
cd apps/api
./mvnw -Dtest=UuidV7GeneratorTest test
```

Expected: FAIL because `UuidV7Generator` does not exist.

- [ ] **Step 3: Implement UUIDv7 using JDK primitives**

```java
package vn.covie.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7Generator {

    private static final long UNIX_TS_MASK = 0x0000FFFFFFFFFFFFL;
    private final SecureRandom random;

    public UuidV7Generator() {
        this(new SecureRandom());
    }

    UuidV7Generator(SecureRandom random) {
        this.random = random;
    }

    public UUID generate() {
        long unixMillis = System.currentTimeMillis() & UNIX_TS_MASK;
        long randA = random.nextInt(1 << 12);
        long msb = (unixMillis << 16) | 0x7000L | randA;

        long lsb = random.nextLong();
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        return new UUID(msb, lsb);
    }
}
```

- [ ] **Step 4: Run UUID tests**

```bash
./mvnw -Dtest=UuidV7GeneratorTest test
```

Expected: PASS.

- [ ] **Step 5: Write failing AppClock test and interface contract**

```java
package vn.covie.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppClockTest {

    @Test
    void fixedClockCanDriveDeterministicBusinessTime() {
        Instant expected = Instant.parse("2026-08-08T08:00:00Z");
        AppClock clock = () -> expected;

        assertThat(clock.now()).isEqualTo(expected);
    }
}
```

Implement:

```java
package vn.covie.shared.time;

import java.time.Instant;

@FunctionalInterface
public interface AppClock {
    Instant now();
}
```

```java
package vn.covie.shared.time;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public final class SystemAppClock implements AppClock {

    private final Clock clock;

    public SystemAppClock() {
        this(Clock.systemUTC());
    }

    SystemAppClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
```

Run:

```bash
./mvnw -Dtest=AppClockTest test
```

Expected: PASS.

- [ ] **Step 6: Write failing Money tests**

```java
package vn.covie.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesIsoCurrencyCode() {
        Money money = new Money(45_000, "vnd");

        assertThat(money.amount()).isEqualTo(45_000);
        assertThat(money.currency()).isEqualTo("VND");
    }

    @Test
    void rejectsUnknownCurrencyCode() {
        assertThatThrownBy(() -> new Money(10, "XYZ_NOT_REAL"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 7: Implement Money without floating-point arithmetic**

```java
package vn.covie.shared.money;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record Money(long amount, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        currency = currency.toUpperCase(Locale.ROOT);
        Currency.getInstance(currency);
    }
}
```

Do not add `double`, `float`, or formatted display strings to this type.

- [ ] **Step 8: Run all primitive tests**

```bash
./mvnw -Dtest=UuidV7GeneratorTest,AppClockTest,MoneyTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/api/src/main/java/vn/covie/shared apps/api/src/test/java/vn/covie/shared
git commit -m "feat(api): add shared id time and money primitives"
```

---

# Task 6: Add Request Correlation, Standard Errors and Health API

**Files:**
- Create: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Create: `apps/api/src/main/java/vn/covie/shared/web/ApiException.java`
- Create: `apps/api/src/main/java/vn/covie/shared/web/ApiErrorResponse.java`
- Create: `apps/api/src/main/java/vn/covie/shared/web/RequestIdFilter.java`
- Create: `apps/api/src/main/java/vn/covie/shared/web/GlobalExceptionHandler.java`
- Create: `apps/api/src/main/java/vn/covie/shared/web/HealthController.java`
- Create: `apps/api/src/main/java/vn/covie/shared/web/HealthResponse.java`
- Create: `apps/api/src/test/java/vn/covie/shared/web/GlobalExceptionHandlerTest.java`
- Create: `apps/api/src/test/java/vn/covie/shared/web/HealthControllerTest.java`

**Produces:** Stable `/api/v1` baseline contract and request correlation used by all later controllers.

Spring Boot 4 keeps MockMvc support in the explicit test-scoped
`org.springframework.boot:spring-boot-starter-webmvc-test` dependency. Keep it alongside
`spring-boot-starter-test` when using `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.

- [ ] **Step 1: Write the failing health/request-id integration test**

```java
package vn.covie.shared.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class HealthControllerTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsHealthAndGeneratedRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("req_[A-Za-z0-9-]+")))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void preservesSafeIncomingRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "client-request-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "client-request-12345"));
    }

    @Test
    void replacesUnsafeIncomingRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "bad value"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("req_[A-Za-z0-9-]+")));
    }
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
cd apps/api
./mvnw -Dtest=HealthControllerTest test
```

Expected: FAIL with 404 and/or missing request ID header.

- [ ] **Step 3: Create `ErrorCode` and `ApiException` foundation**

```java
package vn.covie.shared.web;

public enum ErrorCode {
    MALFORMED_REQUEST,
    VALIDATION_FAILED,
    RESOURCE_NOT_FOUND,
    ACCESS_DENIED,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
```

```java
package vn.covie.shared.web;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        this(code, status, message, Map.of());
    }

    public ApiException(ErrorCode code, HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() { return code; }
    public HttpStatus status() { return status; }
    public Map<String, Object> details() { return details; }
}
```

- [ ] **Step 4: Create the standard error response DTO**

```java
package vn.covie.shared.web;

import java.util.List;
import java.util.Map;

public record ApiErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            Map<String, Object> details,
            Map<String, List<String>> fields,
            String requestId) {
    }

    public static ApiErrorResponse of(
            ErrorCode code,
            String message,
            Map<String, Object> details,
            Map<String, List<String>> fields,
            String requestId) {
        return new ApiErrorResponse(new ErrorBody(
                code.name(), message, Map.copyOf(details), Map.copyOf(fields), requestId));
    }
}
```

- [ ] **Step 5: Implement `RequestIdFilter`**

```java
package vn.covie.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.covie.shared.id.UuidV7Generator;

@Component
public final class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private final UuidV7Generator uuidV7Generator = new UuidV7Generator();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE.matcher(incoming).matches()
                ? incoming
                : "req_" + uuidV7Generator.generate();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

- [ ] **Step 6: Implement health endpoint**

```java
package vn.covie.shared.web;

public record HealthResponse(String status) {
}
```

```java
package vn.covie.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public final class HealthController {

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("ok");
    }
}
```

- [ ] **Step 7: Add global exception mapping**

```java
package vn.covie.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(ApiErrorResponse.of(
                exception.code(),
                exception.getMessage(),
                exception.details(),
                Map.of(),
                requestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.computeIfAbsent(error.getField(), ignored -> new java.util.ArrayList<>())
                        .add(error.getDefaultMessage() == null ? "INVALID_VALUE" : error.getDefaultMessage()));

        return ResponseEntity.unprocessableEntity().body(ApiErrorResponse.of(
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed.",
                Map.of(),
                fields,
                requestId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                ErrorCode.MALFORMED_REQUEST,
                "Request body is malformed.",
                Map.of(),
                Map.of(),
                requestId()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled API exception requestId={} path={}", requestId(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.",
                Map.of(),
                Map.of(),
                requestId()));
    }

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
```

Do not return exception stack traces or raw exception messages from the catch-all handler.

- [ ] **Step 8: Add a test-only controller for error contract tests**

Create `GlobalExceptionHandlerTest` with a nested test-only controller. It must not exist in `src/main`.

```java
package vn.covie.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.ErrorProbeController.class)
class GlobalExceptionHandlerTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void serializesStandardErrorEnvelope() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @RestController
    static class ErrorProbeController {

        @GetMapping("/__test/not-found")
        void notFound() {
            throw new ApiException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "Resource not found.");
        }
    }
}
```

- [ ] **Step 9: Run API foundation tests**

```bash
./mvnw -Dtest=HealthControllerTest,GlobalExceptionHandlerTest test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add apps/api/src/main/java/vn/covie/shared/web apps/api/src/test/java/vn/covie/shared/web
git commit -m "feat(api): add request correlation and error contract"
```

---

# Task 7: Add Shared Security and Outbox Ports Without Implementing Auth or Workers

**Files:**
- Create: `apps/api/src/main/java/vn/covie/shared/security/SystemRole.java`
- Create: `apps/api/src/main/java/vn/covie/shared/security/CurrentActor.java`
- Create: `apps/api/src/main/java/vn/covie/shared/outbox/OutboxEventIntent.java`
- Create: `apps/api/src/main/java/vn/covie/shared/outbox/OutboxEventWriter.java`
- Create: `apps/api/src/test/java/vn/covie/shared/security/CurrentActorTest.java`
- Create: `apps/api/src/test/java/vn/covie/shared/outbox/OutboxEventIntentTest.java`

**Produces:** Stable ports consumed by Auth and event-producing modules later.

- [ ] **Step 1: Write the CurrentActor test**

```java
package vn.covie.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentActorTest {

    @Test
    void defensivelyCopiesGlobalRoles() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        java.util.HashSet<SystemRole> roles = new java.util.HashSet<>();
        roles.add(SystemRole.USER);
        CurrentActor actor = new CurrentActor(userId, roles, sessionId);

        roles.add(SystemRole.ADMIN);

        assertThat(actor.userId()).isEqualTo(userId);
        assertThat(actor.globalRoles()).containsExactly(SystemRole.USER);
        assertThat(actor.sessionId()).isEqualTo(sessionId);
    }
}
```

- [ ] **Step 2: Implement the security primitives**

```java
package vn.covie.shared.security;

public enum SystemRole {
    USER,
    ADMIN
}
```

```java
package vn.covie.shared.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CurrentActor(UUID userId, Set<SystemRole> globalRoles, UUID sessionId) {

    public CurrentActor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(globalRoles, "globalRoles");
        Objects.requireNonNull(sessionId, "sessionId");
        globalRoles = Set.copyOf(globalRoles);
    }
}
```

Do not add Merchant `OWNER/MANAGER/STAFF` roles here; those are database-resolved merchant memberships in Plan 03.

- [ ] **Step 3: Write the outbox intent test**

```java
package vn.covie.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventIntentTest {

    @Test
    void copiesPayloadAtConstructionTime() {
        UUID orderId = UUID.randomUUID();
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("orderId", orderId.toString());
        OutboxEventIntent intent = new OutboxEventIntent(
                "ORDER_CONFIRMED",
                "ORDER",
                orderId,
                payload);

        payload.put("tampered", true);

        assertThat(intent.eventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(intent.payload()).containsEntry("orderId", orderId.toString());
        assertThat(intent.payload()).doesNotContainKey("tampered");
    }
}
```

- [ ] **Step 4: Implement the outbox port only**

```java
package vn.covie.shared.outbox;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OutboxEventIntent(
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> payload) {

    public OutboxEventIntent {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        payload = Map.copyOf(payload);
    }
}
```

```java
package vn.covie.shared.outbox;

import java.util.UUID;

public interface OutboxEventWriter {
    UUID append(OutboxEventIntent intent);
}
```

There is deliberately no `@Component` implementation and no `outbox_events` migration in Plan 01. The port exists so later business plans do not invent competing event abstractions. The database implementation is introduced when the Outbox migration is added.

- [ ] **Step 5: Run focused tests**

```bash
cd apps/api
./mvnw -Dtest=CurrentActorTest,OutboxEventIntentTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/api/src/main/java/vn/covie/shared/security apps/api/src/main/java/vn/covie/shared/outbox apps/api/src/test/java/vn/covie/shared/security apps/api/src/test/java/vn/covie/shared/outbox
git commit -m "feat(api): add shared security and outbox contracts"
```

---

# Task 8: Export OpenAPI and Build the Typed API Client Package

**Files:**
- Create: `apps/api/src/test/java/vn/covie/openapi/OpenApiExportTest.java`
- Create: `packages/api-client/package.json`
- Create: `packages/api-client/tsconfig.json`
- Create: `packages/api-client/openapi/openapi.json`
- Generate: `packages/api-client/src/generated/schema.ts`
- Create: `packages/api-client/src/client.ts`
- Create: `packages/api-client/src/index.ts`
- Create: `infra/scripts/export-openapi.sh`
- Create: `infra/scripts/verify-generated-client.sh`

**Produces:** A reproducible OpenAPI -> TypeScript contract pipeline.

- [ ] **Step 1: Write an OpenAPI export integration test**

```java
package vn.covie.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OpenApiExportTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exportsOpenApiDocument() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json).contains("/api/v1/health");

        String output = System.getProperty("covie.openapi.output", "target/openapi/openapi.json");
        Path outputPath = Path.of(output).toAbsolutePath().normalize();
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, json);
    }
}
```

- [ ] **Step 2: Run export test into Maven target first**

```bash
cd apps/api
./mvnw -Dtest=OpenApiExportTest test
```

Expected: PASS and `apps/api/target/openapi/openapi.json` exists.

- [ ] **Step 3: Create API client package**

`packages/api-client/package.json`:

```json
{
  "name": "@covie/api-client",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "exports": {
    ".": "./src/index.ts"
  },
  "scripts": {
    "generate": "openapi-typescript openapi/openapi.json -o src/generated/schema.ts",
    "check": "tsc --noEmit"
  },
  "dependencies": {
    "openapi-fetch": "^0.17.0"
  },
  "devDependencies": {
    "openapi-typescript": "^7.9.1",
    "typescript": "^5.9.2"
  }
}
```

`packages/api-client/tsconfig.json`:

```json
{
  "extends": "../config/typescript/base.json",
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "lib": ["ES2022", "DOM"],
    "verbatimModuleSyntax": true
  },
  "include": ["src/**/*.ts"]
}
```

- [ ] **Step 4: Add the typed client wrapper**

```ts
import createClient from "openapi-fetch";
import type { paths } from "./generated/schema";

export function createCovieClient(baseUrl: string) {
  return createClient<paths>({ baseUrl });
}
```

`src/index.ts`:

```ts
export { createCovieClient } from "./client";
export type { paths, components, operations } from "./generated/schema";
```

- [ ] **Step 5: Add deterministic export script**

`infra/scripts/export-openapi.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="$ROOT_DIR/packages/api-client/openapi/openapi.json"

cd "$ROOT_DIR/apps/api"
./mvnw -Dtest=OpenApiExportTest -Dcovie.openapi.output="$OUTPUT" test

cd "$ROOT_DIR"
pnpm --filter @covie/api-client generate
```

Make executable:

```bash
chmod +x infra/scripts/export-openapi.sh
```

- [ ] **Step 6: Export schema and generate TypeScript**

Run:

```bash
pnpm install
./infra/scripts/export-openapi.sh
pnpm --filter @covie/api-client check
```

Expected:

```text
packages/api-client/openapi/openapi.json contains /api/v1/health
packages/api-client/src/generated/schema.ts exists
TypeScript check exits 0
```

- [ ] **Step 7: Add generated-drift verification script**

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

./infra/scripts/export-openapi.sh

git diff --exit-code -- \
  packages/api-client/openapi/openapi.json \
  packages/api-client/src/generated/schema.ts
```

Make executable and run:

```bash
chmod +x infra/scripts/verify-generated-client.sh
./infra/scripts/verify-generated-client.sh
```

Expected: PASS after generated artifacts are staged/current; modifying `HealthResponse` without regenerating must make this check fail.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/test/java/vn/covie/openapi packages/api-client infra/scripts pnpm-lock.yaml
git commit -m "feat(contract): generate typed client from openapi"
```

---

# Task 9: Bootstrap Next.js Web App and Same-Origin API Wrapper

**Files:**
- Create: `apps/web/package.json`
- Create: `apps/web/tsconfig.json`
- Create: `apps/web/next-env.d.ts`
- Create: `apps/web/next.config.ts`
- Create: `apps/web/app/layout.tsx`
- Create: `apps/web/app/page.tsx`
- Create: `apps/web/app/globals.css`
- Create: `apps/web/lib/env/server.ts`
- Create: `apps/web/lib/env/client.ts`
- Create: `apps/web/lib/api/api-client.ts`
- Create: `apps/web/lib/api/api-error.ts`
- Create: `apps/web/tests/home.test.tsx`
- Create: `apps/web/vitest.config.ts`
- Create: `apps/web/vitest.setup.ts`
- Create: `apps/web/eslint.config.mjs`

**Consumes:** `@covie/api-client` from Task 8.

**Produces:** Minimal web shell with strict env validation and generated API-client integration.

- [ ] **Step 1: Write the failing home component test**

```tsx
import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import HomePage from "../app/page";

it("renders the CoVie foundation shell", () => {
  render(<HomePage />);
  expect(screen.getByRole("heading", { name: "CoVie" })).toBeInTheDocument();
});
```

- [ ] **Step 2: Create web package manifest**

```json
{
  "name": "@covie/web",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "eslint .",
    "typecheck": "tsc --noEmit",
    "test": "vitest run"
  },
  "dependencies": {
    "@covie/api-client": "workspace:*",
    "next": "16.2.0",
    "react": "19.2.0",
    "react-dom": "19.2.0",
    "zod": "^4.1.5"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.8.0",
    "@testing-library/react": "^16.3.0",
    "@types/node": "^24.0.0",
    "@types/react": "^19.1.0",
    "@types/react-dom": "^19.1.0",
    "@vitejs/plugin-react": "^5.0.0",
    "eslint": "^9.0.0",
    "eslint-config-next": "16.2.0",
    "jsdom": "^26.1.0",
    "typescript": "^5.9.2",
    "vitest": "^3.2.4"
  }
}
```

- [ ] **Step 3: Add strict TypeScript config**

```json
{
  "extends": "../../packages/config/typescript/base.json",
  "compilerOptions": {
    "target": "ES2017",
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": false,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "plugins": [{ "name": "next" }],
    "paths": {
      "@/*": ["./*"]
    }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules"]
}
```

- [ ] **Step 4: Add dev proxy while preserving production same-origin routing**

```ts
import type { NextConfig } from "next";

const apiOrigin = process.env.API_INTERNAL_BASE_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    if (process.env.NODE_ENV !== "development") {
      return [];
    }

    return [
      {
        source: "/api/v1/:path*",
        destination: `${apiOrigin}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
```

In production, Nginx owns `/api/v1/*`; Next.js does not act as a mandatory BFF.

- [ ] **Step 5: Add environment parsers**

`lib/env/server.ts`:

```ts
import { z } from "zod";

const schema = z.object({
  API_INTERNAL_BASE_URL: z.string().url().default("http://localhost:8080"),
});

export const serverEnv = schema.parse({
  API_INTERNAL_BASE_URL: process.env.API_INTERNAL_BASE_URL,
});
```

`lib/env/client.ts`:

```ts
import { z } from "zod";

const schema = z.object({
  NEXT_PUBLIC_APP_URL: z.string().url().default("http://localhost:3000"),
});

export const clientEnv = schema.parse({
  NEXT_PUBLIC_APP_URL: process.env.NEXT_PUBLIC_APP_URL,
});
```

No secret env variable is imported in `client.ts`.

- [ ] **Step 6: Add API client factory**

```ts
import { createCovieClient } from "@covie/api-client";

export function createApiClient(baseUrl = "") {
  return createCovieClient(baseUrl);
}
```

Server Components call `createApiClient(serverEnv.API_INTERNAL_BASE_URL)` after importing `serverEnv` from `lib/env/server`. Client Components call `createApiClient()` and use same-origin `/api/v1`. Keeping server environment imports out of this shared module prevents accidental server-env bundling into client code.

`api-error.ts`:

```ts
export type ApiErrorShape = {
  error: {
    code: string;
    message?: string;
    details?: Record<string, unknown>;
    fields?: Record<string, string[]>;
    requestId?: string;
  };
};

export function isApiErrorShape(value: unknown): value is ApiErrorShape {
  if (typeof value !== "object" || value === null || !("error" in value)) return false;
  const error = (value as { error?: unknown }).error;
  return typeof error === "object" && error !== null && "code" in error;
}
```

This helper only narrows the standardized error envelope; it must not create frontend-only business error semantics.

- [ ] **Step 7: Add minimal layout and page**

```tsx
import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "CoVie",
  description: "Hyperlocal marketplace",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
```

```tsx
export default function HomePage() {
  return (
    <main>
      <h1>CoVie</h1>
      <p>Marketplace gần bạn.</p>
    </main>
  );
}
```

Add `next-env.d.ts`:

```ts
/// <reference types="next" />
/// <reference types="next/image-types/global" />

// This file is maintained by Next.js.
```

Add minimal `app/globals.css`:

```css
* {
  box-sizing: border-box;
}

html,
body {
  margin: 0;
  min-height: 100%;
}

body {
  font-family: Arial, Helvetica, sans-serif;
}
```

Keep CSS minimal; visual design belongs to later frontend plans.

- [ ] **Step 8: Configure Vitest and ESLint**

```ts
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
  },
});
```

```ts
import "@testing-library/jest-dom/vitest";
```

Add `eslint.config.mjs` using the Next.js 16 flat config:

```js
import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypeScript from "eslint-config-next/typescript";

export default defineConfig([
  ...nextVitals,
  ...nextTypeScript,
  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts"]),
]);
```

- [ ] **Step 9: Install and run frontend checks**

```bash
pnpm install
pnpm --filter @covie/web test
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web lint
pnpm --filter @covie/web build
```

Expected: all commands exit 0.

- [ ] **Step 10: Commit**

```bash
git add apps/web package.json pnpm-lock.yaml
git commit -m "feat(web): bootstrap nextjs application shell"
```

---

# Task 10: Add Same-Domain Nginx Routing Skeleton and Developer Bootstrap Documentation

**Files:**
- Create: `infra/docker/nginx.conf`
- Modify: `README.md`

**Produces:** One documented local bootstrap path and production routing intent.

- [ ] **Step 1: Create Nginx config skeleton**

```nginx
worker_processes auto;

events {
    worker_connections 1024;
}

http {
    upstream covie_api {
        server api:8080;
    }

    upstream covie_web {
        server web:3000;
    }

    server {
        listen 80;
        server_name _;

        location /actuator/ {
            return 404;
        }

        location /api/v1/ {
            proxy_pass http://covie_api;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Request-Id $http_x_request_id;
        }

        location / {
            proxy_pass http://covie_web;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        }
    }
}
```

Plan 10 will add TLS, production images, timeouts, security headers, compression, deployment health routing, and secret management. Do not prematurely turn this skeleton into the final production ingress.

- [ ] **Step 2: Validate Nginx syntax in an isolated container**

Run from repository root:

```bash
docker run --rm \
  --add-host api:127.0.0.1 \
  --add-host web:127.0.0.1 \
  -v "$PWD/infra/docker/nginx.conf:/etc/nginx/nginx.conf:ro" \
  nginx:1.28-alpine nginx -t
```

The two temporary aliases are only for an isolated syntax check. At runtime Docker Compose DNS resolves
the real `api` and `web` upstream names.

Expected:

```text
syntax is ok
test is successful
```

- [ ] **Step 3: Replace the minimal README with deterministic bootstrap instructions**

README must contain these exact logical steps:

```text
Prerequisites:
- JDK 25
- Node.js 24 LTS
- pnpm 10
- Docker Desktop / Docker Engine + Compose

Start infrastructure:
pnpm infra:up

Run API:
cd apps/api
./mvnw -Dspring-boot.run.profiles=local spring-boot:run

Windows PowerShell API equivalent:
cd apps/api
.\mvnw.cmd "-Dspring-boot.run.profiles=local" spring-boot:run

Run web in another terminal:
pnpm web:dev

Verify:
http://localhost:8080/api/v1/health
http://localhost:8080/actuator/health
http://localhost:3000/
http://localhost:9001/

Regenerate contract on macOS/Linux/Git Bash:
./infra/scripts/export-openapi.sh

Regenerate contract on Windows PowerShell without Git Bash:
cd apps/api
.\mvnw.cmd -Dtest=OpenApiExportTest "-Dcovie.openapi.output=../../packages/api-client/openapi/openapi.json" test
cd ../..
pnpm api-client:generate

Stop infrastructure:
pnpm infra:down
```

Also document that production secrets never go into `.env.example` and that `.env.local` is gitignored.

- [ ] **Step 4: Commit**

```bash
git add infra/docker/nginx.conf README.md
git commit -m "docs: add foundation bootstrap and routing contract"
```

---

# Task 11: Add CI Baseline

**Files:**
- Create: `.github/workflows/ci.yml`

**Produces:** A pull-request gate for backend, frontend, migrations, OpenAPI drift and generated client.

- [ ] **Step 1: Create CI workflow**

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: apps/api
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "25"
          cache: maven
      - name: Verify backend
        run: ./mvnw verify

  frontend-and-contract:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "25"
          cache: maven
      - uses: actions/setup-node@v4
        with:
          node-version: "24"
      - name: Install pnpm
        run: npm install --global pnpm@10
      - name: Install workspace
        run: pnpm install --frozen-lockfile
      - name: Regenerate OpenAPI client and check drift
        run: ./infra/scripts/verify-generated-client.sh
      - name: Typecheck API client
        run: pnpm --filter @covie/api-client check
      - name: Test web
        run: pnpm --filter @covie/web test
      - name: Typecheck web
        run: pnpm --filter @covie/web typecheck
      - name: Lint web
        run: pnpm --filter @covie/web lint
      - name: Build web
        run: pnpm --filter @covie/web build
```

Testcontainers uses the GitHub-hosted runner Docker daemon. Do not add H2 or a separate CI-only database path.

- [ ] **Step 2: Run the same commands locally before relying on CI**

```bash
cd apps/api && ./mvnw verify && cd ../..
./infra/scripts/verify-generated-client.sh
pnpm --filter @covie/api-client check
pnpm --filter @covie/web test
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web lint
pnpm --filter @covie/web build
```

Expected: all exit `0`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add foundation quality gates"
```

---

# Task 12: Foundation Acceptance Verification

**Files:**
- Modify only files required to fix verification failures discovered here.

**Purpose:** This is a fresh evidence gate before Plan 01 is called complete. Do not add new architecture or features in this task.

- [ ] **Step 1: Verify repository state and secret hygiene**

Run:

```bash
git status --short
git ls-files | grep -E '(^|/)(\.env|.*\.pem|.*\.key)$' && exit 1 || true
git grep -nE '(GOOGLE_CLIENT_SECRET=.+[^-]$|JWT_PRIVATE_KEY=|BEGIN (RSA )?PRIVATE KEY|S3_SECRET_KEY=[^c])' -- . ':!*.md' ':!.env.example' || true
```

Expected:

```text
working tree clean after committed implementation
environment/private-key files are not tracked
no real-looking secret appears in source
```

- [ ] **Step 2: Verify clean local infrastructure**

Run:

```bash
pnpm infra:config
pnpm infra:up
docker compose -f infra/compose/compose.local.yml ps
```

Expected: Postgres and Redis healthy; MinIO running.

- [ ] **Step 3: Verify a clean database migration**

Reset only the local development volume, then start it again:

```bash
docker compose -f infra/compose/compose.local.yml down -v
pnpm infra:up
```

This intentionally deletes only the local Compose development volumes. Start the API with the local profile and verify Flyway migration succeeds from an empty database without manual SQL.

- [ ] **Step 4: Run full backend suite**

```bash
cd apps/api
./mvnw clean verify
cd ../..
```

Expected: `BUILD SUCCESS`, zero test failures.

- [ ] **Step 5: Verify OpenAPI and generated client are current**

```bash
./infra/scripts/verify-generated-client.sh
pnpm --filter @covie/api-client check
```

Expected: exit `0` and no git diff.

- [ ] **Step 6: Run full frontend suite**

```bash
pnpm --filter @covie/web test
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web lint
pnpm --filter @covie/web build
```

Expected: all pass.

- [ ] **Step 7: Start API and web for smoke verification**

Terminal A:

```bash
cd apps/api
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

Terminal B:

```bash
pnpm web:dev
```

Terminal C:

```bash
curl -i http://localhost:8080/api/v1/health
curl -i http://localhost:8080/actuator/health
curl -I http://localhost:3000/
curl -i http://localhost:3000/api/v1/health
```

Expected:

```text
/api/v1/health -> HTTP 200, body {"status":"ok"}, X-Request-Id present
/actuator/health -> HTTP 200
web root -> HTTP 200
Next.js development rewrite /api/v1/health -> backend HTTP 200
```

- [ ] **Step 8: Verify Nginx config again**

```bash
docker run --rm \
  --add-host api:127.0.0.1 \
  --add-host web:127.0.0.1 \
  -v "$PWD/infra/docker/nginx.conf:/etc/nginx/nginx.conf:ro" \
  nginx:1.28-alpine nginx -t
```

Expected: syntax test successful.

- [ ] **Step 9: Verify no Plan 01 scope leakage**

Run:

```bash
find apps/api/src/main/java/vn/covie -maxdepth 1 -mindepth 1 -type d -printf '%f\n' | sort
```

Expected at the end of Plan 01:

```text
shared
```

`auth`, `merchants`, `catalog`, `inventory`, `checkout`, `orders`, `needs`, `reviews`, `moderation`, etc. must not contain fake implementation yet.

- [ ] **Step 10: Record the Plan 01 completion commit only after all fresh verification is green**

If acceptance required fixes, commit them with a narrow message such as:

```bash
git add -A
git commit -m "fix: satisfy foundation acceptance gate"
```

If no files changed, do not create an empty completion commit.

---

# Failure Handling During Implementation

When any test or verification command fails:

1. Use `superpowers:systematic-debugging` before proposing a fix.
2. Reproduce the smallest failing command.
3. Identify whether the problem is source code, dependency compatibility, local Docker/runtime, or the plan itself.
4. If a pinned dependency in this plan no longer resolves, update only to a compatible version inside the already-approved major/minor baseline and record the reason in the commit.
5. Never work around PostgreSQL/PostGIS failures by switching tests to H2.
6. Never disable Flyway validation to make startup green.
7. Never commit generated OpenAPI/client drift without reviewing the contract change.

---

# Plan 01 Explicit Non-Goals

The following belong to later plans and must not be implemented here:

```text
Google OAuth login
password hashing/session tables/JWT issuance
user profile/address business APIs
MerchantProfile/MerchantLocation
media upload session/presigned URL
categories/catalog/variants/location offers
inventory tables/locking
shipping rules
cart
checkout Preview/Commit
idempotency_records implementation
outbox_events table/worker
Order state machine
Need marketplace
reviews/reputation
notifications/SSE
moderation/settings/admin
PWA feature shell beyond minimal Next.js boot
production Docker images/TLS/backups/alerts
```

The `CurrentActor` and `OutboxEventWriter` types are interfaces/contracts only in this plan so later modules share one foundation.

---

# Dependency Handoff to Plan 02

Plan 02 — Identity, Auth & User may assume:

```text
Spring Boot app boots with PostgreSQL/PostGIS
Flyway migration path is established
RequestIdFilter and GlobalExceptionHandler are active
ApiErrorResponse/ErrorCode conventions exist
UuidV7Generator exists
AppClock exists
Money exists
CurrentActor/SystemRole primitives exist
OpenAPI export pipeline works
@covie/api-client generation works
Next.js app and server/browser API client factories exist
CI runs backend/frontend/contract gates
```

Plan 02 must then add, in new migrations and modules:

```text
users
user_identities
user_contact_methods
user_addresses
audit_logs foundation if required by auth audit
auth_sessions
auth_refresh_tokens
Spring Security
Google onboarding
password setup/login/recovery
JWT
CSRF
CurrentActor resolution
session management
```

---

# Self-Review Checklist

Before approving this plan for execution, verify:

- [ ] Every Master Plan 01 deliverable has a task.
- [ ] No business feature from Plans 02–10 was pulled into Foundation.
- [ ] Flyway and PostgreSQL/PostGIS are exercised by real integration tests.
- [ ] No H2 fallback exists.
- [ ] Standard API error shape and request ID are established before domain controllers.
- [ ] Shared `Money` has no floating-point source field.
- [ ] `CurrentActor` contains only global roles; merchant roles are deferred.
- [ ] `OutboxEventWriter` is a port only, not a fake no-op bean.
- [ ] OpenAPI generation has a deterministic diff gate.
- [ ] Frontend uses generated types rather than handwritten API DTO duplication.
- [ ] Next.js dev proxy does not turn Next.js into the production BFF.
- [ ] Nginx blocks public `/actuator/*` in the routing skeleton.
- [ ] `.env.example` contains examples only; real secret files remain ignored.
- [ ] CI and local verification run the same core commands.
- [ ] Final acceptance uses fresh verification evidence.

---

# Execution Handoff

After this plan is reviewed and approved:

1. Create an isolated git worktree/feature branch using `superpowers:using-git-worktrees`.
2. Execute Task 1 through Task 12 in order.
3. Recommended mode: `superpowers:subagent-driven-development`, one fresh implementation agent per task with review between tasks.
4. Alternative: `superpowers:executing-plans` in batches with explicit checkpoints.
5. Do not begin Plan 02 until Task 12 evidence is green.

