# Buyer Mobile remaining coverage matrix

**Implementation branch:** `feature/buyer-mobile-finish-ui`  
**Base branch:** `feature/buyer-desktop-figma-ui`  
**Base SHA:** `6bf733d43ed07a731faba9dedf6f00812678e478`  
**Figma page:** `03 — Buyer Mobile` (`3:4`)  
**Rule:** Figma owns visual composition; the current backend/API owns business eligibility and transitions.

> `IMPLEMENTED / VERIFICATION_PENDING` means the scoped frontend implementation is present on the branch, but the requested typecheck/lint/test/build and browser render gates have not been executed in this connector-only environment. It is intentionally not called `VISUALLY_VERIFIED`.

| Screen | Figma node | Route | Final implementation state | Contract note | Status |
| ------ | ---------- | ----- | -------------------------- | ------------- | ------ |
| PUB-002 Location Chooser | `288:4194` (390: `289:737`, `289:783`, `289:879`; 360: `290:1021`) | Marketplace contextual chooser | Mobile chooser, GPS denied/manual fallback and saved-address selection implemented with feature-local CSS | Human-readable free-text geocoding is not exposed by the current API; raw coordinate capability is retained only as the existing manual contract | IMPLEMENTED / VERIFICATION_PENDING |
| PUB-009 Provider Profile | `312:3069` (Provider 390: `312:3289`) | `/merchants/[merchantId]` | Explicit reusable `PROVIDER` presentation added to `MerchantProfile`; Shop remains the default and unchanged route behavior | `PublicMerchantResponse` has no trustworthy Shop/Provider discriminator and no public catalog-by-merchant source; route must not select Provider heuristically | PRESENTATION_READY / CONTRACT_BLOCKED_ROUTE |
| AUTH-002 Register | `318:3572` (390: `318:3674`) | `/register` | Canonical mobile register route implemented using the existing Google/onboarding flow | No email-registration API invented | IMPLEMENTED / VERIFICATION_PENDING |
| BUY-004 Address Create/Edit | `327:4016` (390: `327:4086`; error: `327:4316`) | `/me/addresses/new`, `/me/addresses/[addressId]` | Mobile form anatomy/spacing and responsive presentation implemented while preserving save/default/coordinate behavior | Existing address contract retained | IMPLEMENTED / VERIFICATION_PENDING |
| BUY-007 My Orders | `332:4356` (390: `332:4364`; 360: `332:4518`; loading `332:4597`; empty `332:4666`; error `332:4744`) | `/orders` | Mobile list/card and loading/empty/error composition implemented | List remains read-only; no action eligibility inferred here | IMPLEMENTED / VERIFICATION_PENDING |
| ORD-001…012 Order Detail | `334:4744` and state frames under the board | `/orders/[orderId]` | Mobile detail presentation extracted around existing loader/refresh flow; cancellation/issue/completed/closed compositions covered | CTA rendering remains exclusively backed by `allowedActions`; no status-derived transition eligibility | IMPLEMENTED / VERIFICATION_PENDING |
| NEED-001 My Needs | `342:5202` (390 `342:5209`; 360 `342:5333`; state frames under board) | `/needs` | Mobile active/found/expired/list states implemented | Read-only state presentation only | IMPLEMENTED / VERIFICATION_PENDING |
| NEED-002 Create Need | `342:5413` (390; 360 `343:5661`; field error `343:5807`) | `/needs/new` | Mobile form/validation/CTA composition implemented using current fields and datetime contract | No expiry preset business logic invented | IMPLEMENTED / VERIFICATION_PENDING |
| NEED-003 Need Detail | `342:5567`; pending `342:5626`; fulfilled `342:5828`; expired `342:5869` | `/needs/[needId]` | Mobile state presentation implemented | Cancel endpoint exists, but Need detail does not expose authoritative cancel eligibility; no status-derived cancel CTA is mounted | IMPLEMENTED / CONTRACT_GAP_CANCEL |
| NEED-004 Responses | `342:5678`; no responses `342:5807`; unavailable `343:5888`; 360 `343:5949` | Need detail responses section | Available/unavailable/no-response mobile composition implemented | Checkout handler re-checks backend `actionable`; no local eligibility rule added | IMPLEMENTED / VERIFICATION_PENDING |
| BUY-009 Reputation | `348:6173` (390; 360 `348:6259`) | candidate `/me/reputation` | Figma/context audited; no production route was fabricated | Schema contains `BuyerReputationSummary`, but current account routes expose no authoritative `/me` reputation aggregate endpoint. Do not derive it from an arbitrary order or fabricate transaction history | CONTRACT_BLOCKED |
| BUY-010 Review | `348:6032` (eligible; 360 `348:6085`; state stack `348:6127`) | reusable review surface | Reusable mobile star-selector/comment/submit presentation implemented | Order Detail does not expose sufficient authoritative review eligibility + `orderItemId` to auto-mount from status; `COMPLETED` is not used as a local eligibility rule | PRESENTATION_READY / CONTRACT_BLOCKED_FLOW |
| BUY-011 Report | `348:6326` (390; states `348:6385`) | reusable report surface | Existing report business component is wired to the scoped mobile full-screen/modal presentation, existing reason codes and idempotent API flow | No moderation outcome/state invented | IMPLEMENTED / VERIFICATION_PENDING |
| Notifications | `348:6408` (390; 360 `348:6511`; states `348:6590`) | `/me/notifications` | App bar, recent inbox, read/unread, mark-all, loading/empty/fallback and responsive mobile styling implemented | Navigation accepts only backend-provided safe same-origin relative paths; notification type never invents a destination | IMPLEMENTED / VERIFICATION_PENDING |
| Profile | Buyer Mobile navigation/trust inventory | `/me` | Mobile account card/navigation composition implemented for notifications, addresses, sessions, Seller Center and logout | Reputation link is intentionally omitted until a real account reputation route exists | IMPLEMENTED / VERIFICATION_PENDING |
| Sessions | Buyer trust/security inventory | `/me/security/sessions` | Mobile session cards, current marker, revoke/revoke-others presentation and responsive layout implemented | Existing revoke contract preserved; no new current-session eligibility rule added | IMPLEMENTED / VERIFICATION_PENDING |

## Conflict risk legend

- **LOW** — new route, new component, new CSS module, new documentation.
- **MEDIUM** — existing feature component with localized markup/style changes.
- **HIGH** — `app/layout.tsx`, shared root/global CSS, `consumer-shell.tsx`, generated API client/types, shared auth helpers, or business-heavy order/need components.

## Final conflict strategy

1. Feature-local components/CSS were preferred; existing files were not moved or renamed.
2. No backend, OpenAPI, generated client, database, permissions, pricing, inventory or moderation state-machine files were changed.
3. `order-detail.tsx` is the main HIGH-risk integration hotspot; preserve newer local business loading/refresh logic and port the `OrderDetailMobile` presentation rather than resolving wholesale in favor of this branch.
4. Shared root layout/global CSS/lockfiles were avoided.
5. Provider, Buyer Reputation, Review eligibility and Need cancel eligibility are represented as explicit contract gaps rather than heuristic frontend behavior.
6. Runtime/static gates and browser screenshot comparison remain pending until the branch is executed in an environment with the repository workspace and browser runtime.
