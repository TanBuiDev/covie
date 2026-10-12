package vn.covie.merchants.api.dto;

import java.util.UUID;
import vn.covie.merchants.model.MerchantStatus;

public record MerchantManagementResponse(
        UUID id, String name, String slug, String description, UUID logoMediaId, UUID coverMediaId,
        MerchantStatus status, long version, String logoUrl, String coverUrl) {
    public MerchantManagementResponse(UUID id, String name, String slug, String description,
            UUID logoMediaId, UUID coverMediaId, MerchantStatus status, long version) {
        this(id, name, slug, description, logoMediaId, coverMediaId, status, version, null, null);
    }
}
