package vn.covie.catalog.api.dto.edit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateVariantRequest(@Size(max = 120) String sku, @NotBlank @Size(max = 240) String variantName,
        List<UUID> optionValueIds) {}
