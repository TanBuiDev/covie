# Báo cáo tiến độ và phạm vi BE Covie — 12/10/2026

Báo cáo này chốt trạng thái hiện tại và thay thế cách điều hành theo hướng mở rộng kiểm thử liên tục. Không triển khai thêm chức năng, sửa nghiệp vụ hoặc chạy thêm test trong lượt lập báo cáo.

## 1. Trạng thái công việc và kiểm thử

- Không còn tiến trình Maven/Java kiểm thử đang chạy tại thời điểm kiểm tra bằng Win32_Process. Hai session kiểm thử cuối đã trả exit0; không có tác vụ cần terminate.
- Full BE gần nhất: **905 test, 0 failure/error, 4 opt-in skip**, Maven verify/package BUILD SUCCESS, 03:43, kết thúc 22:16:13+07 ngày 12/10. Log: `.local/backend-checkout-chains-full-20261012.log`, session2338exit0.
- Sau full gate trên chỉ mở rộng kiểm thử Orders: completion/cancellation và receive/mark-delivered, mỗi tình huống lặp3 lần. Bộ liên quan **14 test, 0 failure/error/skip**, 31.487s, kết thúc23:24:03+07; `.local/order-complete-delivered-race-final.log`, session56009exit0. Full905 chưa bao gồm hai mở rộng này. Không cần chạy lại full chỉ để cập nhật số test.
- Bốn skip của full gate là SSR browser2, HotQuery1, Pilot1. Có bằng chứng focused/opt-in lịch sử riêng, nhưng không coi skip là pass.
- Working tree HEAD `45b895d`, khác remote main đã nâng dependency. CI xanh trên remote không kiểm chứng toàn bộ các thay đổi local đang chưa commit. Không reset/merge/push toàn bộ working tree trong lượt báo cáo.
- Chưa có VPS/domain. Kiểm thử chủ yếu dùng PostgreSQL/Redis/MinIO riêng; không chứng minh production ingress, live Google, rendered Grafana hoặc lưu lượng thực.

## 2. Những module đã rà soát và giới hạn

| Nhóm | Đã kiểm chứng trong phạm vi cụ thể | Chưa kiểm chứng / điểm dừng hiện tại |
|---|---|---|
| Auth/session/security | JWT/session ownership, revoke, inactive user denial, CSRF; SSR bridge và Redis phối hợp refresh có test riêng | Live Google, HTTPS/proxy/cookie production. Giữ TTL15m/30d và reuse policy hiện có |
| User/profile/address/location | Omission/null, avatar ownership, address IDOR/default; hierarchy HTTP/SQL; geocoder failure/fallback | Provider thật và môi trường triển khai; không thay hành vi địa chỉ đang đúng |
| Merchant/location/catalog | Membership/STAFF assignment/revocation; create/edit JWT, required attributes, category hierarchy/detail, visibility | Chỉ đối chiếu caller FE chưa có bằng chứng; không kiểm thử mọi tổ hợp thuộc tính giả định |
| Media/storage | Upload/complete/ownership, actual object-storage test; quota và cleanup/completion locking | Browser CORS và public object delivery trong deployment |
| Discovery/geography/shipping | SQL/cursor/rank; synthetic pilot các sort/coverage; shipping snapshots | Load thực, provider/ingress; EXPLAIN synthetic không phải benchmark production |
| Cart/checkout/inventory | Ownership, idempotency, rollback, last-unit race; pickup/Service/delivery checkout tới completion/dispatch; tracked reservation/SALE | Không cần bổ sung hàng loạt combination khi caller FE và invariant đã được chứng minh |
| Orders/fulfillment/issues | JWT actions, reason/status, cancellation/no-show/issue timeout/resolution; inventory/event invariants;2HTTP race mới | Không xem thiếu HTTP cho mọi race là lỗi. Đã có service/PG soak trong performance package, xem mục3 |
| Needs | Buyer response projection/cursor, source checkout, seller eligibility, lifecycle/moderation, matching/audience, rate-limit merchant scope | Chỉ ưu tiên lỗi FE thực tế hoặc contract chưa khớp; không mặc định mở thêm mọi biến thể source |
| Review/reputation | Eligibility, public privacy/cursor, exact replay, disable-preserve-old; projection và targeted/full maintenance | Không suy ra toàn bộ live lifecycle từ fixture; giữ chính sách đã đúng |
| Notifications/outbox/SSE | Owner/payload/markread; transactional rollback/retry/dedupe/maxattempt; real dispatch; authenticated recipient/reconnect | Nginx/EventSource và multi-replica runtime chưa kiểm chứng |
| Ads | Admin/Seller JWT CRUD/role/revocation; proof token/click/impression/cross-campaign/stats | Browser visibility và targeting thật; không sửa Ads vì test intermittent nếu không có defect |
| Admin/moderation/settings/audit | Authorization/privacy, deterministic paging, maintenance/header/target/replay; target locks/bulk; setting precedence/version/dispatch | Chỉ kiểm tra UI caller còn thiếu hoặc bug cụ thể; không mở rộng Admin UI quản lý token |
| Operational/retention/rate-limit | Prod-profile startup với test providers; real Redis/Lua/outage; key/storage guards; lock ownership; backup/restore riêng; metrics/rules verifier | Deployed secrets/TLS/alerts/offsite backup/load là CONFIG_REQUIRED, không tự thay production |
| Barcode | Mã hiện có, V017 trên testDB, public lookup, import5931rows/repeat/rollback | Đây là feature có sẵn trong working tree, không phải lỗi mới do audit tìm ra. Provenance nguồn chưa xác nhận; chưa áp schema/dữ liệu thật |

## 3. Điều chỉnh nhận định tiến độ

Nhận định trước rằng “chỉ có kiểm thử khóa persistence, còn năm/sáu race chưa được chứng minh” quá rộng do tìm kiếm giới hạn trong package Orders. Nguồn hiện tại có `performance/OrderCommandSoakConcurrencyTest`: cancellation accept/reject, buyer completion/seller cancellation, issue/auto-completion, mỗi case100iterations, PostgreSQL thật nhưng mock actor/access. Full905 bao gồm suite này. Hai test JWT/HTTP mới tăng bằng chứng về boundary HTTP/inventory, không chứng minh code trước đó hỏng và không khiến các test service hiện có mất giá trị.

Vì vậy **không tiếp tục tự động thêm buyer-vs-scheduler hoặc các race còn lại**. Chỉ mở lại khi caller FE, bug report hoặc thay đổi source tạo rủi ro cụ thể chưa được bằng chứng hiện có giải quyết.

Số test tăng từ baseline575 tới905 phản ánh nhiều lượt củng cố bằng chứng, không phải330 lỗi sản phẩm. Lỗi fixture, test isolation, compile harness, artifact interference và test expectation sai đã được tách khỏi lỗi production. Việc chạy full liên tục trước đây vượt mức cần thiết; từ đây chỉ chạy lại khi có production/shared/config change có phạm vi ảnh hưởng rộng và lý do được ghi rõ.

## 4. Lỗi thực sự đã phát hiện và bản sửa

Các dòng sau đối chiếu diff hiện tại với log/checkpoint. RED nêu sai lệch thực tế; không dùng tên file chứa “red” làm bằng chứng duy nhất. Các bản sửa Java có mặt trong full905; focused mới không sửa production.

| Lỗi / bằng chứng | Sửa tối thiểu hiện có | Trạng thái |
|---|---|---|
| STAFF đọc inventory ngoài điểm phân công; membership revoke giữa hai lần đọc. Logs `backend-audit-inventory-scope-red.log`, `backend-audit-merchant-race-red.log` | Lọc/kiểm assignment hiện tại trong MerchantAccessPolicy, InventoryQueryService | Focused + full đạt |
| Inventory bulk known failure làm cả request500 UnexpectedRollbackException (`backend-audit-inventory-bulk-red.log`) | Giữ transaction ngoài; known pre-mutation ApiException không đánh rollback-only bên trong | Mixed success/failure/replay/ledger đạt |
| Xóa1 trong giỏ2 item làm giỏ còn1 bị đóng (`backend-audit-cart-remove-red-20261009.log`) | CartMutationService chỉ abandon khi query sau delete isEmpty | HTTP regression đạt |
| Concurrent add vào giỏ gây conflict/mất cộng số lượng (`backend-audit-cart-add-race-red-20261009.log`) | CartService serialize add qua user row lock, giữ active-cart rules | Actual JWT concurrent add đạt |
| NO_SHOW_CONTESTED timeout không đóng; delivery issue timeout không commit stock (`backend-audit-order-deadlines-red.log`, `backend-audit-delivery-timeout-red.log`) | Thêm đúng trạng thái; commit ACTIVE inventory cho Product delivery issue trước close | Status/event/stock/replay đạt |
| Buyer hoàn tất issue nhưng issue vẫn OPEN (`backend-audit-issue-resolution-red.log`) | Resolve open issue trong transaction completion | Issue/status/event đạt |
| Need tạo preapproval target FK chưa thấy row (`backend-audit-need-moderation-lifecycle-red-20261009.log`) | Flush target trước JDBC case writer | Lifecycle/moderation đạt |
| Need Created trả response không đúng DTO/ETag (`backend-audit-need-created-contract-red-20261009.log`) | MeNeedController dùng existing Created contract | Generated/HTTP đạt |
| Merchant/location ngừng bán nhưng preview/response/matching/audience còn cho giao dịch; stale commit422 thay409 | CheckoutTradingPolicy/eligibility/read/SQL audience; commit chỉ map hai lỗi availability thành CHECKOUT_CHANGED409 | Logs `backend-audit-need-merchant-red-20261010.log`, `backend-audit-need-stale-trading-red-20261010.log`; focused + full đạt |
| Need rank cursor double/real rounding gây duplicate | JdbcNeedDiscoveryRepository cast rank thành real theo SQL source | Real cursor regression và full đạt |
| Suspend concurrent có thể mất Admin cuối cùng (`backend-audit-admin-race-red-20261009.log`) | Serialize last-active-admin decision trước target row | Actual PG/Admin acceptance đạt |
| Admin/audit/moderation phân trang timestamp ties thiếu tie-breaker | Thêm id vào sort, giữ filters/DTO | Tied traversal logs `backend-audit-admin-pagination-red-20261010.log`, `backend-audit-admin-audit-moderation-pagination-red-20261010.log`; đạt |
| Admin order route dùng visibility thường; invalid page/filter/error routing sai | Admin-specific access, bounded validation, mapped404/405 | Actual HTTP role/privacy/error đạt; không nới quyền |
| Moderation concurrent targets/review overwrite; bulk lỗi nghiệp vụ rollback batch item hoặc action khác dùng chung replay | Lock target/review; giữ per-item boundary; operation idempotency gồm action | Logs `backend-audit-moderation-target-red-20261009.log`, `backend-audit-review-target-race-red-20261009.log`, `backend-audit-moderation-bulk-red-20261009.log`, `backend-audit-moderation-bulk-action-red-20261009.log`; đạt |
| Concurrent setting override trả500 unique violation (`backend-audit-settings-create-red-20261009.log`) | Chỉ map đúng4 unique constraints/23505 thành409 | Scope/version/actual PG đạt |
| Maintenance reason/header/nonexistent target trả500 | Validate existing422/400/404 ở đúng layer; Spring optional header nhưng OpenAPI required=true | `backend-maintenance-red-20261011.log`, `backend-maintenance-key-red-20261011.log`, `backend-maintenance-target-red-final-20261011.log`; đạt |
| Missing Idempotency-Key Review/Report/6Admin commands trả500 MissingRequestHeaderException | Delegate existing executor validation, preserve OpenAPI required=true | `backend-review-key-red-20261011.log`, `backend-report-key-red-20261011.log`, `backend-moderation-key-red-20261011.log`; đạt |
| Idempotency replay mất nanoseconds; full maintenance clear persistence context làm record IN_PROGRESS | Mapper copy lưu ISO timestamps; explicit save completed record | `backend-review-replay-green-20261011.log`, `backend-full-reputation-red-20261011.log`→green; đạt |
| Media cleanup có thể xóa object vừa complete; quota concurrent vượt cap | Lock completion/cleanup target và quota owner, giữ rule upload | `backend-audit-media-cleanup-race-red-20261009.log`, `backend-audit-media-quota-red-20261009.log`; actual storage tests đạt |
| Retention advisory session lock acquire/unlock khác pooled connection | Borrow cùng connection xuyên suốt sweep/finally unlock, giữ SQL/partial commits | `backend-audit-retention-connection-red-20261009.log`; normal/failure/busy-lock đạt |
| Need response command thiếu rate-limit và bucket user-only sai merchant scope | Gate3commands, resolve authorized merchant server-side, user+merchant bucket | Routing/scope RED; real Redis/JWT/HTTP acceptance đạt |
| Base64 HMAC kiểm độ dài encoded thay decoded, malformed accepted | Dùng keyMaterial thực để validate>=32bytes | `backend-rate-key-red-20261012.log`; focused5 + full đạt |
| Storage HTTPS thiếu host, IPv6 loopback lọt guard | Require host, reject literal IPv6 loopback không DNS | `backend-storage-uri-red-20261012.log`; focused6 + full đạt |
| RFC Forwarded client có thể override rewritten XFF/host/scheme | Clear Forwarded trong4Nginx proxy locations; static guard | Real Spring characterization + positive/negative Bash verifier đạt; Nginx deployed chưa chứng minh |
| Deploy preflight HMAC/mounted JWT path kiểm sai; backup chỉ kiểm tuổi dump | Byte decode/path resolution guard; SHA sidecar + actual pg_restore list trước deploy | Bash fixtures và disposable backup/restore đạt; chưa deploy |
| Monitoring verifier dùng image/mount khác Compose; missing required histogram/metrics/rules theo Plan10 | Parity helper; prod histogram; bounded metrics, scheduled DB snapshot;12rules + dashboard config | Real prod-test scrape/Promtool/Amtool đạt; không coi missing observability là lỗi nghiệp vụ; chưa live Grafana/alerts |
| SSR expired access redirect login dù refresh còn30ngày; concurrent refresh/retry sai response | Next route bridge, shared Redis coordination, forward cookies; browser middleware dùng final retry response | Web/real backend/browser evidence riêng trong `docs/operations/ssr-refresh-coordination.md`; không đổi backend TTL/reuse |

Không có lỗi mới chứng minh bởi hai test Orders vừa thêm. Các thay đổi schema/barcode, UI và dọn tài liệu không được xếp vào danh sách sửa lỗi này.

## 5. File đã thay đổi và lý do

Danh sách đầy đủ tại `backend-progress-files-2026-10-12.csv`: từng file, trạng thái Git, nhóm, lý do/phạm vi và lưu ý nguồn gốc. Snapshot trước báo cáo có171tracked changed/deleted entries và120untracked files (291filepaths). CSV dùng `git ls-files --others` để mở rộng thư mục untracked, không bỏ sót file bên trong. Hai file báo cáo mới được ghi riêng; `.local` evidence không nằm trong change inventory Git.

- BE Java:53trackedmodified +11newJava; trong đó6newJava barcode là feature hiện có, không phải6bản sửa audit. CSV mô tả riêng từng production file.
- Test/resource: regression và acceptance cho các nhóm nêu trên, isolation fixtures và operational verifiers. **Không phải thay đổi nghiệp vụ**; đã dừng bổ sung tự động.
- FE/UI/SSR: thay đổi từ đợt UI và auth trước đó. Giữ nguyên, không tự quy tác giả/provenance nếu chỉ có working-tree diff.
- Contract/generated: OpenAPI + generated TypeScript đồng bộ contract; riêng MerchantManagementResponse thêm logoUrl/coverUrl là thay đổi additive đã có trong tree, **không được mô tả là API hoàn toàn không đổi**. Không mở thêm contract trong lượt này.
- Infra: local Compose/Nginx bridge, prod guards/scripts/monitoring. Đây là file source, không đồng nghĩa cấu hình production đã được áp dụng.
- Schema/data: V017 barcode và seed/ETL có sẵn trong tree; test migration/import trên DBriêng. Không chạy trên dữ liệu thật, không tự publish/apply.
- Docs/outputs: audit/runbook/plan/checkpoints/inventory;4duplicate-spec deletions thuộc công việc cũ, không phải dọn tiếp trong lượt báo cáo.

Không thể xác định chắc chắn tác giả/thời điểm của mọi thay đổi chỉ từ Git diff local. Những file không có mapping bằng chứng được ghi **cần đối chiếu nguồn gốc trước commit**, không tự nhận là đã sửa/kiểm chứng.

## 6. Phần nên giữ nguyên và phần chưa đạt

**Giữ nguyên:** TTL15m/30d, rotation/reuse/CSRF; existing permission denial; snapshot giá/shipping; NEGOTIATED fee/total null; SERVICE/cart quantity1; reserve chỉ commit theo lifecycle hiện có; review disable giữ old feed; V1 SSE sync-required/RESTrefetch; các luồng Ads, checkout, public hierarchy và attribute validation đã đạt khi source không đổi.

**Chưa đủ chứng cứ triển khai:** live Google, TLS/Nginx/browser cookies, public storage/CORS, live Grafana/alert delivery, offsite restore/production-sizedload. Đây là kiểm chứng theo môi trường, không tự nhận là bug và không khiến toàn bộ module phải sửa.

**Chưa hoàn tất quản lý thay đổi:** root chưa đồng bộ dependency remote; local audit/FE/SSR/barcode trộn chung. Cần review/phân nhóm diff trước commit/PR, không gộp mọi file. CI remote xanh không thay full local; full local xanh không thay deployment acceptance.

## 7. Kế hoạch tiếp theo được đề xuất — chưa thực thi

| Thứ tự/module | Công việc giới hạn | Tiêu chí hoàn thành | Điểm dừng |
|---|---|---|---|
| 1. Contract/caller còn lại | Đọc inventory và actual caller FE, chỉ liệt kê endpoint/payload/error chưa đối chiếu; ưu tiên lỗi người dùng tái hiện | Caller khớp route/schema/permission/version/error; dẫn nguồn/test sẵn có | Nếu đã khớp và code không đổi, đóng mục; không viết test mới |
| 2. Auth/Buyer read flows | Đối chiếu SSR/session, Cần mua/Thông báo với evidence đã có; chỉ retest bug còn tái hiện | Không logout do access expiry khi refresh hợp lệ; owner response và notification loading đúng | Không sửa TTL/backend security; live Google/TLS thiếu môi trường ghi CONFIG_REQUIRED |
| 3. Seller/catalog/media/commerce | Kiểm tra các caller FE chưa chứng minh, không mở lại matrix đã đạt | Payload/ETag/assignment/display URLs/availability khớp; bug cụ thể có focused regression | Không làm “mọi combination”; provider/browser chưa có thì ghi giới hạn |
| 4. Orders/Needs/review/Admin | Dùng tests/service/HTTP đã có để xác nhận requirement; thêm test chỉ cho rủi ro có source/bug cụ thể | Permission/transition/side effect có evidence phù hợp với caller thực | Không tự thêm5race còn lại chỉ vì thiếu HTTP test; không refactor |
| 5. Operational readiness | Review env/deploy/runbooks; runtime chỉ khi có môi trường và approval cần thiết | Guards/config khớp; danh sách deployment checks cụ thể | Không applyschema/data/configproduction; thiếu VPS/domain không chặn source review |
| 6. Đóng gói thay đổi | Đối chiếu291fileinventory, tách proven fixes/test/feature/UI/docs; review additive contracts và dependency delta | Mỗi PR có phạm vi/lý do/evidence rõ, checks bắt buộc đạt | Không bulkcommit/merge dirty tree, không push chỉ vì suite xanh |

Quy tắc kiểm thử: ưu tiên đọc evidence có sẵn → focused test nếu thiếu cho bug cụ thể → full chỉ khi thay đổi shared transaction/auth/serialization/config ảnh hưởng nhiều module, sau khi focused đạt. Không chạy lại full khi chỉ sửa docs hoặc source không đổi. Mỗi lượt dừng khi caller/requirement đã có bằng chứng hoặc cần môi trường/approval; báo kết quả và không mở rộng sang chức năng mới.

Trạng thái cuối báo cáo: đã hoàn tất tổng hợp tiến độ theo yêu cầu; mục tiêu hoàn thiện BE toàn hệ thống vẫn đang mở. Đợi định hướng tiếp theo theo báo cáo, không tự động triển khai kế hoạch đề xuất trong lượt này.

## Cập nhật sau báo cáo: review error compatibility — 12/10/2026
Lượt đối chiếu caller phát hiện sai lệch tại FE boundary, không phải lỗi nghiệp vụ BE: `features/reviews/review-api.ts` bỏ qua `error` đã parse của typed client rồi đọc lại response body. Trong khi `review-form.tsx` dùng REVIEW_ALREADY_EXISTS/REVIEW_CREATION_DISABLED để gọi onStale/refetch. Hai regression với body đã consumed tái hiện HTTP_ERROR/mất requestId thay expected code (`.local/review-api-error-red.log`,2fail). Sửa tối thiểu helper dùng existing parsedApiError(response,error); không đổi endpoint/payload/idempotency hoặc BE.

Focused review helper+form3PASS (`.local/review-api-error-green.log`), lint2files và webtypecheck exit0 (`.local/review-api-error-typecheck.log`). Không chạy fullBE vì Java/config/contract không thay đổi. Hai file mới phát sinh thay đổi so với snapshot291: review-api.ts và tests/reviews/review-api-error.test.ts, được bổ sung CSV theo provenance lượt này. Báo cáo snapshot ban đầu giữ nguyên giá trị lịch sử; CSV hiện295rows bao gồm2reportfiles và2reviewfixfiles. Media/report/reputation/inventory routes/payload đã đối chiếu source, chưa có BE mismatch mới; không tự sửa các helper khác chỉ vì cùng pattern nếu caller chưa có ảnh hưởng cụ thể.

## Cập nhật: Cart If-Match contract — 12/10/2026
Actual cart-api.ts update/remove gửi String(version), ví dụ3; VersionPrecondition.parseRequired chỉ nhận quoted vN, ví dụ"v3", trả400INVALID_IF_MATCH cho bare number. Hai helper regressions tái hiện header sai (`.local/cart-api-version-red.log`,2fail), sửa đúng2header formatting ở FE, giữ nguyên BE/quantity/ownership/version rules. Focused helper+cart-detail11PASS (`.local/cart-api-version-final.log`), changed-file lint và webtypecheckPASS (`.local/cart-api-version-typecheck.log`). Không chạy fullBE, không live mutation. Đây là boundary contract repair, chưa claim deployed browser fix.

Điểm còn lại có căn cứ: catalog-api.ts vẫn bỏ parsed error và đọc consumed response; catalog-edit-form dựa RESOURCE_VERSION_CONFLICT để setStale/khóa editing. Cần focused error-boundary regression trước sửa, không bulk đổi mọi helper. Các nhóm caller contact/shipping/coverage/merchant/location đã source-aligned và dừng khi source không đổi; membership/hours không currentcaller. Đây là backlog hữu hạn, không tự mở rộng race/feature/UI.

## Cập nhật: Catalog stale-version error compatibility — 12/10/2026
Catalog edit form calls setStale and stops editing only for RESOURCE_VERSION_CONFLICT. Existing catalog-api mutation helper readApiError re-read consumed typed-client response and discarded parsed error. Two targeted edit/pause regressions reproduced lost code/requestId (`.local/catalog-api-error-red.log`,2fail). Existing requireOk helper now accepts parsed error; its current mutation callsites pass that error. GET/data helpers unchanged, endpoint/payload/ETag/BE unchanged. Focused helper+catalog-edit-form22PASS (`.local/catalog-api-error-green.log`), changed-file lint/typecheckPASS (`.local/catalog-api-error-typecheck.log`). No BE full rerun or actual data/config change. This closes the concrete catalog conflict-handling item listed above; it is not a blanket claim about all helper error flows.

## Trạng thái tổng hợp hiện tại sau ba bản sửa tương thích
CSV hiện có299filepaths, không trùng; đã đối chiếu với git diff --name-only cộng toàn bộ untracked files, không có file thay đổi thiếu trong inventory hoặc entry không còn thay đổi tại thời điểm kiểm tra. Số250 dòng porcelain khác299filepaths vì Git gộp thư mục untracked. Snapshot291 ban đầu và các số295 lịch sử giữ nguyên nghĩa thời điểm, không dùng làm count hiện tại.

| Mục | Trạng thái/điểm dừng |
|---|---|
| Báo cáo tiến độ, lỗi, bằng chứng, file và kế hoạch | Đã cung cấp, cập nhật299filepaths; không có Maven/Java đang chạy |
| Review business error | Đã sửa và focused3PASS/lint/typecheck; dừng khi source không đổi |
| Cart If-Match | Đã sửa và focused11PASS/lint/typecheck; dừng khi source không đổi |
| Catalog mutation stale conflict | Đã sửa và focused22PASS/lint/typecheck; dừng khi source không đổi |
| Contact/shipping/coverage/merchant/location callers | Source-aligned; giữ phạm vi bằng chứng HTTP hiện có; không thêm tests để lấp mọi combination |
| Membership/hours | Không tìm thấy currentFEcaller; giữ existingBE, không tự triển khai UI |
| Broad BE Java | Full905/0fail/errors4optinskips + focusedOrders14 saufull; không rerun khi Java/config không đổi |
| Live Google/TLS/Nginx/public storage/Grafana/alerts/load/offsite restore | Chưa kiểm chứng deployment; cần môi trường, không gọi đây là lỗi BE |
| Commit/PR/release | Chưa đóng gói local299filetree; phải review từng nhóm/provenance và remote dependency delta trước publish |

Không có bằng chứng về lỗi BE mới còn tái hiện trong các caller đã đọc. Điều này không chứng minh toàn bộ deployment sẵn sàng. Hành động có giá trị tiếp theo là review phạm vi publish từ inventory, hoặc kiểm chứng một lỗi thực tế/ngữ cảnh runtime cụ thể; không tiếp tục sinh arbitrary tests hoặc mở lại module đã đạt.

## Revalidation current GitHub state — 2026-10-13
Authoritative state has changed since the earlier snapshot: local HEAD and origin/main both2f9888c2f2645df2f5872c82ec49aa08f710427e. PR25 merged2026-10-13 00:23:49+07; required backend/frontend-and-contract/release-security-source/secret-history allSUCCESS. Main runs38283925576/38283925907 completedSUCCESS. SpringBoot4.1.1/springdoc3.1.1/AWS2.55.12 and webNext16.4.0/Zod4.6.5/jest-dom7.0.1 are integrated; committed Redis6.3.0 retained. Prior299file inventory now historical, not current dirty-file inventory. Current uncommitted files concern catalog/barcode UI including @zxing/browser; preserve unrelated work, no overwrite/merge performed by this revalidation.

Security distinction: GitHub aggregate CodeQL check on PR25 reportsFAILURE with2High new alerts despite individual Analyze jobsSUCCESS. Current remote open CodeQL alerts6/7 catalog create/edit and8/9/10 merchant form, alljs/xss-through-dom High. API evidencecheckrun114926005959/annotations and code-scanning/alerts. Analyze job success means analysis completed, not zero vulnerabilities.

Initial merchant source trace: alerts point to img src={logoPreview/coverPreview}, from file-input change into guarded JPEG/PNG/WebP File→URL.createObjectURL, and storedREADYasset URLs. No innerHTML/outerHTML/insertAdjacentHTML/dangerouslySetInnerHTML found in those three form files. This is source evidence against the alert's rawHTML interpretation, not sufficient to dismiss alerts automatically or claim whole safety. No security suppression/refactor performed. Next review actual CodeQL dataflow on committedHEAD for all5 alerts and distinguish exploitable sink from falsepositive before minimal repair/dismissal; avoid editing active uncommitted barcode UI. No new tests/runtime/deploy/data writes.

## CodeQL five image-preview alerts triaged — 2026-10-13
Actual main JS SARIF1937994773 downloaded and5specific codeFlows compared with committed2f9888c2 source. Alert6/7catalog file input→File[]→URL.createObjectURL→img.src;8/9logo and10cover input→guardedFile→objectURL→img.src. No rawHTMLsink in cited files. Source-reviewed false positives documented in docs/operations/codeql-image-preview-triage-2026-10-13.md, limited to these paths/commit. Alerts remainOPEN; no dismissal/suppression/codeedit or arbitrarytest. Uncommitted barcode UI preserved. Old299fileinventory historical afterPR25 merge; newtriagedoc is a report, not productrepair. WholeBE deployment readiness still unproven, but these5alerts do not justify changing working preview code.

## Latest authoritative gate — 2026-10-13
Actual mainCI38283925907 logs nowconfirm BE911/0failure/errors4skip BUILD SUCCESS03:50 and FE481PASS12skip/typecheck/buildPASS for2f9888c2. This replaces historical905local as latest committed-source gate; no tests rerun. Current dirtybarcodeUI is excluded. Localruntime services are stopped (dockerps empty/noweb/APIlisteners/noJava), notbroken. Next runtime check must explicitly start a reviewed configuration and honor actual-data/job boundaries; do not treat absentruntime as failedbusinesslogic. Current source/fullCI evidence supports preserving verified modules; remaining environment proof must not trigger arbitrarycode changes.
