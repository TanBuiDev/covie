package vn.covie.needs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
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
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.model.PinSource;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;
import org.springframework.http.MediaType;

class NeedEligibleItemsControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired NeedRequestRepository needs;
    @Autowired JdbcTemplate jdbc;
    @Autowired vn.covie.notifications.persistence.NeedAudienceRepository audience;
    @Autowired vn.covie.auth.application.InitialSessionIssuer issuer;
    @Autowired vn.covie.needs.persistence.NeedResponseRepository responseRepository;
    private MockMvc mockMvc;

    @Test
    void signedBuyerTraversesResponseTimestampTiesThroughEncodedHttpCursor() throws Exception {
        var fixture = visibleFixture();
        UUID buyerId = needs.findById(fixture.needId()).orElseThrow().getBuyerUserId();
        String bearer = "Bearer " + issuer.issue(buyerId,
                new vn.covie.auth.model.DeviceMetadata("Buyer browser", "need-pagination")).accessToken().value();
        var timestamp = Instant.parse("2026-10-01T00:00:00Z");
        for (int index = 0; index < 5; index++) {
            var location = locations.saveAndFlush(location(fixture.merchantId(), "Response " + index, true));
            var response = vn.covie.needs.model.NeedResponseEntity.active(UUID.randomUUID(), fixture.needId(),
                    fixture.merchantId(), location.getId(), fixture.seller().userId(), null, timestamp);
            if (index == 4) response.withdraw(timestamp);
            responseRepository.saveAndFlush(response);
        }
        var expected = jdbc.queryForList("select id::text from need_responses where need_request_id=? and response_status='ACTIVE' order by created_at desc,id desc",
                String.class, fixture.needId());
        var observed = new java.util.ArrayList<String>();
        String cursor = null;
        for (int page = 0; page < 2; page++) {
            var request = get("/api/v1/me/need-requests/{needId}/responses", fixture.needId())
                    .header("Authorization", bearer).queryParam("limit", "2");
            if (cursor != null) request.queryParam("cursor", cursor);
            var result = mockMvc.perform(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.hasMore").value(page == 0)).andReturn();
            var json = new ObjectMapper().readTree(result.getResponse().getContentAsString());
            json.path("items").forEach(row -> observed.add(row.path("id").asText()));
            cursor = json.path("nextCursor").asText(null);
        }
        assertThat(observed).containsExactlyElementsOf(expected);
        assertThat(cursor).isNull();
    }

    @Test
    void signedSellerTokenSupportsCandidateAndSelectedLocationResponseRoutes() throws Exception {
        var fixture = visibleFixture();
        String bearer = "Bearer " + issuer.issue(fixture.seller().userId(),
                new vn.covie.auth.model.DeviceMetadata("Need browser", "need-http-test")).accessToken().value();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId()).header("Authorization", bearer)
                .queryParam("locationId", fixture.locationId().toString()).queryParam("limit", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId()).header("Authorization", bearer)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchantLocationId\":\"" + fixture.locationId() + "\",\"locationVariantIds\":[\"" + fixture.offerId() + "\"]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/need-requests/{needId}/response", fixture.merchantId(), fixture.needId())
                .header("Authorization", bearer).queryParam("locationId", fixture.locationId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.merchantLocationId").value(fixture.locationId().toString()));
        UUID buyerId = needs.findById(fixture.needId()).orElseThrow().getBuyerUserId();
        String buyerBearer = "Bearer " + issuer.issue(buyerId,
                new vn.covie.auth.model.DeviceMetadata("Buyer browser", "need-http-test")).accessToken().value();
        mockMvc.perform(get("/api/v1/me/need-requests/{needId}/responses", fixture.needId())
                .header("Authorization", buyerBearer).queryParam("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items[0].merchantLocationId").value(fixture.locationId().toString()))
                .andExpect(jsonPath("$.items[0].items[0].locationVariantId").value(fixture.offerId().toString()));
        var outsider = actor();
        String outsiderBearer = "Bearer " + issuer.issue(outsider.userId(),
                new vn.covie.auth.model.DeviceMetadata("Other browser", "need-http-test")).accessToken().value();
        mockMvc.perform(get("/api/v1/me/need-requests/{needId}/responses", fixture.needId())
                .header("Authorization", outsiderBearer)).andExpect(status().isNotFound());
    }

    @Test
    void needReportsRequireAuthorizedVisibleMerchantContextAndReplayOneCase() throws Exception {
        var json = new ObjectMapper();
        var fixture = visibleFixture(); var other = visibleFixture(); var outsider = actor();
        String seller = "Bearer " + issuer.issue(fixture.seller().userId(), new vn.covie.auth.model.DeviceMetadata("Reporter", "need-report")).accessToken().value();
        String foreign = "Bearer " + issuer.issue(outsider.userId(), new vn.covie.auth.model.DeviceMetadata("Other reporter", "need-report")).accessToken().value();
        String body = json.writeValueAsString(java.util.Map.of("targetType", "NEED_REQUEST", "targetId", fixture.needId(), "contextMerchantId", fixture.merchantId(), "reasonCode", "AUDIT_TEST"));
        mockMvc.perform(post("/api/v1/reports").header("Authorization", foreign).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/reports").header("Authorization", seller).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("targetType", "NEED_REQUEST", "targetId", fixture.needId(), "contextMerchantId", other.merchantId(), "reasonCode", "AUDIT_TEST"))))
                .andExpect(status().isNotFound());
        String key = UUID.randomUUID().toString(), first = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            var result = mockMvc.perform(post("/api/v1/reports").header("Authorization", seller).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
            if (first == null) first = result.getResponse().getContentAsString(); else assertThat(result.getResponse().getContentAsString()).isEqualTo(first);
        }
        assertThat(json.readTree(first).properties().stream().map(java.util.Map.Entry::getKey).toList()).containsOnly("caseId", "status");
        UUID caseId = UUID.fromString(json.readTree(first).path("caseId").asText());
        jdbc.update("update need_requests set moderation_status='HIDDEN' where id=?", fixture.needId());
        mockMvc.perform(post("/api/v1/reports").header("Authorization", seller).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_need_request_id=?", Long.class, fixture.needId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select reporter_user_id from moderation_cases where id=?", UUID.class, caseId)).isEqualTo(fixture.seller().userId());
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, caseId)).isEqualTo(1L);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void requiresTheSelectedLocationQueryParameter() throws Exception {
        Actor seller = actor();
        mockMvc.perform(get(path(), UUID.randomUUID(), UUID.randomUUID()).with(seller.jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnonymousCandidateReads() throws Exception {
        mockMvc.perform(get(path(), UUID.randomUUID(), UUID.randomUUID())
                        .queryParam("locationId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hidesForeignMerchantAndLocationBehindNotFound() throws Exception {
        Actor seller = actor();
        mockMvc.perform(get(path(), UUID.randomUUID(), UUID.randomUUID())
                        .queryParam("locationId", UUID.randomUUID().toString()).with(seller.jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesAnExistingMerchantNeedFromANonMember() throws Exception {
        Fixture fixture = visibleFixture();
        Actor stranger = actor();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(stranger.jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesAnExistingMerchantNeedAfterMembershipRevocation() throws Exception {
        Fixture fixture = visibleFixture();
        var membership = memberships.findActive(fixture.merchantId(), fixture.seller().userId()).orElseThrow();
        membership.revoke();
        memberships.saveAndFlush(membership);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesModerationPendingNeedBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.requireModeration(Instant.now());
        needs.saveAndFlush(need);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesModerationRejectedNeedBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.rejectModeration(Instant.now());
        needs.saveAndFlush(need);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesCancelledNeedBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.cancel(Instant.now());
        needs.saveAndFlush(need);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesExpiredNeedBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.expire(Instant.now());
        needs.saveAndFlush(need);
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesNeedNotDiscoverableAtTheSelectedLocation() throws Exception {
        Fixture fixture = visibleFixture();
        UUID otherLocationId = locations.saveAndFlush(location(fixture.merchantId(), "Điểm khác", true)).getId();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", otherLocationId.toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesPausedSelectedLocationBehindNotFound() throws Exception {
        Fixture fixture = visibleFixture();
        jdbc.update("update merchant_locations set location_status='PAUSED' where id=?", fixture.locationId());
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAResponseCommandWhenItsAssignedLocationIsPaused() throws Exception {
        Fixture fixture = visibleFixture();
        jdbc.update("update merchant_locations set location_status='PAUSED' where id=?", fixture.locationId());
        String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString())));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"PRODUCT,SUSPENDED", "SERVICE,SUSPENDED", "PRODUCT,HIDDEN", "SERVICE,HIDDEN",
            "PRODUCT,PENDING", "SERVICE,PENDING", "PRODUCT,DELETED", "SERVICE,DELETED"})
    void nonTradingMerchantCannotDiscoverNeedsOrReadCandidates(NeedType type, String state) throws Exception {
        Fixture fixture = visibleFixture(type);
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/need-requests", fixture.merchantId()).with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        changeMerchantState(fixture, state);
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/need-requests", fixture.merchantId()).with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isNotFound());
        if (state.equals("SUSPENDED") || state.equals("HIDDEN")) {
            jdbc.update("update merchant_profiles set merchant_status='ACTIVE', moderation_status='APPROVED' where id=?", fixture.merchantId());
            mockMvc.perform(get("/api/v1/merchants/{merchantId}/need-requests", fixture.merchantId()).with(fixture.seller().jwt()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
            mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                            .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"PRODUCT,SUSPENDED", "SERVICE,SUSPENDED", "PRODUCT,HIDDEN", "SERVICE,HIDDEN",
            "PRODUCT,PENDING", "SERVICE,PENDING", "PRODUCT,DELETED", "SERVICE,DELETED"})
    void nonTradingMerchantCannotCreateANeedResponse(NeedType type, String state) throws Exception {
        Fixture fixture = visibleFixture(type);
        changeMerchantState(fixture, state);
        String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString())));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForObject("select count(*) from need_responses where need_request_id=?", Long.class, fixture.needId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='NEED_RESPONSE_CREATED'", Long.class, fixture.needId())).isZero();
    }

    private void changeMerchantState(Fixture fixture, String state) {
        switch (state) {
            case "SUSPENDED" -> jdbc.update("update merchant_profiles set merchant_status='SUSPENDED' where id=?", fixture.merchantId());
            case "DELETED" -> jdbc.update("update merchant_profiles set deleted_at=now() where id=?", fixture.merchantId());
            default -> jdbc.update("update merchant_profiles set moderation_status=? where id=?", state, fixture.merchantId());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"PRODUCT,SUSPENDED", "SERVICE,SUSPENDED", "PRODUCT,HIDDEN", "SERVICE,HIDDEN",
            "PRODUCT,PENDING", "SERVICE,PENDING", "PRODUCT,DELETED", "SERVICE,DELETED"})
    void newNeedNotificationAudienceExcludesNonTradingMerchants(NeedType type, String state) {
        Fixture fixture = visibleFixture(type);
        assertThat(audience.findCandidateLocations(fixture.needId(), 500))
                .anyMatch(candidate -> candidate.merchantLocationId().equals(fixture.locationId()));
        changeMerchantState(fixture, state);
        assertThat(audience.findCandidateLocations(fixture.needId(), 500))
                .noneMatch(candidate -> candidate.merchantLocationId().equals(fixture.locationId()));
    }

    @Test
    void rejectsAResponseCommandForAModerationHiddenNeed() throws Exception {
        Fixture fixture = visibleFixture();
        NeedRequestEntity need = needs.findById(fixture.needId()).orElseThrow();
        need.hide(Instant.now());
        needs.saveAndFlush(need);
        String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString())));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsAnExplicitAuthorizedPageBeforeCandidateFiltering() throws Exception {
        Fixture fixture = visibleFixture();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.maxResponseItems").isNumber());
    }

    @Test
    void returnsAProductOfferValidatedByTheResponsePolicy() throws Exception {
        Fixture fixture = visibleFixture();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andExpect(jsonPath("$.items[0].catalogItemId").value(fixture.itemId().toString()))
                .andExpect(jsonPath("$.items[0].variantId").value(fixture.variantId().toString()))
                .andExpect(jsonPath("$.items[0].catalogItemName").value("Cà phê"))
                .andExpect(jsonPath("$.items[0].variantName").value("Mặc định"))
                .andExpect(jsonPath("$.items[0].currentPrice.amount").value(45000))
                .andExpect(jsonPath("$.items[0].available").value(true));
    }

    @Test
    void returnsOnlyAServiceOfferWithinItsPostgisServiceArea() throws Exception {
        Fixture fixture = visibleFixture(NeedType.SERVICE);
        String body = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("inventoryMode").doesNotContain("geoPoint");
    }

    @Test
    void returnsTheAuthenticatedMerchantsExistingResponseWithReadableItemNames() throws Exception {
        Fixture fixture = visibleFixture();
        String commandBody = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString()),
                "note", "Có thể giao trong chiều nay."));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/need-requests/{needId}/response",
                        fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(fixture.merchantId().toString()))
                .andExpect(jsonPath("$.merchantLocationId").value(fixture.locationId().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.note").value("Có thể giao trong chiều nay."))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].catalogItemName").value("Cà phê"));
    }

    @Test
    void omitsAnOutOfStockTrackedOfferWhileKeepingTheUsableOffer() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID trackedVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-TRACKED", "Có theo dõi kho", false, 1, now)).getId();
        UUID trackedOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(),
                fixture.locationId(), trackedVariantId, 69000, "VND", true,
                InventoryMode.TRACKED, null, now)).getId();
        String body = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(trackedOfferId.toString());
    }

    @Test
    void omitsAnOfferFromAnUnrelatedCategory() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherCategoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null,
                "Danh mục khác", "other-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT,
                CategoryStatus.ACTIVE, 1)).getId();
        UUID otherItemId = UUID.randomUUID();
        CatalogItemEntity otherItem = CatalogItemEntity.create(otherItemId, fixture.merchantId(),
                CatalogItemType.PRODUCT, ListingKind.STANDARD, otherCategoryId,
                "Nội dung khác", "other-item-" + otherItemId, null, now);
        otherItem.publish(now);
        items.saveAndFlush(otherItem);
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(),
                otherItemId, "OTHER", "Mặc định", true, 0, now)).getId();
        UUID otherOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(),
                fixture.locationId(), otherVariantId, 69000, "VND", true,
                InventoryMode.UNTRACKED, null, now)).getId();
        String body = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(otherOfferId.toString());
    }

    @Test
    void pagesTwoEligibleOffersWithAContextBoundCursor() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-2", "Gói lớn", false, 1, now)).getId();
        UUID otherOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                otherVariantId, 69000, "VND", true, InventoryMode.UNTRACKED, null, now)).getId();

        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).queryParam("limit", "1")
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        var first = new ObjectMapper().readTree(firstBody);
        String cursor = first.path("nextCursor").asText();
        String firstOffer = first.path("items").get(0).path("locationVariantId").asText();
        String secondBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").queryParam("cursor", cursor)
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();
        String secondOffer = new ObjectMapper().readTree(secondBody)
                .path("items").get(0).path("locationVariantId").asText();
        assertThat(List.of(firstOffer, secondOffer)).containsExactlyInAnyOrder(
                fixture.offerId().toString(), otherOfferId.toString());
    }

    @Test
    void rejectsACursorReplayedAgainstAnotherVisibleNeed() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-2", "Gói lớn", false, 1, now)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                otherVariantId, 69000, "VND", true, InventoryMode.UNTRACKED, null, now));
        NeedRequestEntity firstNeed = needs.findById(fixture.needId()).orElseThrow();
        UUID secondNeedId = needs.saveAndFlush(NeedRequestEntity.create(UUID.randomUUID(),
                firstNeed.getBuyerUserId(), NeedType.PRODUCT, firstNeed.getCategoryId(),
                "Cần thêm cà phê", "Cà phê", null, 1L, null,
                point(), new ObjectMapper().createObjectNode(), 5000,
                now.plus(1, ChronoUnit.DAYS), now)).getId();
        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(firstBody).path("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        mockMvc.perform(get(path(), fixture.merchantId(), secondNeedId)
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("cursor", cursor).with(fixture.seller().jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsACursorReplayedAgainstAnotherVisibleLocation() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-OTHER", "Gói khác", false, 1, now)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                otherVariantId, 69000, "VND", true, InventoryMode.UNTRACKED, null, now));
        UUID otherLocationId = locations.saveAndFlush(location(fixture.merchantId(), "Điểm thứ hai", true)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), otherLocationId,
                fixture.variantId(), 45000, "VND", true, InventoryMode.UNTRACKED, null, now));
        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(firstBody).path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", otherLocationId.toString())
                        .queryParam("cursor", cursor).with(fixture.seller().jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMalformedCandidateCursor() throws Exception {
        Fixture fixture = visibleFixture();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("cursor", "not-a-candidate-cursor")
                        .with(fixture.seller().jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void clampsCandidatePageLimitToTheSupportedRange() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID otherVariantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), fixture.itemId(),
                "CF-CLAMP", "Gói khác", false, 1, now)).getId();
        offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                otherVariantId, 69000, "VND", true, InventoryMode.UNTRACKED, null, now));
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "-100").with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(true));
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "500").with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void scansPastARejectedCoarseBatchWithoutEndingPagination() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID categoryId = items.findById(fixture.itemId()).orElseThrow().getPrimaryCategoryId();
        UUID earlyItemId = new UUID(0, 500);
        CatalogItemEntity earlyItem = CatalogItemEntity.create(earlyItemId, fixture.merchantId(),
                CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Cà phê thêm",
                "early-" + UUID.randomUUID(), null, now);
        earlyItem.publish(now);
        items.saveAndFlush(earlyItem);
        for (int index = 1; index <= 31; index++) {
            UUID variantId = variants.saveAndFlush(VariantEntity.create(new UUID(0, index), earlyItemId,
                    "TRACKED-" + index, "Hết kho " + index, index == 1, index, now)).getId();
            offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), fixture.locationId(),
                    variantId, 69000, "VND", true, InventoryMode.TRACKED, null, now));
        }
        UUID validVariantId = variants.saveAndFlush(VariantEntity.create(new UUID(0, 100), earlyItemId,
                "VALID", "Còn hàng", false, 100, now)).getId();
        UUID validOfferId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(),
                fixture.locationId(), validVariantId, 69000, "VND", true,
                InventoryMode.UNTRACKED, null, now)).getId();

        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(validOfferId.toString()))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(firstBody).path("nextCursor").asText();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").queryParam("cursor", cursor)
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void continuesAfterTheRejectedCandidateScanCeiling() throws Exception {
        Fixture fixture = visibleFixture();
        Instant now = Instant.now();
        UUID categoryId = items.findById(fixture.itemId()).orElseThrow().getPrimaryCategoryId();
        UUID earlyItemId = new UUID(0, UUID.randomUUID().getLeastSignificantBits());
        CatalogItemEntity earlyItem = CatalogItemEntity.create(earlyItemId, fixture.merchantId(),
                CatalogItemType.PRODUCT, ListingKind.STANDARD, categoryId, "Hết kho hàng loạt",
                "scan-ceiling-" + UUID.randomUUID(), null, now);
        earlyItem.publish(now);
        items.saveAndFlush(earlyItem);
        jdbc.update("""
                insert into variants(id,catalog_item_id,sku,variant_name,is_default,variant_status,
                                     sort_order,created_at,updated_at,version)
                select gen_random_uuid(), ?, null, 'Hết kho ' || sequence_number, false, 'ACTIVE',
                       sequence_number, ?, ?, 0
                from generate_series(1,501) sequence_number
                """, earlyItemId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        jdbc.update("""
                insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,
                                              is_available,inventory_mode,created_at,updated_at,version)
                select gen_random_uuid(), ?, v.id, 45000, 'VND', true, 'TRACKED', ?, ?, 0
                from variants v where v.catalog_item_id = ?
                """, fixture.locationId(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), earlyItemId);

        String firstBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(firstBody).path("nextCursor").asText();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString())
                        .queryParam("limit", "1").queryParam("cursor", cursor)
                        .with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void acceptsAnUnchangedReturnedCandidateOnTheResponseCommand() throws Exception {
        Fixture fixture = visibleFixture();
        String readBody = mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String returnedId = new ObjectMapper().readTree(readBody)
                .path("items").get(0).path("locationVariantId").asText();
        String commandBody = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(returnedId)));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].locationVariantId").value(returnedId));
    }

    @Test
    void revalidatesAvailabilityAfterACandidateRead() throws Exception {
        Fixture fixture = visibleFixture();
        mockMvc.perform(get(path(), fixture.merchantId(), fixture.needId())
                        .queryParam("locationId", fixture.locationId().toString()).with(fixture.seller().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locationVariantId").value(fixture.offerId().toString()));
        LocationVariantEntity offer = offers.findById(fixture.offerId()).orElseThrow();
        offer.update(offer.getPrice(), false, offer.getInventoryMode(),
                offer.getPreparationTimeMinutes(), Instant.now());
        offers.saveAndFlush(offer);
        String commandBody = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "merchantLocationId", fixture.locationId().toString(),
                "locationVariantIds", List.of(fixture.offerId().toString())));
        mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(fixture.seller().jwt())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NEED_RESPONSE_ITEM_INVALID"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void needResponseCheckoutRevalidatesSourceAndFulfillsOnlyAfterSellerConfirmation(boolean cancelAfterPreview) throws Exception {
        Fixture fixture = visibleFixture();
        UUID buyerId = needs.findById(fixture.needId()).orElseThrow().getBuyerUserId();
        Actor buyer = new Actor(buyerId, jdbc.queryForObject("select id from auth_sessions where user_id=?", UUID.class, buyerId));
        String buyerToken = issuer.issue(buyerId, new vn.covie.auth.model.DeviceMetadata("Buyer checkout", "need-checkout-test")).accessToken().value();
        String sellerToken = issuer.issue(fixture.seller().userId(), new vn.covie.auth.model.DeviceMetadata("Seller checkout", "need-checkout-test")).accessToken().value();
        ObjectMapper json = new ObjectMapper();
        String responseBody = mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .header("Authorization", "Bearer " + sellerToken).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                                "merchantLocationId", fixture.locationId(), "locationVariantIds", List.of(fixture.offerId())))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String responseItemId = json.readTree(responseBody).path("items").get(0).path("id").asText();
        String previewBody = json.writeValueAsString(java.util.Map.of(
                "source", java.util.Map.of("type", "NEED_REQUEST", "needRequestId", fixture.needId(), "needResponseItemId", responseItemId),
                "items", List.of(java.util.Map.of("locationVariantId", fixture.offerId(), "quantity", 1)),
                "fulfillmentMethod", "PICKUP", "paymentMethod", "COD"));
        var foreignBuyer = actor();
        String foreignToken = issuer.issue(foreignBuyer.userId(), new vn.covie.auth.model.DeviceMetadata("Foreign checkout", "need-checkout-test")).accessToken().value();
        mockMvc.perform(post("/api/v1/checkouts/preview").header("Authorization", "Bearer " + foreignToken)
                .contentType(MediaType.APPLICATION_JSON).content(previewBody))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("NEED_SOURCE_INVALID"));
        UUID categoryId = jdbc.queryForObject("select category_id from need_requests where id=?", UUID.class, fixture.needId());
        UUID otherNeed = needs.saveAndFlush(NeedRequestEntity.create(UUID.randomUUID(), buyerId,
                NeedType.PRODUCT, categoryId, "Other need", "Cà phê", null, 1L, null, point(),
                json.createObjectNode(), 5000, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now())).getId();
        String mismatchedBody = previewBody.replace(fixture.needId().toString(), otherNeed.toString());
        mockMvc.perform(post("/api/v1/checkouts/preview").header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON).content(mismatchedBody))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("NEED_SOURCE_INVALID"));
        assertThat(jdbc.queryForObject("select count(*) from checkout_sessions where user_id in (?,?)", Long.class, buyerId, foreignBuyer.userId())).isZero();
        String preview = mockMvc.perform(post("/api/v1/checkouts/preview").header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(previewBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String commitBody = json.writeValueAsString(java.util.Map.of("checkoutToken", json.readTree(preview).path("checkoutToken").asText()));
        if (cancelAfterPreview) {
            var need = needs.findById(fixture.needId()).orElseThrow();
            need.cancel(Instant.now());
            needs.saveAndFlush(need);
            mockMvc.perform(post("/api/v1/checkouts/commit").header("Authorization", "Bearer " + buyerToken)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(commitBody))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CHECKOUT_CHANGED"));
            assertThat(jdbc.queryForObject("select count(*) from orders where source_need_request_id=?", Integer.class, fixture.needId())).isZero();
            return;
        }
        String key = UUID.randomUUID().toString();
        String created = mockMvc.perform(post("/api/v1/checkouts/commit").header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(commitBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = json.readTree(created).path("id").asText();
        mockMvc.perform(post("/api/v1/checkouts/commit").header("Authorization", "Bearer " + buyerToken).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(commitBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(orderId));
        assertThat(needs.findById(fixture.needId()).orElseThrow().getRequestStatus().name()).isEqualTo("ACTIVE");
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/confirm").header("Authorization", "Bearer " + sellerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        var fulfilled = needs.findById(fixture.needId()).orElseThrow();
        assertThat(fulfilled.getRequestStatus().name()).isEqualTo("FULFILLED");
        assertThat(fulfilled.getFulfilledOrderId()).isEqualTo(UUID.fromString(orderId));
        assertThat(jdbc.queryForObject("select count(*) from orders where source_need_request_id=?", Integer.class, fixture.needId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='NEED_FULFILLED'", Integer.class, fixture.needId())).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"LOCATION_PAUSED,false", "MERCHANT_SUSPENDED,false", "LOCATION_PAUSED,true", "MERCHANT_SUSPENDED,true"})
    void buyerResponseAvailabilityMatchesCheckoutWhenSellerStopsTrading(String change, boolean commitAfterPreview) throws Exception {
        Fixture fixture = visibleFixture();
        UUID buyerId = needs.findById(fixture.needId()).orElseThrow().getBuyerUserId();
        Actor buyer = new Actor(buyerId, jdbc.queryForObject("select id from auth_sessions where user_id=?", UUID.class, buyerId));
        ObjectMapper json = new ObjectMapper();
        String created = mockMvc.perform(post("/api/v1/need-requests/{needId}/responses", fixture.needId())
                        .with(fixture.seller().jwt()).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                                "merchantLocationId", fixture.locationId(), "locationVariantIds", List.of(fixture.offerId())))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String preview = json.writeValueAsString(java.util.Map.of(
                "source", java.util.Map.of("type", "NEED_REQUEST", "needRequestId", fixture.needId(), "needResponseItemId", json.readTree(created).path("items").get(0).path("id").asText()),
                "items", List.of(java.util.Map.of("locationVariantId", fixture.offerId(), "quantity", 1)),
                "fulfillmentMethod", "PICKUP", "paymentMethod", "COD"));
        String token = null;
        if (commitAfterPreview) {
            String body = mockMvc.perform(post("/api/v1/checkouts/preview").with(buyer.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(preview)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            token = json.readTree(body).path("checkoutToken").asText();
        }
        if (change.equals("LOCATION_PAUSED")) jdbc.update("update merchant_locations set location_status='PAUSED' where id=?", fixture.locationId());
        else jdbc.update("update merchant_profiles set merchant_status='SUSPENDED' where id=?", fixture.merchantId());
        if (commitAfterPreview) {
            mockMvc.perform(post("/api/v1/checkouts/commit").with(buyer.jwt())
                    .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(java.util.Map.of("checkoutToken", token))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CHECKOUT_CHANGED"));
            assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?", Long.class, buyerId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from checkout_sessions where user_id=? and consumed_at is not null", Long.class, buyerId)).isZero();
        }
        mockMvc.perform(post("/api/v1/checkouts/preview").with(buyer.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(preview))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(change.equals("LOCATION_PAUSED")
                        ? "CHECKOUT_LOCATION_UNAVAILABLE" : "OFFER_NOT_AVAILABLE"));
        mockMvc.perform(get("/api/v1/me/need-requests/{needId}/responses", fixture.needId()).with(buyer.jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].items[0].available").value(false))
                .andExpect(jsonPath("$.items[0].items[0].actionable").value(false));
    }

    private Fixture visibleFixture() {
        return visibleFixture(NeedType.PRODUCT);
    }

    private Fixture visibleFixture(NeedType type) {
        Actor seller = actor();
        Actor buyer = actor();
        Instant now = Instant.now();
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Seller", "seller-" + merchantId, now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId,
                seller.userId(), MerchantRole.OWNER, now));
        UUID locationId = locations.saveAndFlush(location(merchantId, "Điểm chính", type == NeedType.PRODUCT,
                type == NeedType.SERVICE)).getId();
        if (type == NeedType.SERVICE) {
            jdbc.update("insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version) values (?,?,'SERVICE_AREA',true,5000,false,'ACTIVE',?,?,0)",
                    UUID.randomUUID(), locationId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        }
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Cà phê",
                "coffee-" + UUID.randomUUID(), type == NeedType.PRODUCT ? CategoryCatalogType.PRODUCT
                        : CategoryCatalogType.SERVICE, CategoryStatus.ACTIVE, 0)).getId();
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, merchantId,
                type == NeedType.PRODUCT ? CatalogItemType.PRODUCT : CatalogItemType.SERVICE,
                ListingKind.STANDARD, categoryId, "Cà phê", "coffee-" + itemId, null, now);
        item.publish(now);
        items.saveAndFlush(item);
        UUID variantId = variants.saveAndFlush(VariantEntity.create(UUID.randomUUID(), itemId,
                "CF-1", "Mặc định", true, 0, now)).getId();
        UUID offerId = offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(), locationId, variantId,
                45000, "VND", true, type == NeedType.PRODUCT ? InventoryMode.UNTRACKED : null, null, now)).getId();
        UUID needId = needs.saveAndFlush(NeedRequestEntity.create(UUID.randomUUID(), buyer.userId(),
                type, categoryId, "Cần cà phê", "Cà phê", null, 1L, null,
                point(), new ObjectMapper().createObjectNode(), 5000, now.plus(1, ChronoUnit.DAYS), now)).getId();
        return new Fixture(seller, merchantId, locationId, needId, itemId, variantId, offerId);
    }

    private MerchantLocationEntity location(UUID merchantId, String name, boolean pickup) {
        return location(merchantId, name, pickup, false);
    }

    private MerchantLocationEntity location(UUID merchantId, String name, boolean pickup, boolean service) {
        return MerchantLocationEntity.create(UUID.randomUUID(), merchantId, name, "1 CoVie", null,
                point(), PinSource.GPS, "Asia/Ho_Chi_Minh", pickup, false, service,
                null, "VND", Instant.now());
    }

    private Point point() {
        Point point = new GeometryFactory().createPoint(new Coordinate(106.7, 10.8));
        point.setSRID(4326);
        return point;
    }

    private Actor actor() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(UserEntity.active(userId, "Seller", now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now,
                now.plus(30, ChronoUnit.DAYS)));
        return new Actor(userId, sessionId);
    }

    private String path() {
        return "/api/v1/merchants/{merchantId}/need-requests/{needId}/eligible-items";
    }

    private record Actor(UUID userId, UUID sessionId) {
        JwtRequestPostProcessor jwt() {
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(token -> token.subject(userId.toString()).claim("sessionId", sessionId.toString())
                            .claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }

    private record Fixture(Actor seller, UUID merchantId, UUID locationId, UUID needId,
            UUID itemId, UUID variantId, UUID offerId) {}
}
