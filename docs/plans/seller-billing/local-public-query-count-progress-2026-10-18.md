# Seller Billing — measured public/Need query counts

Whole original Seller Billing/payOS goal ACTIVE / NOT_READY. Original BIL018 now has measured local query-count evidence in addition to the previous entitlement correctness checks. This is not a production load/SLO claim. Normal flags remain OFF; no transfer, real PSP, remote Git write, commit, branch, deployment, normal-stack restart or recovered-data operation.

## Previous full gate: terminal success, not current completion

Previous continuation made actual progress by reproducing/fixing four public entitlement errors. Its full verify TERMINALexit0 `.local/billing-public-entitlement-full-verify.log`:1013reported/1012PASS/zero failures/errors/1SKIP,8m33s. Independent XML `.local/billing-public-entitlement-full-verify-summary.json` was saved before focused reports overwrite it. Billing HTTP33cases pass, including the prior public-read/Buyer-history additions. The earlier unchanged Order dispatch failure does not recur; no Order production/test change or root-cause fix is claimed.

The remaining skip is the configured hot-query JDBC/query-file gate, not a pass or demonstrated external blocker. Eight previous manifest files excluding the newly edited HTTP test match exactly after the full run in `.local/billing-public-post-full-source-check.json`. The new query-counter fixture and measurement method were added after this full run compiled; they are not retroactively in1013. The full results predate the performance fixes below.

## Actual request-scoped measurement

New test-only `RequestSqlCounter` wraps the fixture's real DataSource/Connections/Statements, delegating execution and original exception causes. A test-only servlet filter activates a ThreadLocal counter only for explicitly marked fixture requests; results are consumed by a random request ID. Counts include application JDBC `execute*` calls from both JPA and JdbcTemplate on the request thread. No query text, parameter, credential or user payload is collected or logged. No SQL result is mocked. The filter/configuration is under src/test and is not a production API or security bypass. Background scheduler calls are outside the request counter.

The actual issued-JWT/HTTP/PostGIS fixture creates a paid merchant,20different items/offers/locations, and20actual Seller responses to one actual Buyer Need. Public and Need GETs assert200, actual returned cardinality1or20 and a positive counter. Detail measures one versus20variants on an actual item. `assertAll` records all four surfaces before evaluating growth, so the Need failure cannot mask Detail.

`.local/billing-public-query-count-red.log` TERMINALexit1,one case/one failure/zero errors/skips,42.880seconds; two measured violations:

| Surface | One returned item/variant | Twenty | Result before fix |
| --- | ---: | ---: | --- |
| Search | 2 | 2 | Already constant; unchanged |
| Shop catalog | 3 | 3 | Already constant; unchanged |
| Buyer Need responses | 14 | 166 | Confirmed N+1 |
| Public Detail variants | 15 | 34 | Confirmed N+1 |

The Need reader loads merchant/location/items/offer/variant/catalog/inventory inside each response/item mapping. Detail queries option links once per variant. These are measured defects against original BIL018, not a speculative architecture optimization.

## Targeted correction

- Need response rows are still fetched/paginated with the existing cursor. Their related records are fetched by bounded ID sets once per page, then mapped without repository calls inside DTO loops. The single merchant-response reader reuses the same lookup records.
- New batch response-item query retains the original ACTIVE predicate and created-at ordering. New batch usable-offer query retains the original offer/variant/catalog soft-delete joins. Deleted merchant-name behavior, stock, moderation, Need status, billing eligibility, historical price and current-price semantics are preserved.
- Detail loads option links once for the active-variant ID set, then groups IDs for the existing variant DTO construction. Existing single-ID methods remain available for other callers.
- No transaction mode, global cache, index, migration, REST field, generated client, frontend, billing price or business rule changes. No history row is rewritten. Repository batches are limited by the existing page/item bounds; no unbounded cross-tenant scan is introduced.

Only five production files change in this correction: NeedResponseReadService, NeedResponseItemRepository, LocationVariantRepository, CatalogDetailService and VariantOptionValueRepository. This is not a general repository/service refactor.

## Focused final proof

`.local/billing-public-query-count-green.log` TERMINALexit0:76PASS/zero failures/errors/skips,1m32s:

- Seven Billing HTTP cases (26.91seconds): measured four-surface query counts plus four public grace/expired cases and two Need history/availability cases.
- Eight existing public Detail cases (36.18seconds) and61existing Need eligible/response cases (12.45seconds) pass unchanged, covering existing contracts/filters/authorization/response behavior.

| Surface | One returned item/variant | Twenty | Final scoped result |
| --- | ---: | ---: | --- |
| Search | 2 | 2 | Constant |
| Shop catalog | 3 | 3 | Constant |
| Buyer Need responses | 13 | 13 | N+1 removed |
| Public Detail variants | 15 | 15 | N+1 removed |

The previous79-case correctness run is separate, not added to76as an invented aggregate. Search/Shop SQL correctness and Buyer/Social history evidence remain valid for their unchanged paths. Original BIL018 is locally PASS based on scoped functional+measured evidence; original matrix21/30,not whole project acceptance. This does not establish arbitrary cardinalities, all cross-products,500+merchant load, index-plan health or latency SLOs; remaining operational requirements stay open.

After focused verification, only identification-variable naming in the new batch JPQL was normalized; predicates remain identical. The following full gate validates the final exact source including this change and the new test-only counting DataSource wiring across the whole Billing fixture.

## Provenance and next gate

Fourteen source files have matching owned copies/SHA256 in `.local/billing-query-count-source-manifest.json`. Gitleaks8.30.1 delta scan exits0/no findings,216687bytes, `.local/billing-query-count-security.log` and redacted JSON. No ignore/allowlist change. `git diff --check` exits0 with the existing generated-schema EOL warning. This is not an exhaustive dependency/image/CodeQL security PASS; earlier security limitations remain.

The initial full `.local/billing-query-count-full-verify.log` is interrupted/unproven: it stops at02:44 after the four successful query measurements,without BUILD SUCCESS/FAILURE. After later user input, at06:59 the exec handle30220 is missing and authoritative process/container/port inventory finds no owned Java/Node/Python process or container,normal3000/8081 absent. All14source hashes still match in `.local/billing-query-count-resume-state.json`. This is not a successful gate and no aggregate is inferred from mixed old/new XML reports.

A fresh sequential full verify is RUNNING in `.local/billing-query-count-full-verify-resumed.log`,same SSRbrowser/pilot/calendar-native opt-ins. Restart is justified by authoritative process absence,not a timeout. The old log is retained. No overlapping Maven, predicted count, early full PASS or unchanged frontend rerun. Previous1013full results remain historical for newly modified Need/Detail code. Two subsequent finance-only test methods/properties were added after this new full compiled; they are not retroactively covered by it and require a separate focused run.

Next action: Finish the current full gate, then continue original idempotency/legacy/finance/refund/recovery/UI/privacy/Ads and local operational/security/pilot acceptance.
Required from you: Nothing
Completion condition: Current full output is terminal and the remaining original local requirements have their own adequate evidence; real-money actions remain explicitly user-controlled.
