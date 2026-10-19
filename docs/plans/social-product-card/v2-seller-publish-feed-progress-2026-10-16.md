# Social V2 — Seller ba loại tin create/publish → public feed

Ngày: 2026-10-16. G2/G3/G6 local E2E theo11.3.4, không nghiệm thu toàn phase/staging. Base803b25b; workingbb2e06b41282c8e9205c35a13e7b6adec68ceaa2/main plus dirty changes, no new PR. Continuation trước có progress ở fixture/real business flow/DB assertions nhưng xuất hiện late harness error; không coi kết quả Maven đơn lẻ đó là all-browser PASS.

## Contract và fixture trước implementation

- PRODUCT=PRODUCT/STANDARD, SERVICE=SERVICE/STANDARD, RESALE=PRODUCT/RESALE; không thêm enum item type mới. Existing Create form có type/listing-kind controls, compatible category và service-enabled location filter.
- Existing `CatalogPublishValidator` cần active merchant/category/variant/offer/location và READY media, thêm service-enabled location cho SERVICE. `SystemSettingRegistry` default moderation.mode=AUTO_PUBLISH; test kiểm chứng đúng nhánh này, không sửa policy/disable moderation. Publish qua existing command +If-Match, không SQL update trạng thái.
- Extend existing `CatalogSocialBrowserIntegrationTest` bằng opt-in `covie.test.social-seller-publish-journey`: fixture category BOTH/location service_enabled=true trước snapshot, chỉ isolated API/Next bật authoring/previews. Shared storage registration chỉ thêm preview override khi opt-in=true để không ghi đè flag của các fixture caller khác. Default old one-item/gallery scenario giữ nguyên assertions.
- New `seller-publish-feed-browser.mjs`: actual issued HttpOnly OWNER cookie, UI selections/uploads/create/publish; fresh independent **guest** context đọc public feed. No mutation/response mocks hoặc pre-created items.

## Actual chain và kết quả

Lần lượt PRODUCT/SERVICE/RESALE, actual Next/API/disposable PostgreSQL/MinIO:

1. Native type/kind/category/location controls; enter unique name/commerce description/literal multiline caption/price25000; native file input chọn riêng first/second/third PNG.
2. Each actual presign201 → browser storage PUT200 → complete200 → item Create201/DRAFT. Create uses Idempotency-Key, entire API mutation flow uses normal CSRF. No raw presigned URL/secret response body logged.
3. Actual Seller detail reloads server caption, native Đăng bán → POST204 +ETag, If-Match present; server-refreshed UI shows Tạm dừng. Three creates had createVersion1; final versions2. No draft forced into public via SQL.
4. Close OWNER context, fresh guest/no AT/RT/Authorization; actual Search `q=Seller journey&sort=NEWEST&view=social` returns all3new items. Actual preview batch200/no-store contains exact captions +1media/item, no raw storageKey property. All3cards show literal newline caption without rendered `<b>`, authoritative25.000₫ and actual decoded8px image. Public visibility is not inferred from owner management reads.

Observed12API writes (3×presign/complete/create/publish),3storage PUT200,2guest preview batches (dev lifecycle), pageErrors0 **after context cleanup**, Chrome155.0.8059.84/1280×900. No guest mutation. Fixture location preference is seeded coordinates; not native GPS/input-picker proof.

## Independent database/storage assertions

- Exactly3items for fixture merchant, exact type/kind/name/description/caption, ACTIVE/NOT_REQUIRED and createVersion+1; each exactly1default variant/offer at fixture location, VND25000/available.
- Each item exactly1owned READY/CATALOG_IMAGE media link, sort0/primary=true.3assets total; actual SDK object bytes match each respective PNG file.
- Exactly3Create idempotency records; each item has1CATALOG_ITEM_CREATED +1CATALOG_ITEM_PUBLISHED outbox.3MEDIA_UPLOAD_COMPLETED audit rows. No moderation case for these AUTO_PUBLISH items; reactions/comments0. These are expected create/publish/upload writes, not a readonly test.
- Full merchant_profiles/merchant_memberships/merchant_locations/categories JSONB snapshots unchanged after fixture setup. No unrelated merchant/category/commerce mutation inferred or introduced.

## Harness failures được xử lý, không giả làm product RED

1. Initial script completed the functional route but hit `route.fetch: Request context disposed` during context close; Java DB assertions did not finish.
2. A temporary `unrouteAll(wait)` cleanup let Maven pass DB checks, but final evidence had1late localStorage page error **after** the earlier assertion. That run (`...-final.log`) is not accepted as clean-browser proof. Previous evidence did not capture that page's origin/stack; do not invent an exact causal URL. Isolated opaque data-document probe confirms unbounded fixture init can throw on non-app origins; a separate closed-port probe did not reproduce the precise original error.
3. Final harness keeps the same-origin tunnel until context close; suppresses only confirmed transport-disposal errors once its own context is closing, not active request/assertion errors. Location init runs only on the intended app origin; no blanket storage-error catch. Page errors retain URL/stack, checked **after both contexts close**, and independently required to be an empty array in Java before accepting the result. Functional/business assertions unchanged; no production workaround.

## Commands và evidence cuối cùng

```text
mvnw.cmd -o -Dtest=CatalogSocialBrowserIntegrationTest -Dcovie.test.social-seller-browser=true -Dcovie.test.social-seller-publish-journey=true -Dcovie.test.minio-image=cgr.dev/chainguard/minio@sha256:a05a4497e8dce3cb7a7a1bf1872ba5d30ea988f1e8c22c9e0920503761c4b5f1 -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/seller-publish-feed-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-seller-publish-feed-browser-verified test
```

Final **1PASS85.78s/Maven01:39min,0skip**, `.local/social-v2-seller-publish-feed-browser-verified.log` and same-basename evidence directory, pageErrors=[] at final boundary. Helper targeted ESLint exit0 `.local/social-v2-seller-publish-feed-verified-lint.log`; final whitespace/syntax checks PASS. After run only helper indentation normalized, no functional change or needless repeat of the full flow. Java independently passed all persistence/object/evidence checks. Existing dev Next Image aspect-ratio warnings remain warnings, not full visual acceptance or a reason to change unrelated CSS.

Eight relevant application module hashes matched original/validation copies; Java fixture match. Manifest deleted; owned Next59356/API59353 stopped; normal3000/8081 absent/untouched. Source edits: new helper + isolated fixture branch + docs/AGENTS. Production BE/FE/API/OpenAPI/client/schema/CSS/migrations=none. No full BE/web suites, old gallery/guest/buyer/concurrency flows, typecheck/build rerun because app source unchanged. No recovered/runtime DB, normal service restart, remote Git/commit/branch, activation or deployment. Normal V2/video flags OFF; full objective ACTIVE.

## Gate and next action

Local11.3.4all-three-type AUTO_PUBLISH create/upload/publish→guest feed proof achieved. Not staging12.4, PRE_APPROVAL/HYBRID branches (separate HTTP/race evidence), role-outsider/security matrix, four-width/visual/a11y, native OS file picker/GPS, failed-upload/retry/load/CDN or video proof. Do not repeat this unchanged success or call it complete G2/G3.

Next action: Trace11.3.5's published caption edit/reapproval and Admin visibility UI contracts/current fixtures before one focused real Seller→Admin verification.
Required from you: Nothing
Completion condition: Establish the current PATCH/ETag/moderation/Admin-read path and expected effects, without inventing policy or treating DRAFT edits as published reapproval proof.
