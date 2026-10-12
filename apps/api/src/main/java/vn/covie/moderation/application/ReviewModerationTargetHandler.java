package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.reviews.model.ReviewStatus;
import vn.covie.reviews.application.RatingProjectionService;
import vn.covie.reviews.persistence.ReviewRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

@Component
public class ReviewModerationTargetHandler implements ModerationTargetHandler {
    private final ReviewRepository reviews; private final RatingProjectionService ratings;
    public ReviewModerationTargetHandler(ReviewRepository reviews, RatingProjectionService ratings) { this.reviews=reviews; this.ratings=ratings; }
    @Override public ModerationTargetType supports() { return ModerationTargetType.REVIEW; }
    @Override @Transactional(readOnly=true) public ModerationTargetSnapshot inspect(UUID id) { return snapshot(reviews.findById(id).orElseThrow()); }
    @Override @Transactional(noRollbackFor = ApiException.class)
    public ModerationTargetMutationResult apply(UUID id, ModerationActionType action, Instant now) {
        var review=reviews.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Review was not found."));
        var before=snapshot(review);
        ReviewStatus old=review.getReviewStatus();
        if(action==ModerationActionType.HIDE) {
            if(old!=ReviewStatus.ACTIVE) throw stateConflict();
            review.hide(now);
        } else if(action==ModerationActionType.RESTORE) {
            if(old!=ReviewStatus.HIDDEN) throw stateConflict();
            review.restore(now);
        } else throw new IllegalArgumentException("Review only supports HIDE and RESTORE.");
        ratings.onVisibilityTransition(review,old,review.getReviewStatus());
        return new ModerationTargetMutationResult(before,snapshot(review));
    }
    private static ApiException stateConflict() {
        return new ApiException(ErrorCode.MODERATION_TARGET_STATE_CONFLICT, HttpStatus.CONFLICT,
                "Review is not in a compatible moderation state.");
    }
    private ModerationTargetSnapshot snapshot(vn.covie.reviews.model.ReviewEntity r) { return new ModerationTargetSnapshot(ModerationTargetType.REVIEW,r.getId(),r.getReviewStatus().name(),r.getReviewStatus().name()); }
}
