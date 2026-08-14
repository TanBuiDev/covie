package vn.covie.merchants.api.dto;

import java.util.UUID;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.PinSource;

public record MerchantLocationResponse(
        UUID id,
        UUID merchantId,
        String name,
        String addressText,
        UUID locationAreaId,
        double latitude,
        double longitude,
        PinSource pinSource,
        String timezone,
        MerchantLocationStatus status,
        boolean pickupEnabled,
        boolean sellerDeliveryEnabled,
        boolean serviceEnabled,
        Long minimumDeliveryOrder,
        String currency,
        long version) {}
