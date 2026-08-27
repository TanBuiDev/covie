# Seller Mobile — Engineering Handoff

## Scope

This branch implements the production-safe Seller/Provider mobile surfaces from Figma page `04 — Seller Mobile` while preserving the existing responsive/desktop Seller composition and backend-owned business behavior.

- branch: `feature/seller-mobile-figma-ui`
- base: `main` @ `42693a64139b8213765dafaa57e4fd66bb3d3736`
- pull request: `#5` — open, not merged
- Figma file: `CoVie — V1 UI Design`
- Figma Seller page: `3:5`
- production matrix: `274:192`
- detailed coverage: `docs/seller-mobile-coverage-matrix.md`
- CI evidence: `docs/seller-mobile-ci-verification.md`

## Frozen Seller mobile IA

Primary tabs, exact order:

1. Tổng quan
2. Đơn hàng
3. Danh mục
4. Điểm bán
5. Cài đặt

This is the frozen `Navigation/SellerMobileNav` horizontal sticky-tab pattern. Need feed, inventory, reputation and moderation notices are supporting Seller routes; they are intentionally not added as extra primary tabs.

## Production routes

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

## Implementation summary

### Shell / responsive architecture

- mobile-first Seller shell with frozen 5-tab IA and merchant switcher
- canonical CoVie repo asset on desktop where branding is present; no text/Unicode logo approximation
- 390px Figma reference plus explicit 360px Figma QA targets; desktop composition preserved at `>=1024px`
- no global `max-width:390px` app shell
- Seller semantic variables live in scoped CSS; cross-cutting accessibility rules are isolated in `seller-accessibility.css` rather than a giant override file
- Seller focus uses the frozen Emerald brand focus token; practical touch targets are at least 44px for the audited controls

### Dashboard / merchant settings / locations

- Dashboard reads orders, Need matches and locations from backend; displayed metrics are returned business data, not inferred analytics
- merchant edit uses server ETag + `If-Match`
- logo/cover upload uses canonical media purposes `MERCHANT_LOGO` and `MERCHANT_COVER`
- location create/edit uses backend DTO + ETag conflict behavior
- create, edit and minimum-order surfaces are separated to match the production frames
- coverage configuration shown on location cards comes from backend records
- backend-required coordinates remain an explicit technical fallback because Figma/API currently have no canonical geocoder/pin-picker contract

### Coverage / shipping / contacts

- delivery coverage supports backend radius and selected-area representations
- coverage and shipping create/edit/deactivate use canonical version tokens; version conflicts never silently overwrite
- shipping supports FREE, FIXED, NEGOTIATED and CONDITIONAL (ANY/ALL)
- negotiated shipping is never rendered as `0 ₫`
- contacts support PHONE, ZALO_LINK, ZALO_QR, MESSENGER_LINK and OTHER
- contact QR uses `MERCHANT_CONTACT_QR`, never payment QR
- destructive configuration actions use confirmation and pending lockout

### Catalog / inventory

- catalog list follows Product / Service / Thanh lý segmented presentation and renders only management-summary fields actually returned by API
- aggregate create uses real categories, category detail attributes, merchant locations, media, option groups, variants and location offers
- category attributes are generated from backend definitions: `TEXT`, `NUMBER`, `BOOLEAN`, `SINGLE_SELECT`, `MULTI_SELECT`; required and option ownership remain backend-owned
- `SEL-012` service variants create separate backend location offers per variant with independent exact price and availability
- `SEL-013` resale uses `listingKind=RESALE`; a Figma “Tình trạng” value appears only if the selected category defines the corresponding backend attribute/options — no hard-coded resale-condition enum
- default no-option variant uses backend canonical `__default__`
- inventory renders mutations only for TRACKED offers; reserved quantities are not edited optimistically
- stock mutation intents keep stable idempotency semantics; no full-page reload

### Orders

- incoming list uses assigned-location filtering and cursor pagination
- detail renders authoritative items, pricing, fulfillment, cancellation/issue, timeline and reputation context
- **every Seller command is mounted from `OrderDetailResponse.allowedActions`**
- no Seller action is inferred from order status
- command endpoints and `Idempotency-Key` usage were re-audited against generated OpenAPI
- destructive commands use dialogs with pending lock, keyboard focus trapping/restoration and authoritative refresh on conflict/action revocation
- Figma sample buyer name/amount/deadline fields are omitted from list rows when the current summary API does not return them

### Need matching

- merchant location must be selected before discovery
- feed exposes only safe buyer-area label/distance and backend Need data; private buyer coordinates/address are not rendered
- expiry/countdown is presentation-only and never transitions Need state locally
- `NEED-006` is intentionally a safe non-interactive fallback until backend exposes authoritative eligible response candidates

### Reputation / moderation

- Seller reputation uses only backend public aggregate reputation
- buyer reputation is displayed only inside the authorized Order reputation context
- moderation notices are included only when notification `targetType/targetId` ownership can be proven for the active merchant or one of its catalog items
- backend notification type is not mapped to a local route/deep-link
- notification payload does not contain moderation decision/reason, therefore Figma sample Pending/Needs Edit/Approved business states are not fabricated

## Backend-owned guardrails / known contract gaps

### NEED-006 eligible offer read

Submit accepts `merchantLocationId` + `locationVariantIds`, and backend validates merchant capability, serviceability/coverage, Need type, category hierarchy, listing/moderation state, availability and tracked stock. There is currently no read endpoint returning candidates after that same policy.

Do not reconstruct the eligibility policy in the browser and do not restore manual UUID fields. Recommended backend follow-up: `merchantId + needId + merchantLocationId -> eligible locationVariant[]` with display-safe name, current price/currency, availability/actionability and max-items metadata.

### Existing catalog edit/lifecycle

Backend supports granular mutations, but the current management surface does not expose an authoritative management-detail GET plus concurrency token that the Seller editor can safely read before mutation. Aggregate create, list presentation, media and inventory are implemented; existing-item structural edit/lifecycle CTA remains unmounted rather than guessing version state.

### Contact edit

The contact list does not provide a safe per-contact version/detail read for PATCH. Create/delete are production-mounted; edit is intentionally not mounted.

### Location coordinate UX

Location upsert requires latitude/longitude/`pinSource`. Frozen `SEL-004` does not define a canonical geocoder/pin picker. Current fields are explicitly technical contract inputs, not fabricated location data. Replace them when an authoritative picker/geocoder contract exists.

### Figma sample-only fields

Current management summaries do not return all visual sample values shown in Figma (for example catalog card media/price/offer count/moderation state and incoming-order buyer/amount/deadline). Those values are intentionally omitted rather than mocked.

## Figma production mapping

Exact mobile production frames audited include:

- `SEL-001` `357:9`
- `SEL-002` `357:220`
- `SEL-003` `357:321`
- `SEL-004` `357:407`
- `SEL-005` `362:358`, `362:445`
- `SEL-006` `362:525`, `362:647`
- `SEL-007` `362:718`
- `SEL-008` `362:796`
- `SEL-009` `368:709`
- `SEL-010` `368:854` (inventory supporting frame `368:1410`)
- `SEL-011` `368:1026`
- `SEL-012` `368:1139`
- `SEL-013` `368:1271`
- catalog lifecycle/media/conflict supporting stacks `368:1535`, `368:1578`, `368:1601`
- `SEL-014` `374:1340`
- `SEL-015` `374:1606`, `374:1744`, `374:1874`, `374:1984`
- `SEL-016` latest production `387:13327`; explicit 360 QA `387:13569`
- `SEL-017` latest production `387:13416`
- `NEED-005` `387:2001`
- `NEED-006` `387:2124`, edit `387:2277`, state stack `387:2413`
- `SEL-018` `389:13788`
- `SEL-019` `389:13846`, empty/fallback `389:13921`

Shared frozen components/tokens audited include SellerMobileNav, Merchant Switcher, StatusChip, Select, TextField, Switch, SegmentedControl, Seller Location/Catalog/Order/Need rows, action buttons and ConfirmDialog.

## Loading / error / mutation / accessibility coverage

- route-group loading/error boundaries
- scoped Loading/Empty/Error states on remote reads
- no double submit; pending controls disabled and important forms/actions expose busy/status semantics
- destructive actions confirm before mutation
- conflict/action-revocation paths refetch backend truth
- focus-visible Seller ring, semantic labels/headings, `aria-current`, status/alert regions
- confirm dialog supports Escape, focus trap, initial focus and focus restore
- audited mobile action targets use minimum practical 44px height
- long content wraps/truncates and layout is not fixed to 390px
- reduced-motion handling added for the custom switch presentation

## Static verification performed

Remote repo/OpenAPI/Figma audit performed on the branch includes:

- no current Seller `window.location.reload()`
- no current Seller `window.location.assign()` navigation
- manual production Category ID / LocationVariant ID entry removed where the backend has authoritative reads
- no current Unicode back-arrow/icon approximation in Seller routes
- Order actions derive from `allowedActions`; generated OpenAPI was checked for the command headers used by the client
- category attribute payload rules were checked against `CatalogAggregateValidator`
- service variant offer creation was checked against `CatalogCreateService`
- shipping/coverage version update contract was checked against generated types/backend DTOs
- StatusChip refactor retains global base presentation; Seller tone styling is scoped
- project uses TypeScript `strict` + `noUncheckedIndexedAccess`; obvious high-risk newly-added DTO property accesses were cross-checked against generated schema

This is **remote/static verification**, not an assertion that TypeScript/lint/tests/build have executed.

## CI execution status

Repository `.github/workflows/ci.yml` normally executes:

- API `./mvnw verify`
- OpenAPI export + generated client drift check
- API client check/typecheck
- web typecheck
- web lint
- web tests
- web build
- secret hygiene
- Trivy source/config/secret scan

GitHub Actions could not be started by this connector session. Evidence is recorded in `docs/seller-mobile-ci-verification.md`: PR #5 `opened`/Ready/`synchronize` events created no run, and a separate connector push to `codex/plan-10-seller-mobile-ci` (which matches the workflow's official push pattern) also created no run. Historical repo runs exist, so the limitation is the connector event/dispatch path, not a claim that CI passed.

## Local verification instructions

After pulling the branch, run from repo root with Node 24 and pnpm 10:

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm api-client:check
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
pnpm --filter @covie/web test:e2e
```

Backend verification:

```bash
cd apps/api
./mvnw verify
```

The root `package.json` currently has **no `format:check` script**. Do not report `pnpm format:check` as passed unless that script is added later.

For runtime/pixel tuning, start the documented local stack, then compare the main Seller frames at `360px`, `390px`, `768px` and desktop against the Figma frames above. Specifically check header/tab stickiness, horizontal nav scroll, padding, typography, card geometry, dialogs, long content, pending states and horizontal overflow.

## Verification classification at handoff

- **Code implementation:** completed to the maximum production-safe scope exposed by current Figma + repo + backend contracts.
- **Figma node coverage:** mapped with exact production frame IDs.
- **Remote/static contract verification:** completed.
- **GitHub Actions execution:** blocked by connector-created event semantics; no run was produced, so CI is not claimed green.
- **Local runtime verification:** pending.
- **Rendered 360/390 browser comparison:** pending.
- **Pixel-perfect claim:** not made.
- **PR:** #5 open and unmerged.

The branch is intended to be pulled directly for the remaining runtime/browser verification. Do not reimplement the contract-safe Seller work locally; use local agents only to fix actual runtime/type/test failures and final rendered pixel tuning that cannot be observed from this connector session.
