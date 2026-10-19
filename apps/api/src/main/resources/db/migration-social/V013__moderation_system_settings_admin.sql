create table system_settings (
    id uuid primary key,
    setting_key varchar(160) not null,
    value_type varchar(30) not null,
    value_json jsonb not null,
    scope_type varchar(30) not null,
    location_area_id uuid null references location_areas(id) on delete restrict,
    category_id uuid null references categories(id) on delete restrict,
    merchant_profile_id uuid null references merchant_profiles(id) on delete restrict,
    version bigint not null default 0,
    updated_by_user_id uuid not null references users(id) on delete restrict,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_system_setting_value_type check (value_type in ('BOOLEAN', 'INTEGER', 'STRING', 'ENUM')),
    constraint ck_system_setting_scope_type check (scope_type in ('GLOBAL', 'LOCATION_AREA', 'CATEGORY', 'MERCHANT')),
    constraint ck_system_setting_scope_shape check (
        (scope_type = 'GLOBAL' and location_area_id is null and category_id is null and merchant_profile_id is null)
        or (scope_type = 'LOCATION_AREA' and location_area_id is not null and category_id is null and merchant_profile_id is null)
        or (scope_type = 'CATEGORY' and category_id is not null and location_area_id is null and merchant_profile_id is null)
        or (scope_type = 'MERCHANT' and merchant_profile_id is not null and location_area_id is null and category_id is null)
    )
);

create unique index ux_system_setting_global on system_settings(setting_key) where scope_type = 'GLOBAL';
create unique index ux_system_setting_area on system_settings(setting_key, location_area_id) where scope_type = 'LOCATION_AREA';
create unique index ux_system_setting_category on system_settings(setting_key, category_id) where scope_type = 'CATEGORY';
create unique index ux_system_setting_merchant on system_settings(setting_key, merchant_profile_id) where scope_type = 'MERCHANT';
create index ix_system_setting_key_scope on system_settings(setting_key, scope_type);

create table system_setting_history (
    id uuid primary key,
    setting_id uuid not null references system_settings(id) on delete restrict,
    old_value_json jsonb null,
    new_value_json jsonb not null,
    old_version bigint not null,
    new_version bigint not null,
    changed_by_user_id uuid not null references users(id) on delete restrict,
    changed_at timestamptz not null
);
create index ix_system_setting_history_setting on system_setting_history(setting_id, changed_at desc, id desc);

create table moderation_cases (
    id uuid primary key,
    case_type varchar(30) not null,
    reporter_user_id uuid null references users(id) on delete restrict,
    target_type varchar(30) not null,
    target_user_id uuid null references users(id) on delete restrict,
    target_merchant_profile_id uuid null references merchant_profiles(id) on delete restrict,
    target_catalog_item_id uuid null references catalog_items(id) on delete restrict,
    target_need_request_id uuid null references need_requests(id) on delete restrict,
    target_review_id uuid null references reviews(id) on delete restrict,
    reason_code varchar(60) null,
    description text null,
    case_status varchar(30) not null,
    assigned_admin_user_id uuid null references users(id) on delete restrict,
    opened_at timestamptz not null,
    resolved_at timestamptz null,
    closed_at timestamptz null,
    constraint ck_moderation_case_type check (case_type in ('USER_REPORT', 'PRE_APPROVAL', 'SYSTEM_FLAG')),
    constraint ck_moderation_target_type check (target_type in ('USER', 'MERCHANT', 'CATALOG_ITEM', 'NEED_REQUEST', 'REVIEW')),
    constraint ck_moderation_case_status check (case_status in ('OPEN', 'RESOLVED', 'CLOSED')),
    constraint ck_moderation_exactly_one_target check (
        num_nonnulls(target_user_id, target_merchant_profile_id, target_catalog_item_id, target_need_request_id, target_review_id) = 1
    ),
    constraint ck_moderation_target_matches_type check (
        (target_type = 'USER' and target_user_id is not null)
        or (target_type = 'MERCHANT' and target_merchant_profile_id is not null)
        or (target_type = 'CATALOG_ITEM' and target_catalog_item_id is not null)
        or (target_type = 'NEED_REQUEST' and target_need_request_id is not null)
        or (target_type = 'REVIEW' and target_review_id is not null)
    )
);

create table moderation_actions (
    id uuid primary key,
    moderation_case_id uuid not null references moderation_cases(id) on delete restrict,
    action_type varchar(30) not null,
    actor_admin_user_id uuid not null references users(id) on delete restrict,
    reason_code varchar(60) null,
    note text null,
    metadata jsonb null,
    created_at timestamptz not null,
    constraint ck_moderation_action_type check (action_type in ('APPROVE', 'REJECT', 'HIDE', 'RESTORE', 'CLOSE', 'SUSPEND', 'REINSTATE'))
);

create index ix_moderation_cases_admin on moderation_cases(case_status, opened_at desc, id desc);
create index ix_moderation_cases_target on moderation_cases(target_type, opened_at desc);
create index ix_moderation_actions_case on moderation_actions(moderation_case_id, created_at, id);
create unique index ux_open_preapproval_catalog on moderation_cases(target_catalog_item_id)
    where case_type = 'PRE_APPROVAL' and case_status = 'OPEN' and target_catalog_item_id is not null;
create unique index ux_open_preapproval_need on moderation_cases(target_need_request_id)
    where case_type = 'PRE_APPROVAL' and case_status = 'OPEN' and target_need_request_id is not null;

alter table need_requests add column moderation_status varchar(30) not null default 'NOT_REQUIRED';
alter table need_requests add constraint ck_need_moderation_status
    check (moderation_status in ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED', 'HIDDEN'));
create index ix_need_active_moderation_expiry on need_requests(request_status, moderation_status, expires_at, created_at desc);

alter table merchant_profiles add column status_before_suspension varchar(30) null;
alter table merchant_profiles add constraint ck_merchant_status_before_suspension
    check (status_before_suspension is null or status_before_suspension in ('ACTIVE', 'PAUSED'));

create index if not exists ix_users_admin_state on users(account_status, created_at desc, id desc);
create index if not exists ix_merchant_profiles_admin_state on merchant_profiles(merchant_status, created_at desc, id desc);
create index if not exists ix_orders_admin_state_created on orders(status, created_at desc, id desc);
