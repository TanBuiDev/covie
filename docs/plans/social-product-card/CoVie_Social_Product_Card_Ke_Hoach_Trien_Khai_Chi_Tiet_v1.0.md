# COVIE — KẾ HOẠCH TRIỂN KHAI CHI TIẾT SOCIAL PRODUCT CARD 2.0

**Tài liệu kỹ thuật dùng để thẩm định, chia việc và giao cho AI coding agent**  
**Phiên bản:** 1.0 — 15/10/2026 (ICT)  
**Repository:** `TanBuiDev/covie` — <https://github.com/TanBuiDev/covie>  
**Baseline đã đối chiếu:** nhánh `main`, commit `803b25b89f4686b4135b0de5ef101aabcc84b614` (merge PR #28).  
**Trạng thái:** **BẢN KẾ HOẠCH — CHƯA TRIỂN KHAI, CHƯA ĐƯỢC PHÉP TỰ ĐỘNG MERGE/DEPLOY**.  
**Đối tượng:** chủ dự án, lập trình viên frontend/backend, DBA, QA, DevOps và AI coding agent.

> **Quy tắc đọc:** `ĐÃ XÁC MINH` = tồn tại trong mã nguồn của baseline; `ĐỀ XUẤT` = chưa được triển khai theo bằng chứng được kiểm tra; `PHẢI KIỂM TRA LẠI` = trạng thái có thể thay đổi trước lúc thực thi. Các đường dẫn, hợp đồng API, migration, hạn mức chưa tồn tại không được xem như sự thật của repository.

---

## 0. TÓM TẮT ĐIỀU HÀNH VÀ QUYẾT ĐỊNH KIẾN TRÚC

### 0.1. Mục tiêu cuối cùng

Biến thẻ sản phẩm/dịch vụ trong chế độ Social thành **một bài đăng thương mại thu nhỏ**: nội dung giới thiệu hấp dẫn, gallery ảnh/video, tương tác Like–Comment–Save–Share, dữ liệu giá/địa điểm có cấu trúc và lời kêu gọi đặt hàng. Đồng thời cung cấp:

1. **Người mua:** trang "Hoạt động của tôi" (đã thích, đã lưu, bình luận của tôi) cùng khả năng quay lại nội dung hợp lệ.
2. **Seller:** trình biên tập nội dung giới thiệu, quản lý ảnh/video, xem trước Social Card, quản trị bình luận và xem chỉ số phù hợp quyền.
3. **Admin:** danh sách nội dung đăng bán thực, tìm kiếm/lọc, xem chi tiết, xử lý qua moderation case, ghi audit và kiểm soát nội dung đa phương tiện.
4. **Hạ tầng:** dữ liệu nhất quán, kiểm duyệt an toàn, không làm hỏng Catalog/Search/Ads/Cart/Checkout; có số đo, khả năng rollback và bằng chứng nghiệm thu.

### 0.2. Quyết định sản phẩm cố định cho đợt 2.0

- **Một Catalog Item có tối đa một bài giới thiệu Social đang có hiệu lực**. Không tạo bảng `posts` dùng chung, không mở "đăng trạng thái tự do" cho mọi người trong phạm vi tài liệu này.
- **Catalog Item vẫn là nguồn sự thật của dữ liệu bán hàng:** giá, biến thể, tồn kho, phân loại, vị trí bán, chính sách giao hàng, trạng thái bán hàng và ID.
- **Nội dung Social là phần mở rộng có kiểm soát** của Catalog; không nhân bản dữ liệu thương mại trong caption.
- **Bình luận Social khác với đánh giá mua hàng đã xác thực**. Không gộp số bình luận vào điểm đánh giá, không tạo đơn hàng từ tương tác.
- **Duy trì hai chế độ `social` và `catalog`**; Social có nội dung giàu thông tin, Catalog tối ưu quét nhanh và mua sắm. Không ép toàn bộ thẻ tăng kích thước.
- **Không auto-play video kèm âm thanh, không upload video từ URL tự do, không làm live stream** ở phạm vi này.
- **Ưu tiên an toàn:** hoàn tất gate phát hành Social cũ và năng lực kiểm duyệt Admin trước khi cho phép nội dung phong phú xuất hiện rộng rãi.

### 0.3. Ngoài phạm vi — không tự mở rộng

Không triển khai bài đăng độc lập, hồ sơ cá nhân kiểu Facebook, theo dõi kết bạn, Story/Reels, Livestream, nhắn tin, Recommendation AI, quảng cáo tự động mới, chỉnh sửa bình luận, reaction nhiều cảm xúc, hashtag discovery chuyên biệt, hệ thống thanh toán mới, hoặc thay đổi mô hình vận hành đơn hàng. Mọi yêu cầu này phải lập ADR/PRD khác, được chủ dự án phê duyệt.

### 0.4. Thứ tự thực thi

`G0 Baseline & an toàn` → `G1 Admin listing moderation` → `G2 Seller caption + ảnh` → `G3 Social Card caption + gallery` → `G4 Lịch sử hoạt động` → `G5 Video (feature flag riêng)` → `G6 Kiểm thử hệ thống / staging / phê duyệt production`.

Các giai đoạn có thể chuẩn bị song song về thiết kế, **nhưng không được mở feature trước khi dependency và gate phù hợp đạt PASS có bằng chứng**.

---

## 1. HIỆN TRẠNG ĐÃ KIỂM TRA TRÊN GITHUB

### 1.1. Thông tin repository

- Baseline: `main@803b25b89f4686b4135b0de5ef101aabcc84b614`.
- PR: <https://github.com/TanBuiDev/covie/pull/28>.
- Commit merge ghi nhận CI PR đã đạt nhưng **staging E2E, performance benchmark, rollback rehearsal và production approval là các gate riêng vẫn chưa có bằng chứng hoàn thành trong review**. Không tự suy diễn hệ thống đã phát hành production.
- `README.md`: JDK 25, Node.js 24 LTS, pnpm 10, Docker Compose; API/Web/PostgreSQL/Redis/MinIO. Cần kiểm tra lại phiên bản và scripts tại lúc thực thi.
- `AGENTS.md`, `RULES.md`, `.agents/rules/ui-rules.md`: bắt buộc đọc và tuân thủ, nhất là **không dùng emoji Unicode làm icon UI; sử dụng `@phosphor-icons/react`; nền content/card thuần trắng; tránh lặp control; UI nhất quán**.

### 1.2. Bằng chứng hiện hữu

| Mảng | ĐÃ XÁC MINH | Vấn đề còn thiếu cần giải quyết |
|---|---|---|
| Like/Save | `CatalogSocialController`, `CatalogSocialService`, bảng `catalog_social_reactions` (LIKE/SAVE) | Chưa có API /me tổng hợp và UI lịch sử đã thích/đã lưu |
| Comment | `catalog_social_comments`, comment thread lazy load, CRUD cơ bản và báo cáo vi phạm | Chưa có "Bình luận của tôi" và công cụ hỗ trợ Seller theo dõi tập trung |
| Nội dung bán hàng | `CatalogItemEntity.description`, form Seller create/edit, detail page | `CatalogItemSummaryResponse` không trả description; Social Card không có bài giới thiệu |
| Ảnh | Seller chọn nhiều ảnh; detail gallery nhiều ảnh | Social Card chỉ lấy `primaryMedia`; chưa có Social gallery nhiều ảnh |
| Video | Upload MP4/WebM cho `AD_BANNER`; giới hạn video trong storage hiện có là 50 MiB | `MediaPurpose` chưa có `CATALOG_VIDEO`; Catalog chỉ gắn `CATALOG_IMAGE` |
| Seller | Catalog Create/Edit/Management tồn tại | Chưa có workflow Social Caption/Media Preview/Moderation Feedback riêng |
| Admin | ModerationCase, report, HIDE/RESTORE, audit/outbox, comment preview theo quyền | `/admin/listings` tự ghi rõ chưa có admin listing-read contract độc lập |
| Hiệu năng | Batch Social Summary tối đa 40 IDs; lazy comment thread | Chưa có bằng chứng đầy đủ production-like staging/đa phương tiện |

### 1.3. File và đường dẫn tham chiếu bắt buộc

**Backend:**

- `apps/api/src/main/java/vn/covie/social/api/CatalogSocialController.java`
- `apps/api/src/main/java/vn/covie/social/application/CatalogSocialService.java`
- `apps/api/src/main/java/vn/covie/social/api/AdminSocialCommentController.java`
- `apps/api/src/main/java/vn/covie/catalog/api/dto/discovery/CatalogItemSummaryResponse.java`
- `apps/api/src/main/java/vn/covie/catalog/application/CatalogDiscoveryService.java`
- `apps/api/src/main/java/vn/covie/catalog/application/CatalogCreateService.java`
- `apps/api/src/main/java/vn/covie/catalog/application/CatalogEditService.java`
- `apps/api/src/main/java/vn/covie/catalog/model/CatalogItemEntity.java`
- `apps/api/src/main/java/vn/covie/media/application/MediaUploadService.java`
- `apps/api/src/main/java/vn/covie/media/application/MediaAttachmentPolicy.java`
- `apps/api/src/main/java/vn/covie/media/model/MediaPurpose.java`
- `apps/api/src/main/java/vn/covie/moderation/api/AdminModerationController.java`
- `apps/api/src/main/java/vn/covie/moderation/application/CatalogModerationTargetHandler.java`
- `apps/api/src/main/java/vn/covie/moderation/application/SocialCommentModerationTargetHandler.java`
- `apps/api/src/main/resources/db/migration/V018__catalog_social_interactions.sql`
- `apps/api/src/main/resources/db/migration/V019__social_comment_moderation.sql`

**Frontend:**

- `apps/web/features/marketplace-view/marketplace-product-card.tsx`
- `apps/web/features/marketplace-view/marketplace-view.module.css`
- `apps/web/features/marketplace-view/social-feed.tsx`
- `apps/web/features/marketplace-view/social-comments.tsx`
- `apps/web/features/marketplace-view/social-api.ts`
- `apps/web/features/catalog/catalog-create-form.tsx`
- `apps/web/features/catalog/catalog-edit-form.tsx`
- `apps/web/features/catalog/catalog-detail.tsx`
- `apps/web/features/profile/profile-overview.tsx`
- `apps/web/app/(buyer)/me/page.tsx`
- `apps/web/app/(admin)/admin/listings/page.tsx`
- `apps/web/features/admin/admin-reports-queue.tsx`
- `apps/web/features/admin/admin-moderation-detail.tsx`
- `apps/web/features/admin/moderation-actions.tsx`

**Tài liệu / kiểm thử / vận hành:**

- `docs/plans/social-product-card/phase-1-implementation-review.md`
- `docs/plans/social-product-card/phase-2-implementation-review.md`
- `docs/plans/social-product-card/phase-3-implementation-review.md`
- `docs/plans/social-product-card/phase-4-implementation-review.md`
- `apps/web/e2e/social-phase4.staging.spec.ts`
- `infra/scripts/run-social-phase4-staging.sh`
- `infra/scripts/social-phase4-smoke.sh`
- `infra/perf/scenarios.mjs`
- `infra/deploy/rehearse-release.sh`, `infra/deploy/rollback.sh`

**Lưu ý:** Đường dẫn trên được xác minh ở baseline nêu đầu tài liệu. AI phải kiểm kê lại tại commit mới trước khi chỉnh sửa; không tạo file trùng nếu code đã thay đổi.

### 1.4. API hiện có — KHÔNG ĐƯỢC ĐẶT LẠI TÊN HOẶC PHÁ VỠ

```text
GET    /api/v1/catalog/social-summary?itemIds=<uuid>&itemIds=<uuid>  (1–40)
GET    /api/v1/catalog/items/{itemId}/comments?limit=5&before=<uuid>
PUT    /api/v1/me/catalog/items/{itemId}/likes
DELETE /api/v1/me/catalog/items/{itemId}/likes
PUT    /api/v1/me/catalog/items/{itemId}/saves
DELETE /api/v1/me/catalog/items/{itemId}/saves
POST   /api/v1/me/catalog/items/{itemId}/comments
DELETE /api/v1/me/catalog/items/{itemId}/comments/{commentId}
POST   /api/v1/reports
GET    /api/v1/admin/moderation-cases
GET    /api/v1/admin/moderation-cases/{caseId}
POST   /api/v1/admin/moderation-cases/{caseId}/{approve|reject|hide-content|restore-content|close}
```

**Bảo toàn:** quyền JWT/CSRF, owner check, idempotency, comment 2–1.000 ký tự, rate limit hiện tại 5 comments/phút/account, counts/batch, cursor, filtering trạng thái, kiểu lỗi, pagination, audit/outbox, không optimistic success giả, comment không được xem là verified review.

---

## 2. G0 — KIỂM KÊ BASELINE, BẰNG CHỨNG VÀ CHỐT HỢP ĐỒNG

**Ưu tiên:** P0 | **Điều kiện vào:** có repo và môi trường đọc được | **Kết quả:** báo cáo baseline + kế hoạch PR đã chốt.

### G0.1. Kiểm tra nguồn sự thật

1. Đọc `AGENTS.md`, `RULES.md`, `.agents/rules/ui-rules.md`, README, technical plans và runbooks liên quan; ghi những hạn chế phải tuân thủ.
2. `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git fetch --prune`, đối chiếu `origin/main`. **Không chạy reset --hard, clean -fd hoặc ghi đè local changes.**
3. Chụp commit SHA, môi trường, migration cao nhất, các nhánh/PR liên quan và tính trạng CI/staging nếu có truy cập.
4. Đọc code thật: catalog authoring, item media, discovery query, permissions, report/moderation, auth/CSRF, generated OpenAPI, social components và file test.
5. Lập bảng `Existing / Partially existing / Missing / Unknown` cho từng yêu cầu F01–F05.
6. Rà soát schema hiện có, index, constraints, unique, delete semantics, migration staging/backfill. Không sửa migration đã apply (V018/V019 và các migration cũ).
7. Đánh giá bundle / network request / database plans đang có trước khi thêm câu truy vấn Social.
8. Kiểm tra release gates của PR #28 trên staging; nếu chưa PASS, xem là **blocker phát hành tính năng mới**, không "đánh dấu hoàn thành" bằng CI đơn thuần.

### G0.2. Quyết định cần ghi vào ADR và được duyệt trước triển khai

- ADR-A: Một caption cho một Catalog Item, không phát sinh social post độc lập.
- ADR-B: **`socialCaption` là trường riêng với `description`**; mô tả sản phẩm phục vụ thông tin bán hàng, caption phục vụ nội dung feed.
- ADR-C: Lấy social preview theo **batch endpoint riêng**, chỉ khi ở Social mode; Catalog mode không phải tải media caption nặng.
- ADR-D: Dùng moderation hiện có cho item/comment; nội dung quảng bá/ảnh/video thuộc item, không được bypass khi item đã duyệt.
- ADR-E: Gallery loại mixed media có ordering thống nhất; video rollout độc lập sau ảnh.
- ADR-F: Activity v1 biểu diễn **trạng thái hiện còn** cho Like/Save và danh sách comment do mình tạo; không giả danh đầy đủ event lịch sử Like→Unlike. Nếu thật sự cần audit lịch sử đã hủy, làm scope/migration riêng.

### G0.3. Hạn mức đề xuất cần duyệt (không phải thông số đang tồn tại)

| Thông số thiết kế | Giá trị đề xuất v1 | Ghi chú |
|---|---:|---|
| `socialCaption` | 3.000 UTF-16 code units | Plain text; giữ xuống dòng; không HTML tùy ý |
| Gallery / item | tối đa 10 media | Bao gồm hình/video; enforce server-side |
| Video / item | tối đa 1 video trong v1 | Sau feature flag và vận hành đủ điều kiện |
| Video upload | tối đa 50 MiB | Không tự tăng giới hạn hiện có; verify bằng cấu hình thực tế |
| Video length | mục tiêu 60 giây | Chỉ enforce khi có metadata probing đáng tin cậy |
| Gallery feed | hiển thị tối đa 6 media đầu, liên kết xem đủ | Không sinh N+1 trên feed |
| Caption feed mặc định | 3–5 dòng + "Xem thêm" | Tôn trọng newline, không phá tương tác |
| Activity page | 20 items/lần, giới hạn tối đa 50 | Keyset cursor, deterministic |
| Batch social preview | tối đa 40 itemIds/lần | Đồng bộ giới hạn summary hiện tại |
| Layout Social desktop | 2 cards/hàng từ >=1024px | Đã có CSS cơ bản, kiểm tra lại pixel thực tế |

**Nếu chủ dự án chưa duyệt hạn mức:** AI phải đánh dấu `DECISION_PENDING` và không âm thầm dùng những con số trên để thay đổi hợp đồng production.

### G0.4. Gate nghiệm thu

- Có báo cáo nguồn mã, SHA, migration và route map; liệt kê issue đã biết.
- Có ADR thiết kế chữ ký API/mô hình dữ liệu và quyết định về video.
- Chưa chỉnh sửa production, chưa tạo migration chưa duyệt, không đổi API hiện có.

---

## 3. G1 — ADMIN: QUẢN LÝ TIN ĐĂNG VÀ KIỂM DUYỆT ĐẦY ĐỦ

**Ưu tiên:** P0, **làm trước khi phát hành caption/video công khai**.  
**ĐÃ XÁC MINH:** `/admin/listings` hiện chỉ có thông tin điều phối, không có admin listing-read contract riêng.  
**Mục tiêu:** biến trang này thành màn hình vận hành thực, không phải mock.

### G1.1. Backend read model cho Admin

**ĐỀ XUẤT API mới (chưa tồn tại):**

```http
GET /api/v1/admin/catalog-items?page=0&size=20&q=&type=&listingStatus=&moderationStatus=&merchantId=&sort=UPDATED_DESC
GET /api/v1/admin/catalog-items/{itemId}
```

- Chỉ Admin qua chính sách quyền hiện có `AdminAccessPolicy.requireAdmin()` hoặc service phù hợp, kiểm tra ở server; không chỉ ẩn UI.
- Response danh sách có ID, tên, loại Product/Service/Resale, merchant, loại/danh mục, trạng thái listing/moderation, ảnh đại diện, cờ có caption/video, thời gian tạo/cập nhật và số báo cáo **nếu query/aggregation đã duyệt**.
- Response chi tiết có text caption/description, media theo thứ tự + MIME + status, merchant/location, các trạng thái, danh sách case liên quan được phân quyền; hạn chế trường riêng tư (không rò user PII, storage key, presigned URL ghi).
- Pagination + sort **allowlist**; default page/size và max page/size có validate; tìm kiếm theo name/ID/merchant; không query toàn bộ dataset.
- Read-only endpoint không được làm thay đổi trạng thái; `Cache-Control: private, no-store`.
- Dùng JPA/JDBC query hoặc read model có index phù hợp, tránh count per item hay N+1.
- Dữ liệu đăng bán bị ẩn/xóa vẫn có thể đọc ở Admin theo quyền; public vẫn phải bị chặn như cũ.
- Response/route cuối cùng phải cập nhật OpenAPI và generated typed client.

### G1.2. Actions phải dùng đúng moderation case

**TUYỆT ĐỐI không:** thêm nút "Ẩn" gọi SQL update thẳng `catalog_items.moderation_status`, hoặc đổi lại ownership.

- Từ danh sách mở item và case liên quan. Nếu item đã có case hợp lệ, sử dụng đúng `/api/v1/admin/moderation-cases/{caseId}/...` đã triển khai.
- Nếu chưa có case mà Admin cần can thiệp, thiết kế **một luồng tạo case có audit/idempotency** và phê duyệt riêng trước khi code. Không giả định đã tồn tại API khởi tạo case cho thao tác tùy ý.
- Hành động: approve/reject/hide/restore/close **chỉ hiển thị theo state machine hiện có**, không tự đặt lại transition.
- Mỗi thao tác cần reason khi policy yêu cầu; idempotency key, permission check, optimistic/concurrency conflict handling, audit, outbox, thông báo phù hợp.
- Với `SOCIAL_COMMENT`, dùng target handler đã có; không thêm route ghi trực tiếp ngoài moderation.
- Kiểm tra chính xác phạm vi ẩn: item sau khi ẩn không còn trong discovery/public detail/social count/comment APIs; Seller vẫn xem được dữ liệu quản lý cần thiết và lý do theo quyền.
- Kiểm tra thay đổi caption/media **sau khi phê duyệt**: bắt buộc đánh giá lại moderation/risk. Không cho "phê duyệt một lần, đăng nội dung mới không cần kiểm tra" nếu policy cấu hình kiểm duyệt trước.

### G1.3. Frontend Admin

Thay trang `apps/web/app/(admin)/admin/listings/page.tsx` bằng bố cục thật:

1. Thanh tìm kiếm; filters có ý nghĩa: loại nội dung, trạng thái, merchant, moderation, khoảng ngày nếu backend hỗ trợ.
2. Bảng responsive: thumbnail, item + merchant, trạng thái, cập nhật gần nhất, thao tác xem chi tiết.
3. Empty/loading/error/retry và URL query params ổn định, không load dữ liệu giả.
4. Detail drawer hoặc route chi tiết, có gallery xem nội dung, preview caption, report case history.
5. Action area dùng moderation-case commands được phép; confirmation + reason; hiển thị 409/412 và re-fetch authoritative status.
6. Hàng đợi báo cáo `/admin/reports` và chi tiết `/admin/moderation/[caseId]` **tiếp tục sử dụng**, không nhân bản chức năng.
7. Cảnh báo content/media vi phạm bằng trạng thái thực, không tạo nhãn "AI đã duyệt" giả.
8. Luôn dùng icon Phosphor, nền trắng, contrast và keyboard support.

### G1.4. Kiểm thử

- 401 guest; 403 buyer/seller; Admin hợp lệ 200.
- Pagination/sort/filter/search kết hợp và trường hợp không có kết quả.
- Xử lý tin ACTIVE, PAUSED, ARCHIVED, HIDDEN, REJECTED, merchant suspended, soft-deleted; không suy diễn rằng mọi trạng thái đều có thể chuyển cùng chiều.
- Gửi report → tạo case → Admin HIDE → Audit/Outbox → public không thấy → RESTORE → public thấy theo policy; case cạnh tranh/đã xử lý.
- Check response Admin không rò `storageKey`, phone/email không cần thiết.
- Integration database + web tests + staging browser; không mock API để tuyên bố test end-to-end.

### G1.5. Definition of Done

- Admin tra cứu được một item có thật kể cả không có report, xem đúng quyền và trạng thái.
- Admin thao tác kiểm duyệt dựa trên case hợp lệ, có audit và quan sát được hiệu ứng public.
- Không làm giảm chính sách bảo vệ đối với comment/report/merchant hiện tại.

---

## 4. G2 — SELLER SOCIAL STUDIO: NỘI DUNG GIỚI THIỆU, ẢNH VÀ PREVIEW

**Ưu tiên:** P1. **Phụ thuộc:** G0 ADR + G1 policy.  
**Mục tiêu:** Seller có thể quản lý nội dung marketing mà không sửa cấu trúc Catalog hiện có.

### G2.1. Data model nội dung xã hội

**Giải pháp được khuyến nghị cho v1:** thêm cột nullable `social_caption` vào `catalog_items`, optional `social_caption_updated_at` nếu thật sự cần tracking, không tạo bảng bài đăng riêng. Phải kiểm tra schema/cơ chế version của `CatalogItemEntity` trước khi chốt migration. `description` giữ nguyên hoàn toàn.

**ĐỀ XUẤT migration** (dùng số Flyway **kế tiếp tại lúc chạy**, baseline hiện cao nhất V019; không khẳng định V020 còn trống trong tương lai):

```sql
-- SƠ ĐỒ MINH HỌA, KHÔNG CHẠY NGUYÊN VĂN TRƯỚC KHI KIỂM TRA SCHEMA
ALTER TABLE catalog_items ADD COLUMN social_caption text NULL;
-- Cân nhắc CHECK char_length(social_caption) <= 3000 nếu chính sách đã duyệt.
-- Mọi schema change forward-only; không drop bảng/tương tác cũ.
```

Các việc bắt buộc:

1. Thêm migration additive, nullable, không full-table rewrite/lock dài; đo thời gian trên dữ liệu mẫu staging.
2. Cập nhật entity/DTO create/edit + validation thống nhất; trường mới có thể không có để Seller cũ vẫn hoạt động.
3. Làm rõ semantics PATCH: phân biệt **không cung cấp trường** với **cung cấp chuỗi rỗng để xóa**; nếu Jackson DTO hiện tại không phân biệt, phải thiết kế explicit update semantics và test trước khi triển khai. Không phá PATCH partial cũ.
4. Đồng bộ version/ETag `If-Match` của Catalog Item; 412/409 đúng khi conflict; không lost update.
5. Trim đầu/cuối theo policy đã duyệt, giữ newline nội dung; render plain text (`white-space: pre-wrap`), escape HTML/XSS, không Markdown/HTML tùy ý.
6. Không copy caption vào `description`, không sửa giá/order; không tự sinh caption từ tên sản phẩm.
7. Khi cập nhật caption/media của item ACTIVE, gọi moderation/risk flow phù hợp; thử nghiệm case "đã duyệt rồi sửa thành vi phạm".

### G2.2. Backend create/edit API

Ưu tiên mở rộng request hiện có:

```text
POST  /api/v1/merchants/{merchantId}/catalog-items
PATCH /api/v1/merchants/{merchantId}/catalog-items/{itemId}
```

**Chỉ đề xuất thêm** trường `socialCaption` và contract cho media ordering; không đổi route khi chưa có lý do kỹ thuật. Seller/merchant member chỉ sửa theo `MerchantCapability.MANAGE_CATALOG_STRUCTURE` hoặc capability hiện có phù hợp. Việc giữ role này hay tách role cần ADR quyền để tránh trao quyền ngoài dự kiến.

- Ensure `null`, absent, `""`, Unicode, newline, 3.000/3.001 ký tự và HTML-string được kiểm thử.
- Merchant A không được sửa item của Merchant B (không rò tồn tại tài nguyên).
- Sửa caption không xóa media/variant/location nếu không có patch trường đó.
- Media không thể gắn khi PENDING_UPLOAD, không thuộc owner hoặc sai purpose.
- Generated OpenAPI/typed client phải được cập nhật, zero drift.

### G2.3. UX Seller Social Studio

Trong `catalog-create-form.tsx` và `catalog-edit-form.tsx`:

- Thêm tab/section **"Nội dung hiển thị trên Khám phá"**, giải thích mô tả thương mại khác caption.
- Textarea caption: đếm ký tự, newline, placeholder mang tính hướng dẫn, xóa/hoàn nguyên, trạng thái draft unsaved, cảnh báo rời trang khi thay đổi chưa lưu.
- Chọn ảnh đã có + tải nhiều ảnh, **kéo thả đổi thứ tự và chọn cover** nếu backend ordering hỗ trợ; dùng keyboard alternative cho reorder.
- Preview dạng Social Card thật, dùng chung component/model preview với feed, mobile/desktop.
- Hiển thị điều kiện upload, lỗi MIME/kích thước/quota, retry; không mất form khi upload lỗi.
- Hiển thị moderation status có thật (`PENDING`, `APPROVED`, `REJECTED`, ...) theo model hiện có, hiển thị lý do cho Seller theo quyền.
- Không thay đổi form giá, SKU, variant, location offers, inventory, shipping hoặc publish state chỉ để phục vụ Social UX.
- Quản lý phản hồi Seller giai đoạn đầu: liên kết tới danh sách comment của sản phẩm; dashboard comment tổng hợp chỉ làm khi có API quyền & paging thật; không giả dữ liệu.

### G2.4. Kiểm thử Seller

- PRODUCT, SERVICE, RESALE tạo mới và chỉnh sửa đều lưu caption đúng; test bản nháp/đã publish.
- Xóa caption và giữ `description`; không làm biến mất ảnh gốc hoặc giá.
- Thứ tự ảnh và cover sau reload nhất quán; upload lỗi không tạo dữ liệu mồ côi hiển thị công khai.
- Seller member bị thu hồi quyền (kể cả JWT đã cấp) không sửa được; kiểm thử đúng access model.
- UI layout ở 360/768/1280/1440; keyboard, label/ARIA, error recovery, refresh.

### G2.5. Definition of Done

Seller có thể tạo/sửa caption trên dữ liệu Catalog có thật, xem preview phản ánh nội dung server-confirmed, media đúng thứ tự và moderation không bị bypass; không có regression luồng tạo sản phẩm cũ.

---

## 5. G3 — SOCIAL FEED: BÀI VIẾT + GALLERY ẢNH VÀ TƯƠNG TÁC

**Ưu tiên:** P1. **Phụ thuộc:** G2 contract, G1 visibility.  
**Mục tiêu:** hiển thị bài đăng giàu nội dung mà vẫn tải nhanh.

### G3.1. Batch Social Preview API (đề xuất, CHƯA tồn tại)

Không lấy detail riêng cho mỗi card vì N+1. Không ép Catalog-mode phải nhận caption/gallery nếu không cần.

```http
GET /api/v1/catalog/social-previews?itemIds=<uuid>&itemIds=<uuid>
```

**ĐỀ XUẤT shape JSON minh họa:**

```json
{
  "<item-uuid>": {
    "itemId": "<item-uuid>",
    "socialCaption": "Bánh mới ra lò hôm nay...",
    "media": [
      { "id": "<media-uuid>", "mimeType": "image/jpeg", "url": "https://...", "sortOrder": 0, "isCover": true }
    ]
  }
}
```

- Truy vấn 1–40 ID, loại trùng, batch lookup; tối đa số media đã duyệt và chỉ trả record **công khai thực sự**.
- Áp dụng cùng predicate với `CatalogSocialService` về ACTIVE, moderation, merchant active/not suspended/visible, deletedAt.
- Không trả trạng thái nhạy cảm, storage keys hoặc media PENDING/FAILED; không xuất presigned upload link.
- Giới hạn payload/URL, input UUID strict, thứ tự media ổn định; kết quả không có item bị ẩn, không báo tài nguyên bí mật qua error chi tiết.
- Thiết kế cache response an toàn cho public; **không cache viewer flags** của Like/Save. Existing Social Summary viewer booleans được giữ khác biệt.
- Tối ưu query/indices và đo actual SQL query plan; media join không gây nhân bản bản ghi hoặc bị tùy ý count.
- OpenAPI generation + typed client + regression tests.

### G3.2. Frontend Social Card

Tái sử dụng `MarketplaceProductCard` và `SocialFeedProvider` thay vì tạo component hoàn toàn độc lập nếu không cần.

- Social mode: header người bán/vị trí, caption ở trước hoặc sau gallery theo thiết kế được duyệt, xem thêm/thu gọn, gallery ảnh carousel (counter/dots), giá + vị trí + CTA, Like–Comment–Share–Save.
- Catalog mode: vẫn card nhỏ, chỉ cover + thông tin thương mại, không phát sinh preview batch request.
- Ảnh thất bại: placeholder, không làm sập card; aspect ratio có kiểm soát tránh layout shift.
- Một ảnh: không hiện slider controls; nhiều ảnh: next/prev, swipe touch, keyboard, aria label dạng "Ảnh 2/5".
- Gallery không lấy click vào nút để điều hướng sang detail ngoài ý muốn; ảnh click có semantics xem chi tiết/overlay được duyệt.
- Caption quá dài được line clamp, nút "Xem thêm" có trạng thái và keyboard; không render HTML do Seller nhập.
- Existing Like/Save được xác nhận server trước khi hiển thị thành công; đồng bộ nhiều card trùng sản phẩm và khi quay lại tab.
- Thread comment tiếp tục lazy-open; giữ focus management, error/retry, phân trang, report và 429 message.
- Share dùng Web Share API hoặc clipboard như hiện có, không tuyên bố thành công khi bị từ chối permission.
- In-feed Ads giữ thứ tự; search filters, cursor, route và `view=social|catalog` preference giữ hoạt động.
- Không show số view/conversion/click nếu chưa có đo lường thật.

### G3.3. Mobile/Desktop & UI rules

- 360px: một card/hàng; caption và media không tràn; nút tương tác tối thiểu 44×44px theo nguyên tắc touch.
- 768px: căn lề/padding; gallery dùng chiều ngang hợp lý, thao tác cuộn dọc không bị swipe cướp.
- 1280/1440px: kiểm chứng layout Social hai card/hàng, chiều rộng hợp lý, không cắt caption/giá; Catalog nhiều cột như hiện có.
- Hỗ trợ long Vietnamese names, giá dài, vùng địa chỉ dài, no-media, loading skeleton, slow/offline, dark setting nếu app hỗ trợ.
- UI dùng **Phosphor Icons**, không Unicode emoji làm icon, nền nội dung trắng, border nhẹ theo `.agents/rules/ui-rules.md`.
- `prefers-reduced-motion`, focus ring, meaningful alt text, semantic headings, no autofocus video, keyboard and screen reader checks.

### G3.4. Kiểm thử tính nhất quán

- Duplicate item xuất hiện trong Home/Suggestions: Like/Save/Comment cập nhật nhất quán, không stale overwritten; không leak viewer state giữa tài khoản.
- Guest xem nội dung, Like/Save/Comment bị yêu cầu đăng nhập, không mutation.
- Item vừa bị hide khi đang mở feed: API trả 404/appropriate state, card không hiện dữ liệu nhạy cảm stale quá thời hạn cho phép.
- 40/41 item IDs, UUID sai, duplicate, product deleted/suspended/rejected.
- Kiểm tra mạng: Social mode request batch; Catalog mode không request Social preview; comment không tải trước khi mở.

### G3.5. Definition of Done

Social Card có caption và slider ảnh thật, không N+1, không phá Catalog mode, tất cả tương tác cũ vẫn hoạt động và kiểm duyệt được thực thi end-to-end.

---

## 6. G4 — "HOẠT ĐỘNG CỦA TÔI": LIKE, SAVE, COMMENT

**Ưu tiên:** P1–P2. **Phụ thuộc:** G0 semantics & G1 visibility.  
**ĐÃ XÁC MINH:** reaction/comments đã lưu DB nhưng chưa có danh sách `/me` dành cho người dùng.

### G4.1. Scope v1 cố định

- "Đã thích": **những item còn đang được tài khoản Like**, không bao gồm đã Unlike.
- "Đã lưu": **những item còn đang được lưu**, không bao gồm đã Unsave.
- "Bình luận của tôi": **từng bình luận hiện còn hợp lệ** của tài khoản, có thể nhiều comment/item.
- Không hiển thị nội dung item/comment mà policy không cho xem; lý do unavailable nên chung chung tránh tiết lộ điều tra.
- Không gọi 3 loại trên là "audit log tất cả sự kiện"; nếu cần lịch sử event đã hủy/xóa, phải đánh giá retention/privacy và migration append-only trong kế hoạch khác.

### G4.2. Endpoint đề xuất, CHƯA tồn tại

```http
GET /api/v1/me/social/activity?type=LIKED&limit=20&cursor=<opaque>
GET /api/v1/me/social/activity?type=SAVED&limit=20&cursor=<opaque>
GET /api/v1/me/social/activity?type=COMMENTED&limit=20&cursor=<opaque>
```

**Response đề xuất:**

```json
{
  "items": [
    {
      "itemId": "<uuid>", "itemName": "Bánh su kem", "merchantName": "Bếp Nhà An",
      "coverUrl": "https://...", "activityAt": "2026-10-15T00:00:00Z",
      "commentId": null, "commentExcerpt": null,
      "itemAvailable": true
    }
  ],
  "nextCursor": null
}
```

- Xác thực account bắt buộc; lấy actor từ JWT/session, **không nhận `userId` từ client**.
- Keyset `(created_at, unique-id)` descending hoặc thiết kế equivalent có tie-breaker; cursor opaque, kiểm tra type/filter binding (không cho đổi `type` bằng cursor cũ).
- Limit 1–50, default 20, SQL filters visibility + owner; cần tránh SQL N+1 từ joins Merchant/Media.
- `COMMENTED` lấy comment theo author, xử lý `ACTIVE/HIDDEN/DELETED` theo quy tắc privacy: v1 mặc định **chỉ ACTIVE và item visible**. Hiện tượng comment bị ẩn không gây rò nội dung cho người dùng khác.
- `LIKE/SAVE` dựa `catalog_social_reactions`; index theo `(user_id, kind, created_at desc)` đã có trong V018, nhưng phải kiểm tra plan trên dữ liệu thật.
- Không cache cross-user; `Cache-Control: private, no-store`; rate limit nếu phù hợp hệ thống hiện tại.
- Contract cuối cùng do OpenAPI/typed client sinh, không định nghĩa thủ công tương phản ở FE.

### G4.3. UI đề xuất

- Bổ sung entry trong `apps/web/features/profile/profile-overview.tsx` tới trang `/me/activity` (đường dẫn ĐỀ XUẤT).
- Ba tabs: "Đã thích", "Đã lưu", "Bình luận"; tab nằm trên URL query để có thể lưu/khôi phục.
- Mỗi item: thumbnail, tên, merchant, thời gian hành động; comment hiển thị excerpt an toàn và link đến listing/comment nếu thread hỗ trợ deep-link.
- Infinite scroll hoặc "Xem thêm" đơn giản; skeleton/loading/error/retry/empty, no unauthorized flash.
- Unsave/Unlike ngay trong danh sách chỉ làm nếu tích hợp mutation hiện có và cập nhật UI đồng bộ; không bắt buộc ở MVP.
- Mobile accessibility, keyboard tabs, screen reader notifications.

### G4.4. Test bắt buộc

- Buyer A không xem hoạt động Buyer B (401/403/404 theo policy).
- `LIKE` → hiển thị; `UNLIKE` → biến mất; `SAVE` → hiển thị; `UNSAVE` → biến mất.
- Comment 2 lần vào item → 2 activity rows, xóa một comment → chỉ mất row đó (nếu v1 chỉ ACTIVE).
- Suspended seller, hidden item, archived item, rejected content, deleted comment: phải có hành vi rõ và thống nhất quyền.
- Pagination nhiều record trùng timestamp không mất/trùng item; cursor misuse; duplicate multiple tabs/account switch.

### G4.5. Definition of Done

Người dùng có thể tìm lại nội dung đang Like/Save và bình luận còn hợp lệ từ trang cá nhân, không rò dữ liệu tài khoản khác, không giả trạng thái khi item bị ẩn.

---

## 7. G5 — VIDEO TRONG CATALOG VÀ GALLERY MIXED MEDIA

**Ưu tiên:** P2, **feature flag riêng**. **Chỉ bắt đầu sau G1–G3 + phê duyệt chi phí, content safety, storage**.

### G5.1. Baseline video hiện tại

- `MediaPurpose`: `CATALOG_IMAGE`, `AD_BANNER`, các loại avatar/logo...; **chưa có `CATALOG_VIDEO`**.
- `MediaUploadService.normalizeSupportedType(...)` hiện chỉ chấp nhận `video/mp4`/`video/webm` cho `AD_BANNER`.
- `CatalogCreateService.attachMedia` và `CatalogEditService.replaceMedia` xác minh `CATALOG_IMAGE`; video quảng cáo không tự động dùng được trong Catalog.
- `StorageProperties.maxVideoBytes()` hiện trả 50 MiB, cần xác minh cấu hình và môi trường runtime.

### G5.2. Backend cần thay đổi

1. Thêm purpose riêng `CATALOG_VIDEO` với allowlist MIME và permission seller.
2. Kiểm tra magic bytes, server-side HEAD/content size, storage URL trust, metadata probing (duration, dimensions, codec) trước khi READY/ATTACHABLE.
3. Thiết kế xử lý clip sai định dạng/codec, metadata timeout, upload treo, virus/malware scanning nếu pipeline hiện có yêu cầu; không nhận URL bên ngoài để hệ thống tự tải về.
4. Dùng cấu trúc media ordering đang tồn tại nếu có, mở rộng quan hệ catalog media để chứa loại video; **không thay bảng ảnh một cách phá tương thích**.
5. Phân biệt `coverImage` và `posterImage`: video không có poster hợp lệ phải có fallback; cân nhắc job tạo poster/video transcoding được vận hành thực tế trước khi tuyên bố hỗ trợ mọi MP4.
6. Thiết lập quota: tối đa 1 video/item, 10 media tổng v1 theo ADR; kích thước 50 MiB nếu được duyệt; giới hạn thời lượng chỉ enforce sau server-side probe thực.
7. Handle upload idempotency, cleanup failed/pending/orphan object, quota storage và retry; ensure content moderation đối với video có thể ẩn trước publish tùy chính sách.
8. Nếu cần migration, dùng additive forward-only mới, có migration test data có sẵn; không dùng lại V018/V019.
9. API public chỉ trả video đã verified/READY và item được public; không lộ object storage internal key.

### G5.3. FE uploader & playback

- Seller input ảnh/video mixed, preview/sort/cover, upload progress, rõ lỗi MIME/codecs/limit/quota, không mất draft.
- Social Card: gallery chung với ảnh, slide video có poster và play button. Video **không tự phát** trong đợt đầu; khi người dùng play, `muted/playsInline/controls` phù hợp; không auto phát âm thanh.
- Một card chơi video tại một thời điểm; khi swipe sang slide khác/unmount/out-of-view, pause và giải phóng tài nguyên phù hợp.
- `preload="none"` hoặc `metadata` theo benchmark; lazy poster, không preload nhiều video feed; CDN/range request kiểm tra thực tế.
- Fallback nếu browser không hỗ trợ codec, timeout/offline; không hiển thị spinner vô hạn.
- Chỉ người bán/Ad-min được xem nội dung moderation nhạy cảm theo quyền; public không thể đoán URL không public.

### G5.4. Testing chuyên biệt

- MP4, WebM, magic mismatch, file 0B, vượt size, codec unsupported, truncated upload, concurrent upload quota.
- Poster generation failure, metadata job failure, orphan cleanup, rollback chỉ app giữ dữ liệu media.
- Mixed ordering `[image, video, image]` được giữ trên Seller/Create/Edit/Public Feed/Detail/Admin.
- Staging Android Chrome, iOS Safari và desktop; click/swipe/keyboard/focus, network slow/3G, low-memory browser.
- Bandwidth/network requests khi feed chứa nhiều card; không phát hàng loạt.

### G5.5. Definition of Done

Một item có thể lưu/hiển thị ảnh + video đã xác minh theo thứ tự, chỉ khi flag bật; admin kiểm duyệt được, không phát media ẩn, hiệu năng đo được, chi phí được duyệt. Nếu pipeline transcoding hoặc moderation video chưa sẵn sàng, **giữ flag off**, vẫn có thể phát hành caption+ảnh.

---

## 8. HỢP ĐỒNG DỮ LIỆU, CONSISTENCY VÀ TÍNH RIÊNG TƯ

### 8.1. Dữ liệu bắt buộc giữ nguyên

- PK/catalog item ID, merchant ID và ownership.
- Product/Service/Resale types, listing status, moderation status.
- Giá, offers, currency, variant, inventory, payment/shipping/order path.
- Existing catalog images và ad banners.
- Likes/saves uniqueness `(catalog_item_id, user_id, kind)`; comment statuses ACTIVE/HIDDEN/DELETED.
- Idempotency/CSRF/ETag/outbox/audit existing.

### 8.2. Không được tạo nguồn sự thật trùng

- Không lưu `price`, `stock`, `merchantName`, `distance` dạng denormalized editable trong social caption record.
- Seller caption có thể đề cập giá bằng câu chữ, nhưng UI giá/CTA phải lấy từ Catalog authority; cân nhắc hướng dẫn Seller không ghi giá cứng gây lệch.
- Preview item phải join/resolve từ authoritative merchant/location/catalog media.
- Social counts là derived/state server, không dùng localStorage lâu dài cho trạng thái người dùng.

### 8.3. Các tình huống đồng thời phải khóa chặt

- Buyer Like và Save gần nhau: không ghi đè counters/viewer state; phản hồi server-confirmed.
- Seller update caption/media vs Admin hide: public endpoint tuyệt đối không lọt caption/media mới trong khoảng state đã ẩn.
- Admin Restore khi caption/media đã được sửa: tuân policy moderation để tránh restore nội dung chưa duyệt.
- Người dùng xóa comment trong lúc moderator HIDE: xử lý conflict nhất quán và có audit.
- Seller lost permission/khoá merchant giữa chừng: backend recheck trong transaction cần thiết.
- Migration rollback app giữ cột và record mới, không drop user-generated data.

### 8.4. Quy tắc kiểm duyệt đa phương tiện

- Kiểm duyệt caption và media cùng với Catalog Item; comment là target `SOCIAL_COMMENT` riêng.
- Hành động Admin `HIDE` ảnh hưởng tới social preview, detail và visibility public, không chỉ làm mờ thẻ giao diện.
- Backend phải enforce moderation cho public reads, direct links và cached responses.
- Không yêu cầu AI content moderation từ nhà cung cấp ngoài nếu chưa được phê duyệt về chi phí, dữ liệu, pháp lý.
- Xử lý report/lạm dụng/rate limit ở backend; không chỉ dựa client.

---

## 9. MA TRẬN PHÂN QUYỀN TỐI THIỂU

| Hành động | Guest | Buyer đăng nhập | Seller quản lý item | Seller khác | Admin |
|---|---|---|---|---|---|
| Đọc item Social được public | Có | Có | Có | Có | Có |
| Like/Save | Không | Có | Theo account policy hiện có | Theo account policy | Theo account policy |
| Comment/Report | Không | Có | Theo policy hiện có | Theo policy | Theo policy |
| Xem activity của chính mình | Không | Có | Có nếu là account | Có nếu là account | Có nếu là account (không xem chéo) |
| Sửa caption/media item | Không | Không | Có theo capability/merchant ownership | Không | Không thông qua Seller API |
| Xem tất cả item ở Admin | Không | Không | Không | Không | Có |
| Xử lý moderation case | Không | Không | Không | Không | Có qua case action hợp lệ |

**Không tự trao quyền mới** vì một user có vai Seller/Admin. Cần review `MerchantAccessPolicy`, `CurrentActorProvider` và Admin policy thực tế trước khi chốt role matrix cuối.

---

## 10. KẾ HOẠCH PULL REQUEST NHỎ, CÓ THỂ REVIEW ĐƯỢC

| PR | Thành phần | Phạm vi chủ yếu | Điều kiện merge |
|---|---|---|---|
| PR-00 | Baseline & ADR | G0 report, schema/route matrix, release evidence | Chủ dự án chốt ADR, không thay runtime |
| PR-01 | Admin Read API | Read model, endpoints, DTO, SQL/index/test, OpenAPI | Auth, pagination, visibility và CI pass |
| PR-02 | Admin UI | Listings page, detail, linking moderation cases | Không mock, E2E Admin pass |
| PR-03 | Caption Backend | Additive migration, create/edit/read, version & moderation | Migration upgrade/rollback policy pass |
| PR-04 | Seller Studio | Authoring caption + ảnh/order + preview | Seller E2E, no Catalog regression |
| PR-05 | Social Preview Backend | Batch preview API, permissions, perf test | Query bounded, 40 items, security pass |
| PR-06 | Social Feed UX | Caption truncation, gallery images, interactions | Responsive, a11y, existing social pass |
| PR-07 | My Activity | Auth endpoint, page `/me/activity`, pagination | Cross-user security and state tests pass |
| PR-08 | Mixed Video (optional) | Purpose, validation, media schema, upload UI, gallery video | Storage/codec/moderation/perf approved; flag OFF default |
| PR-09 | System Acceptance | E2E, performance, rollback, security docs | Sign-off cho rollout; không tự production deploy |

**Quy tắc làm việc:** Một PR phải có test phù hợp và đối chiếu OpenAPI khi đụng API; tránh trộn Admin–Seller–Video trong một PR khổng lồ. Nếu thứ tự trên mâu thuẫn với dependency thực tế tại commit mới, cập nhật PR dependency matrix **trước khi code**.

---

## 11. TEST PLAN CHI TIẾT THEO LỚP

### 11.1. Backend Unit / Integration

- Catalog caption validation (null/absent/empty/max, newline, escaped HTML, ETag conflicts).
- Authorize seller/edit on owned item, cross-merchant denied, revoked merchant member denied.
- Media list attach: purpose READY, owner, MIME, sort order, cover, duplicates, limits; không làm mất ảnh.
- Discovery/social preview filtering ACTIVE, MODERATION_APPROVED/NOT_REQUIRED, merchant ACTIVE; suspended/hidden/deleted fail closed.
- Batch 1, 40, 41 UUID; duplicate IDs, invalid UUID, no N+1; index/SQL explain check.
- Activity pagination/cross-user isolation, LIKE→UNLIKE, SAVE→UNSAVE, comment create/delete.
- Admin page/list/detail/filter, role policy, case actions, transitions, audit/outbox integration.
- Concurrent caption edit/Hide and comment Delete/Hide conflict correctness.
- Migrations trên empty DB + populated DB, không sửa migration đã apply.

### 11.2. Frontend Component/Integration

- `MarketplaceProductCard`: Social/Catalog, caption expand/collapse, slider interactions, prices, CTA, failed media.
- `SocialFeedProvider`: viewer state account switch, focus/online refresh, duplicate items, delayed network updates.
- `SocialComments`: lazy fetch, drafts after errors, 429, report, delete, Escape/focus.
- Seller form: create/edit changes, unsaved warning, uploader errors, stable media ordering and cover.
- Admin: real empty/error/read/list cases, no mock, refresh after moderation conflict.
- My Activity: loading/error/empty, pagination, tabs URL, deleted/hidden content.

### 11.3. Browser E2E bắt buộc

Chạy thật với API, DB, auth và media storage có dữ liệu fixture (không thay thế bằng network mock khi ghi nhận staging PASS):

1. Guest mở Home/Search: thấy item hợp lệ, chuyển Social/Catalog, không mutate.
2. Buyer Like/Save/Comment, refresh, vào `/me/activity` thấy record đúng.
3. Buyer khác đăng nhập không thấy activity account trước.
4. Seller tạo PRODUCT/SERVICE/RESALE với caption/ảnh, publish, Social Feed hiển thị đúng.
5. Seller chỉnh caption: ETag, trạng thái moderation, Admin visibility.
6. Admin search item, mở detail, case hide/restore, public visibility sync và audit/outbox.
7. Hidden merchant/suspended seller và deleted item không xuất hiện trong Social preview/comments.
8. Search filters/sort/deep links/Ads/HOME_HERO/IN_FEED unchanged.
9. Cart, checkout, merchant location, order, reviews verified-purchase không regression.
10. Khi video flag on: upload MP4/WebM hợp lệ, mixed gallery, playback, pause, deny invalid MIME.

### 11.4. Responsive + Accessibility + Security matrix

| Matrix | Bắt buộc |
|---|---|
| Viewports | 360, 768, 1280, 1440 CSS px |
| Device | Chrome desktop, mobile Chrome, Safari iOS nếu có môi trường thật |
| Auth | Guest, Buyer A, Buyer B, Seller owner, Seller outsider, Admin |
| Content | No media, long caption, 1 image, multiple images, invalid/broken media, hidden item |
| Network | slow, offline/retry, 429, 401/403/404/409/412, duplicate requests |
| Accessibility | Keyboard, screen reader labels, focus trap/restore, reduced-motion, ≥44px touch targets |
| Security | IDOR, CSRF, XSS, MIME spoof, quota abuse, unauth admin reads, direct object URL leakage |

### 11.5. Performance acceptance

- Bắt đầu từ `infra/perf/scenarios.mjs` và `infra/perf/social-scenarios.test.mjs`; bổ sung kịch bản có 20–40 cards, caption/media, nhiều comments.
- Đo API p50/p95/p99, error ratio, response size, DB pool, query count, browser network/long tasks, LCP/CLS, media transfer, memory.
- Tài liệu Phase 4 nêu **mốc thử nghiệm p95 ≤400ms và error ratio <1%** cho social-read, đây **không phải production SLO đã được chứng minh**. Chỉ phê duyệt SLO mới khi có load baseline thực tế và tài liệu chủ dự án ký duyệt.
- Video dùng budget riêng; không dựa mạng localhost để kết luận.

---

## 12. STAGING, RELEASE GATES, OBSERVABILITY VÀ ROLLBACK

### 12.1. Những việc có thể làm trước production

1. Chụp immutable commit SHA và image digests; làm staging backup trước migration.
2. Apply additive migration lên staging populated DB, kiểm tra dữ liệu cũ còn đúng.
3. Chạy `infra/scripts/run-social-phase4-staging.sh` theo runbook chuẩn với credential disposable và manifest trùng deployment thật.
4. Chạy toàn bộ test mới theo G1–G5 + regression buyer/seller/admin; không bỏ qua test không setup được.
5. Test `report → case → HIDE → audit/outbox → public hidden → RESTORE`, bao gồm caption/media/video.
6. Kiểm tra 360/768/1280/1440, evidence screenshot/trace, accessibility, security smoke, load.
7. Backup/restore & rollback rehearsal theo `infra/deploy/rehearse-release.sh` và các runbook production.
8. Kiểm tra tất cả CI **trên đúng PR head SHA**, ghi link log/artifacts; CodeQL/secrets/typed contract nếu configured.
9. Feature flag chia riêng: Admin read UI, caption UI, gallery images, activity, video. Đảm bảo flag OFF có regression/cấu trúc dữ liệu bình thường.
10. Chủ dự án phê duyệt release theo checklist; AI không tự deploy production.

### 12.2. Không phá dữ liệu khi rollback

- **Không DROP cột/bảng có Social data, đặc biệt V018/V019 và migration mới.**
- App rollback dùng deployment manifests/digests cũ; schema additive compatible with old readers/writers.
- Rollback feature flags riêng trước khi rollback app nếu nội dung nguy hiểm.
- Video đã upload không xóa/ghi đè hàng loạt nếu chưa đánh giá lifecycle, retention và ownership.
- Nếu migration không backward-compatible, **không merge** đến khi thiết kế mở rộng tương thích (expand/contract) được duyệt.

### 12.3. Quan sát sau phát hành

Metrics tối thiểu: social-preview request latency/error, media upload failures, storage growth, video delivery errors, moderation backlog, case action conflict, report rates, comment abuse rate, API 5xx/429, DB slow queries, frontend LCP/CLS. Dùng labels hữu hạn; không log body comment/caption, PII, token, storage credentials.

### 12.4. Các gate bắt buộc — tất cả phải PASS với chứng cứ

| Gate | Bằng chứng bắt buộc | Trạng thái khởi tạo |
|---|---|---|
| Baseline/ADR | SHA, inventory, migration plan, quyết định sản phẩm | NOT STARTED |
| Phase 4 cũ | Staging runner/evidence hiện hữu, không skip | UNVERIFIED |
| Backend | Maven tests, migration populated DB, auth/ownership, OpenAPI zero drift | NOT STARTED |
| Frontend | typecheck, lint, tests, build, component screenshots | NOT STARTED |
| E2E | Buyer, Seller, Admin flows, 4 viewports, real staging | NOT STARTED |
| Moderation | Report–HIDE–RESTORE, audit/outbox/public visibility | NOT STARTED |
| Performance | Số đo staging, baseline, SLO được duyệt | NOT STARTED |
| Security | IDOR/CSRF/XSS/MIME/abuse/secret checks | NOT STARTED |
| Recovery | Backup + restore + application rollback drill | NOT STARTED |
| Release | Owner sign-off, image digest, flag rollout plan | NOT STARTED |

**Lưu ý:** `UNVERIFIED` không có nghĩa là chắc chắn FAIL; nghĩa là chưa kiểm chứng lại bằng bằng chứng tại lần thực thi kế hoạch. Không đổi nhãn trạng thái theo cảm nhận.

---

## 13. LỆNH KIỂM TRA GỢI Ý — LUÔN ĐỐI CHIẾU PACKAGE SCRIPTS THỰC TẾ

Đọc `README.md`, `package.json`, workflow CI và hướng dẫn repository ở commit làm việc trước khi sử dụng lệnh; những lệnh dưới đây mang tính hướng dẫn, **không chạy production hoặc xóa dữ liệu**.

```bash
# Metadata, không phá workspace
git status --short
git rev-parse HEAD
git branch --show-current
git log -5 --oneline

# Ví dụ toolchain từ README (kiểm tra lại node/java/pnpm/OS)
pnpm install
pnpm infra:up
pnpm dev

# Frontend (kiểm tra các scripts tương ứng đã tồn tại)
pnpm web:typecheck
pnpm web:lint
pnpm web:test
pnpm web:build

# Export OpenAPI với hướng dẫn từ README / script hiện tại
./infra/scripts/export-openapi.sh

# Backend — chạy trong môi trường an toàn, credentials test
./apps/api/mvnw -f apps/api/pom.xml verify
```

**Không được tự sử dụng:** `git reset --hard`, `git clean -fd`, `docker compose down -v`, `DROP TABLE`, `TRUNCATE`, `DELETE` dữ liệu người dùng, force push, thay production secrets, hoặc deploy không xác nhận.

---

## 14. RỦI RO, PHƯƠNG ÁN GIẢM THIỂU VÀ ĐIỀU KIỆN DỪNG

| Rủi ro | Hậu quả | Giảm thiểu / điều kiện dừng |
|---|---|---|
| Nội dung mới bỏ qua moderation sau khi item đã duyệt | Hiển thị nội dung vi phạm | Revalidate on edit; deny public until valid; test race |
| Batch social preview N+1 hoặc payload quá lớn | Feed chậm và quá tải DB | max40, bounded gallery, SQL plan/load test, feature flag |
| Ẩn listing chỉ ở FE | IDOR/direct API leak | enforce tất cả public API tại server và cache |
| Social caption phá `description` | Dữ liệu sản phẩm sai | field độc lập + migration additive + regression |
| Video codec/metadata không xác minh | Browser không phát, rủi ro storage | magic + probing/transcoding policy + video flag OFF |
| Media storage object mồ côi | Chi phí và rò dữ liệu | upload states, cleanup policy, quota, lifecycle tests |
| Chính sách seller bị nâng quyền ngầm | Người khác sửa listing | auth matrix + access integration test |
| Admin direct mutations bypass audit | Mất khả năng truy vết | tất cả moderation ghi qua case state machine |
| Cursor hoạt động sai dưới đồng thời | Item trùng/mất trong activity | opaque keyset + tie-breaker + tests |
| Backward incompatible migration | Rollback không an toàn | expand-only, populated migration test, restore rehearsal |
| Chỉ CI xanh đã deploy production | Sự cố UX/perf trên thực tế | staging + manual + security + owner release gate |
| UI emoji/gray card trái guideline | Không nhất quán thiết kế | review `.agents/rules/ui-rules.md` và visual QA |

**DỪNG NGAY và báo chủ dự án** khi: schema/permission/API thực tế không khớp baseline, phải làm destructive migration, cần cấp quyền Admin mới chưa duyệt, nghi ngờ lộ nội dung ẩn, benchmark vượt budget chưa được chốt, không có staging/evidence, hoặc task buộc sửa chức năng order/checkout ngoài phạm vi.

---

## 15. BẢNG TRUY VẾT YÊU CẦU → TRIỂN KHAI → NGHIỆM THU

| ID | Yêu cầu người dùng | Giai đoạn | Acceptance tối thiểu |
|---|---|---|---|
| F01 | Xem những gì đã Like | G4 | `/me/activity` LIKED đúng tài khoản, unlike biến mất |
| F02 | Xem những gì đã Save | G4 | `/me/activity` SAVED, unsave biến mất |
| F03 | Xem bình luận cá nhân | G4 | COMMENTED theo từng comment, cross-user denied |
| F04 | Soạn nội dung dài như Facebook | G2+G3 | Seller nhập caption, public Social Card hiển thị xuống dòng/expand |
| F05 | Hiển thị nhiều ảnh | G2+G3 | Feed gallery ảnh, thứ tự và cover nhất quán |
| F06 | Ảnh và video hỗn hợp | G5 | Upload verified + mixed ordered playback, flag riêng |
| F07 | Seller quản lý Social Card | G2 | Preview + caption/media + moderation status, no Catalog regression |
| F08 | Admin xem toàn bộ nội dung đăng bán | G1 | Search/filter/detail thật, no mock, admin-only |
| F09 | Admin xử lý vi phạm | G1 | Case state machine + audit/outbox + visibility |
| F10 | Bảo toàn mô hình thương mại | G0–G6 | Search/Ads/Cart/Checkout/Orders/Reviews pass regression |
| F11 | An toàn phát hành | G6 | CI + staging + load + security + recovery + sign-off |

---

## 16. TEMPLATE BÁO CÁO BẮT BUỘC SAU MỖI GIAI ĐOẠN

Agent phải trả về tài liệu có các trường sau, không chỉ nói "hoàn thành":

```text
GIAI ĐOẠN: G1 / G2 / G3 / G4 / G5 / G6
BASE COMMIT SHA: ...
WORKING COMMIT SHA: ...
BRANCH/PR: ...
PHẠM VI ĐÃ LÀM: ...
FILE ĐÃ SỬA/TẠO: ...
MIGRATION MỚI / TÌNH TRẠNG APPLY: ...
API CHANGE + OPENAPI DIFF: ...
SECURITY / ROLE CHECK: ...
TEST COMMAND ĐÃ CHẠY: ...
TEST SỐ LƯỢNG/PASS/FAIL/SKIP: ...
LOG/ARTIFACT/SCREENSHOT/TRACE: ...
BẢNG Fxx ĐẠT/CHƯA ĐẠT: ...
REGRESSION ẢNH HƯỞNG: ...
RỦI RO CÒN LẠI: ...
GATE: PASS | BLOCKED | FAIL | UNVERIFIED
CẦN NGƯỜI DÙNG DUYỆT: ...
HÀNH ĐỘNG TIẾP THEO ĐỀ XUẤT: ...
```

- Test chưa chạy: `NOT RUN`, không tự đánh PASS.
- Test bị skip: nêu số lượng/lý do và tính ảnh hưởng; gate staging thực yêu cầu không bỏ qua các kịch bản bắt buộc.
- Log, screenshot, commit và CI phải là link/path thật, không bịa.
- Sau **mỗi giai đoạn**, dừng báo cáo cho chủ dự án duyệt rồi mới chuyển giai đoạn tiếp theo.

---

## 17. PROMPT GIAO CHO AI CODING AGENT — CÓ THỂ SAO CHÉP

> Bạn là AI kỹ sư triển khai CoVie. Đọc toàn bộ tài liệu `CoVie_Social_Product_Card_Ke_Hoach_Trien_Khai_Chi_Tiet_v1.0.md` và tuân thủ thứ tự G0→G6. Trước khi viết code, kiểm tra commit mới nhất, `AGENTS.md`, `RULES.md`, `.agents/rules/ui-rules.md`, README, migrations, OpenAPI, code hiện có và các PR đang mở. Đối chiếu với baseline `803b25b...` rồi lập báo cáo sai khác. Tuyệt đối không coi API/model được ghi "ĐỀ XUẤT" là đã tồn tại. Không tự tăng scope, không tạo post/blog độc lập, không làm lại Catalog hay Checkout, không sửa migration đã apply, không ghi dữ liệu giả, không làm giảm RBAC/CSRF/IDOR/moderation. Bất kỳ chỗ nào thiếu thông tin ảnh hưởng đến schema, permission, release, content safety hoặc chi phí video phải trình phương án và đợi duyệt; không suy luận thay. Mỗi giai đoạn phải có source diff, integration tests, generated OpenAPI zero drift, regression, report bằng chứng và gate rõ ràng. Kết thúc mỗi giai đoạn phải **dừng để người dùng nghiệm thu**. Không merge PR và không deploy production khi chưa được chủ dự án cho phép rõ ràng. Nhiệm vụ hiện tại: **CHỈ THỰC HIỆN G0** (điều tra + ADR + test/release gate status), chưa sửa business code.

---

## 18. TÀI LIỆU THAM CHIẾU VÀ DẤU MỐC

1. Repository: <https://github.com/TanBuiDev/covie>
2. PR #28: <https://github.com/TanBuiDev/covie/pull/28>
3. Merge commit: <https://github.com/TanBuiDev/covie/commit/803b25b89f4686b4135b0de5ef101aabcc84b614>
4. Phase 4 review: <https://github.com/TanBuiDev/covie/blob/main/docs/plans/social-product-card/phase-4-implementation-review.md>
5. UI rules: <https://github.com/TanBuiDev/covie/blob/main/.agents/rules/ui-rules.md>
6. Current social service: <https://github.com/TanBuiDev/covie/blob/main/apps/api/src/main/java/vn/covie/social/application/CatalogSocialService.java>
7. Current Social Card: <https://github.com/TanBuiDev/covie/blob/main/apps/web/features/marketplace-view/marketplace-product-card.tsx>
8. Admin listings: <https://github.com/TanBuiDev/covie/blob/main/apps/web/app/%28admin%29/admin/listings/page.tsx>

**Tuyên bố chất lượng:** Đây là kế hoạch triển khai dựa trên việc đọc repository ở baseline nêu trên, **không phải bằng chứng đã chạy build, test, staging, SQL migration hoặc production**. Mọi bằng chứng mới phải được tạo trong quá trình thực hiện bởi AI coding agent/devops và được xác minh độc lập.

**HẾT TÀI LIỆU — Version 1.0.**
