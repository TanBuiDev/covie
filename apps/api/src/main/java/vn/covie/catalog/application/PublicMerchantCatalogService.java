package vn.covie.catalog.application;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.catalog.api.dto.discovery.CursorPageResponse;
import vn.covie.catalog.api.dto.publicview.PublicMediaResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCatalogCursor;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCatalogItemResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCatalogResponse;
import vn.covie.catalog.persistence.PublicMerchantCatalogRepository;
import vn.covie.media.storage.StorageProperties;
import vn.covie.shared.money.Money;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class PublicMerchantCatalogService {
    private static final int DEFAULT_LIMIT = 12;
    private static final int MAX_LIMIT = 50;

    private final PublicMerchantViewService merchants;
    private final PublicMerchantCatalogRepository repository;
    private final StorageProperties storage;
    private final PublicMerchantCatalogCursorCodec cursors = new PublicMerchantCatalogCursorCodec();

    public PublicMerchantCatalogService(PublicMerchantViewService merchants,
            PublicMerchantCatalogRepository repository, StorageProperties storage) {
        this.merchants = merchants;
        this.repository = repository;
        this.storage = storage;
    }

    public PublicMerchantCatalogResponse list(UUID merchantId, Integer requestedLimit, String encodedCursor) {
        merchants.requireVisible(merchantId);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalid("Catalog page limit is invalid.");
        }
        PublicMerchantCatalogCursor cursor = cursors.decode(encodedCursor, merchantId);
        List<PublicMerchantCatalogRepository.Row> rows = repository.findItems(merchantId, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<PublicMerchantCatalogRepository.Row> pageRows = hasMore ? rows.subList(0, limit) : rows;
        List<PublicMerchantCatalogItemResponse> items = pageRows.stream().map(this::response).toList();
        String nextCursor = hasMore ? cursors.encode(cursorOf(pageRows.getLast()), merchantId) : null;
        return new PublicMerchantCatalogResponse(new CursorPageResponse<>(items, hasMore, nextCursor));
    }

    private PublicMerchantCatalogItemResponse response(PublicMerchantCatalogRepository.Row row) {
        PublicMediaResponse primaryMedia = row.primaryMediaId() == null ? null : new PublicMediaResponse(
                row.primaryMediaId(), row.primaryMediaMimeType(), publicUrl(row.primaryMediaStorageKey()));
        return new PublicMerchantCatalogItemResponse(row.itemId(), row.itemType(), row.listingKind(), row.name(),
                row.slug(), primaryMedia, new Money(row.price(), row.currencyCode()));
    }

    private URI publicUrl(String key) {
        String base = storage.publicBaseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + "/" + key);
    }

    private static PublicMerchantCatalogCursor cursorOf(PublicMerchantCatalogRepository.Row row) {
        return new PublicMerchantCatalogCursor(row.createdAt(), row.itemId());
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.CATALOG_FILTER_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
