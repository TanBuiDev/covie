package vn.covie.media.application;

import java.util.UUID;

/**
 * Cross-task attachment authorization port. Task 8 supplies the READY/owner/purpose-backed adapter.
 */
public interface MediaAttachmentPolicy {
    void requireMerchantLogo(UUID mediaId, UUID ownerUserId);

    void requireMerchantCover(UUID mediaId, UUID ownerUserId);
}
