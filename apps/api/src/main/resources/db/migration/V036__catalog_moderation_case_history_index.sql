create index ix_moderation_cases_catalog_recent
    on moderation_cases(target_catalog_item_id, opened_at desc, id desc)
    where target_catalog_item_id is not null;
