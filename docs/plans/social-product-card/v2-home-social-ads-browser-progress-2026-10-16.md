# Social V2 — populated HOME_HERO / IN_FEED browser regression

Date:2026-10-16. Scoped local11.3.8 evidence, not full discovery/commerce/staging acceptance. Current project UI retained; normal V2/video flags OFF.

## Scope and fixture

Added one optional `covie.test.social-feed-ads=true` branch to existing `SocialFeedBrowserIntegrationTest`, run with guest flag, and `apps/web/e2e/home-social-ads-browser.mjs`. Actual Next/API/disposable PostgreSQL/MinIO. No user/runtime/recovered data or normal services used.

Two seeded APPROVED CSS/HERO campaigns, HOME_HERO and IN_FEED, target actual `/search?type=PRODUCT&q=Social&sort=NEWEST&view=social`; valid existing merchant, owner, deviceALL, bounded dates. This is a public delivery/navigation regression, not Admin/Seller campaign-creation or uploaded-ad-media proof. Native Ads delivery and telemetry endpoints are used, no body mocks or synthetic impression callbacks. Current `CatalogSearchDocumentService.rebuild` indexes only fixture items before snapshots, as production Create already does.

The first run navigated to the correct Search URL but could not find the seeded result because raw JDBC item setup had omitted search_vector. Source confirms keyword query uses that vector and production Create rebuilds it. Fixture-only correction calls the existing rebuild service; query parameters/assertions were not weakened or removed. `.local/social-v2-home-ads-browser.log` is a harness failure, not product RED or permission to change search logic.

## Accepted actual chain

Chrome155.0.8059.84/1280, guest no AT/RT/Authorization or issued actor session; coordinate preference seeded, not GPS proof:

1. Home initial Catalog serves both real campaigns through API200/no-store, with opaque delivery proof and correct placement/target. Native DOM has both labeled/disclosed “Quảng cáo” links.
2. Native Social switch loads the actual gallery; switching back Catalog removes gallery. Both Ads keep their exact target/disclosure in each mode. Other previously verified gallery/image behaviors are not repeated as a new acceptance claim.
3. For each placement, scroll actual link into view and wait for native continuous-visibility impression POST204. Click native link: exact Search URL retains typePRODUCT, qSocial, sortNEWEST, viewSocial; actual route heading and indexed product visible. Native click telemetry POST204 is observed once per campaign. Native Back returns Home with both placements retained.
4. No non-Ads API writes. Final context-close boundary pageErrors=[]; no auth cookies/header. Raw delivery tokens are validated in memory but not retained in evidence.

Final evidence: **6 actual delivery records,2 IMPRESSION events,2 CLICK events**. Java independently matches delivery count to actual served responses, all four event UUIDs/types/campaigns to persisted records and valid campaign-version0 delivery joins, exactly one click and at least one impression per placement. Campaign full JSONB unchanged; all11 existing Catalog/social/moderation/audit/outbox snapshot tables unchanged; guest actor sessions0/reactions0. Intended ad_deliveries/ad_events writes are accounted for, not misclassified as forbidden business mutations.

## Verification and boundaries

Final **1PASS54.23s**, Maven**01:05min**,0skip: `.local/social-v2-home-ads-browser-verified.log`, same-basename evidence directory. Targeted helper ESLint/node syntax/focused whitespace checks PASS. Seven relevant app hashes matched validation copies: HomeScreen, AdSlot, ad-api, AdCreative, AdService, SearchDocumentService and discovery repository; Java fixture matched. Contextual IN_FEED1280 screenshot inspected, not full visual/four-width/a11y acceptance or HOME_HERO screenshot proof (its native assertions/actual click remain evidence).

Fixture manifest deleted, owned API64450/Next64453 stopped, normal3000/8081 absent/untouched. No production BE/FE/API/schema/client/CSS/migration changes, full suites, old successful Ads-management/guest/buyer/lifecycle/race flows, typecheck/build rerun, recovered/runtime DB access, normal restart, remote/commit/branch/push, activation or deploy. Whole objective ACTIVE.

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-ads=true -Dcovie.test.social-feed-guest=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/home-social-ads-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-home-ads-browser-verified test
```

This closes the scoped local populated placements/mode/deep-link/telemetry gap. Native Search filter/sort-control changes are not proven merely by a retained fixed sort URL. Full11.3.9 cart/checkout/location/order/verified-review and actual candidate/staging/11.4/load/CDN/release evidence remain separate; do not substitute this chain or its snapshots for them. Do not rerun unchanged successful Ads chain.

Next action: Trace current native Search filter/sort controls and fixture data needed for the remaining11.3.8 interactions.
Required from you: Nothing
Completion condition: Identify one actual filter/sort/deep-link verification with observable result changes, preserving achieved Ads evidence and without broad suite reruns.
