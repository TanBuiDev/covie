package vn.covie.reviews.application;

import java.util.Map;
import java.util.UUID;
import vn.covie.shared.outbox.OutboxEventIntent;

final class ReviewOutboxEvents {
    private ReviewOutboxEvents() {}
    static OutboxEventIntent created(UUID reviewId, UUID orderId, UUID orderItemId, UUID merchantProfileId) {
        return new OutboxEventIntent("REVIEW_CREATED", "REVIEW", reviewId, Map.of(
                "reviewId", reviewId, "orderId", orderId, "orderItemId", orderItemId,
                "merchantProfileId", merchantProfileId));
    }
}
