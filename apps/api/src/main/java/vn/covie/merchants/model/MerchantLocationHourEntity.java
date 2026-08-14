package vn.covie.merchants.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "merchant_location_hours")
public class MerchantLocationHourEntity {
    @Id private UUID id;
    @Column(name = "merchant_location_id", nullable = false) private UUID merchantLocationId;
    @Column(name = "day_of_week", nullable = false) private short dayOfWeek;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, length = 20) private OperationType operationType;
    @Column(name = "open_time") private LocalTime openTime;
    @Column(name = "close_time") private LocalTime closeTime;
    @Column(name = "is_closed", nullable = false) private boolean closed;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected MerchantLocationHourEntity() {}

    public static MerchantLocationHourEntity closed(UUID id, UUID locationId, int dayOfWeek,
            OperationType operationType) {
        return create(id, locationId, dayOfWeek, operationType, null, null, true, 0);
    }

    public static MerchantLocationHourEntity open(UUID id, UUID locationId, int dayOfWeek,
            OperationType operationType, LocalTime openTime, LocalTime closeTime, int sortOrder) {
        return create(id, locationId, dayOfWeek, operationType, openTime, closeTime, false, sortOrder);
    }

    private static MerchantLocationHourEntity create(UUID id, UUID locationId, int dayOfWeek,
            OperationType operationType, LocalTime openTime, LocalTime closeTime, boolean closed, int sortOrder) {
        MerchantLocationHourEntity hour = new MerchantLocationHourEntity();
        hour.id = id; hour.merchantLocationId = locationId; hour.dayOfWeek = (short) dayOfWeek;
        hour.operationType = operationType; hour.openTime = openTime; hour.closeTime = closeTime;
        hour.closed = closed; hour.sortOrder = sortOrder;
        return hour;
    }

    public UUID getId() { return id; }
    public UUID getMerchantLocationId() { return merchantLocationId; }
    public short getDayOfWeek() { return dayOfWeek; }
    public OperationType getOperationType() { return operationType; }
    public LocalTime getOpenTime() { return openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public boolean isClosed() { return closed; }
    public int getSortOrder() { return sortOrder; }
}
