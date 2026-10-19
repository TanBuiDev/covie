# Social V2 — merchant suspension guard trong Social Create

Ngày: 2026-10-16. Giai đoạn: G2/8.3 verification, không hoàn tất toàn phase. Base803b25b; working HEADbb2e06b41282c8e9205c35a13e7b6adec68ceaa2/main plus dirty source, no new PR. Lượt trước là progress: full requirement audit và actual Phase4 runner defect sửa/kiểm chứng cục bộ. Lượt này chỉ xử lý named merchant-state transactional requirement trong8.3, không thêm prohibition nghiệp vụ mới.

## Source trace và quyết định trước test

- `CatalogCreateService.create()` kiểm tra capability + merchant ACTIVE trước idempotency. Trong `createInTransaction()` lại kiểm tra membership (shared lock cho V2 caption/media), sau đó gọi existing `MerchantProfileRepository.findActiveForUpdate()` và kiểm tra merchant ACTIVE lần nữa. Không chỉ tin preflight hoặc JWT.
- `AdminMerchantService.suspend()` dùng cùng existing pessimistic merchant lock, lưu SUSPENDED/statusBeforeSuspension/updatedAt/version, audit và outbox. `open-in-view:false` trong normal/prod config; không cố tình giữ detached ACTIVE entity từ preflight.
- Vì source thể hiện đúng guard, **không có production bug để sửa**. Một focused actual contention case phù hợp8.3 xác minh locked recheck hoạt động đúng khi suspension đứng trước Create.
- `CatalogEditService.updateItem()` không có rule merchant-must-be-ACTIVE; `CatalogManagementActionPolicy.itemActions()` cho UPDATE theo capability, không theo merchant status. Existing `CatalogPublishValidator` mới kiểm tra ACTIVE khi publish/activate; public Social/preview SQL độc lập yêu cầu merchant ACTIVE/visible. Membership guard và caption/media reapproval vẫn áp dụng cho Edit.
- Do đó không tự biến8.3 thành lệnh cấm sửa draft/metadata của merchant suspended hoặc thêm merchant lock vào mọi Edit. Chưa có contract/evidence đủ để thay nghiệp vụ quản lý hiện tại. Source distinction này không phải actual Edit-suspension browser/race proof, cũng không chứng minh mọi lifecycle operation atomicity.

## Test mới, đúng một schedule có căn cứ

Thêm một method vào existing `SocialCreateMembershipRevocationIntegrationTest`, giữ nguyên old membership-revocation case. Fixture: actual issued OWNER/Admin JWTs, disposable PostgreSQL, ACTIVE/APPROVED merchant, ACTIVE owner membership/location, READY owned CATALOG_IMAGE, PRODUCT Create body có caption/media + offer25000.

1. Independent connection giữ merchant `FOR NO KEY UPDATE`.
2. Actual Admin suspend request vào trước. Observe1real PostgreSQL lock waiter.
3. Actual Social Create request đi qua ACTIVE preflight, membership guard, tới cùng merchant lock. Observe2real waiters trước release; captured SQL là Hibernate `FOR NO KEY UPDATE OF ...`, không chỉ giả định request đã tới barrier.
4. Release blocker. Admin suspend200; waiting Create422/existing `MERCHANT_ACTION_NOT_ALLOWED`. Không thay error/API/role contract.
5. Merchant full row chỉ đổi đúng SUSPENDED/status_before_suspension=ACTIVE/version1/updatedAt. Full membership/location/media rows giữ nguyên; membership vẫn ACTIVE.
6. Zero merchant item, offer, media association, Create idempotency record hoặc CATALOG_ITEM_CREATED outbox. Exactly1ADMIN_MERCHANT_SUSPENDED audit +1outbox là expected suspension effects, không gọi cả scenario readonly. Same issued OWNER JWT + same Create key retry422.

```text
mvnw.cmd -o -Dtest=SocialCreateMembershipRevocationIntegrationTest#committedMerchantSuspensionCannotBeFollowedByWaitingCaptionCreate test
```

**1PASS19.64s/Maven28.336s,0skip**, `.local/social-v2-create-merchant-suspension.log`. First run PASS on unchanged production code; không dựng một artificial RED hoặc đổi guard đã đúng. Không chạy lại old Create membership test/old browser flows/full BE. New test compiled by Maven; whitespace check PASS. Create/access/repository/Admin service/test SHA256 match isolated validation copies, nên không dùng kết quả từ stale production copy.

## Bảo toàn và trạng thái gate

File source mới đổi: test method/helper nêu trên; docs/AGENTS/matrix. Production/API/OpenAPI/schema/generated client/UI/migration changes: **none**. No runtime/recovered DB access, normal service start/restart/stop, remote Git/commit/branch/merge, activation/deploy. Normal3000/8081 không có listener ở final observation và untouched. Testcontainers lifecycle đóng fixture sau Maven terminal run.

Scoped local Create suspension-first transactional recheck đã được kiểm chứng; không suy rộng thành mọi ordering/role/lifecycle/merchant-HIDE/browser/staging/load proof. Previous current-tree final build gap và perf/staging/storage/recovery requirements giữ nguyên. Whole V2 ACTIVE; normal V2/video flags OFF. Không cần phê duyệt lại trong scope đã duyệt.

Next action: Bổ sung preparation perf còn thiếu được chỉ ra tại11.5: V2 preview/activity scenarios và đo response payload đầy đủ, chỉ với targeted contract checks.
Required from you: Nothing
Completion condition: Có kịch bản và phép đo phản ánh đúng API V2 để dùng khi staging sẵn sàng, không tuyên bố localhost/stub là staging SLO.
