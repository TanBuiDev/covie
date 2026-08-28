package vn.covie.catalog.api.dto.publicview;

import java.util.UUID;
import vn.covie.location.application.LocationResolutionSource;

public record ResolvedMerchantLocationResponse(
        UUID id,
        String name,
        String addressText,
        Double distanceMeters,
        LocationResolutionSource resolutionSource,
        Boolean pickupEnabled,
        Boolean sellerDeliveryEnabled,
        Boolean serviceEnabled,
        Boolean openNow) {
    public ResolvedMerchantLocationResponse(UUID id, String name, String addressText, Double distanceMeters,
            LocationResolutionSource resolutionSource) {
        this(id, name, addressText, distanceMeters, resolutionSource, null, null, null, null);
    }
}
