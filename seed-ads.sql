-- Seed active ad campaigns for local development and demonstration in covie_demo_recovered
INSERT INTO ad_campaigns (
    id,
    merchant_id,
    owner_user_id,
    payload,
    placement,
    device,
    status,
    enabled,
    priority,
    starts_at,
    ends_at,
    version,
    created_at,
    updated_at,
    deleted_at
) VALUES 
-- 1. Sidebar Left (FLASH template)
(
    '77777777-1111-4777-8777-111111111111',
    NULL,
    'd0ebf9ca-aa5a-5c32-9f6a-a81d7279f0b3',
    '{"title":"Ưu đãi Chợ Cư Dân","body":"Giảm 20% đơn đầu tiên hôm nay","ctaLabel":"Săn ngay","targetUrl":"/search","creativeType":"CSS","template":"FLASH","mediaId":null,"placement":"SIDEBAR_LEFT","device":"ALL","startsAt":"2026-10-01T00:00:00Z","endsAt":"2027-10-01T00:00:00Z","enabled":true,"priority":100}'::jsonb,
    'SIDEBAR_LEFT',
    'ALL',
    'APPROVED',
    TRUE,
    100,
    '2026-10-01 00:00:00+00',
    '2027-10-01 00:00:00+00',
    0,
    NOW(),
    NOW(),
    NULL
),
-- 2. Sidebar Left 2 (NEARBY template for rotation demo)
(
    '77777777-1111-4777-8777-222222222222',
    NULL,
    'd0ebf9ca-aa5a-5c32-9f6a-a81d7279f0b3',
    '{"title":"Dịch vụ Quanh Tòa","body":"Thợ sửa, giao nhanh 15 phút","ctaLabel":"Gọi thợ","targetUrl":"/search","creativeType":"CSS","template":"NEARBY","mediaId":null,"placement":"SIDEBAR_LEFT","device":"ALL","startsAt":"2026-10-01T00:00:00Z","endsAt":"2027-10-01T00:00:00Z","enabled":true,"priority":90}'::jsonb,
    'SIDEBAR_LEFT',
    'ALL',
    'APPROVED',
    TRUE,
    90,
    '2026-10-01 00:00:00+00',
    '2027-10-01 00:00:00+00',
    0,
    NOW(),
    NOW(),
    NULL
),
-- 3. Home Hero (HERO template)
(
    '77777777-2222-4777-8777-333333333333',
    NULL,
    'd0ebf9ca-aa5a-5c32-9f6a-a81d7279f0b3',
    '{"title":"Chào mừng Cư dân CoVie","body":"Món ngon nóng hổi và đồ thanh lý giá hời từ hàng xóm","ctaLabel":"Khám phá ngay","targetUrl":"/search","creativeType":"CSS","template":"HERO","mediaId":null,"placement":"HOME_HERO","device":"ALL","startsAt":"2026-10-01T00:00:00Z","endsAt":"2027-10-01T00:00:00Z","enabled":true,"priority":100}'::jsonb,
    'HOME_HERO',
    'ALL',
    'APPROVED',
    TRUE,
    100,
    '2026-10-01 00:00:00+00',
    '2027-10-01 00:00:00+00',
    0,
    NOW(),
    NOW(),
    NULL
),
-- 4. In Feed (SPOTLIGHT template)
(
    '77777777-3333-4777-8777-444444444444',
    NULL,
    'd0ebf9ca-aa5a-5c32-9f6a-a81d7279f0b3',
    '{"title":"Gian Hàng Nổi Bật","body":"Bếp nhà cư dân được tin chọn nhiều nhất tuần qua","ctaLabel":"Xem gian hàng","targetUrl":"/search","creativeType":"CSS","template":"SPOTLIGHT","mediaId":null,"placement":"IN_FEED","device":"ALL","startsAt":"2026-10-01T00:00:00Z","endsAt":"2027-10-01T00:00:00Z","enabled":true,"priority":100}'::jsonb,
    'IN_FEED',
    'ALL',
    'APPROVED',
    TRUE,
    100,
    '2026-10-01 00:00:00+00',
    '2027-10-01 00:00:00+00',
    0,
    NOW(),
    NOW(),
    NULL
)
ON CONFLICT (id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    payload = EXCLUDED.payload,
    status = EXCLUDED.status,
    enabled = EXCLUDED.enabled,
    starts_at = EXCLUDED.starts_at,
    ends_at = EXCLUDED.ends_at,
    updated_at = NOW(),
    deleted_at = NULL;
