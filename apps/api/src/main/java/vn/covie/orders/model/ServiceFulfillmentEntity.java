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

@Entity
@Table(name = "service_fulfillments")
public class ServiceFulfillmentEntity {
    @Id @Column(name = "order_id") private UUID orderId;
    @Column(name = "service_address_id") private UUID serviceAddressId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "service_address_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode serviceAddressSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "provider_location_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode providerLocationSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "buyer_contact_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode buyerContactSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "merchant_contact_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode merchantContactSnapshot;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ServiceFulfillmentEntity() {}
    public static ServiceFulfillmentEntity create(UUID orderId, UUID serviceAddressId, JsonNode serviceAddressSnapshot,
            JsonNode providerLocationSnapshot, JsonNode buyerContactSnapshot, JsonNode merchantContactSnapshot, Instant now) {
        ServiceFulfillmentEntity fulfillment = new ServiceFulfillmentEntity(); fulfillment.orderId=orderId;
        fulfillment.serviceAddressId=serviceAddressId; fulfillment.serviceAddressSnapshot=serviceAddressSnapshot;
        fulfillment.providerLocationSnapshot=providerLocationSnapshot; fulfillment.buyerContactSnapshot=buyerContactSnapshot;
        fulfillment.merchantContactSnapshot=merchantContactSnapshot; fulfillment.createdAt=now; return fulfillment;
    }
}
