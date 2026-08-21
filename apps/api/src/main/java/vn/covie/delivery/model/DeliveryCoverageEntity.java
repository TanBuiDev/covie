package vn.covie.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_coverages")
public class DeliveryCoverageEntity {
    @Id private UUID id;
    @Column(name = "merchant_location_id", nullable = false) private UUID merchantLocationId;
    @Enumerated(EnumType.STRING) @Column(name = "coverage_type", nullable = false, length = 30) private CoverageType coverageType;
    @Column(name = "radius_enabled", nullable = false) private boolean radiusEnabled;
    @Column(name = "radius_meters") private Integer radiusMeters;
    @Column(name = "area_enabled", nullable = false) private boolean areaEnabled;
    @Enumerated(EnumType.STRING) @Column(name = "coverage_status", nullable = false, length = 20) private CoverageStatus coverageStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    protected DeliveryCoverageEntity() {}

    public static DeliveryCoverageEntity create(UUID id, UUID locationId, CoverageType type, boolean radiusEnabled,
            Integer radiusMeters, boolean areaEnabled, CoverageStatus status, Instant now) {
        DeliveryCoverageEntity entity = new DeliveryCoverageEntity();
        entity.id = id; entity.merchantLocationId = locationId; entity.coverageType = type;
        entity.radiusEnabled = radiusEnabled; entity.radiusMeters = radiusMeters; entity.areaEnabled = areaEnabled;
        entity.coverageStatus = status; entity.createdAt = now; entity.updatedAt = now;
        return entity;
    }
    public void update(boolean radiusEnabled, Integer radiusMeters, boolean areaEnabled, CoverageStatus status, Instant now) {
        this.radiusEnabled = radiusEnabled; this.radiusMeters = radiusMeters; this.areaEnabled = areaEnabled;
        this.coverageStatus = status; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getMerchantLocationId() { return merchantLocationId; }
    public CoverageType getCoverageType() { return coverageType; }
    public boolean isRadiusEnabled() { return radiusEnabled; }
    public Integer getRadiusMeters() { return radiusMeters; }
    public boolean isAreaEnabled() { return areaEnabled; }
    public CoverageStatus getCoverageStatus() { return coverageStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
