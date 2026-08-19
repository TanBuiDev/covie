package vn.covie.merchants.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MerchantControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository profiles;
    @Autowired MerchantMembershipRepository memberships;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void requiresAuthenticationForMerchantRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/me/merchants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_INVALID"));
    }

    @Test
    void createsMerchantAndListsCurrentActiveMemberships() throws Exception {
        Actor actor = actor("creator");

        mockMvc.perform(post("/api/v1/me/merchants").with(actor.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tạp hóa Minh\",\"description\":\"Local shop\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tạp hóa Minh"))
                .andExpect(jsonPath("$.slug").value("tap-hoa-minh"))
                .andExpect(jsonPath("$.currentUserRole").value("OWNER"));

        mockMvc.perform(get("/api/v1/me/merchants").with(actor.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentUserRole").value("OWNER"));
    }

    @Test
    void exposesPrivateEtagContractAndRejectsStaleOrMissingPreconditions() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);

        mockMvc.perform(get("/api/v1/merchants/{merchantId}", merchantId).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Cache-Control", "private, no-store"));

        mockMvc.perform(patch("/api/v1/merchants/{merchantId}", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"After\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.error.code").value("IF_MATCH_REQUIRED"));

        mockMvc.perform(patch("/api/v1/merchants/{merchantId}", merchantId).with(owner.jwt())
                        .header("If-Match", "\"v99\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"After\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_VERSION_CONFLICT"));

        mockMvc.perform(patch("/api/v1/merchants/{merchantId}", merchantId).with(owner.jwt())
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"After\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.name").value("After"));
    }

    @Test
    void hidesPrivateMerchantFromUnrelatedActorAndForbidsOwnerOnlyMembershipAction() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        Actor unrelated = actor("unrelated");

        mockMvc.perform(get("/api/v1/merchants/{merchantId}", merchantId).with(unrelated.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        Actor manager = actor("manager");
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, manager.userId(), MerchantRole.MANAGER, NOW));
        MerchantMembershipEntity ownerMembership = memberships.findActive(merchantId, owner.userId()).orElseThrow();
        mockMvc.perform(patch("/api/v1/merchants/{merchantId}/members/{memberId}", merchantId, ownerMembership.getId())
                        .with(manager.jwt()).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_ACTION_NOT_ALLOWED"));
    }

    @Test
    void validatesCreateInput() throws Exception {
        Actor actor = actor("validation");

        mockMvc.perform(post("/api/v1/me/merchants").with(actor.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rollsBackLastOwnerRevocation() throws Exception {
        Actor owner = actor("last-owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        MerchantMembershipEntity ownerMembership = memberships.findActive(merchantId, owner.userId()).orElseThrow();

        mockMvc.perform(delete("/api/v1/merchants/{merchantId}/members/{memberId}", merchantId, ownerMembership.getId())
                        .with(owner.jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_LAST_OWNER_REQUIRED"));

        org.assertj.core.api.Assertions.assertThat(memberships.findById(ownerMembership.getId()).orElseThrow()
                .getMembershipStatus()).isEqualTo(vn.covie.merchants.model.MembershipStatus.ACTIVE);
    }

    private UUID merchant(Actor actor, MerchantRole role) {
        UUID id = UUID.randomUUID();
        profiles.saveAndFlush(MerchantProfileEntity.create(id, "Merchant " + id, "merchant-" + id, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), id, actor.userId(), role, NOW));
        return id;
    }

    private Actor actor(String prefix) {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, prefix, now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }

    private record Actor(UUID userId, UUID sessionId) {
        org.springframework.test.web.servlet.request.RequestPostProcessor jwt() {
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString()).claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
