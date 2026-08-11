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

@Entity
@Table(name = "merchant_contact_methods")
public class MerchantContactMethodEntity {
    @Id private UUID id;
    @Column(name = "merchant_profile_id", nullable = false) private UUID merchantProfileId;
    @Column(name = "merchant_location_id") private UUID merchantLocationId;
    @Enumerated(EnumType.STRING) @Column(name = "contact_type", nullable = false, length = 30) private ContactType contactType;
    private String value;
    @Column(name = "qr_media_id") private UUID qrMediaId;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "is_public", nullable = false) private boolean publicContact;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContactStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version @Column(nullable = false) private long version;

    protected MerchantContactMethodEntity() {}

    public UUID getId() { return id; }
    public UUID getMerchantProfileId() { return merchantProfileId; }
    public UUID getMerchantLocationId() { return merchantLocationId; }
    public ContactType getContactType() { return contactType; }
    public String getValue() { return value; }
    public UUID getQrMediaId() { return qrMediaId; }
    public boolean isPrimary() { return primary; }
    public boolean isPublicContact() { return publicContact; }
    public ContactStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
