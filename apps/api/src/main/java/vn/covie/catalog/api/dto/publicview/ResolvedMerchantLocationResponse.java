package vn.covie.catalog.api.dto.publicview;

import java.util.UUID;
import vn.covie.location.application.LocationResolutionSource;

public record ResolvedMerchantLocationResponse(
        UUID id,
        String name,
        String addressText,
        Double distanceMeters,
        LocationResolutionSource resolutionSource) {}
