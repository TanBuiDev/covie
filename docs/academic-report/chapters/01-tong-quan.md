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
