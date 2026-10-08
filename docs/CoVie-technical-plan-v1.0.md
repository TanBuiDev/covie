# CoVie Technical Plan V1.0 — Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Project:** CoVie  
**Document:** Technical Plan / Master Implementation Plan  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Implementation baseline  

**Goal:** Biến Product Plan, User Flow, IA/UI Flow, Database Design và API Design đã chốt thành một lộ trình triển khai có thứ tự dependency rõ ràng, kiểm thử được theo từng lát cắt end-to-end và đủ chi tiết để chia thành các implementation plan nhỏ hơn trước khi code.

**Architecture:** CoVie V1 là modular monolith. Frontend Next.js và backend Spring Boot nằm trong cùng monorepo; Spring Boot là nguồn business logic duy nhất; PostgreSQL + PostGIS là source of truth; Redis chỉ phục vụ cache/rate-limit/ephemeral coordination; media lưu ở S3-compatible object storage. REST `/api/v1` là contract chính, SSE chỉ là tín hiệu realtime nhẹ, Transactional Outbox bảo đảm side effects sau transaction.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1.x, Spring Security, Spring Data JPA/Hibernate, PostgreSQL 18 + PostGIS 3.6.x, Flyway, Redis 8.x, S3-compatible object storage, Next.js 16.2.x Active LTS, Node.js 24 LTS, TypeScript, pnpm, OpenAPI-generated TypeScript client, JUnit 5, Testcontainers, Vitest/React Testing Library, Playwright, Docker Compose, Nginx.

---

# 1. Source of truth và thứ tự ưu tiên

Technical Plan này phải tuân theo thứ tự nguồn sau khi có mâu thuẫn:

1. `CoVie-api-design-v1.0.md` — HTTP contract, auth/security, command semantics, idempotency, concurrency, outbox, error model.
2. `CoVie-database-design-v1.0.md` — schema business, indexes, snapshots, constraints, transaction invariants.
3. `CoVie-user-flow-v1.0.md` — lifecycle nghiệp vụ và actor behavior.
4. `CoVie-information-architecture-ui-flow-v1.0.md` — route/screen/CTA/UX organization.
5. `CoVie-product-plan-v0.3.md` — product scope và MVP/non-goals.

Không implement behavior chỉ vì có trong Product Plan cũ nếu API/Database/User Flow mới hơn đã thay đổi nó.

---

# 2. Global Constraints

- V1 là **Web App / PWA**, chưa làm native mobile app.
- Backend là **modular monolith**, chưa tách microservices.
- API public base path là `/api/v1`.
- Spring Boot là business source of truth; Next.js không được duplicate state machine hay pricing rules.
- JPA entities không được serialize trực tiếp ra API.
- UUIDv7 là canonical identifier cho business entities.
- Money dùng integer minor units + ISO currency code; tuyệt đối không dùng floating-point làm nguồn tính toán.
- Timestamps tuyệt đối dùng UTC ISO-8601; business local time luôn gắn MerchantLocation timezone.
- PostgreSQL + PostGIS là source of truth cho transactional data và geospatial search.
- Redis không được trở thành nguồn authoritative cho auth session, order, inventory, cart, checkout hoặc notification history.
- Media binary upload trực tiếp object storage bằng presigned URL; Spring Boot không proxy binary.
- Product và Service dùng chung catalog framework nhưng không được trộn trong cùng cart/order.
- `LocationVariant` là offer có thể mua và là nguồn price/availability tại location.
- Checkout luôn Preview → Commit; Preview không reserve stock và không lock price.
- Tracked inventory chỉ reserve trong transaction tạo Order.
- Order status chỉ thay đổi qua explicit domain command.
- `COMPLETED` là terminal.
- Need chỉ `FULFILLED` khi source Order chuyển sang `CONFIRMED`.
- Review chỉ tạo từ `COMPLETED` OrderItem.
- Ambiguous outcome không tự động làm xấu reputation.
- Admin V1 không có force-status cho Order.
- Transactional Outbox là bắt buộc cho business event cần side effect sau commit.
- Các mutation quan trọng phải hỗ trợ Idempotency-Key đúng contract.
- Browser mutations dùng cookie auth + CSRF; API/mobile-style clients có thể dùng Bearer access token.
- Không log password, raw token, OAuth code, CSRF secret hoặc presigned credentials.
- Breaking HTTP contract yêu cầu `/api/v2`; additive optional changes được phép trong v1.
- Không đưa chat realtime, Kafka/RabbitMQ, escrow/refund, QR payment verification, service booking calendar, AI matching hoặc generic GraphQL vào V1.

---

# 3. Technical baseline được khóa cho V1

## 3.1 Backend runtime

```text
Java 25 LTS
Spring Boot 4.1.x
Maven Wrapper
Spring MVC / embedded Tomcat
Spring Security
Spring Data JPA / Hibernate
Hibernate Spatial
Flyway
PostgreSQL JDBC
Spring Data Redis
Springdoc OpenAPI
Micrometer / Actuator
```

Không dùng Spring WebFlux cho HTTP API V1. SSE có thể chạy bằng Servlet `SseEmitter`; tránh trộn reactive stack nếu chưa có nhu cầu.

## 3.2 Database

```text
PostgreSQL 18
PostGIS 3.6.x
Extensions:
- postgis
- pg_trgm
- unaccent
```

PostgreSQL full-text + trigram + PostGIS đủ cho discovery/matching V1. Không thêm Elasticsearch/OpenSearch ở pilot.

## 3.3 Redis

Redis dùng cho:

```text
rate limiting counters
short-lived public/discovery cache
selected invalidation keys
optional SSE node-local coordination khi scale nhiều API instance
```

Không lưu raw refresh token, checkout authoritative state hay Order state trong Redis.

## 3.4 Frontend runtime

```text
Node.js 24 LTS
pnpm
Next.js 16.2.x Active LTS
React / TypeScript strict
App Router
Server Components cho public/read-heavy pages
Client Components cho interactive workflows
TanStack Query cho client-side server state cần mutation/refetch
React Hook Form + Zod cho form UX
```

Frontend luôn coi backend validation/error code là authoritative.

## 3.5 Storage

```text
Development: MinIO-compatible local S3
Production: S3-compatible object storage
```

Application chỉ phụ thuộc `ObjectStoragePort`, không phụ thuộc provider-specific API ngoài adapter.

## 3.6 Testing

```text
Backend unit: JUnit 5 + AssertJ + Mockito khi thật sự cần
Backend integration: Spring Boot Test + Testcontainers
DB integration image: PostgreSQL/PostGIS 18/3.6 line
Frontend unit/component: Vitest + React Testing Library
E2E: Playwright
Contract: generated OpenAPI + schema drift gate
Concurrency: real PostgreSQL Testcontainers, không dùng H2
```

H2 không được dùng để chứng minh transaction/inventory/PostGIS correctness.

---

# 4. Repository strategy

CoVie dùng **một monorepo** để giữ frontend, backend, generated API client, infra và docs cùng version.

```text
covie/
├── apps/
│   ├── api/
│   │   ├── pom.xml
│   │   ├── mvnw
│   │   ├── mvnw.cmd
│   │   ├── .mvn/
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/vn/covie/
│   │       │   └── resources/
│   │       │       ├── application.yml
│   │       │       ├── application-local.yml
│   │       │       └── db/migration/
│   │       └── test/
│   │           ├── java/vn/covie/
│   │           └── resources/
│   └── web/
│       ├── app/
│       ├── components/
│       ├── features/
│       ├── lib/
│       ├── public/
│       ├── tests/
│       ├── package.json
│       └── next.config.ts
├── packages/
│   ├── api-client/
│   │   ├── src/generated/
│   │   ├── src/index.ts
│   │   └── package.json
│   ├── ui/
│   │   └── package.json
│   └── config/
│       ├── eslint/
│       └── typescript/
├── infra/
│   ├── docker/
│   │   ├── api.Dockerfile
│   │   ├── web.Dockerfile
│   │   └── nginx.conf
│   ├── compose/
│   │   ├── compose.local.yml
│   │   └── compose.ci.yml
│   └── scripts/
├── docs/
│   ├── product/
│   ├── architecture/
│   ├── api/
│   ├── database/
│   ├── runbooks/
│   ├── adr/
│   └── superpowers/plans/
├── .github/workflows/
│   ├── ci.yml
│   └── security.yml
├── pnpm-workspace.yaml
├── package.json
├── .editorconfig
├── .gitignore
├── .env.example
└── README.md
```

## 4.1 Vì sao monorepo

- OpenAPI-generated TypeScript client thay đổi cùng backend contract.
- Một PR có thể thay API + UI + migration cùng lúc.
- CI có thể gate contract drift trước merge.
- Pilot team nhỏ không cần split repo/release process.

---

# 5. Backend modular-monolith structure

Root package:

```text
vn.covie
```

Các module nghiệp vụ:

```text
vn.covie
├── shared
├── auth
├── users
├── locations
├── merchants
├── media
├── catalog
├── inventory
├── shipping
├── carts
├── checkout
├── orders
├── needs
├── reviews
├── reputation
├── notifications
├── moderation
├── settings
└── admin
```

Mỗi module ưu tiên cấu trúc:

```text
<module>/
├── api/
│   ├── <Resource>Controller.java
│   ├── request/
│   └── response/
├── application/
│   ├── <UseCase>Service.java
│   ├── command/
│   ├── query/
│   └── mapper/
├── domain/
│   ├── model/
│   ├── policy/
│   ├── event/
│   └── repository/
└── infrastructure/
    ├── persistence/
    ├── security/
    └── integration/
```

Không bắt buộc tạo đủ 4 thư mục khi module nhỏ. Không tạo layer/file rỗng chỉ để “đúng clean architecture”.

## 5.1 Dependency rule

```text
api -> application -> domain
infrastructure -> domain/application ports
```

Module không import JPA entity của module khác để thao tác trực tiếp. Cross-module behavior đi qua application service/public domain interface hoặc repository projection đã được chủ module cho phép.

## 5.2 Shared kernel chỉ chứa thứ thật sự dùng chung

`shared` được phép chứa:

```text
Money
ApiError / ErrorCode
PageCursor primitives
RequestId
UuidV7Generator
Clock abstraction
Idempotency infrastructure
Outbox primitives
Auditing primitives
security current-actor primitives
```

Không đưa business entity như Order, Merchant, Catalog vào `shared`.

---

# 6. Frontend structure

```text
apps/web/
├── app/
│   ├── (public)/
│   │   ├── page.tsx
│   │   ├── search/
│   │   ├── categories/[slug]/
│   │   ├── items/[slug]/
│   │   └── merchants/[slug]/
│   ├── (auth)/
│   │   ├── login/
│   │   └── onboarding/password/
│   ├── (buyer)/
│   │   ├── carts/
│   │   ├── checkout/
│   │   ├── orders/
│   │   ├── needs/
│   │   ├── notifications/
│   │   └── me/
│   ├── seller/
│   │   ├── page.tsx
│   │   ├── merchants/[merchantId]/
│   │   └── ...
│   └── admin/
├── features/
│   ├── auth/
│   ├── location/
│   ├── catalog/
│   ├── cart/
│   ├── checkout/
│   ├── orders/
│   ├── needs/
│   ├── merchant/
│   ├── inventory/
│   ├── reviews/
│   └── notifications/
├── components/
│   ├── ui/
│   └── layout/
├── lib/
│   ├── api/
│   ├── auth/
│   ├── query/
│   ├── sse/
│   └── env/
└── tests/
```

Frontend không tạo “frontend OrderStateMachine”. Nút hiển thị dựa vào `allowedActions` và server error fallback.

---

# 7. Database migration strategy

Dùng Flyway làm **con đường duy nhất** thay đổi schema.

```text
apps/api/src/main/resources/db/migration/
├── V001__extensions_and_foundation.sql
├── V002__identity_and_users.sql
├── V003__locations.sql
├── V004__merchants.sql
├── V005__media.sql
├── V006__catalog.sql
├── V007__inventory.sql
├── V008__shipping.sql
├── V009__carts.sql
├── V010__orders.sql
├── V011__needs.sql
├── V012__reviews_and_reputation.sql
├── V013__moderation_settings_notifications.sql
├── V014__auth_sessions.sql
├── V015__checkout_sessions.sql
├── V016__idempotency.sql
├── V017__outbox.sql
└── V018__indexes_and_constraints.sql
```

Sau khi migration đã merge/applied vào shared environment, không sửa file cũ; thêm migration mới.

Hibernate:

```text
spring.jpa.hibernate.ddl-auto=validate
```

Không dùng `update` hoặc `create` ngoài isolated test fixture.

---

# 8. Technical tables được chốt

API Design đã nâng các bảng sau từ “optional” thành **required V1 infrastructure**:

```text
auth_sessions
auth_refresh_tokens
checkout_sessions
idempotency_records
outbox_events
```

Không tạo `security_audit_events` riêng ở V1; dùng `audit_logs` hiện có với `event_category = SECURITY` để tránh duplicate audit storage.

## 8.1 `auth_sessions`

Vai trò:

```text
một row = một browser/device login session
session revocation boundary
token family boundary
```

Bắt buộc có index active sessions theo `user_id` và unique token family identifier.

## 8.2 `auth_refresh_tokens`

Mỗi rotation tạo row mới. Raw token không lưu DB.

Các trạng thái suy ra từ:

```text
used_at
revoked_at
replaced_by_token_id
expires_at
```

Reuse token đã used/replaced sẽ revoke toàn session family.

## 8.3 `checkout_sessions`

Server-side opaque checkout token, single-use, short-lived. `preview_snapshot` dùng để detect checkout changes nhưng không đại diện price/stock lock.

## 8.4 `idempotency_records`

Unique logical key:

```text
(actor_id, operation, idempotency_key_hash)
```

Record lưu request hash + final HTTP/result payload đủ để replay cùng response.

Default retention: **24 giờ** cho transaction commands V1.

## 8.5 `outbox_events`

Outbox claim bằng `FOR UPDATE SKIP LOCKED` theo batch nhỏ; consumer phải idempotent.

---

# 9. Authentication implementation choices

## 9.1 Password

CoVie V1 dùng **Argon2id**.

Baseline parameters:

```text
saltLength = 16 bytes
hashLength = 32 bytes
memory = 19456 KiB
iterations = 2
parallelism = 1
```

Mọi password hash lưu encoded form có algorithm parameters. Không hardcode plain salt/pepper vào source.

## 9.2 First registration

```text
Google OAuth callback
→ resolve Google subject/email
→ nếu user mới: tạo short-lived onboarding session
→ bắt buộc set CoVie password
→ transaction tạo/hoàn tất User + GOOGLE identity + EMAIL identity
→ tạo auth session
```

Không cấp full authenticated session trước khi password onboarding hoàn tất.

## 9.3 Access token

Baseline:

```text
JWT signed bằng RSA key pair
TTL = 15 phút
claims tối thiểu:
- sub
- global roles
- sessionId
- iat
- exp
- jti
```

Không embed merchant OWNER/MANAGER/STAFF membership vào JWT.

Keys nạp từ secret files/environment, không commit private key.

## 9.4 Refresh token

Baseline:

```text
opaque random 256-bit token
TTL = 30 ngày
rotation mỗi lần refresh
reuse detection revoke toàn session family
```

DB chỉ lưu SHA-256/HMAC-style token fingerprint hoặc slow-independent token hash phù hợp cho random high-entropy token; raw token chỉ tồn tại phía client.

## 9.5 Web cookie

Production:

```text
Secure
HttpOnly cho access/refresh cookies
SameSite=Lax mặc định
CSRF token cookie readable by frontend
X-CSRF-Token header cho unsafe methods
```

Mobile/Bearer flow không phụ thuộc cookie-CSRF mechanism.

## 9.6 Password reset

V1 chỉ reset bằng recent Google re-authentication:

```text
Google recent auth proof
→ reset password
→ revoke toàn bộ session cũ
→ tạo session mới
```

Không implement email reset token.

---

# 10. Authorization implementation

Tạo `CurrentActor` abstraction:

```java
public record CurrentActor(UUID userId, Set<SystemRole> globalRoles, UUID sessionId) {}
```

Merchant authorization đi qua service/policy:

```text
MerchantAccessPolicy
- requireMembership(userId, merchantId)
- requireRole(...)
- requireLocationAccess(...)
```

Private resource policy:

```text
không có visibility -> 404
có visibility nhưng action forbidden -> 403
```

Controller không tự viết membership query lặp lại.

---

# 11. API foundation conventions

## 11.1 Standard error

Một global exception handler map domain/application exceptions thành:

```json
{
  "error": {
    "code": "ORDER_STATE_CONFLICT",
    "message": "...",
    "details": {},
    "fields": {},
    "requestId": "req_..."
  }
}
```

Frontend logic dùng `code`, không parse `message`.

## 11.2 Request ID

Filter đầu request:

```text
accept safe X-Request-Id hoặc generate mới
put vào MDC
return X-Request-Id
propagate vào logs/audit/outbox diagnostics
```

## 11.3 DTO mapping

Không dùng JPA entity làm response. Mapper viết explicit hoặc MapStruct nếu mapping thực sự lặp nhiều; không thêm MapStruct chỉ để map object 3 field.

## 11.4 Validation

```text
Bean Validation -> structural/input validation
Domain policy -> business invariants
DB constraints -> final integrity backstop
```

Không chỉ dựa Bean Validation để bảo vệ cross-table rules.

---

# 12. Transaction strategy

Default transaction isolation: PostgreSQL `READ COMMITTED`.

Không tăng toàn application lên SERIALIZABLE.

Dùng explicit locking ở critical path:

```text
Checkout tracked inventory:
SELECT inventory balance rows ... FOR UPDATE

Order transition:
lock target Order row khi command có competing transitions

Outbox worker:
FOR UPDATE SKIP LOCKED

Scheduled batch transitions:
FOR UPDATE SKIP LOCKED
```

Mọi transaction giữ ngắn; không gọi external network API trong transaction.

---

# 13. Checkout transaction boundary

## Preview

```text
read current source
validate location/type
read price
read stock availability
check coverage/minimum/shipping
create checkout_session
commit
```

Không inventory reservation.

## Commit

```text
BEGIN
lock checkout_session
validate user/not expired/not consumed
resolve source again
lock relevant tracked inventory rows
revalidate price/availability/coverage/minimum/shipping
compare against preview
insert Order + OrderItems + snapshots
insert fulfillment subtype
insert inventory reservations/movements
insert initial order_event
insert outbox events
consume checkout_session
clear selected cart items nếu source CART
COMMIT
```

Nếu bất kỳ authoritative value quan trọng thay đổi, rollback và trả `CHECKOUT_CHANGED`.

`Idempotency-Key` bao ngoài use case để retry không tạo Order thứ hai.

---

# 14. Inventory implementation

`inventory_balances` là materialized balance; `inventory_movements` là append-only ledger; `inventory_reservations` theo OrderItem.

Invariant:

```text
stock_on_hand >= reserved_quantity >= 0
available = stock_on_hand - reserved_quantity
```

Không expose repository method kiểu `setStockOnHand()` trực tiếp cho controller.

Application commands:

```text
set stock absolute -> compute delta -> movement ADJUSTMENT_IN/OUT
stock in -> movement
stock out -> movement
reserve -> reservation + balance reserved increment
release -> reservation release + reserved decrement
commit -> reservation commit + stock decrement + reserved decrement
```

Concurrency tests bắt buộc dùng real PostgreSQL.

---

# 15. Order state machine implementation

Tạo state transition policy tập trung, không viết `if status` rải rác trong controllers.

Suggested boundary:

```text
OrderCommandService
OrderTransitionPolicy
OrderActionCalculator
OrderEventRecorder
```

`OrderActionCalculator` và `OrderTransitionPolicy` dùng cùng source rules để tránh UI allowedActions lệch backend validation.

Mọi transition:

```text
lock Order
validate actor/action/state/time policy
apply domain change
append order_event
apply inventory/need/reputation side effects thuộc transaction khi cần strong consistency
append outbox event
commit
```

System jobs gọi cùng application command path với `SystemActor`, không direct SQL status update.

---

# 16. Need implementation

Need discovery V1 dùng PostgreSQL:

```text
need_type
category hierarchy
keyword/trigram/full-text
location radius/PostGIS
merchant location coverage
active status / expiry
```

Response invariant được bảo vệ bằng unique partial index hoặc equivalent DB constraint cho:

```text
one ACTIVE NeedResponse per (need_request_id, merchant_location_id)
```

Need → Checkout phải validate toàn source chain, không chỉ trust IDs client gửi.

Order confirm side effect:

```text
source_type = NEED_REQUEST
→ source NeedRequest ACTIVE ?
→ mark FULFILLED
→ outbox notification
```

Reject/expire/cancel-before-confirm không fulfill Need.

---

# 17. Review & reputation implementation

Review creation transaction resolve target từ OrderItem; client không gửi authoritative merchant/catalog target.

Stats tables là projection/rebuildable aggregate.

Strong rule:

```text
Order business outcome source of truth
→ projection updater
→ merchant/user reputation stats
```

Ambiguous states không tăng positive/negative counters.

Projection update phải idempotent theo source order/outcome version hoặc event id để outbox retry không double-count.

---

# 18. Outbox, notification và SSE

## 18.1 Outbox writer

Domain/application service ghi `outbox_events` trong cùng transaction với business change.

## 18.2 Worker

Polling baseline:

```text
250–1000 ms interval
batch 50–100
FOR UPDATE SKIP LOCKED
bounded retry with exponential backoff
terminal/dead status sau max attempts
```

Các giá trị cuối cùng để config qua application properties, không hardcode trong worker.

## 18.3 Notification consumer

Consumer tạo `notifications` bằng event idempotency key để retry không tạo duplicate notification.

## 18.4 SSE

Endpoint:

```text
GET /api/v1/me/events
```

SSE registry giữ connection in-memory cho instance hiện tại. Event chỉ chứa IDs/version. Client reconnect thì refetch REST; V1 không durable replay.

Khi scale multi-instance, outbox notification persistence vẫn đúng; SSE cross-node fanout có thể thêm Redis Pub/Sub mà không đổi REST contract.

---

# 19. Scheduled jobs

Jobs V1:

```text
pending Order expiration
Order auto-completion
issue timeout -> CLOSED_WITH_ISSUE
Need expiration
checkout session cleanup
idempotency cleanup
media orphan cleanup
outbox retry/recovery
```

Mỗi job xử lý batch bằng row locking + `SKIP LOCKED` để nhiều instance không double-process.

Không giữ global distributed lock nếu row-level claim đã đủ.

---

# 20. Rate limiting

Redis-backed endpoint-group limiter.

Key dimensions:

```text
AUTH_LOGIN: IP + normalized email fingerprint
AUTH_GOOGLE_START: IP
AUTH_REFRESH: session/user + IP
PUBLIC_DISCOVERY: IP
CHECKOUT: userId
ORDER_COMMAND: userId
NEED_CREATE: userId
NEED_RESPONSE: userId + merchantId
REPORT: userId
MEDIA_UPLOAD: userId
ADMIN: admin userId
```

Rate-limit implementation phải trả:

```text
429
Retry-After
RATE_LIMIT_EXCEEDED
```

Business limit như `max 3 NeedResponse items` không đi qua rate limiter.

---

# 21. Search and geospatial plan

V1 dùng:

```text
PostGIS geography(Point,4326)
GIST indexes
ST_DWithin
ST_Distance
pg_trgm
unaccent
PostgreSQL full-text 'simple' configuration
```

Không ép tất cả query qua một generic search endpoint/DSL.

LocationContext normalization nằm backend và trả resolved location context khi cần.

Geocoding provider được cô lập sau interface; core discovery chỉ cần coordinates + normalized address fields. Pilot có thể vận hành với user-provided address text + map coordinates mà không cho geocoding provider tham gia transaction-critical logic.

---

# 22. Media implementation

Ports:

```text
ObjectStoragePort
MediaSecurityPolicy
MediaUploadService
```

Flow:

```text
request upload session
→ create PENDING_UPLOAD MediaAsset
→ presigned PUT
→ client uploads object
→ complete command HEAD/metadata verify
→ READY
```

Object key do backend tạo, không nhận arbitrary key từ client.

Cleanup xóa PENDING/orphan objects theo retention sau khi verify không còn reference.

---

# 23. OpenAPI and generated client

Backend build sinh OpenAPI JSON.

Generated client path:

```text
packages/api-client/src/generated/
```

CI gate:

```text
backend contract build
→ regenerate TS client
→ git diff must be clean
```

Không hand-edit files trong `src/generated`.

Frontend có wrapper mỏng tại:

```text
apps/web/lib/api/
```

để cấu hình base URL, cookies/CSRF, error mapping và request ID; wrapper không duplicate DTOs.

---

# 24. Same-domain deployment topology

```text
Internet
   |
 Nginx
   ├── /api/v1/* -> Spring Boot :8080
   ├── /actuator/* -> internal-only / blocked public
   └── /* -> Next.js :3000

Spring Boot -> PostgreSQL/PostGIS
Spring Boot -> Redis
Spring Boot -> S3-compatible storage
```

Public domain:

```text
https://covie.vn/
https://covie.vn/api/v1/*
```

Browser không cần cross-origin API request trong normal production flow.

---

# 25. Local development

`infra/compose/compose.local.yml` chạy:

```text
postgres + postgis
redis
minio
mailpit: không bắt buộc vì V1 không email reset/verification
```

App processes chạy native để hot reload nhanh:

```text
pnpm --filter web dev
./apps/api/mvnw -f apps/api/pom.xml spring-boot:run
```

Seed local phải tạo:

```text
location area tree mẫu Grand Park
categories mẫu
admin user test fixture
merchant/location/catalog fixture
inventory fixture
```

Không seed password/token production-like secrets vào git.

---

# 26. Observability

Backend:

```text
structured JSON logs production
MDC requestId/userId/merchantId/orderId khi có
Actuator health/readiness/liveness
Micrometer metrics
DB pool metrics
HTTP latency/status metrics
outbox backlog/retry metrics
SSE connection count
checkout conflict rate
inventory conflict rate
```

Frontend:

```text
route/page errors
API error code frequency
Core Web Vitals
client-side exception reporting adapter
```

Sensitive body fields không log.

---

# 27. CI quality gates

Pull request phải chạy tối thiểu:

```text
Backend:
- mvnw spotless/checkstyle-equivalent gate nếu được cấu hình
- unit tests
- integration tests
- migration validation
- OpenAPI generation

Frontend:
- pnpm lint
- pnpm typecheck
- unit/component tests
- next build

Contract:
- regenerate api client
- fail nếu generated diff chưa commit

E2E smoke:
- guest search
- auth fixture login
- cart -> preview -> commit
- seller confirm
- buyer complete
```

Concurrency suite có thể chạy riêng nhưng phải chạy trước release candidate.

---

# 28. Security gates

Release candidate phải verify:

```text
CSRF negative tests
404-vs-403 private resource tests
refresh reuse detection
session revoke tests
merchant location authorization matrix
admin authorization
no secret in logs
secure cookie attributes production profile
rate-limit auth endpoints
presigned upload ownership
SQL injection-resistant typed filters
Open redirect protection OAuth callback
```

Dependency/security scanner chạy CI; critical/high issue trên runtime dependency phải được triage trước deploy.

---

# 29. Delivery strategy: vertical slices, không build toàn DB rồi mới UI

Mỗi milestone phải cho ra phần mềm chạy được end-to-end.

```text
Foundation
→ Auth/User
→ Merchant/Catalog Discovery
→ Cart/Checkout/Inventory
→ Order lifecycle
→ Need matching
→ Review/Reputation
→ Moderation/Admin
→ Hardening/Pilot
```

Không hoàn thiện toàn bộ Seller Center trước khi có buyer order end-to-end.

---

# 30. Sub-plan decomposition

Master Plan này chia implementation thành 10 kế hoạch nhỏ. Mỗi kế hoạch phải được viết chi tiết theo TDD trước khi thực thi.

## Plan 01 — Foundation & Repository

**File:** `docs/superpowers/plans/2026-08-08-covie-foundation.md`

Deliverable:

```text
monorepo bootstrapped
Next.js boots
Spring Boot boots
PostgreSQL/PostGIS/Redis/MinIO local infra
Flyway foundation
request-id/error DTO baseline
OpenAPI generation
CI baseline
```

Gate: `/api/v1/health`/Actuator internal health + web home render + CI green.

## Plan 02 — Identity, Auth & User

**File:** `docs/superpowers/plans/2026-08-08-covie-auth-user.md`

Deliverable:

```text
users/user identities/addresses
Google first-registration onboarding
set-password
email/password login
access JWT
rotating refresh tokens
session management
Google password recovery
CSRF
CurrentActor
```

Gate: complete authentication testing contract passes.

## Plan 03 — Merchant, Location, Media & Catalog

**File:** `docs/superpowers/plans/2026-08-08-covie-merchant-catalog.md`

Deliverable:

```text
MerchantProfile/members/location authorization
location hours/contact
media presigned upload
categories/catalog/options/variants/location offers
publish/pause lifecycle
public catalog detail/search
PostGIS LocationContext
```

Gate: guest can discover a purchasable `LocationVariant`; seller can manage it.

## Plan 04 — Shipping, Cart, Inventory & Checkout

**File:** `docs/superpowers/plans/2026-08-08-covie-checkout-inventory.md`

Deliverable:

```text
delivery coverage
shipping policies
multi-cart
tracked/untracked inventory
checkout Preview/Commit
checkout_sessions
idempotency
inventory concurrency
```

Gate: buyer can create one `PENDING_CONFIRMATION` Order and concurrent last-stock test yields 1 success + 1 conflict.

## Plan 05 — Orders & Fulfillment

**File:** `docs/superpowers/plans/2026-08-08-covie-orders.md`

Deliverable:

```text
canonical order read model
allowedActions
seller confirm/reject
buyer/seller cancellation
seller delivery
pickup/no-show
service fulfillment
issues
auto-complete/expiry
order events
reservation release/commit
```

Gate: Product delivery, Pickup và Service happy paths + critical forbidden transitions pass.

## Plan 06 — Needs

**File:** `docs/superpowers/plans/2026-08-08-covie-needs.md`

Deliverable:

```text
Need create/list/detail/expire/cancel
seller Need discovery
NeedResponse create/edit/withdraw
one active response/location
Need direct checkout source validation
Need -> Order -> FULFILLED
```

Gate: rejected source Order leaves Need active; confirmed source Order fulfills Need.

## Plan 07 — Reviews, Reputation & Notifications

**File:** `docs/superpowers/plans/2026-08-08-covie-trust-notifications.md`

Deliverable:

```text
review eligibility
public reviews
rating aggregates
buyer/merchant reputation aggregates
outbox worker
notification inbox
SSE
```

Gate: outbox retry cannot duplicate projection or notification.

## Plan 08 — Moderation, Settings & Admin

**File:** `docs/superpowers/plans/2026-08-08-covie-admin-moderation.md`

Deliverable:

```text
reports
moderation cases/actions
AUTO/PRE/HYBRID publishing
system settings + history
admin read models
admin order audit-only
admin web surface
```

Gate: policy changes take effect without deploy and Order has no admin force-state command.

## Plan 09 — Frontend Buyer/Seller Completion

**File:** `docs/superpowers/plans/2026-08-08-covie-web-completion.md`

Deliverable:

```text
PWA shell
public discovery UX
multi-cart UX
checkout UX
order CTA matrix
Need UX
Seller Center
notifications/deep links
review/reputation UX
responsive states
```

Gate: IA/UI P0 screens are connected to generated API client and Playwright end-to-end flows pass.

## Plan 10 — Production Hardening & Pilot Launch

**File:** `docs/superpowers/plans/2026-08-08-covie-pilot-hardening.md`

Deliverable:

```text
Nginx same-domain routing
production Docker images
secret management contract
backups/restore runbook
monitoring/alerts
security gates
load/concurrency tests
seed/bootstrap Grand Park pilot
release checklist
rollback runbook
```

Gate: staging rehearsal passes from clean DB migration to smoke test and rollback drill.

---

# 31. Dependency graph

```text
Plan 01 Foundation
   |
   v
Plan 02 Auth/User
   |
   +---------------------+
   |                     |
   v                     v
Plan 03 Merchant/Catalog  frontend auth shell
   |
   v
Plan 04 Checkout/Inventory
   |
   v
Plan 05 Orders
   |
   +----------+
   |          |
   v          v
Plan 06 Needs Plan 07 Trust/Notifications
   |          |
   +-----+----+
         v
Plan 08 Admin/Moderation
         |
         v
Plan 09 Web Completion
         |
         v
Plan 10 Hardening/Pilot
```

Plan 07 Outbox foundation có thể được tạo sớm ở Plan 01 ở mức infrastructure, nhưng consumer business hoàn chỉnh ở Plan 07.

---

# 32. Milestone gates

## M0 — Development Environment Ready

Acceptance:

```text
repo clone -> one documented bootstrap path
Postgres/PostGIS, Redis, MinIO start locally
Spring Boot starts and Flyway migrates clean DB
Next.js starts
CI runs baseline checks
```

## M1 — Identity Ready

Acceptance:

```text
new Google user -> set password -> authenticated
existing user -> Google login
existing user -> email/password login
refresh rotates
reuse revokes session
multi-device sessions independent
```

## M2 — Marketplace Discovery Ready

Acceptance:

```text
seller creates merchant/location
seller creates/publishes catalog offer
guest searches by location
guest opens detail with resolved MerchantLocation
```

## M3 — First Real Order

Acceptance:

```text
buyer adds locationVariant to cart
Preview calculates current price/shipping
Commit creates pending Order
tracked stock reserved transactionally
seller confirms
buyer completes
inventory reservation committed
```

## M4 — Full Order Lifecycle

Acceptance:

```text
seller reject
pending expire
buyer pre-confirm cancel
post-confirm cancellation request
seller cancel
pickup
no-show contest
service complete
issue prevents auto-complete
```

## M5 — Need Marketplace

Acceptance:

```text
buyer posts Need
seller discovers and responds
buyer direct checkout
pending Order leaves Need active
confirmed Order fulfills Need
```

## M6 — Trust & Operations

Acceptance:

```text
review completed order
reputation computed correctly
notifications persisted
SSE refreshes UI
moderation/report/settings work
admin audit works
```

## M7 — Pilot Release Candidate

Acceptance:

```text
all P0 UI screens wired
security test matrix green
concurrency suite green
migration from empty DB green
backup/restore rehearsal green
same-domain staging deployment green
```

---

# 33. First implementation plan — Foundation scope

Plan 01 là plan phải viết và thực thi đầu tiên. Nó không tạo business features giả. Nó chỉ tạo nền mà Plan 02–10 dựa vào.

Plan 01 phải tạo chính xác các interface/baseline sau:

```text
vn.covie.shared.id.UuidV7Generator
vn.covie.shared.web.RequestIdFilter
vn.covie.shared.web.ApiErrorResponse
vn.covie.shared.web.GlobalExceptionHandler
vn.covie.shared.time.AppClock
vn.covie.shared.money.Money
vn.covie.shared.outbox.OutboxEventWriter
vn.covie.shared.security.CurrentActor
```

Frontend baseline:

```text
packages/api-client
apps/web/lib/api/api-client.ts
apps/web/lib/api/api-error.ts
apps/web/lib/env/server.ts
apps/web/lib/env/client.ts
```

Infra baseline:

```text
PostgreSQL 18 + PostGIS
Redis
MinIO
Nginx config skeleton
GitHub Actions CI
```

---

# 34. TDD rule for every detailed sub-plan

Mỗi task implementation phải theo loop:

```text
write focused failing test
→ run and observe expected failure
→ implement minimum behavior
→ run focused test
→ run relevant module suite
→ commit
```

Không tạo một commit 20 domain cùng lúc.

Integration behavior về JPA/SQL/PostGIS/locking luôn test với Testcontainers.

---

# 35. Commit strategy

Commit theo independently reviewable unit, ví dụ:

```text
chore: bootstrap covie monorepo
feat(api): add request correlation and error contract
feat(auth): add google onboarding session
feat(auth): rotate refresh tokens with reuse detection
feat(catalog): create aggregate catalog draft
feat(inventory): reserve tracked stock atomically
feat(checkout): commit checkout idempotently
feat(order): add seller confirmation command
feat(needs): add merchant need response
feat(outbox): persist notifications idempotently
```

Không commit generated secrets, `.env`, service-account JSON hoặc private keys.

---

# 36. Environment contract

Root `.env.example` chỉ liệt kê tên/shape, không chứa secret thật.

Backend minimum:

```text
SPRING_PROFILES_ACTIVE
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_URL
APP_PUBLIC_URL
JWT_PRIVATE_KEY_PATH
JWT_PUBLIC_KEY_PATH
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
GOOGLE_REDIRECT_URI
S3_ENDPOINT
S3_REGION
S3_BUCKET
S3_ACCESS_KEY
S3_SECRET_KEY
S3_PUBLIC_BASE_URL
```

Frontend minimum:

```text
NEXT_PUBLIC_APP_URL
API_INTERNAL_BASE_URL
```

Không expose `GOOGLE_CLIENT_SECRET`, DB credentials hoặc S3 secret bằng `NEXT_PUBLIC_*`.

---

# 37. Seed and test identity policy

Local/staging không phụ thuộc Google OAuth thật cho mọi automated E2E run.

Tạo test-only authentication fixture dưới profile `test/e2e` hoặc direct DB fixture builder; tuyệt đối không expose bypass endpoint trong production profile.

Production startup fail nếu test auth/bypass profile bị bật.

---

# 38. Performance baseline before optimization

Không cache tùy tiện trước khi đo.

Các query cần benchmark/index ngay từ đầu:

```text
catalog search by location/category/query
MerchantLocation radius coverage
Need discovery by category/radius
active carts by user
seller Order list by location/status
pending order expiration scan
outbox pending scan
notification list by user
```

Dùng `EXPLAIN (ANALYZE, BUFFERS)` cho query nóng khi dataset fixture đủ lớn.

---

# 39. Data retention baseline

Technical ephemeral data:

```text
checkout_sessions: cleanup sau expiry + safety window
idempotency_records: 24h default
used/revoked refresh token rows: giữ đủ cửa sổ để reuse detection + security audit, sau đó purge theo policy
published outbox events: giữ ngắn hạn phục vụ diagnostics rồi archive/purge
PENDING_UPLOAD orphan media: cleanup theo retention
```

Business/audit/order/history data không áp retention chung với technical tables.

---

# 40. Backup and restore baseline

Trước pilot cần runbook:

```text
daily PostgreSQL backup
point-in-time recovery nếu hosting hỗ trợ
object-storage versioning/lifecycle theo provider
restore rehearsal vào staging
migration compatibility check sau restore
```

Không coi Docker volume là backup strategy.

---

# 41. Definition of Done cho một backend feature

Feature chỉ hoàn tất khi:

```text
API contract đúng design
business rules nằm application/domain layer
JPA entity không leak
migration/constraint/index cần thiết đã có
unit/integration tests pass
negative authz tests có
idempotency/concurrency test có nếu feature cần
outbox side effect atomic nếu feature phát event
OpenAPI updated
generated client updated
structured logs không leak sensitive data
```

---

# 42. Definition of Done cho một frontend feature

```text
uses generated API types/client
loading/empty/error states implemented
server error codes mapped safely
no duplicated authoritative business calculation
responsive mobile-first behavior
critical mutation handles 409/422/429
SSE only triggers REST refetch
component tests for branching UI
Playwright coverage for P0 journey
```

---

# 43. Release blockers

Không pilot nếu còn một trong các lỗi:

```text
tracked inventory có thể oversell trong concurrency test
same idempotency key tạo duplicate Order
refresh token reuse không revoke session
buyer xem được private Order của user khác
STAFF vượt location scope
Order status PATCH trực tiếp được
Need fulfilled khi Order chỉ pending
review được tạo trước COMPLETED
outbox transaction có thể mất event sau business commit
production cookies thiếu Secure/HttpOnly phù hợp
migration clean DB không chạy được
backup chưa restore thử
```

---

# 44. Explicitly deferred after V1

Technical Plan không chuẩn bị infrastructure sớm cho:

```text
microservices service mesh
Kafka cluster
WebSocket chat
native mobile backend gateway
payment reconciliation
wallet/escrow/refund
real-time driver tracking
search cluster
AI recommendation infrastructure
booking calendar engine
multi-region active-active
```

Chỉ giữ boundary/interface đủ sạch để sau này thay adapter hoặc tách module khi có nhu cầu thật.

---

# 45. Self-review against approved API Design

Coverage check:

```text
Spring module/package boundaries              -> Sections 4–6
Controller/application/domain/repository       -> Section 5
Spring Security/Google/cookies/CSRF            -> Section 9
Password hashing                              -> Section 9.1
Access-token signing/key management            -> Section 9.3
Refresh token/session transactions             -> Sections 8, 9.4
Technical migrations                          -> Sections 7–8
Inventory locking/isolation                    -> Sections 12, 14
Checkout transactions                          -> Section 13
Outbox claim/retry                             -> Section 18
Scheduled-job locking                          -> Section 19
SSE management                                -> Section 18.4
Presigned storage                             -> Section 22
Rate limiting                                 -> Section 20
OpenAPI -> TypeScript client                  -> Section 23
DTO/validation                                -> Section 11
Integration/concurrency/contract testing       -> Sections 27, 34, 41
Observability/request correlation              -> Sections 11.2, 26
Same-domain routing                           -> Section 24
```

Business coverage:

```text
Auth/User             -> Plans 02
Merchant/Catalog      -> Plan 03
Shipping/Cart/Checkout/Inventory -> Plan 04
Orders/Fulfillment    -> Plan 05
Needs                 -> Plan 06
Reviews/Reputation    -> Plan 07
Notifications/SSE     -> Plan 07
Moderation/Settings/Admin -> Plan 08
Buyer/Seller Web      -> Plan 09
Deployment/Pilot      -> Plan 10
```

No V1 feature requires microservice/message broker/search engine to launch.

---

# 46. Immediate next execution artifact

Không bắt đầu code trực tiếp từ Master Plan này.

Artifact tiếp theo cần tạo là:

```text
docs/superpowers/plans/2026-08-08-covie-foundation.md
```

Plan đó phải chia Plan 01 thành task nhỏ có:

```text
exact file paths
failing tests
commands + expected failures
minimal implementations
verification commands
commit boundaries
```

Sau khi Foundation plan được review, mới tạo worktree/branch và bắt đầu implementation.

---

# 47. Final implementation sequence

```text
01 Foundation
02 Auth/User
03 Merchant/Catalog
04 Shipping/Cart/Inventory/Checkout
05 Orders/Fulfillment
06 Needs
07 Reviews/Reputation/Outbox/Notifications
08 Moderation/Settings/Admin
09 Web Completion
10 Production Hardening/Pilot
```

Mỗi plan phải tạo một system state deployable/testable hơn plan trước. Không có “big bang integration” ở cuối.

## Advertising MVP extension — 2026-10-08

Implemented both approved phases: Admin/Seller campaign ownership and approval/reapproval, verified image/MP4/WebM creatives and four CSS templates, scheduled device-aware delivery, carousel controls, impression/click deduplication, CTR and daily analytics. Wide desktop rail is limited to home/search with measured spare gutter; responsive horizontal slots cover home, search feed and item details. API and generated client are synchronized. Implementation and operations evidence: `docs/CoVie-ad-engine.md`; execution plan: `docs/superpowers/plans/2026-10-08-ad-engine.md`. Local recovered demo API has V015 applied; staging/production deployment is outside current verified evidence.
