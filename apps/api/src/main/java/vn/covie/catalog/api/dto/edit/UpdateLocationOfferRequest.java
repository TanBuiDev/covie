package vn.covie.catalog.api.dto.edit;

import jakarta.validation.constraints.PositiveOrZero;
import vn.covie.catalog.model.InventoryMode;

public record UpdateLocationOfferRequest(@PositiveOrZero Long price, Boolean available, InventoryMode inventoryMode,
        @PositiveOrZero Integer preparationTimeMinutes) {}
