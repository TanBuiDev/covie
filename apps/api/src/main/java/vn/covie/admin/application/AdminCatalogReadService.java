package vn.covie.admin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.api.dto.AdminCatalogItemDetailResponse;
import vn.covie.admin.api.dto.AdminCatalogCasePageResponse;
import vn.covie.admin.api.dto.AdminCatalogItemSummaryResponse;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.catalog.api.dto.publicview.PublicMediaResponse;
import vn.covie.catalog.model.*;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.moderation.api.dto.ModerationCaseSummaryResponse;
import vn.covie.moderation.model.*;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class AdminCatalogReadService {
    private static final String FROM = " from catalog_items i join merchant_profiles m on m.id=i.merchant_profile_id join categories c on c.id=i.primary_category_id ";
    private static final String SELECT = """
        select i.id,i.name,i.item_type,i.listing_kind,i.merchant_profile_id,m.name merchant_name,
          i.primary_category_id,c.name category_name,i.listing_status,i.moderation_status,
          i.created_at,i.updated_at,i.deleted_at,i.social_caption is not null has_caption,
          exists (select 1 from catalog_item_media video join media_assets asset on asset.id=video.media_asset_id
                  where video.catalog_item_id=i.id and video.media_type='VIDEO'
                    and asset.upload_purpose='CATALOG_VIDEO' and asset.deleted_at is null) has_video,
          (select cm.media_asset_id from catalog_item_media cm join media_assets a on a.id=cm.media_asset_id
           where cm.catalog_item_id=i.id and a.media_status='READY' and a.deleted_at is null
             and a.upload_purpose='CATALOG_IMAGE'
           order by cm.is_primary desc,cm.sort_order,cm.media_asset_id limit 1) cover_id
        """;
    private final AdminAccessPolicy access;
    private final AdminPageRequestValidator pages;
    private final NamedParameterJdbcTemplate jdbc;
    private final MediaAssetRepository assets;
    private final MediaUrlResolver urls;
    private final boolean enabled;
    private final ObjectMapper json;

    public AdminCatalogReadService(AdminAccessPolicy access, AdminPageRequestValidator pages,
            NamedParameterJdbcTemplate jdbc, MediaAssetRepository assets, MediaUrlResolver urls, ObjectMapper json,
            @Value("${covie.social-v2.admin-reads-enabled:false}") boolean enabled) {
        this.access=access; this.pages=pages; this.jdbc=jdbc; this.assets=assets; this.urls=urls; this.json=json; this.enabled=enabled;
    }

    @Transactional(readOnly=true)
    public AdminPageResponse<AdminCatalogItemSummaryResponse> list(int page, int size, String q,
            CatalogItemType type, ListingKind listingKind, ListingStatus listingStatus,
            CatalogModerationStatus moderationStatus, UUID merchantId, String sort) {
        requireEnabled();
        var request = pages.validate(page,size,sort == null ? "UPDATED_DESC" : sort);
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE || q != null && q.length() > 220) throw invalid();
        StringBuilder where = new StringBuilder(" where true");
        var params = new MapSqlParameterSource().addValue("limit",size).addValue("offset",offset);
        filter(where,params,"i.item_type","type",type);
        filter(where,params,"i.listing_kind","listingKind",listingKind);
        filter(where,params,"i.listing_status","listingStatus",listingStatus);
        filter(where,params,"i.moderation_status","moderationStatus",moderationStatus);
        if (merchantId != null) { where.append(" and i.merchant_profile_id=:merchantId"); params.addValue("merchantId",merchantId); }
        if (q != null && !q.isBlank()) {
            String query = q.strip().replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
            where.append(" and (i.name ilike :q or m.name ilike :q or cast(i.id as text) ilike :q or cast(m.id as text) ilike :q)");
            params.addValue("q","%" + query + "%");
        }
        long total = jdbc.queryForObject("select count(*)" + FROM + where,params,Long.class);
        String order = switch (request.sort()) {
            case "CREATED_ASC" -> " order by i.created_at asc,i.id asc";
            case "CREATED_DESC" -> " order by i.created_at desc,i.id desc";
            default -> " order by i.updated_at desc,i.id desc";
        };
        List<ItemRow> rows = jdbc.query(SELECT + FROM + where + order + " limit :limit offset :offset",params,this::row);
        Map<UUID,PublicMediaResponse> covers = publicMedia(rows.stream().map(ItemRow::coverId).filter(java.util.Objects::nonNull).toList());
        return new AdminPageResponse<>(rows.stream().map(entry -> entry.item().withPrimaryMedia(covers.get(entry.coverId()))).toList(),total,page,size);
    }

    @Transactional(readOnly=true)
    public AdminCatalogItemDetailResponse detail(UUID itemId) {
        requireEnabled();
        var params = new MapSqlParameterSource("id",itemId);
        ItemRow row = jdbc.query(SELECT + FROM + " where i.id=:id",params,this::row).stream().findFirst().orElseThrow(AdminCatalogReadService::notFound);
        var content = jdbc.queryForMap("select description,social_caption from catalog_items where id=:id",params);
        var mediaRows = jdbc.query("""
            select a.id,a.mime_type,a.media_status,cm.sort_order,cm.is_primary
            from catalog_item_media cm join media_assets a on a.id=cm.media_asset_id
            where cm.catalog_item_id=:id and a.deleted_at is null
              and (a.upload_purpose<>'CATALOG_VIDEO' or :video)
            order by case when :video and exists (select 1 from catalog_item_media mixed where mixed.catalog_item_id=:id and mixed.media_type='VIDEO')
                          then 0 when cm.is_primary then 0 else 1 end,cm.sort_order,a.id
            """,params.addValue("video",urls.catalogVideoEnabled()),(rs,index) -> new AdminCatalogItemDetailResponse.Media(rs.getObject("id",UUID.class),
                    rs.getString("mime_type"),rs.getString("media_status"),rs.getInt("sort_order"),rs.getBoolean("is_primary"),null,null));
        Map<UUID,PublicMediaResponse> resolved = publicMedia(mediaRows.stream().filter(media -> "READY".equals(media.status())).map(AdminCatalogItemDetailResponse.Media::id).toList());
        var media = mediaRows.stream().map(entry -> new AdminCatalogItemDetailResponse.Media(entry.id(),entry.contentType(),
                entry.status(),entry.sortOrder(),entry.cover(),resolved.containsKey(entry.id()) ? resolved.get(entry.id()).url() : null,
                resolved.containsKey(entry.id()) ? resolved.get(entry.id()).video() : null)).toList();
        var locations = jdbc.query("""
            select distinct l.id,l.name,l.location_status from location_variants offer
            join variants v on v.id=offer.variant_id join merchant_locations l on l.id=offer.merchant_location_id
            where v.catalog_item_id=:id order by l.name,l.id
            """,params,(rs,index) -> new AdminCatalogItemDetailResponse.Location(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("location_status")));
        var cases = casePage(itemId,50,null);
        return new AdminCatalogItemDetailResponse(row.item().withPrimaryMedia(resolved.get(row.coverId())),(String)content.get("description"),media,locations,
                cases.items(),cases.total(),cases.nextCursor(),(String)content.get("social_caption"));
    }

    @Transactional(readOnly=true)
    public AdminCatalogCasePageResponse moderationCases(UUID itemId, int limit, String cursor) {
        requireEnabled();
        if (limit < 1 || limit > 100) throw invalid();
        if (jdbc.queryForObject("select count(*) from catalog_items where id=:id",Map.of("id",itemId),Integer.class) == 0) throw notFound();
        return casePage(itemId,limit,cursor);
    }

    private AdminCatalogCasePageResponse casePage(UUID itemId, int limit, String rawCursor) {
        CaseCursor cursor = decodeCursor(rawCursor,itemId);
        var params = new MapSqlParameterSource("id",itemId).addValue("limit",limit + 1);
        String after = "";
        if (cursor != null) {
            after = " and (opened_at,id)<(:beforeAt,:beforeId)";
            params.addValue("beforeAt",Timestamp.from(cursor.openedAt())).addValue("beforeId",cursor.id());
        }
        long total = jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=:id",params,Long.class);
        var rows = jdbc.query("""
            select id,case_type,case_status,reason_code,opened_at,resolved_at from moderation_cases
            where target_catalog_item_id=:id
            """ + after + " order by opened_at desc,id desc limit :limit",params,(rs,index) -> new ModerationCaseSummaryResponse(rs.getObject("id",UUID.class),
                    ModerationCaseType.valueOf(rs.getString("case_type")),ModerationCaseStatus.valueOf(rs.getString("case_status")),
                    ModerationTargetType.CATALOG_ITEM,itemId,rs.getString("reason_code"),instant(rs,"opened_at"),instant(rs,"resolved_at")));
        boolean more = rows.size() > limit;
        var result = more ? List.copyOf(rows.subList(0,limit)) : rows;
        String next = more ? encodeCursor(new CaseCursor(1,itemId,result.getLast().openedAt(),result.getLast().id())) : null;
        return new AdminCatalogCasePageResponse(result,next,more,total);
    }

    private CaseCursor decodeCursor(String value, UUID itemId) {
        if (value == null) return null;
        try {
            if (value.isBlank() || value.length() > 1024) throw invalid();
            var cursor = json.readValue(Base64.getUrlDecoder().decode(value),CaseCursor.class);
            if (cursor.version() != 1 || !itemId.equals(cursor.itemId()) || cursor.openedAt() == null || cursor.id() == null) throw invalid();
            return cursor;
        } catch (Exception exception) { throw invalid(); }
    }

    private String encodeCursor(CaseCursor cursor) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(cursor)); }
        catch (Exception exception) { throw new IllegalArgumentException("Case cursor cannot be encoded.",exception); }
    }

    private Map<UUID,PublicMediaResponse> publicMedia(List<UUID> mediaIds) {
        Map<UUID,PublicMediaResponse> result = new LinkedHashMap<>();
        if (!mediaIds.isEmpty()) for (var asset : assets.findAllById(mediaIds)) {
            if (asset.getMediaStatus() == MediaStatus.READY && urls.includeCatalogMedia(asset)) result.put(asset.getId(),new PublicMediaResponse(asset.getId(),asset.getMimeType(),urls.publicUrl(asset),vn.covie.media.api.dto.CatalogVideoMetadata.from(asset)));
        }
        return result;
    }

    private ItemRow row(ResultSet rs, int index) throws SQLException {
        return new ItemRow(new AdminCatalogItemSummaryResponse(rs.getObject("id",UUID.class),rs.getString("name"),
                CatalogItemType.valueOf(rs.getString("item_type")),ListingKind.valueOf(rs.getString("listing_kind")),
                rs.getObject("merchant_profile_id",UUID.class),rs.getString("merchant_name"),rs.getObject("primary_category_id",UUID.class),
                rs.getString("category_name"),ListingStatus.valueOf(rs.getString("listing_status")),CatalogModerationStatus.valueOf(rs.getString("moderation_status")),
                instant(rs,"created_at"),instant(rs,"updated_at"),instant(rs,"deleted_at"),null,rs.getBoolean("has_caption"),rs.getBoolean("has_video")),rs.getObject("cover_id",UUID.class));
    }

    private void requireEnabled() { access.requireAdmin(); if (!enabled) throw notFound(); }
    private static Instant instant(ResultSet rs, String column) throws SQLException { Timestamp timestamp=rs.getTimestamp(column); return timestamp == null ? null : timestamp.toInstant(); }
    private static void filter(StringBuilder where, MapSqlParameterSource params, String column, String key, Enum<?> value) {
        if (value != null) { where.append(" and ").append(column).append("=:").append(key); params.addValue(key,value.name()); }
    }
    private static ApiException notFound() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Catalog item not found."); }
    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,"Catalog query is invalid."); }
    private record ItemRow(AdminCatalogItemSummaryResponse item, UUID coverId) {}
    private record CaseCursor(int version, UUID itemId, Instant openedAt, UUID id) {}
}
