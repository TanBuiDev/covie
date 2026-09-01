# Seller Desktop — Figma Coverage Matrix

> Integrated acceptance update (2026-09-01): Seller Desktop runtime acceptance is complete locally at `fc93244` across the required desktop widths, with route landmarks, Axe and overflow assertions. See `integrated-ui-verification-2026-09-01.md`. PR CI, human visual review and staging remain open.

**Implementation branch:** `feature/seller-desktop-figma-ui`  
**Baseline:** `feature/seller-mobile-figma-ui` @ `aae96f38ce7a4b7ecfa5bed40a6489ddcffa3078`  
**Baseline relationship:** Seller Mobile was 88 commits ahead / 0 behind `main` when the branch was audited. The separate `codex/plan-10-seller-mobile-ci` branch only contributed CI-oriented divergence and was not used as the implementation base.  
**Visual source of truth:** Figma `CoVie — V1 UI Design`, page `05 — Seller Desktop` (`3:6`)  
**Shared production matrix:** `274:192`  
**Foundations:** `01 — Foundations` (`3:2`), desktop reference 1440×900; desktop starts at 1024.  
**Component source:** `02 — Components`; Seller Sidebar is frozen at 240px and IA order is `Tổng quan`, `Đơn hàng`, `Danh mục`, `Điểm bán`, `Cài đặt`.

## Status contract

- `IMPLEMENTED`: production route/component and safely implementable API-backed desktop states are mapped to the Figma composition.
- `FIGMA_AUDITED`: exact production frame/component contract inspected.
- `REMOTE_STATIC_VERIFIED`: repo/API ownership, source composition, responsive CSS, and contract-gap handling audited remotely.
- `TEST_VERIFIED`: executable lint/typecheck/test/build evidence exists for the recorded implementation tree.
- `RUNTIME_VERIFIED`: local/full-stack application behavior was exercised.
- `VISUALLY_VERIFIED`: rendered-browser comparison exists for the stated viewport/state.
- `CONTRACT_GAP`: Figma asks for a workflow/detail that current authoritative backend contracts do not safely expose; the production UI deliberately does not fabricate it.
- `TEST_PENDING`, `RUNTIME_PENDING`, `VISUAL_PENDING`: corresponding final-gate evidence has not yet been produced.

A documented backend/Figma contract gap is not treated as permission to hard-code sample data, reconstruct server authorization, or expose UUID/debug input as a normal Seller workflow.

## Desktop layout contract

- Frozen 1440 reference shell: `240px` Seller sidebar + `1200px` content region.
- Reference inner content is approximately `1120px` on overview/settings/location boards and `1128px` on denser config/catalog/order boards.
- Breakpoints follow Foundations: mobile `<640`, tablet `640–1023`, desktop `1024–1439`, wide `>=1440`.
- Required browser audit targets: `1024`, `1280`, `1440`.
- No page-level horizontal overflow is acceptable at 1024.
- Seller Mobile stays on the same route/data/business layer below 1024. Desktop is a distinct composition reusing the same API/domain behavior.
- Desktop navigation remains locked to five top-level destinations. Need, Inventory, Reputation and Notices remain contextual/sub-routes.

## Production coverage

| ID | Exact Figma production frame(s) | Production route / surface | Desktop composition | API / business ownership | Status |
| --- | --- | --- | --- | --- | --- |
| `SEL-001` | `358:8` — `SEL Desktop · Tổng quan` | `/seller/[merchantId]` | 240 sidebar; title/meta; operational metric cards; dense recent orders; Need match row | Merchant status, locations, orders and Need match data remain server-owned. No synthetic analytics. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-002` | `358:189` — `SEL Desktop · Cài đặt shop` | `/seller/[merchantId]/settings` | desktop profile/media form composition | Merchant mutation keeps existing ETag/concurrency and upload contracts. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-003` | `358:324` — `SEL Desktop · Điểm bán` | `/seller/[merchantId]/locations` | title/meta, add CTA, dense responsive location cards | Capabilities/config state come from API. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-004` | `358:324` inline edit reference + Seller Mobile canonical form `357:407` | `/seller/[merchantId]/locations/new`, `/seller/[merchantId]/locations/[locationId]` | responsive two-column desktop field composition | Backend requires lat/lng + `pinSource`; no canonical Figma geocoder/pin contract, so coordinates are never fabricated. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-005` | `364:402` coverage card | `/seller/[merchantId]/locations/[locationId]/delivery` | desktop config-card composition | Coverage records/version state remain authoritative. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-006` | `364:402` shipping card | `/seller/[merchantId]/locations/[locationId]/shipping` | desktop shipping/rule composition | FREE/FIXED/NEGOTIATED/CONDITIONAL and ANY/ALL logic remain backend contract; negotiated is never displayed as `0 ₫`. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-007` | `364:402` minimum-order card | `/seller/[merchantId]/locations/[locationId]/minimum-order` | desktop config-card composition | Location ETag/concurrency remains authoritative. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-008` | `364:402` contact card | `/seller/[merchantId]/locations/[locationId]/contacts` | desktop contact card/form composition | Contact types come from API. No safe per-contact concurrency/detail contract is invented for PATCH edit. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-009` | `370:548` — `SEL-009 — Catalog · 1440×900` | `/seller/[merchantId]/catalog` | segmented desktop management controls + dense Catalog rows/cards | Listing type/status comes from management summary API. Sample-only media/price/offer/moderation details are not fabricated. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-010` | `370:745` — editor/product/inventory board | `/seller/[merchantId]/catalog/new`, `/seller/[merchantId]/inventory` | two-column desktop editor; inventory/media/offer sections | Generated request schemas and real category/location/variant/inventory APIs remain authoritative. Existing-item edit lacks authoritative detail+version contract. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-011` | `370:745` service composition | `/seller/[merchantId]/catalog/new` (`SERVICE`) | service-safe editor composition | Service availability/options/pricing use request/API state; service has no inventory. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-012` | `370:745` variant pricing composition | Catalog create composition | compact package/variant pricing within editor | Variant/location offer values remain caller/request-schema owned; no standalone existing variant edit is invented. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-013` | `370:745` resale/product editor composition | `/seller/[merchantId]/catalog/new` (`PRODUCT + RESALE`) | same editor using category-provided attributes | `listingKind=RESALE`; condition/attributes render only when category API defines them. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-014` | `375:850` — `SEL-014 — Incoming Orders · 1440×900` | `/seller/[merchantId]/orders` | desktop location filter + dense order rows/cards + cursor load-more | Summary API stays authoritative. Buyer name/amount/deadline/action are not invented if absent from summary contract. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-015` | `375:1070` — `SEL-015 — Seller Order Detail · 1440×900` | `/seller/[merchantId]/orders/[orderId]` | split detail/action desktop composition | Every action mounts from backend `allowedActions`; frontend does not derive permission from raw status. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-016` | canonical `388:13366` — Reject Reason; grouped `382:1114` | order reject surface from detail | desktop reason/impact/confirmation composition | Mounted only when backend authorizes `REJECT`; no backend reason taxonomy exists, so Figma sample options are not invented. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-017` | canonical `388:13477` — Cancel Reason; grouped `382:1114` | seller cancel/cancellation response surface | same reason/impact desktop composition | Mounted only from allowed actions; reason-option taxonomy is not invented. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `NEED-005` | `387:13665` — `NEED-005/006 — Seller Need · 1440×900` feed half | `/seller/[merchantId]/needs` | desktop location filter + feed/response composition | Merchant location, safe buyer area/distance, expiry and response state are backend-owned. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `NEED-006` | `387:13665` response half | Need response composition | response summary and safe unavailable state where candidate contract is absent | Submission requires `locationVariantId`, but backend exposes no authoritative eligible-candidate read under the same policy. No manual UUID/reconstructed eligibility UI. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-018` | canonical `389:13627` — `SEL-018 — Reputation · 1440×900`; grouped `390:1626` | `/seller/[merchantId]/reputation` | desktop reputation summary/explanation composition | Public aggregate reputation only; no raw history or synthetic score. | `IMPLEMENTED / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| `SEL-019` | canonical `389:13692` — `SEL-019 — Moderation Notices · 1440×900`; grouped `390:1626` | `/seller/[merchantId]/notices` | desktop notice list + behavior/fallback composition | Ownership-filtered notifications only. Payload has no canonical moderation decision/reason/destination, so no type-to-route inference. | `IMPLEMENTED / CONTRACT_GAP / FIGMA_AUDITED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |

## Required state matrix

| State | Desktop requirement | Implementation state |
| --- | --- | --- |
| loading | scoped loading stays bounded by desktop content region | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| empty | canonical empty state fits dense list/card region; CTA only when route/API allows it | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| error + retry | scoped retry/refetch; no `window.location.reload()` loop | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| disabled / pending | mutation CTA locked; busy/pending semantics retained | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| confirmation | destructive order/config actions retain dialog/confirmation behavior | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| version/action conflict | refetch authoritative state; no silent overwrite | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| no permission / restricted | render only backend merchant/action capability state | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| unavailable / contract gap | safe explanatory fallback; no UUID/debug workflow | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| long content | desktop rows/sidebar wrap or truncate without planned page overflow | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / VISUAL_PENDING` |
| pagination/filter/search | API/query/cursor state remains authoritative; no filtering over partial remote data except already-loaded Catalog type segmentation | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED` |
| accessibility | visible focus, semantic labels/headings, status text plus color, contextual actions, existing dialog focus behavior, shared touch targets | `IMPLEMENTED / REMOTE_STATIC_VERIFIED / TEST_VERIFIED / RUNTIME_PENDING` |

## Verification evidence

### Figma audit

The canonical production frames and frozen shared Seller component contracts were inspected before bulk implementation. Desktop structure is based on the real Figma page rather than inferred from Seller Mobile.

### Remote static verification

Completed against the Seller Desktop branch:

- branch/base relationship and diff audited;
- desktop CSS isolated in Seller-specific responsive files;
- five-item IA preserved;
- existing Seller route/data layer reused;
- no synthetic order/Need/catalog permission state introduced;
- documented backend/Figma gaps remain safe fallbacks instead of fabricated production data;
- desktop compact rules exist for the required 1024/intermediate widths.

### Executable verification

`TEST_VERIFIED` for application code head `266e9019bedc3b870daa32edbd6361fdf93c772a`.

The exact application tree was mirrored to the CI-only branch `codex/plan-10-seller-desktop-ci`, where GitHub Actions run `33037040294` (wrapper commit `0b1468baa6f17c143cd5e7d8b49937fabff1c025`) completed the full web gate successfully:

- TypeScript typecheck: **PASS**.
- ESLint: **PASS**, 0 errors; only 2 `@next/next/no-img-element` warnings in existing Catalog image components.
- Vitest: **PASS**, 83/83 tests.
- Next.js production build: **PASS**.

The repository's normal CI is independently blocked by pre-existing generated API-client drift. Run `33037040323` exported OpenAPI and generated the client successfully, then failed at `Check generated contract drift`, so its later web steps were skipped. The same drift existed on the Seller Mobile baseline and is not classified as a Seller Desktop regression. `release-security-source` passed on that normal CI run.

Documentation commits after `266e9019...` only record verification state and do not alter the application tree covered by this evidence.

### Runtime and visual verification

`RUNTIME_PENDING / VISUAL_PENDING`.

Final gate still requires actual rendered Seller flows and screenshots/equivalent comparison at 1024, 1280, and 1440 with representative authoritative data. Static source inspection and executable tests/build must not be labeled `VISUALLY_VERIFIED`.

## Canonical-vs-reference duplicate frames

- Reject: `388:13366` canonical; `382:1114` grouped reference.
- Cancel: `388:13477` canonical; `382:1114` grouped reference.
- Reputation: `389:13627` canonical; `390:1626` grouped reference.
- Moderation Notices: `389:13692` canonical; `390:1626` grouped reference.

No duplicate route or second business workflow is created for the grouped reference boards.

## Final-gate rule

The branch must not be labeled `Seller Desktop — COMPLETE`, described as pixel-perfect, or opened as the final PR until executable checks and required browser/visual verification are complete. Do not merge automatically.
