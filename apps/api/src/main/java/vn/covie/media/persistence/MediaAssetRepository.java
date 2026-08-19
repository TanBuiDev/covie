package vn.covie.media.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaStatus;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {
    long countByOwnerUserIdAndMediaStatus(UUID userId, MediaStatus status);
    Optional<MediaAssetEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select media from MediaAssetEntity media where media.id = :id and media.ownerUserId = :ownerUserId")
    Optional<MediaAssetEntity> findByIdAndOwnerUserIdForUpdate(UUID id, UUID ownerUserId);
    List<MediaAssetEntity> findByMediaStatusAndCreatedAtBeforeOrderByIdAsc(MediaStatus status, Instant before, Pageable pageable);
}
