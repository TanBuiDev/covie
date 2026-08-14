package vn.covie.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAssetEntity {
    @Id private UUID id;
    @Column(name = "owner_user_id") private UUID ownerUserId;
    @Column(name = "storage_provider", nullable = false, length = 30) private String storageProvider;
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Enumerated(EnumType.STRING) @Column(name = "upload_purpose", nullable = false, length = 40) private MediaPurpose purpose;
    @Column(name = "mime_type", nullable = false, length = 120) private String mimeType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Enumerated(EnumType.STRING) @Column(name = "media_status", nullable = false, length = 20) private MediaStatus mediaStatus;
    @Column(name = "upload_expires_at", nullable = false) private Instant uploadExpiresAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected MediaAssetEntity() {}

    public static MediaAssetEntity pending(UUID id, UUID ownerUserId, String storageKey, MediaPurpose purpose,
            String mimeType, long sizeBytes, Instant expiresAt, Instant now) {
        MediaAssetEntity asset = new MediaAssetEntity();
        asset.id = id; asset.ownerUserId = ownerUserId; asset.storageProvider = "S3"; asset.storageKey = storageKey;
        asset.purpose = purpose; asset.mimeType = mimeType; asset.sizeBytes = sizeBytes;
        asset.mediaStatus = MediaStatus.PENDING_UPLOAD; asset.uploadExpiresAt = expiresAt; asset.createdAt = now;
        return asset;
    }

    public void markReady(Instant now) {
        if (mediaStatus == MediaStatus.PENDING_UPLOAD) { mediaStatus = MediaStatus.READY; verifiedAt = now; }
    }
    public void markFailed() { if (mediaStatus == MediaStatus.PENDING_UPLOAD) mediaStatus = MediaStatus.FAILED; }
    public void markDeleted(Instant now) { if (mediaStatus == MediaStatus.PENDING_UPLOAD) { mediaStatus = MediaStatus.DELETED; deletedAt = now; } }
    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getStorageKey() { return storageKey; }
    public MediaPurpose getPurpose() { return purpose; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public MediaStatus getMediaStatus() { return mediaStatus; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
