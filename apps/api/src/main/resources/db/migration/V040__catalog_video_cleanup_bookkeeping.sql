alter table media_assets add column storage_cleanup_attempted_at timestamptz;
alter table media_assets add column storage_deleted_at timestamptz;
