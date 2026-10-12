package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
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
    @Autowired VariantRepository variants;
    @Autowired VariantOptionValueRepository variantOptionValues;
    @Autowired JdbcTemplate jdbc;
    @Autowired vn.covie.auth.application.InitialSessionIssuer issuer;
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
    void listsTheMerchantCatalogWithManagementStatuses() throws Exception {
        Actor owner = actor();
        UUID merchantId = merchant(owner);
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = category(CategoryCatalogType.PRODUCT);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt())
                        .header("Idempotency-Key", "catalog-management-list").contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(categoryId, locationId, "Coffee", "UNTRACKED")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Coffee"))
                .andExpect(jsonPath("$[0].listingStatus").value("DRAFT"))
                .andExpect(jsonPath("$[0].type").value("PRODUCT"));
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
        String bearer = "Bearer " + issuer.issue(owner.userId(), new vn.covie.auth.model.DeviceMetadata("Catalog test", "catalog-test")).accessToken().value();
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = category(CategoryCatalogType.PRODUCT);
        categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), categoryId, "Brand", "brand",
                CategoryAttributeType.TEXT, true, false, 0));
        String withoutAttribute = productBody(categoryId, locationId, "Coffee", "UNTRACKED");
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).header("Authorization", bearer)
                        .header("Idempotency-Key", "required-attribute").contentType(MediaType.APPLICATION_JSON).content(withoutAttribute))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_ATTRIBUTE_INVALID"));
        assertThat(jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?", Long.class, merchantId)).isZero();
        UUID plainCategory = category(CategoryCatalogType.PRODUCT);
        String first = productBody(plainCategory, locationId, "Tea", "UNTRACKED");
        String created = mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).header("Authorization", bearer)
                        .header("Idempotency-Key", "reused").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).header("Authorization", bearer)
                        .header("Idempotency-Key", "reused").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(created);
        assertThat(jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?", Long.class, merchantId)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).header("Authorization", bearer)
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

    @Test
    void rejectsIdempotentReplayWhenMembershipWasRevokedOrDemoted() throws Exception {
        Actor revokedOwner = actor(); UUID revokedMerchant = merchant(revokedOwner);
        UUID revokedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), revokedMerchant,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId(); UUID categoryId = category(CategoryCatalogType.PRODUCT);
        String revokedBody = productBody(categoryId, revokedLocation, "Coffee", "UNTRACKED");
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", revokedMerchant).with(revokedOwner.jwt()).header("Idempotency-Key", "revoke-replay")
                        .contentType(MediaType.APPLICATION_JSON).content(revokedBody)).andExpect(status().isCreated());
        MerchantMembershipEntity revoked = memberships.findActive(revokedMerchant, revokedOwner.userId()).orElseThrow();
        revoked.revoke(); memberships.saveAndFlush(revoked);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", revokedMerchant).with(revokedOwner.jwt()).header("Idempotency-Key", "revoke-replay")
                        .contentType(MediaType.APPLICATION_JSON).content(revokedBody)).andExpect(status().isNotFound());

        Actor demotedOwner = actor(); UUID demotedMerchant = merchant(demotedOwner);
        UUID demotedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), demotedMerchant,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId(); String demotedBody = productBody(categoryId, demotedLocation, "Tea", "UNTRACKED");
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", demotedMerchant).with(demotedOwner.jwt()).header("Idempotency-Key", "demote-replay")
                        .contentType(MediaType.APPLICATION_JSON).content(demotedBody)).andExpect(status().isCreated());
        MerchantMembershipEntity demoted = memberships.findActive(demotedMerchant, demotedOwner.userId()).orElseThrow();
        demoted.changeRole(MerchantRole.STAFF); memberships.saveAndFlush(demoted);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", demotedMerchant).with(demotedOwner.jwt()).header("Idempotency-Key", "demote-replay")
                        .contentType(MediaType.APPLICATION_JSON).content(demotedBody)).andExpect(status().isNotFound());
    }

    @Test
    void acceptsLongValidNamesAndRejectsOverlongTagsBeforeDatabasePersistence() throws Exception {
        Actor owner = actor(); UUID merchantId = merchant(owner); UUID categoryId = category(CategoryCatalogType.PRODUCT);
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()).header("Idempotency-Key", "name-205")
                        .contentType(MediaType.APPLICATION_JSON).content(productBody(categoryId, locationId, "n".repeat(205), "UNTRACKED")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()).header("Idempotency-Key", "name-220")
                        .contentType(MediaType.APPLICATION_JSON).content(productBody(categoryId, locationId, "m".repeat(220), "UNTRACKED")))
                .andExpect(status().isCreated());
        String tooLongTag = "t".repeat(101);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()).header("Idempotency-Key", "tag-101")
                        .contentType(MediaType.APPLICATION_JSON).content(productBody(categoryId, locationId, "Tagged", "UNTRACKED")
                                .replace("\"locationOffers\"", "\"tags\":[\"" + tooLongTag + "\"],\"locationOffers\"")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void persistsSearchVectorAndExactlyOneDefaultVariantWithoutOptionJoins() throws Exception {
        Actor owner = actor(); UUID merchantId = merchant(owner); UUID categoryId = category(CategoryCatalogType.PRODUCT);
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId(); long joinsBefore = variantOptionValues.count();
        String result = mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(owner.jwt()).header("Idempotency-Key", "search-default")
                        .contentType(MediaType.APPLICATION_JSON).content(productBody(categoryId, locationId, "Coffee", "UNTRACKED")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(result.replaceFirst("^\\{\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1"));
        assertThat(variants.findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(itemId))
                .singleElement().satisfies(variant -> assertThat(variant.isDefaultVariant()).isTrue());
        assertThat(variantOptionValues.count()).isEqualTo(joinsBefore);
        assertThat(jdbc.queryForObject("select search_vector is not null from catalog_items where id = ?", Boolean.class, itemId)).isTrue();
    }

    @Test
    void rejectsEveryRemainingClientKeyLocationOfferServiceAndMediaInvariant() throws Exception {
        Actor owner = actor(); UUID merchantId = merchant(owner); UUID productCategory = category(CategoryCatalogType.PRODUCT);
        UUID bothCategory = category(CategoryCatalogType.BOTH); UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        assertCatalogError(owner, merchantId, duplicateGroupBody(productCategory, locationId), "duplicate-group", "CATALOG_VARIANT_COMBINATION_INVALID", 422);
        assertCatalogError(owner, merchantId, duplicateValueBody(productCategory, locationId), "duplicate-value", "CATALOG_VARIANT_COMBINATION_INVALID", 422);
        assertCatalogError(owner, merchantId, variantBody(productCategory, locationId, "[\"unknown\"]", "v"), "unknown-option", "CATALOG_VARIANT_COMBINATION_INVALID", 422);
        assertCatalogError(owner, merchantId, twoSameGroupBody(productCategory, locationId), "two-same-group", "CATALOG_VARIANT_COMBINATION_INVALID", 422);
        assertCatalogError(owner, merchantId, productBody(productCategory, locationId, "Offer", "UNTRACKED").replace("__default__", "unknown"), "unknown-offer", "CATALOG_LOCATION_OFFER_INVALID", 422);

        Actor foreignOwner = actor(); UUID foreignMerchant = merchant(foreignOwner); UUID foreignLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), foreignMerchant,
                "Foreign", "2 CoVie", 10.8, 106.7, NOW)).getId();
        assertCatalogError(owner, merchantId, productBody(productCategory, foreignLocation, "Foreign offer", "UNTRACKED"), "foreign-location", "RESOURCE_NOT_FOUND", 404);
        assertCatalogError(owner, merchantId, productBody(bothCategory, locationId, "Service stock", "UNTRACKED").replace("PRODUCT", "SERVICE"), "service-stock", "CATALOG_LOCATION_OFFER_INVALID", 422);
        String disabledService = productBody(bothCategory, locationId, "Disabled service", "UNTRACKED").replace("PRODUCT", "SERVICE").replace(",\"inventoryMode\":\"UNTRACKED\"", "");
        assertCatalogError(owner, merchantId, disabledService, "disabled-service", "CATALOG_LOCATION_OFFER_INVALID", 422);
        MediaAssetEntity pending = MediaAssetEntity.pending(UUID.randomUUID(), owner.userId(), "media/pending", MediaPurpose.CATALOG_IMAGE,
                "image/png", 10, NOW.plusSeconds(600), NOW); mediaAssets.saveAndFlush(pending);
        assertCatalogError(owner, merchantId, productBody(productCategory, locationId, "Pending media", "UNTRACKED")
                .replace("\"locationOffers\"", "\"mediaIds\":[\"" + pending.getId() + "\"],\"locationOffers\""), "pending-media", "MEDIA_NOT_ATTACHABLE", 422);
    }

    private UUID category(CategoryCatalogType type) { return categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null,
            "Category", "category-" + UUID.randomUUID(), type, CategoryStatus.ACTIVE, 0)).getId(); }
    private String productBody(UUID categoryId, UUID locationId, String name, String inventoryMode) {
        return "{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name
                + "\",\"locationOffers\":[{\"merchantLocationId\":\"" + locationId + "\",\"variantClientKey\":\"__default__\",\"price\":25000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"" + inventoryMode + "\"}]}";
    }
    private String variantBody(UUID categoryId, UUID locationId, String keys, String variantKey) {
        return "{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\"" + categoryId + "\",\"name\":\"Variant\",\"options\":[{\"clientKey\":\"color\",\"name\":\"Color\",\"values\":[{\"clientKey\":\"red\",\"value\":\"Red\"}]}],\"variants\":[{\"clientKey\":\"" + variantKey + "\",\"name\":\"Red\",\"optionValueKeys\":" + keys + "}],\"locationOffers\":[{\"merchantLocationId\":\"" + locationId + "\",\"variantClientKey\":\"" + variantKey + "\",\"price\":1,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"UNTRACKED\"}]}";
    }
    private String duplicateGroupBody(UUID categoryId, UUID locationId) {
        return variantBody(categoryId, locationId, "[\"red\"]", "v").replace("}],\"variants\"", "},{\"clientKey\":\"color\",\"name\":\"Other\",\"values\":[{\"clientKey\":\"blue\",\"value\":\"Blue\"}]}],\"variants\"");
    }
    private String duplicateValueBody(UUID categoryId, UUID locationId) {
        return variantBody(categoryId, locationId, "[\"red\"]", "v").replace("}],\"variants\"", "},{\"clientKey\":\"size\",\"name\":\"Size\",\"values\":[{\"clientKey\":\"red\",\"value\":\"S\"}]}],\"variants\"");
    }
    private String twoSameGroupBody(UUID categoryId, UUID locationId) {
        return variantBody(categoryId, locationId, "[\"red\",\"blue\"]", "v").replace("{\"clientKey\":\"red\",\"value\":\"Red\"}", "{\"clientKey\":\"red\",\"value\":\"Red\"},{\"clientKey\":\"blue\",\"value\":\"Blue\"}");
    }
    private void assertCatalogError(Actor actor, UUID merchantId, String body, String key, String errorCode, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items", merchantId).with(actor.jwt()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.error.code").value(errorCode));
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

