# Seller Desktop — Engineering Handoff

## Scope

This handoff covers the Seller Desktop implementation on `feature/seller-desktop-figma-ui` using Figma page `05 — Seller Desktop` (`3:6`) as the visual source of truth and `feature/seller-mobile-figma-ui` as the business/data baseline.

The branch deliberately keeps the existing Seller Mobile routes, API clients, mutations, concurrency handling, and authorization behavior. Desktop is implemented as a separate responsive composition at `>=1024px`; it is not a stretched copy of the mobile layout and it does not introduce a second Seller business state machine.

## Baseline decision

The implementation branch was created from `feature/seller-mobile-figma-ui` at `aae96f38ce7a4b7ecfa5bed40a6489ddcffa3078` after comparing Seller-related branches. That baseline was 88 commits ahead and 0 behind `main` at the time of the audit and already contained the Seller domain/API/UI foundation needed for Desktop. The separate `codex/plan-10-seller-mobile-ci` branch was not used as the base because its divergence was CI-oriented rather than Seller implementation code.

## Figma source and layout contract

- Figma file: `CoVie — V1 UI Design`
- Page: `05 — Seller Desktop` (`3:6`)
- Shared production matrix: `274:192`
- Frozen Seller sidebar: 240px
- Frozen top-level IA: `Tổng quan`, `Đơn hàng`, `Danh mục`, `Điểm bán`, `Cài đặt`
- Desktop breakpoint: 1024px
- Reference desktop canvas: 1440×900
- Reference content region: 1200px after the 240px sidebar
- Inner content uses the 1120–1128px composition from the audited frames, with responsive compact rules at 1024/1280 rather than horizontal page overflow.

The implementation uses project CSS files and existing React/Next.js patterns; Tailwind was not added.

## Implemented production surfaces

### Seller shell and navigation

- Desktop Seller shell with 240px sticky sidebar.
- Merchant switcher reused from the existing Seller layer.
- Exact frozen navigation order and active-state semantics.
- Navigation icons were taken from the Figma component vectors rather than substituted with unrelated icon libraries.
- Mobile header/tabs remain active below 1024px.
- Restricted merchant status banner remains server-state driven.

### Dashboard

Route: `/seller/[merchantId]`

- Figma-aligned title/meta and operational metric composition.
- Merchant status and location count are API-backed.
- Recent orders use real order summaries.
- Need matches use real matching data and distance/area values supplied by the backend.
- No GMV, revenue, profit, trend percentages, or other synthetic analytics were introduced.

### Settings

Route: `/seller/[merchantId]/settings`

- Desktop profile/settings form composition.
- Existing merchant ETag/concurrency behavior remains unchanged.
- Existing media upload flow remains the source for logo/cover mutations.

### Locations and configuration

Routes:

- `/seller/[merchantId]/locations`
- `/seller/[merchantId]/locations/new`
- `/seller/[merchantId]/locations/[locationId]`
- `/seller/[merchantId]/locations/[locationId]/delivery`
- `/seller/[merchantId]/locations/[locationId]/shipping`
- `/seller/[merchantId]/locations/[locationId]/minimum-order`
- `/seller/[merchantId]/locations/[locationId]/contacts`

Implemented desktop list/form/config compositions while preserving the existing API behavior.

Known contract constraints are intentionally retained:

- Location creation still requires authoritative latitude/longitude plus `pinSource`; the Figma does not define a canonical geocoder/pin-picker contract, so coordinates are never fabricated.
- Contact edit cannot safely invent a per-contact concurrency token/detail contract that the backend does not expose.

### Catalog, editor, inventory

Routes:

- `/seller/[merchantId]/catalog`
- `/seller/[merchantId]/catalog/new`
- `/seller/[merchantId]/inventory`

Implemented:

- Desktop segmented Catalog management composition.
- Product, service, and resale views using existing management summaries.
- Two-column desktop create/editor composition.
- Category-defined attributes and real location/offer inputs.
- Service variant pricing/availability behavior from the existing request schema.
- Product inventory mode and inventory management composition.

Intentional gaps:

- Catalog management summary does not expose all sample-only Figma fields such as full media/offer/moderation detail, so they are not fabricated.
- Existing-item edit is not implemented as a fake client workflow because there is no authoritative management-detail GET plus concurrency token contract for it.

### Orders

Routes:

- `/seller/[merchantId]/orders`
- `/seller/[merchantId]/orders/[orderId]`

Implemented:

- Desktop location filter and dense order-list composition.
- Desktop split order-detail composition.
- Existing order action UI remains mounted from backend-supplied `allowedActions` only.
- Reject/cancel reason surfaces preserve existing mutation behavior.

Important invariant: frontend does not derive permissions or valid actions from raw order status.

Known summary/detail gaps remain explicit. If buyer name, amount, deadline, reason taxonomy, or action presentation is not provided by the authoritative API, the implementation does not manufacture sample values solely to match a Figma example.

### Need matching

Route: `/seller/[merchantId]/needs`

Implemented desktop feed/response composition while preserving server-owned matching, area/distance, expiry, and response state.

Blocking contract gap retained: a response submission requires `locationVariantId`, while the current backend does not expose an authoritative eligible-candidate read under the same policy. The UI therefore does not ask users to paste UUIDs and does not reconstruct eligibility locally.

### Reputation and moderation notices

Routes:

- `/seller/[merchantId]/reputation`
- `/seller/[merchantId]/notices`

Implemented desktop compositions using the existing aggregate reputation and ownership-filtered notification data.

The notification payload does not provide a canonical moderation decision/reason/destination contract, so the frontend does not infer moderation routes from notification type strings.

## Responsive and accessibility work

Desktop-specific CSS was split by responsibility:

- `seller-shell-desktop.css`
- `seller-dashboard-desktop.css`
- `seller-settings-desktop.css`
- `seller-catalog-desktop.css`
- `seller-operations-desktop.css`
- `seller-trust-desktop.css`

The implementation includes compact desktop rules for 1024 and intermediate widths, long-content wrapping/truncation, visible focus, text labels in addition to status color, scoped loading/error/empty states, pending/disabled controls, and preservation of existing confirmation/concurrency behavior.

## Verification state

### Completed

- `FIGMA_AUDITED`: exact Seller Desktop production frames and frozen component contracts were inspected before implementation.
- `REMOTE_STATIC_VERIFIED`: branch diff, route wiring, API ownership, responsive CSS, and contract-gap handling were audited against the current repository and generated API usage.
- Seller Mobile regression strategy: the same production routes/data components remain active below 1024px; desktop overrides are isolated behind desktop media queries/compositions.

### Executable verification

At the time this handoff file was first committed, direct execution was still pending because the connector environment could not clone the repository over the network. The repository CI itself contains the required frontend checks (`typecheck`, `lint`, `test`, `build`), but its normal triggers are PRs plus pushes to `main`/`codex/plan-10-*`.

A separate CI-only branch may be used to verify the exact implementation tree without opening the Seller Desktop PR early. Once executable evidence exists, record the run/commit in the coverage matrix and update this section to `TEST_VERIFIED`.

### Runtime/browser verification still required for final gate

The final gate requires actual rendered-browser evidence at 1024, 1280, and 1440 and affected Seller flows with real/local authoritative backend state. Source inspection alone must not be labeled `VISUALLY_VERIFIED` or `RUNTIME_VERIFIED`.

Until that evidence exists:

- do not claim pixel-perfect completion;
- do not mark the branch `Seller Desktop — COMPLETE`;
- do not open the final PR solely to make CI run;
- do not merge anything.

## Required local/browser verification checklist

Run from a checkout of `feature/seller-desktop-figma-ui`:

```bash
pnpm install --frozen-lockfile
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
```

For real Seller browser verification, start the local infrastructure/API/web stack described in `README.md`, use a Seller account with representative merchant/location/catalog/order/Need data, then verify at minimum:

1. 1024px: no page-level horizontal overflow; sidebar/content remain usable.
2. 1280px: intermediate desktop composition remains balanced.
3. 1440px: compare against the canonical Figma frames.
4. Dashboard with populated and empty order/Need states.
5. Settings save and version-conflict behavior.
6. Location list/create/edit and all config sub-routes.
7. Catalog product/service/resale segmentation, create flow, and inventory.
8. Orders list/detail plus only server-authorized actions.
9. Need feed and safe unavailable state for the candidate-contract gap.
10. Reputation and notices with long text.
11. Keyboard focus and destructive confirmation flow.
12. Mobile Seller regression below 1024px.

Capture screenshots or equivalent browser evidence for the canonical desktop states and record any remaining visual deltas before the PR is opened.

## Branch/PR policy

- Working branch: `feature/seller-desktop-figma-ui`
- Intended PR base: `feature/seller-mobile-figma-ui` unless repository integration strategy changes before handoff.
- Do not merge automatically.
- Open the PR only after the final gate has executable and browser evidence required by the project brief.

## Key commits before this handoff

- `2d8fff5` — coverage matrix created before bulk implementation.
- `d7b4347` — Seller desktop shell/workspace pass.
- `16036df` — Catalog/editor/inventory desktop pass.
- `ecd5ca0` — Operations/trust desktop pass.
- `ed3a032` — State/accessibility desktop pass.

The handoff/cleanup commit follows these implementation commits and should be treated as the branch documentation checkpoint, not as proof of runtime/browser verification by itself.
