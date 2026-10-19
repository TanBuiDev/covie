create table orders (
    id uuid primary key,
    order_code varchar(32) not null unique,
    order_type varchar(20) not null,
    buyer_user_id uuid not null references users(id),
    merchant_profile_id uuid not null references merchant_profiles(id),
    merchant_location_id uuid not null references merchant_locations(id),
    status varchar(60) not null,
    source_type varchar(20) not null,
    source_need_request_id uuid null,
    source_need_response_item_id uuid null,
    payment_method varchar(40) not null,
    subtotal bigint not null,
    shipping_fee_type varchar(30) null,
    shipping_fee bigint null,
    known_total bigint not null,
    total_amount bigint null,
    currency_code char(3) not null,
    confirmation_expires_at timestamptz null,
    confirmed_at timestamptz null,
    completed_at timestamptz null,
    closed_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_order_type check (order_type in ('PRODUCT','SERVICE')),
    constraint ck_order_source check (source_type in ('MARKETPLACE','NEED_REQUEST')),
    constraint ck_order_status_creation check (status in ('PENDING_CONFIRMATION')),
    constraint ck_order_payment check (payment_method in ('COD','BANK_TRANSFER_NEGOTIATED')),
    constraint ck_order_shipping_fee_type check (shipping_fee_type is null or shipping_fee_type in ('FREE','FIXED','CONDITIONAL','NEGOTIATED','NOT_APPLICABLE')),
    constraint ck_order_money_nonnegative check (subtotal >= 0 and known_total >= 0 and (shipping_fee is null or shipping_fee >= 0) and (total_amount is null or total_amount >= 0)),
    constraint ck_order_negotiated_total check (shipping_fee_type <> 'NEGOTIATED' or (shipping_fee is null and total_amount is null and known_total = subtotal)),
    constraint ck_order_service_shipping check (order_type <> 'SERVICE' or (shipping_fee_type = 'NOT_APPLICABLE' and shipping_fee is null and total_amount = subtotal and known_total = subtotal))
);
create index ix_orders_buyer_created on orders(buyer_user_id, created_at desc);
create index ix_orders_merchant_created on orders(merchant_location_id, created_at desc);

create table order_items (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete restrict,
    catalog_item_id uuid null references catalog_items(id),
    variant_id uuid null references variants(id),
    location_variant_id uuid null references location_variants(id),
    item_type varchar(20) not null,
    quantity bigint not null,
    unit_price bigint not null,
    line_total bigint not null,
    currency_code char(3) not null,
    item_snapshot jsonb not null,
    created_at timestamptz not null,
    constraint ck_order_item_type check (item_type in ('PRODUCT','SERVICE')),
    constraint ck_order_item_quantity check (quantity > 0),
    constraint ck_order_item_money check (unit_price >= 0 and line_total >= 0)
);
create index ix_order_items_order on order_items(order_id);

create table product_fulfillments (
    order_id uuid primary key references orders(id) on delete restrict,
    fulfillment_method varchar(30) not null,
    pickup_location_id uuid null references merchant_locations(id),
    buyer_address_id uuid null references user_addresses(id),
    distance_meters integer null,
    pickup_location_snapshot jsonb not null,
    delivery_address_snapshot jsonb null,
    buyer_contact_snapshot jsonb not null,
    merchant_contact_snapshot jsonb not null,
    shipping_policy_snapshot jsonb null,
    seller_marked_ready_at timestamptz null,
    seller_marked_delivered_at timestamptz null,
    awaiting_buyer_confirmation_at timestamptz null,
    constraint ck_product_fulfillment_method check (fulfillment_method in ('PICKUP','SELLER_DELIVERY')),
    constraint ck_product_fulfillment_payload check ((fulfillment_method = 'PICKUP' and buyer_address_id is null and delivery_address_snapshot is null) or (fulfillment_method = 'SELLER_DELIVERY' and buyer_address_id is not null and delivery_address_snapshot is not null))
);

create table service_fulfillments (
    order_id uuid primary key references orders(id) on delete restrict,
    service_address_id uuid null references user_addresses(id),
    service_address_snapshot jsonb not null,
    provider_location_snapshot jsonb not null,
    buyer_contact_snapshot jsonb not null,
    merchant_contact_snapshot jsonb not null,
    provider_marked_performed_at timestamptz null,
    awaiting_buyer_confirmation_at timestamptz null,
    created_at timestamptz not null
);

create table order_events (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete restrict,
    event_type varchar(50) not null,
    actor_user_id uuid null references users(id),
    metadata jsonb null,
    created_at timestamptz not null,
    constraint ck_order_event_type_v009 check (event_type in ('ORDER_CREATED'))
);
create index ix_order_events_order_created on order_events(order_id, created_at, id);

alter table inventory_reservations
    add constraint fk_inventory_reservation_order foreign key (order_id) references orders(id),
    add constraint fk_inventory_reservation_order_item foreign key (order_item_id) references order_items(id);

alter table inventory_movements
    add constraint fk_inventory_movement_order foreign key (order_id) references orders(id),
    add constraint fk_inventory_movement_order_item foreign key (order_item_id) references order_items(id);
