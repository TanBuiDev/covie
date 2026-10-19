package vn.covie.media.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class MediaAttachmentPolicy {
    private final MediaAssetRepository repository;
    public MediaAttachmentPolicy(MediaAssetRepository repository) { this.repository = repository; }

    public MediaAssetEntity requireReadyOwned(UUID mediaId, UUID userId, Set<MediaPurpose> allowedPurposes) {
        MediaAssetEntity media = repository.findByIdAndOwnerUserId(mediaId, userId).orElseThrow(() -> new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
        if (media.getMediaStatus() != MediaStatus.READY || !allowedPurposes.contains(media.getPurpose())) throw new ApiException(
                ErrorCode.MEDIA_NOT_ATTACHABLE, HttpStatus.UNPROCESSABLE_ENTITY, "Media cannot be attached to this resource.");
        return media;
    }
    public void requireMerchantLogo(UUID mediaId, UUID ownerUserId) {
        requireReadyOwned(mediaId, ownerUserId, Set.of(MediaPurpose.MERCHANT_LOGO));
    }

    public MediaAssetEntity lockReadyOwnedCatalogVideo(UUID mediaId, UUID userId) {
        var asset = repository.findByIdAndOwnerUserIdForUpdate(mediaId, userId).orElseThrow(() -> new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
        if (asset.getPurpose() != MediaPurpose.CATALOG_VIDEO || asset.getMediaStatus() != MediaStatus.READY || asset.getDeletedAt() != null)
            throw new ApiException(ErrorCode.MEDIA_NOT_ATTACHABLE, HttpStatus.UNPROCESSABLE_ENTITY, "Media cannot be attached to this resource.");
        return asset;
    }
    public void requireMerchantCover(UUID mediaId, UUID ownerUserId) {
        requireReadyOwned(mediaId, ownerUserId, Set.of(MediaPurpose.MERCHANT_COVER));
    }
    public void requireMerchantContactQr(UUID mediaId, UUID ownerUserId) {
        requireReadyOwned(mediaId, ownerUserId, Set.of(MediaPurpose.MERCHANT_CONTACT_QR));
    }
}
