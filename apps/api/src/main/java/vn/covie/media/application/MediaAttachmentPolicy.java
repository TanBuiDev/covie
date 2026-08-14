package vn.covie.media.application;

import java.util.UUID;

/**
 * Cross-task attachment authorization port. Task 8 supplies the READY/owner/purpose-backed adapter.
 */
public interface MediaAttachmentPolicy {
    void requireMerchantLogo(UUID mediaId, UUID ownerUserId);

    void requireMerchantCover(UUID mediaId, UUID ownerUserId);

    /**
     * Task 8 overrides this port with READY/owner/purpose validation. Keeping a
     * fail-closed default preserves compatibility with the temporary adapter and
     * with existing test fakes while preventing an unchecked QR attachment.
     */
    default void requireMerchantContactQr(UUID mediaId, UUID ownerUserId) {
        throw new IllegalStateException("Merchant contact QR validation is not configured.");
    }
}
