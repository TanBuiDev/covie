package vn.covie.admin.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;

/** Intentionally excludes buyer identity, precise coordinates, snapshots, and contact data. */
public record AdminNeedSummaryResponse(
        UUID id,
        NeedType type,
        String title,
        UUID categoryId,
        String categoryName,
        NeedStatus status,
        int searchRadiusMeters,
        long responseCount,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {}
