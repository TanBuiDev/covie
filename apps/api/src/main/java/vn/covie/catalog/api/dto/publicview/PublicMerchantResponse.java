package vn.covie.catalog.api.dto.publicview;

import java.util.List;
import java.util.UUID;
import vn.covie.reputation.api.dto.MerchantReputationSummary;

public record PublicMerchantResponse(
        UUID id,
        String name,
        String slug,
        String description,
        PublicMediaResponse logo,
        PublicMediaResponse cover,
        List<ResolvedMerchantLocationResponse> locations,
        List<PublicMerchantContactResponse> contacts,
        MerchantReputationSummary reputation) {}
