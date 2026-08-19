package vn.covie.catalog.api.dto.discovery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CatalogCursor(
        CatalogSort sort,
        BigDecimal rank,
        Long distanceMeters,
        Long price,
        Instant createdAt,
        UUID itemId) {}
