# Seller Mobile — Figma Coverage Matrix

**Implementation branch:** `feature/seller-mobile-figma-ui`  
**Baseline:** `main` @ `42693a64139b8213765dafaa57e4fd66bb3d3736`  
**Visual source of truth:** Figma `CoVie — V1 UI Design`, page `04 — Seller Mobile` (`3:5`)  
**Production matrix:** `274:192`

**Current local issue checkpoint:** `codex/issue-completion` (2026-09-19) completes the runtime/visual gate. All 36 Seller cases / 111 captures pass automated diagnostics and hash-bound manual review, including management detail, merchant-owned Need response replacement and the documented safe contract gaps. The historical branch/baseline and dated labels below remain provenance; current Seller acceptance is `VERIFIED_LOCAL`, while staging remains incomplete.

## Status contract

The project-requested screen state is kept separate from execution verification:

- `TODO`: production mapping has not been implemented.
- `IMPLEMENTED`: production route/component plus all safely implementable backend-owned states are mapped to code. A documented API contract gap can still prevent one Figma interaction from being mounted; the safe fallback is part of the implementation.
- `VERIFIED_LOCAL`: executable typecheck/lint/test/build evidence plus runtime/browser responsive validation and hash-bound manual review are complete in the current local worktree.

Verification labels used below:

- `REMOTE_STATIC_VERIFIED`: Figma frame + repo + generated OpenAPI/backend contract were audited remotely.
- `RUNTIME_PENDING`: historical label for the earlier remote-only checkpoint; superseded for cataloged Seller cases by the 2026-09-19 local evidence.
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
| `SEL-010` | `368:854`; inventory support `368:1410` | `/seller/[merchantId]/catalog/new`, `/seller/[merchantId]/catalog/[itemId]`, `/seller/[merchantId]/inventory` | Product create plus existing-item edit from authenticated management detail/ETag; category-defined attributes, media append, variants, location offers and server-returned lifecycle actions; separate inventory controls | `IMPLEMENTED` | Existing-item contract gap resolved in local API/client. Focused form/API and full web gates pass 2026-09-16; populated Seller runtime and 360/390 visual comparison remain pending. |
| `SEL-011` | `368:1026` | `/seller/[merchantId]/catalog/new` (`SERVICE`), `/seller/[merchantId]/catalog/[itemId]` (`SERVICE`) | Service create/edit with media, options/variants and service-safe no-inventory semantics | `IMPLEMENTED` | Management detail/independent offer versions exist; service edit state and viewport acceptance remain pending. |
| `SEL-012` | `368:1139` | Service variant composition inside catalog create/edit | Service variants/gói read from authoritative option values; existing variants and offers use independent versions and server-returned edit actions | `IMPLEMENTED` | Existing variant edit contract is present; only safe new-combination/create or delete operations are exposed. Runtime/visual acceptance remains pending. |
| `SEL-013` | `368:1271` | `/seller/[merchantId]/catalog/new` (`PRODUCT + RESALE`) | Resale create uses backend `listingKind=RESALE`, media, exact price and generic category-detail attributes (`TEXT/NUMBER/BOOLEAN/SINGLE_SELECT/MULTI_SELECT`) | `IMPLEMENTED` | “Tình trạng” is rendered only if the selected category actually defines that attribute/options; frontend does not hard-code Figma sample enum values. |
| supporting catalog states | lifecycle `368:1535`; media `368:1578`; inventory conflict `368:1601` | catalog/inventory surfaces | Upload pending/error/retry; listing-status presentation; reserved-stock/error behavior | `IMPLEMENTED` | Existing-item lifecycle CTA now mounts only from management-detail `allowedActions` and submits the item ETag; fresh visual acceptance remains pending. |
| `SEL-014` | mobile `374:1340`; tablet `374:1485` | `/seller/[merchantId]/orders` | Assigned-location filter, cursor pagination/load more, mobile rows, backend status and detail navigation | `IMPLEMENTED` | Summary API lacks Figma sample buyer name/amount/deadline/row action; no fake values. |
| `SEL-015` | `374:1606`, `374:1744`, `374:1874`, `374:1984`; conflict `374:2091` | `/seller/[merchantId]/orders/[orderId]` | Product pending/delivery/pickup and service detail composition, items/pricing/fulfillment/timeline, buyer reputation context and action panel | `IMPLEMENTED` | All actions are exclusively `allowedActions`; no frontend state machine. |
| `SEL-016` | latest production `387:13327`; 360 QA `387:13569` | Order reject reason dialog/surface | Reject reason/note presentation, destructive confirmation, stable idempotency intent, pending/error/conflict refresh | `IMPLEMENTED` | Mounted only when backend returns `REJECT`. Backend has no canonical reason-option list, so sample dropdown choices are not invented. |
| `SEL-017` | latest production `387:13416` | Order cancel/cancellation response dialog/surface | Cancel and cancellation-response variants with confirmation, pending/error and action revocation refresh | `IMPLEMENTED` | Mounted only from corresponding `allowedActions`; reason options are not invented. |
| `NEED-005` | `387:2001` | `/seller/[merchantId]/needs` | Required merchant-location selector, safe buyer area/distance, expiry presentation, responded state, loading/error/cursor states and deliberate response expansion | `IMPLEMENTED / FIGMA_AUDITED / TEST_VERIFIED / VISUAL_PENDING` | Expired countdown is presentation only: Respond is withheld and a reload requests authoritative state; the frontend never transitions the Need. Selected Need/location changes discard stale candidate reads. |
| `NEED-006` | selection `387:2124`; existing-response reference `387:2277`; warning states `387:2413` | `/seller/[merchantId]/needs` response expansion | Generated eligible-items GET supplies item/variant names, current price, available IDs, cursor and `maxResponseItems`; checkboxes and POST appear only after that read succeeds. Loading/empty/error/retry and load-more stay safe. | `CODE_LEVEL / FIGMA_AUDITED / TEST_VERIFIED / RUNTIME_PENDING / VISUAL_PENDING` | Only returned `locationVariantId` values enter POST; no manual UUID, sample media, guessed coverage/stock or fulfillment cues. The existing-response edit/replacement presentation from `387:2277` is not mounted; mobile 390×844/360×800 runtime comparison and E2E remain open. |
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
