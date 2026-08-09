# CoVie Plan 10 — Production Hardening & Pilot Launch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Plan:** 10 / 10  
**Target repo path:** `docs/superpowers/plans/2026-08-08-covie-pilot-hardening.md`  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Ready for implementation review

**Goal:** Đưa CoVie V1 từ trạng thái feature-complete của Plans 01–09 thành một release candidate có thể vận hành pilot an toàn: production images, same-domain HTTPS deployment, startup safety, rate limiting, metrics/alerts, retention, backup/restore, performance/security gates, Grand Park bootstrap, immutable release/rollback và staging rehearsal có bằng chứng.

**Architecture:** CoVie pilot chạy single-region với Nginx là public ingress duy nhất, phía sau là stateless Next.js Web container và Spring Boot API container. PostgreSQL/PostGIS vẫn là source of truth; Redis là ephemeral rate-limit/cache dependency; media ở S3-compatible object storage. Production deploy dùng immutable image digest và controlled single-stack restart; V1 không thêm Kubernetes, service mesh, message broker, multi-region active-active hoặc blue/green database migration complexity. Database rollback mặc định là **forward-fix**, không chạy down migration; backup restore chỉ dùng khi có sự cố dữ liệu nghiêm trọng và theo runbook.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1.x, PostgreSQL 18 + PostGIS 3.6.x, Redis 8.x, S3-compatible object storage, Next.js 16.2.x, Node.js 24 LTS, Docker/Compose, Nginx, Micrometer + Prometheus registry, Prometheus 3.11.3, Grafana 13.1.0, Alertmanager 0.32.1, node_exporter 1.11.1, Trivy 0.72.0, GitHub Actions, Playwright, Bash, PostgreSQL `pg_dump/pg_restore`, Node.js native `fetch`.

## Global Constraints

- Plans 01–09 are prerequisites. Plan 10 does not add or redesign marketplace business features.
- Public production origin remains `https://covie.vn`.
- Public API remains `https://covie.vn/api/v1/*`.
- Nginx is the only public ingress for the pilot deployment. API/Web/Prometheus/Grafana/Alertmanager are not directly internet-exposed.
- `/actuator/*` is internal-only and must never be proxied by public Nginx.
- Spring Boot remains authoritative for all business decisions; hardening must not move business state into Nginx, Redis, scripts or monitoring.
- PostgreSQL/PostGIS remains the transactional source of truth.
- Redis must never become authoritative for auth sessions, checkout sessions, Cart, Order, inventory, Need, Review, notification history or moderation.
- Object storage contains media bytes only; media metadata/ownership/readiness remains in PostgreSQL.
- No Kubernetes, Kafka/RabbitMQ, Elasticsearch/OpenSearch, service mesh, multi-region active-active, WebSocket, internal chat, booking calendar or payment/escrow infrastructure is introduced.
- Production containers are non-root and images are immutable.
- Secrets are supplied at runtime. No real secret, private key, production `.env`, service-account JSON or registry credential is committed or baked into an image layer.
- Browser authentication keeps Plan 02 cookie/CSRF semantics. Production auth cookies must be `Secure`, `HttpOnly` where specified and `SameSite=Lax`.
- `COVIE_E2E_SUPPORT_ENABLED` must be false in production and the `e2e` profile must never be active.
- Rate limiting is an abuse-control layer, never a substitute for authorization, validation, idempotency, row locking or business limits.
- Rate-limit identifiers derived from email/refresh credentials use keyed HMAC, never raw values or plain SHA-256 dictionary targets.
- Important mutation correctness continues to rely on PostgreSQL transaction/idempotency/locking even if Redis is unavailable.
- All data schema changes still go through Flyway.
- Plan 09 ended with `V013__moderation_system_settings_admin.sql`. Plan 10 creates **no Flyway migration by default**. The next available number remains `V014`.
- Do not create `V014__production_hardening.sql` or any empty migration merely to consume a version.
- If execution discovers an evidence-backed missing index/schema constraint, stop that task, capture `EXPLAIN`/failure evidence, review the database change separately, then use V014 and update the migration handoff.
- Business/order/audit/history rows are never purged by generic technical retention.
- Docker volumes are not a backup strategy.
- A backup is not considered operational until restore has been rehearsed into a separate environment.
- Application rollback switches immutable image digests. It must not automatically run a Flyway down migration.
- SSE remains best-effort. Reconnect causes REST refetch; no durable SSE replay is added.
- Production performance numbers in this plan are **pilot acceptance objectives**, not a public SLA.
- A release cannot pilot while any release blocker listed near the end of this document is open.

---

# Production Topology Locked by Plan 10

```text
Internet
   |
   v
HTTPS :443
Nginx
   |-------------------------------|
   |                               |
   v                               v
/api/v1/*                      all other routes
Spring Boot API                Next.js Web
:8080 internal                 :3000 internal
:9091 management internal
   |
   +--> PostgreSQL/PostGIS
   +--> Redis
   +--> S3-compatible object storage
   |
   +--> Outbox/Schedulers/SSE

Internal monitoring network:
Prometheus --> API :9091/actuator/prometheus
           --> node_exporter
Alertmanager <--> Prometheus
Grafana --> Prometheus

Publicly blocked:
API container port
Web container port
management port
Prometheus
Alertmanager
Grafana
Redis
PostgreSQL
```

Production pilot intentionally does not require zero-downtime orchestration. A short controlled restart during a release is acceptable; correctness, rollback simplicity and data safety take priority.

---

# Repository Map Added by Plan 10

```text
apps/
├── api/
│   ├── Dockerfile
│   └── src/main/
│       ├── java/vn/covie/
│       │   ├── ops/
│       │   ├── ratelimit/
│       │   ├── maintenance/
│       │   ├── observability/
│       │   └── bootstrap/
│       └── resources/
│           ├── application-prod.yml
│           ├── bootstrap/grand-park-reference-data.json
│           └── ratelimit/token-bucket.lua
└── web/
    └── Dockerfile

infra/
├── compose/
│   ├── compose.prod.yml
│   └── compose.monitoring.yml
├── docker/
│   ├── nginx.prod.conf
│   └── security-headers.conf
├── deploy/
│   ├── prod.env.example
│   ├── deploy.sh
│   ├── rollback.sh
│   ├── smoke.sh
│   ├── verify-runtime-env.sh
│   └── release-manifest.example
├── backup/
│   ├── postgres-backup.sh
│   ├── postgres-restore.sh
│   ├── verify-backup.sh
│   └── covie-backup.prom.example
├── monitoring/
│   ├── prometheus.yml
│   ├── alerts.yml
│   ├── alertmanager.yml
│   └── grafana/
│       └── provisioning/
├── storage/
│   ├── cors.json
│   └── lifecycle.json
├── perf/
│   ├── run-load.mjs
│   ├── scenarios.mjs
│   └── run-query-plan-checks.sh
└── scripts/
    └── verify-production-artifacts.sh

docs/operations/
├── production-topology.md
├── secrets.md
├── rate-limits.md
├── observability.md
├── backup-restore.md
├── object-storage.md
├── performance.md
├── deploy-rollback.md
├── incident-runbook.md
├── staging-rehearsal.md
└── pilot-launch-checklist.md

.github/workflows/
├── ci.yml
└── release.yml
```

No operator script adds a public `/api/v1/admin/backdoor/*` or `/api/v1/bootstrap/*` endpoint.

---

## Task 1: Add Production Profile and Fail-fast Startup Safety

**Files:**
- Create: `apps/api/src/main/resources/application-prod.yml`
- Create: `apps/api/src/main/java/vn/covie/ops/ProductionSafetyProperties.java`
- Create: `apps/api/src/main/java/vn/covie/ops/ProductionSafetyValidator.java`
- Modify: `apps/api/src/main/java/vn/covie/CovieApplication.java`
- Modify: `.env.example`
- Test: `apps/api/src/test/java/vn/covie/ops/ProductionSafetyValidatorTest.java`
- Test: `apps/api/src/test/java/vn/covie/ops/ProductionProfileContextTest.java`

**Interfaces:**
- Consumes existing `AuthProperties`, storage properties and Spring environment.
- Produces a production profile that refuses unsafe cookie/key/URL/E2E configuration before serving traffic.

- [ ] **Step 1: Write production-safety RED tests**

Required cases:

```text
prod + COVIE_COOKIE_SECURE=false -> startup fails
prod + HTTP public URL/issuer/Google redirect -> startup fails
prod + missing/unreadable JWT private/public key -> startup fails
prod + e2e active -> startup fails
prod + COVIE_E2E_SUPPORT_ENABLED=true -> startup fails
prod + Hibernate ddl-auto other than validate -> startup fails
prod + Flyway disabled -> startup fails
valid production-shaped configuration -> context starts
```

Use temporary RSA test files; never use a checked-in production key.

Run:

```bash
cd apps/api
./mvnw -Dtest=ProductionSafetyValidatorTest,ProductionProfileContextTest test
```

Expected: FAIL before implementation.

- [ ] **Step 2: Create `application-prod.yml`**

Use:

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    validate-on-migrate: true
  data:
    redis:
      repositories:
        enabled: false

server:
  shutdown: graceful
  forward-headers-strategy: framework

spring.lifecycle:
  timeout-per-shutdown-phase: 30s

management:
  server:
    port: 9091
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
      group:
        liveness:
          include: livenessState,ping
        readiness:
          include: readinessState,db

logging:
  structured:
    format:
      console: logstash

springdoc:
  swagger-ui:
    enabled: false
```

Redis/S3 health may remain visible through `/actuator/health` components when available, but they are **not** required in readiness. Database is the dependency that determines transactional readiness.

- [ ] **Step 3: Bind production-safety properties**

```java
@ConfigurationProperties(prefix = "covie.production")
public record ProductionSafetyProperties(
        URI publicUrl,
        boolean e2eSupportEnabled) {}
```

`application-prod.yml`:

```yaml
covie:
  production:
    public-url: ${COVIE_PUBLIC_URL:https://covie.vn}
    e2e-support-enabled: ${COVIE_E2E_SUPPORT_ENABLED:false}
```

Do not store secrets in this properties object.

- [ ] **Step 4: Implement `ProductionSafetyValidator`**

`@Profile("prod")`, `ApplicationRunner`.

Validate:
- public URL scheme `https`;
- `AuthProperties.issuer` scheme `https`;
- configured Google redirect is exactly production HTTPS callback `/api/v1/auth/google/callback/google`;
- Google client ID/secret are nonblank and not the `disabled-local-*` test defaults;
- `auth.cookies.secure == true`;
- JWT private/public key paths exist, are regular files and readable;
- active profiles do not contain `e2e`;
- E2E support false;
- `spring.jpa.hibernate.ddl-auto == validate`;
- Flyway enabled.

Throw `IllegalStateException` with configuration **name only**, never secret value.

- [ ] **Step 5: Register typed configuration**

Update `CovieApplication` or the existing configuration-properties enablement so `ProductionSafetyProperties` is bound once. Do not read environment variables ad hoc in multiple services.

- [ ] **Step 6: Add environment-name contract only**

`.env.example` lists:

```text
SPRING_PROFILES_ACTIVE=prod
COVIE_PUBLIC_URL=https://covie.vn
API_INTERNAL_BASE_URL=http://api:8080
DB_URL=
DB_USERNAME=
DB_PASSWORD=
REDIS_URL=
COVIE_AUTH_ISSUER=https://covie.vn
COVIE_COOKIE_SECURE=true
COVIE_JWT_KEY_ID=
COVIE_JWT_PRIVATE_KEY_PATH=/run/covie-secrets/jwt-private.pem
COVIE_JWT_PUBLIC_KEY_PATH=/run/covie-secrets/jwt-public.pem
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
GOOGLE_OAUTH_REDIRECT_URI=https://covie.vn/api/v1/auth/google/callback/google
COVIE_STORAGE_BUCKET=
COVIE_STORAGE_REGION=
COVIE_STORAGE_ENDPOINT=
COVIE_MEDIA_PUBLIC_BASE_URL=
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
COVIE_RATE_LIMIT_HMAC_KEY=
COVIE_E2E_SUPPORT_ENABLED=false
COVIE_ALERT_WEBHOOK_FILE=
COVIE_GRAFANA_ADMIN_PASSWORD=
```

Values remain blank/examples only.

- [ ] **Step 7: Run GREEN and commit**

```bash
cd apps/api
./mvnw -Dtest=ProductionSafetyValidatorTest,ProductionProfileContextTest test
cd ../..
git add apps/api/src/main/resources/application-prod.yml \
        apps/api/src/main/java/vn/covie/ops \
        apps/api/src/main/java/vn/covie/CovieApplication.java \
        apps/api/src/test/java/vn/covie/ops \
        .env.example
git commit -m "feat(ops): fail fast on unsafe production configuration"
```

---

## Task 2: Build a Non-root Production Spring Boot Image

**Files:**
- Create: `apps/api/Dockerfile`
- Create: `apps/api/.dockerignore`
- Create: `infra/scripts/verify-api-image.sh`
- Modify: `apps/api/pom.xml`

**Interfaces:**
- Produces an immutable API image that contains only the built application/runtime and runs as numeric non-root UID.

- [ ] **Step 1: Write runtime-contract RED test/script**

`verify-api-image.sh` must assert:

```text
container user != 0
image starts successfully with a test-shaped production config
/api/v1/health works through a temporary published test port
JWT private key is provided by runtime mount, not present in image filesystem
source tree/.git/.env are absent
```

- [ ] **Step 2: Create multi-stage Dockerfile**

Use a Java 25 JDK builder and Java 25 JRE runtime.

Shape:

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY apps/api/.mvn apps/api/.mvn
COPY apps/api/mvnw apps/api/pom.xml apps/api/
RUN cd apps/api && ./mvnw -B -DskipTests dependency:go-offline

COPY apps/api/src apps/api/src
RUN cd apps/api && ./mvnw -B -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar

USER 10001:10001
EXPOSE 8080 9091
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
```

Do not copy Maven cache, source, `.git` or secrets into runtime stage.

If Spring Boot produces multiple jars, make the Maven build copy the executable non-`original-` artifact deterministically; do not use an ambiguous wildcard in the final implementation.

- [ ] **Step 3: Add Docker ignore**

At minimum:

```text
target
.git
.env
.env.*
infra/secrets
**/*.pem
**/*.key
```

Do not ignore source required by build context.

- [ ] **Step 4: Add build metadata**

Expose application version/build revision through existing Actuator `info` using Maven filtered build info or Spring Boot build-info goal.

No git credentials are embedded.

- [ ] **Step 5: Build and inspect**

```bash
docker build -f apps/api/Dockerfile -t covie-api:plan10 .
docker image inspect covie-api:plan10 --format '{{.Config.User}}'
```

Expected user: `10001:10001`.

Run Trivy later in Task 15; this task only proves image shape/startup.

- [ ] **Step 6: Commit**

```bash
git add apps/api/Dockerfile apps/api/.dockerignore apps/api/pom.xml \
        infra/scripts/verify-api-image.sh
git commit -m "build(api): add nonroot production image"
```

---

## Task 3: Build a Next.js Standalone Non-root Production Image

**Files:**
- Modify: `apps/web/next.config.ts`
- Modify: `apps/web/lib/env/server.ts`
- Modify: `apps/web/lib/env/client.ts`
- Create: `apps/web/lib/env/public-origin.ts`
- Create: `apps/web/Dockerfile`
- Create: `apps/web/.dockerignore`
- Create: `infra/scripts/verify-web-image.sh`
- Test: `apps/web/tests/production/next-config.test.ts`

**Interfaces:**
- Produces a self-contained Next.js `standalone` runtime image.
- Browser API traffic remains same-origin; the Web image is not a BFF.

- [ ] **Step 1: Write configuration RED test**

Assert production Next config/runtime env has:

```text
output = "standalone"
no production rewrite that proxies /api/v1 through Next.js
development rewrite remains allowed
COVIE_PUBLIC_URL is read at Next server runtime, not baked as NEXT_PUBLIC_*
browser absolute links use window.location.origin
```

- [ ] **Step 2: Enable standalone output**

`next.config.ts`:

```ts
const apiOrigin = process.env.API_INTERNAL_BASE_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    if (process.env.NODE_ENV !== "development") return [];
    return [
      {
        source: "/api/v1/:path*",
        destination: `${apiOrigin}/api/v1/:path*`,
      },
    ];
  },
};
```

- [ ] **Step 3: Remove build-time public-origin coupling**

`apps/web/lib/env/server.ts` adds runtime-only:

```ts
COVIE_PUBLIC_URL: z.string().url()
```

`client.ts` no longer treats `NEXT_PUBLIC_APP_URL` as the canonical runtime origin. Add `public-origin.ts`:

```ts
export function browserPublicOrigin(): string {
  if (typeof window === "undefined") {
    throw new Error("browserPublicOrigin is browser-only");
  }
  return window.location.origin;
}
```

Server Components that need an absolute canonical/base URL use `serverEnv.COVIE_PUBLIC_URL`; Client Components use `browserPublicOrigin()`. Search the Web source and replace existing `clientEnv.NEXT_PUBLIC_APP_URL` usages.

This is an operations-hardening change: it allows the **same Web image digest** to run on staging and production with different runtime origins. No API routing depends on this value because browser API calls remain same-origin.

- [ ] **Step 4: Create multi-stage Web Dockerfile**

Use Node 24.

Stages:
1. deps: install pnpm 10 + workspace dependencies with frozen lockfile;
2. builder: copy `apps/web`, `packages/api-client`, workspace config and run `pnpm --filter @covie/web build`;
3. runner: copy `.next/standalone`, `.next/static`, `public`.

Runtime:
- `NODE_ENV=production`;
- `PORT=3000`;
- `HOSTNAME=0.0.0.0`;
- `USER 10001:10001`;
- start `node apps/web/server.js` or the exact standalone server path produced by the monorepo build.

Do not copy pnpm store or TypeScript source into runner unless standalone output requires a specific file.

- [ ] **Step 5: Build and inspect**

```bash
docker build -f apps/web/Dockerfile -t covie-web:plan10 .
docker image inspect covie-web:plan10 --format '{{.Config.User}}'
```

No environment-specific public origin is baked into the image. No secret may use `NEXT_PUBLIC_*`.

- [ ] **Step 6: Start and smoke**

```bash
docker run --rm -d --name covie-web-plan10 -p 33000:3000 covie-web:plan10
curl --fail http://127.0.0.1:33000/
docker rm -f covie-web-plan10
```

Expected: `200`.

- [ ] **Step 7: Commit**

```bash
git add apps/web/next.config.ts apps/web/lib/env/server.ts apps/web/lib/env/client.ts \
        apps/web/lib/env/public-origin.ts apps/web/Dockerfile apps/web/.dockerignore \
        apps/web/tests/production/next-config.test.ts \
        infra/scripts/verify-web-image.sh
git commit -m "build(web): add standalone production image"
```

---

## Task 4: Lock Same-domain HTTPS Nginx and Production Compose Topology

**Files:**
- Create: `infra/compose/compose.prod.yml`
- Create: `infra/docker/nginx.prod.conf`
- Create: `infra/docker/security-headers.conf`
- Create: `infra/scripts/verify-nginx-prod.sh`
- Create: `docs/operations/production-topology.md`

**Interfaces:**
- Consumes immutable API/Web images via environment image references.
- Produces one internal Docker network named `covie_internal` and one public Nginx ingress.

- [ ] **Step 1: Write Nginx/Compose static RED checks**

Script must fail unless:

```text
only nginx publishes host 80/443
api/web use expose only
/api/v1 routes to api:8080
/actuator is not proxied publicly
SSE location disables buffering
TLS redirect exists
security headers are included
API request body limit is bounded
```

- [ ] **Step 2: Create production Compose**

Core:

```yaml
services:
  api:
    image: ${COVIE_API_IMAGE:?COVIE_API_IMAGE is required}
    env_file:
      - ${COVIE_ENV_FILE:?COVIE_ENV_FILE is required}
    volumes:
      - ${COVIE_JWT_SECRET_DIR:?}:/run/covie-secrets:ro
    expose:
      - "8080"
      - "9091"
    networks: [internal]
    restart: unless-stopped
    stop_grace_period: 40s

  web:
    image: ${COVIE_WEB_IMAGE:?COVIE_WEB_IMAGE is required}
    env_file:
      - ${COVIE_ENV_FILE:?COVIE_ENV_FILE is required}
    expose:
      - "3000"
    networks: [internal]
    restart: unless-stopped

  nginx:
    image: nginx:1.28-alpine
    depends_on: [api, web]
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ../docker/nginx.prod.conf:/etc/nginx/nginx.conf:ro
      - ../docker/security-headers.conf:/etc/nginx/security-headers.conf:ro
      - ${COVIE_TLS_CERT_DIR:?}:/etc/nginx/tls:ro
    networks: [internal]
    restart: unless-stopped

networks:
  internal:
    name: covie_internal
```

Do not define production PostgreSQL/S3 as throwaway Docker volumes in this Compose file. Their lifecycle is external/managed by the production environment.

Apply bounded Docker stdout rotation to Nginx/API/Web:

```yaml
logging:
  driver: json-file
  options:
    max-size: "20m"
    max-file: "5"
```

This is host-disk protection, not a long-term log archive.

- [ ] **Step 3: Configure HTTPS, safe access logging and same-domain routing**

`nginx.prod.conf`:
- port 80 → 301 HTTPS;
- `server_name covie.vn www.covie.vn`;
- TLS certificate `/etc/nginx/tls/fullchain.pem`;
- key `/etc/nginx/tls/privkey.pem`;
- `/api/v1/` proxy to `http://api:8080`;
- `/` proxy to `http://web:3000`;
- do not add an `/actuator` proxy.

Proxy headers:

```nginx
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Real-IP $remote_addr;
```

Using `$remote_addr` rather than blindly forwarding client-provided chains means API sees a single Nginx-controlled source address.

Define a production access-log format that records `$request_method` and `$uri` but **not** `$request_uri`/`$args`:

```nginx
log_format covie_json escape=json
  '{"time":"$time_iso8601","remote":"$remote_addr","method":"$request_method",'
  '"uri":"$uri","status":$status,"bytes":$body_bytes_sent,'
  '"request_time":$request_time,"request_id":"$upstream_http_x_request_id"}';
access_log /var/log/nginx/access.log covie_json;
```

This prevents OAuth authorization `code`, search terms or other query parameters from being copied into routine Nginx access logs. Never add Cookie/Authorization headers to the format.

- [ ] **Step 4: Configure SSE separately**

Exact location before generic API:

```nginx
location = /api/v1/me/events {
    proxy_pass http://api:8080;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 35m;
    proxy_send_timeout 35m;
    add_header X-Accel-Buffering no always;
    include /etc/nginx/security-headers.conf;
}
```

No Nginx SSE cache/replay.

- [ ] **Step 5: Bound normal API payload**

Use:

```nginx
client_max_body_size 2m;
```

Media bytes upload directly to object storage, so the API does not need large multipart limits.

- [ ] **Step 6: Add security headers**

`security-headers.conf`:

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(self), camera=(), microphone=()" always;
add_header Content-Security-Policy "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; connect-src 'self'; img-src 'self' data: blob: https:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; upgrade-insecure-requests" always;
```

HSTS is enabled only after staging confirms HTTPS/certificate correctness and production owns all intended subdomains. If that prerequisite is not true, release is blocked rather than silently weakening transport.

- [ ] **Step 7: Verify config**

```bash
./infra/scripts/verify-nginx-prod.sh
COVIE_API_IMAGE=covie-api:plan10 \
COVIE_WEB_IMAGE=covie-web:plan10 \
COVIE_ENV_FILE=/tmp/covie-prod-test.env \
COVIE_JWT_SECRET_DIR=/tmp/covie-jwt \
COVIE_TLS_CERT_DIR=/tmp/covie-tls \
docker compose -f infra/compose/compose.prod.yml config
```

Expected: config parses and only Nginx has published ports.

- [ ] **Step 8: Commit**

```bash
git add infra/compose/compose.prod.yml infra/docker \
        infra/scripts/verify-nginx-prod.sh docs/operations/production-topology.md
git commit -m "ops: add same domain production topology"
```

---

## Task 5: Define Runtime Secret Contract and Secret Hygiene Gate

**Files:**
- Create: `infra/deploy/prod.env.example`
- Create: `infra/deploy/verify-runtime-env.sh`
- Create: `docs/operations/secrets.md`
- Modify: `.gitignore`
- Modify: `.github/workflows/ci.yml`
- Test: `apps/api/src/test/java/vn/covie/ops/SecretRedactionRegressionTest.java`

**Interfaces:**
- Produces one runtime-secret contract for deploy operators.
- Does not introduce an application secret-management API.

- [ ] **Step 1: Create production env template with names only**

`prod.env.example` includes the exact names from Task 1 and:

```text
COVIE_RATE_LIMIT_HMAC_KEY=
COVIE_BOOTSTRAP_REFERENCE_DATA_ENABLED=false
COVIE_BOOTSTRAP_REFERENCE_DATA_PATH=
COVIE_BOOTSTRAP_FIRST_ADMIN_EMAIL=
```

No example that resembles a real password/token/private key.

- [ ] **Step 2: Define secret delivery rules**

`docs/operations/secrets.md`:

```text
DB_PASSWORD                    -> host/cloud secret store -> runtime env
GOOGLE_OAUTH_CLIENT_SECRET     -> runtime env
AWS_SECRET_ACCESS_KEY          -> runtime env / workload credential
COVIE_RATE_LIMIT_HMAC_KEY      -> runtime env, >=32 random bytes
JWT private/public PEM         -> read-only mounted files
TLS private key                -> Nginx read-only mounted file
Grafana admin password         -> monitoring env secret
Alert webhook URL              -> read-only secret file (`url_file`), never committed
```

Host pilot contract:
- `/etc/covie/prod.env` owner root, mode `0600`;
- `/etc/covie/secrets/jwt-private.pem` readable only by deployment/container UID;
- no secret stored under repo checkout;
- rotate JWT/rate-limit key only through a documented maintenance operation.

- [ ] **Step 3: Validate runtime env without printing values**

`verify-runtime-env.sh` checks presence/nonblank, HTTPS shapes and file readability. It prints only variable names on failure.

HMAC key check:
- decode/base form accepted according to implementation;
- require at least 32 bytes of key material.

- [ ] **Step 4: Expand gitignore**

Ignore:

```text
infra/deploy/*.env
!infra/deploy/prod.env.example
infra/releases/
infra/secrets/
*.pem
*.key
```

Do not ignore source certificate fixtures required by tests if they are explicitly fake and under test resources; prefer generating keys dynamically.

- [ ] **Step 5: Add secret/log regression tests**

Test login/password/reset/media/checkout exception paths and assert captured logs do not contain:
- submitted password;
- raw refresh token;
- OAuth code;
- JWT;
- CSRF token;
- presigned URL query;
- `DB_PASSWORD`;
- exact buyer address in generic exception logging.

- [ ] **Step 6: Add CI secret hygiene**

Reuse existing CI action versions. Add shell gate:

```bash
git ls-files | grep -E '(^|/)(\.env|.*\.pem|.*\.key)$' && exit 1 || true
git grep -nE 'BEGIN (RSA )?PRIVATE KEY|GOOGLE_OAUTH_CLIENT_SECRET=.+|AWS_SECRET_ACCESS_KEY=.+' -- ':!*.md' ':!*.example' && exit 1 || true
```

Trivy secret scan is added in Task 15.

- [ ] **Step 7: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=SecretRedactionRegressionTest test
cd ../..
./infra/deploy/verify-runtime-env.sh --example infra/deploy/prod.env.example
git add infra/deploy/prod.env.example infra/deploy/verify-runtime-env.sh \
        docs/operations/secrets.md .gitignore .github/workflows/ci.yml \
        apps/api/src/test/java/vn/covie/ops/SecretRedactionRegressionTest.java
git commit -m "ops: define production secret contract"
```

---

## Task 6: Implement Redis Token-bucket Rate Limiting Core

**Files:**
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimitGroup.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimitPolicy.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimitProperties.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimitDecision.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimitFingerprintService.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RedisTokenBucketRateLimiter.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimitExceededException.java`
- Create: `apps/api/src/main/resources/ratelimit/token-bucket.lua`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/ErrorCode.java`
- Modify: `apps/api/src/main/java/vn/covie/ops/ProductionSafetyValidator.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/src/main/resources/application-prod.yml`
- Test: `apps/api/src/test/java/vn/covie/ratelimit/RedisTokenBucketRateLimiterIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/ratelimit/RateLimitFingerprintServiceTest.java`

**Interfaces:**
- Consumes existing Spring Data Redis.
- Produces an atomic limiter callable by HTTP and auth-specific adapters.

- [ ] **Step 1: Define policy groups**

```java
public enum RateLimitGroup {
    AUTH_LOGIN_IP,
    AUTH_LOGIN_EMAIL,
    AUTH_GOOGLE_START,
    AUTH_REFRESH_IP,
    AUTH_REFRESH_TOKEN,
    PUBLIC_DISCOVERY,
    CHECKOUT,
    ORDER_COMMAND,
    NEED_CREATE,
    NEED_RESPONSE,
    REPORT,
    MEDIA_UPLOAD,
    ADMIN
}
```

Policy record:

```java
public record RateLimitPolicy(
        int capacity,
        int refillTokens,
        Duration refillPeriod,
        boolean failClosed) {}
```

`RateLimitProperties` also has:

```java
boolean enabled
String hmacKey
Map<RateLimitGroup, RateLimitPolicy> policies
```

Generic `application.yml` defaults `covie.rate-limit.enabled=false` so local/test contexts that do not need Redis abuse-control still start. Production explicitly enables it.

- [ ] **Step 2: Bind explicit pilot defaults**

`application-prod.yml`:

```yaml
covie:
  rate-limit:
    enabled: true
    hmac-key: ${COVIE_RATE_LIMIT_HMAC_KEY:}
    policies:
      AUTH_LOGIN_IP:       { capacity: 10, refill-tokens: 10, refill-period: 1m, fail-closed: true }
      AUTH_LOGIN_EMAIL:    { capacity: 5,  refill-tokens: 5,  refill-period: 1m, fail-closed: true }
      AUTH_GOOGLE_START:   { capacity: 20, refill-tokens: 20, refill-period: 1m, fail-closed: true }
      AUTH_REFRESH_IP:     { capacity: 30, refill-tokens: 30, refill-period: 1m, fail-closed: true }
      AUTH_REFRESH_TOKEN:  { capacity: 30, refill-tokens: 30, refill-period: 1m, fail-closed: true }
      PUBLIC_DISCOVERY:    { capacity: 120, refill-tokens: 120, refill-period: 1m, fail-closed: false }
      CHECKOUT:            { capacity: 30, refill-tokens: 30, refill-period: 1m, fail-closed: false }
      ORDER_COMMAND:       { capacity: 60, refill-tokens: 60, refill-period: 1m, fail-closed: false }
      NEED_CREATE:         { capacity: 10, refill-tokens: 10, refill-period: 1h, fail-closed: true }
      NEED_RESPONSE:       { capacity: 30, refill-tokens: 30, refill-period: 1h, fail-closed: true }
      REPORT:              { capacity: 10, refill-tokens: 10, refill-period: 1h, fail-closed: true }
      MEDIA_UPLOAD:        { capacity: 30, refill-tokens: 30, refill-period: 1h, fail-closed: true }
      ADMIN:               { capacity: 120, refill-tokens: 120, refill-period: 1m, fail-closed: true }
```

These are pilot starting values. Change only after observing real metrics.

- [ ] **Step 3: Implement HMAC fingerprints**

`RateLimitFingerprintService` uses `HmacSHA256` with `COVIE_RATE_LIMIT_HMAC_KEY`.

Methods:

```java
String email(String normalizedEmail)
String opaqueCredential(String rawCredential)
String ip(String normalizedIp)
```

Return URL-safe/base64 or lowercase hex HMAC. Never log the input.

Plain `SHA-256(email)` is forbidden.

- [ ] **Step 4: Implement atomic Lua token bucket**

Redis key:

```text
covie:rl:<group>:<fingerprint>
```

Hash fields:

```text
tokens
last_refill_ms
```

The Lua script:
1. loads current tokens/timestamp;
2. refills proportional to elapsed complete refill intervals;
3. caps at capacity;
4. consumes one token if available;
5. writes state + TTL slightly longer than time-to-full;
6. returns `[allowed,remaining,retryAfterMillis]`.

Do not perform GET/SET as separate Java calls.

- [ ] **Step 5: Implement Redis failure semantics**

`RedisTokenBucketRateLimiter.require(group,key)`:
- allowed -> return decision;
- denied -> throw `RateLimitExceededException` carrying rounded-up retry seconds;
- Redis error + `failClosed=true` -> throw `RATE_LIMITER_UNAVAILABLE`, HTTP 503;
- Redis error + `failClosed=false` -> allow request, record fail-open metric/log in Task 9.

Add error:

```text
RATE_LIMITER_UNAVAILABLE
```

No business mutation is considered successful merely because limiter was unavailable.

- [ ] **Step 6: Extend production startup safety for rate limiting**

When `prod` is active, `ProductionSafetyValidator` now also requires:

```text
covie.rate-limit.enabled == true
COVIE_RATE_LIMIT_HMAC_KEY contains >=32 bytes of key material
all required RateLimitGroup policies are present
```

Never log the HMAC key.

- [ ] **Step 7: Test real Redis atomicity**

Use Redis Testcontainer/local-compatible integration:
- 100 concurrent consumes against capacity 10 -> exactly 10 allowed;
- refill after fixed clock/time simulation according to script args;
- TTL exists;
- same raw email produces same HMAC but raw email absent from Redis key;
- different HMAC key changes fingerprint.

- [ ] **Step 8: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=RedisTokenBucketRateLimiterIntegrationTest,RateLimitFingerprintServiceTest test
git add src/main/java/vn/covie/ratelimit \
        src/main/resources/ratelimit \
        src/main/java/vn/covie/shared/web/ErrorCode.java \
        src/main/java/vn/covie/ops/ProductionSafetyValidator.java \
        src/main/resources/application.yml \
        src/main/resources/application-prod.yml \
        src/test/java/vn/covie/ratelimit
git commit -m "feat(rate-limit): add atomic redis token buckets"
```

---

## Task 7: Apply Endpoint-group and Credential-aware Rate Limits

**Files:**
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimited.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimitInterceptor.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/RateLimitWebConfiguration.java`
- Create: `apps/api/src/main/java/vn/covie/ratelimit/ClientIpResolver.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/api/AuthController.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/api/GoogleAuthController.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/application/EmailPasswordLoginService.java`
- Modify: `apps/api/src/main/java/vn/covie/auth/application/RefreshTokenService.java`
- Modify: `apps/api/src/main/java/vn/covie/catalog/api/PublicCatalogController.java`
- Modify: `apps/api/src/main/java/vn/covie/checkout/api/CheckoutController.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/api/OrderController.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/api/MeNeedController.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/api/NeedResponseController.java`
- Modify: `apps/api/src/main/java/vn/covie/media/api/MediaController.java`
- Modify: `apps/api/src/main/java/vn/covie/moderation/api/ReportController.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/GlobalExceptionHandler.java`
- Test: `apps/api/src/test/java/vn/covie/ratelimit/RateLimitHttpIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/auth/application/AuthRateLimitIntegrationTest.java`

**Interfaces:**
- Applies Task 6 limiter without reading request bodies in generic interceptors.
- Keeps business limit and authorization semantics separate.

- [ ] **Step 1: Define annotation**

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    RateLimitGroup value();
}
```

- [ ] **Step 2: Resolve keys safely**

Generic interceptor key dimensions:
- anonymous group -> client IP;
- authenticated user group -> user UUID;
- NEED_RESPONSE -> `userId + merchantId` from URI template variables;
- ADMIN -> admin user UUID.

`ClientIpResolver` trusts the request address after Spring forwarded-header processing. Production safety relies on API not being publicly exposed and Nginx overwriting forwarded IP headers.

Never use arbitrary client-supplied `X-User-Id`.

- [ ] **Step 3: Annotate groups**

At minimum:

```text
GET  catalog discovery/detail       -> PUBLIC_DISCOVERY where appropriate
POST checkouts preview/commit       -> CHECKOUT
POST Order commands                 -> ORDER_COMMAND
POST /me/need-requests              -> NEED_CREATE
seller Need response mutations      -> NEED_RESPONSE
POST /reports                       -> REPORT
POST /media/uploads                 -> MEDIA_UPLOAD
/api/v1/admin/** path fallback       -> ADMIN
Google start                         -> AUTH_GOOGLE_START
login                               -> AUTH_LOGIN_IP
refresh                             -> AUTH_REFRESH_IP
```

For `/api/v1/admin/**`, `RateLimitInterceptor` applies `ADMIN` by URI prefix before annotation lookup, so every present/future Admin controller is covered without duplicating annotations.

Do not rate-limit SSE heartbeat per message; opening the SSE endpoint can use a modest authenticated connection guard if later evidence needs it, but V1 does not add it here.

- [ ] **Step 4: Add credential-aware login limiter inside login service**

After normalization and **before identity lookup/password work**:

```text
fingerprint = HMAC(normalized email)
require AUTH_LOGIN_EMAIL
```

The generic interceptor separately enforces IP.

Audit may record the HMAC fingerprint reference after Task 7, never clear email/password.

- [ ] **Step 5: Add refresh credential limiter**

Before DB token lookup/rotation:
- HMAC raw refresh cookie;
- require `AUTH_REFRESH_TOKEN`;
- generic IP limiter already ran.

Do not replace existing SHA token fingerprint used for DB lookup; HMAC rate-limit key is an additional non-persistent Redis identity.

- [ ] **Step 6: Return `Retry-After`**

`RateLimitExceededException` extends/uses the existing API exception semantics and exposes `retryAfterSeconds`. Add a dedicated `GlobalExceptionHandler` branch that returns HTTP 429 and:

```http
Retry-After: <integer seconds>
```

Translate retry duration into integer seconds rounded up, minimum `1`.

Standard body stays:

```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests.",
    "requestId": "req_example_01"
  }
}
```

- [ ] **Step 7: Prove limiter cannot bypass business/security**

Tests:
- allowed request still fails normal authorization if actor lacks permission;
- rate-limit denial happens without Order/Need/Review write;
- after bucket refill normal business validation resumes;
- Redis fail-open CHECKOUT still cannot duplicate Order due Idempotency-Key;
- Redis fail-open ORDER_COMMAND still obeys state lock/idempotency;
- fail-closed REPORT creates no report;
- raw email/token absent from logs and Redis keys.

- [ ] **Step 8: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=RateLimitHttpIntegrationTest,AuthRateLimitIntegrationTest test
git add src/main/java/vn/covie/ratelimit \
        src/main/java/vn/covie/auth \
        src/main/java/vn/covie/catalog/api/PublicCatalogController.java \
        src/main/java/vn/covie/checkout/api/CheckoutController.java \
        src/main/java/vn/covie/orders/api/OrderController.java \
        src/main/java/vn/covie/needs/api \
        src/main/java/vn/covie/media/api/MediaController.java \
        src/main/java/vn/covie/moderation/api/ReportController.java \
        src/main/java/vn/covie/shared/web/GlobalExceptionHandler.java \
        src/test/java/vn/covie/ratelimit \
        src/test/java/vn/covie/auth/application/AuthRateLimitIntegrationTest.java
git commit -m "feat(rate-limit): protect v1 endpoint groups"
```

---

## Task 8: Wire Bounded Technical-data Retention Jobs

**Files:**
- Create: `apps/api/src/main/java/vn/covie/maintenance/application/TechnicalRetentionService.java`
- Create: `apps/api/src/main/java/vn/covie/maintenance/application/TechnicalRetentionScheduler.java`
- Create: `apps/api/src/main/java/vn/covie/maintenance/persistence/TechnicalRetentionRepository.java`
- Create: `apps/api/src/main/java/vn/covie/maintenance/config/RetentionProperties.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/src/main/resources/application-prod.yml`
- Test: `apps/api/src/test/java/vn/covie/maintenance/application/TechnicalRetentionIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/maintenance/application/TechnicalRetentionConcurrencyTest.java`

**Interfaces:**
- Purges only technical ephemeral rows.
- Reuses existing `MediaCleanupService.cleanupExpiredPending(int limit)`.

- [ ] **Step 1: Define retention policy**

Defaults:

```yaml
covie:
  retention:
    checkout-session-safety-window: 24h
    idempotency-expired-window: 0s
    refresh-token-history: 45d
    auth-action-ticket-history: 7d
    published-outbox-history: 7d
    terminal-failed-outbox-history: 30d
    batch-size: 200
    max-batches-per-run: 10
    schedule: "0 20 3 * * *"
```

Do not delete active/unexpired credential records.

- [ ] **Step 2: Implement bounded `SKIP LOCKED` deletes**

Use JDBC CTE pattern per table:

```sql
with doomed as (
    select id
    from idempotency_records
    where expires_at < :now
    order by expires_at, id
    for update skip locked
    limit :limit
)
delete from idempotency_records t
using doomed d
where t.id = d.id;
```

Equivalent predicates:

```text
checkout_sessions:
  expires_at < now - 24h safety window

auth_refresh_tokens:
  expires/revoked/used terminal age < now - 45d
  and parent auth session is no longer ACTIVE/unexpired

auth_action_tickets:
  expired/consumed and terminal age < now - 7d

outbox_events PUBLISHED:
  published_at < now - 7d

outbox_events terminal FAILED:
  next_attempt_at is null
  and age < now - 30d
```

Do not delete `auth_sessions` in V1 retention. Their volume is small and keeping session history simplifies security audit.

- [ ] **Step 3: Retain source-of-truth business data**

An explicit integration test snapshots row counts and proves retention never deletes:

```text
users
audit_logs
merchant_profiles
catalog_items
orders
order_items
order_events
need_requests
need_responses
reviews
reputation stats/outcomes
notifications
moderation_cases/actions
system_setting_history
inventory_movements
```

- [ ] **Step 4: Wire media cleanup**

After DB technical cleanup, scheduler calls:

```java
mediaCleanupService.cleanupExpiredPending(100);
```

If existing media selection can race across replicas, ensure transition-to-DELETED is conditional and object deletion remains `deleteIfExists`; duplicate storage delete is harmless.

- [ ] **Step 5: Bounded scheduler**

One scheduled invocation runs at most configured `maxBatchesPerRun` for each table. No unbounded `while(true)`.

Multiple API replicas may run it; `SKIP LOCKED` makes DB deletes safe.

- [ ] **Step 6: Test concurrency and retention boundaries**

Two schedulers in parallel:
- no deadlock;
- terminal rows deleted once;
- active rows survive;
- business rows survive.

- [ ] **Step 7: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=TechnicalRetentionIntegrationTest,TechnicalRetentionConcurrencyTest test
git add src/main/java/vn/covie/maintenance \
        src/main/resources/application.yml \
        src/main/resources/application-prod.yml \
        src/test/java/vn/covie/maintenance
git commit -m "feat(ops): add bounded technical retention jobs"
```

---

## Task 9: Add Structured Logging and Operational Micrometer Metrics

**Files:**
- Modify: `apps/api/pom.xml`
- Create: `apps/api/src/main/java/vn/covie/observability/ApiErrorMetrics.java`
- Create: `apps/api/src/main/java/vn/covie/observability/OutboxOperationalMetrics.java`
- Create: `apps/api/src/main/java/vn/covie/observability/OutboxOperationalMetricsRepository.java`
- Create: `apps/api/src/main/java/vn/covie/observability/ScheduledJobMetrics.java`
- Create: `apps/api/src/main/java/vn/covie/observability/RateLimitMetrics.java`
- Modify: `apps/api/src/main/java/vn/covie/shared/web/GlobalExceptionHandler.java`
- Modify: `apps/api/src/main/java/vn/covie/realtime/SseConnectionRegistry.java`
- Modify: `apps/api/src/main/java/vn/covie/realtime/SseHeartbeatScheduler.java`
- Modify: `apps/api/src/main/java/vn/covie/outbox/application/OutboxScheduler.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderScheduler.java`
- Modify: `apps/api/src/main/java/vn/covie/needs/scheduling/NeedExpirationScheduler.java`
- Modify: `apps/api/src/main/java/vn/covie/maintenance/application/TechnicalRetentionScheduler.java`
- Modify: `apps/api/src/main/java/vn/covie/ratelimit/RedisTokenBucketRateLimiter.java`
- Modify: `apps/api/src/main/resources/application-prod.yml`
- Test: `apps/api/src/test/java/vn/covie/observability/OperationalMetricsIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/observability/StructuredLoggingSafetyTest.java`

**Interfaces:**
- Produces `/actuator/prometheus` on internal management port.
- Metrics contain low-cardinality labels only.

- [ ] **Step 1: Add Prometheus registry**

Add:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Spring Boot Actuator already exists.

- [ ] **Step 2: Keep built-in structured JSON**

Production config uses:

```yaml
logging:
  structured:
    format:
      console: logstash
```

Existing MDC `requestId` flows into structured JSON. Add only safe IDs such as `userId`, `merchantId`, `orderId` around scoped operations and clear MDC in `finally`.

Do not log free-form bodies.

- [ ] **Step 3: Record API error metrics centrally**

`ApiErrorMetrics.record(code,status)`:

```text
covie.api.errors{code,status}
```

Only stable code + status labels. No userId/requestId/path ID label.

`GlobalExceptionHandler` records mapped `ApiException` outcomes. Security-filter errors can add their own low-cardinality counters where necessary.

This gives checkout/inventory conflict alert queries through stable error codes without instrumenting every service.

- [ ] **Step 4: Add Outbox operational gauges**

Every 15 seconds query and cache:

```text
due count:
  PENDING + due FAILED

oldest due age seconds

terminal failed count:
  FAILED and next_attempt_at is null
```

Expose:

```text
covie.outbox.due
covie.outbox.oldest_due_seconds
covie.outbox.terminal_failed
```

Do not execute a DB query for every individual Prometheus scrape gauge callback; scheduled refresh updates `AtomicLong` values.

- [ ] **Step 5: Expose SSE connection gauge**

Add:

```java
public int connectionCount()
```

to existing `SseConnectionRegistry`.

Register:

```text
covie.sse.connections
```

Do not label by user.

- [ ] **Step 6: Add scheduler counters**

`ScheduledJobMetrics`:

```text
covie.scheduler.runs{job}
covie.scheduler.failures{job}
covie.scheduler.duration
```

Allowed `job` values are fixed:
`outbox`, `orders`, `needs`, `retention`, `sse-heartbeat`.

Wrap scheduler entrypoints; exceptions still log sanitized diagnostics.

- [ ] **Step 7: Rate-limit metrics**

Expose:

```text
covie.ratelimit.denied{group}
covie.ratelimit.redis_fail_open{group}
covie.ratelimit.redis_fail_closed{group}
```

Group is enum only. Never fingerprint as label.

- [ ] **Step 8: Verify built-in metrics**

Integration test asserts Prometheus endpoint contains:
- `http_server_requests`;
- Hikari/DataSource metrics;
- custom CoVie metrics.

Management port is not tested through public Nginx.

- [ ] **Step 9: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=OperationalMetricsIntegrationTest,StructuredLoggingSafetyTest test
git add pom.xml src/main/java/vn/covie/observability \
        src/main/java/vn/covie/shared/web/GlobalExceptionHandler.java \
        src/main/java/vn/covie/realtime/SseConnectionRegistry.java \
        src/main/java/vn/covie/realtime/SseHeartbeatScheduler.java \
        src/main/java/vn/covie/outbox/application/OutboxScheduler.java \
        src/main/java/vn/covie/orders/application/OrderScheduler.java \
        src/main/java/vn/covie/needs/scheduling/NeedExpirationScheduler.java \
        src/main/java/vn/covie/maintenance/application/TechnicalRetentionScheduler.java \
        src/main/java/vn/covie/ratelimit/RedisTokenBucketRateLimiter.java \
        src/main/resources/application-prod.yml \
        src/test/java/vn/covie/observability
git commit -m "feat(observability): expose pilot operational metrics"
```

---

## Task 10: Provision Pilot Prometheus, Grafana, Alertmanager and Host Alerts

**Files:**
- Create: `infra/compose/compose.monitoring.yml`
- Create: `infra/monitoring/prometheus.yml`
- Create: `infra/monitoring/alerts.yml`
- Create: `infra/monitoring/alertmanager.yml`
- Create: `infra/monitoring/grafana/provisioning/datasources/prometheus.yml`
- Create: `infra/monitoring/grafana/provisioning/dashboards/provider.yml`
- Create: `infra/monitoring/grafana/dashboards/covie-operations.json`
- Create: `infra/scripts/verify-monitoring-config.sh`
- Create: `docs/operations/observability.md`

**Interfaces:**
- Scrapes only internal metrics.
- Sends alerts through one operator-configured webhook; webhook secret is runtime-only.

- [ ] **Step 1: Pin monitoring images**

`compose.monitoring.yml`:

```text
prom/prometheus:v3.11.3
grafana/grafana:13.1.0
prom/alertmanager:v0.32.1
prom/node-exporter:v1.11.1
```

Monitoring joins external Docker network `covie_internal`.

`alertmanager` mounts:

```yaml
volumes:
  - ../monitoring/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
  - ${COVIE_ALERT_WEBHOOK_FILE:?}:/run/secrets/alert-webhook-url:ro
```

`grafana` requires runtime secret:

```yaml
environment:
  GF_SECURITY_ADMIN_PASSWORD: ${COVIE_GRAFANA_ADMIN_PASSWORD:?}
  GF_USERS_ALLOW_SIGN_UP: "false"
```

Bind UIs to loopback only:

```text
127.0.0.1:9090 -> Prometheus
127.0.0.1:3001 -> Grafana
127.0.0.1:9093 -> Alertmanager
```

Remote operator access goes through SSH tunnel/VPN, not public internet.

- [ ] **Step 2: Configure Prometheus**

Scrape:
- `api:9091/actuator/prometheus` every 15s;
- `node-exporter:9100`.

Prometheus itself is not a long-term archive. Pilot retention can start at 15 days with bounded disk size.

- [ ] **Step 3: Configure Alertmanager**

Use Alertmanager generic webhook `url_file`, not an environment placeholder inside committed YAML:

```yaml
receivers:
  - name: operator-webhook
    webhook_configs:
      - url_file: /run/secrets/alert-webhook-url
        send_resolved: true
```

`compose.monitoring.yml` bind-mounts `${COVIE_ALERT_WEBHOOK_FILE}` read-only to that path. The committed config never contains the real URL.

Group alerts by `alertname`/`severity`, with reasonable group wait to avoid storms.

- [ ] **Step 4: Add alert rules**

Required:

```text
ApiDown:
  up{job="covie-api"} == 0 for 2m

Api5xxHigh:
  5xx / all requests > 2% over 5m
  AND request volume above a small floor

ApiP95LatencyHigh:
  p95 > 1s for 10m

DbPoolSaturation:
  active/max > 0.8 for 5m

OutboxBacklogOld:
  covie_outbox_oldest_due_seconds > 60 for 5m

OutboxTerminalFailure:
  covie_outbox_terminal_failed > 0 for 1m

SchedulerFailures:
  increase(covie_scheduler_failures_total[5m]) > 0

RateLimiterFailOpen:
  increase(covie_ratelimit_redis_fail_open_total[5m]) > 0

HostDiskLow:
  available/root filesystem < 15%

BackupStale:
  absent(covie_postgres_backup_timestamp_seconds)
  OR time() - covie_postgres_backup_timestamp_seconds > 93600
```

Actual Micrometer metric names must be checked with `/actuator/prometheus`; update PromQL to emitted names instead of guessing suffixes.

- [ ] **Step 5: Create minimal Grafana dashboard**

Panels use fixed low-cardinality PromQL, for example:

```text
Request rate:
  sum(rate(http_server_requests_seconds_count[5m]))

5xx ratio:
  sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))

P95 latency:
  histogram_quantile(0.95,
    sum by (le) (rate(http_server_requests_seconds_bucket[5m])))

Outbox oldest:
  covie_outbox_oldest_due_seconds

SSE connections:
  covie_sse_connections

Backup age:
  time() - covie_postgres_backup_timestamp_seconds
```

Also panel Hikari active/max, outbox due/terminal failed, scheduler failures, rate-limit deny/fail-open, CPU/memory/disk. Confirm exact Spring/Micrometer metric names from the running `/actuator/prometheus` before committing dashboard queries.

No buyer/seller PII in labels or dashboard variables.

Because Prometheus/Alertmanager live on the same pilot host, configure one **independent external HTTPS uptime check** outside that host for `https://covie.vn/api/v1/health`. It may be supplied by the hosting provider or another independent monitor; it must notify the Incident Lead after repeated failures. Same-host Prometheus alone cannot detect/report a total host/network outage.

- [ ] **Step 6: Validate configs**

Use container binaries:

```bash
docker run --rm -v "$PWD/infra/monitoring:/etc/prometheus:ro" \
  prom/prometheus:v3.11.3 \
  promtool check config /etc/prometheus/prometheus.yml

docker run --rm -v "$PWD/infra/monitoring:/etc/prometheus:ro" \
  prom/prometheus:v3.11.3 \
  promtool check rules /etc/prometheus/alerts.yml
```

Validate Alertmanager config with its config checker if included in image.

- [ ] **Step 7: Commit**

```bash
git add infra/compose/compose.monitoring.yml infra/monitoring \
        infra/scripts/verify-monitoring-config.sh \
        docs/operations/observability.md
git commit -m "ops: add pilot metrics dashboards and alerts"
```

---

## Task 11: Implement PostgreSQL Backup, Restore and Backup-age Evidence

**Files:**
- Create: `infra/backup/postgres-backup.sh`
- Create: `infra/backup/postgres-restore.sh`
- Create: `infra/backup/verify-backup.sh`
- Create: `infra/backup/covie-backup.prom.example`
- Create: `docs/operations/backup-restore.md`
- Modify: `infra/compose/compose.monitoring.yml`
- Modify: `infra/monitoring/alerts.yml`

**Interfaces:**
- Produces portable logical backups and a mandatory restore rehearsal.
- PITR remains a provider capability, not implemented in application code.

- [ ] **Step 1: Define pilot recovery objectives**

Document operator objectives:

```text
Logical backup: daily
Backup freshness alert: >26h
Pilot logical-backup RPO objective: <=24h
Preferred RPO with provider PITR/WAL: <=15m when available
Pilot restore RTO objective: <=4h
```

These are operational objectives, not a customer SLA.

- [ ] **Step 2: Implement `postgres-backup.sh`**

Require standard `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`.

Run:

```bash
pg_dump \
  --format=custom \
  --compress=6 \
  --no-owner \
  --no-acl \
  --file "$tmp_file" \
  "$PGDATABASE"
```

Then:
- `pg_restore --list` sanity check;
- SHA-256 checksum;
- atomically rename to timestamped `.dump`;
- mode `0600`;
- write metadata file with DB name, UTC timestamp, checksum, highest Flyway version;
- update node_exporter textfile collector atomically:

```text
covie_postgres_backup_timestamp_seconds <epoch>
```

Do not echo DB password.

- [ ] **Step 3: Define off-host encrypted storage requirement**

The script may write local staging backup, but runbook requires copying to a restricted encrypted backup destination outside the application host.

Provider configuration must have:
- server-side encryption / encrypted volume;
- private access;
- retention policy;
- operator-only restore permission.

A local Docker volume or same-host filesystem copy does not satisfy off-host backup.

- [ ] **Step 4: Implement restore script**

Safety:
- requires explicit `COVIE_RESTORE_CONFIRM=RESTORE`;
- refuses database name matching configured production DB unless `COVIE_ALLOW_PRODUCTION_RESTORE=true` and runbook change window exists;
- verifies checksum;
- restores into empty target DB with `pg_restore --no-owner --no-acl`;
- does not overwrite source backup.

- [ ] **Step 5: Verify restored migration compatibility**

`verify-backup.sh` after restore:
1. query `flyway_schema_history`;
2. start/test API against restored DB with Flyway validation;
3. run critical read smoke;
4. compare invariant counts for Orders/OrderEvents/InventoryMovements/Reviews.

Do not “repair” Flyway checksum automatically.

- [ ] **Step 6: Document PITR**

When hosting supports PITR:
- enable continuous WAL/PITR with provider;
- rehearse provider restore to a new instance;
- keep daily logical dump for portability/independent recovery path.

- [ ] **Step 7: Wire backup stale metric to node_exporter**

Monitoring compose mounts a read-only host textfile collector directory to node_exporter and enables `--collector.textfile.directory`.

Alert from Task 10 uses the metric.

- [ ] **Step 8: Run a local rehearsal**

Against disposable local DB:
- seed known Orders/Reviews;
- backup;
- drop target;
- restore new DB;
- run verify;
- prove source DB untouched.

- [ ] **Step 9: Commit**

```bash
git add infra/backup infra/compose/compose.monitoring.yml \
        infra/monitoring/alerts.yml docs/operations/backup-restore.md
git commit -m "ops: add postgres backup and restore rehearsal"
```

---

## Task 12: Harden Production S3-compatible Media Storage

**Files:**
- Create: `infra/storage/cors.json`
- Create: `infra/storage/lifecycle.json`
- Create: `docs/operations/object-storage.md`
- Create: `apps/api/src/test/java/vn/covie/media/storage/ProductionStorageContractTest.java`
- Modify: `apps/api/src/main/resources/application-prod.yml`

**Interfaces:**
- Preserves Plan 03 direct presigned upload.
- Does not give application credentials bucket-administration permission.

- [ ] **Step 1: Define bucket security contract**

Production origin bucket:
- private/no anonymous list;
- object keys generated by backend only;
- versioning enabled;
- encryption at rest;
- application principal limited to required object read/head/put/delete in the CoVie media bucket/prefix;
- public delivery through the configured media/CDN URL or a deliberately public read layer;
- no S3 credentials exposed to browser.

- [ ] **Step 2: Create CORS policy template**

Use this S3-compatible baseline:

```json
[
  {
    "AllowedOrigins": ["https://covie.vn"],
    "AllowedMethods": ["PUT", "HEAD"],
    "AllowedHeaders": ["Content-Type", "x-amz-*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 600
  }
]
```

Do not allow `*` origin with credentials. Provider syntax may need a thin translation, but permissions must not become broader than this baseline.

- [ ] **Step 3: Create lifecycle template**

`lifecycle.json` baseline:

```json
{
  "Rules": [
    {
      "ID": "AbortIncompleteMultipartUploads",
      "Status": "Enabled",
      "Filter": {"Prefix": ""},
      "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 1}
    }
  ]
}
```

Do not automatically delete READY current objects on a short generic TTL. Enable bucket versioning separately and retain noncurrent versions according to operator cost/recovery policy.

Database `MediaCleanupService` remains responsible for application orphan semantics; bucket lifecycle does not decide which CoVie media row is authoritative.

- [ ] **Step 4: Production storage configuration**

Require:
- bucket;
- region;
- public base URL;
- 10-minute presign TTL from existing plan;
- path-style false unless provider requires;
- max image remains 10 MiB unless product policy changes.

No local MinIO default leaks into prod.

- [ ] **Step 5: Contract test**

Using S3-compatible integration fixture:
- presigned upload works only for generated key;
- wrong signed content type fails/complete fails;
- READY URL contains public base, not secret endpoint;
- browser response never contains bucket credential;
- storage exception does not expose signed query in log.

- [ ] **Step 6: Document outage behavior**

S3 unavailable:
- existing DB transaction features continue;
- media upload/complete fails with controlled error;
- operators do not mark DB media READY manually;
- do not take whole API readiness down solely for S3 outage;
- alert/incident runbook guides recovery.

- [ ] **Step 7: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=ProductionStorageContractTest,S3ObjectStorageTest test
cd ../..
git add infra/storage docs/operations/object-storage.md \
        apps/api/src/main/resources/application-prod.yml \
        apps/api/src/test/java/vn/covie/media/storage/ProductionStorageContractTest.java
git commit -m "ops: harden production media storage contract"
```

---

## Task 13: Build Realistic Performance Dataset and Query-plan Gate

**Files:**
- Create: `apps/api/src/test/java/vn/covie/performance/PerformanceFixtureBuilder.java`
- Create: `apps/api/src/test/java/vn/covie/performance/HotQueryPlanIntegrationTest.java`
- Create: `infra/perf/run-query-plan-checks.sh`
- Create: `docs/operations/performance.md`

**Interfaces:**
- Reuses actual repositories/query services.
- Does not add an index unless query-plan/load evidence proves one is needed.
- Performance dataset tests are tagged `@Tag("performance")` and run in release/staging gates, not every fast PR loop.

- [ ] **Step 1: Build deterministic staging-scale fixture**

Test fixture sizes:

```text
location_areas:            100+
merchant_profiles:       1,000
merchant_locations:      2,000
catalog_items:          20,000
variants:               30,000
location_variants:      40,000
need_requests:          10,000
orders:                 50,000
order_events:          200,000
notifications:         100,000
outbox historical/due:  50,000
```

Use bulk JDBC/generate-series helpers inside test setup; do not create thousands of JPA transactions.

Fixture contains realistic active/inactive mix and geospatial spread around the pilot area.

- [ ] **Step 2: Re-run existing hot query integration tests**

Include:
- catalog discovery by query/category/location;
- delivery coverage radius/areas;
- seller Need discovery;
- active carts by user;
- seller Order cursor/location;
- Order scheduler due claim;
- Outbox due claim;
- notification cursor list.

- [ ] **Step 3: Capture JSON EXPLAIN plans**

For each exact SQL statement exercised by the hot-query integration test, prepend the PostgreSQL clause `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` and execute the same bound parameters used by the repository test. The test helper receives the complete SQL string, not a rewritten approximation.

Persist plan output only as CI artifact/local output, not as a giant committed snapshot tied to PostgreSQL planner noise.

- [ ] **Step 4: Assert critical planner invariants, not exact costs**

Examples:
- geospatial query uses GIST-supported path for large candidate set;
- notification user+created index participates;
- outbox due scan does not sequentially scan all historical rows;
- seller Order query does not sort all 50k rows after filtering one merchant/location;
- keyset queries do not use OFFSET.

Do not assert exact milliseconds in JUnit.

- [ ] **Step 5: Evidence rule for new index**

If a hot query fails:
1. save EXPLAIN before;
2. identify missing/incorrect index;
3. create a separate DB-review change using next migration `V014`;
4. rerun EXPLAIN/load;
5. update this Plan's migration handoff.

Do not edit V001–V013.

- [ ] **Step 6: Run and commit test infrastructure**

```bash
cd apps/api
./mvnw -Dtest=HotQueryPlanIntegrationTest -Dgroups=performance test
cd ../..
./infra/perf/run-query-plan-checks.sh
git add apps/api/src/test/java/vn/covie/performance \
        infra/perf/run-query-plan-checks.sh \
        docs/operations/performance.md
git commit -m "test(perf): gate hot postgres query plans"
```

---

## Task 14: Add HTTP Load, Checkout Soak and Concurrency Release Gates

**Files:**
- Create: `infra/perf/run-load.mjs`
- Create: `infra/perf/scenarios.mjs`
- Create: `apps/api/src/test/java/vn/covie/performance/CheckoutSoakConcurrencyTest.java`
- Create: `apps/api/src/test/java/vn/covie/performance/OrderCommandSoakConcurrencyTest.java`
- Modify: `docs/operations/performance.md`

**Interfaces:**
- Uses Node 24 native `fetch`; no new load-test runtime dependency.
- Mutating correctness remains tested in PostgreSQL integration tests, not inferred from HTTP latency alone.

- [ ] **Step 1: Implement read-load harness**

CLI:

```bash
node infra/perf/run-load.mjs \
  --base-url https://staging.covie.vn \
  --scenario public-search \
  --concurrency 50 \
  --duration 120s
```

Collect:
- total;
- success/error;
- p50/p95/p99;
- max;
- status counts.

Never print response bodies containing private data.

- [ ] **Step 2: Define pilot read objectives**

Staging objective:

```text
public catalog search:
  concurrency 50
  p95 <= 500ms
  errors <1%

public item detail:
  concurrency 50
  p95 <= 300ms
  errors <1%

authenticated Order list:
  concurrency 20
  p95 <= 500ms
  errors <1%

overall:
  Hikari sustained active/max < 80%
  no DB lock/deadlock error
  outbox oldest due remains <60s after load settles
```

If environment cannot meet these with realistic host size, record measured baseline and fix bottleneck before pilot rather than hiding the threshold.

- [ ] **Step 3: Add last-stock soak**

Run existing last-stock race 100 iterations under one test:

```text
stock=1
two buyers commit concurrently
every iteration:
  one Order success
  one conflict
  no oversell
  one ACTIVE reservation before completion/cancel
  no deadlock
```

- [ ] **Step 4: Add independent Checkout parallelism test**

20 buyers checkout independent tracked variants in parallel:
- 20 valid Orders;
- no shared-lock bottleneck/deadlock;
- outbox count 20;
- no duplicate idempotency record.

- [ ] **Step 5: Add Order-command soak**

Repeated overlapping supported races from Plan 05:
- complete vs seller cancel;
- issue vs auto-complete boundary;
- cancellation accept vs reject.

Run enough iterations to detect nondeterministic duplicate events/inventory movements.

- [ ] **Step 6: Observe infrastructure during load**

Record:
- API p95;
- CPU/memory;
- Hikari;
- PostgreSQL connections;
- outbox;
- rate-limit fail-open;
- Redis errors.

Do not increase pool sizes blindly; fix query/concurrency bottleneck based on evidence.

- [ ] **Step 7: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=CheckoutSoakConcurrencyTest,OrderCommandSoakConcurrencyTest test
cd ../..
node infra/perf/run-load.mjs --help
git add infra/perf apps/api/src/test/java/vn/covie/performance \
        docs/operations/performance.md
git commit -m "test(perf): add pilot load and concurrency gates"
```

---

## Task 15: Add Trivy, SBOM and Release Security Gates

**Files:**
- Create: `infra/scripts/security-scan.sh`
- Create: `infra/scripts/generate-sbom.sh`
- Modify: `.github/workflows/ci.yml`
- Create: `docs/operations/security-release-gate.md`

**Interfaces:**
- Scans repository and built runtime images.
- Security exceptions are explicit, dated and operator-reviewed; no silent `--ignore-unfixed` blanket.

- [ ] **Step 1: Pin Trivy 0.72.0**

Use the `aquasec/trivy:0.72.0` image or verified binary.

Filesystem scan:

```bash
trivy fs \
  --scanners vuln,misconfig,secret \
  --severity HIGH,CRITICAL \
  --exit-code 1 \
  .
```

Exclude generated build directories, not source configuration.

- [ ] **Step 2: Scan both images**

After build:

```bash
trivy image --severity HIGH,CRITICAL --exit-code 1 "$COVIE_API_IMAGE"
trivy image --severity HIGH,CRITICAL --exit-code 1 "$COVIE_WEB_IMAGE"
```

A HIGH/CRITICAL runtime finding blocks release unless an exception record includes:
- CVE/advisory;
- why not exploitable/mitigation;
- owner role;
- expiry date;
- target fix version.

Do not add permanent global ignore.

- [ ] **Step 3: Add Node production audit**

```bash
pnpm audit --prod --audit-level=high
```

A transient registry failure is a CI infrastructure failure, not permission to skip the gate.

- [ ] **Step 4: Generate SBOM**

For each release image:

```bash
trivy image --format cyclonedx --output api.cdx.json "$COVIE_API_IMAGE"
trivy image --format cyclonedx --output web.cdx.json "$COVIE_WEB_IMAGE"
```

Store SBOM as release artifact, not runtime public endpoint.

- [ ] **Step 5: Re-run application security matrix**

CI release job includes:
- CSRF negatives;
- 404/403 IDOR;
- refresh reuse;
- revoked session;
- STAFF location scope;
- Admin auth;
- presigned upload ownership;
- OAuth return/open-redirect tests;
- rate-limit tests;
- no direct Order status PATCH mapping.

- [ ] **Step 6: Commit**

```bash
git add infra/scripts/security-scan.sh infra/scripts/generate-sbom.sh \
        .github/workflows/ci.yml docs/operations/security-release-gate.md
git commit -m "ci: add release security and sbom gates"
```

---

## Task 16: Add Idempotent Grand Park Reference-data and First-admin Bootstrap

**Files:**
- Create: `apps/api/src/main/java/vn/covie/bootstrap/PilotBootstrapProperties.java`
- Create: `apps/api/src/main/java/vn/covie/bootstrap/PilotReferenceDataBootstrapRunner.java`
- Create: `apps/api/src/main/java/vn/covie/bootstrap/FirstAdminBootstrapRunner.java`
- Create: `apps/api/src/main/java/vn/covie/bootstrap/BootstrapReferenceData.java`
- Create: `apps/api/src/main/resources/bootstrap/grand-park-reference-data.json`
- Modify: `apps/api/src/main/resources/application-prod.yml`
- Test: `apps/api/src/test/java/vn/covie/bootstrap/PilotReferenceDataBootstrapIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/bootstrap/FirstAdminBootstrapIntegrationTest.java`
- Create: `docs/operations/pilot-bootstrap.md`

**Interfaces:**
- Seeds reference configuration only, not fake Orders/users/merchants.
- First admin is promoted only after normal Google onboarding.

- [ ] **Step 1: Define disabled-by-default properties**

```yaml
covie:
  bootstrap:
    reference-data:
      enabled: ${COVIE_BOOTSTRAP_REFERENCE_DATA_ENABLED:false}
      path: ${COVIE_BOOTSTRAP_REFERENCE_DATA_PATH:classpath:/bootstrap/grand-park-reference-data.json}
    first-admin-email: ${COVIE_BOOTSTRAP_FIRST_ADMIN_EMAIL:}
```

No bootstrap endpoint.

- [ ] **Step 2: Define reference-data JSON**

`grand-park-reference-data.json` starts with this deterministic reference set:

```json
{
  "locationAreas": [
    {"parentSlug": null, "slug": "vinhomes-grand-park", "name": "Vinhomes Grand Park", "areaType": "COMMUNITY", "sortOrder": 10},
    {"parentSlug": "vinhomes-grand-park", "slug": "the-rainbow", "name": "The Rainbow", "areaType": "ZONE", "sortOrder": 10},
    {"parentSlug": "vinhomes-grand-park", "slug": "the-origami", "name": "The Origami", "areaType": "ZONE", "sortOrder": 20},
    {"parentSlug": "vinhomes-grand-park", "slug": "the-beverly", "name": "The Beverly", "areaType": "ZONE", "sortOrder": 30}
  ],
  "categories": [
    {"parentSlug": null, "slug": "food-drink", "name": "Ăn uống", "catalogType": "PRODUCT", "sortOrder": 10},
    {"parentSlug": null, "slug": "grocery", "name": "Tạp hóa", "catalogType": "PRODUCT", "sortOrder": 20},
    {"parentSlug": null, "slug": "home-personal", "name": "Nhà cửa & cá nhân", "catalogType": "PRODUCT", "sortOrder": 30},
    {"parentSlug": null, "slug": "services", "name": "Dịch vụ", "catalogType": "SERVICE", "sortOrder": 40},
    {"parentSlug": "services", "slug": "cleaning", "name": "Vệ sinh", "catalogType": "SERVICE", "sortOrder": 10},
    {"parentSlug": "services", "slug": "repair", "name": "Sửa chữa", "catalogType": "SERVICE", "sortOrder": 20},
    {"parentSlug": "services", "slug": "other-services", "name": "Dịch vụ khác", "catalogType": "SERVICE", "sortOrder": 90}
  ]
}
```

The JSON is environment reference data, not a claim that these are the only zones/categories forever. Admin can add more after bootstrap; rerunning seed must not delete them.

Do not include:
- user;
- merchant;
- inventory;
- Order;
- password;
- OAuth identity;
- moderation case.

Use stable human slugs as natural bootstrap keys; database UUIDs remain generated UUIDv7.

- [ ] **Step 3: Implement idempotent LocationArea upsert**

For each parent-first area:
- find active/nondeleted by `(parentId,slug)`;
- absent -> create UUIDv7;
- present -> update name/type/sort only when bootstrap is explicitly enabled;
- do not overwrite admin-created geometry boundary with null from JSON.

Run twice -> no duplicate.

- [ ] **Step 4: Implement idempotent Category upsert**

Same parent+slug strategy.
- preserve existing IDs;
- ensure requested catalog type/status/sort;
- do not delete category absent from seed file.

Bootstrap is additive/reconciling, never destructive.

- [ ] **Step 5: Implement first-admin promotion safely**

Operator procedure:
1. intended admin completes normal Google registration + CoVie password onboarding;
2. operator sets `COVIE_BOOTSTRAP_FIRST_ADMIN_EMAIL`;
3. restart once;
4. runner normalizes email with existing `EmailNormalizer`;
5. inside one transaction query:

```sql
select distinct u.id
from users u
join user_identities g
  on g.user_id = u.id
 and g.provider = 'GOOGLE'
 and g.email_normalized = :email
 and g.verified_at is not null
join user_identities e
  on e.user_id = u.id
 and e.provider = 'EMAIL'
 and e.email_normalized = :email
where u.account_status = 'ACTIVE';
```

6. require exactly one row; zero or multiple -> fail startup;
7. grant with UUIDv7 id:

```sql
insert into system_role_assignments(id, user_id, role, created_at)
values (:id, :userId, 'ADMIN', :now)
on conflict (user_id, role) do nothing;
```

8. only when insert count is `1`, call the existing writer in the same transaction:

```java
auditLogWriter.securityEvent(
    null,
    "FIRST_ADMIN_GRANTED",
    "USER",
    userId,
    null,
    Map.of("source", "BOOTSTRAP"));
```
9. insert count `0` means already ADMIN -> idempotent no-op;
10. operator removes env and restarts.

The runner injects `NamedParameterJdbcTemplate`, `UuidV7Generator`, `AppClock`, `EmailNormalizer` and `AuditLogWriter`; it does not invent a second role/audit model. No direct password/user creation.

- [ ] **Step 6: Test safety**

Required:
- disabled -> no DB change;
- reference seed twice -> identical row IDs/counts;
- first-admin unknown -> startup failure/no role;
- Google-only incomplete onboarding -> no promotion;
- inactive user -> no promotion;
- correct onboarded user -> one ADMIN;
- rerun -> one ADMIN;
- no public controller/mapping under `/api/v1/bootstrap`.

- [ ] **Step 7: Commit**

```bash
cd apps/api
./mvnw -Dtest=PilotReferenceDataBootstrapIntegrationTest,FirstAdminBootstrapIntegrationTest test
cd ../..
git add apps/api/src/main/java/vn/covie/bootstrap \
        apps/api/src/main/resources/application-prod.yml \
        apps/api/src/test/java/vn/covie/bootstrap \
        apps/api/src/main/resources/bootstrap/grand-park-reference-data.json \
        docs/operations/pilot-bootstrap.md
git commit -m "feat(ops): add safe pilot reference bootstrap"
```

---

## Task 17: Build Immutable Release Images and Release Manifest in GitHub Actions

**Files:**
- Create: `.github/workflows/release.yml`
- Create: `infra/deploy/release-manifest.example`
- Create: `infra/scripts/create-release-manifest.sh`
- Modify: `docs/operations/deploy-rollback.md`

**Interfaces:**
- Reuses CI workflow versions/patterns already established.
- Produces image tags + digests + SBOM and a deployable manifest artifact.

- [ ] **Step 1: Define release trigger**

Workflow:
- manual `workflow_dispatch` for RC/pilot;
- optional semver tag trigger;
- protected GitHub Environment for production deployment approval.

Set:

```yaml
permissions:
  contents: read
  packages: write
```

Default registry for the GitHub-hosted repository is GHCR:

```text
REGISTRY=ghcr.io
IMAGE_NAMESPACE=${GITHUB_REPOSITORY_OWNER}/covie
```

Authenticate without a third-party action:

```bash
echo "$GITHUB_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin
```

Do not deploy every main commit directly to production.

- [ ] **Step 2: Re-run immutable quality gate before image push**

Jobs must require:
1. backend `./mvnw verify`;
2. OpenAPI/generated drift clean;
3. frontend lint/typecheck/test/build;
4. critical concurrency suite;
5. Plan 09 Playwright smoke against CI/staging-compatible stack where available;
6. Task 15 security scan.

A release does not rely solely on a previous PR check.

- [ ] **Step 3: Build images tagged by Git SHA**

Registry variables:

```text
REGISTRY
IMAGE_NAMESPACE
```

Tags:
- `api:<git-sha>`;
- `web:<git-sha>`.

The Web build has no environment-specific `NEXT_PUBLIC_APP_URL`; `COVIE_PUBLIC_URL` is runtime server config, so the exact Web digest can be promoted staging -> production.

Never deploy `latest`.

- [ ] **Step 4: Push and capture immutable digests**

After push resolve:

```text
API_IMAGE=ghcr.io/example-owner/covie/api@sha256:0000000000000000000000000000000000000000000000000000000000000000
WEB_IMAGE=ghcr.io/example-owner/covie/web@sha256:1111111111111111111111111111111111111111111111111111111111111111
GIT_SHA=0123456789abcdef0123456789abcdef01234567
OPENAPI_SHA256=2222222222222222222222222222222222222222222222222222222222222222
HIGHEST_FLYWAY=V013
BUILD_UTC=2026-08-08T12:00:00Z
```

Save as release artifact `release-manifest.env`.

It contains no secrets.

- [ ] **Step 5: Store SBOM and scan results**

Upload:
- API CycloneDX;
- Web CycloneDX;
- Trivy reports;
- test summaries;
- release manifest.

- [ ] **Step 6: Do not create release migration**

Workflow verifies highest migration expected by this plan remains V013 unless an explicitly reviewed migration change exists.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/release.yml \
        infra/deploy/release-manifest.example \
        infra/scripts/create-release-manifest.sh \
        docs/operations/deploy-rollback.md
git commit -m "ci: build immutable covie release manifests"
```

---

## Task 18: Implement Digest-based Deploy, Smoke and Rollback Runbooks

**Files:**
- Create: `infra/deploy/deploy.sh`
- Create: `infra/deploy/rollback.sh`
- Modify: `.github/workflows/release.yml`
- Create: `infra/deploy/smoke.sh`
- Create: `docs/operations/deploy-rollback.md`
- Create: `docs/operations/incident-runbook.md`

**Interfaces:**
- Deploys Task 17 manifest with Task 4 Compose.
- Rollback changes application image digests only.

- [ ] **Step 1: Define deployment state directory**

On host:

```text
/var/lib/covie/releases/current.env
/var/lib/covie/releases/previous.env
/etc/covie/prod.env
/etc/covie/secrets/
/etc/covie/tls/
```

`current.env` and `previous.env` contain image digests/build metadata only, no app secrets.

- [ ] **Step 2: Implement preflight**

`deploy.sh release-manifest.env`:
1. require root/operator permissions;
2. validate Task 5 runtime env;
3. check disk >20% free;
4. `docker compose config`;
5. pull digest images;
6. verify expected image digests;
7. verify TLS files;
8. verify DB connectivity through a short API/Flyway startup/preflight path;
9. verify latest backup timestamp acceptable before production deploy;
10. copy current→previous.

No production deploy proceeds with stale/missing backup evidence.

- [ ] **Step 3: Controlled deploy**

Export image digests from manifest, then:

```bash
docker compose -f infra/compose/compose.prod.yml up -d --remove-orphans
```

Wait:
- Nginx running;
- `/api/v1/health` public HTTPS 200;
- app homepage 200;
- API management readiness 200 from internal network.

If timeout/failure -> automatically invoke rollback to previous digest.

- [ ] **Step 4: Smoke script**

Public smoke:

```text
HTTPS redirect
security headers
GET /
GET /api/v1/health
anonymous catalog query
no public /actuator
no direct API/Web host port
SSE endpoint anonymous -> 401
```

Authenticated staging smoke may use Plan 09 e2e fixture **only on staging profile**, never production.

- [ ] **Step 5: Rollback semantics**

`rollback.sh`:
- requires existing `previous.env`;
- pulls previous digests;
- runs Compose with previous images;
- waits health;
- runs smoke;
- updates current/previous only after success.

It does **not**:
- revert Flyway;
- drop columns/tables;
- restore DB automatically.

If candidate migration was backward-incompatible with previous app, release design is invalid for this simple rollback strategy and must be redesigned before pilot.

- [ ] **Step 6: Define data-incident path separately**

If database data is corrupted:
- stop writes/maintenance window;
- preserve evidence;
- choose PITR/backup restore into **new** database;
- verify;
- switch connection only after incident decision.

Do not conflate app rollback with DB restore.

- [ ] **Step 7: Wire staging and production deploy jobs in `release.yml`**

After Task 18 scripts exist, add SSH-based single-host deploy jobs. GitHub secrets are environment-scoped:

```text
STAGING_DEPLOY_HOST
STAGING_DEPLOY_USER
STAGING_DEPLOY_SSH_KEY
STAGING_DEPLOY_KNOWN_HOSTS
PROD_DEPLOY_HOST
PROD_DEPLOY_USER
PROD_DEPLOY_SSH_KEY
PROD_DEPLOY_KNOWN_HOSTS
```

`deploy-staging` runs after immutable images/manifest/security gates and transfers only the manifest plus the checked-out deploy scripts, then calls host `deploy.sh`.

`deploy-production`:
- `needs: deploy-staging`;
- uses protected GitHub Environment `production`;
- requires manual reviewer approval configured in GitHub;
- deploys the exact same image digests that passed staging, never rebuilds.

Write the matching `*_DEPLOY_KNOWN_HOSTS` secret to `~/.ssh/known_hosts`; do not use `StrictHostKeyChecking=no` or trust a just-scanned host key during deployment.

Do not put production SSH keys in repository variables or artifacts. The full Task 19 restore/rollback rehearsal remains a human release prerequisite before approving the production environment.

- [ ] **Step 8: Run shell tests/static checks**

Use shellcheck if present; otherwise strict `bash -n` plus dry-run functions.

Scripts use `set -euo pipefail`, quote variables and never `eval` secret content.

- [ ] **Step 9: Commit**

```bash
git add infra/deploy .github/workflows/release.yml docs/operations/deploy-rollback.md \
        docs/operations/incident-runbook.md
git commit -m "ops: add digest deploy and rollback runbooks"
```

---

## Task 19: Rehearse Clean Staging Deploy, Backup Restore and Rollback

**Files:**
- Create: `docs/operations/staging-rehearsal.md`
- Create: `infra/scripts/run-staging-rehearsal.sh`
- Create: `infra/scripts/verify-clean-migration.sh`
- Create: `infra/scripts/verify-restored-invariants.sh`
- Create: `apps/web/e2e/fixtures/staging-auth-fixture.ts`
- Create: `apps/web/e2e/staging-p0.spec.ts`

**Interfaces:**
- Uses only staging/disposable resources.
- Produces recorded evidence required before pilot launch.

- [ ] **Step 1: Provision a clean staging target**

Staging must have:
- new empty PostgreSQL/PostGIS DB;
- clean Redis namespace/instance;
- staging S3 bucket;
- staging TLS/domain;
- production-shaped runtime config;
- e2e support only if staging run requires Plan 09 browser fixtures.

Never point rehearsal restore/drop commands at production identifiers.

- [ ] **Step 2: Prove clean Flyway migration**

Start API against empty DB.

Verify `flyway_schema_history` contains exactly the successful version sequence `1` through `13` in ascending order, with no missing/duplicate version and no checksum failure.

If Plan 10 implementation had an explicitly reviewed real V014 migration, expected list is updated. Otherwise V013 remains highest.

- [ ] **Step 3: Run reference bootstrap**

Enable Grand Park reference seed once, then disable.

Verify:
- no duplicate location areas/categories;
- rerun idempotent.

Create the intended first staging Admin through the **normal staging Google OAuth + mandatory CoVie password onboarding**. Then set the bootstrap email once, verify ADMIN, remove the variable and restart. The E2E test-support login is not accepted as proof for first-admin bootstrap because it does not prove the Google recovery-root identity path.

- [ ] **Step 4: Deploy RC by digest**

Use Task 18 deploy script and release manifest.

Verify:
- HTTPS;
- same-domain API;
- security headers;
- no public Actuator/monitoring;
- API/Web containers non-root;
- production profile active;
- e2e support disabled for production-shaped staging unless the specific automated test window enables staging-only support.

- [ ] **Step 5: Run P0 smoke**

The final rehearsal runs the application with production-shaped `prod` profile and `COVIE_E2E_SUPPORT_ENABLED=false`. Create dedicated staging Buyer/Seller/Admin accounts through normal Google onboarding once; their CoVie email/password credentials live only in staging CI/environment secrets.

`staging-auth-fixture.ts` performs normal browser auth:

```text
GET /api/v1/auth/csrf
POST /api/v1/auth/login with staging email/password
retain normal HttpOnly cookies in Playwright context
```

It never calls `/api/v1/test-support/login`. Required environment names:

```text
STAGING_BUYER_EMAIL / STAGING_BUYER_PASSWORD
STAGING_SELLER_EMAIL / STAGING_SELLER_PASSWORD
STAGING_ADMIN_EMAIL / STAGING_ADMIN_PASSWORD
```

Run `staging-p0.spec.ts` against HTTPS staging:

```text
guest discovery
buyer Product Order
seller confirm/delivery
buyer complete
Service flow
Need -> response -> confirmed source Order -> FULFILLED
Review
Admin settings/report read
```

Verify Outbox drains and notifications appear.

- [ ] **Step 6: Exercise rate limits**

Controlled test:
- exceed auth/login IP bucket -> 429 + Retry-After;
- wait/refill -> successful path resumes;
- verify HMAC key/raw email absent from Redis key inspection;
- simulate Redis outage only in staging:
  - fail-closed group returns controlled 503;
  - PUBLIC_DISCOVERY fail-opens with metric;
  - no business correctness loss.

- [ ] **Step 7: Create backup and restore to a second DB**

Workflow:

```text
staging DB A
-> pg_dump custom
-> checksum
-> create empty DB B
-> pg_restore into B
-> Flyway validate
-> critical invariant verification
-> point a temporary API instance at DB B
-> smoke read + one isolated safe staging mutation
```

Do not overwrite DB A.

- [ ] **Step 8: Verify restored invariants**

At minimum:
- Order count;
- OrderItem count;
- OrderEvents per sample Order;
- Inventory balance/reservation relationships;
- InventoryMovement count;
- Need source trace sample;
- Review/rating sample;
- `system_setting_history`;
- no pending Flyway checksum change.

- [ ] **Step 9: Perform application rollback drill**

Staging has:
- previous known-good digest A;
- release candidate digest B.

Procedure:
1. deploy B;
2. smoke;
3. rollback to A with `rollback.sh`;
4. smoke A against same DB;
5. redeploy B;
6. smoke again.

If A cannot start after B's migrations, migration compatibility violates the pilot rollback strategy and release is blocked.

- [ ] **Step 10: Verify alerts**

Trigger/stage:
- stop API scrape -> ApiDown;
- create test terminal Outbox failure or controlled fixture -> alert;
- stale backup metric in staging -> BackupStale;
- rate-limit fail-open metric -> alert.

Restore normal state and ensure alerts resolve.

- [ ] **Step 11: Record rehearsal evidence**

Record in a dated operator artifact, not source secrets:

```text
release manifest digest
migration highest/checksum result
Playwright summary
security scan result
load result
backup filename/checksum
restore DB result
rollback timestamps/result
alert test result
open exceptions
operator roles/sign-off
```

- [ ] **Step 12: Commit rehearsal automation/runbook**

```bash
git add docs/operations/staging-rehearsal.md \
        infra/scripts/run-staging-rehearsal.sh \
        infra/scripts/verify-clean-migration.sh \
        infra/scripts/verify-restored-invariants.sh \
        apps/web/e2e/fixtures/staging-auth-fixture.ts \
        apps/web/e2e/staging-p0.spec.ts
git commit -m "ops: codify staging release rehearsal"
```

---

## Task 20: Run Final Pilot Release Candidate Gate and Freeze V1 Operations Baseline

**Files:**
- Create: `docs/operations/pilot-launch-checklist.md`
- Modify: `README.md`

**Interfaces:**
- Produces the final evidence gate for CoVie V1 pilot.
- No new feature is allowed in this task; failures send work back to the owning task.

- [ ] **Step 1: Run clean backend verification**

```bash
cd apps/api
./mvnw clean verify
cd ../..
```

Expected: exit `0`, zero failures/errors.

- [ ] **Step 2: Run frontend + contract verification**

```bash
./infra/scripts/verify-generated-client.sh
pnpm --filter @covie/api-client check
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web test
pnpm --filter @covie/web build
```

Expected: all exit `0`, generated diff clean.

- [ ] **Step 3: Run critical security/concurrency suites explicitly**

```text
refresh reuse/concurrency
CSRF negative
IDOR 404/403
merchant STAFF location scope
Admin auth/read-only Order
last-stock 100-iteration soak
Order command races
Outbox multi-replica/dedupe
Need fulfillment timing
Review eligibility
Rate-limit atomicity/failure mode
technical retention concurrency
```

No release if one is quarantined/skipped.

- [ ] **Step 4: Run P0 Playwright**

```bash
pnpm --filter @covie/web test:e2e
```

Expected: all required P0 specs pass.

- [ ] **Step 5: Build and security-scan release images**

```bash
docker build -f apps/api/Dockerfile -t covie-api:rc .
docker build -f apps/web/Dockerfile -t covie-web:rc .
./infra/scripts/security-scan.sh covie-api:rc covie-web:rc
./infra/scripts/generate-sbom.sh covie-api:rc covie-web:rc
```

No unresolved release-blocking vulnerability/secret.

- [ ] **Step 6: Run production artifact validators**

```bash
./infra/scripts/verify-production-artifacts.sh
./infra/scripts/verify-nginx-prod.sh
./infra/scripts/verify-monitoring-config.sh
bash -n infra/deploy/*.sh
bash -n infra/backup/*.sh
```

Expected: all pass.

- [ ] **Step 7: Re-run load baseline against staging RC**

Required:
- search/objectives from Task 14;
- authenticated Order list;
- no pool saturation;
- no Outbox sustained backlog;
- zero oversell/deadlock/duplicate mutation.

Capture result in release evidence.

- [ ] **Step 8: Confirm backup/restore evidence**

Checklist:

```text
[ ] last backup <=26h
[ ] checksum verified
[ ] off-host encrypted copy exists
[ ] restore rehearsal succeeded
[ ] restored API Flyway validate succeeded
[ ] key business invariants checked
[ ] PITR status documented if provider supports it
```

- [ ] **Step 9: Confirm monitoring and on-call path**

```text
[ ] Prometheus scrapes API/node
[ ] Grafana dashboard loads
[ ] Alertmanager test reaches operator channel
[ ] ApiDown tested
[ ] Outbox alert tested
[ ] backup stale alert tested
[ ] disk alert expression valid
[ ] Incident Lead role assigned
[ ] Release Operator role assigned
[ ] Database Operator role assigned
```

Use roles rather than personal names in source-controlled runbook.

- [ ] **Step 10: Confirm pilot SLO/operational objectives**

Pilot internal objectives:

```text
API monthly availability objective: 99.5%
core API read p95 objective: <1s under expected pilot load
Outbox oldest due normal objective: <60s
backup freshness: <26h
logical-backup RPO objective: <=24h
restore RTO objective: <=4h
```

These do not create a contractual public SLA.

- [ ] **Step 11: Verify release blockers are all false**

```text
[ ] tracked inventory cannot oversell in concurrency tests
[ ] same Idempotency-Key cannot duplicate Order
[ ] refresh reuse revokes session family
[ ] buyer cannot read another buyer's private Order
[ ] STAFF cannot exceed MerchantLocation scope
[ ] no direct PATCH Order status API
[ ] Need does not fulfill on PENDING Order
[ ] Review cannot be created before COMPLETED
[ ] Outbox write is atomic with business commit
[ ] Outbox retry cannot duplicate projection/notification
[ ] production auth cookies have required Secure/HttpOnly/SameSite attributes
[ ] e2e/test-support is disabled in prod
[ ] clean DB migration succeeds
[ ] production startup safety passes
[ ] Redis rate-limit outage semantics are tested
[ ] `/actuator` is not public
[ ] secrets are absent from repo/images/logs
[ ] backup has been restore-tested
[ ] staging rollback drill passes
[ ] high/critical runtime vulnerability exceptions are zero or explicitly approved+dated
```

Any unchecked item blocks pilot.

- [ ] **Step 12: Freeze migration handoff**

If no evidence-backed DB migration was needed:

```text
highest migration = V013__moderation_system_settings_admin.sql
next available migration = V014
```

Plan 10 must not consume V014.

If execution added a reviewed V014 index/schema migration, update this section and all release-manifest checks to next available V015.

- [ ] **Step 13: Update top-level README operations entrypoint**

Link only:
- deployment;
- secrets;
- backup restore;
- observability;
- incident;
- pilot launch checklist.

Do not copy sensitive operator values into README.

- [ ] **Step 14: Final commit only if documentation changed**

```bash
git add docs/operations/pilot-launch-checklist.md README.md
git commit -m "docs: freeze covie v1 pilot operations baseline"
```

Do not create an empty commit.

---

# Pilot Rate-limit Matrix

| Group | Key | Capacity/refill | Redis outage |
|---|---|---:|---|
| `AUTH_LOGIN_IP` | HMAC normalized IP | 10/min | fail closed |
| `AUTH_LOGIN_EMAIL` | HMAC normalized email | 5/min | fail closed |
| `AUTH_GOOGLE_START` | HMAC normalized IP | 20/min | fail closed |
| `AUTH_REFRESH_IP` | HMAC normalized IP | 30/min | fail closed |
| `AUTH_REFRESH_TOKEN` | HMAC raw refresh credential | 30/min | fail closed |
| `PUBLIC_DISCOVERY` | HMAC normalized IP | 120/min | fail open + alert |
| `CHECKOUT` | userId | 30/min | fail open + alert |
| `ORDER_COMMAND` | userId | 60/min | fail open + alert |
| `NEED_CREATE` | userId | 10/hour | fail closed |
| `NEED_RESPONSE` | userId + merchantId | 30/hour | fail closed |
| `REPORT` | userId | 10/hour | fail closed |
| `MEDIA_UPLOAD` | userId | 30/hour | fail closed |
| `ADMIN` | admin userId | 120/min | fail closed |

`fail open` never means “skip business protection”; PostgreSQL authz/idempotency/state/locks continue normally.

---

# Production Health Matrix

| Signal | Purpose | Readiness dependency? |
|---|---|---:|
| JVM/process liveness | container restart decision | liveness |
| PostgreSQL | transactional source of truth | yes |
| Redis | rate limiting/cache | no; visible/alerted |
| S3/object storage | media operations | no; visible/runbook |
| Outbox backlog | async health | alert, not readiness |
| SSE count | visibility | no |
| backup age | recoverability | release/alert |
| disk | host safety | alert |

Do not create restart storms because Redis/S3 is temporarily degraded.

---

# Retention Matrix

| Data | V1 retention action |
|---|---|
| `checkout_sessions` | delete expired after 24h safety window |
| `idempotency_records` | delete after `expires_at` |
| used/revoked/expired refresh rows | delete after 45d terminal history, only when session no longer active |
| auth action tickets | delete 7d after expiry/consume |
| Outbox `PUBLISHED` | delete after 7d |
| terminal Outbox `FAILED` | retain 30d before purge |
| pending/orphan media | existing 24h cleanup flow |
| auth sessions | retain V1 |
| audit logs | no generic purge |
| Orders/OrderEvents | no generic purge |
| inventory movements | no generic purge |
| Needs/responses | no generic purge |
| Reviews/Reputation | no generic purge |
| Moderation/settings history | no generic purge |

---

# Backup / Recovery Matrix

```text
Normal application release failure
-> rollback API/Web image digests
-> same DB
-> no down migration

Redis outage
-> security-sensitive limiter groups fail closed
-> selected business/read groups fail open + metrics
-> PostgreSQL correctness unchanged

S3 outage
-> media operations degraded
-> core marketplace/Order DB flows continue
-> API readiness remains DB-based

Database incident
-> stop/contain writes as incident requires
-> PITR or logical backup restore into new DB
-> verify Flyway + invariants
-> controlled connection switch
-> never restore blindly over source

Outbox terminal failure
-> alert
-> inspect sanitized last_error/event
-> correct handler/data
-> operator requeue only with documented maintenance procedure
```

---

# Alert Baseline

```text
P0:
- API down >2m
- database unavailable/readiness failing
- backup stale >26h

P1:
- Outbox terminal failure >0
- Outbox oldest due >60s for 5m
- Hikari >80% for 5m
- host disk <15%
- scheduler failure

P2:
- 5xx >2% for 5m with traffic floor
- p95 >1s for 10m
- rate-limit Redis fail-open >0
```

Pilot team may tune thresholds after observing real traffic, but changing a threshold must not disable detection of known release blockers.

---

# Release Evidence Bundle

Every pilot release stores, outside the source repo where appropriate:

```text
release-manifest.env
API image digest
Web image digest
git SHA
highest Flyway version
OpenAPI SHA-256
SBOM API
SBOM Web
Trivy scan result
backend test summary
frontend test summary
Playwright summary
concurrency summary
load summary
backup checksum + timestamp
restore rehearsal result
rollback drill result
alert test result
approved temporary security exceptions, if any
```

No secret is included.

---

# Operator Roles

Source-controlled runbooks use role names:

```text
Release Operator
- runs deployment/rollback
- validates release manifest
- confirms smoke

Incident Lead
- coordinates incident and communication
- decides containment/rollback/escalation

Database Operator
- backup/PITR/restore
- validates restored DB
- performs controlled DB switch
```

A small pilot team may have one person wearing multiple roles, but each action/runbook still names the role responsibility.

---

# Plan 10 Non-goals

Do not add:

```text
Kubernetes
service mesh
Kafka/RabbitMQ
Loki/ELK requirement for launch
multi-region active-active
automatic cross-region failover
zero-downtime schema migration framework
automatic DB down migrations
native mobile infrastructure
WebSocket/chat
payment gateway/reconciliation
wallet/escrow/refund
service booking calendar
driver tracking
search cluster
AI recommendation
advanced BI/revenue analytics
full SIEM
customer-facing SLA system
24/7 enterprise on-call platform
auto-scaling orchestration beyond host/container restart policy
production test-auth bypass
public database/Redis/Prometheus/Grafana/Actuator ports
```

These may be revisited after pilot evidence justifies them.

---

# Final Definition of Done — CoVie Technical Plan 01–10

CoVie V1 is technically ready for pilot only after fresh evidence proves:

```text
Foundation:
  monorepo builds
  clean PostgreSQL/PostGIS migration works
  generated API contract is current

Identity:
  Google-first onboarding + password works
  rotating refresh/reuse detection works
  sessions/recovery/CSRF secure

Marketplace:
  Merchant/Location/Catalog works
  discovery resolves concrete MerchantLocation
  Cart/Checkout works
  tracked inventory cannot oversell
  Order lifecycle state machine passes
  Need source tracing/fulfillment passes
  Review/Reputation/Notification/Outbox passes
  Moderation/SystemSetting/Admin passes

Web:
  P0 Guest/Buyer/Seller/Admin journeys pass Playwright
  Order actions use allowedActions
  Review CTA uses backend eligibility
  SSE only refetches REST

Production:
  API/Web images non-root + immutable
  HTTPS same-domain Nginx works
  `/actuator` internal-only
  production safety fails insecure config
  secrets absent from source/image/logs
  Redis rate limiting behaves per fail-open/closed policy
  technical retention is bounded/safe
  Prometheus/Grafana/Alertmanager operational
  backup freshness monitored
  backup restored successfully into separate DB
  query/load/concurrency baselines pass
  Trivy HIGH/CRITICAL gate resolved
  Grand Park bootstrap is idempotent
  first Admin bootstrap has no public backdoor
  staging clean-deploy rehearsal passes
  application rollback drill passes
  release blockers all closed
```

---

# Migration Handoff After Technical Plan

Expected normal case:

```text
V001–V013 implemented by Plans 01–08
Plan 09: no migration
Plan 10: no migration
next available: V014
```

Production hardening alone is not a schema-change reason.

If a Plan 10 performance test discovers a proven missing index:
- capture evidence;
- review DB change;
- use V014;
- next available becomes V015;
- do not amend V001–V013.

---

# Execution Handoff

When Plan 10 implementation begins, use the isolated worktree required by the repository Superpowers workflow.

Recommended execution:

```text
1. Subagent-Driven Development
   - fresh subagent per Task
   - specification review after every Task
   - code-quality review after spec compliance
   - ops/security review for Tasks 4–5, 10–12, 15, 17–20
   - fresh verification before completion claims

2. Inline Executing Plans
   - execute Tasks 1 -> 20 in dependency order
   - preserve TDD RED/GREEN evidence
   - stop at production/DB/security review checkpoints
   - never skip restore or rollback rehearsal
```

The final step is not “deploy because tests look good”. The final step is a green **staging clean-deploy + backup/restore + rollback rehearsal**, followed by the release-blocker checklist.
