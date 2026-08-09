package vn.covie.users.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.covie.users.model.PinSource;

public record UpdateUserAddressRequest(
        @NotBlank @Size(max = 80) String label,
        @Size(max = 120) String recipientName,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 1000) String addressText,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull PinSource pinSource,
        @Size(max = 1000) String note,
        boolean defaultAddress) {}
