# Social V2 — discovery/Ads and commerce regression audit

Date:2026-10-16. Read-only source/evidence audit for literal11.3.8–9. Not a new runtime regression PASS. No application or test execution in this checkpoint.

## Findings that determine the next verification

| Requirement | Current authoritative source/evidence | Conclusion |
| --- | --- | --- |
| Home placement preservation | `HomeScreen` retains HOME_HERO near page start, IN_FEED under hydrated+location. Both are outside ProductSection Social/Catalog content selection. Current diff relocates/styles HOME_HERO, does not remove either slot | Wiring present; actual filled placements with V2 mode changes not yet proved by previous no-ad fixtures |
| Search filters/sort/deep links | Current `tests/search.test.tsx` covers generated filters, price/fulfillment/range/reset/category/URL changes and sort; `home.test.tsx` covers three section query contracts and route links. Earlier local no-auth Home/Search browser proves toggles without repeated discovery, but its fixed initial query is not every filter/sort/deep-link interaction | Preserve existing tests; no reason to rerun all or call exact release11.3.8 PASS from fixed query/component mocks |
| Ads runtime and tracking | `AdService.active` inserts expiring `ad_deliveries` for actual served campaigns and removes bounded expired deliveries; `event` records validated public telemetry. `AdSlot` tracks a continuous visible second and clicks, client uses sendBeacon/fetch to public `/ads/{id}/events` | Ads-filled browser must allow/account for these intended writes, not apply previous empty-ad fixture's zero-write invariant to all production traffic |
| Existing Ads tests | API `AdServiceIntegrationTest` covers delivery proof/dedup/stale delivery/merchant visibility; FE `ad-slot.test.tsx` and review tests cover disclosure, targets, visibility/expiry/failure; layout tests mock gutter geometry | Existing coverage, not actual Home HOME_HERO+IN_FEED served-and-clicked V2 browser proof. Do not rerun unrelated Ads-management suites |
| Commerce authority | Current diff against HEAD has no paths in API ads/cart/checkout/orders/reviews or FE cart/checkout/orders/reviews feature directories. V2 changes in Catalog create/edit/DTO/access and Home/Search still require their actual compatibility evidence | Unchanged code boundary is not a completed release regression or justification for broad refactor |
| Existing local commerce browser | `populated-buyer-flow.local.spec.ts` targets `.local/dev/ui-fixture.json`, already exists; flows reach real checkout/order. `buyer-flow.local.spec.ts` also changes `review.enabled` through Admin settings | Do not run these against recovered/runtime data or restart normal services. File existence alone is not a safe disposable environment or fresh test evidence; file contents were not opened |
| Existing release commerce browser | `staging-p0.spec.ts` covers normal-auth roles, product cart/checkout→Seller fulfillment→Buyer completion/review, service flow and needs; staging fixture requires HTTPS target and six credential fields, rejects production host | Named suite exists, but user confirmed no staging. Not executed/skipped as fake PASS. No staging credentials requested again |
| Phase4 runner scope | `run-social-phase4-staging.sh` invokes `social-phase4.staging.spec.ts`, not `staging-p0.spec.ts`. The Social spec's configured fixture/credentials and widths are a distinct acceptance scope | Social acceptance is not evidence of cart/checkout/orders/reviews release regression. Both scopes remain explicit; no infrastructure rewrite inferred |

Public Ads telemetry is intentionally unauthenticated but validated with expiring delivery proof. This audit found no evidence of a security defect requiring removal, CSRF policy expansion, changed event semantics or API refactor. Existing empty-ad guest tests' zero API writes and protected business snapshots remain valid within their fixture scope; they never claimed populated Ads delivery tables unchanged.

## Next executable scope

One disposable real Home Ads fixture with HOME_HERO and IN_FEED can close a concrete11.3.8 local gap without touching commerce/recovered data. Use current actual AdService payload/placement/target contracts; capture served placement IDs, disclosures, targets and intended delivery/telemetry effects separately from unchanged Catalog/order business rows. Verify mode changes retain Ads and real target/deep-link behavior. Do not count existing mock Ads tests or a page with no served campaigns as that proof. Preserve unrelated search/commerce source and all already verified browser chains.

Full literal11.3.9 still requires candidate-specific cart/checkout/location/orders/verified-purchase review evidence in a safe disposable or actual staging environment. Do not silently substitute catalog row preservation, an old checkout log, an unchanged source diff, a skip, or Social-only runner for the named flow. Existing suite is the starting point; no mass speculative new test matrix is warranted.

No BE/FE/API/schema/client/test source changes, tests/full suites, runtime/recovered DB access, manifest-secret access, normal restart, remote operations, activation or deploy. Only tracking documents change. Whole objective ACTIVE; normal V2/video flags OFF. Staging/load/private object/CDN/recovery/immutable-release evidence and G5 entry conditions remain unproved.

Next action: Prepare one disposable HOME_HERO+IN_FEED real-browser verification using current Ads delivery/telemetry contracts.
Required from you: Nothing
Completion condition: Verify served placements and mode/target behavior with intended Ads effects separated from protected commerce rows; do not claim full11.3.8–9/staging acceptance from this scope.
