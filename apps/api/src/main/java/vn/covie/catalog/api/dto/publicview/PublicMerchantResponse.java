package vn.covie.catalog.api.dto.publicview;

import java.util.List;
import java.util.UUID;

public record PublicMerchantResponse(
        UUID id,
        String name,
        String slug,
        String description,
        PublicMediaResponse logo,
        PublicMediaResponse cover,
        List<ResolvedMerchantLocationResponse> locations,
        List<PublicMerchantContactResponse> contacts) {}
