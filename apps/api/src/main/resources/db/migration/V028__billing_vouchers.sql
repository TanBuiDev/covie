-- Seller subscription-only vouchers. These never apply to buyer checkout/orders.
-- Reservation capacity is determined from invoices in PENDING_PAYMENT/PAID;
-- a pending payment cannot silently release inventory until payOS confirms finality.
create table billing_vouchers (
    code varchar(32) primary key,
    plan_code varchar(60) references billing_plans(code) on delete restrict,
    discount_type varchar(12) not null check (discount_type in ('FIXED','PERCENT')),
    discount_value bigint not null check (discount_value > 0),
    max_redemptions integer not null check (max_redemptions between 1 and 100000),
    per_merchant_limit integer not null check (per_merchant_limit between 1 and 100),
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    enabled boolean not null default true,
    created_by_user_id uuid not null references users(id) on delete restrict,
    created_at timestamptz not null,
    disabled_by_user_id uuid references users(id) on delete restrict,
    disabled_at timestamptz,
    check (ends_at > starts_at),
    check ((discount_type = 'PERCENT' and discount_value < 10000)
       or (discount_type = 'FIXED' and discount_value <= 1000000000))
);

create table billing_voucher_redemptions (
    invoice_id uuid primary key references billing_invoices(id) on delete restrict,
    voucher_code varchar(32) not null references billing_vouchers(code) on delete restrict,
    merchant_id uuid not null references merchant_profiles(id) on delete restrict,
    original_amount_vnd bigint not null check(original_amount_vnd > 0),
    discount_vnd bigint not null check(discount_vnd > 0),
    created_at timestamptz not null,
    check (discount_vnd < original_amount_vnd)
);
create index ix_billing_voucher_redemptions_code on billing_voucher_redemptions(voucher_code,merchant_id);
