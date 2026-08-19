package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class MediaAttachmentPolicyTest {
    private final MediaAssetRepository repository = org.mockito.Mockito.mock(MediaAssetRepository.class);
    private final MediaAttachmentPolicy policy = new MediaAttachmentPolicy(repository);

    @Test
    void hidesMediaOwnedByAnotherUser() {
        UUID mediaId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        when(repository.findByIdAndOwnerUserId(mediaId, actor)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policy.requireReadyOwned(mediaId, actor, Set.of(MediaPurpose.MERCHANT_LOGO)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void rejectsPendingMediaEvenForItsOwnerAndAllowedPurpose() {
        MediaAssetEntity pending = asset(MediaPurpose.MERCHANT_LOGO);
        when(repository.findByIdAndOwnerUserId(pending.getId(), pending.getOwnerUserId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> policy.requireReadyOwned(pending.getId(), pending.getOwnerUserId(), Set.of(MediaPurpose.MERCHANT_LOGO)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.MEDIA_NOT_ATTACHABLE));
    }

    @Test
    void rejectsReadyMediaWhosePurposeIsNotAllowed() {
        MediaAssetEntity readyCover = asset(MediaPurpose.MERCHANT_COVER);
        readyCover.markReady(Instant.now());
        when(repository.findByIdAndOwnerUserId(readyCover.getId(), readyCover.getOwnerUserId())).thenReturn(Optional.of(readyCover));

        assertThatThrownBy(() -> policy.requireReadyOwned(readyCover.getId(), readyCover.getOwnerUserId(), Set.of(MediaPurpose.MERCHANT_LOGO)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.MEDIA_NOT_ATTACHABLE));
    }

    private static MediaAssetEntity asset(MediaPurpose purpose) {
        Instant now = Instant.now(); UUID id = UUID.randomUUID(); UUID owner = UUID.randomUUID();
        return MediaAssetEntity.pending(id, owner, "users/%s/%s.png".formatted(owner, id), purpose,
                "image/png", 8, now.plusSeconds(600), now);
    }
}
