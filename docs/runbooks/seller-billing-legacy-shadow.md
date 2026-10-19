# Seller Billing — legacy grant và shadow preview

## Giới hạn

Đây là quy trình do operator chủ động gọi cho cohort có cutoff rõ ràng, không phải tác vụ tự chạy hay replay traffic production. Preview chỉ tính **billing entitlement**: không quyết định thay moderation, merchant visibility, offer readiness, tồn kho hoặc luật order. Không dùng số liệu aggregate để tự tuyên bố zero false denial cho toàn bộ commerce.

Không tự chọn ngày miễn phí/cutoff, bật enforcement, thu phí, tạo invoice hoặc gọi payOS. Local acceptance chỉ sử dụng dữ liệu disposable; quyền duyệt trong fixture không cấp quyền thao tác dữ liệu thật. Các cờ payment/release/reconcile/enforcement bình thường vẫn OFF.

## Preview / shadow trước chuyển đổi

1. Người phụ trách cung cấp cutoff `createdBefore` và danh sách/cohort đã đối chiếu với inventory, moderation, order đang mở và nghĩa vụ quảng cáo. Không lấy ngày fixture từ bài test làm lịch chuyển đổi thật.
2. Global Admin có quyền tài chính READER trở lên gọi GET `/api/v1/admin/billing/legacy/preview?createdBefore=<ISO-8601>`. Dùng phiên xác thực private hiện có; không dán token hoặc secret vào chat/log/report. Response no-store.
3. Các trường cũ được giữ nguyên: `eligibleMerchants`, `previouslyGranted`, `createdBefore`. Cohort gồm merchant chưa soft-delete và `created_at < createdBefore`; cutoff tương lai bị từ chối.
4. `shadow` là snapshot billing-only có `decisionScope=BILLING_ENTITLEMENT_ONLY`, `observedAt`, `enforcementEnabled`, `evaluatedMerchants`, `currentlyAllowed`, `wouldAllow`, `wouldDeny`. Khi enforcement OFF, billing hiện cho phép toàn cohort; wouldAllow/wouldDeny vẫn tính theo grant thật. Khi enforcement ON, currentlyAllowed bằng wouldAllow. Preview không thay đổi cờ hoặc cấp quyền bán.
5. Grant cho phép bán chỉ khi chưa revoke, `valid_from <= observedAt` và `valid_until > observedAt`. Grant future/expired/revoked và grace READ_ONLY không được tính là quyền tạo bán hàng mới. Nhiều grant hợp lệ chồng nhau chỉ tính merchant một lần.
6. Các phép đọc preview cùng snapshot REPEATABLE_READ. Không ghi merchant, grant, invoice, setting, order, outbox hay audit từ preview. Số liệu được phản ánh bằng Micrometer `covie.billing.shadow.previews` (tag enforcement ON/OFF) và `covie.billing.shadow.decisions` (decision ALLOW/DENY). Không có identifier/PII labels. Counter đếm **lượt đánh giá**: preview lại sẽ tăng counter, không phải số merchant duy nhất. Kiểm tra tên xuất thực tế trong monitoring của môi trường trước khi tạo PromQL/alert.

## Grant được duyệt và chạy lại an toàn

- Chỉ global Admin APPROVER (hoặc fallback Admin khi tách quyền đang OFF), với backend `COVIE_BILLING_LEGACY_GRANTS_APPROVED=true` trong phiên được duyệt, được gọi POST `/api/v1/admin/billing/legacy/grant`.
- Body chứa `createdBefore`, `validUntil`, `reason`. Deadline phải ở tương lai và không vượt giới hạn10năm hiện hành; reason bắt buộc tối đa400ký tự, không chứa bí mật/chứng từ ngân hàng. Không chạy khi enforcement ON; backend trả422.
- Backend chỉ tạo LEGACY grant thiếu theo unique source_type/source_id. Lặp lại cùng lệnh không cấp trùng, không kéo dài hay sửa grant cũ. Grant đã revoke/expired vẫn là lịch sử, không được khôi phục lén bằng chạy lại migration; dùng quy trình điều chỉnh được duyệt riêng nếu thực sự cần.
- Mỗi thao tác grant hợp lệ vẫn audit actor/correlation/cutoff/deadline/reason/newly-created count. Lần replay có created0 không đồng nghĩa không có audit. Không có provider charge, invoice hoặc thay merchant_status trong lệnh này.
- Preview lại và đối chiếu từng trường hợp kỳ vọng của cohort, không chỉ một tổng đếm. Nếu wouldDeny khác kỳ vọng, dừng rollout để điều tra; không tắt validation hay sửa lịch sử để làm số liệu đẹp. Giữ các nghĩa vụ cũ và quyền buyer theo policy hiện hành.

## Không phải điều kiện tự bật thu phí

Preview/grant local không thay thế inventory thực, thông báo Seller, quyết định cohort/deadline, approval rollout, staging/provider/bank evidence hoặc rollback/recovery kiểm chứng. Enforcement, activation và real-money vẫn cần đúng gate riêng.

Bằng chứng local được ghi trong `docs/plans/seller-billing/local-legacy-shadow-progress-2026-10-18.md` và bảng BIL gốc. Không coi comment OBSERVE hoặc enforcement OFF đơn thuần là đã thực hiện shadow.
