package vn.covie.merchants.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantLocationStatus;
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

class MerchantLocationControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository profiles;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired MerchantMemberLocationRepository assignments;
    private MockMvc mockMvc;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    @Test
    void createsLocationWithLongitudeFirstGeometryAndRequiresValidVndTimezoneContract() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/locations", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(locationJson("Downtown", "Asia/Ho_Chi_Minh", "VND")))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"v0\""))
                .andExpect(jsonPath("$.latitude").value(10.8)).andExpect(jsonPath("$.longitude").value(106.7));

        MerchantLocationEntity saved = locations.findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(
                merchantId).getFirst();
        assertThat(saved.getGeoPoint().getX()).isEqualTo(106.7);
        assertThat(saved.getGeoPoint().getY()).isEqualTo(10.8);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/locations", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(locationJson("Bad zone", "Not/AZone", "VND")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/locations", merchantId).with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(locationJson("Bad currency", "UTC", "USD")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void staffListContainsOnlyExplicitAssignmentsAndUnassignedDetailIsPrivate() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        MerchantLocationEntity assigned = location(merchantId, "Assigned");
        MerchantLocationEntity hidden = location(merchantId, "Hidden");
        locations.saveAllAndFlush(List.of(assigned, hidden));
        Actor staff = actor("staff");
        MerchantMembershipEntity membership = memberships.saveAndFlush(MerchantMembershipEntity.active(
                UUID.randomUUID(), merchantId, staff.userId(), MerchantRole.STAFF, NOW));
        assignments.saveAndFlush(membership.assignTo(new vn.covie.shared.id.UuidV7Generator(), assigned.getId(), NOW));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations", merchantId).with(staff.jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(assigned.getId().toString()));
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/locations/{locationId}", merchantId, hidden.getId())
                        .with(staff.jwt()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void patchRequiresMatchingVersionDeleteIsSoftAndStaffAssignmentRejectsDuplicatesAndManagers() throws Exception {
        Actor owner = actor("owner");
        UUID merchantId = merchant(owner, MerchantRole.OWNER);
        MerchantLocationEntity target = locations.saveAndFlush(location(merchantId, "Target"));

        mockMvc.perform(patch("/api/v1/merchants/{merchantId}/locations/{locationId}", merchantId, target.getId())
                        .with(owner.jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(locationJson("Updated", "UTC", "VND")))
                .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.error.code").value("IF_MATCH_REQUIRED"));
        mockMvc.perform(patch("/api/v1/merchants/{merchantId}/locations/{locationId}", merchantId, target.getId())
                        .with(owner.jwt()).header("If-Match", "\"v0\"").contentType(MediaType.APPLICATION_JSON)
                        .content(locationJson("Updated", "UTC", "VND")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"v1\""));

        Actor managerActor = actor("manager");
        MerchantMembershipEntity manager = memberships.saveAndFlush(MerchantMembershipEntity.active(
                UUID.randomUUID(), merchantId, managerActor.userId(), MerchantRole.MANAGER, NOW));
        mockMvc.perform(put("/api/v1/merchants/{merchantId}/members/{memberId}/locations", merchantId, manager.getId())
                        .with(owner.jwt()).header("If-Match", "\"v0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationIds\":[\"" + target.getId() + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MEMBERSHIP_ROLE_NOT_LOCATION_SCOPED"));

        Actor staffActor = actor("staff");
        MerchantMembershipEntity staff = memberships.saveAndFlush(MerchantMembershipEntity.active(
                UUID.randomUUID(), merchantId, staffActor.userId(), MerchantRole.STAFF, NOW));
        mockMvc.perform(put("/api/v1/merchants/{merchantId}/members/{memberId}/locations", merchantId, staff.getId())
                        .with(owner.jwt()).header("If-Match", "\"v0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationIds\":[\"" + target.getId() + "\",\"" + target.getId() + "\"]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(put("/api/v1/merchants/{merchantId}/members/{memberId}/locations", merchantId, staff.getId())
                        .with(owner.jwt()).header("If-Match", "\"v0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationIds\":[\"" + target.getId() + "\"]}"))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"v1\""));
        assertThat(assignments.existsByMembershipIdAndMerchantLocationId(staff.getId(), target.getId())).isTrue();

        mockMvc.perform(delete("/api/v1/merchants/{merchantId}/locations/{locationId}", merchantId, target.getId())
                        .with(owner.jwt())).andExpect(status().isNoContent());
        assertThat(locations.findById(target.getId())).get().extracting(MerchantLocationEntity::getLocationStatus)
                .isEqualTo(MerchantLocationStatus.DELETED);
    }

    private String locationJson(String name, String timezone, String currency) {
        return "{\"name\":\"" + name + "\",\"addressText\":\"1 CoVie Street\",\"latitude\":10.8,"
                + "\"longitude\":106.7,\"pinSource\":\"GPS\",\"timezone\":\"" + timezone + "\","
                + "\"pickupEnabled\":true,\"sellerDeliveryEnabled\":true,\"serviceEnabled\":false,"
                + "\"minimumDeliveryOrder\":10000,\"currency\":\"" + currency + "\"}";
    }

    private MerchantLocationEntity location(UUID merchantId, String name) {
        return MerchantLocationEntity.create(UUID.randomUUID(), merchantId, name, "Address", 10.8, 106.7, NOW);
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
