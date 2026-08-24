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

@Component
public class ReviewModerationTargetHandler implements ModerationTargetHandler {
    private final ReviewRepository reviews; private final RatingProjectionService ratings;
    public ReviewModerationTargetHandler(ReviewRepository reviews, RatingProjectionService ratings) { this.reviews=reviews; this.ratings=ratings; }
    @Override public ModerationTargetType supports() { return ModerationTargetType.REVIEW; }
    @Override @Transactional(readOnly=true) public ModerationTargetSnapshot inspect(UUID id) { return snapshot(reviews.findById(id).orElseThrow()); }
    @Override @Transactional public ModerationTargetMutationResult apply(UUID id, ModerationActionType action, Instant now) { var review=reviews.findById(id).orElseThrow(); var before=snapshot(review); ReviewStatus old=review.getReviewStatus(); if(action==ModerationActionType.HIDE) review.hide(now); else if(action==ModerationActionType.RESTORE) review.restore(now); else throw new IllegalArgumentException("Review only supports HIDE and RESTORE."); ratings.onVisibilityTransition(review,old,review.getReviewStatus()); return new ModerationTargetMutationResult(before,snapshot(review)); }
    private ModerationTargetSnapshot snapshot(vn.covie.reviews.model.ReviewEntity r) { return new ModerationTargetSnapshot(ModerationTargetType.REVIEW,r.getId(),r.getReviewStatus().name(),r.getReviewStatus().name()); }
}
