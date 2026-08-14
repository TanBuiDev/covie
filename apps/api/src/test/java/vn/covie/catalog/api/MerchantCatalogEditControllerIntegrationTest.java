package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.OptionGroupEntity;
import vn.covie.catalog.model.OptionValueEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantOptionValueEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
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
    void keepsStructuralEditingForbiddenToStaffButAllowsTheirAssignedOffer() throws Exception {
        Actor staff = actor(); Fixture fixture = fixture(staff, MerchantRole.STAFF);
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).with(staff.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MERCHANT_ACTION_NOT_ALLOWED"));
        mockMvc.perform(patch(offerPath(fixture), fixture.offerId()).with(staff.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":31000}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        UUID unassignedLocation = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), fixture.merchantId(),
                "Unassigned", "2 CoVie", 10.8, 106.7, NOW)).getId();
        UUID unassignedOffer = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), unassignedLocation,
                fixture.variantId(), 25000, "VND", true, InventoryMode.UNTRACKED, null, NOW)).getId();
        mockMvc.perform(patch(itemPath(fixture) + "/location-offers/" + unassignedOffer).with(staff.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":32000}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
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
        UUID requiredCategory = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Required", "required-" + UUID.randomUUID(),
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 1)).getId();
        categoryAttributes.saveAndFlush(CategoryAttributeEntity.create(UUID.randomUUID(), requiredCategory, "Brand", "brand",
                CategoryAttributeType.TEXT, true, false, 0));
        mockMvc.perform(patch(itemPath(fixture), fixture.itemId()).with(owner.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"primaryCategoryId\":\"" + requiredCategory + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_ATTRIBUTE_INVALID"));
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
    private record Fixture(UUID merchantId, UUID itemId, UUID variantId, UUID offerId, UUID replacementOptionId) {}
    private record Actor(UUID userId, UUID sessionId) { SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() { return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString()).claim("sessionId", sessionId.toString()).claim("roles", List.of("USER"))).authorities(new SimpleGrantedAuthority("ROLE_USER")); } }
}
