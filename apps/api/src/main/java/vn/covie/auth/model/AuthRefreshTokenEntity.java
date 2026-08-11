package vn.covie.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "auth_refresh_tokens")
public class AuthRefreshTokenEntity {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false, columnDefinition = "char(64)") private String tokenHash;
    @Column(name = "replaced_by_token_id") private UUID replacedByTokenId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected AuthRefreshTokenEntity() {}
    public static AuthRefreshTokenEntity create(UUID id, UUID sessionId, String tokenHash, Instant expiresAt, Instant now) { AuthRefreshTokenEntity token = new AuthRefreshTokenEntity(); token.id=id; token.sessionId=sessionId; token.tokenHash=tokenHash; token.expiresAt=expiresAt; token.createdAt=now; return token; }
    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public String getTokenHash() { return tokenHash; }
    public UUID getReplacedByTokenId() { return replacedByTokenId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isUsedOrReplaced() { return usedAt != null || replacedByTokenId != null; }
    public boolean isRevoked() { return revokedAt != null; }
    public void replaceWith(UUID replacementId, Instant now) { usedAt = now; replacedByTokenId = replacementId; }
    public void revoke(Instant now) { revokedAt = now; }
}
