package vn.covie.merchants.api.dto;

import java.util.UUID;
import vn.covie.merchants.model.MerchantKind;
import vn.covie.merchants.model.MerchantStatus;

public record MerchantManagementResponse(
        UUID id, String name, String slug, String description, UUID logoMediaId, UUID coverMediaId,
        MerchantKind merchantKind, MerchantStatus status, long version) {}
