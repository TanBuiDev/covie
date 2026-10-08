package vn.covie.needs.api.dto;

import java.util.UUID;

/** Merchant-owned offer display data; no buyer location or rejected-candidate reason. */
public record NeedEligibleItemResponse(
        UUID locationVariantId,
        UUID catalogItemId,
        UUID variantId,
        String catalogItemName,
        String variantName,
        MoneyResponse currentPrice,
        boolean available) {
    public record MoneyResponse(long amount, String currency) {}
}
