package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.catalog.model.CatalogItemAttributeValueEntity;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemMediaEntity;
import vn.covie.catalog.model.CatalogItemTagEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryAttributeOptionEntity;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.OptionGroupEntity;
import vn.covie.catalog.model.OptionValueEntity;
import vn.covie.catalog.model.TagEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantOptionValueEntity;
import vn.covie.catalog.persistence.CatalogItemAttributeValueRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CatalogItemTagRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.CategoryAttributeOptionRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.OptionGroupRepository;
import vn.covie.catalog.persistence.OptionValueRepository;
import vn.covie.catalog.persistence.TagRepository;
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMemberLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MerchantCatalogManagementDetailControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantMemberLocationRepository assignments;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CategoryAttributeRepository categoryAttributes;
    @Autowired CategoryAttributeOptionRepository attributeOptions;
    @Autowired CatalogItemRepository items;
    @Autowired CatalogItemAttributeValueRepository attributeValues;
    @Autowired CatalogItemTagRepository itemTags;
    @Autowired TagRepository tags;
    @Autowired MediaAssetRepository media;
    @Autowired CatalogItemMediaRepository itemMedia;
    @Autowired OptionGroupRepository optionGroups;
    @Autowired OptionValueRepository optionValues;
    @Autowired VariantRepository variants;
    @Autowired VariantOptionValueRepository variantOptions;
    @Autowired LocationVariantRepository offers;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void returnsLosslessOwnerDetailWithIndependentVersionsAndSafeMedia() throws Exception {
        Actor owner = actor();
        Fixture fixture = richFixture(owner, MerchantRole.OWNER);

        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(jsonPath("$.id").value(fixture.itemId().toString()))
                .andExpect(jsonPath("$.type").value("PRODUCT"))
                .andExpect(jsonPath("$.listingKind").value("STANDARD"))
                .andExpect(jsonPath("$.categoryId").value(fixture.categoryId().toString()))
                .andExpect(jsonPath("$.name").value("Cà phê hạt"))
                .andExpect(jsonPath("$.description").value("Rang vừa"))
                .andExpect(jsonPath("$.listingStatus").value("DRAFT"))
                .andExpect(jsonPath("$.moderationStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.attributes[0].categoryAttributeId").value(fixture.attributeId().toString()))
                .andExpect(jsonPath("$.attributes[0].attributeKey").value("origin"))
                .andExpect(jsonPath("$.attributes[0].valueText").value("Đà Lạt"))
                .andExpect(jsonPath("$.tags[0]").value(org.hamcrest.Matchers.startsWith("arabica-")))
                .andExpect(jsonPath("$.media[0].id").value(fixture.mediaId().toString()))
                .andExpect(jsonPath("$.media[0].status").value("READY"))
                .andExpect(jsonPath("$.media[0].url").value("https://media.test.covie/catalog/detail-" + fixture.mediaId() + ".png"))
                .andExpect(jsonPath("$.media[0].sortOrder").value(0))
                .andExpect(jsonPath("$.media[0].primary").value(true))
                .andExpect(jsonPath("$.media[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.optionGroups[0].values[0].id").value(fixture.optionValueId().toString()))
                .andExpect(jsonPath("$.variants[0].selectedOptionIds[0]").value(fixture.optionValueId().toString()))
                .andExpect(jsonPath("$.variants[0].version").value(0))
                .andExpect(jsonPath("$.locationOffers[0].merchantLocationName").value("Điểm chính"))
                .andExpect(jsonPath("$.locationOffers[0].version").value(0))
                .andExpect(jsonPath("$.allowedActions.length()").value(4))
                .andExpect(jsonPath("$.allowedActions[0]").value("UPDATE"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CREATE_VARIANT"))
                .andExpect(jsonPath("$.allowedActions[2]").value("PUBLISH"))
                .andExpect(jsonPath("$.allowedActions[3]").value("ARCHIVE"))
                .andExpect(jsonPath("$.variants[0].allowedActions[0]").value("UPDATE"))
                .andExpect(jsonPath("$.locationOffers[0].allowedActions[0]").value("UPDATE"));
    }

    @Test
    void returnsExplicitEmptyCollectionsForAnIncompleteDraft() throws Exception {
        Actor owner = actor();
        Fixture fixture = emptyFixture(owner, MerchantRole.OWNER);

        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes").isEmpty())
                .andExpect(jsonPath("$.tags").isEmpty())
                .andExpect(jsonPath("$.media").isEmpty())
                .andExpect(jsonPath("$.optionGroups").isEmpty())
                .andExpect(jsonPath("$.variants").isEmpty())
                .andExpect(jsonPath("$.locationOffers").isEmpty());
    }

    @Test
    void attributesFollowCategoryDefinitionOrderRatherThanUuidHashOrder() throws Exception {
        Actor owner = actor(); Fixture fixture = emptyFixture(owner, MerchantRole.OWNER);
        UUID firstId = new UUID(0, 2); UUID secondId = new UUID(0, 1);
        categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(firstId, fixture.categoryId(),
                "Thứ nhất", "first", CategoryAttributeType.TEXT, false, true, 0));
        categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(secondId, fixture.categoryId(),
                "Thứ hai", "second", CategoryAttributeType.TEXT, false, true, 1));
        attributeValues.saveAndFlush(CatalogItemAttributeValueEntity.create(UUID.randomUUID(), fixture.itemId(),
                firstId, "A", null, null, null));
        attributeValues.saveAndFlush(CatalogItemAttributeValueEntity.create(UUID.randomUUID(), fixture.itemId(),
                secondId, "B", null, null, null));

        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes[0].attributeKey").value("first"))
                .andExpect(jsonPath("$.attributes[1].attributeKey").value("second"));
    }

    @Test
    void selectedVariantOptionsFollowTheirOptionGroupOrder() throws Exception {
        Actor owner = actor(); Fixture fixture = richFixture(owner, MerchantRole.OWNER);
        OptionGroupEntity secondGroup = optionGroups.saveAndFlush(OptionGroupEntity.create(UUID.randomUUID(),
                fixture.itemId(), "Kiểu đóng gói", 1));
        UUID secondOptionId = new UUID(0, 9);
        optionValues.saveAndFlush(OptionValueEntity.create(secondOptionId, secondGroup.getId(), "Túi", 0));
        variantOptions.saveAndFlush(VariantOptionValueEntity.associate(
                variants.findById(fixture.variantId()).orElseThrow(),
                optionValues.findById(secondOptionId).orElseThrow()));

        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optionGroups[0].sortOrder").value(0))
                .andExpect(jsonPath("$.optionGroups[1].sortOrder").value(1))
                .andExpect(jsonPath("$.variants[0].selectedOptionIds[0]").value(fixture.optionValueId().toString()))
                .andExpect(jsonPath("$.variants[0].selectedOptionIds[1]").value(secondOptionId.toString()));
    }

    @Test
    void returnsCanonicalTypedNumberBooleanAndSelectionAttributes() throws Exception {
        Actor owner = actor(); Fixture fixture = emptyFixture(owner, MerchantRole.OWNER);
        UUID numberId = attributeDefinition(fixture, "weight", CategoryAttributeType.NUMBER, 0);
        UUID booleanId = attributeDefinition(fixture, "organic", CategoryAttributeType.BOOLEAN, 1);
        UUID singleId = attributeDefinition(fixture, "roast", CategoryAttributeType.SINGLE_SELECT, 2);
        UUID multiId = attributeDefinition(fixture, "colors", CategoryAttributeType.MULTI_SELECT, 3);
        UUID singleOption = attributeOptions.saveAndFlush(CategoryAttributeOptionEntity.create(UUID.randomUUID(), singleId, "Light", 0)).getId();
        UUID firstMulti = attributeOptions.saveAndFlush(CategoryAttributeOptionEntity.create(UUID.randomUUID(), multiId, "Red", 0)).getId();
        UUID secondMulti = attributeOptions.saveAndFlush(CategoryAttributeOptionEntity.create(UUID.randomUUID(), multiId, "Blue", 1)).getId();
        attributeValues.saveAndFlush(CatalogItemAttributeValueEntity.create(UUID.randomUUID(), fixture.itemId(),
                numberId, null, new BigDecimal("12.5"), null, null));
        attributeValues.saveAndFlush(CatalogItemAttributeValueEntity.create(UUID.randomUUID(), fixture.itemId(),
                booleanId, null, null, true, null));
        attributeValues.saveAndFlush(CatalogItemAttributeValueEntity.create(UUID.randomUUID(), fixture.itemId(),
                singleId, null, null, null, singleOption));
        attributeValues.saveAndFlush(CatalogItemAttributeValueEntity.create(UUID.randomUUID(), fixture.itemId(),
                multiId, null, null, null, firstMulti));
        attributeValues.saveAndFlush(CatalogItemAttributeValueEntity.create(UUID.randomUUID(), fixture.itemId(),
                multiId, null, null, null, secondMulti));

        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.length()").value(5))
                .andExpect(jsonPath("$.attributes[0].attributeKey").value("weight"))
                .andExpect(jsonPath("$.attributes[0].valueNumber").value(12.5))
                .andExpect(jsonPath("$.attributes[1].attributeKey").value("organic"))
                .andExpect(jsonPath("$.attributes[1].valueBoolean").value(true))
                .andExpect(jsonPath("$.attributes[2].attributeKey").value("roast"))
                .andExpect(jsonPath("$.attributes[2].optionId").value(singleOption.toString()))
                .andExpect(jsonPath("$.attributes[3].attributeKey").value("colors"))
                .andExpect(jsonPath("$.attributes[4].attributeKey").value("colors"));
    }

    @Test
    void returnsPendingAttachedMediaWithoutAUrlOrStorageKey() throws Exception {
        Actor owner = actor(); Fixture fixture = richFixture(owner, MerchantRole.OWNER);
        UUID pendingId = UUID.randomUUID();
        MediaAssetEntity pending = media.saveAndFlush(MediaAssetEntity.pending(pendingId, owner.userId(),
                "catalog/private-" + pendingId + ".png", MediaPurpose.CATALOG_IMAGE, "image/png", 100,
                NOW.plusSeconds(600), NOW));
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(items.findById(fixture.itemId()).orElseThrow(), pending, 1, false));

        String body = mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.media[0].id").value(fixture.mediaId().toString()))
                .andExpect(jsonPath("$.media[0].status").value("READY"))
                .andExpect(jsonPath("$.media[1].id").value(pendingId.toString()))
                .andExpect(jsonPath("$.media[1].status").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.media[1].url").doesNotExist())
                .andExpect(jsonPath("$.media[1].primary").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("private-" + pendingId);
    }

    @Test
    void hidesOwnershipAndDeletionBoundariesBehindNotFound() throws Exception {
        Actor owner = actor();
        Actor outsider = actor();
        Fixture fixture = emptyFixture(owner, MerchantRole.OWNER);

        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(outsider.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(path(UUID.randomUUID(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(path(fixture.merchantId(), UUID.randomUUID())).with(owner.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        CatalogItemEntity item = items.findById(fixture.itemId()).orElseThrow();
        item.delete(NOW.plusSeconds(1));
        items.saveAndFlush(item);
        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void staffSeesOnlyOffersAtAssignedActiveLocations() throws Exception {
        Actor staff = actor();
        Fixture fixture = richFixture(staff, MerchantRole.STAFF);
        UUID unassignedLocationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Điểm riêng", "2 CoVie", 10.81, 106.71, NOW)).getId();
        UUID hiddenOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), unassignedLocationId,
                fixture.variantId(), 99000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();

        String body = mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(staff.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationOffers.length()").value(1))
                .andExpect(jsonPath("$.locationOffers[0].merchantLocationId").value(fixture.locationId().toString()))
                .andExpect(jsonPath("$.allowedActions").isEmpty())
                .andExpect(jsonPath("$.variants[0].allowedActions").isEmpty())
                .andExpect(jsonPath("$.locationOffers[0].allowedActions[0]").value("UPDATE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(hiddenOfferId.toString()).doesNotContain("Điểm riêng");
    }

    @Test
    void managerReceivesStructureLifecycleAndAllOfferActions() throws Exception {
        Actor manager = actor();
        Fixture fixture = richFixture(manager, MerchantRole.MANAGER);
        UUID secondLocationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Điểm phụ", "3 CoVie", 10.82, 106.72, NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), secondLocationId, fixture.variantId(),
                55000, "VND", true, InventoryMode.UNTRACKED, null, NOW));

        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(manager.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions[0]").value("UPDATE"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CREATE_VARIANT"))
                .andExpect(jsonPath("$.allowedActions[2]").value("PUBLISH"))
                .andExpect(jsonPath("$.allowedActions[3]").value("ARCHIVE"))
                .andExpect(jsonPath("$.locationOffers.length()").value(2))
                .andExpect(jsonPath("$.locationOffers[0].allowedActions[0]").value("UPDATE"))
                .andExpect(jsonPath("$.locationOffers[1].allowedActions[0]").value("UPDATE"));
    }

    @Test
    void lifecycleActionsFollowListingModerationAndPublishabilityTruth() throws Exception {
        Actor owner = actor();

        Fixture active = richFixture(owner, MerchantRole.OWNER);
        CatalogItemEntity activeItem = items.findById(active.itemId()).orElseThrow();
        activeItem.publish(NOW.plusSeconds(1));
        items.saveAndFlush(activeItem);
        expectActions(owner, active, "UPDATE", "CREATE_VARIANT", "PAUSE", "ARCHIVE");

        activeItem = items.findById(active.itemId()).orElseThrow();
        activeItem.pause(NOW.plusSeconds(2));
        items.saveAndFlush(activeItem);
        expectActions(owner, active, "UPDATE", "CREATE_VARIANT", "ACTIVATE", "ARCHIVE");

        activeItem = items.findById(active.itemId()).orElseThrow();
        activeItem.requireModeration(NOW.plusSeconds(3));
        items.saveAndFlush(activeItem);
        expectActions(owner, active, "UPDATE", "CREATE_VARIANT", "ARCHIVE");

        activeItem = items.findById(active.itemId()).orElseThrow();
        activeItem.rejectModeration(NOW.plusSeconds(4));
        items.saveAndFlush(activeItem);
        expectActions(owner, active, "UPDATE", "CREATE_VARIANT", "ARCHIVE");

        Fixture hidden = richFixture(owner, MerchantRole.OWNER);
        CatalogItemEntity hiddenItem = items.findById(hidden.itemId()).orElseThrow();
        hiddenItem.publish(NOW.plusSeconds(1));
        hiddenItem.hide(NOW.plusSeconds(2));
        items.saveAndFlush(hiddenItem);
        expectActions(owner, hidden, "UPDATE", "CREATE_VARIANT", "PAUSE", "ARCHIVE");

        Fixture incomplete = emptyFixture(owner, MerchantRole.OWNER);
        expectActions(owner, incomplete, "UPDATE", "CREATE_VARIANT", "ARCHIVE");
    }

    @Test
    void variantDeleteAppearsOnlyWhenAnotherNonDeletedVariantRemains() throws Exception {
        Actor owner = actor();
        Fixture fixture = richFixture(owner, MerchantRole.OWNER);
        variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(), "CF-M", "Vừa", false, 1, NOW));

        mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].allowedActions.length()").value(2))
                .andExpect(jsonPath("$.variants[0].allowedActions[0]").value("UPDATE"))
                .andExpect(jsonPath("$.variants[0].allowedActions[1]").value("DELETE"))
                .andExpect(jsonPath("$.variants[1].allowedActions[1]").value("DELETE"));
    }

    private void expectActions(Actor actor, Fixture fixture, String... expected) throws Exception {
        var result = mockMvc.perform(get(path(fixture.merchantId(), fixture.itemId())).with(actor.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(expected.length));
        for (int index = 0; index < expected.length; index++) {
            result.andExpect(jsonPath("$.allowedActions[" + index + "]").value(expected[index]));
        }
    }

    private Fixture richFixture(Actor actor, MerchantRole role) {
        Fixture fixture = emptyFixture(actor, role);
        CategoryAttributeEntity attribute = categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(),
                fixture.categoryId(), "Xuất xứ", "origin", CategoryAttributeType.TEXT, false, true, 0));
        attributeValues.saveAndFlush(CatalogItemAttributeValueEntity.create(UUID.randomUUID(), fixture.itemId(),
                attribute.getId(), "Đà Lạt", null, null, null));
        String tagName = "arabica-" + UUID.randomUUID();
        TagEntity tag = tags.saveAndFlush(TagEntity.create(UUID.randomUUID(), tagName, tagName, NOW));
        itemTags.saveAndFlush(CatalogItemTagEntity.associate(items.findById(fixture.itemId()).orElseThrow(), tag));

        UUID mediaId = UUID.randomUUID();
        MediaAssetEntity asset = MediaAssetEntity.pending(mediaId, actor.userId(), "catalog/detail-" + mediaId + ".png",
                MediaPurpose.CATALOG_IMAGE, "image/png", 100, NOW.plusSeconds(600), NOW);
        asset.markReady(NOW);
        media.saveAndFlush(asset);
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(items.findById(fixture.itemId()).orElseThrow(), asset, 0, true));

        OptionGroupEntity group = optionGroups.saveAndFlush(OptionGroupEntity.create(UUID.randomUUID(), fixture.itemId(), "Cỡ", 0));
        OptionValueEntity value = optionValues.saveAndFlush(OptionValueEntity.create(UUID.randomUUID(), group.getId(), "Nhỏ", 0));
        VariantEntity variant = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(), "CF-S", "Nhỏ", false, 0, NOW));
        variantOptions.saveAndFlush(VariantOptionValueEntity.associate(variant, value));
        LocationVariantEntity offer = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                variant.getId(), 45000, "VND", true, InventoryMode.UNTRACKED, 10, NOW));
        return new Fixture(fixture.merchantId(), fixture.itemId(), fixture.locationId(), fixture.categoryId(), attribute.getId(),
                mediaId, value.getId(), variant.getId(), offer.getId());
    }

    private UUID attributeDefinition(Fixture fixture, String key, CategoryAttributeType type, int sortOrder) {
        return categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), fixture.categoryId(),
                key, key, type, false, true, sortOrder)).getId();
    }

    private Fixture emptyFixture(Actor actor, MerchantRole role) {
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        MerchantMembershipEntity membership = memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),
                merchantId, actor.userId(), role, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Điểm chính", "1 CoVie", 10.8, 106.7, NOW)).getId();
        if (role == MerchantRole.STAFF) {
            assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(), membership.getId(), locationId, NOW));
        }
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Đồ uống",
                "drinks-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID();
        items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD,
                categoryId, "Cà phê hạt", "coffee-" + itemId, "Rang vừa", NOW));
        return new Fixture(merchantId, itemId, locationId, categoryId, null, null, null, null, null);
    }

    private Actor actor() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, "User", now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now,
                now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }

    private static String path(UUID merchantId, UUID itemId) {
        return "/api/v1/merchants/" + merchantId + "/catalog-items/" + itemId;
    }

    private record Fixture(UUID merchantId, UUID itemId, UUID locationId, UUID categoryId, UUID attributeId,
            UUID mediaId, UUID optionValueId, UUID variantId, UUID offerId) {}

    private record Actor(UUID userId, UUID sessionId) {
        JwtRequestPostProcessor jwt() {
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(token -> token.subject(userId.toString()).claim("sessionId", sessionId.toString())
                    .claim("roles", List.of("USER"))).authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
