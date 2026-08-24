package vn.covie.shared.moderation;

import java.util.Objects;
import java.util.UUID;
import vn.covie.moderation.model.ModerationTargetType;

public record ModerationTargetRef(ModerationTargetType type, UUID id) {
    public ModerationTargetRef {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(id, "id is required");
    }
}
