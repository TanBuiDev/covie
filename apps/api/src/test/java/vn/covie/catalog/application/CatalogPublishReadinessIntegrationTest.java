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
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemMediaEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantStatus;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.outbox.OutboxEventRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class CatalogPublishReadinessIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired CatalogLifecycleService lifecycle; @Autowired EntityManager entityManager; @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users; @Autowired MerchantProfileRepository merchants; @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations; @Autowired CategoryRepository categories; @Autowired CategoryAttributeRepository categoryAttributes;
    @Autowired CatalogItemRepository items; @Autowired VariantRepository variants; @Autowired LocationVariantRepository offers;
    @Autowired MediaAssetRepository mediaAssets; @Autowired CatalogItemMediaRepository itemMedia; @Autowired OutboxEventRepository outbox;

    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }

    @Test
    void rejectsEveryReadinessFailureWithoutPartiallyPublishing() {
        for (ReadinessFailure failure : ReadinessFailure.values()) {
            Fixture fixture = fixture(failure); authenticate(fixture.ownerId());
            long eventsBefore = outbox.count(); breakInvariant(failure, fixture);

            assertRejected(failure, () -> lifecycle.publish(fixture.merchantId(), fixture.itemId(), "\"v0\""));
            assertUnchanged(fixture.itemId(), ListingStatus.DRAFT, 0, eventsBefore);
        }
    }

    @Test
    void rechecksEveryReadinessFailureBeforeReactivating() {
        for (ReadinessFailure failure : ReadinessFailure.values()) {
            Fixture fixture = fixture(failure); authenticate(fixture.ownerId());
            lifecycle.publish(fixture.merchantId(), fixture.itemId(), "\"v0\"");
            lifecycle.pause(fixture.merchantId(), fixture.itemId(), "\"v1\"");
            long eventsBefore = outbox.count(); breakInvariant(failure, fixture);

            assertRejected(failure, () -> lifecycle.activate(fixture.merchantId(), fixture.itemId(), "\"v2\""));
            assertUnchanged(fixture.itemId(), ListingStatus.PAUSED, 2, eventsBefore);
        }
    }

    private void assertRejected(ReadinessFailure failure, ThrowingCommand command) {
        assertThatThrownBy(command::run).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo(failure.errorCode());
    }

    private void assertUnchanged(UUID itemId, ListingStatus expectedStatus, long expectedVersion, long expectedOutboxCount) {
        entityManager.clear();
        CatalogItemEntity item = entityManager.find(CatalogItemEntity.class, itemId);
        assertThat(item.getListingStatus()).isEqualTo(expectedStatus);
        assertThat(item.getVersion()).isEqualTo(expectedVersion);
        assertThat(item.getDeletedAt()).isNull();
        assertThat(outbox.count()).isEqualTo(expectedOutboxCount);
    }

    private Fixture fixture(ReadinessFailure failure) {
        boolean service = failure == ReadinessFailure.SERVICE_LOCATION_DISABLED;
        UUID ownerId = UUID.randomUUID(); UUID merchantId = UUID.randomUUID(); UUID itemId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, ownerId, MerchantRole.OWNER, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        if (service) jdbc.update("update merchant_locations set service_enabled = true where id = ?", locationId);
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Catalog", "catalog-" + UUID.randomUUID(),
                service ? CategoryCatalogType.SERVICE : CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        CatalogItemType itemType = service ? CatalogItemType.SERVICE : CatalogItemType.PRODUCT;
        items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, itemType, ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + itemId, null, NOW));
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25000, "VND", true, service ? null : InventoryMode.UNTRACKED, null, NOW)).getId();
        entityManager.clear();
        return new Fixture(ownerId, merchantId, itemId, categoryId, variantId, offerId, locationId);
    }

    private void breakInvariant(ReadinessFailure failure, Fixture fixture) {
        switch (failure) {
            case MERCHANT_INACTIVE -> jdbc.update("update merchant_profiles set merchant_status = 'PAUSED' where id = ?", fixture.merchantId());
            case CATEGORY_INACTIVE -> jdbc.update("update categories set status = 'INACTIVE' where id = ?", fixture.categoryId());
            case CATEGORY_INCOMPATIBLE -> jdbc.update("update categories set catalog_type = 'SERVICE' where id = ?", fixture.categoryId());
            case REQUIRED_ATTRIBUTE_MISSING -> categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), fixture.categoryId(), "Brand", "brand", CategoryAttributeType.TEXT, true, false, 0));
            case ACTIVE_VARIANT_MISSING -> { VariantEntity variant = variants.findById(fixture.variantId()).orElseThrow(); variant.update(null, variant.getVariantName(), VariantStatus.PAUSED, NOW); variants.saveAndFlush(variant); }
            case AVAILABLE_OFFER_MISSING -> { LocationVariantEntity offer = offers.findById(fixture.offerId()).orElseThrow(); offer.update(offer.getPrice(), false, offer.getInventoryMode(), offer.getPreparationTimeMinutes(), NOW); offers.saveAndFlush(offer); }
            case OFFER_LOCATION_INACTIVE -> jdbc.update("update merchant_locations set location_status = 'PAUSED' where id = ?", fixture.locationId());
            case SERVICE_LOCATION_DISABLED -> jdbc.update("update merchant_locations set service_enabled = false where id = ?", fixture.locationId());
            case MEDIA_NOT_READY -> attachPendingMedia(fixture);
        }
        entityManager.clear();
    }

    private void attachPendingMedia(Fixture fixture) {
        MediaAssetEntity asset = mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(), fixture.ownerId(), "catalog/" + UUID.randomUUID(), MediaPurpose.CATALOG_IMAGE, "image/png", 10, NOW.plusSeconds(600), NOW));
        CatalogItemEntity item = items.findById(fixture.itemId()).orElseThrow();
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(item, asset, 0, true));
    }

    private static void authenticate(UUID userId) {
        Jwt jwt = new Jwt("test", NOW, NOW.plusSeconds(60), Map.of("alg", "none"), Map.of("sub", userId.toString(), "sessionId", UUID.randomUUID().toString(), "roles", List.of("USER")));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private enum ReadinessFailure {
        MERCHANT_INACTIVE(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED), CATEGORY_INACTIVE(ErrorCode.RESOURCE_NOT_FOUND),
        CATEGORY_INCOMPATIBLE(ErrorCode.CATEGORY_NOT_COMPATIBLE), REQUIRED_ATTRIBUTE_MISSING(ErrorCode.CATALOG_ATTRIBUTE_INVALID),
        ACTIVE_VARIANT_MISSING(ErrorCode.CATALOG_VARIANT_COMBINATION_INVALID), AVAILABLE_OFFER_MISSING(ErrorCode.CATALOG_LOCATION_OFFER_INVALID),
        OFFER_LOCATION_INACTIVE(ErrorCode.CATALOG_LOCATION_OFFER_INVALID), SERVICE_LOCATION_DISABLED(ErrorCode.CATALOG_LOCATION_OFFER_INVALID),
        MEDIA_NOT_READY(ErrorCode.MEDIA_NOT_ATTACHABLE);
        private final ErrorCode errorCode;
        ReadinessFailure(ErrorCode errorCode) { this.errorCode = errorCode; }
        ErrorCode errorCode() { return errorCode; }
    }
    private record Fixture(UUID ownerId, UUID merchantId, UUID itemId, UUID categoryId, UUID variantId, UUID offerId, UUID locationId) {}
    @FunctionalInterface private interface ThrowingCommand { void run(); }
}
