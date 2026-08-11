package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMerchantRequest(
        @NotBlank @Size(max = 180) String name,
        @Size(max = 5000) String description) {}
