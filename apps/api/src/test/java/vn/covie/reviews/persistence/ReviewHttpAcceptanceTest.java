package vn.covie.reviews.persistence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class ReviewHttpAcceptanceTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialSessionIssuer issuer;
    @Autowired ReviewRepository reviews;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void populatedOrderTrustSummaryExposesOnlyAllowedAggregatesForEachActor() throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var seller = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var admin = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var history = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version) "
                + "values (?,?,?,'OWNER','ACTIVE',current_timestamp,current_timestamp,0)", UUID.randomUUID(), fixture.merchantId(), seller.buyerId());
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',current_timestamp)", UUID.randomUUID(), admin.buyerId());
        jdbc.update("insert into user_reputation_stats(user_id,eligible_order_count,completed_order_count,buyer_no_show_count,completion_rate_basis_points,updated_at) "
                + "values (?,15,10,5,6667,current_timestamp)", fixture.buyerId());
        jdbc.update("insert into merchant_reputation_stats(merchant_profile_id,eligible_order_count,completed_order_count,seller_cancel_after_confirm_count,completion_rate_basis_points,updated_at) "
                + "values (?,12,9,3,7500,current_timestamp)", fixture.merchantId());
        UUID location = jdbc.queryForObject("select merchant_location_id from orders where id=?", UUID.class, fixture.orderId());
        jdbc.update("update orders set buyer_user_id=?,merchant_profile_id=?,merchant_location_id=?,order_code='PRIVATE_HISTORY_SENTINEL',status='BUYER_NO_SHOW' where id=?",
                fixture.buyerId(), fixture.merchantId(), location, history.orderId());
        jdbc.update("insert into reputation_order_outcomes(order_id,buyer_user_id,merchant_profile_id,buyer_outcome,merchant_outcome,source_order_status,updated_at) "
                + "values (?,?,?,'NEGATIVE_NO_SHOW','EXCLUDED','BUYER_NO_SHOW',current_timestamp)", history.orderId(), fixture.buyerId(), fixture.merchantId());
        var buyerBefore = jdbc.queryForMap("select * from user_reputation_stats where user_id=?", fixture.buyerId());
        var merchantBefore = jdbc.queryForMap("select * from merchant_reputation_stats where merchant_profile_id=?", fixture.merchantId());
        var historyBefore = jdbc.queryForMap("select * from reputation_order_outcomes where order_id=?", history.orderId());
        for (int actor = 0; actor < 3; actor++) {
            UUID user = actor == 0 ? fixture.buyerId() : actor == 1 ? seller.buyerId() : admin.buyerId();
            String bearer = "Bearer " + issuer.issue(user, new DeviceMetadata("Populated trust", "trust-http")).accessToken().value();
            var response = mvc.perform(get("/api/v1/orders/" + fixture.orderId()).header("Authorization", bearer))
                    .andExpect(status().isOk()).andReturn();
            org.assertj.core.api.Assertions.assertThat(response.getResponse().getContentAsString())
                    .doesNotContain("PRIVATE_HISTORY_SENTINEL", history.orderId().toString(), "NEGATIVE_NO_SHOW");
            var reputation = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("reputation");
            org.assertj.core.api.Assertions.assertThat(reputation.properties().stream().map(java.util.Map.Entry::getKey).toList())
                    .containsOnly(actor == 0 ? new String[] {"merchant"} : actor == 1 ? new String[] {"buyer"} : new String[] {"merchant", "buyer"});
            if (actor != 1) {
                var summary = reputation.path("merchant");
                org.assertj.core.api.Assertions.assertThat(summary.properties().stream().map(java.util.Map.Entry::getKey).toList())
                        .containsOnly("eligibleOrders", "completedOrders", "completionRateBasisPoints");
                org.assertj.core.api.Assertions.assertThat(summary.path("eligibleOrders").asLong()).isEqualTo(12);
                org.assertj.core.api.Assertions.assertThat(summary.path("completedOrders").asLong()).isEqualTo(9);
                org.assertj.core.api.Assertions.assertThat(summary.path("completionRateBasisPoints").asInt()).isEqualTo(7500);
            }
            if (actor != 0) {
                var summary = reputation.path("buyer");
                org.assertj.core.api.Assertions.assertThat(summary.properties().stream().map(java.util.Map.Entry::getKey).toList())
                        .containsOnly("eligibleOrders", "completedOrders", "completionRateBasisPoints", "warning");
                org.assertj.core.api.Assertions.assertThat(summary.path("eligibleOrders").asLong()).isEqualTo(15);
                org.assertj.core.api.Assertions.assertThat(summary.path("completedOrders").asLong()).isEqualTo(10);
                org.assertj.core.api.Assertions.assertThat(summary.path("completionRateBasisPoints").asInt()).isEqualTo(6667);
                org.assertj.core.api.Assertions.assertThat(summary.path("warning").asBoolean()).isTrue();
            }
            org.assertj.core.api.Assertions.assertThat(reputation.toString()).doesNotContain("PRIVATE_HISTORY_SENTINEL",
                    history.orderId().toString(), fixture.buyerId().toString(), fixture.merchantId().toString(), "NEGATIVE_NO_SHOW");
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("select * from user_reputation_stats where user_id=?", fixture.buyerId())).isEqualTo(buyerBefore);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("select * from merchant_reputation_stats where merchant_profile_id=?", fixture.merchantId())).isEqualTo(merchantBefore);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("select * from reputation_order_outcomes where order_id=?", history.orderId())).isEqualTo(historyBefore);
    }

    @Test
    void orderTrustSummaryProjectsOnlyTheCounterpartyForBuyerAndSeller() throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var seller = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version) "
                + "values (?,?,?,'OWNER','ACTIVE',current_timestamp,current_timestamp,0)", UUID.randomUUID(), fixture.merchantId(), seller.buyerId());
        for (boolean buyer : new boolean[] {true, false}) {
            String bearer = "Bearer " + issuer.issue(buyer ? fixture.buyerId() : seller.buyerId(),
                    new DeviceMetadata("Trust viewer", "trust-http")).accessToken().value();
            var result = mvc.perform(get("/api/v1/orders/" + fixture.orderId()).header("Authorization", bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath(buyer ? "$.reputation.buyer" : "$.reputation.merchant").doesNotExist())
                    .andExpect(jsonPath(buyer ? "$.reputation.merchant.eligibleOrders" : "$.reputation.buyer.eligibleOrders").value(0)).andReturn();
            var reputation = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).path("reputation");
            var summary = reputation.path(buyer ? "merchant" : "buyer");
            org.assertj.core.api.Assertions.assertThat(summary.properties().stream().map(java.util.Map.Entry::getKey).toList())
                    .containsOnly(buyer ? new String[] {"eligibleOrders", "completedOrders"}
                            : new String[] {"eligibleOrders", "completedOrders", "warning"});
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2,0,0,false", "5,4,8000,false", "3,2,6667,true"})
    void sellerWarningRespectsMinimumSampleAndStrictCompletionThreshold(long eligible, long completed, int rate, boolean warning) throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var seller = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version) "
                + "values (?,?,?,'OWNER','ACTIVE',current_timestamp,current_timestamp,0)", UUID.randomUUID(), fixture.merchantId(), seller.buyerId());
        jdbc.update("insert into user_reputation_stats(user_id,eligible_order_count,completed_order_count,buyer_no_show_count,completion_rate_basis_points,updated_at) "
                + "values (?,?,?,?,?,current_timestamp)", fixture.buyerId(), eligible, completed, eligible - completed, rate);
        String bearer = "Bearer " + issuer.issue(seller.buyerId(), new DeviceMetadata("Warning policy", "trust-http")).accessToken().value();
        mvc.perform(get("/api/v1/orders/" + fixture.orderId()).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reputation.buyer.eligibleOrders").value((int) eligible))
                .andExpect(jsonPath("$.reputation.buyer.completionRateBasisPoints").value(rate))
                .andExpect(jsonPath("$.reputation.buyer.warning").value(warning))
                .andExpect(jsonPath("$.reputation.merchant").doesNotExist());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void disablingCreationPreservesExistingPublicReviewsAndRating() throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        jdbc.update("update orders set status='COMPLETED',completed_at=current_timestamp where id=?", fixture.orderId());
        String bearer = "Bearer " + issuer.issue(fixture.buyerId(), new DeviceMetadata("Review buyer", "review-disable")).accessToken().value();
        mvc.perform(post("/api/v1/me/reviews").header("Authorization", bearer)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":\"" + fixture.orderItemId() + "\",\"rating\":4}"))
                .andExpect(status().isCreated());
        entityManager.flush();
        Long outboxBefore = jdbc.queryForObject("select count(*) from outbox_events where event_type='REVIEW_CREATED'", Long.class);
        jdbc.update("insert into system_settings(id,setting_key,value_type,value_json,scope_type,updated_by_user_id,created_at,updated_at,version) "
                + "values (?,'review.enabled','BOOLEAN','false'::jsonb,'GLOBAL',?,current_timestamp,current_timestamp,0)", UUID.randomUUID(), fixture.buyerId());
        mvc.perform(post("/api/v1/me/reviews").header("Authorization", bearer)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":\"" + fixture.anotherOrderItemId() + "\",\"rating\":5}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("REVIEW_CREATION_DISABLED"));
        for (String route : java.util.List.of("/api/v1/catalog/items/" + fixture.catalogItemId() + "/reviews",
                "/api/v1/merchants/" + fixture.merchantId() + "/reviews")) {
            mvc.perform(get(route)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].rating").value(4)).andExpect(jsonPath("$.rating.reviewCount").value(1));
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from reviews where order_id=?", Long.class, fixture.orderId())).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='REVIEW_CREATED'", Long.class)).isEqualTo(outboxBefore);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PICKUP", "SELLER_DELIVERY", "SERVICE"})
    void sellerConfirmationAndBuyerCompletionUnlockReviewThenPreventDuplicate(String mode) throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var seller = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        jdbc.update("update orders set confirmation_expires_at=current_timestamp + interval '1 hour' where id=?", fixture.orderId());
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version) "
                + "values (?,?,?,'OWNER','ACTIVE',current_timestamp,current_timestamp,0)",
                UUID.randomUUID(), fixture.merchantId(), seller.buyerId());
        UUID location = jdbc.queryForObject("select merchant_location_id from orders where id=?", UUID.class, fixture.orderId());
        if (mode.equals("SERVICE")) {
            jdbc.update("update orders set order_type='SERVICE',shipping_fee_type=null,shipping_fee=null where id=?", fixture.orderId());
            jdbc.update("update order_items set item_type='SERVICE' where order_id=?", fixture.orderId());
            jdbc.update("update catalog_items set item_type='SERVICE' where id=?", fixture.catalogItemId());
            jdbc.update("insert into service_fulfillments(order_id,service_address_snapshot,provider_location_snapshot,buyer_contact_snapshot,merchant_contact_snapshot,created_at) "
                    + "values (?,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,current_timestamp)", fixture.orderId());
        } else if (mode.equals("SELLER_DELIVERY")) {
            UUID address = UUID.randomUUID();
            jdbc.update("insert into user_addresses(id,user_id,label,address_text,geo_point,pin_source,status,created_at,updated_at) "
                    + "values (?,?,'Home','1 Street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','ACTIVE',current_timestamp,current_timestamp)", address, fixture.buyerId());
            jdbc.update("insert into product_fulfillments(order_id,fulfillment_method,buyer_address_id,pickup_location_snapshot,delivery_address_snapshot,buyer_contact_snapshot,merchant_contact_snapshot) "
                    + "values (?,'SELLER_DELIVERY',?,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb)", fixture.orderId(), address);
        } else jdbc.update("insert into product_fulfillments(order_id,fulfillment_method,pickup_location_id,pickup_location_snapshot,buyer_contact_snapshot,merchant_contact_snapshot) "
                + "values (?,'PICKUP',?,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb)", fixture.orderId(), location);
        String buyerBearer = "Bearer " + issuer.issue(fixture.buyerId(), new DeviceMetadata("Buyer", "review-test")).accessToken().value();
        String sellerBearer = "Bearer " + issuer.issue(seller.buyerId(), new DeviceMetadata("Seller", "review-test")).accessToken().value();
        String route = "/api/v1/orders/" + fixture.orderId();
        mvc.perform(get(route).header("Authorization", buyerBearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reviewEligibility.eligible").value(false))
                .andExpect(jsonPath("$.items[0].reviewEligibility.reason").value("ORDER_NOT_COMPLETED"));
        mvc.perform(post(route + "/confirm").header("Authorization", sellerBearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        if (mode.equals("SELLER_DELIVERY")) {
            mvc.perform(post(route + "/mark-out-for-delivery").header("Authorization", sellerBearer)
                    .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        }
        String fulfillmentAction = mode.equals("SERVICE") ? "mark-service-performed"
                : mode.equals("SELLER_DELIVERY") ? "mark-delivered" : "mark-ready-for-pickup";
        mvc.perform(post(route + "/" + fulfillmentAction).header("Authorization", sellerBearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        String completionKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(route + (mode.equals("SERVICE") ? "/confirm-completed" : "/confirm-received")).header("Authorization", buyerBearer)
                    .header("Idempotency-Key", completionKey)).andExpect(status().isOk());
        }
        mvc.perform(get(route).header("Authorization", buyerBearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].reviewEligibility.eligible").value(true));
        mvc.perform(post("/api/v1/me/reviews").header("Authorization", buyerBearer)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":\"" + fixture.orderItemId() + "\",\"rating\":5}"))
                .andExpect(status().isCreated());
        var detail = mvc.perform(get(route).header("Authorization", buyerBearer)).andExpect(status().isOk()).andReturn();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(detail.getResponse().getContentAsString());
        var reviewedItem = java.util.stream.StreamSupport.stream(json.path("items").spliterator(), false)
                .filter(item -> item.path("id").asText().equals(fixture.orderItemId().toString())).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reviewedItem.path("reviewEligibility").path("eligible").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(reviewedItem.path("reviewEligibility").path("reason").asText()).isEqualTo("ALREADY_REVIEWED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=? and event_type=?",
                Long.class, fixture.orderId(), mode.equals("SERVICE") ? "BUYER_CONFIRMED_SERVICE_COMPLETED" : "BUYER_CONFIRMED_RECEIVED")).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select review_count from catalog_item_rating_stats where catalog_item_id=?",
                Long.class, fixture.catalogItemId())).isEqualTo(1);
    }

    @Test
    void anonymousPublicReviewsPaginateTiesAndExcludeHiddenAndPrivateFields() throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var timestamp = java.time.Instant.parse("2026-10-01T00:00:00Z");
        var first = fixture.review(fixture.orderItemId(), 4, timestamp);
        var second = fixture.review(fixture.anotherOrderItemId(), 5, timestamp);
        reviews.saveAndFlush(first);
        reviews.saveAndFlush(second);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String route : java.util.List.of("/api/v1/catalog/items/" + fixture.catalogItemId() + "/reviews",
                "/api/v1/merchants/" + fixture.merchantId() + "/reviews")) {
            var page = mvc.perform(get(route).param("limit", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.page.hasMore").value(true)).andReturn();
            var json = mapper.readTree(page.getResponse().getContentAsString());
            String cursor = json.path("page").path("nextCursor").asText();
            var next = mvc.perform(get(route).param("limit", "1").param("cursor", cursor))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.page.hasMore").value(false)).andReturn();
            var nextJson = mapper.readTree(next.getResponse().getContentAsString());
            org.assertj.core.api.Assertions.assertThat(java.util.Set.of(json.path("items").get(0).path("id").asText(),
                    nextJson.path("items").get(0).path("id").asText()))
                    .containsExactlyInAnyOrder(first.getId().toString(), second.getId().toString());
        }
        second.hide(timestamp.plusSeconds(1));
        reviews.saveAndFlush(second);
        for (String route : java.util.List.of("/api/v1/catalog/items/" + fixture.catalogItemId() + "/reviews",
                "/api/v1/merchants/" + fixture.merchantId() + "/reviews")) {
            var result = mvc.perform(get(route)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(first.getId().toString()))
                    .andExpect(jsonPath("$.items[0].verifiedPurchase").value(true))
                    .andExpect(jsonPath("$.items[0].reviewer.displayName").value("Buyer")).andReturn();
            var item = mapper.readTree(result.getResponse().getContentAsString()).path("items").get(0);
            org.assertj.core.api.Assertions.assertThat(item.properties().stream().map(java.util.Map.Entry::getKey).toList())
                    .containsExactlyInAnyOrder("id", "rating", "comment", "reviewer", "verifiedPurchase", "createdAt");
            org.assertj.core.api.Assertions.assertThat(item.path("reviewer").properties().stream().map(java.util.Map.Entry::getKey).toList())
                    .containsExactly("displayName");
        }
    }

    @Test
    void pendingOrderCannotBeReviewedAndDoesNotChangeRatingAggregates() throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        String bearer = "Bearer " + issuer.issue(fixture.buyerId(), new DeviceMetadata("Review browser", "review-test")).accessToken().value();
        mvc.perform(post("/api/v1/me/reviews").header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderItemId\":\"" + fixture.orderItemId() + "\",\"rating\":4}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REVIEW_ORDER_NOT_COMPLETED"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from reviews where order_item_id=?",
                Long.class, fixture.orderItemId())).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from catalog_item_rating_stats where catalog_item_id=?",
                Long.class, fixture.catalogItemId())).isZero();
    }

    @Test
    void completedOrderReviewReplaysOnceAndRejectsForeignBuyer() throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var foreign = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        jdbc.update("update orders set status='COMPLETED',completed_at=current_timestamp where id=?", fixture.orderId());
        String bearer = "Bearer " + issuer.issue(fixture.buyerId(), new DeviceMetadata("Review browser", "review-test")).accessToken().value();
        String foreignBearer = "Bearer " + issuer.issue(foreign.buyerId(), new DeviceMetadata("Other browser", "review-test")).accessToken().value();
        String body = "{\"orderItemId\":\"" + fixture.orderItemId() + "\",\"rating\":4,\"comment\":\"Helpful\"}";
        mvc.perform(post("/api/v1/me/reviews").header("Authorization", foreignBearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        String key = UUID.randomUUID().toString();
        String first = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            var result = mvc.perform(post("/api/v1/me/reviews").header("Authorization", bearer).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rating").value(4)).andReturn();
            if (first == null) first = result.getResponse().getContentAsString();
            else org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).isEqualTo(first);
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from reviews where order_item_id=?",
                Long.class, fixture.orderItemId())).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select review_count from catalog_item_rating_stats where catalog_item_id=?",
                Long.class, fixture.catalogItemId())).isEqualTo(1);
        mvc.perform(post("/api/v1/me/reviews").header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_EXISTS"));
    }

    @Test
    void missingIdempotencyKeyUsesTheExistingCommandValidationContract() throws Exception {
        var fixture = ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        String bearer = "Bearer " + issuer.issue(fixture.buyerId(), new DeviceMetadata("Review browser", "review-test")).accessToken().value();
        mvc.perform(post("/api/v1/me/reviews").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":\"" + fixture.orderItemId() + "\",\"rating\":4}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from reviews where order_item_id=?",
                Long.class, fixture.orderItemId())).isZero();
    }
}
