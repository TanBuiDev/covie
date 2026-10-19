-- Expiry policy is snapshotted per invoice/merchant period.
-- No retroactive extension if an Admin changes grace days in the future.
alter table billing_invoices add column grace_until timestamptz;
update billing_invoices set grace_until = period_end where grace_until is null;
alter table billing_invoices alter column grace_until set not null;
alter table billing_invoices add constraint ck_billing_invoice_grace check (grace_until >= period_end);

alter table merchant_subscriptions add column grace_until timestamptz;
update merchant_subscriptions set grace_until = period_end where period_end is not null;
alter table merchant_subscriptions add constraint ck_billing_sub_grace check (
    grace_until is null or (period_end is not null and grace_until >= period_end)
);
