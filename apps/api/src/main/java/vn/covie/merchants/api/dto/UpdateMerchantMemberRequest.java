package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.NotNull;
import vn.covie.merchants.model.MerchantRole;

public record UpdateMerchantMemberRequest(@NotNull MerchantRole role) {}
