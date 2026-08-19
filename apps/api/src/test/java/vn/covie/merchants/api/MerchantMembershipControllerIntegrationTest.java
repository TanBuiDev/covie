package vn.covie.merchants.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;

class MerchantMembershipControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository profiles;
    @Autowired MerchantMembershipRepository memberships;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void requiresAuthenticationForMemberCollectionRoutes() throws Exception {
        UUID merchantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/members", merchantId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_INVALID"));

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/members", merchantId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@covie.vn\",\"role\":\"STAFF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
    }

    @Test
    void listsActiveMembersAndHidesThemFromUnrelatedActors() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        Actor staff = actor("staff");
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, staff.userId(), MerchantRole.STAFF, NOW));
        Actor unrelated = actor("unrelated");

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/members", merchantId).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].role").value("STAFF"));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/members", merchantId).with(unrelated.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void addsPreRegisteredLocalEmailMemberWithCreatedEtag() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        Actor member = actor("member");
        identities.saveAndFlush(UserIdentityEntity.email(UUID.randomUUID(), member.userId(), "member@covie.vn", "hash", NOW));

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/members", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@covie.vn\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(jsonPath("$.userId").value(member.userId().toString()))
                .andExpect(jsonPath("$.email").value("member@covie.vn"))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(memberships.findActive(merchantId, member.userId())).isPresent();
    }

    @Test
    void rejectsInvalidAndUnknownMemberAddsWithDocumentedErrors() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/members", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\",\"role\":\"STAFF\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/members", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"missing@covie.vn\",\"role\":\"STAFF\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_MEMBER_USER_NOT_FOUND"));
    }

    @Test
    void forbidsManagerFromAddingAnOwner() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        Actor manager = actor("manager");
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, manager.userId(), MerchantRole.MANAGER, NOW));
        Actor candidate = actor("candidate");
        identities.saveAndFlush(UserIdentityEntity.email(UUID.randomUUID(), candidate.userId(), "candidate@covie.vn", "hash", NOW));

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/members", merchantId).with(manager.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"candidate@covie.vn\",\"role\":\"OWNER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_ACTION_NOT_ALLOWED"));
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
            return SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString()).claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
