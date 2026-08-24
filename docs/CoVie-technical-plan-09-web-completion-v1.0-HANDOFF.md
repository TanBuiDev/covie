# CoVie Plan 09 — Maximum-Completion Handoff

**Branch:** `feat/plan-09-web-completion`  
**Base:** `a4b0193f565b01565d4d670d23ea7a68156fcf69` (`codex/plan-08-moderation-system-settings-admin`)  
**Draft PR:** #1 — `feat(web): Plan 09 web completion`  
**Handoff date:** 2026-08-24  
**Status:** **MAXIMUM COMPLETION ON AVAILABLE PREREQUISITES / NOT 100% PLAN ACCEPTANCE**

This branch contains all Plan 09 work that was safe to implement against the contracts actually present on the available prerequisite branch. It must not be represented as 100% Plan 09 acceptance because the required Plan 07/V012 and completed Plan 08/V013 foundations are absent, and the repository currently has an inherited Maven test-compilation failure that blocks OpenAPI regeneration and downstream web gates.

## Final task status

| Task | Status | Handoff summary |
| --- | --- | --- |
| 1 Shared runtime/providers | PARTIAL | query keys, field-error mapping, error copy and provider boundary added; TanStack Query/RHF dependencies + lockfile/query client not installed remotely |
| 2 PWA/responsive shells | SUBSTANTIAL | manifest, icon, offline shell, service worker, registration, consumer/seller/admin shells, responsive/safe-area CSS |
| 3 Browse location | SUBSTANTIAL | privacy-safe BrowseLocation persistence/provider/chooser, user-triggered geolocation/manual coordinates; saved-address/query invalidation integration remains |
| 4 Public discovery | SUBSTANTIAL | canonical `/search`, generated-client discovery, safe URL filters, states, `/explore` redirect; final review/reputation surfaces depend on Plan 07 |
| 5 Purchase resume | PARTIAL | session-only TTL purchase intent, one-time consume, service quantity normalization, safe return-path logic; full login/resume wiring remains |
| 6 Account + saved address | SUBSTANTIAL | account overview/logout; address create/edit/default/delete confirmation; generated address DTOs; backend field-error mapping and focused tests |
| 7 Multi-cart | SUBSTANTIAL | cart overview + per-cart detail, merchant-location/type separation, service qty=1, `If-Match`, stale conflict refetch and tests |
| 8 Unified checkout | PARTIAL | safe CART/DIRECT/NEED_REQUEST source parser and existing generated preview/commit boundary retained; full Query/RHF preview/commit lifecycle remains |
| 9 Buyer Orders | SUBSTANTIAL | generated Order types, backend `allowedActions`, no frontend transition machine, action dialogs, reason input, idempotency intent handling, irreversible completion copy, safe status fallback |
| 10 Seller Center | SUBSTANTIAL | merchant route context/switcher, seller layout/navigation, actionable dashboard from real Orders/Locations, separate Settings; no fake analytics |
| 11 Location/coverage/shipping | SUBSTANTIAL | location detail/edit with `If-Match`, RADIUS/SELECTED_AREAS coverage, FREE/FIXED/NEGOTIATED/CONDITIONAL shipping, ANY/ALL, negotiated-fee copy and tests |
| 12 Catalog/media/inventory | PARTIAL | generated aggregate create/catalog foundations already present; full granular editor, lifecycle/media/inventory acceptance not completed |
| 13 Seller Orders + Need response | PARTIAL | seller Order side substantially completed using shared `allowedActions`; Need side intentionally not extended because generated Need contract is stale |
| 14 Buyer Need flow | PARTIAL/BLOCKED | existing Need UI/backend exists, but feature layer still hand-copies DTOs; Plan 09-compliant completion requires successful OpenAPI regeneration first |
| 15 Review/Reputation/Report | BLOCKED | required Plan 07 review/reputation foundation and `V012` are absent; do not invent eligibility/reputation rules |
| 16 Notifications/SSE | PARTIAL/BLOCKED | safe notification deep-link primitive implemented; inbox/EventSource/query invalidation requires missing Plan 07 Notification/SSE contracts |
| 17 Admin control plane | BLOCKED | completed Plan 08/V013 contracts/frontend are absent on available base |
| 18 Shared UX/accessibility | SUBSTANTIAL | loading/empty/error/confirm/status/warning/action primitives, money/time/status formatters, safe unknown-enum fallback; full P0 matrix not executed |
| 19 Playwright E2E | BLOCKED/PARTIAL | dependency/harness not added because lockfile cannot be safely regenerated here and prerequisite auth/admin/review contracts are incomplete |
| 20 Final P0 acceptance | BLOCKED | cannot truthfully pass until Tasks 15–17/19 prerequisites exist and repository gates are executable |

## Major implementation delivered

### Consumer/public
- Canonical public discovery and `/explore` redirect.
- Browse-location context that persists only minimum location context, not sensitive account/auth data.
- PWA/offline shell with `/api/v1/**` kept network-only.
- Safe purchase-intent and checkout-source primitives.
- Account overview/logout and full saved-address mutation UX.
- Multi-cart overview and cart-detail mutation UX.
- Buyer Order list/detail/actions driven exclusively by backend `allowedActions`.

### Seller
- Seller Center merchant switcher and route-bound merchant context.
- Actionable dashboard based on existing APIs rather than fabricated KPIs.
- Shared Order command UI for seller/buyer views.
- Location detail/edit and conflict handling.
- Delivery coverage configuration for radius and selected areas.
- Shipping modes FREE/FIXED/NEGOTIATED/CONDITIONAL with ANY/ALL conditional rules.
- Negotiated shipping is never presented as zero-cost shipping.

### Shared correctness/safety
- Generated Order/Cart/Address/shipping contracts used where available.
- Removed handwritten Order DTO duplication from the Plan 09 Order API layer.
- No frontend Order state machine.
- `If-Match` conflicts refetch instead of silently overwriting.
- Destructive/irreversible Order actions require explicit confirmation; completion copy states irreversibility.
- Unknown status/type values render safe fallback copy.
- No Plan 09 Flyway migration was added.

## Prerequisite blockers confirmed

Plan 09 states Plans 01–08 are prerequisites and assumes Plan 08 ended at `V013__moderation_system_settings_admin.sql`. The branch available for implementation does not satisfy that assumption:

- highest inherited migration is `V011__need_matching.sql`;
- `V012__reviews_reputation_outbox_notifications.sql` is absent;
- `V013__moderation_system_settings_admin.sql` is absent;
- no completed Plan 07 Review/Reputation/Notification/SSE frontend foundation is available;
- final Plan 08 Admin contracts/frontend are not available.

Therefore Tasks 15–17 and Task 20 cannot be completed without fabricating backend contracts/business rules, which Plan 09 explicitly prohibits.

## Generated Need contract blocker

The checked-in generated API client does not currently expose the complete Need contracts implemented by the backend. Existing `apps/web/features/needs/need-api.ts` contains handwritten Need DTOs from earlier work. Plan 09 explicitly forbids extending this pattern.

Correct continuation is:

1. fix the inherited Maven test-compilation drift below;
2. export OpenAPI from the real backend;
3. regenerate `@covie/api-client`;
4. replace handwritten Need DTOs with generated types;
5. then finish Tasks 13–14.

## CI / verification evidence

A draft PR was opened specifically so GitHub Actions could execute the private workspace.

### Run `32741245640`

Initial failure was infrastructure-only:

```text
./mvnw: Permission denied
```

Plan 09 branch updated `.github/workflows/ci.yml` to make `apps/api/mvnw` executable before Maven/OpenAPI steps.

### Run `32741725398`

The executable issue is fixed. CI now reaches real Maven compilation, but both backend verification and OpenAPI export stop during `testCompile` because an inherited prerequisite test references a repository method that does not exist:

```text
apps/api/src/test/java/vn/covie/orders/application/OrderScheduledTransitionIntegrationTest.java
cannot find symbol:
method createPending(UUID, UUID, OrderType, Instant)
location: variable orders of type vn.covie.orders.persistence.OrderRepository
```

Current production `OrderRepository` does not expose that method. A fake production helper was deliberately **not** added merely to make CI green because the missing merchant/location semantics cannot be inferred safely from the failing test signature.

Because `infra/scripts/export-openapi.sh` runs Maven `test` for `OpenApiExportTest`, Maven first compiles all test sources. Consequently this prerequisite test drift blocks:

- OpenAPI export;
- generated client regeneration/drift check;
- API-client typecheck;
- web typecheck;
- web lint;
- Vitest suite;
- Next.js production build.

No green claim is made for gates that CI never reached.

## What to do next locally

### 1. Pull this handoff branch

If the branch does not exist locally:

```bash
git fetch origin
git switch -c feat/plan-09-web-completion --track origin/feat/plan-09-web-completion
```

If it already exists:

```bash
git switch feat/plan-09-web-completion
git pull origin feat/plan-09-web-completion
```

### 2. Complete/integrate prerequisites

Integrate the reviewed final Plan 07 and Plan 08 implementations so the branch contains `V012` and `V013` and their generated contracts. Do not create empty/fake migrations to fill the numbering gap.

### 3. Fix the inherited scheduled-transition test fixture

Inspect `OrderScheduledTransitionIntegrationTest` and update its fixture setup to create a valid pending Order through the current Order creation/entity contract, including the required merchant and merchant-location data. Prefer fixing the test fixture over adding a production `OrderRepository.createPending(...)` helper solely for tests.

Then run:

```bash
cd apps/api
./mvnw verify
cd ../..
```

### 4. Regenerate contracts

```bash
./infra/scripts/export-openapi.sh
pnpm --filter @covie/api-client generate
pnpm --filter @covie/api-client check
```

Review the generated diff and migrate Need UI to generated contracts before finishing Need flows.

### 5. Install Plan 09 runtime/E2E dependencies together with the lockfile

```bash
pnpm --filter @covie/web add @tanstack/react-query@^5 react-hook-form@^7 @hookform/resolvers@^5
pnpm --filter @covie/web add -D @playwright/test@^1
pnpm --filter @covie/web exec playwright install chromium
```

Do not manually edit `pnpm-lock.yaml`.

### 6. Run final web gates

```bash
pnpm install --frozen-lockfile
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web lint
pnpm --filter @covie/web test
pnpm --filter @covie/web build
```

After Tasks 15–17 and the E2E harness are present:

```bash
pnpm --filter @covie/web test:e2e
```

## Acceptance statement

This branch is the **maximum safe Plan 09 implementation possible on the available prerequisite state**. It is intentionally left as a draft integration branch rather than falsely marked Plan 09 complete. Continue from this branch after integrating the missing prerequisite plans and resolving the inherited test fixture drift.
