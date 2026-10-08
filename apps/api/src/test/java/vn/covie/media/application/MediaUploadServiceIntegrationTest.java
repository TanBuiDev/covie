package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.media.api.dto.CreateMediaUploadRequest;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.media.storage.PresignedUpload;
import vn.covie.media.storage.StoredObjectMetadata;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MediaUploadServiceIntegrationTest extends PostgresIntegrationTest {
    private static final byte[] PNG = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @Autowired MediaUploadService uploads;
    @Autowired MediaAssetRepository media;
    @Autowired UserRepository users;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ObjectStorage storage;
    @MockitoBean CurrentActorProvider actors;
    private UUID owner;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner, "Media owner", Instant.now()));
        when(actors.currentActor()).thenReturn(new CurrentActor(owner, java.util.Set.of(), UUID.randomUUID()));
        when(storage.presignPut(any(), any(), anyLong(), any())).thenAnswer(invocation ->
                new PresignedUpload(URI.create("https://upload.example/" + invocation.getArgument(0)), "PUT",
                        Instant.parse("2026-01-01T00:10:00Z"), Map.of("content-type", invocation.getArgument(1))));
    }

    @Test
    void completesVerifiedAdVideoButRejectsContainerMismatch() {
        byte[] mp4 = new byte[] {0,0,0,20,102,116,121,112,105,115,111,109,0,0,0,0,109,112,52,50};
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.AD_BANNER, "video/mp4", mp4.length));
        var pending = media.findById(upload.mediaId()).orElseThrow();
        when(storage.head(pending.getStorageKey())).thenReturn(new StoredObjectMetadata("video/mp4", mp4.length));
        when(storage.readPrefix(pending.getStorageKey(), 4096)).thenReturn(mp4);
        assertThat(uploads.complete(upload.mediaId(), "ad-mp4").purpose()).isEqualTo(MediaPurpose.AD_BANNER);
        assertThat(media.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.READY);

        var wrong = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.AD_BANNER, "video/webm", mp4.length));
        var wrongAsset = media.findById(wrong.mediaId()).orElseThrow();
        when(storage.head(wrongAsset.getStorageKey())).thenReturn(new StoredObjectMetadata("video/webm", mp4.length));
        when(storage.readPrefix(wrongAsset.getStorageKey(), 4096)).thenReturn(mp4);
        assertThatThrownBy(() -> uploads.complete(wrong.mediaId(), "ad-wrong-container")).isInstanceOf(ApiException.class);
        assertThat(media.findById(wrong.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.FAILED);
        org.mockito.Mockito.verify(storage).deleteIfExists(wrongAsset.getStorageKey());
    }

    @Test
    void createsDeterministicDirectUploadWithoutFilenameAndCompletesVerifiedPngIdempotently() {
        var request = new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length);
        var upload = uploads.createUpload(request);

        assertThat(upload.method()).isEqualTo("PUT");
        assertThat(upload.uploadUrl()).isNotNull();
        var pending = media.findById(upload.mediaId()).orElseThrow();
        assertThat(pending.getStorageKey()).isEqualTo("users/%s/%s.png".formatted(owner, upload.mediaId()));
        assertThat(pending.getMediaStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);

        when(storage.head(pending.getStorageKey())).thenReturn(new StoredObjectMetadata("image/png", (long) PNG.length));
        when(storage.readPrefix(pending.getStorageKey(), 32)).thenReturn(PNG);
        var first = uploads.complete(upload.mediaId(), "complete-key");
        var replay = uploads.complete(upload.mediaId(), "complete-key");

        assertThat(first).isEqualTo(replay);
        assertThat(first.url().toString()).isEqualTo("https://media.test.covie/" + pending.getStorageKey());
        assertThat(media.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.READY);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where actor_id = ? and operation = 'media.complete'",
                Integer.class, owner)).isEqualTo(1);
    }

    @Test
    void marksFailedAndDeletesObjectWhenStoredObjectHasWrongSize() {
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        var pending = media.findById(upload.mediaId()).orElseThrow();
        when(storage.head(pending.getStorageKey())).thenReturn(new StoredObjectMetadata("image/png", 9));

        assertThatThrownBy(() -> uploads.complete(upload.mediaId(), "bad-size"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code())
                        .isEqualTo(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED));
        assertThat(media.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.FAILED);
        org.mockito.Mockito.verify(storage).deleteIfExists(pending.getStorageKey());
    }

    @Test
    void marksFailedWhenObjectMagicDoesNotMatchDeclaredPng() {
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        var pending = media.findById(upload.mediaId()).orElseThrow();
        when(storage.head(pending.getStorageKey())).thenReturn(new StoredObjectMetadata("image/png", (long) PNG.length));
        when(storage.readPrefix(pending.getStorageKey(), 32)).thenReturn(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});

        assertThatThrownBy(() -> uploads.complete(upload.mediaId(), "bad-magic"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code())
                        .isEqualTo(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED));
        assertThat(media.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.FAILED);
    }

    @Test
    void hidesOtherUsersMediaAndRejectsUnsupportedTypesOrPendingQuota() {
        assertThatThrownBy(() -> uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/svg+xml", 1)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED));

        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        UUID other = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(other, "Other", Instant.now()));
        when(actors.currentActor()).thenReturn(new CurrentActor(other, java.util.Set.of(), UUID.randomUUID()));
        assertThatThrownBy(() -> uploads.complete(upload.mediaId(), "other-user"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void requiresIdempotencyKeyForCompletionBeforeInspectingTheObject() {
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        assertThatThrownBy(() -> uploads.complete(upload.mediaId(), " "))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED));
    }

    @Test
    void keyUsedForReadyMediaCannotBeReusedForPendingMediaBeforeAnyVerificationSideEffect() {
        var readyUpload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        var ready = media.findById(readyUpload.mediaId()).orElseThrow();
        when(storage.head(ready.getStorageKey())).thenReturn(new StoredObjectMetadata("image/png", (long) PNG.length));
        when(storage.readPrefix(ready.getStorageKey(), 32)).thenReturn(PNG);
        uploads.complete(readyUpload.mediaId(), "shared-cross-media-key");
        var pendingUpload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        var pending = media.findById(pendingUpload.mediaId()).orElseThrow();

        assertThatThrownBy(() -> uploads.complete(pendingUpload.mediaId(), "shared-cross-media-key"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST));
        assertThat(media.findById(pendingUpload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).head(pending.getStorageKey());
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteIfExists(pending.getStorageKey());
    }

    @Test
    void keyUsedForFailedPendingMediaCannotMutateAnotherPendingMedia() {
        var firstUpload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        var first = media.findById(firstUpload.mediaId()).orElseThrow();
        when(storage.head(first.getStorageKey())).thenThrow(new IllegalStateException("missing"));
        assertThatThrownBy(() -> uploads.complete(firstUpload.mediaId(), "shared-failed-key"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED));
        var secondUpload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        var second = media.findById(secondUpload.mediaId()).orElseThrow();

        assertThatThrownBy(() -> uploads.complete(secondUpload.mediaId(), "shared-failed-key"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST));
        assertThat(media.findById(secondUpload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).head(second.getStorageKey());
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteIfExists(second.getStorageKey());
    }

    @Test
    void concurrentInvalidCompletionCannotDeleteObjectAfterValidCompletionMakesMediaReady() throws Exception {
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.USER_AVATAR, "image/png", PNG.length));
        var pending = media.findById(upload.mediaId()).orElseThrow();
        CountDownLatch validHeadEntered = new CountDownLatch(1);
        CountDownLatch releaseValidHead = new CountDownLatch(1);
        when(storage.head(pending.getStorageKey())).thenAnswer(invocation -> {
            validHeadEntered.countDown();
            releaseValidHead.await();
            return new StoredObjectMetadata("image/png", (long) PNG.length);
        });
        when(storage.readPrefix(pending.getStorageKey(), 32)).thenReturn(PNG);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> valid = executor.submit(() -> uploads.complete(upload.mediaId(), "valid-concurrent-key"));
            validHeadEntered.await();
            Future<?> invalid = executor.submit(() -> {
                when(storage.head(pending.getStorageKey())).thenThrow(new IllegalStateException("missing"));
                return uploads.complete(upload.mediaId(), "invalid-concurrent-key");
            });
            releaseValidHead.countDown();
            valid.get();
            invalid.get();
        }
        assertThat(media.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.READY);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteIfExists(pending.getStorageKey());
    }
}
