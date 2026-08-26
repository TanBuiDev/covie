# Seller Mobile — Engineering Handoff

## Scope

This branch implements the Seller/Provider mobile production surfaces from Figma page `04 — Seller Mobile` while preserving the existing desktop Seller shell and backend-owned business behavior.

- branch: `feature/seller-mobile-figma-ui`
- base: `main` @ `42693a64139b8213765dafaa57e4fd66bb3d3736`
- Figma file: `CoVie — V1 UI Design`
- Figma Seller production matrix: `274:192`
- coverage details: `docs/seller-mobile-coverage-matrix.md`

## Frozen mobile IA

The mobile Seller navigation is a sticky horizontal tab strip. Exact order:

1. Tổng quan
2. Đơn hàng
3. Danh mục
4. Điểm bán
5. Cài đặt

`Needs`, reputation, moderation notices and inventory are supporting Seller routes reached from the five frozen IA sections. They are intentionally not added as extra primary tabs.

## Main production routes

- `/seller`
- `/seller/[merchantId]`
- `/seller/[merchantId]/settings`
- `/seller/[merchantId]/locations`
- `/seller/[merchantId]/locations/new`
- `/seller/[merchantId]/locations/[locationId]`
- `/seller/[merchantId]/locations/[locationId]/minimum-order`
- `/seller/[merchantId]/locations/[locationId]/delivery`
- `/seller/[merchantId]/locations/[locationId]/shipping`
- `/seller/[merchantId]/locations/[locationId]/contacts`
- `/seller/[merchantId]/catalog`
- `/seller/[merchantId]/catalog/new`
- `/seller/[merchantId]/inventory`
- `/seller/[merchantId]/orders`
- `/seller/[merchantId]/orders/[orderId]`
- `/seller/[merchantId]/needs`
- `/seller/[merchantId]/reputation`
- `/seller/[merchantId]/notices`

## Implementation groups

### Seller shell and navigation

- responsive Seller shell with canonical CoVie asset
- sticky horizontal mobile nav with `aria-current`
- 390px Figma reference, 360px minimum-width usability, responsive desktop preservation
- scoped Seller semantic tokens and component CSS; no Tailwind dependency added

### Dashboard, settings and locations

- dashboard consumes current orders, merchant Need matches and locations
- merchant settings use HTTP ETag + `If-Match`
- location create/edit uses backend location DTO and ETag conflict behavior
- location list shows backend capability/configuration values only
- minimum delivery order is separated into its own Figma-aligned route/surface
- minimum-order PATCH round-trips the authoritative location data and disables mutation when required fields are absent

### Coverage, shipping and contacts

- delivery coverage supports radius/area representations exposed by backend
- coverage and shipping edit/deactivate use canonical backend version tokens; no client-generated ETag guessing
- shipping supports FREE, FIXED, NEGOTIATED and CONDITIONAL with ANY/ALL rules
- NEGOTIATED shipping is never rendered as `0 ₫`
- contacts support PHONE, ZALO_LINK, ZALO_QR, MESSENGER_LINK and OTHER
- contact QR uploads use media purpose `MERCHANT_CONTACT_QR`, never payment QR
- destructive operations use confirmation and pending lockout

### Catalog and inventory

- catalog list uses Product / Service / Thanh lý segmented presentation based only on summary fields returned by API
- aggregate create uses real category/location/media contracts; manual production UUID fields were removed where authoritative option APIs exist
- inventory uses responsive cards and idempotent stock mutations rather than full-page reloads
- fields not present in the catalog management summary (sample price/media/offer count/moderation state) are intentionally not fabricated

### Orders

- order list supports assigned-location filtering and cursor pagination
- detail uses authoritative Order detail data
- every Seller command is mounted from `OrderDetailResponse.allowedActions`
- reject/cancel/cancellation-response dialogs do not infer authorization from order status
- command intents use stable idempotency behavior and pending lockout
- version/action-revocation conflicts refresh authoritative state

### Need matching

- Seller selects a merchant location before discovery
- list displays safe buyer area, distance, expiry and responded state returned by backend
- expiry countdown is presentation-only; frontend never transitions Need state
- no buyer private address/coordinates are exposed

### Reputation and moderation

- Seller reputation displays only public aggregate fields available from the public merchant contract
- buyer reputation is only displayed inside the authorized Order reputation context
- moderation notices are selected from `/api/v1/me/notifications` only when target ownership can be proven against the active merchant or one of its catalog items
- no admin moderation actions are exposed to Seller
- notification type is not locally mapped to a route/deep link

## Backend-owned guardrails

### Order lifecycle

Never add a Seller order CTA from `status`. Use `allowedActions` only.

### Need response eligibility

`CreateNeedResponseRequest` requires `merchantLocationId` and one or more `locationVariantIds`. Backend eligibility validates the selected location and offers against merchant capability, distance/serviceability, coverage, Need type, category hierarchy, listing/moderation state, offer availability and tracked stock.

The current API has **no read endpoint returning eligible response candidates** after this complete policy is applied. Existing catalog/inventory summaries do not expose enough data to reproduce the backend policy safely. Therefore `NEED-006` remains a documented `CONTRACT_GAP`: do not add manual UUID inputs or client-side eligibility reconstruction. The Seller feed renders a safe explanatory fallback until the API adds an authoritative candidate read contract.

Recommended backend follow-up contract shape:

- scoped by `merchantId`, `needId`, `merchantLocationId`
- returns eligible `locationVariantId` records already filtered by the same policy used by submit
- includes display-safe item/variant name, current price/currency, availability summary and any caller-safe actionability reason
- supports the backend `responseConstraints.maxItems`

### Catalog edit/lifecycle

Do not mount granular edit/lifecycle commands unless the UI can first read the authoritative management detail plus its concurrency token. The current production implementation supports aggregate create and safe summary presentation; missing authoritative edit detail/version remains a contract gap.

### Contact edit

The contact list currently does not expose a safe per-contact concurrency token/detail read that can be used for PATCH without guessing. Create/delete are mounted; edit must remain disabled/unmounted until the version contract is authoritative.

### Location coordinates

The backend location upsert requires latitude/longitude and `pinSource`, while the frozen `SEL-004` Figma frame does not provide a geocoder/pin-picker interaction. The UI keeps these fields as an explicit technical fallback rather than manufacturing coordinates. When a canonical location picker/geocoder contract is available, replace this fallback instead of keeping two competing position sources.

## Loading, errors, conflicts and accessibility

- Seller route group has loading/error boundaries
- scoped client reads use canonical Loading/Empty/Error states where applicable
- mutations lock controls while pending and avoid double submission
- destructive actions use `ConfirmDialog`; focus is keyboard constrained/restored
- version conflicts never silently overwrite; users can refresh authoritative state
- mobile controls preserve practical touch targets
- visible labels and semantic headings are preferred
- status/error content uses semantic status/alert regions
- long user/API content wraps or truncates without locking the application to 390px

## Figma verification

Actual production frames accessed during implementation include:

- `SEL-001` `357:9`
- `SEL-002` `357:220`
- `SEL-003` `357:321`
- `SEL-004` `357:407`
- `SEL-005` `362:358`, `362:445`
- `SEL-006` `362:525`, `362:647`
- `SEL-007` `362:718`
- `SEL-008` `362:796`
- `SEL-009` `368:709`
- `SEL-014` `374:1340`
- `NEED-005` `387:2001`
- `NEED-006` `387:2124`
- `SEL-018` `389:13788`
- `SEL-019` `389:13846`

Additional editor/detail/modal surfaces were audited in the same Seller page groups. Shared frozen component contracts used include SellerMobileNav, SellerLocationSummary, SellerCatalogRow, SellerOrderRow, SellerNeedMatchRow, StatusChip, Select, TextField, Switch, SegmentedControl, buttons, ConfirmDialog and Need expiry.

A pixel screenshot comparison against a running Next.js build was not available in this connector-only session. Do not describe the branch as pixel-verified until such a browser/runtime comparison is performed.

## Verification gate

The repository CI workflow runs on pull requests and executes:

- backend `mvnw verify`
- OpenAPI export and generated-client drift check
- API client typecheck
- web typecheck
- web lint
- web tests
- web build
- secret-hygiene checks
- source security scan

At the time this handoff file is created, those PR checks are still pending. Update this section with the final PR/run result after opening the PR and fixing any failures.

## Commit groups already delivered

Representative branch groups include:

- coverage matrix first
- responsive Seller shell
- dashboard/settings/locations
- coverage/shipping/contacts
- catalog/inventory
- orders/Needs
- trust/moderation
- Figma alignment passes for locations, catalog, orders and Need feed
- mutation hardening and documentation

See Git history on `feature/seller-mobile-figma-ui` for exact SHAs.

## Final handoff rule

This branch may be called implementation-complete only when:

1. production-safe Figma coverage is reflected in `seller-mobile-coverage-matrix.md`;
2. documented contract gaps remain unimplemented rather than guessed;
3. the PR CI workflow passes or any unrelated infrastructure failure is explicitly proven and recorded;
4. no required Seller action depends on client-inferred authorization/state;
5. the PR remains unmerged for maintainer review.
