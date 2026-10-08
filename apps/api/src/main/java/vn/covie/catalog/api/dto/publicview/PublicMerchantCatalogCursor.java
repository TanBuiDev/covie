package vn.covie.catalog.api.dto.publicview;

import java.time.Instant;
import java.util.UUID;

public record PublicMerchantCatalogCursor(Instant createdAt, UUID itemId) {}
