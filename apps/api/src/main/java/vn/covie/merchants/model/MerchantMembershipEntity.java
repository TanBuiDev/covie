package vn.covie.merchants.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import vn.covie.shared.id.UuidV7Generator;

@Entity
@Table(name = "merchant_memberships")
public class MerchantMembershipEntity {
    @Id private UUID id;
    @Column(name = "merchant_profile_id", nullable = false) private UUID merchantProfileId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MerchantRole role;
    @Enumerated(EnumType.STRING) @Column(name = "membership_status", nullable = false, length = 20) private MembershipStatus membershipStatus;
    @Column(name = "joined_at") private Instant joinedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version @Column(nullable = false) private long version;

    protected MerchantMembershipEntity() {}

    public static MerchantMembershipEntity active(UUID id, UUID merchantProfileId, UUID userId, MerchantRole role, Instant now) {
        MerchantMembershipEntity membership = new MerchantMembershipEntity();
        membership.id = id; membership.merchantProfileId = merchantProfileId; membership.userId = userId; membership.role = role;
        membership.membershipStatus = MembershipStatus.ACTIVE; membership.joinedAt = now; membership.createdAt = now;
        return membership;
    }

    public MerchantMemberLocationEntity assignTo(UuidV7Generator ids, UUID merchantLocationId, Instant now) {
        return MerchantMemberLocationEntity.assign(ids.generate(), id, merchantLocationId, now);
    }

    public void changeRole(MerchantRole role) { this.role = role; }
    public void revoke() { membershipStatus = MembershipStatus.REVOKED; }
    public UUID getId() { return id; }
    public UUID getMerchantProfileId() { return merchantProfileId; }
    public UUID getUserId() { return userId; }
    public MerchantRole getRole() { return role; }
    public MerchantRole role() { return role; }
    public MembershipStatus getMembershipStatus() { return membershipStatus; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
