package vn.covie.orders.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_events")
public class OrderEventEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false) private OrderEventType eventType;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Enumerated(EnumType.STRING) @Column(name = "actor_context") private OrderActorContext actorContext;
    @Enumerated(EnumType.STRING) @Column(name = "from_status") private OrderStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status") private OrderStatus toStatus;
    @Column(name = "reason_code", length = 60) private String reasonCode;
    @Column(name = "note") private String note;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private JsonNode metadata;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected OrderEventEntity() {}
    public static OrderEventEntity created(UUID id, UUID orderId, UUID actorUserId, JsonNode metadata, Instant now) {
        OrderEventEntity event = new OrderEventEntity(); event.id=id; event.orderId=orderId; event.eventType=OrderEventType.ORDER_CREATED;
        event.actorUserId=actorUserId; event.metadata=metadata; event.createdAt=now; return event;
    }

    public static OrderEventEntity lifecycle(UUID id, UUID orderId, OrderEventType eventType, UUID actorUserId,
            OrderActorContext actorContext, OrderStatus fromStatus, OrderStatus toStatus, String reasonCode,
            String note, JsonNode metadata, Instant now) {
        OrderEventEntity event = new OrderEventEntity();
        event.id = id; event.orderId = orderId; event.eventType = eventType; event.actorUserId = actorUserId;
        event.actorContext = actorContext; event.fromStatus = fromStatus; event.toStatus = toStatus;
        event.reasonCode = reasonCode; event.note = note; event.metadata = metadata; event.createdAt = now;
        return event;
    }

    public UUID getId() { return id; } public UUID getOrderId() { return orderId; } public UUID getActorUserId() { return actorUserId; } public OrderActorContext getActorContext() { return actorContext; } public String getReasonCode() { return reasonCode; } public String getNote() { return note; } public Instant getCreatedAt() { return createdAt; }
    public OrderEventType getEventType() { return eventType; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
}
