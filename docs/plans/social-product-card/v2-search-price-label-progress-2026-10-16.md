# Social V2 — desktop Search price accessible names

Date:2026-10-16. Concrete11.4 screen-reader-label defect, not a filter/API behavior change.

## Evidence and minimal correction

Existing desktop minimum/maximum price number inputs had placeholders but no associated label or aria-label. Added one targeted test in existing `tests/search.test.tsx` that computes both accessible names and also applies10000/30000 to the existing filter adapter. Before production change, both names were actually empty: **1test failed with2 accessible-name assertions**, `.local/social-v2-search-price-label-red.log`. This is concrete DOM evidence, not an assumed accessibility risk.

Changed only two production attributes in `features/discovery/search-results.tsx`: aria-label “Giá từ (₫)” and “Giá đến (₫)”, matching existing mobile-sheet terminology. No handlers, values, min/max constraints, placeholders, style/layout, business rules, API or contract changed. No refactor or extra speculative matrix.

## Verification

- `.local/social-v2-search-price-label-green.log`: **3PASS5.40s**, new accessible-name/value test plus existing price/fulfillment/reset and URL-derived synchronization tests. Nine other tests intentionally skipped by name filter.
- A test-only `exact` option copied from Playwright was rejected by Testing Library's TypeScript role options; removed it (string names already exact by default). Final new-test **1PASS4.32s**, `.local/social-v2-search-price-label-final.log`; no second unrelated regression rerun.
- Final direct installed `tsc.cmd --noEmit` on matched isolated copy: exit0, `.local/social-v2-search-price-label-typecheck-final.log`; targeted component/test ESLint exit0 and focused whitespace check PASS.
- Initial pnpm wrapper in the copied workspace tried dependency validation/install and failed missing workspace package `@covie/api-client` before typecheck, `.local/social-v2-search-price-label-typecheck.log`. No workaround install or dependency reconfiguration performed; original api-client link remains available. Used the installed compiler directly; initial compiler failure was solely the new test option above, not production props.
- Component and test SHA256 match isolated validation copies. No browser/server started; normal3000/8081 absent/untouched. Existing native Search price/sort/reload chain remains historical functional evidence, not rerun merely for two accessible-name attributes.

No BE/schema/client/API/CSS/migration/normal-config change, full suite, browser chain, recovered/runtime DB access, normal restart, remote operation, flag activation or deployment. Whole objective ACTIVE; normal V2/video flags OFF. This is computed accessible-name/component evidence, not a new actual screen-reader-device/four-width/whole-WCAG/staging acceptance claim.

The previous configured production image/build predates these two SearchResults attributes. Keep its packaging/runtime/public-asset evidence, but do not label that image current-tree proof. A fresh exact-source production build remains required once UI corrections settle, not after every isolated test/helper change.

Next action: Reconcile current11.4 viewport/actor/content/network/accessibility/security evidence and select the remaining literal local gaps.
Required from you: Nothing
Completion condition: Produce an evidence-backed remaining matrix without repeating achieved unchanged cases or inventing exhaustive cross-product gates.
