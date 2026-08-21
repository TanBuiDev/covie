package vn.covie.delivery.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class DeliveryCoverageAreaId implements Serializable {
    private UUID deliveryCoverageId;
    private UUID locationAreaId;
    protected DeliveryCoverageAreaId() {}
    public DeliveryCoverageAreaId(UUID deliveryCoverageId, UUID locationAreaId) {
        this.deliveryCoverageId = deliveryCoverageId;
        this.locationAreaId = locationAreaId;
    }
    public UUID getDeliveryCoverageId() { return deliveryCoverageId; }
    public UUID getLocationAreaId() { return locationAreaId; }
    @Override public boolean equals(Object other) {
        return other instanceof DeliveryCoverageAreaId value
                && java.util.Objects.equals(deliveryCoverageId, value.deliveryCoverageId)
                && java.util.Objects.equals(locationAreaId, value.locationAreaId);
    }
    @Override public int hashCode() { return java.util.Objects.hash(deliveryCoverageId, locationAreaId); }
}
