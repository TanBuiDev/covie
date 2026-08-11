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

    public UUID getId() { return id; }
    public UUID getMerchantLocationId() { return merchantLocationId; }
    public short getDayOfWeek() { return dayOfWeek; }
    public OperationType getOperationType() { return operationType; }
    public LocalTime getOpenTime() { return openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public boolean isClosed() { return closed; }
    public int getSortOrder() { return sortOrder; }
}
