# Social V2 — native Search price/sort/deep-link regression

Date:2026-10-16. Scoped local11.3.8 evidence; not full release/staging/commerce or11.4 acceptance. No production application change.

## Fixture and accepted actual chain

New optional `covie.test.social-feed-search-filters=true` branch in existing `SocialFeedBrowserIntegrationTest`, used with guest flag, plus `apps/web/e2e/social-search-filters-browser.mjs`. Only fixture setup changes the second offer to60000 while target remains25000; existing SearchDocumentService indexes both seeded items before snapshots. This provides distinguishable actual results instead of merely observing a sort parameter or two equal-price cards. No live policy/data changes.

Chrome155.0.8059.84/1280; actual Next/API/disposable PostgreSQL/MinIO; no AT/RT/Authorization or issued actor session; local coordinate preference, not GPS. Real responses bridged unmodified, no body mocks. Six native stages:

| Stage | Observed results / contract |
| --- | --- |
| Initial deep link `type=PRODUCT&q=Social&sort=PRICE_ASC&view=social` | Cheap25000 first, expensive60000 second |
| Native “Sắp xếp” → “Giá cao nhất” | Reversed order; URL/API PRICE_DESC, keyword/type retained |
| Native desktop max price30000 + “Áp dụng giá” | Only cheap target remains; actual API result contains its exact UUID, URL maxPrice30000 |
| Native reload | Same one item, PRICE_DESC/maxPrice/q/type retained, price input repopulated30000 |
| Native Catalog switch | Same one item/filter/sort retained, no new discovery request |
| Native clear max input + apply | Both items restored expensive-first, maxPrice removed from URL, PRICE_DESC/q/type retained |

Five discovery200 responses for six stages. Assertions inspect both DOM result order and actual API page UUID order/filter. API writes0, comment reads0, pageErrors=[] after context close. Java independently checks evidence array/six stages and full11business-table JSONB snapshots unchanged, guest actor sessions0/reactions0. Ads-filled behavior remains the separate achieved report and was not rerun.

## Verification

Final **1PASS43.68s**, Maven**46.368s**,0skip: `.local/social-v2-search-filters-browser-final.log`; evidence directory of same basename. Targeted helper ESLint/node syntax/focused whitespace checks PASS. SearchResults, discovery repository, SearchDocumentService and Java fixture hashes match validation copies. Manifest deleted; owned API59765/Next59768 closed; normal3000/8081 absent/untouched.

Earlier harness failures, not product RED:
- `.local/social-v2-search-filters-browser.log`: helper incorrectly assumed top-level items; existing generated/backend contract is `page.items`. Corrected helper only.
- `...-verified.log`: accepted clear-price response/DOM arrived before asynchronous router URL replacement; helper read old maxPrice synchronously. Final helper explicitly awaits matching URL parameter as well as DOM. API/UI source unchanged, no assertion weakened.

No production BE/FE/API/schema/client/CSS/migration changes, full suites, old Ads/guest/buyer/gallery/lifecycle/race reruns, typecheck/build rerun, recovered/runtime DB access, normal restart, remote/commit/branch/push, activation or deployment. Normal V2/video flags OFF; whole objective ACTIVE.

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-search-filters=true -Dcovie.test.social-feed-guest=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-search-filters-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-search-filters-browser-final test
```

Local11.3.8 native price/sort/deep-link/mode behavior now verified in this scope, alongside previous Ads proof. This does not assert every category/fulfillment/sort combination, mobile sheet, four widths/a11y, staging or exact immutable release result. Desktop price controls are currently found by placeholder rather than explicit label; source lacks an associated label/aria-label for those two inputs. Preserve this as a concrete11.4 accessibility review candidate, not an inferred functional price bug or a reason to refactor unrelated UI. Do not repeat the unchanged successful functional chain.

Next action: Inspect reusable disposable commerce fixture/acceptance setup for literal11.3.9 without using existing local runtime data.
Required from you: Nothing
Completion condition: Identify a safe actual cart/checkout/order/verified-review path and required fixture effects, preserving existing working business contracts and recovered data.
