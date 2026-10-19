package vn.covie.social.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.social.api.dto.SocialActivityEntry;
import vn.covie.social.api.dto.SocialActivityPage;
import vn.covie.social.api.dto.SocialActivityType;

@Service
public class SocialActivityReadService {
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;
    private final MediaAssetRepository assets;
    private final MediaUrlResolver urls;
    private final boolean enabled;
    public SocialActivityReadService(NamedParameterJdbcTemplate jdbc,CurrentActorProvider actors,ObjectMapper json,
            MediaAssetRepository assets,MediaUrlResolver urls,@Value("${covie.social-v2.activity-enabled:false}") boolean enabled) {
        this.jdbc=jdbc; this.actors=actors; this.json=json; this.assets=assets; this.urls=urls; this.enabled=enabled;
    }

    @Transactional(readOnly=true)
    public SocialActivityPage activity(SocialActivityType type,int limit,String rawCursor) {
        UUID actor=actors.currentActor().userId();
        if (!enabled) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");
        if (type==null || limit<1 || limit>50) throw invalid();
        var cursor=decode(rawCursor,actor,type);
        var params=new MapSqlParameterSource().addValue("actor",actor).addValue("fetchSize",limit+1);
        boolean comments=type==SocialActivityType.COMMENTED;
        String source=comments?"catalog_social_comments r":"catalog_social_reactions r";
        String unique=comments?"r.id":"r.catalog_item_id";
        String ownership=comments?"r.author_user_id=:actor and r.status='ACTIVE'":"r.user_id=:actor and r.kind=:kind";
        if (!comments) params.addValue("kind",type==SocialActivityType.LIKED?"LIKE":"SAVE");
        String before="";
        if (cursor!=null) {
            before=" and (r.created_at,"+unique+")<(:beforeAt,:beforeId)";
            params.addValue("beforeAt",Timestamp.from(cursor.at())).addValue("beforeId",cursor.id());
        }
        String sql="select i.id item_id,i.name item_name,m.name merchant_name,r.created_at activity_at,"+unique+" activity_id,"
                +(comments?"r.id comment_id,r.body comment_excerpt,":"null::uuid comment_id,null::text comment_excerpt,")
                +"cover.id media_id from "+source+" join catalog_items i on i.id=r.catalog_item_id "
                +"join merchant_profiles m on m.id=i.merchant_profile_id "+"""
                left join lateral (
                    select a.id from catalog_item_media cm join media_assets a on a.id=cm.media_asset_id
                    where cm.catalog_item_id=i.id and cm.media_type='IMAGE' and a.media_status='READY'
                      and a.deleted_at is null and a.upload_purpose='CATALOG_IMAGE'
                    order by cm.is_primary desc,cm.sort_order,a.id limit 1
                ) cover on true where
                """+ownership+"""
                 and i.deleted_at is null and i.listing_status='ACTIVE'
                 and i.moderation_status in ('NOT_REQUIRED','APPROVED')
                 and m.deleted_at is null and m.merchant_status='ACTIVE'
                 and m.moderation_status in ('NOT_REQUIRED','APPROVED')
                """+before+" order by r.created_at desc,"+unique+" desc limit :fetchSize";
        var rows=jdbc.query(sql,params,(result,index) -> new ActivityRow(result.getObject("item_id",UUID.class),
                result.getString("item_name"),result.getString("merchant_name"),result.getTimestamp("activity_at").toInstant(),
                result.getObject("activity_id",UUID.class),result.getObject("comment_id",UUID.class),result.getString("comment_excerpt"),
                result.getObject("media_id",UUID.class)));
        boolean more=rows.size()>limit;
        var page=more?rows.subList(0,limit):rows;
        var mediaIds=page.stream().map(ActivityRow::mediaId).filter(Objects::nonNull).distinct().toList();
        Map<UUID,MediaAssetEntity> media=new LinkedHashMap<>();
        if (!mediaIds.isEmpty()) assets.findAllByIdInAndMediaStatusAndPurposeAndDeletedAtIsNull(mediaIds,MediaStatus.READY,MediaPurpose.CATALOG_IMAGE)
                .forEach(asset -> media.put(asset.getId(),asset));
        var entries=page.stream().map(row -> new SocialActivityEntry(row.itemId(),row.name(),row.merchant(),
                media.containsKey(row.mediaId())?urls.publicUrl(media.get(row.mediaId())):null,row.at(),row.commentId(),row.comment(),true)).toList();
        String next=more?encode(new ActivityCursor(1,actor,type,page.getLast().at(),page.getLast().id())):null;
        return new SocialActivityPage(entries,next);
    }

    private ActivityCursor decode(String value,UUID actor,SocialActivityType type) {
        if (value==null) return null;
        try {
            if (value.isBlank() || value.length()>1024) throw invalid();
            var cursor=json.readValue(Base64.getUrlDecoder().decode(value),ActivityCursor.class);
            if (cursor.version()!=1 || !actor.equals(cursor.actor()) || type!=cursor.type() || cursor.at()==null || cursor.id()==null) throw invalid();
            return cursor;
        } catch (Exception exception) { throw invalid(); }
    }
    private String encode(ActivityCursor cursor) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(cursor)); }
        catch (Exception exception) { throw new IllegalArgumentException("Activity cursor cannot be encoded.",exception); }
    }
    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,"Activity query is invalid."); }
    private record ActivityCursor(int version,UUID actor,SocialActivityType type,Instant at,UUID id) {}
    private record ActivityRow(UUID itemId,String name,String merchant,Instant at,UUID id,UUID commentId,String comment,UUID mediaId) {}
}
