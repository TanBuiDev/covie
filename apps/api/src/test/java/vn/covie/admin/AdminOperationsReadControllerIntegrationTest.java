package vn.covie.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
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
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class AdminOperationsReadControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository catalog;
    @Autowired NeedRequestRepository needs;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void adminReadsFilteredOperationalCollectionsWithoutBuyerIdentity() throws Exception {
        Actor admin = actor("admin");
        Actor buyer = actor("buyer");
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Air Shop", "air-shop-" + merchantId, NOW));
        locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId,
                "Branch One", "1 CoVie", 10.8, 106.7, NOW));
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Air care",
                "air-care-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        catalog.saveAndFlush(CatalogItemEntity.create(UUID.randomUUID(), merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, "Air purifier", "air-purifier-" + UUID.randomUUID(), null, NOW));
        var point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(106.7, 10.8));
        point.setSRID(4326);
        needs.saveAndFlush(NeedRequestEntity.create(UUID.randomUUID(), buyer.userId(), NeedType.PRODUCT, categoryId,
                "Need an air purifier", "air purifier", null, 1L, null, point,
                JsonNodeFactory.instance.objectNode().put("label", "District"), 5_000,
                NOW.plus(7, ChronoUnit.DAYS), NOW));

        mockMvc.perform(get("/api/v1/admin/shops").param("q", "branch").with(admin.jwt("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].merchantName").value("Air Shop"))
                .andExpect(jsonPath("$.items[0].locationName").value("Branch One"));
        mockMvc.perform(get("/api/v1/admin/listings").param("type", "PRODUCT").param("q", "purifier")
                        .with(admin.jwt("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].merchantName").value("Air Shop"));
        mockMvc.perform(get("/api/v1/admin/needs").param("status", "ACTIVE").param("q", "purifier")
                        .with(admin.jwt("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Need an air purifier"))
                .andExpect(jsonPath("$.items[0].buyerUserId").doesNotExist())
                .andExpect(jsonPath("$.items[0].locationSnapshot").doesNotExist());
    }

    @Test
    void ordinaryUserCannotReadOperationalCollections() throws Exception {
        Actor user = actor("user");
        for (String path : List.of("/api/v1/admin/shops", "/api/v1/admin/listings", "/api/v1/admin/needs")) {
            mockMvc.perform(get(path).with(user.jwt("USER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ADMIN_ROLE_REQUIRED"));
        }
    }

    private Actor actor(String prefix) {
        UUID userId = UUID.randomUUID(), sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, prefix, now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now,
                now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }

    private record Actor(UUID userId, UUID sessionId) {
        SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String role) {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString()).claim("roles", List.of(role)))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role));
        }
    }
}
