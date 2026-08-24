package vn.covie.needs.api.dto;

import java.util.UUID;

public record NeedResponseItemResponse(
        UUID id,
        UUID locationVariantId,
        UUID catalogItemId,
        UUID variantId,
        MoneyResponse priceAtResponse,
        MoneyResponse currentPrice,
        boolean available,
        boolean actionable) {
    public record MoneyResponse(long amount, String currency) {}
}
