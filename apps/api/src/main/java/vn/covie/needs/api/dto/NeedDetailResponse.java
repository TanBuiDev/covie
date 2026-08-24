package vn.covie.needs.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;

public record NeedDetailResponse(
        UUID id, NeedType type, NeedStatus status, UUID categoryId, String title, String queryText, String description,
        Long requestedQuantity, NeedLocationSummaryResponse locationSummary, int radiusMeters, Instant expiresAt,
        long responseCount, UUID fulfilledOrderId, Instant createdAt, Instant closedAt) {}
