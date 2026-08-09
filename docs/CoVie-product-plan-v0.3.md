# CoVie — Kế hoạch sản phẩm Hyperlocal Marketplace

**Phiên bản:** V0.3  
**Ngày cập nhật:** 08/08/2026  
**Thị trường thử nghiệm ban đầu:** Vinhomes Grand Park  
**Mô hình:** Hyperlocal Marketplace / Local Commerce Platform  
**Trạng thái:** Bản kế hoạch sản phẩm gốc để tiếp tục chốt User Flow, UI/UX, Database, API và Technical Plan.

---

# 1. Tóm tắt dự án

**CoVie** là nền tảng mua bán hyperlocal giúp người bán và người mua trong cùng khu vực hoặc ở gần nhau dễ dàng tìm thấy nhau, giao dịch và thỏa thuận việc giao nhận.

Thị trường thử nghiệm đầu tiên là **Vinhomes Grand Park**, nơi hoạt động mua bán hiện nay thường diễn ra trên các nhóm Facebook cư dân.

CoVie giải quyết các vấn đề phổ biến của hình thức mua bán qua Facebook:

- Người bán phải chờ admin duyệt bài.
- Bài đăng dễ bị trôi.
- Người bán phải đăng lại nhiều lần.
- Người mua khó tìm lại sản phẩm đã từng thấy.
- Các bài “cần mua” và “đang bán” bị lẫn giữa hàng chục hoặc hàng trăm bài khác.
- Người mua khó biết shop nào đang mở, còn hàng hoặc có giao tới mình không.
- Không có cấu trúc thống nhất cho shop, sản phẩm, dịch vụ và đơn hàng.
- Phí ship thường phải hỏi thủ công.
- Không có hệ thống matching giữa nhu cầu mua và nguồn cung.
- Không có hệ thống đánh giá và lịch sử giao dịch rõ ràng.

CoVie không cố trở thành Shopee hoặc Grab ngay từ đầu. Mục tiêu V1 là giải quyết tốt phần **discovery → matching → order → delivery rules → kết nối hai bên**.

---

# 2. Định vị sản phẩm

CoVie không phải nền tảng chỉ dành riêng cho cư dân chung cư.

CoVie là một **hyperlocal marketplace mở**, trong đó community như Vinhomes Grand Park chỉ là một đơn vị tổ chức và là khu vực thử nghiệm đầu tiên.

Người ngoài community vẫn có thể:

- mua hàng;
- bán hàng;
- cung cấp dịch vụ;
- giao hàng vào community;
- giao dịch với người trong community;

miễn là người bán và người mua tự thống nhất với nhau.

Định vị ngắn gọn:

> **CoVie giúp người mua tìm đúng người đang bán thứ mình cần ở gần mình, đồng thời giúp người bán tiếp cận đúng người đang có nhu cầu mua.**

Các điểm khác biệt chính:

1. **Hyperlocal** — ưu tiên giao dịch gần nhau.
2. **Community-aware** — có cấu trúc community, phân khu và tòa.
3. **Open marketplace** — không giới hạn chỉ cư dân.
4. **Delivery-aware** — biết shop có giao tới buyer hay không.
5. **Seller-controlled delivery** — seller tự thiết lập vùng giao, phí ship và điều kiện freeship.
6. **Structured buying demand** — “Cần mua” không chỉ là bài đăng, mà là dữ liệu có cấu trúc để hệ thống matching.
7. **Multi-location seller** — một seller có thể có nhiều điểm bán.
8. **Flexible transaction** — V1 không bắt CoVie đứng giữa việc thanh toán và giao nhận chi tiết.

---

# 3. Thị trường thử nghiệm

## 3.1. Community đầu tiên

**Vinhomes Grand Park** là khu vực thử nghiệm đầu tiên.

CoVie không phụ thuộc vào thương hiệu hoặc cấu trúc của riêng Grand Park.

Kiến trúc dữ liệu phải hỗ trợ nhiều community:

```text
CoVie
│
├── Vinhomes Grand Park
│   ├── The Rainbow
│   ├── The Origami
│   ├── The Beverly
│   └── ...
│
├── Vinhomes Central Park
├── Masteri Centre Point
├── Lumière Boulevard
└── Community khác
```

## 3.2. Chiến lược launch

Không launch toàn Grand Park ngay.

Ưu tiên:

1. Chọn một phân khu có hoạt động mua bán sôi động.
2. Tuyển nhóm seller thường xuyên bán trong Facebook Group.
3. Đảm bảo marketplace có đủ sản phẩm/dịch vụ trước khi kéo nhiều buyer.
4. Dùng Facebook làm kênh acquisition.
5. Theo dõi giao dịch thực tế.
6. Sau khi có retention mới mở rộng.

---

# 4. Nhóm người dùng

## 4.1. Buyer

Buyer có thể là cư dân hoặc người ngoài community.

Nhu cầu chính:

- tìm đồ ăn;
- đồ uống;
- thực phẩm;
- tạp hóa;
- đồ gia dụng;
- đồ thanh lý;
- dịch vụ;
- shop gần mình;
- shop có giao tới mình;
- phí ship phù hợp;
- freeship;
- đăng nhu cầu “Cần mua”.

## 4.2. Seller cá nhân

Ví dụ:

- bếp nhà làm;
- bánh;
- trà sữa;
- trái cây;
- thực phẩm;
- handmade;
- quần áo;
- đồ gia dụng;
- đồ thanh lý.

Seller không bắt buộc là cư dân và không bắt buộc xác minh cư dân.

## 4.3. Shop / hộ kinh doanh / dịch vụ

Ví dụ:

- nhà hàng;
- cafe;
- nail;
- salon;
- pet shop;
- giặt ủi;
- sửa chữa;
- vệ sinh;
- gia sư;
- cửa hàng tiện lợi;
- dịch vụ tại nhà.

## 4.4. Runner

Runner chưa thuộc MVP.

Sẽ nghiên cứu khi CoVie có đủ volume đơn để hình thành nhu cầu giao hàng nội khu do nền tảng điều phối.

---

# 5. Community và vị trí

## 5.1. Vị trí seller

Một seller có thể có **nhiều điểm bán**.

Ví dụ:

```text
Bếp Nhà Mai
│
├── Điểm bán 1
│   └── The Origami - S10.05
│
└── Điểm bán 2
    └── The Rainbow - S7.03
```

Mỗi điểm bán có thể có:

- địa chỉ riêng;
- tọa độ riêng;
- giờ hoạt động riêng;
- phạm vi giao hàng riêng;
- phí ship riêng;
- minimum order riêng.

## 5.2. Địa chỉ buyer

Buyer có thể lưu một hoặc nhiều địa chỉ để sử dụng khi tìm kiếm và đặt hàng.

Trong một đơn hàng, buyer sử dụng **một địa điểm giao hàng chính thức**.

Ví dụ:

```text
S5.03 - Sảnh chính
```

Nếu buyer muốn seller giao lên tận căn hộ hoặc tới một vị trí khác so với địa chỉ của đơn thì đó là **thỏa thuận phát sinh giữa hai bên**.

CoVie không cần xử lý các phụ phí phát sinh như:

- phí giao lên căn hộ;
- phí gửi xe;
- phí chờ;
- phí lên tầng;
- yêu cầu giao tận cửa.

## 5.3. Địa điểm lấy và giao trong đơn hàng

Mỗi đơn chỉ có một:

```text
Pickup Location
+
Delivery Location
```

Ví dụ:

```text
Pickup:
S10.05

Delivery:
S5.03 - Sảnh chính
```

Đây là hai địa điểm chính thức dùng để:

- xác định khả năng giao hàng;
- tính khoảng cách;
- áp dụng shipping rule;
- hiển thị cho các bên liên quan.

## 5.4. Quyền xem vị trí

CoVie có thể hiển thị vị trí seller để buyer đánh giá khoảng cách và khu vực.

Thông tin địa chỉ chi tiết của một đơn hàng được hiển thị cho những bên liên quan đến đơn đó.

Không cần công khai toàn bộ thông tin giao hàng của buyer cho tất cả người dùng trên marketplace.

---

# 6. Shop và điểm bán

Mỗi seller có một hồ sơ shop.

Ví dụ:

```text
BẾP NHÀ MAI

✓ Shop đã xác minh
⭐ 4.9 (125 đánh giá)

Điểm bán:
The Origami - S10.05

🟢 Đang nhận đơn
08:00 - 21:00
```

Seller có thể bật/tắt từng điểm bán:

```text
🟢 Đang mở
🔴 Tạm nghỉ
```

Nếu có nhiều điểm bán, buyer cần biết mình đang đặt hàng từ điểm nào.

---

# 7. Sản phẩm

Mỗi sản phẩm có thể gồm:

- tên;
- hình ảnh;
- giá;
- mô tả;
- danh mục;
- trạng thái còn hàng / hết hàng;
- seller;
- điểm bán;
- thời gian chuẩn bị;
- tùy chọn sản phẩm;
- pickup/delivery;
- tags phục vụ tìm kiếm và matching.

---

# 8. Dịch vụ

Dịch vụ nằm trong MVP.

Ví dụ:

- vệ sinh nhà;
- sửa điện nước;
- sửa điều hòa;
- giặt ủi;
- nail;
- làm tóc;
- gia sư;
- chăm sóc thú cưng;
- vận chuyển nhỏ;
- dịch vụ khác.

Dịch vụ có thể khác sản phẩm vật lý ở các thuộc tính như:

- khu vực phục vụ;
- giá cố định / giá từ;
- lịch hẹn;
- seller tới chỗ buyer;
- buyer tới địa điểm dịch vụ;
- liên hệ để báo giá.

MVP chưa cần xây hệ thống booking phức tạp; ưu tiên discovery + contact + order/request cơ bản.

---

# 9. Thanh lý

Thanh lý nằm trong MVP.

Ví dụ:

```text
Ghế gaming
500.000đ

Tình trạng: 80%
📍 S10.03
🤝 Nhận tại sảnh
```

Thanh lý nên hỗ trợ:

- giá;
- tình trạng;
- ảnh thực tế;
- vị trí giao dịch;
- pickup;
- seller delivery nếu có;
- liên hệ trực tiếp.

---

# 10. “Cần mua” — Buyer Demand Marketplace

“Cần mua” nằm trong MVP.

CoVie không triển khai nó như một feed bài đăng tự do giống Facebook.

Mỗi nhu cầu mua phải là một đối tượng có cấu trúc.

Ví dụ:

```text
CẦN MUA

Sản phẩm:
Coca-Cola 1.5L

Số lượng:
2

Danh mục:
Đồ uống

Vị trí:
S5.03

Phạm vi tìm:
3 km

Cần trước:
20:00 hôm nay

Giá mong muốn:
Không bắt buộc
```

## 10.1. Matching

Sau khi buyer đăng “Cần mua”, CoVie chủ động tìm seller phù hợp.

```text
Buyer Demand
      ↓
Category / Keyword Matching
      ↓
Product Catalog
      ↓
Location / Delivery Coverage
      ↓
Stock / Shop Status
      ↓
Relevant Sellers
```

Buyer có thể nhận danh sách sản phẩm phù hợp ngay.

Seller có thể nhận thông báo về nhu cầu mua phù hợp với sản phẩm họ đang bán.

Mục tiêu:

> Tránh tình trạng người mua và người bán không tìm thấy nhau giữa hàng chục hoặc hàng trăm bài đăng.

## 10.2. Matching V1

Không cần AI trong V1.

Có thể dựa trên:

- category;
- keyword;
- product tags;
- khoảng cách;
- delivery coverage;
- stock;
- trạng thái shop.

AI matching có thể bổ sung sau.

---

# 11. Delivery Coverage

Delivery coverage là core feature của CoVie.

Seller tự quyết định mình phục vụ khu vực nào.

Mỗi điểm bán có delivery configuration riêng.

CoVie hỗ trợ hai kiểu chính:

## 11.1. Radius

Ví dụ:

```text
Delivery Radius:
2 km
```

Buyer cách shop 1.5 km:

```text
✅ Có thể giao
```

Buyer cách 2.5 km:

```text
❌ Ngoài phạm vi
```

Không đặt giới hạn bán kính cứng ở cấp nền tảng trong V1.

UI có thể cung cấp preset:

```text
500m
1km
2km
3km
5km
10km
Tùy chỉnh
```

## 11.2. Zone

Seller có thể chọn khu vực phục vụ.

Ví dụ:

```text
☑ The Origami
☑ The Rainbow
☐ The Beverly
```

Hoặc khu vực hành chính phù hợp.

## 11.3. Tương lai

Có thể bổ sung vùng polygon tùy chỉnh trên bản đồ khi sản phẩm lớn hơn.

---

# 12. Shipping Policy V1

Shipping policy do seller tự cấu hình theo từng điểm bán.

V1 cần hỗ trợ các mô hình sau.

## 12.1. Freeship

```text
Phí giao hàng:
0đ
```

## 12.2. Phí ship cố định

Ví dụ:

```text
Phí ship:
10.000đ
```

Tất cả buyer nằm trong delivery coverage sẽ áp dụng mức phí này, trừ khi có điều kiện freeship.

## 12.3. Phí ship thỏa thuận

Seller có thể chọn:

```text
Phí giao hàng:
Thỏa thuận với người bán
```

Checkout hiển thị:

```text
Tiền hàng       100.000đ
Phí giao hàng   Thỏa thuận
──────────────────────────
Tạm tính        100.000đ
```

CoVie không ghi nhận phí giao thực tế sau khi hai bên tự thỏa thuận trong V1.

**Không được coi “thỏa thuận” là freeship.**

## 12.4. Shipping theo điều kiện

Seller có thể thiết lập phí cơ bản và điều kiện freeship.

Ví dụ:

```text
Phí mặc định:
10.000đ

Freeship nếu:
Đơn ≥ 150.000đ
```

Hoặc:

```text
Freeship nếu:
Khoảng cách ≤ 1 km
```

Hoặc kết hợp:

```text
Freeship nếu:
Đơn ≥ 150.000đ
VÀ
Khoảng cách ≤ 3 km
```

Có thể cho seller chọn logic:

```text
○ Chỉ cần một điều kiện đúng
○ Phải thỏa mãn tất cả điều kiện
```

## 12.5. Shipping Rule chưa thuộc V1

Chưa cần ngay:

- phí theo từng km;
- bảng phí nhiều khoảng cách;
- bảng phí theo từng zone;
- surge pricing;
- dynamic delivery pricing;
- nhiều lớp shipping rule phức tạp.

Nhưng data model nên cho phép mở rộng về sau.

---

# 13. Minimum Order cho Delivery

Seller có thể tùy chọn minimum order cho delivery.

Ví dụ:

```text
Đơn tối thiểu để giao:
50.000đ
```

Buyer có giỏ hàng 35.000đ:

```text
Bạn cần mua thêm 15.000đ
để sử dụng giao hàng.
```

Nếu seller bật pickup thì buyer vẫn có thể tự lấy.

Minimum order được cấu hình theo từng điểm bán.

---

# 14. Pickup và Seller Delivery

V1 hỗ trợ:

```text
☑ Pickup
☑ Seller Delivery
```

Seller có thể bật một hoặc cả hai.

Ví dụ:

```text
Ghế gaming

🚚 Không giao
🤝 Nhận tại sảnh
```

Hoặc:

```text
Cơm gà

🚚 Seller giao
🤝 Có thể tự lấy
```

---

# 15. Giờ hoạt động và giờ giao hàng

Mỗi điểm bán có thể có:

- giờ mở cửa;
- giờ seller delivery;
- giờ pickup.

Ví dụ:

```text
Shop:
07:00 - 22:00

Delivery:
07:00 - 20:00

Pickup:
07:00 - 22:00
```

Sau 20:00, delivery có thể không khả dụng nhưng pickup vẫn hoạt động.

---

# 16. ETA

Seller có thể cấu hình thời gian chuẩn bị và thời gian giao dự kiến.

Ví dụ:

```text
Chuẩn bị:
15 phút

Giao dự kiến:
10 - 20 phút
```

Buyer thấy:

```text
Dự kiến nhận:
25 - 35 phút
```

ETA trong V1 có thể là seller-configured estimate, chưa cần thuật toán realtime.

---

# 17. Search và Discovery

Search là core feature.

Buyer có thể tìm:

```text
"trà sữa"
```

Bộ lọc có thể gồm:

## Delivery

```text
☑ Giao tới vị trí của tôi
```

## Khoảng cách

```text
< 500m
< 1km
< 3km
< 5km
```

## Community / Zone

- community;
- phân khu;
- tòa;
- khu vực khác.

## Phí giao

```text
Freeship
Phí cố định
Thỏa thuận
```

## Trạng thái

```text
☑ Đang mở
☑ Còn hàng
```

## Loại nội dung

```text
Sản phẩm
Dịch vụ
Thanh lý
```

## Giá / rating

Có thể bổ sung tùy loại nội dung.

---

# 18. Ranking kết quả

Không chỉ sắp theo khoảng cách.

Có thể xét:

```text
CanDeliver
+
ShopOpen
+
Distance
+
ShippingPolicy
+
Rating
+
Availability
+
Popularity
+
SellerActivity
```

Ưu tiên V1:

1. Có giao tới buyer hay không.
2. Shop có hoạt động hay không.
3. Sản phẩm còn hàng hay không.
4. Khoảng cách.
5. Phí ship.
6. Rating.

---

# 19. Feed

Không sử dụng một feed hỗn hợp duy nhất như Facebook.

Có thể chia:

- Gần tôi.
- Đang bán.
- Mới đăng.
- Freeship.
- Thanh lý.
- Dịch vụ.
- Cần mua.
- Bán chạy.

Mục tiêu là giảm hiện tượng nội dung bị trôi và giúp người dùng đi thẳng tới đúng nhu cầu.

---

# 20. Order Flow

Luồng cơ bản:

```text
Buyer
↓
Product / Service / Listing
↓
Chọn điểm bán
↓
Chọn Pickup / Delivery
↓
Delivery Coverage Check
↓
Shipping Policy Check
↓
Checkout
↓
Đặt hàng
↓
Seller xác nhận
↓
Chuẩn bị / thực hiện
↓
Giao / nhận
↓
Hoàn thành
↓
Review
```

Trạng thái gợi ý:

```text
PENDING
CONFIRMED
PREPARING
READY
DELIVERING
COMPLETED
CANCELLED
```

Các dịch vụ có thể cần state riêng về sau.

---

# 21. Thanh toán V1

CoVie V1 hỗ trợ hai hướng chính:

## 21.1. COD / thanh toán trực tiếp

Buyer và seller thanh toán khi nhận hàng hoặc theo thỏa thuận.

## 21.2. Chuyển khoản do hai bên tự thỏa thuận

CoVie không upload/lưu QR ngân hàng của seller trong V1.

Lý do:

- giảm rủi ro liên quan đến thay đổi QR;
- giảm nguy cơ tài khoản seller bị chiếm rồi đổi QR;
- giảm trách nhiệm của nền tảng khi chưa có hệ thống payment security hoàn chỉnh;
- không cần CoVie đứng giữa dòng tiền ngay từ đầu.

Có thể hiển thị:

```text
Phương thức thanh toán

✓ Thanh toán khi nhận
✓ Chuyển khoản — liên hệ seller để lấy thông tin
```

---

# 22. Contact / Chat V1

Chưa xây chat realtime nội bộ ở V1 để giảm hạ tầng và độ phức tạp.

Seller có thể cung cấp:

- link Zalo;
- QR Zalo;
- link Messenger;
- phương thức liên hệ khác được nền tảng cho phép.

Buyer có thể bấm để chuyển sang kênh liên hệ bên ngoài.

CoVie vẫn quản lý order và trạng thái order, nhưng trao đổi chi tiết có thể diễn ra ngoài nền tảng trong V1.

---

# 23. Review và Reputation

Seller profile có thể hiển thị:

```text
⭐ 4.9
126 đánh giá
243 đơn hoàn thành
98% phản hồi
✓ Đã xác minh
```

Xác minh cư dân **không bắt buộc**.

Có thể có nhiều dạng verification về sau:

- xác minh số điện thoại;
- xác minh seller;
- xác minh business;
- xác minh cư dân;
- xác minh community.

Buyer cũng có thể có lịch sử giao dịch hoặc reputation để hạn chế spam/đặt phá.

---

# 24. Moderation

Không hard-code một mô hình moderation duy nhất.

Admin có thể cấu hình bật/tắt chính sách kiểm duyệt.

Các mode:

## 24.1. Auto Publish

```text
Seller đăng
↓
Visible ngay
```

## 24.2. Pre-Approval

```text
Seller đăng
↓
Pending
↓
Admin duyệt
↓
Visible
```

## 24.3. Hybrid

Ví dụ:

```text
Seller bình thường
→ Auto Publish

Seller bị report nhiều
→ Require Approval

Category nhạy cảm
→ Require Approval
```

Admin có thể cấu hình ở các mức:

- toàn hệ thống;
- community;
- category;
- seller/user cụ thể.

Admin cần có quyền:

- hide listing;
- delete listing;
- warning;
- suspend;
- ban;
- restrict seller;
- chuyển seller sang pre-moderation.

---

# 25. Report và Safety

CoVie phải có từ MVP:

- Report Product.
- Report Service.
- Report Thanh lý.
- Report Seller.
- Report Buyer nếu cần.
- Block User.

Các mặt hàng/dịch vụ vi phạm policy phải bị cấm.

Ví dụ:

- hàng bất hợp pháp;
- thuốc kê đơn;
- chất kích thích;
- vũ khí;
- hàng giả;
- dữ liệu/tài khoản đánh cắp;
- sản phẩm vi phạm bản quyền;
- dịch vụ bất hợp pháp;
- nội dung gian lận.

---

# 26. Monetization

## 26.1. V1

```text
Buyer: FREE
Seller: FREE
```

Không áp transaction fee mặc định trong V1.

Khi nào bắt đầu monetization sẽ do admin/business decision quyết định sau.

## 26.2. Seller Subscription về sau

Có thể có Shop Pro:

- nhiều sản phẩm hơn;
- analytics;
- voucher;
- inventory nâng cao;
- auto reply;
- advanced shipping rule;
- ưu tiên hiển thị;
- báo cáo doanh thu.

## 26.3. Boost

Có thể monetization pain point “bài bị trôi” bằng:

- đẩy sản phẩm;
- sản phẩm nổi bật;
- top category;
- sponsored listing.

## 26.4. Transaction Fee

Transaction fee không bắt buộc và chưa thuộc V1.

**Admin cần có khả năng cấu hình bật/tắt transaction fee trong tương lai.**

Khi hệ thống payment đủ hoàn chỉnh, có thể hỗ trợ:

```text
Transaction Fee:
OFF / ON
```

và cấu hình:

- % phí;
- phí cố định;
- áp dụng theo category;
- áp dụng theo seller;
- áp dụng theo community;
- miễn phí cho seller cụ thể.

V1 chưa thu vì CoVie chưa đứng giữa dòng tiền.

---

# 27. Admin Configuration

Một nguyên tắc quan trọng:

> Những chính sách business có khả năng thay đổi nên được đưa vào Admin Configuration thay vì hard-code.

Ví dụ:

- bật/tắt transaction fee;
- tỷ lệ transaction fee;
- bật/tắt pre-moderation;
- moderation mode;
- seller restrictions;
- category restrictions;
- giới hạn listing;
- seller subscription;
- boost settings;
- các policy khác.

---

# 28. MVP V1

## 28.1. User

- đăng ký;
- đăng nhập;
- profile;
- địa chỉ buyer;
- phương thức liên hệ.

## 28.2. Community

- community;
- phân khu;
- tòa;
- location data.

## 28.3. Seller

- tạo shop;
- nhiều điểm bán;
- bật/tắt từng điểm;
- seller location;
- contact methods.

## 28.4. Product

- CRUD;
- ảnh;
- giá;
- category;
- stock;
- tags;
- điểm bán.

## 28.5. Service

- đăng dịch vụ;
- khu vực phục vụ;
- giá/giá từ;
- mô tả;
- contact/order request.

## 28.6. Thanh lý

- listing thanh lý;
- tình trạng;
- ảnh;
- giá;
- pickup/delivery.

## 28.7. Cần mua

- structured buyer request;
- category;
- keyword;
- vị trí;
- phạm vi tìm;
- deadline;
- matching cơ bản;
- seller discovery.

## 28.8. Delivery

- pickup;
- seller delivery;
- radius;
- zone;
- freeship;
- fixed fee;
- negotiated shipping;
- conditional freeship;
- minimum order;
- delivery hours;
- ETA.

## 28.9. Search

- search;
- location;
- category;
- filters;
- delivery coverage;
- shipping type;
- distance.

## 28.10. Order

- cart nếu phù hợp;
- chọn điểm bán;
- pickup/delivery;
- tính shipping rule;
- checkout;
- order status.

## 28.11. Payment

- COD/direct payment;
- bank transfer by private agreement;
- không upload QR bank trong V1.

## 28.12. Contact

- Zalo link/QR;
- Messenger link;
- external contact.

## 28.13. Review

- review sau giao dịch;
- seller rating.

## 28.14. Report / Moderation

- report;
- block;
- admin review;
- auto publish / pre-approval / hybrid config.

## 28.15. Admin

- users;
- sellers;
- shops;
- locations;
- products;
- services;
- thanh lý;
- cần mua;
- orders;
- reports;
- communities;
- categories;
- moderation settings;
- business settings.

---

# 29. Chưa làm trong MVP

- Runner marketplace.
- GPS realtime tracking.
- Native chat realtime.
- CoVie wallet.
- Payment escrow.
- Seller bank QR upload.
- Automated bank reconciliation.
- Livestream.
- Affiliate.
- Flash Sale.
- Loyalty.
- Membership.
- AI Recommendation.
- AI Matching nâng cao.
- Group Buy.
- Ads System hoàn chỉnh.
- Dynamic delivery pricing.
- Multi-stop logistics.
- Native mobile app.
- Microservices.

---

# 30. Platform ban đầu

Ưu tiên **Web App / PWA**.

Luồng acquisition:

```text
Facebook Group
      ↓
CoVie product/shop link
      ↓
Web/PWA
      ↓
Search / Match / Order
```

Ưu điểm:

- không bắt tải app;
- dễ chia sẻ link;
- phù hợp seller đang bán trên Facebook;
- thử nghiệm nhanh;
- chi phí phát triển thấp hơn native app.

---

# 31. Technology Stack dự kiến

## Frontend

```text
Next.js
```

## Backend

```text
Java
Spring Boot
```

## Database

```text
PostgreSQL
```

## Cache

```text
Redis
```

## Storage

```text
S3-compatible object storage
```

## Search

MVP có thể bắt đầu bằng PostgreSQL search phù hợp quy mô.

Sau này có thể dùng search engine chuyên dụng nếu cần.

## Maps / Geolocation

Cần một dịch vụ map/geocoding phù hợp khi triển khai radius, distance và zone.

---

# 32. Backend Architecture

Ưu tiên **Modular Monolith**.

```text
Spring Boot
│
├── auth
├── user
├── community
├── location
├── seller
├── shop
├── product
├── service
├── resale
├── demand
├── matching
├── delivery
├── order
├── payment
├── review
├── report
├── notification
├── settings
└── admin
```

Một PostgreSQL database ở giai đoạn đầu.

Không dùng microservices trước khi có nhu cầu thực tế.

---

# 33. Delivery Data Model định hướng

Mỗi điểm bán có thể có:

```text
ShopLocation
│
├── pickupEnabled
├── sellerDeliveryEnabled
├── deliveryCoverageType
├── deliveryRadius
├── deliveryZones
├── shippingPolicyType
├── fixedShippingFee
├── freeShippingThreshold
├── freeShippingRadius
├── freeShippingLogic
├── minimumDeliveryOrder
├── deliveryHours
└── estimatedDeliveryTime
```

Luồng checkout:

```text
Buyer Delivery Location
        ↓
Selected Shop Location
        ↓
Delivery Coverage Check
        ↓
Minimum Order Check
        ↓
Shipping Policy Evaluation
        ↓
Shipping Display
        ↓
Checkout
```

---

# 34. Matching Engine định hướng

Matching “Cần mua” V1:

```text
Demand
│
├── category
├── keyword
├── tags
├── quantity
├── buyer location
├── search radius
├── desired time
└── optional target price
```

Matching:

```text
Category Match
+
Keyword/Tag Match
+
Seller/Product Availability
+
Location
+
Delivery Coverage
+
Shop Status
```

Kết quả phải giúp buyer nhanh chóng tìm được seller phù hợp và seller thấy nhu cầu phù hợp.

---

# 35. Facebook Acquisition Strategy

Facebook không phải đối thủ cần loại bỏ ngay.

Facebook là kênh acquisition.

Seller có thể đăng:

```text
Hôm nay em có cơm gà nhé mọi người.

Menu + đặt hàng:
covie.vn/shop/bepnhamai
```

Luồng:

```text
Facebook
↓
CoVie
↓
Structured product page
↓
Order
↓
Repeat purchase
```

CoVie dần trở thành nơi người dùng chủ động search thay vì tìm bài Facebook.

---

# 36. Growth Loop

```text
Seller đăng CoVie link lên Facebook
        ↓
Buyer vào CoVie
        ↓
Buyer tìm/mua sản phẩm
        ↓
Buyer khám phá seller khác
        ↓
Buyer quay lại trực tiếp CoVie
        ↓
Seller nhận thêm đơn
        ↓
Seller tiếp tục sử dụng và chia sẻ
```

“Cần mua” tạo thêm một growth loop:

```text
Buyer đăng nhu cầu
        ↓
CoVie match seller
        ↓
Seller thấy nhu cầu thật
        ↓
Seller phản hồi / đăng thêm sản phẩm
        ↓
Marketplace có thêm supply
```

---

# 37. KPI giai đoạn thử nghiệm

## Seller

- active sellers;
- active shop locations;
- products/services active;
- seller có đơn;
- seller retention;
- số nhu cầu “Cần mua” seller phản hồi.

## Buyer

- active buyers;
- repeat buyers;
- searches/user;
- orders/buyer;
- repeat purchase rate;
- tỷ lệ “Cần mua” tìm được match.

## Marketplace

- orders/day;
- GMV nếu đo được;
- search → detail conversion;
- detail → order conversion;
- cancellation rate;
- tỷ lệ search có kết quả;
- tỷ lệ demand được match;
- average seller distance;
- tỷ lệ shop có thể giao tới buyer.

---

# 38. Milestones

## Mốc 1

```text
20 sellers
100 buyers
```

Kiểm chứng:

> Người dùng có chấp nhận dùng CoVie thay cho việc chỉ đọc bài Facebook hay không?

## Mốc 2

```text
50 sellers
500 buyers
```

Kiểm chứng:

> Search, listing và “Cần mua” có tạo giao dịch tự nhiên không?

## Mốc 3

```text
100 sellers
2.000 buyers
```

Kiểm chứng:

> Retention và repeat transaction có tồn tại không?

## Mốc 4

```text
100+ orders/day
```

Bắt đầu nghiên cứu sâu:

- payment integration;
- runner;
- monetization;
- community tiếp theo.

---

# 39. Rủi ro chính

## 39.1. Không đủ seller

Buyer tìm không thấy thứ cần → rời CoVie.

Giải pháp:

- tuyển seller trước;
- tập trung một phân khu/category;
- dùng “Cần mua” để seller thấy nhu cầu thực.

## 39.2. Không đủ buyer

Seller không có đơn → quay lại chỉ dùng Facebook.

Giải pháp:

- social sharing;
- Facebook acquisition;
- product/shop deep link.

## 39.3. CoVie biến thành Facebook Feed thứ hai

Nếu chỉ có bài đăng không cấu trúc, vấn đề cũ sẽ lặp lại.

Giải pháp:

- structured listing;
- structured buyer demand;
- search;
- filters;
- location;
- matching.

## 39.4. Delivery quá phức tạp

Shipping rules có thể phát triển thành một hệ thống rất lớn.

Giải pháp V1:

- freeship;
- fixed fee;
- negotiated fee;
- conditional freeship;
- minimum order.

## 39.5. Payment risk

Nếu CoVie đứng giữa thanh toán quá sớm, rủi ro bảo mật và dispute tăng mạnh.

Giải pháp V1:

- COD/direct payment;
- transfer by private agreement;
- chưa upload QR bank;
- chưa giữ tiền.

## 39.6. Spam / scam

Giải pháp:

- report;
- review;
- reputation;
- verification tùy chọn;
- admin moderation config;
- ban/suspend/restrict.

---

# 40. Roadmap

## Phase 0 — Validate

- nghiên cứu Facebook Group;
- khảo sát seller;
- khảo sát buyer;
- chọn phân khu/category đầu tiên;
- xác định seller seed group.

## Phase 1 — CoVie Marketplace MVP

- user;
- community;
- multi-location seller;
- product;
- service;
- thanh lý;
- “Cần mua”;
- matching cơ bản;
- search;
- delivery radius;
- delivery zone;
- shipping policies V1;
- minimum order;
- order;
- COD/private transfer;
- external contact;
- review;
- report;
- admin moderation/settings.

## Phase 2 — Commerce Enhancement

- follow shop;
- notification nâng cao;
- voucher;
- seller analytics;
- Shop Pro;
- boost listing;
- shipping pricing nâng cao;
- search/matching nâng cao.

## Phase 3 — Payment & Delivery Platform

- payment provider;
- secure seller settlement;
- transaction fee nếu admin bật;
- runner;
- delivery matching;
- tracking;
- platform delivery.

## Phase 4 — Community Commerce

- group buy;
- local deals;
- service booking nâng cao;
- community commerce features;
- advertising.

## Phase 5 — Scale

- community mới;
- mobile app;
- AI matching;
- recommendation;
- logistics optimization;
- local ads platform.

---

# 41. Nguyên tắc phát triển

Mỗi tính năng mới phải trả lời được:

> **Tính năng này có giúp buyer tìm đúng người bán nhanh hơn, giúp seller tìm đúng người mua nhanh hơn, hoặc giúp hai bên hoàn thành giao dịch địa phương thuận tiện hơn không?**

Nếu không:

> Chưa ưu tiên.

---

# 42. Định nghĩa ngắn gọn của CoVie

> **CoVie là một hyperlocal marketplace mở, giúp người mua và người bán ở gần nhau tìm thấy nhau thông qua sản phẩm, dịch vụ, thanh lý và nhu cầu “Cần mua”; seller tự quản lý điểm bán, vùng giao và phí ship, còn CoVie tập trung vào discovery, matching, order và trust thay vì đứng giữa toàn bộ quá trình thanh toán và giao nhận ngay từ V1.**

---

# 43. Trạng thái sau V0.3

Các quyết định chính của Product Scope V1 đã được chốt đủ để chuyển sang bước tiếp theo.

Quy trình đề xuất:

```text
V0.3 Product Plan
↓
Chốt User Flow
↓
Thiết kế Information Architecture
↓
UI/UX Wireframe
↓
Database Design
↓
API Design
↓
Technical Plan
↓
Implementation
```

Những thay đổi lớn về business model hoặc product scope sau này sẽ tạo phiên bản V0.4 trở lên.
