alter table media_assets add column video_duration_ms bigint;
alter table media_assets add column video_codec varchar(32);

alter table media_assets add constraint ck_catalog_video_ready_metadata check (
    upload_purpose <> 'CATALOG_VIDEO' or media_status <> 'READY' or (
        width is not null and width > 0 and height is not null and height > 0
        and video_duration_ms is not null and video_duration_ms between 1 and 60000
        and video_codec is not null and (
            (mime_type = 'video/mp4' and video_codec = 'h264')
            or (mime_type = 'video/webm' and video_codec in ('vp8', 'vp9'))
        )
    )
);
