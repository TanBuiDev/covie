-- Seller billing lives outside buyer orders/checkout. All monetary amounts are integer VND.
-- No existing merchant is charged or disabled by this migration.
create table billing_plans (
    code varchar(60) primary key,
    display_name varchar(180) not null,
    retired_at timestamptz,
    created_at timestamptz not null default now()
);

create table billing_plan_versions (
    id uuid primary key,
    plan_code varchar(60) not null references billing_plans(code) on delete restrict,
    revision integer not null check (revision > 0),
    price_vnd bigint not null check (price_vnd >= 0),
    cycle_months integer not null check (cycle_months between 1 and 120),
    effective_at timestamptz not null,
    published_at timestamptz not null,
    created_by_user_id uuid references users(id) on delete restrict,
    unique (plan_code, revision),
    unique (plan_code, effective_at)
);
create index ix_billing_plan_effective on billing_plan_versions(plan_code, effective_at desc);

create table billing_policy_versions (
    id uuid primary key,
    revision integer not null unique check (revision > 0),
    onboarding_mode varchar(30) not null check (onboarding_mode in ('PAY_BEFORE_SELL','TRIAL')),
    trial_days integer not null check (trial_days between 0 and 365),
    grace_days integer not null check (grace_days between 0 and 60),
    effective_at timestamptz not null unique,
    created_at timestamptz not null,
    created_by_user_id uuid references users(id) on delete restrict
);

create table merchant_subscriptions (
    merchant_id uuid primary key references merchant_profiles(id) on delete restrict,
    plan_code varchar(60) not null references billing_plans(code) on delete restrict,
    status varchar(24) not null check (status in (
        'DRAFT','PENDING_PAYMENT','TRIALING','ACTIVE','PAST_DUE','EXPIRED','CANCELLED','REVOKED'
    )),
    period_start timestamptz,
    period_end timestamptz,
    trial_end timestamptz,
    cancel_at_period_end boolean not null default false,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_subscription_period check (period_end is null or period_start is not null and period_end > period_start)
);
create index ix_subscriptions_due on merchant_subscriptions(status, period_end) where period_end is not null;

create table billing_entitlement_grants (
    id uuid primary key,
    merchant_id uuid not null references merchant_profiles(id) on delete restrict,
    source_type varchar(24) not null check (source_type in ('PAID_INVOICE','TRIAL','LEGACY','ADMIN')),
    source_id uuid not null,
    valid_from timestamptz not null,
    valid_until timestamptz not null,
    revoked_at timestamptz,
    reason varchar(400),
    created_by_user_id uuid references users(id) on delete restrict,
    created_at timestamptz not null,
    unique (source_type, source_id),
    check (valid_until > valid_from)
);
create index ix_billing_grants_active on billing_entitlement_grants(merchant_id, valid_from, valid_until)
    where revoked_at is null;

create table billing_invoices (
    id uuid primary key,
    invoice_number varchar(44) not null unique,
    merchant_id uuid not null references merchant_profiles(id) on delete restrict,
    plan_version_id uuid not null references billing_plan_versions(id) on delete restrict,
    policy_version_id uuid not null references billing_policy_versions(id) on delete restrict,
    status varchar(25) not null check (status in (
        'PENDING_PAYMENT','PAID','VOID','REFUNDED','DISPUTED'
    )),
    amount_vnd bigint not null check (amount_vnd >= 0),
    period_start timestamptz not null,
    period_end timestamptz not null,
    pricing_snapshot jsonb not null,
    idempotency_key varchar(160) not null,
    request_fingerprint varchar(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    paid_at timestamptz,
    version bigint not null default 0,
    unique (merchant_id, idempotency_key),
    check (period_end > period_start),
    check (expires_at > created_at)
);
create index ix_billing_invoices_merchant_created on billing_invoices(merchant_id, created_at desc);

create table billing_payment_attempts (
    id uuid primary key,
    invoice_id uuid not null references billing_invoices(id) on delete restrict,
    provider varchar(40) not null,
    provider_order_id varchar(180),
    provider_transaction_id varchar(180),
    status varchar(24) not null check (status in (
        'CREATED','PENDING','SUCCEEDED','FAILED','EXPIRED','REFUNDED','DISPUTED'
    )),
    expected_amount_vnd bigint not null check (expected_amount_vnd >= 0),
    received_amount_vnd bigint check (received_amount_vnd >= 0),
    created_at timestamptz not null,
    settled_at timestamptz,
    version bigint not null default 0
);
create unique index ux_billing_provider_order on billing_payment_attempts(provider, provider_order_id)
    where provider_order_id is not null;
create unique index ux_billing_provider_transaction on billing_payment_attempts(provider, provider_transaction_id)
    where provider_transaction_id is not null;

create table billing_webhook_inbox (
    id uuid primary key,
    provider varchar(40) not null,
    provider_event_id varchar(180) not null,
    payload_sha256 varchar(64) not null,
    verified boolean not null,
    processing_status varchar(24) not null check (processing_status in ('PENDING','DONE','ERROR','REJECTED')),
    received_at timestamptz not null,
    processed_at timestamptz,
    attempt_count integer not null default 0,
    last_error_code varchar(80),
    unique (provider, provider_event_id)
);

-- Default plan/policy: data values that may be superseded with immutable versions.
insert into billing_plans(code, display_name) values ('SELLER_STANDARD', 'CoVie Seller');
insert into billing_plan_versions(id, plan_code, revision, price_vnd, cycle_months, effective_at, published_at)
values (gen_random_uuid(), 'SELLER_STANDARD', 1, 10000, 1, '2026-01-01T00:00:00Z', now());
insert into billing_policy_versions(id, revision, onboarding_mode, trial_days, grace_days, effective_at, created_at)
values (gen_random_uuid(), 1, 'PAY_BEFORE_SELL', 0, 3, '2026-01-01T00:00:00Z', now());
