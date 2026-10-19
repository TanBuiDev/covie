package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;

class CatalogVideoUploadPolicyTest {
    @Test void disabledFlagRejectsBeforePermissionStorageOrProbe() {
        var storage = mock(ObjectStorage.class);
        var memberships = mock(MerchantMembershipRepository.class);
        var access = mock(MerchantAccessPolicy.class);
        var probe = mock(CatalogVideoProbe.class);
        var policy = new CatalogVideoUploadPolicy(storage, memberships, access, probe, false, 2, "https://video.example.invalid", "PT20S");
        assertThatThrownBy(() -> policy.requireUpload(new CurrentActor(UUID.randomUUID(), Set.of(), UUID.randomUUID())))
                .isInstanceOf(ApiException.class).satisfies(failure -> assertThat(((ApiException) failure).status()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(storage, memberships, access, probe);
    }

    @Test void boundsConcurrentWorkAndReleasesCapacityAfterCompletion() throws Exception {
        var storage = mock(ObjectStorage.class);
        var probe = mock(CatalogVideoProbe.class);
        var policy = new CatalogVideoUploadPolicy(storage, null, null, probe, true, 1, "https://video.example.invalid", "PT20S");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { Files.write(invocation.getArgument(1, Path.class), new byte[] {1}); return null; })
                .when(storage).downloadTo(anyString(), any(Path.class), anyLong(), any(java.time.Duration.class));
        when(probe.inspect(any(Path.class), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new CatalogVideoProbe.Metadata(64, 48, 1000, "h264");
        });
        var asset = MediaAssetEntity.pending(UUID.randomUUID(), UUID.randomUUID(), "owned/video.mp4", MediaPurpose.CATALOG_VIDEO,
                "video/mp4", 1, Instant.now().plusSeconds(600), Instant.now());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> policy.verify(asset));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> policy.verify(asset)).isInstanceOf(ApiException.class)
                        .satisfies(failure -> assertThat(((ApiException) failure).status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
            } finally { release.countDown(); }
            assertThat(first.get(5, TimeUnit.SECONDS)).isPresent();
            assertThat(policy.verify(asset)).isPresent();
        }
    }
}
