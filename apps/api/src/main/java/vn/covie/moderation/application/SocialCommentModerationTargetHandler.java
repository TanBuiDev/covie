package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/** Reuses existing admin authorization, idempotency, audit and outbox commands. */
@Component
public class SocialCommentModerationTargetHandler implements ModerationTargetHandler {
    private final JdbcTemplate jdbc;
    public SocialCommentModerationTargetHandler(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public ModerationTargetType supports() { return ModerationTargetType.SOCIAL_COMMENT; }

    @Override @Transactional(readOnly=true)
    public ModerationTargetSnapshot inspect(UUID id) { return snapshot(id,status(id,false)); }

    @Override @Transactional(noRollbackFor=ApiException.class)
    public ModerationTargetMutationResult apply(UUID id, ModerationActionType action, Instant now) {
        String before = status(id,true);
        if (action == ModerationActionType.HIDE && !"ACTIVE".equals(before)
                || action == ModerationActionType.RESTORE && !"HIDDEN".equals(before)) {
            throw new ApiException(ErrorCode.MODERATION_TARGET_STATE_CONFLICT,HttpStatus.CONFLICT,
                    "Comment is not in a compatible moderation state.");
        }
        if (action != ModerationActionType.HIDE && action != ModerationActionType.RESTORE) {
            throw new ApiException(ErrorCode.MODERATION_ACTION_NOT_SUPPORTED,HttpStatus.UNPROCESSABLE_ENTITY,
                    "Comment only supports hide and restore.");
        }
        String after = action == ModerationActionType.HIDE ? "HIDDEN" : "ACTIVE";
        jdbc.update("update catalog_social_comments set status=?,updated_at=? where id=?",
                after, java.sql.Timestamp.from(now), id);
        return new ModerationTargetMutationResult(snapshot(id,before),snapshot(id,after));
    }
    private String status(UUID id,boolean lock) {
        String value = jdbc.query("select status from catalog_social_comments where id=?" +
                (lock ? " for update" : ""),rs -> rs.next() ? rs.getString(1) : null,id);
        if(value == null) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Comment not found.");
        return value;
    }
    private static ModerationTargetSnapshot snapshot(UUID id,String status) {
        return new ModerationTargetSnapshot(ModerationTargetType.SOCIAL_COMMENT,id,status,status);
    }
}
