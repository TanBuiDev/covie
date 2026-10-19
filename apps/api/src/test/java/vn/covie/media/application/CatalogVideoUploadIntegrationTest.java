package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.media.api.dto.CreateMediaUploadRequest;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.media.storage.PresignedUpload;
import vn.covie.media.storage.StoredObjectMetadata;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@SpringBootTest(properties = {"covie.social-v2.video-enabled=true", "covie.production.public-url=https://video.example.invalid", "covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false"})
class CatalogVideoUploadIntegrationTest extends PostgresIntegrationTest {
    private static final byte[] MP4 = {0,0,0,24,'f','t','y','p','i','s','o','m',0,0,0,0,0,0,0,0,'i','s','o','m'};
    @Autowired MediaUploadService uploads;
    @Autowired MediaAssetRepository assets;
    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ObjectStorage storage;
    @MockitoBean CurrentActorProvider actors;
    @MockitoBean CatalogVideoProbe probe;
    private UUID owner;
    private UUID membership;

    @BeforeEach void fixture() throws Exception {
        owner = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner, "Video seller", Instant.now()));
        var merchant = merchants.saveAndFlush(MerchantProfileEntity.create(UUID.randomUUID(), "Video shop", "video-" + owner, Instant.now()));
        membership = memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchant.getId(), owner, MerchantRole.OWNER, Instant.now())).getId();
        when(actors.currentActor()).thenReturn(new CurrentActor(owner, Set.of(), UUID.randomUUID()));
        when(storage.presignPut(anyString(), anyString(), anyLong(), any())).thenReturn(
                new PresignedUpload(URI.create("https://disposable.example.invalid/upload"), "PUT", Instant.now().plusSeconds(600), Map.of()));
        when(storage.head(anyString())).thenReturn(new StoredObjectMetadata("video/mp4", MP4.length));
        when(storage.readPrefix(anyString(), any(Integer.class))).thenReturn(MP4);
        doAnswer(invocation -> { Files.write(invocation.getArgument(1, Path.class), MP4); return null; })
                .when(storage).downloadTo(anyString(), any(Path.class), anyLong(), any(java.time.Duration.class));
        when(probe.inspect(any(Path.class), anyString())).thenAnswer(invocation -> {
            assertThat(Files.readAllBytes(invocation.getArgument(0, Path.class))).containsExactly(MP4);
            return new CatalogVideoProbe.Metadata(64, 48, 1000, "h264");
        });
    }

    @Test void restrictsVideoPurposeToCatalogAuthorsAndApprovedMimeAndSize() {
        uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/mp4", MP4.length));
        assertStatus(() -> uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "image/png", 20)), HttpStatus.UNPROCESSABLE_ENTITY);
        assertStatus(() -> uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/mp4", 50L * 1024 * 1024 + 1)), HttpStatus.UNPROCESSABLE_ENTITY);
        jdbc.update("update merchant_memberships set role='MANAGER' where id=?", membership);
        uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/webm", 20));
        jdbc.update("update merchant_memberships set role='STAFF' where id=?", membership);
        assertStatus(() -> uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/mp4", 20)), HttpStatus.FORBIDDEN);
        jdbc.update("update merchant_memberships set membership_status='REVOKED' where id=?", membership);
        assertStatus(() -> uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/mp4", 20)), HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select count(*) from media_assets where owner_user_id=?", Long.class, owner)).isEqualTo(2);
    }

    @Test void persistsOnlyVerifiedMetadataAndReplaysWithoutReprobeOrKeyLeak() throws Exception {
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/mp4", MP4.length));
        var response = uploads.complete(upload.mediaId(), "video-ready");
        assertThat(response.url()).isEqualTo(URI.create("https://video.example.invalid/api/v1/media/" + upload.mediaId() + "/content"));
        assertThat(uploads.complete(upload.mediaId(), "video-ready")).isEqualTo(response);
        MediaAssetEntity asset = assets.findById(upload.mediaId()).orElseThrow();
        assertThat(asset.getMediaStatus()).isEqualTo(MediaStatus.READY);
        assertThat(asset.getWidth()).isEqualTo(64);
        assertThat(asset.getHeight()).isEqualTo(48);
        assertThat(asset.getVideoDurationMillis()).isEqualTo(1000);
        assertThat(asset.getVideoCodec()).isEqualTo("h264");
        var file = ArgumentCaptor.forClass(Path.class);
        verify(probe, times(1)).inspect(file.capture(), anyString());
        assertThat(file.getValue()).doesNotExist();
        assertThat(file.getValue().getParent()).doesNotExist();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='MEDIA_UPLOAD_COMPLETED'", Long.class, upload.mediaId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where actor_id=? and status='COMPLETED'", Long.class, owner)).isEqualTo(1);
    }

    @Test void invalidVideoFailsOnceAndDeletesOnlyItsObject() throws Exception {
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/mp4", MP4.length));
        when(probe.inspect(any(Path.class), anyString())).thenThrow(new CatalogVideoProbe.InvalidVideoException());
        assertStatus(() -> uploads.complete(upload.mediaId(), "video-invalid"), HttpStatus.UNPROCESSABLE_ENTITY);
        assertStatus(() -> uploads.complete(upload.mediaId(), "video-invalid"), HttpStatus.UNPROCESSABLE_ENTITY);
        MediaAssetEntity asset = assets.findById(upload.mediaId()).orElseThrow();
        assertThat(asset.getMediaStatus()).isEqualTo(MediaStatus.FAILED);
        assertThat(asset.getVideoDurationMillis()).isNull();
        verify(probe, times(1)).inspect(any(Path.class), anyString());
        verify(storage, times(1)).deleteIfExists(asset.getStorageKey());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='MEDIA_UPLOAD_COMPLETED'", Long.class, upload.mediaId())).isZero();
    }

    @Test void unavailableProbeKeepsPendingAndAllowsSameKeyRetry() throws Exception {
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/mp4", MP4.length));
        when(probe.inspect(any(Path.class), anyString())).thenThrow(new IOException("tool unavailable"));
        assertStatus(() -> uploads.complete(upload.mediaId(), "video-retry"), HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(assets.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where actor_id=?", Long.class, owner)).isZero();
        when(probe.inspect(any(Path.class), anyString())).thenReturn(new CatalogVideoProbe.Metadata(64, 48, 1000, "h264"));
        uploads.complete(upload.mediaId(), "video-retry");
        assertThat(assets.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.READY);
    }

    @Test void unavailableStorageKeepsVideoPendingWithoutDeletingItsObject() {
        var upload = uploads.createUpload(new CreateMediaUploadRequest(MediaPurpose.CATALOG_VIDEO, "video/mp4", MP4.length));
        when(storage.head(anyString())).thenThrow(new IllegalStateException("storage temporarily unavailable"));
        assertStatus(() -> uploads.complete(upload.mediaId(), "video-storage-retry"), HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(assets.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        verify(storage, org.mockito.Mockito.never()).deleteIfExists(anyString());
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where actor_id=?", Long.class, owner)).isZero();
        org.mockito.Mockito.doReturn(new StoredObjectMetadata("video/mp4", MP4.length)).when(storage).head(anyString());
        uploads.complete(upload.mediaId(), "video-storage-retry");
        assertThat(assets.findById(upload.mediaId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.READY);
    }

    private static void assertStatus(Runnable operation, HttpStatus status) {
        assertThatThrownBy(operation::run).isInstanceOf(ApiException.class)
                .satisfies(failure -> assertThat(((ApiException) failure).status()).isEqualTo(status));
    }
}
