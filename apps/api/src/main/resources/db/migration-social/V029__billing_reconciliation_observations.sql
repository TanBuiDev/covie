-- Read-only reconciliation observations. Never convert a provider GET into a paid invoice here.
create table billing_reconciliation_observations (
    invoice_id uuid primary key references billing_invoices(id) on delete restrict,
    provider varchar(40) not null,
    provider_status varchar(40) not null,
    discrepancy varchar(48) not null,
    last_checked_at timestamptz not null,
    revision bigint not null default 0,
    check (discrepancy in (
      'NONE','PAID_PENDING_WEBHOOK','AMOUNT_MISMATCH','LINK_MISMATCH',
      'ORDER_MISMATCH','UNEXPECTED_STATUS','PROVIDER_FAILURE'
    ))
);
create index ix_billing_reconcile_discrepancy on billing_reconciliation_observations
  (discrepancy, last_checked_at desc);
