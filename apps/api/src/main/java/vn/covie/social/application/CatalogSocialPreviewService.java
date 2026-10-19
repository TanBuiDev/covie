package vn.covie.social.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.social.api.dto.SocialItemPreview;

@Service
public class CatalogSocialPreviewService {
    private static final Pattern UUID_TEXT=Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private final NamedParameterJdbcTemplate jdbc;
    private final MediaAssetRepository assets;
    private final MediaUrlResolver urls;
    private final boolean enabled;
    public CatalogSocialPreviewService(NamedParameterJdbcTemplate jdbc, MediaAssetRepository assets, MediaUrlResolver urls,
            @Value("${covie.social-v2.previews-enabled:false}") boolean enabled) {
        this.jdbc=jdbc; this.assets=assets; this.urls=urls; this.enabled=enabled;
    }

    @Transactional(readOnly=true)
    public Map<UUID,SocialItemPreview> previews(List<String> rawIds) {
        if (!enabled) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");
        if (rawIds==null || rawIds.isEmpty() || rawIds.size()>40
                || rawIds.stream().anyMatch(value -> value==null || !UUID_TEXT.matcher(value).matches()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,"Supply between 1 and 40 catalog item IDs.");
        var ids=new LinkedHashSet<>(rawIds.stream().map(UUID::fromString).toList());
        var rows=jdbc.query("""
                select i.id,i.social_caption,preview.id as media_id,preview.sort_order,preview.is_primary
                from catalog_items i join merchant_profiles m on m.id=i.merchant_profile_id
                left join lateral (
                    select a.id,cm.sort_order,cm.is_primary
                    from catalog_item_media cm join media_assets a on a.id=cm.media_asset_id
                    where cm.catalog_item_id=i.id
                      and ((cm.media_type='IMAGE' and a.upload_purpose='CATALOG_IMAGE')
                           or (:video and cm.media_type='VIDEO' and a.upload_purpose='CATALOG_VIDEO'))
                      and a.media_status='READY' and a.deleted_at is null
                    order by case when :video and exists (select 1 from catalog_item_media mixed where mixed.catalog_item_id=i.id and mixed.media_type='VIDEO')
                                  then 0 when cm.is_primary then 0 else 1 end,cm.sort_order,a.id limit 6
                ) preview on true
                where i.id in (:ids) and i.deleted_at is null and i.listing_status='ACTIVE'
                  and i.moderation_status in ('NOT_REQUIRED','APPROVED')
                  and m.deleted_at is null and m.merchant_status='ACTIVE'
                  and m.moderation_status in ('NOT_REQUIRED','APPROVED')
                order by i.id,case when :video and exists (select 1 from catalog_item_media mixed where mixed.catalog_item_id=i.id and mixed.media_type='VIDEO')
                                  then 0 when preview.is_primary then 0 else 1 end,preview.sort_order,preview.id
                """,new MapSqlParameterSource("ids",ids).addValue("video",urls.catalogVideoEnabled()),(result,index) -> new PreviewRow(result.getObject("id",UUID.class),
                        result.getString("social_caption"),result.getObject("media_id",UUID.class),result.getInt("sort_order"),result.getBoolean("is_primary")));
        var mediaIds=rows.stream().map(PreviewRow::mediaId).filter(Objects::nonNull).distinct().toList();
        Map<UUID,MediaAssetEntity> mediaById=new LinkedHashMap<>();
        if (!mediaIds.isEmpty()) {
            if (urls.catalogVideoEnabled()) assets.findAllById(mediaIds).stream()
                    .filter(media -> media.getMediaStatus()==MediaStatus.READY && media.getDeletedAt()==null
                            && (media.getPurpose()==MediaPurpose.CATALOG_IMAGE || media.getPurpose()==MediaPurpose.CATALOG_VIDEO))
                    .forEach(media -> mediaById.put(media.getId(),media));
            else assets.findAllByIdInAndMediaStatusAndPurposeAndDeletedAtIsNull(
                    mediaIds,MediaStatus.READY,MediaPurpose.CATALOG_IMAGE).forEach(media -> mediaById.put(media.getId(),media));
        }
        Map<UUID,String> captions=new LinkedHashMap<>();
        Map<UUID,List<SocialItemPreview.Media>> galleries=new LinkedHashMap<>();
        for (var row : rows) {
            captions.put(row.itemId(),row.caption());
            var media=mediaById.get(row.mediaId());
            if (media==null) continue;
            var gallery=galleries.computeIfAbsent(row.itemId(),ignored -> new ArrayList<>());
            gallery.add(new SocialItemPreview.Media(media.getId(),media.getMimeType(),urls.publicUrl(media),row.sortOrder(),
                    urls.catalogVideoEnabled() ? row.primary() : gallery.isEmpty(),vn.covie.media.api.dto.CatalogVideoMetadata.from(media)));
        }
        Map<UUID,SocialItemPreview> result=new LinkedHashMap<>();
        for (UUID id : ids) if (captions.containsKey(id)) {
            var gallery=galleries.getOrDefault(id,List.of());
            if (urls.catalogVideoEnabled() && gallery.stream().noneMatch(SocialItemPreview.Media::isCover)) {
                for (int index=0;index<gallery.size();index++) {
                    var entry=gallery.get(index);
                    if (entry.video()!=null) continue;
                    gallery.set(index,new SocialItemPreview.Media(entry.id(),entry.mimeType(),entry.url(),entry.sortOrder(),true,null));
                    break;
                }
            }
            result.put(id,new SocialItemPreview(id,captions.get(id),gallery));
        }
        return result;
    }

    private record PreviewRow(UUID itemId,String caption,UUID mediaId,int sortOrder,boolean primary) {}
}
