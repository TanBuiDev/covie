package vn.covie.location.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiPolygon;

@Entity
@Table(name = "location_areas")
public class LocationAreaEntity {
    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_type", nullable = false, length = 40)
    private LocationAreaType areaType;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 200)
    private String slug;

    @Column(name = "path_cache")
    private String pathCache;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "geo_boundary", columnDefinition = "geography(MultiPolygon,4326)")
    private MultiPolygon geoBoundary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationAreaStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected LocationAreaEntity() {}

    public UUID getId() { return id; }
    public UUID getParentId() { return parentId; }
    public LocationAreaType getAreaType() { return areaType; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getPathCache() { return pathCache; }
    public MultiPolygon getGeoBoundary() { return geoBoundary; }
    public LocationAreaStatus getStatus() { return status; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
