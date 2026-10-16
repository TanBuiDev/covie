-- Manual refund/dispute workflow; this table NEVER transfers money.
-- Only independently confirmed external refunds may revoke paid entitlements.
create table billing_finance_cases (
    id uuid primary key,
    invoice_id uuid not null references billing_invoices(id) on delete restrict,
    merchant_id uuid not null references merchant_profiles(id) on delete restrict,
    kind varchar(24) not null check(kind in ('REFUND','DISPUTE')),
    status varchar(24) not null check(status in ('REQUESTED','REVIEWING','REJECTED','CONFIRMED')),
    reason varchar(2000) not null,
    created_by_user_id uuid not null references users(id) on delete restrict,
    created_at timestamptz not null,
    resolved_by_user_id uuid references users(id) on delete restrict,
    resolved_at timestamptz,
    resolution_note varchar(2000),
    external_refund_reference varchar(180),
    unique(invoice_id,kind),
    check((status not in ('REJECTED','CONFIRMED')) or (resolved_at is not null and resolved_by_user_id is not null)),
    check(status <> 'CONFIRMED' or external_refund_reference is not null)
);
create index ix_billing_finance_cases_status on billing_finance_cases(status,created_at);
