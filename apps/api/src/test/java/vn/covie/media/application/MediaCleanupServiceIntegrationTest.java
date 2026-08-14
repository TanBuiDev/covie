package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MediaCleanupServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired MediaCleanupService cleanup;
    @Autowired MediaAssetRepository media;
    @Autowired UserRepository users;
    @MockitoBean ObjectStorage storage;

    @Test
    void claimsOnlyTheRequestedBoundOfOldPendingUploadsBeforeBestEffortDeletion() {
        UUID owner = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner, "Cleanup", Instant.now()));
        Instant old = Instant.now().minus(25, ChronoUnit.HOURS);
        MediaAssetEntity first = MediaAssetEntity.pending(UUID.randomUUID(), owner, "users/a.png", MediaPurpose.USER_AVATAR,
                "image/png", 8, old.plusSeconds(600), old);
        MediaAssetEntity second = MediaAssetEntity.pending(UUID.randomUUID(), owner, "users/b.png", MediaPurpose.USER_AVATAR,
                "image/png", 8, old.plusSeconds(600), old);
        media.saveAllAndFlush(java.util.List.of(first, second));

        assertThat(cleanup.cleanupExpiredPending(1)).isEqualTo(1);
        assertThat(media.countByOwnerUserIdAndMediaStatus(owner, MediaStatus.DELETED)).isEqualTo(1);
        verify(storage).deleteIfExists(org.mockito.ArgumentMatchers.anyString());
    }
}
