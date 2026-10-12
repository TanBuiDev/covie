package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemMediaEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.OptionGroupEntity;
import vn.covie.catalog.model.OptionValueEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantOptionValueEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.OptionGroupRepository;
import vn.covie.catalog.persistence.OptionValueRepository;
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMemberLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MerchantCatalogEditControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired WebApplicationContext web; @Autowired UserRepository users; @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants; @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantMemberLocationRepository assignments; @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories; @Autowired CatalogItemRepository items; @Autowired VariantRepository variants;
    @Autowired CategoryAttributeRepository categoryAttributes;
    @Autowired LocationVariantRepository offers;
    @Autowired OptionGroupRepository optionGroups; @Autowired OptionValueRepository optionValues;
    @Autowired VariantOptionValueRepository variantOptions;
    @Autowired MediaAssetRepository mediaAssets; @Autowired CatalogItemMediaRepository itemMedia;
    private MockMvc mockMvc;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    @Test
    void editsEachResourceAgainstItsOwnEtag() throws Exception {
        Actor owner = actor(); Fixture fixture = fixtureWithOptions(owner);
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).with(owner.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Edited coffee\",\"tags\":[\"arabica\"]}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        mockMvc.perform(patch(variantPath(fixture), fixture.variantId()).with(owner.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"COFFEE-01\",\"variantName\":\"Regular\",\"variantStatus\":\"PAUSED\"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":30000,\"available\":false,\"inventoryMode\":\"TRACKED\",\"preparationTimeMinutes\":10}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getName()).isEqualTo("Edited coffee");
        assertThat(variants.findById(fixture.variantId()).orElseThrow().getVariantStatus().name()).isEqualTo("PAUSED");
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPrice()).isEqualTo(30000);
    }

    @Test
    void managerCanAppendOwnMediaWithoutReowningTheOwnersExistingMedia() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        Actor manager = actor();
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), fixture.merchantId(),
                manager.userId(), MerchantRole.MANAGER, NOW));
        MediaAssetEntity original = readyMedia(owner.userId());
        MediaAssetEntity addition = readyMedia(manager.userId());
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(items.findById(fixture.itemId()).orElseThrow(), original, 0, true));

        mockMvc.perform(patch(itemPath(fixture)).with(manager.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[\"" + original.getId() + "\",\"" + addition.getId() + "\"]}"))
                .andExpect(status().isNoContent());

        assertThat(itemMedia.findAllAssociationsByCatalogItemId(fixture.itemId()))
                .extracting(association -> association.getId().getMediaAssetId())
                .containsExactly(original.getId(), addition.getId());
    }

    @Test
    void keepsStructuralEditingForbiddenToStaffButAllowsTheirAssignedOffer() throws Exception {
        Actor staff = actor(); Fixture fixture = fixture(staff, MerchantRole.STAFF);
        String bearer = "Bearer " + web.getBean(vn.covie.auth.application.InitialSessionIssuer.class).issue(staff.userId(), new vn.covie.auth.model.DeviceMetadata("Staff edit", "catalog-test")).accessToken().value();
        var jdbc = web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var beforeItem = jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId());
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MERCHANT_ACTION_NOT_ALLOWED"));
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId())).isEqualTo(beforeItem);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":31000}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        UUID unassignedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Unassigned", "2 CoVie", 10.8, 106.7, NOW)).getId();
        UUID unassignedOffer = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), unassignedLocation,
                fixture.variantId(), 25000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();
        var beforeOffer = jdbc.queryForMap("select * from location_variants where id=?", unassignedOffer);
        mockMvc.perform(patch(itemPath(fixture) + "/location-offers/" + unassignedOffer).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":32000}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForMap("select * from location_variants where id=?", unassignedOffer)).isEqualTo(beforeOffer);
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPrice()).isEqualTo(31000);
        var assignedAfterEdit = jdbc.queryForMap("select * from location_variants where id=?", fixture.offerId());
        int removed = jdbc.update("delete from merchant_member_locations where membership_id in "
                + "(select id from merchant_memberships where merchant_profile_id=? and user_id=?)", fixture.merchantId(), staff.userId());
        assertThat(removed).isEqualTo(1);
        mockMvc.perform(patch(offerPath(fixture)).header("Authorization", bearer).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":33000}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForMap("select * from location_variants where id=?", fixture.offerId())).isEqualTo(assignedAfterEdit);
    }

    @Test
    void rejectsStaleVersionAndProtectsTheFinalVariant() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v7\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":31000}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RESOURCE_VERSION_CONFLICT"));
        mockMvc.perform(delete(variantPath(fixture), fixture.variantId()).with(owner.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_VARIANT_COMBINATION_INVALID"));
    }

    @Test
    void revalidatesRequiredAttributesWhenChangingThePrimaryCategory() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        String bearer = "Bearer " + web.getBean(vn.covie.auth.application.InitialSessionIssuer.class)
                .issue(owner.userId(), new vn.covie.auth.model.DeviceMetadata("Catalog edit", "catalog-edit-test")).accessToken().value();
        UUID requiredCategory = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Required", "required-" + UUID.randomUUID(),
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 1)).getId();
        categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), requiredCategory, "Brand", "brand",
                CategoryAttributeType.TEXT, true, false, 0));
        var jdbc = web.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var original = jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId());
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"primaryCategoryId\":\"" + requiredCategory + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_ATTRIBUTE_INVALID"));
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId())).isEqualTo(original);
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).header("Authorization", bearer).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"primaryCategoryId\":\"" + requiredCategory
                                + "\",\"attributes\":[{\"attributeKey\":\"brand\",\"valueText\":\"CoVie\"}]}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        assertThat(jdbc.queryForObject("select primary_category_id from catalog_items where id=?", UUID.class, fixture.itemId())).isEqualTo(requiredCategory);
        var stored = jdbc.queryForList("select * from catalog_item_attribute_values where catalog_item_id=?", fixture.itemId());
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().get("value_text")).isEqualTo("CoVie");
        Actor outsider = actor();
        String foreignBearer = "Bearer " + web.getBean(vn.covie.auth.application.InitialSessionIssuer.class)
                .issue(outsider.userId(), new vn.covie.auth.model.DeviceMetadata("Other catalog user", "catalog-test")).accessToken().value();
        var updated = jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId());
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).header("Authorization", foreignBearer).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attributes\":[{\"attributeKey\":\"brand\",\"valueText\":\"Rejected\"}]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?", fixture.itemId())).isEqualTo(updated);
        assertThat(jdbc.queryForList("select * from catalog_item_attribute_values where catalog_item_id=?", fixture.itemId())).isEqualTo(stored);
    }

    @Test
    void distinguishesAnOmittedPreparationTimeFromAnExplicitNull() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        LocationVariantEntity offer = offers.findById(fixture.offerId()).orElseThrow();
        offer.update(offer.getPrice(), offer.isAvailable(), offer.getInventoryMode(), 15, NOW);
        offers.saveAndFlush(offer);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":26000}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v2\""));
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPreparationTimeMinutes()).isEqualTo(15);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v2\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"preparationTimeMinutes\":null}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v3\""));
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPreparationTimeMinutes()).isNull();
    }

    @Test
    void ignoresInjectedPreparationTimePresenceMarker() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        LocationVariantEntity offer = offers.findById(fixture.offerId()).orElseThrow();
        offer.update(offer.getPrice(), offer.isAvailable(), offer.getInventoryMode(), 15, NOW);
        offers.saveAndFlush(offer);
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(owner.jwt()).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":26000,\"preparationTimeMinutesPresent\":true}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v2\""));
        assertThat(offers.findById(fixture.offerId()).orElseThrow().getPreparationTimeMinutes()).isEqualTo(15);
    }

    @Test
    void createsANewVariantAndArchivesTheOldOneInsteadOfChangingCombinations() throws Exception {
        Actor owner = actor(); Fixture fixture = fixtureWithOptions(owner);
        String path = "/api/v1/merchants/" + fixture.merchantId() + "/catalog-items/" + fixture.itemId() + "/variants";
        mockMvc.perform(post(path).with(owner.jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantName\":\"Replacement\",\"optionValueIds\":[\"" + fixture.replacementOptionId() + "\"]}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(header().string("ETag", "\"v0\""));
        mockMvc.perform(delete(variantPath(fixture), fixture.variantId()).with(owner.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        assertThat(variants.countByCatalogItemIdAndDeletedAtIsNull(fixture.itemId())).isEqualTo(1);
    }

    @Test
    void inventoryListDoesNotExposeUnassignedLocationsToStaff() throws Exception {
        Actor staff = actor();
        Fixture fixture = fixture(staff, MerchantRole.STAFF);
        UUID otherLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Other", "2 CoVie", 10.8, 106.7, NOW)).getId();
        UUID otherOffer = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), otherLocation,
                fixture.variantId(), 30000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();
        String path = "/api/v1/merchants/" + fixture.merchantId() + "/inventory";
        mockMvc.perform(get(path).with(staff.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].locationVariantId").value(fixture.offerId().toString()));
        Actor manager = actor();
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), fixture.merchantId(),
                manager.userId(), MerchantRole.MANAGER, NOW));
        mockMvc.perform(get(path).with(manager.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].locationVariantId").value(org.hamcrest.Matchers.hasItem(otherOffer.toString())));
    }

    @Test
    void failedMediaReplacementRollsBackEarlierAttachmentsAndItemEdits() throws Exception {
        Actor owner=actor(); Fixture fixture=fixture(owner,MerchantRole.OWNER);
        var original=readyMedia(owner.userId()); var addition=readyMedia(owner.userId()); var foreign=readyMedia(actor().userId());
        itemMedia.saveAndFlush(CatalogItemMediaEntity.image(items.findById(fixture.itemId()).orElseThrow(),original,0,true));
        mockMvc.perform(patch(itemPath(fixture)).with(owner.jwt()).header("If-Match","\"v0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rejected edit\",\"mediaIds\":[\""+original.getId()+"\",\""+addition.getId()+"\",\""+foreign.getId()+"\"]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getVersion()).isZero();
        assertThat(items.findById(fixture.itemId()).orElseThrow().getName()).isEqualTo("Coffee");
        assertThat(itemMedia.findAllAssociationsByCatalogItemId(fixture.itemId()))
                .extracting(association->association.getId().getMediaAssetId()).containsExactly(original.getId());
        var pending=mediaAssets.saveAndFlush(MediaAssetEntity.pending(UUID.randomUUID(),owner.userId(),"catalog/"+UUID.randomUUID(),
                MediaPurpose.CATALOG_IMAGE,"image/png",8,NOW.plusSeconds(600),NOW));
        var wrongPurpose=MediaAssetEntity.pending(UUID.randomUUID(),owner.userId(),"avatar/"+UUID.randomUUID(),
                MediaPurpose.USER_AVATAR,"image/png",8,NOW.plusSeconds(600),NOW);
        wrongPurpose.markReady(NOW); wrongPurpose=mediaAssets.saveAndFlush(wrongPurpose);
        for(var rejected:java.util.List.of(pending,wrongPurpose)) mockMvc.perform(patch(itemPath(fixture)).with(owner.jwt())
                .header("If-Match","\"v0\"").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[\""+original.getId()+"\",\""+rejected.getId()+"\"]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("MEDIA_NOT_ATTACHABLE"));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getVersion()).isZero();
        mockMvc.perform(patch(itemPath(fixture)).with(owner.jwt()).header("If-Match","\"v0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mediaIds\":[\""+original.getId()+"\",\""+addition.getId()+"\",\""+addition.getId()+"\"]}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag","\"v1\""));
        assertThat(itemMedia.findAllAssociationsByCatalogItemId(fixture.itemId()))
                .extracting(association->association.getId().getMediaAssetId()).containsExactly(original.getId(),addition.getId());
    }

    @Test
    void bulkStockKeepsSuccessfulItemWhenAnotherOfferIsUntracked() throws Exception {
        Actor owner = actor();
        Fixture fixture = fixture(owner, MerchantRole.OWNER);
        UUID trackedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Tracked", "3 CoVie", 10.8, 106.7, NOW)).getId();
        UUID trackedId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), trackedLocation,
                fixture.variantId(), 30000, "VND", true, InventoryMode.TRACKED, null, NOW)).getId();
        inventoryBalances.saveAndFlush(vn.covie.inventory.model.InventoryBalanceEntity.zero(trackedId, NOW));
        UUID reservedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Reserved", "4 CoVie", 10.8, 106.7, NOW)).getId();
        UUID reservedId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), reservedLocation,
                fixture.variantId(), 30000, "VND", true, InventoryMode.TRACKED, null, NOW)).getId();
        var reservedBalance = vn.covie.inventory.model.InventoryBalanceEntity.zero(reservedId, NOW);
        reservedBalance.adjust(10, 7, NOW);
        inventoryBalances.saveAndFlush(reservedBalance);
        String body = "{\"items\":[{\"locationVariantId\":\"" + trackedId + "\",\"stockOnHand\":5},"
                + "{\"locationVariantId\":\"" + fixture.offerId() + "\",\"stockOnHand\":7},"
                + "{\"locationVariantId\":\"" + reservedId + "\",\"stockOnHand\":5}],\"reasonCode\":\"AUDIT_TEST\"}";
        String key = UUID.randomUUID().toString();
        long movementsBefore = inventoryMovements.count();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/merchants/" + fixture.merchantId() + "/inventory/bulk-set-stock")
                            .with(owner.jwt()).header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].success").value(true))
                    .andExpect(jsonPath("$.results[0].stockOnHand").value(5))
                    .andExpect(jsonPath("$.results[1].success").value(false))
                    .andExpect(jsonPath("$.results[1].errorCode").value("INVENTORY_NOT_TRACKED"))
                    .andExpect(jsonPath("$.results[2].success").value(false))
                    .andExpect(jsonPath("$.results[2].errorCode").value("INVENTORY_BELOW_RESERVED_QUANTITY"));
        }
        assertThat(inventoryBalances.findById(trackedId).orElseThrow().getStockOnHand()).isEqualTo(5);
        assertThat(inventoryMovements.count()).isEqualTo(movementsBefore + 1);
        var unchanged = inventoryBalances.findById(reservedId).orElseThrow();
        assertThat(unchanged.getStockOnHand()).isEqualTo(10);
        assertThat(unchanged.getReservedQuantity()).isEqualTo(7);
    }

    @Test
    void staffCannotAdjustStockAtAnUnassignedLocation() throws Exception {
        Actor staff = actor();
        Fixture fixture = fixture(staff, MerchantRole.STAFF);
        UUID otherLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Private", "5 CoVie", 10.8, 106.7, NOW)).getId();
        UUID otherOffer = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), otherLocation,
                fixture.variantId(), 30000, "VND", true, InventoryMode.TRACKED, null, NOW)).getId();
        inventoryBalances.saveAndFlush(vn.covie.inventory.model.InventoryBalanceEntity.zero(otherOffer, NOW));
        long before = inventoryMovements.count();
        for (String operation : List.of("set-stock", "stock-in", "stock-out")) {
            String body = operation.equals("set-stock") ? "{\"stockOnHand\":5}" : "{\"quantity\":5}";
            mockMvc.perform(post("/api/v1/merchants/" + fixture.merchantId() + "/inventory/" + otherOffer + "/" + operation)
                            .with(staff.jwt()).header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        }
        assertThat(inventoryBalances.findById(otherOffer).orElseThrow().getStockOnHand()).isZero();
        assertThat(inventoryMovements.count()).isEqualTo(before);
    }

    @Autowired vn.covie.inventory.persistence.InventoryBalanceRepository inventoryBalances;
    @Autowired vn.covie.inventory.persistence.InventoryMovementRepository inventoryMovements;

    @Test
    void singleStockCommandsReplayWithoutDuplicateLedgerAndRejectExcessWithdrawal() throws Exception {
        Actor owner = actor();
        Fixture fixture = fixture(owner, MerchantRole.OWNER);
        var offer = offers.findById(fixture.offerId()).orElseThrow();
        offer.update(offer.getPrice(), true, InventoryMode.TRACKED, null, NOW);
        offers.saveAndFlush(offer);
        inventoryBalances.saveAndFlush(vn.covie.inventory.model.InventoryBalanceEntity.zero(fixture.offerId(), NOW));
        long before = inventoryMovements.count();
        String prefix = "/api/v1/merchants/" + fixture.merchantId() + "/inventory/" + fixture.offerId();
        String[] operations = {"set-stock", "stock-in", "stock-out"};
        String[] bodies = {"{\"stockOnHand\":5}", "{\"quantity\":3}", "{\"quantity\":2}"};
        int[] expected = {5, 8, 6};
        for (int index = 0; index < operations.length; index++) {
            String key = UUID.randomUUID().toString();
            for (int attempt = 0; attempt < 2; attempt++) {
                mockMvc.perform(post(prefix + "/" + operations[index]).with(owner.jwt())
                                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(bodies[index]))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.stockOnHand").value(expected[index]));
            }
        }
        mockMvc.perform(post(prefix + "/stock-out").with(owner.jwt())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_INVENTORY"));
        assertThat(inventoryBalances.findById(fixture.offerId()).orElseThrow().getStockOnHand()).isEqualTo(6);
        assertThat(inventoryMovements.count()).isEqualTo(before + 3);
    }

    private Fixture fixture(Actor actor, MerchantRole role) {
        UUID merchantId = UUID.randomUUID(); merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "m-" + merchantId, NOW));
        MerchantMembershipEntity membership = memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, actor.userId(), role, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        if (role == MerchantRole.STAFF) assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(), membership.getId(), locationId, NOW));
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID(); items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + itemId, null, NOW));
        UUID variantId = UUID.randomUUID(); variants.saveAndFlush(VariantEntity.createDefault(variantId, itemId, "Default", NOW));
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();
        return new Fixture(merchantId, itemId, variantId, offerId, null);
    }
    private Fixture fixtureWithOptions(Actor actor) {
        Fixture base = fixture(actor, MerchantRole.OWNER);
        OptionGroupEntity group = optionGroups.saveAndFlush(OptionGroupEntity.create(UUID.randomUUID(), base.itemId(), "Size", 0));
        OptionValueEntity small = optionValues.saveAndFlush(OptionValueEntity.create(UUID.randomUUID(), group.getId(), "Small", 0));
        OptionValueEntity large = optionValues.saveAndFlush(OptionValueEntity.create(UUID.randomUUID(), group.getId(), "Large", 1));
        VariantEntity old = variants.findById(base.variantId()).orElseThrow();
        variantOptions.saveAndFlush(VariantOptionValueEntity.associate(old, small));
        return new Fixture(base.merchantId(), base.itemId(), base.variantId(), base.offerId(), large.getId());
    }
    private static String itemPath(Fixture fixture) { return "/api/v1/merchants/" + fixture.merchantId() + "/catalog-items/" + fixture.itemId(); }
    private static String variantPath(Fixture fixture) { return itemPath(fixture) + "/variants/" + fixture.variantId(); }
    private static String offerPath(Fixture fixture) { return itemPath(fixture) + "/location-offers/" + fixture.offerId(); }
    private Actor actor() { UUID userId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID(); Instant now = Instant.now(); users.saveAndFlush(UserEntity.active(userId, "User", now)); sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS))); return new Actor(userId, sessionId); }
    private MediaAssetEntity readyMedia(UUID ownerId) {
        MediaAssetEntity asset = MediaAssetEntity.pending(UUID.randomUUID(), ownerId, "catalog/" + UUID.randomUUID(),
                MediaPurpose.CATALOG_IMAGE, "image/png", 10, NOW.plusSeconds(600), NOW);
        asset.markReady(NOW.plusSeconds(1));
        return mediaAssets.saveAndFlush(asset);
    }
    private record Fixture(UUID merchantId, UUID itemId, UUID variantId, UUID offerId, UUID replacementOptionId) {}
    private record Actor(UUID userId, UUID sessionId) { SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() { return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString()).claim("sessionId", sessionId.toString()).claim("roles", List.of("USER"))).authorities(new SimpleGrantedAuthority("ROLE_USER")); } }
}

