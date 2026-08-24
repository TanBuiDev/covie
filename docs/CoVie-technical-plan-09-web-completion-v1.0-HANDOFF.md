# Plan 09 Implementation Handoff

**Branch:** `feat/plan-09-web-completion`  
**Initial Plan 09 base used for implementation:** `a4b0193f565b01565d4d670d23ea7a68156fcf69` from `codex/plan-08-moderation-system-settings-admin`  
**Handoff date:** 2026-08-24  
**Status:** PARTIAL — prerequisite Plans 07–08 are not implementation-complete on the available base.

## Why Plan 09 cannot be marked complete yet

The Plan 09 document requires Plans 01–08 as prerequisites and explicitly assumes Review, Notification/SSE and Admin foundations already exist. The available Plan 08 branch is still partial:

- highest available migration on the Plan 09 base is `V011__need_matching.sql`;
- `V012__reviews_reputation_outbox_notifications.sql` is absent;
- `V013__moderation_system_settings_admin.sql` is absent;
- `apps/web/features/reviews/review-api.ts` is absent;
- the current Plan 08 head contains partial Settings/Moderation/Admin backend work but not the completed Plan 07/08 frontend/contracts required by Tasks 15–17 and the Task 20 acceptance suite.

Do not treat this branch as Plan 09 acceptance-complete until the final Plan 07/08 work is integrated and the full gates are run.

## Implemented on this branch

### Task 1 — partial shared runtime foundation

Implemented dependency-independent pieces:

- `apps/web/lib/query/query-keys.ts`
- `apps/web/lib/forms/apply-field-errors.ts`
- `apps/web/lib/api/error-copy.ts`
- `apps/web/tests/forms/apply-field-errors.test.ts`
- root `Providers` boundary in `apps/web/app/providers.tsx`

Not implemented yet:

- TanStack Query/RHF runtime dependencies and `pnpm-lock.yaml` update;
- `query-client.ts` / QueryClientProvider;
- QueryClient tests.

Reason: the GitHub connector can mutate repository files but cannot run `pnpm add`/regenerate the private-repo lockfile safely. Do not hand-edit the frozen lockfile.

### Task 2 — PWA/responsive shell foundation implemented

Added:

- `apps/web/app/manifest.ts`
- `apps/web/public/icon.svg`
- `apps/web/public/offline.html`
- `apps/web/public/sw.js`
- `apps/web/lib/pwa/register-service-worker.tsx`
- `apps/web/components/layout/app-header.tsx`
- `apps/web/components/layout/consumer-bottom-nav.tsx`
- `apps/web/components/layout/consumer-shell.tsx`
- `apps/web/components/layout/seller-shell.tsx`
- `apps/web/components/layout/admin-shell.tsx`
- responsive/safe-area primitives in `apps/web/app/globals.css`
- `apps/web/app/(public)/layout.tsx`
- `apps/web/app/(buyer)/layout.tsx`
- root home wired to the consumer shell
- `apps/web/tests/pwa/service-worker-policy.test.ts`

Service worker policy is intentionally conservative: `/api/v1/**` is network-only and only `/icon.svg` + `/offline.html` are pre-cached.

Still needed after prerequisites/dependencies are complete:

- role-aware shell/session integration;
- seller/admin route layouts tied to authoritative auth reads;
- full shell/navigation component test matrix.

### Task 3 — browse-location foundation implemented/partial

Added/normalized:

- `BrowseLocation` type while retaining legacy `DiscoveryLocation` compatibility;
- `toCatalogLocationParams` and conversion helpers;
- versioned localStorage persistence containing only `lat/lng` or `addressId` plus display label;
- malformed/old stored values are cleared;
- `BrowseLocationProvider` + `useBrowseLocation`;
- `LocationChooser` with user-initiated Geolocation API and manual coordinate fallback;
- location persistence tests.

Still needed:

- saved-address picker backed by `/api/v1/me/addresses` after session state is available;
- TanStack Query discovery invalidation on location switch;
- server-query hydration preference and full location-selector tests.

### Task 4 — canonical search route partially implemented

Added:

- `apps/web/app/(public)/search/page.tsx`
- `apps/web/features/discovery/search-results.tsx`
- `/explore` now redirects to `/search` rather than maintaining two discovery implementations;
- search calls the existing generated-client-backed `discoverCatalogItems` API;
- no-location, loading, error and empty states are surfaced; empty search links to Need creation;
- URL sort values are allow-listed before reaching the generated API.

Still needed after contracts are stable:

- category route/real category labels;
- canonical Product/Service/Resale detail split;
- merchant detail and reviews/reputation integration;
- concrete resolved-location continuity without guessing generated DTO fields;
- TanStack Query migration and all Task 4 tests.

### Task 5 — safe purchase resume primitives implemented/partial

Added `apps/web/features/purchase/purchase-intent.ts` and tests covering:

- session-only intent storage;
- 15-minute expiry;
- one-time consume;
- service quantity normalization helper;
- sanitized same-origin relative return paths, including encoded redirect rejection;
- no price, checkout token or auth token in the intent.

Still needed: wire listing purchase buttons, auth return flow and server revalidation.

### Task 8 — checkout source parser implemented/partial

Added `apps/web/features/checkout/checkout-source.ts` and tests for `CART`, `DIRECT` and `NEED_REQUEST` source identifiers. Existing generated preview/commit APIs remain authoritative.

Still needed: unified RHF/Query checkout screen, ephemeral Preview token lifecycle, `CHECKOUT_CHANGED` re-preview UI and success flow.

### Task 16 — safe notification deep-link primitive implemented/partial

Added `apps/web/features/notifications/notification-deep-link.ts` and tests. It derives links from notification type + typed entity IDs and never accepts arbitrary payload URLs.

The notification inbox/bell/EventSource integration is blocked until Plan 07 notification/SSE contracts exist on this branch.

### Task 18 — presentation/accessibility primitives implemented/partial

Added:

- `apps/web/components/ui/empty-state.tsx`
- `apps/web/components/ui/error-state.tsx`
- `apps/web/components/ui/loading-state.tsx`
- `apps/web/components/ui/confirm-dialog.tsx`
- `apps/web/components/ui/status-chip.tsx`
- `apps/web/components/ui/warning-banner.tsx`
- `apps/web/components/ui/action-bar.tsx`
- `apps/web/lib/format/money.ts`
- `apps/web/lib/format/time.ts`
- `apps/web/lib/format/status-copy.ts`
- `apps/web/tests/ui/formatters.test.ts`

Unknown status/type values use safe fallback copy instead of printing raw enums.

Still needed: apply primitives to every P0 screen and run the full accessibility/responsive matrix.

## Tasks not completed on this handoff

The following remain incomplete and must not be checked off as complete yet:

- Task 1 remainder: install Query/RHF dependencies, lockfile, QueryClientProvider/tests.
- Task 2 remainder: full role/auth integration and shell tests.
- Task 3 remainder: saved-address/session/query invalidation integration.
- Task 4 remainder: category/detail/merchant/reputation completion.
- Tasks 5–14: workflow wiring and P0 screens beyond the safe foundations described above.
- Task 15: blocked by missing Plan 07 Review foundation and requires backend additive Review eligibility + regenerated OpenAPI/client.
- Task 16 remainder: blocked by missing Plan 07 Notification/SSE contracts.
- Task 17: blocked by incomplete Plan 08 contracts/frontend.
- Task 18 remainder: cross-screen normalization + accessibility/responsive acceptance.
- Task 19: Playwright dependency/lockfile and production-safe E2E auth harness not implemented.
- Task 20: P0 Playwright journeys and final acceptance gate not runnable until prerequisites and Task 19 are complete.

## Required continuation sequence

1. Finish Plan 07 and Plan 08, including their acceptance evidence and expected `V012` / `V013` migrations/contracts.
2. Bring the completed prerequisite head into this branch before continuing Plan 09:

```bash
git fetch origin
git checkout feat/plan-09-web-completion
git merge origin/codex/plan-08-moderation-system-settings-admin
```

If Plan 07 is maintained on a separate branch and is not already contained by final Plan 08, integrate it before Plan 08.

3. Install Plan 09 frontend dependencies locally so `package.json` and `pnpm-lock.yaml` are updated together:

```bash
pnpm --filter @covie/web add @tanstack/react-query@^5 react-hook-form@^7 @hookform/resolvers@^5
pnpm --filter @covie/web add -D @playwright/test@^1
pnpm --filter @covie/web exec playwright install chromium
```

4. Implement `apps/web/lib/query/query-client.ts`, wrap `Providers` with one `QueryClientProvider`, then migrate the temporary effect-based search flow to Query.
5. Continue Plan 09 Tasks 3–20 in dependency order; do not copy backend enums/business rules to unblock the frontend.
6. Add the E2E-only backend test-support profile from Task 19 only after the final auth primitives from prerequisite plans are present.

## Verification status at handoff

No green execution claim is made from this remote-only session. The connector can read/write the private repository but cannot execute the private workspace, run `pnpm install`, start Postgres/Redis/MinIO, export OpenAPI or run Maven/Playwright. Existing repository CI runs on pull requests and pushes to `main`, not ordinary pushes to this branch.

After the prerequisites and dependencies above are integrated, run at minimum:

```bash
pnpm install --frozen-lockfile
pnpm --filter @covie/web lint
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web test
pnpm --filter @covie/web build
./infra/scripts/export-openapi.sh
pnpm --filter @covie/api-client generate
pnpm --filter @covie/api-client check
cd apps/api && ./mvnw verify
```

Then complete Task 19 and run:

```bash
pnpm --filter @covie/web test:e2e
```

## Migration safety

This Plan 09 work added **no database migration**. On the current partial prerequisite base the highest migration is still `V011`; do not create a fake Plan 09 migration to bridge missing Plan 07/08 work. Once prerequisites are complete, Plan 09 should inherit the reviewed Plan 07/08 migrations and still avoid a new schema migration unless implementation evidence requires one.
