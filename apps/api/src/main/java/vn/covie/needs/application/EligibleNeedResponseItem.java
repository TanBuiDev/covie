package vn.covie.needs.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Server-derived response item data safe to persist as an audit snapshot. */
public record EligibleNeedResponseItem(
        UUID catalogItemId,
        UUID variantId,
        UUID locationVariantId,
        long currentPrice,
        String currency,
        JsonNode availabilitySnapshot) {}
