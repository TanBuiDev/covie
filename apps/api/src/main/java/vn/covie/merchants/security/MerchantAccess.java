package vn.covie.merchants.security;

import java.util.UUID;
import vn.covie.merchants.model.MerchantRole;

public record MerchantAccess(UUID userId, UUID merchantId, UUID membershipId, MerchantRole role) {}
