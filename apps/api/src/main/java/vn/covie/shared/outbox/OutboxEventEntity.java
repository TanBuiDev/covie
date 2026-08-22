package vn.covie.shared.outbox;

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
@Table(name = "outbox_events")
public class OutboxEventEntity {
    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEventEntity() {}

    public static OutboxEventEntity pending(
            UUID id,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            JsonNode payload,
            Instant occurredAt) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.id = id;
        event.eventType = eventType;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.payload = payload;
        event.occurredAt = occurredAt;
        event.status = OutboxEventStatus.PENDING;
        event.attempts = 0;
        return event;
    }
    public void markPublished(Instant now) { status=OutboxEventStatus.PUBLISHED; publishedAt=now; nextAttemptAt=null; lastError=null; }
    public void markFailed(int newAttempts, Instant nextAttemptAt, String sanitizedError) { attempts=newAttempts; status=OutboxEventStatus.FAILED; this.nextAttemptAt=nextAttemptAt; lastError=sanitizedError; }
    public UUID getId(){return id;} public String getEventType(){return eventType;} public String getAggregateType(){return aggregateType;} public UUID getAggregateId(){return aggregateId;} public JsonNode getPayload(){return payload;} public Instant getOccurredAt(){return occurredAt;} public OutboxEventStatus getStatus(){return status;} public int getAttempts(){return attempts;}
}
