package vn.covie.catalog.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;

public interface PublicMerchantCatalogRepository {
    List<Row> find(UUID merchantId, Instant cursorCreatedAt, UUID cursorItemId, int fetchLimit, Instant now);

    record Row(
            UUID itemId,
            CatalogItemType itemType,
            ListingKind listingKind,
            String name,
            String slug,
            Instant createdAt,
            UUID merchantId,
            String merchantName,
            String merchantSlug,
            UUID locationId,
            String locationName,
            String addressText,
            boolean pickupEnabled,
            boolean sellerDeliveryEnabled,
            boolean serviceEnabled,
            boolean openNow,
            long minPrice,
            String currencyCode,
            UUID primaryMediaId,
            String primaryMediaMimeType,
            String primaryMediaStorageKey) {}
}
