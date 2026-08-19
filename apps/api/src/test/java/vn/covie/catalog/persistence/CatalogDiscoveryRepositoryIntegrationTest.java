package vn.covie.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery;
import vn.covie.catalog.api.dto.discovery.CatalogCursor;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row;
import vn.covie.catalog.application.CatalogSearchDocumentService;
import vn.covie.location.application.GeoPointFactory;
import vn.covie.location.application.LocationContext;
import vn.covie.location.application.LocationResolutionSource;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantLocationHourEntity;
import vn.covie.merchants.model.MerchantLocationHourExceptionEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.OperationType;
import vn.covie.merchants.persistence.MerchantLocationHourExceptionRepository;
import vn.covie.merchants.persistence.MerchantLocationHourRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;

class CatalogDiscoveryRepositoryIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired CatalogDiscoveryRepository discovery;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired CatalogSearchDocumentService searchDocuments;
    @Autowired JdbcTemplate jdbc;
    @Autowired MerchantLocationHourRepository locationHours;
    @Autowired MerchantLocationHourExceptionRepository exceptions;

    @Test
    void resolvesTheNearestConcreteLocationBeforePriceSorting() {
        Fixture fixture = fixture();
        UUID fartherLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Far but cheap", "Far", 10.807, 106.707, NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fartherLocation, fixture.variantId(), 1_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));

        Row result = discovery.find(query(CatalogSort.PRICE_ASC, 5_000, fixture.categoryId()), context(), null, NOW).getFirst();

        assertThat(result.itemId()).isEqualTo(fixture.itemId());
        assertThat(result.locationId()).isEqualTo(fixture.nearLocationId());
        assertThat(result.minPrice()).isEqualTo(25_000);
        assertThat(result.distanceMeters()).isLessThan(10);
    }

    @Test
    void excludesItemsWhoseOnlyOfferedLocationFallsOutsideTheRadius() {
        Fixture fixture = fixture();
        UUID merchantId = merchants.saveAndFlush(MerchantProfileEntity.create(UUID.randomUUID(), "Outside", "outside-" + UUID.randomUUID(), NOW)).getId();
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Outside", "Outside", 10.82, 106.72, NOW)).getId();
        UUID itemId = activeItem(merchantId, "Outside coffee");
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 20_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));

        assertThat(discovery.find(query(CatalogSort.DISTANCE, 500), context(), null, NOW))
                .extracting(Row::itemId)
                .contains(fixture.itemId())
                .doesNotContain(itemId);
    }

    @Test
    void returnsTheNextRelevancePageWithoutRepeatingTheCursorItem() {
        Fixture fixture = fixture();
        UUID secondItem = activeItem(fixture.merchantId(), CatalogItemType.PRODUCT, ListingKind.STANDARD,
                fixture.categoryId(), "Coffee beans");
        UUID secondVariant = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), secondItem, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.nearLocationId(), secondVariant, 30_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));
        searchDocuments.rebuild(fixture.itemId());
        searchDocuments.rebuild(secondItem);
        CatalogDiscoveryQuery query = new CatalogDiscoveryQuery("coffee", null, null, fixture.categoryId(), 10.8, 106.7, null, 5_000,
                false, false, null, null, CatalogSort.RELEVANCE, 1, null);

        Row first = discovery.find(query, context(), null, NOW).getFirst();
        CatalogCursor cursor = new CatalogCursor(CatalogSort.RELEVANCE, first.rank(), first.distanceMeters(), null, null, first.itemId());

        assertThat(discovery.find(query, context(), cursor, NOW)).extracting(Row::itemId)
                .doesNotContain(first.itemId())
                .isNotEmpty();
    }

    @Test
    void hidesAnItemWhenItsMerchantOrOnlyActiveLocationBecomesInactive() {
        Fixture fixture = fixture();
        jdbc.update("update merchant_locations set location_status = 'PAUSED' where id = ?", fixture.nearLocationId());

        assertThat(discovery.find(query(CatalogSort.DISTANCE, 5_000), context(), null, NOW))
                .extracting(Row::itemId).doesNotContain(fixture.itemId());

        jdbc.update("update merchant_locations set location_status = 'ACTIVE' where id = ?", fixture.nearLocationId());
        jdbc.update("update merchant_profiles set merchant_status = 'PAUSED' where id = ?", fixture.merchantId());

        assertThat(discovery.find(query(CatalogSort.DISTANCE, 5_000), context(), null, NOW))
                .extracting(Row::itemId).doesNotContain(fixture.itemId());
    }

    @Test
    void appliesTypeListingCategoryPickupAndPriceFiltersAtTheResolvedLocation() {
        Fixture fixture = fixture();
        UUID serviceId = activeItem(fixture.merchantId(), CatalogItemType.SERVICE, ListingKind.STANDARD,
                fixture.categoryId(), "Service coffee");
        UUID serviceVariant = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), serviceId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.nearLocationId(), serviceVariant, 35_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));
        UUID resaleId = activeItem(fixture.merchantId(), CatalogItemType.PRODUCT, ListingKind.RESALE,
                fixture.categoryId(), "Resale coffee");
        UUID resaleVariant = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), resaleId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.nearLocationId(), resaleVariant, 45_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));
        jdbc.update("update merchant_locations set pickup_enabled = true where id = ?", fixture.nearLocationId());

        assertThat(discovery.find(new CatalogDiscoveryQuery(null, CatalogItemType.SERVICE, null, fixture.categoryId(),
                10.8, 106.7, null, 5_000, true, false, 30_000L, 40_000L, CatalogSort.DISTANCE, 20, null), context(), null, NOW))
                .extracting(Row::itemId).containsExactly(serviceId);
        assertThat(discovery.find(new CatalogDiscoveryQuery(null, CatalogItemType.PRODUCT, ListingKind.RESALE, fixture.categoryId(),
                10.8, 106.7, null, 5_000, false, false, null, null, CatalogSort.DISTANCE, 20, null), context(), null, NOW))
                .extracting(Row::itemId).containsExactly(resaleId);
    }

    @Test
    void matchesAccentedSearchTermsAndHidesPausedItemsVariantsAndOffers() {
        Fixture fixture = fixture("Cà phê");
        searchDocuments.rebuild(fixture.itemId());
        CatalogDiscoveryQuery search = new CatalogDiscoveryQuery("ca phe", null, null, fixture.categoryId(), 10.8, 106.7,
                null, 5_000, false, false, null, null, CatalogSort.RELEVANCE, 20, null);
        assertThat(discovery.find(search, context(), null, NOW)).extracting(Row::itemId).contains(fixture.itemId());

        jdbc.update("update catalog_items set listing_status = 'PAUSED' where id = ?", fixture.itemId());
        assertThat(discovery.find(search, context(), null, NOW)).isEmpty();
        jdbc.update("update catalog_items set listing_status = 'ACTIVE' where id = ?", fixture.itemId());
        jdbc.update("update variants set variant_status = 'PAUSED' where id = ?", fixture.variantId());
        assertThat(discovery.find(search, context(), null, NOW)).isEmpty();
        jdbc.update("update variants set variant_status = 'ACTIVE' where id = ?", fixture.variantId());
        jdbc.update("update location_variants set is_available = false where variant_id = ?", fixture.variantId());
        assertThat(discovery.find(search, context(), null, NOW)).isEmpty();
    }

    @Test
    void keysetsEverySortWithoutRepeatingTiedItemIds() {
        Fixture fixture = fixture("Matrix coffee one");
        UUID second = activeItem(fixture.merchantId(), CatalogItemType.PRODUCT, ListingKind.STANDARD, fixture.categoryId(), "Matrix coffee two");
        UUID third = activeItem(fixture.merchantId(), CatalogItemType.PRODUCT, ListingKind.STANDARD, fixture.categoryId(), "Matrix coffee three");
        for (UUID itemId : List.of(second, third)) {
            UUID variant = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
            offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.nearLocationId(), variant, 25_000,
                    "VND", true, InventoryMode.UNTRACKED, null, NOW));
            searchDocuments.rebuild(itemId);
        }
        searchDocuments.rebuild(fixture.itemId());
        for (CatalogSort sort : CatalogSort.values()) {
            CatalogDiscoveryQuery query = new CatalogDiscoveryQuery(sort == CatalogSort.RELEVANCE ? "coffee" : null,
                    null, null, fixture.categoryId(), 10.8, 106.7, null, 5_000, false, false, null, null, sort, 1, null);
            Row first = discovery.find(query, context(), null, NOW).getFirst();
            CatalogCursor cursor = cursor(sort, first);
            assertThat(discovery.find(query, context(), cursor, NOW)).extracting(Row::itemId)
                    .doesNotContain(first.itemId()).isNotEmpty();
        }
    }

    @Test
    void honorsWeeklyHoursAndAnExceptionDayForOpenNow() {
        Fixture fixture = fixture();
        locationHours.saveAndFlush(MerchantLocationHourEntity.open(UUID.randomUUID(), fixture.nearLocationId(), 4,
                OperationType.SHOP, LocalTime.of(9, 0), LocalTime.of(18, 0), 0));
        CatalogDiscoveryQuery openNow = new CatalogDiscoveryQuery(null, null, null, fixture.categoryId(), 10.8, 106.7,
                null, 5_000, false, true, null, null, CatalogSort.DISTANCE, 20, null);
        assertThat(discovery.find(openNow, context(), null, NOW)).extracting(Row::itemId).contains(fixture.itemId());

        exceptions.saveAndFlush(MerchantLocationHourExceptionEntity.closed(UUID.randomUUID(), fixture.nearLocationId(),
                LocalDate.of(2026, 1, 1), OperationType.SHOP, "Holiday"));
        assertThat(discovery.find(openNow, context(), null, NOW)).isEmpty();
        exceptions.deleteAll();
        exceptions.saveAndFlush(MerchantLocationHourExceptionEntity.open(UUID.randomUUID(), fixture.nearLocationId(),
                LocalDate.of(2026, 1, 1), OperationType.SHOP, LocalTime.of(16, 0), LocalTime.of(18, 0), "Late hours", 0));
        assertThat(discovery.find(openNow, context(), null, NOW)).extracting(Row::itemId).contains(fixture.itemId());
    }

    private Fixture fixture() {
        return fixture("Coffee");
    }

    private Fixture fixture(String itemName) {
        UUID merchantId = merchants.saveAndFlush(MerchantProfileEntity.create(UUID.randomUUID(), "Near merchant",
                "near-" + UUID.randomUUID(), NOW)).getId();
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Near", "Near", 10.8, 106.7, NOW)).getId();
        UUID categoryId = category();
        UUID itemId = activeItem(merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, itemName);
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));
        return new Fixture(merchantId, itemId, variantId, locationId, categoryId);
    }

    private UUID activeItem(UUID merchantId, String name) {
        return activeItem(merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD, category(), name);
    }

    private UUID activeItem(UUID merchantId, CatalogItemType type, ListingKind listingKind, UUID categoryId, String name) {
        CatalogItemEntity item = CatalogItemEntity.create(UUID.randomUUID(), merchantId, type,
                listingKind, categoryId, name, "item-" + UUID.randomUUID(), null, NOW);
        item.publish(NOW);
        return items.saveAndFlush(item).getId();
    }

    private UUID category() {
        return categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(),
                CategoryCatalogType.BOTH, CategoryStatus.ACTIVE, 0)).getId();
    }

    private static CatalogDiscoveryQuery query(CatalogSort sort, int radius) {
        return new CatalogDiscoveryQuery(null, null, null, null, 10.8, 106.7, null, radius,
                false, false, null, null, sort, 20, null);
    }

    private static CatalogDiscoveryQuery query(CatalogSort sort, int radius, UUID categoryId) {
        return new CatalogDiscoveryQuery(null, null, null, categoryId, 10.8, 106.7, null, radius,
                false, false, null, null, sort, 20, null);
    }

    private static LocationContext context() {
        return new LocationContext(LocationResolutionSource.COORDINATES, new GeoPointFactory().point(10.8, 106.7),
                null, null, ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private static CatalogCursor cursor(CatalogSort sort, Row row) {
        return switch (sort) {
            case RELEVANCE -> new CatalogCursor(sort, row.rank(), row.distanceMeters(), null, null, row.itemId());
            case DISTANCE -> new CatalogCursor(sort, null, row.distanceMeters(), null, null, row.itemId());
            case PRICE_ASC, PRICE_DESC -> new CatalogCursor(sort, null, null, row.minPrice(), null, row.itemId());
            case NEWEST -> new CatalogCursor(sort, null, null, null, row.createdAt(), row.itemId());
        };
    }

    private record Fixture(UUID merchantId, UUID itemId, UUID variantId, UUID nearLocationId, UUID categoryId) {}
}
