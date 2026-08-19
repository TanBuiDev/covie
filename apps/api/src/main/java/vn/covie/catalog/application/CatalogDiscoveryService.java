package vn.covie.catalog.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.catalog.api.dto.discovery.CatalogCursor;
import vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery;
import vn.covie.catalog.api.dto.discovery.CatalogDiscoveryResponse;
import vn.covie.catalog.api.dto.discovery.CatalogItemSummaryResponse;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.catalog.api.dto.discovery.CursorPageResponse;
import vn.covie.catalog.api.dto.publicview.PublicMediaResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantSummary;
import vn.covie.catalog.api.dto.publicview.ResolvedMerchantLocationResponse;
import vn.covie.catalog.persistence.CatalogDiscoveryRepository;
import vn.covie.location.application.LocationContext;
import vn.covie.location.application.LocationContextResolver;
import vn.covie.media.storage.StorageProperties;
import vn.covie.shared.money.Money;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CatalogDiscoveryService {
    private static final int DEFAULT_RADIUS_METERS = 5_000;
    private static final int MIN_RADIUS_METERS = 100;
    private static final int MAX_RADIUS_METERS = 50_000;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final CatalogDiscoveryRepository repository;
    private final LocationContextResolver contexts;
    private final CatalogCursorCodec cursors;
    private final AppClock clock;
    private final StorageProperties storage;

    public CatalogDiscoveryService(CatalogDiscoveryRepository repository, LocationContextResolver contexts,
            AppClock clock, StorageProperties storage) {
        this.repository = repository;
        this.contexts = contexts;
        this.clock = clock;
        this.storage = storage;
        this.cursors = new CatalogCursorCodec();
    }

    public CatalogDiscoveryResponse discover(CatalogDiscoveryQuery requested) {
        CatalogDiscoveryQuery query = normalize(requested);
        LocationContext context = contexts.resolveForDiscovery(query.lat(), query.lng(), query.addressId());
        String scope = scope(query, context);
        CatalogCursor cursor = cursors.decode(query.cursor(), query.sort(), scope);
        List<CatalogDiscoveryRepository.Row> rows = repository.find(query, context, cursor, clock.now());
        boolean hasMore = rows.size() > query.limit();
        List<CatalogDiscoveryRepository.Row> pageRows = hasMore ? rows.subList(0, query.limit()) : rows;
        List<CatalogItemSummaryResponse> items = pageRows.stream().map(row -> summary(row, context)).toList();
        String nextCursor = hasMore ? cursors.encode(cursorOf(pageRows.getLast(), query.sort()), scope) : null;
        return new CatalogDiscoveryResponse(new CursorPageResponse<>(items, hasMore, nextCursor));
    }

    private CatalogDiscoveryQuery normalize(CatalogDiscoveryQuery query) {
        int radius = query.radiusMeters() == null ? DEFAULT_RADIUS_METERS : query.radiusMeters();
        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
        if (radius < MIN_RADIUS_METERS || radius > MAX_RADIUS_METERS || limit < 1 || limit > MAX_LIMIT
                || nonNegative(query.minPrice()) || nonNegative(query.maxPrice())
                || (query.minPrice() != null && query.maxPrice() != null && query.minPrice() > query.maxPrice())) {
            throw invalid("Catalog discovery filters are invalid.");
        }
        String term = query.q() == null || query.q().isBlank() ? null : query.q().trim();
        CatalogSort sort = query.sort() == null ? (term == null ? CatalogSort.DISTANCE : CatalogSort.RELEVANCE) : query.sort();
        if (sort == CatalogSort.RELEVANCE && term == null) {
            throw invalid("Relevance sorting requires a search query.");
        }
        return new CatalogDiscoveryQuery(term, query.type(), query.listingKind(), query.categoryId(), query.lat(), query.lng(),
                query.addressId(), radius, Boolean.TRUE.equals(query.pickup()), Boolean.TRUE.equals(query.openNow()),
                query.minPrice(), query.maxPrice(), sort, limit, query.cursor());
    }

    private static boolean nonNegative(Long price) {
        return price != null && price < 0;
    }

    private CatalogItemSummaryResponse summary(CatalogDiscoveryRepository.Row row, LocationContext context) {
        PublicMediaResponse primaryMedia = row.primaryMediaId() == null ? null : new PublicMediaResponse(row.primaryMediaId(),
                row.primaryMediaMimeType(), publicUrl(row.primaryMediaStorageKey()));
        return new CatalogItemSummaryResponse(row.itemId(), row.itemType(), row.listingKind(), row.name(), row.slug(), primaryMedia,
                new PublicMerchantSummary(row.merchantId(), row.merchantName(), row.merchantSlug()),
                new ResolvedMerchantLocationResponse(row.locationId(), row.locationName(), row.addressText(),
                        (double) row.distanceMeters(), context.source()),
                new Money(row.minPrice(), row.currencyCode()));
    }

    private URI publicUrl(String key) {
        String base = storage.publicBaseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + "/" + key);
    }

    private static CatalogCursor cursorOf(CatalogDiscoveryRepository.Row row, CatalogSort sort) {
        return switch (sort) {
            case RELEVANCE -> new CatalogCursor(sort, row.rank(), row.distanceMeters(), null, null, row.itemId());
            case DISTANCE -> new CatalogCursor(sort, null, row.distanceMeters(), null, null, row.itemId());
            case PRICE_ASC, PRICE_DESC -> new CatalogCursor(sort, null, null, row.minPrice(), null, row.itemId());
            case NEWEST -> new CatalogCursor(sort, null, null, null, row.createdAt(), row.itemId());
        };
    }

    private static String scope(CatalogDiscoveryQuery query, LocationContext context) {
        return String.join("|", String.valueOf(context.point().getY()), String.valueOf(context.point().getX()),
                String.valueOf(query.q()), String.valueOf(query.type()), String.valueOf(query.listingKind()),
                String.valueOf(query.categoryId()), String.valueOf(query.radiusMeters()), String.valueOf(query.pickup()),
                String.valueOf(query.openNow()), String.valueOf(query.minPrice()), String.valueOf(query.maxPrice()),
                String.valueOf(query.sort()));
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.CATALOG_FILTER_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
