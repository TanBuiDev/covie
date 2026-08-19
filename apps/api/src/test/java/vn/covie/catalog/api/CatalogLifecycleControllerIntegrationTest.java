package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryCatalogType;
import vn.covie.catalog.model.CategoryEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
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

class CatalogLifecycleControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users; @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants; @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations; @Autowired MerchantMemberLocationRepository assignments; @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items; @Autowired VariantRepository variants; @Autowired LocationVariantRepository offers;
    private MockMvc mockMvc;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    @Test
    void exposesOnlyCommandsAndReturnsTheFreshItemEtag() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        mockMvc.perform(post(path(fixture, "publish")).with(owner.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        mockMvc.perform(post(path(fixture, "pause")).with(owner.jwt()).header("If-Match", "\"v1\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v2\""));
        mockMvc.perform(post(path(fixture, "activate")).with(owner.jwt()).header("If-Match", "\"v2\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v3\""));
        mockMvc.perform(post(path(fixture, "archive")).with(owner.jwt()).header("If-Match", "\"v3\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v4\""));
        assertThat(items.findById(fixture.itemId()).orElseThrow().getListingStatus()).isEqualTo(ListingStatus.DELETED);
    }

    @Test
    void enforcesCurrentMembershipAndRejectsInvalidCommandVersionsAndStates() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.OWNER);
        mockMvc.perform(post(path(fixture, "pause")).with(owner.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CATALOG_LIFECYCLE_INVALID"));
        mockMvc.perform(post(path(fixture, "publish")).with(owner.jwt()).header("If-Match", "\"v5\""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RESOURCE_VERSION_CONFLICT"));
        MerchantMembershipEntity membership = memberships.findActive(fixture.merchantId(), owner.userId()).orElseThrow();
        membership.changeRole(MerchantRole.STAFF); memberships.saveAndFlush(membership);
        assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(), membership.getId(), fixture.locationId(), NOW));
        mockMvc.perform(post(path(fixture, "publish")).with(owner.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MERCHANT_ACTION_NOT_ALLOWED"));
    }

    @Test
    void returnsNotFoundForAnItemOutsideTheMerchantScope() throws Exception {
        Actor owner = actor(); Fixture fixture = fixture(owner, MerchantRole.MANAGER);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish", fixture.merchantId(), UUID.randomUUID())
                        .with(owner.jwt()).header("If-Match", "\"v0\""))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private Fixture fixture(Actor actor, MerchantRole role) {
        UUID merchantId = UUID.randomUUID(); merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, actor.userId(), role, NOW));
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId, "Main", "1 CoVie", 10.8, 106.7, NOW)).getId();
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Food", "food-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID(); items.saveAndFlush(CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Coffee", "coffee-" + itemId, null, NOW));
        UUID variantId = variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(), itemId, "Default", NOW)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId, 25000, "VND", true, InventoryMode.UNTRACKED, null, NOW));
        return new Fixture(merchantId, itemId, locationId);
    }

    private static String path(Fixture fixture, String command) { return "/api/v1/merchants/" + fixture.merchantId() + "/catalog-items/" + fixture.itemId() + "/" + command; }
    private Actor actor() {
        UUID userId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID(); Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, "Actor", now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }
    private record Fixture(UUID merchantId, UUID itemId, UUID locationId) {}
    private record Actor(UUID userId, UUID sessionId) { SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() { return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString()).claim("sessionId", sessionId.toString()).claim("roles", List.of("USER"))).authorities(new SimpleGrantedAuthority("ROLE_USER")); } }
}
