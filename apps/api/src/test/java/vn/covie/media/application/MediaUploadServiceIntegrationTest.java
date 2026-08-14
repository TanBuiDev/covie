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
}
