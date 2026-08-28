package vn.covie.admin.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantModerationStatus;
import vn.covie.merchants.model.MerchantStatus;

public record AdminShopLocationResponse(
        UUID merchantId,
        String merchantName,
        MerchantStatus merchantStatus,
        MerchantModerationStatus merchantModerationStatus,
        UUID locationId,
        String locationName,
        String addressText,
        MerchantLocationStatus locationStatus,
        boolean pickupEnabled,
        boolean sellerDeliveryEnabled,
        boolean serviceEnabled,
        Instant updatedAt) {}
