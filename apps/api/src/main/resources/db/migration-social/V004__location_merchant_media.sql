create table location_areas (
    id uuid primary key,
    parent_id uuid null references location_areas(id),
    area_type varchar(40) not null,
    name varchar(180) not null,
    slug varchar(200) not null,
    path_cache text null,
    geo_boundary geography(MultiPolygon,4326) null,
    status varchar(20) not null,
    sort_order integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_location_area_status check (status in ('ACTIVE','INACTIVE','DELETED'))
);

create unique index ux_location_area_parent_slug
    on location_areas(coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), slug)
    where status <> 'DELETED';
create index ix_location_area_parent_sort on location_areas(parent_id, sort_order, name);
create index ix_location_area_boundary on location_areas using gist(geo_boundary);

create table media_assets (
    id uuid primary key,
    owner_user_id uuid null references users(id),
    storage_provider varchar(30) not null,
    storage_key text not null,
    upload_purpose varchar(40) not null,
    mime_type varchar(120) not null,
    size_bytes bigint not null,
    width integer null,
    height integer null,
    checksum varchar(128) null,
    media_status varchar(20) not null,
    upload_expires_at timestamptz not null,
    verified_at timestamptz null,
    created_at timestamptz not null,
    deleted_at timestamptz null,
    constraint ck_media_status check (media_status in ('PENDING_UPLOAD','READY','FAILED','DELETED')),
    constraint ck_media_size check (size_bytes > 0)
);

create unique index ux_media_storage_key on media_assets(storage_key);
create index ix_media_owner_status_created on media_assets(owner_user_id, media_status, created_at desc);

alter table users
    add constraint fk_users_avatar_media
    foreign key (avatar_media_id) references media_assets(id);

create table merchant_profiles (
    id uuid primary key,
    name varchar(180) not null,
    slug varchar(200) not null,
    description text null,
    logo_media_id uuid null references media_assets(id),
    cover_media_id uuid null references media_assets(id),
    merchant_status varchar(30) not null,
    moderation_status varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_merchant_status check (merchant_status in ('ACTIVE','PAUSED','SUSPENDED','DELETED')),
    constraint ck_merchant_moderation_status check (moderation_status in ('NOT_REQUIRED','PENDING','APPROVED','REJECTED','HIDDEN'))
);

create unique index ux_merchant_profile_slug on merchant_profiles(slug) where deleted_at is null;

create table merchant_memberships (
    id uuid primary key,
    merchant_profile_id uuid not null references merchant_profiles(id),
    user_id uuid not null references users(id),
    role varchar(20) not null,
    membership_status varchar(20) not null,
    joined_at timestamptz null,
    created_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_merchant_membership_role check (role in ('OWNER','MANAGER','STAFF')),
    constraint ck_merchant_membership_status check (membership_status in ('ACTIVE','INVITED','REVOKED'))
);

create unique index ux_merchant_membership_user
    on merchant_memberships(merchant_profile_id, user_id)
    where membership_status in ('ACTIVE','INVITED');
create index ix_merchant_membership_user_status
    on merchant_memberships(user_id, membership_status, merchant_profile_id);

create table merchant_locations (
    id uuid primary key,
    merchant_profile_id uuid not null references merchant_profiles(id),
    name varchar(150) not null,
    address_text text not null,
    location_area_id uuid null references location_areas(id),
    geo_point geography(Point,4326) not null,
    pin_source varchar(30) not null,
    timezone varchar(60) not null,
    location_status varchar(30) not null,
    pickup_enabled boolean not null default false,
    seller_delivery_enabled boolean not null default false,
    service_enabled boolean not null default false,
    minimum_delivery_order bigint null,
    currency_code char(3) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_merchant_location_status check (location_status in ('ACTIVE','PAUSED','DELETED')),
    constraint ck_merchant_location_pin_source check (pin_source in ('GPS','GEOCODED','USER_ADJUSTED')),
    constraint ck_merchant_location_min_order check (minimum_delivery_order is null or minimum_delivery_order >= 0)
);

create index ix_merchant_locations_profile_status
    on merchant_locations(merchant_profile_id, location_status);
create index ix_merchant_locations_area on merchant_locations(location_area_id);
create index ix_merchant_locations_geo on merchant_locations using gist(geo_point);

create table merchant_member_locations (
    id uuid primary key,
    membership_id uuid not null references merchant_memberships(id),
    merchant_location_id uuid not null references merchant_locations(id),
    created_at timestamptz not null,
    unique(membership_id, merchant_location_id)
);

create table merchant_contact_methods (
    id uuid primary key,
    merchant_profile_id uuid not null references merchant_profiles(id),
    merchant_location_id uuid null references merchant_locations(id),
    contact_type varchar(30) not null,
    value text null,
    qr_media_id uuid null references media_assets(id),
    is_primary boolean not null default false,
    is_public boolean not null default true,
    status varchar(20) not null,
    created_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_merchant_contact_type check (contact_type in ('ZALO_LINK','ZALO_QR','MESSENGER_LINK','PHONE','OTHER')),
    constraint ck_merchant_contact_status check (status in ('ACTIVE','INACTIVE')),
    constraint ck_merchant_contact_payload check (value is not null or qr_media_id is not null)
);

create index ix_merchant_contact_profile_location
    on merchant_contact_methods(merchant_profile_id, merchant_location_id, status);

create table merchant_location_hours (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    day_of_week smallint not null,
    operation_type varchar(20) not null,
    open_time time null,
    close_time time null,
    is_closed boolean not null default false,
    sort_order integer not null default 0,
    constraint ck_merchant_hour_day check (day_of_week between 1 and 7),
    constraint ck_merchant_hour_operation check (operation_type in ('SHOP','DELIVERY','PICKUP')),
    constraint ck_merchant_hour_window check (
        (is_closed and open_time is null and close_time is null)
        or
        (not is_closed and open_time is not null and close_time is not null and open_time < close_time)
    )
);

create index ix_merchant_hours_location_day
    on merchant_location_hours(merchant_location_id, day_of_week, operation_type, sort_order);

create table merchant_location_hour_exceptions (
    id uuid primary key,
    merchant_location_id uuid not null references merchant_locations(id),
    local_date date not null,
    operation_type varchar(20) not null,
    open_time time null,
    close_time time null,
    is_closed boolean not null default false,
    note text null,
    sort_order integer not null default 0,
    constraint ck_merchant_hour_exception_operation check (operation_type in ('SHOP','DELIVERY','PICKUP')),
    constraint ck_merchant_hour_exception_window check (
        (is_closed and open_time is null and close_time is null)
        or
        (not is_closed and open_time is not null and close_time is not null and open_time < close_time)
    )
);

create index ix_merchant_hour_exception_location_date
    on merchant_location_hour_exceptions(merchant_location_id, local_date, operation_type, sort_order);

alter table user_addresses
    add constraint fk_user_addresses_location_area
    foreign key (location_area_id) references location_areas(id);
