-- Admin-controlled trial: limit free trial to one MerchantProfile per owner identity.
create table billing_trial_claims (
    owner_user_id uuid primary key references users(id) on delete restrict,
    merchant_id uuid not null unique references merchant_profiles(id) on delete restrict,
    policy_version_id uuid not null references billing_policy_versions(id) on delete restrict,
    claimed_at timestamptz not null
);
