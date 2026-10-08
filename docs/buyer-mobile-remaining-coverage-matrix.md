# Buyer Mobile remaining coverage matrix

**Implementation branch:** `codex/issue-completion`
**Base SHA:** `151c5f2`
**Figma page:** `03 — Buyer Mobile` (`3:4`)  
**Rule:** Figma owns visual composition; the current backend/API owns business eligibility and transitions.

> 2026-09-19 superseding checkpoint: every Buyer/Public row represented by the authoritative acceptance catalog now passes runtime diagnostics and hash-bound manual review. Buyer/Public contributes 27 cases / 42 captures to the 201-capture manifest. Historical pending labels below describe the earlier implementation checkpoint; current evidence is `VISUALLY_VERIFIED_LOCAL`. Staging remains separate and incomplete.

| Screen | Figma node | Route | Final implementation state | Contract note | Status |
| ------ | ---------- | ----- | -------------------------- | ------------- | ------ |
| PUB-002 Location Chooser | `288:4194` (390: `289:737`, `289:783`, `289:879`; 360: `290:1021`) | Marketplace contextual chooser | Mobile chooser, GPS denied/manual fallback and saved-address selection implemented with feature-local CSS | Human-readable free-text geocoding is not exposed by the current API; raw coordinate capability is retained only as the existing manual contract | IMPLEMENTED / VERIFICATION_PENDING |
| PUB-009 Provider Profile | `312:3069` (Shop full `312:3076`; Provider 390 `312:3289`; Shop 360 `312:3491`; states `312:3689`; acceptance `312:3743`) | `/merchants/[merchantId]` | Product-only uses Shop wording, Service-only uses Provider wording, and mixed/empty uses neutral wording from server capabilities; the page renders generated-contract Product/Service/Resale cards with isolated empty/error and opaque cursor states | `capabilities.catalogTypes` is derived from qualifying public inventory and never persisted as a Shop/Provider discriminator; the merchant-scoped catalog omits open-now, delivery and location claims when buyer location context is absent | IMPLEMENTED / VISUAL_PENDING |
| AUTH-002 Register | `318:3572` (390: `318:3674`) | `/register` | Canonical mobile register route implemented using the existing Google/onboarding flow | No email-registration API invented | IMPLEMENTED / VERIFICATION_PENDING |
| BUY-004 Address Create/Edit | `327:4016` (390: `327:4086`; error: `327:4316`); desktop `564:5448`, error `564:5613`, compact `564:5873` | `/me/addresses/new`, `/me/addresses/[addressId]` | Create/edit round trips, Figma-aligned mobile/desktop form, responsive placeholders/switch and inline backend field errors verified at 360×800, 390×844, 1024×900 and 1280×900 | Existing address contract retained; a selected saved address supplies its authoritative coordinates and parsed OpenAPI errors preserve field codes | IMPLEMENTED / VISUALLY_VERIFIED_LOCAL |
| BUY-007 My Orders | `332:4356` (390: `332:4364`; 360: `332:4518`; loading `332:4597`; empty `332:4666`; error `332:4744`) | `/orders` | Mobile list/card and loading/empty/error composition implemented | List remains read-only; no action eligibility inferred here | IMPLEMENTED / VERIFICATION_PENDING |
| ORD-001…012 Order Detail | `334:4744` and state frames under the board | `/orders/[orderId]` | Mobile detail presentation extracted around existing loader/refresh flow; cancellation/issue/completed/closed compositions covered | CTA rendering remains exclusively backed by `allowedActions`; no status-derived transition eligibility | IMPLEMENTED / VERIFICATION_PENDING |
| NEED-001 My Needs | `342:5202` (390 `342:5209`; 360 `342:5333`; state frames under board) | `/needs` | Mobile active/found/expired/list states implemented | Read-only state presentation only | IMPLEMENTED / VERIFICATION_PENDING |
| NEED-002 Create Need | `342:5413` (390; 360 `343:5661`; field error `343:5807`) | `/needs/new` | Mobile form/validation/CTA composition implemented using current fields and datetime contract | No expiry preset business logic invented | IMPLEMENTED / VERIFICATION_PENDING |
| NEED-003 Need Detail | `342:5567`; pending `342:5626`; fulfilled `342:5828`; expired `342:5869` | `/needs/[needId]` | Mobile state presentation plus inline destructive cancel confirmation, pending state, safe failure and retry are implemented | `NeedDetailResponse.allowedActions` returns `CANCEL` only while the command can accept it; the web never infers cancellation from `ACTIVE` | IMPLEMENTED / TEST_VERIFIED / VISUAL_PENDING |
| NEED-004 Responses | `342:5678`; no responses `342:5807`; unavailable `343:5888`; 360 `343:5949` | Need detail responses section | Available/unavailable/no-response mobile composition implemented | Checkout handler re-checks backend `actionable`; no local eligibility rule added | IMPLEMENTED / VERIFICATION_PENDING |
| BUY-009 Reputation | `348:6173` (390; 360 `348:6259`; desktop `566:8794`) | `/me/reputation` | Authenticated route, loading/aggregate/zero-history/warning/retry states and Profile link implemented from the exact frame context | `GET /api/v1/me/reputation` returns only the signed-in buyer aggregate with private/no-store caching; no transaction history or review stars are fabricated | IMPLEMENTED / VISUAL_PENDING |
| BUY-010 Review | `348:6032` (eligible; 360 `348:6085`; state stack `348:6127`; desktop `566:8427`) | reusable review surface in `/orders/[orderId]` | Buyer desktop/mobile order detail renders eligible, already-reviewed, not-completed, not-reviewable and creation-disabled states from each item contract; success and stale conflicts refresh the order | `OrderItemResponse.reviewEligibility` and its authoritative `orderItemId` are buyer-only and server-derived through the same evaluator used by review creation; no raw-status rule exists in web code | IMPLEMENTED / VISUAL_PENDING |
| BUY-011 Report | `348:6326` (390; states `348:6385`) | reusable report surface | Existing report business component is wired to the scoped mobile full-screen/modal presentation, existing reason codes and idempotent API flow | No moderation outcome/state invented | IMPLEMENTED / VERIFICATION_PENDING |
| Notifications | `348:6408` (390; 360 `348:6511`; states `348:6590`) | `/me/notifications` | App bar, recent inbox, read/unread, mark-all, loading/empty/fallback and responsive mobile styling implemented | Navigation accepts only backend-provided safe same-origin relative paths; notification type never invents a destination | IMPLEMENTED / VERIFICATION_PENDING |
| Profile | Buyer Mobile navigation/trust inventory | `/me` | Mobile account card/navigation composition includes notifications, reputation, addresses, sessions, Seller Center and logout | Reputation link targets the authenticated `/me/reputation` route | IMPLEMENTED / VISUAL_PENDING |
| Sessions | Buyer trust/security inventory | `/me/security/sessions` | Mobile session cards, current marker, revoke/revoke-others presentation and responsive layout implemented | Existing revoke contract preserved; no new current-session eligibility rule added | IMPLEMENTED / VERIFICATION_PENDING |

## Conflict risk legend

- **LOW** — new route, new component, new CSS module, new documentation.
- **MEDIUM** — existing feature component with localized markup/style changes.
- **HIGH** — `app/layout.tsx`, shared root/global CSS, `consumer-shell.tsx`, generated API client/types, shared auth helpers, or business-heavy order/need components.

## Final conflict strategy

1. Feature-local components/CSS were preferred; existing files were not moved or renamed.
2. Backend/OpenAPI/generated-client changes are limited to authoritative Need cancel eligibility; database, pricing, inventory and moderation state machines are unchanged.
3. `order-detail.tsx` is the main HIGH-risk integration hotspot; preserve newer local business loading/refresh logic and port the `OrderDetailMobile` presentation rather than resolving wholesale in favor of this branch.
4. Shared root layout/global CSS/lockfiles were avoided.
5. PUB-009 Provider/public catalog, Buyer Reputation, Review eligibility and Need cancel eligibility are authoritative API-backed flows with current runtime/Figma hash-bound evidence.
6. BUY-004 passed on 2026-09-16; the complete Buyer/Public batch passed 42/42 captures on 2026-09-19, including NEED-003 and every formerly pending row in the acceptance catalog.
