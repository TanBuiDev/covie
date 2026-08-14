package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import vn.covie.merchants.model.PinSource;

public record MerchantLocationUpsertRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 1000) String addressText,
        UUID locationAreaId,
        @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
        @NotNull PinSource pinSource,
        @NotBlank @Size(max = 60) String timezone,
        boolean pickupEnabled,
        boolean sellerDeliveryEnabled,
        boolean serviceEnabled,
        @PositiveOrZero Long minimumDeliveryOrder,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {}
