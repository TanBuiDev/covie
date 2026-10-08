# UI & Icon System Rules (Strict)

## 1. Tuyệt đối KHÔNG sử dụng Unicode Emoji trong UI
- **Cấm hoàn toàn:** Không dùng bất kỳ emoji Unicode nào (`📦`, `🛠️`, `🥦`, `🍱`, `🪜`, `🧻`, `🏢`, `🏘️`, `🛵`, `🚗`, `⚡`, `🌅`, `⏰`, `📅`, `👁️`, `💡`, `🏆`, `📍`, `💬`, `📡`, `🛒`, `💰`, `🏷️`, `✨`...) làm biểu tượng trên giao diện (nút bấm, badge, thẻ danh mục, gợi ý nhanh, tabs, header, xem trước, danh sách, mẹo, thông báo...).
- **Giải pháp chuẩn:** Luôn sử dụng icon từ `@phosphor-icons/react` (ví dụ: `Package`, `Wrench`, `Tag`, `MapPin`, `Clock`, `ChatText`, `Trophy`, `Buildings`, `Lightbulb`, `Eye`, `Check`, `CheckCircle`...) hoặc icon chuẩn hệ thống (`CoVieIcon`, SVG vector).
- Emoji chỉ được phép xuất hiện khi người dùng tự tay gõ vào phần nội dung văn bản (User-generated content).

## 2. Nền giao diện Pure White (`#ffffff`), không dùng nền xám cục bộ
- Không dùng nền xám (`#f8fafc`, `#f1f5f9`) cho toàn bộ khung nội dung chính (main container) hoặc thẻ bao bọc (card wrapper) gây cảm giác tối tăm, cũ kỹ.
- Nền khung nội dung chính phải là màu trắng thuần `#ffffff` sạch sẽ, phân tách bằng border viền tinh tế `#e2e8f0` hoặc shadow nhẹ.

## 3. Tính nhất quán của Form và bố cục (Layout Consistency)
- **Không nhân đôi control điều khiển:** Mỗi trường dữ liệu chỉ có DUY NHẤT một phần tử tương tác trực quan (ví dụ: đã có nút chuyển đổi phân đoạn `[Sản phẩm] [Dịch vụ]` thì không hiển thị thêm dropdown `Loại nhu cầu`).
- **Cân bằng lưới form (Grid Balance):** Các cột trong cùng một hàng form grid (`.need-form__grid`) phải có chiều cao và cấu trúc tương đương. Không nhồi nhét nhiều control con vào một cột gây lệch layout.
- **Phân đoạn (Segments):** Phải được style chỉnh chu, bo góc viền nhẹ `#e2e8f0`, nền `#f1f5f9`, hover mượt mà trên cả Mobile và Desktop.
- **Bản xem trước (Live Preview):** Thiết kế thanh lịch, màu sắc hài hòa với thương hiệu CoVie (xanh ngọc / xám tinh tế), không dùng màu tương phản chói gắt hoặc lệch tông thiết kế.
