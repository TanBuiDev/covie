package vn.covie.social.api;

import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Sensitive moderator-only context; not a public social API. */
@Hidden
@RestController
public class AdminSocialCommentController {
    private final AdminAccessPolicy access;
    private final JdbcTemplate jdbc;
    public AdminSocialCommentController(AdminAccessPolicy access,JdbcTemplate jdbc) {
        this.access=access;this.jdbc=jdbc;
    }
    public record ModerationCommentDetail(UUID id,UUID catalogItemId,String listingTitle,
            String authorName,String body,String status,Instant createdAt) {}
    @GetMapping("/api/v1/admin/catalog-social/comments/{commentId}")
    public ResponseEntity<ModerationCommentDetail> detail(@PathVariable UUID commentId) {
        access.requireAdmin();
        ModerationCommentDetail data = jdbc.query("""
            select c.id,c.catalog_item_id,i.name as listing_title,u.display_name as author_name,
                   c.body,c.status,c.created_at
            from catalog_social_comments c
            join catalog_items i on i.id=c.catalog_item_id
            join users u on u.id=c.author_user_id
            where c.id=?
            """,rs -> rs.next()?new ModerationCommentDetail(
                    rs.getObject("id",UUID.class),rs.getObject("catalog_item_id",UUID.class),
                    rs.getString("listing_title"),rs.getString("author_name"),
                    rs.getString("body"),rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant()):null,commentId);
        if(data==null)throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                HttpStatus.NOT_FOUND,"Comment not found.");
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(data);
    }
}
