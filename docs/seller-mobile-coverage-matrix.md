# Seller Mobile — Figma Coverage Matrix

**Implementation branch:** `feature/seller-mobile-figma-ui`  
**Baseline:** `main` @ `42693a64139b8213765dafaa57e4fd66bb3d3736`  
**Visual source of truth:** Figma `CoVie — V1 UI Design`, page `04 — Seller Mobile`  
**Production matrix:** Figma node `274:192`

## Status legend

- `IMPLEMENTED`: production route/component and backend-owned states are implemented.
- `IMPLEMENTED_WITH_GAP`: production-safe implementation exists, but one or more Figma fields/actions cannot be rendered safely because the current read/mutation contract does not expose enough authoritative data.
- `CONTRACT_GAP`: the requested interaction cannot be mounted without inventing IDs, eligibility, state, or business rules. The UI intentionally exposes a safe non-interactive fallback instead.
- `CI_PENDING`: implementation has not yet passed the repository PR workflow. This document is updated before opening the verification PR.

## Global implementation contract

- Mobile-first at the Figma reference width `390px`, usable at `360px`, responsive through `768px` and existing desktop Seller shell.
- Frozen Seller mobile IA is exactly: `Tổng quan`, `Đơn hàng`, `Danh mục`, `Điểm bán`, `Cài đặt`; horizontal sticky tab strip, never Buyer bottom navigation.
- UI consumes generated `@covie/api-client` contracts and existing server read clients only.
- Order actions are mounted exclusively from `OrderDetailResponse.allowedActions`; no action is inferred from status.
- Need response eligibility, response uniqueness, coverage, catalog/category eligibility, moderation and stock remain backend-owned.
- Versioned merchant/location/coverage/shipping mutations use the HTTP ETag or canonical backend version token exposed for that resource; no client-generated version inference.
- Important mutations prevent double submit; destructive operations use confirmation; version conflicts force refresh/reload rather than silent overwrite.
- Notification type is never converted to a route locally. Seller moderation notices are shown only when notification payload ownership can be proven against the active merchant or its catalog IDs.
- No private buyer address/coordinates, raw reputation history, synthetic analytics, fake prices, fake UUIDs or fake moderation states are rendered.

## Production coverage

| ID | Actual Figma frame | Production route / surface | Implementation | Status | Known contract-safe limitation |
| --- | --- | --- | --- | --- | --- |
| `SEL-001` | `357:9` | `/seller/[merchantId]` | Seller shell, dashboard, recent orders, matching Needs, locations, reputation/notices links | `IMPLEMENTED / CI_PENDING` | Dashboard only displays metrics/data returned by current APIs; no synthetic analytics. |
| `SEL-002` | `357:220` | `/seller/[merchantId]/settings` | Merchant settings with ETag PATCH, conflict refresh, media/contact composition | `IMPLEMENTED / CI_PENDING` | Contact edit is not mounted without a safe per-contact version read contract. |
| `SEL-003` | `357:321` | `/seller/[merchantId]/locations` | Responsive location cards, pickup/delivery flags, server-derived coverage configuration state, create CTA | `IMPLEMENTED / CI_PENDING` | Coverage/config status comes only from returned coverage records; no open/closed inference. |
| `SEL-004` | `357:407` | `/seller/[merchantId]/locations/new`, `/seller/[merchantId]/locations/[locationId]` | Create/edit location with ETag conflict handling | `IMPLEMENTED_WITH_GAP / CI_PENDING` | Backend currently requires latitude/longitude but exposes no geocoder/pin-picker contract matching Figma; technical coordinates remain an explicit fallback instead of fabricated values. |
| `SEL-005` | `362:358`, `362:445` | `/seller/[merchantId]/locations/[locationId]/delivery` | Radius/area coverage create/edit/deactivate, location-area loading/error, version conflict | `IMPLEMENTED / CI_PENDING` | Uses backend coverage representation only. |
| `SEL-006` | `362:525`, `362:647` | `/seller/[merchantId]/locations/[locationId]/shipping` | FREE/FIXED/NEGOTIATED/CONDITIONAL policy create/edit/deactivate, ANY/ALL rules, conflict handling | `IMPLEMENTED / CI_PENDING` | Negotiated shipping is always copy-based, never represented as `0 ₫`. |
| `SEL-007` | `362:718` | `/seller/[merchantId]/locations/[locationId]/minimum-order` | Dedicated minimum-delivery-order form, ETag PATCH, positive-integer validation and pickup explanation | `IMPLEMENTED / CI_PENDING` | Mutation is disabled if the location response cannot be round-tripped safely. |
| `SEL-008` | `362:796` | merchant settings + `/seller/[merchantId]/locations/[locationId]/contacts` | PHONE/ZALO_LINK/ZALO_QR/MESSENGER_LINK/OTHER create/delete, public/primary flags, `MERCHANT_CONTACT_QR` upload | `IMPLEMENTED_WITH_GAP / CI_PENDING` | Current contact list lacks a safe per-contact ETag/detail read; PATCH edit is intentionally not mounted. |
| `SEL-009` | `368:709` | `/seller/[merchantId]/catalog` | Product/Service/Thanh lý segmented filtering, responsive catalog rows, backend listing status | `IMPLEMENTED_WITH_GAP / CI_PENDING` | Management summary does not expose the Figma sample's media, price, offer count or moderation state, so those are not invented. |
| `SEL-010` | page `04` Product editor group | `/seller/[merchantId]/catalog/new` | Product aggregate-create presentation using real categories, locations, variants/offers and media contracts | `IMPLEMENTED_WITH_GAP / CI_PENDING` | Safe edit route is not mounted without a management detail read + version/ETag contract. |
| `SEL-011` | page `04` Service editor group | `/seller/[merchantId]/catalog/new` | Service create state, service-safe quantity/inventory semantics, offers/media | `IMPLEMENTED_WITH_GAP / CI_PENDING` | Same edit-contract limitation as `SEL-010`. |
| `SEL-012` | page `04` Resale editor group | `/seller/[merchantId]/catalog/new` | RESALE listing-kind create state with real category/location/media inputs | `IMPLEMENTED_WITH_GAP / CI_PENDING` | Same edit-contract limitation as `SEL-010`. |
| `SEL-013` | page `04` Catalog lifecycle group | catalog create/inventory surfaces | Media upload, variant/location-offer composition, inventory management with idempotency and responsive cards | `IMPLEMENTED_WITH_GAP / CI_PENDING` | Lifecycle/edit actions requiring a missing authoritative detail/version read are not inferred. |
| `SEL-014` | `374:1340` | `/seller/[merchantId]/orders` | Location filter, cursor pagination, mobile order cards, backend status, detail CTA | `IMPLEMENTED_WITH_GAP / CI_PENDING` | Current order summary lacks the Figma sample's buyer name, amount, response deadline and row-level valid action; UI does not fabricate them. |
| `SEL-015` | page `04` Order detail group | `/seller/[merchantId]/orders/[orderId]` | Detail, items, pricing/fulfillment/timeline, buyer reputation context and server-owned action panel | `IMPLEMENTED / CI_PENDING` | Seller action ownership is entirely `allowedActions`. |
| `SEL-016` | page `04` Reject reason modal group | order detail modal | Reject reason dialog, stable idempotency intent, pending/error handling | `IMPLEMENTED / CI_PENDING` | Mounted only when backend returns `REJECT`. |
| `SEL-017` | page `04` Cancel/cancellation modal group | order detail modal | Seller cancel and cancellation response variants, destructive confirmation, conflict refresh | `IMPLEMENTED / CI_PENDING` | Mounted only from corresponding `allowedActions`. |
| `NEED-005` | `387:2001` | `/seller/[merchantId]/needs` | Required location selector, safe area/distance, expiry presentation, responded state, cursor loading | `IMPLEMENTED / CI_PENDING` | At/after deadline the next authoritative read owns state; frontend never transitions a Need locally. |
| `NEED-006` | `387:2124` | Need response selector/sheet | Safe explanatory fallback only | `CONTRACT_GAP / CI_PENDING` | Backend validates `locationVariantId` eligibility on submit but exposes no read endpoint returning eligible candidates. Inventory/catalog summaries lack enough category/type/moderation/price data to reproduce the policy safely. No manual UUID selector is exposed. |
| `SEL-018` | `389:13788` | `/seller/[merchantId]/reputation` + order context | Public merchant reputation summary and authorized buyer reputation context | `IMPLEMENTED / CI_PENDING` | No raw buyer history or synthetic rating data. |
| `SEL-019` | `389:13846` | `/seller/[merchantId]/notices` | Seller moderation notice surface filtered to active merchant/catalog ownership; read-state handling | `IMPLEMENTED / CI_PENDING` | No admin moderation actions and no type-to-route inference. |

## Shared state coverage

| State | Coverage |
| --- | --- |
| loading | Seller route-group loading plus scoped loading states for orders, Needs, areas and other client reads |
| empty/no data | canonical `EmptyState` or semantic empty panel on dashboard modules, locations, catalog, inventory, orders, Needs, reputation and notices |
| error + retry | route error boundary plus scoped remote-read retry where the read occurs client-side |
| mutation pending | submit/action lockout and `aria-busy` on Seller forms/commands |
| destructive confirmation | configuration deactivate/delete and destructive order command family |
| version conflict | merchant, location, minimum order, coverage and shipping refresh latest data instead of overwriting |
| restricted/404 | backend remains authoritative; Seller UI does not manufacture an alternate access state |
| long content | wrapping/truncation and mobile card layouts; no fixed 390px application shell |
| accessibility | visible labels, semantic headings, `aria-current`, status/alert regions, keyboard-safe confirm dialog, minimum mobile touch targets |

## Visual verification performed

The connected Figma design surface now exposes page `04 — Seller Mobile`; implementation was audited against the actual frames listed above and the frozen shared component contracts, including `SellerMobileNav`, `Seller/LocationSummary`, `Seller/CatalogRow`, `Seller/OrderRow`, `Seller/NeedMatchRow`, `StatusChip`, `Select`, `Switch`, buttons and Need expiry behavior.

A pixel screenshot comparison of a running Next.js build has **not** yet been performed in this remote connector session because no local/browser runtime is attached. GitHub PR CI is the executable verification gate for typecheck/lint/test/build; its result must be recorded in the engineering handoff before this branch is called complete.
