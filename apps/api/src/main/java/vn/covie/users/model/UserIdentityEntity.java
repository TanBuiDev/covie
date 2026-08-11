package vn.covie.users.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_identities")
public class UserIdentityEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private IdentityProvider provider;
    @Column(name = "provider_subject", length = 255) private String providerSubject;
    @Column(name = "email_normalized", length = 320) private String emailNormalized;
    @Column(name = "phone_normalized", length = 30) private String phoneNormalized;
    @Column(name = "password_hash", length = 255) private String passwordHash;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;

    protected UserIdentityEntity() {}
    public static UserIdentityEntity google(UUID id, UUID userId, String subject, String emailNormalized, Instant verifiedAt) {
        UserIdentityEntity identity = base(id, userId, IdentityProvider.GOOGLE, verifiedAt);
        identity.providerSubject = subject;
        identity.emailNormalized = emailNormalized;
        return identity;
    }
    public static UserIdentityEntity email(UUID id, UUID userId, String emailNormalized, String passwordHash, Instant verifiedAt) {
        UserIdentityEntity identity = base(id, userId, IdentityProvider.EMAIL, verifiedAt);
        identity.emailNormalized = emailNormalized;
        identity.passwordHash = passwordHash;
        return identity;
    }
    private static UserIdentityEntity base(UUID id, UUID userId, IdentityProvider provider, Instant now) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.id = id; identity.userId = userId; identity.provider = provider; identity.verifiedAt = now; identity.createdAt = now;
        return identity;
    }
    public void updatePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void markUsed(Instant now) { this.lastUsedAt = now; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public IdentityProvider getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
    public String getEmailNormalized() { return emailNormalized; }
    public String getPasswordHash() { return passwordHash; }
}
