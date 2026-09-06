# CHƯƠNG 3. PHÂN TÍCH YÊU CẦU

## 3.1. Tác nhân và quyền sử dụng

Khách chưa đăng nhập cần xem danh mục, tìm mặt hàng và đọc hồ sơ công khai. Người mua cần danh tính để lưu địa chỉ, quản lý giỏ, tạo đơn và theo dõi giao dịch. Người bán cần quản lý một hoặc nhiều đơn vị và điểm bán được cấp quyền. Quản trị viên có quyền vận hành riêng, nhưng không mặc nhiên được thay đổi tùy ý vòng đời đơn hàng. Cách chia tác nhân này được ánh xạ vào các nhóm “API” công khai, /“me”, /merchants và /admin. [N4]

| Tác nhân | Dữ liệu và hành vi chính | Ranh giới quyền |
| --- | --- | --- |
| Khách | “Catalog”, danh mục, khu vực, hồ sơ người bán công khai | Không truy cập địa chỉ riêng và giao dịch của người khác |
| Người mua | Hồ sơ, địa chỉ, giỏ, đơn, nhu cầu và thông báo của mình | Máy chủ xác minh quyền sở hữu tài nguyên |
| Thành viên người bán | “Catalog”, tồn kho, đơn, điểm bán được phân công | Kiểm tra “membership” và phạm vi “location” |
| Quản trị viên | Người dùng, “merchant”, báo cáo, “settings”, “audit” | Chỉ dùng “API” quản trị được định nghĩa |
| Tác vụ nền | “Timeout”, xử lý sự kiện, cập nhật thông báo | Thực hiện chính sách phía máy chủ, có truy vết |

Một tài khoản có thể tham gia nhiều vai trò tùy ngữ cảnh. Quyền thành viên người bán không nên được coi là một nhãn toàn cục cho phép truy cập tất cả điểm bán. Trong kiến trúc xác thực đã ghi nhận, quyền “merchant” được kiểm tra từ dữ liệu “membership”/“location”, không nhúng làm căn cứ duy nhất trong “JWT”. [N11]

[[FIGURE:actors]]

## 3.2. Yêu cầu chức năng

### 3.2.1. Danh tính, tài khoản và địa chỉ

Hệ thống hỗ trợ đăng ký lần đầu qua “Google”, thiết lập mật khẩu và đăng nhập. Sau khi hoàn tất bước xác minh danh tính “Google”, người dùng đặt mật khẩu để hoàn tất đăng ký. Các chức năng tài khoản còn gồm xem và thu hồi phiên, đăng xuất, quản lý hồ sơ và địa chỉ. Phạm vi hiện tại chưa bao gồm đăng ký độc lập bằng “email”. [N11]

Địa chỉ chứa thông tin phục vụ giao nhận và vị trí. Việc chọn địa chỉ phải giữ liên kết với người sở hữu. Một người mua không được truyền “ID” địa chỉ của người khác để “checkout”. Khi địa chỉ đã được sửa sau “preview”, “version” địa chỉ là một phần dữ liệu giúp phát hiện “checkout” đã thay đổi. [N7]

### 3.2.2. Người bán, điểm bán và “catalog”

Người bán cần tạo hồ sơ và cấu hình điểm bán. “Catalog” mô tả mặt hàng dùng chung; “variant” biểu diễn lựa chọn; “location variant” biểu diễn chào bán tại điểm bán, bao gồm giá và khả dụng. Giá và tồn kho vì thế không được gắn đơn giản vào tên sản phẩm toàn cục. Cấu trúc này hỗ trợ một sản phẩm xuất hiện ở nhiều điểm bán với điều kiện khác nhau. [N6]

Hệ thống phân biệt “PRODUCT” và “SERVICE”. Thanh lý được mô tả bằng sản phẩm có “listingKind” là “RESALE”. Quy tắc sản phẩm và dịch vụ khác nhau ở tồn kho, số lượng và thực hiện đơn. Giao diện chỉ hiển thị các thuộc tính tình trạng hàng thanh lý khi hợp đồng cung cấp dữ liệu; không suy ra phần trăm độ mới từ ảnh hoặc từ một giá trị mẫu trong thiết kế.

### 3.2.3. Tìm kiếm, giỏ hàng và “checkout”

Người mua cần tìm theo danh mục và ngữ cảnh vị trí, mở chi tiết, chọn biến thể và thêm vào giỏ. Giỏ được tách theo người mua, điểm bán và loại hàng. Một lần “checkout” không gom tùy ý sản phẩm của nhiều điểm bán thành một đơn, vì giá, điều kiện giao nhận và chủ thể thực hiện khác nhau.

Hệ thống phải xem trước lại giá, khả dụng, số lượng và thông tin giao nhận từ phía máy chủ. Sau khi người dùng chấp nhận bản xem trước, bước “commit” phải phát hiện các thay đổi xảy ra trong khoảng chờ. Kết quả tạo đơn là “PENDING_CONFIRMATION”, không phải trạng thái đã thanh toán, đã giao hàng hoặc đã được người bán chấp nhận. [N7]

### 3.2.4. Vòng đời đơn và tương tác sau đặt hàng

Người bán có thể xác nhận hoặc từ chối theo quyền và trạng thái. Các luồng tiếp theo phụ thuộc loại thực hiện: giao hàng, đến nhận hoặc dịch vụ. Người mua có thể xác nhận nhận hàng/hoàn thành khi “API” cho phép; các nhánh hủy, không đến nhận và báo sự cố có điều kiện riêng. Thao tác làm thay đổi đơn cần cập nhật dữ liệu và lịch sử sự kiện nhất quán.

Màn hình đơn cần lấy danh sách “allowedActions” từ máy chủ. Chỉ kiểm tra “status” ở “frontend” là chưa đủ, vì cùng một trạng thái có thể dẫn đến quyền khác nhau theo tác nhân, phương thức thực hiện, thời hạn hoặc dữ liệu liên quan. Việc giấu nút không thay thế phân quyền ở “endpoint” lệnh.

### 3.2.5. Nhu cầu mua, đánh giá và quản trị

“NeedRequest” giúp ghi nhận nhu cầu có loại hàng, danh mục, thông tin vị trí và thời hạn. Người bán được phép phản hồi bằng dữ liệu “catalog” hợp lệ. Người mua có thể dùng phản hồi có thể hành động để đi tiếp sang “checkout”. Cần giữ tham chiếu từ đơn tới nguồn nhu cầu nhằm giải thích vì sao nhu cầu được đánh dấu đã đáp ứng. [N8]

Đánh giá gắn với giao dịch; uy tín là dữ liệu tổng hợp có thể truy về nguồn. Thông báo cần được lưu, đánh dấu đã đọc và điều hướng an toàn. Quản trị viên cần xem báo cáo, xử lý kiểm duyệt, quản lý cấu hình có phiên bản và đọc “audit”. Các chức năng phụ thuộc hợp đồng chưa đầy đủ được xác định là phần còn cần hoàn thiện.

## 3.3. Yêu cầu phi chức năng và tiêu chí chấp nhận

| Mã | Yêu cầu | Tiêu chí kiểm tra đề xuất |
| --- | --- | --- |
| NFR-01 | Toàn vẹn giao dịch | “Commit” lỗi không để lại đơn hoặc “reservation” dở dang |
| NFR-02 | Chống bán vượt tồn | Với một đơn vị hàng cuối, tổng giữ hàng hợp lệ không vượt tồn |
| NFR-03 | Phân quyền tài nguyên | Người không có quyền không đọc/ghi được “ID” tài nguyên khác |
| NFR-04 | Khôi phục sau dữ liệu cũ | 409/412 dẫn tới tải lại hoặc “preview” lại, không báo thành công giả |
| NFR-05 | Truy vết | Lệnh quan trọng có lịch sử sự kiện hoặc “audit” tương ứng |
| NFR-06 | Khả dụng trên nhiều kích thước | Kiểm tra “mobile” và “desktop”; không tràn ngang toàn trang |
| NFR-07 | Khả năng tái lập | Có lệnh chạy, “fixture” và đầu ra kiểm thử được ghi nhận |
| NFR-08 | Bảo vệ dữ liệu cá nhân | Không đưa “cookie”, khóa riêng hoặc địa chỉ riêng vào “log” công khai |
| NFR-09 | Khả năng vận hành | Có hướng dẫn “health”, “backup”, “rollback” và xử lý sự cố |

Các tiêu chí trên là cơ sở xây dựng kế hoạch kiểm thử tại Chương 6. Mức độ đạt được phụ thuộc kết quả thực thi của từng phiên bản. Chỉ tiêu thông lượng và độ sẵn sàng của hệ thống chưa được xác nhận do chưa có thí nghiệm tải và theo dõi vận hành tương ứng.

## 3.4. Các quy tắc nghiệp vụ trọng yếu

BR-01: Mỗi giỏ hoạt động thuộc một người mua, một điểm bán và một loại hàng. BR-02: “PRODUCT” và “SERVICE” không trộn trong cùng giỏ/đơn. BR-03: số lượng dịch vụ bằng một. BR-04: giá sử dụng khi đặt hàng phải được lấy lại từ “offer” hiện tại, không tin giá máy khách gửi lên. Các quy tắc này hạn chế việc “frontend” tạo một tổ hợp mà “backend” không thể thực hiện nhất quán. [N6, N7]

BR-05: với hàng theo dõi tồn, lượng khả dụng bằng lượng tồn trừ lượng đã giữ. BR-06: giữ hàng và tạo đơn phải nhất quán trong giao dịch. BR-07: một “checkout” thay đổi không được “commit” như thể người dùng đã chấp nhận dữ liệu mới. BR-08: phí giao thỏa thuận là chưa biết, không phải miễn phí. BR-09: thông báo tạo đơn không khẳng định thanh toán thành công.

BR-10: thao tác đơn phải thuộc “allowedActions” và được kiểm tra lại ở máy chủ. BR-11: trạng thái bán hàng và trạng thái kiểm duyệt là hai khía cạnh khác nhau. BR-12: lịch sử đơn cần giữ dữ liệu giao dịch của thời điểm phát sinh thay vì phụ thuộc hoàn toàn vào “catalog” có thể sửa. Mức độ đầy đủ của “snapshot” hiện tại còn giới hạn và được đánh giá riêng. BR-13: dữ liệu tổng hợp uy tín không thay thế dữ liệu giao dịch gốc.

## 3.5. Đặc tả “use case” UC-01: Đăng nhập và quản lý phiên

Tác nhân chính là người dùng. Mục tiêu là thiết lập phiên hợp lệ và có khả năng thu hồi quyền truy cập. Tiền điều kiện phụ thuộc luồng: người đã có tài khoản có thể đăng nhập; người đăng ký lần đầu thực hiện “Google” “onboarding”. Hậu điều kiện thành công là trình duyệt có cơ chế xác thực hợp lệ và người dùng được truy cập đúng tài nguyên của mình.

Luồng chính bắt đầu từ lựa chọn đăng nhập. Với “email”/mật khẩu, máy chủ xác minh thông tin, tạo hoặc cập nhật trạng thái phiên theo cơ chế đã cài đặt và trả “cookie” phù hợp. Với “onboarding” “Google”, máy chủ xử lý định danh nhà cung cấp, yêu cầu thiết lập mật khẩu và hoàn tất liên kết danh tính theo quy tắc dự án. Sau biên xác thực, “frontend” lấy lại “CSRF” “state” trước những yêu cầu thay đổi dữ liệu.

Luồng thay thế gồm sai mật khẩu, tài khoản bị hạn chế, “callback” “OAuth” thất bại và “token” không còn hợp lệ. Khi “refresh token” đã dùng bị phát lại, kiến trúc ghi nhận thu hồi cả họ phiên liên quan. Tiêu chí chấp nhận quan trọng là “access token” còn hạn không vượt qua được phiên đã bị thu hồi tại “PostgreSQL”. Không tự động liên kết tài khoản chỉ vì hai địa chỉ “email” giống nhau. [N11]

## 3.6. Đặc tả “use case” UC-02: Tìm và chọn mặt hàng

Tác nhân là khách hoặc người mua. Tiền điều kiện là có “catalog” công khai đáp ứng điều kiện hiển thị. Người dùng mở trang chủ, chọn danh mục hoặc nhập từ khóa, điều chỉnh bộ lọc và xem kết quả. Khi mở chi tiết, hệ thống hiển thị dữ liệu mặt hàng và “offer” được hợp đồng cho phép, sau đó cho chọn lựa chọn mua.

Nếu không có kết quả, ứng dụng hiển thị trạng thái rỗng và cho đổi tiêu chí. Nếu nguồn dữ liệu lỗi, phải có thông báo thử lại; nếu vị trí bị từ chối, cần lối chọn vị trí khác. Nếu mặt hàng không còn khả dụng khi thêm hoặc “checkout”, máy chủ từ chối thao tác phù hợp. Danh sách tìm kiếm trước đó không được dùng làm bằng chứng rằng giá và hàng vẫn còn tại thời điểm mua.

Tiêu chí chấp nhận gồm bộ lọc có thể phản ánh trên “URL”, chọn danh mục không sinh tổ hợp “sort” không hợp lệ và chi tiết phân biệt đúng sản phẩm/dịch vụ/thanh lý. “Handoff” đã ghi nhận một “adapter” sắp xếp khi tìm danh mục không có từ khóa để tránh phản hồi 422. [N2]

## 3.7. Đặc tả “use case” UC-03: Xem trước và tạo đơn

Tác nhân chính là người mua. Tiền điều kiện là phiên hợp lệ, nguồn mua thuộc quyền sử dụng, “offer” khả dụng và phương thức thực hiện phù hợp. Hậu điều kiện thành công là tồn tại một đơn chờ xác nhận; nếu hàng được theo dõi thì có “reservation” tương ứng; phiên “checkout” được tiêu thụ; giỏ nguồn được xử lý theo luồng “commit”.

| Bước | Người dùng/máy khách | Máy chủ |
| --- | --- | --- |
| 1 | Chọn giỏ hoặc nguồn mua | Kiểm tra nguồn và phạm vi dữ liệu |
| 2 | Chọn giao nhận, địa chỉ và phương thức thanh toán | Xác minh địa chỉ sở hữu và khả năng điểm bán |
| 3 | Gửi yêu cầu “preview” | Tính lại giá, tổng tiền; lưu phiên và trả “token” |
| 4 | Đọc bản xem trước, chọn đặt hàng | Khóa phiên, nguồn và hàng cần theo dõi |
| 5 | Chờ kết quả | Tái kiểm tra, so sánh “snapshot”, tạo đơn, giữ hàng, ghi “outbox” |
| 6 | Mở chi tiết đơn | Trả mã đơn và “PENDING_CONFIRMATION” |

Nhánh E1: giỏ thay đổi sau “preview” làm cho “version” không khớp; hệ thống trả “checkout changed” và cần xem trước lại. Nhánh E2: giá hoặc địa chỉ đổi; hệ thống không tự chấp nhận thay người mua. Nhánh E3: hàng cuối đã được người khác giữ; đơn mới không được tạo vượt tồn. Nhánh E4: “token” sai hoặc hết hiệu lực; yêu cầu tạo “preview” mới. Nhánh E5: một bước sau tạo đơn phát lỗi trong giao dịch; toàn bộ giao dịch phải “rollback”.

Với phí giao thỏa thuận, giao diện phải trình bày phần tiền đã biết và phần chưa xác định. Ví dụ minh họa: tiền hàng 170.000 đồng và phí giao chưa thống nhất không được trình bày thành tổng cuối cùng 170.000 đồng với phí giao 0 đồng. Đây là ví dụ giải thích quy tắc, không phải số liệu từ một đơn hàng thật.

## 3.8. Đặc tả “use case” UC-04: Xử lý và hoàn tất đơn

Tác nhân chính là thành viên người bán có phạm vi điểm bán phù hợp; người mua tham gia các bước xác nhận hoặc phản hồi. Tiền điều kiện là đơn tồn tại và lệnh được máy chủ cho phép. Người bán mở chi tiết, đọc trạng thái và các hành động khả dụng, thực hiện lệnh với các “header”/“body” được hợp đồng yêu cầu. Máy chủ khóa đơn, kiểm tra quyền và chuyển trạng thái; máy khách tải lại dữ liệu sau thành công.

Với giao hàng, chuỗi tiêu biểu đi từ xác nhận tới đang giao, chờ người mua xác nhận và hoàn tất. Với đến nhận, người bán có thể đánh dấu sẵn sàng nhận. Với dịch vụ, nhà cung cấp có thể đánh dấu đã thực hiện. Sơ đồ chi tiết được rút gọn ở Chương 4; các đường tắt hoặc ngoại lệ chỉ hợp lệ khi “policy” máy chủ cho phép.

Nếu hai phía đồng thời cập nhật, phản hồi xung đột phải yêu cầu làm mới trạng thái. Nếu người mua báo sự cố sau khi người bán đánh dấu giao/thực hiện, đơn đi vào nhánh xử lý sự cố thay vì tiếp tục giải thích như một lần hoàn tất thông thường. Nếu yêu cầu hủy bị từ chối, trạng thái khôi phục phải dựa trên dữ liệu nghiệp vụ, không do máy khách đoán.

## 3.9. Đặc tả “use case” UC-05: Đăng nhu cầu và chọn phản hồi

Người mua tạo nhu cầu với loại hàng, danh mục, nội dung, vị trí và thời hạn theo hợp đồng. Máy chủ ghi nhận và xác định đối tượng người bán có thể tiếp cận. Người bán được phép gửi phản hồi gắn với “location variant” hợp lệ. Người mua mở danh sách phản hồi; khi phản hồi còn “actionable”, có thể tạo “checkout” từ nguồn đó.

Nếu nhu cầu hết hạn, bị hủy hoặc phản hồi đã rút, hành động phải được kiểm tra lại. Việc đơn được tạo chưa nhất thiết đồng nghĩa nhu cầu đã được đáp ứng; tài liệu nghiệp vụ và các “test” liên quan xác định mốc xác nhận đơn cho luồng “fulfillment” nhu cầu. Mối liên kết nguồn phải được lưu để ngăn ghi nhận không đúng hoặc không truy vết được. [N8]

Giao diện nhu cầu còn thiếu nguồn dữ liệu để lựa chọn ứng viên phản hồi cho người bán và một số thông tin về quyền hủy. Các khoảng trống hợp đồng này làm gián đoạn hành trình sử dụng dù các thành phần nghiệp vụ liên quan đã có ở “backend”. Việc hoàn thiện cần đồng bộ “API” và giao diện tương ứng. [N9]

## 3.10. Đặc tả “use case” UC-06: Kiểm duyệt và thay đổi cấu hình

Quản trị viên mở danh sách báo cáo hoặc cấu hình, lọc dữ liệu, xem chi tiết và chọn thao tác được hỗ trợ. Một thao tác hạn chế tài khoản hoặc ẩn nội dung cần lý do và xác nhận rõ. Một thay đổi cấu hình cần giá trị hợp lệ, phạm vi được cho phép và “version” phù hợp. Kết quả phải đọc lại từ máy chủ và có lịch sử/“audit” theo thiết kế.

Nếu dữ liệu đã thay đổi bởi quản trị viên khác, không tự áp dụng lại lệnh với “version” mới mà chưa xem lại nội dung. Các bảng khu vực và danh mục hiện có thể sử dụng dữ liệu đọc công khai; không vì có trang quản trị mà mặc nhiên có “CRUD” quản trị. Các màn hình “Shops”, “Listings” hoặc “Needs” thiếu “endpoint” quản trị chuyên biệt phải được ghi đúng là giới hạn hợp đồng theo “matrix” đang lưu. [N9]

## 3.11. Ma trận truy vết yêu cầu

| Yêu cầu | Thành phần triển khai | Hiện vật kiểm tra |
| --- | --- | --- |
| Danh tính và phiên | “auth”, “users” | auth-v1.md; “test” phiên; nhóm “API” “auth”/me |
| Nguồn cung theo điểm bán | “merchants”, “catalog” | V004–V005; “API” “merchant”/“catalog” |
| Toàn vẹn “checkout” | “checkout”, “inventory”, “orders” | V007–V010; “test” hàng cuối và “rollback” |
| Luồng cần mua | “needs”, “orders” | V011; “test” “fulfillment” khi “confirm” |
| Uy tín và thông báo | “reviews”, “reputation”, “notifications” | V012; “worker” và “test” tương ứng |
| Quản trị có truy vết | “moderation”, “settings”, “audit” | V013; “matrix” “Admin” và hợp đồng |
| Trải nghiệm đa màn hình | apps/web | “Handoff”, “test” “frontend”, ảnh lưu cục bộ |

Ma trận liên kết từng nhóm yêu cầu với thành phần triển khai và nguồn kiểm tra tương ứng. Kết quả và phạm vi thực thi được phân tích tại Chương 6, trong đó phân biệt các trường hợp đã có kết quả với các kịch bản còn chờ kiểm chứng.

## 3.12. Sơ đồ “use case” theo vai trò và nghiệp vụ

Các sơ đồ sau bổ sung góc nhìn chức năng cho đặc tả UC-01 đến UC-06. Tác nhân nằm ngoài khung CoVie; hình bầu dục biểu diễn mục tiêu sử dụng và đường liền biểu diễn sự tham gia. Các liên kết không biểu diễn trình tự xử lý hay xác nhận mức độ hoàn tất triển khai.

### 3.12.1. Người mua và hành trình giao dịch

Người mua sử dụng CoVie để tìm nguồn cung, tạo giao dịch và theo dõi kết quả. Sơ đồ tổng hợp các mục tiêu chính của vai trò này; chức năng khám phá còn được mở cho khách chưa đăng nhập, trong khi dữ liệu địa chỉ, giỏ, đơn và nhu cầu gắn với tài khoản.

[[USECASE_FIGURE:buyer]]

Hình 3.2. Sơ đồ “use case” Người mua và hành trình giao dịch

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

UC-02 mô tả tìm kiếm và lựa chọn mặt hàng; UC-03 mô tả xem trước và tạo đơn. UC-04 và UC-05 làm rõ tương tác sau đặt hàng và hành trình Cần mua. Quyền đánh giá phụ thuộc giao dịch đủ điều kiện; thông báo và dữ liệu cá nhân chỉ được đọc trong phạm vi của người sử dụng. [N4, N8]

### 3.12.2. Người bán và tổ chức hoạt động

Thành viên người bán tổ chức nguồn cung theo đơn vị bán hàng và điểm phục vụ. Sơ đồ tách việc chuẩn bị mặt hàng, điều kiện phục vụ và xử lý giao dịch thành các mục tiêu riêng. Một mặt hàng có thể được chào bán tại nhiều điểm với giá và tình trạng khác nhau.

[[USECASE_FIGURE:seller]]

Hình 3.3. Sơ đồ “use case” Người bán và tổ chức hoạt động

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

Mỗi chức năng yêu cầu quyền thành viên và phạm vi điểm bán phù hợp. Sơ đồ thể hiện phạm vi yêu cầu của vai trò, còn mức độ kết nối giao diện và hợp đồng được đánh giá tại Chương 6. Đặc tả UC-04 bao quát xử lý đơn; UC-05 bao quát phản hồi nhu cầu. Người bán không được truy cập đơn hoặc thay đổi nguồn cung của đơn vị khác. [N4, N6, N9]

### 3.12.3. Quản trị viên và kiểm soát hệ thống

Quản trị viên có các mục tiêu kiểm soát tài khoản, nội dung và chính sách vận hành. Tra cứu đơn hỗ trợ đối chiếu thông tin giao dịch; nhật ký giúp xác định người thực hiện, thời điểm và đối tượng của thay đổi.

[[USECASE_FIGURE:admin]]

Hình 3.4. Sơ đồ “use case” Quản trị viên và kiểm soát hệ thống

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

Thao tác quản trị chịu ràng buộc quyền, trạng thái và phiên bản dữ liệu. Việc tra cứu đơn không trao quyền tùy ý chuyển trạng thái thay người mua hoặc người bán. UC-06 trình bày luồng kiểm duyệt và cập nhật cấu hình. Những trang chưa có hợp đồng quản trị chuyên biệt vẫn là giới hạn triển khai, dù thuộc phạm vi thiết kế giao diện. [N4, N9]

### 3.12.4. UC-01 Đăng nhập và quản lý phiên

Sơ đồ phân rã UC-01 thành các mục tiêu xác lập và kiểm soát quyền truy cập. “Google” là tác nhân hệ thống bên ngoài tham gia xác minh danh tính khi đăng ký; kho phiên và cơ sở dữ liệu thuộc bên trong CoVie, không được biểu diễn như tác nhân.

[[USECASE_FIGURE:uc01]]

Hình 3.5. Sơ đồ “use case” UC-01 Đăng nhập và quản lý phiên

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

Đăng ký lần đầu gồm xác minh danh tính và thiết lập mật khẩu theo luồng hiện có; sơ đồ không bổ sung đăng ký độc lập bằng “email”. Sau đăng nhập, người dùng có thể xem và thu hồi phiên theo quyền. Các trường hợp sai mật khẩu, phiên hết hiệu lực và tài khoản bị hạn chế được xử lý theo đặc tả tại mục 3.5. Đường liên kết trên hình biểu thị sự tham gia, không biểu thị thứ tự các bước. [N11]

### 3.12.5. UC-02 Tìm và chọn mặt hàng

Khách và người mua cùng tham gia khám phá nguồn cung công khai. Người mua đã xác thực có thêm mục tiêu lưu lựa chọn vào giỏ của mình. Sơ đồ giúp phân biệt chức năng có thể truy cập trước đăng nhập với chức năng phát sinh dữ liệu giao dịch cá nhân.

[[USECASE_FIGURE:uc02]]

Hình 3.6. Sơ đồ “use case” UC-02 Tìm và chọn mặt hàng

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

Chi tiết mặt hàng phân biệt hàng hóa, dịch vụ và đồ thanh lý. Không tìm thấy kết quả, lỗi tải dữ liệu và mặt hàng không còn khả dụng là các tình huống thay thế trong cùng chức năng. Kết quả tìm kiếm không bảo đảm giá hoặc lượng hàng còn nguyên khi đặt mua; dữ liệu được kiểm tra tiếp ở UC-03. Các tiêu chí bộ lọc và lựa chọn được trình bày tại mục 3.6. [N4, N7]

### 3.12.6. UC-03 Xem trước và tạo đơn

UC-03 tập trung vào quyết định đặt hàng của người mua. Nguồn mua có thể đến từ giỏ hoặc phản hồi Cần mua còn hiệu lực. Chọn địa chỉ chỉ áp dụng khi hình thức giao nhận yêu cầu; phương thức thanh toán thể hiện lựa chọn theo hợp đồng hiện có.

[[USECASE_FIGURE:uc03]]

Hình 3.7. Sơ đồ “use case” UC-03 Xem trước và tạo đơn

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

Địa chỉ và nguồn mua phải thuộc phạm vi được sử dụng. Máy chủ tính lại dữ liệu khi xem trước và kiểm tra lại trước tạo đơn. Nếu giá, giỏ, địa chỉ hoặc lượng hàng thay đổi, người mua cần xem lại bản tính; hệ thống không tự chấp nhận thay. Kết quả thành công là đơn chờ xác nhận, chưa khẳng định đã thanh toán. Trình tự và ngoại lệ được đặc tả tại mục 3.7; sơ đồ này chỉ biểu diễn phạm vi tham gia. [N7]

### 3.12.7. UC-04 Xử lý và hoàn tất đơn

UC-04 có sự tham gia của hai phía giao dịch. Người bán tiếp nhận và cập nhật quá trình thực hiện; người mua theo dõi, xác nhận kết quả hoặc phản hồi. Các mục tiêu hủy và xử lý sự cố được đặt trong cùng phạm vi vì quyền của mỗi phía thay đổi theo tình trạng đơn.

[[USECASE_FIGURE:uc04]]

Hình 3.8. Sơ đồ “use case” UC-04 Xử lý và hoàn tất đơn

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

Liên kết tới nhóm chức năng không có nghĩa hai tác nhân được thực hiện mọi lệnh như nhau. Chẳng hạn, yêu cầu hủy và phản hồi yêu cầu hủy là các hành động khác nhau trong cùng mục tiêu; chỉ hành động được máy chủ cho phép mới hợp lệ. Giao hàng, đến nhận và dịch vụ có nhánh thực hiện riêng. Mục 3.8 và sơ đồ trạng thái tại Chương 4 mô tả các điều kiện chuyển tiếp. [N4, N7]

### 3.12.8. UC-05 Đăng nhu cầu và chọn phản hồi

Người mua công bố nhu cầu khi chưa tìm được nguồn cung phù hợp. Thành viên người bán tiếp cận nhu cầu trong phạm vi được phép và phản hồi bằng chào bán hợp lệ. Người mua lựa chọn phản hồi còn có thể sử dụng để tiếp tục sang UC-03.

[[USECASE_FIGURE:uc05]]

Hình 3.9. Sơ đồ “use case” UC-05 Đăng nhu cầu và chọn phản hồi

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

Hủy nhu cầu, rút phản hồi và chuyển sang đặt hàng đều phụ thuộc thời hạn, quyền và trạng thái hiện tại. Tạo đơn chưa tự động chứng minh nhu cầu đã được đáp ứng; mốc ghi nhận phải theo quy tắc liên kết nhu cầu với đơn. Nguồn dữ liệu chọn ứng viên phản hồi trên giao diện người bán còn hạn chế, được nêu tại mục 3.9 và Chương 6. [N8, N9]

### 3.12.9. UC-06 Kiểm duyệt và thay đổi cấu hình

UC-06 tách mục tiêu xem xét hồ sơ, thực hiện quyết định kiểm duyệt và quản lý chính sách. Quản trị viên cần đọc thông tin hiện tại trước khi thao tác; nhật ký phục vụ đối chiếu sau thay đổi. Sơ đồ không gán tác vụ kiểm duyệt cho người mua hoặc người bán.

[[USECASE_FIGURE:uc06]]

Hình 3.10. Sơ đồ “use case” UC-06 Kiểm duyệt và thay đổi cấu hình

Nguồn: mô hình hóa từ yêu cầu và đặc tả chức năng CoVie.

Các thao tác có tác động cần lý do, xác nhận và quyền tương ứng. Cập nhật cấu hình yêu cầu giá trị hợp lệ cùng phiên bản đang có hiệu lực; khi xảy ra xung đột, quản trị viên phải đọc lại trước khi tiếp tục. Nhật ký thuộc phạm vi tra cứu, không phải nơi sửa lịch sử. Quyền quản trị và giới hạn hợp đồng được trình bày tại mục 3.10. [N4, N9]
