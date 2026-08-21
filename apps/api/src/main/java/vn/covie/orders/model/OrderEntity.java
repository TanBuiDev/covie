package vn.covie.orders.model;

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
import vn.covie.shared.commerce.PaymentMethod;

@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id private UUID id;
    @Column(name = "order_code", nullable = false, unique = true, length = 32) private String orderCode;
    @Enumerated(EnumType.STRING) @Column(name = "order_type", nullable = false) private OrderType orderType;
    @Column(name = "buyer_user_id", nullable = false) private UUID buyerUserId;
    @Column(name = "merchant_profile_id", nullable = false) private UUID merchantProfileId;
    @Column(name = "merchant_location_id", nullable = false) private UUID merchantLocationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false) private OrderSourceType sourceType;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false) private PaymentMethod paymentMethod;
    @Column(nullable = false) private long subtotal;
    @Enumerated(EnumType.STRING) @Column(name = "shipping_fee_type") private OrderShippingFeeType shippingFeeType;
    @Column(name = "shipping_fee") private Long shippingFee;
    @Column(name = "known_total", nullable = false) private long knownTotal;
    @Column(name = "total_amount") private Long totalAmount;
    @JdbcTypeCode(java.sql.Types.CHAR) @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "confirmation_expires_at") private Instant confirmationExpiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected OrderEntity() {}

    public static OrderEntity pending(UUID id, String orderCode, OrderType orderType, UUID buyerUserId,
            UUID merchantProfileId, UUID merchantLocationId, PaymentMethod paymentMethod, long subtotal,
            OrderShippingFeeType shippingFeeType, Long shippingFee, long knownTotal, Long totalAmount,
            String currencyCode, Instant confirmationExpiresAt, Instant now) {
        OrderEntity order = new OrderEntity();
        order.id = id; order.orderCode = orderCode; order.orderType = orderType; order.buyerUserId = buyerUserId;
        order.merchantProfileId = merchantProfileId; order.merchantLocationId = merchantLocationId;
        order.status = OrderStatus.PENDING_CONFIRMATION; order.sourceType = OrderSourceType.MARKETPLACE;
        order.paymentMethod = paymentMethod; order.subtotal = subtotal; order.shippingFeeType = shippingFeeType;
        order.shippingFee = shippingFee; order.knownTotal = knownTotal; order.totalAmount = totalAmount;
        order.currencyCode = currencyCode; order.confirmationExpiresAt = confirmationExpiresAt;
        order.createdAt = now; order.updatedAt = now;
        return order;
    }

    public UUID getId() { return id; } public String getOrderCode() { return orderCode; }
    public OrderStatus getStatus() { return status; } public Instant getCreatedAt() { return createdAt; }
}
