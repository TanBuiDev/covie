package vn.covie.merchantcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.catalog.application.CatalogPublishValidator;
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
import vn.covie.media.application.MediaAttachmentPolicy;
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
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class CatalogInvariantTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired WebApplicationContext web;
    @Autowired CatalogPublishValidator publishValidator;
    @Autowired MediaAttachmentPolicy mediaAttachmentPolicy;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired MediaAssetRepository mediaAssets;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void enforcesInventoryModeAndCategoryCompatibilityAtCreateTime() throws Exception {
        Actor actor = actor();
        UUID merchantId = merchant(actor);
        UUID locationId = location(merchantId);
        UUID productCategoryId = category(CategoryCatalogType.PRODUCT);
        UUID serviceCategoryId = category(CategoryCatalogType.SERVICE);

        assertCatalogCreateError(actor, merchantId, productCategoryId, locationId, CatalogItemType.PRODUCT, null,
                ErrorCode.CATALOG_LOCATION_OFFER_INVALID);
        assertCatalogCreateStatus(actor, merchantId, productCategoryId, locationId, CatalogItemType.SERVICE, InventoryMode.UNTRACKED);
        assertCatalogCreateError(actor, merchantId, serviceCategoryId, locationId, CatalogItemType.PRODUCT, InventoryMode.UNTRACKED,
                ErrorCode.CATEGORY_NOT_COMPATIBLE);
    }

    @Test
    void rejectsPublishWhenMerchantLocationVariantOrOfferBecomeInactive() {
        UUID merchantId = merchant();
        UUID locationId = location(merchantId);
        UUID categoryId = category(CategoryCatalogType.PRODUCT);
        CatalogItemEntity item = CatalogItemEntity.create(UUID.randomUUID(), merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + UUID.randomUUID(), null, NOW);
        items.saveAndFlush(item);
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), item.getId(), "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25000, "VND",
                true, InventoryMode.UNTRACKED, null, NOW));

        publishValidator.requirePublishable(items.findById(item.getId()).orElseThrow());

        jdbc.update("update merchant_profiles set merchant_status = 'PAUSED' where id = ?", merchantId);
        assertCode(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED, () -> publishValidator.requirePublishable(items.findById(item.getId()).orElseThrow()));

        jdbc.update("update merchant_profiles set merchant_status = 'ACTIVE' where id = ?", merchantId);
        jdbc.update("update merchant_locations set location_status = 'PAUSED' where id = ?", locationId);
        assertCode(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, () -> publishValidator.requirePublishable(items.findById(item.getId()).orElseThrow()));

        jdbc.update("update merchant_locations set location_status = 'ACTIVE' where id = ?", locationId);
        jdbc.update("update variants set variant_status = 'PAUSED' where id = ?", variantId);
        assertCode(ErrorCode.CATALOG_VARIANT_COMBINATION_INVALID, () -> publishValidator.requirePublishable(items.findById(item.getId()).orElseThrow()));

        jdbc.update("update variants set variant_status = 'ACTIVE' where id = ?", variantId);
        jdbc.update("update location_variants set is_available = false where variant_id = ?", variantId);
        assertCode(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, () -> publishValidator.requirePublishable(items.findById(item.getId()).orElseThrow()));
    }

    @Test
    void requiresReadyOwnedCatalogMediaWithAllowedPurposeForAttachment() {
        UUID ownerId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId, "Owner", NOW));
        MediaAssetEntity ready = mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(), ownerId, "media/ready.png",
                MediaPurpose.CATALOG_IMAGE, "image/png", 8, NOW.plusSeconds(600), NOW));
        ready.markReady(NOW);
        mediaAssets.saveAndFlush(ready);

        assertThat(mediaAttachmentPolicy.requireReadyOwned(ready.getId(), ownerId, Set.of(MediaPurpose.CATALOG_IMAGE)))
                .extracting(MediaAssetEntity::getId).isEqualTo(ready.getId());
        assertCode(ErrorCode.RESOURCE_NOT_FOUND,
                () -> mediaAttachmentPolicy.requireReadyOwned(ready.getId(), UUID.randomUUID(), Set.of(MediaPurpose.CATALOG_IMAGE)));
        MediaAssetEntity pending = mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(), ownerId, "media/pending.png",
                MediaPurpose.CATALOG_IMAGE, "image/png", 8, NOW.plusSeconds(600), NOW));
        assertCode(ErrorCode.MEDIA_NOT_ATTACHABLE,
                () -> mediaAttachmentPolicy.requireReadyOwned(pending.getId(), ownerId, Set.of(MediaPurpose.CATALOG_IMAGE)));
        assertCode(ErrorCode.MEDIA_NOT_ATTACHABLE,
                () -> mediaAttachmentPolicy.requireReadyOwned(ready.getId(), ownerId, Set.of(MediaPurpose.MERCHANT_LOGO)));
    }

    private void assertCatalogCreateError(Actor actor, UUID merchantId, UUID categoryId, UUID locationId, CatalogItemType type,
            InventoryMode inventoryMode, ErrorCode expected) throws Exception {
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(actor.jwt())
                        .header("Idempotency-Key", "inventory-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(catalogCreateBody(categoryId, locationId, type, inventoryMode, false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(expected.name()));
    }

    private void assertCatalogCreateStatus(Actor actor, UUID merchantId, UUID categoryId, UUID locationId, CatalogItemType type,
            InventoryMode inventoryMode) throws Exception {
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(actor.jwt())
                        .header("Idempotency-Key", "inventory-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(catalogCreateBody(categoryId, locationId, type, inventoryMode, true)))
                .andExpect(status().isUnprocessableEntity());
    }

    private String catalogCreateBody(UUID categoryId, UUID locationId, CatalogItemType type, InventoryMode inventoryMode,
            boolean includeInventoryMode) {
        String inventoryField = includeInventoryMode ? ",\"inventoryMode\":\"" + inventoryMode + "\"" : "";
        return """
                {
                  "type":"%s",
                  "listingKind":"STANDARD",
                  "categoryId":"%s",
                  "name":"Item",
                  "locationOffers":[
                    {
                      "merchantLocationId":"%s",
                      "variantClientKey":"__default__",
                      "price":25000,
                      "currency":"VND",
                      "available":true%s
                    }
                  ]
                }
                """.formatted(type, categoryId, locationId, inventoryField);
    }

    private void assertCode(ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable runnable) {
        assertThatThrownBy(runnable).satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(code));
    }

    private UUID merchant() {
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        return merchantId;
    }

    private UUID merchant(Actor actor) {
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, actor.userId(), MerchantRole.OWNER, NOW));
        return merchantId;
    }

    private UUID location(UUID merchantId) {
        return locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
    }

    private UUID category(CategoryCatalogType type) {
        return categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Category", "category-" + UUID.randomUUID(), type, CategoryStatus.ACTIVE, 0)).getId();
    }

    private Actor actor() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, "Actor", now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }

    private record Actor(UUID userId, UUID sessionId) {
        SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString())
                    .claim("roles", List.of("USER"))).authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
