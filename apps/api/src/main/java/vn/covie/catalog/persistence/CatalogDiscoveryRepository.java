package vn.covie.catalog.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.covie.catalog.api.dto.discovery.CatalogCursor;
import vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;
import vn.covie.location.application.LocationContext;

public interface CatalogDiscoveryRepository {
    List<Row> find(CatalogDiscoveryQuery query, LocationContext context, CatalogCursor cursor, Instant now);

    record Row(UUID itemId, CatalogItemType itemType, ListingKind listingKind, String name, String slug,
               Instant createdAt, UUID merchantId, String merchantName, String merchantSlug,
               UUID locationId, String locationName, String addressText, String timezone,
               long distanceMeters, long minPrice, String currencyCode,
               UUID primaryMediaId, String primaryMediaMimeType, String primaryMediaStorageKey,
               BigDecimal rank) {}
}
