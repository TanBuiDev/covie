package vn.covie.media.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaStatus;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {
    @Query(value="select exists(select 1 from catalog_item_media where media_asset_id=:id)",nativeQuery=true)
    boolean hasCatalogReference(@org.springframework.data.repository.query.Param("id") UUID id);
    @Query(value = """
            select asset.* from media_assets asset
            where asset.upload_purpose='CATALOG_VIDEO' and asset.storage_deleted_at is null
              and asset.upload_expires_at < :before
              and (asset.media_status<>'READY' or asset.verified_at < :before)
              and (asset.storage_cleanup_attempted_at is null or asset.storage_cleanup_attempted_at < :retryBefore)
              and not exists (select 1 from catalog_item_media link where link.media_asset_id=asset.id)
            order by coalesce(asset.storage_cleanup_attempted_at,asset.upload_expires_at),asset.id
            limit 1 for update of asset skip locked
            """, nativeQuery = true)
    List<MediaAssetEntity> claimExpiredCatalogVideo(
            @org.springframework.data.repository.query.Param("before") Instant before,
            @org.springframework.data.repository.query.Param("retryBefore") Instant retryBefore);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update MediaAssetEntity asset set asset.storageDeletedAt=:now where asset.id=:id and asset.mediaStatus=vn.covie.media.model.MediaStatus.DELETED and asset.storageDeletedAt is null and asset.storageCleanupAttemptedAt=:attempt")
    int completeVideoStorageDeletion(@org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("attempt") Instant attempt,
            @org.springframework.data.repository.query.Param("now") Instant now);
    Optional<MediaAssetEntity> findByIdAndMediaStatusAndPurposeAndDeletedAtIsNull(
            UUID id, MediaStatus status, vn.covie.media.model.MediaPurpose purpose);
    long countByOwnerUserIdAndMediaStatus(UUID userId, MediaStatus status);
    List<MediaAssetEntity> findAllByIdInAndMediaStatusAndPurposeAndDeletedAtIsNull(
            List<UUID> ids, MediaStatus status, vn.covie.media.model.MediaPurpose purpose);
    Optional<MediaAssetEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select media from MediaAssetEntity media where media.id = :id and media.ownerUserId = :ownerUserId")
    Optional<MediaAssetEntity> findByIdAndOwnerUserIdForUpdate(UUID id, UUID ownerUserId);
    @Query(value = "select * from media_assets where media_status='PENDING_UPLOAD' and created_at < :before order by created_at asc, id asc for update skip locked limit :limit", nativeQuery = true)
    List<MediaAssetEntity> claimExpiredPending(
            @org.springframework.data.repository.query.Param("before") Instant before,
            @org.springframework.data.repository.query.Param("limit") int limit);
}
