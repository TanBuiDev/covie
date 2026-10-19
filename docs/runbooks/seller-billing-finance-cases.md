# Seller Billing: xử lý refund, reversal và dispute thủ công

## Phạm vi và giới hạn

Runbook mô tả chức năng P0 hiện có, không cấp quyền chuyển tiền, hoàn tiền qua ngân hàng/payOS hoặc bật thu phí. API CoVie chỉ ghi nhận một khoản hoàn toàn phần **đã được thực hiện và xác minh độc lập bên ngoài**. Không có lệnh payout/refund tự động. Redirect của browser, ảnh QR hoặc reference do người yêu cầu tự nhập không phải bằng chứng nhận/hoàn tiền.

Giữ các cờ payment/release/reconciliation/enforcement thông thường OFF cho đến khi có quyết định vận hành riêng. Không nhập secrets, số tài khoản, chứng từ ngân hàng đầy đủ hoặc raw provider payload vào chat, Git, audit metadata hay case note. Evidence tài chính thật phải ở hệ thống lưu trữ riêng có kiểm soát truy cập; case chỉ dùng reference tối thiểu để tra cứu.

Partial refund và tự động xử lý chargeback không được API này hỗ trợ. Không dùng CONFIRMED để biểu diễn một khoản hoàn một phần hoặc một sự kiện ngân hàng chưa được xác minh; giữ điều tra và chuyển người có thẩm quyền quyết định theo chính sách đã duyệt, không sửa amount/period/pricing snapshot trong DB.

## Quyền và chuẩn bị

- Seller phải là OWNER hiện hành của đúng merchant để mở case cho invoice PAID; MANAGER/STAFF hoặc merchant khác không được thao tác tài chính.
- `billing.finance_roles_enabled` mặc định false: giữ hành vi global Admin hiện có. Trong phạm vi tài chính khi bật tách quyền, READER chỉ đọc; OPERATOR được review/reject; APPROVER được xác nhận refund và quản lý grant, vẫn chịu các cờ duyệt vận hành riêng. Quyền cấu hình giá vẫn theo global Admin hiện hành, không được suy ra từ tên finance role.
- Cấp/thu hồi finance role qua `/api/v1/admin/billing/finance-access/{userId}` cần một **global Admin khác** và cờ backend `COVIE_BILLING_FINANCE_ROLE_ASSIGNMENT_APPROVED=true` trong phiên vận hành có giám sát. Không tự cấp cho mình, không cấp global ADMIN qua API này, không sửa JWT.
- Xác nhận hoàn tiền cần `COVIE_BILLING_REFUND_CONFIRM_APPROVED=true` ở backend được phê duyệt. Manual grant/revoke độc lập cần `COVIE_BILLING_ADMIN_GRANTS_APPROVED=true`. Không đưa các cờ/khóa backend vào môi trường Web; trả cờ duyệt vận hành về false sau phiên được phép.
- Người yêu cầu, Admin reviewer và Admin approver phải là ba danh tính khác nhau. Reviewer không được tự đổi vai rồi duyệt cùng case; OWNER đồng thời là Admin vẫn không được tự review/confirm refund của mình.

## Quy trình REFUND

1. Đối chiếu đúng merchant/invoice, trạng thái PAID, amount/currency và tham chiếu thanh toán. Dùng invoice hiện có, không tạo lại checkout để giải quyết tranh chấp.
2. OWNER gọi `POST /api/v1/merchants/{merchantId}/billing/invoices/{invoiceId}/finance-cases` với `kind=REFUND` và reason có ý nghĩa, không chứa dữ liệu ngân hàng nhạy cảm. Khi invoice còn PAID, cùng invoice/kind trả case đã có, không tạo thêm case hay REQUESTED audit/outbox. Sau khi invoice đã REFUNDED, không mở lại yêu cầu; đọc trạng thái/hồ sơ hiện có.
3. Admin có quyền đọc dùng `GET /api/v1/admin/billing/finance-cases`. OPERATOR độc lập gọi `POST /api/v1/admin/billing/finance-cases/{caseId}/resolve` với `status=REVIEWING` và note. REQUESTED → CONFIRMED trực tiếp bị từ chối.
4. Việc thực hiện hoàn tiền bên ngoài chỉ do người có thẩm quyền tiến hành sau phê duyệt thực tế. CoVie không thực hiện bước chuyển tiền này. Người duyệt phải kiểm tra evidence bên ngoài, không chỉ tin case note.
5. APPROVER khác reviewer/requester ghi `status=CONFIRMED`, note và `externalReference` của kết quả đã xác minh. Reference là chuỗi6–180ký tự thuộc `[A-Za-z0-9._/-]`; dùng mã tra cứu tối thiểu, không dùng secrets hoặc toàn bộ chứng từ.
6. Kiểm tra kết quả: case CONFIRMED lưu reviewer/approver và thời điểm; invoice/attempt REFUNDED; amount, kỳ hạn và pricing snapshot lịch sử không thay đổi. Chỉ grant PAID_INVOICE của invoice đó bị revoke; grant ADMIN/TRIAL/legacy/paid khác không bị xóa. Nếu còn grant hợp lệ khác, merchant vẫn có quyền theo evaluator.
7. Kiểm tra audit REQUESTED/REVIEWING/CONFIRMED và outbox REQUESTED; không suy diễn chuyển tiền từ các bản ghi này. Retry xác nhận case đã kết thúc trả409 và không sửa ledger thêm. Không xóa case/invoice/payment để "thử lại".

Nếu không chấp thuận yêu cầu, OPERATOR/APPROVER có thể ghi REJECTED với note. Đây là kết luận của case, không phải lệnh hoàn tiền hay quyền sửa invoice.

## Reversal/chargeback/DISPUTE

- Khi có thông báo ngân hàng/provider hoặc tranh chấp cần điều tra, OWNER mở case `kind=DISPUTE` cho invoice PAID. Reviewer ghi REVIEWING và giữ reference bằng chứng ở nơi riêng.
- DISPUTE không được CONFIRMED như REFUND: API trả409, ledger/entitlement không đổi. Chỉ ghi REJECTED khi đó thực sự là quyết định được phê duyệt; không dùng REJECTED làm cách giả lập "đã hoàn tiền".
- Nếu đã có một khoản hoàn toàn phần được phê duyệt/thực hiện/xác minh, dùng quy trình REFUND độc lập ở trên. Không tự đóng DISPUTE, tự thu hồi kỳ hạn, tạo payout hay đánh dấu PAID/REFUNDED bằng SQL để biểu diễn một reversal chưa rõ.
- Với partial refund, evidence thiếu hoặc trạng thái provider không hỗ trợ: dừng ghi nhận tài chính cuối cùng, giữ hồ sơ điều tra và chuyển người phụ trách. Không phát minh policy, amount điều chỉnh, credit hoặc trạng thái kế toán mới.

## Khi lỗi và đối soát

- 401/403/404: kiểm tra phiên, membership/global Admin/finance role hiện hành; không nâng quyền bằng JWT hoặc đổi merchantId để thử bypass.
- 409: đọc trạng thái case và invoice hiện có; không tạo checkout/grant mới để vượt independent approval hoặc case đã kết thúc.
- 422: sửa dữ liệu hợp lệ, không hạ validation hay thêm reference giả vào môi trường thật.
- 500 hoặc không chắc transaction đã commit: đọc lại case/ledger/audit trước khi retry. Audit lỗi phải khiến thao tác tài chính rollback; không bỏ audit để làm API thành công.
- Admin OPERATOR/APPROVER có thể đối soát trong phần Giao dịch gần đây của `/admin/billing` khi invoice có server-owned `allowedActions=RECONCILE`. Reader chỉ xem. API là `POST /api/v1/admin/billing/invoices/{invoiceId}/reconciliation`, body `{"reason":"Lý do kiểm tra không chứa dữ liệu bí mật"}` và header `Idempotency-Key` bắt buộc. Backend kiểm tra quyền hiện hành cả khi replay.
- Khi timeout/mất response hoặc409 đang xử lý, giữ nguyên invoice/reason/key rồi thử lại sau; không đổi key để vượt request đang chạy. Cùng key/body đã hoàn thành trả đúng kết quả lần trước, không thực hiện provider GET thêm. Muốn có một lần kiểm tra mới sau khi đã nhận kết quả, thực hiện thao tác mới; UI tạo key mới. Thay đổi reason với cùng key bị409.
- Kết quả chỉ phản ánh `checkedAt`, không phải trạng thái realtime. `PROVIDER_FAILURE`, mismatch hoặc `MANUAL_REVIEW_REQUIRED` cần điều tra, không được tự coi là PAID. Chỉ invoice đang chờ được recover/void theo bằng chứng provider đã xác minh; invoice cuối cùng chỉ được đọc, không tự hoàn tiền hay sửa ledger. Trạng thái EXPIRED/CANCELLED thiếu hoặc có tiền nhận không được coi là bằng chứng VOID nhất quán.
- REQUESTED audit ghi actor/reason/operation trước khi gọi provider; COMPLETED audit và kết quả idempotency commit cùng transaction. Audit lỗi không được bỏ qua; sau lỗi completion, request có thể còn lease kỹ thuật30giây, dùng cùng key để tiếp tục. REQUESTED vẫn được giữ để truy vết; settlement đã xác minh có transaction/audit độc lập, nên luôn đọc ledger trước khi suy diễn từ lỗi response.
- Không tự gọi provider thật khi chưa có phê duyệt/configuration phù hợp. Khả năng đọc provider không đồng nghĩa được bật thu phí hoặc scheduler: giữ payment/release/reconcile/enforcement OFF trong công việc local. Xem `local-manual-reconciliation-progress-2026-10-18.md` cho bằng chứng fake-provider, không dùng nó thay nghiệm thu ngân hàng.

## Chứng cứ local, không phải nghiệm thu ngân hàng

`local-finance-audit-progress-2026-10-18.md` trong `docs/plans/seller-billing/` ghi exact test/log/count. Fixture chỉ dùng HTTP provider giả, dữ liệu disposable và reference `FIXTURE-NO-BANK-TRANSFER`; không có khoản hoàn ngân hàng thật. Các cờ approval=true chỉ trong Spring test fixture, không xác nhận approval/activation của môi trường vận hành.
