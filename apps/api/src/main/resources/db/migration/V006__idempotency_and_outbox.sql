create table idempotency_records (
    id uuid primary key,
    actor_id uuid not null references users(id),
    operation varchar(120) not null,
    idempotency_key_hash char(64) not null,
    request_hash char(64) not null,
    status varchar(20) not null,
    response_status integer null,
    response_body jsonb null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    constraint ck_idempotency_status check (status in ('IN_PROGRESS','COMPLETED')),
    unique(actor_id, operation, idempotency_key_hash)
);
create index ix_idempotency_expiry on idempotency_records(expires_at);

create table outbox_events (
    id uuid primary key,
    event_type varchar(120) not null,
    aggregate_type varchar(80) not null,
    aggregate_id uuid not null,
    payload jsonb not null,
    occurred_at timestamptz not null,
    status varchar(20) not null,
    attempts integer not null default 0,
    next_attempt_at timestamptz null,
    published_at timestamptz null,
    last_error text null,
    constraint ck_outbox_status check (status in ('PENDING','PROCESSING','PUBLISHED','FAILED')),
    constraint ck_outbox_attempts check (attempts >= 0)
);
create index ix_outbox_dispatch
    on outbox_events(status, next_attempt_at, occurred_at)
    where status in ('PENDING','FAILED');
