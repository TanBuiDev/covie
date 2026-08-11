create table auth_sessions (
    id uuid primary key,
    user_id uuid not null references users(id),
    token_family_id uuid not null,
    device_name varchar(120) null,
    user_agent text null,
    created_at timestamptz not null,
    last_used_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz null,
    revocation_reason varchar(40) null,
    version bigint not null default 0,
    constraint ux_auth_session_family unique (token_family_id),
    constraint ck_auth_session_revocation_reason check (
        revocation_reason is null or revocation_reason in (
            'LOGOUT','USER_REVOKED','REVOKE_OTHERS','PASSWORD_RESET','REFRESH_REUSE','ADMIN_REVOKED','EXPIRED'
        )
    )
);

create index ix_auth_session_user_active
    on auth_sessions(user_id, expires_at desc)
    where revoked_at is null;

create table auth_refresh_tokens (
    id uuid primary key,
    session_id uuid not null references auth_sessions(id),
    token_hash char(64) not null,
    replaced_by_token_id uuid null,
    expires_at timestamptz not null,
    used_at timestamptz null,
    revoked_at timestamptz null,
    created_at timestamptz not null,
    constraint ux_auth_refresh_token_hash unique (token_hash),
    constraint fk_auth_refresh_replaced_by
        foreign key (replaced_by_token_id) references auth_refresh_tokens(id)
);

create index ix_refresh_session_created on auth_refresh_tokens(session_id, created_at desc);
create index ix_refresh_active_hash on auth_refresh_tokens(token_hash) where revoked_at is null;

create table auth_action_tickets (
    id uuid primary key,
    purpose varchar(30) not null,
    token_hash char(64) not null,
    user_id uuid null references users(id),
    google_subject varchar(255) not null,
    email_normalized varchar(320) not null,
    google_email_verified boolean not null,
    display_name varchar(120) null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    consumed_at timestamptz null,
    constraint ux_auth_action_ticket_hash unique (token_hash),
    constraint ck_auth_action_purpose check (purpose in ('ONBOARDING','PASSWORD_RESET')),
    constraint ck_auth_action_verified_email check (google_email_verified = true)
);

create index ix_auth_action_expiry on auth_action_tickets(expires_at) where consumed_at is null;
create index ix_auth_action_user on auth_action_tickets(user_id, purpose) where consumed_at is null;
