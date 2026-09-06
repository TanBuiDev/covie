# CoVie V1 — Project Status Dashboard

**Last verified:** 2026-08-26
**Overall status:** Plans 01–10 are integrated on `main` for planning closure. The requested Public Buyer P0 route chain has populated local flow and screenshot acceptance for PUB-001, PUB-003…008 and BUY-001/002/003/005/006; PUB-009 Shop has a reviewed contract-safe first pass while its Provider variant remains open. BUY-004 create/edit and remaining Figma units, authenticated staging journeys and release acceptance remain open.
**Current implementation plan:** Verification and carry-over hardening — see `CoVie-plan-closure-audit-2026-08-24.md`.

## Purpose

### Academic-report checkpoint — 2026-09-04

The local source tree inspected for the report is `main` at `c477f30`, newer than the historical `42693a6` snapshot below. `docs/academic-report/` now contains a seven-chapter Vietnamese DOCX report, editable sources, static inventory, diagrams and a continuation README. Official HUTECH logo and student metadata were added; advisor omitted at user request. Faculty and major supplied: Khoa Công nghệ thông tin; Công nghệ phần mềm. LibreOffice now renders the report successfully: 72 pages visually inspected, 163 navigation targets and 150 cached page references verified, and 116 structural checks passed on 2026-09-04. Fonts, table layout and paginated navigation were refined; checksum-bound evidence is in render-verification.json. Institutional formatting remains subject to the supplied faculty template. This documentation task did not rerun application tests or perform staging/production deployment.

The source review found that checkout passes distance zero into the shipping evaluator and does not establish full geographic coverage enforcement in the inspected revalidation path. Item/contact/shipping-policy snapshots are also incomplete relative to the intended historical snapshot model. These are static findings to reproduce and address before claiming the corresponding capabilities complete. Newer UI matrices must be read with their own pending runtime/visual gates; the older test figures below remain historical.

This is the single progress dashboard for CoVie V1. It records completed work only when there is reviewable evidence: a commit or pull request, fresh verification output, and the relevant plan acceptance gate.

## Source-of-truth order

1. Business scope and user flows: Product Plan, User Flow, and IA/UI Flow.
2. Data and HTTP contracts: Database Design and API Design.
3. Engineering sequence and acceptance: Master Technical Plan and Plan 01–10.
4. Visual implementation reference: the frozen CoVie V1 Figma file.

## Current snapshot

| Area | Status | Evidence / note |
|---|---|---|
| Product, UX, database, API and technical planning | Complete | Approved documents in `docs/` |
| Figma design source | Complete | Figma cover records 37/37 production units `FROZEN`; cross-screen QA and critical prototypes `PASS` |
| Web UI implementation against Figma | In progress | PUB-001, PUB-003…008 and BUY-001/002/003/005/006 use exact frozen frames, exported SVG icons, Inter/Emerald tokens and buyer navigation. PUB-009 Shop now renders public identity, reputation, reviews, safe contacts and locations; the Provider discriminator and public catalog/fulfillment data are absent from current contracts, so the unit remains incomplete. BUY-003 Address List now covers main, delete, empty, loading and error states; BUY-004 remains open. `pnpm ui:verify-local` passed the populated Home → Search → Product Detail → Shop Profile → Address List → Service Detail → Resale Detail → Cart → Checkout → Success → Orders journey and captured target viewport comparisons on 2026-08-26. Other Figma units remain open. |
| Repository implementation | Integrated through Plan 10 | `main` and `origin/main` point to `42693a6` (`merge: close plans 01-10`); Flyway migrations are sequential from `V001` through `V013`. |
| Local development launcher | Fixed and cold-start verified | `pnpm dev` now runs Maven Wrapper from `apps/api` and derives `JAVA_HOME` from `java` on `PATH` when unset. Regression script passed; API and web returned HTTP 200 after a clean launcher start on 2026-08-25. |
| Identity, auth and user implementation | Complete | Plan 02 Task 16 fresh gate: backend `clean verify` (84 tests), regenerated OpenAPI/client, web lint/typecheck/test/build (20 web tests), Flyway V001–V003 and secret hygiene checks (2026-08-12) |
| Production / pilot | Not started | No staging or production environment has been provisioned or deployed. It depends on the carry-over verification and rehearsal gates. |

## Delivery checklist

| Plan | Scope | Status | Required evidence before marking complete |
|---|---|---|---|
| 01 | Foundation & Repository | Complete | Local infra, API/web boot, OpenAPI client and CI acceptance gate green (2026-08-09) |
| 02 | Identity, Auth & User | Complete | Task 16 fresh CI/documentation/acceptance gate is green (2026-08-12) |
| 03 | Merchant, Location, Media & Catalog | Closed — locally verified | Included in the integrated backend regression scope |
| 04 | Shipping, Cart, Inventory & Checkout | Closed — locally verified | Included in integrated regression, migration and concurrency scope |
| 05 | Orders & Fulfillment | Closed — locally verified | `V010` and lifecycle coverage are integrated |
| 06 | Need Requests & Matching | Closed — locally verified | `V011` and focused concurrency/query gates are integrated |
| 07 | Reviews, Reputation, Outbox, Notifications & SSE | Closed — locally verified | `V012`, notification/outbox behavior and regression coverage are integrated |
| 08 | Moderation, Settings & Admin | Closed — locally verified | `V013`, authorization and contract coverage are integrated |
| 09 | Web Completion / PWA | Closed for planning — UI carry-over in progress | Requested Public Buyer P0 through PUB-008/BUY-006 plus PUB-009 Shop and BUY-003: typecheck, production build and 103 unit tests pass; the populated journey including Shop, Address List and Resale Detail passes (2026-08-26). Lint retains 2 pre-existing warnings and 0 errors; staging P0 journeys remain open. |
| 10 | Production Hardening & Pilot Launch | Closed — carry-over required | Staging rehearsal and release evidence are open |

## Public Buyer P0 local acceptance — 2026-08-26

- `pnpm ui:verify-local`: 1/1 populated Playwright journey passed on the first checkout commit; the generated credential remains only in ignored `.local/dev/ui-fixture.json`.
- Visual evidence: `.local/dev/visual-review/` contains Home, Results, Product Detail, Shop Profile, Address List, Service Detail, Resale Detail, Multi-cart, Cart Detail, Checkout Preview and Checkout Success at 390×844, 360×800 and 1280×900; Shop has a bottom-scrolled review and Address List has an inline-delete review at 390×844.
- Recurrence guards added for CSRF rotation, pre-hydration credential submission, server-side catalog URL resolution, checkout navigation overlap, first-page order queries and the fixture's UTF-8/category/cart invariants.
- Fresh repository gates: API `460` passed / `1` skipped before the UI-only batches; web typecheck, `103/103` tests and production build pass; standard E2E `2` passed / `1` fixture-gated skip; populated E2E `1/1`; launcher and fixture regression scripts exited `0`.

## Update protocol

1. Treat the Plan 01–10 checklists as historical specifications; do not tick them retroactively.
2. Add the commit/PR reference and exact verification commands/results to the relevant handoff or closure audit.
3. Update this dashboard and `AGENTS.md` in the same change whenever implementation status changes.
4. Record blocked dependencies and source-of-truth conflicts in the relevant handoff.
5. Do not label a plan release-accepted until its required environment gate is green and evidence is recorded.

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

Page 1 now follows the user-supplied HUTECH cover composition while retaining CoVie metadata. It includes Ngành Công nghệ thông tin and Chuyên ngành Công nghệ phần mềm, omits the advisor, and uses a cover-only double frame. QA `.local/report-cover-reference-v2/`: 109 pages; pages 1–2 inspected and pages 2–109 unchanged by exact image SHA-256. Structural and navigation verification remains 121 checks, 228 targets and 206 cached page references.


## Academic report explanatory figures — 2026-09-05

Seven explanatory figures now illustrate text-heavy sections in Chapters 1, 2, 4, 6 and 7. They are generated from the report's existing source model, include captions and source notes, and do not add runtime claims. Current report: 113 pages, 52 figure captions, 40 tables. QA .local/report-explanatory-final/: all pages inspected; 235 navigation targets, 213 cached page references and 122 structural checks.
