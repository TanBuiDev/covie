package vn.covie.needs.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record NeedResponseCandidateResponse(
        UUID locationVariantId,
        UUID catalogItemId,
        UUID variantId,
        String itemName,
        String variantName,
        NeedResponseItemResponse.MoneyResponse currentPrice,
        boolean available,
        JsonNode availability) {}
