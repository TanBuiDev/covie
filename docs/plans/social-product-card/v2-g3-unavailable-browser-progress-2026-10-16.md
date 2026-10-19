# Social V2 — actual browser unavailable-item reconciliation

## Scope

Plan G3.4: item bị HIDE trong khi feed đang mở phải được xử lý đúng. Sau component RED/fix ở `v2-g3-unavailable-revalidation-progress-2026-10-16.md`, lần này kiểm chứng chuỗi Next/API/issued JWT/PostgreSQL/MinIO thật. Không thay đổi production code hoặc chạy lại gallery/feed happy path.

`SocialFeedBrowserIntegrationTest` có nhánh opt-in `covie.test.social-feed-hide=true`. Nhánh cũ vẫn giữ assertion readonly toàn bộ business tables và không tạo Admin fixture. Nhánh mới cấp Admin test JWT, cho helper thực hiện report/HIDE và kiểm tra chính xác thay đổi được phép. Helper mới: `apps/web/e2e/social-unavailable-browser.mjs`.

## Chuỗi thực sự chạy

1. Source-only Next copy (không env/runtime data) và API random-port, PostgreSQL/MinIO disposable. Browser dùng origin localhost:3000 với HTTP/HMR forwarding tới Next riêng, không đụng service đang chạy.
2. Cookie HttpOnly chứa JWT buyer do InitialSessionIssuer cấp. Actual `/search?...&view=social` SSR/hydrate, render caption literal và gallery từ preview thật. Không mock API/media response; seed coordinate preference không phải test GPS/login form.
3. Node helper gọi actual report API bằng buyer bearer: **201**, case USER_REPORT mới. Gọi actual Admin HIDE bằng Admin bearer: **200**, same-key replay **200**. Đây là actual Admin command HTTP, **không phải Admin UI/CSRF acceptance**.
4. Card đang mở chưa reload. Browser click Like bằng control thật; normal browser API/CSRF bridge gửi PUT và nhận **404 RESOURCE_NOT_FOUND**. Không có reaction row được ghi.
5. Revalidation phát sinh một actual fresh batch200/no-store; response không có item HIDDEN. Proxy chỉ giữ delivery của response thật, không sửa status/body.
6. Trong khi response này bị giữ: gallery, caption và tên item cũ không còn trên DOM. Sau release, card HIDDEN vẫn không có; item công khai khác tiếp tục hiển thị.

Evidence JSON ghi hai initial preview responses và một fresh response sau HIDE, một reaction404, no uncaught page errors, `oldContentAbsentWhileFreshResponseHeld=true`. Chrome155.0.8059.40. Screenshot1280px kiểm tra đúng `/search` và landmark trước capture, đã xem: item còn lại hiển thị; ảnh chụp đang ở vị trí cuộn contextual, không phải full-page/four-viewport visual acceptance.

## Kiểm tra persistence độc lập trong Java

- Item target: HIDDEN, version1; mọi cột khác ngoài moderation_status/updated_at/version không đổi, bao gồm caption và commerce fields.
- Tất cả item khác và toàn bộ variants/location_variants/catalog_item_media/media_assets JSONB snapshots không đổi.
- Đúng một USER_REPORT case cho target, RESOLVED.
- Đúng một HIDE action, một MODERATION_HIDE audit, một MODERATION_ACTION_APPLIED/HIDE outbox, một MODERATION_CASE_OPENED outbox; replay không nhân đôi.
- catalog_social_reactions vẫn bằng0.

## Validation

Sequential isolated API command:

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-hide=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/social-unavailable-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-g3-unavailable-browser test
```

- `.local/social-v2-g3-unavailable-browser.log`: **1 PASS**, test44.20s, Maven54.850s. No failures/errors/skips.
- `.local/social-v2-g3-unavailable-browser/evidence.json`, `browser.log`, `next.log`, `after-hide.png` chứa actual artifacts. Không publish credential manifest.
- Helper targeted ESLint exits0: `.local/social-v2-g3-unavailable-browser-lint.log`; diff check PASS.
- Source/private browser copy hashes của changed provider/card trùng nhau. Không rerun unchanged component/typecheck/client/BE suites để cộng PASS.
- Fixture manifest tuyệt đối `E:/WebApp/CoVie/.local/social-v2-g3-unavailable-browser/fixture.json` không còn sau finally. Helper redact cả buyer/Admin JWT; owned children được đóng trong finally. Normal web/API PIDs7544/25008 vẫn hoạt động.

## Không được suy rộng

Đây là one-provider actual Like404 chain. Duplicate-provider handling có component proof riêng; không gọi nó là duplicate-provider real browser proof. Save/comment error, Home, native cross-tab/account change, eventless HIDE, in-flight old media/object URLs, physical devices/cross-browser, load và staging chưa được chứng minh bởi lần chạy này.

Disposable MinIO bucket cho anonymous GetObject để kiểm tra browser delivery, giống fixture trước. HIDE không xóa media objects/records: không phải proof private origin/CDN authorization hoặc thu hồi object URL đã biết. Không thay policy production để ép gate pass.

Không production source/schema/API/client/CSS change, recovered/runtime DB access, normal restart, activation, commit/branch/remote operation hay deploy. Whole V2 ACTIVE; normal V2/video flags OFF; staging/release UNVERIFIED.

Next: trace thiếu sót G2 Seller comment entry và moderation reason qua existing authorized contracts trước khi thêm UI/API; không expose Admin-only notes hoặc dẫn DRAFT tới public404.
