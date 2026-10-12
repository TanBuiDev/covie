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
    void cleanupLeavesOldReadyAndRecentPendingUploadsUntouched() {
        UUID owner=UUID.randomUUID(); Instant now=Instant.now(),old=Instant.parse("1800-01-01T00:00:00Z");
        users.saveAndFlush(UserEntity.active(owner,"Cleanup boundaries",now));
        String prefix="users/"+owner+"/";
        var expired=MediaAssetEntity.pending(UUID.randomUUID(),owner,prefix+"expired.png",MediaPurpose.USER_AVATAR,"image/png",8,old.plusSeconds(600),old);
        var ready=MediaAssetEntity.pending(UUID.randomUUID(),owner,prefix+"ready.png",MediaPurpose.USER_AVATAR,"image/png",8,old.plusSeconds(600),old);
        ready.markReady(now);
        var recent=MediaAssetEntity.pending(UUID.randomUUID(),owner,prefix+"recent.png",MediaPurpose.USER_AVATAR,"image/png",8,now.plusSeconds(600),now);
        media.saveAllAndFlush(java.util.List.of(expired,ready,recent));
        assertThat(cleanup.cleanupExpiredPending(1)).isEqualTo(1);
        assertThat(media.findById(expired.getId()).orElseThrow().getMediaStatus()).isEqualTo(vn.covie.media.model.MediaStatus.DELETED);
        assertThat(media.findById(ready.getId()).orElseThrow().getMediaStatus()).isEqualTo(vn.covie.media.model.MediaStatus.READY);
        assertThat(media.findById(recent.getId()).orElseThrow().getMediaStatus()).isEqualTo(vn.covie.media.model.MediaStatus.PENDING_UPLOAD);
        verify(storage).deleteIfExists(expired.getStorageKey());
        verify(storage,org.mockito.Mockito.never()).deleteIfExists(ready.getStorageKey());
        verify(storage,org.mockito.Mockito.never()).deleteIfExists(recent.getStorageKey());
    }

    @Test
    void claimsOnlyTheRequestedBoundOfOldPendingUploadsBeforeBestEffortDeletion() {
        UUID owner = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner, "Cleanup", Instant.now()));
        Instant old = Instant.now().minus(365, ChronoUnit.DAYS);
        MediaAssetEntity first = MediaAssetEntity.pending(new UUID(0, 1), owner, "users/a.png", MediaPurpose.USER_AVATAR,
                "image/png", 8, old.plusSeconds(600), old);
        MediaAssetEntity second = MediaAssetEntity.pending(new UUID(0, 2), owner, "users/b.png", MediaPurpose.USER_AVATAR,
                "image/png", 8, old.plusSeconds(600), old);
        media.saveAllAndFlush(java.util.List.of(first, second));

        assertThat(cleanup.cleanupExpiredPending(1)).isEqualTo(1);
        verify(storage).deleteIfExists(org.mockito.ArgumentMatchers.anyString());
    }
}
