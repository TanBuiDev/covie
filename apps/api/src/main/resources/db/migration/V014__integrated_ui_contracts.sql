alter table merchant_profiles
    add column merchant_kind varchar(20) not null default 'SHOP';

alter table merchant_profiles
    add constraint ck_merchant_profile_kind
    check (merchant_kind in ('SHOP', 'PROVIDER')) not valid;

alter table merchant_profiles validate constraint ck_merchant_profile_kind;
