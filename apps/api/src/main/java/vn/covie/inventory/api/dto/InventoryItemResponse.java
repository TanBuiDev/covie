package vn.covie.inventory.api.dto;
import java.util.UUID; import vn.covie.catalog.model.InventoryMode;
public record InventoryItemResponse(UUID locationVariantId,UUID catalogItemId,String itemName,UUID variantId,String variantName,UUID locationId,String locationName,InventoryMode mode,Long stockOnHand,Long reservedQuantity,Long availableQuantity,long version) {}
