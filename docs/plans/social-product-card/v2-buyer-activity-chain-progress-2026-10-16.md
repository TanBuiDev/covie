# Social V2 — Buyer Like/Save/Comment → reload → Activity

Ngày: 2026-10-16. G3/G4/G6 local E2E theo11.3.2, không full-phase/staging acceptance. Base803b25b; workingbb2e06b41282c8e9205c35a13e7b6adec68ceaa2/main plus dirty changes; no new PR. Lượt trước là progress: actual Guest Home/Search verification. Lượt này thực hiện missing single buyer chain, không ghép seed Activity/read và independent Like proofs thành một E2E giả.

## Contract/source đã đối chiếu

- Existing reaction PUT `/api/v1/me/catalog/items/{itemId}/likes|saves` trả server summary200; comment POST cùng item `/comments` trả201/id/body. Normal browser client gửi CSRF, không thay API/role.
- Existing SocialComments composer plain text/max1000, lazy GET, server-created comment. `CatalogSocialService.addComment()` lưu current actor/ACTIVE body, không có business rule yêu cầu outbox/audit cho thao tác này; giữ nguyên hiện trạng.
- Existing private activity requires actor từ session, backend feature flag + server UI flag, `type=LIKED|SAVED|COMMENTED`, no client userId, private/no-store. COMMENTED dùng actual comment ID/excerpt và visible item.

Thêm `covie.test.social-feed-buyer-journey` opt-in vào existing `SocialFeedBrowserIntegrationTest`: bật Activity chỉ trong isolated API/Next test process, assert trước run rằng **reactions0/comments0**. Existing default/guest/HIDE/overlap behavior giữ nhánh riêng. New `buyer-social-activity-browser.mjs` dùng actual issued HttpOnly Buyer cookie; không login-form/credential proof và không inject response mocks. Item/media/offer được seed như existing fixture, **tương tác không seed**.

## Actual single chain

1. Actual Next/API/disposable PostgreSQL/MinIO, Search Social target. Initial summary liked=false/saved=false/likes0/comments0, native Like/Save controls available.
2. Native Like click → actual PUT200, server-confirmed aria-pressed=true; Save click → actual PUT200/pressed=true. Then lazy-open comment thread, nhập literal `<script>Buyer literal comment</script>\nActivity chain`, native submit → actual201. UI renders exact multiline text, no script element; retain returned comment UUID.
3. Native `page.reload()` trên Search: fresh actual summary reads liked=true/saved=true/likes1/comments1; no write replay. UI vẫn Bỏ thích/Bỏ lưu pressed=true. Không dùng pending local optimistic memory làm bằng chứng persistence.
4. Navigate actual `/me/activity?type=LIKED`, sau đó native Next tabs SAVED/COMMENTED. Each real activity response200/private,no-store/exactly1target item, no userId param. COMMENTED commentId đúng UUID vừa nhận201, excerpt exact multiline literal. UI selected tab/URL/link đúng item; comment text escaped, không script.
5. Exactly3browser mutations: PUTlikes200, PUTsaves200, POSTcomment201, **all CSRF headers present**. No other mutation during reload/tabs; pageErrors0. This is expected interaction writes, không gọi scenario readonly.

## DB effects độc lập sau browser

- Exactly2reaction rows total, đúng target/actor/kindsLIKE+SAVE; exactly1comment total, đúng target/actor/body/ACTIVE, UUID trùng evidence201.
- Full9protected-table JSONB snapshots unchanged: catalog_items, variants, location_variants, catalog_item_media, media_assets, moderation_cases, moderation_actions, audit_logs, outbox_events. Không đổi commerce/catalog/media hoặc tạo moderation side effects ngoài contract.
- Exactly1active session của fixture actor (session được issuer cấp trước browser), không tạo account hoặc session mới trong luồng này.

## Lệnh/kết quả

```text
mvnw.cmd -o -Dtest=SocialFeedBrowserIntegrationTest -Dcovie.test.social-feed-browser=true -Dcovie.test.social-feed-buyer-journey=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/buyer-social-activity-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-buyer-social-activity-browser test
```

**First1PASS42.38s/Maven51.091s,0skip**, `.local/social-v2-buyer-social-activity-browser.log`; redacted evidence directory cùng basename. Chrome155.0.8059.84/1280×900; no product failure/fix/refactor justified. Targeted helper ESLint exit0 `.local/social-v2-buyer-activity-chain-lint.log`; Java compiled, diff whitespace PASS. No repeated Guest/gallery/HIDE/account-login/full BE/web suites/typecheck/build vì application source unchanged.

Card/comments/provider/Activity screen và Java fixture hashes match validation copies; helper chạy trực tiếp current source. Manifest deleted, owned Next60956/API60953 stopped. Normal3000/8081 absent/untouched. Changes only fixture/helper/docs/AGENTS: no production BE/FE/API/OpenAPI/client/schema/migration/CSS change; no recovered/runtime DB, normal restart, remote Git/commit/branch, activation/deployment. Normal V2/video flags remain OFF; test flags are isolated functional-test setup, not activation evidence.

## Gate/limits và next action

Scoped local11.3.2single chain verified. Not real staging12.4, login form, accountB isolation (separate existing real email-flow evidence), unlike/unsave/delete-two-comment behavior, four-width/a11y/error/load/CDN proof. Existing contract/HTTP tests retain those scoped semantics; do not invent a required inline Activity mutation feature. Full objective ACTIVE; G5/infra/release gates retained.

Next action: Trace existing Seller create/publish and type/category contracts/fixtures for11.3.4, preserving the distinction between PRODUCT DRAFT upload proof and all-three-type publish→feed acceptance.
Required from you: Nothing
Completion condition: Establish actual PRODUCT/SERVICE/RESALE browser setup, publish/moderation gates and expected persistence before adding a focused real Seller chain.
