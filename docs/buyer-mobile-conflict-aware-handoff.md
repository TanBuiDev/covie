# Buyer Mobile conflict-aware handoff

## Branch / base

- Repository: `TanBuiDev/covie`
- Implementation branch: `feature/buyer-mobile-finish-ui`
- Base branch: `feature/buyer-desktop-figma-ui`
- Base SHA: `6bf733d43ed07a731faba9dedf6f00812678e478`
- Figma visual source: `03 — Buyer Mobile` (`3:4`)
- Behavior source: current API/backend contract on the selected base; frontend does not own lifecycle transitions or eligibility.

The handoff document is itself a branch commit, so the final branch HEAD is intentionally reported in the delivery message rather than self-referencing a SHA that changes when this file is edited.

## Implemented scope

### Authentication
- Added canonical `/register` mobile route and scoped styling.
- Reuses the existing Google/onboarding authentication behavior.
- Does not invent an email-registration endpoint.

### Location
- Finished mobile Location Chooser presentation for current location, GPS-denied fallback, manual coordinates and saved addresses.
- Preserves the existing `BrowseLocation` storage/selection behavior.
- Does not pretend free-text geocoding exists when the API does not expose it.

### Addresses
- Finished Buyer mobile create/edit address presentation with existing save, field-error, default-address and coordinate behavior preserved.

### Orders
- Finished Buyer order list mobile composition and loading/empty/error states.
- Added `order-detail-mobile.tsx` + scoped CSS as the presentation layer for Order Detail states.
- Kept `OrderActions` / backend `allowedActions` as the only source for order CTA eligibility.
- No status-to-action transition table was added to the frontend.

### Needs
- Finished My Needs, Create Need, Need Detail and response presentation for mobile.
- Response checkout continues to require backend `actionable` and checks it immediately before checkout.
- Need cancellation is not rendered from `ACTIVE` or another local status because the detail contract does not expose authoritative cancel eligibility.

### Trust / Reviews / Reports
- Finished reusable Buyer mobile review form presentation (1–5 star selector, optional comment, submit/success/error behavior) using the existing review API.
- Did not auto-mount review CTA from `COMPLETED`; current Order Detail does not expose sufficient authoritative review eligibility + `orderItemId` for that flow.
- Finished report mobile presentation and wired it back into the existing `ReportButton`, reason codes, idempotency and moderation API behavior.
- Added optional report target title/meta presentation so the mobile report sheet can show the Figma “NỘI DUNG ĐANG BÁO CÁO” card only when the caller supplies authoritative display data.
- Catalog Detail and Merchant Profile pass their real API-backed name/type/location/merchant context to the report sheet; missing target context is omitted rather than fabricated.
- No moderation result/state was invented.

### Notifications
- Finished Buyer mobile notification app bar, recent inbox, read/unread presentation, loading/empty/fallback, mark-all-read and responsive composition.
- Existing `notificationDeepLink()` remains backend-owned: only safe same-origin relative paths from notification data are accepted; type is never mapped to an invented destination.

### Profile / Security
- Finished mobile Profile overview using scoped CSS for notifications, addresses, sessions, Seller Center and logout.
- Finished mobile sessions/security cards while preserving existing single-session and revoke-others calls.
- No Buyer Reputation link is exposed because no authoritative account reputation route exists in the current contract.

### Public Provider presentation
- Added an explicit `PROVIDER` presentation variant to `MerchantProfile`, plus exported `ProviderProfile` wrapper.
- Shop remains the default behavior for `/merchants/[merchantId]`.
- The route does **not** choose Provider heuristically because `PublicMerchantResponse` currently exposes no trustworthy Shop/Provider discriminator.
- Public service/catalog cards are not fabricated when the current public merchant response does not expose catalog-by-merchant data.

## Contract-blocked items

These are deliberately not implemented as guessed frontend behavior:

1. **PUB-009 automatic Shop vs Provider route selection**
   - Missing authoritative discriminator on the current public merchant response.
   - Safe implementation present: explicit Provider presentation wrapper for a future authoritative caller.

2. **BUY-009 Buyer Reputation account route**
   - Generated contract has a `BuyerReputationSummary` shape, but current account routes do not expose an authoritative `/me` reputation aggregate endpoint.
   - Do not derive self reputation from an arbitrary Order response and do not fabricate transaction history.

3. **BUY-010 automatic Review CTA from Order Detail**
   - Review component/API exist.
   - Current detail flow does not provide explicit authoritative review eligibility + the target `orderItemId` required to mount the form safely.
   - Do not implement `status === COMPLETED => canReview` locally.

4. **NEED-003 cancel CTA**
   - Cancel API exists, but Need Detail does not provide authoritative cancel eligibility.
   - Do not implement `status === ACTIVE => canCancel` locally.

## Files added

- `apps/web/app/(auth)/register/page.tsx`
- `apps/web/app/(auth)/register/register.module.css`
- `apps/web/app/(buyer)/needs/needs-mobile.module.css`
- `apps/web/app/(buyer)/orders/orders-mobile.module.css`
- `apps/web/features/auth/session-list.module.css`
- `apps/web/features/location/location-chooser.module.css`
- `apps/web/features/needs/buyer-needs-mobile.module.css`
- `apps/web/features/notifications/notification-inbox.module.css`
- `apps/web/features/orders/order-detail-mobile.module.css`
- `apps/web/features/orders/order-detail-mobile.tsx`
- `apps/web/features/orders/order-list-mobile.module.css`
- `apps/web/features/profile/profile-overview.module.css`
- `apps/web/features/reports/report-button.module.css`
- `apps/web/features/reviews/review-form.module.css`
- `docs/buyer-mobile-remaining-coverage-matrix.md`
- `docs/buyer-mobile-conflict-aware-handoff.md`

## Existing files modified

- `apps/web/app/(buyer)/me/security/sessions/page.tsx`
- `apps/web/app/(buyer)/needs/[needId]/page.tsx`
- `apps/web/app/(buyer)/needs/new/page.tsx`
- `apps/web/app/(buyer)/needs/page.tsx`
- `apps/web/app/(buyer)/orders/page.tsx`
- `apps/web/features/auth/session-list.tsx`
- `apps/web/features/catalog/catalog-detail.tsx`
- `apps/web/features/location/location-chooser.tsx`
- `apps/web/features/merchant/merchant-profile.tsx`
- `apps/web/features/needs/need-create-form.tsx`
- `apps/web/features/needs/need-detail.tsx`
- `apps/web/features/needs/need-list.tsx`
- `apps/web/features/needs/need-response-list.tsx`
- `apps/web/features/notifications/notification-inbox.tsx`
- `apps/web/features/orders/order-detail.tsx`
- `apps/web/features/orders/order-list.tsx`
- `apps/web/features/profile/address-api.ts`
- `apps/web/features/profile/address-form.module.css`
- `apps/web/features/profile/profile-overview.tsx`
- `apps/web/features/reports/report-button.tsx`
- `apps/web/features/reviews/review-form.tsx`

No backend, OpenAPI, generated API client, DB/schema, pricing, inventory, permission/state-machine, root layout, lockfile or global CSS file was changed by this branch.

## Conflict map

| Area | Risk | Integration guidance |
| --- | --- | --- |
| New CSS modules / new mobile presentation files | LOW | `SAFE_STANDALONE`; normally take branch version as-is |
| `/register` new route | LOW | `SAFE_STANDALONE` unless local already added the same route |
| `merchant-profile.tsx` Provider/report presentation | MEDIUM | Preserve newer local Shop business/data wiring; port only explicit presentation parameter/wrapper and report display context |
| `catalog-detail.tsx` report target context | MEDIUM | Preserve newer local cart/availability behavior; the branch change only passes authoritative display props into `ReportButton` |
| `location-chooser.tsx` | MEDIUM | Preserve newer local `BrowseLocation` contract/storage behavior; port mobile presentation and saved-address wiring selectively |
| Need feature components | MEDIUM | Preserve newer local API/error/business logic; port class/wrapper/presentation changes; keep `actionable` authoritative |
| `notification-inbox.tsx` | MEDIUM | Preserve safe deep-link helper and API calls; presentation is separable via module CSS |
| `profile-overview.tsx`, sessions | MEDIUM | Presentation-oriented; preserve newer local auth/session APIs if changed |
| `report-button.tsx` | MEDIUM | Keep current API/reason/idempotency behavior; port CSS-module wiring/mobile markup and optional display-only target context |
| `review-form.tsx` | MEDIUM | Standalone reusable form; do not add order-status eligibility during merge |
| `order-detail.tsx` | **HIGH** | Do not resolve wholesale. Preserve local loader/refresh/business behavior and `allowedActions`; port `OrderDetailMobile` wiring/presentation selectively |

## Suggested cherry-pick / manual integration order

1. Coverage/documentation baseline (`80764263` and final docs later).
2. Register route + auth presentation.
3. Location chooser presentation and saved-address support.
4. Address presentation.
5. Orders list presentation.
6. Add `order-detail-mobile.tsx` + CSS first, then manually port the narrow wiring in `order-detail.tsx`.
7. Needs list/create/detail/responses presentation.
8. Review reusable presentation (do not auto-mount from status).
9. Report CSS + `ReportButton` wiring, then optional Catalog/Merchant target-context commits.
10. Notifications CSS + inbox wiring.
11. Profile CSS + overview wiring.
12. Sessions CSS + list/page wiring.
13. Provider presentation parameter/wrapper.
14. Apply final coverage/handoff docs after local conflict resolution.

For local branches that have evolved business logic after this branch was cut, prefer **manual port of presentation code** over accepting this branch wholesale in MEDIUM/HIGH hotspots.

## Representative commits

Early/domain commits already on the branch include:

- `80764263` — initial coverage matrix
- `322fb868`, `ec17513f` — Register
- `03c16984`, `e25ee8eb`, `a38630d6` — Location
- `ec1aab63` — Address presentation
- `612c3942`, `3a8da8f9`, `ab48c4f3`, `12e2d421` — Orders list
- `ccd014ca`, `c3fdfa22`, `f19ddc5a` — Order Detail presentation/wiring
- `ebb67b22`, `506e2f44`, `17fe247a`, `b1c84dc4`, `716634dd`, `2fc7a65e`, `178dde8b`, `55f5d668`, `b50e159d` — Needs
- `caf675f4`, `ad6ab4f1` — Review
- `b83c0f77`, `f662f075` — Report presentation + wiring
- `ff0957de`, `2632a9d1`, `c8dc3827`, `98d228b9` — authoritative Report target-context presentation and caller wiring
- `97f42576` — Provider presentation variant
- `4ea2e33c`, `ea76b025`, `79b3b6b6`, `591e4066` — Notification presentation/app bar
- `bf55badb`, `97e0fb54` — Profile
- `e0c150f9`, `93b1128f`, `7af2d3cd`, `ccb0d962` — Sessions/security (including explicit preservation of existing revoke behavior)
- `da810950` — finalized coverage matrix
- `16b70b46` — first handoff snapshot; this document was subsequently refined to include final Report context changes

Use `git log --oneline 6bf733d4..feature/buyer-mobile-finish-ui` locally for the exact full ordered commit list.

## Verification status

Requested repository gates:

```bash
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
```

Current status at final handoff update:

- Typecheck: **NOT EXECUTED**
- Lint: **NOT EXECUTED**
- Tests: **NOT EXECUTED**
- Build: **NOT EXECUTED**
- Browser runtime 390×844: **VISUAL_PENDING**
- Browser runtime 360×800: **VISUAL_PENDING**

Reason: this session can edit/read the private repository through the GitHub connector but does not have an executable checkout/browser runtime. Existing CI only triggers on pull requests, `main`, and `codex/plan-10-*`; the implementation branch does not trigger CI on push. Per the task brief, a PR was not opened merely to obtain CI.

Do not relabel these items as passed until they are actually executed. Figma context/screenshot comparison performed during implementation is design reference, not runtime browser evidence.

## Local verification / acceptance sequence

```bash
git fetch origin
git checkout feature/buyer-mobile-finish-ui
pnpm install --frozen-lockfile
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
```

Then run the normal local stack and review at minimum:

- 390×844
- 360×800

Check: app-bar heights/touch targets, horizontal overflow, bottom-nav safe area, loading/empty/error states, keyboard/focus behavior, report modal scrolling and target context, notification fallback with no deep link, session revoke states, Order `allowedActions`, Need `actionable`, GPS denied/manual location fallback.

Only after these executable/browser gates pass should the branch be called `Buyer Mobile remaining UI — COMPLETE` / `VISUALLY_VERIFIED`.
