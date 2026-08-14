package vn.covie.media.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaStatus;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {
    long countByOwnerUserIdAndMediaStatus(UUID userId, MediaStatus status);
    Optional<MediaAssetEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    List<MediaAssetEntity> findByMediaStatusAndCreatedAtBeforeOrderByIdAsc(MediaStatus status, Instant before, Pageable pageable);
}
