create table reviews (
    id uuid primary key,
    order_item_id uuid not null references order_items(id) on delete restrict,
    order_id uuid not null references orders(id) on delete restrict,
    buyer_user_id uuid not null references users(id) on delete restrict,
    catalog_item_id uuid not null references catalog_items(id) on delete restrict,
    variant_id uuid null references variants(id) on delete restrict,
    merchant_profile_id uuid not null references merchant_profiles(id) on delete restrict,
    rating smallint not null,
    comment text null,
    review_status varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz null,
    constraint ux_reviews_order_item unique(order_item_id),
    constraint ck_reviews_rating check (rating between 1 and 5),
    constraint ck_reviews_status check (review_status in ('ACTIVE', 'HIDDEN', 'DELETED'))
);

create index ix_reviews_catalog_visible
    on reviews(catalog_item_id, created_at desc, id desc)
    where review_status = 'ACTIVE' and deleted_at is null;
create index ix_reviews_merchant_visible
    on reviews(merchant_profile_id, created_at desc, id desc)
    where review_status = 'ACTIVE' and deleted_at is null;

create table catalog_item_rating_stats (
    catalog_item_id uuid primary key references catalog_items(id) on delete restrict,
    review_count bigint not null default 0,
    rating_sum bigint not null default 0,
    average_rating numeric(4,2) not null default 0,
    updated_at timestamptz not null,
    constraint ck_catalog_rating_count check (review_count >= 0),
    constraint ck_catalog_rating_sum check (rating_sum >= 0),
    constraint ck_catalog_average_rating check (average_rating between 0 and 5)
);

create table merchant_rating_stats (
    merchant_profile_id uuid primary key references merchant_profiles(id) on delete restrict,
    review_count bigint not null default 0,
    rating_sum bigint not null default 0,
    average_rating numeric(4,2) not null default 0,
    updated_at timestamptz not null,
    constraint ck_merchant_rating_count check (review_count >= 0),
    constraint ck_merchant_rating_sum check (rating_sum >= 0),
    constraint ck_merchant_average_rating check (average_rating between 0 and 5)
);

create table reputation_order_outcomes (
    order_id uuid primary key references orders(id) on delete restrict,
    buyer_user_id uuid not null references users(id) on delete restrict,
    merchant_profile_id uuid not null references merchant_profiles(id) on delete restrict,
    buyer_outcome varchar(40) not null,
    merchant_outcome varchar(40) not null,
    source_order_status varchar(60) not null,
    source_outbox_event_id uuid null references outbox_events(id) on delete set null,
    updated_at timestamptz not null,
    constraint ck_rep_buyer_outcome check (buyer_outcome in (
        'NONE', 'SUCCESS', 'NEGATIVE_NO_SHOW', 'NEGATIVE_CANCEL_AFTER_CONFIRM', 'EXCLUDED'
    )),
    constraint ck_rep_merchant_outcome check (merchant_outcome in (
        'NONE', 'SUCCESS', 'NEGATIVE_SELLER_CANCEL', 'EXCLUDED'
    ))
);

create table user_reputation_stats (
    user_id uuid primary key references users(id) on delete restrict,
    eligible_order_count bigint not null default 0,
    completed_order_count bigint not null default 0,
    buyer_no_show_count bigint not null default 0,
    buyer_cancel_after_confirm_count bigint not null default 0,
    excluded_issue_count bigint not null default 0,
    completion_rate_basis_points integer not null default 0,
    updated_at timestamptz not null,
    constraint ck_user_rep_nonnegative check (
        eligible_order_count >= 0
        and completed_order_count >= 0
        and buyer_no_show_count >= 0
        and buyer_cancel_after_confirm_count >= 0
        and excluded_issue_count >= 0
    ),
    constraint ck_user_rep_rate check (completion_rate_basis_points between 0 and 10000)
);

create table merchant_reputation_stats (
    merchant_profile_id uuid primary key references merchant_profiles(id) on delete restrict,
    eligible_order_count bigint not null default 0,
    completed_order_count bigint not null default 0,
    seller_cancel_after_confirm_count bigint not null default 0,
    excluded_issue_count bigint not null default 0,
    completion_rate_basis_points integer not null default 0,
    updated_at timestamptz not null,
    constraint ck_merchant_rep_nonnegative check (
        eligible_order_count >= 0
        and completed_order_count >= 0
        and seller_cancel_after_confirm_count >= 0
        and excluded_issue_count >= 0
    ),
    constraint ck_merchant_rep_rate check (completion_rate_basis_points between 0 and 10000)
);

create index ix_reputation_outcome_buyer on reputation_order_outcomes(buyer_user_id);
create index ix_reputation_outcome_merchant on reputation_order_outcomes(merchant_profile_id);

create table notifications (
    id uuid primary key,
    user_id uuid not null references users(id) on delete restrict,
    notification_type varchar(60) not null,
    title varchar(220) not null,
    body text not null,
    related_entity_type varchar(40) null,
    related_entity_id uuid null,
    payload jsonb null,
    source_outbox_event_id uuid null references outbox_events(id) on delete set null,
    read_at timestamptz null,
    created_at timestamptz not null,
    expires_at timestamptz null
);

create unique index ux_notification_source_recipient_type
    on notifications(source_outbox_event_id, user_id, notification_type)
    where source_outbox_event_id is not null;
create index ix_notifications_user_cursor
    on notifications(user_id, created_at desc, id desc);
create index ix_notifications_user_unread
    on notifications(user_id, created_at desc)
    where read_at is null;

create table notification_deliveries (
    id uuid primary key,
    notification_id uuid not null references notifications(id) on delete cascade,
    channel varchar(20) not null,
    delivery_status varchar(20) not null,
    attempted_at timestamptz null,
    delivered_at timestamptz null,
    failure_code varchar(80) null,
    constraint ck_notification_channel check (channel in ('IN_APP', 'PUSH', 'EMAIL')),
    constraint ck_notification_delivery_status check (delivery_status in ('PENDING', 'DELIVERED', 'FAILED')),
    unique(notification_id, channel)
);
