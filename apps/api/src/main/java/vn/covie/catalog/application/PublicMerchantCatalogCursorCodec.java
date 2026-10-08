package vn.covie.catalog.application;

import java.util.UUID;
import vn.covie.catalog.api.dto.discovery.CatalogCursor;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCatalogCursor;

public final class PublicMerchantCatalogCursorCodec {
    private final CatalogCursorCodec delegate = new CatalogCursorCodec();

    public String encode(PublicMerchantCatalogCursor cursor, UUID merchantId) {
        return delegate.encode(new CatalogCursor(CatalogSort.NEWEST, null, null, null,
                cursor.createdAt(), cursor.itemId()), scope(merchantId));
    }

    public PublicMerchantCatalogCursor decode(String encoded, UUID merchantId) {
        CatalogCursor cursor = delegate.decode(encoded, CatalogSort.NEWEST, scope(merchantId));
        return cursor == null ? null : new PublicMerchantCatalogCursor(cursor.createdAt(), cursor.itemId());
    }

    private static String scope(UUID merchantId) {
        return "public-merchant-catalog:" + merchantId;
    }
}
