# PHỤ LỤC B. “MIGRATION” VÀ TỪ ĐIỂN DỮ LIỆU CHỌN LỌC

## B.1. Danh mục “migration”

Danh mục gồm các tệp “migration” tại phiên bản c477f30, thể hiện thứ tự thay đổi cấu trúc dữ liệu. Số lượng trong bảng được thống kê từ các lệnh “CREATE TABLE”; đây là cấu trúc khai báo trong mã nguồn, chưa phải kết quả kiểm tra trạng thái “migration” của một cơ sở dữ liệu đang chạy.

| “Migration” | Số “CREATE TABLE” |
| --- | --- |
| V001__extensions_and_foundation.sql | 0 |
| V002__identity_users_and_audit.sql | 6 |
| V003__auth_sessions_and_tickets.sql | 3 |
| V004__location_merchant_media.sql | 9 |
| V005__catalog_and_search.sql | 14 |
| V006__idempotency_and_outbox.sql | 2 |
| V007__delivery_shipping_inventory.sql | 7 |
| V008__carts_and_checkout_sessions.sql | 3 |
| V009__order_creation_foundation.sql | 5 |
| V010__order_lifecycle.sql | 2 |
| V011__need_matching.sql | 3 |
| V012__trust_notifications_outbox.sql | 8 |
| V013__moderation_system_settings_admin.sql | 4 |

Tổng cộng có 66 khai báo bảng trong 13 “migration”. Các phiên bản “migration” sau có thể điều chỉnh bảng và ràng buộc đã tạo. Từ điển dưới đây trình bày các cột chọn lọc theo câu lệnh tạo ban đầu; cấu trúc sau cùng được xác định bằng toàn bộ chuỗi thay đổi tương ứng.

## B.2. Các bảng theo “migration”

**V001__extensions_and_foundation.sql**
Không tạo bảng mới; xem các thay đổi khác trong “migration”.

**V002__identity_users_and_audit.sql**
“users”, “user_identities”, “system_role_assignments”, “user_contact_methods”, “user_addresses”, “audit_logs”

**V003__auth_sessions_and_tickets.sql**
“auth_sessions”, “auth_refresh_tokens”, “auth_action_tickets”

**V004__location_merchant_media.sql**
“location_areas”, “media_assets”, “merchant_profiles”, “merchant_memberships”, “merchant_locations”, “merchant_member_locations”, “merchant_contact_methods”, “merchant_location_hours”, “merchant_location_hour_exceptions”

**V005__catalog_and_search.sql**
“categories”, “category_attributes”, “category_attribute_options”, “tags”, “catalog_items”, “catalog_item_attribute_values”, “catalog_item_tags”, “option_groups”, “option_values”, “variants”, “variant_option_values”, “location_variants”, “catalog_item_media”, “merchant_media”

**V006__idempotency_and_outbox.sql**
“idempotency_records”, “outbox_events”

**V007__delivery_shipping_inventory.sql**
“delivery_coverages”, “delivery_coverage_areas”, “shipping_policies”, “shipping_rules”, “inventory_balances”, “inventory_reservations”, “inventory_movements”

**V008__carts_and_checkout_sessions.sql**
“carts”, “cart_items”, “checkout_sessions”

**V009__order_creation_foundation.sql**
“orders”, “order_items”, “product_fulfillments”, “service_fulfillments”, “order_events”

**V010__order_lifecycle.sql**
“order_cancellation_requests”, “order_issues”

**V011__need_matching.sql**
“need_requests”, “need_responses”, “need_response_items”

**V012__trust_notifications_outbox.sql**
“reviews”, “catalog_item_rating_stats”, “merchant_rating_stats”, “reputation_order_outcomes”, “user_reputation_stats”, “merchant_reputation_stats”, “notifications”, “notification_deliveries”

**V013__moderation_system_settings_admin.sql**
“system_settings”, “system_setting_history”, “moderation_cases”, “moderation_actions”

## B.3. “users” — Tài khoản người dùng

Nguồn tạo bảng: V002__identity_users_and_audit.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “display_name” | “varchar”(120) | “NOT NULL” |
| “created_at” | “timestamptz” | “NOT NULL” |
| “updated_at” | “timestamptz” | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.4. “merchant_profiles” — Đơn vị bán hàng hoặc cung cấp dịch vụ

Nguồn tạo bảng: V004__location_merchant_media.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “name” | “varchar”(180) | “NOT NULL” |
| “created_at” | “timestamptz” | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.5. “merchant_locations” — Điểm bán và khả năng thực hiện

Nguồn tạo bảng: V004__location_merchant_media.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “merchant_profile_id” | “uuid” | “NOT NULL”; “FK” → “merchant_profiles”.“id” |
| “name” | “varchar”(150) | “NOT NULL” |
| “address_text” | “text” | “NOT NULL” |
| “geo_point” | “geography”(“Point”,4326) | “NOT NULL” |
| “pickup_enabled” | “boolean” | “NOT NULL” |
| “seller_delivery_enabled” | “boolean” | “NOT NULL” |
| “service_enabled” | “boolean” | “NOT NULL” |
| “minimum_delivery_order” | “bigint” | Cho phép “NULL” theo khai báo |
| “version” | “bigint” | “NOT NULL” |

## B.6. “catalog_items” — Mô tả mặt hàng

Nguồn tạo bảng: V005__catalog_and_search.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “merchant_profile_id” | “uuid” | “NOT NULL”; “FK” → “merchant_profiles”.“id” |
| “item_type” | “varchar”(20) | “NOT NULL” |
| “listing_kind” | “varchar”(20) | “NOT NULL” |
| “name” | “varchar”(220) | “NOT NULL” |
| “primary_category_id” | “uuid” | “NOT NULL”; “FK” → “categories”.“id” |

## B.7. “location_variants” — Chào bán tại một điểm bán

Nguồn tạo bảng: V005__catalog_and_search.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “merchant_location_id” | “uuid” | “NOT NULL”; “FK” → “merchant_locations”.“id” |
| “variant_id” | “uuid” | “NOT NULL”; “FK” → “variants”.“id” |
| “price” | “bigint” | “NOT NULL” |
| “currency_code” | “char”(3) | “NOT NULL” |
| “inventory_mode” | “varchar”(20) | Cho phép “NULL” theo khai báo |
| “is_available” | “boolean” | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.8. “inventory_balances” — Tồn và lượng đã giữ

Nguồn tạo bảng: V007__delivery_shipping_inventory.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “location_variant_id” | “uuid” | “PK”; “NOT NULL”; “FK” → “location_variants”.“id” |
| “stock_on_hand” | “bigint” | “NOT NULL” |
| “reserved_quantity” | “bigint” | “NOT NULL” |
| “updated_at” | “timestamptz” | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.9. “carts” — Giỏ theo người, điểm bán và loại hàng

Nguồn tạo bảng: V008__carts_and_checkout_sessions.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “merchant_location_id” | “uuid” | “NOT NULL”; “FK” → “merchant_locations”.“id” |
| “cart_type” | “varchar”(20) | “NOT NULL” |
| “cart_status” | “varchar”(20) | “NOT NULL” |
| “version” | “bigint” | “NOT NULL” |

## B.10. “checkout_sessions” — Phiên chấp nhận “checkout”

Nguồn tạo bảng: V008__carts_and_checkout_sessions.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “token_hash” | “varchar”(64) | “NOT NULL”; “UNIQUE” |
| “user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “source_type” | “varchar”(30) | “NOT NULL” |
| “cart_version” | “bigint” | Cho phép “NULL” theo khai báo |
| “request_snapshot” | “jsonb” | “NOT NULL” |
| “preview_snapshot” | “jsonb” | “NOT NULL” |
| “expires_at” | “timestamptz” | “NOT NULL” |
| “consumed_at” | “timestamptz” | Cho phép “NULL” theo khai báo |

## B.11. “orders” — Đơn và tiền tại thời điểm giao dịch

Nguồn tạo bảng: V009__order_creation_foundation.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “order_code” | “varchar”(32) | “NOT NULL”; “UNIQUE” |
| “order_type” | “varchar”(20) | “NOT NULL” |
| “buyer_user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “merchant_location_id” | “uuid” | “NOT NULL”; “FK” → “merchant_locations”.“id” |
| “status” | “varchar”(60) | “NOT NULL” |
| “source_type” | “varchar”(20) | “NOT NULL” |
| “payment_method” | “varchar”(40) | “NOT NULL” |
| “subtotal” | “bigint” | “NOT NULL” |
| “shipping_fee” | “bigint” | Cho phép “NULL” theo khai báo |
| “known_total” | “bigint” | “NOT NULL” |
| “total_amount” | “bigint” | Cho phép “NULL” theo khai báo |
| “version” | “bigint” | “NOT NULL” |

## B.12. “order_items” — Dòng đơn và dữ liệu lịch sử

Nguồn tạo bảng: V009__order_creation_foundation.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “order_id” | “uuid” | “NOT NULL”; “FK” → “orders”.“id” |
| “location_variant_id” | “uuid” | Cho phép “NULL” theo khai báo; “FK” → “location_variants”.“id” |
| “item_type” | “varchar”(20) | “NOT NULL” |
| “quantity” | “bigint” | “NOT NULL” |
| “unit_price” | “bigint” | “NOT NULL” |
| “line_total” | “bigint” | “NOT NULL” |
| “item_snapshot” | “jsonb” | “NOT NULL” |

## B.13. “need_requests” — Nhu cầu phía người mua

Nguồn tạo bảng: V011__need_matching.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “buyer_user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “need_type” | “varchar”(20) | “NOT NULL” |
| “category_id” | “uuid” | “NOT NULL”; “FK” → “categories”.“id” |
| “expires_at” | “timestamptz” | “NOT NULL” |

## B.14. “reviews” — Đánh giá theo giao dịch

Nguồn tạo bảng: V012__trust_notifications_outbox.sql. Các trường dưới được chọn theo vai trò của bảng; xem “migration” để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.

| Cột | Kiểu “SQL” | Thuộc tính tại “CREATE TABLE” |
| --- | --- | --- |
| “id” | “uuid” | “PK”; “NOT NULL” |
| “order_item_id” | “uuid” | “NOT NULL”; “FK” → “order_items”.“id” |
| “buyer_user_id” | “uuid” | “NOT NULL”; “FK” → “users”.“id” |
| “rating” | “smallint” | “NOT NULL” |
| “comment” | “text” | Cho phép “NULL” theo khai báo |
| “created_at” | “timestamptz” | “NOT NULL” |

# PHỤ LỤC C. DANH MỤC “API”

Hợp đồng “OpenAPI” 3.1.0 gồm 119 đường dẫn và 144 thao tác “HTTP”. Danh mục dưới đây nhóm các đường dẫn theo tài nguyên. Tham số, nội dung yêu cầu, phản hồi và cơ chế xác thực được mô tả tại từng thao tác trong tệp hợp đồng; kết quả kiểm thử “API” được trình bày tại Chương 6.

## C.1. Nhóm “admin”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/admin/audit-logs |
| “POST” | /api/v1/admin/maintenance/ratings/rebuild |
| “POST” | /api/v1/admin/maintenance/reputation/rebuild |
| “GET” | /api/v1/admin/merchants |
| “GET” | /api/v1/admin/merchants/{merchantId} |
| “POST” | /api/v1/admin/merchants/{merchantId}/restore |
| “POST” | /api/v1/admin/merchants/{merchantId}/suspend |
| “GET” | /api/v1/admin/moderation-cases |
| “POST” | /api/v1/admin/moderation-cases/bulk-actions |
| “GET” | /api/v1/admin/moderation-cases/{caseId} |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/approve |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/close |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/hide-content |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/reject |
| “POST” | /api/v1/admin/moderation-cases/{caseId}/restore-content |
| “GET” | /api/v1/admin/orders |
| “GET” | /api/v1/admin/orders/{orderId} |
| “GET” | /api/v1/admin/orders/{orderId}/events |
| “GET” | /api/v1/admin/settings |
| “GET”, “PATCH” | /api/v1/admin/settings/{settingKey} |
| “GET” | /api/v1/admin/settings/{settingKey}/history |
| “GET” | /api/v1/admin/users |
| “GET” | /api/v1/admin/users/{userId} |
| “POST” | /api/v1/admin/users/{userId}/restore |
| “POST” | /api/v1/admin/users/{userId}/suspend |

## C.2. Nhóm “auth”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/auth/csrf |
| “GET” | /api/v1/auth/google/start |
| “POST” | /api/v1/auth/login |
| “POST” | /api/v1/auth/logout |
| “POST” | /api/v1/auth/onboarding/set-password |
| “POST” | /api/v1/auth/password/reset-with-google |
| “POST” | /api/v1/auth/refresh |

## C.3. Nhóm “catalog”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/catalog/items |
| “GET” | /api/v1/catalog/items/{itemId} |
| “GET” | /api/v1/catalog/items/{itemId}/reviews |
| “GET” | /api/v1/catalog/merchants/{merchantId} |

## C.4. Nhóm “categories”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/categories |
| “GET” | /api/v1/categories/{categoryId} |

## C.5. Nhóm “checkouts”

| “Method” | Đường dẫn |
| --- | --- |
| “POST” | /api/v1/checkouts/commit |
| “POST” | /api/v1/checkouts/preview |

## C.6. Nhóm “health”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/health |

## C.7. Nhóm “location-areas”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/location-areas |

## C.8. Nhóm “me”

| “Method” | Đường dẫn |
| --- | --- |
| “GET”, “PATCH” | /api/v1/me |
| “GET”, “POST” | /api/v1/me/addresses |
| “GET”, “DELETE”, “PATCH” | /api/v1/me/addresses/{addressId} |
| “GET” | /api/v1/me/carts |
| “POST” | /api/v1/me/carts/items |
| “GET” | /api/v1/me/carts/{cartId} |
| “DELETE”, “PATCH” | /api/v1/me/carts/{cartId}/items/{itemId} |
| “GET”, “POST” | /api/v1/me/contact-methods |
| “DELETE”, “PATCH” | /api/v1/me/contact-methods/{contactMethodId} |
| “GET” | /api/v1/me/events |
| “GET”, “POST” | /api/v1/me/merchants |
| “GET”, “POST” | /api/v1/me/need-requests |
| “GET” | /api/v1/me/need-requests/{needRequestId} |
| “POST” | /api/v1/me/need-requests/{needRequestId}/cancel |
| “GET” | /api/v1/me/need-requests/{needRequestId}/responses |
| “GET” | /api/v1/me/notifications |
| “POST” | /api/v1/me/notifications/mark-all-read |
| “POST” | /api/v1/me/notifications/{notificationId}/mark-read |
| “GET” | /api/v1/me/orders |
| “POST” | /api/v1/me/reviews |
| “GET” | /api/v1/me/security/sessions |
| “POST” | /api/v1/me/security/sessions/revoke-others |
| “DELETE” | /api/v1/me/security/sessions/{sessionId} |

## C.9. Nhóm “media”

| “Method” | Đường dẫn |
| --- | --- |
| “POST” | /api/v1/media/uploads |
| “POST” | /api/v1/media/{mediaId}/complete |

## C.10. Nhóm “merchants”

| “Method” | Đường dẫn |
| --- | --- |
| “GET”, “PATCH” | /api/v1/merchants/{merchantId} |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/catalog-items |
| “PATCH” | /api/v1/merchants/{merchantId}/catalog-items/{itemId} |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/activate |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/archive |
| “PATCH” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/location-offers/{locationVariantId} |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/pause |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish |
| “POST” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId} |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/contacts |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/contacts/{contactId} |
| “GET” | /api/v1/merchants/{merchantId}/inventory |
| “POST” | /api/v1/merchants/{merchantId}/inventory/bulk-set-stock |
| “POST” | /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/set-stock |
| “POST” | /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-in |
| “POST” | /api/v1/merchants/{merchantId}/inventory/{locationVariantId}/stock-out |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/locations |
| “GET”, “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/locations/{locationId} |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId} |
| “GET”, “PUT” | /api/v1/merchants/{merchantId}/locations/{locationId}/hours |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies/{policyId} |
| “GET”, “POST” | /api/v1/merchants/{merchantId}/members |
| “DELETE”, “PATCH” | /api/v1/merchants/{merchantId}/members/{memberId} |
| “PUT” | /api/v1/merchants/{merchantId}/members/{memberId}/locations |
| “GET” | /api/v1/merchants/{merchantId}/need-requests |
| “GET” | /api/v1/merchants/{merchantId}/orders |
| “GET” | /api/v1/merchants/{merchantId}/reviews |

## C.11. Nhóm “need-requests”

| “Method” | Đường dẫn |
| --- | --- |
| “POST” | /api/v1/need-requests/{needRequestId}/responses |
| “PUT” | /api/v1/need-requests/{needRequestId}/responses/{responseId} |
| “POST” | /api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw |

## C.12. Nhóm “orders”

| “Method” | Đường dẫn |
| --- | --- |
| “GET” | /api/v1/orders/{orderId} |
| “POST” | /api/v1/orders/{orderId}/accept-cancellation |
| “POST” | /api/v1/orders/{orderId}/cancel |
| “POST” | /api/v1/orders/{orderId}/confirm |
| “POST” | /api/v1/orders/{orderId}/confirm-completed |
| “POST” | /api/v1/orders/{orderId}/confirm-received |
| “POST” | /api/v1/orders/{orderId}/contest-no-show |
| “GET” | /api/v1/orders/{orderId}/events |
| “POST” | /api/v1/orders/{orderId}/mark-delivered |
| “POST” | /api/v1/orders/{orderId}/mark-out-for-delivery |
| “POST” | /api/v1/orders/{orderId}/mark-ready-for-pickup |
| “POST” | /api/v1/orders/{orderId}/mark-service-performed |
| “POST” | /api/v1/orders/{orderId}/reject |
| “POST” | /api/v1/orders/{orderId}/reject-cancellation |
| “POST” | /api/v1/orders/{orderId}/report-buyer-no-show |
| “POST” | /api/v1/orders/{orderId}/report-delivery-issue |
| “POST” | /api/v1/orders/{orderId}/report-service-issue |
| “POST” | /api/v1/orders/{orderId}/request-cancellation |

## C.13. Nhóm “reports”

| “Method” | Đường dẫn |
| --- | --- |
| “POST” | /api/v1/reports |

# PHỤ LỤC D. GHI NHẬN PHIÊN BẢN VÀ HÌNH MINH HỌA

Phạm vi phân tích kỹ thuật sử dụng mã nguồn tại “commit” c477f308b71a512b8002fe9d26866708527f8da1, đối chiếu ngày 04/09/2026. Thống kê “migration”, hợp đồng “API” và tệp kiểm thử được xác định từ mã nguồn. Kết quả thực thi ngày 26/08/2026 được trình bày riêng tại Chương 6 theo đúng phạm vi phiên bản đã ghi nhận.

Các sơ đồ tác nhân, kiến trúc, dữ liệu, “checkout” và trạng thái đơn biểu diễn mô hình cùng luồng xử lý của CoVie. Sơ đồ “ERD” tập trung vào các quan hệ cốt lõi; danh mục cấu trúc dữ liệu chi tiết được bổ sung tại Phụ lục B.

Chương 5 sử dụng 29 ảnh giao diện ứng dụng với dữ liệu thử nghiệm và 14 hình thiết kế từ “Figma”, trình bày trong 31 hình minh họa. Các hình bao quát người mua, người bán, quản trị và những trạng thái giao diện tiêu biểu. Hình thiết kế được phân biệt bằng chú thích nguồn; dữ liệu trong hình không đại diện cho khách hàng hoặc hoạt động thương mại thực tế.
