package vn.covie.catalog.api.dto.create;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import vn.covie.catalog.model.InventoryMode;
public record LocationOfferCreateRequest(@NotNull UUID merchantLocationId, @NotBlank String variantClientKey,
        @PositiveOrZero long price, @Pattern(regexp="^[A-Z]{3}$") String currency, boolean available,
        InventoryMode inventoryMode, @PositiveOrZero Integer preparationTimeMinutes) {}
