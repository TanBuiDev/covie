package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.shared.outbox.OutboxEventRepository;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MerchantCatalogCreateControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CategoryAttributeRepository categoryAttributes;
    @Autowired CatalogItemRepository items;
    @Autowired OutboxEventRepository outbox;
    @Autowired MediaAssetRepository mediaAssets;
    private MockMvc mockMvc;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    @Test
    void createsProductDraftWithServerDefaultVariantAndIdempotentlyReplaysIt() throws Exception {
        Actor owner = actor();
        UUID merchantId = merchant(owner);
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(),
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        String body = "{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\"" + categoryId
                + "\",\"name\":\"Coffee\",\"locationOffers\":[{\"merchantLocationId\":\"" + locationId
                + "\",\"variantClientKey\":\"__default__\",\"price\":25000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"UNTRACKED\"}]}";

        long countBefore = items.count();
        long outboxBefore = outbox.count();
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "catalog-create-key").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.listingStatus").value("DRAFT"))
                .andExpect(jsonPath("$.variants[0].isDefault").value(true)).andExpect(jsonPath("$.variants[0].name").doesNotExist());
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "catalog-create-key").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertThat(items.count()).isEqualTo(countBefore + 1);
        assertThat(outbox.count()).isEqualTo(outboxBefore + 1);
    }

    @Test
    void rejectsInvalidAggregateInvariantsWithBusinessErrors() throws Exception {
        Actor owner = actor(); UUID merchantId = merchant(owner);
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID productCategoryId = category(CategoryCatalogType.PRODUCT);
        UUID serviceCategoryId = category(CategoryCatalogType.SERVICE);
        String productBase = "{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\"" + productCategoryId
                + "\",\"name\":\"Coffee\",\"locationOffers\":[{\"merchantLocationId\":\"" + locationId
                + "\",\"variantClientKey\":\"__default__\",\"price\":25000,\"currency\":\"VND\",\"available\":true";
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "missing-inventory").contentType(MediaType.APPLICATION_JSON)
                        .content(productBase + "}]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_LOCATION_OFFER_INVALID"));
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "service-resale").contentType(MediaType.APPLICATION_JSON)
                        .content(productBase.replace("PRODUCT", "SERVICE").replace("STANDARD", "RESALE").replace(productCategoryId.toString(), serviceCategoryId.toString()) + ",\"inventoryMode\":\"UNTRACKED\"}]}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "wrong-category").contentType(MediaType.APPLICATION_JSON)
                        .content(productBase.replace("PRODUCT", "SERVICE") + ",\"inventoryMode\":\"UNTRACKED\"}]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_COMPATIBLE"));
    }

    @Test
    void rejectsMissingRequiredAttributeAndIdempotencyBodyMismatch() throws Exception {
        Actor owner = actor(); UUID merchantId = merchant(owner);
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = category(CategoryCatalogType.PRODUCT);
        categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), categoryId, "Brand", "brand",
                CategoryAttributeType.TEXT, true, false, 0));
        String withoutAttribute = productBody(categoryId, locationId, "Coffee", "UNTRACKED");
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "required-attribute").contentType(MediaType.APPLICATION_JSON).content(withoutAttribute))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_ATTRIBUTE_INVALID"));
        UUID plainCategory = category(CategoryCatalogType.PRODUCT);
        String first = productBody(plainCategory, locationId, "Tea", "UNTRACKED");
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "reused").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "reused").contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(plainCategory, locationId, "Other tea", "UNTRACKED")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST"));
    }

    @Test
    void resolvesClientKeysForVariantsAndRollsBackWholeAggregateWhenTheyAreInvalid() throws Exception {
        Actor owner = actor(); UUID merchantId = merchant(owner);
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId(); UUID categoryId = category(CategoryCatalogType.PRODUCT);
        String valid = "{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\"" + categoryId
                + "\",\"name\":\"Shirt\",\"options\":[{\"clientKey\":\"size\",\"name\":\"Size\",\"values\":[{\"clientKey\":\"small\",\"value\":\"S\"},{\"clientKey\":\"large\",\"value\":\"L\"}]}],\"variants\":[{\"clientKey\":\"shirt-s\",\"name\":\"Small\",\"optionValueKeys\":[\"small\"]},{\"clientKey\":\"shirt-l\",\"name\":\"Large\",\"optionValueKeys\":[\"large\"]}],\"locationOffers\":[{\"merchantLocationId\":\"" + locationId
                + "\",\"variantClientKey\":\"shirt-s\",\"price\":10000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"TRACKED\"},{\"merchantLocationId\":\"" + locationId
                + "\",\"variantClientKey\":\"shirt-l\",\"price\":12000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"UNTRACKED\"}]}";
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()).header("Idempotency-Key", "keys-ok")
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.variants.length()").value(2))
                .andExpect(jsonPath("$.locationOffers.length()").value(2));
        long itemsBefore = items.count(); long eventsBefore = outbox.count();
        String invalid = valid.replace("\"shirt-l\",\"name\":\"Large\",\"optionValueKeys\":[\"large\"]",
                "\"shirt-l\",\"name\":\"Large\",\"optionValueKeys\":[\"small\"]");
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()).header("Idempotency-Key", "keys-bad")
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_VARIANT_COMBINATION_INVALID"));
        assertThat(items.count()).isEqualTo(itemsBefore);
        assertThat(outbox.count()).isEqualTo(eventsBefore);
    }

    @Test
    void acceptsOnlyReadyCatalogMediaOwnedByTheActor() throws Exception {
        Actor owner = actor(); UUID merchantId = merchant(owner); UUID categoryId = category(CategoryCatalogType.PRODUCT);
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID otherUserId = UUID.randomUUID(); users.saveAndFlush(UserEntity.active(otherUserId, "Other", NOW));
        MediaAssetEntity otherOwned = readyMedia(otherUserId, MediaPurpose.CATALOG_IMAGE);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()).header("Idempotency-Key", "foreign-media")
                        .contentType(MediaType.APPLICATION_JSON).content(productBody(categoryId, locationId, "Coffee", "UNTRACKED")
                                .replace("\"locationOffers\"", "\"mediaIds\":[\"" + otherOwned.getId() + "\"],\"locationOffers\"")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        MediaAssetEntity wrongPurpose = readyMedia(owner.userId(), MediaPurpose.MERCHANT_LOGO);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()).header("Idempotency-Key", "wrong-media-purpose")
                        .contentType(MediaType.APPLICATION_JSON).content(productBody(categoryId, locationId, "Coffee", "UNTRACKED")
                                .replace("\"locationOffers\"", "\"mediaIds\":[\"" + wrongPurpose.getId() + "\"],\"locationOffers\"")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("MEDIA_NOT_ATTACHABLE"));
    }

    private UUID category(CategoryCatalogType type) { return categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null,
            "Category", "category-" + UUID.randomUUID(), type, CategoryStatus.ACTIVE, 0)).getId(); }
    private String productBody(UUID categoryId, UUID locationId, String name, String inventoryMode) {
        return "{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name
                + "\",\"locationOffers\":[{\"merchantLocationId\":\"" + locationId + "\",\"variantClientKey\":\"__default__\",\"price\":25000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"" + inventoryMode + "\"}]}";
    }
    private MediaAssetEntity readyMedia(UUID ownerId, MediaPurpose purpose) {
        MediaAssetEntity asset = MediaAssetEntity.pending(UUID.randomUUID(), ownerId, "media/" + UUID.randomUUID(), purpose,
                "image/png", 10, NOW.plusSeconds(600), NOW); asset.markReady(NOW); return mediaAssets.saveAndFlush(asset);
    }

    private UUID merchant(Actor actor) {
        UUID id = UUID.randomUUID(); merchants.saveAndFlush(MerchantProfileEntity.create(id, "Merchant", "m-" + id, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), id, actor.userId(), MerchantRole.OWNER, NOW)); return id;
    }
    private Actor actor() {
        UUID userId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID(); Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, "Owner", now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }
    private record Actor(UUID userId, UUID sessionId) {
        org.springframework.test.web.servlet.request.RequestPostProcessor jwt() {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString()).claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
