package vn.covie.delivery.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "delivery_coverage_areas")
public class DeliveryCoverageAreaEntity {
    @EmbeddedId private DeliveryCoverageAreaId id;
    protected DeliveryCoverageAreaEntity() {}
    private DeliveryCoverageAreaEntity(DeliveryCoverageAreaId id) { this.id = id; }
    public static DeliveryCoverageAreaEntity link(java.util.UUID coverageId, java.util.UUID areaId) {
        return new DeliveryCoverageAreaEntity(new DeliveryCoverageAreaId(coverageId, areaId));
    }
    public DeliveryCoverageAreaId getId() { return id; }
}
