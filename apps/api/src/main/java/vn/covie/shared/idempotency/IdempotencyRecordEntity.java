package vn.covie.shared.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {
    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false)
    private String operation;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "idempotency_key_hash", nullable = false, length = 64)
    private String idempotencyKeyHash;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private JsonNode responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecordEntity() {}

    public static IdempotencyRecordEntity inProgress(
            UUID id,
            UUID actorId,
            String operation,
            String idempotencyKeyHash,
            String requestHash,
            Instant createdAt,
            Instant expiresAt) {
        IdempotencyRecordEntity record = new IdempotencyRecordEntity();
        record.id = id;
        record.actorId = actorId;
        record.operation = operation;
        record.idempotencyKeyHash = idempotencyKeyHash;
        record.requestHash = requestHash;
        record.status = IdempotencyStatus.IN_PROGRESS;
        record.createdAt = createdAt;
        record.expiresAt = expiresAt;
        return record;
    }

    public String requestHash() {
        return requestHash;
    }

    public IdempotencyStatus status() {
        return status;
    }

    public JsonNode responseBody() {
        return responseBody;
    }

    public void complete(int responseStatus, JsonNode responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }
}

enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
