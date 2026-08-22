package vn.covie.reviews.api.dto;

import java.util.List;

public record ReviewPageResponse(List<PublicReviewResponse> items, ReviewPageInfo page, RatingSummary rating) {}
