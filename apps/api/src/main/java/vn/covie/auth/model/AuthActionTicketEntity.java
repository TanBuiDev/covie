package vn.covie.auth.model;

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

@Entity
@Table(name = "auth_action_tickets")
public class AuthActionTicketEntity {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AuthActionPurpose purpose;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)") private String tokenHash;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "google_subject", nullable = false) private String googleSubject;
    @Column(name = "email_normalized", nullable = false) private String emailNormalized;
    @Column(name = "google_email_verified", nullable = false) private boolean googleEmailVerified;
    @Column(name = "display_name") private String displayName;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    protected AuthActionTicketEntity() {}
    public static AuthActionTicketEntity create(UUID id, AuthActionPurpose purpose, String tokenHash, UUID userId, String googleSubject, String emailNormalized, boolean googleEmailVerified, String displayName, Instant now, Instant expiresAt) {
        AuthActionTicketEntity ticket = new AuthActionTicketEntity();
        ticket.id = id; ticket.purpose = purpose; ticket.tokenHash = tokenHash; ticket.userId = userId;
        ticket.googleSubject = googleSubject; ticket.emailNormalized = emailNormalized; ticket.googleEmailVerified = googleEmailVerified;
        ticket.displayName = displayName; ticket.createdAt = now; ticket.expiresAt = expiresAt;
        return ticket;
    }
    public void consume(Instant now) { consumedAt = now; }
}
