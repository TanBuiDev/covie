# Integrated UI verification — 2026-08-29

## Scope

This record covers the locally integrated implementation of Tasks 8–13 from `docs/superpowers/plans/2026-08-28-complete-integrated-ui.md`. The plan labels Tasks 8–12 as Chunk 2; Task 13 is included here because the active delivery request explicitly included deterministic actor fixtures.

No branch was pushed, no staging or production environment was used, and no deployment claim is made. Full exact-frame Buyer, Seller and Admin acceptance remains Tasks 14–16.

## Delivered commits

| Commit | Delivery |
| --- | --- |
| `048ce8e` | Public merchant kind, paginated catalog, location capabilities, open state and availability |
| `b1e3aff` | Buyer reputation, order-item review eligibility and Need action contracts |
| `b01bed0` | Buyer reputation and eligibility-driven web actions |
| `4207cbf` | Authoritative Seller Need response candidates |
| `fcd7f44` | Versioned Seller catalog/contact reads and edits with stale-conflict handling |
| `4bf131b` | Read-only Admin Shops, Listings and Needs collections |
| `cd3c2bc` | Deterministic local Buyer/Seller/Admin fixtures and actor smoke tests |
| `0f03b44` | Integrated lint/schema/unit-test quality-gate fixes |
| `0ec9460` | Seller responsive overflow fix, viewport assertions and UTF-8-safe fixture evidence |

## Contract and UI outcomes

- PUB-009 uses server-provided `merchantKind`, merchant catalog, fulfillment capabilities, `openNow` and explicit availability.
- Buyer reputation, review targets and Need cancellation are mounted from server eligibility rather than status inference.
- Seller Need response candidates and submit validation share the same ownership, category, moderation, coverage, availability and stock policy.
- Seller catalog and contact editing use versioned reads and `ETag`/`If-Match`; stale writes recover through an explicit refresh-required state.
- Admin Shops, Listings and Needs use role-protected paginated/filterable safe DTOs. Zone/category mutations remain intentionally unimplemented.
- The local seed is idempotent, local-only and stores disposable credentials only under ignored `.local/dev/ui-fixture.json`.

## Fresh verification evidence

Environment: Node `24.11.1`, pnpm `10.34.5`, JDK `25.0.2`, Playwright `1.62.1`, Chromium project.

| Gate | Result |
| --- | --- |
| `apps/api/mvnw clean verify` | Pass: 472 tests, 0 failures, 0 errors, 1 skipped |
| `pnpm api-client:check` | Pass; generated client matches OpenAPI |
| `pnpm web:typecheck` | Pass |
| `pnpm web:lint` | Pass with 0 errors and one existing `catalog-card` `no-img-element` warning |
| `pnpm web:test` | Pass: 58 files, 145 tests |
| `pnpm web:build` | Pass: 35 static pages |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1` | Pass |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1` | Pass; repeated seed remains idempotent |
| `pnpm ui:verify-local` | Pass: 4/4 Playwright tests |

The four local browser tests comprise one populated Buyer journey and protected smoke paths for Buyer, Seller and Admin. The Buyer journey covers Home → Search → Product Detail → Shop Profile → Address List → Service Detail → Resale Detail → Cart → Checkout → Success → Orders.

## Runtime viewport evidence

Ignored evidence is written to `.local/dev/visual-review/`:

- `sel-catalog-edit-{360,390,768,1024,1280,1440}.png`
- `sel-contact-edit-{360,390,768,1024,1280,1440}.png`
- `adm-shops-{1024,1280,1440}.png`

Each captured viewport asserts `document.documentElement.scrollWidth <= window.innerWidth`. The first run exposed Seller horizontal overflow at 360px; `0ec9460` corrected shell box sizing and the complete viewport matrix then passed. The Admin evidence was reseeded and visually inspected to confirm UTF-8 Vietnamese text is preserved under Windows PowerShell 5.

These screenshots are runtime/responsive evidence only. They do not replace route-by-route comparison with the exact frozen Figma frames required by Tasks 14–16.

## Preserved local work

The integration commits deliberately exclude unrelated pre-existing working-tree edits in the address form, its tests, the direct-upload test and the closure-audit document, plus local `apps/web/AGENTS.md` and `apps/web/CLAUDE.md`. Line-ending-only API status entries were also not staged. The safety stash remains untouched.

## Remaining work

1. Task 14: Buyer Mobile/Desktop exact-frame and state acceptance.
2. Task 15: complete Seller Mobile/Desktop exact-frame, interaction, stale-action and accessibility acceptance.
3. Task 16: complete Admin desktop exact-frame, pagination/dialog/accessibility and read-only-boundary acceptance.
4. Tasks 17–18: integrated gates from a clean checkout, remote review, staging rehearsal and release evidence.

