package vn.covie.reviews.api.dto;

import java.math.BigDecimal;

public record RatingSummary(long reviewCount, BigDecimal averageRating) {}
