package vn.covie.cart.api.dto; import java.util.UUID; public record CartItemResponse(UUID id,UUID locationVariantId,long quantity,long priceSnapshot,String itemName) {}
