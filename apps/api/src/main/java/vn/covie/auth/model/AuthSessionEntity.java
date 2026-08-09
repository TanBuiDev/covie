package vn.covie.auth.model;

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
@Table(name = "auth_sessions")
public class AuthSessionEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_family_id", nullable = false) private UUID tokenFamilyId;
    @Column(name = "device_name") private String deviceName;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_used_at", nullable = false) private Instant lastUsedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Enumerated(EnumType.STRING) @Column(name = "revocation_reason") private AuthSessionRevocationReason revocationReason;
    @Version private long version;
    protected AuthSessionEntity() {}
    public static AuthSessionEntity create(UUID id, UUID userId, UUID familyId, Instant now, Instant expiresAt) { return create(id, userId, familyId, null, now, expiresAt); }
    public static AuthSessionEntity create(UUID id, UUID userId, UUID familyId, DeviceMetadata device, Instant now, Instant expiresAt) { AuthSessionEntity session = new AuthSessionEntity(); session.id=id; session.userId=userId; session.tokenFamilyId=familyId; session.deviceName=device == null ? null : device.deviceName(); session.userAgent=device == null ? null : device.userAgent(); session.createdAt=now; session.lastUsedAt=now; session.expiresAt=expiresAt; return session; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTokenFamilyId() { return tokenFamilyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getDeviceName() { return deviceName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public boolean isActiveAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void revoke(Instant now, AuthSessionRevocationReason reason) { if (revokedAt == null) { revokedAt = now; revocationReason = reason; } }
    public void markUsed(Instant now) { lastUsedAt = now; }
}
