package vn.covie.merchants.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "merchant_location_hour_exceptions")
public class MerchantLocationHourExceptionEntity {
    @Id private UUID id;
    @Column(name = "merchant_location_id", nullable = false) private UUID merchantLocationId;
    @Column(name = "local_date", nullable = false) private LocalDate localDate;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, length = 20) private OperationType operationType;
    @Column(name = "open_time") private LocalTime openTime;
    @Column(name = "close_time") private LocalTime closeTime;
    @Column(name = "is_closed", nullable = false) private boolean closed;
    private String note;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected MerchantLocationHourExceptionEntity() {}

    public UUID getId() { return id; }
    public UUID getMerchantLocationId() { return merchantLocationId; }
    public LocalDate getLocalDate() { return localDate; }
    public OperationType getOperationType() { return operationType; }
    public LocalTime getOpenTime() { return openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public boolean isClosed() { return closed; }
    public String getNote() { return note; }
    public int getSortOrder() { return sortOrder; }
}
