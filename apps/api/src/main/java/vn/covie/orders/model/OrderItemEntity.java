package vn.covie.orders.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.covie.catalog.model.CatalogItemType;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "catalog_item_id") private UUID catalogItemId;
    @Column(name = "variant_id") private UUID variantId;
    @Column(name = "location_variant_id") private UUID locationVariantId;
    @Column(name = "item_type", nullable = false) @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING) private CatalogItemType itemType;
    @Column(nullable = false) private long quantity;
    @Column(name = "unit_price", nullable = false) private long unitPrice;
    @Column(name = "line_total", nullable = false) private long lineTotal;
    @JdbcTypeCode(java.sql.Types.CHAR) @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "item_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode itemSnapshot;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected OrderItemEntity() {}
    public static OrderItemEntity create(UUID id, UUID orderId, UUID catalogItemId, UUID variantId, UUID locationVariantId,
            CatalogItemType itemType, long quantity, long unitPrice, String currencyCode, JsonNode itemSnapshot, Instant now) {
        OrderItemEntity item = new OrderItemEntity(); item.id=id; item.orderId=orderId; item.catalogItemId=catalogItemId;
        item.variantId=variantId; item.locationVariantId=locationVariantId; item.itemType=itemType; item.quantity=quantity;
        item.unitPrice=unitPrice; item.lineTotal=Math.multiplyExact(quantity, unitPrice); item.currencyCode=currencyCode;
        item.itemSnapshot=itemSnapshot; item.createdAt=now; return item;
    }
    public UUID getId() { return id; } public UUID getOrderId() { return orderId; } public UUID getCatalogItemId() { return catalogItemId; } public UUID getVariantId() { return variantId; } public UUID getLocationVariantId() { return locationVariantId; }
    public CatalogItemType getItemType() { return itemType; } public long getUnitPrice() { return unitPrice; } public long getLineTotal() { return lineTotal; } public String getCurrencyCode() { return currencyCode; } public JsonNode getItemSnapshot() { return itemSnapshot; }
    public long getQuantity() { return quantity; }
}
