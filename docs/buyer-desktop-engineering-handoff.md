# Buyer Desktop — Engineering Handoff

> Integrated acceptance update (2026-09-01): Buyer Desktop is integrated at `fc93244` and passes the local responsive route/state, Axe and overflow matrix. See `integrated-ui-verification-2026-09-01.md`. Human visual review and staging remain open.

## Source of truth

- Figma file: `CoVie — V1 UI Design`
- Figma file key: `4LcMXFQz3BnU1EXzBk69t2`
- Production page: `03B — Buyer Desktop`
- Implementation branch: `feature/buyer-desktop-figma-ui`
- Baseline branch: `feature/public-buyer-figma-ui`

Buyer Desktop is a responsive extension of the existing buyer experience, not a separate business application. Mobile behavior remains supported below the desktop breakpoints.

## Responsive contract

| Zone | Width | Shell |
| --- | --- | --- |
| Mobile | 360–767 | Existing mobile buyer shell and bottom navigation |
| Tablet | 768–1023 | Existing responsive/mobile shell |
| Compact Desktop | 1024–1279 | Buyer desktop header, constrained content, compact desktop composition |
| Full Desktop | 1280+ | Buyer desktop header, max-width content, expanded multi-column composition |

The canonical Buyer Desktop header is implemented in `apps/web/components/layout/buyer-desktop-header.tsx` and mounted from the consumer shell. It reuses the project logo/assets, search, location state and existing buyer information architecture.

## Production route coverage

Buyer Desktop coverage includes:

- Public discovery and search
- Standard product, service and resale detail
- Public merchant profile
- Public rating/review lists and reporting
- Authentication: login, Google login, onboarding password and reset password
- Cart
- Checkout preview, changed-checkout recovery and order-created confirmation
- Saved address list/create/edit
- Buyer order list and state-driven order detail
- Need list/create/detail/response-to-checkout flow
- Notifications
- Profile and active-session security management

## Backend ownership contract

The UI must not infer business eligibility from presentation status.

### Orders

`OrderDetailResponse.allowedActions` is the only source of truth for order commands. `OrderActions` must not expose a CTA merely because an order has a particular `status`. Status labels, timeline labels and chips are presentation-only.

### Need requests

Need response purchase actions are rendered only when the response item is `actionable` according to the backend. `NeedDetailResponse` currently does not expose an action-eligibility field for cancelling the need, so Buyer Desktop intentionally does not render a cancel CTA based only on `status=ACTIVE`, even though the API has a cancellation endpoint.

### Checkout

Checkout uses preview → commit. A logical commit keeps a stable `Idempotency-Key` while it is retried. When the backend returns `CHECKOUT_CHANGED`, the old preview and commit key are invalidated and the user must preview again. The order-created screen does not infer the next lifecycle state; it directs the buyer to the backend-driven order detail.

### Notifications

Notification navigation is backend-owned. Buyer Desktop accepts only a relative same-origin path supplied in notification data (`deepLink`, `href`, `path` or `route`) and rejects unsafe/external paths. It does not map notification `type` to an invented route.

### Reviews

Public catalog and merchant review lists are backed by the review endpoints and display `verifiedPurchase` exactly as returned. The review creation API requires an `orderItemId`, but `OrderDetailResponse` currently does not expose explicit review eligibility. For that reason the reusable `ReviewForm` exists but is intentionally not mounted automatically from `COMPLETED` or any other order status. Mount it only when the backend exposes an explicit reviewable/review-eligibility contract.

### Reports / moderation

Buyer Desktop supports explicit user reporting for public catalog items, merchants and public reviews through `POST /api/v1/reports`. The report target is passed directly (`CATALOG_ITEM`, `MERCHANT`, or `REVIEW`) and the returned moderation case ID/status is treated as backend-owned state.

## Design-system implementation notes

- Existing Emerald/Slate semantic language is preserved.
- Existing typography, spacing, radius, elevation and focus treatment are reused rather than introducing a separate Buyer Desktop theme.
- Desktop controls use the compact control density established by the Figma system; mobile controls retain mobile sizing.
- Buyer Desktop header is the only new shell-level navigation pattern required by the desktop extension.
- Trust/report/review presentation is implemented in `apps/web/app/buyer-trust.css`; the main responsive Buyer Desktop layer is in `apps/web/app/buyer-desktop.css`.

## Accessibility contract

- Desktop navigation and icon actions expose accessible labels/current-page state.
- Keyboard focus uses the existing emerald focus treatment.
- Forms have explicit labels and error/status regions.
- Reporting uses a modal dialog with labelled title and visible close/cancel actions.
- Review stars include text equivalents for screen readers.
- Mobile bottom navigation is removed from the accessibility tree by responsive display when desktop header navigation is active.

## Validation gate

The branch is considered ready for handoff only after the pull-request CI passes all repository gates defined in `.github/workflows/ci.yml`:

1. backend `mvn verify`
2. OpenAPI export and generated-contract drift check
3. API-client typecheck
4. web TypeScript typecheck
5. web lint
6. web tests
7. web production build
8. secret-hygiene checks
9. source/misconfiguration/secret security scan

Do not merge this branch based solely on visual review if one of those gates is red.

## Intentional V1 constraints

- No dark mode is introduced by Buyer Desktop.
- Service flow does not invent calendar booking; scheduling remains coordinated outside a V1 calendar feature.
- Need cancellation is not surfaced until eligibility is explicit in the backend contract.
- Review creation is not surfaced from order status alone until review eligibility is explicit in the backend contract.
- Admin order history remains read-only and no Buyer Desktop work introduces force-status behavior.
