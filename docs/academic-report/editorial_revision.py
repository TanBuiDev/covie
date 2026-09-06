"""Apply the report's reviewed academic prose without rebuilding its layout."""
from pathlib import Path
import json, re, shutil
from docx import Document
from docx.oxml.ns import qn

BASE=Path(__file__).resolve().parent
# Prefixes identify entire paragraphs; technical data and tables are retained.
REVISIONS=[
('Nguồn: sơ đồ giải thích do bản báo cáo', 'Nguồn: mô hình hóa từ mã nguồn và tài liệu thiết kế CoVie.'),
('Nguồn: ảnh local lưu sẵn', 'Nguồn: bộ ảnh giao diện thử nghiệm CoVie tại .local/dev/visual-review.'),
('Các script triển khai, sao lưu và khôi phục tạo', 'Các script vận hành đã có trong mã nguồn; diễn tập staging và triển khai production chưa có kết quả hoàn tất.'),
('Giới hạn giao diện hiện tại gồm', 'Giao diện nhu cầu còn thiếu nguồn dữ liệu để lựa chọn ứng viên phản hồi cho người bán và một số thông tin về quyền hủy. Các khoảng trống hợp đồng này làm gián đoạn hành trình sử dụng dù các thành phần nghiệp vụ liên quan đã có ở backend. Việc hoàn thiện cần đồng bộ API và giao diện tương ứng. [N9]'),
('Ở đường đi CheckoutPreviewService đã kiểm tra,', 'Trong CheckoutPreviewService tại phiên bản c477f30, evaluator được gọi với distanceMeters bằng 0. Phương thức validateFulfillmentCapability kiểm tra cờ khả năng và yêu cầu địa chỉ, nhưng bước revalidate chưa thể hiện đầy đủ kiểm tra vùng phục vụ địa lý. Điều kiện giao hàng theo khoảng cách vì thế còn là giới hạn tích hợp cần khắc phục và kiểm thử.'),
('Snapshot item trong đường code được đọc', 'Snapshot của mặt hàng hiện chưa chứa đầy đủ tên, lựa chọn và hình ảnh theo mô hình lịch sử dự kiến. Snapshot liên hệ được khởi tạo rỗng và snapshot chính sách từ evaluator chưa có nội dung. Các trường tiền đã được lưu, nhưng việc hiển thị toàn bộ thông tin đơn cũ vẫn có thể phụ thuộc vào dữ liệu hiện tại.'),
('Hình kiến trúc thể hiện quan hệ trách nhiệm,', 'Hình kiến trúc thể hiện trách nhiệm và quan hệ giữa các thành phần. Trong môi trường cục bộ, web sử dụng cổng 3000, API sử dụng 8081 và PostgreSQL được công bố tại 5433. Redis sử dụng cổng 6379 và MinIO sử dụng cổng 9000. Cấu hình mạng cho môi trường triển khai thực tế được quản lý riêng.'),
('Các tiêu chí trên được dùng để tổ chức kiểm thử;', 'Các tiêu chí trên là cơ sở xây dựng kế hoạch kiểm thử tại Chương 6. Mức độ đạt được phụ thuộc kết quả thực thi của từng phiên bản. Chỉ tiêu thông lượng và độ sẵn sàng của hệ thống chưa được xác nhận do chưa có thí nghiệm tải và theo dõi vận hành tương ứng.'),
('Các đánh đổi trên là lập luận kỹ thuật', 'Việc lựa chọn công nghệ dựa trên yêu cầu giao dịch, tổ chức dữ liệu và khả năng tích hợp của CoVie. Phạm vi đánh giá chưa bao gồm thí nghiệm đối chứng về hiệu năng giữa các framework; các lợi ích trong bảng được phân tích từ đặc điểm kiến trúc và cách triển khai.'),
('Tài liệu dự án ghi nhận các kết quả kiểm thử local', 'Kết quả kiểm thử ngày 26/08/2026 ghi nhận 460 kiểm thử backend đạt, 1 trường hợp bỏ qua và 103 kiểm thử frontend đạt, cùng các hành trình người mua được kiểm tra cục bộ. Phân tích mã nguồn c477f30 xác định những hạn chế về khoảng cách giao hàng, dữ liệu lịch sử và hợp đồng giao diện. Đề tài hình thành nền tảng chức năng cho giao dịch địa phương; khả năng vận hành production và hiệu quả với người dùng thực tế còn cần đánh giá.'),
('Historical project records report local verification', 'Project records dated 26 August 2026 report 460 passing backend tests, one skipped case and 103 passing frontend tests, alongside local buyer journey checks. Analysis of source version c477f30 identifies limitations in delivery-distance integration, historical data snapshots and UI contracts. The project establishes a functional foundation for local transactions; production operation and effectiveness with real users remain to be evaluated.'),
('Tên đề tài đề xuất:', 'Đề tài “Xây dựng nền tảng mua bán địa phương CoVie” tập trung vào tổ chức giao dịch trong cộng đồng dân cư, với kiến trúc web phân lớp và cơ chế kiểm soát tính nhất quán của đơn hàng.'),
('Khi biên soạn báo cáo,', 'Phương pháp phân tích kết hợp tài liệu yêu cầu, mã nguồn, migration và hợp đồng OpenAPI. Tài liệu yêu cầu xác định mục tiêu nghiệp vụ; mã nguồn và migration thể hiện cách triển khai; OpenAPI mô tả giao tiếp giữa các thành phần. Kết quả thực thi kiểm thử được đối chiếu theo phiên bản và thời điểm để đánh giá mức độ đáp ứng yêu cầu.'),
('Phiên bản mã nguồn được đọc khi lập báo cáo', 'Phạm vi phân tích mã nguồn là phiên bản c477f30, đối chiếu ngày 04/09/2026. Kết quả kiểm thử tại mục 6.3 thuộc đợt đánh giá ngày 26/08/2026 trên phiên bản được tài liệu dự án ghi nhận. Hai tập dữ liệu có mốc phiên bản khác nhau; kết quả của đợt kiểm thử trước chưa xác nhận các thay đổi bổ sung trong c477f30. [N2, N3, N9]'),
('Giá trị thực nghiệm của đề tài', 'Giá trị thực nghiệm của đề tài nằm ở mô hình phần mềm có thể tái lập bằng mã nguồn, dữ liệu thử và các lệnh kiểm tra. Phạm vi đóng góp tập trung vào tích hợp nghiệp vụ và kiểm soát giao dịch. Hiệu quả sử dụng trong cộng đồng chưa được đo bằng khảo sát hoặc thử nghiệm người dùng.'),
('Tại snapshot đã đọc,', 'Tệp apps/web/package.json tại phiên bản c477f30 khai báo Next.js 16.3.2, React 19.2.0 và TypeScript ^5.9.2. React Hook Form và Zod hỗ trợ tổ chức biểu mẫu và kiểm tra dữ liệu; TanStack Query hỗ trợ quản lý dữ liệu máy chủ tại giao diện. Đây là các phiên bản và thư viện được khai báo trong cấu hình dự án. [N5]'),
('Không phải mọi lớp có hậu tố Service', 'Các lớp service tổ chức việc điều phối nghiệp vụ, còn các model và policy biểu diễn dữ liệu cùng điều kiện xử lý. Phân lớp giúp tách trách nhiệm tiếp nhận yêu cầu, xử lý giao dịch và truy cập dữ liệu; sơ đồ kiến trúc tại Chương 4 mô tả các quan hệ này.'),
('Hệ thống cần hỗ trợ quy trình Google onboarding,', 'Hệ thống hỗ trợ đăng ký lần đầu qua Google, thiết lập mật khẩu và đăng nhập. Sau khi hoàn tất bước xác minh danh tính Google, người dùng đặt mật khẩu để hoàn tất đăng ký. Các chức năng tài khoản còn gồm xem và thu hồi phiên, đăng xuất, quản lý hồ sơ và địa chỉ. Phạm vi hiện tại chưa bao gồm đăng ký độc lập bằng email. [N11]'),
('Ma trận này liên kết yêu cầu với nơi tìm bằng chứng.', 'Ma trận liên kết từng nhóm yêu cầu với thành phần triển khai và nguồn kiểm tra tương ứng. Kết quả và phạm vi thực thi được phân tích tại Chương 6, trong đó phân biệt các trường hợp đã có kết quả với các kịch bản còn chờ kiểm chứng.'),
('Lưu lịch sử order_events', 'Lịch sử order_events lưu chuỗi thao tác trên đơn. Các bảng cancellation và issue bổ sung ngữ cảnh yêu cầu, thông tin sự cố và kết quả giải quyết. Giao diện quản trị hỗ trợ đọc đơn và lịch sử; việc chuyển trạng thái vẫn tuân theo các lệnh nghiệp vụ và điều kiện phân quyền. [N9]'),
('Khóa JWT riêng, refresh token, cookie', 'Dữ liệu xác thực được tách khỏi nội dung minh họa. Các hình giao diện sử dụng tài khoản, giá và tên cửa hàng thử nghiệm; thông tin này phục vụ mô tả chức năng, không đại diện cho giao dịch thương mại thực tế.'),
('Nguồn thiết kế Figma được ghi', 'Đánh giá mức độ đáp ứng thiết kế dựa trên ma trận bàn giao và bộ ảnh giao diện đã lưu. Phạm vi này chưa bao gồm một đợt đối chiếu trực quan mới cho toàn bộ màn hình tại phiên bản c477f30.'),
('README yêu cầu JDK', 'Môi trường phát triển sử dụng JDK 25, Node.js 24 LTS, pnpm 10 và Docker Compose. Sau khi cấu hình môi trường và khóa JWT, lệnh pnpm dev khởi động hệ thống cục bộ. Web sử dụng cổng 3000; API sử dụng cổng 8081 để tránh xung đột với dịch vụ đang chiếm cổng 8080. [N3]'),
('Địa chỉ được dùng lại giữa chọn vị trí', 'Địa chỉ được sử dụng trong lựa chọn vị trí và checkout, với các thuộc tính chủ sở hữu, tọa độ và phiên bản dữ liệu. Giao diện Buyer Mobile đã có chức năng tạo và chỉnh sửa địa chỉ. Ma trận bàn giao vẫn ghi trạng thái VERIFICATION_PENDING, vì vậy phần triển khai này còn chờ xác minh đầy đủ trong môi trường chạy. [N9]'),
('Thanh điều hướng dưới màn hình', 'Thanh điều hướng dưới màn hình kết nối các khu vực trang chủ, khám phá, cần mua, đơn hàng và tài khoản. Ở kích thước điện thoại, vị trí này giúp các chức năng chính tiếp tục hiện diện khi nội dung chi tiết kéo dài. Các hình minh họa thể hiện bố cục của bộ dữ liệu thử nghiệm cục bộ.'),
('Ảnh là hiện vật local có sẵn', 'Hình minh họa thể hiện kết quả tạo đơn với dữ liệu thử nghiệm cục bộ. Mã đơn và ngày trong hình thuộc bộ dữ liệu thử. Trạng thái này xác nhận việc ghi nhận đơn hàng trong luồng minh họa; việc thanh toán và thực hiện đơn là các bước nghiệp vụ tiếp theo.'),
('Một bản demo nên bắt đầu', 'Môi trường minh họa sử dụng dữ liệu thử gồm người mua, điểm bán, sản phẩm, dịch vụ và mặt hàng thanh lý. Lệnh pnpm ui:verify-local tạo bộ dữ liệu và thực hiện hành trình Public Buyer P0. Thông tin đăng nhập thử được lưu cục bộ trong .local/dev/ui-fixture.json và được loại khỏi hệ thống quản lý phiên bản.'),
('Kịch bản trình bày nên mở đầu', 'Hành trình minh họa tập trung vào tìm kiếm mặt hàng, lựa chọn nguồn cung, xem trước giá và tạo đơn. Các tình huống giá thay đổi hoặc nhiều người cùng đặt lượng hàng cuối được phân tích qua thuật toán checkout và các kịch bản kiểm thử giao dịch. Phạm vi Seller và Admin được đánh giá riêng theo chức năng và hợp đồng dữ liệu hiện có.'),
('Các script deploy, backup, rollback', 'Các script triển khai, sao lưu và khôi phục tạo nền tảng cho vận hành hệ thống. Phạm vi kết quả hiện tại chưa có một đợt diễn tập staging hoặc triển khai production hoàn tất; khả năng khôi phục và triển khai thực tế còn chờ đánh giá trong môi trường tương ứng.'),
('Trong phiên biên soạn này', 'Chưa có kết quả chạy lại toàn bộ Maven, Vitest, build và Playwright cho phiên bản c477f30 trong tập dữ liệu đánh giá. Vì vậy, kết quả tại mục 6.3 chỉ xác nhận phạm vi của đợt kiểm thử ngày 26/08/2026; các thay đổi sau mốc này còn cần kiểm thử hồi quy.'),
('Nguồn: AGENTS.md và docs/CoVie-project-status.md', 'Nguồn: AGENTS.md và docs/CoVie-project-status.md, mốc kiểm thử 26/08/2026. Các bộ kiểm thử có phạm vi và đơn vị thống kê khác nhau nên được trình bày riêng. Những trường hợp bỏ qua chưa được tính là kiểm thử đạt. [N2]'),
('Ảnh đã lưu có mốc file', 'Bộ ảnh giao diện minh họa sử dụng dữ liệu thử có mốc thời gian riêng. Chưa có thông tin liên kết đầy đủ từng ảnh với phiên bản thực thi của đợt kiểm thử 26/08/2026. Do đó, ảnh được sử dụng để mô tả chức năng và bố cục, còn kết quả kiểm thử được đánh giá theo bản ghi thực thi tương ứng.'),
('Các test case sau được thiết kế', 'Bảng sau trình bày 24 kịch bản kiểm thử nghiệm thu, bao gồm dữ liệu đầu vào theo tình huống, kết quả mong đợi và trạng thái kiểm chứng. Các kịch bản còn chờ thực thi tại phiên bản c477f30; tài khoản và danh mục thử được tách khỏi dữ liệu sử dụng thực tế.'),
('Staging rehearsal bao gồm', 'Kế hoạch diễn tập staging bao gồm migration, luồng có xác thực, giới hạn tần suất yêu cầu, sao lưu và khôi phục vào cơ sở dữ liệu khác, quay lui phiên bản và kiểm tra cảnh báo. Chưa có kết quả hoàn tất đợt diễn tập này; Dockerfile và workflow hiện là thành phần chuẩn bị triển khai. [N13]'),
('Ảnh hưởng là không nên demo', 'Hạn chế này ảnh hưởng đến độ chính xác của chính sách phí theo khoảng cách và điều kiện phục vụ theo vùng. Hướng khắc phục là truyền khoảng cách thực, tích hợp kiểm tra vùng phục vụ trong bước revalidate và bổ sung kiểm thử cho địa chỉ trong vùng, ngoài vùng cũng như chính sách thay đổi giữa preview và commit.'),
('Kết quả 26/08 không tự xác nhận', 'Phạm vi đánh giá bị giới hạn bởi khác biệt phiên bản giữa kết quả kiểm thử ngày 26/08/2026 và mã nguồn c477f30. Bộ ảnh cục bộ chưa liên kết đầy đủ với phiên bản thực thi. Ngoài ra, chưa có số đo tải pilot, khảo sát người dùng, xác nhận thanh toán tự động hoặc kết quả diễn tập staging hoàn tất.'),
('Kết quả phù hợp để trình bày', 'Đề tài đã triển khai các miền nghiệp vụ chính và có kết quả kiểm thử cục bộ tại mốc 26/08/2026. Mức độ hoàn thiện hiện tại còn giới hạn ở tích hợp dữ liệu địa lý, lưu lịch sử giao dịch, hợp đồng giao diện và vận hành. Hệ thống chưa được xác nhận sẵn sàng phục vụ trên môi trường production.'),
('Bộ tài liệu và bằng chứng lịch sử', 'Đợt kiểm thử ngày 26/08/2026 ghi nhận kết quả đạt đối với các bộ kiểm thử backend, frontend và hành trình người mua trong môi trường cục bộ. Phân tích phiên bản c477f30 bổ sung đánh giá về cấu trúc triển khai và các hạn chế tích hợp. Kết quả này cho thấy nền tảng chức năng đã được hình thành, trong khi các thay đổi mới và khả năng vận hành thực tế còn cần kiểm chứng.'),
('Đồ án không triển khai một cổng', 'Phạm vi hiện tại chưa bao gồm cổng thanh toán có xác nhận tự động hoặc hệ thống điều phối giao hàng. Việc phát triển các chức năng này cần bổ sung mô hình trạng thái, hợp đồng tích hợp và kiểm thử cho các tình huống thất bại hoặc xử lý trùng.'),
('Ưu tiên thứ ba là tái lập', 'Ưu tiên thứ ba là thực hiện một đợt kiểm thử hồi quy trên phiên bản dự kiến phát hành, ghi nhận đồng bộ mã phiên bản, cấu hình môi trường, kết quả kiểm thử và ảnh giao diện. Sau đó, hệ thống được đánh giá ở môi trường staging với các bài kiểm tra sao lưu, khôi phục, quay lui phiên bản và tải đồng thời. Các số đo hiệu năng cần kèm điều kiện thí nghiệm để có thể so sánh và tái lập.'),
('Các lệnh dưới là hướng dẫn thực thi', 'Các lệnh dưới phục vụ tái lập kiểm tra hợp đồng, kiểu dữ liệu, mã nguồn, bản dựng và hành trình sử dụng. Maven Wrapper được chạy từ apps/api. Một số kiểm thử phụ thuộc cơ sở dữ liệu hoặc bộ dữ liệu riêng; kết quả bỏ qua được thống kê tách biệt với kết quả đạt.'),
('Mở đầu bằng một tình huống tìm mua', 'Kịch bản chức năng bắt đầu từ một người mua tìm mặt hàng gần địa chỉ nhận. Người mua mở chi tiết, chọn lựa chọn mua, thêm vào giỏ và xem trước giá. Sau khi xác nhận, hệ thống tạo đơn ở trạng thái chờ người bán xác nhận. Mã đơn được dùng để truy cập chi tiết và kiểm tra lịch sử thao tác.'),
('Trong phần hỏi đáp,', 'Các điểm kiểm tra gồm tính nhất quán giữa giá xem trước và dữ liệu tạo đơn, lượng tồn khả dụng sau giao dịch, quyền xem đơn và trạng thái chờ xác nhận. Với COD hoặc chuyển khoản thỏa thuận, việc tạo đơn chưa xác nhận đã nhận tiền. Kiểm thử cạnh tranh lượng hàng cuối được thực hiện riêng theo các tình huống tại Chương 6.'),
('Báo cáo được tổng hợp ngày', 'Phạm vi phân tích kỹ thuật sử dụng mã nguồn tại commit c477f308b71a512b8002fe9d26866708527f8da1, đối chiếu ngày 04/09/2026. Thống kê migration, hợp đồng API và tệp kiểm thử được xác định từ mã nguồn. Kết quả thực thi ngày 26/08/2026 được trình bày riêng tại Chương 6 theo đúng phạm vi phiên bản đã ghi nhận.'),
('Các sơ đồ tác nhân, kiến trúc,', 'Các sơ đồ tác nhân, kiến trúc, dữ liệu, checkout và trạng thái đơn biểu diễn mô hình cùng luồng xử lý của CoVie. Sơ đồ ERD tập trung vào các quan hệ cốt lõi; danh mục cấu trúc dữ liệu chi tiết được bổ sung tại Phụ lục B.'),
('Logo HUTECH trên bìa được lấy', 'Các hình trang chủ, chi tiết sản phẩm và tạo đơn sử dụng bộ dữ liệu thử trong môi trường cục bộ. Chúng minh họa giao diện của hành trình người mua, không đại diện cho dữ liệu khách hàng hoặc hoạt động thương mại thực tế. Nguồn hình được ghi dưới từng hình minh họa.'),
('Thông tin bìa do người dùng cung cấp:', 'Các hình trang chủ, chi tiết sản phẩm và tạo đơn sử dụng bộ dữ liệu thử trong môi trường cục bộ. Chúng minh họa giao diện của hành trình người mua, không đại diện cho dữ liệu khách hàng hoặc hoạt động thương mại thực tế. Nguồn hình được ghi dưới từng hình minh họa.'),
('Các công nghệ, thư viện, tài liệu và mã nguồn được sử dụng cần', 'Tôi ghi nhận các nguồn tài liệu, công nghệ và thư viện được sử dụng trong đề tài. Báo cáo phân biệt kết quả kiểm thử đã ghi nhận, phân tích mã nguồn và các kịch bản chưa thực thi. Tôi có sử dụng ChatGPT/Codex để hỗ trợ tổng hợp tài liệu, diễn đạt, xây dựng sơ đồ và dàn trang; tôi chịu trách nhiệm về nội dung được trình bày.'),
('Mục lục có liên kết và số trang', ''),
('[N2] CoVie.', '[N2] CoVie. AGENTS.md và CoVie-project-status.md. Kết quả kiểm thử và trạng thái triển khai tại mốc 26/08/2026. Đối chiếu ngày 04/09/2026.'),
('[N4] CoVie.', '[N4] CoVie. Hợp đồng OpenAPI 3.1.0 tại packages/api-client/openapi/openapi.json, phiên bản c477f30. Đối chiếu ngày 04/09/2026.'),
('[N9] CoVie.', '[N9] CoVie. buyer-mobile-remaining-coverage-matrix.md; seller-desktop-coverage-matrix.md; seller-desktop-engineering-handoff.md; seller-mobile-ci-verification.md; admin-desktop-coverage-matrix.md; CoVie-ui-handoff-matrix.md. Tài liệu bàn giao và phạm vi xác minh giao diện, đối chiếu ngày 04/09/2026.'),
('[N12] CoVie.', '[N12] CoVie. Mã nguồn kiểm thử backend tại apps/api/src/test và các bộ kiểm thử giao diện tại apps/web, phiên bản c477f30. Đối chiếu ngày 04/09/2026.'),
('Danh mục được trích trực tiếp từ các file migration', 'Danh mục gồm các tệp migration tại phiên bản c477f30, thể hiện thứ tự thay đổi cấu trúc dữ liệu. Số lượng trong bảng được thống kê từ các lệnh CREATE TABLE; đây là cấu trúc khai báo trong mã nguồn, chưa phải kết quả kiểm tra trạng thái migration của một cơ sở dữ liệu đang chạy.'),
('Hợp đồng lưu có 119 path', 'Hợp đồng OpenAPI 3.1.0 gồm 119 đường dẫn và 144 thao tác HTTP. Danh mục dưới đây nhóm các đường dẫn theo tài nguyên. Tham số, nội dung yêu cầu, phản hồi và cơ chế xác thực được mô tả tại từng thao tác trong tệp hợp đồng; kết quả kiểm thử API được trình bày tại Chương 6.'),
]
PHRASES={
 'Những màn hình mà hợp đồng còn thiếu không được bổ sung dữ liệu giả chỉ để đủ hình thức trình bày.':'Các chức năng phụ thuộc hợp đồng chưa đầy đủ được xác định là phần còn cần hoàn thiện.',
 'Dashboard cũng không được tạo số liệu tổng giả khi không có API thống kê tương ứng.':'Dashboard còn thiếu nguồn API thống kê để cung cấp đầy đủ các số liệu tổng hợp.',
 '5.9. Chuẩn bị một bản demo tái lập':'5.9. Môi trường và hành trình thử nghiệm',
 'A.4. Kịch bản trình bày đề xuất':'A.4. Kịch bản kiểm tra chức năng',
 'A.3. Lệnh kiểm tra phiên bản nộp cuối':'A.3. Lệnh kiểm thử hệ thống',
 'PHỤ LỤC A. HƯỚNG DẪN TÁI LẬP VÀ DEMO':'PHỤ LỤC A. CÀI ĐẶT VÀ KIỂM THỬ HỆ THỐNG',
 'PHỤ LỤC C. DANH MỤC API TRONG SNAPSHOT':'PHỤ LỤC C. DANH MỤC API',
 'Chờ chạy tại HEAD':'Chưa thực thi tại c477f30',
 'Giá trị đối với hồ sơ đồ án':'Mục đích sử dụng',
 'trong quy trình nộp phiên bản cuối':'trong quy trình tích hợp phần mềm',
 'không có nghĩa mọi màn hình đã được nghiệm thu trực quan và mọi luồng đã được chạy lại trên phiên bản hiện tại':'mức độ xác minh từng nhóm chức năng được trình bày tại Chương 6',
 'Kiểm tra schema và contract tại bản nộp':'Kiểm tra schema và contract tại c477f30',
 'Chạy lại ở commit bảo vệ':'Kiểm thử hồi quy tại c477f30',
 'thiếu regression mới tại HEAD':'thiếu kết quả kiểm thử hồi quy tại c477f30',
 'nhưng tên test không thay thế kết quả chạy tại HEAD':'kết quả thực thi tại c477f30 còn chờ cập nhật',
}

def rewrite(text):
    for prefix,value in REVISIONS:
        if text.startswith(prefix):return value
    for a,b in PHRASES.items():text=text.replace(a,b)
    return text

def apply_editorial(doc):
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    for heading in doc.paragraphs:
        if heading.style.name == "Heading 1" and heading.text.startswith("CHƯƠNG "):
            heading.alignment = WD_ALIGN_PARAGRAPH.CENTER
    changed=[]
    # Includes cached TOC hyperlink text, while retaining bookmarks and page fields.
    for p in doc.paragraphs:
        ts=p._p.xpath('.//w:t'); old=''.join(t.text or '' for t in ts)
        new=rewrite(old)
        if new!=old:
            if not new:
                p._p.getparent().remove(p._p)
            elif any(old.startswith(prefix) for prefix,_ in REVISIONS):
                ts[0].text=new
                for t in ts[1:]:t.text=''
            else:
                for t in ts:t.text=rewrite(t.text or '')
            changed.append({'before':old,'after':new})
    for table in doc.tables:
        for row in table.rows:
            for cell in row.cells:
                for p in cell.paragraphs:
                    for r in p.runs:r.text=rewrite(r.text)
    from academic_prose import apply as apply_academic_prose
    apply_academic_prose(doc)
    from terminology import apply_terminology
    apply_terminology(doc)
    return changed

if __name__=='__main__':
    target=BASE/'CoVie_BaoCaoDoAn.docx'
    backup=BASE.parents[1]/'.local/report-before-editorial.docx'
    if not backup.exists():shutil.copy2(target,backup)
    d=Document(target);changes=apply_editorial(d);d.save(target)
    for p in list((BASE/'chapters').glob('*.md'))+[BASE/'front-matter.md']:
        lines=p.read_text(encoding='utf-8').splitlines()
        p.write_text('\n'.join(rewrite(line) for line in lines)+'\n',encoding='utf-8')
    body='\n\n'.join(p.read_text(encoding='utf-8') for p in sorted((BASE/'chapters').glob('*.md')))
    (BASE/'CoVie_BaoCaoDoAn.md').write_text('# CoVie — Báo cáo đồ án\n\n'+body,encoding='utf-8')
    nav=BASE/'navigation-pages.json';data=json.loads(nav.read_text(encoding='utf-8'))
    for e in data['entries'].values():e['text']=rewrite(e['text'])
    nav.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    (BASE/'editorial-review.json').write_text(json.dumps({'changed_paragraphs':len(changes),'changes':changes},ensure_ascii=False,indent=2),encoding='utf-8')
    print('Revised paragraphs:',len(changes))
