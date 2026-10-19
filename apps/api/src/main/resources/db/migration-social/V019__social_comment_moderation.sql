-- Integrate social comments with the existing audited moderation case pipeline.
alter table moderation_cases add column target_social_comment_id uuid
    references catalog_social_comments(id) on delete restrict;
alter table moderation_cases drop constraint ck_moderation_target_type;
alter table moderation_cases add constraint ck_moderation_target_type
    check (target_type in ('USER','MERCHANT','CATALOG_ITEM','NEED_REQUEST','REVIEW','SOCIAL_COMMENT'));
alter table moderation_cases drop constraint ck_moderation_exactly_one_target;
alter table moderation_cases add constraint ck_moderation_exactly_one_target
    check (num_nonnulls(target_user_id,target_merchant_profile_id,target_catalog_item_id,
        target_need_request_id,target_review_id,target_social_comment_id) = 1);
alter table moderation_cases drop constraint ck_moderation_target_matches_type;
alter table moderation_cases add constraint ck_moderation_target_matches_type check (
    (target_type='USER' and target_user_id is not null)
    or (target_type='MERCHANT' and target_merchant_profile_id is not null)
    or (target_type='CATALOG_ITEM' and target_catalog_item_id is not null)
    or (target_type='NEED_REQUEST' and target_need_request_id is not null)
    or (target_type='REVIEW' and target_review_id is not null)
    or (target_type='SOCIAL_COMMENT' and target_social_comment_id is not null)
);
create index ix_moderation_cases_comment on moderation_cases(target_social_comment_id,opened_at desc)
    where target_social_comment_id is not null;
