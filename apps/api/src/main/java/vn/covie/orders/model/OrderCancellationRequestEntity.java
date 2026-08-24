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

    public static OrderCancellationRequestEntity pending(UUID id, UUID orderId, UUID requestedByUserId,
            String reasonCode, String reasonText, OrderStatus previousOrderStatus, Instant now) {
        OrderCancellationRequestEntity request = new OrderCancellationRequestEntity();
        request.id = id; request.orderId = orderId; request.requestedByUserId = requestedByUserId;
        request.reasonCode = reasonCode; request.reasonText = reasonText; request.previousOrderStatus = previousOrderStatus;
        request.requestStatus = CancellationRequestStatus.PENDING; request.createdAt = now;
        return request;
    }
    public void accept(UUID actorUserId, String note, Instant now) { respond(CancellationRequestStatus.ACCEPTED, actorUserId, note, now); }
    public void reject(UUID actorUserId, String note, Instant now) { respond(CancellationRequestStatus.REJECTED, actorUserId, note, now); }
    private void respond(CancellationRequestStatus status, UUID actorUserId, String note, Instant now) {
        if (requestStatus != CancellationRequestStatus.PENDING) throw new IllegalStateException("Cancellation request is not pending.");
        requestStatus = status; respondedByUserId = actorUserId; responseNote = note; respondedAt = now;
    }
    public UUID getOrderId() { return orderId; }
    public UUID getId() { return id; } public UUID getRequestedByUserId() { return requestedByUserId; }
    public String getReasonCode() { return reasonCode; } public String getReasonText() { return reasonText; }
    public OrderStatus getPreviousOrderStatus() { return previousOrderStatus; }
    public CancellationRequestStatus getRequestStatus() { return requestStatus; }
    public Instant getRespondedAt() { return respondedAt; }
    public UUID getRespondedByUserId() { return respondedByUserId; } public String getResponseNote() { return responseNote; }
    public Instant getCreatedAt() { return createdAt; }
}
