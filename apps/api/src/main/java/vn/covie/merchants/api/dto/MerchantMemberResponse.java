package vn.covie.merchants.api.dto;

import java.util.UUID;
import vn.covie.merchants.model.MembershipStatus;
import vn.covie.merchants.model.MerchantRole;

public record MerchantMemberResponse(
        UUID id, UUID userId, String email, MerchantRole role, MembershipStatus status, long version) {}
