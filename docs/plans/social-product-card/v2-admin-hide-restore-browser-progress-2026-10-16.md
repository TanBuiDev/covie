# Social V2 — native Admin HIDE/RESTORE and public visibility

Date:2026-10-16. Requirement: plan11.3.6. Local evidence only; not staging acceptance. Figma waiver/current project UI retained.

## Scoped implementation and contracts

Existing Admin detail/actions already expose HIDE for OPEN SYSTEM_FLAG, then RESTORE for the resolved HIDE case, then no further content action after RESTORE. Existing API owns authorization and transition validation. No production correction was needed.

Added one optional `covie.test.social-feed-admin-journey=true` branch to `SocialFeedBrowserIntegrationTest`, run together with existing guest branch. Only this branch enables API/Next Admin reads and case opening; normal flags unchanged. New `apps/web/e2e/admin-hide-restore-browser.mjs` bridges actual Next/API responses without response-body mocks and uses actual issued HttpOnly Admin cookie. Public context has no AT/RT/Authorization; fixture buyer is not issued an auth session. Disposable PostgreSQL/MinIO uses existing feed setup. Seeded coordinates are local preference, not GPS proof.

## Actual browser chain

Chrome155.0.8059.84/1280, one actual Admin and one independent guest context:

1. Guest Search Social shows target caption and real16px image from MinIO; unrelated second item visible. Explicit same-browser public read probes: previews200/no-store includes target, comments200.
2. Admin searches target UUID via native form, sees exactly one list row, opens native detail, supplies reason/confirmation and opens SYSTEM_FLAG. Actual opening POST200/createdtrue, then route is exact returned case URL.
3. Native “Ẩn nội dung” modal supplies SPAM/private note and confirms. Actual POST200. Fresh case UI exposes RESTORE. Guest native reload excludes target but retains unrelated item; explicit previews200/no-store omits target, comments404.
4. Native “Khôi phục nội dung” modal supplies reason/note and confirms. Actual POST200. Case shows no remaining action. Guest native reload again shows literal caption and decoded real image; explicit previews200/no-store includes target, comments200.
5. Native Admin returns to item detail, sees same-case link and actual APPROVED state. Exactly three API writes total, all Admin POST200 with normal CSRF and Idempotency-Key. Guest writes0; no Node bearer commands are substituted for Admin UI commands. pageErrors=[] after context close.

Public visibility synchronization here is on native reload plus actual direct browser read probes; it is not a claim of automatic realtime invalidation of an idle tab or CDN/object denial. MinIO fixture has a public read policy; hiding does not prove object URL revocation. No such broader claim is made.

## Persistence and verification

Java independently passed:
- Final target APPROVED/version2; full target JSONB unchanged except moderation status/time/version. Other items full JSONB unchanged.
- Full JSONB unchanged for variants, offers, gallery associations, media assets, reactions, comments.
- Exactly one SYSTEM_FLAG case for target, RESOLVED; exact same UUID as native opening response.
- Exactly one HIDE and one RESTORE action by the actual Admin; each has exactly one MODERATION_* audit and MODERATION_ACTION_APPLIED outbox. Opening audit/outbox exactly one each. Total actions2/audits3/outbox3, no duplicates or other case/effects.
- Guest fixture actor sessions0/reactions0; evidence pageErrors is an array of size0.

First run **1PASS82.89s**, Maven**01:34min**,0skip: `.local/social-v2-admin-hide-restore-browser.log`; evidence/snapshots in same-basename directory. No product RED or fix. Contextual1280 restored-case screenshot inspected; not four-width/a11y acceptance. Targeted helper ESLint, node syntax and focused diff checks PASS.

Six relevant app module hashes matched original/validation copies: Admin catalog screen, moderation detail/actions, case opening service, command service and catalog moderation handler; Java fixture also matched. Manifest deleted, owned API57411/Next57416 stopped, normal3000/8081 absent/untouched. No application BE/FE/API/schema/client/CSS/migration change, full suite, old successful guest/buyer/gallery/race tests, typecheck/build rerun, recovered/runtime DB access, normal restart, remote/commit/branch/push, activation or deployment. Normal V2/video flags remain OFF. Whole objective ACTIVE.

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-admin-journey=true -Dcovie.test.social-feed-guest=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/admin-hide-restore-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-admin-hide-restore-browser test
```

Local11.3.6 chain is now verified within these boundaries. Not staging, account role removal, all case types/orders, private storage/CDN, mobile/a11y, load or release evidence. Do not rerun unchanged success or infer full G1/G3/G6 acceptance.

Next action: Trace current public eligibility guards and existing evidence for11.3.7 hidden merchant/suspended seller/deleted item.
Required from you: Nothing
Completion condition: Identify actual remaining coverage without repeating item-HIDE proof or inventing a new Seller editing policy.
