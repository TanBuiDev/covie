package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.SystemRoleAssignmentEntity;

@AutoConfigureMockMvc
class AdminAdAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired vn.covie.merchants.persistence.MerchantProfileRepository merchants;
    @Autowired vn.covie.merchants.persistence.MerchantMembershipRepository memberships;

    @Test
    void publicDeliveryTokensDeduplicateClicksAndRejectUnknownTokens() throws Exception {
        var admin = activeUser("Public ads operator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(), admin.getId(), SystemRole.ADMIN, Instant.now()));
        String bearer = "Bearer " + issuer.issue(admin.getId(), device()).accessToken().value();
        String body = """
                {"title":"Public campaign","ctaLabel":"View","targetUrl":"/search",
                "creativeType":"CSS","template":"FLASH","placement":"IN_FEED","device":"ALL",
                "startsAt":"%s","endsAt":"%s","enabled":true,"priority":1000}
                """.formatted(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        var created = mvc.perform(post("/api/v1/admin/ads").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String id = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        var delivered = mvc.perform(get("/api/v1/ads/active").param("placement", "IN_FEED").param("device", "MOBILE"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        com.fasterxml.jackson.databind.JsonNode selected = null;
        for (var row : mapper.readTree(delivered.getResponse().getContentAsString())) {
            if (row.path("id").asText().equals(id)) selected = row;
        }
        assertThat(selected).isNotNull();
        assertThat(selected.path("title").asText()).isEqualTo("Public campaign");
        assertThat(selected.has("merchantId")).isFalse();
        String token = selected.path("deliveryToken").asText();
        assertThat(token).isNotBlank();
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getCookie("COVIE-XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        String event = "{\"deliveryToken\":\"" + token + "\",\"eventId\":\"" + UUID.randomUUID()
                + "\",\"type\":\"CLICK\",\"visibleMillis\":0}";
        var otherCreated = mvc.perform(post("/api/v1/admin/ads").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("Public campaign", "Other campaign")))
                .andExpect(status().isCreated()).andReturn();
        String otherId = mapper.readTree(otherCreated.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/ads/" + otherId + "/events")
                
                .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from ad_events where campaign_id in (?,?)", Long.class,
                UUID.fromString(id), UUID.fromString(otherId))).isZero();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/ads/" + id + "/events")
                    
                    .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForObject("select count(*) from ad_events where campaign_id=?", Long.class, UUID.fromString(id))).isEqualTo(1);
        String impression = "{\"deliveryToken\":\"" + token + "\",\"eventId\":\"" + UUID.randomUUID()
                + "\",\"type\":\"IMPRESSION\",\"visibleMillis\":999}";
        mvc.perform(post("/api/v1/ads/" + id + "/events")
                
                .contentType(MediaType.APPLICATION_JSON).content(impression)).andExpect(status().isUnprocessableEntity());
        jdbc.update("update ad_deliveries set issued_at=? where id=?", java.sql.Timestamp.from(Instant.now().minusSeconds(5)), UUID.fromString(token));
        impression = impression.replace("\"visibleMillis\":999", "\"visibleMillis\":1000");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/ads/" + id + "/events")
                    
                    .contentType(MediaType.APPLICATION_JSON).content(impression)).andExpect(status().isNoContent());
        }
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        mvc.perform(get("/api/v1/admin/ads/" + id + "/stats").header("Authorization", bearer).param("from", today).param("to", today))
                .andExpect(status().isOk()).andExpect(jsonPath("$.impressions").value(1))
                .andExpect(jsonPath("$.clicks").value(1)).andExpect(jsonPath("$.ctr").value(100.0));
        jdbc.update("update ad_deliveries set expires_at=? where id=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), UUID.fromString(token));
        mvc.perform(post("/api/v1/ads/" + id + "/events")
                
                .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from ad_events where campaign_id=?", Long.class, UUID.fromString(id))).isEqualTo(2);
        mvc.perform(post("/api/v1/ads/" + id + "/events")
                
                .contentType(MediaType.APPLICATION_JSON).content(event.replace(token, UUID.randomUUID().toString())))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from ad_events where campaign_id=?", Long.class, UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void sellerCampaignsRemainScopedToTheirMerchantAndRequireAdminReview() throws Exception {
        var seller = activeUser("Ads owner");
        var now = Instant.now();
        var merchant = merchants.saveAndFlush(vn.covie.merchants.model.MerchantProfileEntity.create(
                UUID.randomUUID(), "Ads store", "ads-" + UUID.randomUUID(), now));
        var other = merchants.saveAndFlush(vn.covie.merchants.model.MerchantProfileEntity.create(
                UUID.randomUUID(), "Other store", "ads-" + UUID.randomUUID(), now));
        for (var store : java.util.List.of(merchant, other)) {
            memberships.saveAndFlush(vn.covie.merchants.model.MerchantMembershipEntity.active(UUID.randomUUID(),
                    store.getId(), seller.getId(), vn.covie.merchants.model.MerchantRole.OWNER, now));
        }
        String bearer = "Bearer " + issuer.issue(seller.getId(), device()).accessToken().value();
        String base = "/api/v1/merchants/" + merchant.getId() + "/ads";
        String body = """
                {"title":"Seller campaign","ctaLabel":"View","targetUrl":"/merchants/%s",
                "creativeType":"CSS","template":"FLASH","placement":"IN_FEED","device":"ALL",
                "startsAt":"%s","endsAt":"%s","enabled":true,"priority":2}
                """.formatted(merchant.getId(), now.minusSeconds(60), now.plusSeconds(3600));
        var created = mvc.perform(post(base).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();
        String etag = created.getResponse().getHeader("ETag");
        mvc.perform(delete("/api/v1/merchants/" + other.getId() + "/ads/" + id)
                .header("Authorization", bearer).header("If-Match", etag)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/ads/" + id + "/review").header("Authorization", bearer)
                .header("If-Match", etag).contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
        var submitted = mvc.perform(post(base + "/" + id + "/submit").header("Authorization", bearer).header("If-Match", etag))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        var admin = activeUser("Ads reviewer");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(), admin.getId(), SystemRole.ADMIN, now));
        String adminBearer = "Bearer " + issuer.issue(admin.getId(), device()).accessToken().value();
        mvc.perform(post("/api/v1/admin/ads/" + id + "/review").header("Authorization", adminBearer)
                .header("If-Match", submitted.getResponse().getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        var outsider = activeUser("Ads outsider");
        String outsiderBearer = "Bearer " + issuer.issue(outsider.getId(), device()).accessToken().value();
        mvc.perform(get(base).header("Authorization", outsiderBearer)).andExpect(status().isNotFound());
        jdbc.update("update merchant_memberships set membership_status='REVOKED' where merchant_profile_id=? and user_id=?",
                merchant.getId(), seller.getId());
        mvc.perform(get(base).header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(post(base).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/merchants/" + other.getId() + "/ads").header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    @Test
    void realAdminCampaignCommandsEnforceAccessAndVersionPreconditions() throws Exception {
        var admin = activeUser("Ads administrator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(), admin.getId(), SystemRole.ADMIN, Instant.now()));
        String bearer = "Bearer " + issuer.issue(admin.getId(), device()).accessToken().value();
        var buyer = activeUser("Ads buyer");
        String buyerBearer = "Bearer " + issuer.issue(buyer.getId(), device()).accessToken().value();
        String body = """
                {"title":"Campaign acceptance","body":"Sale","ctaLabel":"View","targetUrl":"/search",
                "creativeType":"CSS","template":"FLASH","placement":"IN_FEED","device":"ALL",
                "startsAt":"%s","endsAt":"%s","enabled":true,"priority":2}
                """.formatted(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        mvc.perform(post("/api/v1/admin/ads").header("Authorization", buyerBearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        var created = mvc.perform(post("/api/v1/admin/ads").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Campaign acceptance")).andReturn();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();
        String path = "/api/v1/admin/ads/" + id;
        String etag = created.getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();
        mvc.perform(patch(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(428)).andExpect(jsonPath("$.error.code").value("IF_MATCH_REQUIRED"));
        var updated = mvc.perform(patch(path).header("Authorization", bearer).header("If-Match", etag)
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("Campaign acceptance", "Campaign edited")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Campaign edited")).andReturn();
        mvc.perform(delete(path).header("Authorization", bearer).header("If-Match", etag))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RESOURCE_VERSION_CONFLICT"));
        mvc.perform(delete(path).header("Authorization", buyerBearer).header("If-Match", updated.getResponse().getHeader("ETag")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path).header("Authorization", bearer).header("If-Match", updated.getResponse().getHeader("ETag")))
                .andExpect(status().isNoContent());
        var listed = mvc.perform(get("/api/v1/admin/ads").header("Authorization", bearer)).andExpect(status().isOk()).andReturn();
        assertThat(listed.getResponse().getContentAsString()).doesNotContain(id);
    }
}
