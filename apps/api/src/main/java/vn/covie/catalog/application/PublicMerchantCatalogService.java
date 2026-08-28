package vn.covie.catalog.application;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.catalog.api.dto.discovery.CatalogAvailability;
import vn.covie.catalog.api.dto.discovery.CatalogCursor;
import vn.covie.catalog.api.dto.discovery.CatalogItemSummaryResponse;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.catalog.api.dto.discovery.CursorPageResponse;
import vn.covie.catalog.api.dto.publicview.PublicMediaResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantSummary;
import vn.covie.catalog.api.dto.publicview.ResolvedMerchantLocationResponse;
import vn.covie.catalog.persistence.PublicMerchantCatalogRepository;
import vn.covie.media.storage.StorageProperties;
import vn.covie.shared.money.Money;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class PublicMerchantCatalogService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private final PublicMerchantViewService merchants;
    private final PublicMerchantCatalogRepository catalog;
    private final StorageProperties storage;
    private final AppClock clock;
    private final CatalogCursorCodec cursors = new CatalogCursorCodec();

    public PublicMerchantCatalogService(PublicMerchantViewService merchants,
            PublicMerchantCatalogRepository catalog, StorageProperties storage, AppClock clock) {
        this.merchants = merchants;
        this.catalog = catalog;
        this.storage = storage;
        this.clock = clock;
    }

    public CursorPageResponse<CatalogItemSummaryResponse> list(UUID merchantId, Integer requestedLimit, String cursor) {
        merchants.requireVisible(merchantId);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(ErrorCode.CATALOG_FILTER_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Catalog page limit is invalid.");
        }
        String scope = "public-merchant:" + merchantId;
        CatalogCursor decoded = cursors.decode(cursor, CatalogSort.NEWEST, scope);
        List<PublicMerchantCatalogRepository.Row> rows = catalog.find(merchantId,
                decoded == null ? null : decoded.createdAt(), decoded == null ? null : decoded.itemId(),
                limit + 1, clock.now());
        boolean hasMore = rows.size() > limit;
        List<PublicMerchantCatalogRepository.Row> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? cursors.encode(new CatalogCursor(CatalogSort.NEWEST, null, null, null,
                page.getLast().createdAt(), page.getLast().itemId()), scope) : null;
        return new CursorPageResponse<>(page.stream().map(this::toResponse).toList(), hasMore, nextCursor);
    }

    private CatalogItemSummaryResponse toResponse(PublicMerchantCatalogRepository.Row row) {
        PublicMediaResponse media = row.primaryMediaId() == null ? null : new PublicMediaResponse(row.primaryMediaId(),
                row.primaryMediaMimeType(), publicUrl(row.primaryMediaStorageKey()));
        return new CatalogItemSummaryResponse(row.itemId(), row.itemType(), row.listingKind(), row.name(), row.slug(),
                media, new PublicMerchantSummary(row.merchantId(), row.merchantName(), row.merchantSlug()),
                new ResolvedMerchantLocationResponse(row.locationId(), row.locationName(), row.addressText(), null, null,
                        row.pickupEnabled(), row.sellerDeliveryEnabled(), row.serviceEnabled(), row.openNow()),
                new Money(row.minPrice(), row.currencyCode()), CatalogAvailability.AVAILABLE);
    }

    private URI publicUrl(String storageKey) {
        String base = storage.publicBaseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + "/" + storageKey);
    }
}
