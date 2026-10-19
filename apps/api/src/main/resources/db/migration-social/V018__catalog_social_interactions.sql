-- Social interactions belong to catalog items, not to reviews or paid orders.
create table catalog_social_reactions (
    catalog_item_id uuid not null references catalog_items(id),
    user_id uuid not null references users(id),
    kind varchar(10) not null,
    created_at timestamptz not null default now(),
    primary key (catalog_item_id, user_id, kind),
    constraint ck_catalog_social_reaction_kind check (kind in ('LIKE','SAVE'))
);
create index ix_catalog_social_reactions_user_kind on catalog_social_reactions(user_id, kind, created_at desc);
create index ix_catalog_social_reactions_item_kind on catalog_social_reactions(catalog_item_id, kind);

create table catalog_social_comments (
    id uuid primary key,
    catalog_item_id uuid not null references catalog_items(id),
    author_user_id uuid not null references users(id),
    body text not null,
    status varchar(10) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_catalog_social_comment_status check (status in ('ACTIVE','HIDDEN','DELETED')),
    constraint ck_catalog_social_comment_length check (char_length(btrim(body)) between 2 and 1000)
);
create index ix_catalog_social_comments_feed on catalog_social_comments(catalog_item_id, created_at desc, id desc) where status='ACTIVE';
create index ix_catalog_social_comments_author_recent on catalog_social_comments(author_user_id, created_at desc);
