create table carts (
    id uuid primary key,
    user_id uuid not null references users(id),
    merchant_location_id uuid not null references merchant_locations(id),
    cart_type varchar(20) not null,
    cart_status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_cart_type check (cart_type in ('PRODUCT','SERVICE')),
    constraint ck_cart_status check (cart_status in ('ACTIVE','CHECKED_OUT','ABANDONED'))
);
create unique index ux_cart_active_user_location_type on carts(user_id, merchant_location_id, cart_type) where cart_status = 'ACTIVE';

create table cart_items (
    id uuid primary key,
    cart_id uuid not null references carts(id) on delete cascade,
    location_variant_id uuid not null references location_variants(id),
    variant_id uuid not null references variants(id),
    quantity bigint not null,
    price_snapshot bigint not null,
    currency_code char(3) not null,
    item_name_snapshot varchar(220) not null,
    variant_snapshot jsonb null,
    added_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_cart_item_quantity check (quantity >= 1),
    constraint ck_cart_item_price check (price_snapshot >= 0),
    unique(cart_id, location_variant_id)
);
create index ix_cart_items_cart on cart_items(cart_id);

create table checkout_sessions (
    id uuid primary key,
    token_hash varchar(64) not null unique,
    user_id uuid not null references users(id),
    source_type varchar(30) not null,
    source_context jsonb not null,
    cart_version bigint null,
    request_snapshot jsonb not null,
    preview_snapshot jsonb not null,
    expires_at timestamptz not null,
    consumed_at timestamptz null,
    created_at timestamptz not null,
    constraint ck_checkout_source_type check (source_type in ('CART','DIRECT'))
);
create index ix_checkout_sessions_user_created on checkout_sessions(user_id, created_at desc);
create index ix_checkout_sessions_expiry on checkout_sessions(expires_at) where consumed_at is null;
