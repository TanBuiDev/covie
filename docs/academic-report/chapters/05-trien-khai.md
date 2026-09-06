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
