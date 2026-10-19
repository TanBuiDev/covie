# Social V2 — actual Guest Home/Search theo11.3.1

Ngày: 2026-10-16. G3/G6 local E2E verification, không staging/full phase acceptance. Base803b25b, workingbb2e06b41282c8e9205c35a13e7b6adec68ceaa2/main plus dirty changes; no new PR. Lượt trước là progress: current production build/runtime và image guard defects được kiểm chứng. Lượt này chỉ bổ sung đúng Guest Home/Search read/toggle/no-mutation path đã thiếu trong requirement audit, không lặp old buyer gallery/gesture/HIDE scenario.

## Coverage/source trước test

- Home dùng public discovery cho3sections PRODUCT STANDARD/SERVICE STANDARD/PRODUCT RESALE và shared SocialFeedProvider. Browse location lấy existing localStorage preference, không cần account. Search dùng cùng public discovery/card/provider nhưng filters/title khác.
- Existing real local feed/browser proof dùng issued Buyer cookie trên Search; Home component tests mock transport. Existing Phase4 staging spec có fresh-context Search nhưng chưa chạy staging và không phải current Home proof. Không gọi những bằng chứng đó là Guest Home/Search PASS.
- Thêm optional `covie.test.social-feed-guest` vào existing `SocialFeedBrowserIntegrationTest`: skip session issuance entirely; manifest token rỗng. Old scenarios giữ nguyên token/assertion paths. Guest snapshots thêm reaction/comment/moderation/audit/outbox tables vào existing5catalog/media snapshots (11tables total), và assert actor has0auth sessions.
- New helper `apps/web/e2e/social-guest-home-search-browser.mjs` không add/clear auth cookies, không inject Authorization, không mock API response body/status. Location preference seeded10.8/106.7 bằng existing storage shape để các item có context công khai; không gọi đây là native GPS/location-picker proof.

## Chuỗi browser thực tế

Actual Next/API/disposable PostgreSQL/MinIO, Chrome155.0.8059.84,1280×900:

1. Fresh context/no AT/RT cookie hoặc session. Mọi browser request qua same-origin bridge được assert không Authorization/AT/RT. MinIO fixture cho public GET và context được grant local-network-access như fixture trước; không disable web security.
2. `/?view=catalog`: actual Home route/landmark, actual product/cover,2empty service/resale states. Public discovery3queries200. Catalog phát sinh0new preview query.
3. Native Social toggle: actual batch200/no-store map keyed IDs, exact literal caption và six ordered READY media IDs, no raw storageKey property. First image actual16px decode từ storage. Social→Catalog→Social giữ discovery/filter state, không extra discovery query; old thread chưa mở nên0comment GET.
4. `/search?type=PRODUCT&sort=NEWEST&view=catalog`: actual route/filter title DOM + visible desktop view controls, product/cover; one additional discovery200. Lặp only guest mode transitions, actual preview/image and no auth; không rerun gallery navigation/gesture tests đã đạt.
5. Both routes vẫn không auth cookies; public summary fields liked/saved false. Tổng4discovery,4Social preview batches và4summary reads; browser API mutations0, comment GET0, pageErrors0. Evidence field `catalogPreviews` là cumulative baseline tại route start (0Home/2Search), **không** phải số Catalog requests; live assertions kiểm tra count không tăng trong initial Catalog stage của mỗi route.
6. Java independently asserts11full-table JSONB snapshots unchanged, reactions0 and0auth sessions for fixture actor. Đây là scoped catalog/social/moderation readonly proof, không tuyên bố mọi bảng hệ thống đã snapshot.

## Lệnh và kết quả

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-guest=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-guest-home-search-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-guest-home-search-browser-verified test
```

Final **1PASS43.26s/Maven45.914s,0skip**, `.local/social-v2-guest-home-search-browser-verified.log`, redacted evidence in same-basename directory. Two earlier runs stopped on test-only Search landmarks: first guessed wrong title; second expected visible h1 while current desktop CSS intentionally hides the mobile appBar. Traced `hasIntent` and appBar breakpoint, corrected helper to exact title DOM + actual visible desktop control. No product source changed between runs; do not count locator errors as product RED or as permission to redesign working desktop UI.

Final targeted helper ESLint **exit0** `.local/social-v2-guest-home-search-verified-lint.log`, Java compiled in focused run, whitespace check PASS. No production bug found/fix/refactor justified. No full BE/web/component/typecheck/build rerun because application source unchanged. New e2e helper is not a production module; prior production build remains scoped to its recorded snapshot, not a new release identity for added test files.

Home/Search/provider/card + Java fixture hashes match validation copies. Final manifest deleted, owned Next62271/API62268 stopped; normal3000/8081 absent/untouched. Migration/apply/API/OpenAPI/client/BE/FE/CSS changes=none. No recovered/runtime DB, normal service restart, remote Git/commit/branch/merge, flag activation or deployment. Whole V2 ACTIVE; normal V2/video flags OFF.

## Limits và bước kế tiếp

Local functional11.3.1Guest Home/Search proof có, không thay thế staging12.4/E2E, four-width visual/a11y/mobile browser acceptance, guest Like/Save/Comment denial interaction, actual GPS, cookies/account-switch, cross-browser, Ads creatives or object/CDN privacy. Không snapshot màn hình hoặc tuyên bố visual acceptance từ headless functional assertions. Fixture public storage không chứng minh private-origin behavior.

Next action: Trace existing Buyer Like/Save/Comment→refresh→activity UI/fixture contracts for11.3.2, then implement one focused real chain only where current browser evidence is missing.
Required from you: Nothing
Completion condition: Establish the actual mutation/refresh/activity contracts and expected DB effects without inventing routes, roles or counting separate seeded scenarios as a completed chain.
