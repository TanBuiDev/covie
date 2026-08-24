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
@Table(name = "merchant_profiles")
public class MerchantProfileEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 180) private String name;
    @Column(nullable = false, length = 200) private String slug;
    private String description;
    @Column(name = "logo_media_id") private UUID logoMediaId;
    @Column(name = "cover_media_id") private UUID coverMediaId;
    @Enumerated(EnumType.STRING) @Column(name = "merchant_status", nullable = false, length = 30) private MerchantStatus merchantStatus;
    @Enumerated(EnumType.STRING) @Column(name = "moderation_status", nullable = false, length = 30) private MerchantModerationStatus moderationStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version @Column(nullable = false) private long version;

    protected MerchantProfileEntity() {}

    public static MerchantProfileEntity create(UUID id, String name, String slug, Instant now) {
        return create(id, name, slug, null, now);
    }

    public static MerchantProfileEntity create(UUID id, String name, String slug, String description, Instant now) {
        MerchantProfileEntity profile = new MerchantProfileEntity();
        profile.id = id; profile.name = name; profile.slug = slug; profile.description = description; profile.merchantStatus = MerchantStatus.ACTIVE;
        profile.moderationStatus = MerchantModerationStatus.NOT_REQUIRED; profile.createdAt = now; profile.updatedAt = now;
        return profile;
    }

    public void update(String name, String description, UUID logoMediaId, UUID coverMediaId, Instant now) {
        this.name = name;
        this.description = description;
        this.logoMediaId = logoMediaId;
        this.coverMediaId = coverMediaId;
        this.updatedAt = now;
    }

    public void hide(Instant now) {
        if (moderationStatus != MerchantModerationStatus.NOT_REQUIRED && moderationStatus != MerchantModerationStatus.APPROVED) {
            throw new IllegalStateException("Merchant is not visible.");
        }
        moderationStatus = MerchantModerationStatus.HIDDEN;
        updatedAt = now;
    }

    public void restoreVisibility(Instant now) {
        if (moderationStatus != MerchantModerationStatus.HIDDEN && moderationStatus != MerchantModerationStatus.REJECTED) {
            throw new IllegalStateException("Merchant cannot be restored.");
        }
        moderationStatus = MerchantModerationStatus.APPROVED;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public UUID getLogoMediaId() { return logoMediaId; }
    public UUID getCoverMediaId() { return coverMediaId; }
    public MerchantStatus getMerchantStatus() { return merchantStatus; }
    public MerchantModerationStatus getModerationStatus() { return moderationStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public long getVersion() { return version; }
}
