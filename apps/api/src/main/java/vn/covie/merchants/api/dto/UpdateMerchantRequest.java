package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateMerchantRequest(
        @Size(max = 180) String name,
        @Size(max = 5000) String description,
        UUID logoMediaId,
        UUID coverMediaId) {}
