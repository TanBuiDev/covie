# CoVie — Kế hoạch đóng toàn bộ công việc còn lại

> **For agentic workers:** Dùng executing-plans khi được yêu cầu thực thi. Hoàn thành từng checkbox bằng bằng chứng thực tế. Không biến lượt lập kế hoạch này thành việc tự bật enforcement, chuyển tiền hoặc deploy.

**Goal:** Đối chiếu toàn bộ phạm vi đã duyệt, hoàn tất luồng Seller Billing thật, xác minh các phần FE/BE còn chưa đủ bằng chứng và chuẩn bị phát hành mà không sửa lại chức năng đang đúng.

**Architecture:** Giữ nguyên Catalog/commerce/Social/Billing và API contract hiện có. Tách rõ hoàn thành mã nguồn, nghiệm thu local, kích hoạt chính sách, giao dịch thật và triển khai production. Chỉ sửa code khi có lỗi tái hiện hoặc vi phạm yêu cầu đã duyệt; không dùng việc thiếu hạ tầng làm lý do dừng các việc local độc lập.

**Tech Stack:** Java25/Spring/Flyway/PostgreSQL18, Redis8, Next.js16.4.1, pnpm, payOS, Docker/GitHub Actions, Gitleaks/Trivy, Prometheus/Grafana.

## 1. Phạm vi và cách đọc

**Yêu cầu mới nhất2026-10-20:** Owner yêu cầu bật cả payment/release/enforcement/reconciliation trên local, sau đó cung cấp credential Admin qua Drive. Cả bốn hiệnON: gauges1/1; GLOBAL enforcement qua PATCH201, effective/onboarding true, history/audit và outboxPUBLISHED; phiên Admin tạm logout204. Phạm vi này thay thế chỉ dẫn OFF tương ứng bên dưới; không bao gồm Social/video/deploy/chuyển tiền. Invoice vẫn0; chưa nghiệm thu giao dịch thật. Xem current acceptance index và receipt riêng tư `.local/payos-live/enable-current.json`.

**Cập nhật thực thi2026-10-20:** Tiến độ và bằng chứng tại `docs/operations/project-remaining-work-progress-2026-10-19.md`, trạng thái từng W tại `project-acceptance-current-2026-10-19.md`. Kế hoạch chưa hoàn tất toàn bộ. AGENTS mới nhất yêu cầu giữ cờ tiền/release/reconciliation/enforcement/Social OFF, không ghi recovered DB, thanh toán thật hoặc deploy; baseline ON bên dưới là lịch sử, không phải trạng thái hiện tại.

- Kế hoạch này có **14 gói công việc W01–W14**, không phải14 lỗi FE/BE.
- Đây là danh mục công việc còn lại đã nhận diện từ code, yêu cầu, các ma trận và vận hành hiện tại. W01 phải hoàn tất đối chiếu tất cả yêu cầu trước khi khẳng định danh mục đã đầy đủ; không thể cam kết trước rằng không còn lỗi nào khác.
- Một thiếu sót cấu hình hiện tại đã xác nhận: enforcement mặc địnhOFF trong khi chính sách là PAY_BEFORE_SELL. Một sai sót hướng dẫn đã xác nhận: `/seller/billing` không phải trang thanh toán của một cửa hàng; route đúng là `/seller/{merchantId}/billing`.
- Chưa có bằng chứng code FE/BE sai trong tình huống tạo cửa hàng vừa báo. UI hiện đi theo enforcementOFF. Tạo hồ sơ trước thanh toán là đúng nghiệp vụ; **mở bán** mới cần entitlement khi enforcementON.
- Đã bỏ yêu cầu khớp Figma. Không đưa nó trở lại backlog. Staging không là điều kiện để hoàn thành local; bằng chứng production chưa làm phải được ghi riêng, không giả lập hoặc xóa điều kiện thực tế của workflow release hiện có.
- Không mở lại các mục PASS trên đường code không đổi. Không viết hàng loạt test giả định. Không sửa SQL đã áp dụng, repair/baseline lịch sử, reset dữ liệu, bật toàn bộ flags hay refactor diện rộng.
- Không tự tạo nhánh/commit/push trong lượt lập kế hoạch. Khi thực thi, chỉ đưa lên Git thay đổi đã review và được phép; không đẩy env, key, dump, log hoặc chứng từ ngân hàng.

## 2. Baseline đã kiểm tra ngày2026-10-19

| Nội dung | Trạng thái / giới hạn |
|---|---|
| Git | Local HEAD và remote main cùng `5ad071ffb9f3c2f9d7cfaf327c09726690dc3c1a`; remote chỉ có main ở thời điểm kiểm tra |
| CI source | PR39/40 và final main đã có các checks thành công trong bàn giao; không phải chứng minh thanh toán ngân hàng hoặc runtime production |
| Runtime local | API đang báo metric `covie.billing.payment.creation.enabled=1`; reconciliation worker=0 |
| payOS thật | Ba key có trong env server; confirm-webhook thực tếHTTP200/code00; chữ ký giả bị401. Không công khai key |
| Tunnel | Tạm thời, chỉ webhook + redirect return về local; không phải hosting production. URL trong `.local/payos-live/activation.json` có thể hết hiệu lực |
| Billing policy | DB hiện PAY_BEFORE_SELL, trial0, grace3; không có GLOBAL override cho enforcement/registration; registry mặc định enforcement=false, registration=true |
| Tiền thật | Chưa chứng minh tạo QR từ UI Seller rồi nhận tiền ngân hàng và cấp entitlement. Callback mẫu xác nhận URL không phải giao dịch thật |
| Dữ liệu | DB runtime hiện là database recovered đã cóV040. Trước bật payOS đã backup cấu hình và đúng DB này riêng tư; restart validate40, không áp dụng migration |
| Dirty work | `apps/web/AGENTS.md`, `apps/web/next-env.d.ts` đang có thay đổi từ dev runtime; phải bảo toàn, không gộp vô điều kiện vào thay đổi mới |
| Lỗi UI lịch sử | Báo cáo Billing ghi audit-filter overlap ở1280; chưa tái xác nhận trên runtime hiện tại, không gọi là lỗi hiện tại đã chứng minh |
| Advisory | Handoff ghi1high dev-only Braces, production dependency audit0 tại lần đo đó. Cần triage hiện trạng, không suy thành lỗ hổng production đã xác nhận |

### Nguồn đối chiếu ưu tiên

1. `docs/CoVie-product-plan-v0.3.md`, `docs/CoVie-technical-plan-v1.0.md` và bộ technical-plan01–10: yêu cầu gốc, không tự cộng ý tưởng tương lai vào phạm vi bắt buộc.
2. `docs/plans/social-product-card/CoVie_Social_Product_Card_Ke_Hoach_Trien_Khai_Chi_Tiet_v1.0.md`, `v2-local-acceptance-matrix-2026-10-17.md`, `v2-final-local-acceptance-2026-10-17.md`.
3. `docs/plans/seller-billing/CoVie-Seller-Billing-Subscription-Implementation-Plan-v1.0.md`, `acceptance-original-bil-v1-local-2026-10-17.md`, `local-whole-numbered-plan-acceptance-2026-10-19.md`, `local-final-acceptance-2026-10-19.md`.
4. `docs/operations/main-handoff-2026-10-19.md`, `migration-lineages.md`, `codeql-handoff-triage-2026-10-19.md`: integration/lineage/security hiện tại. Các đoạn IN PROGRESS cũ chỉ có nghĩa theo thời điểm ghi.
5. `docs/plans/seller-billing/payos-setup-and-local-acceptance.md`, `real-money-pilot-gate.md`, `docs/runbooks/seller-billing-observability.md`, `docs/runbooks/seller-billing-legacy-shadow.md`.
6. `docs/operations/release-evidence-gaps-2026-10-13.md`, `staging-rehearsal.md`, `production-topology.md` và workflow thực tế: dùng để nhận diện giới hạn, không lấy yêu cầu cũ đã sửa làm backlog mới.

`docs/plans/seller-billing/acceptance-matrix-v1.md` dùng bộ ID lịch sử khác; không thay cho original BIL001–030. Các file Figma/handoff cũ ghi missing route phải đối chiếu route hiện có trước khi yêu cầu viết lại.

## Chunk 1: Chốt phạm vi và hoàn tất thanh toán local thật

### W01 — Một ma trận nghiệm thu duy nhất cho toàn dự án — P0

**Tạo khi thực thi:** `docs/operations/project-acceptance-current-2026-10-19.md` và `docs/operations/project-open-issues-2026-10-19.md`.

- [ ] Liệt kê từng yêu cầu trong phạm vi đã duyệt của01–10, toàn bộ G0–G6/F/ADR Social và các mục đánh số Billing; không chỉ đếm30 BIL hay các checkbox trong bản kế hoạch cũ.
- [ ] Gắn mỗi dòng với code/API/UI hiện có, evidence cụ thể, source/artifact SHA và trạng thái: PASS_LOCAL / VERIFIED_LIVE / CONFIRMED_GAP / NOT_VERIFIED / EXTERNAL_INPUT / WAIVED / HISTORICAL_SUPERSEDED.
- [ ] Đối chiếu bằng chứng trước merge với source hiện tại. Chỉ yêu cầu kiểm lại phần ảnh hưởng bởi merge/dependency/config mới; không gán image/JAR cũ cho source mới.
- [x] Ghi riêng mỗi lỗi tái hiện: requirement, bước tái hiện, expected/actual, phạm vi ảnh hưởng, mức ưu tiên và điều kiện đóng. Thiếu bằng chứng không được ghi là lỗi code. Xem issue register và progress receipt2026-10-20; không đồng nghĩa census đã đầy đủ.
- [ ] Rà từng dòng để không còn yêu cầu không được phân loại. Mục newly discovered chỉ thêm vào W02–W14 hoặc issue có bằng chứng; không hứa tổng số lỗi bằng0.

**Đạt:** Mọi yêu cầu trong phạm vi có disposition và evidence/việc còn lại; không còn thông báo “hoàn tất” không ghi phạm vi. Nếu inventory chưa đầy đủ, phải báo rõ chưa đủ cơ sở kết luận toàn bộ dự án.

### W02 — Kích hoạt đúng chính sách thu phí và bảo vệ cửa hàng cũ — P0

**Đọc, không mặc định sửa:**
`apps/api/src/main/java/vn/covie/settings/application/SystemSettingRegistry.java`, `SystemSettingService.java`, `SystemSettingResolver.java`;
`apps/api/src/main/java/vn/covie/settings/api/AdminSystemSettingController.java`;
`apps/api/src/main/java/vn/covie/billing/application/BillingEntitlementService.java`, `BillingLegacyMigrationService.java`;
`apps/web/features/merchant/merchant-form.tsx`.

- [ ] Chụp baseline setting/policy/version và thống kê cohort cửa hàng sẽ mất quyền khi bật GLOBAL enforcement, bằng quyền Admin hợp lệ. Không nhập PII vào báo cáo công khai.
- [ ] Dùng preview/legacy-shadow hiện có để tách cửa hàng cũ, đã trả tiền, dùng thử, chưa có quyền; xác nhận đúng phạm vi rollout/cutoff vận hành, không tự cấp miễn phí hoặc thu phí toàn bộ merchant.
- [x] Chọn triển khai local kiểm soát hoặc cohort được owner xác nhận. Broad coding approvals vẫn hiệu lực; cutoff ảnh hưởng người dùng cũ và dữ liệu thực tế không được tự suy ra từ các approvals đó. Owner chọn toàn bộ12 cửa hàng local, sau đó xác nhận bật cả bốn cờ Billing.
  Enforcement hiện chỉ đọc GLOBAL, **không có công tắc riêng từng merchant**. Một cohort kiểm soát phải dùng runtime/dataset cô lập hoặc phương án bảo vệ merchant ngoài cohort được duyệt bằng cơ chế quyền hiện có; không giả định bật GLOBAL chỉ tác động shop đang thử, không tự tạo grants miễn phí hay viết thêm cơ chế cohort.
- [x] Sau backup và review tác động, dùng Admin settings UI/API hợp lệ bật `billing.enforcement_enabled`, đúng optimistic version/If-Match nếu có; giữ audit/outbox/cache invalidation. Không UPDATE SQL để bỏ qua quyền/lịch sử. Không bật registration/reminder/finance flags nếu không cần. Đã PATCH201 tạo GLOBAL override mới không If-Match; rereadtrue, history/audit và một outboxPUBLISHED; không tạo invoice/grant.
- [ ] Đọc lại effective setting/policy qua API và kiểm người dùng/cửa hàng mới, chưa trả tiền không được publish/activate/sell, nhưng vẫn tạo và xem hồ sơ/draft, quản lý thanh toán, đọc lịch sử theo nghiệp vụ.
- [ ] Ghi cơ chế tắt enforcement/checkout có kiểm soát khi lỗi; không xóa invoice/grant hoặc restore DB cũ để “undo” tiền đã nhận.

**Đạt:** Không còn nhầm payment-gatewayON với enforcementON. New Seller theo PAY_BEFORE_SELL được chuyển đúng bước thanh toán; cohort cũ không bị cắt quyền ngoài phạm vi đã thống nhất. Buyer vẫn miễn phí, đơn cũ và Social vẫn được bảo toàn.

### W03 — Tạo hóa đơn và QR thật qua đúng UI — P0

**Đọc/kiểm:**
`apps/web/app/(seller)/seller/[merchantId]/billing/page.tsx`, `apps/web/features/seller/seller-billing-panel.tsx`, `seller-billing-onboarding.tsx`;
`apps/api/src/main/java/vn/covie/billing/application/BillingCheckoutService.java`;
`apps/api/src/main/java/vn/covie/billing/payos/PayosGateway.java`.

- [ ] Owner chọn đúng merchant, plan/revision, số tiền và giới hạn ngân sách cho một lần kiểm chứng; kiểm điều kiện tài khoản/kênh payOS. Không tự đổi giá chung hoặc đoán số tiền tối thiểu.
- [ ] Dùng tài khoản Seller thật thuộc merchant đã chọn. Sau tạo hồ sơ xác minh route `/seller/{merchantId}/billing`, không hướng dẫn `/seller/billing`.
- [ ] Thực hiện quote→review giá→create checkout qua UI, lấy invoice/orderCode/link từ server. Chỉ tạo một khoản yêu cầu có kiểm soát, không click/retry mù quáng nếu trạng thái chưa rõ.
- [ ] Kiểm response payOS thật được xác minh chữ ký, amount/currency/merchant/invoice khớp và QR/link hiển thị đúng; không nhận return URL làm bằng chứng PAID.
- [ ] Reload/quay lại hóa đơn còn hiệu lực theo cơ chế replay đã có; chứng minh thao tác thực tế vừa làm không sinh thêm invoice/link. Nếu có lỗi cụ thể, giữ nguyên key/body và đối soát trước khi tạo mới.

**Đạt:** Seller thấy QR/link thật và số tiền đúng trong UI; trước trả tiền vẫn chưa có PAID/entitlement. Đây mới là VERIFIED_LIVE cho tạo QR, chưa phải hoàn tất settlement.

### W04 — Giao dịch ngân hàng thật và mở bán đúng một lần — P0

**Đọc/kiểm:**
`apps/api/src/main/java/vn/covie/billing/api/PayosWebhookController.java`;
`apps/api/src/main/java/vn/covie/billing/application/BillingWebhookService.java`;
`apps/web/app/(seller)/seller/billing/return/page.tsx`.

- [ ] Owner xem lại số tiền và tự quét/chuyển khoản cho invoice W03. Agent không thực hiện chuyển tiền. Nếu chưa có hành động này, ghi WAITING_FOR_HUMAN_PAYMENT, không đóng VERIFIED_LIVE.
- [ ] Theo dõi callback thật có chữ ký trên runtime hiện tại; so amount/currency/orderCode/paymentLinkId/reference và merchant với invoice đã chọn. Lưu evidence tối thiểu riêng tư, không đưa raw bank payload lên Git.
- [ ] Đọc invoice/attempt/transaction/grant/audit/outbox qua API được phép: đúng một PAID invoice và một entitlement tương ứng, không nhân đôi kỳ hạn.
- [ ] Kiểm FE từ pending sang paid sau dữ liệu server xác nhận; Seller được mở bán đúng merchant, không ảnh hưởng merchant khác. Redirect/reload không tự tạo quyền.
- [ ] Ghi ID tham chiếu tối thiểu, thời điểm và kết quả. Nếu callback thất lạc, chuyển W05, không sửa DB thành PAID bằng tay.

**Đạt:** Có giao dịch ngân hàng thật được xác minh và quyền đúng một lần. Callback mẫu confirm-webhookHTTP200 không được dùng thay bước này.

### W05 — Đối soát và xử lý sự cố tiền thật — P0

**Dùng sẵn:** `apps/api/src/main/java/vn/covie/billing/api/AdminBillingReconciliationController.java`, `apps/api/src/main/java/vn/covie/billing/reconciliation/BillingReconciliationService.java`, `docs/runbooks/seller-billing-finance-cases.md`.

- [ ] Kiểm một lượt đọc trạng thái/đối soát có quyền cho chính invoice pilot, với lý do/audit/idempotency theo contract. Không bật scheduler hàng loạt chỉ để thử.
- [ ] Kế thừa evidence các test hiện có về replay, duplicate callback, lost callback, cancel/expiry, quote cũ, concurrent settlement và legacy409; chỉ chạy lại khi source/config liên quan thay đổi hoặc có lỗi tái hiện.
- [ ] Với giao dịch thật, đối chiếu trạng thái cuối và chứng minh lượt đọc/reconcile vừa làm không cấp thêm grant; không phát lại raw callback thật nếu có khả năng side effect chưa được giới hạn.
- [ ] Ghi playbook provider timeout/unknown state, chữ ký sai, thanh toán chưa đủ/nhiều giao dịch, refund/dispute và người chịu trách nhiệm. Không tạo thêm charge/refund/bank transfer để hoàn thành checklist; không diễn giải CANCELLED trên trình duyệt thành provider-final VOID.

**Đạt:** Operator có đường xử lý invoice cụ thể an toàn; pending có thể truy vết, không tạo khoản thu mới hoặc entitlement từ trạng thái không được xác minh. Reconciliation/reminder worker chỉ bật khi có phạm vi, cadence và quan sát vận hành rõ.

### W06 — Đóng rủi ro vận hành tunnel tạm và cấu hình thanh toán — P0

**Local/private:** `.local/payos-live/bridge.mjs`, `activation.json`, `README.md`, root `.env.local`; tham chiếu `scripts/dev.ps1`, `scripts/stop-dev.ps1`.

- [ ] Kiểm lại tunnel còn sống, chỉ webhook/return được expose; secret chỉ ở API, không có trong process Web/bundle/log công khai.
- [x] Ghi rõ return hiện redirect về localhost của máy đang dùng: chỉ phù hợp kiểm chứng desktop local, không phải thanh toán đa thiết bị hoặc production. Receipt riêng tư đã ghi giới hạn này; phiên ON hiện đã bị supersede.
- [ ] Trước khi đóng tunnel/đổi URL, dừng tạo checkout mới; đăng ký và confirm lại URL khi mở phiên khác. Có pending payment thì giữ đường xử lý callback/reconcile, không hủy hoặc xóa bản ghi tùy tiện.
- [x] Chọn kết thúc phiên local bằng tắt checkout có kiểm soát, hoặc chuyển sang HTTPS/domain ổn định theo W11. Không để env bật tạo thanh toán vô thời hạn trong khi callback URL đã chết. Đã tắt payment/release và dừng stack, giữ volumes; pre-stop không có invoice.
- [x] Cập nhật activation receipt đúng thực tế và tài liệu setup; chỉ số payment creation, enforcement và reconciliation phải được trình bày riêng. Receipt có mục supersession/OFF, không xóa xác nhận provider lịch sử.

**Đạt:** Biết ai giữ tunnel/app sống, cách dừng an toàn, cách thay URL và xử lý pending. Không gọi Quick Tunnel là triển khai production hoàn chỉnh.

## Chunk 2: Nghiệm thu phần mềm còn chưa đủ bằng chứng

### W07 — FE/BE Billing trên cấu hình thực tế và sửa lỗi được chứng minh — P1

**Mục tiêu kiểm chứng:** `apps/web/features/merchant/merchant-form.tsx`, `apps/web/features/seller/seller-billing-panel.tsx`, `apps/web/features/admin/admin-billing-screen.tsx`; các guard Catalog/Need/Ads/Checkout đang có.

- [ ] Trên cohort local đã chọn, thực hiện luồng owner hiện tại vừa báo: tạo hồ sơ→chính sách→billing→pending→paid→mở bán, cùng trường hợp quay lại shop vừa tạo. Không tạo cửa hàng thật hàng loạt để lấp test matrix.
- [ ] Chỉ kiểm lại boundary chịu tác động của enforcement: OWNER/STAFF/MANAGER, chưa trả tiền/đã có quyền, Buyer/đơn cũ/Social. Giữ nguyên evidence integration hiện có cho các đường không đổi; dùng fixture cô lập cho các trạng thái không thể hoặc không nên gây ra với tiền thật.
- [ ] Kiểm trạng thái chưa tải/error/401/403/409, redirect URL và CTA của luồng mới bật trên desktop/mobile layout hiện có. Chụp bằng chứng và đọc lỗi thực tế; HTTP200 của route động không chứng minh đúng màn hình.
- [ ] Với audit-filter overlap1280 trong báo cáo cũ: tái hiện đúng màn hình/source hiện tại trước. Nếu còn lỗi, sửa tối thiểu tại vùng đó; nếu không còn, đóng HISTORICAL_NOT_REPRODUCED, không refactor UI khác.
- [ ] Mỗi lỗi có RED có cơ sở→minimal fix→focused GREEN/typecheck/lint tương ứng. Không hạ assertion, timeout, quota hoặc CSRF/auth để “đạt”.

**Đạt:** Không có issue P0/P1 còn tái hiện trong luồng billing đã duyệt; các issue khác được phân loại và không bị giấu trong chữ hoàn thành.

### W08 — Đóng các khoảng trống còn lại của Plan01–09 và Social — P1

**Nguồn:** W01; `scripts/verify-ui-flow.ps1`; `docs/CoVie-ui-handoff-matrix.md`; `docs/plans/social-product-card/v2-local-acceptance-matrix-2026-10-17.md`.

- [ ] Dùng ma trận hiện tại kiểm những dòng thực sự còn NOT_VERIFIED/CONFIRMED_GAP: identity/profile/address, merchant/catalog/media, cart/shipping/inventory, fulfillment/receipt, Need, review/reputation/notification/SSE, moderation/settings/Admin và Social/activity/video.
- [ ] Với mỗi dòng: xác định đã đóng bằng evidence mới hơn hay còn thiếu thật. Ví dụ Admin audit và activity routes đã tồn tại; không triển khai lại theo tài liệu cũ ghi missing.
- [ ] Chạy đúng journey/role/API bị thiếu evidence trong fixture cô lập. Giữ owner-waived Figma; không gọi201 capture là201 manual reviews, không tự thêm màn hình không có requirement.
- [ ] Nếu đường code không đổi và evidence còn đủ, đánh PASS_RETAINED có nguồn; nếu chỉ thiếu deployed HTTPS/OAuth/storage thì chuyển W11, không gọi là cần viết lại BE.
- [ ] Mọi tính năng Social/V2/video vẫn là flag độc lập. Không bật chúng do Billing đã bật, hoặc do local G0–G6 từng hoàn thành. Chỉ lập danh sách activation gates riêng nếu owner muốn release chúng.

**Đạt:** Toàn bộ yêu cầu01–09/Social trong phạm vi có disposition, không có yêu cầu mất dấu hoặc bị thay bằng một full suite chung. Không có số lượng lỗi được “đoán bằng số checkbox cũ”.

### W09 — Security/dependency triage hiện trạng — P1

**Nguồn/tool hiện có:** `infra/scripts/security-scan.sh`, `.gitleaks.toml`, `.gitleaksignore`, `docs/operations/codeql-handoff-triage-2026-10-19.md`, lockfile hiện tại.

- [ ] Kiểm advisory dev-only Braces hiện còn không, dependency chain và phạm vi khai thác; phân biệt dev/build với runtime. Đóng bằng cập nhật tương thích nhỏ có regression hoặc chấp nhận rủi ro được ghi rõ; không tự nâng TypeScript7 đã chứng minh không tương thích.
- [ ] Kế thừa CodeQL false-positive disposition cho flow/source không đổi. Flow thay đổi phải review lại. Không disable rule, thêm ignore rộng hay gọi AnalyzeSUCCESS là scanner0findings.
- [x] Kiểm đúng delta chuẩn bị publish và release artifacts; không scan/copy env/dump vào context. Raw finding và giải thích phải giữ đúng nghĩa; source scan không thay image scan. Local candidate đã scan; Web gate vẫn FAIL, không phải release approval.
- [ ] Nếu phát hiện lỗi có thể khai thác thật: mở issue có đường dữ liệu/ảnh hưởng, fix tối thiểu và kiểm chứng; không “làm sạch” working code chỉ để thay scanner output.

**Đạt:** Không còn HIGH/CRITICAL runtime chưa xử lý theo gate hiện có; finding ngoài runtime có disposition và scope. Không cam kết phần mềm tuyệt đối không có lỗ hổng.

### W10 — Release artifacts đúng source hiện tại — P1, trước production

**Dùng sẵn:** `.github/workflows/release.yml`, `apps/api/Dockerfile`, `apps/web/Dockerfile`, `infra/scripts/verify-production-artifacts.sh`, `security-scan.sh`, `generate-sbom.sh`, `create-release-manifest.sh`.

- [ ] Chọn exact source SHA đã review. Dev dirty generated files và cấu hình local không được lén trở thành source release; preserve chúng trước khi tạo context sạch.
- [ ] Build API/Web pair từ whitelist sạch; xác minh metadata/source manifest, normal flags, nonroot, FFmpeg và runtime assets. JAR/images trước thay đổi probe runner không được gọi là artifacts hiện tại.
- [x] Chạy validator hiện có, scan image/runtime libraries và tạo SBOM/manifest gắn exact digest; giữ logs và giới hạn scanner/database. Không tự đặt digest hoặc claim registry push chưa thực hiện. Local image IDs/source manifest/SBOM ghi trong progress receipt; Web scan FAIL, chưa có registry digest.
- [ ] Boot pair trong môi trường cô lập, health/contract/static assets và một smoke chịu ảnh hưởng của packaging. Không chạy lại toàn BE vì tài liệu đổi; code/dependency/runtime đổi thì chọn gate tương ứng và một full integration cuối khi nguồn ổn định nếu cần.
- [ ] Publish/registry chỉ khi bước execution được phép; không auto deploy từ việc build thành công.

**Đạt:** Có cặp immutable artifacts, manifest/SBOM/scan/runtime proof cùng source; không dùng lỗi provenance cũ để phát biểu release-ready.

## Chunk 3: Vận hành production và bàn giao thật

Các mục W11–W13 là việc production còn chưa làm/chưa đủ bằng chứng, **không chặn W01–W10 local**. Nếu owner chưa cung cấp target, ghi EXTERNAL_INPUT theo từng mục; không quay lại vòng audit local vô hạn. Không tự tạo staging chỉ để thay quyết định bỏ staging của owner; nếu dùng workflow release hiện có, phải thực hiện điều kiện promotion của nó hoặc review thay đổi workflow minh bạch, không bypass.

### W11 — Cấu hình và triển khai môi trường đích — P2

**Đọc:** `infra/compose/compose.prod.yml`, `infra/deploy/deploy.sh`, `.github/workflows/release.yml`, `docs/operations/production-topology.md`, `docs/operations/staging-rehearsal.md`.

- [ ] Nhận target/SSH/domain/TLS, registry và đường dẫn secret server-only; không nhận key trong chat. Ghi ai chịu trách nhiệm thông tin kênh thanh toán, terms/privacy/invoice/thuế theo yêu cầu kế hoạch, không tự kết luận pháp lý từ code.
- [ ] Cấu hình DB/Redis/private bucket/CORS/limits/proxy/cookies/Google redirect/backup/alert destination và khóa truy cập vận hành; không copy cấu hình development hoặc database thật sang nơi công khai.
- [ ] Preflight và backup đúng lineage trước deploy. Dùng digest W10, payment/enforcement ở trạng thái rollout đã review; không bật toàn bộ Social/video/financial flags theo một lệnh.
- [ ] Kiểm HTTPS app/normal login/refresh/CSRF/roles/media/webhook/return trên URL đích, không dựa vào localhost hoặc auth bypass. Hosted return phải làm việc trên thiết bị người dùng, không redirect localhost như phiên local.
- [ ] Đăng ký/confirm webhook riêng cho kênh/môi trường đích với tác động đã biết; không vô tình ghi đè kênh production khác. Hoàn tất W12/W13 trước tuyên bố operational release-ready.

**Đạt:** Runtime đích và public routing có bằng chứng exact-digest; phần chưa cung cấp hạ tầng được ghi đúng EXTERNAL_INPUT, không phải lỗi FE/BE.

### W12 — Backup, restore và rollback đúng môi trường — P2

**Dùng sẵn:** `infra/backup/postgres-backup.sh`, `postgres-restore.sh`, `verify-dump.sh`, `infra/scripts/verify-restored-invariants.sh`, `infra/deploy/rollback.sh`, `docs/operations/migration-lineages.md`.

- [ ] Chọn đúng DB đang cấu hình, không chỉ default database của container. Lưu backup riêng tư/checksum và kiểm đọc được; `pg_restore -l` chỉ kiểm danh mục, không thay diễn tập restore.
- [ ] Restore vào DB riêng, so invoices/ledger/grants/audit/catalog/media metadata và quyền; không đè current recovered/live DB.
- [ ] Xác minh object/provider backup/off-host retention và khả năng khôi phục thật. DB backup không tự sao lưu S3 objects hay giao dịch đã gửi cho ngân hàng.
- [ ] Diễn tập rollback ứng dụng bằng cặp digest tương thích và evidence guard hiện có. Giữ floor đọc CATALOG_VIDEO, không rollback về binary chỉ healthy nhưng không đọc được enum; không sửa lịch sử SQL hai lineage.
- [ ] Đo RPO/RTO theo mục tiêu đã có trong Plan10 bằng thực nghiệm trên target; khi có tiền thật, rollback code không được biến thành rollback ledger về thời điểm trước chuyển khoản.

**Đạt:** Restore và rollback có dữ liệu/target/digest/thời điểm thực, không chỉ script-stubPASS hay marker; không mất giao dịch ngân hàng/người dùng.

### W13 — Monitoring, tải thực tế và người vận hành — P2

**Dùng sẵn:** `docs/runbooks/seller-billing-observability.md`, `docs/operations/social-v2-observability.md`, `infra/observability/`, `infra/perf/`, `infra/scripts/run-staging-rehearsal.sh`.

- [ ] Kiểm scrape/recording rules/dashboards trên runtime đích, alert gửi đến nơi thật và người nhận xác nhận; synthetic/promtoolPASS không thay delivery thực.
- [ ] Theo dõi invoice pending, webhook rejection, reconciliation discrepancy, outbox/notification jobs, backup age, DB/Redis/storage pressure bằng metric hiện có; không log credentials/bank payload.
- [ ] Chạy workload đã định nghĩa theo pilot objective, an toàn với dữ liệu riêng; không load-test payOS production hoặc tạo giao dịch tiền hàng loạt. Ghi dataset/concurrency/duration/p95/error/CPU/memory/query counts và giới hạn.
- [ ] Tách CLS/LCP lab khỏi field75th, READY declared bytes khỏi physical bucket usage/chi phí; kiểm thiết bị/mạng thực nếu thuộc release criterion, không tự yêu cầu lại Figma.
- [ ] Ghi lịch vận hành jobs/reconciliation/reminders, on-call/support, quy trình incident/tắt checkout và reconcile pending. Chỉ bật từng job sau quan sát cohort phù hợp, không dùng quyền owner thay evidence.

**Đạt:** Có bằng chứng phục hồi/cảnh báo/load theo mục tiêu thật và người chịu trách nhiệm; không gọi cấu hình dashboard là hệ thống đã được giám sát đầy đủ.

### W14 — Chốt nghiệm thu, tài liệu và đồng bộ cuối — P1/P2 theo phạm vi

**Cập nhật:** ma trận W01; báo cáo kết thúc mới; README/runbooks/activation receipts liên quan. Giữ báo cáo cũ làm lịch sử và thêm supersession rõ ràng, không sửa kết quả thất bại cũ thành PASS.

- [ ] Đóng hoặc phân loại từng issue; ghi code hoàn thành, local acceptance, live-payment acceptance, production release thành bốn kết luận riêng. Nếu mục còn EXTERNAL_INPUT/WAITING_FOR_HUMAN_PAYMENT thì không gọi toàn bộ production hoàn tất.
- [x] Khi có fix, chạy focused checks tương ứng. Source ổn định mới chạy integration gate cần thiết; CI exact-head là bằng chứng riêng. Không rerun full BE sau mỗi bước hoặc cộng các lượt focused thành fullsuite xanh. Các lượt scoped được ghi riêng, chưa có CI cho dirty delta.
- [x] Sửa các hướng dẫn route/state sai; báo rõ `PAYOS_PAYMENT_ENABLED`, release gate, enforcement và reconciliation là các trạng thái độc lập. Gắn thời điểm/source/runtime, không để chữ “mọi flags OFF” lịch sử phủ nhận phiên được owner bật thật. Timeline ON lịch sử/OFF hiện tại và route merchant-specific đã phân biệt.
- [ ] Đưa chỉ file cần thiết lên Git qua quy trình được phép; bảo toàn dirty files/private backups/stashes. Review diff, secret gate, merge có checks, đồng bộ local/remote và dọn chỉ nhánh tạm được review. Lượt lập kế hoạch không tự thực hiện những thao tác Git này.
- [ ] Bàn giao operator: domain, trạng thái flags/cohort, giám sát, backup, pending invoices, cách tắt an toàn và issue chấp nhận còn lại. Nhận kết quả nghiệm thu đúng phạm vi thay vì chỉ báo “CI xanh”.

**Đạt local/live:** W01–W09 và phần local W14 có disposition đầy đủ; W03/W04 chỉ đóng VERIFIED_LIVE khi thực sự có invoice/QR/giao dịch/grant đã xác minh.

**Đạt production:** W10–W13 và release W14 có bằng chứng target/digest/vận hành thật. Không dùng hoàn thành local để chứng minh điều này.

## 3. Thứ tự, phụ thuộc và quyền quyết định

1. **Ưu tiên ngay:** W01 baseline → W02 rollout/cửa hàng cũ → W03 QR → W04 owner trả tiền → W05 đối soát → W06 đóng phiên tạm an toàn.
2. **Nghiệm thu phần mềm:** W07/W08 dựa trên gap thực của W01; W09 chạy cho delta cần phát hành. Không bỏ luồng Buyer/Social chỉ vì vấn đề hiện tại là Billing.
3. **Release:** W10 → preflight W11 → W12/W13 trên target kiểm soát → production signoff W14. Các việc hạ tầng chưa có không cản sửa lỗi local đã được chứng minh.
4. **Cần người thật:** lựa chọn merchant/plan/amount và hành động chuyển tiền; xác định phạm vi/cutoff ảnh hưởng cửa hàng cũ; cung cấp hạ tầng và trách nhiệm vận hành/pháp lý còn thiếu. Không hỏi lại các ADR/approval riêng đã được duyệt, nhưng không bịa các dữ kiện này.
5. **Được giữ nguyên:** toàn bộ đường code đã đúng/PASS, original BIL30/30 local, Social G0–G6 local, API contracts/migration histories, dữ liệu cũ, chính sách Buyer miễn phí, không auto-debit.

## 4. Lệnh kiểm tra có mục tiêu khi thực thi

Chạy từ root `E:/WebApp/CoVie`, Java25/Maven cache phù hợp. Không chạy tất cả lệnh chỉ vì chúng được liệt kê; chọn theo đường thực sự thay đổi. Kiểm trước không có Maven khác dùng chung target.

```powershell
git status --short
git diff --check
pnpm --filter @covie/web exec vitest run tests/seller/merchant-management.test.tsx features/seller/seller-billing-onboarding.test.tsx features/seller/seller-billing-panel.test.tsx --maxWorkers=2
Push-Location apps/api
try {
  ./mvnw.cmd '-Dtest=BillingEntitlementFlagTest,BillingAuthorizationHttpIntegrationTest' test
} finally { Pop-Location }
Push-Location apps/api
try {
  ./mvnw.cmd '-Dtest=BillingCheckoutReplayIntegrationTest,BillingSettlementIntegrationTest,PayosGatewayHttpIntegrationTest' test
} finally { Pop-Location }
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web lint
```

Nếu không đổi implementation, không cần chạy lại các fixture trên chỉ để bật Admin setting; đọc effective setting và kiểm runtime thật/cohort là evidence bắt buộc, test cũ không thay được.

Khi đổi contract mới dùng export OpenAPI/generate/check và review semantic diff. Khi cần broad gate cuối, dùng command hiện có và ghi rõ skipped opt-ins; không chạy fullsuite với dữ liệu thật hoặc bật jobs/payment trong test ngoài ý muốn.

Mỗi checkbox đóng phải có: thời điểm, source SHA + dirty delta, runtime/config/cohort, thao tác/lệnh, expected/actual, evidence riêng tư đã sanitize và giới hạn. Đổi môi trường/source thì đánh giá lại phạm vi evidence, không tự xóa PASS trên đường không đổi.

## 5. Điểm dừng đúng của lượt này

Đây chỉ là **kế hoạch**; chưa bật enforcement, chưa tạo invoice/QR mới, chưa chuyển tiền, chưa deploy/push/commit. Các flags hiện tại chỉ được đọc lại. Giữ nguyên dịch vụ và dữ liệu đang dùng; không coi việc lập kế hoạch xong là dự án đã xong.

Rà soát kế hoạch độc lập đã hoàn tất: chấp nhận phạm vi và phân loại evidence; đã sửa lệnh Maven sang chạy trong `apps/api` và làm rõ giới hạn GLOBAL của enforcement. Không có kiểm thử ứng dụng hay thao tác vận hành nào được thực hiện để review tài liệu này.
