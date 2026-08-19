package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReplaceMemberLocationsRequest(@NotNull List<@NotNull UUID> locationIds) {}
