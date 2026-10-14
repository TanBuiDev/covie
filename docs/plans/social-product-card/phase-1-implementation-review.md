# Phase 1 — UI dual view (implementation review)

Date: 2026-10-14. Branch: `feature/social-product-card-phase1`. Base: `8df8c51628056c1d26db904e2345129b9febcddc`.

## Changes

- Shared `MarketplaceProductCard` for Product/Service/Resale based on existing discovery DTO; catalog remains the authoritative item source.
- Accessible `MarketplaceViewToggle` offers **Khám phá** and **Mua sắm nhanh** on home/search.
- Client-side preference: `view=social|catalog` URL parameter, then `covie:marketplace-view:v1` localStorage, then page default (home social, search catalog). Existing location key is untouched.
- Search view switch changes presentation only and does not alter filters, sorting, current cursor or the `discoverCatalogItems` useEffect dependencies. IN_FEED ad keeps the existing position in the result list; HOME_HERO unchanged.
- Home product groups remain `nearby`, `services`, `resale`, with their original endpoints and 4-item limits.
- Like/comment/save controls are visibly disabled as no API exists yet; no fake counters or fake success toasts. Web Share/clipboard report success only when it occurred.
- Catalog description is not in discovery summary; there is no invented excerpt, detail-per-card fetch or fake posts. Full description remains on item detail until an additive backend summary is available.

## Open gates / limitations

- Gate G0 previous runtime baseline is still **NOT PASSED** (no local repository checkout, screenshots, executable baseline results).
- This branch is an implementation candidate, **NOT** an approved production deployment. Do not merge before validating `pnpm web:typecheck`, `pnpm web:lint`, `pnpm web:test`, `pnpm web:build` and responsive screenshots at 360/768/1280/1440px.
- Search filters/URL/navigation/history must be regression-tested; current preference uses browser history API and storage. Deep-link params preserved only when other navigation routines do not rewrite them; update those routines if QA finds view loss.
- Phase 2 required to display verified social counts, comments, likes and saves. No database/API migrations made here.
- `CatalogCard` and merchant-specific catalog UI intentionally retained pending exhaustive callsite audit.
