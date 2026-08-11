package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.covie.merchants.model.MerchantRole;

public record AddMerchantMemberRequest(
        @Email @NotBlank String email,
        @NotNull MerchantRole role) {}
