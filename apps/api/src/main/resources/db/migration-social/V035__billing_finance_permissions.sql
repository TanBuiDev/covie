-- Optional finance least privilege for GLOBAL ADMIN accounts.
-- Feature flag remains OFF until roles are explicitly assigned and tested.
create table billing_finance_permissions (
    user_id uuid primary key references users(id) on delete restrict,
    finance_role varchar(16) not null check (finance_role in ('READER','OPERATOR','APPROVER')),
    assigned_by_user_id uuid not null references users(id) on delete restrict,
    assigned_at timestamptz not null,
    updated_at timestamptz not null
);
