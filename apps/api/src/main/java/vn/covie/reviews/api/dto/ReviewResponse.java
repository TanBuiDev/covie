package vn.covie.reviews.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID orderItemId, int rating, String comment, String status, Instant createdAt) {}
