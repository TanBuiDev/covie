package vn.covie.needs.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.covie.needs.model.NeedResponseStatus;

public record NeedResponseResponse(
        UUID id,
        UUID needRequestId,
        UUID merchantId,
        String merchantName,
        UUID merchantLocationId,
        String merchantLocationName,
        Long distanceMeters,
        NeedResponseStatus status,
        String note,
        long version,
        Instant createdAt,
        List<NeedResponseItemResponse> items) {}
