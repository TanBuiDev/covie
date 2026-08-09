# CoVie Plan 09 — Web Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Plan:** 09 / 10  
**Target repo path:** `docs/superpowers/plans/2026-08-08-covie-web-completion.md`  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Ready for implementation review

**Goal:** Hoàn thiện CoVie V1 Web App/PWA để các P0 journey Guest → Buyer, Seller/Provider và Admin sử dụng được xuyên suốt trên mobile/desktop, toàn bộ UI nối với generated API client và không sao chép business rule của Spring Boot.

**Architecture:** Giữ Next.js App Router làm một web app duy nhất với route groups Public/Auth/Buyer/Seller/Admin. Public/read-heavy pages ưu tiên Server Components; các workflow có mutation/refetch dùng Client Components + TanStack Query; forms dùng React Hook Form + Zod cho UX, nhưng backend validation/error codes vẫn authoritative. SSE chỉ invalidate/refetch REST cache. Không tạo frontend Order state machine, pricing engine, shipping evaluator, review eligibility rule, moderation rule hay Need matching rule.

**Tech Stack:** Node.js 24 LTS, pnpm 10, Next.js 16.2.x, React 19, TypeScript strict, generated `@covie/api-client`, TanStack Query v5 line, React Hook Form v7 line, Zod 4, Vitest, React Testing Library, Playwright 1.x, native EventSource, CSS/CSS custom properties, Spring Boot 4.1 backend contracts from Plans 01–08.

## Global Constraints

- Plans 01–08 are prerequisites. Plan 09 does not redesign their business contracts.
- Public API base path remains `/api/v1`.
- Spring Boot is authoritative for authentication, authorization, location eligibility, pricing, shipping, inventory, checkout, Order lifecycle, Need matching, review eligibility, reputation and moderation.
- Frontend consumes generated OpenAPI types. Do not hand-copy backend DTO/enums into feature folders.
- Existing generated schema path remains `packages/api-client/src/generated/schema.ts`.
- Browser uses same-origin `/api/v1`; Server Components use `API_INTERNAL_BASE_URL` and explicitly forward incoming cookies through `createServerReadClient()`.
- Personal/order/cart/checkout/admin reads are `no-store`.
- Browser unsafe requests continue to use Plan 02 CSRF-aware `createApiClient()`.
- HttpOnly access/refresh cookies are never read or persisted by JavaScript.
- No auth token, refresh token or checkout token is placed in URL, localStorage or persistent client state.
- `checkoutToken` exists only in the active Checkout component memory; a reload creates a fresh Preview.
- `Idempotency-Key` is stable for one mutation intent and replaced only when the user starts a genuinely new intent.
- `If-Match` uses server versions/ETags; stale data causes refetch UX, never silent overwrite.
- Order UI renders backend `allowedActions`; it never derives allowed transitions from `status`.
- Unknown enum values render safe fallback text and never crash the page.
- SSE payloads never become authoritative cache data. SSE only invalidates/refetches REST queries.
- Money uses integer minor units + ISO currency from generated DTOs. UI formatting is presentation only.
- Absolute countdowns use server timestamps and `Date.now()` only to display remaining duration; client time never decides whether a backend action is legal.
- Negotiated shipping is never displayed as `0đ`. Show “Thỏa thuận với người bán” and treat backend `knownTotal` as the known subtotal.
- Service quantity is always `1`; scale/quantity-like service selection comes from Variant.
- Guest browse remains anonymous. Cart/Checkout/Order/Need personal actions require login.
- There is no guest cart.
- No WebSocket, internal chat, booking calendar, payment verification, bank QR storage, dispute-arbitration UI, native app or search cluster is added.
- Consumer UI is mobile-first. Seller Center must remain usable on mobile/tablet. Admin remains desktop-first.
- Destructive/irreversible actions require explicit confirmation.
- `COMPLETED` confirmation copy must state that completion is irreversible.
- Plan 09 creates **no Flyway migration by default**. Plan 08 ended at `V013__moderation_system_settings_admin.sql`; the next available migration number remains `V014` for Plan 10 unless an evidence-backed backend schema change is introduced. Never create an empty migration merely to consume `V014`.

---

# Inputs from Earlier Plans

Plan 09 assumes these frontend foundations already exist:

```text
apps/web/lib/api/api-client.ts
apps/web/lib/api/api-error.ts
apps/web/lib/api/server-api-client.ts
apps/web/lib/auth/require-user.ts
apps/web/features/auth/csrf.ts
apps/web/features/auth/auth-api.ts
apps/web/features/profile/*
apps/web/features/location/*
apps/web/features/merchant/*
apps/web/features/media/*
apps/web/features/catalog/*
apps/web/features/cart/*
apps/web/features/checkout/*
apps/web/features/inventory/*
apps/web/features/orders/*
apps/web/features/needs/*
apps/web/features/reviews/*
apps/web/features/notifications/*
apps/web/features/admin/*
packages/api-client/openapi/openapi.json
packages/api-client/src/generated/schema.ts
```

Existing backend/API behavior is reused. Plan 09 may add an **additive read field for Review eligibility** because the UI must not infer eligibility from Order status. No other new business API is planned.

Realtime boundary remains exactly the Plan 07 design:

```text
backend: vn.covie.realtime.SseSignal / SseSignalPublisher
browser: native EventSource("/api/v1/me/events")
```

The web app never calls or reimplements `SseSignalPublisher`; it treats the emitted signal as cache invalidation and refetches REST.

---

# Final Route Shape

Route groups do not change visible URLs.

```text
/
├── search
├── category/[slug]
├── items/[itemId]
├── merchants/[merchantId]
├── login
├── register
├── onboarding/password
├── reset-password
├── cart
├── cart/[cartId]
├── checkout
├── checkout/success/[orderId]
├── orders
├── orders/[orderId]
├── needs
├── needs/new
├── needs/[needId]
├── me
│   ├── addresses
│   ├── notifications
│   └── security/sessions
├── seller
│   └── [merchantId]
│       ├── orders
│       ├── orders/[orderId]
│       ├── needs
│       ├── catalog
│       ├── catalog/new
│       ├── catalog/[itemId]
│       ├── inventory
│       ├── locations
│       ├── locations/[locationId]
│       ├── locations/[locationId]/delivery
│       ├── locations/[locationId]/shipping
│       └── settings
└── admin
    ├── settings
    ├── moderation
    ├── moderation/[caseId]
    ├── reports
    ├── users
    ├── merchants
    ├── orders
    └── audit
```

Legacy `/explore` may remain as a redirect to `/search`; do not maintain two independent discovery implementations.

---

# Repository Map Added/Normalized by Plan 09

```text
apps/web/
├── app/
│   ├── layout.tsx
│   ├── providers.tsx
│   ├── manifest.ts
│   ├── page.tsx
│   ├── (public)/
│   │   ├── search/page.tsx
│   │   ├── category/[slug]/page.tsx
│   │   ├── items/[itemId]/page.tsx
│   │   └── merchants/[merchantId]/page.tsx
│   ├── (auth)/
│   │   ├── login/page.tsx
│   │   ├── register/page.tsx
│   │   ├── onboarding/password/page.tsx
│   │   └── reset-password/page.tsx
│   ├── (buyer)/
│   │   ├── layout.tsx
│   │   ├── cart/page.tsx
│   │   ├── cart/[cartId]/page.tsx
│   │   ├── checkout/page.tsx
│   │   ├── checkout/success/[orderId]/page.tsx
│   │   ├── orders/page.tsx
│   │   ├── orders/[orderId]/page.tsx
│   │   ├── needs/*
│   │   └── me/*
│   ├── (seller)/seller/
│   │   ├── page.tsx
│   │   └── [merchantId]/*
│   └── (admin)/admin/*
├── components/
│   ├── layout/
│   └── ui/
├── features/
│   ├── discovery/
│   ├── purchase/
│   ├── cart/
│   ├── checkout/
│   ├── orders/
│   ├── needs/
│   ├── seller/
│   ├── reviews/
│   ├── reputation/
│   ├── reports/
│   ├── notifications/
│   └── admin/
├── lib/
│   ├── api/
│   ├── auth/
│   ├── query/
│   ├── forms/
│   ├── format/
│   ├── location/
│   └── pwa/
├── public/
│   ├── icon.svg
│   ├── offline.html
│   └── sw.js
├── e2e/
│   ├── fixtures/
│   ├── guest-discovery.spec.ts
│   ├── buyer-product-order.spec.ts
│   ├── service-order.spec.ts
│   ├── need-flow.spec.ts
│   ├── seller-management.spec.ts
│   └── admin-control-plane.spec.ts
└── playwright.config.ts
```

Do not create a parallel DTO tree or frontend domain model mirroring backend entities.

---

## Task 1: Install Client Server-State/Form Runtime and Normalize Shared Providers

**Files:**
- Modify: `apps/web/package.json`
- Modify: `pnpm-lock.yaml`
- Modify: `apps/web/app/layout.tsx`
- Create: `apps/web/app/providers.tsx`
- Create: `apps/web/lib/query/query-client.ts`
- Create: `apps/web/lib/query/query-keys.ts`
- Create: `apps/web/lib/forms/apply-field-errors.ts`
- Create: `apps/web/lib/api/error-copy.ts`
- Test: `apps/web/tests/query/query-client.test.tsx`
- Test: `apps/web/tests/forms/apply-field-errors.test.ts`

**Interfaces:**
- Consumes: Plan 01 `createApiClient`, Plan 02 `CovieApiError`, generated API client.
- Produces: one QueryClient provider, stable query-key factory and shared backend error-to-form mapping for Tasks 3–17.

- [ ] **Step 1: Write RED tests for query defaults and backend field errors**

Create tests that assert:

```text
Query mutations do not retry automatically
Query 401 does not enter an infinite retry loop
5xx read queries may retry at most once
field map {"title":["REQUIRED"]} attaches to RHF field "title"
unknown field error goes to form-level error collection
backend human message is not parsed to determine behavior
```

Run:

```bash
pnpm --filter @covie/web test -- query-client apply-field-errors
```

Expected: FAIL because the shared query/form utilities do not exist.

- [ ] **Step 2: Add only the runtime dependencies required by the Master Technical Plan**

Run:

```bash
pnpm --filter @covie/web add @tanstack/react-query@^5 react-hook-form@^7 @hookform/resolvers@^5
```

Zod already exists from Plan 01. Do not add Redux/Zustand for server state.

Add package scripts only if absent:

```json
{
  "scripts": {
    "lint": "eslint .",
    "typecheck": "tsc --noEmit",
    "test": "vitest run",
    "build": "next build"
  }
}
```

- [ ] **Step 3: Implement `QueryClient` defaults**

`apps/web/lib/query/query-client.ts`:

```ts
import { QueryClient } from "@tanstack/react-query";
import { CovieApiError } from "@/lib/api/api-error";

export function createCovieQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: true,
        retry(failureCount, error) {
          if (error instanceof CovieApiError && error.status < 500) return false;
          return failureCount < 1;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });
}
```

These defaults are UX policy only. They do not change backend retry/idempotency semantics.

- [ ] **Step 4: Create stable query-key factories**

`query-keys.ts` must use arrays, never ad-hoc concatenated strings:

```ts
export const queryKeys = {
  me: ["me"] as const,
  carts: ["me", "carts"] as const,
  cart: (cartId: string) => ["me", "carts", cartId] as const,
  orders: (scope: "buyer" | "seller", ownerId?: string) =>
    ["orders", scope, ownerId ?? "me"] as const,
  order: (orderId: string) => ["orders", "detail", orderId] as const,
  needs: ["me", "needs"] as const,
  need: (needId: string) => ["me", "needs", needId] as const,
  notifications: ["me", "notifications"] as const,
  merchant: (merchantId: string) => ["merchant", merchantId] as const,
  sellerInventory: (merchantId: string) => ["seller", merchantId, "inventory"] as const,
  adminSettings: ["admin", "settings"] as const,
  moderation: ["admin", "moderation"] as const,
};
```

Do not encode Order status transitions into query keys.

- [ ] **Step 5: Implement one client provider**

`app/providers.tsx` is a Client Component that creates one QueryClient per browser app lifetime using `useState`, then wraps children in `QueryClientProvider`.

`app/layout.tsx` keeps metadata and wraps:

```tsx
<body>
  <Providers>{children}</Providers>
</body>
```

Do not make the root layout a Client Component.

- [ ] **Step 6: Implement backend field-error mapping**

`apply-field-errors.ts` receives generated/backend field names and a RHF `setError`. Known field names are mapped explicitly; unknown fields are returned as form-level errors.

Never use backend fallback `message` to choose a branch.

- [ ] **Step 7: Add stable Vietnamese error copy**

`error-copy.ts` maps stable codes used broadly:

```text
AUTHENTICATION_REQUIRED -> "Vui lòng đăng nhập để tiếp tục."
RESOURCE_NOT_FOUND -> "Nội dung không còn khả dụng."
RESOURCE_VERSION_CONFLICT -> "Dữ liệu đã thay đổi. Hãy tải lại và thử lại."
CHECKOUT_CHANGED -> "Thông tin đơn đã thay đổi. Hãy kiểm tra lại trước khi đặt."
RATE_LIMIT_EXCEEDED -> "Bạn thao tác quá nhanh. Vui lòng thử lại sau."
```

Unknown code -> `"Không thể hoàn tất thao tác. Mã yêu cầu: <requestId>"` when requestId exists.

- [ ] **Step 8: Run GREEN checks**

```bash
pnpm --filter @covie/web test -- query-client apply-field-errors
pnpm --filter @covie/web typecheck
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/web/package.json pnpm-lock.yaml apps/web/app apps/web/lib/query apps/web/lib/forms apps/web/lib/api/error-copy.ts apps/web/tests/query apps/web/tests/forms
git commit -m "feat(web): add shared query and form runtime"
```

---

## Task 2: Build PWA Shell, Consumer Navigation and Responsive Layout Primitives

**Files:**
- Modify: `apps/web/app/globals.css`
- Modify: `apps/web/app/providers.tsx`
- Create: `apps/web/app/manifest.ts`
- Create: `apps/web/public/icon.svg`
- Create: `apps/web/public/offline.html`
- Create: `apps/web/public/sw.js`
- Create: `apps/web/lib/pwa/register-service-worker.tsx`
- Create: `apps/web/components/layout/app-header.tsx`
- Create: `apps/web/components/layout/consumer-bottom-nav.tsx`
- Create: `apps/web/components/layout/consumer-shell.tsx`
- Create: `apps/web/components/layout/seller-shell.tsx`
- Create: `apps/web/components/layout/admin-shell.tsx`
- Create: `apps/web/components/ui/action-bar.tsx`
- Create: `apps/web/components/ui/empty-state.tsx`
- Create: `apps/web/components/ui/error-state.tsx`
- Create: `apps/web/components/ui/loading-state.tsx`
- Test: `apps/web/tests/layout/consumer-shell.test.tsx`
- Test: `apps/web/tests/pwa/service-worker-policy.test.ts`

**Interfaces:**
- Produces: mobile-first shell and PWA install metadata used by every route.
- Does not cache authoritative business API responses.

- [ ] **Step 1: Write shell and cache-policy RED tests**

Assert:

```text
consumer nav has Home/Search/Need/Orders/Me
cart lives in header, not bottom nav
seller shell exposes Dashboard/Orders/Catalog/Locations/Settings
admin shell is separate and has no buyer bottom nav
service worker bypasses every /api/v1 request
service worker does not cache /orders, /checkout, /me, /seller or /admin HTML
```

- [ ] **Step 2: Create manifest and icon**

`manifest.ts`:

```ts
import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "CoVie",
    short_name: "CoVie",
    description: "Marketplace gần bạn",
    start_url: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#ffffff",
    lang: "vi",
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml" }],
  };
}
```

Create an actual simple SVG icon containing a vector `C`/location motif; do not depend on a remote asset.

- [ ] **Step 3: Implement conservative service worker**

`public/sw.js` rules:

```text
GET /api/v1/**              -> fetch only, never Cache API
non-GET                     -> fetch only
navigation request          -> network first; offline.html only on network failure
/icon.svg + /offline.html   -> precache
all other requests          -> normal network; no business-data cache in Plan 09
```

This gives install/offline shell behavior without risking stale Order/Cart/Checkout data.

- [ ] **Step 4: Register service worker only in production browser**

`register-service-worker.tsx` checks `"serviceWorker" in navigator` and `process.env.NODE_ENV === "production"`.

Mount `<RegisterServiceWorker />` once inside the existing Client Component `app/providers.tsx`, alongside `QueryClientProvider`. Do not convert the root layout to a Client Component.

Registration errors are logged without auth/business payload.

- [ ] **Step 5: Implement navigation shells**

Consumer:
- header: CoVie, current browse location button, cart icon;
- bottom nav on mobile: `Trang chủ`, `Khám phá`, `Cần mua`, `Đơn`, `Tôi`;
- desktop: top/side equivalent;
- sticky bottom action bar reserves safe-area inset.

Seller:
- merchant switcher;
- Dashboard, Orders, Catalog, Needs, Inventory, Locations, Settings;
- mobile horizontal/compact navigation acceptable.

Admin:
- desktop-first sidebar;
- Dashboard, Moderation, Reports, Users, Merchants, Orders, Settings, Audit;
- never render buyer bottom nav.

- [ ] **Step 6: Define responsive primitives in CSS**

Use CSS custom properties and media queries. Minimum layout rules:

```text
< 768px: consumer bottom nav fixed, one-column forms/cards
>= 768px: wider content, optional two-column card grid
>= 1024px: seller/admin side navigation
admin minimum practical width: 1024px; below it show horizontal-scroll-safe tables, not broken layouts
```

No component may require hover to reveal its only action.

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- consumer-shell service-worker-policy
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
git add apps/web/app apps/web/public apps/web/lib/pwa apps/web/components apps/web/tests/layout apps/web/tests/pwa
git commit -m "feat(web): add responsive pwa application shells"
```

---

## Task 3: Complete Browse Location Context and Location Chooser

**Files:**
- Modify: `apps/web/features/location/location-context.ts`
- Modify: `apps/web/features/location/location-input.tsx`
- Create: `apps/web/features/location/browse-location-provider.tsx`
- Create: `apps/web/features/location/location-chooser.tsx`
- Create: `apps/web/features/location/use-browse-location.ts`
- Create: `apps/web/lib/location/browse-location-storage.ts`
- Modify: `apps/web/components/layout/app-header.tsx`
- Test: `apps/web/tests/location/location-chooser.test.tsx`
- Test: `apps/web/tests/location/browse-location-storage.test.ts`

**Interfaces:**
- Produces explicit `{lat,lng}` guest context or authenticated `addressId` context.
- Never creates a magic backend session/current-location header.

- [ ] **Step 1: Write location behavior tests**

Cover:

```text
first visit with no saved browse context shows chooser affordance
GPS success stores only browse coordinates + display label
GPS denial offers manual selection
authenticated saved address selection stores addressId, not copied private address object
changing browse location invalidates public discovery queries
location is never encoded into custom X-Current-Location header
```

- [ ] **Step 2: Define client-side browse context**

Use a discriminated union for UI state only:

```ts
export type BrowseLocation =
  | { kind: "coordinates"; lat: number; lng: number; label: string }
  | { kind: "address"; addressId: string; label: string };
```

Do not duplicate backend `LocationContext` DTO.

- [ ] **Step 3: Persist only the location selector preference**

Use localStorage key `covie:browse-location:v1`.

Rules:
- coordinate preference may persist;
- saved-address selection stores only `addressId` + display label;
- no phone, unit note, access token or full address object is persisted;
- malformed/old version clears itself.

- [ ] **Step 4: Implement GPS request**

Call `navigator.geolocation.getCurrentPosition()` only after user action.

Do not request permission automatically on every page load.

Error states:
- denied -> manual chooser;
- unavailable/timeout -> manual chooser;
- success -> set coordinate context.

- [ ] **Step 5: Connect to generated discovery query params**

Provide helper:

```ts
export function toCatalogLocationParams(location: BrowseLocation | null) {
  if (!location) return {};
  return location.kind === "address"
    ? { addressId: location.addressId }
    : { lat: location.lat, lng: location.lng };
}
```

Do not invent a backend header/session.

- [ ] **Step 6: Invalidate discovery after location change**

Use QueryClient invalidation for public catalog/merchant location-sensitive queries.

Cart/Order/Checkout queries are not invalidated merely because browse location changes.

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- location
pnpm --filter @covie/web typecheck
git add apps/web/features/location apps/web/lib/location apps/web/components/layout/app-header.tsx apps/web/tests/location
git commit -m "feat(web): complete explicit browse location ux"
```

---

## Task 4: Complete Home, Search, Category, Listing and Merchant Public Discovery

**Files:**
- Modify: `apps/web/app/page.tsx`
- Create: `apps/web/app/(public)/search/page.tsx`
- Create: `apps/web/app/(public)/category/[slug]/page.tsx`
- Modify: `apps/web/app/(public)/items/[itemId]/page.tsx`
- Create: `apps/web/app/(public)/merchants/[merchantId]/page.tsx`
- Modify: `apps/web/app/(public)/explore/page.tsx`
- Create: `apps/web/features/discovery/catalog-read-api.ts`
- Create: `apps/web/features/discovery/home-sections.tsx`
- Create: `apps/web/features/discovery/search-form.tsx`
- Create: `apps/web/features/discovery/search-results.tsx`
- Create: `apps/web/features/discovery/search-filters.tsx`
- Create: `apps/web/features/discovery/listing-card.tsx`
- Create: `apps/web/features/discovery/listing-detail.tsx`
- Create: `apps/web/features/discovery/merchant-detail.tsx`
- Create: `apps/web/features/discovery/variant-selector.tsx`
- Test: `apps/web/tests/discovery/public-discovery.test.tsx`
- Test: `apps/web/tests/discovery/listing-detail.test.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/catalog/items`, item detail, merchant detail, categories, explicit location context.
- Produces: PUB-001 through PUB-009 behavior without a duplicate search/pricing engine.

- [ ] **Step 1: Write branching RED tests**

Cover:
- Guest home renders without `/me`;
- Product/Service/RESALE use one catalog API and different labels;
- exact-priced Service variant never renders “Giá từ”;
- Product/RESALE cards show resolved-location offer price;
- missing location has clear chooser CTA;
- empty search offers “Đăng Cần mua”;
- unavailable/hidden item 404 renders safe not-found state;
- negotiated shipping label is never `0đ`;
- unknown listing enum renders generic “Nội dung”.

- [ ] **Step 2: Split stable server reads from browser-location-sensitive reads**

`catalog-read-api.ts` exposes generated-client wrappers in two categories:

```text
stable public metadata/detail without browser-only context
-> Server Component may call Spring with API_INTERNAL_BASE_URL

location-sensitive catalog discovery
-> Client Component uses same-origin generated client + BrowseLocation from Task 3
```

Do not try to read browser `localStorage`/GPS from a Server Component. Do not copy browser coordinates into a Next.js server session.

When an authenticated `addressId` is used from a Server Component, use the existing `createServerReadClient()` and `cache: no-store`; otherwise location-sensitive discovery stays client-driven.

- [ ] **Step 3: Implement Home shell + bounded location-sensitive sections**

`app/page.tsx` remains a Server Component for metadata/layout/stable category data, but renders a Client Component `HomeSections` that consumes `BrowseLocation`.

`HomeSections` performs parallel generated-client calls to `/catalog/items` for:

```text
Nearby PRODUCT STANDARD
Nearby SERVICE STANDARD
Nearby PRODUCT RESALE
```

Each section fetches a small first page with explicit `{lat,lng}` or `addressId`. Home is not an infinite combined feed and does not compute distance client-side.

- [ ] **Step 4: Implement search URL state and client query execution**

`/search` and `/category/[slug]` keep shareable filter text/type/category/sort in the URL, while the actual location-sensitive result query runs in `SearchResults` as a Client Component using Task 3 `BrowseLocation`.

Canonical query params:

```text
/search?q=tra+sua&type=PRODUCT&listingKind=STANDARD&categoryId=...&sort=...
```

Location coordinates/address preference are supplied from BrowseLocation; do not put a private full address in URL.

Filter UI only exposes backend-supported filters/sorts from the approved API. No generic query DSL.

- [ ] **Step 5: Implement listing cards**

Priority:
1. media;
2. name;
3. formatted price;
4. distance from backend;
5. shipping/delivery label from backend;
6. merchant/reputation summary.

Do not display tracked stock count unless API explicitly exposes it.

- [ ] **Step 6: Complete Listing Detail with concrete location continuity**

Search/card links include the backend-resolved location as a non-private identifier:

```text
/items/{itemId}?locationId={merchantLocationId}
```

When `locationId` is present, the detail page can use a Server Component read with that explicit public location. When absent, render a Client Component that resolves the item using the current `BrowseLocation`; do not silently choose a different client-side location.

One detail component supports Product, Service and Resale.

Rules:
- require a concrete backend-resolved MerchantLocation;
- selection uses `locationVariantId`;
- Service quantity UI hidden/fixed 1;
- Product quantity selector appears only for Product;
- merchant completion rate is display-only;
- warning comes from backend response if supplied;
- public contacts are safe links, never innerHTML.

- [ ] **Step 7: Implement Merchant Detail**

Render:
- public profile/media;
- public contacts;
- merchant reputation;
- active public locations;
- catalog query scoped to merchant.

No staff/member data.

- [ ] **Step 8: Redirect legacy explore route**

`/explore` uses `redirect("/search")`; do not maintain duplicate search state.

- [ ] **Step 9: Run and commit**

```bash
pnpm --filter @covie/web test -- public-discovery listing-detail
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
git add apps/web/app apps/web/features/discovery apps/web/tests/discovery
git commit -m "feat(web): complete public marketplace discovery"
```

---

## Task 5: Implement Auth Resume Intent and Listing Purchase Actions

**Files:**
- Create: `apps/web/features/purchase/purchase-actions.tsx`
- Create: `apps/web/features/purchase/purchase-intent.ts`
- Create: `apps/web/features/purchase/resume-purchase-intent.tsx`
- Modify: `apps/web/features/discovery/listing-detail.tsx`
- Modify: `apps/web/app/(auth)/login/page.tsx`
- Modify: `apps/web/app/(auth)/onboarding/password/page.tsx`
- Test: `apps/web/tests/purchase/purchase-actions.test.tsx`
- Test: `apps/web/tests/purchase/resume-intent.test.tsx`

**Interfaces:**
- Connects Guest Listing Detail to authenticated Add-to-Cart or Direct Checkout.
- Stores no authoritative price/stock/token.

- [ ] **Step 1: Write RED tests**

Required:
- anonymous Add-to-Cart redirects login and retains only locationVariantId/quantity intent;
- anonymous Buy Now resumes after auth;
- Service intent quantity always 1;
- intent expires after 15 minutes;
- malformed intent is discarded;
- no price, checkoutToken, auth token or full item snapshot is persisted.

- [ ] **Step 2: Define the only resumable intent**

```ts
type PurchaseIntent =
  | {
      version: 1;
      kind: "ADD_TO_CART";
      locationVariantId: string;
      quantity: number;
      createdAt: number;
    }
  | {
      version: 1;
      kind: "DIRECT_CHECKOUT";
      locationVariantId: string;
      quantity: number;
      createdAt: number;
    };
```

Store in `sessionStorage` key `covie:purchase-intent:v1`.

- [ ] **Step 3: Sanitize post-login navigation**

Use a fixed internal return path or validated relative path. Reject:
- `http://...`;
- `https://...`;
- `//evil.example`;
- backslash/encoded external tricks.

No open redirect.

- [ ] **Step 4: Implement Add-to-Cart**

Authenticated mutation calls existing `POST /api/v1/me/carts/items`.

On success:
- invalidate carts;
- show success;
- optionally navigate `/cart/[cartId]` when response includes cart ID.

409/422 show backend-code copy; never switch MerchantLocation silently.

- [ ] **Step 5: Implement Direct Checkout navigation**

Navigate with source identifiers only:

```text
/checkout?source=DIRECT&locationVariantId=<uuid>&quantity=<n>
```

Do not put checkout token in URL.

- [ ] **Step 6: Resume intent once**

After login/onboarding:
- read and validate age;
- remove intent before executing;
- if add-cart, perform mutation;
- if direct checkout, route to checkout;
- on backend failure, show normal error and do not loop.

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- purchase-actions resume-intent
pnpm --filter @covie/web typecheck
git add apps/web/features/purchase apps/web/features/discovery/listing-detail.tsx apps/web/app/'(auth)' apps/web/tests/purchase
git commit -m "feat(web): resume authenticated purchase intents"
```

---

## Task 6: Complete Login/Register, Account and Saved Address UX

**Files:**
- Create: `apps/web/app/(auth)/register/page.tsx`
- Modify: `apps/web/features/auth/login-form.tsx`
- Modify: `apps/web/features/auth/password-form.tsx`
- Modify: `apps/web/app/(buyer)/me/page.tsx`
- Modify: `apps/web/app/(buyer)/me/addresses/page.tsx`
- Create: `apps/web/features/profile/address-form.tsx`
- Create: `apps/web/features/profile/address-list.tsx`
- Create: `apps/web/features/profile/profile-overview.tsx`
- Test: `apps/web/tests/auth/register.test.tsx`
- Test: `apps/web/tests/profile/address-form.test.tsx`

**Interfaces:**
- Preserves Google-only first registration and email/password subsequent login.
- Produces P0 Address UI for Checkout.

- [ ] **Step 1: Write auth/address RED tests**

Assert:
- Register has Google CTA and no direct email/password signup form;
- Login supports Google + existing CoVie password;
- Forgot password starts Google recovery;
- address form uses backend field errors;
- default address is visibly marked;
- delete requires confirmation;
- private address data is never cached in service worker.

- [ ] **Step 2: Implement `/register` truthfully**

Copy:

```text
Tạo tài khoản CoVie
Bước đầu tiên dùng Google để xác minh danh tính.
Sau đó bạn sẽ tạo mật khẩu CoVie để lần sau có thể đăng nhập bằng Google hoặc email/mật khẩu.
```

Button uses Plan 02 Google start endpoint. Do not add email signup.

- [ ] **Step 3: Upgrade forms to RHF + Zod**

Zod handles immediate UX constraints such as required/nonempty/basic number ranges. On submit, backend remains authoritative.

Use `apply-field-errors` for backend `fields`.

Do not duplicate password hashing/rules beyond client UX.

- [ ] **Step 4: Build Account overview**

Links:
- addresses;
- security sessions;
- notifications;
- Seller Center;
- logout.

Show current account data from `/me`, no auth internals.

- [ ] **Step 5: Build Address create/edit**

Fields use existing generated request:
- label;
- recipient name;
- phone;
- address text;
- latitude/longitude;
- pin source;
- note;
- default.

Map/GPS pin editing can remain simple coordinate/manual selection in V1; do not add paid geocoder dependency in Plan 09.

- [ ] **Step 6: Enforce personal no-store**

Server address page uses `createServerReadClient()`.

Client Query keys are invalidated after mutation; service worker never caches.

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- register address-form
pnpm --filter @covie/web typecheck
git add apps/web/app/'(auth)' apps/web/app/'(buyer)'/me apps/web/features/auth apps/web/features/profile apps/web/tests/auth apps/web/tests/profile
git commit -m "feat(web): complete account and address ux"
```

---

## Task 7: Complete Multi-Cart Overview and Cart Detail

**Files:**
- Modify: `apps/web/features/cart/cart-api.ts`
- Modify: `apps/web/features/cart/cart-store.ts`
- Modify: `apps/web/features/cart/cart-list.tsx`
- Modify: `apps/web/features/cart/cart-item-row.tsx`
- Create: `apps/web/features/cart/cart-detail.tsx`
- Create: `apps/web/features/cart/cart-summary.tsx`
- Modify: `apps/web/app/(buyer)/cart/page.tsx`
- Create: `apps/web/app/(buyer)/cart/[cartId]/page.tsx`
- Test: `apps/web/tests/cart/multi-cart.test.tsx`
- Test: `apps/web/tests/cart/cart-detail.test.tsx`

**Interfaces:**
- One cart remains one `(user, MerchantLocation, type)`.
- Server state moves to TanStack Query; `cart-store.ts` may retain only ephemeral UI intent if needed.

- [ ] **Step 1: Write cart RED tests**

Cover:
- multiple carts render separately by merchant location/type;
- Product/Service never visually merge;
- Service has no quantity +/- controls;
- Product quantity mutation sends `If-Match`;
- stale version -> refetch and message;
- remove item updates only affected cart;
- no cart selected -> no checkout;
- subtotal labeled as current/display subtotal, not locked total.

- [ ] **Step 2: Remove authoritative cart duplication from client store**

If `cart-store.ts` currently stores backend cart/item snapshots, delete that state or reduce it to UI-only selections such as currently expanded card.

TanStack Query cache is not source of truth either; refetch from backend after mutations.

- [ ] **Step 3: Implement cart queries**

Use:
- `GET /me/carts`;
- `GET /me/carts/{cartId}`.

Never calculate shipping fee in Cart.

- [ ] **Step 4: Implement quantity/update/delete mutations**

Send backend version/If-Match exactly as generated wrapper requires.

On success:
- update via refetch/invalidation;
- do not manually increment local version.

On 409:
- discard optimistic visual value;
- refetch;
- tell user data changed.

- [ ] **Step 5: Build cart detail CTA**

`Tiếp tục đặt hàng` navigates:

```text
/checkout?source=CART&cartId=<cartId>
```

No multi-cart combined checkout.

- [ ] **Step 6: Empty state**

If all carts empty:

```text
Giỏ hàng của bạn đang trống.
[Khám phá gần bạn]
```

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- multi-cart cart-detail
pnpm --filter @covie/web typecheck
git add apps/web/features/cart apps/web/app/'(buyer)'/cart apps/web/tests/cart
git commit -m "feat(web): complete multi cart experience"
```

---

## Task 8: Complete Unified Checkout Preview → Commit UX

**Files:**
- Modify: `apps/web/features/checkout/checkout-api.ts`
- Modify: `apps/web/features/checkout/checkout-state.ts`
- Modify: `apps/web/features/checkout/checkout-preview.tsx`
- Modify: `apps/web/features/checkout/checkout-change-dialog.tsx`
- Create: `apps/web/features/checkout/checkout-form.tsx`
- Create: `apps/web/features/checkout/checkout-source.ts`
- Create: `apps/web/features/checkout/checkout-pricing.tsx`
- Modify: `apps/web/app/(buyer)/checkout/page.tsx`
- Modify: `apps/web/app/(buyer)/checkout/success/[orderId]/page.tsx`
- Test: `apps/web/tests/checkout/checkout-flow.test.tsx`
- Test: `apps/web/tests/checkout/checkout-changed.test.tsx`

**Interfaces:**
- One UI handles `CART`, `DIRECT`, `NEED_REQUEST`.
- Preview token lives in component memory only.

- [ ] **Step 1: Write Checkout RED tests**

Must cover:
- CART source from `cartId`;
- DIRECT source from `locationVariantId`;
- NEED_REQUEST source from Need identifiers;
- Preview is called before Commit;
- no checkout token in URL/storage;
- changing address/fulfillment/payment invalidates prior Preview and requests a new one;
- `CHECKOUT_CHANGED` never auto-commits changed values;
- negotiated shipping renders null fee/total correctly;
- Service quantity always one;
- double click Commit uses same idempotency key and triggers one logical mutation.

- [ ] **Step 2: Parse source safely**

`checkout-source.ts` validates URL params into:

```ts
type CheckoutUiSource =
  | { type: "CART"; cartId: string }
  | { type: "DIRECT"; locationVariantId: string; quantity: number }
  | {
      type: "NEED_REQUEST";
      needRequestId: string;
      needResponseItemId: string;
      quantity: number;
    };
```

Invalid source -> safe error with links back to cart/Need, no API mutation.

- [ ] **Step 3: Keep only ephemeral checkout state**

`checkout-state.ts` may hold:
- chosen address;
- fulfillment;
- payment method;
- latest Preview;
- raw checkoutToken;
- one commit idempotency key.

It must not persist to localStorage/sessionStorage.

- [ ] **Step 4: Preview on explicit user-confirmable configuration**

Flow:

```text
parse source
-> choose address/fulfillment/payment
-> POST /checkouts/preview
-> display exact returned items/pricing/coverage/shipping
-> user reviews
-> Commit enabled
```

Do not calculate shipping/minimum order client-side.

- [ ] **Step 5: Render pricing semantics**

Known fixed shipping:

```text
Tiền hàng
Phí giao
Tổng
```

Negotiated:

```text
Tiền hàng: <knownTotal/subtotal>
Phí giao: Thỏa thuận với người bán
Tạm tính trên CoVie: <knownTotal>
Tổng cuối cùng: không hiển thị số giả
```

- [ ] **Step 6: Commit**

On first commit intent generate `crypto.randomUUID()` and retain until success/final failure/user changes Preview.

Call commit with current token.

On success:
- invalidate source cart if CART;
- invalidate Orders/notifications;
- navigate success page.

- [ ] **Step 7: Handle `CHECKOUT_CHANGED`**

Display comparison details from backend if available.

Primary CTA:
`Kiểm tra giá/thông tin mới`.

That CTA discards token and performs new Preview. Never silently accept price/availability changes.

- [ ] **Step 8: Run and commit**

```bash
pnpm --filter @covie/web test -- checkout-flow checkout-changed
pnpm --filter @covie/web typecheck
git add apps/web/features/checkout apps/web/app/'(buyer)'/checkout apps/web/tests/checkout
git commit -m "feat(web): complete preview commit checkout ux"
```

---

## Task 9: Complete Buyer Orders with Backend-driven `allowedActions`

**Files:**
- Modify: `apps/web/features/orders/order-api.ts`
- Modify: `apps/web/features/orders/order-actions.tsx`
- Modify: `apps/web/features/orders/order-status.tsx`
- Modify: `apps/web/features/orders/order-timeline.tsx`
- Modify: `apps/web/features/orders/order-detail.tsx`
- Create: `apps/web/features/orders/order-list.tsx`
- Create: `apps/web/features/orders/order-action-dialog.tsx`
- Create: `apps/web/features/orders/order-contact.tsx`
- Modify: `apps/web/app/(buyer)/orders/page.tsx`
- Modify: `apps/web/app/(buyer)/orders/[orderId]/page.tsx`
- Test: `apps/web/tests/orders/buyer-order-actions.test.tsx`
- Test: `apps/web/tests/orders/order-detail.test.tsx`

**Interfaces:**
- Consumes canonical `GET /orders/{id}` and `allowedActions`.
- Produces one state-driven Order Detail, not one page per status.

- [ ] **Step 1: Write action-matrix RED tests based on allowedActions, not status**

Fixtures intentionally include combinations where status alone would mislead.

Assert:
- action present in `allowedActions` -> supported CTA rendered;
- action absent -> CTA not rendered even if status historically allowed it;
- unknown future action -> no crash and no unsafe command;
- completed confirmation has irreversible warning;
- issue CTA appears only when backend exposes it.

- [ ] **Step 2: Define presentation-only action map**

Map known `OrderAction` to:
- label;
- visual priority;
- dialog requirements;
- command function.

Example:

```ts
const actionUi = {
  CANCEL: { label: "Hủy đơn", confirm: true },
  REQUEST_CANCELLATION: { label: "Yêu cầu hủy", reason: true },
  CONFIRM_RECEIVED: { label: "Đã nhận hàng", irreversible: true },
  CONFIRM_COMPLETED: { label: "Dịch vụ đã hoàn thành", irreversible: true },
  REPORT_DELIVERY_ISSUE: { label: "Chưa nhận được hàng", confirm: true },
  CONTEST_NO_SHOW: { label: "Seller báo sai", confirm: true },
} as const;
```

This maps presentation to command; it does not define state transitions.

- [ ] **Step 3: Implement the approved chronological cursor list**

Plan 05 exposes:

```http
GET /api/v1/me/orders?limit=20&cursor=...
```

It does **not** expose a status filter in V1. Therefore `/orders` renders one newest-first cursor list with status chips and load-more.

Do not implement Active/Completed/Cancelled tabs by filtering only the currently loaded page; that would present an incomplete result set. A future server-side status filter may add those tabs additively.

- [ ] **Step 4: Implement Order Detail**

Sections:
- status copy;
- timeline;
- merchant/contact;
- fulfillment/location snapshot;
- items;
- payment/shipping snapshot;
- reputation context;
- action bar.

Order snapshots are displayed as historical transaction data even if catalog/merchant later changed.

- [ ] **Step 5: Implement command mutation lifecycle**

For every command:
- generate/reuse idempotency key for one dialog intent;
- disable button while in-flight;
- success -> invalidate Order/list/notifications;
- 409 -> refetch Order and explain action no longer available;
- 403 -> show action-not-allowed copy;
- 404 -> safe not-found.

- [ ] **Step 6: External contact safety**

Render only `https:`/approved schemes or `tel:` from backend fields. Use normal anchors with `rel="noopener noreferrer"` when opening new tabs.

No internal chat UI.

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- buyer-order-actions order-detail
pnpm --filter @covie/web typecheck
git add apps/web/features/orders apps/web/app/'(buyer)'/orders apps/web/tests/orders
git commit -m "feat(web): complete backend driven buyer order ux"
```

---

## Task 10: Complete Seller Center Shell, Merchant Switcher and Actionable Dashboard

**Files:**
- Modify: `apps/web/app/(seller)/seller/page.tsx`
- Modify: `apps/web/app/(seller)/seller/[merchantId]/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/layout.tsx`
- Create: `apps/web/features/seller/merchant-switcher.tsx`
- Create: `apps/web/features/seller/seller-dashboard.tsx`
- Create: `apps/web/features/seller/seller-nav.tsx`
- Create: `apps/web/features/seller/seller-context.ts`
- Test: `apps/web/tests/seller/seller-shell.test.tsx`
- Test: `apps/web/tests/seller/seller-dashboard.test.tsx`

**Interfaces:**
- Consumes `/me/merchants`, seller Order/Need/catalog/location APIs.
- Does not invent analytics not supported by V1.

- [ ] **Step 1: Write seller shell RED tests**

Cover:
- one user can switch among multiple MerchantProfiles;
- selected merchant is encoded in route, not hidden global server session;
- inaccessible merchant -> 404/redirect to seller chooser;
- dashboard shows actionable order/Need sections;
- no fake revenue chart;
- STAFF sees only backend-authorized data.

- [ ] **Step 2: Seller entry page**

`/seller`:
- fetch `/me/merchants`;
- zero merchants -> CTA “Bán hàng trên CoVie” / create merchant;
- one -> navigate its dashboard;
- many -> switcher.

Do not create a second seller account.

- [ ] **Step 3: Merchant route context**

Route `[merchantId]` is the current Seller Center merchant.

Client context may store merchant display metadata only; authorization remains backend request-by-request.

- [ ] **Step 4: Build actionable dashboard**

Without introducing an aggregate analytics endpoint, use bounded existing queries:

```text
Recent Orders -> first page of /merchants/{merchantId}/orders
Matching Needs -> first page of /merchants/{merchantId}/need-requests
Locations -> existing merchant locations read
Moderation notices -> existing notification/moderation state
```

For recent Orders, render each backend status and link to the canonical Order detail where `allowedActions` provides the actionable controls. Do not label the first page as an exhaustive “pending count” or “active count”; the cursor API has no total/status filter.

- [ ] **Step 5: Responsive seller nav**

Desktop sidebar/tablet nav/mobile compact tab row.

Keep core order actions reachable on phone.

- [ ] **Step 6: Run and commit**

```bash
pnpm --filter @covie/web test -- seller-shell seller-dashboard
pnpm --filter @covie/web typecheck
git add apps/web/app/'(seller)' apps/web/features/seller apps/web/tests/seller
git commit -m "feat(web): complete seller center shell"
```

---

## Task 11: Complete Merchant Location, Coverage, Shipping and Contact Configuration

**Files:**
- Modify: `apps/web/features/merchant/location-form.tsx`
- Modify: `apps/web/app/(seller)/seller/[merchantId]/locations/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/locations/[locationId]/page.tsx`
- Modify: `apps/web/app/(seller)/seller/[merchantId]/locations/[locationId]/delivery/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/locations/[locationId]/shipping/page.tsx`
- Create: `apps/web/features/seller/location-list.tsx`
- Create: `apps/web/features/seller/delivery-coverage-form.tsx`
- Create: `apps/web/features/seller/shipping-policy-form.tsx`
- Create: `apps/web/features/seller/contact-settings-form.tsx`
- Test: `apps/web/tests/seller/location-config.test.tsx`
- Test: `apps/web/tests/seller/shipping-policy.test.tsx`

**Interfaces:**
- Uses MerchantLocation as operational boundary.
- Uses backend coverage/shipping definitions without client evaluation.

- [ ] **Step 1: Write configuration RED tests**

Must prove:
- STAFF cannot access unassigned location UI after backend 404;
- location edit uses If-Match;
- radius/selected-area coverage round-trips;
- FREE/FIXED/NEGOTIATED/CONDITIONAL shipping forms produce generated request shape;
- ANY/ALL only appears for CONDITIONAL;
- negotiated shipping never asks for fee=0;
- Product minimum delivery order does not imply pickup minimum;
- Contact QR is labeled “QR liên hệ”, never payment QR.

- [ ] **Step 2: Build Location list/edit**

Render:
- name/address;
- status;
- pickup/service flags;
- timezone;
- coverage summary.

Do not merge two locations because addresses match.

- [ ] **Step 3: Coverage form**

Supports:
- RADIUS;
- SELECTED_AREAS.

If backend contract represents combined rules as multiple coverage records, UI creates/edits those records rather than inventing a third storage mode.

- [ ] **Step 4: Shipping form**

Use RHF/Zod for structural UX; server validates business rule.

Conditional rule editor creates supported rule rows and operator.

Money inputs parse integer VND minor units; no floating point.

- [ ] **Step 5: Contacts**

Use the exact Plan 03 contact routes:

```http
GET    /api/v1/merchants/{merchantId}/contacts
POST   /api/v1/merchants/{merchantId}/contacts
PATCH  /api/v1/merchants/{merchantId}/contacts/{contactId}
DELETE /api/v1/merchants/{merchantId}/contacts/{contactId}
```

`MerchantContactRequest.merchantLocationId` scopes an override to one location. `ZALO_QR` uses `qrMediaId` with the existing contact-media purpose; link/phone contact types use their generated request fields.

No bank account/QR/payment fields.

- [ ] **Step 6: Conflict handling**

Every editable versioned resource:
- sends If-Match;
- on 409 refetches;
- preserves unsaved user text separately only when safe;
- requires user to reapply against latest version.

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- location-config shipping-policy
pnpm --filter @covie/web typecheck
git add apps/web/app/'(seller)'/seller/'[merchantId]'/locations apps/web/features/merchant apps/web/features/seller apps/web/tests/seller
git commit -m "feat(web): complete seller location and shipping configuration"
```

---

## Task 12: Complete Product/Service/Resale Catalog CRUD, Media and Inventory UX

**Files:**
- Modify: `apps/web/app/(seller)/seller/[merchantId]/catalog/page.tsx`
- Modify: `apps/web/app/(seller)/seller/[merchantId]/catalog/new/page.tsx`
- Create: `apps/web/app/(seller)/seller/[merchantId]/catalog/[itemId]/page.tsx`
- Modify: `apps/web/app/(seller)/seller/[merchantId]/inventory/page.tsx`
- Modify: `apps/web/features/media/direct-image-upload.ts`
- Create: `apps/web/features/catalog/catalog-list.tsx`
- Create: `apps/web/features/catalog/catalog-editor.tsx`
- Create: `apps/web/features/catalog/variant-editor.tsx`
- Create: `apps/web/features/catalog/location-offer-editor.tsx`
- Create: `apps/web/features/catalog/catalog-lifecycle-actions.tsx`
- Create: `apps/web/features/inventory/inventory-table.tsx`
- Test: `apps/web/tests/catalog/catalog-editor.test.tsx`
- Test: `apps/web/tests/catalog/catalog-lifecycle.test.tsx`
- Test: `apps/web/tests/inventory/inventory-ui.test.tsx`

**Interfaces:**
- Aggregate create uses Plan 03 `clientKey` contract.
- Edit uses granular PATCH/If-Match.
- LocationVariant remains authoritative price/availability offer.

- [ ] **Step 1: Write catalog RED tests**

Cover:
- Product/Service/Resale selector maps to PRODUCT/SERVICE + listingKind;
- no-options item still creates hidden/default variant through backend aggregate contract, not fake client SKU;
- Service variant has exact price and no inventory fields;
- Product location offer owns price;
- media upload uses presigned direct upload then complete;
- publish CTA follows returned moderation state;
- PENDING moderation cannot be presented as publicly active;
- version conflict refetches.

- [ ] **Step 2: Build aggregate create wizard**

Wizard sections:
1. listing type/category/basic fields;
2. options/variants;
3. MerchantLocation offers/prices;
4. media;
5. review + create DRAFT.

Use stable clientKeys within one submit payload.

Do not save partial pseudo-entities to browser storage.

- [ ] **Step 3: Build granular editor**

After creation:
- item PATCH;
- variant PATCH;
- location offer PATCH;
- lifecycle commands publish/pause/activate/archive.

Each versioned edit uses If-Match.

- [ ] **Step 4: Direct media upload UX**

Flow:

```text
POST /media/uploads
-> PUT/POST directly to presigned storage target
-> POST /media/{id}/complete
-> attach mediaId to catalog
```

Do not upload bytes through Spring.

Progress/error UI may retry storage upload only while presigned contract remains valid; completion is idempotent.

- [ ] **Step 5: Inventory UI**

Only Product TRACKED offers show stock controls.

Display:
- stockOnHand;
- reservedQuantity;
- availableQuantity.

Set-stock submits absolute physical count + reason/note.

On `INVENTORY_BELOW_RESERVED_QUANTITY`, show reserved count and require a valid higher value. Never mutate displayed balance optimistically.

- [ ] **Step 6: Run and commit**

```bash
pnpm --filter @covie/web test -- catalog-editor catalog-lifecycle inventory-ui
pnpm --filter @covie/web typecheck
git add apps/web/app/'(seller)'/seller/'[merchantId]'/catalog apps/web/app/'(seller)'/seller/'[merchantId]'/inventory apps/web/features/catalog apps/web/features/media apps/web/features/inventory apps/web/tests/catalog apps/web/tests/inventory
git commit -m "feat(web): complete seller catalog and inventory ux"
```

---

## Task 13: Complete Seller Incoming Order and Need Response Workflows

**Files:**
- Modify: `apps/web/app/(seller)/seller/[merchantId]/orders/page.tsx`
- Modify: `apps/web/app/(seller)/seller/[merchantId]/orders/[orderId]/page.tsx`
- Modify: `apps/web/features/orders/order-actions.tsx`
- Modify: `apps/web/features/orders/order-detail.tsx`
- Modify: `apps/web/app/(seller)/seller/[merchantId]/needs/page.tsx`
- Modify: `apps/web/features/needs/merchant-need-list.tsx`
- Modify: `apps/web/features/needs/merchant-need-response-form.tsx`
- Test: `apps/web/tests/orders/seller-order-actions.test.tsx`
- Test: `apps/web/tests/needs/seller-need-workflow.test.tsx`

**Interfaces:**
- Seller Order actions use same canonical `/orders/{id}/...` commands.
- Need seller flow preserves location scope and response constraints from backend.

- [ ] **Step 1: Write seller action RED tests**

Order:
- Confirm/Reject only when corresponding backend allowedAction exists;
- reject requires reason;
- seller post-confirm cancel requires reason;
- delivery/pickup/service CTAs differ only from backend allowedActions/fulfillment DTO;
- buyer completion-rate warning renders from backend reputation context;
- no private buyer history is rendered.

Need:
- selected MerchantLocation is explicit;
- safe area/distance only;
- exact buyer coordinates/address never rendered;
- response max uses backend response constraint;
- edit sends If-Match;
- withdraw updates list;
- 409 refetches.

- [ ] **Step 2: Reuse one action infrastructure**

`order-actions.tsx` accepts actor view but still renders exact backend `allowedActions`.

Do not create a seller-specific transition table.

- [ ] **Step 3: Seller Order list**

Use the exact Plan 05 route:

```http
GET /api/v1/merchants/{merchantId}/orders?limit=20&cursor=...&locationId=...
```

Render newest-first cursor rows with status chips and optional explicit `locationId` selector. V1 does not add a client-only status filter that could hide unloaded Orders. Authorization/location scope remains backend-owned.

- [ ] **Step 4: Need response form**

Seller chooses only existing LocationVariants returned/loaded for the selected location.

No arbitrary offer price field.

The approved `CreateNeedResponseRequest` / `ReplaceNeedResponseRequest` supports the response `note`; that optional note is the only response free text. No chat/comment thread is added.

- [ ] **Step 5: Run and commit**

```bash
pnpm --filter @covie/web test -- seller-order-actions seller-need-workflow
pnpm --filter @covie/web typecheck
git add apps/web/app/'(seller)' apps/web/features/orders apps/web/features/needs apps/web/tests/orders apps/web/tests/needs
git commit -m "feat(web): complete seller order and need workflows"
```

---

## Task 14: Complete Buyer Need Flow and NEED_REQUEST Direct Checkout

**Files:**
- Modify: `apps/web/features/needs/need-api.ts`
- Modify: `apps/web/features/needs/need-create-form.tsx`
- Modify: `apps/web/features/needs/need-list.tsx`
- Modify: `apps/web/features/needs/need-detail.tsx`
- Modify: `apps/web/features/needs/need-response-list.tsx`
- Modify: `apps/web/app/(buyer)/needs/page.tsx`
- Modify: `apps/web/app/(buyer)/needs/new/page.tsx`
- Modify: `apps/web/app/(buyer)/needs/[needId]/page.tsx`
- Test: `apps/web/tests/needs/buyer-need-workflow.test.tsx`

**Interfaces:**
- Need status and matching remain backend-owned.
- Response item purchase goes to unified Checkout, never a temporary Cart unless buyer explicitly uses normal catalog cart later.

- [ ] **Step 1: Write buyer Need RED tests**

Cover:
- create requires Product/Service + category + location + radius + expiry;
- own Need can expose own saved location summary;
- ACTIVE shows cancel;
- FULFILLED/EXPIRED/CANCELLED hide mutation;
- responses display current offer price as current/not locked;
- unavailable response disables checkout;
- clicking available response routes source NEED_REQUEST;
- fulfilled occurs only after confirmed Order refetch, not immediately on checkout success;
- no response empty state shows expiry countdown.

- [ ] **Step 2: Upgrade create form with RHF/Zod**

Do not add unsupported `quantity`/target-price fields unless generated CreateNeedRequest contains them.

The approved API contract is authoritative over older IA wireframe details.

- [ ] **Step 3: Countdown**

Render `expiresAt` relative to client time for display.

At zero:
- disable local action immediately for UX;
- refetch backend;
- do not mutate Need status client-side.

- [ ] **Step 4: Direct Checkout source**

Navigate:

```text
/checkout?source=NEED_REQUEST&needRequestId=<id>&needResponseItemId=<id>&quantity=<n>
```

Service quantity fixed 1.

- [ ] **Step 5: Run and commit**

```bash
pnpm --filter @covie/web test -- buyer-need-workflow
pnpm --filter @covie/web typecheck
git add apps/web/features/needs apps/web/app/'(buyer)'/needs apps/web/tests/needs
git commit -m "feat(web): complete buyer need response journey"
```

---

## Task 15: Add Backend Review Eligibility Read Contract and Complete Review/Reputation/Report UX

**Files:**
- Create: `apps/api/src/main/java/vn/covie/reviews/application/ReviewEligibilityService.java`
- Create: `apps/api/src/main/java/vn/covie/reviews/api/dto/ReviewEligibilityResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/reviews/application/ReviewService.java`
- Modify: `apps/api/src/main/java/vn/covie/reviews/persistence/ReviewRepository.java`
- Create: `apps/api/src/main/java/vn/covie/orders/api/dto/OrderItemResponse.java`
- Modify: `apps/api/src/main/java/vn/covie/orders/application/OrderMapper.java`
- Test: `apps/api/src/test/java/vn/covie/reviews/application/ReviewEligibilityServiceIntegrationTest.java`
- Test: `apps/api/src/test/java/vn/covie/openapi/Plan09OpenApiContractTest.java`
- Regenerate: `packages/api-client/openapi/openapi.json`
- Regenerate: `packages/api-client/src/generated/schema.ts`
- Modify: `apps/web/features/reviews/review-form.tsx`
- Modify: `apps/web/features/reviews/review-list.tsx`
- Create: `apps/web/features/reputation/reputation-summary.tsx`
- Create: `apps/web/features/reports/report-dialog.tsx`
- Create: `apps/web/features/reports/report-api.ts`
- Modify: `apps/web/features/orders/order-detail.tsx`
- Modify: `apps/web/features/discovery/listing-detail.tsx`
- Modify: `apps/web/features/discovery/merchant-detail.tsx`
- Test: `apps/web/tests/reviews/review-eligibility.test.tsx`
- Test: `apps/web/tests/reports/report-dialog.test.tsx`

**Interfaces:**
- Adds one V1-compatible optional read field. No schema migration.
- Review create and read eligibility share one authoritative service.

- [ ] **Step 1: Write backend eligibility RED tests**

Required:

```text
COMPLETED + enabled + no existing review -> eligible=true
COMPLETED + existing review -> eligible=false reason=ALREADY_REVIEWED + reviewId
COMPLETED + review.enabled=false -> eligible=false reason=REVIEW_CREATION_DISABLED
non-COMPLETED -> eligible=false reason=ORDER_NOT_COMPLETED
merchant/admin Order read does not expose buyer-only create eligibility as actionable
review create calls the same eligibility service and cannot disagree with read result
```

- [ ] **Step 2: Implement `ReviewEligibilityService`**

Contract:

```java
public record ReviewEligibilityResponse(
        boolean eligible,
        String reasonCode,
        UUID existingReviewId) {}
```

Service evaluates:
1. authorized buyer relation;
2. Order `COMPLETED`;
3. `ReviewPolicyProvider.creationEnabled()`;
4. existing Review by OrderItem.

`ReviewService.create...` calls a `requireEligible(...)` method from this service before insert. No duplicate formula.

- [ ] **Step 3: Normalize `OrderItemResponse` file and add buyer Review eligibility**

Plan 05 referenced `OrderItemResponse` inside `OrderDetailResponse` but did not assign that DTO an exact source file in its plan. Before changing the contract, normalize it to:

```text
apps/api/src/main/java/vn/covie/orders/api/dto/OrderItemResponse.java
```

If the Plan 05 implementation placed the record as a package-private/nested declaration, extract it to this file **without changing any existing fields or JSON names**. If it already exists at this exact path, modify it in place.

Then add optional:

```java
ReviewEligibilityResponse reviewEligibility
```

Order mapper bulk-loads Reviews for all Order item IDs to avoid one query per item. For SELLER/MERCHANT and ADMIN actor views, buyer-only creation eligibility is `null`; existing Review read data remains separately accessible.

- [ ] **Step 4: Regenerate OpenAPI client**

```bash
./infra/scripts/export-openapi.sh
pnpm --filter @covie/api-client generate
pnpm --filter @covie/api-client check
```

Inspect generated diff. Do not hand-edit `schema.ts`.

- [ ] **Step 5: Render Review CTA from eligibility**

Only `eligible=true` renders active form CTA.

`ALREADY_REVIEWED` may show “Đã đánh giá” and link/render existing review.

Disabled review system silently removes creation CTA; do not display it as a system failure.

- [ ] **Step 6: Render Reputation**

Buyer public/item/shop:
- merchant completion summary only.

Seller Order:
- buyer completion summary + backend warning.

No raw no-show/cancellation history.

- [ ] **Step 7: Add Report dialog**

Use `POST /api/v1/reports` with one stable idempotency key per submit.

Supported target buttons are shown only where target IDs are legitimately available:
- CatalogItem;
- Merchant;
- NeedRequest;
- Review;
- User in authorized seller Order context if API exposes buyer ID.

Client submits targetType/targetId/reasonCode/note only. No priority/assignee/action.

- [ ] **Step 8: Run and commit**

```bash
cd apps/api
./mvnw -Dtest=ReviewEligibilityServiceIntegrationTest,Plan09OpenApiContractTest test
cd ../..
pnpm --filter @covie/web test -- review-eligibility report-dialog
pnpm --filter @covie/web typecheck
git add apps/api/src/main/java/vn/covie/reviews apps/api/src/main/java/vn/covie/orders apps/api/src/test/java/vn/covie/reviews apps/api/src/test/java/vn/covie/openapi packages/api-client apps/web/features/reviews apps/web/features/reputation apps/web/features/reports apps/web/features/orders apps/web/features/discovery apps/web/tests/reviews apps/web/tests/reports
git commit -m "feat(web): expose review eligibility and trust ux"
```

---

## Task 16: Complete Notification Bell, Inbox Deep Links and SSE Query Invalidation

**Files:**
- Modify: `apps/web/features/notifications/notification-api.ts`
- Modify: `apps/web/features/notifications/notification-inbox.tsx`
- Modify: `apps/web/features/notifications/covie-event-stream.ts`
- Modify: `apps/web/features/notifications/notification-types.ts`
- Create: `apps/web/features/notifications/notification-deep-link.ts`
- Create: `apps/web/features/notifications/notification-bell.tsx`
- Modify: `apps/web/components/layout/app-header.tsx`
- Modify: `apps/web/app/(buyer)/me/notifications/page.tsx`
- Test: `apps/web/tests/notifications/deep-link.test.ts`
- Test: `apps/web/tests/notifications/query-invalidation.test.ts`

**Interfaces:**
- Notifications remain DB-backed.
- Deep links are derived locally from trusted notification type + typed entity IDs, never arbitrary payload URLs.

- [ ] **Step 1: Write deep-link RED tests**

Examples:

```text
ORDER_* buyer notification + orderId -> /orders/{id}
ORDER_CREATED seller recipient + merchantId/orderId -> /seller/{merchantId}/orders/{id}
NEED_RESPONSE_* + needRequestId -> /needs/{id}
NEED_MATCH_AVAILABLE + merchantId -> /seller/{merchantId}/needs
MODERATION_* seller + merchantId -> /seller/{merchantId}/catalog or notice surface
unknown/malformed -> /me/notifications
payload url="https://evil..." -> ignored
```

- [ ] **Step 2: Build notification bell**

Header can indicate unread if loaded notification page contains unread records.

Do not fabricate a global unread count when API does not return total.

- [ ] **Step 3: Mark-read and navigation order**

On click:
1. mark read mutation;
2. navigate even if mark-read already idempotently read;
3. invalidate notifications.

Mark-all-read refetches current page.

- [ ] **Step 4: Centralize SSE invalidation**

Map event types to Query invalidations:

```text
notification.created -> notifications
order.updated -> affected order + order lists
need-response.* -> need + needs list
need-request.fulfilled -> need + orders
review.created -> review lists/order item eligibility
moderation.updated -> seller catalog/admin moderation as relevant
sync-required -> all core personal queries
```

Never `setQueryData` from SSE business payload.

- [ ] **Step 5: Reconnect behavior**

Preserve Plan 07:
- close old EventSource first;
- auth refresh using cookies/CSRF;
- bounded reconnect delay;
- 401 stops stream;
- reconnect then REST refetch.

- [ ] **Step 6: Run and commit**

```bash
pnpm --filter @covie/web test -- deep-link query-invalidation covie-event-stream
pnpm --filter @covie/web typecheck
git add apps/web/features/notifications apps/web/components/layout/app-header.tsx apps/web/app/'(buyer)'/me/notifications apps/web/tests/notifications
git commit -m "feat(web): complete notification and sse navigation"
```

---

## Task 17: Complete Admin Control-plane UX without Adding Order Mutations

**Files:**
- Modify: `apps/web/app/(admin)/admin/layout.tsx`
- Modify: `apps/web/app/(admin)/admin/page.tsx`
- Modify: `apps/web/app/(admin)/admin/settings/page.tsx`
- Modify: `apps/web/app/(admin)/admin/moderation/page.tsx`
- Modify: `apps/web/app/(admin)/admin/moderation/[caseId]/page.tsx`
- Create: `apps/web/app/(admin)/admin/reports/page.tsx`
- Modify: `apps/web/app/(admin)/admin/users/page.tsx`
- Modify: `apps/web/app/(admin)/admin/merchants/page.tsx`
- Modify: `apps/web/app/(admin)/admin/orders/page.tsx`
- Create: `apps/web/app/(admin)/admin/audit/page.tsx`
- Modify: `apps/web/features/admin/admin-api.ts`
- Modify: `apps/web/features/admin/settings-editor.tsx`
- Modify: `apps/web/features/admin/moderation-actions.tsx`
- Modify: `apps/web/features/admin/admin-table.tsx`
- Create: `apps/web/features/admin/admin-dashboard.tsx`
- Create: `apps/web/features/admin/audit-table.tsx`
- Test: `apps/web/tests/admin/admin-navigation.test.tsx`
- Test: `apps/web/tests/admin/admin-order-readonly.test.tsx`
- Test: `apps/web/tests/admin/admin-settings.test.tsx`

**Interfaces:**
- Reuses Plan 08 admin APIs.
- `/admin/reports` reuses the Plan 08 moderation-case read API filtered to `caseType=USER_REPORT`; do not create a duplicate report adjudication system.

- [ ] **Step 1: Write Admin safety RED tests**

Assert:
- non-ADMIN does not render admin app;
- Admin Orders has no confirm/cancel/complete/status mutation;
- Settings only render allowlisted key/scope choices returned by backend;
- Reports view cannot choose internal priority/assignee;
- moderation unknown state disables unsafe action and refetches;
- suspend/restore always asks reason/confirmation.

- [ ] **Step 2: Build actionable dashboard**

Show:
- open moderation/report queue links;
- preapproval queue link;
- settings link;
- audit link.

No revenue/payment charts.

- [ ] **Step 3: Complete settings editor**

Render effective/default/source.

Existing override update -> If-Match.

New override -> create/upsert contract from generated API.

409 -> refetch before resubmit.

Never show secret/environment key input.

- [ ] **Step 4: Reports route**

Reuse moderation cases filtered `USER_REPORT`.

Show reporter-safe target summary and allowed moderation actions.

Do not introduce “resolve transaction dispute”.

- [ ] **Step 5: Users/Merchants safety actions**

Suspend/restore:
- one idempotency key per intent;
- reason required;
- refetch on result;
- user suspension session effect is backend-owned;
- merchant suspension copy warns it blocks new commerce but existing Orders remain.

- [ ] **Step 6: Read-only Admin Orders/Audit**

Order detail/timeline may be linked from rows if existing admin detail route is available; no command component is imported.

Audit table is read-only and redacts/does not render hidden secret fields.

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- admin-navigation admin-order-readonly admin-settings
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
git add apps/web/app/'(admin)' apps/web/features/admin apps/web/tests/admin
git commit -m "feat(web): complete admin control plane ux"
```

---

## Task 18: Normalize Loading/Empty/Error/Accessibility and Responsive State across P0 Screens

**Files:**
- Modify: `apps/web/components/ui/empty-state.tsx`
- Modify: `apps/web/components/ui/error-state.tsx`
- Modify: `apps/web/components/ui/loading-state.tsx`
- Create: `apps/web/components/ui/confirm-dialog.tsx`
- Create: `apps/web/components/ui/status-chip.tsx`
- Create: `apps/web/components/ui/warning-banner.tsx`
- Create: `apps/web/lib/format/money.ts`
- Create: `apps/web/lib/format/time.ts`
- Create: `apps/web/lib/format/status-copy.ts`
- Modify: `apps/web/features/discovery/listing-detail.tsx`
- Modify: `apps/web/features/cart/cart-detail.tsx`
- Modify: `apps/web/features/checkout/checkout-form.tsx`
- Modify: `apps/web/features/orders/order-detail.tsx`
- Modify: `apps/web/features/needs/need-detail.tsx`
- Modify: `apps/web/features/seller/seller-dashboard.tsx`
- Modify: `apps/web/features/admin/admin-table.tsx`
- Test: `apps/web/tests/ui/formatters.test.ts`
- Test: `apps/web/tests/ui/unknown-enum.test.tsx`
- Test: `apps/web/tests/ui/p0-accessibility.test.tsx`

**Interfaces:**
- Presentation-only normalization.
- Never makes backend authorization/business decisions.

- [ ] **Step 1: Write cross-screen RED tests**

Cover:
- unknown status/action/type safe fallback;
- no raw enum printed to users;
- search empty -> Need CTA;
- no delivery -> pickup option only if backend says pickup eligible;
- seller timeout/expired Order -> relevant backend state, not local forced status;
- review disabled -> no error banner;
- every destructive action uses confirm dialog;
- keyboard focus enters/closes dialog correctly;
- icon-only buttons have accessible labels.

- [ ] **Step 2: Implement money formatter**

Input:

```ts
type MoneyLike = { amount: number; currency: string };
```

Use `Intl.NumberFormat("vi-VN", { style: "currency", currency })` only for display.

Never parse formatted currency back into business arithmetic.

- [ ] **Step 3: Implement time/countdown formatter**

Use absolute ISO time from backend.

Countdown returns presentation states:
- `Còn 2 giờ`;
- `Sắp hết hạn`;
- `Đã tới hạn, đang cập nhật...`.

At deadline it triggers refetch callback; it never performs a state transition.

- [ ] **Step 4: Status copy with fallback**

Known statuses map to Vietnamese user copy.

Unknown -> `Trạng thái đã được cập nhật` and no inferred actions.

- [ ] **Step 5: Responsive check matrix**

Component tests and manual viewport gate cover:
- 360×800;
- 390×844;
- 768×1024;
- 1440×900.

Required:
- no horizontal overflow on consumer core pages;
- sticky CTA not hidden behind bottom nav/safe area;
- Seller catalog forms usable at tablet width;
- Admin table can scroll horizontally without clipping action menu.

- [ ] **Step 6: Basic accessibility gate**

Use semantic:
- `nav`;
- `main`;
- headings in order;
- form labels;
- error `aria-describedby`;
- dialogs;
- button names.

Do not rely on color alone for status/error.

- [ ] **Step 7: Run and commit**

```bash
pnpm --filter @covie/web test -- formatters unknown-enum p0-accessibility
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
git add apps/web/components apps/web/lib/format apps/web/app apps/web/features apps/web/tests/ui
git commit -m "feat(web): normalize responsive and accessible ui states"
```

---

## Task 19: Add Playwright E2E Harness and Production-safe Test Authentication Fixtures

**Files:**
- Modify: `apps/web/package.json`
- Modify: `pnpm-lock.yaml`
- Create: `apps/web/playwright.config.ts`
- Create: `apps/web/e2e/fixtures/test-users.ts`
- Create: `apps/web/e2e/fixtures/api-fixture.ts`
- Create: `apps/web/e2e/fixtures/auth-fixture.ts`
- Create: `apps/api/src/main/java/vn/covie/testsupport/E2eSupportProperties.java`
- Create: `apps/api/src/main/java/vn/covie/testsupport/E2eProfileSafetyConfiguration.java`
- Create: `apps/api/src/main/java/vn/covie/testsupport/E2eSecurityConfiguration.java`
- Create: `apps/api/src/main/java/vn/covie/testsupport/E2eFixtureUserService.java`
- Create: `apps/api/src/main/java/vn/covie/testsupport/E2eAuthController.java`
- Create: `apps/api/src/test/java/vn/covie/testsupport/E2eProfileSafetyTest.java`
- Create: `apps/api/src/test/java/vn/covie/testsupport/E2eAuthControllerTest.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Create: `apps/api/src/main/resources/application-e2e.yml`

**Interfaces:**
- Enables deterministic browser E2E without Google network calls.
- Test bypass cannot load in normal local/prod profile.

- [ ] **Step 1: Write backend safety RED tests**

Prove:
- `/api/v1/test-support/**` is 404/not mapped without `e2e` profile;
- `e2e` profile requires `COVIE_E2E_SUPPORT_ENABLED=true`;
- production profile + e2e support causes startup failure;
- fixture login only accepts fixed enum actors, not arbitrary userId/email;
- issued browser session uses normal auth cookie/session infrastructure.

- [ ] **Step 2: Add Playwright**

Run:

```bash
pnpm --filter @covie/web add -D @playwright/test@^1
pnpm --filter @covie/web exec playwright install chromium
```

Package script:

```json
{
  "scripts": {
    "test:e2e": "playwright test"
  }
}
```

CI browser installation may be optimized in Plan 10 but tests must run locally now.

- [ ] **Step 3: Define fixed fixture actors**

Only:

```text
BUYER
SELLER_OWNER
SELLER_STAFF
ADMIN
```

`E2eFixtureUserService` ensures deterministic fixture accounts by known fixture key under e2e profile. It may use application/repository services, but it must not accept arbitrary client identity.

- [ ] **Step 4: Isolate the exact E2E login route in an E2E-only SecurityFilterChain**

`E2eAuthController` must be guarded twice:

```java
@Profile("e2e")
@ConditionalOnProperty(
    name = "covie.e2e-support.enabled",
    havingValue = "true")
@RestController
@RequestMapping("/api/v1/test-support")
```

`application.yml` default:

```yaml
covie:
  e2e-support:
    enabled: false
```

`application-e2e.yml`:

```yaml
covie:
  e2e-support:
    enabled: ${COVIE_E2E_SUPPORT_ENABLED:false}
```

Create `E2eSecurityConfiguration` under the same `vn.covie.testsupport` package. It is itself guarded by `@Profile("e2e")` + the same `@ConditionalOnProperty` and defines a higher-priority chain:

```java
@Bean
@Order(0)
SecurityFilterChain e2eLoginChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/v1/test-support/login")
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/v1/test-support/login").permitAll()
            .anyRequest().denyAll())
        .csrf(csrf -> csrf.disable());
    return http.build();
}
```

This chain matches **only** the fixed login endpoint. Never use `/api/v1/test-support/**` as a wildcard matcher. The ordinary `AuthSecurityConfiguration` and `BrowserCsrfRequestMatcher` stay unchanged and authoritative for every production route.

`E2eProfileSafetyConfiguration` fails application startup when:

```text
covie.e2e-support.enabled=true
AND
active profiles contain prod
```

Tests must prove normal profiles have no mapped test-support controller/filter chain and the normal security configuration remains unchanged.

- [ ] **Step 5: Issue normal session cookies through existing auth primitives**

`POST /api/v1/test-support/login` accepts only the fixed fixture actor enum.

Implementation flow:

```text
require E2E support enabled
resolve fixed fixture actor
ensure deterministic fixture user/roles
InitialSessionIssuer.issue(userId, DeviceMetadata.from(request))
SecurityCookieService.writeAccessCookie(...)
SecurityCookieService.writeRefreshCookie(...)
invalidate old CSRF token with CsrfTokenLifecycleService
return safe fixture identity
```

Use the exact existing classes:

```text
vn.covie.auth.application.InitialSessionIssuer
vn.covie.auth.model.DeviceMetadata
vn.covie.auth.security.SecurityCookieService
vn.covie.auth.security.CsrfTokenLifecycleService
vn.covie.testsupport.E2eSecurityConfiguration
```

No raw access/refresh token is returned in JSON. The browser receives normal HttpOnly cookies just like ordinary login.

- [ ] **Step 6: Add Playwright auth fixture**

`auth-fixture.ts`:
- calls test-support login through browser request context;
- preserves Set-Cookie in browser context;
- navigates `/me` to assert session works.

No Google dependency.

- [ ] **Step 7: Add API seed helper using normal business APIs**

`api-fixture.ts` uses authenticated generated/raw HTTP calls to create merchant/location/catalog/stock/addresses as needed.

Prefer business APIs over direct SQL so E2E setup also exercises contracts.

- [ ] **Step 8: Configure Playwright**

Use:
- Chromium;
- baseURL `http://localhost:3000`;
- trace `retain-on-failure`;
- screenshot `only-on-failure`;
- video `retain-on-failure`;
- test retries `0` locally;
- serial mode for stateful marketplace journey project.

Do not record auth headers/body in custom logs.

- [ ] **Step 9: Run and commit harness**

```bash
cd apps/api
SPRING_PROFILES_ACTIVE=e2e COVIE_E2E_SUPPORT_ENABLED=true ./mvnw -Dtest=E2eProfileSafetyTest,E2eAuthControllerTest test
cd ../..
pnpm --filter @covie/web typecheck
git add apps/api/src/main/java/vn/covie/testsupport \
        apps/api/src/test/java/vn/covie/testsupport \
        apps/api/src/main/resources \
        apps/web/package.json pnpm-lock.yaml apps/web/playwright.config.ts apps/web/e2e/fixtures
git commit -m "test(e2e): add production safe browser fixtures"
```

---

## Task 20: Implement P0 Playwright Journeys and Run Plan 09 Acceptance Gate

**Files:**
- Create: `apps/web/e2e/guest-discovery.spec.ts`
- Create: `apps/web/e2e/buyer-product-order.spec.ts`
- Create: `apps/web/e2e/service-order.spec.ts`
- Create: `apps/web/e2e/need-flow.spec.ts`
- Create: `apps/web/e2e/seller-management.spec.ts`
- Create: `apps/web/e2e/admin-control-plane.spec.ts`
- Modify: `README.md`

**Interfaces:**
- Produces the release-quality functional gate before Plan 10 Hardening/Pilot.
- No production behavior is introduced by this task.
- `README.md` is modified only when the verified bootstrap/E2E commands differ from the current runbook; otherwise leave it untouched and do not create an empty documentation commit.

- [ ] **Step 1: Guest discovery E2E**

Scenario:

```text
anonymous /
-> choose browse coordinates
-> search
-> see Product + Service/Resale fixtures
-> open Product detail
-> click Add to cart
-> redirected/login required
```

Assertions:
- no `/me` required to browse;
- location context affects request/result;
- merchant/location shown;
- no raw enum.

- [ ] **Step 2: Buyer Product order E2E**

Using BUYER fixture:

```text
login fixture
-> saved address
-> Product listing at concrete location
-> Add cart
-> Multi-cart/detail
-> Checkout Preview
-> Commit
-> PENDING order
SELLER_OWNER fixture confirms
-> marks delivered
BUYER reload/SSE-refetch
-> confirms received
-> COMPLETED
-> Review CTA eligible
-> submit review
```

Assertions:
- one Order;
- no duplicate on double commit click;
- `allowedActions` drive CTAs;
- Review becomes non-eligible after create.

- [ ] **Step 3: Product pickup branch E2E**

Use Product fixture with pickup.

At least test:

```text
checkout pickup
-> seller confirms
-> buyer can confirm received directly from CONFIRMED
-> COMPLETED
```

No requirement to wait real no-show grace in browser E2E; backend Plan 05 covers timing correctness.

- [ ] **Step 4: Service order E2E**

```text
Service exact variant
-> quantity UI absent/fixed 1
-> checkout address
-> Provider confirms
-> external-contact buttons visible
-> Buyer can confirm completed directly from CONFIRMED
-> COMPLETED
```

No booking calendar appears.

- [ ] **Step 5: Need E2E**

```text
BUYER creates PRODUCT Need
SELLER_OWNER opens matching Need feed
-> chooses MerchantLocation
-> responds with existing LocationVariant
BUYER sees structured response
-> NEED_REQUEST Checkout
-> Order PENDING
Need remains ACTIVE
SELLER confirms Order
BUYER refetch
-> Need FULFILLED
```

Assertions:
- seller never sees exact buyer coordinates;
- no arbitrary response price field;
- fulfillment happens on confirmation, not creation.

- [ ] **Step 6: Seller management E2E**

```text
Seller Center
-> location list/edit
-> delivery coverage
-> shipping FIXED or CONDITIONAL
-> create DRAFT Product
-> upload test image through local MinIO presigned flow
-> publish
-> set TRACKED stock
```

Assert resulting public detail uses that location offer.

- [ ] **Step 7: Admin control-plane E2E**

Using ADMIN fixture:

```text
/admin
-> settings
-> change review.enabled false with version-safe mutation
-> buyer completed Order no longer shows eligible Review CTA after refetch
-> restore true
-> moderation/report queue accessible
-> Admin Orders contains no lifecycle buttons
```

Do not depend on arbitrary Order force mutation because it must not exist.

- [ ] **Step 8: Responsive smoke in Playwright**

Run guest/home, listing, cart, Order detail at:
- mobile 390×844;
- desktop 1440×900.

Assert no document horizontal overflow and primary CTA is visible.

- [ ] **Step 9: Run full frontend verification**

```bash
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web test
pnpm --filter @covie/web build
```

Expected: exit 0.

- [ ] **Step 10: Run generated contract drift gate**

```bash
./infra/scripts/export-openapi.sh
pnpm --filter @covie/api-client generate
pnpm --filter @covie/api-client check
git diff --exit-code -- packages/api-client/openapi/openapi.json packages/api-client/src/generated/schema.ts
```

Expected: exit 0 after intentional generated changes are committed.

- [ ] **Step 11: Run backend regression affected by Plan 09**

```bash
cd apps/api
./mvnw -Dtest='*Review*Test,*Order*Test,*Auth*Test,*Admin*Test' test
./mvnw verify
```

Expected: exit 0.

- [ ] **Step 12: Run Playwright**

Start local Postgres/PostGIS/Redis/MinIO, backend with e2e profile and web app, then:

```bash
pnpm --filter @covie/web test:e2e
```

Expected: all six P0 spec files pass with zero unexpected retries.

- [ ] **Step 13: Verify no schema migration was invented**

```bash
ls apps/api/src/main/resources/db/migration | sort
```

Expected highest applied business migration remains:

```text
V013__moderation_system_settings_admin.sql
```

unless implementation evidence forced a real migration and that migration was separately reviewed. Do not create `V014__web_completion.sql`.

Therefore the next available Flyway version for Plan 10 remains:

```text
V014
```

- [ ] **Step 14: Verify P0 screen coverage checklist**

```text
[ ] Home
[ ] Location chooser
[ ] Search/results/category
[ ] Product detail
[ ] Service detail
[ ] Resale detail
[ ] Merchant detail
[ ] Login/Register/Google onboarding
[ ] Multi-cart + cart detail
[ ] Saved address
[ ] Checkout Preview/Commit
[ ] Buyer Orders + state-driven Order Detail
[ ] Seller Center
[ ] Seller locations/coverage/shipping
[ ] Product/Service/Resale CRUD
[ ] Inventory
[ ] Seller incoming Orders
[ ] Buyer Need create/responses
[ ] Seller Need match/response
[ ] Notification inbox/deep links/SSE refetch
[ ] Review eligibility/form
[ ] Reputation display
[ ] Report
[ ] Admin settings
[ ] Admin moderation/report minimum
[ ] Admin read-only Order explorer
[ ] Mobile consumer responsive gate
[ ] Desktop Admin gate
```

- [ ] **Step 15: Commit acceptance suite**

```bash
git add apps/web/e2e apps/web/playwright.config.ts README.md
git commit -m "test(web): cover covie p0 marketplace journeys"
```

Do not create an empty README commit when README did not change.

---

# Frontend Data Ownership Matrix

| Data | Frontend role | Authoritative source |
|---|---|---|
| Browse coordinates/address selection | stores user preference | user/browser + backend validation |
| Catalog results | render/refetch | Spring REST |
| Offer price | render only | LocationVariant via Spring REST |
| Cart | query/refetch | PostgreSQL/Spring |
| Checkout Preview | temporary render | Spring Checkout Engine |
| Checkout token | ephemeral memory | server-side checkout session + opaque client token |
| Order state | render/refetch | Spring Order state machine |
| `allowedActions` | render supported CTAs | Spring authorization/state machine |
| Need status/matches | render/refetch | Spring Need domain |
| Review eligibility | render | Spring ReviewEligibilityService |
| Rating/Reputation | render | Spring projections/source rules |
| Notification history | render/refetch | PostgreSQL notification inbox |
| SSE | invalidation signal only | non-authoritative |
| Admin settings | edit through API | SystemSetting |
| Moderation state | render/command through API | Moderation domain |

---

# Error UX Matrix

| HTTP/code | UI behavior |
|---|---|
| `401 AUTHENTICATION_REQUIRED` | redirect/login with safe return intent |
| `403 ACCESS_DENIED` | show permission message; do not retry |
| `403 CSRF_INVALID` | invalidate CSRF cache; ask user to retry intent; do not auto-replay unsafe mutation |
| `404 RESOURCE_NOT_FOUND` | safe unavailable/not-found state |
| `409 RESOURCE_VERSION_CONFLICT` | refetch latest resource and require reapply |
| `409 ORDER_STATE_CONFLICT` | refetch Order; allowedActions may change |
| `409 CHECKOUT_CHANGED` | discard Preview token and show new Preview |
| `409 INVENTORY_BELOW_RESERVED_QUANTITY` | show current reserved constraint from backend if supplied |
| `422 VALIDATION_FAILED` | map backend `fields` to form |
| `422 REVIEW_CREATION_DISABLED` | hide/disable Review creation after refetch |
| `429 RATE_LIMIT_EXCEEDED` | respect/display Retry-After when available |
| `5xx` | stable retry-safe read UI + requestId; do not auto-retry mutations |

Never branch on English/Vietnamese backend `message`.

---

# Query Invalidation Matrix

```text
Cart mutation
-> carts + affected cart

Checkout commit
-> carts if CART
-> buyer orders
-> notifications

Order command
-> order detail
-> buyer/seller order lists
-> notifications

Need create/cancel/response mutation
-> need detail/list
-> seller Need list where relevant
-> notifications

Review create
-> order detail reviewEligibility
-> catalog review list
-> merchant review list
-> notification list

Admin setting mutation
-> admin settings
-> any open UI whose next mutation/read depends on that setting is refetched on navigation/SSE signal

Moderation mutation
-> moderation case/list
-> target public/seller query
-> notifications
```

Do not globally invalidate every query after every mutation.

---

# UI Security/Privacy Checklist

```text
[ ] no access/refresh token in browser storage
[ ] no checkout token in URL/storage
[ ] no arbitrary notification URL navigation
[ ] no raw buyer geo in seller Need UI
[ ] no buyer transaction history in reputation UI
[ ] no hidden moderation note in public UI
[ ] no payment QR/account UI
[ ] no unsafe rich HTML from notification/review/note payload
[ ] external contact links use safe schemes
[ ] auth return URLs are relative/sanitized
[ ] service worker never caches /api/v1 or private HTML
[ ] Admin role is UX-checked but backend remains authoritative
[ ] seller merchant/location permission is never inferred only from route
[ ] 404 private-resource semantics are preserved
```

---

# Plan 09 Non-goals

Do not add:

```text
new business Order states
frontend Order state machine
client shipping/minimum-order evaluator
client inventory reservation logic
client Review eligibility formula
guest cart
multi-seller checkout
native iOS/Android app
offline Order/Cart mutation queue
offline business API cache
push notification channel
email notification channel
internal chat/WebSocket
service booking/calendar
online payment verification
bank QR/account storage
refund/escrow
Admin dispute arbitration
Admin force Order state
map/list discovery mode
advanced seller analytics
BI/revenue dashboard
favorite/saved listings
rich storefront themes
Redux/Zustand for server state
large UI framework migration
new database migration without actual schema need
```

Map/list toggle, advanced analytics, production performance tuning, backup/restore, rate-limit tuning, security headers/CDN, observability/SLO and pilot operations remain Plan 10 or later.

---

# Definition of Done — Plan 09

Plan 09 is implementable-complete only when fresh evidence proves:

```text
generated API client is current
no handwritten duplicate backend DTO enums
public browse works anonymously
explicit location context is used
login/Google registration path is truthful
multi-cart works by MerchantLocation/type
Checkout Preview -> Commit works for CART/DIRECT/NEED_REQUEST
CHECKOUT_CHANGED requires re-preview
Order UI is driven by allowedActions
buyer can complete directly after CONFIRMED when backend allows
Seller Center works across multiple merchants/locations
catalog Product/Service/Resale CRUD works
media direct upload works
tracked inventory adjustment UI works
Need buyer + seller flows work
Need fulfills only after source Order CONFIRMED
notification deep links are safe
SSE only invalidates/refetches
Review CTA uses backend eligibility
Reputation privacy is preserved
Report flow works
Admin settings/moderation minimum is operational
Admin Orders remain read-only
consumer mobile + desktop responsive gates pass
Playwright P0 journeys pass
frontend lint/typecheck/test/build pass
backend affected regression passes
OpenAPI generated diff is clean
highest Flyway migration remains V013 unless a real reviewed schema change exists
```

---

# Migration Handoff

Plan 08 ended at:

```text
V013__moderation_system_settings_admin.sql
```

Plan 09 intentionally requires **no schema change**. Therefore Plan 09 does not create an empty V014 migration.

Plan 10 starts with the next available migration number:

```text
V014
```

If implementation of Plan 09 discovers a real schema requirement, stop that task, document why existing contracts cannot satisfy it, add a reviewed migration using V014, and update Plan 10 handoff to V015. UI convenience alone is not sufficient reason for a schema migration.

---

# Execution Handoff

When Plan 09 implementation begins, use the isolated worktree required by the repository Superpowers workflow.

Recommended execution:

```text
1. Subagent-Driven Development
   - fresh subagent per Task
   - specification review after each Task
   - code-quality review after spec review
   - fresh focused verification before completion claim

2. Inline Executing Plans
   - execute Tasks 1 -> 20 in dependency order
   - preserve TDD RED/GREEN evidence
   - stop at review checkpoints
   - do not skip generated-contract or Playwright gates
```

Do not start Plan 10 hardening/pilot implementation until Task 20 has fresh green evidence.
