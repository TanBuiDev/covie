# CoVie API Design V1.0

**Project:** CoVie  
**Document:** API Design / API Contract  
**Version:** 1.0  
**Status:** Approved design baseline  
**Backend:** Java / Spring Boot  
**Frontend:** Next.js  
**Database:** PostgreSQL + PostGIS  
**API style:** REST JSON + explicit business commands  
**Base path:** `/api/v1`

---

## 1. Purpose and scope

This document defines the V1 HTTP API contract and application-boundary design for CoVie. The API is use-case/domain oriented rather than a direct CRUD mirror of database tables.

Spring Boot is the authoritative source for:

- authentication and authorization;
- buyer/merchant/location permission checks;
- catalog and offer validation;
- cart rules;
- checkout revalidation;
- tracked-inventory concurrency;
- Order state transitions;
- Need → Order attribution;
- review eligibility and reputation rules;
- moderation policy;
- idempotency, optimistic concurrency and transaction boundaries;
- Outbox/notification/SSE behavior.

V1 remains a **modular monolith**. No microservice split is required.

---

# 2. System architecture

```text
Browser / Next.js
        |
        | https://covie.vn/api/v1/*
        v
Reverse proxy / ingress
        |
        v
Spring Boot
├── REST Controllers
├── Application Services
├── Domain Services / Policies
├── Repositories
├── Scheduled Jobs
├── Outbox Worker
└── SSE
        |
        +-----------------------+
        |                       |
        v                       v
PostgreSQL + PostGIS      Object Storage
```

Recommended public topology:

```text
https://covie.vn/          -> Next.js
https://covie.vn/api/v1/*  -> Spring Boot
```

Next.js is not a mandatory BFF. Spring Boot is the business source of truth.

Suggested backend modules:

```text
auth
users
merchants
catalog
inventory
checkout
orders
needs
reviews
reputation
media
notifications
moderation
admin
settings
```

---

# 3. API principles

## 3.1 REST reads + explicit business commands

Reads use normal resources:

```http
GET /api/v1/orders/{orderId}
```

Domain transitions use commands:

```http
POST /api/v1/orders/{orderId}/confirm
POST /api/v1/orders/{orderId}/reject
POST /api/v1/orders/{orderId}/mark-delivered
```

Do **not** expose Order lifecycle as arbitrary client-writable state:

```http
PATCH /api/v1/orders/{orderId}

{
  "status": "COMPLETED"
}
```

## 3.2 API DTOs, never JPA entities

Use use-case DTOs such as:

```text
CatalogItemSummaryResponse
CatalogItemDetailResponse
CartResponse
CheckoutPreviewResponse
OrderSummaryResponse
OrderDetailResponse
OrderCommandResult
NeedRequestDetailResponse
MerchantInventoryItemResponse
```

JPA entities must never be serialized directly.

## 3.3 Canonical identifiers

Use UUIDv7 as canonical IDs.

Examples:

```text
userId
merchantId
locationId
catalogItemId
variantId
locationVariantId
cartId
orderId
needRequestId
mediaId
```

Slug/order code are alternate public lookups only and are never canonical foreign-key/command identities.

---

# 4. API namespaces

```text
/api/v1
│
├── /auth/*
├── /catalog/*
├── /categories/*
├── /location-areas/*
├── /media/*
├── /reports
│
├── /me/*
│   ├── addresses
│   ├── carts
│   ├── merchants
│   ├── orders
│   ├── need-requests
│   ├── reviews
│   ├── notifications
│   ├── events
│   └── security
│
├── /merchants/{merchantId}/*
│   ├── members
│   ├── locations
│   ├── catalog-items
│   ├── inventory
│   ├── orders
│   └── need-requests
│
├── /orders/{orderId}/*
├── /need-requests/{needRequestId}/*
├── /checkouts/*
│
└── /admin/*
```

Rules:

- `/me/*`: current-user owned/personal resources.
- `/merchants/{merchantId}/*`: Seller Center management.
- `/orders/{orderId}/*`: canonical shared Order read/command namespace.
- `/catalog/*`: buyer/public discovery.
- `/admin/*`: internal operational administration.

---

# 5. Success, errors and HTTP semantics

## 5.1 Success responses

No universal `{success,data}` wrapper.

Single resource:

```json
{
  "id": "0198...",
  "name": "..."
}
```

Cursor collection:

```json
{
  "items": [],
  "page": {
    "nextCursor": null,
    "hasMore": false
  }
}
```

## 5.2 Standard error envelope

```json
{
  "error": {
    "code": "ORDER_STATE_CONFLICT",
    "message": "The order can no longer perform this action.",
    "details": {},
    "fields": {},
    "requestId": "req_..."
  }
}
```

Frontend logic uses `error.code`, not localized `message`.

HTTP mapping:

| HTTP | Meaning |
|---|---|
| 400 | malformed JSON/protocol-level input |
| 401 | authentication missing/invalid |
| 403 | actor can see resource but cannot perform action |
| 404 | absent resource or actor may not know it exists |
| 409 | state/version/inventory/idempotency conflict |
| 422 | structurally valid request fails validation/business rules |
| 429 | rate limited |
| 500 | unexpected server failure |

Important stable error codes include:

```text
INVALID_CREDENTIALS
RESOURCE_VERSION_CONFLICT
ORDER_STATE_CONFLICT
ORDER_ACTION_NOT_ALLOWED
CHECKOUT_CHANGED
CHECKOUT_SESSION_EXPIRED
CHECKOUT_SESSION_ALREADY_CONSUMED
INSUFFICIENT_INVENTORY
INVENTORY_BELOW_RESERVED_QUANTITY
NEED_RESPONSE_ITEM_LIMIT_EXCEEDED
REVIEW_CREATION_DISABLED
RATE_LIMIT_EXCEEDED
IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
```

## 5.3 Request correlation

Every request has `X-Request-Id`. The value is correlated through HTTP logs, audit data, outbox diagnostics and error responses.

---

# 6. Authentication and security

## 6.1 V1 account creation

V1 has **no direct Email/Password registration**.

First account creation:

```text
Google OAuth
    ↓
Google identity validated
    ↓
New CoVie identity?
    ├── No  -> normal Google login
    └── Yes -> short-lived onboarding context
                 ↓
             Require CoVie password
                 ↓
        User + GOOGLE identity
        + EMAIL/password identity
                 ↓
             Active session
```

The Google-verified email becomes the Email/Password login email.

Future login methods:

```text
Google OAuth
or
Email + Password
```

Both must resolve to the same `user_id`.

Recommended endpoints:

```http
GET  /api/v1/auth/google/start
GET  /api/v1/auth/google/callback
POST /api/v1/auth/onboarding/set-password
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/me
```

## 6.2 Google is V1 recovery root

V1 intentionally does not implement:

```text
email verification links
forgot-password email
reset-password email token
email OTP
```

Forgot-password flow:

```text
Google OAuth recent re-authentication
    ↓
verify same CoVie user
    ↓
set new CoVie password
    ↓
revoke old sessions
    ↓
create fresh current session
```

If the user loses both Google access and the CoVie password, V1 uses manual support recovery.

## 6.3 Access token

Use short-lived signed access tokens with minimal claims:

```json
{
  "sub": "0198...",
  "roles": ["USER"],
  "sessionId": "0198...",
  "iat": 1786130000,
  "exp": 1786130900
}
```

Do **not** embed merchant OWNER/MANAGER/STAFF membership or location scopes in JWT.

## 6.4 Multi-device sessions

A user may have multiple server-side sessions.

```http
GET    /api/v1/me/security/sessions
DELETE /api/v1/me/security/sessions/{sessionId}
POST   /api/v1/me/security/sessions/revoke-others
```

## 6.5 Rotating refresh tokens + reuse detection

Each refresh token is one-time/rotating.

```text
Refresh #1
   ↓ validate
mark #1 used/replaced
   ↓
issue Refresh #2
```

Reuse of an old rotated token:

```text
REUSE DETECTED
→ revoke session/token family
→ require login again
```

Only token hashes are stored. Raw refresh tokens are never persisted.

Concurrent refresh calls must not create two valid successor tokens from the same predecessor.

## 6.6 Browser cookie and CSRF policy

Browser credentials use:

```text
HttpOnly
Secure
SameSite
```

Refresh token is not readable by JavaScript.

Browser mutation requests (`POST`, `PUT`, `PATCH`, `DELETE`) require CSRF protection, e.g.:

```http
X-CSRF-Token: ...
```

Bearer-token mobile/API clients do not use browser CSRF flow.

## 6.7 Password handling

Passwords are accepted only over TLS, immediately hashed with a modern password hashing function, and never logged, reversibly encrypted or stored in plaintext.

---

# 7. Authorization

Authorization layers:

```text
Authentication
    ↓
Global role
    ↓
Domain/resource permission
```

Global role example:

```text
USER
ADMIN
```

Merchant membership roles:

```text
OWNER
MANAGER
STAFF
```

STAFF may be restricted to selected MerchantLocations.

Every Seller Center command checks:

```text
current user
→ MerchantMembership
→ role permission
→ location scope if applicable
→ resource belongs to merchant/location
→ domain rule
```

### 404 vs 403

Private resource not visible to actor:

```http
GET /api/v1/orders/{someoneElsesOrder}
```

returns `404`.

Visible Order but forbidden action returns `403 ORDER_ACTION_NOT_ALLOWED`.

---

# 8. Public Catalog and discovery

## 8.1 Unified catalog endpoint

Use one discovery endpoint:

```http
GET /api/v1/catalog/items
```

Do not split Product/Service/Resale into separate discovery APIs.

Typed filters:

```text
type=PRODUCT|SERVICE
listingKind=STANDARD|RESALE
```

Potential query parameters:

```text
q
type
listingKind
categoryId
lat
lng
addressId
radiusMeters
canServe
pickup
shipping
openNow
minPrice
maxPrice
minRating
sort
limit
cursor
```

Whitelisted sort values:

```text
RELEVANCE
DISTANCE
PRICE_ASC
PRICE_DESC
RATING
NEWEST
```

No generic database/JPA query DSL is exposed.

## 8.2 Explicit LocationContext

Guest requests use `lat + lng`.

Authenticated requests may use owned `addressId`.

Backend normalizes location to:

```text
LocationContext
├── source = COORDINATES | SAVED_ADDRESS
├── point
├── resolved area
├── addressId?
└── timezone/context metadata
```

Do not rely on hidden session `currentLocation`.

## 8.3 Every buyer offer resolves to a MerchantLocation

Buyer-facing search/detail must identify the concrete `MerchantLocation` being offered.

Add-to-cart cannot silently switch location.

## 8.4 Catalog detail

```http
GET /api/v1/catalog/items/{itemId}
```

Optional context may use `locationId`, `addressId` or `lat/lng`.

Purchasable response exposes:

```json
{
  "variants": [
    {
      "id": "0198...",
      "offer": {
        "locationVariantId": "0198...",
        "available": true,
        "price": {
          "amount": 18000,
          "currency": "VND"
        }
      }
    }
  ]
}
```

Buyer purchase identity is `locationVariantId`, not bare `variantId`.

---

# 9. Money, time and localization

## 9.1 Money

Authoritative money uses integer minor units + ISO currency code:

```json
{
  "amount": 45000,
  "currency": "VND"
}
```

No float. No formatted currency string as source data.

Negotiated shipping:

```json
{
  "shippingFee": null,
  "total": null,
  "knownTotal": {
    "amount": 150000,
    "currency": "VND"
  }
}
```

## 9.2 Date/time

Absolute timestamps: ISO-8601 UTC.

```text
2026-08-08T05:29:00Z
```

Calendar date:

```text
YYYY-MM-DD
```

Business local time:

```text
HH:mm:ss
```

interpreted with MerchantLocation IANA timezone, e.g. `Asia/Ho_Chi_Minh`.

## 9.3 Localization

Backend returns stable codes/raw values. Frontend owns translated labels and button text.

---

# 10. Cart API

One ACTIVE cart per:

```text
(user, merchantLocation, cartType)
```

where cart type is PRODUCT or SERVICE.

A Cart never mixes Product and Service and never spans multiple MerchantLocations.

## 10.1 Add to cart

```http
POST /api/v1/me/carts/items
```

```json
{
  "locationVariantId": "0198...",
  "quantity": 2
}
```

Backend resolves the proper cart.

Rules:

- Product quantity >= 1.
- Service quantity = 1.
- Service scale/package is represented by Variant.

## 10.2 Read and mutate

```http
GET    /api/v1/me/carts
GET    /api/v1/me/carts/{cartId}
PATCH  /api/v1/me/carts/{cartId}/items/{cartItemId}
DELETE /api/v1/me/carts/{cartId}/items/{cartItemId}
```

Cart subtotal is informational/current price only, never a locked checkout price.

Cart writes use optimistic versioning with `If-Match` where appropriate.

---

# 11. Unified Checkout Engine

CoVie uses exactly one engine:

```http
POST /api/v1/checkouts/preview
POST /api/v1/checkouts/commit
```

Supported sources:

```text
CART
DIRECT
NEED_REQUEST
```

## 11.1 CART

```json
{
  "source": {
    "type": "CART",
    "cartId": "0198..."
  },
  "fulfillmentMethod": "SELLER_DELIVERY",
  "addressId": "0198...",
  "paymentMethod": "COD"
}
```

## 11.2 DIRECT / Buy now

```json
{
  "source": {
    "type": "DIRECT"
  },
  "items": [
    {
      "locationVariantId": "0198...",
      "quantity": 1
    }
  ],
  "fulfillmentMethod": "PICKUP",
  "paymentMethod": "COD"
}
```

## 11.3 NEED_REQUEST

```json
{
  "source": {
    "type": "NEED_REQUEST",
    "needRequestId": "0198...",
    "needResponseItemId": "0198..."
  },
  "items": [
    {
      "locationVariantId": "0198...",
      "quantity": 2
    }
  ],
  "fulfillmentMethod": "SELLER_DELIVERY",
  "addressId": "0198...",
  "paymentMethod": "COD"
}
```

NeedResponse checkout is direct and does not create a temporary cart.

## 11.4 Preview pipeline

All sources normalize to one internal `CheckoutCandidate` and run:

```text
resolve source
→ validate ownership/context
→ enforce one MerchantLocation
→ enforce one PRODUCT/SERVICE type
→ validate availability
→ validate inventory
→ resolve current prices
→ validate minimum order
→ validate coverage
→ calculate shipping
→ create preview
```

Preview **does not reserve inventory** and **does not lock price**.

## 11.5 Server-side checkout session

Preview creates a short-lived server-side `checkout_session` and returns an opaque token.

Conceptual fields:

```text
id
token_hash
user_id
source_type
source_context
cart_version
request_snapshot
preview_snapshot
expires_at
consumed_at
created_at
```

Properties:

```text
short-lived
bound to user
bound to source
single-use
not a reservation
not a price lock
```

## 11.6 Commit

```http
POST /api/v1/checkouts/commit
Idempotency-Key: ...
```

```json
{
  "checkoutToken": "chk_..."
}
```

Backend validates session and revalidates all current business data.

If material data changed:

```http
409 Conflict
```

```text
CHECKOUT_CHANGED
```

Client must Preview again.

## 11.7 Commit transaction

Successful Commit atomically creates:

```text
Order
OrderItems
Order snapshots
fulfillment
source tracing
tracked-inventory reservations
inventory movements
initial OrderEvent
Outbox events
```

For CART source, checked-out cart items are cleared according to the application transaction policy.

Retry with the same Idempotency-Key must not create a second Order.

---

# 12. Payment V1

Supported:

```text
COD
BANK_TRANSFER_NEGOTIATED
```

Not in V1:

```text
automatic bank verification
stored seller bank QR/payment account system
payment gateway settlement
refund engine
escrow
```

---

# 13. Canonical Order API

## 13.1 Order detail

```http
GET /api/v1/orders/{orderId}
```

Backend resolves actor: buyer, permitted merchant member, admin, or unauthorized.

Unauthorized existence returns 404.

Conceptual `OrderDetailResponse`:

```text
order
source
buyer/merchant
items[]
pricing
fulfillment
payment
contacts
timeline[]
issue?
cancellation?
reputationContext?
allowedActions[]
```

Sensitive fields are authorization-aware.

## 13.2 Lists

```http
GET /api/v1/me/orders
GET /api/v1/merchants/{merchantId}/orders
GET /api/v1/admin/orders
```

## 13.3 `allowedActions`

Backend calculates actor-specific `allowedActions` from:

```text
actor
merchant membership
location scope
Order status
Order type
fulfillment method
grace period
cancellation state
issue state
```

It helps UI but is not a security boundary.

---

# 14. Order states and commands

Important V1 states:

```text
PENDING_CONFIRMATION
CONFIRMED
OUT_FOR_DELIVERY
READY_FOR_PICKUP
AWAITING_BUYER_CONFIRMATION
CANCELLATION_REQUESTED
DELIVERY_ISSUE_REPORTED
SERVICE_ISSUE_REPORTED
BUYER_NO_SHOW
NO_SHOW_CONTESTED
COMPLETED
EXPIRED
REJECTED_BY_SELLER
CANCELLED_BEFORE_CONFIRMATION
CANCELLED_BY_BUYER_AFTER_CONFIRMATION
CANCELLED_BY_SELLER_AFTER_CONFIRMATION
CLOSED_WITH_ISSUE
```

`COMPLETED` is terminal and irreversible.

Canonical commands:

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

Order status must never be directly PATCHed by client.

---

# 15. Order lifecycle

## 15.1 Creation

Checkout Commit creates:

```text
PENDING_CONFIRMATION
```

Tracked Product inventory is reserved immediately.

## 15.2 Seller confirm

```text
PENDING_CONFIRMATION
→ CONFIRMED
```

Side effects:

```text
OrderEvent
Outbox event
buyer notification
NeedRequest -> FULFILLED if source is Need
```

## 15.3 Seller reject before confirmation

```text
PENDING_CONFIRMATION
→ REJECTED_BY_SELLER
```

Reason required. Inventory reservation released.

Pre-confirm rejection is not automatically seller-reputation negative.

A source Need remains ACTIVE if otherwise valid.

## 15.4 Expiration

System job:

```text
PENDING_CONFIRMATION
→ EXPIRED
```

Reservation released. No public expire endpoint.

## 15.5 Buyer cancel before confirmation

```text
PENDING_CONFIRMATION
→ CANCELLED_BEFORE_CONFIRMATION
```

Buyer may cancel freely; tracked reservation released.

## 15.6 Buyer request cancel after confirmation

```text
CONFIRMED
→ CANCELLATION_REQUESTED
```

Seller accepts:

```text
→ CANCELLED_BY_BUYER_AFTER_CONFIRMATION
```

Seller rejects:

```text
→ CONFIRMED
```

## 15.7 Seller cancel after confirmation

```text
CONFIRMED
→ CANCELLED_BY_SELLER_AFTER_CONFIRMATION
```

Reason required and may negatively affect merchant reputation.

---

# 16. Product fulfillment

## 16.1 Seller delivery

Typical flow:

```text
CONFIRMED
→ OUT_FOR_DELIVERY
→ AWAITING_BUYER_CONFIRMATION
→ COMPLETED
```

Buyer may confirm receipt directly from:

```text
CONFIRMED
OUT_FOR_DELIVERY
AWAITING_BUYER_CONFIRMATION
```

This handles sellers forgetting intermediate progress actions.

## 16.2 Pickup

Typical:

```text
CONFIRMED
→ READY_FOR_PICKUP
→ COMPLETED
```

`READY_FOR_PICKUP` is useful but not required before buyer completion confirmation.

## 16.3 Buyer no-show

After configured grace:

```text
READY_FOR_PICKUP
→ BUYER_NO_SHOW
```

Buyer may contest:

```text
BUYER_NO_SHOW
→ NO_SHOW_CONTESTED
```

V1 has no arbitration engine. `NO_SHOW_CONTESTED` is excluded from automatic reputation.

---

# 17. Service fulfillment

Service V1 has no CoVie booking-slot engine.

Typical:

```text
PENDING_CONFIRMATION
→ CONFIRMED
→ AWAITING_BUYER_CONFIRMATION
→ COMPLETED
```

Provider marks performed with:

```http
POST /api/v1/orders/{orderId}/mark-service-performed
```

Buyer may confirm completed directly from `CONFIRMED` if provider forgot progress update.

Service OrderItem quantity is always 1; scale/package is encoded in Variant.

---

# 18. Issues and auto-completion

Delivery issue only after seller marks delivered:

```text
AWAITING_BUYER_CONFIRMATION
→ DELIVERY_ISSUE_REPORTED
```

Service issue only after provider marks performed:

```text
AWAITING_BUYER_CONFIRMATION
→ SERVICE_ISSUE_REPORTED
```

Issue prevents auto-completion.

After configured timeout:

```text
*_ISSUE_REPORTED
→ CLOSED_WITH_ISSUE
```

`CLOSED_WITH_ISSUE` is neutral/excluded from automatic reputation.

If Order remains `AWAITING_BUYER_CONFIRMATION` with no issue beyond grace:

```text
→ COMPLETED
```

by system job.

---

# 19. Order events

Important transitions create append-only `order_events`, e.g.:

```text
ORDER_CREATED
ORDER_CONFIRMED
ORDER_REJECTED
OUT_FOR_DELIVERY
DELIVERED
BUYER_CONFIRMED
SERVICE_PERFORMED
CANCELLATION_REQUESTED
CANCELLATION_ACCEPTED
CANCELLATION_REJECTED
ISSUE_REPORTED
BUYER_NO_SHOW_REPORTED
NO_SHOW_CONTESTED
ORDER_COMPLETED
```

Timeline may be embedded in OrderDetail or read through:

```http
GET /api/v1/orders/{orderId}/events
```

Order command response is a small `OrderCommandResult`, not the full detail DTO.

---

# 20. Inventory concurrency and reservation lifecycle

Product LocationVariant inventory mode:

```text
TRACKED
UNTRACKED
```

Service inventory is null.

Tracked Product lifecycle:

```text
Checkout Commit
→ reserve
```

On rejection/expiry/cancellation:

```text
→ release reservation
```

On COMPLETED:

```text
→ commit reservation / consume stock
```

Critical last-stock checkout uses DB transaction/locking so if available stock = 1 and two commits race, exactly one succeeds and one gets an inventory conflict.

---

# 21. Need Request API

## 21.1 Create

```http
POST /api/v1/me/need-requests
Idempotency-Key: ...
```

```json
{
  "type": "PRODUCT",
  "categoryId": "0198...",
  "title": "Cần mua Coca-Cola 1.5L",
  "description": "Cần 2 chai, giao tối nay.",
  "location": {
    "addressId": "0198..."
  },
  "radiusMeters": 3000
}
```

Rules:

```text
exactly one PRODUCT/SERVICE type
category required
location required
location context snapshotted
```

Seller-facing Need data should expose sufficient area/distance information but not unnecessarily expose exact buyer private address before an Order requires it.

## 21.2 Lifecycle

```text
ACTIVE
FULFILLED
EXPIRED
CANCELLED
```

Buyer cancel:

```http
POST /api/v1/me/need-requests/{needRequestId}/cancel
```

System expires stale Needs.

## 21.3 Fulfilled rule

Creating an Order from a Need does **not** immediately fulfill it.

```text
NeedResponse
→ Checkout
→ Order PENDING_CONFIRMATION
→ Need remains ACTIVE
```

Only seller/provider Order confirmation causes:

```text
Need ACTIVE
→ FULFILLED
```

If source Order is rejected/expired/cancelled before confirmation, Need stays ACTIVE if otherwise valid.

---

# 22. Need Response API

Seller discovery:

```http
GET /api/v1/merchants/{merchantId}/need-requests
```

Create response:

```http
POST /api/v1/need-requests/{needRequestId}/responses
Idempotency-Key: ...
```

```json
{
  "merchantLocationId": "0198...",
  "items": [
    {
      "locationVariantId": "0198..."
    }
  ],
  "note": "Có thể giao tối nay."
}
```

Rules:

- response is tied to one concrete MerchantLocation;
- response items use existing LocationVariants;
- seller does not create an independent arbitrary response price;
- one MerchantLocation has at most one ACTIVE response per Need;
- the same MerchantProfile may respond from several locations;
- max response items comes from SystemSetting.

Edit:

```http
PUT /api/v1/need-requests/{needRequestId}/responses/{responseId}
If-Match: "3"
```

Withdraw:

```http
POST /api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw
Idempotency-Key: ...
```

```text
ACTIVE → WITHDRAWN
```

Orders created earlier remain valid through snapshots/source tracing.

---

# 23. Need → Direct Checkout → Order attribution

Need-origin checkout stores:

```text
source_type = NEED_REQUEST
source_need_request_id
source_need_response_item_id
```

Backend validates the complete source chain:

```text
NeedRequest
→ NeedResponse
→ NeedResponseItem
→ MerchantLocation
→ LocationVariant
```

This allows reliable attribution and conversion analytics.

---

# 24. Review and reputation

## 24.1 Review eligibility

Review requires:

```text
Order = COMPLETED
```

One buyer review per OrderItem.

Create:

```http
POST /api/v1/me/reviews
Idempotency-Key: ...
```

```json
{
  "orderItemId": "0198...",
  "rating": 5,
  "comment": "Giao nhanh, hàng đúng mô tả."
}
```

Backend resolves merchant/catalog from OrderItem. Client does not select arbitrary review targets.

If reviews are disabled, new review creation fails but old reviews remain.

## 24.2 Public reviews

```http
GET /api/v1/catalog/items/{itemId}/reviews
GET /api/v1/merchants/{merchantId}/reviews
```

Only safe buyer display identity is public. `verifiedPurchase` is backend-derived.

## 24.3 Reputation is separate from Review

Review = subjective experience.

Reputation = transaction reliability from eligible outcomes.

Merchant example:

```text
CONFIRMED -> COMPLETED
= positive

CONFIRMED -> CANCELLED_BY_SELLER_AFTER_CONFIRMATION
= negative
```

Pre-confirm rejection is not automatically negative.

Ambiguous outcomes such as `CLOSED_WITH_ISSUE` and `NO_SHOW_CONTESTED` are excluded.

Seller sees buyer completion rate + warning only, not private transaction history.

Reputation is precomputed/projected rather than scanned from all Orders on every read.

---

# 25. Merchant and location management

A User may manage multiple MerchantProfiles. A MerchantProfile may own multiple MerchantLocations.

Current user's merchants:

```http
GET  /api/v1/me/merchants
POST /api/v1/me/merchants
```

Merchant membership:

```http
GET    /api/v1/merchants/{merchantId}/members
POST   /api/v1/merchants/{merchantId}/members
PATCH  /api/v1/merchants/{merchantId}/members/{memberId}
DELETE /api/v1/merchants/{merchantId}/members/{memberId}
PUT    /api/v1/merchants/{merchantId}/members/{memberId}/locations
```

MerchantLocation:

```http
GET    /api/v1/merchants/{merchantId}/locations
POST   /api/v1/merchants/{merchantId}/locations
GET    /api/v1/merchants/{merchantId}/locations/{locationId}
PATCH  /api/v1/merchants/{merchantId}/locations/{locationId}
DELETE /api/v1/merchants/{merchantId}/locations/{locationId}
```

MerchantLocation is the boundary for:

```text
price
inventory
pickup
product delivery coverage
service area
staff permission
Need response
Order
```

It is not merely an address.

---

# 26. Catalog management

Seller catalog namespace:

```text
/api/v1/merchants/{merchantId}/catalog-items/*
```

Public discovery remains under `/catalog/*`.

## 26.1 Aggregate create

Initial listing creation is one transaction:

```http
POST /api/v1/merchants/{merchantId}/catalog-items
Idempotency-Key: ...
```

Request may include:

```text
CatalogItem
Options
OptionValues
Variants
LocationOffers
```

Temporary `clientKey` values may reference not-yet-created child objects inside the request.

Every CatalogItem has at least one Variant. If no visible options exist, backend creates a hidden/default Variant.

## 26.2 Draft and publish

Initial listing can remain DRAFT.

Publish:

```http
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish
```

Result depends on moderation policy:

```text
DRAFT -> ACTIVE
or
DRAFT -> PENDING_MODERATION
```

## 26.3 Granular edits

After creation, use small PATCH endpoints rather than replacing the entire aggregate.

```http
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId}
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}/location-offers/{locationVariantId}
```

Use `If-Match` on concurrent mutable resources.

Lifecycle commands:

```http
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/pause
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/activate
POST /api/v1/merchants/{merchantId}/catalog-items/{itemId}/archive
```

---

# 27. LocationVariant as offer

`LocationVariant = MerchantLocation + Variant` is the authoritative purchasable offer.

It owns/links:

```text
price
availability
inventory mode/balance linkage
buyer purchase identity
NeedResponse selection
```

CatalogItem and Variant do not carry authoritative universal base price.

---

# 28. Inventory management API

Read:

```http
GET /api/v1/merchants/{merchantId}/inventory
```

Never PATCH tracked stock balance directly.

Set absolute physical stock:

```http
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/set-stock
Idempotency-Key: ...
```

```json
{
  "stockOnHand": 50,
  "reasonCode": "PHYSICAL_COUNT",
  "note": "Kiểm kê cuối ngày"
}
```

Backend converts the difference into `ADJUSTMENT_IN` / `ADJUSTMENT_OUT` and writes balance + movement in one transaction.

Optional explicit commands:

```http
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-in
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-out
```

Invariant:

```text
stockOnHand >= reservedQuantity
```

Otherwise return `409 INVENTORY_BELOW_RESERVED_QUANTITY`.

---

# 29. Selective bulk operations

V1 allows bulk operations only where they are operationally useful and safe:

```text
Catalog
Inventory
selected Admin/moderation actions
```

No generic bulk Order commands.

Inventory:

```http
POST /api/v1/merchants/{merchantId}/inventory/bulk-set-stock
```

Catalog:

```http
POST /api/v1/merchants/{merchantId}/catalog-items/bulk-actions
```

Each item returns independent success/error. One failed item does not automatically roll back successful items unless a future operation explicitly requires an atomic batch.

---

# 30. Delivery coverage and shipping

Coverage belongs to MerchantLocation.

Types:

```text
PRODUCT_DELIVERY
SERVICE_AREA
```

Geometry modes:

```text
RADIUS
SELECTED_AREAS
```

Representative endpoints:

```http
GET   /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages
POST  /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages
PATCH /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId}
```

Shipping applies only to Product.

Policy types:

```text
FREE
FIXED
NEGOTIATED
CONDITIONAL
```

Conditional composition:

```text
ANY
ALL
```

Checkout is authoritative for evaluation.

---

# 31. Media API

Binary uploads go directly from client to object storage using presigned upload information.

Create upload session:

```http
POST /api/v1/media/uploads
```

```json
{
  "purpose": "CATALOG_IMAGE",
  "contentType": "image/jpeg",
  "fileSize": 1284000
}
```

Backend validates authentication, permission, purpose, type, size and quota, then creates `PENDING_UPLOAD` media metadata.

Client uploads directly to storage.

Complete:

```http
POST /api/v1/media/{mediaId}/complete
Idempotency-Key: ...
```

Backend verifies object and moves:

```text
PENDING_UPLOAD -> READY
```

Resources attach by `mediaId`, not arbitrary authoritative external URL.

Potential lifecycle:

```text
PENDING_UPLOAD
READY
FAILED
DELETED
```

Orphan media is cleaned by retention job.

---

# 32. Notifications and SSE

Notifications are database-backed.

```http
GET  /api/v1/me/notifications
POST /api/v1/me/notifications/{notificationId}/mark-read
POST /api/v1/me/notifications/mark-all-read
```

Notification payloads contain stable type/data codes. Frontend localizes text.

SSE endpoint:

```http
GET /api/v1/me/events
Accept: text/event-stream
```

Possible lightweight events:

```text
notification.created
order.updated
need-response.created
need-response.updated
need-response.withdrawn
need-request.fulfilled
moderation.updated
```

SSE is **not** source of truth. Payloads contain IDs/version and frontend refetches REST.

V1 has no durable replay guarantee and no internal realtime chat/WebSocket requirement.

---

# 33. Transactional Outbox

Important business transactions write domain state and Outbox intent in the same PostgreSQL transaction.

Example:

```text
BEGIN
update Order
insert OrderEvent
insert OutboxEvent
COMMIT
```

Conceptual `outbox_events`:

```text
id
event_type
aggregate_type
aggregate_id
payload
occurred_at
status
attempts
next_attempt_at
published_at
last_error
```

Worker:

```text
claim pending events
→ process idempotently
→ create notifications/update projection/send SSE signal
→ mark published
```

Assume at-least-once processing. Consumers must be idempotent.

Kafka/RabbitMQ is not required in V1.

---

# 34. Idempotency

Important mutations support:

```http
Idempotency-Key: ...
```

Scope:

```text
actor + operation + key
```

Same key + same body => same logical result.

Same key + different body => `409 IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST`.

Required at minimum for:

```text
Checkout Commit
Order state commands
Create NeedRequest
Create/withdraw NeedResponse
Create Review
Inventory adjustment
important moderation actions
bulk catalog/inventory commands
media complete
```

Conceptual storage:

```text
idempotency_records
├── id
├── actor_id
├── operation
├── idempotency_key_hash
├── request_hash
├── status
├── response_status
├── response_body_or_result_reference
├── created_at
└── expires_at
```

---

# 35. Optimistic concurrency

Use resource version + ETag/If-Match for mutable resources with concurrent edit risk.

Example:

```http
ETag: "12"
```

Client mutation:

```http
If-Match: "12"
```

Apply to:

```text
Cart
MerchantProfile
MerchantLocation
CatalogItem
Variant
LocationVariant
ShippingPolicy
DeliveryCoverage
SystemSetting
NeedResponse edits
```

Conflict => `409 RESOURCE_VERSION_CONFLICT`.

Order commands primarily use state-machine validation and transactional locking, not mandatory client version on every command.

Use the three mechanisms for different problems:

```text
If-Match/version
→ overwrite protection

Idempotency-Key
→ retry/duplicate protection

DB transaction/locking
→ critical consistency and inventory concurrency
```

---

# 36. Pagination, caching and rate limits

## 36.1 Cursor pagination

Use for:

```text
catalog/search
Need lists/responses
notifications
buyer/seller Order lists
reviews
operational timelines
```

Cursor is opaque.

## 36.2 Admin pagination

Admin tables/reports may use `page`, `size`, whitelisted `sort`, `totalElements`, `totalPages`.

## 36.3 Caching

Stable public metadata can use ETag + short public cache:

```text
categories
location-area hierarchy
selected public merchant metadata
```

Location-sensitive discovery should use private/short carefully keyed caching.

Sensitive resources:

```text
Cart
Checkout
Order
buyer addresses
notifications
transaction contacts
security/session APIs
```

use:

```http
Cache-Control: private, no-store
```

## 36.4 Rate limiting

Use endpoint-group policies, not one global limit.

Groups may include:

```text
Public Discovery
Auth
Checkout / Orders
Need Requests
Need Responses
Reports
Media Upload
Admin
```

Dimensions may include IP, userId or merchantId.

429 returns `Retry-After` and `RATE_LIMIT_EXCEEDED`.

---

# 37. Admin and Moderation

Admin namespace:

```text
/api/v1/admin/*
```

Admin APIs must still use application/domain rules and are not a database bypass.

## 37.1 Admin Orders

V1 Admin Order API is read/audit-only:

```http
GET /api/v1/admin/orders
GET /api/v1/admin/orders/{orderId}
GET /api/v1/admin/orders/{orderId}/events
```

No normal Admin API for:

```text
force-status
force-complete
force-cancel
force-confirm
```

Technical corruption repair belongs to separate maintenance tooling with strict audit.

## 37.2 Moderation

Moderation is conceptually separate from business lifecycle.

Modes:

```text
AUTO_PUBLISH
PRE_APPROVAL
HYBRID
```

Targets may include:

```text
USER
MERCHANT
CATALOG_ITEM
NEED_REQUEST
REVIEW
```

Read:

```http
GET /api/v1/admin/moderation-cases
GET /api/v1/admin/moderation-cases/{caseId}
```

Commands:

```http
POST /api/v1/admin/moderation-cases/{caseId}/approve
POST /api/v1/admin/moderation-cases/{caseId}/hide-content
POST /api/v1/admin/moderation-cases/{caseId}/restore-content
POST /api/v1/admin/moderation-cases/{caseId}/close
```

Every important action records actor, target, reason, timestamp and audit metadata.

## 37.3 User reports

```http
POST /api/v1/reports
Idempotency-Key: ...
```

Client may choose target/reason/note but cannot choose internal priority, assignee or final moderation action.

---

# 38. System Settings

```http
GET   /api/v1/admin/settings
GET   /api/v1/admin/settings/{settingKey}
PATCH /api/v1/admin/settings/{settingKey}
```

Scopes:

```text
GLOBAL
LOCATION_AREA
CATEGORY
MERCHANT
```

Examples of allowlisted business settings:

```text
Need response max items
Order confirmation timeout
Auto-completion grace
Pickup no-show grace
Review enabled
Buyer reputation warning threshold
Moderation mode
Bulk operation max items
```

Never expose secrets such as database URL, JWT signing secret, OAuth client secret or storage credentials.

Updates use optimistic versioning and must retain change history/audit.

---

# 39. Logging and security audit

Important security events:

```text
LOGIN_SUCCESS
LOGIN_FAILED
GOOGLE_LOGIN
PASSWORD_CHANGED
PASSWORD_RESET
SESSION_REVOKED
REFRESH_TOKEN_REUSE_DETECTED
MERCHANT_MEMBERSHIP_CHANGED
ADMIN_SECURITY_ACTION
```

Never log:

```text
password
raw access token
raw refresh token
OAuth authorization code
CSRF secret
presigned-upload credential/signature
```

Avoid full request-body logging for buyer address, phone and free-form private transaction notes.

Prefer structured logs:

```text
requestId
userId
merchantId
orderId
operation
result
latency
errorCode
```

---

# 40. OpenAPI and versioning

Spring Boot DTO/controller contracts produce a controlled OpenAPI specification.

```text
Spring Boot contract
→ OpenAPI
→ generated TypeScript types/client
→ Next.js
```

OpenAPI must document:

```text
authentication
CSRF
Idempotency-Key
If-Match
pagination
error codes
allowedActions
money/time formats
rate limits
enum semantics
```

API major version lives in URL:

```text
/api/v1
```

Additive optional changes are allowed inside V1. Breaking changes require `/api/v2`.

Frontend must safely render unknown future enum values instead of crashing; business actions still follow backend `allowedActions`.

---

# 41. Scheduled system jobs

No public endpoints are exposed for system transitions such as:

```text
Order pending expiration
Order auto-completion
Issue timeout -> CLOSED_WITH_ISSUE
Need expiration
Checkout-session cleanup
Media orphan cleanup
Idempotency-record cleanup
Outbox retry
```

Jobs call application/domain services rather than directly bypassing state-machine rules with arbitrary SQL updates.

---

# 42. Technical tables introduced by API Design

Compared with the domain-focused Database Design, API Design requires these technical structures at minimum:

```text
auth_sessions
auth_refresh_tokens
checkout_sessions
idempotency_records
outbox_events
```

Potentially add `security_audit_events` if the existing audit model is not sufficient.

Conceptual fields:

### `auth_sessions`

```text
id
user_id
device_name
user_agent
created_at
last_used_at
expires_at
revoked_at
token_family_id
```

### `auth_refresh_tokens`

```text
id
session_id
token_hash
replaced_by_token_id
expires_at
used_at
revoked_at
created_at
```

### `checkout_sessions`

```text
id
token_hash
user_id
source_type
source_context
cart_version
request_snapshot
preview_snapshot
expires_at
consumed_at
created_at
```

### `idempotency_records`

```text
id
actor_id
operation
idempotency_key_hash
request_hash
status
response_status
response_body_or_result_reference
created_at
expires_at
```

### `outbox_events`

```text
id
event_type
aggregate_type
aggregate_id
payload
occurred_at
status
attempts
next_attempt_at
published_at
last_error
```

These additions must be reflected in the subsequent Database migration and Technical Plan.

---

# 43. Core invariants

## Authentication

```text
First registration uses Google OAuth.
New Google user must create CoVie password.
Future login supports Google or Email/Password.
Google is automated V1 recovery root.
Refresh tokens rotate and raw values are never stored.
Merchant permissions are DB-resolved, not JWT-embedded.
```

## Catalog/Cart

```text
Every CatalogItem has at least one Variant.
LocationVariant is the purchasable offer.
Price is authoritative at LocationVariant.
Add-to-cart uses locationVariantId.
Cart belongs to one MerchantLocation.
Cart never mixes Product and Service.
Service quantity is always 1.
```

## Checkout

```text
CART, DIRECT and NEED_REQUEST use one Checkout Engine.
Preview never locks price.
Preview never reserves stock.
Checkout token is opaque, server-side, short-lived and single-use.
Commit always revalidates.
Client subtotal/total is never authoritative.
```

## Order/Inventory

```text
Order status is never client PATCHed directly.
Transitions occur through commands/system jobs.
COMPLETED is irreversible.
Tracked inventory reserves at Order creation.
Reservation releases on unsuccessful terminal outcomes.
Reservation commits/consumes on COMPLETED.
Concurrent last-stock checkout cannot oversell.
Important transitions create OrderEvents.
Business transition + Outbox intent are transactionally consistent.
```

## Need

```text
Need has exactly one PRODUCT/SERVICE type.
Category is required.
NeedResponse belongs to one MerchantLocation.
One active response per MerchantLocation per Need.
Response item uses existing LocationVariant.
Need checkout is direct, not cart-based.
Need becomes FULFILLED only after source Order is CONFIRMED.
```

## Review/Reputation

```text
Review requires COMPLETED Order.
One buyer review per OrderItem.
Review and reputation are separate systems.
Ambiguous outcomes are excluded from automatic reputation.
```

## Admin

```text
Normal Admin API cannot force Order state.
Moderation is separate from transaction arbitration.
System Settings expose only allowlisted business policy.
```

---

# 44. Endpoint catalog — V1 baseline

The exact minor naming may be polished during OpenAPI/controller implementation, but semantics must remain consistent.

## Auth

```http
GET  /api/v1/auth/google/start
GET  /api/v1/auth/google/callback
POST /api/v1/auth/onboarding/set-password
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

## Me / Security

```http
GET    /api/v1/me
GET    /api/v1/me/security/sessions
DELETE /api/v1/me/security/sessions/{sessionId}
POST   /api/v1/me/security/sessions/revoke-others
```

## Addresses

```http
GET    /api/v1/me/addresses
POST   /api/v1/me/addresses
GET    /api/v1/me/addresses/{addressId}
PATCH  /api/v1/me/addresses/{addressId}
DELETE /api/v1/me/addresses/{addressId}
```

## Public discovery

```http
GET /api/v1/categories
GET /api/v1/location-areas
GET /api/v1/catalog/items
GET /api/v1/catalog/items/{itemId}
GET /api/v1/catalog/items/{itemId}/reviews
```

## Cart

```http
GET    /api/v1/me/carts
GET    /api/v1/me/carts/{cartId}
POST   /api/v1/me/carts/items
PATCH  /api/v1/me/carts/{cartId}/items/{cartItemId}
DELETE /api/v1/me/carts/{cartId}/items/{cartItemId}
```

## Checkout

```http
POST /api/v1/checkouts/preview
POST /api/v1/checkouts/commit
```

## Buyer Orders

```http
GET /api/v1/me/orders
```

## Canonical Orders

```http
GET  /api/v1/orders/{orderId}
GET  /api/v1/orders/{orderId}/events
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

## Need buyer

```http
GET  /api/v1/me/need-requests
POST /api/v1/me/need-requests
GET  /api/v1/me/need-requests/{needRequestId}
POST /api/v1/me/need-requests/{needRequestId}/cancel
GET  /api/v1/me/need-requests/{needRequestId}/responses
```

## Need seller

```http
GET  /api/v1/merchants/{merchantId}/need-requests
POST /api/v1/need-requests/{needRequestId}/responses
PUT  /api/v1/need-requests/{needRequestId}/responses/{responseId}
POST /api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw
```

## Reviews

```http
POST /api/v1/me/reviews
GET  /api/v1/merchants/{merchantId}/reviews
```

## Merchant

```http
GET   /api/v1/me/merchants
POST  /api/v1/me/merchants
GET   /api/v1/merchants/{merchantId}
PATCH /api/v1/merchants/{merchantId}
```

## Merchant members

```http
GET    /api/v1/merchants/{merchantId}/members
POST   /api/v1/merchants/{merchantId}/members
PATCH  /api/v1/merchants/{merchantId}/members/{memberId}
DELETE /api/v1/merchants/{merchantId}/members/{memberId}
PUT    /api/v1/merchants/{merchantId}/members/{memberId}/locations
```

## Merchant locations

```http
GET    /api/v1/merchants/{merchantId}/locations
POST   /api/v1/merchants/{merchantId}/locations
GET    /api/v1/merchants/{merchantId}/locations/{locationId}
PATCH  /api/v1/merchants/{merchantId}/locations/{locationId}
DELETE /api/v1/merchants/{merchantId}/locations/{locationId}
```

## Seller Catalog

```http
GET   /api/v1/merchants/{merchantId}/catalog-items
POST  /api/v1/merchants/{merchantId}/catalog-items
GET   /api/v1/merchants/{merchantId}/catalog-items/{itemId}
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId}
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}/location-offers/{locationVariantId}
POST  /api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish
POST  /api/v1/merchants/{merchantId}/catalog-items/{itemId}/pause
POST  /api/v1/merchants/{merchantId}/catalog-items/{itemId}/activate
POST  /api/v1/merchants/{merchantId}/catalog-items/{itemId}/archive
POST  /api/v1/merchants/{merchantId}/catalog-items/bulk-actions
```

## Inventory

```http
GET  /api/v1/merchants/{merchantId}/inventory
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/set-stock
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-in
POST /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-out
POST /api/v1/merchants/{merchantId}/inventory/bulk-set-stock
```

## Coverage / Shipping

```http
GET   /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages
POST  /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages
PATCH /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId}
GET   /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies
POST  /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies
PATCH /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies/{policyId}
```

## Seller Orders

```http
GET /api/v1/merchants/{merchantId}/orders
```

## Media

```http
POST /api/v1/media/uploads
POST /api/v1/media/{mediaId}/complete
```

## Notifications / SSE

```http
GET  /api/v1/me/notifications
POST /api/v1/me/notifications/{notificationId}/mark-read
POST /api/v1/me/notifications/mark-all-read
GET  /api/v1/me/events
```

## Reports

```http
POST /api/v1/reports
```

## Admin

```http
GET /api/v1/admin/users
GET /api/v1/admin/merchants
GET /api/v1/admin/catalog-items
GET /api/v1/admin/need-requests
GET /api/v1/admin/reviews
GET /api/v1/admin/orders
GET /api/v1/admin/orders/{orderId}
GET /api/v1/admin/orders/{orderId}/events
GET /api/v1/admin/moderation-cases
GET /api/v1/admin/moderation-cases/{caseId}
POST /api/v1/admin/moderation-cases/{caseId}/approve
POST /api/v1/admin/moderation-cases/{caseId}/hide-content
POST /api/v1/admin/moderation-cases/{caseId}/restore-content
POST /api/v1/admin/moderation-cases/{caseId}/close
GET /api/v1/admin/settings
GET /api/v1/admin/settings/{settingKey}
PATCH /api/v1/admin/settings/{settingKey}
GET /api/v1/admin/audit-logs
```

---

# 45. Testing contract

Before implementation is considered complete, test at least the following boundaries.

## Authorization

```text
buyer reads only own private Orders
seller member sees only permitted merchant/location Orders
STAFF location restriction works
unauthorized private resource returns 404
visible-but-forbidden action returns 403
```

## Authentication

```text
first Google login requires password onboarding
existing Google login resolves to same User
Email/Password resolves to same User
refresh rotation works
reuse detection revokes session family
multi-device sessions are independent
password reset via recent Google auth revokes old sessions
CSRF rejects invalid browser mutation
```

## Checkout

```text
Preview does not reserve stock
Preview does not lock price
expired checkout token rejected
consumed token cannot create another Order
Commit revalidates price/availability/coverage
same Idempotency-Key creates one Order
```

## Inventory concurrency

Required concurrent test:

```text
available stock = 1

Checkout A and B execute simultaneously

Expected:
1 success
1 inventory conflict
stock/reservation invariant remains valid
```

Also test reservation release/commit and `stockOnHand >= reservedQuantity`.

## Order state machine

Test all allowed/forbidden transitions, especially:

```text
buyer direct completion from CONFIRMED
seller cannot confirm cancelled Order
issue prevents auto-complete
COMPLETED cannot reopen
no-show grace enforced server-side
ambiguous outcomes excluded from reputation
```

## Need

```text
one active response per MerchantLocation
response item belongs to that location
Need source chain is validated at checkout
Need remains ACTIVE while Order is pending
Need becomes FULFILLED only at Order CONFIRMED
rejected/expired source Order does not fulfill Need
```

## Review/Reputation

```text
review only after COMPLETED
one review per OrderItem
review target cannot be spoofed
review-disable setting blocks new reviews
ambiguous outcomes excluded from reputation
```

## Reliability

```text
business transaction + Outbox intent atomic
Outbox consumer idempotent
Idempotency-Key same body returns same result
same key different body returns conflict
If-Match conflict returns RESOURCE_VERSION_CONFLICT
```

---

# 46. Explicit V1 non-goals

```text
internal realtime chat
WebSocket chat infrastructure
Kafka/RabbitMQ requirement
service booking slots/calendar engine
automatic payment settlement
bank transfer verification
escrow/refund engine
admin transaction arbitration
admin force Order status
email forgot-password flow
phone OTP login
Facebook/Apple/Zalo login
durable SSE replay
generic GraphQL API
generic database query DSL
generic bulk operations for every resource
```

---

# 47. Inputs to the Technical Plan

The next Technical Plan should convert this API contract into implementation work for:

1. Spring Boot module/package boundaries.
2. Controller/application/domain/repository layering.
3. Spring Security, Google OAuth2, cookies and CSRF.
4. Password hashing policy.
5. Access-token signing/key management.
6. Refresh-session/token-family transactions.
7. Migrations for `auth_sessions`, `auth_refresh_tokens`, `checkout_sessions`, `idempotency_records`, `outbox_events`.
8. Inventory locking and transaction isolation.
9. Checkout transaction boundaries.
10. Outbox worker claim/retry design.
11. Scheduled-job locking.
12. SSE connection management.
13. Presigned object-storage integration.
14. Rate-limit implementation.
15. OpenAPI + TypeScript client generation.
16. Validation/DTO mapping conventions.
17. Integration/concurrency/contract testing.
18. Observability and request correlation.
19. Same-domain Next.js + Spring Boot deployment routing.

---

# 48. Final architecture summary

```text
                              CoVie API V1
                                   |
                +------------------+------------------+
                |                  |                  |
           Public API          User API          Seller API
                |                  |                  |
            Catalog              /me/*          /merchants/*
            Search                 |                  |
            Metadata               +--------+---------+
                                            |
                                      Checkout Engine
                                  CART / DIRECT / NEED
                                            |
                                          Orders
                                            |
                          +-----------------+-----------------+
                          |                                   |
                    Domain state                         OrderEvents
                          |                                   |
                          +-----------------+-----------------+
                                            |
                                      PostgreSQL Tx
                                 +----------+----------+
                                 |                     |
                            Domain Data           Outbox Events
                                                       |
                                                     Worker
                                           +-----------+-----------+
                                           |                       |
                                     Notifications              SSE signal
```

Security:

```text
Google-first registration
+ mandatory CoVie password
+ later Google or Email/Password login
+ short-lived access token
+ rotating refresh token
+ multi-device sessions
+ CSRF for browser mutations
+ DB-resolved merchant/location permissions
+ 404/403 visibility rules
+ endpoint-group rate limits
+ audit logging
```

Reliability:

```text
use-case DTOs
+ explicit commands
+ allowedActions
+ optimistic locking
+ idempotency
+ DB transactions
+ inventory locking
+ immutable Order snapshots
+ append-only OrderEvents
+ transactional Outbox
+ REST as source of truth
```

**This document is the approved CoVie API Design V1.0 baseline for the subsequent Technical Plan.**
