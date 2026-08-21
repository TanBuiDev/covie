package vn.covie.inventory.api.dto; import java.util.UUID; public record InventoryCommandResult(UUID locationVariantId,long stockOnHand,long reservedQuantity,long availableQuantity,long version) {}
