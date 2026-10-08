# Local Runtime and Visual Acceptance Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce reproducible local runtime and viewport evidence for every still-open Buyer, Seller, Admin, and Chunk 3 contract screen, while preserving explicit safe contract gaps and preventing legacy/static screenshots from being mistaken for current acceptance.

**Architecture:** One deterministic local seed owns Buyer, Seller, and Admin identities plus the domain records required by the acceptance journeys. Playwright authenticates each role through real application routes, drives server-owned states, captures exact frozen-frame viewports, and emits a machine-readable evidence manifest. Existing screenshots are inventoried and hashed before new work; they are reference-only unless recreated by the current Playwright run. Visual acceptance combines route assertions, zero console/page errors, overflow checks, deterministic screenshot naming, manual Figma overlay review, and post-approval snapshot tolerances.

**Tech Stack:** PowerShell, PostgreSQL/PostGIS local Docker stack, Spring Boot, Next.js 16, Playwright Chromium, Figma frozen production frames, SHA-256 evidence manifests.

---

## Evidence policy and pass criteria

- Runtime evidence directory: `.local/dev/visual-review/acceptance-2026-09-13/`.
- Legacy inventory source: `.local/dev/visual-review/chunk3/` currently contains 267 PNG files (45 Admin, 90 Seller, 36 Public, 30 Buyer, 24 Need, 18 Auth, and 24 other supporting captures). These 2026-09-01 images are reference-only; filenames alone do not prove backend-populated runtime or current-code acceptance.
- Manifest: `.local/dev/visual-review/acceptance-2026-09-13/manifest.json` records working-tree identity, API/web build identity, route, role, state setup, Figma node, viewport, output filename, SHA-256, assertion result, console/page errors, horizontal-overflow result, manual-review result, reviewer note, and any intentional contract-backed deviation.
- Authoritative case catalog: tracked `docs/visual-acceptance-cases.csv`. Its 81 case rows expand to exactly 201 capture tokens. Each token uses `overlay|responsive@<exact-node-id>@<width>x<height>=<literal-filename>`, so the node belongs to the individual screenshot rather than only to its case. Each row also fixes fixture setup, role, executable route and assertion. Playwright loads this file and fails if a case/capture/filename is missing or duplicated, if the filename's node or viewport differs from its token, or if the row's primary node is absent from all its capture tokens; prose tables below are navigation summaries only.
- Deterministic filename: `<screen-id>-<state>-<figma-node-with-hyphen>-<width>x<height>.png`, for example `pub-009-mixed-312-3743-390x844.png`.
- Exact viewports: mobile `360×800` and `390×844`; compact desktop `1024×900` where a compact frame exists; desktop `1280×900`; `1440×900` only for a canonical 1440 Seller/Admin frame or matrix requirement.
- Automated pass: expected route and landmark are visible; no unexpected 4xx/5xx response; no uncaught page error; no unexpected console error; `document.documentElement.scrollWidth <= viewport width`; keyboard focus remains visible for exercised controls; mutation pending/conflict controls use their tested server behavior.
- Manual Figma pass has two explicit modes stored per capture in the case catalog. `overlay` is used only when the frozen node has the same target dimensions; it requires an equal-size overlay review of regions, typography hierarchy, spacing, color tokens, clipping, fixed navigation and state copy. `responsive` is used when no equal-size frozen derivative exists; it compares the rendered capture to the canonical node's layout contract and requires correct breakpoint composition, no overlap/clipping/overflow, preserved hierarchy/tokens, and touch/focus targets without claiming pixel equality. Dynamic fixture content may differ only where recorded in the manifest.
- Regression tolerance after a screenshot is manually approved: Playwright `toHaveScreenshot` uses `threshold: 0.2` and `maxDiffPixelRatio: 0.01`; a larger delta fails and requires explicit re-review, never automatic baseline replacement.
- A row loses `RUNTIME_PENDING` only after its real route/state assertion passes. It loses `VISUAL_PENDING` only after every applicable viewport and named state is manually marked pass in the manifest. Safe contract gaps remain labeled and are judged against their safe-state Figma frame, not against fabricated data/actions.

## Chunk 1: Inventory before new captures

### Task 1: Preserve and classify prior evidence

**Files:**
- Create: `scripts/inventory-visual-evidence.ps1`
- Create: `scripts/tests/inventory-visual-evidence.test.ps1`
- Generate ignored: `.local/dev/visual-review/acceptance-2026-09-13/prior-inventory.json`

- [x] RED: test that the inventory records all 267 current `chunk3` PNGs, groups screen prefixes, stores relative paths/SHA-256/dimensions/timestamps, and marks each `REFERENCE_ONLY`.
- [x] Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/inventory-visual-evidence.test.ps1`; the first run failed for the missing script as expected.
- [x] GREEN: implement the read-only inventory script; source path/name/size/hash comparisons prove it does not rename, delete, overwrite, or promote old screenshots.
- [x] Rerun the focused test, then run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/inventory-visual-evidence.ps1`; 267 inputs are classified at ignored `.local/dev/visual-review/acceptance-2026-09-13/prior-inventory.json` as Admin 45, Seller 90, Public 36, Buyer 30, Need 24, Auth 18 and Other 24.

## Chunk 2: Deterministic multi-role fixture and harness

### Task 2: Extend the local fixture for all roles

**Files:**
- Modify: `scripts/seed-ui-fixture.ps1`
- Modify: `scripts/tests/seed-ui-fixture.test.ps1`
- Modify ignored schema: `.local/dev/ui-fixture.json`
- Modify: `scripts/verify-ui-flow.ps1`

- [x] RED: extend the fixture regression test to require disposable Buyer, Seller, and Admin credentials; deterministic merchant/location/catalog IDs; public Product/Service/Resale items; catalog edit versions; eligible Need candidates and existing response; Product delivery, pickup, Service orders with allowed-action variants; review-eligible/already-reviewed/disabled Buyer items; moderation case; settings; notifications; reputation; and audit events.
- [x] RED: prove repeat seeding is idempotent, preserves unrelated records, starts each named journey from its expected state, stores credentials only in ignored `.local/dev/ui-fixture.json`, and writes UTF-8 without BOM.
- [x] Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1`; initial RED runs exposed missing state and later noncanonical checkout leakage.
- [x] GREEN: extend the existing seed transaction and JSON handoff. Use deterministic IDs and real password hashing; never hard-code credentials into Playwright or tracked files.
- [x] GREEN: update `scripts/verify-ui-flow.ps1` to set `COVIE_UI_FIXTURE=1`, run the populated Buyer journey, reseed for isolation, then run Buyer/Public, Seller, Admin and the visual-manifest spec serially.
- [x] Rerun the seed regression test twice and require both runs green with identical deterministic IDs, exact canonical order/address state and valid fresh disposable credentials.

Checkpoint 2026-09-17: the seed regression invokes the seed twice and passes stable role, order/fulfillment, inventory, Need and notification assertions. Buyer review-state fixtures and final combined-flow proof are still open, so the broader Task 2 checkboxes remain unchecked.

### Task 3: Add the role journeys and capture helper

**Files:**
- Modify: `apps/web/e2e/populated-buyer-flow.local.spec.ts`
- Create: `apps/web/e2e/seller-flow.local.spec.ts`
- Create: `apps/web/e2e/admin-flow.local.spec.ts`
- Create: `apps/web/e2e/support/visual-evidence.ts`
- Create: `apps/web/e2e/support/visual-cases.ts`
- Create: `apps/web/tests/e2e-support/visual-evidence.test.ts`
- Create: `apps/web/e2e/visual-manifest.local.spec.ts`

- [x] RED: unit-test that the helper parses 81 rows and expands exactly 201 capture tokens from `docs/visual-acceptance-cases.csv`; rejects missing/duplicate cases, capture tokens or filenames; rejects token/filename node or viewport mismatches and a primary node unused by all row captures; enforces review mode; checks overflow and error collection; and writes one per-spec JSON fragment atomically.
- [x] Run `pnpm --filter @covie/web exec vitest run tests/e2e-support/visual-evidence.test.ts`; the first run failed for the missing helper as expected.
- [x] GREEN: centralize `captureEvidence`; hide only the Next.js development portal, wait for fonts/images, reset scroll deliberately, and never mask product UI. Each spec can write `.local/dev/visual-review/acceptance-2026-09-13/fragments/<spec>.json` through temp-file + atomic rename; specs never write shared `manifest.json`.
- [x] GREEN: Buyer, Seller and Admin specs authenticate through `/login`, prove their expected account shell, and use credentials/IDs only from the ignored JSON. The Seller/Admin specs currently establish the authenticated base journeys; complete route/state capture coverage remains Tasks 5–6.
- [x] Rerun the focused helper test: 6 tests pass, including 81/201 catalog validation, diagnostics, atomic fragment/manifest writes, PNG dimensions and SHA-256 corruption rejection; web typecheck also passes.

## Chunk 3: Buyer and public acceptance set

### Task 4: Capture contract-completed and remaining Buyer screens

The authoritative per-case execution rows are `pub-*`, `buy-*`, and `buyer-profile` in `docs/visual-acceptance-cases.csv`. They provide literal filenames for every main, bottom/catalog-equivalent, loading, empty, error, zero-history, warning, eligible, submitted, reviewed, disabled, create and edit case. The summary below must not override that catalog.

| Screen/state | Route | Exact Figma node(s) | Required captures |
| --- | --- | --- | --- |
| PUB-009 Product-only / Shop | `/merchants/33333333-3333-4333-8333-333333333333` | `312:3076`, 360 `312:3491`, acceptance `312:3743` | literal names in case catalog; 360 filename carries `312-3491` |
| PUB-009 Service-only / Provider | `/merchants/33333333-3333-4333-8333-333333333334` | `312:3289`, states `312:3689` | literal names in case catalog |
| PUB-009 mixed / empty / load error | exact deterministic routes in case catalog | states `312:3689`, loading `312:3692`, safe unavailable `312:3715`, acceptance `312:3743` | literal names in case catalog |
| BUY-004 create | `/me/addresses/new` | `327:4086`, backend error `327:4316`; desktop `564:5448`, error `564:5613`, compact `564:5873` | 360, 390, 1024, 1280 for default; 390/1280 for API error |
| BUY-004 edit | `/me/addresses/99999999-9999-4999-8999-999999999999` | same canonical mobile/desktop frames | literal names in case catalog |
| BUY-009 reputation | `/me/reputation` | `348:6173`, 360 `348:6259`, desktop `566:8794` | 360, 390, 1280 plus zero-history/warning/error at 390 |
| BUY-010 eligible/submitted/reviewed/disabled | the three literal `/orders/...211`, `/orders/...212`, and `/orders/...213` routes in the case catalog | `348:6032`, 360 `348:6085`, states `348:6127`, desktop `566:8427`, `566:8534`, `566:8626`, `566:8714`, compact `566:9076` | literal names and viewports in case catalog |
| Profile reputation entry | `/me` | profile desktop `566:9705` plus Buyer navigation inventory | 360, 390, 1280 |

- [x] Before each UI adjustment, retrieve that row's exact Figma design context, including PUB-009 acceptance `312:3743`; store the node in the evidence manifest.
- [x] Capture and manually review all eight PUB-009 rows from distinct Product-only, Service-only, mixed and empty fixture merchants. Twelve checksum-recorded entries pass automated diagnostics and hash-bound manual review, including streamed loading, cursor error/retry, exact mobile frames and responsive desktop/context-board derivatives. Figma metadata confirms `312:3689` and `312:3743` are context boards, so their cases use `responsive` review rather than false exact-overlay claims (2026-09-18).
- [x] Manually review all ten BUY-010 captures. The comparison exposed a mobile rating-hint collision; its red/green repair removed the negative offset, the reseeded 30-capture Buyer Playwright run passed, and eligible/submitted/reviewed/disabled evidence is bound to current runtime and Figma hashes (2026-09-18). The merged manifest is 22 PASS / 179 PENDING.
- [x] Complete the remaining Buyer/Public manual review: ten BUY-004 and ten BUY-009/profile/auth-boundary captures pass with hash-bound deviations recorded. Correct profile desktop from false `overlay` to `responsive` because `566:9705` is an accessibility context board, rerun the full Buyer spec, and re-review four automatically invalidated hashes. All 42 Buyer/Public captures now pass; the merged manifest is 42 PASS / 159 PENDING (2026-09-18).
- [x] Extend the Buyer acceptance specs to assert real public catalog cards/capability wording, BUY-004 create and edit round trips, BUY-009 aggregate, BUY-010 server eligibility transitions and all 19 remaining Buyer catalog cases; Buyer/Public now produces 42 automated-PASS captures across 27 cases.
- [x] Use response interception only for explicit loading/error/conflict presentation captures; main and empty states come from deterministic database records and real API responses.
- [x] Require all Buyer/Public captures and manifest entries to pass before clearing their visual labels; 42/42 are hash-bound manual `PASS`.
- [x] Run Buyer cases first and alone with one worker; the populated Buyer and complete Buyer/Public specs pass and every expanded token produces exactly one screenshot and one fragment record.

## Chunk 4: Seller acceptance set

### Task 5: Capture Seller routes and server-owned states

`apps/web/e2e/seller-flow.local.spec.ts` must consume every `sel-*` and `need-005/006-*` row in `docs/visual-acceptance-cases.csv`. That catalog supplies complete `/seller/33333333-3333-4333-8333-333333333333/...` routes, fixture state, assertion, exact node, review mode, literal filenames, and all applicable 360/390/1024/1280/1440 viewports. Every main Seller route has 1024, 1280 and 1440 evidence; 1440 is `overlay` for canonical desktop nodes, while 1024/1280 are `responsive` unless a same-size node is named.

| Screen | Route/surface | Exact node(s) |
| --- | --- | --- |
| SEL-001 | `/seller/33333333-3333-4333-8333-333333333333` | mobile `357:9`; desktop `358:8` |
| SEL-002 | `/seller/33333333-3333-4333-8333-333333333333/settings` | mobile `357:220`; desktop `358:189` |
| SEL-003/004 | exact list/new/edit routes ending `/locations`, `/locations/new`, `/locations/44444444-4444-4444-8444-444444444444` in the case catalog | `357:321`, `357:407`; desktop `358:324` |
| SEL-005 | `/seller/33333333-3333-4333-8333-333333333333/locations/44444444-4444-4444-8444-444444444444/delivery` | `362:358`, `362:445`; desktop `364:402` |
| SEL-006 | `/seller/33333333-3333-4333-8333-333333333333/locations/44444444-4444-4444-8444-444444444444/shipping` | `362:525`, `362:647`; desktop `364:402` |
| SEL-007 | `/seller/33333333-3333-4333-8333-333333333333/locations/44444444-4444-4444-8444-444444444444/minimum-order` | `362:718`; desktop `364:402` |
| SEL-008 | `/seller/33333333-3333-4333-8333-333333333333/locations/44444444-4444-4444-8444-444444444444/contacts` | `362:796`; desktop `364:402` |
| SEL-009 | `/seller/33333333-3333-4333-8333-333333333333/catalog` | mobile `368:709`; desktop `370:548` |
| SEL-010/011/012/013 | exact `/seller/33333333-3333-4333-8333-333333333333/catalog/new`, `/catalog/66666666-6666-4666-8666-666666666661`, and `/inventory` routes in the case catalog | `368:854`, `368:1026`, `368:1139`, `368:1271`, inventory `368:1410`; desktop `370:745` |
| catalog states | lifecycle/media/inventory | `368:1535`, `368:1578`, `368:1601` |
| SEL-014 | `/seller/33333333-3333-4333-8333-333333333333/orders` | mobile `374:1340`, tablet `374:1485`; desktop `375:850` |
| SEL-015 | exact deterministic order-detail routes in the case catalog | `374:1606`, `374:1744`, `374:1874`, `374:1984`, conflict `374:2091`; desktop `375:1070` |
| SEL-016/017 | reject/cancel surfaces | `387:13327`, 360 `387:13569`, `387:13416`; desktop `388:13366`, `388:13477` |
| NEED-005/006 | `/seller/33333333-3333-4333-8333-333333333333/needs` | `387:2001`, `387:2124`, `387:2277`, `387:2413`; desktop `387:13665` |
| SEL-018 | `/seller/33333333-3333-4333-8333-333333333333/reputation` | `389:13788`, tablet `389:13947`; desktop `389:13627` |
| SEL-019 | `/seller/33333333-3333-4333-8333-333333333333/notices` | `389:13846`, empty `389:13921`; desktop `389:13692` |

- [x] Assert membership isolation: the Seller cannot open another merchant's private route and no other merchant's rows appear.
- [x] Exercise real ETag/conflict, idempotent mutation, upload retry, order allowed-action revocation, candidate selection, pagination and empty states from seeded records.
- [x] Preserve documented contract gaps (coordinates, contact PATCH, reason taxonomies, notification destination); acceptance verifies truthful safe presentation instead of adding guessed controls.
- [x] Require `seller-flow.local.spec.ts` green and all applicable manifest rows manually approved before clearing Seller runtime/visual labels; 111/111 captures pass.
- [x] Run Seller cases only after Buyer completes: `pnpm --filter @covie/web exec playwright test e2e/seller-flow.local.spec.ts --project=chromium --workers=1`; all 36 Seller cases produced 111 unique screenshot/fragment records with automated `PASS`. Manual review remains pending, so Seller visual acceptance is not cleared.
- [x] Retrieve and validate all 47 unique Seller Figma reference PNGs; manually review and hash-bind 37 SEL-001…006 captures. Repair SEL-002's desktop profile card after red tests, correct SEL-004 route derivatives plus SEL-005/006 composite-board evidence to responsive review, and rerun Seller Playwright plus the manifest. The merged result is 79 PASS / 122 PENDING (2026-09-18); later Seller cases remain open.
- [x] Complete the remaining Seller review, including authoritative merchant-owned Need response replacement; all 36 Seller cases / 111 captures pass automated diagnostics and hash-bound manual review (2026-09-19).

## Chunk 5: Admin acceptance set

### Task 6: Capture Admin routes, read-only boundaries and safe states

`apps/web/e2e/admin-flow.local.spec.ts` consumes every `adm-*` row from `docs/visual-acceptance-cases.csv`, including fixture state, assertion and literal filenames. It uses `1024×900`, `1280×900`, and `1440×900` for each main row: 1440 is exact-frame `overlay`; 1024/1280 are `responsive` layout-contract reviews because no equal-size frozen derivative exists.

| Screen | Route | Exact node(s) |
| --- | --- | --- |
| ADM-001 | `/admin` | `393:4507` |
| ADM-002 | `/admin/users` | `393:4746` |
| ADM-003 | `/admin/merchants` | `393:5003` |
| ADM-004 | `/admin/shops` safe gap | `399:1190` |
| ADM-005 | `/admin/listings` safe gap | `399:1413` |
| ADM-006 | `/admin/orders` | `400:1545` |
| ADM-007 | `/admin/orders/12121212-1212-4121-8121-121212121221/history` | `400:1818` |
| ADM-008 | `/admin/needs` safe gap | `404:2216` |
| ADM-009 | `/admin/reports` | `404:2473` |
| ADM-010/011/012 | `/admin/settings` | `407:2674` |
| ADM-013 | `/admin/settings?section=orders` | `407:2946` |
| ADM-014 | `/admin/settings?section=needs` | `407:3149`, `408:3265` |
| ADM-015 | `/admin/zones` | `423:17219` |
| ADM-016 | `/admin/categories` | `423:17230` |
| ADM-017 | `/admin/audit` | `417:3838` |

- [x] Assert the Admin authorization boundary: a Buyer receives the not-found boundary for `/admin`, while the Admin sees the authorized shell and data.
- [x] Exercise users and merchants suspend/restore, moderation action with `409/412` refresh, settings `If-Match` conflict, order search/history, pagination, filters, loading, empty and backend-error states.
- [x] Verify ADM-004/005/008 remain explicit safe contract-gap screens and do not call Seller/private endpoints or fabricate production data.
- [x] Require `admin-flow.local.spec.ts` green and all applicable manifest rows manually approved before clearing Admin runtime/visual labels; 48/48 captures pass.
- [x] Run Admin cases only after Seller completes: `pnpm --filter @covie/web exec playwright test e2e/admin-flow.local.spec.ts --project=chromium --workers=1`; all 18 Admin cases produced 48 unique screenshot/fragment records with automated `PASS`. Manual review remains open.

## Chunk 6: Full gates and evidence synchronization

### Task 7: Run the complete local acceptance sequence

- [x] Run `Set-Location apps/api; .\mvnw.cmd clean verify`; 536 tests, 0 failures/errors, 1 skipped on 2026-09-17.
- [x] Run API-client generation/check, `pnpm web:typecheck`, `pnpm web:lint`, `pnpm web:test`, and `pnpm web:build`; 72 Vitest files / 194 tests plus all other gates pass on 2026-09-17.
- [x] Run `pnpm --filter @covie/web test:e2e`; 3 tests pass and 5 local-fixture specs are intentionally skipped outside the populated command.
- [x] Run `pnpm ui:verify-local`; it executes seed → populated Buyer → complete Buyer/Public → Seller → Admin → ordered manifest validation, each Playwright command with `--workers=1`, and all six stages pass.
- [x] Run the final `visual-manifest.local.spec.ts` only after all role fragments exist. It atomically writes `manifest.json` and validates exactly 81 cases / 201 records and files with one-to-one node, viewport, filename, dimensions and SHA-256 coverage.
- [x] Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1`; two consecutive seeds pass stable-count and address-state assertions.
- [x] Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1` and `scripts/tests/verify-ui-flow.test.ps1`; both exit `0`.
- [x] Record exact totals, branch/HEAD/dirty identity and output directory in the ignored merged manifest; no role spec was skipped in the populated acceptance sequence.
- [x] Final 2026-09-19 rerun: Maven 538 tests (0 failures/errors, 1 skip), API-client check, typecheck, zero-warning lint, 75 Vitest files / 208 tests, production build, standard Playwright 3 passed / 5 fixture-gated skips, full `pnpm ui:verify-local`, seed/launcher/orchestration regressions and 201/201 manual manifest pass.

### Task 8: Synchronize truthful status

**Files:**
- Modify: `docs/CoVie-ui-handoff-matrix.md`
- Modify: `docs/buyer-mobile-remaining-coverage-matrix.md`
- Modify: `docs/seller-mobile-coverage-matrix.md`
- Modify: `docs/seller-desktop-coverage-matrix.md`
- Modify: `docs/admin-desktop-coverage-matrix.md`
- Modify: `docs/CoVie-project-status.md`
- Modify: `AGENTS.md`

- [x] Link completed role matrices to the current 81-case / 201-capture manifest result; do not cite the legacy `chunk3` inventory as new runtime proof.
- [x] Remove current `RUNTIME_PENDING`, `VISUAL_PENDING`, or `VERIFICATION_PENDING` claims where direct evidence exists. Retain every unresolved `CONTRACT_GAP`/safe-state qualification and dated historical checkpoints.
- [x] Keep staging/release incomplete; local screenshots and tests are not deployment evidence.
