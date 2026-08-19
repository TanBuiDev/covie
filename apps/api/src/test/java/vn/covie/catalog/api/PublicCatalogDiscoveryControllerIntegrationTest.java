package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserAddressRepository;
import vn.covie.users.persistence.UserRepository;

class PublicCatalogDiscoveryControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired WebApplicationContext web;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired UserRepository users;
    @Autowired UserAddressRepository addresses;
    @Autowired ObjectMapper objectMapper;
    @Autowired InitialSessionIssuer sessions;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void requiresAnExplicitLocationContextForPublicDiscovery() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/items"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LOCATION_CONTEXT_REQUIRED"));
    }

    @Test
    void rejectsUnknownAndInvalidCatalogFiltersWith422() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/items").param("lat", "10.8").param("lng", "106.7")
                        .param("type", "UNKNOWN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CATALOG_FILTER_INVALID"));
        mockMvc.perform(get("/api/v1/catalog/items").param("lat", "10.8").param("lng", "106.7")
                        .param("sort", "RELEVANCE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CATALOG_FILTER_INVALID"));
    }

    @Test
    void rejectsMalformedTypedDiscoveryParametersWithCatalogFilterError() throws Exception {
        for (String[] malformed : new String[][] {
                {"categoryId", "not-a-uuid"}, {"radiusMeters", "far"}, {"minPrice", "free"},
                {"limit", "many"}, {"pickup", "perhaps"}, {"openNow", "sometimes"}}) {
            mockMvc.perform(get("/api/v1/catalog/items").param("lat", "10.8").param("lng", "106.7")
                            .param(malformed[0], malformed[1]))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("CATALOG_FILTER_INVALID"));
        }
    }

    @Test
    void returnsThePublicSummaryItemAndPagesWithAnOpaqueCursor() throws Exception {
        Fixture fixture = fixture();
        UUID nextItem = addItem(fixture.merchantId(), fixture.categoryId(), fixture.locationId(), "Second coffee", 30_000);

        MvcResult first = mockMvc.perform(get("/api/v1/catalog/items").param("lat", "10.8").param("lng", "106.7")
                        .param("categoryId", fixture.categoryId().toString()).param("limit", "1").param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items[0].id").value(fixture.itemId().toString()))
                .andExpect(jsonPath("$.page.nextCursor").isNotEmpty())
                .andReturn();
        String cursor = objectMapper.readTree(first.getResponse().getContentAsString()).at("/page/nextCursor").asText();

        MvcResult second = mockMvc.perform(get("/api/v1/catalog/items").param("lat", "10.8").param("lng", "106.7")
                        .param("categoryId", fixture.categoryId().toString()).param("limit", "1").param("sort", "PRICE_ASC")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondIds = objectMapper.readTree(second.getResponse().getContentAsString()).at("/page/items");
        assertThat(secondIds.get(0).path("id").asText()).isEqualTo(nextItem.toString())
                .isNotEqualTo(fixture.itemId().toString());
    }

    @Test
    void resolvesAnOwnedSavedAddressAndRejectsAnAddressOwnedByAnotherUser() throws Exception {
        Fixture fixture = fixture();
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        users.saveAndFlush(UserEntity.active(strangerId, "Stranger", NOW));
        UUID ownedAddress = addresses.saveAndFlush(UserAddressEntity.create(UUID.randomUUID(), ownerId, "Home", "Owner",
                "0900000000", "1 CoVie", 10.8, 106.7, PinSource.GPS, null, true, NOW)).getId();
        UUID foreignAddress = addresses.saveAndFlush(UserAddressEntity.create(UUID.randomUUID(), strangerId, "Elsewhere", "Stranger",
                "0900000001", "2 CoVie", 10.8, 106.7, PinSource.GPS, null, true, NOW)).getId();

        String ownerBearer = "Bearer " + sessions.issue(ownerId, new DeviceMetadata("Test", "Test")).accessToken().value();
        mockMvc.perform(get("/api/v1/catalog/items").param("addressId", ownedAddress.toString())
                        .param("categoryId", fixture.categoryId().toString()).header("Authorization", ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items[0].id").value(fixture.itemId().toString()));
        mockMvc.perform(get("/api/v1/catalog/items").param("addressId", foreignAddress.toString())
                        .param("categoryId", fixture.categoryId().toString()).header("Authorization", ownerBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private Fixture fixture() {
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food",
                "food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = addItem(merchantId, categoryId, locationId, "Coffee");
        return new Fixture(merchantId, itemId, locationId, categoryId);
    }

    private UUID addItem(UUID merchantId, UUID categoryId, UUID locationId, String name) {
        return addItem(merchantId, categoryId, locationId, name, 25_000);
    }

    private UUID addItem(UUID merchantId, UUID categoryId, UUID locationId, String name, long price) {
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, name, "item-" + itemId, null, NOW);
        item.publish(NOW);
        items.saveAndFlush(item);
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId,
                price, "VND", true, InventoryMode.UNTRACKED, null, NOW));
        return itemId;
    }

    private record Fixture(UUID merchantId, UUID itemId, UUID locationId, UUID categoryId) {}
}
