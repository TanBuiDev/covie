package vn.covie.catalog.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemMediaEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class PublicMerchantCatalogControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired WebApplicationContext web;
    @Autowired ObjectMapper json;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired CatalogItemMediaRepository itemMedia;
    @Autowired MediaAssetRepository mediaAssets;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void anonymouslyReturnsOnlyOwnedPublicItemsWithLowestPriceAndReadyPrimaryMedia() throws Exception {
        MerchantFixture fixture = merchantFixture();
        ItemFixture product = visibleItem(fixture, CatalogItemType.PRODUCT, ListingKind.STANDARD, "Coffee", NOW, 35000);
        secondOffer(fixture, product, 25000);
        String readyMediaUrl = readyPrimaryMedia(product.itemId());
        ItemFixture service = visibleItem(fixture, CatalogItemType.SERVICE, ListingKind.STANDARD, "Repair",
                NOW.plusSeconds(1), 90000);
        pendingPrimaryMedia(service.itemId());
        MerchantFixture other = merchantFixture();
        visibleItem(other, CatalogItemType.PRODUCT, ListingKind.STANDARD, "Foreign", NOW.plusSeconds(2), 10000);

        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", fixture.merchantId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items.length()").value(2))
                .andExpect(jsonPath("$.page.items[0].name").value("Repair"))
                .andExpect(jsonPath("$.page.items[0].primaryMedia").doesNotExist())
                .andExpect(jsonPath("$.page.items[1].id").value(product.itemId().toString()))
                .andExpect(jsonPath("$.page.items[1].priceFrom.amount").value(25000))
                .andExpect(jsonPath("$.page.items[1].priceFrom.currency").value("VND"))
                .andExpect(jsonPath("$.page.items[1].primaryMedia.url").value(readyMediaUrl))
                .andExpect(jsonPath("$.page.items[1].primaryMedia.storageKey").doesNotExist())
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
    }

    @Test
    void paginatesNewestFirstWithoutDuplicatesAndScopesOpaqueCursorToMerchant() throws Exception {
        MerchantFixture fixture = merchantFixture();
        ItemFixture oldest = visibleItem(fixture, CatalogItemType.PRODUCT, ListingKind.STANDARD, "Old", NOW, 10000);
        ItemFixture middle = visibleItem(fixture, CatalogItemType.PRODUCT, ListingKind.STANDARD, "Middle", NOW.plusSeconds(1), 20000);
        ItemFixture newest = visibleItem(fixture, CatalogItemType.PRODUCT, ListingKind.STANDARD, "New", NOW.plusSeconds(2), 30000);

        MvcResult firstResult = mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", fixture.merchantId())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items[0].id").value(newest.itemId().toString()))
                .andExpect(jsonPath("$.page.items[1].id").value(middle.itemId().toString()))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.nextCursor").isString())
                .andReturn();
        JsonNode first = json.readTree(firstResult.getResponse().getContentAsByteArray());
        String cursor = first.at("/page/nextCursor").asText();

        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", fixture.merchantId())
                        .param("limit", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items.length()").value(1))
                .andExpect(jsonPath("$.page.items[0].id").value(oldest.itemId().toString()))
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andExpect(jsonPath("$.page.nextCursor").doesNotExist());

        MerchantFixture other = merchantFixture();
        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", other.merchantId())
                        .param("limit", "2").param("cursor", cursor))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CATALOG_FILTER_INVALID"));
    }

    @Test
    void breaksEqualCreationTimestampsByItemIdWithoutSkippingItems() throws Exception {
        MerchantFixture fixture = merchantFixture();
        ItemFixture first = visibleItem(fixture, CatalogItemType.PRODUCT, ListingKind.STANDARD, "Tie A", NOW, 10000);
        ItemFixture second = visibleItem(fixture, CatalogItemType.PRODUCT, ListingKind.STANDARD, "Tie B", NOW, 20000);
        List<UUID> expected = List.of(first.itemId(), second.itemId()).stream()
                .sorted(Comparator.comparing(UUID::toString).reversed())
                .toList();

        MvcResult firstPage = mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", fixture.merchantId())
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items[0].id").value(expected.get(0).toString()))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andReturn();
        String cursor = json.readTree(firstPage.getResponse().getContentAsByteArray())
                .at("/page/nextCursor").asText();

        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", fixture.merchantId())
                        .param("limit", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items.length()").value(1))
                .andExpect(jsonPath("$.page.items[0].id").value(expected.get(1).toString()))
                .andExpect(jsonPath("$.page.hasMore").value(false));
    }

    @Test
    void rejectsInvalidLimitsAndMalformedCursor() throws Exception {
        MerchantFixture fixture = merchantFixture();

        for (String limit : new String[] {"0", "51"}) {
            mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", fixture.merchantId()).param("limit", limit))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("CATALOG_FILTER_INVALID"));
        }
        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", fixture.merchantId())
                        .param("cursor", "not-a-cursor"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CATALOG_FILTER_INVALID"));
    }

    @Test
    void filtersNonPublicCatalogLayersAndReturnsEmptyPageForVisibleMerchant() throws Exception {
        MerchantFixture fixture = merchantFixture();
        ItemFixture item = visibleItem(fixture, CatalogItemType.PRODUCT, ListingKind.STANDARD, "Hidden", NOW, 10000);

        jdbc.update("update catalog_items set moderation_status = 'HIDDEN' where id = ?", item.itemId());
        assertEmpty(fixture.merchantId());
        jdbc.update("update catalog_items set moderation_status = 'APPROVED' where id = ?", item.itemId());
        jdbc.update("update variants set variant_status = 'PAUSED' where id = ?", item.variantId());
        assertEmpty(fixture.merchantId());
        jdbc.update("update variants set variant_status = 'ACTIVE' where id = ?", item.variantId());
        jdbc.update("update location_variants set is_available = false where id = ?", item.offerId());
        assertEmpty(fixture.merchantId());
        jdbc.update("update location_variants set is_available = true where id = ?", item.offerId());
        jdbc.update("update merchant_locations set location_status = 'PAUSED' where id = ?", fixture.locationId());
        assertEmpty(fixture.merchantId());
        jdbc.update("update merchant_locations set location_status = 'ACTIVE' where id = ?", fixture.locationId());

        for (String listingStatus : new String[] {"DRAFT", "PAUSED", "DELETED"}) {
            jdbc.update("update catalog_items set listing_status = ? where id = ?", listingStatus, item.itemId());
            assertEmpty(fixture.merchantId());
            jdbc.update("update catalog_items set listing_status = 'ACTIVE' where id = ?", item.itemId());
        }
        for (String moderationStatus : new String[] {"PENDING", "REJECTED", "HIDDEN"}) {
            jdbc.update("update catalog_items set moderation_status = ? where id = ?", moderationStatus, item.itemId());
            assertEmpty(fixture.merchantId());
            jdbc.update("update catalog_items set moderation_status = 'APPROVED' where id = ?", item.itemId());
        }

        jdbc.update("update catalog_items set deleted_at = ? where id = ?", Timestamp.from(NOW), item.itemId());
        assertEmpty(fixture.merchantId());
        jdbc.update("update catalog_items set deleted_at = null where id = ?", item.itemId());
        jdbc.update("update variants set deleted_at = ? where id = ?", Timestamp.from(NOW), item.variantId());
        assertEmpty(fixture.merchantId());
        jdbc.update("update variants set deleted_at = null where id = ?", item.variantId());
        jdbc.update("update location_variants set deleted_at = ? where id = ?", Timestamp.from(NOW), item.offerId());
        assertEmpty(fixture.merchantId());
        jdbc.update("update location_variants set deleted_at = null where id = ?", item.offerId());
        jdbc.update("update merchant_locations set deleted_at = ? where id = ?", Timestamp.from(NOW), fixture.locationId());
        assertEmpty(fixture.merchantId());
        jdbc.update("update merchant_locations set deleted_at = null where id = ?", fixture.locationId());

        MerchantFixture other = merchantFixture();
        jdbc.update("update location_variants set merchant_location_id = ? where id = ?", other.locationId(), item.offerId());
        assertEmpty(fixture.merchantId());
    }

    @Test
    void hidesMissingOrNonPublicMerchantWithUniformNotFound() throws Exception {
        MerchantFixture fixture = merchantFixture();
        for (String status : new String[] {"PAUSED", "SUSPENDED", "DELETED"}) {
            jdbc.update("update merchant_profiles set merchant_status = ? where id = ?", status, fixture.merchantId());
            assertNotFound(fixture.merchantId(), false);
            jdbc.update("update merchant_profiles set merchant_status = 'ACTIVE' where id = ?", fixture.merchantId());
        }
        for (String moderation : new String[] {"PENDING", "REJECTED", "HIDDEN"}) {
            jdbc.update("update merchant_profiles set moderation_status = ? where id = ?", moderation, fixture.merchantId());
            assertNotFound(fixture.merchantId(), false);
            jdbc.update("update merchant_profiles set moderation_status = 'APPROVED' where id = ?", fixture.merchantId());
        }
        jdbc.update("update merchant_profiles set deleted_at = ? where id = ?", Timestamp.from(NOW), fixture.merchantId());
        assertNotFound(fixture.merchantId(), true);
        jdbc.update("update merchant_profiles set deleted_at = null where id = ?", fixture.merchantId());

        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private MerchantFixture merchantFixture() {
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        return new MerchantFixture(merchantId, locationId);
    }

    private ItemFixture visibleItem(MerchantFixture fixture, CatalogItemType type, ListingKind listingKind,
            String name, Instant createdAt, long price) {
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, name,
                "category-" + UUID.randomUUID(), type == CatalogItemType.PRODUCT
                        ? CategoryCatalogType.PRODUCT : CategoryCatalogType.SERVICE, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, fixture.merchantId(), type, listingKind, categoryId,
                name, "item-" + itemId, null, createdAt);
        item.publish(createdAt);
        items.saveAndFlush(item);
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", createdAt)).getId();
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(), variantId,
                price, "VND", true, type == CatalogItemType.PRODUCT ? InventoryMode.UNTRACKED : null, null, createdAt)).getId();
        return new ItemFixture(itemId, variantId, offerId);
    }

    private void secondOffer(MerchantFixture fixture, ItemFixture item, long price) {
        UUID secondLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Second", "2 CoVie", 10.81, 106.71, NOW.plusSeconds(1))).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), secondLocation, item.variantId(), price,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));
    }

    private String readyPrimaryMedia(UUID itemId) {
        UUID ownerId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        String storageKey = "catalog/ready-" + UUID.randomUUID() + ".png";
        MediaAssetEntity ready = MediaAssetEntity.pending(UUID.randomUUID(), ownerId, storageKey,
                MediaPurpose.CATALOG_IMAGE, "image/png", 10, NOW.plusSeconds(300), NOW);
        ready.markReady(NOW);
        MediaAssetEntity readyAsset = mediaAssets.saveAndFlush(ready);
        MediaAssetEntity pending = mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(), ownerId,
                "catalog/pending-" + UUID.randomUUID() + ".png", MediaPurpose.CATALOG_IMAGE, "image/png", 10,
                NOW.plusSeconds(300), NOW));
        CatalogItemEntity item = items.findById(itemId).orElseThrow();
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(item, readyAsset, 0, true));
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(item, pending, 1, false));
        return "https://media.test.covie/" + storageKey;
    }

    private void pendingPrimaryMedia(UUID itemId) {
        UUID ownerId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        MediaAssetEntity pending = mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(), ownerId,
                "catalog/pending-primary-" + UUID.randomUUID() + ".png", MediaPurpose.CATALOG_IMAGE, "image/png", 10,
                NOW.plusSeconds(300), NOW));
        CatalogItemEntity item = items.findById(itemId).orElseThrow();
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(item, pending, 0, true));
    }

    private void assertEmpty(UUID merchantId) throws Exception {
        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items").isEmpty())
                .andExpect(jsonPath("$.page.hasMore").value(false));
    }


    private void assertNotFound(UUID merchantId, boolean authenticated) throws Exception {
        var request = get("/api/v1/catalog/merchants/{merchantId}/items", merchantId);
        if (authenticated) {
            request.with(oauth2Login());
        }
        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private record MerchantFixture(UUID merchantId, UUID locationId) {}
    private record ItemFixture(UUID itemId, UUID variantId, UUID offerId) {}
}
