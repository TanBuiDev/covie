# CHƯƠNG 6. KIỂM THỬ VÀ ĐÁNH GIÁ

## 6.1. Mục tiêu và chiến lược kiểm thử

Mục tiêu kiểm thử là kiểm tra yêu cầu nghiệp vụ và các tình huống làm mất tính nhất quán, không chỉ chứng minh ứng dụng khởi động được. Chiến lược cần có kiểm thử đơn vị cho chính sách và phép tính, kiểm thử tích hợp với cơ sở dữ liệu, kiểm thử “HTTP”/“contract”, kiểm thử giao diện và hành trình trình duyệt. Mỗi lớp phát hiện một nhóm lỗi khác nhau.

Đối với CoVie, các rủi ro ưu tiên là bán vượt tồn, tạo đơn dở dang, truy cập tài nguyên người khác, chuyển trạng thái không được phép, dùng “preview” lỗi thời và hiển thị sai phần tiền chưa biết. Những rủi ro này được đánh giá trước các chi tiết hình thức vì tác động trực tiếp đến dữ liệu và niềm tin người sử dụng.

[[EXPLANATORY_FIGURE:test_strategy]]

Hình 6.1. Chiến lược kiểm thử nhiều lớp của CoVie

Nguồn: tổng hợp từ mã kiểm thử và tài liệu vận hành của CoVie. [N12, N13]

## 6.2. Phân loại bằng chứng được sử dụng

Dữ liệu đánh giá được chia thành bốn nhóm: kết quả kiểm thử đã ghi nhận (E-HIST), kết quả phân tích mã nguồn và hợp đồng dữ liệu (E-STATIC), hình ảnh giao diện thử nghiệm (E-VISUAL), và kế hoạch kiểm thử bổ sung (E-PLAN). Mỗi nhóm phản ánh một khía cạnh của hệ thống. Kết quả thực thi được dùng để đánh giá hành vi đã kiểm tra; phân tích mã nguồn và hình ảnh bổ sung thông tin về cấu trúc và giao diện; kế hoạch kiểm thử xác định phạm vi đánh giá tiếp theo.

| Bằng chứng | Nguồn | Phạm vi diễn giải |
| --- | --- | --- |
| E-HIST | “AGENTS” và “Project Status” ngày 26/08/2026 | Kết quả đã được ghi ở “baseline” lịch sử |
| E-STATIC | Mã nguồn c477f30, đặc tả “OpenAPI” và các phiên bản cấu trúc dữ liệu | Sự tồn tại, cấu trúc và hành vi đọc được |
| E-VISUAL | Ảnh giao diện người mua với dữ liệu thử nghiệm | Minh họa trạng thái giao diện của “fixture” |
| E-PLAN | “Runbook” và kịch bản trong báo cáo | Phạm vi kiểm thử bổ sung chưa có kết quả thực thi |

Kết quả kiểm thử tổng hợp tại mục 6.3 thuộc đợt kiểm tra ngày 26/08/2026. Chưa có kết quả kiểm thử hồi quy đầy đủ cho phiên bản c477f30, bao gồm kiểm thử máy chủ, giao diện và hành trình trên trình duyệt. Vì vậy, các thay đổi sau ngày 26/08/2026 được đánh giá trong phạm vi phân tích mã nguồn và những kết quả kiểm tra riêng đã ghi nhận.

[[EXPLANATORY_FIGURE:evidence_levels]]

Hình 6.2. Phân biệt ba mức bằng chứng trong báo cáo

Nguồn: quy ước đánh giá được áp dụng trong báo cáo CoVie.

## 6.3. Kết quả lịch sử đã được ghi nhận

| Hạng mục | Kết quả ghi nhận | Thời điểm/phạm vi |
| --- | --- | --- |
| “Backend” “clean verify” | 460 “test” đạt, 1 bỏ qua | Tổng kết dự án ngày 26/08/2026 |
| “Vitest” “frontend” | 103 “test” đạt | Tổng kết ngày 26/08/2026 |
| “Typecheck” và “production build” | Đạt theo tài liệu | “Baseline” cùng đợt kiểm chứng |
| “Playwright” chuẩn | 2 đạt, 1 “fixture-gated skip” | “Local”, theo tổng kết 26/08 |
| “Public Buyer P0” có “fixture” | 1 hành trình đạt | “Local”, theo tổng kết 26/08 |
| “Lint” | 0 lỗi, 2 cảnh báo được ghi nhận | “Baseline” lịch sử; không phải số liệu “HEAD” |
| “Launcher” và “seed” “regression” | “Exit code” 0 được ghi nhận | Đợt kiểm chứng “local” tương ứng |

Nguồn: AGENTS.md và docs/CoVie-project-status.md, mốc kiểm thử 26/08/2026. Các bộ kiểm thử có phạm vi và đơn vị thống kê khác nhau nên được trình bày riêng. Những trường hợp bỏ qua chưa được tính là kiểm thử đạt. [N2]

Ảnh giao diện được sử dụng để minh họa chức năng và bố cục. Kết quả tại Bảng 6.2 được đánh giá theo bản ghi thực thi ngày 26/08/2026; bộ ảnh minh họa không dùng để xác nhận kết quả của đợt kiểm thử này.

## 6.4. Kiểm thử “backend” và giao dịch đồng thời

Thư mục apps/api/src/test có 179 tệp kết thúc bằng Test.java tại phiên bản c477f30. Các lớp kiểm thử tiêu biểu gồm “CheckoutLastStockConcurrencyTest”, “CheckoutRollbackInvariantTest”, “NeedConcurrencyIntegrationTest” và “OrderLifecycleTransitionTest”, tương ứng với rủi ro tranh chấp tồn kho, khôi phục giao dịch, xử lý nhu cầu đồng thời và chuyển trạng thái đơn hàng. Số lượng tệp phản ánh quy mô mã kiểm thử; kết quả thực thi được tổng hợp riêng tại mục 6.3. [N12]

Tình huống tồn cuối cần ít nhất hai tác nhân độc lập cùng gửi “commit” với lượng khả dụng ban đầu bằng một. Kết quả mong đợi là không quá một đơn giữ được lượng hàng đó, không có “stock” âm, không có “reservation” vượt mức và không nhân đôi biến động. Chỉ đo “HTTP” 200 của một “request” không đủ; cần kiểm tra trạng thái dữ liệu cuối sau tất cả “transaction”.

Tình huống “rollback” cần gây lỗi sau khi một số bước trong “commit” đã được thực hiện nhưng trước khi “transaction” hoàn tất. Kết quả mong đợi là không còn đơn, “reservation” hoặc “outbox” ở trạng thái gây hiểu nhầm về một giao dịch thành công. Cần phân biệt “rollback” dữ liệu trong “DB” với hiệu ứng ngoài “DB”; đó là một lý do sự kiện được ghi vào “outbox” thay vì gọi hệ thống ngoài ngay trong “transaction” tạo đơn.

Tình huống đơn hàng đồng thời cần kiểm tra hai lệnh xung đột, chẳng hạn một phía hủy trong khi phía kia xử lý bước tiếp theo. Kết quả hợp lệ phải thỏa máy trạng thái và số sự kiện tương ứng; không cho hai tác nhân cùng ghi đè một trạng thái đã bị thay đổi. Các kịch bản này cần được chạy trên “PostgreSQL” với cơ chế khóa thực tế để có giá trị kiểm chứng.

## 6.5. Danh sách “test case” nghiệm thu đề xuất

Bảng sau trình bày 24 kịch bản kiểm thử nghiệm thu, bao gồm dữ liệu đầu vào theo tình huống, kết quả mong đợi và trạng thái kiểm chứng. Các kịch bản còn chờ thực thi tại phiên bản c477f30; tài khoản và danh mục thử được tách khỏi dữ liệu sử dụng thực tế.

| Mã | Kịch bản | Kết quả mong đợi | Trạng thái |
| --- | --- | --- | --- |
| TC-01 | Đăng nhập tài khoản hợp lệ | Tạo phiên đúng, đọc được /“me” | Chưa thực thi tại c477f30 |
| TC-02 | Thu hồi phiên rồi gọi lại “API” | Phiên cũ bị từ chối | Chưa thực thi tại c477f30 |
| TC-03 | Gửi lại “refresh token” đã tiêu thụ | Thực hiện chính sách “reuse”/“revoke” | Chưa thực thi tại c477f30 |
| TC-04 | “Buyer” A dùng địa chỉ của “Buyer” B | Bị từ chối, không lộ dữ liệu | Chưa thực thi tại c477f30 |
| TC-05 | “Staff” ngoài phạm vi điểm bán đổi dữ liệu | Máy chủ từ chối | Chưa thực thi tại c477f30 |
| TC-06 | Tìm danh mục không có từ khóa | “Request” “sort” hợp lệ, không lỗi 422 do “adapter” | Chưa thực thi tại c477f30 |
| TC-07 | Thêm “PRODUCT” và “SERVICE” | Tách giỏ theo quy tắc | Chưa thực thi tại c477f30 |
| TC-08 | Gửi “SERVICE” “quantity” lớn hơn một | Từ chối dữ liệu sai | Chưa thực thi tại c477f30 |
| TC-09 | Giá thay đổi sau “preview” | “Commit” báo thay đổi, cần xem lại | Chưa thực thi tại c477f30 |
| TC-10 | Giỏ hoặc địa chỉ đổi “version” | Không “commit” bản chấp nhận cũ | Chưa thực thi tại c477f30 |
| TC-11 | Hai người tranh một hàng cuối | Không bán vượt tồn | Chưa thực thi tại c477f30 |
| TC-12 | Lỗi giữa quá trình “commit” | “Rollback” nhất quán | Chưa thực thi tại c477f30 |
| TC-13 | “Retry” cùng lệnh có “idempotency” | Không sinh hiệu ứng trùng | Chưa thực thi tại c477f30 |
| TC-14 | Phí giao “NEGOTIATED” | Không hiển thị tổng cuối giả | Chưa thực thi tại c477f30 |
| TC-15 | Đặt địa chỉ ngoài “coverage” | Chặn theo chính sách; cần hoàn thiện tích hợp | Chờ sửa/kiểm chứng |
| TC-16 | “Rule” miễn phí theo khoảng cách | Dùng khoảng cách thực tế | Chờ sửa/kiểm chứng |
| TC-17 | “Buyer” đọc đơn người khác | Không đọc được | Chưa thực thi tại c477f30 |
| TC-18 | Gửi lệnh không thuộc “allowedActions” | Máy chủ từ chối | Chưa thực thi tại c477f30 |
| TC-19 | Báo sự cố sau giao/thực hiện | Vào nhánh sự cố hợp lệ | Chưa thực thi tại c477f30 |
| TC-20 | Nhu cầu dẫn tới đơn đã xác nhận | “Attribution” và “fulfillment” nhất quán | Chưa thực thi tại c477f30 |
| TC-21 | Phản hồi hết khả dụng trước “checkout” | Kiểm tra lại và từ chối phù hợp | Chưa thực thi tại c477f30 |
| TC-22 | “Admin” cập nhật “setting” “version” cũ | Xung đột; không ghi đè | Chưa thực thi tại c477f30 |
| TC-23 | Mất mạng rồi “SSE” kết nối lại | “REST state” được tải lại | Chưa thực thi tại c477f30 |
| TC-24 | Mở “app” “offline” | Trang “offline”; không đặt đơn từ “cache” | Chưa thực thi tại c477f30 |

Các kịch bản tại Bảng 6.3 xác định đầu vào, thao tác và kết quả mong đợi cho kiểm thử bổ sung. Trạng thái chưa thực thi cho biết các kịch bản này chưa có kết quả xác nhận tại phiên bản c477f30. Phụ lục A cung cấp lệnh khởi chạy môi trường và bộ kiểm thử.

## 6.6. Kiểm thử giao diện và khả năng sử dụng

Nhóm chức năng người mua “Public Buyer P0” đã được kiểm tra ở các kích thước 390×844, 360×800 và 1280×900 theo kết quả đã ghi nhận. Các hình tại Chương 5 minh họa các hành trình người mua, người bán, quản trị và những trạng thái giao diện tiêu biểu. Phạm vi đánh giá tiếp theo gồm thứ tự chuyển tiêu điểm, nhãn hỗ trợ trình đọc màn hình, trạng thái nút trong quá trình xử lý và khả năng khôi phục sau lỗi mạng.

Với “Buyer”/“Seller”/“Admin” mới hơn, cần lặp lại kiểm tra trạng thái rỗng, lỗi, không được phép, dữ liệu dài và các thao tác phá hủy. “Desktop Seller” cần kiểm tra 1024, 1280 và 1440 theo “handoff”; “mobile” cần kiểm tra các mốc đã xác định. Một bảng hiển thị đúng ở 1440 chưa đủ để kết luận không tràn ngang ở 1024.

Đề tài chưa thực hiện nghiên cứu người dùng có đo thời gian hoàn thành tác vụ và mức độ hài lòng. Do đó, kết quả đánh giá giao diện tập trung vào yêu cầu kỹ thuật và mức độ đáp ứng thiết kế. Hiệu quả sử dụng trong điều kiện thực tế là nội dung cần được khảo sát ở giai đoạn phát triển tiếp theo.

## 6.7. Hiệu năng và vận hành

“Runbook” “performance” có các mục tiêu “pilot”: tìm kiếm công khai “p95” không quá 500 ms với “concurrency” 50; chi tiết mặt hàng “p95” không quá 300 ms với “concurrency” 50; danh sách đơn có xác thực “p95” không quá 500 ms với “concurrency” 20; tỷ lệ lỗi mục tiêu dưới 1%. Đây là các ngưỡng được đặt trong kế hoạch, không phải số liệu đo đã đạt. [N13]

Tài liệu yêu cầu dữ liệu và môi trường gần “production”, kiểm tra “EXPLAIN (ANALYZE, BUFFERS)”, kết nối “PostgreSQL”, “Hikari”, hàng đợi “outbox” và “Redis”. Một cơ sở dữ liệu “local” nhỏ không đủ để chứng minh hiệu năng “pilot”. Để báo cáo số đo có ý nghĩa, cần ghi cấu hình phần cứng, kích thước dữ liệu, thời gian chạy, phiên bản và phân vị độ trễ.

Kế hoạch diễn tập “staging” bao gồm “migration”, luồng có xác thực, giới hạn tần suất yêu cầu, sao lưu và khôi phục vào cơ sở dữ liệu khác, quay lui phiên bản và kiểm tra cảnh báo. Chưa có kết quả hoàn tất đợt diễn tập này; “Dockerfile” và “workflow” hiện là thành phần chuẩn bị triển khai. [N13]

## 6.8. Các giới hạn được xác định

### 6.8.1. Khoảng cách và “coverage” trong “checkout”

Trong “CheckoutPreviewService” tại phiên bản c477f30, bước kiểm tra lại giao dịch truyền giá trị khoảng cách bằng 0 vào bộ đánh giá vận chuyển. Điều kiện “DISTANCE_MAX” vì thế có thể được đánh giá không đúng với khoảng cách thực tế. Phần kiểm tra khả năng giao nhận mới thể hiện các cờ chức năng và yêu cầu địa chỉ; việc thực thi đầy đủ phạm vi phục vụ địa lý chưa được xác minh. Hạn chế này được xác định qua phân tích mã nguồn và cần kiểm thử tích hợp để đánh giá tác động thực tế.

Hạn chế này ảnh hưởng đến độ chính xác của chính sách phí theo khoảng cách và điều kiện phục vụ theo vùng. Hướng khắc phục là truyền khoảng cách thực, tích hợp kiểm tra vùng phục vụ trong bước “revalidate” và bổ sung kiểm thử cho địa chỉ trong vùng, ngoài vùng cũng như chính sách thay đổi giữa “preview” và “commit”.

### 6.8.2. Độ đầy đủ của “snapshot”

“Snapshot” của mặt hàng hiện chưa chứa đầy đủ tên, lựa chọn và hình ảnh theo mô hình lịch sử dự kiến. “Snapshot” liên hệ được khởi tạo rỗng và “snapshot” chính sách từ “evaluator” chưa có nội dung. Các trường tiền đã được lưu, nhưng việc hiển thị toàn bộ thông tin đơn cũ vẫn có thể phụ thuộc vào dữ liệu hiện tại.

Hướng xử lý là định nghĩa “schema” “snapshot” có phiên bản, đưa đủ các trường cần “render” lịch sử vào thời điểm tạo đơn và kiểm thử khi “catalog”/địa chỉ/liên hệ sau đó thay đổi. Cần tránh sao chép dữ liệu nhạy cảm quá mức; phạm vi “snapshot” phải phù hợp với nhu cầu giao dịch và quyền đọc.

### 6.8.3. Hợp đồng và nghiệm thu giao diện

“Buyer” “Provider” “discriminator”, “review” “eligibility”, nguồn uy tín cá nhân và một số quyền hành động nhu cầu còn được “matrix” ghi là thiếu. “Seller” có khoảng trống ở các nguồn “detail”/“version” và ứng viên phản hồi. “Admin” có màn hình chỉ đọc hoặc thiếu “endpoint” quản trị. Chưa có cơ sở coi tất cả giao diện thiết kế đã trở thành hành trình hoạt động hoàn chỉnh. [N9]

### 6.8.4. Phạm vi bằng chứng

Phạm vi đánh giá bị giới hạn bởi khác biệt phiên bản giữa kết quả kiểm thử ngày 26/08/2026 và mã nguồn c477f30. Bộ ảnh cục bộ chưa liên kết đầy đủ với phiên bản thực thi. Ngoài ra, chưa có số đo tải “pilot”, khảo sát người dùng, xác nhận thanh toán tự động hoặc kết quả diễn tập “staging” hoàn tất.

## 6.9. Đánh giá theo mục tiêu

| Mục tiêu | Đánh giá hiện tại | Điều kiện để nâng mức kết luận |
| --- | --- | --- |
| MT-01: Chuẩn hóa dữ liệu | Có mô hình và “migration”, “API” đã lưu | Kiểm tra “schema” và “contract” tại c477f30 |
| MT-02: Hành trình tạo đơn | Có bằng chứng “local” lịch sử cho “Public Buyer” | Kiểm thử hồi quy tại c477f30 |
| MT-03: Nhất quán giao dịch | Có cơ chế khóa, phiên và “test” tương ứng | Chạy lại “race”/“rollback”; hoàn thiện “geo”/“snapshot” |
| MT-04: Phân quyền | Có thiết kế phiên, “membership” và hành động “server-owned” | Kiểm tra ma trận quyền với dữ liệu nhiều “actor” |
| MT-05: Vận hành và truy vết | Có nền tảng mã và “runbook” | Hoàn tất “staging”, “restore”, “rollback” và quan sát |

Đề tài đã triển khai các miền nghiệp vụ chính và có kết quả kiểm thử cục bộ tại mốc 26/08/2026. Mức độ hoàn thiện hiện tại còn giới hạn ở tích hợp dữ liệu địa lý, lưu lịch sử giao dịch, hợp đồng giao diện và vận hành. Hệ thống chưa được xác nhận sẵn sàng phục vụ trên môi trường “production”.
