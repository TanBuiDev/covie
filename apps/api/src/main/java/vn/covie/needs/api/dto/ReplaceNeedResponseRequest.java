package vn.covie.needs.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReplaceNeedResponseRequest(
        @NotEmpty List<@NotNull UUID> locationVariantIds,
        @Size(max = 2000) String note) {}
