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
    @Column(name = "seller_marked_ready_at") private java.time.Instant sellerMarkedReadyAt;
    @Column(name = "out_for_delivery_at") private java.time.Instant outForDeliveryAt;
    @Column(name = "seller_marked_delivered_at") private java.time.Instant sellerMarkedDeliveredAt;
    @Column(name = "awaiting_buyer_confirmation_at") private java.time.Instant awaitingBuyerConfirmationAt;
    @Column(name = "auto_complete_at") private java.time.Instant autoCompleteAt;
    @Column(name = "no_show_eligible_at") private java.time.Instant noShowEligibleAt;
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
    public void markOutForDelivery(java.time.Instant now) { outForDeliveryAt = now; }
    public void markReadyForPickup(java.time.Instant now, java.time.Instant eligibleAt) {
        sellerMarkedReadyAt = now; noShowEligibleAt = eligibleAt;
    }
    public void markDelivered(java.time.Instant now, java.time.Instant completionAt) {
        sellerMarkedDeliveredAt = now; awaitingBuyerConfirmationAt = now; autoCompleteAt = completionAt;
    }
    public ProductFulfillmentMethod getFulfillmentMethod() { return fulfillmentMethod; }
    public UUID getPickupLocationId() { return pickupLocationId; } public UUID getBuyerAddressId() { return buyerAddressId; }
    public Integer getDistanceMeters() { return distanceMeters; } public JsonNode getPickupLocationSnapshot() { return pickupLocationSnapshot; }
    public JsonNode getDeliveryAddressSnapshot() { return deliveryAddressSnapshot; } public JsonNode getBuyerContactSnapshot() { return buyerContactSnapshot; }
    public JsonNode getMerchantContactSnapshot() { return merchantContactSnapshot; }
    public java.time.Instant getSellerMarkedReadyAt() { return sellerMarkedReadyAt; }
    public java.time.Instant getOutForDeliveryAt() { return outForDeliveryAt; }
    public java.time.Instant getSellerMarkedDeliveredAt() { return sellerMarkedDeliveredAt; }
    public java.time.Instant getAwaitingBuyerConfirmationAt() { return awaitingBuyerConfirmationAt; }
    public java.time.Instant getAutoCompleteAt() { return autoCompleteAt; }
    public java.time.Instant getNoShowEligibleAt() { return noShowEligibleAt; }
}
