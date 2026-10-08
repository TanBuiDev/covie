# Buyer Repair and UI Implementation Plan

**Checkpoint 2026-10-07 — món trong tóm tắt đơn:** Backendregression missingitems/name fail;DTO tối thiểu(name,quantity),querybulk các visibleorderIDs,group đúngorderID/name từ snapshot(name hoặc legacyitemName),không fullsnapshot/sensitivefields.6backendreadtests+1OpenAPIexport đạt36.44s,onebulkcall/no privatefield;SpringData startupđạt nhưng chưa populatedSQL. Clientgenerate/check/webTypeScript/lint đạt;webregression thiếuitemfail rồi Buyerlist hiển thị quantity/name/fallback,9focusedtests đạt. Không restartAPI/sửaDBphục hồi. Build/browser/fullsuite mới/chạybulkSQL/isolation/snapshotimmutability/UI/location/icon/125%zoom/fullplan còn mở.

**Checkpoint 2026-10-07 — giá trên thẻ đơn:** Hai regression thiếupricefail,UI đọc totalAmount hoặc knownTotal với label phân biệt Tổng tiền/Số tiền đã biết và phíNEGOTIATED cần thỏa thuận; sharedformatter/updatedAt fallbackcreatedAt.8focusedtests/typecheck/lint/build35pages đạt,Chrome listfixture5sizes5.3s đạt,ảnh390 xem trựcquan. Buildyield khi hoàn thiện,browser mới xác nhận currentprice và terminalbuild sau đó đạt. Backendruntime vẫnchưa restart,giá thật/fullsuite sau sửa chưa xác minh;summaryitem/location/icon/desktopframe/125%zoom/fullplan còn mở,không mutation thật.

**Checkpoint 2026-10-07 — giá tóm tắt đơn:** Regression NEGOTIATED fail thiếupricing; DTO thêm updatedAt/OrderPricingResponse từ OrderEntity lưu sẵn, mọi mapping dùng summary chung/khôngquerymới/N+1/migration.5backendtests/offline đạt, OpenApiExportTest databasePostGIS riêng1test26.1s đạt; regenerateclient/check/webTypeScript đạt. Snapshot item/location có, tên doanh nghiệp lịch sử chưa có. UI giá chưa nối,live8081 vẫn bảncũ/chưarestart;summaryitem/location/fullUI/125%zoom/acceptance còn mở. Không sửaDB phục hồi.353web run trước chưa chứng minh contract mới.

**Checkpoint 2026-10-07 — kiểm chứng phân trang đơn:** Build35pages/fullweb93files353tests đạt (`order-pagination-gates.json`). FocusedChrome5sizes5.1s querycursor/limit20/503giữrows+retry/samebatch1read/dedup/401login. Sửa harness alert trùng Nextannouncer/kỳ vọng422 sai, codec400. Audit demo-login/GET thật25.4s đạt:limit1 traverses4đơn đúng thứ tựlimit50/khôngtrùng/invalidcursor400; readnotifications/deeplink/address/protected/error[]/blockedWrites[] đạt. Không mutation nghiệp vụ. Testfixture thứ9 focusedonly,chưa full9suite;UI/icon/summarybackend/125%zoom thật/mutations/Google/fullplan còn mở.

**Checkpoint 2026-10-07 — phân trang đơn Buyer:** MeOrderController mặc định20/trảcursor,frontend bỏ nextCursor nên ẩn đơn cũ.3regression thiếu load-more fail; myOrders nhận query,Buyer tải tiếp/ref chống trùng/generation bỏ late response/dedupID, lỗi transient giữ list và cursor để thử lại,401 đúnglogin.6focusedtests/TypeScript/lint đạt. Chưabuild/browserpagination/GETcursor thật sau sửa;8Chrome trước không chứng minh thay đổi mới. Chưa sửa contract/backend, không mutation thật;fullplan còn mở.

**Checkpoint 2026-10-07 — chip Đơn hàng:**4regression fail khi chỉ có label; dùng sharedStatusChip với tone presentation3trạng thái biết/unknownneutral. CSS scopedconsumer-shell màu/kích thước28px/11px.13focusedtests/lint/build35pages đạt;fullChrome8tests22.7s trước finalscope, rebuild/focusedorders5sizes4.2s sau scope đạt. Xem ảnh390 xác nhận chip màu. Icon chính xác/API summary thiếu/desktopframe+bố cục/125%zoom thật vẫn mở, không downloadasset hoặc mutation thật. Chưa hoàn tất fullBuyer.

**Checkpoint 2026-10-07 — danh sách Đơn hàng:** Lấy fullcontext/screenshot exact mobile332:4364. API summary thiếu merchant/location/item/amount/updatedTime so với thiết kế, không thêm dữ liệu giả. Browser regression phát hiện nhãn COMPLETED khác frame; OrderStatusLabel đổi Hoàn tất/Chờ người bán xác nhận, chỉ presentation. Build35pages/lint đạt. Chrome test mới5sizes đạt4.5s:503→GETretry/empty/populated link+focus/401 đúng login/no overflow, không mutation thật. Xem ảnh orders-current1366/390: desktop còn căn giữa metadata, mobile còn thiếu chip đúng thiết kế; desktop mapping/assets/full fidelity/125%zoom còn mở. Chỉ test thứ8 mới chạy focused, chưa full8suite.

**Checkpoint 2026-10-07 — đăng xuất và gates hiện tại:** Regression chứng minh hai click cùng batch gọi logout2lần; ref đồng bộ chặn trùng, nhả khi lỗi để thử lại.2focused tests/lint/build35pages đạt. Fullweb90files344tests đạt trước sửa logout (`session-empty-gates.json`), không coi run này bao phủ2test mới. Chrome fixture hiện tại7tests đạt21.5s trên5sizes gồm loading/SSRerror/session recovery/inbox/cart/checkout, mutation mô phỏng, tự dừng tiến trình. Figma/125%zoom thật/nghiệp vụ thật/Google/fullBuyer vẫn mở.

**Checkpoint 2026-10-07 — danh sách phiên rỗng:** Hai regression fail sau khi sửa cấu trúc fixture test.each: nút thu hồi phiên khác vẫn bật khi rỗng/chỉ phiên hiện tại. Thêm thông báo rỗng, khóa nút và guard handler nếu không có phiên khác được xác định.9 focused tests/TypeScript/lint đạt, không thu hồi phiên thật. Inventory chỉ có context trust/security, chưa có frame phiên chính xác nên không xác nhận fidelity.125%zoom/fullBuyer còn mở.

**Checkpoint 2026-10-07 — loading bảo mật:** SSRgate giữ response,Chrome5sizes chứng minh loading live/no visible session rồi release hiển thị list/bỏ loading. FocusedChrome pass3.6s/typecheck/lintclean; locator sửa để loại hidden streamingDOM trùng tạm thời, không productfix. Không mutation thật;fullfixture trước6tests chưa gồm gate mới,fullFigma/125%zoom/fullBuyer còn mở.

**Checkpoint 2026-10-07 — SSR bảo mật phục hồi:** APIserver riêng503→200,Chrome5sizes chứng minh safeerror/no diagnostics/retry fresh SSRGET/sessionrender/no overflow. Build35pages/6Chromefixtures pass17.3s/lintclean, fixtures trước vẫn pass. Không mutation thật;loadingdelayed/Figma/keyboard/125%zoom/fullBuyer còn mở.

**Checkpoint 2026-10-07 — trạng thái ban đầu bảo mật:** Đọc local Next16.3.3 error/loadingdocs, thêm error.tsx safe copy/retry server/profilelink và loading.tsx live status dùng shared UI.7focused tests/typecheck/lint pass; redmissingimport chỉ chứng minh file chưa có, chưa runtime recovery. SSRbrowser/visual vẫn cần kiểm chứng, không ghi dữ liệu.

**Checkpoint 2026-10-07 — Chrome bảo mật:** SSRfixture sessions mô phỏng;build35pages/5Chrome fixtures pass16s,session5sizes1command/read503locks/GETretry không replay/command401 đúng login. Inbox/cart/checkout vẫn pass,fixturelint clean. Không revoke thật;read401 chỉ unit,initialSSRerror/Figma/zoom/fullBuyer còn mở.

**Checkpoint 2026-10-07 — refresh phiên:** Regression fail cho DELETE thành công/list503 không readretry. Tách refresh, caption danh sách chưa xác minh/khóa revoke, GETretry nhả khi thành công và không replay DELETE,401 login giữ nguyên.5focused tests/typecheck/lint pass, không revoke thật; browser/Figma/zoom/fullplan còn mở.

**Checkpoint 2026-10-07 —401 quản lý phiên:** Source catch coi mọi lỗi là transient; giữ parsed typed error,401 thay sessions cũ bằng login returnTo đúng securitysessions. Mock ban đầu xử lý sai string fetch CSRF/refresh, đã sửa string/Request;4focused tests/typecheck/lint pass gồm command/list401, không coi failure mock ban đầu là chứng minh401. Không thu hồi phiên thật; browser/non-authrefresh/fullplan còn mở.

**Checkpoint 2026-10-07 — khóa thu hồi phiên:** Actual-client mocked-fetch regression fail cho hai nút cùng batch gửi2DELETE. Ref revokePending đồng bộ guard/nhả finally,2focused tests/typecheck/lint pass. Không thu hồi phiên thật;401/refresh/browser/Figma còn mở.

**Checkpoint 2026-10-07 — thống kê inbox phân trang:** Regression fail cho trang toàn read nhưng còn cursor vẫn báo tất cả đã đọc. Header giới hạn thống kê theo đang hiển thị khi còn cursor, chỉ dùng copy toàn inbox khi hết trang.14focused tests/typecheck/lint pass; browser copy chưa chạy, không ghi notification thật; fullplan còn mở.

**Checkpoint 2026-10-07 — mở thông báo đã đọc:** Demo-login/GET audit click thông báo đã đọc trên5viewports, URL đúng/mã đơn từ API hiển thị đúng; inbox before/after bằng nhau gồm read state,0blockedWrites. Final strengthened audit pass24.6s/typecheck/lint clean. Không click unread/mark-read; fullUI/zoom/nghiệp vụ vẫn mở.

**Checkpoint 2026-10-07 — inbox GET thật:** Demo-login/read audit pass19.3s; limit50 đối chiếu limit1cursor đọc3items đúng thứ tự/không trùng, cursor sai422; mọi deepLink đúng /orders/id và API đúng /api/v1/orders/id đọc200. Lint/typecheck pass. Harness ban đầu limit100/sai endpoint đã sửa theo contract/log; không coi500 endpoint sai là lỗi UI. Chưa click/mark-read thật; fullplan còn mở.

**Checkpoint 2026-10-07 — Chrome/API Thông báo:**3actual-client tests giữ403code/requestId,13focused tests pass. Build35pages/4isolated Chrome fixtures pass14.7s:5sizes cursor1GET/dedup,20initial/cursor/item/all401cases login đúng/no stale list; giỏ/checkout cũ vẫn pass. Lint clean, mark-read/all/refresh đều intercept mô phỏng, không đổi unread thật. Figma/zoom/realcursor/deep-link/fullBuyer còn mở.

**Checkpoint 2026-10-07 —401 Thông báo:**4UIregression fail cho initial/cursor/item/all401. API wrapper giữ typed parsed error; inbox thay stale list bằng login returnTo notifications và chặn thao tác sau401.10focused UI/deep-link tests/typecheck/lint pass, không mark-read thật; browser/APIwrapper tests riêng và toàn bộ kế hoạch vẫn mở.

**Checkpoint 2026-10-07 — đồng thời inbox:** Hai regression fail cho cursor2reads/cập nhật item+all chồng nhau. Ref loadPending/mutationPending khóa đồng bộ load/command; disable controls và dedup ID giữa trang.6focused inbox/deep-link tests/lint/typecheck pass. Không mark-read thật; browser guard/session401/stalecleanup và toàn bộ kế hoạch còn mở.

**Checkpoint 2026-10-07 — đã sửa Thông báo theo duyệt:** Người dùng duyệt riêng24repair+backup; apply COMMIT. Backup24rows, mapping18completed/1new/1confirmed/3fulfillment/1cancelled,0mismatches. Chỉ UPDATE type/payload, giữ ID/owner/read state. Demo-login+GET-only browser audit pass20.7s; inbox1366/390 không load/pageerror/overflow,0blockedWrites; ảnh desktop thấy3thông báo/2unread. Chưa mark-read/cursor/deep-link acceptance thật; toàn bộ kế hoạch chưa xong. Ghi chú chờ duyệt trước đó đã được thay thế.

**Checkpoint 2026-10-07 — preflight Thông báo mới:** Chạy lại script mặc định trên PostgreSQL phục hồi đang healthy:24ID/owner/order/status khớp, kết thúc ROLLBACK. Mapping18completed/1new/1confirmed/3fulfillment/1cancelled giữ nguyên. Đã gửi câu hỏi duyệt riêng apply+tạo backup, chưa có câu trả lời và chưa ghi dữ liệu. Rà source inbox thấy pending chỉ state/load concurrency/auth cần regression tiếp theo.

**Checkpoint 2026-10-07 — UI giỏ hiện tại:** Build35pages/3Chrome fixtures pass8.9s,5viewport captures cart-current,1366/390 xem trực quan; Tab tăng→Xóa pass. Bottomnav mobile khác exact324:3872; regression Chrome fail rồi scoped hide riêng cartdetail max1023, recapture/rerun pass. API mô phỏng, không ghi thật. Desktop frame/assets/actual125%zoom/fullBuyer chưa nghiệm thu.

**Checkpoint 2026-10-07 — nội dung giỏ/design:** Đã lấy fullcontext+screenshot frame324:3872 Product390. Contract giỏ không có current availability; bỏ nhãn “Có sẵn” hardcoded sau regression fail, thêm giải thích giá/khả dụng kiểm tra lại khi đặt hàng.12focused tests/lint pass, inventory ghi giới hạn contract. Desktop frame/125%zoom/full fidelity và nghiệp vụ còn mở.

**Checkpoint 2026-10-07 — gates/hồ sơ:** Full web326tests/0failures pass (`cart-and-needs-gates.json`). Inventory cập nhật partial runtime giỏ/Cần mua, phân biệt real GET/synthetic/unit khỏi visual/actual mutation; ghi chú Search/address/backend pending cũ được thay bằng trạng thái hiện tại. Cart navigation mới chỉ unit proof, build/browser trước đó chưa bao phủ.26route Figma/keyboard/125%zoom/nghiệp vụ vẫn mở, không ghi dữ liệu thật.

**Checkpoint 2026-10-07 — điều hướng giỏ:** Regression fail chứng minh đổi initialCart ID vẫn giữ giỏ/pending cũ. CartDetailContent keyed theo ID để reset state và cô lập late refresh cũ.18focused cart tests/typecheck/lint pass; test xác nhận checkout link giỏ mới và kết quả cũ trả muộn không ghi đè. Không ghi nghiệp vụ; navigation mới chỉ có unit proof, browser trước đó chưa bao phủ thay đổi này.

**Checkpoint 2026-10-07 — Chrome giỏ:** Build35pages pass, API SSR mô phỏng riêng có fixture-cart;3Chrome fixturetests pass8.8s trên5viewports gồm giỏ pending/chống gửi trùng/503refreshretry chỉ đọc/401login/no overflow và checkout409/SSRretry cũ. Lint fixture pass; afterAll dừng process riêng, không ghi giỏ/đơn/dữ liệu phục hồi thật. Các nhánh khác chỉ có unit evidence; toàn bộ Figma/zoom/nghiệp vụ Buyer còn mở.

**Checkpoint 2026-10-07 — mã lỗi giỏ:** Bốn test API client thực fail vì body đã parse bị đọc lại, mất RESOURCE_VERSION_CONFLICT/requestId cho update/remove/get/list. Tất cả wrapper giỏ truyền error đã parse cho parsedApiError, gồm add/DELETE.17focused cart tests/typecheck/lint pass. Chưa chạy browser giỏ sau thay đổi, không ghi giỏ thật; toàn bộ kế hoạch vẫn mở.

**Checkpoint 2026-10-07 — phiên hết hạn giỏ:** Ba regression fail cho command/refresh/read retry401. CartDetail phân loại401 từng điểm, thay controls cũ bằng login returnTo đúng giỏ, không gọi refresh tiếp khi command401. Lỗi khác giữ read retry.13focused cart tests/typecheck/lint pass, không mutation thật; browser/Figma/nghiệp vụ và toàn bộ kế hoạch vẫn mở.

**Checkpoint 2026-10-07 — lỗi tải lại giỏ:** Hai regression fail cho update thành công/refresh lỗi và conflict/refresh lỗi. Đã tách lỗi command khỏi refresh, chỉ xác nhận dữ liệu mới khi GET thành công; refresh lỗi khóa item/checkout và cung cấp retry chỉ đọc, không replay mutation.10focused cart tests/typecheck/lint pass; không ghi giỏ thật. Auth/browser/nghiệp vụ và toàn bộ Buyer còn mở.

**Checkpoint 2026-10-07 — khóa thao tác giỏ:** Regression fail chứng minh hai item click cùng batch gửi2commands dùng cùng version. Đã khóa ref đồng bộ đến hết mutation/refresh, disable mọi thao tác item và ẩn checkout summary trong lúc pending.8focused cart tests/typecheck/lint pass, kiểm tra nhả khóa cho lần cập nhật sau. Không cập nhật giỏ thật; browser/business acceptance, xử lý auth/lỗi refresh và toàn bộ UI vẫn mở.

**Checkpoint 2026-10-07 —401 phản hồi Cần mua:** Ba regression fail trước sửa cho initial responses/address/cursor401. NeedResponseList phân loại typed401, thay section bằng login returnTo đúng nhu cầu, bỏ checkout action cũ; lỗi khác giữ retry độc lập.18focused tests/typecheck pass, Chrome15trường hợp đọc401 trên5viewports pass. Fixture không ghi nghiệp vụ thật; toàn bộ kế hoạch vẫn mở.

**Checkpoint 2026-10-07 — phiên hết hạn khi hủy Cần mua:** Test fail chứng minh401 giữ thao tác cũ/thiếu login. Đã phân loại typed401 và hiển thị ErrorState đăng nhập lại đúng nhu cầu kể cả đã có detail cũ.10focused tests/typecheck/lint pass; Chrome fixture mới5viewports pass chống gửi trùng/khóa pending/503retry an toàn/401 bỏ action cũ/login/no overflow. API responses đều mô phỏng, không hủy hay sửa dữ liệu thật. Nghiệm thu toàn bộ UI/nghiệp vụ vẫn mở.

**Checkpoint 2026-10-07 — hủy Cần mua:** Regression chứng minh bấm cùng batch gửi2commands và lộ thông báo backend. Đã thêm khóa ref đồng bộ/nhả finally, dùng thông báo lỗi tiếng Việt an toàn.9test action/recovery và lint pass, giữ retry sau lỗi và cập nhật theo response server. Chỉ mock unit, không hủy nhu cầu thật/ghi dữ liệu phục hồi; nghiệm thu browser nghiệp vụ và toàn bộ kế hoạch vẫn mở.

**Checkpoint 2026-10-07 — mã lỗi checkout:** Đã chứng minh bằng test API client thực lỗi đọc lại body đã tiêu thụ làm mất CHECKOUT_CHANGED/VALIDATION_FAILED; hai wrapper POST dùng parsedApiError với error đã parse. Full web309tests pass, build35pages/typecheck/lint pass. Chrome fixture2tests trên5viewports pass gồm409 mở dialog/hủy preview cũ,503retry giữ idempotency key và chống gửi trùng. Toàn bộ POST nghiệp vụ mô phỏng, không tạo đơn hay sửa dữ liệu phục hồi; nghiệm thu thao tác thật và toàn bộ Buyer vẫn mở. Quyền đăng nhập demo/kiểm tra chỉ đọc đã được duyệt.

> **For agentic workers:** Dùng skill `executing-plans` để triển khai tuần tự theo các checkpoint. Checkbox ghi nhận việc đã thực hiện và bằng chứng; không đánh dấu hoàn thành chỉ vì có route hoặc test đơn vị pass. Kế hoạch này chưa cho phép cập nhật dữ liệu hay triển khai production.

**Goal:** Sửa các vấn đề Buyer đã xác nhận, rà soát và chỉnh UI toàn bộ khu vực người mua, kiểm chứng các luồng trên laptop bằng dữ liệu thử nghiệm.

**Architecture:** Giữ Next.js/Spring Boot và contract hiện tại; sửa ở nơi phát sinh lỗi, không che lỗi bằng dữ liệu giả. Tách sửa truy vấn, tương thích dữ liệu demo, trạng thái UI và cải thiện trải nghiệm thành các phần kiểm chứng độc lập. Địa chỉ nhận hàng và vị trí khám phá phải có ý nghĩa riêng.

**Tech Stack:** Next.js 16, React 19, TypeScript, pnpm 10.34.5, Spring Boot 4, Java 25, Maven Wrapper, PostgreSQL 18/PostGIS, Redis, MinIO, Vitest và Playwright.

**Ngày lập:** 2026-10-06, Asia/Saigon.

**Repository:** `E:\WebApp\CoVie\covie`.

**Trạng thái:** Đã được duyệt triển khai; đang thực hiện. Chưa cập nhật dữ liệu demo hoặc cấu hình OAuth.

**Checkpoint 2026-10-07 — browser khóa commit checkout:**Isolated productionweb3002/syntheticAPI thêm2giỏ; fixture browser-only preview/commit/CSRF kiểm tra5viewports pass:2CTA click cùngbatch chỉ1commit, khóa cartpending/nhả sau503,retry cùng token/idempotencykey.2tests (gồm503retrySSR/404 cũ) pass6.2s,afterAll dừng process. CSRF GET ban đầu thoát routing/404; serviceWorkers:block giúp intercept rồi rerun pass, chưa kết luận rootcause productionSW. Build35pages/typecheck/lint pass; không POST nghiệp vụ/tạođơn thật. Fullweb hiện tại/livecommit/idempotency vẫn mở; cần kiểm tra parsed error của checkout API trước khi nghiệm thu CHECKOUT_CHANGED409 thật.

**Checkpoint 2026-10-07 — backend đầy đủ/checkout pending:**80533 offline clean verify với cached MinIO digest kết thúc BUILD SUCCESS(2min), không còn handlebackend live; release performance skip vẫn chưa nghiệm thu. Checkout2regressions fail trước sửa: cùng batch2nútcommit gửi2requests, selector giỏ còn bật lúc commit. Ref commitPending chặn ngay commit/chọn giỏ/preview mới, selector disable pending vàfinally nhả đểretry; commitKey vẫn giữ cơ chế cũ.32focused checkout tests/typecheck/lint pass, không commit/tạođơn thật. Full web/build sau checkout/browser commit và business acceptance vẫn mở. Các ghi chú backend pending trước đó là lịch sử; toàn bộ kế hoạch chưa hoàn tất.

**Checkpoint 2026-10-07 — backend tests/package:**52329 terminalfailed ở đóng gói do dependency cache offline thiếu, sau193reports/548tests:0failures/0errors,1skip HotQueryPlanIntegrationTest vì thiếu COVIE_PERF_JDBC_URL (release performance DB). Online verify -DskipTests tải dependency packaging đúng cấu hình và BUILD SUCCESS; command này không chạy lại tests. Web full lint/API-client check pass. Đã khởi chạy offline clean verify đầy đủ với cached MinIO để chứng minh command cuối thành công; poll handle mới trong output, không52329. Hai checkbox capability/UI Google đã có bằng chứng được đánh dấu, acceptance thật/auth/fullBuyer vẫn mở.

**Checkpoint 2026-10-07 — backend S3 gate:**91446 dừng sau219tests/74reports chưa lỗi vì S3ObjectStorageTest tự pull image quay.io chưa cache, registry401. Đã dừng verifiedSurefire27168,91446 terminal failed. Thêm optional covie.test.minio-image giữ default; kiểm tra cached chainguard digest/entrypoint, focused offline S3 test dùng digest a05a4497e8dc... pass1test trên container tạm, không ghi ảnh thật. Đã khởi chạy lại full offline clean verify với digest có sẵn; chưa có fullbackend pass. Không reset DB/khoảnh phục hồi. Poll handle mới từ output, không restart91446.

**Checkpoint 2026-10-07 — gates tổng hợp:**Full web86files/305tests pass (.local/buyer-ui-audit/need-create-gates.json), build35pages pass/web health200 sau build, thay thế pending gates gần đây của NeedCreateForm/CTA. Backend clean verify offline dừng vì thiếu clean-plugin3.5.0; retry online theo quyền Maven dependency đã duyệt tải plugin đúng cấu hình, compile755main/195test sources rồi đang chạy tests. Exec session91446 phải poll tiếp, không restart; Testcontainers database tạm postgis18-3.6, không DB phục hồi. Chưa có backend pass hoặc nghiệm thu toàn bộ Buyer.

**Checkpoint 2026-10-07 — Chrome readiness và CTA mobile:** Test mới thất bại vì bottom nav che nút đăng360px; đã xem screenshot và xác nhận fixedCTA bottom16 chồng sharednav. Lấy fullcontext/screenshot exactframe343:5661 (NEED-002360) theo coverage matrix: sticky48pxCTA không có bottomnav. CSS max1023 ẩn nav riêng shell chứa needs-surface--form, giữ CTA.2Chrome fixture tests×5viewports pass gồm bấm thật, địa chỉ rỗng, expiry quá khứ không POST, prerequisite401/create401 giữ text, SERVICE tải chờ/đổi danh mục, retry giữ text/số lượng/gửi trùng. Mọi csrf/refresh/create là response mô phỏng, không ghi nghiệp vụ thật. Typecheck/lint pass. Chưa nghiệm thu fullFigma/zoom125%/tạo thật/idempotency/backend/full suite/build/toàn bộ Buyer; các browser-pending ghi chú trước chỉ là lịch sử với states đã liệt kê.

**Checkpoint 2026-10-07 — Chrome tạo Cần mua/copy lỗi gửi:**2regressions trước sửa:503 lộ message,401 thiếu login sau mock create. Catch mutation nay copy theo mã expiry/validation/generic hoặc giữ form/login401;12readiness tests pass. Opt-in e2e/need-create.fixture.spec.ts Chrome5kích thước pass: prerequisite503 retry giữ text, quantity thập phân bị chặn,2submit cùng batch chỉ1POST bị intercept, copy503 an toàn/không tràn ngang. CSRF/create trả response giả trên browser; unsafe khác chặn, không ghi tạo/login/refresh thật. Lỗi locator alert trùng Next announcer đã sửa scope form rồi pass. Typecheck/lint pass. Chưa nghiệm thu tạo thật/idempotency khi response không chắc chắn/browser401/past-date/type-switch/empty-address/full suite/build hoặc toàn bộ Buyer.

**Checkpoint 2026-10-07 — kiểm tra dữ liệu/gửi trùng Cần mua:**6tests thất bại trước sửa: quantity rỗng/0/thập phân/ngoài safe integer, expiry quá khứ, cùng batch submit2lần tạo2mock requests. Nay validate số nguyên dương an toàn/thời điểm hữu hạn trong tương lai trước API, ref khóa ngay khi gửi và nhả trong finally; HTML required/min/max/step hỗ trợ. Backend NeedCommandService xác nhận rule future và DTO quantity Long positive;SERVICE giữ1. Hint thời hạn dùng ngôn ngữ người dùng.37Need tests/typecheck/lint pass; thêm case nhả khóa sau lỗi rồi10readiness tests pass. Không POST tạo thật. Browser/readiness/numeric/date/double-submit, copy lỗi mutation/idempotency khi response không chắc chắn/full suite/build/whole-Buyer vẫn mở.

**Checkpoint 2026-10-07 — readiness tạo Cần mua:** Sau sửa query accessible-name test,3regressions thất bại trước sửa (thiếu retry/login401, selection cũ dùng được khi đổi loại). Nay prerequisite load lỗi có copy an toàn/retry giữ nội dung,401 login returnTo /needs/new, trạng thái loading, disable selector/submit đến khi loại hiện tại tải xong; submit kiểm tra category/address còn trong danh sách. Address đã mất được chọn lại từ danh sách mới. Regression quay PRODUCT→SERVICE→PRODUCT khi chờ cũng thất bại rồi được sửa bằng changeType invalidation.31Need tests pass trước guard cuối;3readiness tests/typecheck/lint pass sau guard; test-only exact option type error đã sửa. Không POST tạo nhu cầu thật. Browser readiness/error, double-submit/số lượng/thời hạn/lỗi mutation/full suite/build và whole-Buyer vẫn mở.

**Checkpoint 2026-10-07 — cô lập trang đánh giá/tải chồng nhau:**3regressions thất bại trước sửa (form cũ còn khi đổi orderId; lỗi ID cũ ghi đè trạng thái ID mới; retry cũ ghi đè mới). Keyed OrderReviewContent cô lập state theo đơn, generation chỉ nhận request mới nhất, cleanup bỏ queued/pending read cũ.18review tests/typecheck/lint pass. Full web85files/293tests pass (.local/buyer-ui-audit/review-recovery-gates.json), build35pages pass, web health200 sau build. Các ghi chú full-suite/build-pending gần đây là lịch sử; không gửi đánh giá thật. Browser5route×5viewport fixture pass trước thay đổi concurrency này, chưa nghiệm thu browser race/expiry backend thật/NeedCreateForm/Google/whole-Buyer Figma/zoom/backend/mutation gates.

**Checkpoint 2026-10-07 — tải trang đánh giá:** 2regressions trước sửa:401 không có login,503 lộ message. OrderReviewPage nhận diện CovieApiError401, quay login đúng review route; lỗi khác retry với copy an toàn.15review tests/typecheck/lint pass; Chrome session fixture5routes×5viewports pass (csrf/refresh/error responses mô phỏng tại browser), không gửi đánh giá/login/refresh backend thật. Chưa nghiệm thu submit thật/backend-expiry, cô lập state theo review orderId/refresh hoặc NeedCreateForm/full-suite/build hiện tại. Kế hoạch vẫn đang triển khai.

**Checkpoint 2026-10-07 — đổi đơn/tải chồng nhau:**3regressions thất bại trước sửa (đơn cũ còn khi đổi ID; lỗi ID cũ ghi đè401 ID mới; retry cũ ghi đè kết quả mới). OrderDetailContent key theo vai trò/merchant+orderId; generation chỉ nhận request mới nhất, cleanup vô hiệu hóa request/queued load cũ.10orders tests/typecheck pass, lint sạch sau điều chỉnh stable-ref cleanup;6recovery tests rerun pass. Chrome session fixture4routes×5viewports rerun pass, không POST backend thật. Chưa nghiệm thu expiry backend thật/full suite/build hiện tại/review/create/whole-Buyer; ghi chú concurrency-pending trước đó là lịch sử.

**Checkpoint 2026-10-07 — phiên hết hạn chi tiết đơn:** 3regressions chứng minh401 thiếu đăng nhập và403/503 lộ message backend trước sửa. Buyer OrderDetailView nhận diện CovieApiError401, hiện login returnTo đúng orderId, lỗi tải khác giữ retry với copy an toàn.7orders tests/typecheck/lint pass; Chrome fixture4routes×5viewports pass với csrf/refresh/401/503 mô phỏng, không POST backend thật. Chưa nghiệm thu expiry backend thật/refresh chồng nhau/đổi orderId/review/create hoặc full suite/build sau thay đổi này; toàn bộ kế hoạch vẫn mở.

**Checkpoint 2026-10-07 — cô lập trạng thái theo needId:** Regression đổi needId vẫn hiện tiêu đề/xác nhận hủy nhu cầu cũ thất bại trước sửa. NeedDetailContent dùng key theo ID, bỏ trạng thái cũ ngay khi đổi nguồn; cleanup ngăn response cũ tới muộn.4recovery tests pass gồm đổi ID khi request mới chờ và response cũ sau response mới. Full web84files/282tests pass (.local/buyer-ui-audit/need-recovery-gates.json), build35pages pass, web health200 sau build. Không gọi hủy thật/ghi nghiệp vụ. Các ghi chú full-suite/build-pending gần đây là lịch sử; toàn bộ Buyer vẫn chưa nghiệm thu Figma/zoom, các detail/create/review khác/OAuth/backend/mutation gates.

**Checkpoint 2026-10-07 — tải lỗi chi tiết Cần mua:** 2regressions 401 không có login và503 không có retry/lộ message thất bại trước sửa. Tải ban đầu nay dùng ErrorState hiện có, copy an toàn,401 đăng nhập lại đúng needId, lỗi khác có thử lại và xóa lỗi khi tải thành công.26Need tests/typecheck/lint pass; fixture mới đã sửa field requestedQuantity sau lỗi fixture. Chrome session-expiry fixture mở rộng3routes×5viewports pass, csrf/refresh mô phỏng trên trình duyệt, không POST backend thật. Chưa nghiệm thu exact-state Figma/stale data khi đổi needId/lỗi hủy nhu cầu hoặc các detail/create/review khác; chưa chạy lại full suite/build cho thay đổi này.

**Checkpoint 2026-10-07 — phiên hết hạn ở danh sách client:** NeedList/Buyer OrderList bỏ nhận diện theo chữ token/401 trong message, dùng CovieApiError status401; liên kết đăng nhập đổi redirect= thành returnTo.2regressions Need thất bại trước sửa;8focused tests/typecheck/lint pass. Chrome fixture2routes×5viewports pass:401 đăng nhập đúng đích,503 thử lại/không tràn ngang. Fixture đầu chặn refresh gây lỗi mạng; fixture sửa chặn và trả response giả cho csrf/refresh ngay trên trình duyệt, không POST backend thật hoặc tạo phiên. Chưa nghiệm thu hết phiên backend thật/detail/create/review/Google; chưa chạy lại full suite/build cho checkpoint này. Bài kiểm tra opt-in e2e/buyer-session-expiry.fixture.spec.ts.

**Checkpoint 2026-10-07 — mở rộng return-to server routes:** Các Buyer page đang gọi requireUser đã truyền đích nội bộ, gồm ID động đã encode và query checkout giữ lựa chọn/array. Regression checkout mất lựa chọn thất bại trước sửa rồi pass;55 focused tests/typecheck/lint pass; full web report tại .local/buyer-ui-audit/return-to-gates.json và build35pages pass. GET thực tế không cookie: /me,/me/notifications,/cart chuyển login với returnTo; /me/addresses và checkout chọn DIRECT trả streamed redirect đúng đích (HTTP200 không chứng minh đã đăng nhập). Không tạo phiên/OAuth hoặc ghi nghiệp vụ. Các route tải bằng client (orders/needs/review), Google return-to và browser end-to-end khi hết phiên vẫn chưa nghiệm thu. Ghi chú /me-only/build-pending bên dưới là lịch sử.

**Checkpoint 2026-10-07 — return-to bước đầu:** Regression mất đích khi phiên hết hạn đã thất bại trước sửa. requireUser nhận đích nội bộ tùy chọn; /me giữ đích qua login. LoginPage đọc searchParams async, LoginForm quay về đích đã kiểm tra sau email login. Chặn URL ngoài/protocol-relative/backslash/control/encoded separator và đích API/Auth (kể cả dot-segment sau chuẩn hóa).21 focused tests/typecheck/lint pass;503 vẫn là lỗi tải, không chuyển login. Chưa mở rộng các route còn lại/Google, chưa kiểm tra hết phiên bằng trình duyệt hoặc chạy lại build/full suite sau thay đổi này. Task6 vẫn mở; kiểm thử dùng mock, không tạo phiên hoặc ghi nghiệp vụ.

**Checkpoint 2026-10-07 — capability Google UI:** Auth layout chia sẻ trạng thái loading/configured/unconfigured/error; nút Google và liên kết quên mật khẩu/đăng ký không mở OAuth khi chưa xác nhận cấu hình, lỗi tải có thử lại. Đăng nhập email vẫn dùng được. Đã quan sát regression liên kết luôn bật trước sửa. Full web81files/257tests, typecheck/lint/build35pages pass. Chrome chỉ đọc kiểm tra4trang Auth ×5kích thước,20ảnh, không tràn ngang/không liên kết OAuth hoạt động khi configured:false; error/retry/configured dùng fixture trên trình duyệt, không xác thực Google thật hoặc ghi dữ liệu nghiệp vụ. Đã xem ảnh login1366/390; API/web health200 sau build. Bằng chứng .local/buyer-ui-audit/auth-capability/observations.json và auth-capability-gates.json. Chưa có exact Auth Figma frames, chưa kiểm tra zoom125%, return-to/session hết hạn hoặc callback Google thật; Task6 và kế hoạch toàn bộ vẫn đang mở. Ghi chú UI chưa nối capability bên dưới là lịch sử.

**Checkpoint 2026-10-07 — capability Google backend:** GET public /api/v1/auth/google/status chỉ trả configured:boolean từ registration backend; không trả client ID/secret, không tạo session. Google/start chưa cấu hình trả503 AUTH_PROVIDER_UNAVAILABLE trước handshake.3unit +2security integration +4cookie/CSRF acceptance +1OpenAPI export pass; contract regenerate/API-client check/web typecheck pass. API8081 khởi động lại bằng script runtime đã duyệt, session1902/PID1680. UI chưa nối capability nên Task6 chưa hoàn thành; configured chỉ có nghĩa cấu hình hiện diện, không chứng minh Google xác thực thành công. Credentials thật/OAuth acceptance và gates toàn bộ vẫn mở, không cập nhật dữ liệu nghiệp vụ phục hồi.

**Checkpoint 2026-10-07 — số lượng và phục hồi checkout:** Product DIRECT/NEED_REQUEST có tăng/giảm, đổi số lượng xóa preview/token và bỏ qua phản hồi muộn; service/thanh lý giữ1. Số lượng vượt độ chính xác integer bị từ chối. Dùng QuantityStepper theo Figma299:2562/81:339 trong Checkout/Section199:2604; icon local20px, nút44px. Chrome5viewport GET thật + preview giả lập pass cả bàn phím và payload số lượng, không ghi nghiệp vụ. Test mới checkout-recovery.fixture.spec.ts cô lập web3002/API tổng hợp chứng minh503→Thử lại→GET mới và404 nguồn sai ở5sizes, không dùng backend/demo login thật, tự dừng tiến trình. Đã sửa nút retry1116px thành220×48 sau regression và đối chiếu Figma safe-error328:4445. Toàn bộ web80files/253tests (JSON report), typecheck/lint/build35pages pass. UI toàn bộ/zoom125% thật, OAuth/backend/mutation gates và apply notification vẫn mở.

**Checkpoint 2026-10-07 — lỗi tải checkout:** Hai regression fail trước sửa:503 bị coi là nguồn hết hiệu lực, error boundary không có retry. Đã tách lỗi đọc tạm thời khỏi nguồn sai/không còn tồn tại; error.tsx dùng retry được tài liệu Next16.3.3 mô tả để tải lại dữ liệu.19 focused tests/typecheck/lint pass; build kiểm tra riêng. Chưa có browser acceptance cho trạng thái error/recovery, chưa ghi dữ liệu nghiệp vụ. Chỉnh số lượng/UI toàn bộ/zoom/OAuth/backend/mutation gates còn mở.

**Checkpoint 2026-10-07 — tên biến thể checkout:** DIRECT/NEED_REQUEST hiển thị tên biến thể lấy từ API; Need đọc catalog tại đúng điểm bán phản hồi và kiểm tra offer/type/location/khả dụng, thanh lý một đơn vị. Hai regression fail trước sửa; 17 focused tests/typecheck/lint và toàn bộ web80files/245tests pass. Chrome5viewport GET thật xác nhận tên biến thể; preview chỉ giả lập trình duyệt, không ghi dữ liệu. Production build pass35 trang tĩnh. Chỉnh số lượng, retry lỗi server, UI toàn bộ/zoom125%, OAuth/backend gates/mutation acceptance còn mở.

**Checkpoint 2026-10-07 — mua trực tiếp:** CTA sản phẩm/dịch vụ/thanh lý đi tới checkout DIRECT, không thêm vào giỏ. Server kiểm tra item/điểm bán/offer khả dụng và số lượng dịch vụ/thanh lý=1; không fallback giỏ. Preview dùng source DIRECT canonical và commit vẫn là thao tác riêng. Regression fail trước sửa, toàn bộ web80files/244tests, typecheck/lint/build35pages pass. Chrome5viewport kiểm tra GET thật sản phẩm→checkout và preview giả lập tại trình duyệt; không POST checkout/cart thật. Tên biến thể, chỉnh số lượng, UI toàn bộ/zoom125%, OAuth và nghiệm thu dữ liệu ghi vẫn mở.

**Checkpoint 2026-10-07 — chọn địa chỉ checkout:** Đã sửa chọn địa chỉ mặc định ngay tại checkout, xóa preview/token khi đổi địa chỉ và chặn xem giá giao hàng/dịch vụ khi chưa chọn địa chỉ. Hai regression fail trước sửa; 7 checkout tests, toàn bộ web 80 files/238 tests, typecheck/lint pass. Chrome 5 viewport pass với GET thật và preview chỉ giả lập trong trình duyệt; không gửi POST checkout thật, không tạo đơn. Production build pass (35 trang tĩnh). Chưa đóng hạng mục UI toàn bộ Buyer hoặc nghiệm thu thao tác ghi dữ liệu.

### Checkpoint triển khai — 2026-10-07

**Build mới nhất:** Sau sửa checkout nguồn Cần mua và preview trả muộn, production build exit0 (Next16.3.3, TypeScript,35 static pages và route /checkout đã compile). NextCLI local docs xác nhận dev xuất .next/dev, build chạy đồng thời được; trạng thái runtime kiểm tra sau build. Checkpoint “build đang chạy” dưới đây là thời điểm trước kết quả. Full backend/whole-Buyer UI/zoom và mutation thật vẫn mở.

**Xác nhận đơn từ Cần mua:** Đã lấy Figma328:4164 đầy đủ+screenshot và dùng lại CheckoutPreview/sections/icon. Phản hồi hiển thị catalogItemName, link /checkout nguồnNEED_REQUEST; bỏ preview+commit tự động. Route server đọc Need thuộc Buyer, tìm item qua cursor, kiểm tra actionable/type/address và không fallback cart cho nguồn sai/trái quyền. Checkout hỗ trợ Product/Service, payload nguồn canonical, tổng từ backend và commit riêng sau xác nhận. Regression preview cũ trả muộn khi đổi nhận hàng fail trước sửa; thêm generation/pending guard.13 focused tests/typecheck/lint và full web80files/236tests pass. Browser GET thật đi tới checkout trên5 viewports; phần preview/tổng/CTA dùng fixture chặn trong browser, không POST checkout/commit thật. Evidence observations.json ghi rõ fixture; ảnh checkout1366/390 đã xem. DIRECT/nhãn biến thể/số lượng/chọn lại địa chỉ/eligibility và mutation thật còn mở. Build đang chạy riêng, chưa kết luận hoàn tất.

**Cần mua API thật và pagination UI:** Test Chrome opt-in pass với demo login/GET-only: nhu cầu có2 phản hồi trả200, limit1/cursor đủ2 ID đúng thứ tự, cursor sai422 VALIDATION_FAILED, guest401, need của buyer fixture khác404. Ép GET limit1 trên API thật (không thêm dữ liệu) kiểm tra tải tiếp UI trên5 viewports. Frontend đã sửa bỏ nextCursor, tải tiếp/khử trùng/giữ dữ liệu/retry cursor cũ và loại kết quả need cũ. Tách tải địa chỉ khỏi phản hồi; lỗi địa chỉ không làm ẩn dữ liệu tốt, có retry riêng. Regression fail trước sửa;4 response tests/7 focused tests pass; full web79files/229tests, typecheck/focused lint pass. Evidence `.local/buyer-ui-audit/need-runtime/observations.json`+5 ảnh,1366/390 đã xem. Item DTO có catalogItemName nhưng UI chưa hiển thị; checkout CTA đang preview+commit trực tiếp và modeSERVICE cần rà. Exact frame/zoom/trạng thái còn lại/full backend/build vẫn mở.

**Rà Buyer sau đăng nhập:** Người dùng duyệt đăng nhập demo, tạo phiên/audit rồi chỉ đọc. Audit Chrome opt-in chặn mutation API ngoài lần đăng nhập, không trace/video, ghi observations.json tại `.local/buyer-ui-audit/authenticated/`. PASS bước kiểm tra địa chỉ mới/chọn điểm/xóa xác nhận trên5 kích thước; đọc địa chỉ, hồ sơ, đơn, cần mua, thông báo, uy tín, phiên tại1366×768/390×844. Không tràn ngang/page error/mutation bị chặn. Thông báo vẫn lỗi tải; uy tín chưa có dữ liệu, chưa coi hoàn tất. Sửa lỗi gửi lưu địa chỉ trùng bằng ref/disabled/busy và cho retry;9 address tests pass. GPS button26px được sửa thành44px với kiểu secondary có sẵn; audit browser pass lại. Exact frame, zoom125%, dynamic state và mutation nghiệp vụ vẫn mở.

**Nguồn điểm giao hàng:** Đã triển khai GPS có lỗi/thử lại, chọn điểm từ địa chỉ đã lưu và lựa chọn nhập tọa độ nâng cao. Đổi điểm hoặc nội dung địa chỉ xóa xác nhận; chọn điểm không thay nội dung địa chỉ nhận hàng. Regression đã tái hiện lỗi tải saved-address trả muộn hiện thông báo trên nguồn mới; generation guard loại kết quả/lỗi cũ và callback sau unmount. 8 address tests pass; toàn bộ web hiện 79 files / 225 tests pass, typecheck và focused eslint pass. Chưa ghi địa chỉ thật. Browser địa chỉ, zoom125%, UI/nghiệp vụ Buyer còn lại, build hiện tại và full backend gate vẫn mở; chưa áp dụng sửa dữ liệu thông báo.

**Xác nhận điểm giao hàng:** Đã lấy frame Address Create/Edit327:4086 + screenshot trước đổi UI. Địa chỉ mới cần xác nhận điểm; thay addressText hoặc tọa độ thủ công xóa xác nhận, đổi nguồn làm key không còn khớp. Edit không đổi điểm có thể giữ xác nhận điểm đã lưu. Dùng lại note/switch styles, nêu rõ địa chỉ chữ không tự xác định điểm. Regression fail trước sửa; 5 address tests/typecheck/lint pass. Chưa ghi địa chỉ thật; GPS/chọn lại điểm/nhập nâng cao/browser và Task4 vẫn mở.

**Discovery runtime thật:** Người dùng duyệt bật Redis/MinIO/API8081. GET thực tế: 45 item unique/3pages; Ăn uống10; giá50k–100k8; pickup30 Product/2pages; canServe45; Service15. 3 Chrome E2E GET-only trên 3 laptop sizes pass cho danh mục cha, giá/apply/reset, paging45 không trùng, keyword có dấu, Back/Forward/reload. Browser phát hiện BFCache giữ input cũ; header hiện controlled draft và đồng bộ query bằng pageshow/popstate. 13 unit tests liên quan/typecheck/lint pass. DB vẫn26orders/5needs/24legacy notifications, chưa tạo bảng backup hoặc sửa notification. Zoom125% và toàn bộ UI Buyer vẫn mở.

**Địa chỉ thiếu tọa độ:** Test tái hiện saved-address thiếu lat/lng rơi qua Number(null), gửi 0,0 đã fail trước sửa. Form hiện đánh dấu input rỗng/thiếu là không hợp lệ và chặn submit. 4 address tests, typecheck và focused lint pass. Task 4 còn xác nhận pin/đổi addressText/GPS và UI. Database hiện Flyway014; chưa bật API thật vì quyền hiện có mới là PostgreSQL/read-only và backend có job tự động.

**Browser panel lọc:** Đã sửa focus khi mở, giữ Tab/Shift+Tab, Escape trả focus và phục hồi scroll. Ảnh phát hiện Apply nằm dưới vùng nhìn thấy; đã tách footer cố định và body cuộn riêng. 5 Playwright tests trên Chrome có sẵn pass tại 1280×720,1366×768,1536×864,360×800,390×844 với API fixture intercept riêng, không ghi DB. Ảnh `.local/buyer-ui-audit/filter-*.png` đã xem; 13 unit tests liên quan, typecheck/lint pass. Chưa đạt nghiệm thu toàn bộ frame/real API/zoom browser 125%; không tải browser hoặc ffmpeg.

**Điều khiển lọc:** Đã lấy context + screenshot frame FilterSheet `295:1616` qua skill figma-design-to-code trước sửa UI; tái dùng controls/icons hiện có. Input giá là phần mở rộng dùng contract API ngoài frame này. Giá từ/đến, checkbox Giao tận nơi/Nhận tại điểm bán và Đặt lại đã nối API; khoảng ngược/giá âm/không nguyên chặn Apply, mở lại giữ filter, reset giữ keyword. Regression mới fail trước sửa; 3 files / 12 tests, typecheck và focused lint pass. Chưa nghiệm thu hình ảnh/browser hoặc keyboard modal, Task 5 và rà UI vẫn mở.

**Plumbing bộ lọc:** minPrice/maxPrice/pickup/canServe/shipping được giữ trong route URL, form header laptop, request trang đầu/trang cursor và key reset khi navigation. Giá rỗng/không hợp lệ không bị đổi thành 0; minPrice=0 hợp lệ được giữ. Hai regression fail trước sửa, sau sửa 3 files / 11 tests và focused lint pass; source typecheck pass. Chưa khôi phục điều khiển Giá/Giao nhận theo frame Figma hoặc kiểm chứng browser, Task 5 vẫn mở.

**Danh mục cha/con và inventory:** Regression PostgreSQL fail trước sửa; query hiện lấy các descendant ACTIVE/non-deleted bằng recursive UNION. Toàn bộ 12 CatalogDiscoveryRepositoryIntegrationTest pass, gồm nested children, loại nhánh khác/deleted child và các filter/type/resale/cursor hiện có. Không đổi dữ liệu catalog đã phục hồi. Inventory nguồn đủ 26 route Buyer/Public/Auth tại `2026-10-07-buyer-ui-audit-inventory.md`; frame lịch sử chỉ là tham chiếu, các kiểm tra runtime/visual vẫn Pending.

**Chẩn đoán Search bổ sung:** Database có 0 item trực tiếp trong food-drink (Ăn uống), 5 prepared-meals + 5 bakery-drinks; repository chỉ lọc primary_category_id bằng categoryId, không lấy cây con. Cần test/sửa query theo contract. Kết luận trước rằng Giá/Giao nhận thiếu contract backend chưa chính xác: CatalogDiscoveryQuery/repository có minPrice/maxPrice/pickup/canServe/shipping. Cần đối chiếu OpenAPI/client và triển khai bộ lọc hoạt động; việc bỏ nút chưa phải trạng thái nghiệm thu cuối cùng.

**Build web:** Production build pass sau sửa đồng bộ URL Search (Next.js 16.3.3; 35 static pages, các route được liệt kê đều compile). Trước build không có listener port 3000. Đây chưa phải bằng chứng runtime/UI acceptance. Duyệt ghi database Thông báo vẫn đang chờ.

**URL tìm kiếm:** Test tái hiện việc initial filters đổi nhưng keyword/results vẫn cũ đã fail trước sửa. State tìm kiếm hiện được reset theo các filter URL chuẩn hóa, hủy request cũ khi unmount. 13 tests Home/Search/Header, typecheck và focused eslint pass. Còn kiểm tra Back/Forward trên browser thật.

**Preflight Thông báo:** Đã tạo `infra/scripts/repair-demo-notifications-20261006.sql`, cố định 24 ID và kiểm tra owner/order/payload/status, mặc định ROLLBACK; apply tạo bảng backup và chỉ đổi type/payload; restore từ chối nếu hai trường đã đổi sau sửa. 4 tests SQL trên PostgreSQL riêng pass; 2 tests NotificationRepository pass, gồm cursor/enum/deep-link/quyền đọc. Người dùng duyệt khởi động PostgreSQL và kiểm tra chỉ đọc: preflight live pass, kết thúc ROLLBACK. Mapping: 18 ORDER_COMPLETED, 1 ORDER_NEW, 1 ORDER_CONFIRMED, 3 ORDER_FULFILLMENT_UPDATED, 1 ORDER_CANCELLED. Chưa chạy apply; cần duyệt riêng trước khi ghi database.

**Thông báo seed:** Đã kiểm tra branch/trạng thái worktree nguồn (clean, codex/demo-seed-regeneration), sửa generator theo trạng thái đơn và thêm payload status/deepLink. Test mới fail trước sửa; toàn bộ 4 generator tests pass sau sửa và tái sinh gzip. Bản gzip gốc được giữ tại `.local/demo-seed-regeneration/.local/seed-before-notification-fix-20261007.sql.gz`. Container ứng dụng đang dừng; chưa đối chiếu live hoặc cập nhật database. Task 2 chưa hoàn tất, còn preflight, backup/rollback và duyệt mutation.

**Checkpoint mới nhất:** Header laptop lấy từ khóa từ URL và giữ các filter được hỗ trợ. Search có tải thêm cursor, giữ kết quả khi lỗi và retry, bỏ phản hồi cũ và khử item trùng; bộ lọc có select danh mục thực và mở lại với giá trị đã áp dụng. Đã bỏ nút lọc Giá/Giao nhận chưa có contract API; chưa triển khai hai tính năng này. Toàn bộ web suite: 78 files / 216 tests pass. Sau chỉnh type guard cho trang API tùy chọn: 12 tests home/search/header, typecheck và focused eslint đều pass. Chưa kiểm chứng build/browser, toàn bộ UI Buyer hoặc chuyển đổi dữ liệu thông báo.

**Bổ sung checkpoint:** `NeedResponsePaginationIntegrationTest` hiện có 3 test PostgreSQL thật pass, gồm dữ liệu populated với createdAt trùng nhau, thứ tự UUID, chuyển sang timestamp cũ hơn, loại phản hồi WITHDRAWN/need khác và trang cuối rỗng. Task 1 vẫn cần kiểm tra service quyền/cursor và runtime. Task 5 đã sửa link Xem tất cả giữ type/listingKind và DISTANCE; Explore có vị trí tải danh sách mặc định không cần keyword. Test regression đã fail trước sửa; 2 file home/search / 9 tests pass sau sửa, web typecheck và lint 4 file liên quan pass. Chưa hoàn tất filters/pagination/header query hoặc rà UI toàn bộ.

- Task 1: thêm `NeedResponsePaginationIntegrationTest`. Trước sửa, cả 2 test PostgreSQL thật lỗi 42P18; sau tách query trang đầu/trang cursor, 2 test pass (0 lỗi, không skip). Chưa hoàn tất Task 1: còn test dữ liệu populated, tie-break, quyền/cursor và kiểm tra runtime.
- Task 3: sửa NotificationInbox/NeedResponseList để lỗi tải không đồng thời hiển thị empty/all-read; thêm retry; search thiếu vị trí không hiện skeleton vô hạn. Các test mới đã fail đúng lỗi trước sửa; sau sửa 4 file / 10 tests liên quan pass.
- Web typecheck pass; eslint trên 6 file UI/test liên quan pass. Chưa chạy toàn bộ web tests/build hoặc toàn bộ API verify.
- Docker đã được người dùng mở. Người dùng duyệt tải dependency kiểm thử Maven vào `E:\Tools\Maven\repository`; backend test dùng database Testcontainers riêng, không ghi vào `covie_demo_recovered` hoặc `covie`.
- Chưa sửa dữ liệu thông báo, chưa tạo đơn/địa chỉ/nhu cầu demo, chưa triển khai production. Task 2 và Tasks 4–10 vẫn mở; rà UI toàn bộ Buyer chưa thực hiện.

---

## 1. Quy tắc triển khai và bảo toàn dữ liệu

- Đọc `AGENTS.md`, `apps/web/AGENTS.md` và hướng dẫn tại thư mục liên quan trước khi sửa. Trước khi dùng API Next.js, đọc tài liệu đúng phiên bản trong dependency local.
- Kiểm tra Git diff trước mỗi đợt; workspace có nhiều thay đổi có sẵn. Không reset, checkout đè hoặc gom thay đổi không thuộc nhiệm vụ.
- Không tự seed/reset database, xóa volume, pull image, nâng dependency, chạy restore hoặc sửa cấu hình hệ thống.
- Chỉ dùng tài khoản demo và dữ liệu thử nghiệm đã xác định. Trước thao tác ghi dữ liệu, trình tên database, bảng, số bản ghi, ảnh hưởng và rollback để duyệt.
- Database ứng dụng đang dùng: `covie_demo_recovered`; database `covie`, volume gốc và các bản sao lưu phải được giữ.
- Không dùng bộ seed UI hiện có trước khi kiểm tra phạm vi mutation. Các script E2E có thể thay đổi giỏ, đơn, nhu cầu hoặc cài đặt.
- Không đưa mật khẩu/token/cookie vào báo cáo, ảnh bằng chứng hoặc Git.
- Chưa commit, push hay triển khai nếu chưa được yêu cầu. Sau mỗi phần, rà lại diff để tránh lẫn thay đổi trước đó.
- Nếu cần file bằng chứng, mô tả nơi lưu và dung lượng dự kiến trước khi tạo theo yêu cầu bảo toàn dữ liệu của phiên này.

## 2. Bằng chứng và vấn đề đầu vào

| ID | Ưu tiên | Vấn đề | Bằng chứng/phạm vi |
| --- | --- | --- | --- |
| BUY-F01 | P0 | API phản hồi Cần mua trả 500 | PostgreSQL SQLState 42P18: không xác định kiểu tham số `$3`, điều kiện cursor `:afterCreatedAt is null`. Tái hiện cả không cursor và có cursor. Nhu cầu `1cac4c69-34d5-5e41-9ad0-7a9aaac9ef14` có 2 phản hồi ACTIVE. |
| BUY-F02 | P0 | API Thông báo trả 500 | 24 bản ghi demo có `ORDER_STATUS_CHANGED`; enum API không có giá trị này. Bộ sinh seed cũng tạo giá trị không tương thích. |
| BUY-F03 | P1 | UI lỗi nhưng hiển thị trạng thái rỗng/thành công | Cần mua báo lỗi rồi hiện chưa có phản hồi; Thông báo báo lỗi nhưng hiện đã đọc tất cả/chưa có thông báo. |
| BUY-F04 | P1 | Search tải vô hạn khi thiếu vị trí | Có từ khóa, loading bật nhưng effect thoát sớm do thiếu discoveryLocation. |
| BUY-F05 | P1 | Địa chỉ mới có nguy cơ dùng sai tọa độ | AddressForm lấy tọa độ từ ngữ cảnh khám phá/địa chỉ đang chọn mà chưa xác nhận khớp địa chỉ nhập mới. Đây là rủi ro xác nhận qua mã; chưa tạo bản ghi lỗi để thử. |
| BUY-F06 | P1 | Chọn vị trí thủ công khó dùng | Luồng khách yêu cầu vĩ độ/kinh độ, chứa diễn giải backend trong giao diện. |
| BUY-F07 | P1 | Xem tất cả không giữ ngữ cảnh | Các nhóm Dịch vụ/Thanh lý dẫn tới `/search` không lọc; trang tìm kiếm không intent chỉ có hướng dẫn. |
| BUY-F08 | P1 | Bộ lọc thiếu chức năng tương ứng nhãn | Danh mục/Giá/Giao nhận mở bảng chung không có các lựa chọn tương ứng. |
| BUY-F09 | P2 | Từ khóa mất trên thanh header | Tìm “cơm” có kết quả nhưng input header không hiển thị query hiện tại. |
| BUY-F10 | P1 | Google OAuth local chưa sẵn sàng | API chạy với client/secret tạm; UI vẫn cung cấp đăng nhập, đăng ký, quên mật khẩu qua Google. |
| BUY-I01 | Chẩn đoán | Danh mục Ăn uống trả rỗng | Tìm từ khóa vẫn có sản phẩm; cần đối chiếu gán danh mục và lọc cha/con trước khi kết luận lỗi. |
| BUY-I02 | Chẩn đoán | Đơn demo pickup có phí giao | Cần phân biệt tính nhất quán dữ liệu seed với lỗi tính phí trên đơn mới. |
| BUY-I03 | Chẩn đoán | Uy tín chưa có dữ liệu | Không suy luận lỗi chỉ từ lịch sử có đơn hoàn thành; kiểm tra quy tắc projection và seed. |
| BUY-I04 | Chẩn đoán | Search giới hạn 20 nhưng chưa thấy tải tiếp | Kiểm tra API nextCursor và UI trước khi quyết định triển khai phân trang. |

Đã kiểm tra bước đầu ở 1366×768: đăng nhập demo, hồ sơ, địa chỉ đã lưu, tìm kiếm có vị trí, danh sách/chi tiết đơn, giỏ và phiên đăng nhập tải được. Preview pickup có tổng 257.000 ₫, phí 0 ₫. Đây không phải xác nhận mọi mutation hoặc toàn bộ nghiệp vụ pass.

## 3. Thứ tự và checkpoint

1. Sửa truy vấn phản hồi và lập phương án tương thích thông báo demo.
2. Sửa trạng thái loading/error/empty và retry.
3. Sửa vị trí/địa chỉ.
4. Hoàn thiện tìm kiếm, bộ lọc và điều hướng.
5. Hoàn thiện trạng thái xác thực local.
6. **Rà soát và chỉnh UI toàn bộ Buyer — hạng mục độc lập.**
7. Kiểm thử nghiệp vụ Buyer với mutation được duyệt.
8. Kiểm thử hồi quy, kiểm tra laptop và cập nhật hồ sơ.

Mỗi task: viết test tái hiện nếu là lỗi hành vi, xác nhận test fail đúng lý do, sửa tối thiểu, chạy test liên quan, kiểm tra trên runtime, rà diff. Không xây lại kiến trúc hoặc mở rộng feature ngoài phạm vi để xử lý một lỗi.

## Chunk 1: API và dữ liệu demo

### Task 1 — Phân trang phản hồi Cần mua (BUY-F01)

**Files:**
- Modify: `apps/api/src/main/java/vn/covie/needs/persistence/NeedResponseRepository.java`.
- Modify: `apps/api/src/main/java/vn/covie/needs/application/NeedResponseReadService.java`.
- Test: `apps/api/src/test/java/vn/covie/needs/persistence/NeedPersistenceIntegrationTest.java` và test controller/read service liên quan.

- [ ] Tạo regression trên PostgreSQL thật cho trang đầu không cursor, chứng minh lỗi 42P18 trước sửa.
- [ ] Thêm regression có cursor, createdAt trùng nhau, thứ tự UUID, limit/nextCursor, trang rỗng, cursor sai và buyer không sở hữu need.
- [ ] Tách query trang đầu không có predicate cursor khỏi query trang sau dùng các tham số đã có kiểu. Giữ thứ tự `createdAt desc, id desc`, không bỏ bảo vệ quyền hoặc lọc ACTIVE.
- [x] Kiểm tra phản hồi API và item DTO để không xuất hiện lỗi tiếp theo sau khi query chạy. API thật trả2 phản hồi/4 item có tên, giá hiện tại/snapshot, available/actionable; browser đọc được trên5 kích thước (2026-10-07).
- [ ] Chạy test liên quan; tái hiện runtime nhu cầu demo có 2 phản hồi, không sửa dữ liệu để làm test pass.

**Acceptance:** Trang đầu/trang sau trả 200, đủ phản hồi, không trùng/bỏ sót; truy cập trái quyền và cursor sai trả lỗi đúng contract.

### Task 2 — Thông báo demo không tương thích (BUY-F02)

**Files:**
- Inspect: `apps/api/src/main/java/vn/covie/notifications/model/NotificationType.java`, repository và controller/service đọc inbox.
- Inspect/Modify sau khi xác định nguồn chính thức: `.local/demo-seed-regeneration/infra/scripts/generate-demo-seed.py` và tests của bộ sinh seed. Đây là worktree đăng ký riêng; không tự sửa song song nếu chưa kiểm tra branch và trạng thái.
- Proposed Create: `infra/scripts/repair-demo-notifications-20261006.sql` (dry-run/preflight và transaction, không tự chạy).
- Test: `apps/api/src/test/java/vn/covie/notifications/persistence/NotificationRepositoryIntegrationTest.java` và test inbox/deep-link liên quan.

- [ ] Đếm bản ghi theo database/user/type; đối chiếu đơn, trạng thái và payload để chọn mapping đúng ý nghĩa.
- [ ] Xác định dữ liệu cũ hay thiếu contract; không thêm enum legacy hoặc đổi tất cả sang ORDER_COMPLETED để né lỗi.
- [ ] Sửa nguồn sinh seed chính thức để chỉ tạo NotificationType được API hỗ trợ; thêm test kiểm tra tập enum và payload deep-link.
- [ ] Chuẩn bị mapping, danh sách ID, số bản ghi dự kiến, backup/rollback và điều kiện abort nếu dữ liệu khác preflight.
- [ ] Trình duyệt riêng thao tác ghi database trước khi chạy. Nếu không đủ thông tin để mapping đúng, báo rõ và giữ dữ liệu.
- [ ] Sau cập nhật được duyệt: kiểm tra inbox, cursor, quyền, unread và deep-link; kiểm tra tổng bản ghi được giữ.

**Acceptance:** Inbox không còn enum error; mỗi thông báo giữ ý nghĩa và đích điều hướng; seed không tái tạo lỗi; rollback cụ thể và được kiểm chứng.

## Chunk 2: Trạng thái UI, vị trí, tìm kiếm, xác thực

### Task 3 — Loading/error/empty/retry (BUY-F03, BUY-F04)

**Files:** `apps/web/features/needs/need-response-list.tsx`, `apps/web/features/notifications/notification-inbox.tsx`, `apps/web/features/discovery/search-results.tsx`; tests tương ứng trong cấu trúc tests hiện có.

- [ ] Test API fail không hiển thị empty/all-read; retry thành công khôi phục dữ liệu.
- [ ] Test search có query nhưng thiếu vị trí không bật skeleton vô hạn.
- [ ] Tách trạng thái lỗi, rỗng, tải, dữ liệu và thiếu vị trí; giữ dữ liệu cũ hợp lý khi refresh thất bại.
- [ ] Thêm nút thử lại, thông báo tiếng Việt dễ hiểu, không lộ thông tin backend.
- [ ] Kiểm tra keyboard/focus và chuyển trạng thái khi request hoàn tất, đổi route hoặc unmount.

**Acceptance:** Không trình bày lỗi mạng/API như “không có dữ liệu”; retry hoạt động, thiếu vị trí có CTA rõ.

### Task 4 — Vị trí khám phá và địa chỉ giao hàng (BUY-F05, BUY-F06)

**Files:** `apps/web/features/location/location-chooser.tsx`, `location-context.ts`, `browse-location-provider.tsx`, `apps/web/features/profile/address-form.tsx`, `apps/web/tests/profile/address-form.test.tsx`; API/address contract nếu thực sự cần thay đổi.

- [ ] Kiểm tra contract vị trí hiện tại trước khi chọn giải pháp.
- [ ] Test địa chỉ mới không tự kế thừa tọa độ địa chỉ cũ mà không xác nhận; kiểm tra invalid/missing coordinates và GPS bị từ chối.
- [ ] Tạo bước xác nhận điểm giao hàng rõ ràng. Không diễn giải chuỗi địa chỉ thành tọa độ giả; không dùng `Number(null)` làm vị trí 0,0.
- [ ] Ưu tiên địa chỉ đã lưu/GPS; đưa nhập tọa độ vào lựa chọn nâng cao và bỏ diễn giải kỹ thuật không cần thiết.
- [ ] Khi đổi addressText, bảo đảm pin cũ được xác nhận lại hoặc đánh dấu cần xác minh.
- [ ] Nếu cần geocoding/bản đồ: trình provider, license/cost, dữ liệu gửi đi và cấu hình trước khi tích hợp; không coi phần này hoàn thành bằng một input địa chỉ thuần văn bản.

**Acceptance:** Người mua biết vị trí nào sẽ được dùng để giao hàng; địa chỉ mới có pin được xác nhận; khám phá và nhận hàng không âm thầm thay thế nhau.

### Task 5 — Search/filter/navigation (BUY-F07, BUY-F08, BUY-F09, BUY-I01, BUY-I04)

**Files:** `apps/web/features/home/home-screen.tsx`, `apps/web/features/discovery/search-results.tsx` và CSS modules; header trong `apps/web/components/layout/`; `apps/web/features/catalog/catalog-api.ts`; route `/search` và tests liên quan.

- [x] Xác minh danh mục cha/con và dữ liệu demo để giải thích Ăn uống rỗng; không thay dữ liệu trước khi chứng minh. Bằng chứng: 10 item ở hai danh mục con, query cũ chỉ exact-match; regression PostgreSQL và suite 12 tests pass sau sửa.
- [ ] Giữ filter type SERVICE/listingKind RESALE theo từng Xem tất cả; search có vị trí và không query vẫn có danh sách mặc định.
- [ ] Đồng bộ input header/query URL, Back/Forward/reload; giữ filter khi thay từ khóa.
- [ ] Danh mục có lựa chọn thực; nút mở đúng panel và draft filter đồng bộ giá trị hiện tại khi mở.
- [ ] Đối chiếu API cho Giá/Giao nhận. Có contract thì triển khai end-to-end; thiếu contract thì lập thay đổi API riêng hoặc bỏ nút chưa hoạt động với trạng thái thiếu được ghi rõ. Sắp xếp giá không thay thế lọc khoảng giá.
- [ ] Kiểm tra nextCursor; nếu có kết quả tiếp thì thêm tải tiếp/reset cursor khi filter đổi, không bỏ sót hoặc trộn trang cũ.
- [ ] Test kết quả, empty/error, chọn/bỏ filter, sort, danh mục con, query có dấu, load-more và navigation.

**Acceptance:** Mỗi CTA đúng nhãn/ngữ cảnh; query và filter nhất quán với kết quả; không giới hạn âm thầm ở 20 mục.

### Task 6 — Xác thực local (BUY-F10)

**Files:** `apps/web/features/auth/google-login-button.tsx`, `apps/web/app/(auth)/login/page.tsx`, register/onboarding/reset-password pages; auth server config/capability nếu cần.

- [x] Phân biệt Google configured/unconfigured qua cấu hình server an toàn, không đưa secret ra client. Public GET chỉ trả boolean; backend security/unit/OpenAPI tests và live GET configured:false đã kiểm chứng, không phải bằng chứng Google callback thật.
- [x] UI giải thích trạng thái chưa cấu hình; không đưa người dùng vào liên kết chắc chắn thất bại. Provider fail-closed và auth4routes×5viewports đã kiểm chứng; cấu hình/error/retry dùng browser fixtures, Google thật vẫn mở.
- [ ] Test email login, lỗi đăng nhập, return-to an toàn chống open redirect và session hết hạn.
- [ ] Google registration/reset/onboarding chỉ kiểm chứng sau khi người dùng cung cấp cấu hình hợp lệ qua kênh an toàn; giữ checkpoint blocked rõ ràng nếu thiếu.

**Acceptance:** Phương thức khả dụng hiển thị đúng; login quay lại đúng trang dự định; không coi OAuth tạm là luồng đã pass.

## Chunk 3: Hạng mục riêng — Rà soát và chỉnh UI toàn bộ Buyer

### Task 7 — Inventory và đối chiếu thiết kế

**Files:** `apps/web/app/page.tsx`, route groups `(public)`, `(buyer)`, `(auth)`; các feature components/CSS và consumer layout được từng route sử dụng.

- [x] Lập danh sách từ toàn bộ `page.tsx`, gồm route động và các trạng thái; cập nhật khi phát hiện route thiếu. Inventory 26 route: `2026-10-07-buyer-ui-audit-inventory.md`; đây là danh sách cần kiểm tra, chưa phải nghiệm thu UI.
- [ ] Đọc handoff/design acceptance catalog hiện có. Khi lấy Figma, dùng skill Figma bắt buộc và đúng frame production của route; không dùng page overview như bằng chứng khớp thiết kế.
- [ ] Với mỗi route ghi: dữ liệu/identity, vấn đề, severity, frame/reference, proposed change, kiểm tra sau sửa và limitation.

| Nhóm | Trang phải có trong inventory |
| --- | --- |
| Khám phá | Trang chủ, explore, search, chi tiết sản phẩm/dịch vụ, cửa hàng |
| Mua hàng | Danh sách/chi tiết giỏ, checkout, success, đơn hàng, chi tiết đơn, review |
| Cần mua | Danh sách, tạo nhu cầu, chi tiết và phản hồi |
| Tài khoản | Hồ sơ, thông báo, uy tín, địa chỉ list/new/edit, phiên đăng nhập |
| Xác thực | Login, register, onboarding password, reset password |

### Task 8 — Chỉnh UI theo nhóm trang

- [ ] Kiểm tra và sửa bố cục/container/grid, hierarchy tiêu đề, font/cỡ chữ, spacing, ảnh, tiền tệ, thời gian và trạng thái tiếng Việt.
- [ ] Kiểm tra nút/link/icon/label, disabled/pending, click target, nội dung kỹ thuật thừa và thao tác bị lặp. Ví dụ form Cần mua đang có cả nút chọn loại và select loại.
- [ ] Kiểm tra modal/drawer: focus, Escape, đóng/mở, cuộn, footer thao tác, không bị header che; dùng label đúng nghĩa cho radio/checkbox.
- [ ] Kiểm tra header/query/active navigation, lối quay lại hợp lý khi mở trực tiếp deep-link.
- [ ] Kiểm tra card sản phẩm, biến thể, gallery, empty/error/loading, trạng thái đã review và link xem lại đánh giá.
- [ ] Đối chiếu Figma, giữ responsive mobile; thay đổi lớn khác thiết kế được trình trước khi sửa.
- [ ] Mỗi nhóm có kiểm tra lại trực quan sau sửa, không dùng snapshot test đơn thuần để chứng minh usability.

**Viewports:** 1366×768, 1280×720, 1536×864; zoom trình duyệt 125%; thêm breakpoint mobile liên quan để tránh regression.

**Acceptance:** Mọi route trong inventory có kết quả kiểm tra; không tràn ngang, cắt chữ, che nút, modal vượt màn hình hoặc mất focus; control có hành vi đúng nhãn. Bằng chứng ghi rõ viewport và phiên bản, thay đổi chưa đạt được liệt kê riêng.

## Chunk 4: Nghiệp vụ và kiểm chứng cuối

### Task 9 — Kiểm thử Buyer end-to-end

**Files:** `apps/web/e2e/buyer-flow.local.spec.ts`, fixture/catalog hiện có; test backend của checkout/orders/needs/reviews khi phát hiện lỗi mới.

- [ ] Trước chạy, trình account/database và các mutation dự kiến; dùng dữ liệu test tách biệt, không reset bộ demo phục hồi.
- [ ] Product/service: media, variant, số lượng, khả dụng, shop, add cart/buy now.
- [ ] Cart: tăng/giảm/xóa, item mất khả dụng, giá đổi, nhiều merchant, product/service tách giỏ.
- [ ] Checkout: pickup/delivery, saved address, coverage, phí/tổng, preview/commit, conflict và duplicate-submit.
- [ ] Order: lifecycle, authorized actions, historical snapshot, review/create/view và lỗi.
- [ ] Needs: tạo, response, chọn item, need-sourced checkout, hủy/hết hạn.
- [ ] Account: address create/edit, notification read/deep-link, reputation, sessions/revoke theo scope duyệt.
- [ ] Đối chiếu BUY-I02/I03: demo pickup fee và reputation projection; phân loại lỗi dữ liệu/logic/missing seed trước khi chỉnh.

**Acceptance:** Các hành trình đã chạy có assertion nghiệp vụ; bản ghi tạo ra được nhận diện và chỉ dọn theo phạm vi được duyệt. Không đánh dấu mutation pass khi chỉ xem màn hình.

### Task 10 — Gates và hồ sơ hoàn thành

Chạy test tập trung sau mỗi task; sau đợt sửa hoàn chỉnh mới chạy gates rộng. Không tự chạy fixture có mutation chưa duyệt.

**Lệnh frontend** (cwd `E:\WebApp\CoVie\covie`, xác minh tên script bằng package.json trước chạy):

```powershell
node E:\Tools\pnpm\10.34.5\bin\pnpm.cjs --filter @covie/web exec vitest run
node E:\Tools\pnpm\10.34.5\bin\pnpm.cjs web:typecheck
node E:\Tools\pnpm\10.34.5\bin\pnpm.cjs web:lint
node E:\Tools\pnpm\10.34.5\bin\pnpm.cjs --filter @covie/web build
git diff --check
```

**Backend:** cwd `E:\WebApp\CoVie\covie\apps\api`, dùng Java `E:\Tools\Java\jdk-25` và Maven cache trên E theo launch script đã có; không dùng Java/PATH mặc định chưa kiểm tra.

```powershell
.\mvnw.cmd -Dtest=NeedPersistenceIntegrationTest,NotificationRepositoryIntegrationTest test
.\mvnw.cmd clean verify
```

Lệnh trên là gates dự kiến, không phải bằng chứng đã chạy. Tên test phải cập nhật nếu task tạo test chuyên biệt. Integration tests cần Docker và image sẵn có; nếu cần tải mới, trình phạm vi trước tải. Production build có thể ảnh hưởng dev dùng chung `.next`; kiểm tra cách cô lập trước chạy.

- [ ] Xác nhận scripts/gates từ package.json; chạy API-client check nếu contract thay đổi và regenerate client đúng workflow.
- [ ] Chạy test có PostgreSQL thật cho query/cursor; test trạng thái UI và Playwright Buyer trong scope dữ liệu được duyệt.
- [ ] Chạy kiểm tra responsive/zoom/keyboard; giữ bằng chứng ảnh khi được duyệt nơi lưu.
- [ ] Ghi kết quả từng vấn đề/route: PASS, FAIL hoặc BLOCKED kèm lý do. OAuth chưa cấu hình không được gộp vào PASS.
- [ ] Cập nhật `AGENTS.md`, hồ sơ trạng thái và hướng dẫn local khi có thay đổi scope/runtime/verification; không ghi test cũ thành bằng chứng mới.
- [ ] Rà diff và công bố hạn chế, migration/data scripts chưa chạy, vấn đề còn mở.

## 4. Điều kiện hoàn thành toàn kế hoạch

- [ ] Hai API ưu tiên đọc được dữ liệu với quyền và cursor đúng.
- [ ] Loading/error/empty/retry không gây hiểu nhầm.
- [ ] Địa chỉ nhận hàng có vị trí xác nhận riêng; luồng khám phá dễ hiểu.
- [ ] Search/filter/navigation có chức năng thật, trạng thái nhất quán và pagination được xử lý.
- [ ] Mọi route Buyer/Public/Auth trong inventory được rà UI trên laptop; regression mobile được kiểm tra.
- [ ] Các mutation nghiệp vụ đã được duyệt, chạy và kiểm chứng; phần thiếu access/config được ghi riêng.
- [ ] Gates liên quan pass, không phá thay đổi có sẵn, không mất dữ liệu/backup.

**Next action:** Sau khi được duyệt triển khai, bắt đầu Task 1: test tái hiện trên PostgreSQL và sửa query phản hồi Cần mua.

**Required from you:** Duyệt triển khai mã; cập nhật database demo, tích hợp dịch vụ vị trí/OAuth và mutation thử nghiệm cần scope riêng nếu chưa được duyệt.

**Completion condition:** Mỗi vấn đề đã bàn và mỗi route Buyer có trạng thái cùng bằng chứng thực tế, UI và nghiệp vụ đạt các điều kiện trên hoặc có blocker được ghi rõ.
