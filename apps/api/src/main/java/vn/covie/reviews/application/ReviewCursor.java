package vn.covie.reviews.application;

import java.time.Instant;
import java.util.UUID;

public record ReviewCursor(int v, Instant createdAt, UUID reviewId) {
    public ReviewCursor(Instant createdAt, UUID reviewId) { this(1, createdAt, reviewId); }
}
