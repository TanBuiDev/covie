# Seller Desktop — Figma Coverage Matrix

**Implementation branch:** `feature/seller-desktop-figma-ui`  
**Baseline:** `feature/seller-mobile-figma-ui` @ `aae96f38ce7a4b7ecfa5bed40a6489ddcffa3078`  
**Baseline relationship:** Seller Mobile is 88 commits ahead / 0 behind `main` (`42693a64139b8213765dafaa57e4fd66bb3d3736`). The separate `codex/plan-10-seller-mobile-ci` branch only contributes an unrelated CI-trigger commit and is not the implementation base.  
**Visual source of truth:** Figma `CoVie — V1 UI Design`, page `05 — Seller Desktop` (`3:6`)  
**Shared production matrix:** `274:192`  
**Foundations:** `01 — Foundations` (`3:2`), desktop reference 1440×900, 12 columns, 24px gutters, content max 1200–1280; desktop begins at 1024 and wide at 1440.  
**Component source:** Figma `02 — Components`; Seller Sidebar is frozen at 240px and the IA order is `Tổng quan`, `Đơn hàng`, `Danh mục`, `Điểm bán`, `Cài đặt`.

## Status contract

- `TODO`: production desktop composition has not yet been implemented/audited against the exact frame.
- `IMPLEMENTED`: production route/component plus safely implementable API-backed states are mapped to the desktop composition.
- `VERIFIED`: executable/runtime/browser evidence exists for the required viewport/state.
- A documented backend/Figma contract gap may leave a sample-only field/action intentionally absent without changing `IMPLEMENTED` to fabricated UI.

Verification labels used in this branch:

- `FIGMA_AUDITED`: exact frame and component contract inspected.
- `REMOTE_STATIC_VERIFIED`: repo/OpenAPI/backend contract plus static implementation audited remotely.
- `TEST_VERIFIED`: executable lint/typecheck/test/build evidence exists.
- `RUNTIME_VERIFIED`: application boot/API behavior was exercised.
- `VISUALLY_VERIFIED`: rendered browser comparison exists for the stated viewport.
- `RUNTIME_PENDING`: connector session cannot run the local Next.js/browser stack.

## Desktop layout contract

- Frozen reference shell at 1440: `240px` Seller sidebar + `1200px` content region.
- Reference inner content is `1120px` (`40px` page padding) on the overview/settings/location board and `1128px` (`36px` padding) on dense config/catalog/order boards.
- Breakpoints follow Foundations rather than a mobile-width stretch: mobile `<640`, tablet `640–1023`, desktop `1024–1439`, wide `>=1440`.
- Required static/browser audit targets: `1024`, `1280`, `1440`; no page-level horizontal overflow at 1024.
- Seller Mobile remains the same route/data/business layer below 1024. Desktop is a distinct composition that reuses the same API/domain components and must never create a second Seller business state machine.
- Desktop navigation is IA-locked to five top-level destinations. Need, Inventory, Reputation and Notices remain contextual/sub-routes, not invented top-level tabs.

## Production coverage

| ID | Exact Figma production frame(s) | Production route / surface | Desktop composition | API / business ownership | Status |
| --- | --- | --- | --- | --- | --- |
| `SEL-001` | `358:8` — `SEL Desktop · Tổng quan` | `/seller/[merchantId]` | 240 sidebar; title/meta; 3 operational metric slots; table-like recent orders; Need match row | Merchant status, locations, orders and Need match data are server-owned. Third metric may only render when backed by real notices/operational data; no synthetic analytics. | `TODO / FIGMA_AUDITED` |
| `SEL-002` | `358:189` — `SEL Desktop · Cài đặt shop` | `/seller/[merchantId]/settings` | 760-ish profile form within desktop content; merchant media management; MD controls | Merchant PATCH uses ETag/concurrency contract already implemented by Seller Mobile. Contact edit is not inferred here. | `TODO / FIGMA_AUDITED` |
| `SEL-003` | `358:324` — `SEL Desktop · Điểm bán` | `/seller/[merchantId]/locations` | title/meta, count + add CTA, dense location rows/cards | Location capabilities/configuration come from API. | `TODO / FIGMA_AUDITED` |
| `SEL-004` | `358:324` inline edit reference + Seller Mobile canonical form `357:407` | `/seller/[merchantId]/locations/new`, `/seller/[merchantId]/locations/[locationId]` | two-column MD field composition where space allows; address and capability switches | **Contract gap retained:** backend requires lat/lng + `pinSource`; Figma has no canonical geocoder/pin picker. Coordinates are never fabricated. | `TODO / FIGMA_AUDITED` |
| `SEL-005` | `364:402` coverage card | `/seller/[merchantId]/locations/[locationId]/delivery` | left card of two-column config grid; radius/area composition | Coverage records/version token are authoritative. | `TODO / FIGMA_AUDITED` |
| `SEL-006` | `364:402` shipping card | `/seller/[merchantId]/locations/[locationId]/shipping` | right config card; fee type, conditions, ANY/ALL | FREE/FIXED/NEGOTIATED/CONDITIONAL and rule logic are backend contract; negotiated never displays `0 ₫`. | `TODO / FIGMA_AUDITED` |
| `SEL-007` | `364:402` minimum-order card | `/seller/[merchantId]/locations/[locationId]/minimum-order` | lower-left config card | Location ETag/concurrency; no local lifecycle inference. | `TODO / FIGMA_AUDITED` |
| `SEL-008` | `364:402` contact card | `/seller/[merchantId]/locations/[locationId]/contacts` | lower-right config card, QR note | PHONE/ZALO_LINK/ZALO_QR/MESSENGER_LINK/OTHER come from API. **Contract gap retained:** no safe per-contact concurrency token/detail read for PATCH edit. | `TODO / FIGMA_AUDITED` |
| `SEL-009` | `370:548` — `SEL-009 — Catalog · 1440×900` | `/seller/[merchantId]/catalog` | 520px segmented control + right add CTA; table-like 920px rows | Listing type/status comes from management summary API. **Summary contract gap:** sample media/price/location-offer/moderation detail is not fabricated if absent from API. | `TODO / FIGMA_AUDITED` |
| `SEL-010` | `370:745` — editor, product/inventory areas | `/seller/[merchantId]/catalog/new`, `/seller/[merchantId]/inventory` | two 548px columns: basic listing + offer; inventory/media cards | Generated request schemas, real category/location/variant/inventory APIs. **Existing-item edit gap retained:** no authoritative management-detail GET + concurrency token. | `TODO / FIGMA_AUDITED` |
| `SEL-011` | `370:745` service composition | `/seller/[merchantId]/catalog/new` (`SERVICE`) | same editor shell, service-safe exact-price variant presentation | Service availability/options are API-backed; service has no inventory. | `TODO / FIGMA_AUDITED` |
| `SEL-012` | `370:745` service exact-price example / variant composition | catalog create composition | compact package/variant pricing in right column | Variant/location offer values are server/request-schema owned; no standalone existing variant edit without authoritative detail/version. | `TODO / FIGMA_AUDITED` |
| `SEL-013` | `370:745` resale/product editor composition | `/seller/[merchantId]/catalog/new` (`PRODUCT + RESALE`) | same editor shell using category-provided attributes | `listingKind=RESALE`; condition/attributes only render when category API defines them. | `TODO / FIGMA_AUDITED` |
| `SEL-014` | `375:850` — `SEL-014 — Incoming Orders · 1440×900` | `/seller/[merchantId]/orders` | 420px location filter; 920px dense order rows; cursor/load-more | Summary API is authoritative. **Contract gap retained:** sample buyer name/amount/deadline/row action are omitted when summary API does not expose them. | `TODO / FIGMA_AUDITED` |
| `SEL-015` | `375:1070` — `SEL-015 — Seller Order Detail · 1440×900` | `/seller/[merchantId]/orders/[orderId]` | 690/422 split layout; buyer summary/timeline/snapshot left; action panel/privacy/fulfillment right | Every action mounts exclusively from `allowedActions`; no status-derived permission/state machine. | `TODO / FIGMA_AUDITED` |
| `SEL-016` | canonical latest `388:13366` — Reject Reason; grouped reference `382:1114` | Order reject dialog/surface from order detail | 720px reason form + 376px impact; destructive confirmation | Mounted only when backend returns `REJECT`. Backend has no canonical reason-option list, so sample dropdown options are not invented. | `TODO / FIGMA_AUDITED` |
| `SEL-017` | canonical latest `388:13477` — Cancel Reason; grouped reference `382:1114` | Seller cancel / cancellation response surface | same reason/impact desktop composition + confirmation | Mounted only from corresponding `allowedActions`; reason options are not invented. | `TODO / FIGMA_AUDITED` |
| `NEED-005` | `387:13665` — `NEED-005/006 — Seller Need · 1440×900` feed half | `/seller/[merchantId]/needs` | 420px location filter; two-column 548/564 feed-response composition | Merchant location selection, safe buyer area/distance, expiry and response state are backend-owned. Countdown is presentation-only. | `TODO / FIGMA_AUDITED` |
| `NEED-006` | `387:13665` response half | Need response composition | response summary, candidate rows, note, submit/withdraw actions | **Blocking API contract gap retained:** submit requires `locationVariantId`, but backend exposes no authoritative eligible-candidate read under the same policy. No manual UUID/reconstructed eligibility UI. | `TODO / FIGMA_AUDITED` |
| `SEL-018` | canonical latest `389:13627` — `SEL-018 — Reputation · 1440×900`; grouped reference `390:1626` | `/seller/[merchantId]/reputation` | 420px ReputationSummary + explanation card | Public aggregate reputation only; no raw history or synthetic score. | `TODO / FIGMA_AUDITED` |
| `SEL-019` | canonical latest `389:13692` — `SEL-019 — Moderation Notices · 1440×900`; grouped reference `390:1626` | `/seller/[merchantId]/notices` | 520px notice list + 576px behavior/fallback column | Ownership-filtered notifications only. **Contract gap retained:** payload has no moderation decision/reason; no type-to-route inference. | `TODO / FIGMA_AUDITED` |

## Required state matrix

Every mapped desktop surface must preserve the Seller Mobile authoritative states and add desktop composition without changing business behavior:

| State | Desktop requirement |
| --- | --- |
| loading | route/scoped loading remains bounded by the desktop content region; no layout-wide mobile skeleton stretch |
| empty | canonical `EmptyState` fits dense list/card region; add CTA only when API/route allows it |
| error + retry | scoped retry/refetch; no `window.location.reload()` loop |
| disabled / pending | mutation CTA locked and `aria-busy`/equivalent communicated; no double submit |
| confirmation | destructive order/config actions use dialog/confirmation layer with focus management |
| version/action conflict | refetch authoritative merchant/location/order state; no silent overwrite |
| no permission / restricted | only backend-returned merchant/action capability state is rendered |
| unavailable / contract gap | explanatory safe fallback; do not expose UUID/debug fields as normal production workflow |
| long content | wrap/truncate in dense desktop rows and sidebar; no page-level horizontal overflow |
| pagination/filter/search | API/query/cursor state stays authoritative; no client filtering over partial remote datasets except the local catalog type segmentation explicitly supported by the already-loaded management collection |
| accessibility | visible focus, semantic headings/labels, status text in addition to color, contextual accessible names for row actions, dialog focus trap/restore, 44px shared touch targets |

## Canonical-vs-reference duplicate frames

The Seller Desktop page contains grouped production boards plus later dedicated frames for some flows. Coverage uses the later dedicated frames as canonical where they exist:

- Reject: `388:13366` canonical; `382:1114` grouped reference.
- Cancel: `388:13477` canonical; `382:1114` grouped reference.
- Reputation: `389:13627` canonical; `390:1626` grouped reference.
- Moderation Notices: `389:13692` canonical; `390:1626` grouped reference.

No duplicate route or second business workflow will be created for these reference boards.

## Implementation order for this branch

1. Seller desktop shell/sidebar + semantic token alignment.
2. Dashboard/settings/locations/config compositions.
3. Catalog/editor/inventory compositions.
4. Orders list/detail/reason actions.
5. Need feed/response safe fallback.
6. Reputation/notices.
7. Loading/empty/error/pending/conflict states and accessibility.
8. Static 1024/1280/1440 audit, executable checks where the environment allows, then engineering handoff.
