create table categories (
    id uuid primary key,
    parent_id uuid null references categories(id),
    name varchar(160) not null,
    slug varchar(180) not null,
    catalog_type varchar(20) not null,
    status varchar(20) not null,
    sort_order integer not null default 0,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_category_catalog_type check (catalog_type in ('PRODUCT','SERVICE','BOTH')),
    constraint ck_category_status check (status in ('ACTIVE','INACTIVE','DELETED'))
);
create unique index ux_categories_parent_slug
    on categories(coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), slug)
    where deleted_at is null;
create index ix_categories_parent_sort on categories(parent_id, sort_order, name);

create table category_attributes (
    id uuid primary key,
    category_id uuid not null references categories(id),
    name varchar(120) not null,
    attribute_key varchar(100) not null,
    data_type varchar(30) not null,
    is_required boolean not null default false,
    is_filterable boolean not null default false,
    sort_order integer not null default 0,
    unique(category_id, attribute_key),
    constraint ck_category_attribute_type check (data_type in ('TEXT','NUMBER','BOOLEAN','SINGLE_SELECT','MULTI_SELECT'))
);

create table category_attribute_options (
    id uuid primary key,
    category_attribute_id uuid not null references category_attributes(id),
    option_value varchar(180) not null,
    sort_order integer not null default 0,
    unique(category_attribute_id, option_value)
);

create table tags (
    id uuid primary key,
    name varchar(100) not null,
    normalized_name varchar(100) not null unique,
    created_at timestamptz not null
);

create table catalog_items (
    id uuid primary key,
    merchant_profile_id uuid not null references merchant_profiles(id),
    item_type varchar(20) not null,
    listing_kind varchar(20) not null,
    primary_category_id uuid not null references categories(id),
    name varchar(220) not null,
    slug varchar(240) not null,
    description text null,
    listing_status varchar(30) not null,
    moderation_status varchar(30) not null,
    search_vector tsvector null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_catalog_item_type check (item_type in ('PRODUCT','SERVICE')),
    constraint ck_catalog_listing_kind check (listing_kind in ('STANDARD','RESALE')),
    constraint ck_catalog_resale_product check (listing_kind <> 'RESALE' or item_type = 'PRODUCT'),
    constraint ck_catalog_listing_status check (listing_status in ('DRAFT','ACTIVE','PAUSED','DELETED')),
    constraint ck_catalog_moderation_status check (moderation_status in ('NOT_REQUIRED','PENDING','APPROVED','REJECTED','HIDDEN'))
);
create unique index ux_catalog_item_merchant_slug
    on catalog_items(merchant_profile_id, slug) where deleted_at is null;
create index ix_catalog_items_merchant_state
    on catalog_items(merchant_profile_id, listing_status, moderation_status);
create index ix_catalog_items_category_type_state
    on catalog_items(primary_category_id, item_type, listing_status);
create index ix_catalog_items_search_vector on catalog_items using gin(search_vector);

create table catalog_item_attribute_values (
    id uuid primary key,
    catalog_item_id uuid not null references catalog_items(id),
    category_attribute_id uuid not null references category_attributes(id),
    value_text text null,
    value_number numeric(20,6) null,
    value_boolean boolean null,
    attribute_option_id uuid null references category_attribute_options(id)
);
create index ix_catalog_attribute_values_item on catalog_item_attribute_values(catalog_item_id);

create table catalog_item_tags (
    catalog_item_id uuid not null references catalog_items(id),
    tag_id uuid not null references tags(id),
    primary key(catalog_item_id, tag_id)
);

create table option_groups (
    id uuid primary key,
    catalog_item_id uuid not null references catalog_items(id),
    name varchar(120) not null,
    sort_order integer not null default 0,
    version bigint not null default 0
);
create index ix_option_groups_item_sort on option_groups(catalog_item_id, sort_order);

create table option_values (
    id uuid primary key,
    option_group_id uuid not null references option_groups(id),
    value varchar(160) not null,
    sort_order integer not null default 0,
    unique(option_group_id, value)
);

create table variants (
    id uuid primary key,
    catalog_item_id uuid not null references catalog_items(id),
    sku varchar(120) null,
    variant_name varchar(240) not null,
    is_default boolean not null default false,
    variant_status varchar(20) not null,
    sort_order integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_variant_status check (variant_status in ('ACTIVE','PAUSED','DELETED'))
);
create index ix_variants_item_status on variants(catalog_item_id, variant_status, sort_order);
create unique index ux_variant_default_per_item
    on variants(catalog_item_id) where is_default and deleted_at is null;

create table variant_option_values (
    variant_id uuid not null references variants(id),
    option_value_id uuid not null references option_values(id),
    primary key(variant_id, option_value_id)
);

create table location_variants (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    variant_id uuid not null references variants(id),
    price bigint not null,
    currency_code char(3) not null,
    is_available boolean not null default true,
    inventory_mode varchar(20) null,
    preparation_time_minutes integer null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_location_variant_price check (price >= 0),
    constraint ck_location_variant_inventory_mode check (inventory_mode is null or inventory_mode in ('TRACKED','UNTRACKED')),
    constraint ck_location_variant_prep_time check (preparation_time_minutes is null or preparation_time_minutes >= 0)
);
create unique index ux_location_variant_active
    on location_variants(merchant_location_id, variant_id) where deleted_at is null;
create index ix_location_variant_location_available
    on location_variants(merchant_location_id, is_available) where deleted_at is null;

create table catalog_item_media (
    catalog_item_id uuid not null references catalog_items(id),
    media_asset_id uuid not null references media_assets(id),
    media_type varchar(20) not null,
    sort_order integer not null default 0,
    is_primary boolean not null default false,
    primary key(catalog_item_id, media_asset_id),
    constraint ck_catalog_item_media_type check (media_type in ('IMAGE'))
);

create table merchant_media (
    merchant_profile_id uuid not null references merchant_profiles(id),
    media_asset_id uuid not null references media_assets(id),
    media_type varchar(30) not null,
    sort_order integer not null default 0,
    primary key(merchant_profile_id, media_asset_id)
);
