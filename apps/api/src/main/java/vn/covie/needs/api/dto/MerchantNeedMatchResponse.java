package vn.covie.needs.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.needs.model.NeedType;

public record MerchantNeedMatchResponse(Need need, ResolvedLocation resolvedLocation, BuyerArea buyerArea,
        long distanceMeters, boolean alreadyResponded, ResponseConstraints responseConstraints) {
    public record Need(UUID id, NeedType type, String title, String description, UUID categoryId, Instant expiresAt) {}
    public record ResolvedLocation(UUID id, String name) {}
    public record BuyerArea(String label) {}
    public record ResponseConstraints(int maxItems) {}
}
