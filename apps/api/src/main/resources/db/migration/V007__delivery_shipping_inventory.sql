create table delivery_coverages (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    coverage_type varchar(30) not null,
    radius_enabled boolean not null default false,
    radius_meters integer null,
    area_enabled boolean not null default false,
    coverage_status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_delivery_coverage_type check (coverage_type in ('PRODUCT_DELIVERY','SERVICE_AREA')),
    constraint ck_delivery_coverage_status check (coverage_status in ('ACTIVE','INACTIVE')),
    constraint ck_delivery_coverage_modes check (radius_enabled or area_enabled),
    constraint ck_delivery_coverage_radius check (
        (not radius_enabled and radius_meters is null)
        or (radius_enabled and radius_meters is not null and radius_meters > 0)
    )
);
create unique index ux_delivery_coverage_active_type on delivery_coverages(merchant_location_id, coverage_type) where coverage_status = 'ACTIVE';
create index ix_delivery_coverages_location_type_status on delivery_coverages(merchant_location_id, coverage_type, coverage_status);

create table delivery_coverage_areas (
    delivery_coverage_id uuid not null references delivery_coverages(id) on delete cascade,
    location_area_id uuid not null references location_areas(id),
    primary key (delivery_coverage_id, location_area_id)
);
create index ix_delivery_coverage_areas_area on delivery_coverage_areas(location_area_id);

create table shipping_policies (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    mode varchar(30) not null,
    base_fee bigint null,
    currency_code char(3) not null,
    rule_match_mode varchar(10) null,
    policy_status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_shipping_mode check (mode in ('FREE','FIXED','NEGOTIATED','CONDITIONAL')),
    constraint ck_shipping_match_mode check (rule_match_mode is null or rule_match_mode in ('ANY','ALL')),
    constraint ck_shipping_status check (policy_status in ('ACTIVE','INACTIVE')),
    constraint ck_shipping_base_fee check (base_fee is null or base_fee >= 0),
    constraint ck_shipping_mode_fields check (
        (mode = 'FREE' and base_fee is null and rule_match_mode is null)
        or (mode = 'FIXED' and base_fee is not null and rule_match_mode is null)
        or (mode = 'NEGOTIATED' and base_fee is null and rule_match_mode is null)
        or (mode = 'CONDITIONAL' and base_fee is not null and rule_match_mode is not null)
    )
);
create unique index ux_shipping_policy_active_location on shipping_policies(merchant_location_id) where policy_status = 'ACTIVE';

create table shipping_rules (
    id uuid primary key,
    shipping_policy_id uuid not null references shipping_policies(id) on delete cascade,
    condition_type varchar(30) not null,
    operator varchar(10) not null,
    threshold_value bigint not null,
    action_type varchar(30) not null,
    priority integer not null,
    enabled boolean not null default true,
    constraint ck_shipping_rule_condition check (condition_type in ('ORDER_AMOUNT_MIN','DISTANCE_MAX')),
    constraint ck_shipping_rule_operator check (operator in ('GTE','LTE')),
    constraint ck_shipping_rule_action check (action_type in ('FREE_SHIPPING')),
    constraint ck_shipping_rule_threshold check (threshold_value >= 0),
    constraint ck_shipping_rule_priority check (priority >= 0),
    constraint ck_shipping_rule_operator_pair check (
        (condition_type = 'ORDER_AMOUNT_MIN' and operator = 'GTE')
        or (condition_type = 'DISTANCE_MAX' and operator = 'LTE')
    ),
    unique(shipping_policy_id, priority)
);
create index ix_shipping_rules_policy_enabled_priority on shipping_rules(shipping_policy_id, enabled, priority);

create table inventory_balances (
    location_variant_id uuid primary key references location_variants(id),
    stock_on_hand bigint not null default 0,
    reserved_quantity bigint not null default 0,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_inventory_nonnegative check (stock_on_hand >= 0 and reserved_quantity >= 0),
    constraint ck_inventory_reserved_lte_stock check (reserved_quantity <= stock_on_hand)
);

create table inventory_reservations (
    id uuid primary key,
    location_variant_id uuid not null references location_variants(id),
    order_id uuid not null,
    order_item_id uuid not null,
    quantity bigint not null,
    reservation_status varchar(20) not null,
    expires_at timestamptz null,
    created_at timestamptz not null,
    committed_at timestamptz null,
    released_at timestamptz null,
    constraint ck_inventory_reservation_quantity check (quantity > 0),
    constraint ck_inventory_reservation_status check (reservation_status in ('ACTIVE','COMMITTED','RELEASED')),
    constraint ck_inventory_reservation_terminal_time check (
        (reservation_status = 'ACTIVE' and committed_at is null and released_at is null)
        or (reservation_status = 'COMMITTED' and committed_at is not null and released_at is null)
        or (reservation_status = 'RELEASED' and committed_at is null and released_at is not null)
    )
);
create unique index ux_inventory_reservation_active_order_item on inventory_reservations(order_item_id) where reservation_status = 'ACTIVE';
create index ix_inventory_reservations_order_status on inventory_reservations(order_id, reservation_status);
create index ix_inventory_reservations_variant_status on inventory_reservations(location_variant_id, reservation_status);

create table inventory_movements (
    id uuid primary key,
    location_variant_id uuid not null references location_variants(id),
    movement_type varchar(40) not null,
    quantity bigint not null,
    order_id uuid null,
    order_item_id uuid null,
    reservation_id uuid null references inventory_reservations(id),
    actor_user_id uuid null references users(id),
    reason_code varchar(60) null,
    metadata jsonb null,
    created_at timestamptz not null,
    constraint ck_inventory_movement_type check (movement_type in ('STOCK_IN','STOCK_OUT','RESERVE','RELEASE_RESERVATION','SALE','ADJUSTMENT_IN','ADJUSTMENT_OUT')),
    constraint ck_inventory_movement_quantity check (quantity > 0)
);
create index ix_inventory_movements_variant_created on inventory_movements(location_variant_id, created_at desc);

insert into inventory_balances(location_variant_id, stock_on_hand, reserved_quantity, updated_at, version)
select lv.id, 0, 0, now(), 0
from location_variants lv
join variants v on v.id = lv.variant_id
join catalog_items ci on ci.id = v.catalog_item_id
where ci.item_type = 'PRODUCT' and lv.inventory_mode = 'TRACKED' and lv.deleted_at is null
on conflict (location_variant_id) do nothing;
