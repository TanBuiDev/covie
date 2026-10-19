-- payOS payment links are separate from buyer checkout.
create sequence billing_payos_order_code_seq start with 4000000000 increment by 1;
alter table billing_invoices add column payos_order_code bigint unique;
alter table billing_payment_attempts add column checkout_url text;
alter table billing_payment_attempts add column qr_payload text;
create index ix_billing_attempt_invoice on billing_payment_attempts(invoice_id, created_at desc);
