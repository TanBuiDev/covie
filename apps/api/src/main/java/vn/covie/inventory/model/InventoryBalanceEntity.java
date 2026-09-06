package vn.covie.inventory.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Entity
@Table(name = "inventory_balances")
public class InventoryBalanceEntity {
    @Id @Column(name = "location_variant_id") private UUID locationVariantId;
    @Column(name = "stock_on_hand", nullable = false) private long stockOnHand;
    @Column(name = "reserved_quantity", nullable = false) private long reservedQuantity;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected InventoryBalanceEntity() {}

    public static InventoryBalanceEntity zero(UUID id, Instant now) {
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.locationVariantId = id;
        balance.updatedAt = now;
        return balance;
    }

    public long availableQuantity() { return stockOnHand - reservedQuantity; }

    public void reserve(long quantity, Instant now) {
        if (quantity <= 0 || availableQuantity() < quantity) {
            throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY, HttpStatus.CONFLICT, "Insufficient inventory.");
        }
        reservedQuantity += quantity;
        updatedAt = now;
    }

    public void adjust(long stockDelta, long reservedDelta, Instant now) {
        long nextStock = Math.addExact(stockOnHand, stockDelta);
        long nextReserved = Math.addExact(reservedQuantity, reservedDelta);
        if (nextStock < 0 || nextReserved < 0 || nextReserved > nextStock) {
            throw new IllegalArgumentException("Inventory invariant violated");
        }
        stockOnHand = nextStock;
        reservedQuantity = nextReserved;
        updatedAt = now;
    }

    public void releaseReserved(long quantity, Instant now) {
        long toRelease = Math.min(Math.max(0, quantity), reservedQuantity);
        adjust(0, -toRelease, now);
    }

    public void commitReservedSale(long quantity, Instant now) {
        adjust(-quantity, -quantity, now);
    }

    public UUID getLocationVariantId() { return locationVariantId; }
    public long getStockOnHand() { return stockOnHand; }
    public long getReservedQuantity() { return reservedQuantity; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
