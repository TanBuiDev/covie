package vn.covie.needs.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "need_response_items")
public class NeedResponseItemEntity {
    @Id private UUID id;
    @Column(name = "need_response_id", nullable = false) private UUID needResponseId;
    @Column(name = "catalog_item_id", nullable = false) private UUID catalogItemId;
    @Column(name = "variant_id", nullable = false) private UUID variantId;
    @Column(name = "location_variant_id", nullable = false) private UUID locationVariantId;
    @Enumerated(EnumType.STRING) @Column(name = "item_status", nullable = false) private NeedResponseItemStatus itemStatus;
    @Column(name = "price_snapshot", nullable = false) private long priceSnapshot;
    @JdbcTypeCode(java.sql.Types.CHAR) @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "availability_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode availabilitySnapshot;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "retired_at") private Instant retiredAt;

    protected NeedResponseItemEntity() {}

    public void retire(Instant now) {
        if (itemStatus == NeedResponseItemStatus.ACTIVE) {
            itemStatus = NeedResponseItemStatus.RETIRED;
            retiredAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getNeedResponseId() { return needResponseId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public UUID getVariantId() { return variantId; }
    public UUID getLocationVariantId() { return locationVariantId; }
    public NeedResponseItemStatus getItemStatus() { return itemStatus; }
    public long getPriceSnapshot() { return priceSnapshot; }
    public String getCurrencyCode() { return currencyCode; }
    public JsonNode getAvailabilitySnapshot() { return availabilitySnapshot; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRetiredAt() { return retiredAt; }
}
