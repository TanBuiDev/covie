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
