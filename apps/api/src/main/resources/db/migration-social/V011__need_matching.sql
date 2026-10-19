create table need_requests (
    id uuid primary key,
    buyer_user_id uuid not null references users(id) on delete restrict,
    need_type varchar(20) not null,
    category_id uuid not null references categories(id) on delete restrict,
    title varchar(220) not null,
    query_text text not null,
    description text null,
    requested_quantity bigint null,
    location_area_id uuid null references location_areas(id) on delete restrict,
    geo_point geography(Point,4326) not null,
    location_snapshot jsonb not null,
    search_radius_meters integer not null,
    request_status varchar(30) not null,
    expires_at timestamptz not null,
    fulfilled_order_id uuid null references orders(id) on delete restrict,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    closed_at timestamptz null,
    constraint ck_need_type check (need_type in ('PRODUCT', 'SERVICE')),
    constraint ck_need_status check (request_status in ('ACTIVE', 'FULFILLED', 'EXPIRED', 'CANCELLED')),
    constraint ck_need_requested_quantity check (requested_quantity is null or requested_quantity > 0),
    constraint ck_need_radius check (search_radius_meters > 0),
    constraint ck_need_expiry_after_create check (expires_at > created_at),
    constraint ck_need_fulfilled_link check (
        (request_status = 'FULFILLED' and fulfilled_order_id is not null)
        or (request_status <> 'FULFILLED' and fulfilled_order_id is null)
    ),
    constraint ck_need_closed_at check (
        (request_status = 'ACTIVE' and closed_at is null)
        or (request_status <> 'ACTIVE' and closed_at is not null)
    ),
    constraint ck_need_service_quantity check (
        need_type <> 'SERVICE' or requested_quantity is null or requested_quantity = 1
    )
);

create table need_responses (
    id uuid primary key,
    need_request_id uuid not null references need_requests(id) on delete restrict,
    merchant_profile_id uuid not null references merchant_profiles(id) on delete restrict,
    merchant_location_id uuid not null references merchant_locations(id) on delete restrict,
    response_status varchar(20) not null,
    note text null,
    created_by_user_id uuid not null references users(id) on delete restrict,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    withdrawn_at timestamptz null,
    version bigint not null default 0,
    constraint ck_need_response_status check (response_status in ('ACTIVE', 'WITHDRAWN')),
    constraint ck_need_response_withdrawn_at check (
        (response_status = 'ACTIVE' and withdrawn_at is null)
        or (response_status = 'WITHDRAWN' and withdrawn_at is not null)
    )
);

create unique index ux_need_response_active_location
    on need_responses(need_request_id, merchant_location_id)
    where response_status = 'ACTIVE';

create table need_response_items (
    id uuid primary key,
    need_response_id uuid not null references need_responses(id) on delete restrict,
    catalog_item_id uuid not null references catalog_items(id) on delete restrict,
    variant_id uuid not null references variants(id) on delete restrict,
    location_variant_id uuid not null references location_variants(id) on delete restrict,
    item_status varchar(20) not null,
    price_snapshot bigint not null,
    currency_code char(3) not null,
    availability_snapshot jsonb not null,
    created_at timestamptz not null,
    retired_at timestamptz null,
    constraint ck_need_response_item_status check (item_status in ('ACTIVE', 'RETIRED')),
    constraint ck_need_response_item_retired_at check (
        (item_status = 'ACTIVE' and retired_at is null)
        or (item_status = 'RETIRED' and retired_at is not null)
    ),
    constraint ck_need_response_price check (price_snapshot >= 0)
);

create unique index ux_need_response_active_location_variant
    on need_response_items(need_response_id, location_variant_id)
    where item_status = 'ACTIVE';

create index ix_need_requests_buyer_created
    on need_requests(buyer_user_id, created_at desc, id desc);
create index ix_need_requests_status_expiry
    on need_requests(request_status, expires_at, id)
    where request_status = 'ACTIVE';
create index ix_need_requests_category_type_state
    on need_requests(category_id, need_type, request_status, created_at desc);
create index ix_need_requests_geo on need_requests using gist(geo_point);
create index ix_need_responses_need_state
    on need_responses(need_request_id, response_status, created_at desc);
create index ix_need_responses_merchant_location
    on need_responses(merchant_profile_id, merchant_location_id, created_at desc);
create index ix_need_response_items_response_state
    on need_response_items(need_response_id, item_status, created_at);

alter table checkout_sessions drop constraint ck_checkout_source_type;
alter table checkout_sessions add constraint ck_checkout_source_type
    check (source_type in ('CART', 'DIRECT', 'NEED_REQUEST'));

alter table orders
    add constraint fk_orders_source_need_request
        foreign key (source_need_request_id) references need_requests(id) on delete restrict,
    add constraint fk_orders_source_need_response_item
        foreign key (source_need_response_item_id) references need_response_items(id) on delete restrict,
    add constraint ck_order_source_payload check (
        (source_type = 'MARKETPLACE'
            and source_need_request_id is null
            and source_need_response_item_id is null)
        or
        (source_type = 'NEED_REQUEST'
            and source_need_request_id is not null
            and source_need_response_item_id is not null)
    );
