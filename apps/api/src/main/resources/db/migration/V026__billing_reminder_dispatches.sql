-- Idempotent billing reminders: one notification attempt per merchant/period/day marker.
create table billing_reminder_dispatches (
    merchant_id uuid not null references merchant_profiles(id) on delete restrict,
    period_end timestamptz not null,
    days_before integer not null check (days_before in (1,3,7)),
    created_at timestamptz not null,
    primary key (merchant_id,period_end,days_before)
);
