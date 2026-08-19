package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.covie.catalog.api.dto.publicview.CatalogItemDetailResponse;
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
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.location.application.LocationResolutionSource;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserAddressRepository;
import vn.covie.users.persistence.UserRepository;

class CatalogDetailServiceIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired CatalogDetailService details;
    @Autowired UserRepository users;
    @Autowired UserAddressRepository addresses;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired CatalogItemMediaRepository itemMedia;
    @Autowired MediaAssetRepository mediaAssets;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesOnlyTheRequestedActiveMerchantLocationAndUsesItsAuthoritativeOffer() {
        Fixture fixture = publicFixture();
        UUID secondLocation = locations.saveAndFlush(MerchantLocationEntity.create(
                UUID.randomUUID(), fixture.merchantId(), "Second", "2 CoVie", 10.801, 106.701, NOW.plusSeconds(1))).getId();
        UUID secondOfferId = offers.saveAndFlush(LocationVariantEntity.create(
                UUID.randomUUID(), secondLocation, fixture.variantId(), 39000, "VND", true,
                InventoryMode.UNTRACKED, 15, NOW)).getId();

        CatalogItemDetailResponse result = details.get(fixture.itemId(), secondLocation, null, null, null);

        assertThat(result.resolvedLocation().id()).isEqualTo(secondLocation);
        assertThat(result.resolvedLocation().resolutionSource())
                .isEqualTo(LocationResolutionSource.EXPLICIT_MERCHANT_LOCATION);
        assertThat(result.resolvedLocation().distanceMeters()).isNull();
        assertThat(result.variants()).singleElement().satisfies(variant -> {
            assertThat(variant.offer()).isNotNull();
            assertThat(variant.offer().locationVariantId()).isEqualTo(secondOfferId);
            assertThat(variant.offer().price().amount()).isEqualTo(39000);
        });

        UUID foreignMerchant = merchants.saveAndFlush(MerchantProfileEntity.create(
                UUID.randomUUID(), "Foreign", "foreign-" + UUID.randomUUID(), NOW)).getId();
        UUID foreignLocation = locations.saveAndFlush(MerchantLocationEntity.create(
                UUID.randomUUID(), foreignMerchant, "Foreign", "3 CoVie", 10.8, 106.7, NOW)).getId();
        assertNotFound(() -> details.get(fixture.itemId(), foreignLocation, null, null, null));

        jdbc.update("update merchant_locations set location_status = 'PAUSED' where id = ?", secondLocation);
        entityManager.clear();
        assertNotFound(() -> details.get(fixture.itemId(), secondLocation, null, null, null));
    }

    @Test
    void resolvesOwnedAddressAndCoordinatesToNearestVisibleOfferAndUsesDeterministicFallbackWithoutContext() {
        Fixture fixture = publicFixture();
        UUID nearerLocation = locations.saveAndFlush(MerchantLocationEntity.create(
                UUID.randomUUID(), fixture.merchantId(), "Near", "2 CoVie", 10.8002, 106.7002, NOW.plusSeconds(1))).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), nearerLocation, fixture.variantId(), 26000,
                "VND", true, InventoryMode.UNTRACKED, null, NOW));
        UUID buyerId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(buyerId, "Buyer", NOW));
        UUID addressId = addresses.saveAndFlush(UserAddressEntity.create(UUID.randomUUID(), buyerId, "Home", "Buyer",
                "0900000000", "Home", 10.8001, 106.7001, PinSource.GPS, null, true, NOW)).getId();
        authenticate(buyerId);

        CatalogItemDetailResponse fromAddress = details.get(fixture.itemId(), null, addressId, null, null);
        assertThat(fromAddress.resolvedLocation().id()).isEqualTo(nearerLocation);
        assertThat(fromAddress.resolvedLocation().resolutionSource()).isEqualTo(LocationResolutionSource.SAVED_ADDRESS);
        assertThat(fromAddress.resolvedLocation().distanceMeters()).isPositive();

        SecurityContextHolder.clearContext();
        CatalogItemDetailResponse fromCoordinates = details.get(fixture.itemId(), null, null, 10.8001, 106.7001);
        assertThat(fromCoordinates.resolvedLocation().id()).isEqualTo(nearerLocation);
        assertThat(fromCoordinates.resolvedLocation().resolutionSource()).isEqualTo(LocationResolutionSource.COORDINATES);

        CatalogItemDetailResponse fallback = details.get(fixture.itemId(), null, null, null, null);
        assertThat(fallback.resolvedLocation().id()).isEqualTo(fixture.locationId());
        assertThat(fallback.resolvedLocation().resolutionSource())
                .isEqualTo(LocationResolutionSource.DEFAULT_MERCHANT_LOCATION);
        assertThat(fallback.resolvedLocation().distanceMeters()).isNull();
    }

    @Test
    void returnsNotFoundForNonPublicCatalogOrUnbuyableExplicitLocation() {
        Fixture fixture = publicFixture();
        jdbc.update("update catalog_items set listing_status = 'DRAFT' where id = ?", fixture.itemId());
        entityManager.clear();
        assertNotFound(() -> details.get(fixture.itemId(), null, null, null, null));

        jdbc.update("update catalog_items set listing_status = 'ACTIVE' where id = ?", fixture.itemId());
        jdbc.update("update location_variants set is_available = false where id = ?", fixture.offerId());
        entityManager.clear();
        assertNotFound(() -> details.get(fixture.itemId(), fixture.locationId(), null, null, null));
    }

    @Test
    void exposesOnlyReadyCatalogMediaUsingTheConfiguredPublicUrl() {
        Fixture fixture = publicFixture();
        UUID ownerId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        MediaAssetEntity ready = MediaAssetEntity.pending(UUID.randomUUID(), ownerId, "catalog/ready.png",
                MediaPurpose.CATALOG_IMAGE, "image/png", 10, NOW.plusSeconds(300), NOW);
        ready.markReady(NOW);
        MediaAssetEntity readyAsset = mediaAssets.saveAndFlush(ready);
        MediaAssetEntity pending = mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(), ownerId,
                "catalog/pending.png", MediaPurpose.CATALOG_IMAGE, "image/png", 10, NOW.plusSeconds(300), NOW));
        CatalogItemEntity item = items.findById(fixture.itemId()).orElseThrow();
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(item, readyAsset, 0, true));
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(item, pending, 1, false));
        entityManager.clear();

        CatalogItemDetailResponse response = details.get(fixture.itemId(), null, null, null, null);

        assertThat(response.media()).singleElement().satisfies(media -> {
            assertThat(media.id()).isEqualTo(readyAsset.getId());
            assertThat(media.url().toString()).isEqualTo("https://media.test.covie/catalog/ready.png");
        });
    }

    private Fixture publicFixture() {
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(
                UUID.randomUUID(), merchantId, "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food",
                "food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + itemId, "Fresh", NOW);
        item.publish(NOW);
        items.saveAndFlush(item);
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25000,
                "VND", true, InventoryMode.UNTRACKED, 10, NOW)).getId();
        entityManager.clear();
        return new Fixture(merchantId, itemId, locationId, variantId, offerId);
    }

    private static void authenticate(UUID userId) {
        Jwt jwt = new Jwt("test", NOW, NOW.plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", userId.toString(), "sessionId", UUID.randomUUID().toString(), "roles", List.of("USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static void assertNotFound(ThrowingCommand command) {
        assertThatThrownBy(command::run).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private record Fixture(UUID merchantId, UUID itemId, UUID locationId, UUID variantId, UUID offerId) {}

    @FunctionalInterface
    private interface ThrowingCommand { void run(); }
}
