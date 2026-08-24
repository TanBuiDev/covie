package vn.covie.checkout.application;

import java.util.UUID;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.model.CheckoutSourceType;

public interface CheckoutCandidateResolver {
    CheckoutSourceType supports();
    CheckoutCandidate resolve(UUID buyerUserId, CheckoutPreviewRequest request);

    /** Called only by Commit after the checkout_session row has been locked. */
    default void lockSourceForCommit(UUID buyerUserId, CheckoutPreviewRequest request) {
        // DIRECT has no source row. CART keeps its existing explicit lock in CheckoutCommitService.
    }
}
