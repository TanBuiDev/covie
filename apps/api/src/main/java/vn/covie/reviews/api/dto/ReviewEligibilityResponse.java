package vn.covie.reviews.api.dto;

import java.util.UUID;

public record ReviewEligibilityResponse(UUID orderItemId, boolean eligible, ReviewEligibilityReason reason) {}
