-- Server-bound, one-use quote identities. A browser may never set the invoice amount.
create table billing_quotes (
    id uuid primary key,
    merchant_id uuid not null references merchant_profiles(id) on delete restrict,
    plan_version_id uuid not null references billing_plan_versions(id) on delete restrict,
    voucher_code varchar(32),
    amount_due_vnd bigint not null check (amount_due_vnd > 0),
    created_at timestamptz not null,
    expires_at timestamptz not null,
    consumed_by_idempotency_key varchar(80),
    check (expires_at > created_at)
);
create index ix_billing_quotes_expiry on billing_quotes(expires_at);
