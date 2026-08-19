package vn.covie.merchants.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_member_locations")
public class MerchantMemberLocationEntity {
    @Id private UUID id;
    @Column(name = "membership_id", nullable = false) private UUID membershipId;
    @Column(name = "merchant_location_id", nullable = false) private UUID merchantLocationId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MerchantMemberLocationEntity() {}

    public static MerchantMemberLocationEntity assign(UUID id, UUID membershipId, UUID merchantLocationId, Instant now) {
        MerchantMemberLocationEntity assignment = new MerchantMemberLocationEntity();
        assignment.id = id; assignment.membershipId = membershipId; assignment.merchantLocationId = merchantLocationId; assignment.createdAt = now;
        return assignment;
    }

    public UUID getId() { return id; }
    public UUID getMembershipId() { return membershipId; }
    public UUID getMerchantLocationId() { return merchantLocationId; }
    public Instant getCreatedAt() { return createdAt; }
}
