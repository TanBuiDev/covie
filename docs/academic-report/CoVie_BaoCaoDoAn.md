# CoVie — Báo cáo đồ án

# CHƯƠNG 1. TỔNG QUAN ĐỀ TÀI

## 1.1. Bối cảnh và bài toán

Hoạt động mua bán trong một khu dân cư có những đặc điểm khác với thương mại điện tử trên phạm vi toàn quốc. Khoảng cách giữa hai bên thường ngắn, nhu cầu có thể phát sinh trong ngày, người bán có quy mô nhỏ và cách giao nhận phụ thuộc từng điểm bán. Một người mua có thể cần một bữa ăn, một món đồ thanh lý hoặc một dịch vụ tại nhà. Trong các tình huống này, khả năng tìm đúng nguồn cung gần địa chỉ nhận hàng có ý nghĩa trực tiếp đối với quyết định giao dịch.

Kế hoạch sản phẩm CoVie sử dụng Vinhomes Grand Park làm khu vực thử nghiệm dự kiến. Tài liệu này mô tả việc giao dịch qua nhóm cộng đồng có thể gặp các vấn đề như bài đăng bị trôi, thông tin thiếu cấu trúc, khó tìm lại sản phẩm và phải hỏi riêng về giao hàng. Đây là nhận định đầu vào của dự án, chưa phải kết quả khảo sát thực địa được kiểm chứng bằng cỡ mẫu, bảng hỏi hoặc thống kê. Báo cáo vì vậy sử dụng chúng để xác định vấn đề thiết kế, không suy rộng thành kết luận về toàn bộ cư dân hoặc thị trường. [N1]

Ví dụ, khi tìm một mặt hàng trong nhóm trao đổi, người mua có thể phải đọc nhiều bài viết, hỏi tình trạng còn hàng, hỏi điểm nhận và hỏi phí giao. Các bước này không được biểu diễn thành dữ liệu thống nhất. Người bán cũng khó tổng hợp những nhu cầu chưa được đáp ứng. Nếu chỉ xây dựng một trang đăng tin, vấn đề về lịch sử đơn, trạng thái thực hiện và tính nhất quán tồn kho vẫn chưa được giải quyết.

Đề tài đặt ra bài toán xây dựng một nền tảng có thể biểu diễn đồng thời nguồn cung và nhu cầu mua tại địa phương; hỗ trợ tìm kiếm, đặt hàng, theo dõi thực hiện và ghi nhận phản hồi; đồng thời bảo vệ dữ liệu giao dịch khi có nhiều thao tác đồng thời. Giải pháp cần phù hợp với cả người mua trên điện thoại, người bán vận hành nhiều điểm bán và quản trị viên theo dõi hệ thống.

## 1.2. Tên đề tài và mục tiêu

Đề tài “Xây dựng nền tảng mua bán địa phương CoVie” tập trung vào tổ chức giao dịch trong cộng đồng dân cư, với kiến trúc “web” phân lớp và cơ chế kiểm soát tính nhất quán của đơn hàng.

Về trải nghiệm sản phẩm, CoVie tổ chức hành trình từ tìm kiếm đến theo dõi giao dịch trong cùng một ứng dụng. Một người mua có thể tìm rau củ, chọn cửa hàng, lưu địa chỉ nhận và tạo đơn; khi chưa có nguồn cung phù hợp, người đó có thể đăng yêu cầu Cần mua. Người bán tiếp nhận đơn theo điểm bán, cập nhật mặt hàng và quản lý điều kiện phục vụ. Quản trị viên có không gian riêng để xem xét tài khoản, phản ánh và lịch sử hoạt động. Phần giới thiệu chi tiết cùng các màn hình của từng vai trò được trình bày tại Chương 5.

Mục tiêu tổng quát là thiết kế và triển khai một ứng dụng “web” hướng “PWA” phục vụ giao dịch địa phương, trong đó giao diện người dùng được kết nối với “API” có hợp đồng rõ ràng và cơ sở dữ liệu có khả năng bảo vệ các bất biến nghiệp vụ. Phạm vi nghiên cứu tập trung vào thiết kế phần mềm và kiểm chứng chức năng trong môi trường phát triển.

| Mã | Mục tiêu cụ thể | Dấu hiệu đạt mục tiêu |
| --- | --- | --- |
| MT-01 | Chuẩn hóa sản phẩm, dịch vụ, điểm bán và nhu cầu mua | Mô hình dữ liệu, “migration” và “API” tương ứng tồn tại |
| MT-02 | Hỗ trợ hành trình tìm kiếm đến tạo đơn | Có giao diện, “API” “checkout” và bằng chứng hành trình mẫu |
| MT-03 | Bảo vệ đơn hàng và hàng tồn trước xung đột | Có khóa giao dịch, phiên “checkout” và kiểm thử bất biến |
| MT-04 | Phân quyền theo người dùng và phạm vi điểm bán | Máy chủ kiểm tra chủ thể, thành viên và quyền trên tài nguyên |
| MT-05 | Tạo nền tảng vận hành, truy vết và kiểm thử | Có “outbox”, “audit”, bộ “test” và hướng dẫn vận hành |

Mức độ hoàn thành từng mục tiêu được đánh giá ở Chương 6. Sự tồn tại của mã nguồn hoặc tài liệu cấu hình không tự chứng minh rằng hệ thống đã đạt khả năng phục vụ người dùng thật trên “production”.

[[EXPLANATORY_FIGURE:value_journey]]

Hình 1.1. Hành trình giá trị chính của nền tảng CoVie

Nguồn: tổng hợp từ phạm vi sản phẩm và hành trình người dùng CoVie. [N1, N9]

## 1.3. Đối tượng và phạm vi

Đối tượng sử dụng gồm khách chưa đăng nhập, người mua, thành viên của đơn vị bán hàng/cung cấp dịch vụ và quản trị viên. Trong nội bộ đơn vị bán hàng, quyền được gắn với thành viên và phạm vi điểm bán; một tài khoản không mặc nhiên được thao tác toàn bộ dữ liệu của mọi đơn vị. Đối tượng nghiên cứu kỹ thuật gồm mô hình dữ liệu quan hệ, hợp đồng “HTTP” “API”, kiểm soát đồng thời, quản lý phiên và tổ chức giao diện theo vai trò.

Phạm vi chức năng gồm danh tính và địa chỉ; hồ sơ người bán và điểm bán; danh mục sản phẩm, dịch vụ và thanh lý; tìm kiếm công khai; giỏ hàng; xem trước và xác nhận đặt hàng; vòng đời đơn; yêu cầu “Cần mua”; đánh giá và uy tín; thông báo; kiểm duyệt và cấu hình hệ thống. Danh sách này mô tả các miền đã hiện diện trong mã nguồn, mức độ xác minh từng nhóm chức năng được trình bày tại Chương 6. [N2, N3]

Các chức năng thanh toán qua cổng trung gian, ký quỹ, điều phối đội giao hàng, đặt lịch dịch vụ theo khung giờ và vận hành thương mại thực tế không được tuyên bố là kết quả của đề tài. Hợp đồng đơn hiện có các phương thức “COD” và “BANK_TRANSFER_NEGOTIATED”; lựa chọn phương thức không đồng nghĩa hệ thống đã xác nhận nhận tiền. Dịch vụ có số lượng bằng một cho mỗi dòng đặt và việc phối hợp thực hiện chưa phải một hệ thống đặt lịch tự động. [N6, N7]

Về môi trường, hệ thống được tổ chức để chạy cục bộ với “web”, “API”, “PostgreSQL”/“PostGIS”, “Redis” và “MinIO”. Các tài liệu triển khai, sao lưu và giám sát là đầu vào vận hành. Báo cáo chưa có bằng chứng về một môi trường “staging” hoặc “production” đang được vận hành; chưa có số liệu người dùng thật, doanh thu, tỷ lệ quay lại hoặc đo tải sản xuất.

## 1.4. Phương pháp thực hiện

Đề tài sử dụng phương pháp phát triển hướng yêu cầu và kiểm chứng qua hiện vật phần mềm. Trước hết, các luồng sản phẩm được phân tích thành tác nhân, thực thể, trạng thái và điều kiện chuyển trạng thái. Tiếp theo, thiết kế cơ sở dữ liệu xác định khóa, quan hệ, ràng buộc và dữ liệu cần lưu lịch sử. Hợp đồng “API” mô tả cách máy khách đọc dữ liệu và phát lệnh. Cuối cùng, giao diện được ánh xạ vào hợp đồng thực tế, sau đó đánh giá bằng các lớp kiểm thử và bằng chứng hình ảnh.

Phương pháp phân tích kết hợp tài liệu yêu cầu, mã nguồn, “migration” và hợp đồng “OpenAPI”. Tài liệu yêu cầu xác định mục tiêu nghiệp vụ; mã nguồn và “migration” thể hiện cách triển khai; “OpenAPI” mô tả giao tiếp giữa các thành phần. Kết quả thực thi kiểm thử được đối chiếu theo phiên bản và thời điểm để đánh giá mức độ đáp ứng yêu cầu.

Phạm vi phân tích mã nguồn là phiên bản c477f30, đối chiếu ngày 04/09/2026. Kết quả kiểm thử tại mục 6.3 thuộc đợt đánh giá ngày 26/08/2026 trên phiên bản được tài liệu dự án ghi nhận. Hai tập dữ liệu có mốc phiên bản khác nhau; kết quả của đợt kiểm thử trước chưa xác nhận các thay đổi bổ sung trong c477f30. [N2, N3, N9]

## 1.5. Đóng góp và giá trị của đề tài

Đóng góp chính nằm ở việc kết hợp các quy tắc nghiệp vụ của giao dịch địa phương trong một hệ thống thống nhất. Mô hình sản phẩm được tách khỏi giá tại điểm bán; giỏ hàng được chia theo điểm bán và loại hàng; bước “checkout” tái kiểm tra dữ liệu trước khi tạo đơn; thông tin giao dịch được lưu cùng trạng thái và lịch sử sự kiện. Cách tổ chức này giúp việc giải thích một đơn hàng không chỉ dựa vào giao diện tại một thời điểm.

Một đóng góp khác là phân biệt quyền thao tác với trạng thái hiển thị. Máy chủ cung cấp các hành động đơn hàng được phép, còn máy khách trình bày chúng. Khi hợp đồng không đủ thông tin để dựng một chức năng trong thiết kế, giao diện phải biểu diễn giới hạn đó. Đây là cơ sở để bảo vệ tính đúng đắn khi giao diện, hợp đồng và trạng thái nghiệp vụ thay đổi độc lập.

Giá trị thực nghiệm của đề tài nằm ở mô hình phần mềm có thể tái lập bằng mã nguồn, dữ liệu thử và các lệnh kiểm tra. Phạm vi đóng góp tập trung vào tích hợp nghiệp vụ và kiểm soát giao dịch. Hiệu quả sử dụng trong cộng đồng chưa được đo bằng khảo sát hoặc thử nghiệm người dùng.

## 1.6. Bố cục báo cáo

Chương 1 xác định bài toán, mục tiêu và phạm vi. Chương 2 trình bày cơ sở kỹ thuật và lý do lựa chọn công nghệ. Chương 3 phân tích tác nhân, yêu cầu và “use case”. Chương 4 mô tả kiến trúc, dữ liệu, “API” và các thuật toán xử lý nghiệp vụ. Chương 5 trình bày tổ chức triển khai cùng giao diện minh họa. Chương 6 đánh giá kiểm thử, giới hạn và mức độ đạt mục tiêu. Chương 7 kết luận và đề xuất hướng phát triển. Các phụ lục cung cấp từ điển dữ liệu chọn lọc, danh mục “API” và kịch bản tái lập để người đọc kiểm tra sâu hơn.


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


# CHƯƠNG 5. GIỚI THIỆU SẢN PHẨM VÀ GIAO DIỆN

## 5.1. Tổng quan sản phẩm và cách sử dụng

CoVie là nền tảng mua bán địa phương kết nối người có nhu cầu với cửa hàng và đơn vị cung cấp dịch vụ trong cùng khu vực. Sản phẩm tổ chức thông tin theo ba loại nguồn cung: hàng hóa, dịch vụ và đồ thanh lý. Người mua có thể tìm mặt hàng đang được đăng bán hoặc chủ động đăng yêu cầu Cần mua để nhận phản hồi từ người bán. Hai cách tiếp cận này cùng dẫn tới quá trình lựa chọn nguồn cung, tạo đơn và theo dõi thực hiện.

Không gian sử dụng được chia theo công việc của từng vai trò. Người mua tập trung vào khám phá và giao dịch; người bán quản lý mặt hàng, điểm bán và đơn nhận được; quản trị viên theo dõi tài khoản, báo cáo vi phạm, cấu hình và nhật ký. Cùng một tài khoản có thể tham gia nhiều vai trò, nhưng quyền truy cập dữ liệu vẫn được kiểm tra theo phạm vi được cấp.

Chương này trình bày sản phẩm theo hành trình sử dụng, từ màn hình đầu vào đến kết quả và các trạng thái phát sinh. Hình giao diện ứng dụng sử dụng dữ liệu thử nghiệm. Hình có ghi nguồn “Figma” thể hiện thiết kế và dữ liệu minh họa của thiết kế; mức độ triển khai và giới hạn kết nối dữ liệu được phân tích riêng tại Chương 6. [N3, N9]

| Không gian | Chức năng chính | Kết quả đối với người dùng |
| --- | --- | --- |
| Người mua | Khám phá, chi tiết và giỏ | Lựa chọn nguồn cung và tạo đơn |
| Người mua | Địa chỉ, đơn hàng và Cần mua | Theo dõi giao dịch và nhu cầu |
| Người bán | Danh mục, điểm bán và chính sách | Tổ chức nguồn cung theo nơi phục vụ |
| Người bán | Đơn hàng, uy tín và thông báo | Theo dõi và xử lý công việc |
| Quản trị | Tài khoản, báo cáo và cấu hình | Kiểm soát hoạt động theo quyền |
| Quản trị | Tra cứu giao dịch và nhật ký | Đối chiếu lịch sử thay đổi |

## 5.2. Đăng nhập và khởi tạo tài khoản

Người dùng truy cập chức năng tài khoản để đăng nhập bằng phương thức hiện có hoặc bắt đầu tạo tài khoản. Màn hình đăng nhập tập trung vào hai trường thông tin và nút hành động chính; liên kết quên mật khẩu hỗ trợ trường hợp không thể sử dụng thông tin đăng nhập cũ. Màn hình tạo tài khoản giải thích bước xác minh qua “Google” và quá trình thiết lập mật khẩu tiếp theo.

[[PRODUCT_FIGURE:1]]

Hình 5.1. Đăng nhập và khởi tạo tài khoản trên điện thoại

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Sau khi xác thực, người dùng tiếp tục vào các chức năng cá nhân như địa chỉ, đơn hàng và thông báo. Tách màn hình khởi tạo khỏi đăng nhập giúp người dùng hiểu bước đang thực hiện, đặc biệt khi tài khoản chưa hoàn tất thiết lập. Luồng xác thực và quản lý phiên được trình bày tại mục 4.9. [N11]

## 5.3. Trang chủ và khám phá nguồn cung

Trang chủ tạo điểm bắt đầu cho việc tìm hàng hóa và dịch vụ. Ô tìm kiếm được đặt ở đầu trang; các danh mục giúp người dùng duyệt khi chưa có từ khóa cụ thể. Thẻ mặt hàng hiển thị tên, giá và đơn vị bán để người mua có thể chọn đối tượng cần xem chi tiết. Thanh điều hướng cuối màn hình liên kết năm khu vực chính: trang chủ, khám phá, Cần mua, đơn hàng và tài khoản.

[[PRODUCT_FIGURE:2]]

Hình 5.2. Trang chủ và kết quả tìm kiếm trên điện thoại

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Ở trang kết quả, người mua có thể điều chỉnh từ khóa, danh mục và tiêu chí sắp xếp. Các điều kiện lọc được thể hiện gần danh sách để người dùng nhận biết phạm vi tìm kiếm hiện tại. Kết quả tìm kiếm là bước lựa chọn nguồn cung; thông tin giá và điều kiện đặt hàng tiếp tục được kiểm tra khi tạo đơn.

## 5.4. Tìm kiếm trên máy tính

Thiết kế cho máy tính đưa điều hướng, vị trí, tìm kiếm và giỏ hàng lên thanh đầu trang. Không gian ngang cho phép bố trí nhiều thẻ kết quả cạnh nhau và giữ bộ lọc gần danh sách. Người dùng có thể so sánh các nguồn cung với ít thao tác cuộn hơn so với điện thoại.

[[PRODUCT_FIGURE:3]]

Hình 5.3. Thiết kế kết quả tìm kiếm trên máy tính

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Các phiên bản điện thoại và máy tính dùng chung khái niệm danh mục, loại mặt hàng và điều kiện lọc. Sự thay đổi chủ yếu nằm ở tổ chức không gian và thứ tự trình bày. Thiết kế “Figma” còn có các màn hình bộ lọc mở, đang tải, không tìm thấy kết quả và lỗi tải dữ liệu; hai trường hợp sau được trình bày tại mục 5.26. [N9]

## 5.5. Chi tiết sản phẩm và dịch vụ

Trang chi tiết giúp người mua trả lời ba câu hỏi trước khi đặt: đang mua gì, do ai cung cấp và giá của lựa chọn hiện tại là bao nhiêu. Với sản phẩm, ảnh và thông tin mặt hàng là nội dung chính. Với dịch vụ, phần lựa chọn gói giúp phân biệt phạm vi công việc và giá tương ứng. Nút hành động được đặt gần cạnh dưới màn hình để tiếp tục giao dịch.

[[PRODUCT_FIGURE:4]]

Hình 5.4. Chi tiết sản phẩm và lựa chọn gói dịch vụ

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Sản phẩm có thể thay đổi số lượng theo điều kiện hàng hóa. Dịch vụ sử dụng một đơn vị cho lựa chọn đang đặt; thời gian thực hiện được hai bên phối hợp sau khi tạo đơn. Phạm vi hiện tại chưa bao gồm đặt lịch tự động theo khung giờ. Thông tin giao nhận và tổng tiền được xác nhận ở bước đặt hàng.

## 5.6. Thanh lý và hồ sơ cửa hàng

Mặt hàng thanh lý có nhu cầu trình bày riêng về mô tả tình trạng và vị trí giao dịch. Trang chi tiết đưa thông tin này cạnh giá và người bán để hỗ trợ quyết định mua. Hồ sơ cửa hàng tập trung vào danh tính công khai, chỉ số uy tín, đánh giá và phương thức liên hệ mà người bán cho phép công bố.

[[PRODUCT_FIGURE:5]]

Hình 5.5. Chi tiết mặt hàng thanh lý

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Người mua có thể chuyển từ chi tiết mặt hàng sang hồ sơ cửa hàng để tìm hiểu nguồn cung. Việc phân biệt đầy đủ cửa hàng với nhà cung cấp dịch vụ và liệt kê toàn bộ mặt hàng theo hồ sơ còn phụ thuộc hợp đồng dữ liệu công khai; các biến thể thiết kế không tự xác nhận phần tích hợp này đã hoàn tất.

## 5.7. Quản lý địa chỉ nhận hàng

Danh sách địa chỉ cho phép người mua lưu nhiều nơi nhận, phân biệt địa chỉ mặc định và mở thao tác chỉnh sửa. Biểu mẫu thêm địa chỉ tập hợp nhãn gợi nhớ, người nhận, số điện thoại, địa chỉ và ghi chú giao nhận. Cách tổ chức này giảm việc nhập lại thông tin khi người dùng đặt nhiều lần.

[[PRODUCT_FIGURE:6]]

Hình 5.6. Danh sách địa chỉ và biểu mẫu thêm địa chỉ

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Địa chỉ được dùng trong lựa chọn giao nhận và kiểm tra đơn hàng. Khi sửa hoặc xóa, hệ thống phải bảo đảm tài khoản đang thao tác là chủ sở hữu. Lỗi dữ liệu cần được gắn với trường nhập tương ứng để người dùng sửa đúng vị trí, đồng thời giữ lại các thông tin hợp lệ đã nhập.

## 5.8. Giỏ hàng và lựa chọn số lượng

CoVie tổ chức giỏ theo điểm bán và loại hàng. Màn hình danh sách giỏ giúp người mua chọn giao dịch cần tiếp tục, trong khi chi tiết giỏ hiển thị từng dòng hàng, số lượng và tạm tính. Việc phân chia này phù hợp khi hàng hóa và dịch vụ có cách thực hiện khác nhau hoặc khi người mua chọn nhiều đơn vị bán.

[[PRODUCT_FIGURE:7]]

Hình 5.7. Danh sách giỏ và chi tiết giỏ sản phẩm

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Người mua điều chỉnh số lượng hoặc xóa dòng hàng trước khi chuyển sang đặt hàng. Tổng tiền tại giỏ chưa thay thế kết quả kiểm tra cuối cùng từ máy chủ. Khi có thay đổi giá, tồn kho hoặc phiên bản giỏ, ứng dụng cần tải lại dữ liệu và cho người dùng kiểm tra trước khi tiếp tục.

## 5.9. Xác nhận giao nhận và tạo đơn

Màn hình xác nhận đơn tập hợp phương thức giao nhận, phương thức thanh toán và các khoản tiền. Người mua chọn nhận tại điểm bán hoặc giao đến địa chỉ nếu lựa chọn đó khả dụng. Với phí cần thỏa thuận, hệ thống phải trình bày đúng trạng thái chưa xác định thay vì mặc định phí bằng không.

[[PRODUCT_FIGURE:8]]

Hình 5.8. Xác nhận đơn và thông báo đã tạo đơn hàng

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Sau khi chấp nhận thông tin, hệ thống trả về mã đơn để mở trang theo dõi. Thông báo Đã tạo đơn hàng thể hiện đơn đã được ghi nhận; đây chưa phải xác nhận đã nhận tiền hoặc hoàn thành giao dịch. Bước xử lý tiếp theo phụ thuộc trạng thái đơn và hành động được máy chủ cho phép.

## 5.10. Theo dõi đơn hàng của người mua

Danh sách đơn giúp người mua tìm lại giao dịch, còn trang chi tiết tập hợp tiến trình, người bán, mặt hàng và các khoản tiền. Thiết kế trên máy tính phân tách phần diễn biến và phần tóm tắt để người dùng theo dõi trạng thái mà vẫn nhìn thấy nội dung giao dịch.

[[PRODUCT_FIGURE:9]]

Hình 5.9. Thiết kế chi tiết đơn hàng của người mua trên máy tính

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Mỗi trạng thái đơn có nhóm thao tác phù hợp, chẳng hạn xác nhận đã nhận hàng hoặc yêu cầu hủy khi được cho phép. Trong triển khai, các nút thao tác phụ thuộc danh sách “allowedActions” từ máy chủ. Thiết kế minh họa vị trí các nút, còn quyền thực hiện phải được xác định theo đơn đang mở. Những trạng thái hủy, phát sinh vấn đề và đóng đơn được liên hệ với mô hình vòng đời tại mục 4.8.

## 5.11. Đăng và quản lý yêu cầu Cần mua

Khi chưa tìm được nguồn cung phù hợp, người mua có thể đăng yêu cầu Cần mua. Màn hình danh sách phân nhóm nhu cầu theo tình trạng để người dùng biết yêu cầu nào còn hiệu lực. Biểu mẫu tạo nhu cầu thu thập loại hàng, danh mục, nội dung cần tìm, từ khóa, số lượng và phạm vi tìm kiếm.

[[PRODUCT_FIGURE:10]]

Hình 5.10. Trạng thái chưa có nhu cầu và biểu mẫu tạo Cần mua

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Trạng thái trống có nút Đăng nhu cầu, giúp người dùng tiếp tục ngay từ nơi chưa có dữ liệu. Sau khi gửi hợp lệ, yêu cầu được lưu và có thể nhận phản hồi từ người bán. Thời hạn và điều kiện phù hợp được xử lý theo quy tắc hệ thống; việc tạo nhu cầu chưa đồng nghĩa đã tìm được người cung cấp.

## 5.12. Phản hồi nhu cầu và lựa chọn nguồn cung

Trang phản hồi tập hợp những đề xuất liên quan đến một yêu cầu Cần mua. Mỗi phản hồi cần giúp người mua nhận biết mặt hàng, giá và người bán trước khi chuyển sang đặt hàng. Thiết kế minh họa một phản hồi còn khả dụng và nút tiếp tục giao dịch từ lựa chọn này.

[[PRODUCT_FIGURE:11]]

Hình 5.11. Thiết kế danh sách phản hồi Cần mua trên máy tính

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Khi mở phản hồi, ứng dụng phải kiểm tra lại khả năng thực hiện theo dữ liệu máy chủ. Phản hồi đã hết hiệu lực hoặc không còn hàng không được tiếp tục như một lựa chọn hợp lệ. Tạo đơn từ phản hồi và đánh dấu nhu cầu đã đáp ứng là hai thay đổi nghiệp vụ khác nhau; giao diện cần thể hiện tình trạng hiện tại của từng đối tượng.

## 5.13. Thông báo và quản lý phiên đăng nhập

Hộp thông báo tập hợp các cập nhật liên quan đến tài khoản, đơn hàng và nhu cầu. Dấu chưa đọc giúp người dùng phân biệt nội dung mới, trong khi chức năng đánh dấu đã đọc hỗ trợ quản lý danh sách. Khu vực bảo mật cho phép xem các phiên đăng nhập và thu hồi phiên cần thiết.

[[PRODUCT_FIGURE:12]]

Hình 5.12. Hộp thông báo và danh sách phiên đăng nhập

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Thông báo dẫn tới nội dung liên quan khi máy chủ cung cấp đường dẫn hợp lệ. Việc thu hồi phiên được xử lý phía máy chủ để tác động đến các yêu cầu tiếp theo của phiên đó. Hai chức năng bổ sung cho hành trình mua hàng bằng khả năng theo dõi thay đổi và kiểm soát truy cập tài khoản.

## 5.14. Không gian làm việc của người bán

Trung tâm bán hàng có năm khu vực điều hướng: tổng quan, đơn hàng, danh mục, điểm bán và cài đặt. Trên máy tính, thanh bên giữ nguyên vị trí khi chuyển chức năng; bộ chọn cửa hàng xác định đơn vị đang được quản lý. Trang tổng quan đưa trạng thái cửa hàng, số điểm bán và các đơn gần đây vào cùng một màn hình.

[[PRODUCT_FIGURE:13]]

Hình 5.13. Tổng quan trung tâm bán hàng trên máy tính

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Người bán có thể mở nhanh đơn cần xử lý hoặc chuyển đến nhóm nhu cầu phù hợp. Cách bố trí này kết nối hoạt động quản lý với giao dịch phát sinh, thay vì buộc người dùng truy cập từng trang độc lập. Dữ liệu được hiển thị theo quyền thành viên và phạm vi điểm bán được giao.

## 5.15. Điểm bán và khả năng phục vụ

Một cửa hàng có thể tổ chức nhiều điểm bán với địa chỉ và khả năng phục vụ khác nhau. Danh sách điểm bán hiển thị các hình thức nhận tại chỗ, giao hàng hoặc cung cấp dịch vụ. Người bán mở chi tiết để cập nhật tên, địa chỉ, múi giờ và những hình thức phục vụ của từng điểm.

[[PRODUCT_FIGURE:14]]

Hình 5.14. Danh sách điểm bán và biểu mẫu cấu hình điểm bán

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Các nhãn cấu hình giúp người bán nhận biết điểm nào còn thiếu thông tin. Khả năng giao nhận là dữ liệu nghiệp vụ cần được kiểm tra ở máy chủ, không chỉ là công tắc hiển thị. Việc cấu hình phạm vi địa lý và tính phí theo khoảng cách vẫn cần được đối chiếu với giới hạn triển khai nêu tại mục 6.8.

## 5.16. Phí giao hàng và giá trị đơn tối thiểu

Chính sách giao hàng được cấu hình theo điểm bán. Người bán lựa chọn cách tính phí phù hợp, chẳng hạn miễn phí, phí cố định, theo điều kiện hoặc thỏa thuận. Giá trị đơn tối thiểu được tách thành một thiết lập riêng để làm rõ điều kiện áp dụng cho giao đến khách.

[[PRODUCT_FIGURE:15]]

Hình 5.15. Cấu hình phí giao hàng và điều kiện đơn tối thiểu

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Trong ví dụ, phí cố định được nhập theo đồng Việt Nam. Thiết lập đơn tối thiểu giải thích riêng trường hợp nhận tại chỗ, tránh khiến người bán hiểu rằng mọi hình thức nhận hàng đều bị chặn. Sau khi lưu, chính sách cần được sử dụng thống nhất ở bước tính đơn; giao diện cấu hình chưa tự chứng minh toàn bộ điều kiện vận chuyển đã được áp dụng đúng.

## 5.17. Quản lý danh mục và thông tin mặt hàng

Danh mục của người bán phân biệt sản phẩm, dịch vụ và thanh lý. Danh sách cho biết các mặt hàng đang quản lý cùng trạng thái hiển thị, từ đó mở trang xem hoặc chỉnh sửa. Việc quản lý nội dung được tách khỏi góc nhìn khám phá của người mua vì cần thêm thông tin trạng thái và thao tác quản trị mặt hàng.

[[PRODUCT_FIGURE:16]]

Hình 5.16. Danh mục mặt hàng trong trung tâm bán hàng

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Ở ví dụ này, danh sách có một sản phẩm và liên kết Chỉnh sửa. Số lượng dòng trong ảnh phản ánh dữ liệu thử nghiệm, không phải giới hạn số mặt hàng của hệ thống. Cấu trúc danh mục kết nối với loại hàng, danh mục phân loại và các chào bán tại điểm bán.

## 5.18. Chỉnh sửa mặt hàng và chào bán

Trang chỉnh sửa tách thông tin chung của mặt hàng khỏi giá và trạng thái tại điểm bán. Tên và mô tả được cập nhật trong nhóm nội dung; giá, thời gian chuẩn bị và trạng thái nhận đơn được đặt trong nhóm chào bán. Cách tách này phản ánh mô hình một mặt hàng có thể được cung cấp tại nhiều điểm với điều kiện khác nhau.

[[PRODUCT_FIGURE:17]]

Hình 5.17. Biểu mẫu thông tin mặt hàng và giá tại điểm bán

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Ảnh ứng dụng thể hiện các trường chỉnh sửa và nút lưu cho từng nhóm. Người bán cần nhận được thông báo thành công hoặc lỗi sau thao tác; trường hợp dữ liệu đã thay đổi phải tải lại phiên bản mới. Ảnh minh họa bố cục hiện có, còn phạm vi xác minh chức năng chỉnh sửa và hợp đồng dữ liệu được đánh giá tại Chương 6.

## 5.19. Tiếp nhận và xử lý đơn của người bán

Danh sách đơn đến hỗ trợ lọc theo điểm bán và mở giao dịch cần xử lý. Các dòng đơn thể hiện mã, trạng thái và thời điểm để người bán xác định công việc tiếp theo. Trên máy tính, mật độ thông tin cao hơn điện thoại nhưng vẫn giữ thao tác Xem chi tiết tại từng đơn.

[[PRODUCT_FIGURE:18]]

Hình 5.18. Danh sách đơn hàng đến và bộ lọc điểm bán

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Phân công theo điểm bán giúp một thành viên chỉ làm việc với đơn thuộc phạm vi của mình. Danh sách là nơi chọn giao dịch; các thao tác xác nhận, từ chối hoặc xử lý phát sinh được đặt trong trang chi tiết, nơi có đủ thông tin và quyền hành động tương ứng.

## 5.20. Nội dung và khoản tiền trong đơn người bán

Chi tiết đơn của người bán tập hợp tiến trình, các dòng hàng, khoản tiền và hình thức thực hiện. Khi phí giao hàng cần thỏa thuận, phần thanh toán giữ riêng tiền hàng đã biết và khoản phí chưa thống nhất. Nhờ đó, người bán có thể trao đổi tiếp mà không coi tạm tính là tổng cuối cùng.

[[PRODUCT_FIGURE:19]]

Hình 5.19. Chi tiết đơn với phí giao hàng cần thỏa thuận

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Các hành động chỉ được mở khi máy chủ cho phép tại trạng thái hiện tại. Với từ chối hoặc hủy, thiết kế có bước lý do và xác nhận tác động. Nếu đơn đã được người khác xử lý, ứng dụng phải cập nhật trạng thái thay vì tiếp tục gửi thao tác dựa trên thông tin cũ.

## 5.21. Nhu cầu phù hợp và uy tín của người bán

Ngoài tiếp nhận đơn trực tiếp, người bán có thể theo dõi yêu cầu Cần mua phù hợp với điểm bán. Bộ lọc xác định điểm đang nhận thông tin; trạng thái chưa có nhu cầu giải thích rằng danh sách sẽ xuất hiện khi có kết quả phù hợp. Phần lựa chọn mặt hàng để phản hồi còn phụ thuộc nguồn dữ liệu ứng viên hợp lệ.

[[PRODUCT_FIGURE:20]]

Hình 5.20. Danh sách nhu cầu phù hợp theo điểm bán

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Uy tín và thông báo kiểm duyệt bổ sung góc nhìn về chất lượng hoạt động. Người bán xem các chỉ số tổng hợp được phép công khai và nội dung thông báo thuộc cửa hàng. Phạm vi này không bao gồm tự suy ra lịch sử vi phạm hoặc điểm uy tín từ các dữ liệu không được cung cấp.

## 5.22. Theo dõi uy tín và thông báo kiểm duyệt

Trang uy tín trình bày chỉ số tổng hợp cùng giải thích để người bán hiểu ý nghĩa của kết quả. Trang thông báo kiểm duyệt tập trung vào nội dung liên quan đến cửa hàng và hướng người dùng về danh mục để xem trạng thái mặt hàng. Hai màn hình giúp nối hoạt động quản lý hằng ngày với chất lượng phục vụ và tuân thủ nội dung.

[[PRODUCT_FIGURE:21]]

Hình 5.21. Uy tín cửa hàng và trạng thái chưa có thông báo kiểm duyệt

Nguồn: giao diện CoVie với dữ liệu thử nghiệm.

Giá trị phần trăm trong hình thuộc bộ dữ liệu thử nghiệm. Trạng thái chưa có thông báo là một trường hợp hợp lệ của giao diện, không phải lỗi tải. Khi có quyết định kiểm duyệt, nội dung và hướng xử lý phải dựa trên dữ liệu được máy chủ cung cấp cho đúng cửa hàng.

## 5.23. Quản trị tài khoản và tra cứu giao dịch

Không gian quản trị tổ chức các nhóm tài khoản, cửa hàng, đơn hàng, báo cáo, danh mục, cấu hình và nhật ký. Thiết kế trang người dùng kết hợp danh sách với vùng chi tiết để quản trị viên xem thông tin và cân nhắc thao tác tạm ngưng hoặc khôi phục tài khoản. Các thay đổi ảnh hưởng truy cập cần lý do và xác nhận.

[[PRODUCT_FIGURE:22]]

Hình 5.22. Thiết kế quản trị tài khoản người dùng

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Quyền quản trị không thay thế các quy tắc giao dịch của người mua và người bán. Chức năng tra cứu đơn được tổ chức theo hướng chỉ đọc để hỗ trợ kiểm tra. Một số trang quản trị còn thiếu hợp đồng dữ liệu độc lập; hình thiết kế trình bày cách tổ chức dự kiến, không xác nhận mọi thao tác trong thiết kế đã có dữ liệu thực thi. [N9]

Trong tra cứu đơn, quản trị viên cần liên kết mã đơn với người mua, người bán, trạng thái và lịch sử sự kiện. Thiết kế dưới đây đặt tiến trình cạnh dữ liệu giao dịch, đồng thời giải thích giới hạn chỉ đọc. Cách bố trí hỗ trợ xác định điều gì đã xảy ra mà không cung cấp một thao tác sửa trạng thái tùy ý.

[[PRODUCT_FIGURE:23]]

Hình 5.23. Thiết kế tra cứu đơn và lịch sử dành cho quản trị viên

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Lịch sử đơn là cơ sở đối chiếu khi xử lý thắc mắc hoặc sự cố. Các thông tin quan trọng gồm thời điểm, loại sự kiện và khoản tiền có liên quan. Dữ liệu cá nhân và nội dung nhạy cảm chỉ được trình bày trong phạm vi cần thiết cho công việc quản trị.

## 5.24. Kiểm duyệt cấu hình và nhật ký quản trị

Hàng đợi báo cáo tập hợp các phản ánh cần xem xét. Quản trị viên mở hồ sơ, đọc thông tin liên quan và chọn hành động được cho phép. Thiết kế phân biệt hồ sơ đang mở với hồ sơ đã thay đổi để hạn chế xử lý trên dữ liệu cũ. Đây là nơi kết nối phản ánh từ người dùng với quy trình kiểm duyệt.

[[PRODUCT_FIGURE:24]]

Hình 5.24. Thiết kế hàng đợi báo cáo và trạng thái hồ sơ thay đổi

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Quyết định kiểm duyệt cần dựa trên nội dung hồ sơ và trạng thái hiện tại. Với hành động có tác động lớn, giao diện hiển thị bước xác nhận và yêu cầu lý do. Nếu máy chủ báo xung đột phiên bản, quản trị viên cần tải lại hồ sơ trước khi tiếp tục.

Trang cấu hình nhóm các chính sách như kiểm duyệt, đánh giá và uy tín. Mỗi mục đặt giá trị cạnh phần giải thích phạm vi áp dụng và ảnh hưởng. Việc trình bày này giúp quản trị viên đánh giá thay đổi trước khi lưu, nhất là khi thiết lập có tác động toàn hệ thống.

[[PRODUCT_FIGURE:25]]

Hình 5.25. Thiết kế cấu hình chính sách hệ thống

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Giá trị mặc định, giới hạn và kiểu dữ liệu phải lấy từ định nghĩa máy chủ. Các con số trong thiết kế là dữ liệu minh họa. Khi lưu, hệ thống kiểm tra phiên bản để tránh ghi đè thay đổi vừa được người khác thực hiện; kết quả được phản hồi ngay tại mục cấu hình tương ứng.

Nhật ký quản trị phục vụ truy vết người thực hiện, hành động, đối tượng và thời điểm. Bộ lọc đặt phía trên danh sách hỗ trợ thu hẹp theo tài khoản, loại hành động, đối tượng hoặc khoảng thời gian. Thiết kế giữ mỗi bản ghi ngắn gọn để thuận tiện đối chiếu chuỗi hoạt động.

[[PRODUCT_FIGURE:26]]

Hình 5.26. Thiết kế nhật ký quản trị và bộ lọc tra cứu

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Nhật ký được trình bày ở chế độ chỉ đọc. Phần tóm tắt chỉ hiển thị nội dung cần thiết, tránh đưa nguyên dữ liệu nhạy cảm vào giao diện. Chức năng này bổ sung khả năng giải thích các thay đổi vận hành, nhưng không thay thế cơ chế phân quyền và kiểm tra nghiệp vụ trước khi hành động được thực hiện.

## 5.25. Thiết kế quản trị trên điện thoại

Thiết kế quản trị trên điện thoại chuyển bảng rộng thành thẻ thông tin và đặt các nhóm điều hướng quan trọng ở cạnh dưới. Màn hình danh sách người dùng giữ bộ tìm kiếm và lọc trạng thái; màn hình báo cáo đưa nội dung, lịch sử và thao tác xem xét vào luồng đọc dọc. [N9]

[[PRODUCT_FIGURE:27]]

Hình 5.27. Thiết kế quản trị người dùng và hồ sơ báo cáo trên điện thoại

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Các màn hình này thuộc trang thiết kế quản trị di động trong “Figma”. Bố cục cho thấy cách thích ứng với chiều ngang nhỏ; mức độ triển khai trên ứng dụng cần được xác minh riêng. Thiết kế còn có trạng thái xác nhận, lỗi dữ liệu, không đủ quyền và xung đột để hỗ trợ các tình huống ngoài luồng bình thường.

## 5.26. Trạng thái trống lỗi và thay đổi dữ liệu

Một chức năng có nhiều màn hình tùy trạng thái. Với tìm kiếm, không có kết quả vẫn là phản hồi hợp lệ: giao diện giải thích và gợi ý đăng nhu cầu. Lỗi tải dữ liệu là tình huống khác, cần nút thử lại. Thiết kế giữ cùng cấu trúc trang để người dùng nhận biết mình vẫn đang ở chức năng tìm kiếm.

[[PRODUCT_FIGURE:28]]

Hình 5.28. Thiết kế tìm kiếm khi chưa có kết quả

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Sự khác biệt giữa chưa có dữ liệu và không thể tải dữ liệu cần được thể hiện bằng nội dung hướng dẫn và hành động tiếp theo. Điều này giúp người dùng lựa chọn thay bộ lọc, đăng nhu cầu hoặc thử tải lại, thay vì gặp một vùng trống không giải thích.

Khi yêu cầu tìm kiếm không thể hoàn thành, màn hình lỗi đặt thông báo và nút thử lại trong vùng nội dung. Thanh điều hướng và ô tìm kiếm vẫn được giữ để người dùng có thể sửa từ khóa hoặc chuyển chức năng. Giao diện không diễn giải lỗi mạng thành kết luận rằng không tồn tại mặt hàng phù hợp.

[[PRODUCT_FIGURE:29]]

Hình 5.29. Thiết kế lỗi tải kết quả tìm kiếm và thao tác thử lại

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Trạng thái đang tải, không đủ quyền và lỗi biểu mẫu cũng cần được tách biệt. Với biểu mẫu, giữ lại dữ liệu đã nhập giúp giảm thao tác lặp lại. Với tác vụ đang gửi, vô hiệu hóa thao tác lặp phải đi cùng cơ chế xử lý yêu cầu trùng ở máy chủ.

Giỏ hàng có thể thay đổi khi người mua thao tác ở nhiều phiên hoặc khi thông tin mặt hàng được cập nhật. Thiết kế cảnh báo ngay phía trên nội dung giỏ, giải thích rằng dữ liệu mới đã được tải và yêu cầu người mua kiểm tra số lượng, giá trước khi tiếp tục.

[[PRODUCT_FIGURE:30]]

Hình 5.30. Thiết kế cảnh báo khi giỏ hàng đã thay đổi

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Cảnh báo có ý nghĩa nghiệp vụ vì ngăn người dùng tiếp tục từ một giả định đã cũ. Giao diện cần giữ rõ dòng hàng và số tiền mới; việc âm thầm dùng lại dữ liệu trước đó có thể dẫn tới quyết định đặt hàng không còn phù hợp. Cơ chế phiên bản và xử lý xung đột được mô tả ở Chương 4.

Tại bước xác nhận đơn, thay đổi giá hoặc thông tin nguồn cần được thể hiện trước khi người mua chấp nhận. Thiết kế đặt thông tin trước và sau cạnh cảnh báo, đồng thời đưa ra hành động kiểm tra lại. Người mua chỉ tiếp tục khi đã xem bản tính mới.

[[PRODUCT_FIGURE:31]]

Hình 5.31. Thiết kế xác nhận đơn khi giá hoặc thông tin đã thay đổi

Nguồn: thiết kế giao diện CoVie trên “Figma”. [N9]

Các trạng thái ngoại lệ làm rõ phạm vi của một chức năng vượt ra ngoài màn hình thành công. Khi mất kết nối sau thao tác tạo đơn, ứng dụng cần đối chiếu trạng thái máy chủ và cơ chế chống xử lý lặp. Khả năng khôi phục phải được kiểm thử bằng tình huống thực thi tương ứng.

## 5.27. Tổ chức triển khai và phạm vi minh họa

Ứng dụng được tổ chức thành giao diện “web” trong apps/web, máy chủ nghiệp vụ trong apps/api và hợp đồng cùng mã máy khách sinh tự động trong packages/api-client. Các cấu hình môi trường và vận hành nằm trong “infra”; lệnh hỗ trợ phát triển và kiểm thử nằm trong “scripts”. Cách tổ chức này liên kết giao diện theo vai trò với cùng một hệ thống dữ liệu và quy tắc nghiệp vụ. [N3]

Môi trường phát triển sử dụng “Next.js”, “Spring Boot”, “PostgreSQL” cùng “PostGIS”, “Redis” và “MinIO”. Lệnh pnpm dev khởi động các thành phần cục bộ sau khi đã chuẩn bị cấu hình. Giao diện sử dụng cổng 3000; “API” sử dụng cổng 8081. Ảnh ứng dụng trong chương phản ánh bố cục và dữ liệu thử của các màn hình được lựa chọn, không thay thế kết quả kiểm thử chức năng.

Thông báo cập nhật sử dụng “SSE” như tín hiệu để máy khách đọc lại trạng thái. Khi mất mạng, trang ngoại tuyến hỗ trợ giải thích tình huống nhưng không biến dữ liệu nghiệp vụ cũ thành kết quả giao dịch mới. Luồng tải tệp gồm tạo yêu cầu, tải nội dung, xác nhận hoàn tất và gắn vào đối tượng sử dụng; các bước này phải hoàn tất trước khi coi tài nguyên là sẵn sàng.

Bộ thiết kế “Figma” có các khu vực người mua và người bán trên điện thoại, máy tính, quản trị trên máy tính và quản trị trên điện thoại. Những hình được chọn thể hiện các hành trình chính và trạng thái có ý nghĩa khác nhau. Phần đánh giá tiếp theo phân biệt kết quả thực thi, phân tích mã nguồn, thiết kế giao diện và những yêu cầu còn cần xác minh; triển khai thương mại và diễn tập vận hành thực tế chưa có kết quả hoàn tất.


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


# CHƯƠNG 7. KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN

## 7.1. Kết quả đạt được

Đề tài đã hình thành một nền tảng mua bán địa phương với mô hình dữ liệu và các miền nghiệp vụ liên kết từ danh tính, nguồn cung, giỏ hàng, “checkout” tới đơn, nhu cầu, uy tín và quản trị. Kiến trúc sử dụng “Next.js”/“React” cho “web”, “Spring Boot” cho “API”, “PostgreSQL”/“PostGIS” cho dữ liệu và các thành phần hỗ trợ phù hợp với việc phát triển cục bộ. Hợp đồng “OpenAPI” giúp ghi nhận bề mặt giao tiếp và sinh “client” tiêu thụ.

Các điểm kỹ thuật nổi bật gồm tách giá theo điểm bán, phân biệt sản phẩm và dịch vụ, chấp nhận giá qua “preview”/“commit”, giữ hàng trong giao dịch, lệnh đơn theo quyền máy chủ và “outbox” cho sự kiện. Các cơ chế này tạo cơ sở xử lý giao dịch có kiểm soát; giá trị của chúng nằm ở việc bảo vệ dữ liệu trong các nhánh lỗi và xung đột, không chỉ ở đường đi thành công.

Đợt kiểm thử ngày 26/08/2026 ghi nhận kết quả đạt đối với các bộ kiểm thử “backend”, “frontend” và hành trình người mua trong môi trường cục bộ. Phân tích phiên bản c477f30 bổ sung đánh giá về cấu trúc triển khai và các hạn chế tích hợp. Kết quả này cho thấy nền tảng chức năng đã được hình thành, trong khi các thay đổi mới và khả năng vận hành thực tế còn cần kiểm chứng.

## 7.2. Những hạn chế cần tiếp tục giải quyết

Các giới hạn quan trọng nhất là tích hợp khoảng cách/“coverage” trong “checkout”, độ đầy đủ của “snapshot” lịch sử, các khoảng trống hợp đồng phục vụ giao diện và thiếu kết quả kiểm thử hồi quy tại c477f30. Bên cạnh đó, chưa có môi trường “production”, chưa có kết quả vận hành “pilot”, chưa kiểm chứng các mục tiêu tải và chưa có khảo sát người dùng để đánh giá tác động thực tế.

Phạm vi hiện tại chưa bao gồm cổng thanh toán có xác nhận tự động hoặc hệ thống điều phối giao hàng. Việc phát triển các chức năng này cần bổ sung mô hình trạng thái, hợp đồng tích hợp và kiểm thử cho các tình huống thất bại hoặc xử lý trùng.

## 7.3. Lộ trình hoàn thiện đề xuất

Ưu tiên thứ nhất là tính đúng đắn của “checkout”. Cần tái hiện vấn đề khoảng cách bằng “test”, nối kiểm tra “coverage” và “distance” thực, hoàn thiện “snapshot”, sau đó chạy lại “concurrency”/“rollback”. Đây là nhóm việc ảnh hưởng trực tiếp đến điều kiện giao dịch nên cần hoàn thành trước mở rộng phạm vi tính năng.

Ưu tiên thứ hai là đóng các khoảng trống hợp đồng đã ghi trong “matrix”. Mỗi thay đổi “backend” cần đi cùng “OpenAPI”, “client” sinh và giao diện tiêu thụ. “Provider”, “review” “eligibility”, “Need” “actionability” và các màn hình “Admin”/“Seller” cần nguồn dữ liệu đúng phạm vi. Sau khi có hợp đồng, tiến hành nghiệm thu “UI” với dữ liệu đủ, rỗng, lỗi và xung đột trên các kích thước mục tiêu.

Ưu tiên thứ ba là thực hiện một đợt kiểm thử hồi quy trên phiên bản dự kiến phát hành, ghi nhận đồng bộ mã phiên bản, cấu hình môi trường, kết quả kiểm thử và ảnh giao diện. Sau đó, hệ thống được đánh giá ở môi trường “staging” với các bài kiểm tra sao lưu, khôi phục, quay lui phiên bản và tải đồng thời. Các số đo hiệu năng cần kèm điều kiện thí nghiệm để có thể so sánh và tái lập.

Ưu tiên dài hạn là thử nghiệm người dùng ở quy mô phù hợp, đo tỷ lệ hoàn thành tác vụ, số bước thao tác và các điểm gây nhầm lẫn. Kết quả này sẽ giúp quyết định việc bổ sung lịch dịch vụ, phương thức thanh toán hoặc mở rộng cộng đồng. Không nên mặc định một hướng mở rộng chỉ vì công nghệ có thể hỗ trợ.

[[EXPLANATORY_FIGURE:roadmap]]

Hình 7.1. Lộ trình hoàn thiện đề xuất cho CoVie

Nguồn: tổng hợp từ các giới hạn và điều kiện nghiệm thu được xác định trong báo cáo.

## 7.4. Kết luận

CoVie cho thấy cách xây dựng một ứng dụng mua bán địa phương cần kết hợp thiết kế trải nghiệm với tính nhất quán dữ liệu và ranh giới quyền. Một sản phẩm được hiển thị đẹp chưa đủ để bảo đảm giá, tồn kho, quyền thao tác và trạng thái đơn đúng; ngược lại, “backend” đầy đủ miền cũng chưa đồng nghĩa hành trình sử dụng đã hoàn thiện. Kết quả của đề tài là nền tảng triển khai và kiểm chứng có thể tiếp tục phát triển, cùng các giới hạn được xác định đủ cụ thể để lập kế hoạch khắc phục và nghiệm thu.

# TÀI LIỆU THAM KHẢO

## Nguồn nội bộ dự án

[N1] CoVie. CoVie-product-plan-v0.3.md, 08/08/2026. Tài liệu xác định bối cảnh, thị trường thử nghiệm dự kiến và phạm vi sản phẩm. Đường dẫn: docs/CoVie-product-plan-v0.3.md.

[N2] CoVie. AGENTS.md và CoVie-project-status.md. Kết quả kiểm thử và trạng thái triển khai tại mốc 26/08/2026. Đối chiếu ngày 04/09/2026.

[N3] CoVie. README.md, RULES.md và cấu trúc “workspace” tại “commit” c477f30, đọc ngày 04/09/2026. Dùng cho hướng dẫn “local”, cấu trúc dự án, “SSE” và “media”.

[N4] CoVie. Hợp đồng “OpenAPI” 3.1.0 tại packages/api-client/openapi/openapi.json, phiên bản c477f30. Đối chiếu ngày 04/09/2026.

[N5] CoVie. apps/web/package.json và apps/api/pom.xml tại c477f30. Dùng cho phiên bản “framework” và khai báo “dependency”, không thay thế kiểm tra môi trường cài thực tế.

[N6] CoVie. “Flyway” “migrations” V001–V013, apps/api/src/main/resources/db/migration. Dùng cho cấu trúc dữ liệu hiện thực. Tài liệu đối chiếu thiết kế: docs/CoVie-database-design-v1.0.md.

[N7] CoVie. CheckoutCommitService.java, CheckoutPreviewService.java, ShippingEvaluator.java, OrderStatus.java và OrderAction.java tại c477f30. Dùng cho phân tích luồng “commit”, tiền, trạng thái và giới hạn tích hợp.

[N8] CoVie. CoVie-technical-plan-06-need-requests-matching-v1.0.md; “migration” V011 và các “test” “Need”/“OrderConfirmNeed” trong apps/api/src/test. Dùng để mô tả mô hình nhu cầu và nguồn đơn.

[N9] CoVie. buyer-mobile-remaining-coverage-matrix.md; seller-desktop-coverage-matrix.md; seller-desktop-engineering-handoff.md; seller-mobile-ci-verification.md; admin-desktop-coverage-matrix.md; CoVie-ui-handoff-matrix.md. Tài liệu bàn giao và phạm vi xác minh giao diện, đối chiếu ngày 04/09/2026. Thiết kế giao diện CoVie trên “Figma”: https://www.figma.com/design/4LcMXFQz3BnU1EXzBk69t2/CoVie (truy cập ngày 05/09/2026).

[N10] CoVie. apps/web/public/sw.js tại c477f30. Dùng cho hành vi “cache” “shell”, “offline” và không “cache API” nghiệp vụ.

[N11] CoVie. docs/architecture/auth-v1.md. Dùng cho “Google” “onboarding”, “cookie”/“CSRF”, phiên “PostgreSQL” và “rotation”/“reuse” của “refresh token”.

[N12] CoVie. Mã nguồn kiểm thử “backend” tại apps/api/src/test và các bộ kiểm thử giao diện tại apps/web, phiên bản c477f30. Đối chiếu ngày 04/09/2026.

[N13] CoVie. docs/operations/performance.md và docs/operations/staging-rehearsal.md. Dùng cho ngưỡng mục tiêu và điều kiện nghiệm thu vận hành, không phải số liệu “pilot” đã đo.

## Tài liệu kỹ thuật chính thức

[T1] Vercel. “Next.js” Documentation: “App Router”. https://nextjs.org/docs/app. Truy cập 04/09/2026. Tham khảo khái niệm “routing”, “layout” và “Server”/“Client Components”.

[T2] “Spring”. “Spring Boot” Reference Documentation. https://docs.spring.io/spring-boot/index.html. Truy cập 04/09/2026. Tham khảo vai trò nền tảng và cấu hình ứng dụng “Spring Boot”.

[T3] “PostgreSQL” Global Development Group. Explicit Locking. https://www.postgresql.org/docs/current/explicit-locking.html. Truy cập 04/09/2026. Tham khảo khóa hàng và hành vi giao dịch; “URL” “current” có thể chuyển phiên bản về sau.

[T4] “PostGIS” Project. “ST_DWithin”. https://postgis.net/docs/ST_DWithin.html. Truy cập 04/09/2026. Tham khảo kiểm tra khoảng cách và đơn vị của “geography”/“geometry”.

[T5] “OpenAPI” Initiative. “OpenAPI” Specification v3.1.0. https://spec.openapis.org/oas/v3.1.0.html. Truy cập 04/09/2026. Tham khảo cấu trúc hợp đồng “HTTP” “API”; số phiên bản hợp đồng CoVie cần đọc từ “file” “snapshot”.

[T6] MDN “Web” “Docs”. Using “server-sent events”. https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events. Truy cập 04/09/2026. Tham khảo “EventSource” và luồng sự kiện máy chủ tới trình duyệt.

# PHỤ LỤC A. CÀI ĐẶT VÀ KIỂM THỬ HỆ THỐNG

## A.1. Chuẩn bị môi trường “local”

Cài “JDK” 25, “Node.js” 24 “LTS”, “pnpm” 10 và “Docker” có “Compose” theo “README”. Tại thư mục gốc, cài “dependency” và tạo cấu hình “local” từ “file” mẫu nếu chưa có. Không ghi đè cấu hình “local” đã sử dụng mà chưa lưu giá trị cần giữ. Sinh khóa “JWT” dùng một lần bằng “script” dự án nếu chưa có; “script” được thiết kế từ chối ghi đè cặp khóa tồn tại. “OAuth” “Google” chỉ hoạt động khi có “client local” được cấu hình hợp lệ.

```powershell
pnpm install
# Chỉ thực hiện khi chưa có .env.local:
Copy-Item .env.example .env.local
& 'C:\Program Files\Git\bin\bash.exe' infra/scripts/generate-dev-jwt-keys.sh
pnpm dev
```

Mở http://localhost:3000 và kiểm tra “API” “health” tại http://localhost:8081/actuator/health. Nếu khởi chạy thất bại, đọc “log” trong .local/dev và kiểm tra “Docker”, “Java”, cổng hoặc biến môi trường theo “README”; không thay đổi cổng riêng lẻ mà bỏ quên “URL” “proxy” của “web”. Đăng nhập “Google” cần cấu hình riêng; các “route” khách có thể hoạt động mà chưa có “OAuth credential”.

## A.2. Dữ liệu và hành trình thử

Khi “full stack” đã hoạt động, dùng pnpm ui:verify-local để “seed fixture” và chạy hành trình “Public Buyer P0”. Lệnh này phục vụ môi trường “local”; cần đọc “README” và “script” trước khi dùng trên cơ sở dữ liệu khác. “Credential” sinh được giữ trong “file” “ignored”, không đưa vào hồ sơ hoặc “slide”.

```powershell
pnpm ui:verify-local
```

Hành trình gồm “Home”, “Search”, “Product Detail”, “Shop Profile”, “Address List”, “Service Detail”, “Resale Detail”, “Cart”, “Checkout”, “Success” và kiểm tra “Orders”. Cần lưu kết quả cùng “commit” và “timestamp” để phân biệt các lần thực thi. Ảnh được xuất trong .local/dev/visual-review; ảnh không chứa dữ liệu riêng ngoài “fixture” mới được chọn cho báo cáo.

## A.3. Lệnh kiểm thử hệ thống

Các lệnh dưới phục vụ tái lập kiểm tra hợp đồng, kiểu dữ liệu, mã nguồn, bản dựng và hành trình sử dụng. “Maven Wrapper” được chạy từ apps/api. Một số kiểm thử phụ thuộc cơ sở dữ liệu hoặc bộ dữ liệu riêng; kết quả bỏ qua được thống kê tách biệt với kết quả đạt.

```powershell
# Từ thư mục gốc:
pnpm api-client:check
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build
pnpm --filter @covie/web test:e2e

# Từ thư mục apps/api:
.\mvnw.cmd clean verify
```

Kết thúc “demo” bằng pnpm dev:stop theo hướng dẫn “launcher”. Không đưa “private key”, “file” .“env”.“local”, “cookie”, “presigned URL” hoặc dữ liệu thật vào mã nguồn nộp kèm.

## A.4. Kịch bản kiểm tra chức năng

Kịch bản chức năng bắt đầu từ một người mua tìm mặt hàng gần địa chỉ nhận. Người mua mở chi tiết, chọn lựa chọn mua, thêm vào giỏ và xem trước giá. Sau khi xác nhận, hệ thống tạo đơn ở trạng thái chờ người bán xác nhận. Mã đơn được dùng để truy cập chi tiết và kiểm tra lịch sử thao tác.

Các điểm kiểm tra gồm tính nhất quán giữa giá xem trước và dữ liệu tạo đơn, lượng tồn khả dụng sau giao dịch, quyền xem đơn và trạng thái chờ xác nhận. Với “COD” hoặc chuyển khoản thỏa thuận, việc tạo đơn chưa xác nhận đã nhận tiền. Kiểm thử cạnh tranh lượng hàng cuối được thực hiện riêng theo các tình huống tại Chương 6.


# PHỤ LỤC B. “MIGRATION” VÀ TỪ ĐIỂN DỮ LIỆU CHỌN LỌC

## B.1. Danh mục “migration”

Danh mục gồm các tệp “migration” tại phiên bản c477f30, thể hiện thứ tự thay đổi cấu trúc dữ liệu. Số lượng trong bảng được thống kê từ các lệnh “CREATE TABLE”; đây là cấu trúc khai báo trong mã nguồn, chưa phải kết quả kiểm tra trạng thái “migration” của một cơ sở dữ liệu đang chạy.

| “Migration” | Số “CREATE TABLE” |
| --- | --- |
| V001__extensions_and_foundation.sql | 0 |
| V002__identity_users_and_audit.sql | 6 |
| V003__auth_sessions_and_tickets.sql | 3 |
| V004__location_merchant_media.sql | 9 |
| V005__catalog_and_search.sql | 14 |
| V006__idempotency_and_outbox.sql | 2 |
| V007__delivery_shipping_inventory.sql | 7 |
| V008__carts_and_checkout_sessions.sql | 3 |
| V009__order_creation_foundation.sql | 5 |
| V010__order_lifecycle.sql | 2 |
| V011__need_matching.sql | 3 |
| V012__trust_notifications_outbox.sql | 8 |
| V013__moderation_system_settings_admin.sql | 4 |

Tổng cộng có 66 khai báo bảng trong 13 “migration”. Các phiên bản “migration” sau có thể điều chỉnh bảng và ràng buộc đã tạo. Từ điển dưới đây trình bày các cột chọn lọc theo câu lệnh tạo ban đầu; cấu trúc sau cùng được xác định bằng toàn bộ chuỗi thay đổi tương ứng.

## B.2. Các bảng theo “migration”

**V001__extensions_and_foundation.sql**
Không tạo bảng mới; xem các thay đổi khác trong “migration”.

**V002__identity_users_and_audit.sql**
“users”, “user_identities”, “system_role_assignments”, “user_contact_methods”, “user_addresses”, “audit_logs”

**V003__auth_sessions_and_tickets.sql**
“auth_sessions”, “auth_refresh_tokens”, “auth_action_tickets”

**V004__location_merchant_media.sql**
“location_areas”, “media_assets”, “merchant_profiles”, “merchant_memberships”, “merchant_locations”, “merchant_member_locations”, “merchant_contact_methods”, “merchant_location_hours”, “merchant_location_hour_exceptions”

**V005__catalog_and_search.sql**
“categories”, “category_attributes”, “category_attribute_options”, “tags”, “catalog_items”, “catalog_item_attribute_values”, “catalog_item_tags”, “option_groups”, “option_values”, “variants”, “variant_option_values”, “location_variants”, “catalog_item_media”, “merchant_media”

**V006__idempotency_and_outbox.sql**
“idempotency_records”, “outbox_events”

**V007__delivery_shipping_inventory.sql**
“delivery_coverages”, “delivery_coverage_areas”, “shipping_policies”, “shipping_rules”, “inventory_balances”, “inventory_reservations”, “inventory_movements”

**V008__carts_and_checkout_sessions.sql**
“carts”, “cart_items”, “checkout_sessions”

**V009__order_creation_foundation.sql**
“orders”, “order_items”, “product_fulfillments”, “service_fulfillments”, “order_events”

**V010__order_lifecycle.sql**
“order_cancellation_requests”, “order_issues”

**V011__need_matching.sql**
“need_requests”, “need_responses”, “need_response_items”

**V012__trust_notifications_outbox.sql**
“reviews”, “catalog_item_rating_stats”, “merchant_rating_stats”, “reputation_order_outcomes”, “user_reputation_stats”, “merchant_reputation_stats”, “notifications”, “notification_deliveries”

**V013__moderation_system_settings_admin.sql**
“system_settings”, “system_setting_history”, “moderation_cases”, “moderation_actions”

## B.3. “users” — Tài khoản người dùng

Nguồn tạo bảng: V002__identity_users_and_audit.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “display_name” | “varchar”(120) | “NOT NULL” |
| “created_at” | “timestamptz” | “NOT NULL” |
| “updated_at” | “timestamptz” | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.4. “merchant_profiles” — Đơn vị bán hàng hoặc cung cấp dịch vụ

Nguồn tạo bảng: V004__location_merchant_media.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “name” | “varchar”(180) | “NOT NULL” |
| “created_at” | “timestamptz” | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.5. “merchant_locations” — Điểm bán và khả năng thực hiện

Nguồn tạo bảng: V004__location_merchant_media.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “merchant_profile_id” | “uuid” | “NOT NULL”; “FK” → “merchant_profiles”.“id” |
| “name” | “varchar”(150) | “NOT NULL” |
| “address_text” | “text” | “NOT NULL” |
| “geo_point” | “geography”(“Point”,4326) | “NOT NULL” |
| “pickup_enabled” | “boolean” | “NOT NULL” |
| “seller_delivery_enabled” | “boolean” | “NOT NULL” |
| “service_enabled” | “boolean” | “NOT NULL” |
| “minimum_delivery_order” | “bigint” | Cho phép “NULL” theo khai báo |
| “version” | “bigint” | “NOT NULL” |

## B.6. “catalog_items” — Mô tả mặt hàng

Nguồn tạo bảng: V005__catalog_and_search.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “merchant_profile_id” | “uuid” | “NOT NULL”; “FK” → “merchant_profiles”.“id” |
| “item_type” | “varchar”(20) | “NOT NULL” |
| “listing_kind” | “varchar”(20) | “NOT NULL” |
| “name” | “varchar”(220) | “NOT NULL” |
| “primary_category_id” | “uuid” | “NOT NULL”; “FK” → “categories”.“id” |

## B.7. “location_variants” — Chào bán tại một điểm bán

Nguồn tạo bảng: V005__catalog_and_search.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “merchant_location_id” | “uuid” | “NOT NULL”; “FK” → “merchant_locations”.“id” |
| “variant_id” | “uuid” | “NOT NULL”; “FK” → “variants”.“id” |
| “price” | “bigint” | “NOT NULL” |
| “currency_code” | “char”(3) | “NOT NULL” |
| “inventory_mode” | “varchar”(20) | Cho phép “NULL” theo khai báo |
| “is_available” | “boolean” | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.8. “inventory_balances” — Tồn và lượng đã giữ

Nguồn tạo bảng: V007__delivery_shipping_inventory.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “location_variant_id” | “uuid” | “PK”; “NOT NULL”; “FK” → “location_variants”.“id” |
| “stock_on_hand” | “bigint” | “NOT NULL” |
| “reserved_quantity” | “bigint” | “NOT NULL” |
| “updated_at” | “timestamptz” | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.9. “carts” — Giỏ theo người, điểm bán và loại hàng

Nguồn tạo bảng: V008__carts_and_checkout_sessions.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “merchant_location_id” | “uuid” | “NOT NULL”; “FK” → “merchant_locations”.“id” |
| “cart_type” | “varchar”(20) | “NOT NULL” |
| “cart_status” | “varchar”(20) | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.10. “checkout_sessions” — Phiên chấp nhận “checkout”

Nguồn tạo bảng: V008__carts_and_checkout_sessions.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “token_hash” | “varchar”(64) | “NOT NULL”; “UNIQUE” |
| “user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “source_type” | “varchar”(30) | “NOT NULL” |
| “cart_version” | “bigint” | Cho phép “NULL” theo khai báo |
| “request_snapshot” | “jsonb” | “NOT NULL” |
| “preview_snapshot” | “jsonb” | “NOT NULL” |
| “expires_at” | “timestamptz” | “NOT NULL” |
| “consumed_at” | “timestamptz” | Cho phép “NULL” theo khai báo |

## B.11. “orders” — Đơn và tiền tại thời điểm giao dịch

Nguồn tạo bảng: V009__order_creation_foundation.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “order_code” | “varchar”(32) | “NOT NULL”; “UNIQUE” |
| “order_type” | “varchar”(20) | “NOT NULL” |
| “buyer_user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “merchant_location_id” | “uuid” | “NOT NULL”; “FK” → “merchant_locations”.“id” |
| “status” | “varchar”(60) | “NOT NULL” |
| “source_type” | “varchar”(20) | “NOT NULL” |
| “payment_method” | “varchar”(40) | “NOT NULL” |
| “subtotal” | “bigint” | “NOT NULL” |
| “shipping_fee” | “bigint” | Cho phép “NULL” theo khai báo |
| “known_total” | “bigint” | “NOT NULL” |
| “total_amount” | “bigint” | Cho phép “NULL” theo khai báo |
| “version” | “bigint” | “NOT NULL” |

## B.12. “order_items” — Dòng đơn và dữ liệu lịch sử

Nguồn tạo bảng: V009__order_creation_foundation.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “order_id” | “uuid” | “NOT NULL”; “FK” → “orders”.“id” |
| “location_variant_id” | “uuid” | Cho phép “NULL” theo khai báo; “FK” → “location_variants”.“id” |
| “item_type” | “varchar”(20) | “NOT NULL” |
| “quantity” | “bigint” | “NOT NULL” |
| “unit_price” | “bigint” | “NOT NULL” |
| “line_total” | “bigint” | “NOT NULL” |
| “item_snapshot” | “jsonb” | “NOT NULL” |

## B.13. “need_requests” — Nhu cầu phía người mua

Nguồn tạo bảng: V011__need_matching.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “buyer_user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “need_type” | “varchar”(20) | “NOT NULL” |
| “category_id” | “uuid” | “NOT NULL”; “FK” → “categories”.“id” |
| “expires_at” | “timestamptz” | “NOT NULL” |

## B.14. “reviews” — Đánh giá theo giao dịch

Nguồn tạo bảng: V012__trust_notifications_outbox.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “order_item_id” | “uuid” | “NOT NULL”; “FK” → “order_items”.“id” |
| “buyer_user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “rating” | “smallint” | “NOT NULL” |
| “comment” | “text” | Cho phép “NULL” theo khai báo |
| “created_at” | “timestamptz” | “NOT NULL” |

# PHỤ LỤC C. DANH MỤC “API”

Hợp đồng “OpenAPI” 3.1.0 gồm 119 đường dẫn và 144 thao tác “HTTP”. Danh mục dưới đây nhóm các đường dẫn theo tài nguyên. Tham số, nội dung yêu cầu, phản hồi và cơ chế xác thực được mô tả tại từng thao tác trong tệp hợp đồng; kết quả kiểm thử “API” được trình bày tại Chương 6.

## C.1. Nhóm “admin”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/admin/audit-logs |
| “POST” | /api/v1/admin/maintenance/ratings/rebuild |
| “POST” | /api/v1/admin/maintenance/reputation/rebuild |
| “GET” | /api/v1/admin/merchants |
| “GET” | /api/v1/admin/merchants/{merchantId} |
| “POST” | /api/v1/admin/merchants/{merchantId}/restore |
| “POST” | /api/v1/admin/merchants/{merchantId}/suspend |
| “GET” | /api/v1/admin/moderation-cases |
| “POST” | /api/v1/admin/moderation-cases/bulk-actions |
| “GET” | /api/v1/admin/moderation-cases/{caseId} |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/approve |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/close |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/hide-content |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/reject |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/restore-content |
| “GET” | /api/v1/admin/orders |
| “GET” | /api/v1/admin/orders/{orderId} |
| “GET” | /api/v1/admin/orders/{orderId}/events |
| “GET” | /api/v1/admin/settings |
| “GET”, “PATCH” | /api/v1/admin/settings/{settingKey} |
| “GET” | /api/v1/admin/settings/{settingKey}/history |
| “GET” | /api/v1/admin/users |
| “GET” | /api/v1/admin/users/{userId} |
| “POST” | /api/v1/admin/users/{userId}/restore |
| “POST” | /api/v1/admin/users/{userId}/suspend |

## C.2. Nhóm “auth”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/auth/csrf |
| “GET” | /api/v1/auth/google/start |
| “POST” | /api/v1/auth/login |
| “POST” | /api/v1/auth/logout |
| “POST” | /api/v1/auth/onboarding/set-password |
| “POST” | /api/v1/auth/password/reset-with-google |
| “POST” | /api/v1/auth/refresh |

## C.3. Nhóm “catalog”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/catalog/items |
| “GET” | /api/v1/catalog/items/{itemId} |
| “GET” | /api/v1/catalog/items/{itemId}/reviews |
| “GET” | /api/v1/catalog/merchants/{merchantId} |

## C.4. Nhóm “categories”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/categories |
| “GET” | /api/v1/categories/{categoryId} |

## C.5. Nhóm “checkouts”

| “Method” | Đường dẫn |
| --- | --- |
| “POST” | /api/v1/checkouts/commit |
| “POST” | /api/v1/checkouts/preview |

## C.6. Nhóm “health”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/health |

## C.7. Nhóm “location-areas”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/location-areas |

## C.8. Nhóm “me”

| “Method” | Đường dẫn |
| --- | --- |
| “GET”, “PATCH” | /api/v1/me |
| “GET”, “POST” | /api/v1/me/addresses |
| “GET”, “DELETE”, “PATCH” | /api/v1/me/addresses/{addressId} |
| “GET” | /api/v1/me/carts |
| “POST” | /api/v1/me/carts/items |
| “GET” | /api/v1/me/carts/{cartId} |
| “DELETE”, “PATCH” | /api/v1/me/carts/{cartId}/items/{itemId} |
| “GET”, “POST” | /api/v1/me/contact-methods |
| “DELETE”, “PATCH” | /api/v1/me/contact-methods/{contactMethodId} |
| “GET” | /api/v1/me/events |
| “GET”, “POST” | /api/v1/me/merchants |
| “GET”, “POST” | /api/v1/me/need-requests |
| “GET” | /api/v1/me/need-requests/{needRequestId} |
| “POST” | /api/v1/me/need-requests/{needRequestId}/cancel |
| “GET” | /api/v1/me/need-requests/{needRequestId}/responses |
| “GET” | /api/v1/me/notifications |
| “POST” | /api/v1/me/notifications/mark-all-read |
| “POST” | /api/v1/me/notifications/{notificationId}/mark-read |
| “GET” | /api/v1/me/orders |
| “POST” | /api/v1/me/reviews |
| “GET” | /api/v1/me/security/sessions |
| “POST” | /api/v1/me/security/sessions/revoke-others |
| “DELETE” | /api/v1/me/security/sessions/{sessionId} |

## C.9. Nhóm “media”

| “Method” | Đường dẫn |
| --- | --- |
| “POST” | /api/v1/media/uploads |
| “POST” | /api/v1/media/{mediaId}/complete |

## C.10. Nhóm “merchants”

| “Method” | Đường dẫn |
| --- | --- |
| “GET”, “PATCH” | /api/v1/merchants/{merchantId} |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/catalog-items |
| “PATCH” | /api/v1/merchants/{merchantId}/catalog-items/{itemId} |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/activate |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/archive |
| “PATCH” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/location-offers/{locationVariantId} |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/pause |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId} |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/contacts |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/contacts/{contactId} |
| “GET” | /api/v1/merchants/{merchantId}/inventory |
| “POST” | /api/v1/merchants/{merchantId}/inventory/bulk-set-stock |
| “POST” | /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/set-stock |
| “POST” | /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-in |
| “POST” | /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-out |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/locations |
| “GET”, “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/locations/{locationId} |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId} |
| “GET”, “PUT” | /api/v1/merchants/{merchantId}/locations/{locationId}/hours |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies/{policyId} |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/members |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/members/{memberId} |
| “PUT” | /api/v1/merchants/{merchantId}/members/{memberId}/locations |
| “GET” | /api/v1/merchants/{merchantId}/need-requests |
| “GET” | /api/v1/merchants/{merchantId}/orders |
| “GET” | /api/v1/merchants/{merchantId}/reviews |

## C.11. Nhóm “need-requests”

| “Method” | Đường dẫn |
| --- | --- |
| “POST” | /api/v1/need-requests/{needRequestId}/responses |
| “PUT” | /api/v1/need-requests/{needRequestId}/responses/{responseId} |
| “POST” | /api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw |

## C.12. Nhóm “orders”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/orders/{orderId} |
| “POST” | /api/v1/orders/{orderId}/accept-cancellation |
| “POST” | /api/v1/orders/{orderId}/cancel |
| “POST” | /api/v1/orders/{orderId}/confirm |
| “POST” | /api/v1/orders/{orderId}/confirm-completed |
| “POST” | /api/v1/orders/{orderId}/confirm-received |
| “POST” | /api/v1/orders/{orderId}/contest-no-show |
| “GET” | /api/v1/orders/{orderId}/events |
| “POST” | /api/v1/orders/{orderId}/mark-delivered |
| “POST” | /api/v1/orders/{orderId}/mark-out-for-delivery |
| “POST” | /api/v1/orders/{orderId}/mark-ready-for-pickup |
| “POST” | /api/v1/orders/{orderId}/mark-service-performed |
| “POST” | /api/v1/orders/{orderId}/reject |
| “POST” | /api/v1/orders/{orderId}/reject-cancellation |
| “POST” | /api/v1/orders/{orderId}/report-buyer-no-show |
| “POST” | /api/v1/orders/{orderId}/report-delivery-issue |
| “POST” | /api/v1/orders/{orderId}/report-service-issue |
| “POST” | /api/v1/orders/{orderId}/request-cancellation |

## C.13. Nhóm “reports”

| “Method” | Đường dẫn |
| --- | --- |
| “POST” | /api/v1/reports |

# PHỤ LỤC D. GHI NHẬN PHIÊN BẢN VÀ HÌNH MINH HỌA

Phạm vi phân tích kỹ thuật sử dụng mã nguồn tại “commit” c477f308b71a512b8002fe9d26866708527f8da1, đối chiếu ngày 04/09/2026. Thống kê “migration”, hợp đồng “API” và tệp kiểm thử được xác định từ mã nguồn. Kết quả thực thi ngày 26/08/2026 được trình bày riêng tại Chương 6 theo đúng phạm vi phiên bản đã ghi nhận.

Các sơ đồ tác nhân, kiến trúc, dữ liệu, “checkout” và trạng thái đơn biểu diễn mô hình cùng luồng xử lý của CoVie. Sơ đồ “ERD” tập trung vào các quan hệ cốt lõi; danh mục cấu trúc dữ liệu chi tiết được bổ sung tại Phụ lục B.

Chương 5 sử dụng 29 ảnh giao diện ứng dụng với dữ liệu thử nghiệm và 14 hình thiết kế từ “Figma”, trình bày trong 31 hình minh họa. Các hình bao quát người mua, người bán, quản trị và những trạng thái giao diện tiêu biểu. Hình thiết kế được phân biệt bằng chú thích nguồn; dữ liệu trong hình không đại diện cho khách hàng hoặc hoạt động thương mại thực tế.
