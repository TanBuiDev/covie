alter table catalog_item_media drop constraint ck_catalog_item_media_type;
alter table catalog_item_media add constraint ck_catalog_item_media_type check (media_type in ('IMAGE', 'VIDEO'));
