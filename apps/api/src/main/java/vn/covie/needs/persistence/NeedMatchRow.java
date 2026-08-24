package vn.covie.needs.persistence;

import java.time.Instant;
import java.util.UUID;
import vn.covie.needs.model.NeedType;

public record NeedMatchRow(UUID needId, NeedType type, UUID categoryId, String title, String description, Instant expiresAt,
        UUID merchantLocationId, String merchantLocationName, String buyerAreaLabel, double textRank, long distanceMeters,
        boolean pickupEnabled, boolean alreadyResponded) {}
