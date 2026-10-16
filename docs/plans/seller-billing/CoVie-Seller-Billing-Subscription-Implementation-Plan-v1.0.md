# CoVie — Seller Billing & Subscription: Kế hoạch triển khai chi tiết v1.0

**Loại tài liệu:** Implementation Contract / PRD kỹ thuật / Kế hoạch nghiệm thu cho AI coding agent và đội phát triển  
**Ngày lập:** 15/10/2026 (ICT)  
**Repository:** https://github.com/TanBuiDev/covie  
**Baseline tham khảo đã đối chiếu:** nhánh `main`, commit `bb2e06b41282c8e9205c35a13e7b6adec68ceaa2`.  
**Trạng thái:** ĐÃ DUYỆT HƯỚNG SẢN PHẨM; **ĐÂY CHỈ LÀ KẾ HOẠCH, CHƯA TRIỂN KHAI MÃ NGUỒN, CHƯA BẬT THU TIỀN, CHƯA ĐƯỢC TỰ ĐỘNG MERGE/DEPLOY**.  
**Chủ sở hữu quyết định:** Chủ dự án CoVie.  
**Nguyên tắc:** Mỗi khi bắt đầu triển khai, AI phải xác minh lại `origin/main` và những thay đổi mới hơn baseline; không suy diễn các API/bảng dữ liệu được đề xuất đã tồn tại.

> **Quy ước chứng cứ:** **ĐÃ XÁC MINH** = đã tìm thấy trên nhánh main khi lập tài liệu; **ĐỀ XUẤT** = hợp đồng mục tiêu chưa triển khai; **GATE/ADR** = phải có bằng chứng/duyệt trước khi code hoặc phát hành. Mọi số tiền, kỳ hạn, chính sách dưới đây là **giá trị khởi tạo từ dữ liệu, không phải constant gán cứng trong logic ứng dụng**.

---

## 0. Mục tiêu, phạm vi và những điều tuyệt đối không làm

### 0.1. Mục tiêu kinh doanh

- Người đăng ký tài khoản CoVie được **miễn phí** sử dụng chức năng người mua: tìm kiếm, giỏ hàng, đặt hàng, đánh giá và tương tác Social Product Card (like, bình luận, lưu, chia sẻ theo quyền hiện hành).
- **Chỉ người muốn mở và vận hành cửa hàng bán sản phẩm/dịch vụ** mới cần quyền thuê bao có trả phí. Một user có thể đồng thời là buyer và OWNER/MANAGER/STAFF của nhiều cửa hàng.
- Phí bán hàng là **10.000 VND/tháng/cửa hàng MerchantProfile** trong cấu hình khởi tạo. Không tính thêm theo số tài khoản nhân viên hay số MerchantLocation thuộc cùng cửa hàng.
- **0% hoa hồng đơn hàng** ở giai đoạn này; không tự thêm phí sàn, phí giao dịch người mua, phí quảng cáo, phí rút tiền, thanh toán hộ seller, escrow hoặc marketplace payout.
- Seller có thể tạo **hồ sơ cửa hàng dạng thiết lập/draft**, nhưng chưa được công khai bán/nhận đơn mới cho đến khi entitlement hợp lệ do backend xác định.
- Admin được thay đổi giá, chu kỳ, khuyến mại, bật/tắt dùng thử, số ngày ân hạn và chính sách áp dụng mà **không sửa code cho các quy tắc đã được hỗ trợ**.
- Giá niêm yết, phí phải thu, khuyến mại, tax/VAT treatment, điều khoản khi xác nhận đơn phí đều được **snapshot, version, audit**. Không được sửa lại hóa đơn đã phát hành.

### 0.2. Phạm vi P0/P1

| Ưu tiên | Trong phạm vi |
|---|---|
| P0 | Pricing/versioning, seller onboarding, subscription lifecycle, invoice/payment, webhook/reconcile, backend entitlement gates, UI Admin/Seller, migration seller cũ, audit, observability, rollback & staging |
| P1 (thiết kế trước, triển khai sau gate) | Promo/voucher, gift/free extension, analytics chi tiết, email/SMS tùy kênh hiện có, cấu hình giá theo khu vực, gói năm, bulk actions có xác nhận |
| Ngoài phạm vi | Buyer trả phí, marketplace commission/settlement, trừ tiền tự động không có ủy quyền, escrow, ví CoVie, payout người bán, chuyển khoản cho đơn hàng buyer, billing theo từng nhân viên, thu phí theo địa điểm |

### 0.3. Bất biến (invariants) bắt buộc

1. `Buyer account` độc lập với `MerchantSubscription`. Buyer không phải trả tiền để đăng ký, mua sắm hoặc bình luận.
2. Merchant ownership xác định bằng `merchant_memberships` từ DB; **không** tin merchant/owner ID từ client/JWT claim tùy biến.
3. Billing state **không** được ghi đè `merchant_profiles.merchant_status` hoặc `moderation_status` để biểu thị hết hạn. Hết phí khác bị Admin khóa/ẩn.
4. Không công khai item mới, không tạo order mới nếu backend kết luận merchant không đủ entitlement; tất cả entrypoint liên quan phải kiểm tra, không chỉ ẩn nút UI.
5. Các đơn đã tạo và nghĩa vụ giao nhận/đánh giá/hoàn tất đơn vẫn được xử lý khi thuê bao hết hạn. **Không xóa merchant/item/media/comment/like/order** khi hết hạn.
6. Không kích hoạt/gia hạn từ query string success, redirect, QR được hiển thị, ảnh chụp chuyển khoản hoặc dữ liệu frontend.
7. Mọi payment event có chống lặp; một sự kiện thành công không được cộng kỳ hạn hai lần; mọi thay đổi tiền phải đối soát/audit.
8. Giá trị điều khiển từ Admin thay đổi theo **phiên bản và thời điểm hiệu lực**; không thay ngầm điều kiện của giao dịch/kỳ thuê bao đã xác lập.
9. Không để lỗi cổng thanh toán khiến buyer checkout của cửa hàng đã trả phí bị phụ thuộc đồng bộ vào PSP; database CoVie là nguồn trạng thái quyền sử dụng.
10. Secret, API key, signing key không đi vào Git, client, API response, audit log hoặc `system_settings`.
11. Phải có kill switch/thử nghiệm tắt enforcement, nhưng không được biến kill switch thành quyền nâng cấp trái phép; thay đổi production cần quyền Admin/ops và audit.
12. Không tự tạo logic mới ngoài tài liệu hoặc âm thầm thay đổi phạm vi khi code; nếu phát hiện thiếu quyết định → ghi ADR và xin duyệt.

---

## 1. Hiện trạng mã nguồn đã đối chiếu (15/10/2026)

### 1.1. Các thành phần hiện hữu

| Thành phần | Bằng chứng repository | Ý nghĩa tích hợp |
|---|---|---|
| Seller onboarding | `apps/web/app/(seller)/seller/page.tsx`, `apps/web/features/merchant/merchant-form.tsx` | Có luồng tạo và chọn nhiều cửa hàng; hiện chưa có bước trả phí |
| Create Merchant | `apps/api/src/main/java/vn/covie/merchants/api/MeMerchantController.java` + `merchants/application/MerchantApplicationService.java` | `POST /api/v1/me/merchants` hiện tạo merchant và OWNER membership trực tiếp |
| Merchant state | `merchants/model/MerchantProfileEntity.java`, migration `V004__location_merchant_media.sql` | `merchant_status` = ACTIVE/PAUSED/SUSPENDED/DELETED; `moderation_status` tách riêng; không dùng chúng làm subscription state |
| Merchant permission | `merchants/security/MerchantAccessPolicy.java` và `MerchantCapability.java` | Quyền OWNER/MANAGER/STAFF được resolve DB; cần policy entitlement riêng ở các hành động thương mại |
| System settings | `settings/application/SystemSettingRegistry.java`, `SystemSettingService.java`, `SystemSettingResolver.java` | Hỗ trợ typed keys BOOLEAN/INTEGER/STRING/ENUM, scope, validation, ETag, lịch sử, outbox/audit; **chưa có billing keys** |
| Admin settings UI | `apps/web/features/admin/admin-settings-screen.tsx`, `settings-editor.tsx` | Có giao diện cấu hình hiện hữu, cần nhóm Billing riêng mang ngữ nghĩa nghiệp vụ |
| Settings SQL | `V013__moderation_system_settings_admin.sql` | `system_settings`, `system_setting_history` đã tồn tại |
| Checkout buyer | `V009__order_creation_foundation.sql`, `apps/api/src/main/java/vn/covie/checkout/` | Payment buyer chỉ có COD và BANK_TRANSFER_NEGOTIATED; không phải subscription payment |
| Social catalog | `V018__catalog_social_interactions.sql`, `V019__social_comment_moderation.sql` | Like/Save/Comment gắn catalog item; giữ nguyên lịch sử khi merchant quá hạn |
| Quảng cáo | `V015__advertising.sql`, `apps/web/features/ads/` | Có ad_campaigns; cần ADR xử lý phân phối quảng cáo của merchant quá hạn |
| Hạ tầng | `README.md`, `apps/api/pom.xml`, `apps/web/package.json` | Spring Boot/JDK, Next.js/Node, PostgreSQL/Flyway, OpenAPI generated client, Redis, MinIO |
| Kế hoạch Social | `docs/plans/social-product-card/CoVie_Social_Product_Card_Ke_Hoach_Trien_Khai_Chi_Tiet_v1.0.md` | Billing phải tránh đè lên các thay đổi Social/UI đang hoạt động |

### 1.2. Khoảng trống cần triển khai

- Chưa thấy module `vn.covie.billing` chuyên biệt, bảng subscription/invoice/payment/webhook inbox hoặc màn `/admin/billing` và `/seller/[merchantId]/billing` trong những đường dẫn đã kiểm tra.
- `MerchantProfileEntity.create` khởi tạo merchant `ACTIVE`. Chỉ thêm màn QR trong Seller Center **không thể** tạo enforcement đúng.
- `SystemSettingRegistry` hiện dùng danh sách định nghĩa cố định trong source (whitelist). Admin hiện chỉ thay đổi **giá trị key đã hỗ trợ**, không thể tự tạo cơ chế tính phí mới mà không có code. **Không mở arbitrary JSON/script execution.**
- Phải rà soát lại toàn bộ các entrypoint thật ở Gate 0, đặc biệt create/update/publish item, location offer, checkout preview/commit, direct buy, Need matching/response, discovery/search, ads delivery, moderation và các consumer dùng catalog.

### 1.3. Tài liệu/phụ thuộc bắt buộc đọc khi thực thi

`AGENTS.md`; `RULES.md`; `apps/web/AGENTS.md`; `README.md`; `docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md`; `docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md`; `docs/CoVie-technical-plan-08-moderation-system-settings-admin-v1.0.md`; `docs/CoVie-project-status.md`; tài liệu Social Product Card; OpenAPI được export từ backend; CI/Playwright/Figma đang dùng.

**Cảnh báo:** Không dùng số liệu test lịch sử như bằng chứng CI hiện tại. Không khẳng định staging hoặc production đã đạt nếu thiếu log/screenshot tương ứng.

---

## 2. Chính sách thương mại mặc định — chỉ là dữ liệu khởi tạo

| Key/thuộc tính nghiệp vụ | Initial value | Quy tắc |
|---|---|---|
| `billing.seller_registration_enabled` | true | Admin có thể tạm ngừng tiếp nhận seller mới; buyer không ảnh hưởng |
| `billing.enforcement_enabled` | false khi deploy đầu tiên | Chỉ bật sau migration + acceptance + approval; không bật ngầm qua migration |
| Default plan | SELLER_STANDARD | Một gói được công bố ban đầu, kiến trúc hỗ trợ nhiều gói |
| Monthly price | 10000 VND | Integer, không hardcode trong service/UI |
| Billing cycle được bán | MONTH x 1 | YEAR/multi-month hỗ trợ bằng mô hình nhưng **không tự bật** |
| Commission | 0% | Không triển khai commission engine trong P0 |
| Onboarding | PAY_BEFORE_SELL | Cho lưu draft trước; phải có entitlement mới publish/sell |
| Trial | disabled, duration 30 days nếu bật sau | Khi OFF không cấp trial; khi ON phải có rule chống lợi dụng và chặn tạo nhiều trial |
| Auto renewal | disabled | Không được tự động trừ tiền trong P0 |
| Grace period | 3 days | `READ_ONLY` mặc định: không tạo đơn mới trong grace; dùng để gia hạn và xử lý backlog |
| Renewal reminders | 7, 3, 1 ngày trước kỳ hết hạn | Cho phép đổi lịch ở Admin; không spam hoặc gửi trùng |
| Existing merchant migration | GRANDFATHER_UNTIL_DATE / COMPLIMENTARY_GRANT, **cần phê duyệt ngày cụ thể** | Không khóa cửa hàng hiện hữu khi bật feature |
| Seller account scope | 1 subscription / 1 MerchantProfile | OWNER chịu trách nhiệm; STAFF không bị tính phí |
| Currency | VND | Gói P0 tại Việt Nam, integer amount; VAT treatment phải được xác nhận trước khi thu tiền |

**Không mặc định gói năm là 100.000đ hoặc miễn phí 30 ngày nếu Admin chưa cấu hình và công bố.** UI đọc catalog giá + chính sách hiệu lực từ backend. Đổi giá phải theo phiên bản; UI phải xác nhận lại khi quote hết hiệu lực.

---

## 3. Domain, quyền và máy trạng thái

### 3.1. Domain boundary

- **Merchant** giữ hồ sơ, location, membership, moderation và catalog.
- **Billing** sở hữu plan/version/price, policy version, subscription, invoice, transaction, entitlement, webhook inbox, renewal scheduler.
- **PSP adapter** là ranh giới kỹ thuật với nhà cung cấp; hỗ trợ `createPayment`, `verifyWebhook`, `getPaymentStatus`, `refund` (nếu được cấp và được duyệt); không để provider DTO xuyên qua domain.
- **Checkout buyer** không tái sử dụng invoice/payment table của subscription; chỉ dùng service đọc entitlement tại đường ra quyết định phù hợp.
- **Admin** có policy quản trị billing riêng, audit và cơ chế xác nhận thay đổi nguy hiểm.

### 3.2. Subscription states (đề xuất)

`DRAFT` → `PENDING_PAYMENT` → `ACTIVE` → `PAST_DUE` → `EXPIRED`; nhánh `TRIALING` (chỉ khi chính sách bật); `CANCELLED` (ngừng gia hạn, hiệu lực tới `current_period_end` nếu còn quyền); `SUSPENDED_BILLING` (chỉ cho quyết định gian lận tài chính được audit, không trùng merchant moderation); `REVOKED` (quyền cấp bù bị thu hồi có lý do).

- `DRAFT/PENDING_PAYMENT`: không có quyền thương mại; người sở hữu xem/edit hồ sơ cơ bản, trả tiền.
- `TRIALING/ACTIVE`: được bán nếu merchant/catalog/moderation cũng hợp lệ.
- `PAST_DUE`: trong grace cho phép xem dữ liệu, xử lý các nghĩa vụ cũ, trả phí; mặc định **không nhận đơn mới**.
- `EXPIRED`: dừng khả năng xuất bản/nhận order; dữ liệu còn nguyên; cho phép gia hạn.
- `CANCELLED` không có nghĩa chấm dứt quyền ngay: phải xét `current_period_end` cùng cờ/cause.
- Billing eligibility là **kết quả được tính server-side** dựa trên entitlement grants, time, subscription state, policy version và revocations; không suy ra từ string status đơn độc.

Tách riêng:
1. `merchant_status`, `moderation_status` — trạng thái kinh doanh và kiểm duyệt đã có.
2. `subscription_status` — trạng thái hợp đồng trả phí.
3. `payment_status` — trạng thái giao dịch thu tiền.
4. `entitlement` — quyền có thể bán tại thời điểm kiểm tra.

### 3.3. Payment / invoice states

- Invoice: `DRAFT`, `ISSUED`, `PENDING_PAYMENT`, `PAID`, `VOID`, `REFUNDED`, `PARTIALLY_REFUNDED`, `DISPUTED` (chỉ tạo state nếu provider/quy trình thật hỗ trợ).
- Payment attempt: `CREATED`, `PENDING`, `SUCCEEDED`, `FAILED`, `EXPIRED`, `REFUNDED`, `DISPUTED`; thay đổi theo transition hợp lệ, không downgrade event thành công khi webhook đến sai thứ tự.
- Payment success ≠ published merchant nếu merchant vẫn `SUSPENDED`, `HIDDEN` hay chưa hoàn thành yêu cầu KYC/kiểm duyệt. Cho hiển thị lý do khác nhau, không mất kỳ phí mà không có chính sách giải quyết.

### 3.4. Ma trận quyền (mặc định)

| Chủ thể | Buyer miễn phí | Tạo merchant draft | Xem giá & trạng thái | Tạo thanh toán/gia hạn | Quản trị giá/hoàn tiền |
|---|---:|---:|---:|---:|---:|
| Guest | Browse public | Không | Chỉ bảng giá public | Không | Không |
| User thông thường | Có | Có nếu bật đăng ký | Shop mình | OWNER của shop | Không |
| OWNER | Có | Có | Toàn bộ billing shop mình | Có | Không |
| MANAGER | Có | Theo owner membership riêng | Được xem trạng thái/expiry | **Không mặc định** | Không |
| STAFF | Có | Theo owner membership riêng | Chỉ entitlement cần cho công việc | Không | Không |
| Admin/finance operator | Theo quyền user | Không thay OWNER | Theo quyền đã cấp | Theo nghiệp vụ được duyệt | Theo RBAC, audit bắt buộc |

Không cho `MANAGER`/`STAFF` tự thao tác tiền chỉ vì có `MerchantCapability.VIEW_MERCHANT`; phải thiết kế `BillingAccessPolicy` và tests chéo merchant ID.

---

## 4. Admin-driven configuration — không hardcode chính sách

### 4.1. Tách chính sách hệ thống và catalog giá

**Tận dụng `system_settings`** cho các switch/key đơn giản, được whitelist/validate trong `SystemSettingRegistry`, ví dụ `billing.seller_registration_enabled`, `billing.enforcement_enabled`, `billing.reminder_enabled`. Nếu là BOOLEAN/INTEGER/ENUM phải validate server. Việc thêm key mới vào registry là thay đổi code **một lần** để hỗ trợ tính năng mới; sau đó Admin đổi value mà không deploy.

**Tạo bảng Billing chuyên biệt** cho gói giá, chu kỳ, trial/grace/renewal policy, entitlements và effective dates. Không nhét cả bảng giá vào một JSON global không có phiên bản; Admin có UI semantic, lịch sử, preview tác động, publish/retire và giới hạn quyền.

### 4.2. Bất biến publish pricing/policies

1. `DRAFT` có thể sửa; `PUBLISHED` **immutable**. Mọi thay đổi xuất bản tạo version mới.
2. `effective_from` rõ ràng, UTC lưu trong DB; UI hiển thị ICT theo locale. Không publish phiên bản chồng thời gian trong cùng phạm vi/chu kỳ.
3. Invoice tạo dưới phiên bản nào phải lưu `plan_version_id`, `price_id`, `policy_version_id` và `pricing_snapshot`.
4. Đổi giá **không đổi kỳ đã thanh toán**. Tại renewal tạo quote theo chính sách tăng giá cho khách cũ do Admin công bố; không ép tăng giá không thông báo.
5. Admin có preview số merchant chịu tác động, giá cũ/mới, ngày hiệu lực, xác nhận hai bước khi thay đổi có hậu quả tài chính.
6. Không hard delete policy/plan version có invoice/subscription reference. Chỉ `RETIRED`.
7. Hành vi "giảm phí", "miễn phí", "tặng thời gian" phải có grant/adjustment rõ issuer, reason, start/end và audit; không sửa `paid_until` bằng tay.
8. Admin không cấu hình JS/SQL tùy ý; mọi rule nằm trong tập rule type được server kiểm soát. Khuyến mại P1 có schema riêng, validation và abuse control.
9. Giá trên UI phải lấy từ `GET /api/v1/billing/plans`; dùng `quoteId`/`quoteExpiresAt` để tránh TOCTOU khi Admin đổi giá ngay lúc seller trả tiền.
10. Có rollback cấu hình bằng **phiên bản mới** trỏ tới giá cũ, không xóa audit hoặc sửa dữ liệu lịch sử.

### 4.3. Quy tắc kỳ hạn/gia hạn

- Dùng thời điểm server `Instant` và `AppClock`; lưu timestamptz UTC. Kỳ `MONTH` tính theo calendar month với thuật toán anchor rõ ràng (ví dụ ngày 31 → ngày cuối tháng phù hợp), test 28/29/30/31 và leap year.
- Gia hạn sớm theo mặc định nối từ `max(current_period_end, now)` khi vẫn còn quyền; hết hạn có thể bắt đầu từ thời điểm xác nhận mới. Không cộng trùng do duplicate webhook.
- Một invoice chứa period_start/period_end bất biến, số tiền thu và các thành phần giảm giá/thuế (nếu có). Khi hoàn một phần/toàn phần, quyền sử dụng phải theo policy hoàn tiền được duyệt; không tự xóa kỳ nếu chưa có quy tắc.
- Trial eligibility thuộc phạm vi OWNER identity được kiểm chứng và chống abuse (tạo nhiều merchant/đổi tài khoản); không giả định tuyệt đối chặn được gian lận nếu chưa có phương pháp nhận dạng hợp pháp.
- Kỳ hạn/quote/gia hạn không được dựa trên thời gian local browser.

---

## 5. Thiết kế PostgreSQL/Flyway mục tiêu

**KHÔNG tạo các bảng dưới đây trong bước viết kế hoạch.** Giai đoạn implement phải xác minh latest migration trên `origin/main`. Tại thời điểm lập kế hoạch: V019 là migration mới nhất thấy trên `main`; **đừng tự lấy số V020 nếu lúc thực thi đã có file mới**.

| Table đề xuất | Trường/constraint tối thiểu |
|---|---|
| `billing_plans` | UUID PK, immutable business `code` unique, display_name, status DRAFT/PUBLISHED/RETIRED, timestamps |
| `billing_plan_versions` | plan_id FK, version integer > 0, features/limits JSONB có schema, publication status, effective_from, author, unique(plan_id, version), optimistic lock |
| `billing_plan_prices` | plan_version_id FK, cycle_unit MONTH/YEAR, cycle_count > 0, currency_code char(3), amount_minor bigint >= 0, effective window, unique active price window theo constraint thiết kế, không hard delete |
| `billing_policy_versions` | version, onboarding_mode, trial_days, grace_days, grace_mode, reminders, renewal_price_policy, effective_from, approval metadata, immutable published snapshot; có kiểm tra min/max |
| `merchant_subscriptions` | merchant_id FK, plan_id/version, status, current_period_start/end, trial_end, cancel_at_period_end, lifecycle reason, version, timestamps; enforce tối đa một subscription current/merchant |
| `billing_invoices` | invoice_no unique, merchant_id, subscription_id, price_id, policy_version_id, quote token/fingerprint, period_start/end, subtotal/discount/tax/total bigint, currency VND, status, immutable pricing_snapshot, due/paid_at, version |
| `billing_payment_attempts` | invoice_id, provider, provider_order_id unique theo provider, provider_transaction_id unique nullable, expected_amount, received_amount, status, transaction timestamp, intent/external reference, version |
| `billing_webhook_inbox` | provider, provider_event_id unique, payload_sha256, verified/processing state, processed_at, error metadata loại bỏ PII, timestamps, retry counters; unique(provider, event_id) |
| `billing_entitlement_grants` | merchant_id, source_type SUBSCRIPTION/TRIAL/ADMIN_GRANT, source_id unique trong nguồn, valid_from/to, revoked_at/reason, audit actor; không overlap trái policy |
| `billing_adjustments` (P1 hoặc P0 nếu miễn phí legacy) | reason, type CREDIT/FREE_DAYS/REFUND_ADJUSTMENT, amount/term, approver, immutable history |

**DB rules:** foreign key on delete restrict với hóa đơn/thuê bao, unique theo tenant, check constraints cho positive duration/nonnegative money/valid windows, indexes merchant+status+period_end, payment provider+externalID, invoice+created_at, retry/reconciliation. Monetary values dùng **integer VND** (không double/float). Định nghĩa rõ `minor units` theo currency convention; với VND lưu số đồng, xác minh adapter provider. Không lưu PAN/card/account secret.

**Concurrency:** tạo invoice, publish plan, settle payment, gia hạn/subscription transition đều phải idempotent và có row lock/version thích hợp. Thiết kế chống hai callback cùng ghi vào một subscription và chống một invoice có nhiều lần ghi `SUCCEEDED`.

**Retention:** invoice/payment/audit theo chính sách pháp luật áp dụng; xóa/anonymize PII theo nghĩa vụ nhưng không phá chứng từ kế toán. Document schema/backfill/rollback tương ứng.

---

## 6. Payment integration — kiến trúc, chưa chốt PSP

### 6.1. Gate chọn nhà cung cấp

So sánh payOS, SePay và các đối tác tương đương **dựa trên tài liệu/hợp đồng mới nhất ở thời điểm triển khai**, không xem nhận xét lịch sử là mức phí được bảo đảm. Tiêu chí: điều kiện pháp nhân và định danh, hỗ trợ merchant of record, loại link/QR, chữ ký webhook, query trạng thái, đối soát, refund, test mode/sandbox, phí cố định + %, phí hoàn/chargeback, SLA, thời hạn trả tiền, giới hạn giao dịch 10.000đ, hóa đơn/chứng từ và xử lý sự cố.

**ADR-PSP bắt buộc:** tên PSP, product/API version, xác thực webhooks, retry behavior, phí thực, sandbox credentials (chỉ secret manager), hợp đồng và trách nhiệm pháp lý; duyệt trước G2. Không giả lập auto-debit nếu nhà cung cấp không hỗ trợ.

### 6.2. Luồng thanh toán chuẩn

1. OWNER chọn merchant + plan/cycle đang `PUBLISHED`; server tạo quote có TTL, scope, policy+price version và breakdown thuế/phí.
2. Client xác nhận đúng quote; gửi idempotency key; backend kiểm tra OWNER, giá/hiệu lực, invoice uniqueness rồi tạo `invoice PENDING_PAYMENT` + `payment_attempt`.
3. Backend gọi PSP qua adapter bằng server credential, trả `paymentUrl/qrPayload/expiry` đã giới hạn trường hiển thị; raw API secret không đến browser.
4. Browser poll API trạng thái có cache-control `no-store`; redirect chỉ cung cấp UX, **không** nâng trạng thái.
5. PSP gửi webhook: giới hạn body, xác thực HMAC/chữ ký/chứng thư theo docs, verify timestamp/replay, lưu inbox dedup, trả ACK theo hợp đồng, xử lý bền vững. Nếu cần, gọi provider status API độc lập xác nhận amount/currency/order/reference.
6. Transaction DB: lock invoice + subscription, validate amount/currency/merchant/provider order, chuyển payment+invoice sang successful duy nhất một lần, tạo entitlement grant/extend period atomically, thêm outbox event + audit.
7. Outbox/notification SSE/email gửi sau commit; frontend GET server state mới.
8. Worker đối soát xử lý webhook mất, đến trễ, không đúng thứ tự, PSP timeout, invoice hết hạn và giao dịch không rõ; alert manual review chứ không tự suy diễn đã nhận tiền.

### 6.3. Trường hợp bắt buộc xử lý

- Hai lần nhấn thanh toán: cùng actor/operation/key/body trả cùng intent; key khác body => conflict.
- Duplicate webhook (cùng event ID hoặc transaction ID khác event); replay ngoài cửa sổ; chữ ký sai.
- Payment provider thành công nhưng webhook timeout; server crash giữa nhận sự kiện và DB commit; webhook đến trước browser redirect.
- Đúng order nhưng sai amount/currency; thanh toán thiếu/thừa; invoice hết hạn; thanh toán cho invoice đã paid; PSP cancel/refund/chargeback.
- Seller đổi shop/tab trong lúc trả tiền; không được áp tiền sang shop khác.
- Admin đổi bảng giá trong khi quote còn hiệu lực; xử lý theo TTL và snapshot hoặc bắt xác nhận lại bằng lỗi xác định.
- Hai invoice/purchase song song cho cùng merchant; khóa tránh gia hạn chồng, không double-charge; nếu đã charge 2 lần phải có credit/refund runbook.
- Không đưa thông tin tài khoản ngân hàng/bằng chứng raw webhook vào log công khai. Rate-limit tạo QR/polling; chống abuse và theo dõi provider fees.

### 6.4. Hoàn tiền và nghiệp vụ kế toán

- P0 phải có **quy trình hỗ trợ** khi thu tiền nhầm/trùng/dư, kể cả khi hoàn tiền tự động qua PSP chưa khả dụng. Không xóa payment record; dùng adjustment/refund record và reconciliation.
- Xác định CoVie là bên thu phí cung cấp dịch vụ nền tảng; lập checklist thuế, hóa đơn điện tử, điều khoản seller, VAT-inclusive/exclusive, cổng thanh toán và bảo vệ người dùng phù hợp pháp luật Việt Nam trước khi nhận tiền thật.
- Không viết khẳng định pháp lý/tax rate chưa được chuyên gia/đơn vị vận hành xác nhận. Không trừ VAT theo phép tính tùy ý.
- Tự động thu tiền định kỳ **ngoài P0**; khi đưa vào sau phải có mandate/consent và API hủy gia hạn hợp lệ.

---

## 7. API contract đề xuất (tất cả là API mới, CHƯA TỒN TẠI)

### 7.1. Public + Seller

| Method | Endpoint dự kiến | Mục đích |
|---|---|---|
| GET | `/api/v1/billing/plans` | Chỉ plan/price đã published và đang hiệu lực, trạng thái trial/config công khai, cache hợp lý |
| GET | `/api/v1/merchants/{merchantId}/billing/summary` | Subscription/entitlement/status/expiry/next action, scoped |
| POST | `/api/v1/merchants/{merchantId}/billing/quotes` | Báo giá chính xác theo cycle, khóa plan/policy version + TTL |
| POST | `/api/v1/merchants/{merchantId}/billing/checkout` | Tạo invoice+intent từ quote; `Idempotency-Key` bắt buộc |
| GET | `/api/v1/merchants/{merchantId}/billing/invoices` | Danh sách hóa đơn phân trang |
| GET | `/api/v1/merchants/{merchantId}/billing/invoices/{invoiceId}` | Invoice chi tiết của đúng merchant, no-store |
| GET | `/api/v1/merchants/{merchantId}/billing/payments/{paymentId}` | Trạng thái payment (không trả provider secrets) |
| POST | `/api/v1/merchants/{merchantId}/billing/cancel-renewal` | Chỉ triển khai nếu P0 có khả năng giữ `cancelAtPeriodEnd`; tránh hủy nhầm entitlement đã paid |

### 7.2. PSP inbound / Admin

| Method | Endpoint dự kiến | Mục đích |
|---|---|---|
| POST | `/api/v1/billing/webhooks/{provider}` | Inbound trusted webhook, signature verification; ngoại lệ CSRF có giới hạn route cụ thể |
| GET | `/api/v1/admin/billing/plans` | Draft/published/retired plans, cycle, versions |
| POST | `/api/v1/admin/billing/plans` | Tạo draft plan, validate |
| POST | `/api/v1/admin/billing/plans/{planId}/versions` | Tạo draft version mới |
| POST | `/api/v1/admin/billing/plan-versions/{versionId}/publish` | Publish có `If-Match`/effectiveFrom và audit |
| GET/PATCH | `/api/v1/admin/billing/policies` | Get effective/draft policy; sửa draft và publish theo version; không sửa published |
| GET | `/api/v1/admin/billing/subscriptions` | Lọc theo merchant, trạng thái, expiry, page/cursor |
| GET | `/api/v1/admin/billing/transactions` | Đối soát, lọc theo provider/status/amount/date |
| POST | `/api/v1/admin/billing/adjustments` | Grant/credit có permission và reason (theo scope được duyệt) |
| POST | `/api/v1/admin/billing/reconciliation/{paymentId}` | Recheck thủ công theo audit và idempotency, không "đánh dấu đã trả" bằng tay vô điều kiện |

**API common contract:** authenticate/authorize theo merchant; `401` chưa login, `404` tài nguyên không tồn tại hoặc không được phép nhìn, `403` thiếu thao tác trên merchant thấy được, `409` stale/duplicate conflict hoặc `SUBSCRIPTION_REQUIRED` theo semantic contract, `422` giá trị không hợp lệ, `429` rate limit, `503` provider unavailable. Mã lỗi đề xuất: `BILLING_SUBSCRIPTION_REQUIRED`, `BILLING_SUBSCRIPTION_EXPIRED`, `BILLING_QUOTE_EXPIRED`, `BILLING_PRICE_CHANGED`, `BILLING_PAYMENT_PENDING`, `BILLING_PAYMENT_AMOUNT_MISMATCH`, `BILLING_PROVIDER_UNAVAILABLE`, `BILLING_ACCESS_DENIED`. Thống nhất sau đối chiếu `ApiException/ErrorCode` hiện tại. Responses server-owned: `merchantId`, `status`, `canSell`, `reasonCode`, `currentPeriodEnd`, `nextDueAt`, `planCode`, `currentPrice`, `allowedActions`.

**Contract-first:** export Spring OpenAPI → regenerate `packages/api-client`; frontend dùng generated operations nếu dự án cho phép. Không tự định nghĩa type lỏng hoặc bypass proxy/cookie/CSRF conventions. `ETag/If-Match` cho cấu hình mutable và resource cần optimistic lock.

---

## 8. Entitlement enforcement matrix — không chỉ sửa frontend

Đề xuất service trung tâm `BillingEntitlementService.requireMerchantCanSell(merchantId, at, operation)` và `BillingEntitlementService.evaluate(...)`. Kết hợp với `MerchantAccessPolicy`/ModerationPolicy hiện có, **không thay thế**. Không đưa DB call tới PSP trong request bán hàng.

| Nơi kiểm tra | Khi chưa có quyền bán | Khi quá hạn | Ngoại lệ |
|---|---|---|---|
| Tạo merchant | Cho tạo draft/profile nếu bật đăng ký, owner xác định DB | Không tạo merchant thay vì gia hạn shop cũ | Giới hạn abuse/rate limit/idempotency |
| Tạo/sửa draft catalog | Có thể cho sửa draft theo policy | Cho xem/sửa draft, không publish | Không bypass bằng update status trực tiếp |
| Publish/activate Product/Service/Resale thuộc merchant | Từ chối server-side | Từ chối | Kết hợp moderation và existing item readiness |
| Bật location offer, đặt hàng qua cart/direct | Seller không được kích hoạt offer khi inactive; buyer không commit order mới | Chặn order mới tại commit (revalidate) | Preview/commit consistency; seller expiry race |
| Discovery/search/public item detail | Loại khỏi kết quả mua được hoặc show trạng thái unavailable rõ ràng | Không tạo false purchasable offer | Like/comment history vẫn được lưu và truy cập theo policy |
| Need matching, seller response | Không tạo response mới cho merchant hết quyền | Không tạo mới | Các response cũ được lưu; đánh dấu unavailable nếu cần |
| Order đã tạo | Không xóa, không ép refund hoặc cancel | Seller được xử lý order cũ | Buyer vẫn xem, xác nhận, khiếu nại/đánh giá theo quyền |
| Social interactions | Buyer miễn phí | Không xóa like/save/comment | Các quy tắc kiểm duyệt hiện hành vẫn áp dụng |
| Merchant staff/member | Được xem phần quản lý cần thiết | OWNER luôn vào billing để gia hạn | STAFF không có quyền tiền |
| Ads gắn merchant | Rà soát vị trí hiển thị và chính sách giao dịch quảng cáo riêng | Không quảng cáo link dẫn đến offer không mua được | ADR về campaign đã trả tiền, credit/pause |
| Moderation/Admin | Không bị che/khóa vì invoice | Có thể tra cứu lịch sử | Moderation block luôn có precedence |

**Quan trọng:** tìm **tất cả** write endpoints và SQL predicates thực sự trước khi gắn guard. Ít nhất xét `MerchantApplicationService.create`, `catalog` publish/offer, `checkout` preview/commit, `orders`, `needs` response, `discovery` query, `ads` và sự kiện outbox. Check thời gian server tại transaction commit; đặt chính sách rõ cho đơn đã commit trước khi entitlement hết hạn. Không lấy `canSell` từ client/store/cache để cấp quyền.

**Hiệu năng:** ở public discovery nhiều sản phẩm, dùng predicate/sql join/batch phù hợp để không tạo N+1; index theo merchant và thời gian. Entitlement cache nếu có phải xử lý invalidation+expiry an toàn, không cấp thêm thời gian khi cache stale. Billing outage chỉ chặn write mới nếu thực sự không thể kiểm chứng; cho đọc và giải quyết đơn cũ. 

---

## 9. UI/UX Seller Center — thay đổi đề xuất

### 9.1. Route thực tế và route dự kiến

- Hiện có: `apps/web/app/(seller)/seller/page.tsx` tự chuyển tới merchant duy nhất, cho tạo shop mới bằng `MerchantForm`.
- Dự kiến: trang onboarding `/seller` hiển thị bước 1 tạo hồ sơ → bước 2 xem giá/chu kỳ/điều khoản → bước 3 thanh toán QR → bước 4 kết quả xác minh từ server.
- Dự kiến: `/seller/[merchantId]/billing` để OWNER xem kỳ hạn, hóa đơn, trạng thái thanh toán, gia hạn/hủy; đặt liên kết từ seller shell.
- Nếu shop chỉ có draft/chưa active: vẫn cho vào workspace setup nhưng chặn publish và nhận đơn bằng policy server; không redirect vô hạn hoặc lỗi 404 khó hiểu.

### 9.2. Màn hình/states bắt buộc

- Public plan pricing: 10.000đ/tháng lấy từ API, hỗ trợ trial/annual khi Admin bật; các cycle không bán không xuất hiện.
- Seller onboarding: stepper, form validation, preview shop, checkbox điều khoản, xác nhận giá/chu kỳ, disclosure thanh toán.
- Checkout: QR + mã tham chiếu; countdown quote/PSP expiry, copy reference, waiting/polling, retry an toàn, offline/error, back-navigation, no false success.
- Seller billing dashboard: `ACTIVE/TRIALING/PENDING/PAST_DUE/EXPIRED/CANCELLED`, days remaining, renewal CTA, last invoices, eligible actions server-owned; multi-shop merchant switcher không lẫn dữ liệu.
- Confirmation: chỉ hiển thị activated sau server GET trả entitlement hợp lệ; nếu webhook chậm hiển thị "đang xác nhận" kèm hỗ trợ.
- Accessibility/mobile 360/390/Desktop; loading/empty/error, focus, keyboard, screen reader, không dùng emoji làm icon UI, dùng icon library hiện có; không tái cấu trúc toàn bộ Seller Center ngoài scope.
- Buyer UX giữ nguyên login, checkout, social; item hết hạn hiện "Tạm thời chưa thể đặt hàng" thay vì crash/giả hết hàng.

---

## 10. Admin Billing Dashboard — quản trị động

Đề xuất sidebar `/admin/billing` với các màn:

1. **Tổng quan:** số merchant có quyền bán, trial, approaching expiry, unpaid, paid invoices, collected/refunds, reconciliation incidents; nguồn server có scope, currency, time range rõ.
2. **Gói thuê bao:** danh sách plan, draft/version, chu kỳ, giá, entitlement limits; publish/schedule/retire; không sửa bản PUBLISHED.
3. **Chính sách mở shop:** bật/tắt yêu cầu thanh toán, trial duration, giới hạn trial, grace days/mode, nhắc hạn, quy tắc gia hạn, chính sách giá cho khách cũ; validation+preview tác động.
4. **Thuê bao seller:** tìm merchant, xem OWNER, trạng thái, số ngày, paid_until, grant ngoại lệ, manual review, lịch sử state events.
5. **Giao dịch/hóa đơn:** provider, invoice, amount, currency, status, reconcile state, filter, CSV export có che PII theo permission.
6. **Khuyến mại (P1):** campaign validity/budget/usage ceiling/eligibility, chưa bật ngầm trong P0.
7. **Audit:** old/new policy refs, author, reason, approvals, timestamps, trước/sau, correlation/request ID; không expose PSP secrets.

**Permission:** billing read, plan editor, finance refund/adjustment, reconciliation ops tách roles nếu hệ thống Admin hiện chưa có role chi tiết thì ghi ADR và thực thi bước triển khai theo mức quyền thực tế. Hành động tiền cần xác nhận, bắt buộc reason; high-risk có thể maker-checker khi policy yêu cầu. Không biến `Admin` thành bypass logging.

**Giao diện "giá động" chỉ cho nhập cấu hình hợp lệ**; không cho script/sql arbitrary. Mọi thay đổi hiển thị trong Seller pricing mới sau publish/effective time (tôn trọng cache invalidation), không buộc sửa code cho việc thay giá trị.

---

## 11. Chuyển đổi an toàn cửa hàng đang tồn tại

**Không bật enforcement lên merchant cũ nếu chưa có mapping/grant.** Đầu Gate 0 xuất báo cáo read-only (không PII không cần thiết): merchant active/paused/suspended/deleted, owned locations, catalog publicly visible, open orders, owner memberships, ads campaigns, moderation state.

Kịch bản migrate:
1. Bật chế độ `billing.observe_only` hoặc `enforcement_enabled=false`; backfill billing association ở DB với migration/command idempotent, không thay merchant_status.
2. Lập danh sách cửa hàng cũ được cấp **legacy complimentary entitlement** theo thời hạn được người phụ trách phê duyệt; hoặc grandfather until date. **Không tự chọn ngày cấp miễn phí trong code.**
3. Gửi thông báo giá, thời gian chuyển đổi và link gia hạn; Admin đối chiếu preview số shop sẽ bị ảnh hưởng.
4. Chạy shadow decision: tính `canSell` theo billing nhưng chỉ log/metric, so sánh với quyền hiện tại; zero false denial trước enforcement.
5. Enable chỉ cho **new merchant cohort** (khi feature flag hỗ trợ) rồi pilot legacy merchant, sau đó mở rộng từng đợt.
6. Khi rollback: tắt enforcement, giữ invoices/payments/entitlements và các order đã tạo; không chạy down migration phá ledger/thuê bao, không đổi trạng thái merchant để rollback.

### Existing obligations invariant

- Seller expired sau khi buyer order `PENDING_CONFIRMATION`: seller được xác nhận/từ chối/hoàn tất theo luật order hiện có; buyer không mất quyền tra cứu.
- Buyer checkout bắt đầu trước expiry nhưng commit sau expiry: commit kiểm tra lại để không tạo order mới; UX lỗi có thể hành động/đổi merchant.
- Item đã bán vẫn có comment/reviews/history; điều chỉnh discovery/purchasability theo policy chứ không xóa.
- Ads liên kết merchant hết hạn phải được giải quyết riêng theo hợp đồng dịch vụ quảng cáo.

---

## 12. Kịch bản kiểm thử bắt buộc (contract + integration + E2E)

| ID | Scenario & kết quả mong đợi |
|---|---|
| BIL-001 | Buyer/guest dùng search, social like/save/comment, cart/checkout seller hợp lệ: không hỏi phí thuê bao buyer |
| BIL-002 | Một user có 2 shop: invoice/entitlement tách theo merchant; 1 shop expired không ảnh hưởng shop còn lại |
| BIL-003 | OWNER tạo draft khi chưa thanh toán; không thể publish/sell bằng API trực tiếp |
| BIL-004 | MANAGER/STAFF không được mua/hủy/hoàn phí nếu chưa được cấp quyền; cross-merchant read/write trả 403/404 đúng |
| BIL-005 | Admin đổi giá từ 10.000 lên 15.000: invoice đã issue giữ 10.000; quote mới dùng phiên bản/hiệu lực đúng |
| BIL-006 | Simultaneous admin publish/plan ETag mismatch: một request thành công, request stale bị conflict |
| BIL-007 | Quote expired/price changed/PSP unavailable: không tạo charge vô hạn, UI có retry |
| BIL-008 | Duplicated click / Idempotency-Key reuse cùng body → một invoice/attempt; khác body → conflict |
| BIL-009 | Duplicate webhook, reordered events, retry: chỉ gia hạn đúng một lần |
| BIL-010 | Webhook signature/timestamp sai, spoofed redirect: không kích hoạt |
| BIL-011 | Provider trả amount 9.000/11.000 thay vì 10.000 hoặc currency sai: không settle tự động |
| BIL-012 | Provider timeout sau charge, webhook mất/đến trễ: reconciliation khôi phục chính xác, không double charge |
| BIL-013 | Gia hạn sớm, gia hạn sau hạn, ngày 28/29/30/31, UTC/ICT, leap year: period chính xác |
| BIL-014 | Trial OFF => không cấp trial; ON => đúng duration/eligibility/abuse limit |
| BIL-015 | Grace READ_ONLY → không nhận order mới nhưng seller hoàn thành order cũ |
| BIL-016 | At checkout commit time subscription expired: reject new order; existing order/status unaffected |
| BIL-017 | Suspended by moderation dù paid => không bán; invoice không thay merchant moderation |
| BIL-018 | Public Search/Shop/Detail/Need không lộ offer mua được của merchant hết quyền; không N+1 query |
| BIL-019 | Buyer comment/like/save/review history không bị mất khi seller expired; Social vẫn đáp ứng moderation |
| BIL-020 | Legacy migration rerun 2 lần không cấp grant trùng; shadow mode không tác động traffic |
| BIL-021 | Admin permission phân loại thay giá/grant/reconcile; audit không thể bỏ qua |
| BIL-022 | Refund/reversal/chargeback xử lý theo policy, có immutable history và manual runbook |
| BIL-023 | DB chết giữa webhook/settle; worker retry sau recovery đúng một lần |
| BIL-024 | Admin tắt new seller registration: không cho shop mới, buyer/merchant hiện hữu không mất quyền |
| BIL-025 | Responsive 360/390/desktop + a11y keyboard/screen-reader + loading/error/empty/success/pending |
| BIL-026 | Disable enforcement rollback giữ dữ liệu tài chính, lịch sử, hoạt động buyer và nghĩa vụ order |
| BIL-027 | Seller checkout QR và buyer checkout COD/BANK_TRANSFER_NEGOTIATED không bị lẫn order/payment |
| BIL-028 | Renewal policy, versioned legacy/founder pricing và các thời điểm effective_from hoạt động đúng |
| BIL-029 | Poll/status endpoints, PSP callback và invoice exports không rò secret, PII hay thông tin shop khác |
| BIL-030 | Ads merchant expiry xử lý đúng theo ADR, không dẫn tới offer không thể mua mà không có thông báo |

**Test design:** API service/unit; repository Flyway/Testcontainers/PostgreSQL; API HTTP security/auth/CSRF; PSP contract test fake/sandbox; time-travel bằng `AppClock`; concurrency parallel transactions; Playwright seeded Buyer/Seller/Admin; query plans, metrics, security tests. Mock provider chỉ cho local/test; staging cần sandbox evidence. Không dùng số test gộp như bằng chứng từng trường hợp; mỗi ID phải có đường dẫn test, run command, PASS log, commit SHA.

---

## 13. Kế hoạch triển khai theo Gate — chỉ chuyển tiếp sau khi PASS

### G0 — Baseline, ADR và security/legal readiness (CHƯA CODE)

- [ ] `git fetch`, ghi `origin/main` SHA, `git status --short`, đọc AGENTS/RULES và kế hoạch Social mới nhất; không reset/clean/ghi đè thay đổi local.
- [ ] Rà endpoint/entity/query và liệt kê tất cả bypass vectors buyer/seller/admin/social/ads/Need/checkout; lập ma trận public+write permissions.
- [ ] Chốt ADR: merchant draft vs ACTIVE, gia hạn calendar, grace mode, invoice/tax, price policy khách cũ, legacy cutoff, refund, ads expiry, promotion P1.
- [ ] Kiểm tra điều kiện PSP, phí trên giao dịch 10.000đ, onboarding pháp nhân, ký webhook, legal/tax/invoicing; duyệt PSP adapter.
- [ ] Chốt schema/API/state machines/feature flags/quality plan; minh họa Figma/UI cho 360/390/desktop.
- **Gate:** ADR đã duyệt; checklist provider/legal được đánh dấu; không có unresolved decision ảnh hưởng tiền hay dữ liệu.

### G1 — DB, Billing Core và Admin Policy (chưa nhận tiền)

- [ ] Viết Flyway migration **số tiếp theo thực tế**; indexes/uniqueness/partial constraints; seed plan/policy mặc định với `enforcement_enabled=false`.
- [ ] Tạo `vn.covie.billing` theo package conventions (api/application/model/persistence/security/psp/outbox/worker).
- [ ] Read/Publish plan+policy version; typed validation, optimistic concurrency, history+audit+outbox.
- [ ] Implement entitlement resolver/grant/lifecycle/clock tests, owner authorization; không expose PII.
- [ ] Export OpenAPI; generate client; read-only Admin Plan/Policy UI.
- **Gate:** Flyway fresh+migrate existing works, G1 test BIL-002/004/005/006/013/014/021/028 PASS; no payment/live traffic impact.

### G2 — PSP, Invoice, Webhook và Reconciliation (sandbox only)

- [ ] Implement quote, invoice snapshot, idempotent payment attempt, PSP adapter, sandbox QR/link.
- [ ] Signed webhook + inbox dedup + atomic settle/entitlement grant + reconciliation worker + ops alerts.
- [ ] Seller trạng thái pending/success/failure; đầy đủ backup/restore và recovery after crash.
- [ ] Run callback replay/amount mismatch/currency/signature/refund exercise; xác minh provider fee & settlement.
- **Gate:** BIL-007/008/009/010/011/012/022/023/029 PASS; sandbox evidence + PSP contract approval; **chưa bật enforcement/thu phí thật**.

### G3 — Seller/Admin UI và API integration

- [ ] Seller onboarding/plan selection/QR/billing dashboard/invoice history/renewal.
- [ ] Admin billing plans/prices/policy/versioning/subscription/invoice/transaction/reconcile dashboard; scoped roles.
- [ ] Responsive + error/loading/empty + accessibility; dùng generated API client, Phosphor icons, quy chuẩn CSS hiện có.
- [ ] Ghi Playwright flows khi tạo thêm shop, user vừa buyer vừa seller, switch merchant.
- **Gate:** BIL-001/002/003/004/025/027 PASS và visual/manual review; không dùng screen hardcoded price.

### G4 — Enforcement, discovery và Legacy Migration

- [ ] Triển khai guard **server-side** từng entrypoint theo inventory G0; central entitlement policy.
- [ ] Không gây N+1 discovery; đảm bảo checkout commit kiểm tra lại quyền; giữ order cũ và social records.
- [ ] Backfill/grant legacy idempotent; shadow decision; thử pilot new merchant + selected existing merchants.
- [ ] Quyết định/triển khai ads handling theo ADR, ẩn hoặc disable purchasable offer không đủ quyền.
- **Gate:** BIL-015/016/017/018/019/020/024/030 PASS; không có regression buyer checkout; shadow false-denial = 0 trên cohort kiểm thử.

### G5 — Staging, operations và production readiness

- [ ] Chạy full Maven verify, OpenAPI client check, web test/typecheck/lint/build, Playwright Buyer+Seller+Admin, `pnpm ui:verify-local` và `git diff --check`.
- [ ] Clean Flyway migration, security regression, load/concurrency, index plans, 1+ full renewal cycles bằng fake clock/sandbox, backup/restore, rollback rehearsal.
- [ ] Staging HTTP TLS, secrets manager, idempotency/reconciliation visibility, metrics + alert test, policy-change audit, CSRF/cookies.
- [ ] Legal/tax/invoice, operator runbook, seller terms/privacy, on-call and PSP support contacts được duyệt.
- **Gate:** Evidence matrix BIL-001..030 green, no severity critical/high, staging verified; release checklist ký duyệt.

### G6 — Phát hành có kiểm soát

- [ ] Deploy với enforcement OFF; verify DB + metrics + permissions.
- [ ] Pilot new merchants; merchant cũ dùng complimentary grant rõ thời hạn, thông báo trước.
- [ ] Tăng cohort theo feature flag, giám sát failed payments, incorrect denials, charge duplicates, reconciliation lag, support tickets.
- [ ] Rollback qua feature flag + service release theo runbook; không sửa/xóa invoice/payment evidence.
- **Gate:** Người quản lý ký chấp thuận bật thu phí thật; chỉ chốt production khi có bằng chứng end-to-end và đối soát.

---

## 14. File/điểm tích hợp dự kiến, để agent tìm chứ không tạo bừa

**Backend nên bổ sung:** `apps/api/src/main/java/vn/covie/billing/` với DTO/Controller/Services/Repositories/Security/PSP Adapter/Workers; migration mới `apps/api/src/main/resources/db/migration/V{NEXT}__seller_billing_*.sql` sau khi xác minh latest; tests trong `apps/api/src/test/...`.

**Backend cần rà và thay đổi có kiểm soát:** `merchants/application/MerchantApplicationService.java`, `merchants/security/MerchantAccessPolicy.java` và capabilities; catalog lifecycle/publish/offer/discovery; checkout preview/commit; Need responses; ads delivery; `settings/application/SystemSettingRegistry.java` và Admin settings; notification/outbox; `shared/web/ErrorCode`; OpenAPI export. Không tự đổi raw table `orders` để lưu seller billing.

**Frontend:** `apps/web/app/(seller)/seller/page.tsx`; `features/merchant/merchant-form.tsx`; `features/seller/`; thêm routes `apps/web/app/(seller)/seller/[merchantId]/billing/`; `apps/web/app/(admin)/admin/billing/`; `apps/web/features/admin/` và sidebar; shared types trong generated `packages/api-client`. Giữ nguyên routing merchantId hiển thị trong URL, không giữ merchant ngầm client session.

**Tài liệu:** ADR mới dưới `docs/architecture/` hoặc theo convention hiện hành; payment provider runbook `docs/runbooks/`; staging/incident/rollback under `docs/operations/`; test evidence matrix `docs/plans/seller-billing/`.

---

## 15. Non-functional requirements / Monitoring

- Audit bắt buộc các action `PLAN_PUBLISHED`, `POLICY_CHANGED`, `SUBSCRIPTION_ACTIVATED/EXPIRED`, `PAYMENT_SETTLED/REFUND_REQUESTED`, `ENTITLEMENT_GRANTED/REVOKED`, `RECONCILIATION_OVERRIDE` với actor + correlation ID, không log secret.
- Metrics: invoice created/paid/unpaid, distinct merchant active/trial/past_due, webhook latency/rejections/replays, payment intent success/failure, duplicate-detection, reconciliation backlog/lag, duration of entitled lookups, stale cache and erroneous write denials.
- PSP webhook ACK nhanh theo hợp đồng, xử lý queue/inbox bền vững; bounded retries/backoff/DLQ/manual triage; alerts đến kênh ops có thật.
- Privacy: tối thiểu hóa buyer/seller PII, masking fields, scope kiểm soát export, retention pháp lý; không log raw bank details.
- Backpressure/rate limit: checkout create/quote/poll/webhook, định mức media catalog theo plan khi entitlement feature thực sự được triển khai.
- Không thêm dependency mới nếu cùng năng lực đã có; Spring PostgreSQL/Flyway, outbox, CSRF, auth conventions phải được giữ.
- Downtime PSP: vẫn hiển thị merchant đã paid và xử lý order cũ từ DB; chỉ tạo payment mới bị degrade an toàn.

---

## 16. ADR cần chủ dự án xác nhận trước G1/G2/G4

| ADR | Mặc định đề xuất | Bắt buộc duyệt ở |
|---|---|---|
| A01. Khởi tạo shop | Lưu merchant draft, chưa mở bán cho đến entitlement hợp lệ; cách mô hình draft không phá status hiện hữu | G0 |
| A02. Phí | 10.000 VND/MONTH/MerchantProfile, nhiều location/staff không tăng phí | G0 |
| A03. Các chu kỳ được bán | Tháng, annual/multi-month không bật mặc định | G0 |
| A04. Trial | Tắt; cho Admin bật kèm anti-abuse | G0 |
| A05. Grace | 3 ngày READ_ONLY, seller xử lý đơn cũ | G0 |
| A06. Existing sellers | Phân nhóm/grant có cutoff do owner quyết định, không tự đặt ngày | G0 trước G4 |
| A07. Kênh thanh toán | Chọn PSP theo hợp đồng/costs/docs thật; không auto debit P0 | G0/G2 |
| A08. Thuế/hóa đơn | Chưa chốt VAT/invoice type; kiểm chứng với pháp nhân/đơn vị tư vấn | Trước G2 và G5 |
| A09. Refund | Manual/adapter workflow có chứng từ, không trừ kỳ tự động nếu không policy | G2 |
| A10. Ads khi hết hạn | Pause merchant-linked delivery hoặc xử lý credit theo hợp đồng ads | G4 |
| A11. Giá với subscriber cũ | Theo lịch hiệu lực + thông báo đã duyệt, không retroactive | G0/G1 |
| A12. Resale/đăng cá nhân | Nếu là catalog thuộc merchant thì áp entitlement; nếu muốn C2C miễn phí phải là PRD khác | G0 |
| A13. Staff/Manager billing rights | OWNER thanh toán; MANAGER chỉ xem trạng thái; STAFF chỉ xem quyền cần thiết | G0 |
| A14. Promotion/free grant | P1, nhưng legacy grant P0 có kiểm soát | G0/G4 |
| A15. Subscription cancellation | Không auto renew P0; cancel chỉ thay future renewal, không hủy kỳ đã paid | G1 |

Nếu ADR chưa duyệt, AI **phải dừng ở thiết kế hoặc sandbox**, không tự chọn lời giải có hậu quả tài chính/pháp lý.

---

## 17. Chỉ dẫn thực thi cho Antigravity / AI coding agent

1. **Task isolation:** Mỗi Gate chia thành các PR nhỏ có diff reviewable; không sửa đồng thời những phần Social/Barcode/UI khác đang phát triển; không push thẳng `main` nếu protected.
2. **Preflight mỗi PR:** fetch origin/main, branch HEAD, file inventory, inspect open PR/conflicts, xác nhận migration highest và routes cập nhật. Không viết theo paths giả định nếu code đã thay đổi.
3. **Trước khi code:** trình bày impact matrix các API/SQL, ADR, tests cần thay đổi và rollback; chỉ thực thi Gate đã duyệt.
4. **Không hardcode:** prices/cycles/trial/grace/renewal/discount/feature limit trong JSX, Java constant làm nguồn sự thật. Constant cho min/max validation và trạng thái giao thức thì được.
5. **Mọi bước tài chính phải TDD:** viết test red cho invariant quan trọng, implement nhỏ nhất, chạy focused tests rồi full regression.
6. **OpenAPI-owned:** backend contract → export → generated client → TS consumers; không sửa generated artifact bằng tay.
7. **PR evidence:** commit SHA, file list, command exit code, test count, screenshots/recordings, migrations, PSP sandbox correlation samples đã redact, review notes và remaining blockers.
8. **Không chạm secrets:** không thêm provider keys vào repo/PR/test snapshots; dùng dummy tokens và hướng dẫn secret manager.
9. **Không tự merge/deploy:** mỗi Gate báo kết quả để owner review; chỉ qua Gate sau khi có phê duyệt và evidence.

### Acceptance cuối cùng

Hoàn thành khi **Admin đổi các chính sách đã hỗ trợ không cần sửa code**, seller mới chỉ bán khi backend cấp entitlement đúng, seller cũ không bị khóa bất ngờ, buyer/Social/đơn cũ vẫn hoạt động, giao dịch có xác thực/đối soát/chống trùng, dữ liệu kế toán immutable, và toàn bộ BIL-001..030 cùng staging/release gates có bằng chứng PASS. **Kế hoạch này không đồng nghĩa bất kỳ chức năng nào đã được triển khai.**

---

## 18. Liên kết tham khảo trong repository

- `README.md` — local stack, auth/proxy, generated OpenAPI và test instructions.
- `docs/CoVie-product-plan-v0.3.md` — nguyên tắc discovery → matching → order.
- `docs/CoVie-technical-plan-03-merchant-location-media-catalog-v1.0.md` — Merchant/Location/Catalog/permissions.
- `docs/CoVie-technical-plan-04-shipping-cart-inventory-checkout-v1.0.md` — order/checkout V1 và payment buyer.
- `docs/CoVie-technical-plan-08-moderation-system-settings-admin-v1.0.md` — Admin/config/modes/history.
- `docs/plans/social-product-card/CoVie_Social_Product_Card_Ke_Hoach_Trien_Khai_Chi_Tiet_v1.0.md` — tính năng Social mới, ràng buộc không xóa tương tác.
- `apps/api/src/main/resources/db/migration/V004__location_merchant_media.sql`, `V009__order_creation_foundation.sql`, `V013__moderation_system_settings_admin.sql`, `V015__advertising.sql`, `V018__catalog_social_interactions.sql`, `V019__social_comment_moderation.sql`.

**Kết thúc bản kế hoạch v1.0 — document-only.**
