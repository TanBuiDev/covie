create table users (
    id uuid primary key,
    display_name varchar(120) not null,
    avatar_media_id uuid null,
    account_status varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_users_status check (account_status in ('ACTIVE','SUSPENDED','BANNED','DELETED'))
);

create table user_identities (
    id uuid primary key,
    user_id uuid not null references users(id),
    provider varchar(30) not null,
    provider_subject varchar(255) null,
    email_normalized varchar(320) null,
    phone_normalized varchar(30) null,
    password_hash varchar(255) null,
    verified_at timestamptz null,
    created_at timestamptz not null,
    last_used_at timestamptz null,
    constraint ck_user_identity_provider check (provider in ('EMAIL','PHONE','GOOGLE','FACEBOOK','APPLE','ZALO')),
    constraint ck_email_identity_password check (
        provider <> 'EMAIL' or (email_normalized is not null and password_hash is not null)
    ),
    constraint ck_google_identity_subject check (
        provider <> 'GOOGLE' or provider_subject is not null
    )
);

create unique index ux_user_identity_provider_subject
    on user_identities(provider, provider_subject)
    where provider_subject is not null;

create unique index ux_user_identity_local_email
    on user_identities(email_normalized)
    where provider = 'EMAIL' and email_normalized is not null;

create index ix_user_identity_user on user_identities(user_id);
create index ix_user_identity_google_email
    on user_identities(email_normalized)
    where provider = 'GOOGLE' and email_normalized is not null;

create table system_role_assignments (
    id uuid primary key,
    user_id uuid not null references users(id),
    role varchar(30) not null,
    created_at timestamptz not null,
    constraint ck_system_role check (role in ('USER','ADMIN')),
    constraint ux_system_role_assignment unique (user_id, role)
);

create table user_contact_methods (
    id uuid primary key,
    user_id uuid not null references users(id),
    contact_type varchar(30) not null,
    value text not null,
    is_primary boolean not null default false,
    is_verified boolean not null default false,
    status varchar(20) not null,
    created_at timestamptz not null,
    constraint ck_user_contact_type check (contact_type in ('PHONE','ZALO','MESSENGER','OTHER')),
    constraint ck_user_contact_status check (status in ('ACTIVE','INACTIVE'))
);

create index ix_user_contact_user_status on user_contact_methods(user_id, status);
create unique index ux_user_primary_contact_type
    on user_contact_methods(user_id, contact_type)
    where is_primary = true and status = 'ACTIVE';

create table user_addresses (
    id uuid primary key,
    user_id uuid not null references users(id),
    label varchar(80) not null,
    recipient_name varchar(120) null,
    phone varchar(30) null,
    address_text text not null,
    location_area_id uuid null,
    geo_point geography(Point,4326) not null,
    pin_source varchar(30) not null,
    note text null,
    is_default boolean not null default false,
    status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint ck_user_address_pin_source check (pin_source in ('GPS','GEOCODED','USER_ADJUSTED')),
    constraint ck_user_address_status check (status in ('ACTIVE','DELETED'))
);

create index ix_user_address_user_status on user_addresses(user_id, status);
create index ix_user_address_geo on user_addresses using gist(geo_point);
create unique index ux_user_default_address
    on user_addresses(user_id)
    where is_default = true and status = 'ACTIVE';

create table audit_logs (
    id uuid primary key,
    actor_user_id uuid null references users(id),
    actor_type varchar(20) not null,
    event_category varchar(30) not null,
    action varchar(100) not null,
    entity_type varchar(60) not null,
    entity_id uuid null,
    before_snapshot jsonb null,
    after_snapshot jsonb null,
    request_id varchar(100) null,
    ip_hash_or_metadata jsonb null,
    created_at timestamptz not null
);

create index ix_audit_actor_created on audit_logs(actor_user_id, created_at desc);
create index ix_audit_entity_created on audit_logs(entity_type, entity_id, created_at desc);
