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
@Table(name = "user_contact_methods")
public class UserContactMethodEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name = "contact_type", nullable = false) private ContactType contactType;
    @Column(nullable = false) private String value;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "is_verified", nullable = false) private boolean verified;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected UserContactMethodEntity() {}
    public static UserContactMethodEntity create(UUID id, UUID userId, ContactType type, String value, boolean primary, Instant now) { UserContactMethodEntity contact = new UserContactMethodEntity(); contact.id=id; contact.userId=userId; contact.contactType=type; contact.value=value; contact.primary=primary; contact.verified=false; contact.status="ACTIVE"; contact.createdAt=now; return contact; }
    public void updateValue(String value) { this.value = value; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public void deactivate() { this.status = "INACTIVE"; this.primary = false; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public ContactType getContactType() { return contactType; }
    public String getValue() { return value; }
    public boolean isPrimary() { return primary; }
    public boolean isVerified() { return verified; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
