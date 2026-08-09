# CoVie V1 — UI Handoff Matrix

**Visual source of truth:** [CoVie — V1 UI Design](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=0-1)  
**Status:** Figma production units frozen; implementation mapping started in Plan 01.  
**Implementation target:** Plan 09. This matrix is a handoff aid, not a replacement for API contracts or detailed plan acceptance criteria.

## Rules

- Figma defines layout, visual tokens, component states and responsive intent.
- Spring Boot owns authorization, pricing, inventory, order actions and all business state.
- Frontend consumes generated OpenAPI types; it does not copy DTOs or infer legal state transitions.
- Each mapped screen needs loading, empty, error and unauthorized behavior before its Plan 09 acceptance is marked complete.

## Shared foundations

| Figma area | Code target | First implementation point |
|---|---|---|
| Foundations: color, type, spacing, radius, elevation | CSS custom properties and shared UI primitives | Plan 09 |
| Components: action, input, selection, navigation, feedback, overlay | `apps/web/components/ui` and `apps/web/components/layout` | Plan 09 |
| Marketplace, commerce, order, need and trust components | Feature-owned presentation components | Plans 03–09, completed in Plan 09 |
| Seller/Admin navigation and data views | Seller/Admin route layouts and feature components | Plans 05–09, completed in Plan 09 |

## P0 screen-to-delivery map

| Journey / Figma group | Route(s) | Backend capability prerequisite | Primary acceptance test |
|---|---|---|---|
| Public discovery | `/`, `/search`, `/category/[slug]`, `/items/[itemId]`, `/merchants/[merchantId]` | Plan 03 catalog/search | Guest discovery E2E |
| Authentication | `/login`, `/register`, `/onboarding/password`, `/reset-password` | Plan 02 identity/auth | Auth journey tests |
| Cart and checkout | `/cart`, `/cart/[cartId]`, `/checkout`, `/checkout/success/[orderId]` | Plan 04 cart, shipping, inventory, checkout | Buyer product-order E2E |
| Buyer orders | `/orders`, `/orders/[orderId]` | Plan 05 order read model and allowed actions | Product/service lifecycle E2E |
| Needs | `/needs`, `/needs/new`, `/needs/[needId]` | Plan 06 matching and sourced checkout | Need-flow E2E |
| Account | `/me`, `/me/addresses`, `/me/notifications`, `/me/security/sessions` | Plans 02 and 07 | Auth/session/notification tests |
| Seller center | `/seller/[merchantId]/*` | Plans 03–06 catalog, inventory, orders and needs | Seller-management E2E |
| Admin desktop | `/admin/settings`, `/admin/moderation/*`, `/admin/reports`, `/admin/users`, `/admin/merchants`, `/admin/orders`, `/admin/audit` | Plan 08 moderation/settings/admin | Admin control-plane E2E |

## Per-screen completion record

For each screen completed in Plan 09, add a row with: Figma frame URL, route, component owner, OpenAPI operation(s), required states, Playwright spec, visual-review date and reviewer. A screen cannot be marked complete from a screenshot alone.
