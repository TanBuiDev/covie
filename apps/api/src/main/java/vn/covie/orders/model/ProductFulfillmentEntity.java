package vn.covie.orders.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_fulfillments")
public class ProductFulfillmentEntity {
    @Id @Column(name = "order_id") private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(name = "fulfillment_method", nullable = false) private ProductFulfillmentMethod fulfillmentMethod;
    @Column(name = "pickup_location_id") private UUID pickupLocationId;
    @Column(name = "buyer_address_id") private UUID buyerAddressId;
    @Column(name = "distance_meters") private Integer distanceMeters;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "pickup_location_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode pickupLocationSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "delivery_address_snapshot", columnDefinition = "jsonb") private JsonNode deliveryAddressSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "buyer_contact_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode buyerContactSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "merchant_contact_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode merchantContactSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "shipping_policy_snapshot", columnDefinition = "jsonb") private JsonNode shippingPolicySnapshot;
    protected ProductFulfillmentEntity() {}
    public static ProductFulfillmentEntity create(UUID orderId, ProductFulfillmentMethod method, UUID pickupLocationId,
            UUID buyerAddressId, Integer distanceMeters, JsonNode pickupLocationSnapshot, JsonNode deliveryAddressSnapshot,
            JsonNode buyerContactSnapshot, JsonNode merchantContactSnapshot, JsonNode shippingPolicySnapshot) {
        ProductFulfillmentEntity fulfillment = new ProductFulfillmentEntity(); fulfillment.orderId=orderId;
        fulfillment.fulfillmentMethod=method; fulfillment.pickupLocationId=pickupLocationId; fulfillment.buyerAddressId=buyerAddressId;
        fulfillment.distanceMeters=distanceMeters; fulfillment.pickupLocationSnapshot=pickupLocationSnapshot;
        fulfillment.deliveryAddressSnapshot=deliveryAddressSnapshot; fulfillment.buyerContactSnapshot=buyerContactSnapshot;
        fulfillment.merchantContactSnapshot=merchantContactSnapshot; fulfillment.shippingPolicySnapshot=shippingPolicySnapshot;
        return fulfillment;
    }
}
