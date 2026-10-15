-- Refund approval requires traceable distinct reviewer and approver identities.
-- This migration does not execute a refund or alter any paid invoice.
alter table billing_finance_cases
  add column reviewed_by_user_id uuid references users(id) on delete restrict,
  add column reviewed_at timestamptz;
create index ix_billing_finance_case_reviewer on billing_finance_cases(reviewed_by_user_id)
  where reviewed_by_user_id is not null;
