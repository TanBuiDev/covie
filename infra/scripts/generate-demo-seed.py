#!/usr/bin/env python3
"""Generate the deterministic, guarded CoVie demo-data-v1 PostgreSQL snapshot."""
from __future__ import annotations

import argparse
import gzip
import io
import json
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "infra/demo/seed-demo-data-v1.sql.gz"
NAMESPACE = uuid.UUID("90c68083-9692-55f5-a990-77f043713dd5")
PASSWORD = "CoVieDemo!2026"
PASSWORD_HASH = (
    "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$"
    "Y292aWUtZGVtby12MSEhIQ$VD7QqObb/vxN1jyUqP9rihNC/FNEf/03AB5M1xdCgtk"
)
EXPECTED = {
    "users": 22,
    "merchants": 10,
    "catalog_items": 45,
    "variants": 60,
    "location_variants": 55,
    "inventory_balances": 18,
    "carts": 11,
    "cart_items": 25,
    "checkout_sessions": 11,
    "orders": 26,
    "active_need_requests": 5,
    "need_responses": 10,
    "need_response_items": 20,
    "reviews": 18,
    "notifications": 24,
    "moderation_cases": 7,
}


USERS = [
    ("buyer-01", "Nguyễn Văn Minh"),
    ("buyer-02", "Trần Thu Hà"),
    ("buyer-03", "Lê Hoàng Nam"),
    ("buyer-04", "Phạm Ngọc Mai"),
    ("buyer-05", "Võ Gia Hân"),
    ("buyer-06", "Đặng Quốc Bảo"),
    ("buyer-07", "Bùi Thanh Trúc"),
    ("buyer-08", "Đỗ Minh Khang"),
    ("seller-01", "Nguyễn An"),
    ("seller-02", "Trần Bình"),
    ("seller-03", "Lê Chi"),
    ("seller-04", "Phạm Dũng"),
    ("seller-05", "Võ Em"),
    ("seller-06", "Đặng Phúc"),
    ("seller-07", "Bùi Giang"),
    ("seller-08", "Đỗ Hương"),
    ("seller-09", "Hồ Khôi"),
    ("seller-10", "Ngô Lan"),
    ("staff-01", "Nhân viên Mai"),
    ("staff-02", "Nhân viên Sơn"),
    ("admin-01", "Quản trị CoVie"),
    ("admin-02", "Điều hành CoVie"),
]

MERCHANTS = [
    ("mer-01", "Bếp Nhà An", "Món Việt nấu trong ngày, giao nhanh nội khu.", "SHOP"),
    ("mer-02", "Vườn Xanh Grand Park", "Rau củ và thực phẩm tươi chọn theo mùa.", "SHOP"),
    ("mer-03", "Tiệm Bánh Mây", "Bánh và đồ uống làm theo mẻ nhỏ mỗi ngày.", "SHOP"),
    ("mer-04", "Tạp Hóa Bình Minh", "Đồ dùng thiết yếu cho gia đình trong khu đô thị.", "SHOP"),
    ("mer-05", "Góc Nhà Mới", "Đồ gia dụng bền đẹp và tiện dụng.", "SHOP"),
    ("mer-06", "Đồ Cũ Tử Tế", "Đồ thanh lý được kiểm tra và mô tả minh bạch.", "SHOP"),
    ("mer-07", "Thợ Nhà Nhanh", "Sửa chữa điện nước và lắp đặt tại nhà.", "PROVIDER"),
    ("mer-08", "Sạch Mỗi Ngày", "Dịch vụ vệ sinh theo giờ và chuyên sâu.", "PROVIDER"),
    ("mer-09", "Bạn Thú Cưng", "Chăm sóc thú cưng tận tâm ngay tại nhà.", "PROVIDER"),
    ("mer-10", "Chạy Việc Grand Park", "Giao nhận và đi chợ hộ nhanh trong nội khu.", "PROVIDER"),
]

CATALOG = [
    ("prd-001-com-tam-suon-nuong", "Cơm tấm sườn nướng", "PRODUCT", "STANDARD", "prepared-meals", 65000),
    ("prd-002-bun-bo-hue", "Bún bò Huế", "PRODUCT", "STANDARD", "prepared-meals", 60000),
    ("prd-003-goi-cuon-tom-thit", "Gỏi cuốn tôm thịt", "PRODUCT", "STANDARD", "prepared-meals", 45000),
    ("prd-004-banh-mi-thit-nuong", "Bánh mì thịt nướng", "PRODUCT", "STANDARD", "prepared-meals", 35000),
    ("prd-005-cha-gio-viet", "Chả giò Việt", "PRODUCT", "STANDARD", "prepared-meals", 55000),
    ("prd-006-rau-muong", "Rau muống", "PRODUCT", "STANDARD", "fresh-produce", 18000),
    ("prd-007-cai-thia", "Cải thìa", "PRODUCT", "STANDARD", "fresh-produce", 22000),
    ("prd-008-ca-chua", "Cà chua", "PRODUCT", "STANDARD", "fresh-produce", 28000),
    ("prd-009-thanh-long-ruot-do", "Thanh long ruột đỏ", "PRODUCT", "STANDARD", "fresh-produce", 42000),
    ("prd-010-trung-ga-hop", "Trứng gà hộp", "PRODUCT", "STANDARD", "fresh-produce", 38000),
    ("prd-011-banh-mi-bo-toi", "Bánh mì bơ tỏi", "PRODUCT", "STANDARD", "bakery-drinks", 32000),
    ("prd-012-banh-flan", "Bánh flan", "PRODUCT", "STANDARD", "bakery-drinks", 25000),
    ("prd-013-banh-su-kem", "Bánh su kem", "PRODUCT", "STANDARD", "bakery-drinks", 40000),
    ("prd-014-ca-phe-sua-da", "Cà phê sữa đá", "PRODUCT", "STANDARD", "bakery-drinks", 29000),
    ("prd-015-tra-dao-cam-sa", "Trà đào cam sả", "PRODUCT", "STANDARD", "bakery-drinks", 39000),
    ("prd-016-nuoc-rua-chen-refill-generic", "Nước rửa chén refill", "PRODUCT", "STANDARD", "household-goods", 45000),
    ("prd-017-khan-giay-hop-generic", "Khăn giấy hộp", "PRODUCT", "STANDARD", "household-goods", 27000),
    ("prd-018-tui-rac-cuon", "Túi rác cuộn", "PRODUCT", "STANDARD", "household-goods", 30000),
    ("prd-019-hop-dung-thuc-pham", "Hộp đựng thực phẩm", "PRODUCT", "STANDARD", "household-goods", 69000),
    ("prd-020-binh-giu-nhiet", "Bình giữ nhiệt", "PRODUCT", "STANDARD", "household-goods", 159000),
    ("rsl-001-ghe-an-go", "Ghế ăn gỗ", "PRODUCT", "RESALE", "secondhand-home", 350000),
    ("rsl-002-ke-sach-go-nho", "Kệ sách gỗ nhỏ", "PRODUCT", "RESALE", "secondhand-home", 420000),
    ("rsl-003-ban-lam-viec-compact", "Bàn làm việc compact", "PRODUCT", "RESALE", "secondhand-home", 650000),
    ("rsl-004-ghe-van-phong", "Ghế văn phòng", "PRODUCT", "RESALE", "secondhand-home", 780000),
    ("rsl-005-ke-giay", "Kệ giày", "PRODUCT", "RESALE", "secondhand-home", 290000),
    ("rsl-006-noi-com-dien-generic", "Nồi cơm điện", "PRODUCT", "RESALE", "secondhand-appliances", 480000),
    ("rsl-007-quat-dung", "Quạt đứng", "PRODUCT", "RESALE", "secondhand-appliances", 320000),
    ("rsl-008-xe-dap-thanh-pho", "Xe đạp thành phố", "PRODUCT", "RESALE", "secondhand-appliances", 1850000),
    ("rsl-009-may-xay-sinh-to", "Máy xay sinh tố", "PRODUCT", "RESALE", "secondhand-appliances", 260000),
    ("rsl-010-den-ban", "Đèn bàn", "PRODUCT", "RESALE", "secondhand-appliances", 180000),
    ("svc-001-sua-voi-nuoc-ro-ri", "Sửa vòi nước rò rỉ", "SERVICE", "STANDARD", "home-repair", 180000),
    ("svc-002-thay-o-cam-cong-tac", "Thay ổ cắm công tắc", "SERVICE", "STANDARD", "home-repair", 150000),
    ("svc-003-lap-quat-tran", "Lắp quạt trần", "SERVICE", "STANDARD", "home-repair", 350000),
    ("svc-004-sua-khoa-cua", "Sửa khóa cửa", "SERVICE", "STANDARD", "home-repair", 220000),
    ("svc-005-don-can-ho-theo-gio", "Dọn căn hộ theo giờ", "SERVICE", "STANDARD", "cleaning", 120000),
    ("svc-006-ve-sinh-bep-chuyen-sau", "Vệ sinh bếp chuyên sâu", "SERVICE", "STANDARD", "cleaning", 420000),
    ("svc-007-ve-sinh-sofa", "Vệ sinh sofa", "SERVICE", "STANDARD", "cleaning", 380000),
    ("svc-008-giat-ui-nhan-tra", "Giặt ủi nhận trả", "SERVICE", "STANDARD", "cleaning", 90000),
    ("svc-009-grooming-thu-cung-tai-nha", "Grooming thú cưng tại nhà", "SERVICE", "STANDARD", "pet-care", 280000),
    ("svc-010-trong-thu-cung-theo-gio", "Trông thú cưng theo giờ", "SERVICE", "STANDARD", "pet-care", 100000),
    ("svc-011-dat-cho-di-dao", "Dắt chó đi dạo", "SERVICE", "STANDARD", "pet-care", 80000),
    ("svc-012-ve-sinh-khu-vuc-thu-cung", "Vệ sinh khu vực thú cưng", "SERVICE", "STANDARD", "pet-care", 160000),
    ("svc-013-giao-kien-hang-noi-khu", "Giao kiện hàng nội khu", "SERVICE", "STANDARD", "local-errands", 45000),
    ("svc-014-di-cho-ho", "Đi chợ hộ", "SERVICE", "STANDARD", "local-errands", 60000),
    ("svc-015-giao-tai-lieu-do-nho", "Giao tài liệu đồ nhỏ", "SERVICE", "STANDARD", "local-errands", 40000),
]


class Raw:
    def __init__(self, value: str) -> None:
        self.value = value


def stable_id(key: str) -> str:
    return str(uuid.uuid5(NAMESPACE, key))


def sql_value(value: object) -> str:
    if isinstance(value, Raw):
        return value.value
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def insert(table: str, columns: list[str], rows: list[tuple], conflict: str = "on conflict do nothing") -> str:
    values = ",\n".join("(" + ",".join(sql_value(value) for value in row) + ")" for row in rows)
    return f"insert into {table}({','.join(columns)}) values\n{values}\n{conflict};"


def ref_id(table: str, slug: str) -> Raw:
    return Raw(f"(select id from {table} where slug={sql_value(slug)} and status='ACTIVE' order by id limit 1)")


def json_raw(value: object) -> Raw:
    return Raw(sql_value(json.dumps(value, ensure_ascii=False, separators=(",", ":"))) + "::jsonb")


def render_sql() -> str:
    now = "2026-09-01 08:00:00+07"
    later = "2030-01-01 00:00:00+07"
    out = [
        r"\set ON_ERROR_STOP on",
        "begin;",
        "do $$ begin if current_setting('covie.demo.allow_seed', true) is distinct from 'true' then raise exception 'CoVie demo seed blocked: covie.demo.allow_seed=true is required'; end if; end $$;",
        "-- Generated by infra/scripts/generate-demo-seed.py. Do not edit the gzip snapshot directly.",
        f"-- Shared local credential for reserved demo identities: {PASSWORD}",
    ]

    area_rows = [
        ("vinhomes-grand-park", None, "COMMUNITY", "Vinhomes Grand Park", 10),
        ("the-rainbow", "vinhomes-grand-park", "ZONE", "The Rainbow", 10),
        ("the-origami", "vinhomes-grand-park", "ZONE", "The Origami", 20),
        ("the-beverly", "vinhomes-grand-park", "ZONE", "The Beverly", 30),
    ]
    for slug, parent, area_type, name, order in area_rows:
        parent_id = "null" if parent is None else f"(select id from location_areas where slug={sql_value(parent)} and status='ACTIVE' order by id limit 1)"
        out.append(
            "insert into location_areas(id,parent_id,area_type,name,slug,status,sort_order,created_at,updated_at,version) "
            f"select '{stable_id('area:'+slug)}'::uuid,{parent_id},{sql_value(area_type)},{sql_value(name)},{sql_value(slug)},'ACTIVE',{order},{sql_value(now)},{sql_value(now)},0 "
            f"where not exists(select 1 from location_areas where slug={sql_value(slug)} and status='ACTIVE');"
        )

    categories = [
        ("food-drink", None, "Ăn uống", "PRODUCT", 10),
        ("prepared-meals", "food-drink", "Món ăn chế biến", "PRODUCT", 10),
        ("bakery-drinks", "food-drink", "Bánh & đồ uống", "PRODUCT", 20),
        ("grocery", None, "Tạp hóa", "PRODUCT", 20),
        ("fresh-produce", "grocery", "Rau củ & thực phẩm tươi", "PRODUCT", 10),
        ("home-personal", None, "Nhà cửa & cá nhân", "PRODUCT", 30),
        ("household-goods", "home-personal", "Đồ tiện ích gia đình", "PRODUCT", 10),
        ("secondhand-home", "home-personal", "Nội thất đã qua sử dụng", "PRODUCT", 20),
        ("secondhand-appliances", "home-personal", "Đồ gia dụng đã qua sử dụng", "PRODUCT", 30),
        ("services", None, "Dịch vụ", "SERVICE", 40),
        ("cleaning", "services", "Vệ sinh", "SERVICE", 10),
        ("home-repair", "services", "Sửa chữa gia đình", "SERVICE", 20),
        ("pet-care", "services", "Chăm sóc thú cưng", "SERVICE", 30),
        ("local-errands", "services", "Giao nhận & đi chợ hộ", "SERVICE", 40),
        ("other-services", "services", "Dịch vụ khác", "SERVICE", 90),
    ]
    for slug, parent, name, kind, order in categories:
        parent_id = "null" if parent is None else f"(select id from categories where slug={sql_value(parent)} and status='ACTIVE' order by id limit 1)"
        out.append(
            "insert into categories(id,parent_id,name,slug,catalog_type,status,sort_order,version) "
            f"select '{stable_id('category:'+slug)}'::uuid,{parent_id},{sql_value(name)},{sql_value(slug)},{sql_value(kind)},'ACTIVE',{order},0 "
            f"where not exists(select 1 from categories where slug={sql_value(slug)} and status='ACTIVE');"
        )

    user_rows = []
    identity_rows = []
    role_rows = []
    for key, name in USERS:
        user_id = stable_id("user:" + key)
        user_rows.append((user_id, name, "ACTIVE", now, now, 0))
        identity_rows.append((stable_id("identity:" + key), user_id, "EMAIL", key + "@demo.covie.invalid", PASSWORD_HASH, now, now, now))
        role_rows.append((stable_id("role:user:" + key), user_id, "USER", now))
        if key.startswith("admin-"):
            role_rows.append((stable_id("role:admin:" + key), user_id, "ADMIN", now))
    out.append(insert("users", ["id", "display_name", "account_status", "created_at", "updated_at", "version"], user_rows, "on conflict(id) do update set display_name=excluded.display_name,account_status='ACTIVE',updated_at=excluded.updated_at"))
    out.append(insert("user_identities", ["id", "user_id", "provider", "email_normalized", "password_hash", "verified_at", "created_at", "last_used_at"], identity_rows, "on conflict(id) do update set email_normalized=excluded.email_normalized,password_hash=excluded.password_hash,verified_at=excluded.verified_at,last_used_at=excluded.last_used_at"))
    out.append(insert("system_role_assignments", ["id", "user_id", "role", "created_at"], role_rows))

    address_rows = []
    for index in range(8):
        key = f"buyer-{index+1:02d}"
        address_rows.append((stable_id("address:" + key), stable_id("user:" + key), "Nhà", USERS[index][1], f"090900{index+1:04d}", f"Tòa S{index+1}.0{index+1}, Vinhomes Grand Park", ref_id("location_areas", ["the-rainbow", "the-origami", "the-beverly"][index % 3]), Raw(f"ST_SetSRID(ST_MakePoint({106.838 + index * .001},{10.838 + index * .001}),4326)::geography"), "USER_ADJUSTED", "Giao tại sảnh", True, "ACTIVE", now, now, 0))
    out.append(insert("user_addresses", ["id", "user_id", "label", "recipient_name", "phone", "address_text", "location_area_id", "geo_point", "pin_source", "note", "is_default", "status", "created_at", "updated_at", "version"], address_rows, "on conflict(id) do update set address_text=excluded.address_text,status='ACTIVE',updated_at=excluded.updated_at"))

    merchant_rows = []
    membership_rows = []
    location_rows = []
    member_location_rows = []
    contact_rows = []
    hour_rows = []
    coverage_rows = []
    policy_rows = []
    rule_rows = []
    for index, (key, name, description, merchant_kind) in enumerate(MERCHANTS):
        merchant_id = stable_id("merchant:" + key)
        owner_id = stable_id(f"user:seller-{index+1:02d}")
        membership_id = stable_id("membership:" + key + ":owner")
        location_id = stable_id("location:" + key)
        merchant_rows.append((merchant_id, name, "demo-" + key, description, "ACTIVE", "APPROVED", merchant_kind, now, now, 0))
        membership_rows.append((membership_id, merchant_id, owner_id, "OWNER", "ACTIVE", now, now, 0))
        location_rows.append((location_id, merchant_id, name + " Grand Park", f"Khu {['Rainbow','Origami','Beverly'][index%3]}, Vinhomes Grand Park", ref_id("location_areas", ["the-rainbow", "the-origami", "the-beverly"][index % 3]), Raw(f"ST_SetSRID(ST_MakePoint({106.838 + index * .001},{10.838 + index * .001}),4326)::geography"), "USER_ADJUSTED", "Asia/Ho_Chi_Minh", "ACTIVE", merchant_kind == "SHOP", merchant_kind == "SHOP", merchant_kind == "PROVIDER", 50000 if merchant_kind == "SHOP" else None, "VND", now, now, 0))
        member_location_rows.append((stable_id("member-location:" + key + ":owner"), membership_id, location_id, now))
        contact_rows.append((stable_id("contact:" + key + ":phone"), merchant_id, location_id, "PHONE", f"0908{index+1:06d}", True, True, "ACTIVE", now, 0))
        for day in range(1, 8):
            operation = "SHOP" if merchant_kind == "SHOP" else "DELIVERY"
            hour_rows.append((stable_id(f"hours:{key}:{day}"), location_id, day, operation, "08:00", "20:30", False, 0))
        coverage_id = stable_id("coverage:" + key)
        coverage_rows.append((coverage_id, location_id, "PRODUCT_DELIVERY" if merchant_kind == "SHOP" else "SERVICE_AREA", True, 5000, False, "ACTIVE", now, now, 0))
        policy_id = stable_id("shipping:" + key)
        policy_rows.append((policy_id, location_id, "CONDITIONAL" if merchant_kind == "SHOP" else "FREE", 15000 if merchant_kind == "SHOP" else None, "VND", "ANY" if merchant_kind == "SHOP" else None, "ACTIVE", now, now, 0))
        if merchant_kind == "SHOP":
            rule_rows.append((stable_id("shipping-rule:" + key), policy_id, "ORDER_AMOUNT_MIN", "GTE", 150000, "FREE_SHIPPING", 10, True))
    # Two staff accounts enrich membership/authorization screens without creating extra merchants.
    for index, staff_key in enumerate(("staff-01", "staff-02")):
        key = f"mer-{index+1:02d}"
        membership_id = stable_id("membership:" + key + ":" + staff_key)
        location_id = stable_id("location:" + key)
        membership_rows.append((membership_id, stable_id("merchant:" + key), stable_id("user:" + staff_key), "STAFF", "ACTIVE", now, now, 0))
        member_location_rows.append((stable_id("member-location:" + key + ":" + staff_key), membership_id, location_id, now))
    out.append(insert("merchant_profiles", ["id", "name", "slug", "description", "merchant_status", "moderation_status", "merchant_kind", "created_at", "updated_at", "version"], merchant_rows, "on conflict(id) do update set name=excluded.name,description=excluded.description,merchant_status='ACTIVE',moderation_status='APPROVED',merchant_kind=excluded.merchant_kind,updated_at=excluded.updated_at"))
    out.append(insert("merchant_memberships", ["id", "merchant_profile_id", "user_id", "role", "membership_status", "joined_at", "created_at", "version"], membership_rows, "on conflict(id) do update set membership_status='ACTIVE'"))
    out.append(insert("merchant_locations", ["id", "merchant_profile_id", "name", "address_text", "location_area_id", "geo_point", "pin_source", "timezone", "location_status", "pickup_enabled", "seller_delivery_enabled", "service_enabled", "minimum_delivery_order", "currency_code", "created_at", "updated_at", "version"], location_rows, "on conflict(id) do update set name=excluded.name,address_text=excluded.address_text,location_status='ACTIVE',updated_at=excluded.updated_at"))
    out.append(insert("merchant_member_locations", ["id", "membership_id", "merchant_location_id", "created_at"], member_location_rows))
    out.append(insert("merchant_contact_methods", ["id", "merchant_profile_id", "merchant_location_id", "contact_type", "value", "is_primary", "is_public", "status", "created_at", "version"], contact_rows, "on conflict(id) do update set value=excluded.value,status='ACTIVE',is_public=true"))
    out.append(insert("merchant_location_hours", ["id", "merchant_location_id", "day_of_week", "operation_type", "open_time", "close_time", "is_closed", "sort_order"], hour_rows))
    out.append(insert("delivery_coverages", ["id", "merchant_location_id", "coverage_type", "radius_enabled", "radius_meters", "area_enabled", "coverage_status", "created_at", "updated_at", "version"], coverage_rows))
    out.append(insert("shipping_policies", ["id", "merchant_location_id", "mode", "base_fee", "currency_code", "rule_match_mode", "policy_status", "created_at", "updated_at", "version"], policy_rows))
    out.append(insert("shipping_rules", ["id", "shipping_policy_id", "condition_type", "operator", "threshold_value", "action_type", "priority", "enabled"], rule_rows))

    item_rows = []
    variant_rows = []
    offer_rows = []
    item_meta: list[dict[str, object]] = []
    variant_meta: list[dict[str, object]] = []
    offer_meta: list[dict[str, object]] = []
    for index, (slug, name, item_type, listing_kind, category, price) in enumerate(CATALOG):
        if item_type == "SERVICE":
            merchant_index = 6 + ((index - 30) % 4)
        else:
            merchant_index = index % 6
        merchant_key = f"mer-{merchant_index+1:02d}"
        item_id = stable_id("catalog:" + slug)
        description = f"{name} — dữ liệu demo CoVie, thông tin rõ ràng cho cư dân Grand Park."
        item_rows.append((item_id, stable_id("merchant:" + merchant_key), item_type, listing_kind, ref_id("categories", category), name, slug, description, "ACTIVE", "APPROVED", Raw(f"to_tsvector('simple',unaccent({sql_value(name + ' ' + description)}))"), now, now, 0))
        item_meta.append({"id": item_id, "slug": slug, "name": name, "type": item_type, "merchant": merchant_key, "price": price})
        count = 2 if index < 15 else 1
        for variant_index in range(count):
            variant_id = stable_id(f"variant:{slug}:{variant_index+1}")
            variant_name = "Mặc định" if variant_index == 0 else ("Phần lớn" if item_type == "PRODUCT" else "Gói mở rộng")
            variant_rows.append((variant_id, item_id, f"DEMO-{index+1:03d}-{variant_index+1}", variant_name, variant_index == 0, "ACTIVE", variant_index, now, now, 0))
            variant_meta.append({"id": variant_id, "item": item_id, "item_index": index, "variant_index": variant_index, "type": item_type, "merchant": merchant_key, "name": variant_name, "price": price + variant_index * 20000})
    out.append(insert("catalog_items", ["id", "merchant_profile_id", "item_type", "listing_kind", "primary_category_id", "name", "slug", "description", "listing_status", "moderation_status", "search_vector", "created_at", "updated_at", "version"], item_rows, "on conflict(id) do update set name=excluded.name,description=excluded.description,listing_status='ACTIVE',moderation_status='APPROVED',search_vector=excluded.search_vector,updated_at=excluded.updated_at"))
    out.append(insert("variants", ["id", "catalog_item_id", "sku", "variant_name", "is_default", "variant_status", "sort_order", "created_at", "updated_at", "version"], variant_rows, "on conflict(id) do update set variant_name=excluded.variant_name,variant_status='ACTIVE',updated_at=excluded.updated_at"))

    # Every default variant plus the first ten secondary variants is offered: 45 + 10 = 55.
    for meta in variant_meta:
        if meta["variant_index"] == 1 and meta["item_index"] >= 10:
            continue
        offer_id = stable_id("offer:" + str(meta["id"]))
        tracked = len(offer_meta) < 18 and meta["type"] == "PRODUCT"
        inventory_mode = "TRACKED" if tracked else ("UNTRACKED" if meta["type"] == "PRODUCT" else None)
        offer_rows.append((offer_id, stable_id("location:" + str(meta["merchant"])), meta["id"], meta["price"], "VND", True, inventory_mode, 25 if meta["type"] == "PRODUCT" else 60, now, now, 0))
        offer_meta.append({**meta, "offer_id": offer_id, "tracked": tracked})
    out.append(insert("location_variants", ["id", "merchant_location_id", "variant_id", "price", "currency_code", "is_available", "inventory_mode", "preparation_time_minutes", "created_at", "updated_at", "version"], offer_rows, "on conflict(id) do update set price=excluded.price,is_available=true,inventory_mode=excluded.inventory_mode,updated_at=excluded.updated_at"))
    inventory_rows = [(meta["offer_id"], 40 + index * 2, index % 3, now, 0) for index, meta in enumerate(offer_meta) if meta["tracked"]]
    out.append(insert("inventory_balances", ["location_variant_id", "stock_on_hand", "reserved_quantity", "updated_at", "version"], inventory_rows, "on conflict(location_variant_id) do update set stock_on_hand=excluded.stock_on_hand,reserved_quantity=excluded.reserved_quantity,updated_at=excluded.updated_at"))

    offers_by_merchant: dict[str, list[dict[str, object]]] = {}
    for meta in offer_meta:
        offers_by_merchant.setdefault(str(meta["merchant"]), []).append(meta)
    cart_rows = []
    cart_item_rows = []
    for index in range(11):
        merchant_key = f"mer-{index % 10 + 1:02d}"
        buyer_key = f"buyer-{index % 8 + 1:02d}"
        choices = offers_by_merchant[merchant_key]
        cart_id = stable_id(f"cart:{index+1}")
        cart_type = str(choices[0]["type"])
        cart_status = "ACTIVE" if index < 8 else ("CHECKED_OUT" if index < 10 else "ABANDONED")
        cart_rows.append((cart_id, stable_id("user:" + buyer_key), stable_id("location:" + merchant_key), cart_type, cart_status, now, now, 0))
        item_count = 3 if index < 3 else 2
        for offset in range(item_count):
            choice = choices[offset % len(choices)]
            cart_item_rows.append((stable_id(f"cart-item:{index+1}:{offset+1}"), cart_id, choice["offer_id"], choice["id"], 1 + (offset % 2), choice["price"], "VND", next(item["name"] for item in item_meta if item["id"] == choice["item"]), json_raw({"variantName": choice["name"]}), now, now))
    out.append(insert("carts", ["id", "user_id", "merchant_location_id", "cart_type", "cart_status", "created_at", "updated_at", "version"], cart_rows, "on conflict(id) do update set cart_status=excluded.cart_status,updated_at=excluded.updated_at"))
    out.append(insert("cart_items", ["id", "cart_id", "location_variant_id", "variant_id", "quantity", "price_snapshot", "currency_code", "item_name_snapshot", "variant_snapshot", "added_at", "updated_at"], cart_item_rows, "on conflict(id) do update set quantity=excluded.quantity,updated_at=excluded.updated_at"))
    checkout_rows = []
    for index in range(11):
        buyer_key = f"buyer-{index % 8 + 1:02d}"
        checkout_rows.append((stable_id(f"checkout:{index+1}"), (uuid.uuid5(NAMESPACE, f"checkout-token:{index+1}").hex * 2), stable_id("user:" + buyer_key), "CART", json_raw({"cartId": stable_id(f"cart:{index+1}")}), 0, json_raw({"fulfillmentMethod": "PICKUP"}), json_raw({"currencyCode": "VND", "valid": True}), later, None, now))
    out.append(insert("checkout_sessions", ["id", "token_hash", "user_id", "source_type", "source_context", "cart_version", "request_snapshot", "preview_snapshot", "expires_at", "consumed_at", "created_at"], checkout_rows, "on conflict(id) do update set expires_at=excluded.expires_at,consumed_at=null"))

    order_rows = []
    order_item_rows = []
    product_fulfillment_rows = []
    service_fulfillment_rows = []
    order_event_rows = []
    order_meta = []
    statuses = ["COMPLETED"] * 18 + ["PENDING_CONFIRMATION", "CONFIRMED", "OUT_FOR_DELIVERY", "READY_FOR_PICKUP", "AWAITING_BUYER_CONFIRMATION", "CANCELLED_BEFORE_CONFIRMATION", "REJECTED_BY_SELLER", "CLOSED_WITH_ISSUE"]
    for index in range(26):
        choice = offer_meta[(index * 7) % len(offer_meta)]
        item = next(item for item in item_meta if item["id"] == choice["item"])
        buyer_key = f"buyer-{index % 8 + 1:02d}"
        order_id = stable_id(f"order:{index+1}")
        order_item_id = stable_id(f"order-item:{index+1}")
        subtotal = int(choice["price"])
        is_service = choice["type"] == "SERVICE"
        shipping_type = "NOT_APPLICABLE" if is_service else "FIXED"
        shipping_fee = None if is_service else 15000
        total = subtotal if is_service else subtotal + 15000
        status = statuses[index]
        completed_at = now if status == "COMPLETED" else None
        closed_at = now if status in {"COMPLETED", "CANCELLED_BEFORE_CONFIRMATION", "REJECTED_BY_SELLER", "CLOSED_WITH_ISSUE"} else None
        order_rows.append((order_id, f"CV-DEMO-{index+1:04d}", choice["type"], stable_id("user:" + buyer_key), stable_id("merchant:" + str(choice["merchant"])), stable_id("location:" + str(choice["merchant"])), status, "MARKETPLACE", None, None, "COD", subtotal, shipping_type, shipping_fee, total, total, "VND", later if status == "PENDING_CONFIRMATION" else None, now if status != "PENDING_CONFIRMATION" else None, completed_at, closed_at, now, now, 0))
        order_item_rows.append((order_item_id, order_id, choice["item"], choice["id"], choice["offer_id"], choice["type"], 1, choice["price"], choice["price"], "VND", json_raw({"name": item["name"], "variantName": choice["name"], "slug": item["slug"]}), now))
        snapshot = json_raw({"name": str(MERCHANTS[int(str(choice["merchant"])[4:]) - 1][1]), "address": "Vinhomes Grand Park"})
        contact = json_raw({"phone": "0908000001"})
        if is_service:
            service_fulfillment_rows.append((order_id, stable_id("address:" + buyer_key), json_raw({"address": "Vinhomes Grand Park"}), snapshot, contact, contact, now if status == "COMPLETED" else None, None, now))
        else:
            product_fulfillment_rows.append((order_id, "PICKUP", stable_id("location:" + str(choice["merchant"])), None, None, snapshot, None, contact, contact, json_raw({"mode": "FIXED", "fee": 15000}), now if status in {"COMPLETED", "READY_FOR_PICKUP"} else None, None, None, None, None, None))
        order_event_rows.append((stable_id(f"order-event:{index+1}"), order_id, "ORDER_CREATED", stable_id("user:" + buyer_key), "BUYER", None, status, None, None, json_raw({"demo": True}), now))
        order_meta.append({"id": order_id, "item_id": order_item_id, "buyer": buyer_key, "choice": choice, "item": item})
    out.append(insert("orders", ["id", "order_code", "order_type", "buyer_user_id", "merchant_profile_id", "merchant_location_id", "status", "source_type", "source_need_request_id", "source_need_response_item_id", "payment_method", "subtotal", "shipping_fee_type", "shipping_fee", "known_total", "total_amount", "currency_code", "confirmation_expires_at", "confirmed_at", "completed_at", "closed_at", "created_at", "updated_at", "version"], order_rows, "on conflict(id) do update set status=excluded.status,updated_at=excluded.updated_at"))
    out.append(insert("order_items", ["id", "order_id", "catalog_item_id", "variant_id", "location_variant_id", "item_type", "quantity", "unit_price", "line_total", "currency_code", "item_snapshot", "created_at"], order_item_rows))
    if product_fulfillment_rows:
        out.append(insert("product_fulfillments", ["order_id", "fulfillment_method", "pickup_location_id", "buyer_address_id", "distance_meters", "pickup_location_snapshot", "delivery_address_snapshot", "buyer_contact_snapshot", "merchant_contact_snapshot", "shipping_policy_snapshot", "seller_marked_ready_at", "seller_marked_delivered_at", "awaiting_buyer_confirmation_at", "out_for_delivery_at", "auto_complete_at", "no_show_eligible_at"], product_fulfillment_rows))
    if service_fulfillment_rows:
        out.append(insert("service_fulfillments", ["order_id", "service_address_id", "service_address_snapshot", "provider_location_snapshot", "buyer_contact_snapshot", "merchant_contact_snapshot", "provider_marked_performed_at", "awaiting_buyer_confirmation_at", "created_at"], service_fulfillment_rows))
    out.append(insert("order_events", ["id", "order_id", "event_type", "actor_user_id", "actor_context", "from_status", "to_status", "reason_code", "note", "metadata", "created_at"], order_event_rows))

    need_rows = []
    response_rows = []
    response_item_rows = []
    need_titles = [
        ("Cần rau củ giao trong ngày", "PRODUCT", "fresh-produce"),
        ("Tìm bữa tối cho gia đình", "PRODUCT", "prepared-meals"),
        ("Cần mua kệ sách nhỏ", "PRODUCT", "secondhand-home"),
        ("Cần thợ sửa vòi nước", "SERVICE", "home-repair"),
        ("Tìm dịch vụ vệ sinh sofa", "SERVICE", "cleaning"),
    ]
    for index, (title, need_type, category) in enumerate(need_titles):
        buyer_key = f"buyer-{index+1:02d}"
        need_id = stable_id(f"need:{index+1}")
        need_rows.append((need_id, stable_id("user:" + buyer_key), need_type, ref_id("categories", category), title, title.lower(), "Ưu tiên người bán gần và có thể phản hồi trong hôm nay.", 1 if need_type == "PRODUCT" else None, ref_id("location_areas", ["the-rainbow", "the-origami", "the-beverly"][index % 3]), Raw(f"ST_SetSRID(ST_MakePoint({106.84 + index * .001},{10.84 + index * .001}),4326)::geography"), json_raw({"area": "Vinhomes Grand Park"}), 5000, "ACTIVE", later, None, now, now, None, "APPROVED"))
        compatible = [offer for offer in offer_meta if offer["type"] == need_type]
        for response_index in range(2):
            first = compatible[(index * 4 + response_index * 2) % len(compatible)]
            response_id = stable_id(f"need-response:{index+1}:{response_index+1}")
            response_rows.append((response_id, need_id, stable_id("merchant:" + str(first["merchant"])), stable_id("location:" + str(first["merchant"])), "ACTIVE", "Có thể đáp ứng và giao trong thời gian yêu cầu.", stable_id("user:seller-" + str(first["merchant"])[4:]), now, now, None, 0))
            same_merchant = offers_by_merchant[str(first["merchant"])]
            same_type = [offer for offer in same_merchant if offer["type"] == need_type]
            for response_item_index in range(2):
                choice = same_type[response_item_index % len(same_type)]
                response_item_rows.append((stable_id(f"need-response-item:{index+1}:{response_index+1}:{response_item_index+1}"), response_id, choice["item"], choice["id"], choice["offer_id"], "ACTIVE", choice["price"], "VND", json_raw({"available": True}), now, None))
    out.append(insert("need_requests", ["id", "buyer_user_id", "need_type", "category_id", "title", "query_text", "description", "requested_quantity", "location_area_id", "geo_point", "location_snapshot", "search_radius_meters", "request_status", "expires_at", "fulfilled_order_id", "created_at", "updated_at", "closed_at", "moderation_status"], need_rows, "on conflict(id) do update set request_status='ACTIVE',expires_at=excluded.expires_at,updated_at=excluded.updated_at,closed_at=null"))
    out.append(insert("need_responses", ["id", "need_request_id", "merchant_profile_id", "merchant_location_id", "response_status", "note", "created_by_user_id", "created_at", "updated_at", "withdrawn_at", "version"], response_rows, "on conflict(id) do update set response_status='ACTIVE',updated_at=excluded.updated_at,withdrawn_at=null"))
    out.append(insert("need_response_items", ["id", "need_response_id", "catalog_item_id", "variant_id", "location_variant_id", "item_status", "price_snapshot", "currency_code", "availability_snapshot", "created_at", "retired_at"], response_item_rows, "on conflict(id) do update set item_status='ACTIVE',retired_at=null"))

    review_rows = []
    for index, order in enumerate(order_meta[:18]):
        choice = order["choice"]
        review_rows.append((stable_id(f"review:{index+1}"), order["item_id"], order["id"], stable_id("user:" + str(order["buyer"])), choice["item"], choice["id"], stable_id("merchant:" + str(choice["merchant"])), 4 + (index % 2), "Phản hồi demo: đúng mô tả, liên hệ thuận tiện và phục vụ tốt.", "ACTIVE", now, now))
    out.append(insert("reviews", ["id", "order_item_id", "order_id", "buyer_user_id", "catalog_item_id", "variant_id", "merchant_profile_id", "rating", "comment", "review_status", "created_at", "updated_at"], review_rows, "on conflict(id) do update set rating=excluded.rating,comment=excluded.comment,review_status='ACTIVE',updated_at=excluded.updated_at"))
    out.append("insert into catalog_item_rating_stats(catalog_item_id,review_count,rating_sum,average_rating,updated_at) select catalog_item_id,count(*),sum(rating),round(avg(rating),2),max(updated_at) from reviews where id in (" + ",".join(sql_value(row[0]) + "::uuid" for row in review_rows) + ") group by catalog_item_id on conflict(catalog_item_id) do update set review_count=excluded.review_count,rating_sum=excluded.rating_sum,average_rating=excluded.average_rating,updated_at=excluded.updated_at;")
    out.append("insert into merchant_rating_stats(merchant_profile_id,review_count,rating_sum,average_rating,updated_at) select merchant_profile_id,count(*),sum(rating),round(avg(rating),2),max(updated_at) from reviews where id in (" + ",".join(sql_value(row[0]) + "::uuid" for row in review_rows) + ") group by merchant_profile_id on conflict(merchant_profile_id) do update set review_count=excluded.review_count,rating_sum=excluded.rating_sum,average_rating=excluded.average_rating,updated_at=excluded.updated_at;")

    notification_rows = []
    for index in range(24):
        buyer_key = f"buyer-{index % 8 + 1:02d}"
        related = order_meta[index % len(order_meta)]["id"]
        notification_rows.append((stable_id(f"notification:{index+1}"), stable_id("user:" + buyer_key), "ORDER_STATUS_CHANGED", f"Cập nhật đơn demo #{index+1:02d}", "Đơn hàng của bạn vừa có cập nhật mới.", "ORDER", related, json_raw({"orderId": related}), now if index % 3 == 0 else None, now, later))
    out.append(insert("notifications", ["id", "user_id", "notification_type", "title", "body", "related_entity_type", "related_entity_id", "payload", "read_at", "created_at", "expires_at"], notification_rows, "on conflict(id) do update set title=excluded.title,body=excluded.body,read_at=excluded.read_at"))

    moderation_rows = [
        ("USER_REPORT", "USER", "target_user_id", stable_id("user:buyer-08"), "OPEN"),
        ("USER_REPORT", "MERCHANT", "target_merchant_profile_id", stable_id("merchant:mer-06"), "OPEN"),
        ("PRE_APPROVAL", "CATALOG_ITEM", "target_catalog_item_id", item_meta[0]["id"], "RESOLVED"),
        ("PRE_APPROVAL", "NEED_REQUEST", "target_need_request_id", stable_id("need:1"), "RESOLVED"),
        ("SYSTEM_FLAG", "REVIEW", "target_review_id", stable_id("review:1"), "OPEN"),
        ("USER_REPORT", "CATALOG_ITEM", "target_catalog_item_id", item_meta[21]["id"], "CLOSED"),
        ("SYSTEM_FLAG", "MERCHANT", "target_merchant_profile_id", stable_id("merchant:mer-09"), "OPEN"),
    ]
    case_rows = []
    target_columns = ["target_user_id", "target_merchant_profile_id", "target_catalog_item_id", "target_need_request_id", "target_review_id"]
    for index, (case_type, target_type, target_column, target_id, status) in enumerate(moderation_rows):
        targets = {column: None for column in target_columns}
        targets[target_column] = target_id
        case_rows.append((stable_id(f"moderation:{index+1}"), case_type, stable_id("user:buyer-01") if case_type == "USER_REPORT" else None, target_type, *(targets[column] for column in target_columns), "DEMO_REVIEW", "Tình huống demo phục vụ kiểm tra quy trình quản trị.", status, stable_id("user:admin-01"), now, now if status == "RESOLVED" else None, now if status == "CLOSED" else None))
    out.append(insert("moderation_cases", ["id", "case_type", "reporter_user_id", "target_type", *target_columns, "reason_code", "description", "case_status", "assigned_admin_user_id", "opened_at", "resolved_at", "closed_at"], case_rows, "on conflict(id) do update set case_status=excluded.case_status,assigned_admin_user_id=excluded.assigned_admin_user_id"))

    out.extend([
        "-- Verify the snapshot shape inside the same transaction before commit.",
        "do $$ declare actual bigint; begin",
    ])
    checks = {
        "users": "select count(distinct user_id) from user_identities where provider='EMAIL' and email_normalized like '%@demo.covie.invalid'",
        "merchants": "select count(*) from merchant_profiles where slug like 'demo-mer-%'",
        "catalog_items": "select count(*) from catalog_items where merchant_profile_id in (select id from merchant_profiles where slug like 'demo-mer-%')",
        "variants": "select count(*) from variants where catalog_item_id in (select id from catalog_items where merchant_profile_id in (select id from merchant_profiles where slug like 'demo-mer-%'))",
        "location_variants": "select count(*) from location_variants where variant_id in (select id from variants where catalog_item_id in (select id from catalog_items where merchant_profile_id in (select id from merchant_profiles where slug like 'demo-mer-%')))",
        "inventory_balances": "select count(*) from inventory_balances where location_variant_id in (select id from location_variants where variant_id in (select id from variants where catalog_item_id in (select id from catalog_items where merchant_profile_id in (select id from merchant_profiles where slug like 'demo-mer-%'))))",
        "carts": "select count(*) from carts where user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid')",
        "cart_items": "select count(*) from cart_items where cart_id in (select id from carts where user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid'))",
        "checkout_sessions": "select count(*) from checkout_sessions where user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid')",
        "orders": "select count(*) from orders where buyer_user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid')",
        "active_need_requests": "select count(*) from need_requests where request_status='ACTIVE' and buyer_user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid')",
        "need_responses": "select count(*) from need_responses where need_request_id in (select id from need_requests where buyer_user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid'))",
        "need_response_items": "select count(*) from need_response_items where need_response_id in (select id from need_responses where need_request_id in (select id from need_requests where buyer_user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid'))) ",
        "reviews": "select count(*) from reviews where buyer_user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid')",
        "notifications": "select count(*) from notifications where user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid')",
        "moderation_cases": "select count(*) from moderation_cases where assigned_admin_user_id in (select user_id from user_identities where email_normalized like '%@demo.covie.invalid')",
    }
    for metric, expected in EXPECTED.items():
        out.append(f"{checks[metric]} into actual; if actual <> {expected} then raise exception 'demo seed count mismatch: {metric} expected {expected}, got %', actual; end if;")
    out.extend(["end $$;", "commit;", ""])
    return "\n".join(out)


def render_gzip() -> bytes:
    buffer = io.BytesIO()
    with gzip.GzipFile(filename="seed-demo-data-v1.sql", mode="wb", fileobj=buffer, mtime=0, compresslevel=9) as stream:
        stream.write(render_sql().encode("utf-8"))
    return buffer.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true", help="fail when the checked-in snapshot differs")
    args = parser.parse_args()
    generated = render_gzip()
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != generated:
            raise SystemExit(f"demo seed snapshot is stale: run {Path(__file__).name}")
        print(f"demo seed snapshot is current: {args.output}")
        return
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(generated)
    print(f"wrote {args.output} ({len(generated)} bytes gzip, {len(render_sql().encode('utf-8'))} bytes SQL)")


if __name__ == "__main__":
    main()
