package vn.covie.orders.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.reviews.api.dto.ReviewEligibilityResponse;

public record OrderItemResponse(UUID id, UUID catalogItemId, UUID variantId, UUID locationVariantId,
        CatalogItemType type, long quantity, long unitPrice, long lineTotal, String currency, JsonNode snapshot,
        ReviewEligibilityResponse reviewEligibility) {}
