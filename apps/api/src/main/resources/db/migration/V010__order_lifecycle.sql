alter table orders drop constraint ck_order_status_creation;

alter table orders add constraint ck_order_status_v010 check (status in (
    'PENDING_CONFIRMATION',
    'CONFIRMED',
    'OUT_FOR_DELIVERY',
    'READY_FOR_PICKUP',
    'AWAITING_BUYER_CONFIRMATION',
    'CANCELLATION_REQUESTED',
    'DELIVERY_ISSUE_REPORTED',
    'SERVICE_ISSUE_REPORTED',
    'BUYER_NO_SHOW',
    'NO_SHOW_CONTESTED',
    'COMPLETED',
    'EXPIRED',
    'REJECTED_BY_SELLER',
    'CANCELLED_BEFORE_CONFIRMATION',
    'CANCELLED_BY_BUYER_AFTER_CONFIRMATION',
    'CANCELLED_BY_SELLER_AFTER_CONFIRMATION',
    'CLOSED_WITH_ISSUE'
));

alter table product_fulfillments
    add column out_for_delivery_at timestamptz null,
    add column auto_complete_at timestamptz null,
    add column no_show_eligible_at timestamptz null;

alter table service_fulfillments
    add column auto_complete_at timestamptz null;

alter table order_events drop constraint ck_order_event_type_v009;
alter table order_events alter column event_type type varchar(80);
alter table order_events
    add column actor_context varchar(30) null,
    add column from_status varchar(60) null,
    add column to_status varchar(60) null,
    add column reason_code varchar(60) null,
    add column note text null;

alter table order_events add constraint ck_order_event_actor_context
    check (actor_context is null or actor_context in ('BUYER', 'MERCHANT', 'SYSTEM', 'ADMIN'));

alter table order_events add constraint ck_order_event_type_v010 check (event_type in (
    'ORDER_CREATED',
    'SELLER_CONFIRMED',
    'SELLER_REJECTED',
    'BUYER_CANCELLED_BEFORE_CONFIRMATION',
    'BUYER_REQUESTED_CANCELLATION',
    'SELLER_ACCEPTED_CANCELLATION',
    'SELLER_REJECTED_CANCELLATION',
    'SELLER_CANCELLED_AFTER_CONFIRMATION',
    'SELLER_MARKED_OUT_FOR_DELIVERY',
    'SELLER_MARKED_DELIVERED',
    'SELLER_MARKED_READY_FOR_PICKUP',
    'SELLER_MARKED_BUYER_NO_SHOW',
    'BUYER_CONTESTED_NO_SHOW',
    'PROVIDER_MARKED_PERFORMED',
    'BUYER_CONFIRMED_RECEIVED',
    'BUYER_CONFIRMED_SERVICE_COMPLETED',
    'BUYER_REPORTED_NOT_RECEIVED',
    'BUYER_REPORTED_SERVICE_NOT_COMPLETED',
    'ORDER_EXPIRED',
    'ORDER_AUTO_COMPLETED',
    'ORDER_CLOSED_WITH_ISSUE'
));

create table order_cancellation_requests (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete restrict,
    requested_by_user_id uuid not null references users(id),
    reason_code varchar(60) not null,
    reason_text text null,
    request_status varchar(20) not null,
    previous_order_status varchar(60) not null,
    responded_by_user_id uuid null references users(id),
    response_note text null,
    created_at timestamptz not null,
    responded_at timestamptz null,
    constraint ck_order_cancel_request_status check (request_status in ('PENDING', 'ACCEPTED', 'REJECTED')),
    constraint ck_order_cancel_previous_status check (previous_order_status in ('CONFIRMED', 'OUT_FOR_DELIVERY', 'READY_FOR_PICKUP')),
    constraint ck_order_cancel_response_consistency check (
        (request_status = 'PENDING' and responded_by_user_id is null and responded_at is null)
        or (request_status in ('ACCEPTED', 'REJECTED') and responded_by_user_id is not null and responded_at is not null)
    )
);

create unique index ux_order_cancel_one_pending
    on order_cancellation_requests(order_id)
    where request_status = 'PENDING';

create index ix_order_cancel_order_created
    on order_cancellation_requests(order_id, created_at desc);

create table order_issues (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete restrict,
    issue_type varchar(50) not null,
    reported_by_user_id uuid not null references users(id),
    reason_text text null,
    issue_status varchar(30) not null,
    opened_at timestamptz not null,
    close_after_at timestamptz not null,
    resolved_at timestamptz null,
    closed_at timestamptz null,
    constraint ck_order_issue_type check (issue_type in (
        'DELIVERY_NOT_RECEIVED',
        'SERVICE_NOT_COMPLETED',
        'BUYER_NO_SHOW_CONTESTED'
    )),
    constraint ck_order_issue_status check (issue_status in (
        'OPEN',
        'RESOLVED_BY_PARTIES',
        'CLOSED_BY_TIMEOUT'
    )),
    constraint ck_order_issue_deadline check (close_after_at >= opened_at),
    constraint ck_order_issue_resolution check (
        (issue_status = 'OPEN' and resolved_at is null and closed_at is null)
        or (issue_status = 'RESOLVED_BY_PARTIES' and resolved_at is not null and closed_at is null)
        or (issue_status = 'CLOSED_BY_TIMEOUT' and closed_at is not null)
    )
);

create unique index ux_order_issue_one_open
    on order_issues(order_id)
    where issue_status = 'OPEN';

create index ix_order_issue_opened
    on order_issues(issue_status, opened_at, order_id);

create index ix_orders_buyer_created_v010
    on orders(buyer_user_id, created_at desc, id desc);

create index ix_orders_merchant_created_v010
    on orders(merchant_profile_id, merchant_location_id, created_at desc, id desc);

create index ix_orders_pending_expiry
    on orders(confirmation_expires_at, id)
    where status = 'PENDING_CONFIRMATION' and confirmation_expires_at is not null;

create index if not exists ix_order_items_order_id
    on order_items(order_id, id);

create index if not exists ix_order_events_order_created
    on order_events(order_id, created_at, id);

create index ix_product_fulfillment_auto_complete
    on product_fulfillments(auto_complete_at, order_id)
    where auto_complete_at is not null;

create index ix_product_fulfillment_no_show
    on product_fulfillments(no_show_eligible_at, order_id)
    where no_show_eligible_at is not null;

create index ix_service_fulfillment_auto_complete
    on service_fulfillments(auto_complete_at, order_id)
    where auto_complete_at is not null;

create index ix_order_issue_close_after
    on order_issues(close_after_at, order_id)
    where issue_status = 'OPEN';
