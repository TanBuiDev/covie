# Seller Mobile — Figma Coverage Matrix

**Implementation branch:** `feature/seller-mobile-figma-ui`  
**Baseline:** `main` @ `42693a64139b8213765dafaa57e4fd66bb3d3736`  
**Visual source of truth:** Figma `CoVie — V1 UI Design`, page `04 — Seller Mobile`  
**Production-matrix source:** Figma `00 — Cover & Index`, Seller column node `274:192`

> Verification rule: `IMPLEMENTED` means code/state mapping exists. `VERIFIED` requires static/runtime evidence for the code and node-level visual evidence for the relevant Figma production frame. The connected Figma surface currently exposes the frozen Seller production matrix but not the child page/frame tree for `04 — Seller Mobile`; therefore actual production frame IDs are intentionally recorded as `UNAVAILABLE_FROM_CONNECTOR` rather than guessed.

## Global contract

- Mobile target: 390 px production composition; 360 px minimum no-horizontal-overflow audit; 768 px responsive seller composition; preserve desktop Seller shell.
- UI renders generated `@covie/api-client` contracts only.
- Order commands are mounted exclusively from `OrderDetailResponse.allowedActions`.
- Need response offer/actions use backend `responseConstraints`, `actionable`, and location scope; no private buyer coordinates/address are invented or exposed.
- Versioned merchant/location/coverage/shipping/catalog resources use server versions/ETag (`If-Match`) where the API exposes them.
- Destructive mutations require confirmation where the production design calls for it, pending lockout, success refresh/invalidation, and error/retry behavior.
- No notification route is inferred from notification type in Seller Mobile. A backend-owned same-origin deep link may be consumed only when such a contract exists.

## Production coverage

### SEL-001 — Seller Center Dashboard

- route: `/seller/[merchantId]`
- figma production matrix node: `274:196` (`SEL-001`); actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: loading, empty orders, empty matching needs, empty locations, API error, restricted/inaccessible merchant, long merchant/order content
- API dependency: `GET /api/v1/merchants/{merchantId}/orders`, `GET /api/v1/merchants/{merchantId}/need-requests`, `GET /api/v1/merchants/{merchantId}/locations`, `/api/v1/me/notifications` only for notice data if safely attributable
- code location: `apps/web/app/(seller)/seller/[merchantId]/page.tsx`, `apps/web/features/seller/seller-dashboard.tsx`, Seller shell/nav
- status: TODO

### SEL-002 — Shop Settings

- route: `/seller/[merchantId]/settings`
- figma production matrix node: `274:204`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: loading, edit, pending save, success refresh, validation error, API error, version conflict/retry, restricted merchant, long description
- API dependency: `GET/PATCH /api/v1/merchants/{merchantId}` with ETag/If-Match
- code location: Seller settings page, `apps/web/features/merchant/merchant-form.tsx`
- status: TODO

### SEL-003 — Location List

- route: `/seller/[merchantId]/locations`
- figma production matrix node: `274:212` (`SEL-003/004`); actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: loading, populated, empty/no-data, error/retry, long address, status/flags, restricted location visibility
- API dependency: `GET /api/v1/merchants/{merchantId}/locations`
- code location: locations page, `apps/web/features/seller/location-list.tsx`
- status: TODO

### SEL-004 — Location Create / Edit

- route: create state on `/seller/[merchantId]/locations`; edit `/seller/[merchantId]/locations/[locationId]`
- figma production matrix node: `274:212`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: create, edit, pending, success refresh, validation error, API error/retry, version conflict, restricted/404, minimum-order zero, disabled service/pickup/delivery toggles
- API dependency: `POST /api/v1/merchants/{merchantId}/locations`, `GET/PATCH /api/v1/merchants/{merchantId}/locations/{locationId}` with ETag/If-Match
- code location: locations routes, `apps/web/features/merchant/location-form.tsx`
- status: TODO

### SEL-005 — Delivery / Service Coverage

- route: `/seller/[merchantId]/locations/[locationId]/delivery`
- figma production matrix node: `274:220` (`SEL-005…008`); actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: loading areas, radius mode, selected-area mode, combined backend records, pending mutation, empty/no coverage, API error/retry, version conflict when editing, restricted/404
- API dependency: `GET /api/v1/location-areas`, `GET/POST/PATCH/DELETE /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages`
- code location: delivery page, `apps/web/features/shipping/coverage-form.tsx`
- status: TODO

### SEL-006 — Shipping Policy

- route: `/seller/[merchantId]/locations/[locationId]/shipping`
- figma production matrix node: `274:220`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: empty, FREE, FIXED, NEGOTIATED, CONDITIONAL/ANY, CONDITIONAL/ALL, pending, error/retry, destructive delete confirmation, version conflict, restricted/404
- API dependency: `GET/POST/PATCH/DELETE /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies`
- code location: shipping page, `apps/web/features/shipping/shipping-policy-form.tsx`
- status: TODO

### SEL-007 — Minimum Delivery Order

- route: `/seller/[merchantId]/locations/[locationId]`
- figma production matrix node: `274:220`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: zero/no minimum, positive VND integer, pending, validation, error/retry, version conflict, restricted
- API dependency: `MerchantLocationResponse.minimumDeliveryOrder` + `PATCH /api/v1/merchants/{merchantId}/locations/{locationId}`
- code location: location detail + `apps/web/features/merchant/location-form.tsx`
- status: TODO

### SEL-008 — Merchant / Location Contact

- route: contact surface composed under `/seller/[merchantId]/locations/[locationId]` and merchant settings where applicable
- figma production matrix node: `274:220`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: empty, PHONE, ZALO_LINK, ZALO_QR, MESSENGER_LINK, OTHER, primary/public toggles, pending, error/retry, version conflict, destructive delete confirmation
- API dependency: `GET/POST/PATCH/DELETE /api/v1/merchants/{merchantId}/contacts`; location override via `merchantLocationId`; QR uses `MERCHANT_CONTACT_QR` media, never payment QR
- code location: seller contact settings presentation/API (to be added without inventing payment fields)
- status: TODO

### SEL-009 — Catalog List

- route: `/seller/[merchantId]/catalog`
- figma production matrix node: `274:228` (`SEL-009…013`); actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: loading, populated, empty/no-data, API error/retry, DRAFT/ACTIVE/PAUSED/DELETED presentation, long item names
- API dependency: `GET /api/v1/merchants/{merchantId}/catalog-items`
- code location: catalog page + catalog list/row presentation
- status: TODO

### SEL-010 — Product Create / Edit Presentation

- route: create `/seller/[merchantId]/catalog/new`; edit surface when management detail contract is routable
- figma production matrix node: `274:228`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: PRODUCT selected, category/basic fields, options/variants, location offers, media, review, pending, API error, validation, long content
- API dependency: generated category/catalog/media/location contracts; aggregate create `CreateCatalogItemRequest`; granular PATCH/If-Match only where current read/version contract supports safe edit
- code location: catalog create/editor feature
- status: TODO

### SEL-011 — Service Create / Edit Presentation

- route: `/seller/[merchantId]/catalog/new` type state; edit surface when management detail contract is routable
- figma production matrix node: `274:228`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: SERVICE selected, quantity-one semantics, no inventory controls, location offer/price, media, pending/error/validation
- API dependency: `CreateCatalogItemRequest(type=SERVICE)`, location offers, media; no client-created service inventory rule
- code location: catalog create/editor feature
- status: TODO

### SEL-012 — Resale Create / Edit Presentation

- route: `/seller/[merchantId]/catalog/new` listing-kind state; edit surface when management detail contract is routable
- figma production matrix node: `274:228`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: RESALE selected, category/basic fields, offers/media, pending/error/validation
- API dependency: `CreateCatalogItemRequest(listingKind=RESALE)` and generated catalog/media contracts
- code location: catalog create/editor feature
- status: TODO

### SEL-013 — Catalog Media / Variant / Lifecycle States

- route: catalog create/edit surfaces
- figma production matrix node: `274:228`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: no media, upload pending/error/retry, variant selection, offer availability, DRAFT/ACTIVE/PAUSED/DELETED; lifecycle CTA only when backed by explicit backend operation/version context
- API dependency: `/api/v1/media/uploads`, `/api/v1/media/{mediaId}/complete`, generated variant/location-offer/lifecycle operations
- code location: catalog + media features
- status: TODO

### SEL-014 — Incoming Orders

- route: `/seller/[merchantId]/orders`
- figma production matrix node: `274:236` (`SEL-014/015`); actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: loading, empty, error/retry, paged/load-more, location scope, long order code, all backend statuses
- API dependency: `GET /api/v1/merchants/{merchantId}/orders?limit&cursor&locationId`; no client-only status filtering over a partial cursor page
- code location: seller orders page, `apps/web/features/orders/order-list.tsx`
- status: TODO

### SEL-015 — Seller Order Detail / Action Panel

- route: `/seller/[merchantId]/orders/[orderId]`
- figma production matrix node: `274:236`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: loading, error/retry, timeline, pricing/fulfillment, cancellation/issue, buyer reputation context, no-actions, one/many `allowedActions`, pending command, conflict refresh, 403 action revoked, destructive confirmation
- API dependency: `GET /api/v1/orders/{orderId}`; commands only from `OrderDetailResponse.allowedActions`; buyer reputation only from `OrderReputationContext.buyer`
- code location: seller order detail page, shared order detail/actions/timeline
- status: TODO

### SEL-016 — Reject Order Reason

- route: modal/dialog state on `/seller/[merchantId]/orders/[orderId]`
- figma production matrix node: `274:244` (`SEL-016/017`); actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: closed, open, validation, pending, API error/retry; CTA mounted only when backend includes `REJECT`
- API dependency: `POST /api/v1/orders/{orderId}/reject`, `ReasonRequest`, stable idempotency key per intent
- code location: shared order action dialog/actions
- status: TODO

### SEL-017 — Seller Cancel / Cancellation Response Reason

- route: modal/dialog state on `/seller/[merchantId]/orders/[orderId]`
- figma production matrix node: `274:244`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: cancel/reject-cancellation/accept-cancellation variants only when returned in `allowedActions`, pending, conflict, error/retry, destructive confirmation
- API dependency: generated cancel/request/accept/reject cancellation command contracts; never inferred from order status
- code location: shared order action dialog/actions
- status: TODO

### NEED-005 — Seller Need Feed

- route: `/seller/[merchantId]/needs`
- figma production matrix node: `274:252` (`NEED-005/006`); actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: loading, empty, error/retry, location scope, responded/not-responded, safe buyer area/distance, expiry/long content
- API dependency: `GET /api/v1/merchants/{merchantId}/need-requests?locationId&limit&cursor`; `MerchantNeedMatchResponse` and `responseConstraints`
- code location: seller needs page + merchant need list
- status: TODO

### NEED-006 — Select Catalog Item / Need Response

- route: sheet/modal/composed state on `/seller/[merchantId]/needs`
- figma production matrix node: `274:252`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: no location selected, loading eligible offers, empty catalog selection, selected items, max-items constraint, note, pending submit, error/retry, existing response/edit/withdraw where contract is available
- API dependency: existing merchant locations/catalog offers plus `CreateNeedResponseRequest` / `ReplaceNeedResponseRequest`; selection limited by backend `responseConstraints.maxItems`; response items preserve backend `actionable`/availability
- code location: merchant need list/response form + seller composition
- status: TODO

### SEL-018 — Seller Reputation

- route: composed on Seller dashboard/order surfaces; no standalone mutation route invented
- figma production matrix node: `274:260` (`SEL-018/019`); actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: reputation present/absent; buyer warning only in authorized seller order context; no raw buyer history
- API dependency: `OrderReputationContext`, public merchant reputation when available; no synthetic score/count
- code location: seller dashboard/order reputation presentation
- status: TODO

### SEL-019 — Moderation Notices

- route: composed Seller notice surface; no admin moderation route/actions exposed to Seller
- figma production matrix node: `274:260`; actual page frame: `UNAVAILABLE_FROM_CONNECTOR`
- mobile viewport: `390`, audit `360`; responsive target `768 + desktop`
- states: none, one/many notices, read/unread, loading/error when backed by notifications; restricted content/status copy only from returned data
- API dependency: `/api/v1/me/notifications` `MODERATION_UPDATED`/merchant suspension-restoration events; backend-owned data only. No seller access to admin moderation case actions is invented.
- code location: Seller notification/notice presentation using existing notification data layer
- status: TODO

## Shared state matrix

| State | Required Seller surfaces | Verification rule |
| --- | --- | --- |
| loading | dashboard/list/detail/config/catalog/orders/needs/notices | LoadingState/skeleton does not shift action ownership |
| empty / no-data | dashboard modules, locations, shipping/coverage, catalog, orders, needs, notices | canonical EmptyState or semantic empty block with recovery CTA when valid |
| error + retry | all remote reads and important mutations | no reload loop; retry/refetch is scoped |
| disabled / pending | all mutations | no double submit; destructive CTA locked while pending |
| destructive confirm | order reject/cancel family; delete config only where implemented | ConfirmDialog/modal with focus semantics |
| version conflict | merchant/location/shipping/coverage/catalog versioned edits | refetch latest; no silent overwrite |
| permission/restricted | merchant/location/order/need scopes | backend 403/404 governs; no client role-derived authorization |
| long content | merchant names, addresses, order codes, catalog/need titles, notes | wraps/truncates only where design allows; no horizontal overflow at 360/390 |

## Figma connector gap

The connected Figma metadata currently enumerates only top-level page `00 — Cover & Index`. It does expose the frozen Seller production matrix and exact matrix nodes above, plus the canonical component inventory/foundations references, but not the page/frame tree for `04 — Seller Mobile`. No production frame node ID will be guessed. Until that page tree becomes available, implementation can be contract/static verified but final node-by-node screenshot comparison remains `PENDING_EXTERNAL_VISUAL_VERIFICATION`.
