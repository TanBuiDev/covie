package vn.covie.catalog.api.dto.edit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.covie.catalog.model.InventoryMode;

/** Uses setter presence for nullable preparation time: omitted retains it; explicit JSON null clears it. */
@JsonIgnoreProperties("preparationTimeMinutesPresent")
public final class UpdateLocationOfferRequest {
    @PositiveOrZero private Long price;
    private Boolean available;
    private InventoryMode inventoryMode;
    @PositiveOrZero private Integer preparationTimeMinutes;
    @JsonIgnore private boolean preparationTimeMinutesPresent;

    public Long price() { return price; }
    public Boolean available() { return available; }
    public InventoryMode inventoryMode() { return inventoryMode; }
    public Integer preparationTimeMinutes() { return preparationTimeMinutes; }
    @JsonIgnore public boolean preparationTimeMinutesWasSupplied() { return preparationTimeMinutesPresent; }

    @JsonSetter public void setPrice(Long price) { this.price = price; }
    @JsonSetter public void setAvailable(Boolean available) { this.available = available; }
    @JsonSetter public void setInventoryMode(InventoryMode inventoryMode) { this.inventoryMode = inventoryMode; }
    @JsonSetter @Schema(nullable = true, description = "Omitted retains the current value; explicit null clears it.")
    public void setPreparationTimeMinutes(Integer preparationTimeMinutes) {
        this.preparationTimeMinutes = preparationTimeMinutes;
        this.preparationTimeMinutesPresent = true;
    }
}
