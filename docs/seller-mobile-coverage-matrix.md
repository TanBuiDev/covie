# Seller Mobile — Figma Coverage Matrix

> Integrated acceptance update (2026-09-01): the historical `RUNTIME_PENDING` limitation below is closed locally by the integrated Seller matrix at `fc93244`, covering 360/390/768/1024/1280/1440 with landmarks, Axe and overflow assertions. See `integrated-ui-verification-2026-09-01.md`. PR CI, human visual review and staging remain open.

**Implementation branch:** `feature/seller-mobile-figma-ui`  
**Baseline:** `main` @ `42693a64139b8213765dafaa57e4fd66bb3d3736`  
**Visual source of truth:** Figma `CoVie — V1 UI Design`, page `04 — Seller Mobile` (`3:5`)  
**Production matrix:** `274:192`

## Status contract

The project-requested screen state is kept separate from execution verification:

- `TODO`: production mapping has not been implemented.
- `IMPLEMENTED`: production route/component plus all safely implementable backend-owned states are mapped to code. A documented API contract gap can still prevent one Figma interaction from being mounted; the safe fallback is part of the implementation.
- `VERIFIED`: requires executable typecheck/lint/test/build evidence plus runtime/browser responsive validation for the screen. **No Seller screen is marked VERIFIED in this remote connector session.**

Verification labels used below:

- `REMOTE_STATIC_VERIFIED`: Figma frame + repo + generated OpenAPI/backend contract were audited remotely.
- `RUNTIME_PENDING`: no attached local Next.js/browser runtime was available for rendered 360/390 comparison.
- `CI_TRIGGER_BLOCKED`: GitHub Actions did not create workflow runs for connector-created PR/push events; see `docs/seller-mobile-ci-verification.md`.

## Global implementation contract

- Mobile reference `390px`; frozen Figma also contains explicit `360px` QA frames. Seller remains responsive through tablet and the existing desktop composition at `>=1024px`; no global `max-width:390px` shell.
- Frozen primary Seller mobile IA is exactly `Tổng quan`, `Đơn hàng`, `Danh mục`, `Điểm bán`, `Cài đặt` using horizontal sticky tabs.
- UI consumes generated `@covie/api-client` contracts and existing API clients; no production UUID text fields are used when an authoritative selector/read API exists.
- Order actions are mounted only from `OrderDetailResponse.allowedActions`.
- Need eligibility, coverage, catalog/category eligibility, moderation and stock remain backend-owned.
- Merchant/location use HTTP ETag; coverage/shipping use canonical backend version tokens. Conflicts refresh authoritative data; no silent overwrite.
- Important mutations lock while pending; destructive operations confirm; retries preserve mutation intent/idempotency where required.
- Notification type is never converted to a local route. Moderation notices are shown only when target ownership can be proven.
- No private buyer address/coordinates, raw reputation history, synthetic analytics, fake prices, fake UUIDs, fake moderation states or Unicode icon approximations are rendered.

## Production coverage

| ID | Exact Figma frame(s) | Production route / surface | Implementation mapping | Status | Verification / contract limitation |
| --- | --- | --- | --- | --- | --- |
| `SEL-001` | `357:9` | `/seller/[merchantId]` | Responsive Seller shell; merchant switcher; 5-tab IA; backend status/location metrics; recent orders and matching Need modules; empty/error/restricted states | `IMPLEMENTED` | `REMOTE_STATIC_VERIFIED / RUNTIME_PENDING / CI_TRIGGER_BLOCKED`; no synthetic analytics. |
| `SEL-002` | `357:220` | `/seller/[merchantId]/settings` | Merchant name/description plus real `MERCHANT_LOGO`/`MERCHANT_COVER` upload, ETag PATCH, pending/conflict/error handling | `IMPLEMENTED` | Contact edit remains a contact-contract limitation, not a settings permission inference. |
| `SEL-003` | `357:321` | `/seller/[merchantId]/locations` | Location cards, backend capabilities/configuration state, separate create CTA, loading/empty/error | `IMPLEMENTED` | Coverage state comes from coverage records only. |
| `SEL-004` | `357:407` | `/seller/[merchantId]/locations/new`, `/seller/[merchantId]/locations/[locationId]` | Create/edit with ETag conflict handling; visible business fields follow Figma; backend-required coordinates live in an explicit technical fallback | `IMPLEMENTED` | **Contract gap:** backend requires lat/lng + `pinSource`; frozen frame has no canonical geocoder/pin picker. Coordinates are never fabricated. |
| `SEL-005` | `362:358`, `362:445` | `/seller/[merchantId]/locations/[locationId]/delivery` | Radius/selected-area coverage create/edit/deactivate; location-area loading/error; version conflict | `IMPLEMENTED` | Backend coverage representation only. |
| `SEL-006` | `362:525`, `362:647` | `/seller/[merchantId]/locations/[locationId]/shipping` | FREE/FIXED/NEGOTIATED/CONDITIONAL, ANY/ALL rules, create/edit/deactivate and conflict handling | `IMPLEMENTED` | NEGOTIATED is copy-based and never represented as `0 ₫`. |
| `SEL-007` | `362:718` | `/seller/[merchantId]/locations/[locationId]/minimum-order` | Dedicated minimum-order form, positive-integer validation, ETag PATCH and pickup explanation | `IMPLEMENTED` | Mutation is blocked if the authoritative location response cannot be safely round-tripped. |
| `SEL-008` | `362:796` | merchant settings + `/seller/[merchantId]/locations/[locationId]/contacts` | PHONE/ZALO_LINK/ZALO_QR/MESSENGER_LINK/OTHER create/delete, primary/public flags, canonical contact QR media purpose | `IMPLEMENTED` | **Contract gap:** list response has no safe per-contact concurrency token/detail read, so PATCH edit is intentionally not mounted. |
| `SEL-009` | `368:709` | `/seller/[merchantId]/catalog` | Product/Service/Thanh lý segmented filtering; responsive management rows; backend listing status; empty/error | `IMPLEMENTED` | Management summary omits sample media/price/offer-count/moderation fields, so those values are not fabricated. |
| `SEL-010` | `368:854`; inventory support `368:1410` | `/seller/[merchantId]/catalog/new`, `/seller/[merchantId]/inventory` | Product aggregate create with real category detail/attributes, variants, location offers, media and TRACKED/UNTRACKED inventory; responsive stock controls | `IMPLEMENTED` | **Contract gap:** no authoritative management-detail GET + concurrency token for full existing-item edit/lifecycle. |
| `SEL-011` | `368:1026` | `/seller/[merchantId]/catalog/new` (`SERVICE`) | Service aggregate create, service-enabled location filtering, media, options/variants and service-safe no-inventory semantics | `IMPLEMENTED` | Existing-item edit remains blocked by the same management-detail concurrency gap. |
| `SEL-012` | `368:1139` | Service variant composition inside `/seller/[merchantId]/catalog/new` | Service variants/gói render from real option values; each variant receives its own backend location offer with independent exact price and `available` state | `IMPLEMENTED` | Standalone edit of an existing variant set is not mounted without authoritative item detail/version. |
| `SEL-013` | `368:1271` | `/seller/[merchantId]/catalog/new` (`PRODUCT + RESALE`) | Resale create uses backend `listingKind=RESALE`, media, exact price and generic category-detail attributes (`TEXT/NUMBER/BOOLEAN/SINGLE_SELECT/MULTI_SELECT`) | `IMPLEMENTED` | “Tình trạng” is rendered only if the selected category actually defines that attribute/options; frontend does not hard-code Figma sample enum values. |
| supporting catalog states | lifecycle `368:1535`; media `368:1578`; inventory conflict `368:1601` | catalog/inventory surfaces | Upload pending/error/retry; listing-status presentation; reserved-stock/error behavior | `IMPLEMENTED` | Lifecycle mutation CTA is not mounted when authoritative edit/version read is unavailable. |
| `SEL-014` | mobile `374:1340`; tablet `374:1485` | `/seller/[merchantId]/orders` | Assigned-location filter, cursor pagination/load more, mobile rows, backend status and detail navigation | `IMPLEMENTED` | Summary API lacks Figma sample buyer name/amount/deadline/row action; no fake values. |
| `SEL-015` | `374:1606`, `374:1744`, `374:1874`, `374:1984`; conflict `374:2091` | `/seller/[merchantId]/orders/[orderId]` | Product pending/delivery/pickup and service detail composition, items/pricing/fulfillment/timeline, buyer reputation context and action panel | `IMPLEMENTED` | All actions are exclusively `allowedActions`; no frontend state machine. |
| `SEL-016` | latest production `387:13327`; 360 QA `387:13569` | Order reject reason dialog/surface | Reject reason/note presentation, destructive confirmation, stable idempotency intent, pending/error/conflict refresh | `IMPLEMENTED` | Mounted only when backend returns `REJECT`. Backend has no canonical reason-option list, so sample dropdown choices are not invented. |
| `SEL-017` | latest production `387:13416` | Order cancel/cancellation response dialog/surface | Cancel and cancellation-response variants with confirmation, pending/error and action revocation refresh | `IMPLEMENTED` | Mounted only from corresponding `allowedActions`; reason options are not invented. |
| `NEED-005` | `387:2001` | `/seller/[merchantId]/needs` | Required merchant-location selector, safe buyer area/distance, expiry presentation, responded state, loading/error/cursor states | `IMPLEMENTED` | Frontend countdown is presentation-only and never transitions Need state. |
| `NEED-006` | select `387:2124`; existing response `387:2277`; state stack `387:2413` | Need response composition | Safe non-interactive contract fallback is mounted; existing response presentation component remains available for an authoritative candidate source | `IMPLEMENTED` | **Blocking API contract gap:** backend validates `locationVariantId` on submit but exposes no read endpoint returning eligible candidates after the same category/moderation/coverage/stock policy. No manual UUID or reconstructed eligibility is exposed. |
| `SEL-018` | `389:13788`; tablet `389:13947` | `/seller/[merchantId]/reputation` + authorized order context | Public merchant aggregate reputation plus buyer reputation only from Order reputation context | `IMPLEMENTED` | No raw history or synthetic score. |
| `SEL-019` | `389:13846`; empty state `389:13921` | `/seller/[merchantId]/notices` | Ownership-filtered moderation notification list, read/unread state, empty/error-safe fallback | `IMPLEMENTED` | Notification payload lacks moderation decision/reason; UI uses generic backend-safe copy and no type-to-route mapping. |

## Shared state matrix

| Required state | Mapped implementation |
| --- | --- |
| loading | Seller route-group loading plus scoped client loading for orders, Need discovery, location areas, category attributes and remote lists |
| empty/no data | canonical `EmptyState` or semantic empty panel on dashboard modules, locations, catalog, inventory, orders, Needs, reputation and notices |
| error + retry | route error boundary and scoped retries/refetches; no reload loop |
| disabled/pending | mutation controls disabled with `aria-busy`; double submit prevented |
| destructive confirmation | configuration deactivate/delete and destructive order commands use `ConfirmDialog`/action dialogs |
| version/action conflict | merchant/location/minimum-order/coverage/shipping and order action revocation refetch authoritative state |
| restricted/permission | only backend-returned merchant/action capability state is rendered; no status-derived permission |
| long content | wrap/truncate/card layouts; Seller shell is not width-locked to 390px |
| accessibility | Seller focus ring uses frozen brand focus token; 44px minimum interactive targets; semantic headings/labels/status/alert; confirm dialog traps/restores focus; reduced-motion switch handling |
| 360 responsive | explicit Figma 360 QA frames were audited; rendered-browser overflow verification remains `RUNTIME_PENDING` |

## Verification state

Figma node/frame coverage and backend/OpenAPI contracts were audited remotely. Current-code static sweeps found no Seller `window.location.reload()`, `window.location.assign()`, manual Category/LocationVariant ID production fields, or Unicode back-arrow approximation. Order command headers were checked against generated OpenAPI and all mounted Seller command endpoints declare `Idempotency-Key` where used.

Executable CI and rendered-browser verification are **not claimed as passed**. The connector-created PR and a connector-created push to a branch matching the workflow's official `codex/plan-10-*` push pattern both produced no Actions workflow run. See `docs/seller-mobile-ci-verification.md` and run the local commands in `docs/seller-mobile-engineering-handoff.md` after pulling the branch.
