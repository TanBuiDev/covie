package vn.covie.reviews.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicReviewResponse(UUID id, int rating, String comment, PublicReviewerResponse reviewer, boolean verifiedPurchase, Instant createdAt) {}
