package vn.covie.needs.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;

public record NeedSummaryResponse(UUID id, NeedType type, NeedStatus status, UUID categoryId, String title,
        int radiusMeters, Instant expiresAt, long responseCount, Instant createdAt) {}
