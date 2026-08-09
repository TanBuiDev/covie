# CoVie — START HERE

> **Entry point duy nhất cho Developer và AI Agent trước khi triển khai CoVie V1**

**Document:** CoVie START HERE  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Approved implementation guide  
**Project:** CoVie  
**Applies to:** Product, UX, Database, API, Master Technical Plan và Detailed Technical Plans 01–10

---

# 1. Mục đích của tài liệu này

File này là **tài liệu đầu tiên phải đọc** trước khi một Developer, AI coding agent, reviewer hoặc technical lead bắt đầu làm việc với CoVie.

Nó không thay thế Product Plan, Database Design, API Design hay các Technical Plan.

Vai trò của file này là:

```text
START HERE
    |
    +--> cho biết phải đọc tài liệu nào
    +--> đọc theo thứ tự nào
    +--> tài liệu nào quyết định vấn đề gì
    +--> khi có xung đột thì tài liệu nào được ưu tiên
    +--> Plan nào được phép triển khai tại thời điểm hiện tại
    +--> khi nào được chuyển sang Plan kế tiếp
    +--> AI/Developer được phép và không được phép tự quyết điều gì
```

**Không bắt đầu code chỉ bằng cách mở một Detailed Plan ngẫu nhiên.**

Mọi implementation session phải bắt đầu từ file này.

---

# 2. Bộ tài liệu CoVie V1 chính thức

Bộ tài liệu hiện tại gồm **16 file**.

## 2.1 Product / UX foundation

```text
CoVie-product-plan-v0.3.md
CoVie-user-flow-v1.0.md
CoVie-information-architecture-ui-flow-v1.0.md
```

## 2.2 Data / API contracts

```text
CoVie-database-design-v1.0.md
CoVie-api-design-v1.0.md
```

## 2.3 Technical architecture master

```text
CoVie-technical-plan-v1.0.md
```

## 2.4 Detailed implementation plans

```text
CoVie-technical-plan-01-foundation-repository-v1.0.md
CoVie-technical-plan-02-identity-auth-user-v1.0.md
CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md
CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md
CoVie-technical-plan-05-orders-fulfillment-v1.0.md
CoVie-technical-plan-06-need-requests-matching-v1.0.md
CoVie-technical-plan-07-reviews-reputation-outbox-notifications-sse-v1.0.md
CoVie-technical-plan-08-moderation-system-settings-admin-v1.0.md
CoVie-technical-plan-09-web-completion-v1.0.md
CoVie-technical-plan-10-production-hardening-pilot-launch-v1.0.md
```

---

# 3. Nguyên tắc quan trọng nhất

## 3.1 Không có một tài liệu duy nhất thay thế tất cả tài liệu còn lại

CoVie được thiết kế theo nhiều lớp:

```text
Business
   ↓
User experience
   ↓
Persistent data
   ↓
External API contract
   ↓
Technical architecture
   ↓
Detailed implementation
   ↓
Code
```

Mỗi tài liệu sở hữu một loại quyết định khác nhau.

Không được đọc một Detailed Technical Plan rồi bỏ qua Product Plan, Database Design hoặc API Design.

---

## 3.2 Không triển khai tất cả Plan song song

Detailed Plans phải được thực hiện theo dependency order:

```text
01
 ↓
02
 ↓
03
 ↓
04
 ↓
05
 ↓
06
 ↓
07
 ↓
08
 ↓
09
 ↓
10
```

Plan sau được phép dựa trên output đã được kiểm chứng của Plan trước.

---

## 3.3 Acceptance gate quan trọng hơn cảm giác “đã code xong”

Một Plan chỉ được coi là hoàn thành khi:

```text
implementation complete
+
tests green
+
verification commands green
+
required generated artifacts current
+
acceptance checklist green
+
no unresolved blocker
```

**“Code compile được” không đồng nghĩa với “Plan hoàn thành”.**

---

# 4. Thứ tự đọc tài liệu lần đầu

Một Developer/AI Agent mới tham gia project phải đọc theo thứ tự sau.

---

## Phase A — Hiểu sản phẩm

### 1. `CoVie-product-plan-v0.3.md`

Đọc để hiểu:

```text
CoVie là gì
V1 giải quyết vấn đề gì
ai là Buyer
ai là Seller/Provider
Product vs Service
Resale
Need / "Cần mua"
Order
Review/Reputation
Moderation
V1 scope
V1 non-goals
pilot context
```

Không lấy implementation detail từ file này.

---

### 2. `CoVie-user-flow-v1.0.md`

Đọc để hiểu:

```text
Guest journey
Buyer journey
Seller/Provider journey
Admin journey
Checkout journey
Order lifecycle ở góc nhìn người dùng
Need journey
Review journey
error/recovery flows
```

File này trả lời:

> Người dùng làm gì trước, làm gì sau?

---

### 3. `CoVie-information-architecture-ui-flow-v1.0.md`

Đọc để hiểu:

```text
screen hierarchy
navigation
route/screen relationships
mobile/desktop behavior
UI states
CTA placement
Seller Center
Admin surfaces
```

File này trả lời:

> Các hành vi trong User Flow xuất hiện ở màn hình nào?

---

# 5. Phase B — Hiểu contract hệ thống

## 4. `CoVie-database-design-v1.0.md`

Đọc để hiểu:

```text
canonical entities
table relationships
persistent state
foreign keys
unique constraints
snapshots
soft delete
inventory ledger
Order persistence
Need persistence
Review/Reputation persistence
Moderation/settings persistence
```

Database Design là nguồn tham chiếu cho **persistent business model**.

Không được đổi schema chỉ vì implementation thuận tiện hơn.

Schema change thực tế phải đi qua Detailed Plan + Flyway.

---

## 5. `CoVie-api-design-v1.0.md`

Đọc để hiểu:

```text
REST resource boundaries
/api/v1
auth contract
checkout contract
Order command endpoints
Need APIs
review APIs
admin APIs
error envelope
Idempotency-Key
If-Match
cursor pagination
SSE
OpenAPI
authorization semantics
404 vs 403
```

API Design là nguồn tham chiếu cho **external behavior contract**.

Frontend không được tự tạo business API khác chỉ vì dễ làm UI hơn.

---

# 6. Phase C — Hiểu technical architecture tổng thể

## 6. `CoVie-technical-plan-v1.0.md`

Master Technical Plan trả lời:

```text
monorepo structure
Spring Boot modular monolith
Next.js architecture
PostgreSQL/PostGIS
Redis role
S3 storage
Spring Security
transaction boundaries
Flyway strategy
Outbox
scheduled jobs
SSE
OpenAPI generation
CI
deployment architecture
testing strategy
Plan 01–10 dependency graph
```

**Không implement trực tiếp từ Master Technical Plan.**

Master Plan là architectural map.

Implementation thực tế được thực hiện bằng Detailed Plans 01–10.

---

# 7. Phase D — Detailed implementation sequence

Sau khi đã đọc Foundation Documents + Master Technical Plan, chỉ đọc sâu Detailed Plan đang chuẩn bị triển khai.

Thứ tự chính thức:

| # | Detailed Plan | Phạm vi chính |
|---|---|---|
| 01 | `CoVie-technical-plan-01-foundation-repository-v1.0.md` | Repository, Spring/Next foundation, PostGIS, Redis/MinIO local, shared primitives, OpenAPI, CI |
| 02 | `CoVie-technical-plan-02-identity-auth-user-v1.0.md` | User, Google onboarding, password, JWT, sessions, CSRF, `/me` |
| 03 | `CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md` | Merchant, membership, location, media, catalog, variants, offers |
| 04 | `CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md` | Coverage, shipping, carts, inventory, Checkout Engine, Order creation foundation |
| 05 | `CoVie-technical-plan-05-orders-fulfillment-v1.0.md` | Order lifecycle, commands, cancellation, fulfillment, issues, jobs |
| 06 | `CoVie-technical-plan-06-need-requests-matching-v1.0.md` | Need requests, matching, response, Need Checkout, Order attribution |
| 07 | `CoVie-technical-plan-07-reviews-reputation-outbox-notifications-sse-v1.0.md` | Review, Reputation, Outbox worker, Notifications, SSE |
| 08 | `CoVie-technical-plan-08-moderation-system-settings-admin-v1.0.md` | Moderation, settings, reports, suspension, Admin |
| 09 | `CoVie-technical-plan-09-web-completion-v1.0.md` | Complete Buyer/Seller/Admin Web UX, PWA, Playwright |
| 10 | `CoVie-technical-plan-10-production-hardening-pilot-launch-v1.0.md` | Production hardening, rate limits, observability, backup, deployment, pilot |

---

# 8. Dependency graph

```text
Product Plan
    |
    +--> User Flow
    |      |
    |      +--> IA / UI Flow
    |
    +--> Database Design
    |
    +--> API Design
            |
            v
    Master Technical Plan
            |
            v
      Plan 01 Foundation
            |
            v
      Plan 02 Auth/User
            |
            v
 Plan 03 Merchant/Catalog
            |
            v
 Plan 04 Shipping/Cart/
 Inventory/Checkout
            |
            v
 Plan 05 Orders/Fulfillment
            |
            v
      Plan 06 Needs
            |
            v
 Plan 07 Trust/Outbox/
 Notifications/SSE
            |
            v
 Plan 08 Moderation/
 Settings/Admin
            |
            v
 Plan 09 Web Completion
            |
            v
 Plan 10 Production/
 Pilot Hardening
```

---

# 9. Document ownership — tài liệu nào quyết định vấn đề gì?

| Vấn đề | Source of truth chính |
|---|---|
| Product scope / V1 feature | Product Plan |
| User journey | User Flow |
| Screen/navigation/UX flow | IA/UI Flow |
| Persistent business model | Database Design |
| Public/backend API semantics | API Design |
| Architecture/system-wide technical decisions | Master Technical Plan |
| Exact implementation steps/files/tests/commands | Current Detailed Technical Plan |
| Runtime behavior cuối cùng | Code đã implement + tests, nhưng phải conform với tài liệu phía trên |

---

# 10. Precedence rules khi tài liệu có khác nhau

Đây là phần bắt buộc đối với Developer và AI.

## Rule 1 — Ưu tiên tài liệu sở hữu đúng domain

Ví dụ:

```text
"Feature có nằm trong V1 không?"
-> Product Plan

"Buyer đi từ màn hình nào sang màn hình nào?"
-> User Flow / IA UI

"Order lưu snapshot nào?"
-> Database Design

"Endpoint command tên gì / semantics gì?"
-> API Design

"Module nằm ở package nào?"
-> Technical Plan

"Task này tạo file nào?"
-> Detailed Plan hiện tại
```

Không dùng Technical Plan để tự thay Product requirement.

---

## Rule 2 — Detailed Plan có thể refine/correct Master Plan

Detailed Plan mới hơn được phép sửa một **technical implementation detail** trong Master Technical Plan khi nó ghi rõ:

```text
correction
refinement
migration ordering correction
dependency correction
implementation sequencing fix
```

Ví dụ đã xảy ra:

```text
Master Plan từng conceptualize auth technical migrations ở vị trí muộn.

Plan 02 phát hiện Flyway sequencing conflict và sửa migration numbering
theo implementation dependency order.
```

Trong trường hợp này:

```text
explicit Detailed Plan correction
    >
older Master Technical Plan detail
```

Nhưng chỉ trong phạm vi correction đã ghi rõ.

---

## Rule 3 — Detailed Plan không được âm thầm thay đổi Product/API contract

Ví dụ không hợp lệ:

```text
Product Plan:
  Review chỉ sau COMPLETED

Developer:
  "Cho review sau CONFIRMED dễ code hơn"

=> KHÔNG ĐƯỢC
```

Hoặc:

```text
API Design:
  Order status đổi qua explicit commands

Developer:
  "PATCH /orders/{id}/status đơn giản hơn"

=> KHÔNG ĐƯỢC
```

---

## Rule 4 — Explicit approved correction thắng older conflicting statement

Nếu một Detailed Plan có câu rõ ràng:

```text
"This plan corrects..."
"This replaces..."
"From this point forward..."
"Implementation sequencing correction..."
```

thì correction đó là canonical cho issue cụ thể đó.

Không mở rộng correction sang vấn đề khác.

---

## Rule 5 — Không tự giải quyết conflict bằng suy đoán

Nếu hai tài liệu thực sự xung đột và các rule trên không giải quyết được:

```text
STOP current implementation task
        ↓
record conflict
        ↓
identify both source sections
        ↓
propose smallest resolution
        ↓
get explicit approval / update authoritative document
        ↓
resume implementation
```

**Không chọn phương án “có vẻ hợp lý nhất” rồi tiếp tục.**

---

# 11. Current canonical migration sequence

Không sử dụng conceptual migration numbering cũ nếu nó mâu thuẫn với Detailed Plans.

Canonical implementation sequence hiện tại:

```text
V001  Foundation/extensions

V002  Identity/users/audit
V003  Auth sessions/tickets

V004  Location/merchant/media
V005  Catalog/search
V006  Idempotency/outbox

V007  Delivery/shipping/inventory
V008  Carts/checkout sessions
V009  Order creation foundation

V010  Order lifecycle

V011  Need matching

V012  Trust/notifications/outbox additions

V013  Moderation/system settings/admin
```

Plan 09:

```text
no migration by default
```

Plan 10:

```text
no migration by default
```

Next available:

```text
V014
```

**Không chỉnh sửa migration V001–V013 sau khi đã được apply vào environment dùng chung.**

Nếu performance/security evidence ở Plan 10 yêu cầu schema/index mới:

```text
create reviewed V014
do not edit historical migration
next available becomes V015
```

---

# 12. Quy trình bắt đầu một implementation session

Mỗi Developer hoặc AI Agent phải làm đúng sequence:

```text
1. Đọc CoVie-START-HERE-v1.0.md

2. Nếu là lần đầu vào project:
   đọc 6 foundation/master documents:
   - Product Plan
   - User Flow
   - IA/UI Flow
   - Database Design
   - API Design
   - Master Technical Plan

3. Xác định CURRENT PLAN.

4. Kiểm tra prerequisite của Current Plan.

5. Đọc toàn bộ Global Constraints của Current Plan.

6. Đọc Task hiện tại hoàn chỉnh.

7. Kiểm tra exact files/interfaces từ Plan trước.

8. Implement đúng Task.

9. Chạy failing test trước nếu Task yêu cầu TDD.

10. Implement minimum code.

11. Chạy focused tests.

12. Chạy relevant regression suite.

13. Chạy verification gate của Task.

14. Review diff.

15. Commit theo commit boundary của Plan.

16. Chỉ sau đó chuyển Task kế tiếp.
```

---

# 13. Quy tắc thực thi bên trong một Detailed Plan

Detailed Plan là **sequential task specification**.

Nếu Plan có:

```text
Task 1
Task 2
Task 3
...
Task N
```

thì mặc định:

```text
Task 1
  ↓ green
Task 2
  ↓ green
Task 3
  ↓ green
...
```

Không:

```text
Task 1 half done
Task 7 start
Task 4 skip
Task 12 temporary workaround
```

trừ khi Plan ghi rõ hai Task độc lập và có thể chạy song song.

---

# 14. Khi nào được chuyển sang Plan tiếp theo?

Ví dụ:

```text
Plan 01
```

chỉ chuyển sang Plan 02 khi:

```text
all Plan 01 tasks complete
+
Plan 01 final acceptance green
+
repo clean/reviewed
+
no unresolved Plan 01 blocker
```

Sau đó:

```text
Plan 02
```

và lặp lại cùng nguyên tắc.

Quy tắc tổng quát:

```text
Plan N acceptance = GREEN
        |
        v
Plan N+1 may begin
```

Nếu acceptance còn đỏ:

```text
Plan N+1 MUST NOT START
```

---

# 15. Developer / AI không được tự ý làm những việc sau

```text
Không thêm feature ngoài V1 scope.

Không đổi REST semantics chỉ vì dễ code.

Không thêm direct PATCH Order status.

Không chuyển business source of truth sang Redis.

Không dùng H2 thay PostgreSQL/PostGIS cho correctness tests.

Không bỏ Flyway để Hibernate tự tạo schema.

Không sửa migration đã apply để "cho nhanh".

Không hand-write duplicate frontend DTO thay generated OpenAPI types.

Không tạo frontend Order state machine.

Không tự tính authoritative shipping/stock/checkout totals ở frontend.

Không bỏ Idempotency-Key ở mutation quan trọng.

Không bỏ If-Match/version ở mutable versioned resources.

Không thêm microservices/Kafka/WebSocket/search cluster nếu Plan không yêu cầu.

Không thêm production secret vào source.

Không tạo test-auth bypass hoạt động trong production.

Không bỏ test/concurrency gate chỉ vì happy path pass.

Không chuyển sang Plan sau khi Plan hiện tại chưa acceptance-green.
```

---

# 16. Developer / AI được phép làm gì?

Developer/AI được phép:

```text
refactor nhỏ để implement Task sạch hơn
thêm private helper
thêm focused test
sửa bug phát hiện trong phạm vi Plan
cải thiện naming nội bộ nếu không phá contract
bổ sung validation phù hợp invariant đã duyệt
update generated OpenAPI client sau backend contract change được Plan cho phép
```

Nhưng không được làm thay đổi:

```text
business behavior
external contract
persistent semantics
security semantics
scope
```

nếu chưa có explicit document approval/correction.

---

# 17. Exact TDD / implementation loop

Khi Detailed Plan yêu cầu TDD:

```text
1. Write failing test
2. Run focused test
3. Observe expected failure
4. Implement smallest correct behavior
5. Run focused test
6. Run relevant regression tests
7. Inspect implementation
8. Commit
```

Không viết test “sau cho đủ checklist” nếu Plan yêu cầu RED trước.

---

# 18. Verification rule

Không được nói:

```text
"Task complete"
"Plan complete"
"ready"
"fixed"
"all good"
```

nếu chưa có **fresh command evidence**.

Completion claim phải dựa trên:

```text
command run
+
actual exit/result inspected
+
requirements checklist inspected
```

Không dùng kết quả test cũ trước khi code thay đổi.

---

# 19. Git / branch / worktree rule

Khi bắt đầu implementation:

```text
main
  |
  +--> isolated worktree / feature branch
```

Mỗi Plan hoặc logical batch phải có isolation phù hợp.

Recommended:

```text
Plan 01
  branch/worktree: plan-01-foundation

Plan 02
  branch/worktree: plan-02-auth-user

...
```

Hoặc một long-running implementation branch nếu team đã có workflow riêng, nhưng vẫn phải giữ Plan/task commits reviewable.

Không implement trực tiếp trên một working tree có thay đổi không liên quan.

---

# 20. Commit rule

Detailed Plan đã định nghĩa commit boundaries.

Ưu tiên commit nhỏ theo task:

```text
feat(...)
test(...)
fix(...)
build(...)
ops(...)
docs(...)
```

Không gom toàn bộ Plan thành một commit khổng lồ nếu Plan đã chia task độc lập.

Không tạo empty commit chỉ để đánh dấu hoàn thành.

---

# 21. Generated code rule

Generated OpenAPI TypeScript:

```text
packages/api-client/src/generated/
```

là generated artifact.

Rules:

```text
Backend OpenAPI = source
         ↓
generate
         ↓
TypeScript client
```

Không chỉnh file generated bằng tay.

Nếu generated diff thay đổi:

```text
inspect API change
verify intended
commit source + generated artifact together
```

---

# 22. Backend / Frontend authority boundary

## Backend owns

```text
authentication
authorization
merchant membership
location permission
price
inventory
shipping
checkout
Order transitions
Need matching
Review eligibility
Reputation
Moderation
System Settings
```

## Frontend owns

```text
presentation
navigation
form interaction
loading/error states
local UI state
server-state caching/refetch
formatting
```

Frontend không được “reimplement backend rules”.

---

# 23. Core invariants không được phá

Developer/AI phải bảo toàn tối thiểu:

## Auth

```text
Google-first signup
mandatory CoVie password after first Google identity
Google subject = canonical Google identity key
refresh token rotates
reuse revokes session family
merchant roles are DB-resolved
```

## Catalog

```text
every CatalogItem has Variant
LocationVariant is concrete purchasable offer
authoritative price belongs to LocationVariant
```

## Cart

```text
one MerchantLocation per cart
Product/Service not mixed
Service quantity = 1
```

## Checkout

```text
Preview does not reserve
Preview does not lock price
Commit revalidates everything
checkout token server-side/opaque/single-use
tracked inventory reserved at Order creation
```

## Inventory

```text
stock_on_hand >= reserved_quantity >= 0
available = stock_on_hand - reserved_quantity
last-stock concurrency cannot oversell
movements are append-only
```

## Order

```text
status never raw client PATCH
commands/system jobs drive transitions
COMPLETED irreversible
events append-only
business transaction + outbox atomic
```

## Need

```text
one active response per MerchantLocation
response references existing LocationVariant
Need Order source is traceable
Need becomes FULFILLED only after source Order CONFIRMED
```

## Review/Reputation

```text
Review only after COMPLETED
one review per OrderItem
Review and Reputation are different systems
ambiguous outcomes excluded from automatic reputation
```

## Admin

```text
normal Admin cannot force Order state
moderation != transaction arbitration
settings are allowlisted
```

---

# 24. Recommended local document layout before coding

Nếu các file hiện đang nằm trong Downloads, hãy copy chúng vào repository trước khi triển khai.

Recommended:

```text
covie/
└── docs/
    ├── START-HERE.md
    │
    ├── product/
    │   ├── CoVie-product-plan-v0.3.md
    │   ├── CoVie-user-flow-v1.0.md
    │   └── CoVie-information-architecture-ui-flow-v1.0.md
    │
    ├── architecture/
    │   ├── CoVie-database-design-v1.0.md
    │   ├── CoVie-api-design-v1.0.md
    │   └── CoVie-technical-plan-v1.0.md
    │
    └── superpowers/
        └── plans/
            ├── CoVie-technical-plan-01-foundation-repository-v1.0.md
            ├── CoVie-technical-plan-02-identity-auth-user-v1.0.md
            ├── CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md
            ├── CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md
            ├── CoVie-technical-plan-05-orders-fulfillment-v1.0.md
            ├── CoVie-technical-plan-06-need-requests-matching-v1.0.md
            ├── CoVie-technical-plan-07-reviews-reputation-outbox-notifications-sse-v1.0.md
            ├── CoVie-technical-plan-08-moderation-system-settings-admin-v1.0.md
            ├── CoVie-technical-plan-09-web-completion-v1.0.md
            └── CoVie-technical-plan-10-production-hardening-pilot-launch-v1.0.md
```

Tên file có thể giữ nguyên để dễ đối chiếu với bản archive/download.

Sau khi copy:

```text
Downloads copy = archive/reference
Repository docs = implementation working source
```

Không nên để AI coding agent phải đọc tài liệu từ `C:\Users\...\Downloads` trong suốt project.

---

# 25. Current Plan detection

Khi implementation mới bắt đầu:

```text
CURRENT PLAN = 01
```

Sau mỗi Plan:

```text
if Plan 01 acceptance GREEN:
    CURRENT PLAN = 02

if Plan 02 acceptance GREEN:
    CURRENT PLAN = 03

...

if Plan 09 acceptance GREEN:
    CURRENT PLAN = 10

if Plan 10 acceptance GREEN:
    V1 technical implementation baseline complete
```

Không suy ra Current Plan chỉ từ “file mới nhất được mở”.

Current Plan được xác định bởi **acceptance state của Plan trước**.

---

# 26. Handoff format giữa các Plan

Khi kết thúc một Plan, ghi handoff tối thiểu:

```text
PLAN:
COMPLETION COMMIT:
ACCEPTANCE COMMANDS:
RESULT:
MIGRATION HIGHEST:
OPEN BLOCKERS:
GENERATED CONTRACT STATUS:
NEXT PLAN:
```

Ví dụ:

```text
PLAN: 01 Foundation
COMPLETION COMMIT: abc123
ACCEPTANCE COMMANDS: ./mvnw clean verify, pnpm test/typecheck/lint/build, contract drift
RESULT: GREEN
MIGRATION HIGHEST: V001
OPEN BLOCKERS: none
GENERATED CONTRACT STATUS: clean
NEXT PLAN: 02 Identity/Auth/User
```

Handoff giúp người/AI session sau không phải suy đoán trạng thái repository.

---

# 27. Nếu implementation phát hiện Plan sai

Plan không phải “bất khả sửa”.

Nếu implementation evidence chứng minh Plan có lỗi:

```text
1. stop affected task
2. reproduce issue
3. identify exact plan statement
4. identify source contract affected
5. propose smallest correction
6. update plan/document
7. record why
8. rerun from failing test
```

Ví dụ hợp lệ:

```text
Flyway version ordering would break later migrations
-> correct migration sequencing explicitly
```

Ví dụ không hợp lệ:

```text
"Code này hơi khó"
-> silently change business rule
```

---

# 28. Rule cho AI Agent có context window giới hạn

AI không nhất thiết phải nạp toàn bộ 16 file vào context mỗi Task.

Recommended strategy:

## Khi bắt đầu project

Đọc:

```text
START HERE
Product Plan
User Flow
IA/UI Flow
Database Design
API Design
Master Technical Plan
Current Detailed Plan
```

## Khi đang implement Task

Giữ context:

```text
START HERE rules
Current Detailed Plan
exact relevant sections từ DB/API/Product docs
handoff từ Plan trước
actual repository code/tests
```

## Không cần

Nạp toàn văn Plan 07 khi đang làm Task 2 của Plan 01 nếu không liên quan.

Search/retrieve section khi cần.

---

# 29. Prompt chuẩn để bắt đầu implementation bằng AI

Có thể dùng prompt sau với Codex/Claude Code/AI coding agent:

```text
You are implementing CoVie V1.

Read docs/START-HERE.md first and obey it as the project document-routing
and execution policy.

Then read the six foundation documents:
1. Product Plan
2. User Flow
3. IA/UI Flow
4. Database Design
5. API Design
6. Master Technical Plan

Determine the current implementation plan from repository state and the
acceptance status of the previous plan.

Do not start a plan unless all prerequisites and the previous plan's
acceptance gate are green.

For the current plan:
- read its Global Constraints;
- execute tasks in order;
- follow the exact file paths/interfaces;
- use the TDD RED -> GREEN cycle where specified;
- run focused and regression verification;
- do not skip acceptance gates;
- do not invent business/API/schema behavior;
- if documents conflict, apply START-HERE precedence rules;
- if conflict remains unresolved, stop the affected task and report it.

Do not begin the next plan until the current plan's final acceptance gate
has fresh green evidence.
```

---

# 30. Prompt chuẩn để tiếp tục project ở session mới

```text
Read docs/START-HERE.md.

Inspect the repository and identify:
- current branch/worktree;
- last completed CoVie plan;
- completion/acceptance evidence;
- highest Flyway migration;
- generated API client drift;
- uncommitted changes.

Then read only the current Detailed Technical Plan and relevant source
documents.

Continue from the first incomplete task.

Do not repeat completed tasks unless verification shows they are invalid.
Do not skip ahead to the next plan.
```

---

# 31. Prompt chuẩn để review code

```text
Review this implementation against:

1. docs/START-HERE.md
2. the current Detailed Technical Plan
3. relevant Product/User Flow/IA requirements
4. Database Design
5. API Design
6. Master Technical Plan
7. tests and acceptance criteria

Classify findings as:
- REQUIREMENT VIOLATION
- API CONTRACT VIOLATION
- DATA INVARIANT VIOLATION
- SECURITY ISSUE
- CONCURRENCY/TRANSACTION ISSUE
- PLAN NON-COMPLIANCE
- CODE QUALITY

Do not recommend architecture outside V1 scope unless necessary to fix a
demonstrated correctness/security problem.
```

---

# 32. Prompt chuẩn khi có conflict

```text
A possible documentation conflict was found.

Do not implement through the conflict.

Identify:
1. exact conflicting statements;
2. which document owns the affected domain;
3. whether a newer Detailed Plan explicitly marks a correction;
4. the smallest resolution that preserves approved Product/API/Data behavior.

Return:
- conflict summary;
- affected task/files;
- recommended canonical rule;
- whether an authoritative document must be updated.

Do not modify code until the conflict is resolved.
```

---

# 33. Quick-start checklist cho Developer

Trước dòng code đầu tiên:

```text
[ ] Tôi đã đọc START HERE.
[ ] Tôi đã đọc Product Plan.
[ ] Tôi đã đọc User Flow.
[ ] Tôi đã đọc IA/UI Flow.
[ ] Tôi đã đọc Database Design.
[ ] Tôi đã đọc API Design.
[ ] Tôi đã đọc Master Technical Plan.
[ ] Tôi biết Current Plan là Plan nào.
[ ] Previous Plan acceptance đã GREEN hoặc Current Plan = 01.
[ ] Tôi đã đọc toàn bộ Current Detailed Plan.
[ ] Tôi hiểu migration handoff.
[ ] Tôi biết task hiện tại.
[ ] Working tree/worktree sạch và đúng branch.
```

---

# 34. Quick-start checklist cho AI Agent

```text
[ ] START HERE loaded.
[ ] Current Plan identified from repository evidence.
[ ] Previous Plan acceptance checked.
[ ] Relevant source-of-truth sections retrieved.
[ ] No unresolved document conflict.
[ ] Exact task files identified.
[ ] Tests to write/run identified.
[ ] Commit boundary identified.
[ ] Next Plan will not be started prematurely.
```

---

# 35. Definition of “Done” cho toàn bộ Technical Plan 01–10

Không gọi CoVie V1 technical implementation complete cho đến khi:

```text
Plan 01 acceptance GREEN
Plan 02 acceptance GREEN
Plan 03 acceptance GREEN
Plan 04 acceptance GREEN
Plan 05 acceptance GREEN
Plan 06 acceptance GREEN
Plan 07 acceptance GREEN
Plan 08 acceptance GREEN
Plan 09 acceptance GREEN
Plan 10 acceptance GREEN
```

Plan 10 final staging rehearsal phải bao gồm:

```text
clean DB migration
production-shaped deployment
P0 smoke
backup
restore into separate DB
invariant verification
rollback drill
redeploy
monitoring/alert validation
release blocker checklist
```

---

# 36. V1 scope freeze

Trong quá trình implementation 01–10, các feature sau không được tự thêm:

```text
microservices
Kafka/RabbitMQ
WebSocket/internal chat
service booking calendar
payment gateway verification
escrow/refund/wallet
native mobile app
multi-region active-active
search cluster
AI recommendation
advanced BI/revenue analytics
transaction dispute arbitration
Admin force Order status
```

Nếu có nhu cầu mới:

```text
create post-V1 proposal
do not inject it into current Plan
```

---

# 37. Final instruction

Nếu bạn chỉ nhớ một quy tắc, hãy nhớ quy tắc này:

```text
READ START HERE
       ↓
READ PRODUCT / UX / DATA / API / MASTER
       ↓
IDENTIFY CURRENT PLAN
       ↓
EXECUTE CURRENT PLAN TASKS IN ORDER
       ↓
VERIFY
       ↓
ACCEPTANCE GREEN
       ↓
ONLY THEN MOVE TO NEXT PLAN
```

**Không nhảy Plan.**

**Không bỏ qua source of truth.**

**Không tự sửa business contract để làm implementation dễ hơn.**

**Không tuyên bố hoàn thành nếu chưa có fresh verification evidence.**

---

# 38. Current implementation starting point

Tại thời điểm bộ tài liệu V1.0 này được khóa:

```text
Planning documents: COMPLETE
Implementation: NOT STARTED
Current Plan: PLAN 01 — FOUNDATION & REPOSITORY
Next action:
  implement CoVie-technical-plan-01-foundation-repository-v1.0.md
```

Sau khi Plan 01 final acceptance GREEN:

```text
Current Plan becomes:
PLAN 02 — IDENTITY, AUTH & USER
```

và tiếp tục tuần tự cho tới Plan 10.

---

**End of CoVie START HERE V1.0**
