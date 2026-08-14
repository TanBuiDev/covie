package vn.covie.catalog.api.dto.edit;

import jakarta.validation.constraints.Size;
import vn.covie.catalog.model.VariantStatus;

public record UpdateVariantRequest(@Size(max = 120) String sku, @Size(max = 240) String variantName,
        VariantStatus variantStatus) {}
