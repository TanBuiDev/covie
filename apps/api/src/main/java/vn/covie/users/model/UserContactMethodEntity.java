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
}
