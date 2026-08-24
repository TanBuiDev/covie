package vn.covie.needs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "need_responses")
public class NeedResponseEntity {
    @Id private UUID id;
    @Column(name = "need_request_id", nullable = false) private UUID needRequestId;
    @Column(name = "merchant_profile_id", nullable = false) private UUID merchantProfileId;
    @Column(name = "merchant_location_id", nullable = false) private UUID merchantLocationId;
    @Enumerated(EnumType.STRING) @Column(name = "response_status", nullable = false) private NeedResponseStatus responseStatus;
    @Column(columnDefinition = "text") private String note;
    @Column(name = "created_by_user_id", nullable = false) private UUID createdByUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    @Version @Column(nullable = false) private long version;

    protected NeedResponseEntity() {}

    public static NeedResponseEntity active(UUID id, UUID needRequestId, UUID merchantProfileId,
            UUID merchantLocationId, UUID createdByUserId, String note, Instant now) {
        NeedResponseEntity entity = new NeedResponseEntity();
        entity.id = id;
        entity.needRequestId = needRequestId;
        entity.merchantProfileId = merchantProfileId;
        entity.merchantLocationId = merchantLocationId;
        entity.createdByUserId = createdByUserId;
        entity.note = normalizeNote(note);
        entity.responseStatus = NeedResponseStatus.ACTIVE;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void replaceNote(String note, Instant now) {
        requireActive();
        this.note = normalizeNote(note);
        this.updatedAt = now;
    }

    public void withdraw(Instant now) {
        requireActive();
        this.responseStatus = NeedResponseStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.updatedAt = now;
    }

    private void requireActive() {
        if (responseStatus != NeedResponseStatus.ACTIVE) {
            throw new IllegalStateException("Need response is not active.");
        }
    }

    private static String normalizeNote(String note) {
        return note == null || note.strip().isEmpty() ? null : note.strip();
    }

    public UUID getId() { return id; }
    public UUID getNeedRequestId() { return needRequestId; }
    public UUID getMerchantProfileId() { return merchantProfileId; }
    public UUID getMerchantLocationId() { return merchantLocationId; }
    public NeedResponseStatus getResponseStatus() { return responseStatus; }
    public String getNote() { return note; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public long getVersion() { return version; }
}
