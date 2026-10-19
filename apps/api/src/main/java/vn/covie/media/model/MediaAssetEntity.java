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
    @Column(name = "width") private Integer width;
    @Column(name = "height") private Integer height;
    @Column(name = "video_duration_ms") private Long videoDurationMillis;
    @Column(name = "video_codec", length = 32) private String videoCodec;
    @Enumerated(EnumType.STRING) @Column(name = "media_status", nullable = false, length = 20) private MediaStatus mediaStatus;
    @Column(name = "upload_expires_at", nullable = false) private Instant uploadExpiresAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(name = "storage_cleanup_attempted_at") private Instant storageCleanupAttemptedAt;
    @Column(name = "storage_deleted_at") private Instant storageDeletedAt;

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
        if (purpose == MediaPurpose.CATALOG_VIDEO && (width == null || height == null || videoDurationMillis == null || videoCodec == null))
            throw new IllegalStateException("Catalog video must be verified before READY.");
        if (mediaStatus == MediaStatus.PENDING_UPLOAD) { mediaStatus = MediaStatus.READY; verifiedAt = now; }
    }
    public void recordVideoMetadata(int width, int height, long durationMillis, String codec) {
        if (purpose != MediaPurpose.CATALOG_VIDEO || mediaStatus != MediaStatus.PENDING_UPLOAD || width <= 0 || height <= 0
                || durationMillis <= 0 || durationMillis > 60_000 || !java.util.Set.of("h264", "vp8", "vp9").contains(codec))
            throw new IllegalArgumentException("Invalid catalog video metadata.");
        this.width = width; this.height = height; this.videoDurationMillis = durationMillis; this.videoCodec = codec;
    }
    public boolean markFailed() {
        if (mediaStatus != MediaStatus.PENDING_UPLOAD) return false;
        mediaStatus = MediaStatus.FAILED;
        return true;
    }
    public void markDeleted(Instant now) { if (mediaStatus == MediaStatus.PENDING_UPLOAD) { mediaStatus = MediaStatus.DELETED; deletedAt = now; } }
    public void claimVideoCleanup(Instant now) {
        if (purpose != MediaPurpose.CATALOG_VIDEO || storageDeletedAt != null) throw new IllegalStateException("Video cleanup is not applicable.");
        mediaStatus = MediaStatus.DELETED;
        if (deletedAt == null) deletedAt = now;
        storageCleanupAttemptedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getStorageKey() { return storageKey; }
    public MediaPurpose getPurpose() { return purpose; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Long getVideoDurationMillis() { return videoDurationMillis; }
    public String getVideoCodec() { return videoCodec; }
    public MediaStatus getMediaStatus() { return mediaStatus; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
