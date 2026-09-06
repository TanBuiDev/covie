# CHƯƠNG 2. CƠ SỞ KỸ THUẬT VÀ CÔNG NGHỆ

## 2.1. Mô hình nền tảng mua bán địa phương

Trong phạm vi đề tài, “mua bán địa phương” được hiểu là mô hình kết nối nguồn cung với nhu cầu có xét đến vị trí và cách giao nhận. Vị trí không chỉ dùng để sắp xếp kết quả gần nhất mà còn liên quan đến điểm bán, địa chỉ người mua và chính sách phục vụ. Một cửa hàng có thể có nhiều điểm bán với khả năng nhận hàng, giao hàng hoặc thực hiện dịch vụ khác nhau.

CoVie phân biệt phía nguồn cung và phía nhu cầu. Phía nguồn cung sử dụng “catalog” để mô tả mặt hàng và các lựa chọn mua. Phía nhu cầu sử dụng “NeedRequest” để mô tả thứ người mua đang tìm, danh mục và thời hạn. Một phản hồi của người bán cần gắn với nguồn cung cụ thể để có thể đi tiếp sang “checkout”, thay vì chỉ là một đoạn bình luận không liên kết tới dữ liệu giao dịch. [N1, N8]

## 2.2. Kiến trúc “web” và tổ chức miền nghiệp vụ

Hệ thống sử dụng một ứng dụng “web” và một ứng dụng “API” riêng. “Web” đảm nhận điều hướng, hiển thị và tương tác; “API” kiểm tra danh tính, quyền, dữ liệu và thực hiện giao dịch. Trong “API”, các “package” được phân theo miền như “catalog”, “checkout”, “orders” và “needs”. Đây là một cách tổ chức “monolith” theo miền, không phải triển khai mỗi miền thành một “microservice” độc lập.

Lựa chọn này giúp một số thao tác cần tính nguyên tử, chẳng hạn tạo đơn đồng thời giữ hàng và ghi “outbox”, có thể nằm trong cùng giao dịch cơ sở dữ liệu. Đổi lại, các miền chia sẻ một tiến trình triển khai và cần giữ ranh giới phụ thuộc rõ ràng để hạn chế ảnh hưởng khi thay đổi. Báo cáo đánh giá kiến trúc theo nhu cầu hiện có, không giả định quy mô lưu lượng chưa được đo.

## 2.3. “Next.js”, “React” và “TypeScript”

“Next.js” cung cấp cơ chế “App Router” với “page”, “layout” và các quy ước tổ chức trạng thái tải/lỗi. Tài liệu chính thức phân biệt “Server Components” và “Client Components”, giúp tổ chức phần kết xuất và tương tác trong ứng dụng “React”. [T1] Trong CoVie, cấu trúc này phù hợp với các khu vực công khai, khu vực người mua, “Seller Center” và “Admin” có bố cục khác nhau.

“React” được sử dụng để xây dựng các thành phần giao diện tái sử dụng. “TypeScript” và “client” sinh từ “OpenAPI” giúp phát hiện một phần sai lệch hình dạng dữ liệu ngay trong quá trình phát triển. Kiểm tra kiểu không thay thế xác thực đầu vào hoặc phân quyền phía máy chủ: dữ liệu “HTTP” vẫn là dữ liệu bên ngoài và người dùng có thể gửi yêu cầu không thông qua giao diện.

Tệp apps/web/package.json tại phiên bản c477f30 khai báo “Next.js” 16.3.2, “React” 19.2.0 và “TypeScript” ^5.9.2. “React Hook Form” và “Zod” hỗ trợ tổ chức biểu mẫu và kiểm tra dữ liệu; “TanStack Query” hỗ trợ quản lý dữ liệu máy chủ tại giao diện. Đây là các phiên bản và thư viện được khai báo trong cấu hình dự án. [N5]

## 2.4. “Spring Boot” và lớp ứng dụng

“Spring Boot” cung cấp nền tảng tạo ứng dụng “Spring” có cấu hình tự động và các khả năng vận hành đi kèm. [T2] Dự án sử dụng “Spring Boot” 4.1.0 và “Java” 25 theo pom.xml. Các “dependency” liên quan gồm “Web”, “Security”, “OAuth2 Client”, “OAuth2 Resource Server”, “Validation”, “Data JPA”, “Data Redis”, “Actuator” và “Flyway”. [N5]

Lớp “API” tiếp nhận yêu cầu và trả “DTO”; lớp “application” điều phối nghiệp vụ; lớp “persistence” truy cập dữ liệu; các “model” và “policy” biểu diễn trạng thái cùng quy tắc. Ví dụ, “CheckoutCommitService” có trách nhiệm tái kiểm tra “checkout”, khóa dữ liệu cần thiết, tạo đơn và giữ tồn kho. Việc đặt ranh giới giao dịch tại “service” giúp các thao tác quan hệ được thực hiện như một đơn vị nhất quán.

Các lớp “service” tổ chức việc điều phối nghiệp vụ, còn các “model” và “policy” biểu diễn dữ liệu cùng điều kiện xử lý. Phân lớp giúp tách trách nhiệm tiếp nhận yêu cầu, xử lý giao dịch và truy cập dữ liệu; sơ đồ kiến trúc tại Chương 4 mô tả các quan hệ này.

## 2.5. “PostgreSQL”, “PostGIS” và quản lý “migration”

“PostgreSQL” lưu dữ liệu giao dịch chính. Khóa ngoại và ràng buộc kiểm tra bảo vệ các điều kiện cục bộ như tham chiếu thực thể, số lượng dương và giá trị tiền không âm. Giao dịch kết hợp với khóa hàng được dùng ở những vùng cần tuần tự hóa truy cập. Tài liệu “PostgreSQL” mô tả “SELECT FOR UPDATE” như một cơ chế khóa hàng, chặn các thao tác cập nhật hoặc khóa xung đột cho đến khi giao dịch hiện tại kết thúc. [T3]

“PostGIS” mở rộng “PostgreSQL” để xử lý dữ liệu không gian. “ST_DWithin” kiểm tra hai đối tượng có nằm trong khoảng cách cho trước hay không; đơn vị khoảng cách phụ thuộc kiểu dữ liệu và hệ tọa độ, với “geography” sử dụng mét theo tài liệu chính thức. [T4] Trong thiết kế CoVie, việc lưu điểm và cấu trúc khu vực hỗ trợ tìm nguồn cung theo vị trí. Tuy nhiên, việc có “PostGIS” không tự chứng minh mọi đường đi “checkout” đều đã dùng khoảng cách thực tế; giới hạn tích hợp được nêu ở Chương 6.

“Flyway” quản lý thay đổi cấu trúc cơ sở dữ liệu theo phiên bản. Mã nguồn gồm 13 tệp “migration”, từ V001 đến V013, với tổng cộng 66 khai báo “CREATE TABLE”. Thống kê này mô tả cấu trúc khai báo trong mã nguồn; trạng thái áp dụng trên từng môi trường phụ thuộc vào lịch sử thực thi “migration” của cơ sở dữ liệu tương ứng. [N6]

## 2.6. Kiểm soát đồng thời và tính lặp an toàn

Hai tình huống cần phân biệt là ghi đè dữ liệu cũ và gửi lại cùng một lệnh. Với tình huống thứ nhất, một tài nguyên có “version” giúp phát hiện người dùng đang cập nhật bản đã lỗi thời. “ETag” và “If-Match” được dùng trên những “endpoint” có hợp đồng tương ứng. Nếu phiên bản không còn khớp, máy khách cần tải lại dữ liệu; tự ghi đè sẽ làm mất cập nhật của người khác.

Với tình huống gửi lại, “Idempotency-Key” cho phép nhận diện một lệnh đã được xử lý trong phạm vi chính sách phía máy chủ. Mục tiêu là tránh tạo hiệu ứng nghiệp vụ trùng khi người dùng bấm lại hoặc mạng làm mất phản hồi. Không nên diễn giải cơ chế này thành “mọi API đều có thể retry vô hạn”: phạm vi khóa, dữ liệu yêu cầu, thời hạn lưu và loại lỗi vẫn phải được xử lý theo hợp đồng.

“Checkout” sử dụng thêm một “token” tham chiếu bản xem trước đã được máy chủ ghi nhận. “Token” này không đóng băng hàng tồn hoặc giá vĩnh viễn. Tại “commit”, dữ liệu hiện tại được đọc và so sánh lại; nếu thay đổi, người dùng phải xem trước lại. Việc tách “preview” và “commit” giúp người mua thấy dữ liệu có căn cứ nhưng vẫn bảo vệ hệ thống trước thay đổi xảy ra giữa hai bước. [N7]

## 2.7. “Outbox”, thông báo và “SSE”

Nếu một giao dịch tạo đơn đã “commit” nhưng gửi thông báo thất bại, thông tin giao dịch và thông tin thông báo có thể bị lệch. Dự án sử dụng bảng “outbox” và các bộ xử lý để tách việc ghi nhận ý định sự kiện khỏi việc phát thông báo. Đối với “checkout”, “ORDER_CREATED” được ghi cùng quá trình tạo đơn. Bộ xử lý phía sau có thể nhận và xử lý lại sự kiện theo cơ chế của hệ thống. [N6, N7]

“Server-Sent Events” là cơ chế máy chủ gửi sự kiện qua kết nối “HTTP” tới trình duyệt; “EventSource” là giao diện máy khách phổ biến cho luồng này. “SSE” thích hợp với các thông báo một chiều. [T6] Trong CoVie, sự kiện có thể dùng để báo dữ liệu đã thay đổi và yêu cầu tải lại thông tin có thẩm quyền. Không được coi nội dung hiển thị tạm thời sau một sự kiện là thay thế cho việc kiểm tra trạng thái và quyền ở “API”.

[[EXPLANATORY_FIGURE:event_flow]]

Hình 2.1. Luồng sự kiện từ giao dịch đến cập nhật giao diện

Nguồn: mô hình hóa từ cơ chế “outbox”, thông báo và “SSE” của CoVie. [N3]

## 2.8. “PWA” và giới hạn hoạt động ngoại tuyến

Mã “service worker” của CoVie lưu các tài nguyên “shell” như biểu tượng và trang “offline”. Với yêu cầu tới /api/v1/, “service worker” gọi mạng trực tiếp và không đưa dữ liệu nghiệp vụ vào “Cache API”. Với điều hướng trang bị mất mạng, ứng dụng có thể trả trang “offline” đã lưu. [N10]

Do đó, “hướng PWA” trong báo cáo có nghĩa là có nền tảng trải nghiệm “web” và hành vi “offline” giới hạn. Nó không có nghĩa người dùng có thể đặt hàng ngoại tuyến rồi đồng bộ sau, cũng không có nghĩa giá, tồn kho và đơn hàng được lấy từ “cache” khi mất mạng. Quyết định này phù hợp với yêu cầu không sử dụng dữ liệu giao dịch đã lỗi thời để tạo một đơn mới.

## 2.9. Lựa chọn công nghệ và đánh đổi

| Lựa chọn | Lợi ích đối với CoVie | Đánh đổi cần quản lý |
| --- | --- | --- |
| “Web” và “API” tách riêng | Tách giao diện khỏi nghiệp vụ; dùng hợp đồng chung | Cần đồng bộ kiểu dữ liệu, “cookie” và xử lý lỗi |
| “Monolith” phân theo miền | Dễ giữ giao dịch nhiều bảng trong cùng “DB” | Cần kiểm soát phụ thuộc và phạm vi thay đổi |
| “PostgreSQL” + “PostGIS” | Kết hợp dữ liệu quan hệ với dữ liệu vị trí | Cần chỉ mục và kiểm tra kế hoạch truy vấn |
| “Client” sinh từ “OpenAPI” | Giảm sai lệch khai báo “request”/“response” | Hợp đồng cũ vẫn có thể lệch với “runtime” |
| Khóa tồn kho khi “commit” | Bảo vệ hàng cuối trong truy cập đồng thời | Có chờ khóa; cần thứ tự khóa nhất quán |
| “PWA” không “cache API” nghiệp vụ | Hạn chế hiển thị dữ liệu giao dịch cũ | Không hỗ trợ tạo đơn “offline” |

Việc lựa chọn công nghệ dựa trên yêu cầu giao dịch, tổ chức dữ liệu và khả năng tích hợp của CoVie. Phạm vi đánh giá chưa bao gồm thí nghiệm đối chứng về hiệu năng giữa các “framework”; các lợi ích trong bảng được phân tích từ đặc điểm kiến trúc và cách triển khai.
