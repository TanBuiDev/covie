package vn.covie.merchants.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
import vn.covie.merchants.model.ContactStatus;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantContactMethodRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MerchantContactControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository profiles;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired MerchantMemberLocationRepository assignments;
    @Autowired MerchantContactMethodRepository contacts;
    private MockMvc mockMvc;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    @Test
    void promotingPrimaryDemotesPreviousContactInTheSameMerchantScope() throws Exception {
        Actor owner = actor("owner"); UUID merchantId = merchant(owner, MerchantRole.OWNER);
        String first = mockMvc.perform(post("/api/v1/merchants/{merchantId}/contacts", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(phone("0900000001", true, null)))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"v0\""))
                .andReturn().getResponse().getContentAsString();
        UUID firstId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).get("id").asText());

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/contacts", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(phone("0900000002", true, null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.primary").value(true));

        assertThat(contacts.findById(firstId)).get().satisfies(contact -> assertThat(contact.isPrimary()).isFalse());
    }

    @Test
    void patchRequiresIfMatchAndLocationContactCannotCrossMerchantBoundary() throws Exception {
        Actor owner = actor("owner"); UUID merchantId = merchant(owner, MerchantRole.OWNER);
        String created = mockMvc.perform(post("/api/v1/merchants/{merchantId}/contacts", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(phone("0900000001", false, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID contactId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/contacts/{contactId}", merchantId, contactId).with(owner.jwt()))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"v0\""))
                .andExpect(jsonPath("$.value").value("0900000001"));

        mockMvc.perform(patch("/api/v1/merchants/{merchantId}/contacts/{contactId}", merchantId, contactId)
                        .with(owner.jwt()).contentType(MediaType.APPLICATION_JSON).content(phone("0900000002", false, null)))
                .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.error.code").value("IF_MATCH_REQUIRED"));
        mockMvc.perform(patch("/api/v1/merchants/{merchantId}/contacts/{contactId}", merchantId, contactId)
                        .with(owner.jwt()).header("If-Match", "\"v0\"").contentType(MediaType.APPLICATION_JSON)
                        .content(phone("0900000002", false, null)))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"v1\""));
        mockMvc.perform(patch("/api/v1/merchants/{merchantId}/contacts/{contactId}", merchantId, contactId)
                        .with(owner.jwt()).header("If-Match", "\"v0\"").contentType(MediaType.APPLICATION_JSON)
                        .content(phone("0900000003", false, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RESOURCE_VERSION_CONFLICT"));
        assertThat(contacts.findById(contactId)).get().satisfies(contact ->
                assertThat(contact.getValue()).isEqualTo("0900000002"));

        Actor other = actor("other"); UUID otherMerchantId = merchant(other, MerchantRole.OWNER);
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/contacts/{contactId}", otherMerchantId, contactId)
                        .with(other.jwt()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        MerchantLocationEntity foreign = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),
                otherMerchantId, "Foreign", "Address", 10, 106, NOW));
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/contacts", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(phone("0900000003", false, foreign.getId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assignedStaffSeesMerchantButGetsForbiddenForPrivateContactManagement() throws Exception {
        Actor owner = actor("owner"); UUID merchantId = merchant(owner, MerchantRole.OWNER);
        MerchantLocationEntity location = locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),
                merchantId, "Assigned", "Address", 10, 106, NOW));
        Actor staffActor = actor("staff");
        MerchantMembershipEntity staff = memberships.saveAndFlush(MerchantMembershipEntity.active(
                UUID.randomUUID(), merchantId, staffActor.userId(), MerchantRole.STAFF, NOW));
        assignments.saveAndFlush(staff.assignTo(new UuidV7Generator(), location.getId(), NOW));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/contacts", merchantId).with(staffActor.jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_ACTION_NOT_ALLOWED"));
    }

    private String phone(String value, boolean primary, UUID locationId) {
        String location = locationId == null ? "" : ",\"merchantLocationId\":\"" + locationId + "\"";
        return "{\"contactType\":\"PHONE\",\"value\":\"" + value + "\",\"primary\":" + primary
                + ",\"publicContact\":true" + location + "}";
    }
    private UUID merchant(Actor actor, MerchantRole role) {
        UUID id = UUID.randomUUID(); profiles.saveAndFlush(MerchantProfileEntity.create(id, "Merchant", "m-" + id, NOW));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), id, actor.userId(), role, NOW)); return id;
    }
    private Actor actor(String prefix) {
        UUID userId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID(); Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, prefix, now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now,
                now.plus(30, ChronoUnit.DAYS))); return new Actor(userId, sessionId);
    }
    private record Actor(UUID userId, UUID sessionId) {
        org.springframework.test.web.servlet.request.RequestPostProcessor jwt() {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString()).claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
