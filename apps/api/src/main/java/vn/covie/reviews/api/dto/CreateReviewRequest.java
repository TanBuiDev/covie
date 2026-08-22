package vn.covie.reviews.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateReviewRequest(
        @NotNull UUID orderItemId,
        @Min(1) @Max(5) int rating,
        @Size(max = 3000) String comment) {}
