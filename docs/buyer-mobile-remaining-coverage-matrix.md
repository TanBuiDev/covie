# Buyer Mobile remaining coverage matrix

**Implementation branch:** `feature/buyer-mobile-finish-ui`  
**Base branch:** `feature/buyer-desktop-figma-ui`  
**Base SHA:** `6bf733d43ed07a731faba9dedf6f00812678e478`  
**Figma page:** `03 — Buyer Mobile` (`3:4`)  
**Rule:** Figma owns visual composition; the current backend/API owns business eligibility and transitions.

| Screen | Figma node | Route | Current remote state | Work needed | Files touched | Contract gap | Status |
| ------ | ---------- | ----- | -------------------- | ----------- | ------------- | ------------ | ------ |
| PUB-002 Location Chooser | `288:4194` (390: `289:737`, `289:783`, `289:879`; 360: `290:1021`) | Marketplace contextual chooser | Existing `LocationChooser` supports GPS + raw lat/lng manual form; Home mounts it inline | Replace raw-coordinate-first presentation with frozen bottom-sheet/manual/saved-address composition while preserving `BrowseLocation` storage contract | Prefer new feature-local CSS/presentation; minimal `home-screen.tsx` / `location-chooser.tsx` wiring | Manual human-readable geocoding/search is not exposed by current API; do not invent it | IN_PROGRESS |
| PUB-009 Provider Profile | `312:3069` (Provider 390: `312:3289`) | `/merchants/[merchantId]` | Shop profile first pass exists and renders contract-backed identity/reputation/reviews/contacts/locations | Add contract-safe mobile Provider visual composition where authoritative data supports it; keep generic fallback | Prefer `merchant-profile.module.css` + small presentation extraction only | No authoritative Shop/Provider discriminator, public catalog-by-merchant, open/capability contract | CONTRACT_BLOCKED_PARTIAL |
| AUTH-002 Register | `318:3572` (390: `318:3674`) | `/register` | `/login`, onboarding password, reset exist; no canonical `/register` | Add mobile register route reflecting existing Google/onboarding auth flow; loading/failure/navigation to login; 360 responsive | New route + scoped auth CSS/component where possible | No invented email registration API | TODO |
| BUY-004 Address Create/Edit | `327:4016` (390: `327:4086`; error: `327:4316`) | `/me/addresses/new`, `/me/addresses/[addressId]` | Create/update flow, field errors, default address and coordinates already implemented | Frozen mobile form spacing/AppBar/input/textarea/pin-source/default switch/save CTA; 390/360 overflow-safe | `address-form.module.css`; minimal `address-form.tsx` markup only if required | None for existing save contract | TODO |
| BUY-007 My Orders | `332:4356` (390: `332:4364`; 360: `332:4518`; loading `332:4597`; empty `332:4666`; error `332:4744`) | `/orders` | Real API-backed list with loading/empty/error exists | Mobile card/list visual composition and responsive states; no list action eligibility inference | Prefer new buyer-only module/component; minimal `order-list.tsx` | None for read-only list; actions remain detail-only | TODO |
| ORD-001…012 Order Detail | `334:4744` (confirmed `334:4869`; 360 `334:4951`; cancellation `334:5089`/`334:5142`; delivery issue `334:5216`; no-show `334:5318`; completed `334:5377`; closed `334:5453`; completion confirm `334:5526`; service issue `336:5147`) | `/orders/[orderId]` | Detail/timeline/context cards/actions exist; `OrderActions` uses backend `allowedActions` | Add frozen mobile header/summary/context/timeline/action composition without changing refresh/business flow | Prefer new presentation components + CSS; keep `order-detail.tsx` diff narrow | CTA eligibility must remain `allowedActions` only | TODO |
| NEED-001 My Needs | `342:5202` (390 `342:5209`; 360 `342:5333`; Found `344:5891`; Expired `344:5977`; loading/error `344:6061`) | `/needs` | API-backed list exists | Active/Found/Expired/loading/error mobile states and 390/360 layout | Feature-local presentation/CSS; minimal `need-list.tsx` | None for read-only states | TODO |
| NEED-002 Create Need | `342:5413` (390; 360 `343:5661`; field error `343:5807`) | `/needs/new` | Create flow exists | Frozen mobile fields, validation, CTA and responsive form | Feature-local CSS; minimal `need-create-form.tsx` | None for existing create contract | TODO |
| NEED-003 Need Detail | `342:5567`; pending `342:5626`; fulfilled `342:5828`; expired `342:5869` | `/needs/[needId]` | Detail exists | State-driven mobile composition while preserving backend data | Presentation wrapper/CSS; minimal `need-detail.tsx` | Backend does not expose authoritative cancel eligibility; do not mount status-derived cancel CTA | CONTRACT_GAP |
| NEED-004 Responses | `342:5678`; no responses `342:5807`; unavailable `343:5888`; 360 `343:5949` | Need detail responses section | Response list and `actionable` handling exist | Frozen available/unavailable/no-response states and responsive layout | Presentation/CSS; minimal `need-response-list.tsx` | Purchase only when backend marks response actionable | TODO |
| BUY-009 Reputation | `348:6173` (390; 360 `348:6259`) | inspect convention; candidate `/me/reputation` | No canonical Buyer reputation route | Render existing aggregate if available; loading/error/safe missing state | Prefer new route/component/CSS | Do not fabricate history when only aggregate exists | AUDIT_CONTRACT |
| BUY-010 Review | `348:6032` (eligible; 360 `348:6085`; state stack `348:6127`) | reusable review surface | `ReviewForm` exists | Complete frozen standalone/reusable visual states; mount only with authoritative item/eligibility | Review feature-local CSS/component | Order detail lacks explicit review eligibility/orderItemId sufficient for automatic CTA | CONTRACT_BLOCKED_FLOW |
| BUY-011 Report | `348:6326` (390; states `348:6385`) | reusable report surface | Report API/component exists, currently reusable dialog-style UI | Adapt mobile presentation to frozen composition without forking business logic; keep existing reason contract | Report feature-local CSS/wrapper | No moderation state invention | TODO |
| Notifications | `348:6408` (390; 360 `348:6511`; states `348:6590`) | `/me/notifications` | Inbox, read/unread, mark-all-read and safe deep-link helper exist | Frozen mobile inbox + loading/empty/fallback states | Prefer scoped notification CSS; minimal inbox JSX | Deep links must remain backend-provided safe same-origin relative routes | TODO |
| Profile | page inventory under Buyer Mobile + Trust board | `/me` | Profile overview exists | Mobile composition/navigation to addresses, reputation, notifications, security | Scoped profile CSS; minimal overview JSX | Do not invent unavailable trust data | TODO |
| Sessions | Buyer trust/security inventory | `/me/security/sessions` | Session list/revoke flow exists | Mobile security/session cards and responsive states | Scoped session CSS/component | None for existing revoke/current-session contract | TODO |

## Conflict risk legend

- **LOW** — new route, new component, new CSS module, new documentation.
- **MEDIUM** — existing feature component with localized markup/style changes.
- **HIGH** — `app/layout.tsx`, shared root/global CSS, `consumer-shell.tsx`, generated API client/types, shared auth helpers, or business-heavy order/need components.

## Initial conflict strategy

1. Add feature-local components/CSS first; do not move or rename existing files.
2. Keep `order-detail.tsx`, Need components, profile/session components and Home wiring changes as narrow as possible.
3. Do not modify backend, OpenAPI, generated client, state machines, pricing, inventory, permissions or moderation logic.
4. Avoid `app/layout.tsx`, `consumer-shell.tsx`, global CSS and lockfiles unless a direct blocker is proven.
5. Each screen/domain is committed independently so local integration can cherry-pick around conflicts.
