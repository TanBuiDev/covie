package vn.covie.catalog.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCatalogCursor;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;

public interface PublicMerchantCatalogRepository {
    List<CatalogItemType> findCatalogTypes(UUID merchantId);

    List<Row> findItems(UUID merchantId, PublicMerchantCatalogCursor cursor, int fetchLimit);

    record Row(
            UUID itemId,
            CatalogItemType itemType,
            ListingKind listingKind,
            String name,
            String slug,
            Instant createdAt,
            long price,
            String currencyCode,
            UUID primaryMediaId,
            String primaryMediaMimeType,
            String primaryMediaStorageKey) {}
}
