package vn.covie.media.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.media.api.dto.CreateMediaUploadRequest;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.storage.StorageProperties;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.media.storage.PresignedUpload;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class AdMediaUploadTest {
    @Test void acceptsAdVideoAboveImageLimitWithCorrectExtension() {
        var actors = mock(CurrentActorProvider.class);
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        when(actors.currentActor()).thenReturn(new CurrentActor(owner, java.util.Set.of(), UUID.randomUUID()));
        var properties = new StorageProperties("bucket", "region", null, null, false, Duration.ofMinutes(5), 10 * 1024 * 1024, 5);
        var storage = mock(ObjectStorage.class);
        var repository = mock(MediaAssetRepository.class);
        var ids = mock(UuidV7Generator.class);
        var clock = mock(AppClock.class);
        when(ids.generate()).thenReturn(id);
        when(clock.now()).thenReturn(java.time.Instant.EPOCH);
        when(storage.presignPut(any(), any(), anyLong(), any())).thenReturn(new PresignedUpload(java.net.URI.create("https://upload.test"), "PUT", java.time.Instant.EPOCH, java.util.Map.of()));
        var users=mock(vn.covie.users.persistence.UserRepository.class);
        when(users.findByIdForUpdate(owner)).thenReturn(java.util.Optional.of(vn.covie.users.model.UserEntity.active(owner,"Owner",java.time.Instant.EPOCH)));
        var service = new MediaUploadService(actors, repository, storage, properties, null, null, null, ids, clock, null, null, users);
        service.createUpload(new CreateMediaUploadRequest(MediaPurpose.AD_BANNER, "video/mp4", 20L * 1024 * 1024));
        verify(storage).presignPut("users/%s/%s.mp4".formatted(owner, id), "video/mp4", 20L * 1024 * 1024, Duration.ofMinutes(5));
        verify(repository).saveAndFlush(any());
    }
    @Test void videosCannotUseCatalogPurposeAndAdVideosHaveBoundedSize() {
        var actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(UUID.randomUUID(), java.util.Set.of(), UUID.randomUUID()));
        var properties = new StorageProperties("bucket", "region", null, null, false, Duration.ofMinutes(5), 10 * 1024 * 1024, 5);
        var service = new MediaUploadService(actors, null, null, properties, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_IMAGE, "video/mp4", 100)))
            .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException)e).code()).isEqualTo(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED));
        assertThatThrownBy(() -> service.createUpload(new CreateMediaUploadRequest(MediaPurpose.valueOf("AD_BANNER"), "video/mp4", 50L * 1024 * 1024 + 1)))
            .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException)e).code()).isEqualTo(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED));
    }
}
