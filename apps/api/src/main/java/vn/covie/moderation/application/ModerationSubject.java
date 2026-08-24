package vn.covie.moderation.application;

import java.util.UUID;
import vn.covie.moderation.model.ModerationTargetType;

public record ModerationSubject(
        ModerationTargetType targetType,
        UUID ownerUserId,
        UUID merchantId,
        UUID categoryId,
        UUID locationAreaId) {}
