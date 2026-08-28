package vn.covie.catalog.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
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
import vn.covie.merchants.model.ContactType;
import vn.covie.merchants.model.MerchantContactMethodEntity;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantContactMethodRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class PublicCatalogDetailControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired WebApplicationContext web;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired MerchantContactMethodRepository contacts;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired UserRepository users;
    @Autowired MediaAssetRepository mediaAssets;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void exposesPublicDetailWithoutAuthenticationAndReturnsConcreteOfferIdentity() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(get("/api/v1/catalog/items/{itemId}", fixture.itemId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedLocation.id").value(fixture.locationId().toString()))
                .andExpect(jsonPath("$.resolvedLocation.resolutionSource").value("DEFAULT_MERCHANT_LOCATION"))
                .andExpect(jsonPath("$.variants[0].offer.locationVariantId").value(fixture.offerId().toString()))
                .andExpect(jsonPath("$.variants[0].offer.price.amount").value(25000));
    }

    @Test
    void returnsOnlyPublicActiveContactsAndActiveLocationsOnPublicMerchantProfile() throws Exception {
        Fixture fixture = fixture();
        UUID pausedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Paused", "Hidden address", 10.81, 106.71, NOW)).getId();
        jdbc.update("update merchant_locations set location_status = 'PAUSED' where id = ?", pausedLocation);
        contacts.saveAndFlush(MerchantContactMethodEntity.create(UUID.randomUUID(), fixture.merchantId(), fixture.locationId(),
                ContactType.PHONE, "0900000000", null, true, true, NOW));
        contacts.saveAndFlush(MerchantContactMethodEntity.create(UUID.randomUUID(), fixture.merchantId(), fixture.locationId(),
                ContactType.OTHER, "private@example.test", null, false, false, NOW));

        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}", fixture.merchantId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantKind").value("SHOP"))
                .andExpect(jsonPath("$.locations.length()").value(1))
                .andExpect(jsonPath("$.locations[0].id").value(fixture.locationId().toString()))
                .andExpect(jsonPath("$.locations[0].pickupEnabled").value(false))
                .andExpect(jsonPath("$.locations[0].sellerDeliveryEnabled").value(false))
                .andExpect(jsonPath("$.locations[0].serviceEnabled").value(false))
                .andExpect(jsonPath("$.locations[0].openNow").value(false))
                .andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.contacts[0].value").value("0900000000"));
    }

    @Test
    void filtersThePublicCatalogByMerchantAndExposesAvailability() throws Exception {
        Fixture selected = fixture();
        fixture();

        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}/items", selected.merchantId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(selected.itemId().toString()))
                .andExpect(jsonPath("$.items[0].merchant.id").value(selected.merchantId().toString()))
                .andExpect(jsonPath("$.items[0].availability").value("AVAILABLE"));
    }

    @Test
    void returnsNotFoundWhenCatalogIsNotPublic() throws Exception {
        Fixture fixture = fixture();
        jdbc.update("update merchant_profiles set merchant_status = 'PAUSED' where id = ?", fixture.merchantId());
        mockMvc.perform(get("/api/v1/catalog/items/{itemId}", fixture.itemId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void exposesReadyPublicZaloQrMediaButFiltersAnUnreadyQrContact() throws Exception {
        Fixture fixture = fixture();
        UUID ownerId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        MediaAssetEntity ready = MediaAssetEntity.pending(UUID.randomUUID(), ownerId, "merchant/ready-qr.png",
                MediaPurpose.MERCHANT_CONTACT_QR, "image/png", 10, NOW.plusSeconds(300), NOW);
        ready.markReady(NOW);
        MediaAssetEntity readyQr = mediaAssets.saveAndFlush(ready);
        MediaAssetEntity pendingQr = mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(), ownerId,
                "merchant/pending-qr.png", MediaPurpose.MERCHANT_CONTACT_QR, "image/png", 10,
                NOW.plusSeconds(300), NOW));
        contacts.saveAndFlush(MerchantContactMethodEntity.create(UUID.randomUUID(), fixture.merchantId(), fixture.locationId(),
                ContactType.ZALO_QR, null, readyQr.getId(), true, true, NOW));
        contacts.saveAndFlush(MerchantContactMethodEntity.create(UUID.randomUUID(), fixture.merchantId(), fixture.locationId(),
                ContactType.ZALO_QR, null, pendingQr.getId(), false, true, NOW.plusSeconds(1)));

        mockMvc.perform(get("/api/v1/catalog/merchants/{merchantId}", fixture.merchantId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.contacts[0].type").value("ZALO_QR"))
                .andExpect(jsonPath("$.contacts[0].value").doesNotExist())
                .andExpect(jsonPath("$.contacts[0].qrMedia.id").value(readyQr.getId().toString()))
                .andExpect(jsonPath("$.contacts[0].qrMedia.url").value("https://media.test.covie/merchant/ready-qr.png"))
                .andExpect(jsonPath("$.contacts[0].qrMedia.storageKey").doesNotExist());
    }

    private Fixture fixture() {
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food",
                "food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + itemId, null, NOW);
        item.publish(NOW);
        items.saveAndFlush(item);
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId,
                25000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();
        return new Fixture(merchantId, itemId, locationId, offerId);
    }

    private record Fixture(UUID merchantId, UUID itemId, UUID locationId, UUID offerId) {}
}
