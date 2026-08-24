package vn.covie.moderation.application;
import java.util.UUID;
import vn.covie.moderation.model.ModerationTargetType;
public record ModerationTargetSnapshot(ModerationTargetType type, UUID targetId, String businessStatus, String moderationStatus) {}
