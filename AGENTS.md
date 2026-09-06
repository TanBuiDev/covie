# CoVie project context

**Last updated:** 2026-09-04
**Repository state:** local `main` at `c477f30` (`test(admin): align coverage with desktop contracts`) when the academic report was authored. Remote synchronization was not checked in this documentation task. The delivery/test figures below retain their explicitly dated 2026-08-26 baseline; they are not fresh verification of this HEAD.

## Academic report and source review — 2026-09-04

`docs/academic-report/CoVie_BaoCaoDoAn.docx` contains the Vietnamese academic-report draft: seven chapters, 52 figures including 21 diagrams and 43 product screens, forty tables, sources and appendices. Editable chapter sources, builder, evidence manifest and a continuation checkpoint are alongside it. The report inventories 13 migrations, 66 CREATE TABLE declarations and 119 OpenAPI paths / 144 HTTP operations; these are static artifact counts, not a live database or endpoint audit. Official HUTECH logo and user-supplied cover metadata are present: Bùi Lê Hoàng Tấn, 2280602879, 22DTHH2; Hồ Chí Minh, 07/2026. Advisor omitted at user request. Faculty and major supplied: Khoa Công nghệ thông tin; Công nghệ phần mềm. Institutional formatting template remains open. LibreOffice is now installed. The current packaged render has 113 pages, 235 navigation targets, 213 cached page references and 122 structural checks. The report remains a draft subject to institutional formatting requirements; this is artifact verification, not a new application regression run. See the report README and verification output for the exact artifact checks.

Source inspection for the report identified additional limitations: `CheckoutPreviewService` passes distance `0` to `ShippingEvaluator`, and the inspected revalidation path does not establish complete geographic coverage enforcement. Item snapshots are limited; contact snapshots are empty objects and the inspected evaluator returns a null policy snapshot. Do not claim end-to-end distance shipping or complete historical snapshot independence without focused runtime verification and any needed fixes. No application code was changed or application regression rerun by the report task. Newer Buyer/Seller/Admin matrices record implementation beyond the old baseline, with contract/runtime/visual gaps still scoped by their own evidence.

## Read this first

This file is the operational context for humans and AI agents. Update it in the same change whenever project scope, delivery state, architecture, verification evidence, or a known gap changes. Do not infer completion from a plan title, passing unit tests, or a merged branch alone.

## Product and source of truth

CoVie is a Vietnamese local marketplace/PWA for buyers, sellers, and administrators.

- Visual source of truth: [CoVie — V1 UI Design](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=0-1).
- The Figma file records 37 production units as **FROZEN**. Figma controls visual tokens, layout, responsive behavior, and component states.
- Product/domain and operational references live in `docs/`; see `docs/CoVie-ui-handoff-matrix.md`, the technical plans, and `docs/operations/`.

### Figma page map

Use these exact page links to enter the relevant design area. A page node is navigation context only: before editing a route, select and retrieve that route's exact production frame `node-id` within the page.

| Page | Exact Figma link |
| --- | --- |
| 00 — Cover & Index | [node `0:1`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=0-1) |
| 01 — Foundations | [node `3:2`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-2) |
| 02 — Components | [node `3:3`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-3) |
| 03 — Buyer Mobile | [node `3:4`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-4) |
| 04 — Seller Mobile | [node `3:5`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-5) |
| 05 — Seller Desktop | [node `3:6`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-6) |
| 06 — Admin Desktop | [node `3:7`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-7) |
| 07 — Prototypes | [node `3:8`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-8) |
| 99 — Archive | [node `3:9`](https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie-%E2%80%94-V1-UI-Design?node-id=3-9) |

## Architecture

| Area | Implementation |
| --- | --- |
| Web | Next.js 16, React 19, TypeScript, pnpm workspace (`apps/web`) |
| API | Spring Boot 4, Java 25, Maven (`apps/api`) |
| Data | PostgreSQL + PostGIS, Flyway migrations |
| Supporting services | Redis and MinIO/S3-compatible media storage |
| Contracts | OpenAPI in `packages/api-client/openapi/openapi.json`; generated TypeScript client in `packages/api-client/src/generated/` |
| Operations | Docker Compose, GitHub Actions, deployment/rollback, backup, observability, security and release runbooks under `infra/` and `docs/operations/` |

## Integrated product scope

The following domain capabilities are present in `main` and have automated verification coverage:

1. Repository foundation, migrations, shared error/idempotency/outbox conventions.
2. Identity, password/OAuth flows, sessions, user profile and addresses.
3. Merchant, location, catalog, public discovery and media foundations.
4. Cart, shipping, inventory reservations, checkout preview/commit.
5. Order creation, authorization, lifecycle, cancellation, fulfillment, issues and read models.
6. Buyer need requests, merchant matching/responses and need-sourced checkout.
7. Reviews, reputation/trust projections, notifications and SSE invalidation.
8. Moderation, reports, administration, settings and audit operations.
9. Web routes, API-client consumption, functional route flows, unit/E2E harness.
10. Production-oriented Docker/CI, deploy/rollback, backup/restore, observability, security and release artifacts.

## Current delivery status — do not overstate

| Area | Status | Evidence / limitation |
| --- | --- | --- |
| Backend/domain implementation | Integrated and verified locally | `mvnw clean verify`: 460 tests passed, 1 skipped (2026-08-26). The regression includes buyer/merchant first-page order queries without nullable cursor parameters. |
| Web functional implementation | Integrated and verified locally | API client/typecheck, 103 Vitest tests and production build pass; standard Playwright has 2 passed and 1 intentional local-fixture skip; populated Public Buyer P0 Playwright has 1 passed (2026-08-26). |
| Git integration | Complete | Plan branches/worktrees were merged and cleaned; only `main` remains. |
| Figma UI fidelity | **In progress; not complete** | The requested Public Buyer P0 chain has populated local acceptance for PUB-001, PUB-003…008 and BUY-001/002/003/005/006 against exact frozen frames at 390×844, 360×800 and 1280×900. The PUB-009 Shop variant has a contract-safe first pass and viewport review; the Provider variant and contract-unavailable catalog/capability details remain open. BUY-004 create/edit remains open. Other routes remain scaffold/open unless recorded in the handoff matrix. |
| Staging/release rehearsal | **Not complete** | No real staging environment, production credentials, or deployment evidence is available. No staging or production deployment has been performed. |
| Lint | Non-blocking warnings remain | Two existing Next.js warnings remain (`catalog-card` image and catalog-create navigation); no lint errors. |

## Known UI gap

PUB-001, PUB-003…008 and BUY-001/002/003/005/006 now use the frozen Inter/Emerald foundation, exact exported SVG assets and buyer navigation. Search covers URL filters, FilterSheet and category states, including the queryless-category sort adapter required to avoid backend 422. Product and Service detail use real offer availability/price; Product Detail links its contract-backed merchant to the PUB-009 profile. Service selection uses native radios, always submits quantity one and explains V1 coordination without inventing calendar booking. Resale detail uses the exact media/listing/card/action anatomy, always submits one unit, and does not invent the sample condition percentage or pickup/delivery eligibility absent from the public contract. The PUB-009 Shop first pass renders public merchant identity, reputation, reviews, safe contacts and locations; its Provider discriminator, fulfillment/open cues and public catalog cards are unavailable in current public contracts and remain open. BUY-003 uses dedicated contract-backed address cards, focused navigation, inline destructive confirmation and loading/empty/error states; BUY-004 create/edit routes remain open. Cart/checkout preserve separate Product/Service quantities, `If-Match` and checkout-token conflict recovery, backend-only totals and truthful order-created semantics. `pnpm ui:verify-local` creates a deterministic ignored fixture and verifies the real Home → Search → Product Detail → Shop Profile → Address List → Service Detail → Resale Detail → Cart → Checkout → Success → Orders journey, while writing viewport comparisons to `.local/dev/visual-review/`. Exact type-specific wording in route-level `loading.tsx`/`error.tsx` remains constrained because Next.js supplies no route params to `loading.tsx`; see the handoff matrix. Remaining routes are functional scaffolds unless recorded otherwise.

Before changing a UI route:

1. Obtain the exact Figma frame URL including `node-id`.
2. Read its design context and map it to existing project components/tokens.
3. Implement loading, empty, error, unauthorized, desktop and mobile states.
4. Compare a rendered route with the Figma frame and record the review.
5. Only then mark that route visually complete.

## Local development

After the one-time `.env.local` and disposable JWT-key setup, start the complete local stack with:

```powershell
pnpm dev
```

This starts PostgreSQL, Redis, MinIO, the API and web app. Stop it with `pnpm dev:stop`. Logs are ignored under `.local/dev/`.

The launcher runs Maven Wrapper from `apps/api` so its `.mvn/wrapper` files resolve correctly. If `JAVA_HOME` is unset but `java` is available on `PATH`, it derives `JAVA_HOME` from that JDK automatically; otherwise it reports the required JDK setup explicitly. The regression check is `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1`.

The default port `8080` is occupied by an environment service in this workspace. The launcher runs API on `8081` and web on `3000`.

```powershell
http://localhost:3000
```

Required API local configuration includes PostgreSQL (`5433`), Redis (`6379`), MinIO (`9000`) and disposable local JWT PEM files in ignored `infra/secrets/dev/`. Generate missing local keys with `infra/scripts/generate-dev-jwt-keys.sh`; never commit PEM files or real OAuth credentials.

For repeatable populated Public Buyer P0 acceptance, run `pnpm ui:verify-local`. It seeds only deterministic local fixture records, stores the generated buyer credential in ignored `.local/dev/ui-fixture.json`, and writes screenshots under ignored `.local/dev/visual-review/`.

## Verification commands

```powershell
# API
Set-Location apps/api
./mvnw clean verify

# From repository root
pnpm api-client:check
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
pnpm --filter @covie/web test:e2e
pnpm ui:verify-local
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/seed-ui-fixture.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-script.test.ps1
```

## Documentation maintenance checklist

- Update this file for every material implementation, merge, rollback, environment change, verification result, or known limitation.
- Update the matching technical plan and runbook when behavior or operations change.
- Record the exact Figma frame and visual-review evidence when a UI screen is completed.
- Keep generated OpenAPI and API client synchronized after API contract changes.

## Required AI progress response

During work, do not stop at a diagnostic report. Every substantive response to the user must end with these explicit sections, in this order:

1. **Next action:** the single concrete action the AI will take next. Continue it without waiting when it is read-only or already authorized.
2. **Required from you:** only the exact decision, credential, access, URL, asset or approval that blocks that action; write `Nothing` when no user input is needed.
3. **Completion condition:** the observable result that will allow the AI to move to the following action.

If an input is missing, explain why it is required, give the shortest actionable way to provide it, and stop only when the missing input materially blocks safe progress. Never leave the user to infer the next step from a long status report.

Academic report front matter: the completion-instructions page was replaced with a drafted declaration, acknowledgments and tool-use disclosure at the user’s request. No cover/front-matter placeholders remain. The 72-page report retains historical technical evidence; only pages 2 and 72 changed in the latest render.

Academic report cover redesign: adapted official HUTECH IT DATN/DACN covers with a small left logo, institutional header, centered topic, aligned student metadata and cover-only double frame. Latest QA is `.local/report-cover-final-v2/`; pages 1–2 inspected and pages 3–72 hash-identical to the previously reviewed render. Source selection is recorded in `docs/academic-report/cover-design.json`.

Report header update: removed the running title from all page headers, retaining footer page numbers. Latest QA: `.local/report-noheader-final/`; 72 pages, with pixel changes confined to the removed header and all remaining regions unchanged.

Footer field repair: replaced empty simple PAGE field with begin/instruction/separate/cached-result/end structure. LibreOffice output verified sequential page numbers 2–72; all 72 page images unchanged. QA: `.local/report-pagefield-final/`. Static viewers that do not evaluate PAGE fields cannot guarantee per-page numbering; Word/LibreOffice must evaluate the dynamic field.


Academic report table redesign: all 40 tables restyled with dark gray headers, white header text, subtle alternating rows, fine borders and content-specific alignment. SQL and use-case column widths adjusted; table contents compared unchanged. Final QA: `.local/report-tables-verified/`, 72 pages, 163 navigation targets and 150 cached references verified; 116 structural checks pass. Changed pages visually inspected; unchanged pages carried forward by image SHA-256. Style reference and settings: `docs/academic-report/table-design.json`.

Academic report editorial revision: replaced authoring instructions and conversational metadata with academic prose throughout the report; rewrote Appendix D, methods, implementation/test descriptions and figure sources. Historical 2026-08-26 application evidence and known limitations remain explicitly scoped. Latest report QA is `.local/report-editorial-verified/`: 71 pages, 163 navigation targets, 150 cached page references and 116 structural checks. Changed pages visually inspected; unchanged body regions matched to previously reviewed renders, with sequential footers separately verified. Chapter sources and Markdown synchronized; no application code or regression results changed. Previous 72-page QA notes describe earlier revisions.

Report chapter alignment: centered all seven body chapter titles (Heading 1 beginning CHƯƠNG), preserving TOC alignment and all text. QA `.local/report-centered-chapters/`: 71 pages; seven changed chapter-opening pages visually inspected, remaining 64 PNGs hash-identical to the reviewed editorial render. 163 navigation targets, 150 cached references and 116 structural checks pass. Builder/refine preserve the alignment through apply_editorial.


Academic report terminology formatting: retained English and technical terms now use curly double quotation marks in Vietnamese prose, headings, navigation, all 40 tables and five source-generated diagrams. English abstract, executable commands, URLs, literal paths/file names and original screenshots retain their syntax/content. All 1607 text paragraphs match the previous report after quote normalization (excluding cached page fields); seven chapter titles remain centered. Latest QA: `.local/report-terminology-complete/`, 71 pages, 163 navigation targets, 150 cached references, 116 structural checks passed. Per-page visual provenance: `docs/academic-report/terminology-visual-review.json`. No application code changed or regression rerun.


Academic report prose revision: rewrote 20 paragraphs/table cells covering screenshot attribution, evaluation methods, test scope, seller implementation and technical limitations. Screenshot sources now read “Nguồn: giao diện CoVie với dữ liệu thử nghiệm.” Chapter Markdown and regeneration rules are synchronized through academic_prose.py. Historical test dates and unverified runtime limitations remain explicit. Latest QA: .local/report-prose-final-v3/, 71 pages; 163 navigation targets, 150 cached references and 116 structural checks pass. Changed pages inspected; unchanged pages matched by full PNG SHA-256. Per-page provenance: docs/academic-report/academic-prose-visual-review.json. No application code changed or regression rerun.


## Academic report product expansion — 2026-09-05

Expanded Chapter 5 into 27 sections with 31 product figure groups containing 43 selected screens: 29 existing application screenshots with test data and 14 Figma design exports. Covers buyer, seller and administrator journeys on desktop/mobile plus empty, error and concurrent-change states. Figma images are explicitly attributed as designs, not runtime acceptance evidence. Added a product journey overview to Chapter 1 and synchronized Chapter 6, reference N9 and Appendix D. One overlapping shop screenshot was excluded from the final selection.

Current report: 99 pages, seven centered chapter titles, 36 figure captions (five diagrams plus 31 product figures), 40 tables. QA `.local/report-product-delivery/`: 209 navigation targets, 196 cached page references and 120 structural checks passed. Changed pages visually inspected; unchanged page bodies matched by pixel SHA-256 to previously reviewed pages and footers verified separately. Exact provenance: `docs/academic-report/product-visual-review.json`. Historical source/test dates and runtime gaps remain unchanged; no application code changed or application regression rerun. Sources and reproducible chapter assembly: `product-expansion-content.json`, `product-image-manifest.json`, `product-figma-inventory.json`, `product_expansion.py` under the report directory.


## Academic report use-case expansion — 2026-09-05

Added nine use-case diagrams in Chapter 3 section 3.12 (pages 29–37): buyer, seller and administrator role views, followed by six views corresponding to UC-01 through UC-06. Each uses external actors, the CoVie system boundary, goal ellipses and undirected participation associations; the prose explains scope, conditional actions and links to the existing specifications. No sequence or include/extend dependency is implied by those associations. The original actor overview remains as Figure 3.1; new figures are 3.2–3.10.

Current DOCX: 109 pages, 45 figure captions (14 diagrams and 31 product figure groups containing 43 screens), 40 tables. QA `.local/report-usecase-final/`: 228 navigation targets, 206 cached references, 121 structural checks and all footer numbers verified. Nine diagram pages and changed navigation pages inspected; unchanged page bodies matched by exact pixel SHA-256 to reviewed pages. Provenance: `docs/academic-report/usecase-visual-review.json`. All 40 table contents compared unchanged. No application code changed or application tests rerun.

Reproduction: `docs/academic-report/usecase_expansion.py` stores the diagram models, drawing and chapter insertion; `usecase-diagrams.json` records the models and asset hashes. Run after product expansion, then render, refresh navigation, render again and verify. The main builder applies both expansion steps. Chapter Markdown and the combined Markdown are synchronized.

## Academic report cover alignment — 2026-09-05

Reworked page 1 from the user-supplied HUTECH cover example: a near-edge double frame, small upper-left HUTECH logo, upper-right ministry and university block, restrained centered report/topic titles, aligned major and student blocks, and bottom location/date. Added Ngành Công nghệ thông tin, retained Chuyên ngành Công nghệ phần mềm and omitted the advisor as requested. QA `.local/report-cover-reference-v2/`: 109 pages; pages 1–2 inspected, pages 2–109 hash-identical to `.local/report-usecase-final/`, 228 navigation targets, 206 cached page references and 121 structural checks. Provenance: `docs/academic-report/cover-reference-visual-review.json`.


## Academic report explanatory figures — 2026-09-05

Added seven source-derived explanatory figures to text-heavy sections: the CoVie value journey, event-to-UI update flow, technology-layer responsibilities, request protection layers, testing strategy, evidence categories and delivery roadmap. The figures summarize existing report content and are not treated as new runtime evidence. Current DOCX: 113 pages, 52 figure captions (21 diagrams plus 31 product groups containing 43 screens), 40 tables. QA .local/report-explanatory-final/: all pages inspected, 235 navigation targets, 213 cached page references and 122 structural checks. Figure models and asset hashes: docs/academic-report/explanatory-figures.json; visual provenance: docs/academic-report/explanatory-visual-review.json.
