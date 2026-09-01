# Integrated UI verification — 2026-09-01

## Scope and evidence identity

- Branch: `codex/integrated-ui-completion`
- Implementation commit: `fc93244` (`feat(ui): complete integrated visual acceptance`)
- Visual source: frozen Figma file `CoVie — V1 UI Design`, using exact production-frame nodes from Buyer Mobile/Desktop, Seller Mobile/Desktop and Admin Desktop pages.
- Local runtime: Windows, Node.js 24.11.1, Playwright 1.62.1, Chromium 151.0.7922.34, JDK 25.0.2.
- Evidence is local and ignored: `.local/dev/visual-review/chunk3/`. It is not committed as product source.

## Runtime acceptance

`apps/web/e2e/chunk3-acceptance.local.spec.ts` passed all four actor suites: Auth, Buyer, Seller and Admin. Before each capture the suite verifies the destination URL and a route-unique heading or landmark, then checks Axe violations and horizontal overflow at the applicable viewport.

The run produced 267 PNG captures:

| Surface | Captures |
|---|---:|
| Admin (`adm-*`) | 45 |
| Auth (`auth-*`) | 18 |
| Buyer (`buy-*`) | 30 |
| Buyer Needs (`need-*`) | 24 |
| Notifications | 6 |
| Orders (`ord-*`) | 6 |
| Profile | 6 |
| Public Buyer (`pub-*`) | 36 |
| Seller (`sel-*`) | 90 |
| Sessions | 6 |
| **Total** | **267** |

Coverage uses 360×800, 390×844, 768, 1024, 1280×900 and 1440×900 where the matching Figma unit requires that breakpoint. Screenshot names retain the Figma unit and exact node identifier, for example `adm-001-393-4507-1440.png`.

## Fresh integrated gates

| Gate | Result |
|---|---|
| `pnpm api-client:check` | Passed; generated OpenAPI client is synchronized |
| `pnpm web:typecheck` | Passed |
| `pnpm web:lint` | Passed with 0 errors and 1 existing `catalog-card` `no-img-element` warning; no rule disabled |
| `pnpm web:test` | 58 files, 146 tests passed |
| `pnpm web:build` | Passed; 35 application pages built |
| `pnpm --filter @covie/web test:e2e` | 2 passed, 8 deterministic-fixture tests skipped without fixture environment |
| `pnpm ui:verify-local` | 4/4 seeded Buyer/Seller/Admin acceptance tests passed |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1` | Passed |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1` | Passed, exit 0 |
| `apps/api/mvnw clean verify` | 476 tests, 0 failures, 0 errors, 1 skipped; build success |

Focused runtime matrices for Seller and Admin also passed after responsive fixes. Seller coverage includes SEL-001…019 and required Need interactions across 360/390/768/1024/1280/1440. Admin coverage includes ADM-001…017 across 1024/1280/1440 and preserves the approved read-only boundaries for unsupported mutations.

## Defects closed during acceptance

- Bound PostgreSQL `Instant` values explicitly in Need discovery instead of relying on ambiguous driver inference.
- Split first-page and cursor notification queries to remove a nullable cursor binding that PostgreSQL rejected with SQLState `42P18`.
- Prevented a write lock from being requested by the read-only shipping-policy list transaction.
- Converted setting response values from Jackson-node bean metadata to actual JSON primitives and regenerated the OpenAPI client.
- Corrected responsive Buyer/Seller headings, desktop composition, overflow, catalog type initialization and Admin setting labels found by the route matrix.

Each backend correction has a focused regression test and is included in the 476-test API gate.

## Explicit limitations

- This document proves local implementation, automated test verification and local runtime acceptance only.
- The screenshots require human visual/product review before merge; automated landmarks, Axe and overflow checks do not prove pixel-perfect approval.
- Pull-request CI has not yet run at this evidence point.
- No staging or production environment was used. Authenticated staging journeys, deployment rehearsal and production release remain open.
- The standard E2E command intentionally skips fixture-gated suites unless the local fixture environment is present; `pnpm ui:verify-local` supplies that environment and passed all four suites.
