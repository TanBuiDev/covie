# Social V2 G1 — actual opening409 and authoritative refresh

Ngày: 2026-10-16. Lượt trước là progress (access-denial defect RED/fix/component/browser verification). Lượt này thực hiện đúng missing browser conflict gate của G1.2/G1.3, không chạy lại happy-path Admin opening/list/filter hoặc full suites.

## Contract và phạm vi

Đã đối chiếu current `AdminCatalogCaseOpeningService.open()`: existing OPEN case phải được tái dùng200, không giả lập thành409. Nếu không còn OPEN case và item không ở NOT_REQUIRED/APPROVED thì trả existing409 `MODERATION_TARGET_STATE_CONFLICT`. Existing BE test có actual open→HIDE→new opening409; component test có conflict→authoritative refresh. Missing evidence là chuỗi browser thật, không phải một BE bug đã xác nhận.

Chỉ thêm opt-in `covie.test.admin-catalog-opening-conflict` vào existing `AdminCatalogBrowserIntegrationTest` và new `apps/web/e2e/admin-catalog-opening-conflict-browser.mjs`. Nhánh mặc định và nhánh access-denial giữ assertions riêng. Không sửa production BE/FE/API/client/schema/CSS, không thêm workflow/transition.

## Chuỗi thực tế

1. Actual Next/API/disposable PostgreSQL, issued HttpOnly Admin cookie. Browser mở target đang APPROVED/chưa có case, nhập SPAM và confirm.
2. Node fixture command bằng **cùng Admin bearer**, context riêng nhưng không phải chứng cứ hai identity/operator khác nhau: actual open200/created=true rồi actual HIDE200 trên case trả về. Không mock body/status hoặc ghi trực tiếp SQL thay cho command. Browser vẫn giữ đúng stale APPROVED/no-case trước submit.
3. Native click submit qua normal CSRF client. Exactly1browser POST, actual409, existing error code; Idempotency-Key + X-CSRF-Token đều có.
4. Existing UI conflict handler fetch lại actual list/detail200, nhận HIDDEN + RESOLVED case; row hiển thị Đã ẩn, case link đúng ID duy nhất/Đã xử lý. Notice xác nhận đã yêu cầu tải lại. Reason reset, URL vẫn `/admin/listings?item=<actual ID>`, không giả vờ mở case thành công hoặc tự navigate tới case mới.
5. All observed Admin responses no-store và không raw fixture storage key. pageErrors0. Không kiểm tra lại public HIDE/gallery chain đã đạt ở các báo cáo độc lập.

## Kiểm chứng độc lập ở DB

Java assertion sau browser:

- Target HIDDEN/version1. Full catalog JSONB giữ nguyên ngoại trừ đúng target moderation_status/updated_at/version; tất cả field của other items được giữ nguyên, không loại những field này khỏi other-item comparison.
- Full catalog_item_media + media_assets JSONB không đổi; không có caption/gallery edit.
- Exactly1target SYSTEM_FLAG/RESOLVED/SPAM, note đúng fixture setup. Exactly1moderation action/HIDE.
- Exactly1opening audit +1HIDE audit và1opening outbox +1action-applied outbox cho case, totals2each. Failed browser opening không tạo case/action/audit/outbox trùng. Expected HIDE/setup writes được ghi nhận, không gọi scenario này readonly.

## Lệnh và kết quả

```text
mvnw.cmd -o -Dtest=AdminCatalogBrowserIntegrationTest -Dcovie.test.admin-catalog-browser=true -Dcovie.test.admin-catalog-opening-conflict=true -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/admin-catalog-opening-conflict-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-g1-opening-conflict-browser test
```

**1PASS36.07s/Maven45.231s**, `.local/social-v2-g1-opening-conflict-browser.log`, evidence directory cùng basename; Chrome155.0.8059.84. First run PASS; no production fix justified. Helper targeted ESLint exit0 `.local/social-v2-g1-opening-conflict-lint.log`; Java compiled in focused Maven run; diff whitespace check PASS. Không rerun component/typecheck vì production/TS source không đổi từ lần đạt gần nhất.

Screen/Java hashes match isolated copies; token manifest deleted. Owned Next61214/API61211 không còn listener sau terminal test; normal3000/8081 absent và untouched. Không recovered/runtime DB access, normal restart, remote Git/commit/branch, registry push, activation hoặc deploy. Historical production image vẫn pre-access-denial fix, không tự nâng thành current-tree build proof.

## Giới hạn và bước kế tiếp

Đây là một reachable409 ordering qua actual browser, không phải toàn bộ concurrency, role-removal403, case-history continuation,412 (không thấy opening contract phát sinh412), browser/a11y/staging/load acceptance. Những giới hạn mở rộng không tự trở thành yêu cầu nghiệp vụ mới; gate bắt buộc phải đối chiếu nguyên văn kế hoạch. Whole V2 ACTIVE; normal V2/video flags OFF; staging chưa có, UNVERIFIED.

Next action: Đối chiếu lại các yêu cầu/gate bắt buộc G0–G6 với source và evidence hiện có, tách việc còn triển khai khỏi việc cần hạ tầng, không tạo thêm gate giả định.
Required from you: Nothing
Completion condition: Có danh sách remaining requirements có căn cứ trong kế hoạch cùng bằng chứng hoặc blocker thực tế cho từng mục.
