package vn.covie.merchantcatalog;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
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

class CatalogAuthorizationMatrixTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired MerchantMemberLocationRepository assignments;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void jwtDoesNotNeedMerchantClaimsAndStaffCanOnlySeeAssignedLocations() throws Exception {
        Actor owner = actor("owner", false);
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        UUID locationA = location(merchantId, "A");
        UUID locationB = location(merchantId, "B");
        Actor manager = actor("manager", true);
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, manager.userId(), MerchantRole.MANAGER, NOW));
        Actor staffA = actor("staff-a", false);
        MerchantMembershipEntity staffMembership = memberships.saveAndFlush(MerchantMembershipEntity.active(
                UUID.randomUUID(), merchantId, staffA.userId(), MerchantRole.STAFF, NOW));
        assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(), staffMembership.getId(), locationA, NOW));
        Actor staffB = actor("staff-b", false);
        MerchantMembershipEntity staffMembershipB = memberships.saveAndFlush(MerchantMembershipEntity.active(
                UUID.randomUUID(), merchantId, staffB.userId(), MerchantRole.STAFF, NOW));
        assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(), staffMembershipB.getId(), locationB, NOW));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations", merchantId).with(owner.jwt()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations", merchantId).with(manager.jwt()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations", merchantId).with(staffA.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(locationA.toString()));
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations/{locationId}", merchantId, locationB).with(staffA.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations/{locationId}", merchantId, locationA).with(staffB.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void revokingMembershipTakesEffectOnTheNextRequest() throws Exception {
        Actor owner = actor("owner", false);
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        location(merchantId, "Main");

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations", merchantId).with(owner.jwt()))
                .andExpect(status().isOk());

        MerchantMembershipEntity membership = memberships.findActive(merchantId, owner.userId()).orElseThrow();
        membership.revoke();
        memberships.saveAndFlush(membership);

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations", merchantId).with(owner.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unrelatedUserGetsNotFoundForPrivateMerchantResources() throws Exception {
        Actor owner = actor("owner", false);
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        UUID locationId = location(merchantId, "Main");
        Actor unrelated = actor("unrelated", false);

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations/{locationId}", merchantId, locationId).with(unrelated.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private UUID merchant(Actor actor, MerchantRole role) {
        UUID id = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(id, "Merchant " + id, "merchant-" + id, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), id, actor.userId(), role, NOW));
        return id;
    }

    private UUID location(UUID merchantId, String name) {
        return locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(), merchantId, name, "1 CoVie", 10.8, 106.7, NOW)).getId();
    }

    private Actor actor(String prefix, boolean includeRolesClaim) {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, prefix, now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId, includeRolesClaim);
    }

    private record Actor(UUID userId, UUID sessionId, boolean includeRolesClaim) {
        SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString())
                    .claim("roles", List.of("USER"))).authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
