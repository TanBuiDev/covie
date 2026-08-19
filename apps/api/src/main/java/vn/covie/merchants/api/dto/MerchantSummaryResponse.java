package vn.covie.merchants.api.dto;

import java.util.UUID;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.model.MerchantStatus;

public record MerchantSummaryResponse(
        UUID id, String name, String slug, MerchantStatus status, MerchantRole currentUserRole, long version) {}
