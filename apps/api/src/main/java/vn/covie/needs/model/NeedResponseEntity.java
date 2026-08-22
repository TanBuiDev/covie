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
