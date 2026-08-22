package vn.covie.orders.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_cancellation_requests")
public class OrderCancellationRequestEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "requested_by_user_id", nullable = false) private UUID requestedByUserId;
    @Column(name = "reason_code", nullable = false, length = 60) private String reasonCode;
    @Column(name = "reason_text") private String reasonText;
    @Enumerated(EnumType.STRING) @Column(name = "request_status", nullable = false) private CancellationRequestStatus requestStatus;
    @Enumerated(EnumType.STRING) @Column(name = "previous_order_status", nullable = false) private OrderStatus previousOrderStatus;
    @Column(name = "responded_by_user_id") private UUID respondedByUserId;
    @Column(name = "response_note") private String responseNote;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "responded_at") private Instant respondedAt;
    protected OrderCancellationRequestEntity() {}
}
