-- Match webhook link id exactly to the payment attempt associated with the invoice.
alter table billing_payment_attempts add column provider_payment_link_id varchar(180);
create unique index ux_billing_payos_link on billing_payment_attempts(provider, provider_payment_link_id)
  where provider_payment_link_id is not null;
