package vn.covie.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
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
import vn.covie.merchants.model.MerchantProfileEntity;
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

    @Test
    void resolvesTheNearestConcreteLocationBeforePriceSorting() {
        Fixture fixture = fixture();
        UUID fartherLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Far but cheap", "Far", 10.807, 106.707, NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fartherLocation, fixture.variantId(), 1_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));

        Row result = discovery.find(query(CatalogSort.PRICE_ASC, 5_000), context(), null, NOW).getFirst();

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
        UUID secondItem = activeItem(fixture.merchantId(), "Coffee beans");
        UUID secondVariant = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), secondItem, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.nearLocationId(), secondVariant, 30_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));
        searchDocuments.rebuild(fixture.itemId());
        searchDocuments.rebuild(secondItem);
        CatalogDiscoveryQuery query = new CatalogDiscoveryQuery("coffee", null, null, null, 10.8, 106.7, null, 5_000,
                false, false, null, null, CatalogSort.RELEVANCE, 1, null);

        Row first = discovery.find(query, context(), null, NOW).getFirst();
        CatalogCursor cursor = new CatalogCursor(CatalogSort.RELEVANCE, first.rank(), first.distanceMeters(), null, null, first.itemId());

        assertThat(discovery.find(query, context(), cursor, NOW)).extracting(Row::itemId)
                .singleElement()
                .isNotEqualTo(first.itemId())
                .isIn(fixture.itemId(), secondItem);
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

    private Fixture fixture() {
        UUID merchantId = merchants.saveAndFlush(MerchantProfileEntity.create(UUID.randomUUID(), "Near merchant",
                "near-" + UUID.randomUUID(), NOW)).getId();
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Near", "Near", 10.8, 106.7, NOW)).getId();
        UUID itemId = activeItem(merchantId, "Coffee");
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25_000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));
        return new Fixture(merchantId, itemId, variantId, locationId);
    }

    private UUID activeItem(UUID merchantId, String name) {
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(),
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        CatalogItemEntity item = CatalogItemEntity.create(UUID.randomUUID(), merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, name, "item-" + UUID.randomUUID(), null, NOW);
        item.publish(NOW);
        return items.saveAndFlush(item).getId();
    }

    private static CatalogDiscoveryQuery query(CatalogSort sort, int radius) {
        return new CatalogDiscoveryQuery(null, null, null, null, 10.8, 106.7, null, radius,
                false, false, null, null, sort, 20, null);
    }

    private static LocationContext context() {
        return new LocationContext(LocationResolutionSource.COORDINATES, new GeoPointFactory().point(10.8, 106.7),
                null, null, ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private record Fixture(UUID merchantId, UUID itemId, UUID variantId, UUID nearLocationId) {}
}
