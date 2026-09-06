# CHƯƠNG 4. THIẾT KẾ HỆ THỐNG

## 4.1. Kiến trúc tổng thể

CoVie được tổ chức thành “workspace” gồm apps/web, apps/api và packages/api-client. “Web” sử dụng “client” và kiểu dữ liệu được sinh từ hợp đồng “OpenAPI”. “API” điều phối các miền và lưu dữ liệu giao dịch vào “PostgreSQL”/“PostGIS”. “Redis” và kho đối tượng “S3-compatible” là các thành phần hỗ trợ; chúng không thay thế cơ sở dữ liệu giao dịch cho đơn hàng và tồn kho. Các “script”, “manifest” và “runbook” được đặt trong “scripts”, “infra” và docs/operations. [N3, N5]

[[FIGURE:architecture]]

Hình kiến trúc thể hiện trách nhiệm và quan hệ giữa các thành phần. Trong môi trường cục bộ, “web” sử dụng cổng 3000, “API” sử dụng 8081 và “PostgreSQL” được công bố tại 5433. “Redis” sử dụng cổng 6379 và “MinIO” sử dụng cổng 9000. Cấu hình mạng cho môi trường triển khai thực tế được quản lý riêng.

## 4.2. Phân rã thành phần “backend”

| Nhóm miền | Trách nhiệm chính |
| --- | --- |
| “auth”, “users” | Danh tính, phiên, tài khoản, địa chỉ và phương thức liên hệ |
| “merchants”, “location”, “media”, “catalog” | Người bán, điểm bán, khu vực, tài nguyên ảnh và nguồn cung |
| “delivery”, “shipping”, “inventory” | Phạm vi phục vụ, chính sách phí và lượng hàng |
| “cart”, “checkout”, “orders” | Giỏ, chấp nhận giá, tạo đơn và vòng đời giao dịch |
| “needs” | Nhu cầu mua, phản hồi và liên kết nguồn đặt hàng |
| “reviews”, “reputation”, “notifications” | Đánh giá, tổng hợp uy tín và thông báo |
| “moderation”, “settings”, “admin”, “audit” | Kiểm duyệt, cấu hình và vận hành |
| “shared”, “outbox” | Hạ tầng lỗi, tiền tệ, thời gian, “idempotency” và sự kiện |

Các thành phần “shared” cần giữ tính dùng chung và tránh chứa quy tắc nghiệp vụ đặc thù chỉ để tiện truy cập. Ngược lại, một “service” điều phối có thể cần gọi nhiều miền trong cùng giao dịch khi đó là yêu cầu nhất quán thật sự. “Checkout” là ví dụ điển hình vì không thể tạo đơn hợp lệ mà bỏ qua tồn kho và nguồn dữ liệu được mua.

## 4.3. Thiết kế mô hình dữ liệu

### 4.3.1. Danh tính và đơn vị bán hàng

“users” là thực thể tài khoản trung tâm. “user_identities” biểu diễn danh tính đăng nhập; “auth_sessions” và “auth_refresh_tokens” theo dõi phiên và vòng đời “refresh”. “user_addresses” thuộc người dùng và giữ dữ liệu địa chỉ. “system_role_assignments” biểu diễn vai trò hệ thống; quyền của người bán lại được mô hình hóa qua “merchant_memberships” và “merchant_member_locations”.

“merchant_profiles” biểu diễn một đơn vị kinh doanh/cung cấp dịch vụ. “merchant_locations” là các điểm bán thuộc đơn vị đó. Việc tách hai mức giúp thông tin chung như tên đơn vị không bị nhân bản theo từng điểm, trong khi địa chỉ, khả năng phục vụ và cấu hình theo điểm có thể thay đổi độc lập. Một thành viên có thể có phạm vi “location” riêng, phù hợp với vận hành nhiều điểm bán.

### 4.3.2. “Catalog” và giá tại điểm bán

“catalog_items” lưu bản mô tả mặt hàng. “variants” giữ lựa chọn cụ thể; “option_groups”, “option_values” và “variant_option_values” hỗ trợ biểu diễn các lựa chọn. “location_variants” nối lựa chọn với một điểm bán và quyết định chào bán tại đó. Đây là mắt xích được dùng khi tạo dòng giỏ, kiểm tra giá và kiểm soát tồn.

Giả sử cùng một loại đồ uống có hai kích cỡ và được bán ở hai địa điểm. Mô hình tách lớp cho phép hai “variant” dùng chung mô tả “catalog”, đồng thời có giá/khả dụng khác nhau ở từng “location”. Nếu chỉ đặt một cột “price” ở “catalog_items”, hệ thống khó biểu diễn chính xác tình huống này mà không nhân bản sản phẩm.

### 4.3.3. Giỏ, “checkout”, đơn hàng và tồn kho

“carts” và “cart_items” biểu diễn ý định mua đang thay đổi. “checkout_sessions” lưu dữ liệu bản xem trước và trạng thái phiên đặt hàng. “orders” giữ trạng thái tổng thể; “order_items” giữ các dòng và giá đã dùng. “product_fulfillments” và “service_fulfillments” giữ thông tin thực hiện tùy loại. “order_events”, “order_cancellation_requests” và “order_issues” giữ lịch sử cùng các nhánh xử lý riêng. [N6]

“inventory_balances” lưu số lượng hiện tại; “inventory_reservations” biểu diễn phần giữ cho đơn; “inventory_movements” lưu biến động. Ba mức dữ liệu này trả lời các câu hỏi khác nhau: hiện còn bao nhiêu, đang giữ cho giao dịch nào và vì sao lượng hàng thay đổi. Việc chỉ có một cột “stock” không đủ để giải thích lịch sử và xử lý hủy/hoàn tất nhất quán.

[[FIGURE:erd]]

Sơ đồ “ERD” trong hình là phần lõi chọn lọc. Các đường nối biểu diễn quan hệ khóa ngoại chính; không thay thế toàn bộ 66 bảng hoặc tất cả ràng buộc trong “migration”. Danh mục “migration” và từ điển dữ liệu chọn lọc được đặt ở phụ lục để tránh làm sơ đồ chính khó đọc.

### 4.3.4. Nhu cầu, uy tín và quản trị

“need_requests” nối người mua với một nhu cầu; “need_responses” gắn phản hồi của người bán; “need_response_items” gắn ứng viên mua cụ thể. Đơn lưu nguồn nhu cầu để truy vết. “reviews” và các bảng “rating stats” phục vụ đánh giá; “reputation_order_outcomes” cùng các bảng “reputation stats” biểu diễn kết quả và tổng hợp. “notifications” và “notification_deliveries” tách nội dung thông báo khỏi quá trình gửi.

“moderation_cases” và “moderation_actions” mô tả vụ việc cùng hành động kiểm duyệt. “system_settings” và “system_setting_history” phục vụ cấu hình và lịch sử thay đổi. “audit_logs” ghi các thao tác cần truy vết. Tách bảng trạng thái hiện tại với bảng lịch sử giúp việc đọc nhanh không làm mất căn cứ giải thích thay đổi.

## 4.4. Ràng buộc và nguyên tắc lưu trữ

Tiền được lưu bằng số nguyên “bigint” ở những bảng giao dịch đã đối chiếu. Với dữ liệu “VND” hiện dùng, việc nhân giá đơn vị với số lượng được thực hiện bằng phép toán nguyên và kiểm tra tràn trong “code”. Không dùng số thực nhị phân để cộng tiền rồi làm tròn tùy ý ở giao diện. Các “constraint” như số lượng dương và tiền không âm là tuyến bảo vệ ở mức dữ liệu. [N6, N7]

Thông tin tổng tiền có thể “nullable” trong trường hợp chưa biết phí giao. Với “NEGOTIATED”, “schema” yêu cầu “shipping_fee” và “total_amount” là “NULL”, còn “known_total” bằng “subtotal”. Với “SERVICE”, “shipping fee” được đánh dấu không áp dụng và tổng bằng tiền dịch vụ. Phân biệt “không áp dụng”, “bằng không” và “chưa biết” giúp tránh kết luận sai trên màn hình “checkout”.

“Snapshot” lưu thông tin giao dịch tại thời điểm tạo đơn. Trong “CheckoutPreviewService”, dữ liệu mặt hàng mới gồm một tập trường hạn chế; dữ liệu liên hệ được khởi tạo rỗng và bộ đánh giá vận chuyển trả về giá trị “null” cho dữ liệu chính sách. Vì vậy, việc hiển thị đầy đủ một đơn hàng cũ vẫn có thể phụ thuộc vào thông tin mặt hàng hoặc liên hệ hiện tại. Hoàn thiện dữ liệu lịch sử là yêu cầu cần giải quyết để bảo đảm khả năng tra cứu sau khi thông tin nguồn thay đổi.

## 4.5. Thiết kế “API” và hợp đồng dữ liệu

“API” được đặt dưới /api/v1. Hợp đồng hiện lưu trong packages/api-client/openapi/openapi.json; thư mục “generated” chứa “client” “TypeScript” tương ứng. “OpenAPI” là định dạng mô tả giao diện “HTTP” bằng các thành phần như “path”, “operation”, “parameter” và “schema”. [T5] Báo cáo đếm được 119 “path” trong “file” hiện tại; một “path” có thể có nhiều “HTTP method” nên số “path” không đồng nghĩa số “endpoint” thao tác.

| Nhóm | Ví dụ đường dẫn | Mục đích |
| --- | --- | --- |
| Công khai | “GET” /api/v1/catalog/items | Khám phá nguồn cung |
| Địa chỉ cá nhân | “GET”/“POST” /api/v1/me/addresses | Đọc hoặc tạo địa chỉ của tài khoản |
| Giỏ | “POST” /api/v1/me/carts/items | Thêm dòng vào giỏ |
| “Checkout” | “POST” /api/v1/checkouts/preview | Tạo bản xem trước có “token” |
| “Checkout” | “POST” /api/v1/checkouts/commit | Chấp nhận và tạo đơn |
| Đơn | “GET” /api/v1/orders/{orderId} | Chi tiết theo quyền tác nhân |
| Lệnh đơn | “POST” /api/v1/orders/{orderId}/confirm | Người bán xác nhận khi được phép |
| Quản trị | “PATCH” /api/v1/admin/settings/{settingKey} | Cập nhật cấu hình theo hợp đồng |

Hệ thống dùng mã lỗi nghiệp vụ giúp máy khách phân biệt nguyên nhân. Chẳng hạn “CHECKOUT_CHANGED” không được xử lý giống lỗi mất mạng, vì hành động đúng là xem lại dữ liệu. Phản hồi 401 biểu diễn vấn đề xác thực; 403 liên quan quyền; 409/412 liên quan xung đột ở các luồng có hợp đồng tương ứng; 422 có thể biểu diễn dữ liệu hoặc tổ hợp nghiệp vụ không hợp lệ. Chi tiết từng “operation” cần đọc trực tiếp “OpenAPI” và “controller” thay vì áp một quy tắc “header” cho toàn bộ “API”.

## 4.6. Thuật toán xem trước và xác nhận “checkout”

Ở bước “preview”, máy chủ xác định người mua, “resolve” nguồn hàng, kiểm tra điểm bán và khả năng thực hiện. Nếu cần địa chỉ, dữ liệu địa chỉ được tải theo “ID” và người sở hữu. Với từng dòng, máy chủ lấy “offer” hiện tại, kiểm tra khả dụng, ràng buộc “SERVICE” “quantity” bằng một và lượng khả dụng đối với hàng “TRACKED”. Tổng tiền được tính lại từ giá “offer”, không lấy từ dữ liệu tính ở trình duyệt.

Sau đó, máy chủ tính phí theo phương thức và tạo “CheckoutComparableSnapshot” chứa các trường được dùng để phát hiện thay đổi. Phiên được lưu và “token” được trả cho máy khách cùng thời điểm hết hiệu lực và các tổng tiền. Đây là bản xem trước có căn cứ phía máy chủ, nhưng các dữ liệu bên ngoài “snapshot” vẫn cần được đánh giá khi cải tiến mức đầy đủ của việc chấp nhận giá.

[[FIGURE:checkout]]

Tại “commit”, “service” khóa phiên bằng “token” và “user ID”. Nếu nguồn là giỏ, giỏ hoạt động được khóa và “version” phải khớp phiên. “Service” khóa nguồn theo “resolver”, tái kiểm tra sơ bộ, xác định các “offer” cần khóa tồn rồi tái kiểm tra dưới các khóa tồn đã lấy. Bản dữ liệu chấp nhận trước đó được so với bản hiện tại. Nếu có thay đổi, lệnh kết thúc bằng lỗi xung đột.

Nếu không có thay đổi, “OrderCreationService” tạo đơn “PENDING_CONFIRMATION”. Các dòng “TRACKED” được giữ hàng, “ORDER_CREATED” được ghi vào “outbox”, “hook” liên quan được gọi, phiên được đánh dấu tiêu thụ; với nguồn giỏ, các dòng giỏ được xóa và giỏ được đánh dấu “checkout”. Toàn bộ phương thức “commit” được bao bởi @“Transactional”. [N7]

Ví dụ cạnh tranh: tồn thực tế bằng một và chưa giữ hàng. Hai người có thể cùng xem “preview” hợp lệ. Khi “commit”, việc khóa cùng “balance” làm tuần tự hóa phần quyết định giữ hàng. Người hoàn tất sau phải nhìn thấy lượng khả dụng sau giao dịch trước; hệ thống không dựa vào số lượng đã thấy lúc “preview”. “Test” “CheckoutLastStockConcurrencyTest” và “CheckoutRollbackInvariantTest” là các hiện vật kiểm tra trực tiếp loại rủi ro này, kết quả thực thi tại c477f30 còn chờ cập nhật.

## 4.7. Thiết kế phí giao và phạm vi phục vụ

“ShippingEvaluator” hiện có bốn “mode”: “FREE”, “FIXED”, “NEGOTIATED” và “CONDITIONAL”. “FREE” cho phí bằng không. “FIXED” dùng “base fee”. “NEGOTIATED” không xác định phí và tổng cuối. “CONDITIONAL” đánh giá các “rule” được bật, với điều kiện ngưỡng tiền hàng hoặc khoảng cách; tổ hợp “ANY” hoặc “ALL” quyết định miễn phí, ngược lại dùng “base fee”. [N7]

Ví dụ minh họa cho “rule” giá trị đơn: “base fee” 15.000 đồng và ngưỡng miễn phí 150.000 đồng. Một đơn tiền hàng 120.000 đồng không đạt điều kiện; một đơn 170.000 đồng đạt. Ví dụ chỉ giải thích nhánh điều kiện; không phải bảng giá mặc định của CoVie. Ngưỡng và chính sách thực tế phải đến từ cấu hình có thẩm quyền.

Trong “CheckoutPreviewService” tại phiên bản c477f30, “evaluator” được gọi với “distanceMeters” bằng 0. Phương thức “validateFulfillmentCapability” kiểm tra cờ khả năng và yêu cầu địa chỉ, nhưng bước “revalidate” chưa thể hiện đầy đủ kiểm tra vùng phục vụ địa lý. Điều kiện giao hàng theo khoảng cách vì thế còn là giới hạn tích hợp cần khắc phục và kiểm thử.

## 4.8. Thiết kế trạng thái đơn hàng

“OrderStatus” hiện khai báo 17 trạng thái, gồm trạng thái chờ/xác nhận, giao/nhận, hủy, sự cố và kết thúc. “OrderAction” khai báo các lệnh thay vì cho phép máy khách ghi tùy ý cột “status”. Cách biểu diễn bằng lệnh giúp máy chủ gắn từng thay đổi với “actor”, điều kiện và hiệu ứng phụ.

[[FIGURE:orderstates]]

Hình chỉ thể hiện chuỗi chính và các nhánh kết thúc điển hình. Không phải mọi mũi tên đều dùng cho cả “PRODUCT” và “SERVICE”, cũng không thể suy ra quyền chỉ từ hình. Người đọc cần đối chiếu “policy” và “allowedActions” đối với từng trường hợp. Một lần hủy trước xác nhận khác với yêu cầu hủy sau xác nhận; báo sự cố sau thực hiện khác với việc từ chối đơn ban đầu.

Lịch sử “order_events” lưu chuỗi thao tác trên đơn. Các bảng “cancellation” và “issue” bổ sung ngữ cảnh yêu cầu, thông tin sự cố và kết quả giải quyết. Giao diện quản trị hỗ trợ đọc đơn và lịch sử; việc chuyển trạng thái vẫn tuân theo các lệnh nghiệp vụ và điều kiện phân quyền. [N9]

## 4.9. Xác thực, phân quyền và bảo vệ dữ liệu

“Access JWT” trong kiến trúc dự án là thông tin xác thực thời hạn ngắn có chữ ký “RSA”. Máy chủ còn kiểm tra phiên đang hoạt động và trạng thái tài khoản trong “PostgreSQL”. “Refresh” áp dụng “rotation” và phát hiện “reuse”; thời hạn tuyệt đối của phiên không tự kéo dài theo từng lần “refresh”. “Cookie HttpOnly” và “CSRF” được dùng cho luồng trình duyệt, còn “Bearer header” là đường cho máy khách không dựa vào “cookie” trình duyệt. [N11]

Phân quyền cần được kiểm tra trên tài nguyên cụ thể: địa chỉ thuộc ai, đơn thuộc “buyer” nào, điểm bán thuộc “merchant” nào, thành viên có phạm vi gì. “UUID” khó đoán không phải cơ chế phân quyền. Thông tin riêng của người mua chỉ được dùng trong bối cảnh giao dịch có quyền; “public” “merchant”/“catalog” không được trở thành đường rò rỉ địa chỉ và liên hệ cá nhân.

Dữ liệu xác thực được tách khỏi nội dung minh họa. Các hình giao diện sử dụng tài khoản, giá và tên cửa hàng thử nghiệm; thông tin này phục vụ mô tả chức năng, không đại diện cho giao dịch thương mại thực tế.

[[EXPLANATORY_FIGURE:request_protection]]

Hình 4.5. Các lớp bảo vệ một yêu cầu thay đổi dữ liệu

Nguồn: mô hình hóa từ thiết kế xác thực, phân quyền và giao dịch của CoVie. [N4, N7, N11]

## 4.10. Thiết kế giao diện và trạng thái sử dụng

“Figma” là nguồn thiết kế hình thức của dự án, trong khi “API” là nguồn quy tắc và dữ liệu. Bộ tài liệu “handoff” ánh xạ màn hình vào “frame” và trạng thái. Khi giao diện cần một trường mà “API” không trả, không thể khắc phục bằng dữ liệu mẫu hoặc suy đoán. Ví dụ, lựa chọn “Shop”/“Provider” cần “discriminator” tin cậy; “review” cần “eligibility” và “order” “item” phù hợp. [N9]

Mỗi màn hình quan trọng cần trạng thái đang tải, không có dữ liệu, lỗi, không được phép và hiển thị ở nhiều kích thước. Giao diện cần giữ dữ liệu nhập khi có thể, làm rõ tác động của thao tác hủy/xóa và hiển thị yêu cầu tải lại khi “version” cũ. Những điều này được xem là một phần tính đúng đắn của trải nghiệm, không chỉ là trang trí sau khi “API” hoàn tất.

Giao diện được đánh giá thông qua ma trận đối chiếu yêu cầu thiết kế và ảnh của các màn hình thử nghiệm. Kết quả hiện có phản ánh phạm vi màn hình đã được kiểm tra; việc đánh giá toàn bộ giao diện tại phiên bản c477f30 vẫn chưa hoàn tất.
