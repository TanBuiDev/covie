# Social V2 G1 — Admin catalog access-denial reconciliation

Ngày: 2026-10-16. Tiếp tục mục tiêu đầy đủ; lượt trước là progress (production image packaging fixes + evidence), không phải toàn bộ V2 đã hoàn tất. Lượt này đối chiếu G1.1–G1.4 với current source/test coverage trước khi sửa; không chạy lại Admin list/filter/open-case happy path đã đạt.

## Khoảng trống được xác nhận

- BE đã kiểm tra guest/non-Admin, bounds, hidden/deleted readonly, report–HIDE–RESTORE, case opening/replay và concurrent openings trong `AdminCatalogReadIntegrationTest`. Không có evidence yêu cầu thay BE đang đúng.
- UI component tests đã có initial403/retry, page503 giữ rows/retry, history503 giữ links/retry và opening409 re-fetch. Source có denial handling cục bộ nhưng không có screen-wide denial boundary.
- `loadMoreCases()` khi401/403 chỉ bỏ detail; bảng Admin đã tải vẫn hiện. Nếu một page200 cũ đang pending thì response đó cũng có thể hiển thị rows sau denial. Các dữ liệu này là bản đã tải trước đó, không phải bằng chứng BE trả thêm dữ liệu cho session bị thu hồi.
- New two-status component tests401/403 **RED2** tại assertion table absent: `.local/social-v2-g1-access-denial-red.log`. Separate pending page200 after history403 **RED1**: `.local/social-v2-g1-access-denial-overlap-red.log`. Đây là current UI state/privacy defect có tái hiện, không phải giả định mọi request403 cần redesign auth.

## Sửa tối thiểu

Trong `apps/web/features/admin/admin-catalog-screen.tsx`, latch denial cho current retry attempt khi một request Admin hiện tại nhận `CovieApiError`401/403. Áp dụng initial list/detail, page/history và existing case-opening denial. Render cùng header/error/retry bằng component/token hiện có thay vì các rows/detail/case links/action form cũ. Successful response đã bay trước denial không xóa latch. Explicit retry tăng attempt, fetch lại list/detail; older denial không ghi đè denial của attempt mới hơn.

Không thêm timer, polling, event account-switch, role inference, API, permission hoặc workflow mới. Không thay503 retention/retry,409 authoritative reload, case commands hoặc giao diện hợp lệ. Đây là ngăn render dữ liệu cũ sau denial, không thể thu hồi dữ liệu đã được browser tải hợp lệ trước đó.

## Kiểm chứng mục tiêu

### Components/static

- `pnpm exec vitest run features/admin/admin-catalog-screen.test.tsx`: **11PASS4.56s**, `.local/social-v2-g1-access-denial-green.log`. Gồm old8tests và new3:401/403 history denial, pending200 không hồi rows và explicit authorized retry đọc lại dữ liệu. Production source không đổi sau run này.
- Initial typecheck phát hiện hai option `exact` thuộc Playwright nhưng không thuộc Testing Library; sửa test-only options, không product fix. Final `pnpm typecheck` **exit0**: `.local/social-v2-g1-access-denial-typecheck.log`.
- Final targeted ESLint screen/test/new browser helper **exit0**: `.local/social-v2-g1-access-denial-final-lint.log`. Diff whitespace check PASS. Không chạy full web/BE suite.

### Actual Next/API/session/browser

Mở rộng fixture `AdminCatalogBrowserIntegrationTest` bằng opt-in `covie.test.admin-catalog-access-denial=true`; old opening assertions giữ nguyên ở nhánh mặc định. Nhánh mới seed21items, target có51CLOSED cases; full seven-table JSONB snapshot trước/sau. New `admin-catalog-access-denial-browser.mjs` không mock API body/status:

1. Actual issued HttpOnly Admin access cookie, actual Next/API/disposable PostgreSQL,20rows + literal caption +50case links/cursor.
2. Node fixture command gọi actual `POST /api/v1/auth/logout` bằng chính issued bearer JWT, nhận204; browser cookie cũ vẫn còn. Đây là test setup thực sự thu hồi session, **không phải** browser logout UI/CSRF proof hoặc role-removal proof.
3. Native click history load-more → actual API401; normal SSR refresh bridge401. UI hiện session-expired notice, không table/caption/description/case links/opening form.
4. Explicit retry → actual list/detail401, không phục hồi dữ liệu. Browser không gửi mutation nào; expected auth write là Node logout.
5. Java xác nhận đúng1session actor revoked với reasonLOGOUT,0active; full `catalog_items`, `catalog_item_media`, `media_assets`, `moderation_cases`, `moderation_actions`, `audit_logs`, `outbox_events` JSONB không đổi. Không tạo case/action/audit/outbox ngầm.

```text
mvnw.cmd -o -Dtest=AdminCatalogBrowserIntegrationTest -Dcovie.test.admin-catalog-browser=true -Dcovie.test.admin-catalog-access-denial=true -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/admin-catalog-access-denial-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-g1-access-denial-browser-final test
```

Final **1PASS36.34s/Maven39.055s**, `.local/social-v2-g1-access-denial-browser-final.log`, redacted evidence in same-basename directory. Chrome155.0.8059.84, pageErrors0. First browser run failed only because broad alert locator also matched Next route announcer; narrow actual notice locator, no product change between browser runs. Do not count that harness failure as product RED.

Screen and Java fixture SHA256 match isolated copies; token manifest deleted; owned Next57125 stopped. Normal3000/8081 absent at observation and untouched. Disposable API stopped at test completion. No recovered/runtime DB access, normal restart, remote Git/commit/branch, API/schema/client/CSS change, activation or deployment.

## Gate status and next work

This closes scoped local history401 denial + component403/pending-response UI handling, **not** actual Admin role removal403, native cross-tab/sessionless change, all concurrency/error orders, complete history continuation/browser409, a11y, staging or load. Previous production build image is a historical earlier snapshot: this screen changed afterwards, so final build for the current web tree remains to be rerun when source stabilizes; do not treat prior577file match as current-image equivalence. Whole V2 ACTIVE, normal V2/video flags OFF, staging unavailable/UNVERIFIED.

Source trace for the next gate is complete: `AdminCatalogCaseOpeningService.open()` first reuses any OPEN case, then returns409 only when no OPEN case exists and item moderation is neither NOT_REQUIRED nor APPROVED. Existing BE opening test already performs real HIDE then new opening409. Therefore do not fabricate a duplicate-OPEN409: use actual Admin HIDE resolving a case between browser detail read and opening submit, then verify authoritative HIDDEN/history refresh without creating another case.

Next action: Run that focused actual browser stale-detail→Admin HIDE→opening409→authoritative refresh scenario in the existing disposable fixture.
Required from you: Nothing
Completion condition: Browser shows actual HIDDEN/case state after409, without invented successful opening or duplicate side effects.
