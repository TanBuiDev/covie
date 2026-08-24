package vn.covie.needs.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import vn.covie.needs.model.NeedType;

public record CreateNeedRequest(
        @NotNull NeedType type,
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 220) String title,
        @NotBlank @Size(max = 500) String queryText,
        @Size(max = 4000) String description,
        @Positive Long requestedQuantity,
        UUID addressId,
        Double lat,
        Double lng,
        @Positive int radiusMeters,
        @NotNull Instant expiresAt) {}
