package vn.covie.social.application;

import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.social.api.dto.SocialComment;
import vn.covie.social.api.dto.SocialCommentPage;
import vn.covie.social.api.dto.SocialItemSummary;

@Service
public class CatalogSocialService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final CurrentActorProvider actors;
    private final UuidV7Generator ids;

    public CatalogSocialService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named,
            CurrentActorProvider actors, UuidV7Generator ids) {
        this.jdbc=jdbc; this.named=named; this.actors=actors; this.ids=ids;
    }

    // Optional viewer context: public discovery is readable without signing in.
    private UUID viewer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) return null;
        return UUID.fromString(jwt.getSubject());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Item not found.");
    }

    private void requireVisible(UUID itemId) {
        Integer visible = jdbc.queryForObject("""
            select count(*) from catalog_items i
            join merchant_profiles m on m.id=i.merchant_profile_id
            where i.id=? and i.deleted_at is null and i.listing_status='ACTIVE'
              and i.moderation_status in ('NOT_REQUIRED','APPROVED')
              and m.deleted_at is null and m.merchant_status='ACTIVE'
              and m.moderation_status in ('NOT_REQUIRED','APPROVED')
            """, Integer.class, itemId);
        if (visible == null || visible == 0) throw notFound();
    }

    @Transactional(readOnly = true)
    public Map<UUID, SocialItemSummary> summaries(List<UUID> rawIds) {
        if (rawIds == null || rawIds.isEmpty() || rawIds.size() > 40 || rawIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Supply between 1 and 40 catalog item IDs.");
        }
        var unique = new LinkedHashSet<>(rawIds);
        var params = new MapSqlParameterSource().addValue("ids", unique)
                .addValue("viewer", viewer(), Types.OTHER);
        List<SocialItemSummary> rows = named.query("""
            select i.id,
              (select count(*) from catalog_social_reactions r where r.catalog_item_id=i.id and r.kind='LIKE') likes,
              (select count(*) from catalog_social_comments c where c.catalog_item_id=i.id and c.status='ACTIVE') comments,
              exists(select 1 from catalog_social_reactions r where r.catalog_item_id=i.id
                     and r.kind='LIKE' and r.user_id=:viewer) liked,
              exists(select 1 from catalog_social_reactions r where r.catalog_item_id=i.id
                     and r.kind='SAVE' and r.user_id=:viewer) saved
            from catalog_items i
            join merchant_profiles m on m.id=i.merchant_profile_id
            where i.id in (:ids)
              and i.deleted_at is null and i.listing_status='ACTIVE'
              and i.moderation_status in ('NOT_REQUIRED','APPROVED')
              and m.deleted_at is null and m.merchant_status='ACTIVE'
              and m.moderation_status in ('NOT_REQUIRED','APPROVED')
            """, params, (rs,n) -> new SocialItemSummary(rs.getObject("id",UUID.class),
                        rs.getLong("likes"),rs.getLong("comments"),rs.getBoolean("liked"),rs.getBoolean("saved")));
        Map<UUID, SocialItemSummary> result = new LinkedHashMap<>();
        for (var row : rows) result.put(row.itemId(), row);
        return result;
    }

    @Transactional
    public SocialItemSummary react(UUID itemId, String kind, boolean enabled) {
        if (!"LIKE".equals(kind) && !"SAVE".equals(kind)) throw new IllegalArgumentException("Invalid reaction kind.");
        UUID userId = actors.currentActor().userId();
        requireVisible(itemId);
        if (enabled) {
            jdbc.update("""
                insert into catalog_social_reactions(catalog_item_id,user_id,kind)
                values (?,?,?) on conflict do nothing
                """, itemId,userId,kind);
        } else {
            jdbc.update("delete from catalog_social_reactions where catalog_item_id=? and user_id=? and kind=?",
                    itemId,userId,kind);
        }
        return summaries(List.of(itemId)).get(itemId);
    }

    @Transactional(readOnly = true)
    public SocialCommentPage comments(UUID itemId, Integer rawLimit, UUID before) {
        requireVisible(itemId);
        int limit = rawLimit == null ? 5 : rawLimit;
        if (limit < 1 || limit > 20) throw new ApiException(ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY, "Comment limit must be between 1 and 20.");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("itemId",itemId).addValue("before",before, Types.OTHER)
                .addValue("viewer",viewer(),Types.OTHER).addValue("limit",limit+1);
        List<SocialComment> rows = named.query("""
            select c.id,c.catalog_item_id,c.body,c.created_at,
                   case when u.account_status='ACTIVE' and u.deleted_at is null
                     then u.display_name else 'Người dùng CoVie' end author_name,
                   (c.author_user_id=:viewer) own
              from catalog_social_comments c join users u on u.id=c.author_user_id
             where c.catalog_item_id=:itemId and c.status='ACTIVE'
               and (cast(:before as uuid) is null or (c.created_at,c.id) <
                  (select older.created_at,older.id from catalog_social_comments older
                   where older.id=:before and older.catalog_item_id=:itemId))
             order by c.created_at desc,c.id desc limit :limit
            """, params, (rs,n) -> new SocialComment(rs.getObject("id",UUID.class),
                  rs.getObject("catalog_item_id",UUID.class),rs.getString("author_name"),
                  rs.getString("body"),rs.getTimestamp("created_at").toInstant(),rs.getBoolean("own")));
        boolean more = rows.size() > limit;
        List<SocialComment> page = more ? new ArrayList<>(rows.subList(0,limit)) : rows;
        return new SocialCommentPage(page, more ? page.get(page.size()-1).id() : null);
    }

    @Transactional
    public SocialComment addComment(UUID itemId, String body) {
        UUID userId = actors.currentActor().userId();
        requireVisible(itemId);
        String normalized = body == null ? "" : body.trim();
        if (normalized.length() < 2 || normalized.length() > 1000) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Comment must be 2 to 1000 characters.");
        }
        // Serialize an author's writes, avoiding concurrent rate-limit bypass.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtext(?))", userId.toString());
        Long recent = jdbc.queryForObject("""
            select count(*) from catalog_social_comments
            where author_user_id=? and created_at > now()-interval '60 seconds'
            """, Long.class,userId);
        if (recent != null && recent >= 5) {
            throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many comments. Try again shortly.");
        }
        UUID id = ids.generate();
        jdbc.update("""
            insert into catalog_social_comments(id,catalog_item_id,author_user_id,body)
            values (?,?,?,?)
            """, id,itemId,userId,normalized);
        String authorName = jdbc.queryForObject("select display_name from users where id=?",
                String.class,userId);
        return new SocialComment(id,itemId,authorName,normalized,Instant.now(),true);
    }

    @Transactional
    public void deleteComment(UUID itemId, UUID commentId) {
        UUID userId = actors.currentActor().userId();
        int changed = jdbc.update("""
            update catalog_social_comments set status='DELETED',updated_at=now()
            where id=? and catalog_item_id=? and author_user_id=? and status='ACTIVE'
            """,commentId,itemId,userId);
        if (changed == 0) throw notFound();
    }
}
