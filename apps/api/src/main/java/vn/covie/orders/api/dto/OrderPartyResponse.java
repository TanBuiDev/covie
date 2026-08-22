package vn.covie.orders.api.dto;

import java.util.UUID;

public record OrderPartyResponse(UUID userId, UUID merchantId, UUID locationId) {}
