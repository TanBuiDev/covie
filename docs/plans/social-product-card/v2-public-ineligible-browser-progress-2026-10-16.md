# Social V2 — public eligibility browser evidence

Date:2026-10-16. Requirement:11.3.7. Local only, whole objective ACTIVE, normal V2/video flags OFF.

## Source audit and scope

Current `CatalogSocialPreviewService` requires item not deleted/ACTIVE/approved-or-not-required, merchant not deleted/ACTIVE/approved-or-not-required. `CatalogSocialService` applies corresponding predicates to summaries and `requireVisible` before comment reads. Existing `CatalogSocialPreviewIntegrationTest` already covers merchant HIDDEN/SUSPENDED and deleted items; `CatalogSocialControllerIntegrationTest.blocksPublicSocialActivityForHiddenOrSuspendedMerchants` covers public summary/comments/mutations. Those unchanged HTTP tests were inspected, not rerun. No evidence justified changing these working guards.

The remaining literal11.3.7 browser boundary was verified using a single optional `covie.test.social-feed-ineligible=true` branch of existing `SocialFeedBrowserIntegrationTest`, together with its guest flag, plus `apps/web/e2e/social-ineligible-browser.mjs`. Existing PostgreSQL/MinIO fixture retained. Added three separate fixture merchant/location/item/variant/offer aggregates:

- MERCHANT_HIDDEN: ACTIVE merchant with HIDDEN moderation, ACTIVE/APPROVED item.
- MERCHANT_SUSPENDED: SUSPENDED merchant with APPROVED moderation, ACTIVE/APPROVED item.
- ITEM_DELETED: ACTIVE/APPROVED merchant, item listing DELETED and deleted_at populated, matching current soft-delete representation.

Each excluded item has a distinct stored caption and an ACTIVE stored comment by the fixture actor. These are not empty-comment404 substitutes. No live merchant/user policy was changed. “Suspended” here means existing merchant_status=SUSPENDED, as the plan's public predicate specifies; it is not a newly invented ban based on an owner's unrelated user/session state.

## Actual browser results

Chrome155.0.8059.84 at1280, actual Next/API/disposable PostgreSQL/MinIO, guest no AT/RT/Authorization and no issued actor auth session. Coordinates are seeded local preference, not GPS evidence. Requests bridge actual responses without body mocks.

1. Actual Search Social renders only the two eligible fixture cards; all three named ineligible cards absent.
2. Explicit browser fetch of one batch containing the eligible control and all three known excluded UUIDs: previews200/no-store and summaries200/no-store both return only eligible UUID, no private fixture caption/comment text.
3. Explicit browser comment GET for each excluded UUID:404 RESOURCE_NOT_FOUND, no private comment text. Eligible control comment GET200. This proves denial rather than a broken/unavailable endpoint.
4. Browser API writes0, pageErrors=[] after context close. Java independently validates evidence array/three blocked entries and three stored comments, guest actor sessions0/reactions0. All14 business tables full-JSONB unchanged before/after browser reads: items, variants, offers, gallery associations, assets, reactions/comments, cases/actions/audit/outbox, merchants/locations/categories.

This is initial eligibility/read denial, not a hide/suspend/delete-command flow, idle-tab invalidation, account switch, private object/CDN access, mutation denial, or staging proof. Previous native Admin item-HIDE/RESTORE chain is separate and was not rerun.

## Verification

First **1PASS45.14s**, Maven**57.052s**,0skip: `.local/social-v2-ineligible-browser.log`; evidence/screenshot directory of same basename. Targeted helper ESLint exit0, node syntax and focused whitespace checks PASS. No product RED/fix. Screenshot captured, not full visual/a11y/four-width acceptance.

Both relevant production services and Java fixture match validation-copy SHA256. Manifest deleted; owned API55475/Next55478 closed; normal3000/8081 absent/untouched. No production BE/FE/API/schema/client/CSS/migration change, full suite, old successful HTTP/browser/race flows, typecheck/build rerun, recovered/runtime DB access, normal restart, remote/commit/branch/push, activation or deployment.

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-ineligible=true -Dcovie.test.social-feed-guest=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-ineligible-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-ineligible-browser test
```

Local11.3.7 scoped browser/read exclusion evidence now achieved. Staging/11.4/12.4 remain separate and unproved. Keep working guards and do not repeat this unchanged success.

Next action: Trace current affected Home/Search/Ads and existing commerce regression evidence against literal11.3.8–9.
Required from you: Nothing
Completion condition: Identify the actual remaining regression scope and executable fixtures without rerunning unchanged successful suites or inventing extra business requirements.
