package vn.covie.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.sql.Timestamp;
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
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class OrderCancellationControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired OrderRepository orders;
    @Autowired vn.covie.auth.application.InitialSessionIssuer issuer;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    @Test
    void merchantCancelsConfirmedOrderAndIdempotentlyReplaysTheSingleEventAndOutboxRecord() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        String path = "/api/v1/orders/" + fixture.orderId() + "/cancel";
        String body = "{\"reasonCode\":\"MERCHANT_UNAVAILABLE\",\"note\":\"Closed\"}";
        String bearer = signed(fixture.seller());

        mockMvc.perform(post(path).header("Authorization", bearer).header("Idempotency-Key", "seller-cancel-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_SELLER_AFTER_CONFIRMATION"));
        mockMvc.perform(post(path).header("Authorization", bearer).header("Idempotency-Key", "seller-cancel-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_SELLER_AFTER_CONFIRMATION"));

        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION);
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_reservations where order_id = ? and reservation_status = 'RELEASED'", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id = ? and movement_type = 'RELEASE_RESERVATION'", Integer.class, fixture.orderId())).isEqualTo(1);
        dispatch(fixture);
        assertThat(jdbc.queryForMap("select eligible_order_count,buyer_cancel_after_confirm_count from user_reputation_stats where user_id=?", fixture.buyer().userId()))
                .containsEntry("eligible_order_count", 0L).containsEntry("buyer_cancel_after_confirm_count", 0L);
        assertThat(jdbc.queryForMap("select eligible_order_count,seller_cancel_after_confirm_count from merchant_reputation_stats where merchant_profile_id=?", fixture.merchantId()))
                .containsEntry("eligible_order_count", 1L).containsEntry("seller_cancel_after_confirm_count", 1L);
    }

    @Test
    void merchantCancellationRequiresReason() throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", fixture.orderId()).with(fixture.seller().jwt())
                        .header("Idempotency-Key", "missing-reason").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void acceptedBuyerCancellationProjectsOnlyBuyerPenaltyAfterRealDispatch() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        String path = "/api/v1/orders/" + fixture.orderId();
        String buyerBearer = signed(fixture.buyer()), sellerBearer = signed(fixture.seller());
        String requestKey = UUID.randomUUID().toString(), acceptKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(path + "/request-cancellation").header("Authorization", buyerBearer)
                    .header("Idempotency-Key", requestKey).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reasonCode\":\"CHANGED_MIND\",\"note\":\"Please cancel\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLATION_REQUESTED"));
        }
        dispatch(fixture);
        assertThat(jdbc.queryForMap("select buyer_outcome,merchant_outcome from reputation_order_outcomes where order_id=?", fixture.orderId()))
                .containsEntry("buyer_outcome", "NONE").containsEntry("merchant_outcome", "NONE");
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, fixture.orderId())).isEqualTo("ACTIVE");
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(path + "/accept-cancellation").header("Authorization", sellerBearer)
                    .header("Idempotency-Key", acceptKey))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED_BY_BUYER_AFTER_CONFIRMATION"));
        }
        dispatch(fixture);
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count,buyer_cancel_after_confirm_count from user_reputation_stats where user_id=?", fixture.buyer().userId()))
                .containsEntry("eligible_order_count", 1L).containsEntry("completed_order_count", 0L).containsEntry("buyer_cancel_after_confirm_count", 1L);
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count,seller_cancel_after_confirm_count from merchant_reputation_stats where merchant_profile_id=?", fixture.merchantId()))
                .containsEntry("eligible_order_count", 0L).containsEntry("completed_order_count", 0L).containsEntry("seller_cancel_after_confirm_count", 0L);
        assertThat(jdbc.queryForMap("select buyer_outcome,merchant_outcome from reputation_order_outcomes where order_id=?", fixture.orderId()))
                .containsEntry("buyer_outcome", "NEGATIVE_CANCEL_AFTER_CONFIRM").containsEntry("merchant_outcome", "NONE");
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=?", Long.class, fixture.orderId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, fixture.orderId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=? and movement_type='RELEASE_RESERVATION'", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, fixture.orderId())).isEqualTo("RELEASED");
    }

    @Test
    void buyerMustRequestRatherThanDirectlyCancelAConfirmedOrder() throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", fixture.orderId()).with(fixture.buyer().jwt())
                        .header("Idempotency-Key", "buyer-direct-cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"CHANGED_MIND\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORDER_ACTION_NOT_ALLOWED"));
    }

    private Fixture fixture() {
        Instant now = Instant.now();
        Actor seller = actor("seller", now); Actor buyer = actor("buyer", now);
        UUID merchantId = UUID.randomUUID(), locationId = UUID.randomUUID(), orderId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, seller.userId(), MerchantRole.OWNER, now));
        locations.saveAndFlush(MerchantLocationEntity.create(locationId, merchantId, "Main", "1 CoVie", 10.8, 106.7, now));
        OrderEntity order = OrderEntity.pending(orderId, "CV" + orderId.toString().substring(0, 8), OrderType.PRODUCT,
                buyer.userId(), merchantId, locationId, PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE,
                0L, 10_000, 10_000L, "VND", now.plusSeconds(600), now);
        order.confirm(now);
        orders.saveAndFlush(order);
        return new Fixture(seller, buyer, merchantId, locationId, orderId);
    }

    @Autowired vn.covie.orders.application.OrderScheduledTransitionService scheduledTransitions;
    @Autowired vn.covie.outbox.application.OutboxProcessor outboxProcessor;

    @Test
    void servicePerformedIssueAndBuyerCompletionStayConsistentWithoutInventory() throws Exception {
        Fixture fixture = fixture();
        jdbc.update("update orders set order_type='SERVICE',shipping_fee_type='NOT_APPLICABLE',shipping_fee=null where id=?", fixture.orderId());
        jdbc.update("insert into service_fulfillments(order_id,service_address_snapshot,provider_location_snapshot,buyer_contact_snapshot,merchant_contact_snapshot,created_at) values (?,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,?)",
                fixture.orderId(), Timestamp.from(Instant.now()));
        String prefix = "/api/v1/orders/" + fixture.orderId();
        String performedKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(prefix + "/mark-service-performed").with(fixture.seller().jwt())
                            .header("Idempotency-Key", performedKey)).andExpect(status().isOk());
        }
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.AWAITING_BUYER_CONFIRMATION);
        assertThat(jdbc.queryForObject("select auto_complete_at > provider_marked_performed_at from service_fulfillments where order_id=?", Boolean.class, fixture.orderId())).isTrue();
        mockMvc.perform(post(prefix + "/report-service-issue").with(fixture.buyer().jwt())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reasonText\":\"Service is incomplete\"}"))
                .andExpect(status().isOk());
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SERVICE_ISSUE_REPORTED);
        String completedKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(prefix + "/confirm-completed").with(fixture.buyer().jwt())
                            .header("Idempotency-Key", completedKey)).andExpect(status().isOk());
        }
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(jdbc.queryForObject("select issue_status from order_issues where order_id=?", String.class, fixture.orderId())).isEqualTo("RESOLVED_BY_PARTIES");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=?", Integer.class, fixture.orderId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from inventory_reservations where order_id=?", Integer.class, fixture.orderId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=?", Integer.class, fixture.orderId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Integer.class, fixture.orderId())).isEqualTo(3);
    }

    @Test
    void noShowGraceContestAndCompletionNeverDeductReleasedStockAgain() throws Exception {
        Fixture fixture = fixture();
        String sellerBearer = signed(fixture.seller()), buyerBearer = signed(fixture.buyer());
        seedActiveReservation(fixture);
        jdbc.update("update orders set status='READY_FOR_PICKUP' where id=?", fixture.orderId());
        jdbc.update("insert into product_fulfillments(order_id,fulfillment_method,pickup_location_id,pickup_location_snapshot,buyer_contact_snapshot,merchant_contact_snapshot,no_show_eligible_at) values (?,'PICKUP',?,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,?)",
                fixture.orderId(), fixture.locationId(), Timestamp.from(Instant.now().plusSeconds(3600)));
        String prefix = "/api/v1/orders/" + fixture.orderId();
        mockMvc.perform(post(prefix + "/report-buyer-no-show").header("Authorization", sellerBearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_NO_SHOW_GRACE_NOT_REACHED"));
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, fixture.orderId())).isEqualTo("ACTIVE");
        jdbc.update("update product_fulfillments set no_show_eligible_at=? where order_id=?",
                Timestamp.from(Instant.now().minusSeconds(3600)), fixture.orderId());
        String noShowKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(prefix + "/report-buyer-no-show").header("Authorization", sellerBearer)
                            .header("Idempotency-Key", noShowKey)).andExpect(status().isOk());
        }
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.BUYER_NO_SHOW);
        dispatch(fixture);
        assertThat(jdbc.queryForMap("select eligible_order_count,buyer_no_show_count from user_reputation_stats where user_id=?", fixture.buyer().userId()))
                .containsEntry("eligible_order_count", 1L).containsEntry("buyer_no_show_count", 1L);
        assertThat(jdbc.queryForObject("select eligible_order_count from merchant_reputation_stats where merchant_profile_id=?", Long.class, fixture.merchantId())).isZero();
        mockMvc.perform(post(prefix + "/contest-no-show").header("Authorization", buyerBearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"I arrived\"}"))
                .andExpect(status().isOk());
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.NO_SHOW_CONTESTED);
        dispatch(fixture);
        assertThat(jdbc.queryForMap("select eligible_order_count,buyer_no_show_count,excluded_issue_count from user_reputation_stats where user_id=?", fixture.buyer().userId()))
                .containsEntry("eligible_order_count", 0L).containsEntry("buyer_no_show_count", 0L).containsEntry("excluded_issue_count", 1L);
        assertThat(jdbc.queryForObject("select excluded_issue_count from merchant_reputation_stats where merchant_profile_id=?", Long.class, fixture.merchantId())).isEqualTo(1L);
        mockMvc.perform(post(prefix + "/confirm-received").header("Authorization", buyerBearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        dispatch(fixture);
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count,buyer_no_show_count,excluded_issue_count from user_reputation_stats where user_id=?", fixture.buyer().userId()))
                .containsEntry("eligible_order_count", 1L).containsEntry("completed_order_count", 1L).containsEntry("buyer_no_show_count", 0L).containsEntry("excluded_issue_count", 0L);
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count,excluded_issue_count from merchant_reputation_stats where merchant_profile_id=?", fixture.merchantId()))
                .containsEntry("eligible_order_count", 1L).containsEntry("completed_order_count", 1L).containsEntry("excluded_issue_count", 0L);
        assertThat(jdbc.queryForObject("select count(*) from reputation_order_outcomes where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select issue_status from order_issues where order_id=?", String.class, fixture.orderId())).isEqualTo("RESOLVED_BY_PARTIES");
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, fixture.orderId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=? and movement_type='SALE'", Integer.class, fixture.orderId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=? and movement_type='RELEASE_RESERVATION'", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", Long.class, fixture.orderId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", Long.class, fixture.orderId())).isZero();
    }

    @Test
    void buyerCompletionResolvesTheOpenDeliveryIssue() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        jdbc.update("update orders set status='DELIVERY_ISSUE_REPORTED' where id=?", fixture.orderId());
        UUID issueId = UUID.randomUUID();
        jdbc.update("insert into order_issues(id,order_id,issue_type,reported_by_user_id,issue_status,opened_at,close_after_at) values (?,?,'DELIVERY_NOT_RECEIVED',?,'OPEN',?,?)",
                issueId, fixture.orderId(), fixture.buyer().userId(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now().plusSeconds(3600)));
        mockMvc.perform(post("/api/v1/orders/" + fixture.orderId() + "/confirm-received")
                        .with(fixture.buyer().jwt()).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(jdbc.queryForObject("select issue_status from order_issues where id=?", String.class, issueId)).isEqualTo("RESOLVED_BY_PARTIES");
        assertThat(jdbc.queryForObject("select resolved_at is not null from order_issues where id=?", Boolean.class, issueId)).isTrue();
        assertThat(jdbc.queryForObject("select closed_at is null from order_issues where id=?", Boolean.class, issueId)).isTrue();
        UUID eventId = jdbc.queryForObject("select id from outbox_events where aggregate_id=? and event_type='ORDER_COMPLETED'", UUID.class, fixture.orderId());
        for (int attempt = 0; attempt < 200; attempt++) {
            if ("PUBLISHED".equals(jdbc.queryForObject("select status from outbox_events where id=?", String.class, eventId))) break;
            outboxProcessor.processOne();
        }
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?", String.class, eventId)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForList("select user_id from notifications where source_outbox_event_id=? and notification_type='ORDER_COMPLETED'", UUID.class, eventId))
                .containsExactlyInAnyOrder(fixture.buyer().userId(), fixture.seller().userId());
        assertThat(jdbc.queryForObject("select count(*) from notification_deliveries d join notifications n on n.id=d.notification_id where n.source_outbox_event_id=?", Integer.class, eventId)).isEqualTo(2);
    }

    @Test
    void deliveryIssueTimeoutCommitsReservedStockOnce() {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        jdbc.update("update orders set status='DELIVERY_ISSUE_REPORTED' where id=?", fixture.orderId());
        jdbc.update("insert into order_issues(id,order_id,issue_type,reported_by_user_id,issue_status,opened_at,close_after_at) values (?,?,'DELIVERY_NOT_RECEIVED',?,'OPEN',?,?)",
                UUID.randomUUID(), fixture.orderId(), fixture.buyer().userId(),
                Timestamp.from(Instant.now().minusSeconds(7200)), Timestamp.from(Instant.now().minusSeconds(3600)));
        scheduledTransitions.closeIssueOne(fixture.orderId());
        scheduledTransitions.closeIssueOne(fixture.orderId());
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CLOSED_WITH_ISSUE);
        dispatch(fixture);
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count,excluded_issue_count from user_reputation_stats where user_id=?", fixture.buyer().userId()))
                .containsEntry("eligible_order_count", 0L).containsEntry("completed_order_count", 0L).containsEntry("excluded_issue_count", 1L);
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count,excluded_issue_count from merchant_reputation_stats where merchant_profile_id=?", fixture.merchantId()))
                .containsEntry("eligible_order_count", 0L).containsEntry("completed_order_count", 0L).containsEntry("excluded_issue_count", 1L);
        assertThat(jdbc.queryForMap("select buyer_outcome,merchant_outcome from reputation_order_outcomes where order_id=?", fixture.orderId()))
                .containsEntry("buyer_outcome", "EXCLUDED").containsEntry("merchant_outcome", "EXCLUDED");
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from inventory_reservations where order_id=? and reservation_status='COMMITTED'", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=? and movement_type='SALE'", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", Long.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", Long.class, fixture.orderId())).isZero();
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void buyerCompletionAndSellerCancellationOverlapWithOneInventoryOutcome() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        String buyerBearer = signed(fixture.buyer()), sellerBearer = signed(fixture.seller());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var blocker = jdbc.getDataSource().getConnection();
             var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("select id from orders where id=? for update")) {
                lock.setObject(1, fixture.orderId());
                try (var row = lock.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            var buyer = workers.submit(() -> {
                assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post("/api/v1/orders/" + fixture.orderId() + "/confirm-received")
                        .header("Authorization", buyerBearer).header("Idempotency-Key", "race-buyer-" + fixture.orderId()))
                        .andReturn().getResponse();
            });
            var seller = workers.submit(() -> {
                assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post("/api/v1/orders/" + fixture.orderId() + "/cancel")
                        .header("Authorization", sellerBearer).header("Idempotency-Key", "race-seller-" + fixture.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"MERCHANT_UNAVAILABLE\",\"note\":\"Closed\"}"))
                        .andReturn().getResponse();
            });
            start.countDown();
            try {
                long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                int waiting;
                do {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%orders%'", Integer.class);
                    if (waiting >= 2) break;
                    Thread.sleep(25);
                } while (System.nanoTime() < until);
                assertThat(waiting).as("both commands reached the locked order").isGreaterThanOrEqualTo(2);
            } finally { blocker.commit(); }
            var buyerResponse = buyer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var sellerResponse = seller.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(buyerResponse.getStatus(), sellerResponse.getStatus())).containsExactlyInAnyOrder(200, 409);
            var loser = buyerResponse.getStatus() == 409 ? buyerResponse : sellerResponse;
            assertThat(loser.getContentAsString()).contains("ORDER_STATE_CONFLICT");
        }
        String state = jdbc.queryForObject("select status from orders where id=?", String.class, fixture.orderId());
        boolean completed = state.equals("COMPLETED");
        assertThat(state).isIn("COMPLETED", "CANCELLED_BY_SELLER_AFTER_CONFIRMATION");
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, fixture.orderId())).isEqualTo(completed ? "COMMITTED" : "RELEASED");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select movement_type from inventory_movements where order_id=?", String.class, fixture.orderId())).isEqualTo(completed ? "SALE" : "RELEASE_RESERVATION");
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", Long.class, fixture.orderId())).isZero();
        assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", Long.class, fixture.orderId())).isEqualTo(completed ? 1L : 2L);
        dispatch(fixture);
        assertThat(jdbc.queryForObject("select count(*) from reputation_order_outcomes where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
    }
    @org.junit.jupiter.api.RepeatedTest(3)
    void buyerCompletionAndMarkDeliveredOverlapWithoutDuplicateSale() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        UUID address = UUID.randomUUID();
        jdbc.update("insert into user_addresses(id,user_id,label,address_text,geo_point,pin_source,status,created_at,updated_at) values (?,?,'Home','1 Street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','ACTIVE',current_timestamp,current_timestamp)", address, fixture.buyer().userId());
        jdbc.update("insert into product_fulfillments(order_id,fulfillment_method,buyer_address_id,pickup_location_snapshot,delivery_address_snapshot,buyer_contact_snapshot,merchant_contact_snapshot) values (?,'SELLER_DELIVERY',?,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb)", fixture.orderId(), address);
        String buyerBearer = signed(fixture.buyer()), sellerBearer = signed(fixture.seller());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var blocker = jdbc.getDataSource().getConnection();
             var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("select id from orders where id=? for update")) {
                lock.setObject(1, fixture.orderId());
                try (var row = lock.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            var buyer = workers.submit(() -> {
                assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post("/api/v1/orders/" + fixture.orderId() + "/confirm-received")
                        .header("Authorization", buyerBearer).header("Idempotency-Key", "race-buyer-" + fixture.orderId()))
                        .andReturn().getResponse();
            });
            var seller = workers.submit(() -> {
                assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post("/api/v1/orders/" + fixture.orderId() + "/mark-delivered")
                        .header("Authorization", sellerBearer).header("Idempotency-Key", "race-seller-" + fixture.orderId())
                        )
                        .andReturn().getResponse();
            });
            start.countDown();
            try {
                long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                int waiting;
                do {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%orders%'", Integer.class);
                    if (waiting >= 2) break;
                    Thread.sleep(25);
                } while (System.nanoTime() < until);
                assertThat(waiting).as("both commands reached the locked order").isGreaterThanOrEqualTo(2);
            } finally { blocker.commit(); }
            var buyerResponse = buyer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var sellerResponse = seller.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(buyerResponse.getStatus()).isEqualTo(200);
            assertThat(sellerResponse.getStatus()).isIn(200, 409);
            if (sellerResponse.getStatus() == 409) assertThat(sellerResponse.getContentAsString()).contains("ORDER_STATE_CONFLICT");
            assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=? and event_type='SELLER_MARKED_DELIVERED'", Long.class, fixture.orderId())).isEqualTo(sellerResponse.getStatus() == 200 ? 1L : 0L);
            assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=? and event_type='BUYER_CONFIRMED_RECEIVED'", Long.class, fixture.orderId())).isEqualTo(1L);
        }
        String state = jdbc.queryForObject("select status from orders where id=?", String.class, fixture.orderId());
        boolean completed = state.equals("COMPLETED");
        assertThat(state).isEqualTo("COMPLETED");
        long eventCount = jdbc.queryForObject("select count(*) from order_events where order_id=?", Long.class, fixture.orderId());
        assertThat(eventCount).isIn(1L, 2L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, fixture.orderId())).isEqualTo(eventCount);
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, fixture.orderId())).isEqualTo(completed ? "COMMITTED" : "RELEASED");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select movement_type from inventory_movements where order_id=?", String.class, fixture.orderId())).isEqualTo(completed ? "SALE" : "RELEASE_RESERVATION");
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", Long.class, fixture.orderId())).isZero();
        assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", Long.class, fixture.orderId())).isEqualTo(completed ? 1L : 2L);
        dispatch(fixture);
        assertThat(jdbc.queryForObject("select count(*) from reputation_order_outcomes where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
    }
    @org.junit.jupiter.api.RepeatedTest(3)
    void confirmationAndBuyerPendingCancellationSerializeWithoutDoubleRelease() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        jdbc.update("update orders set status='PENDING_CONFIRMATION', confirmed_at=null where id=?", fixture.orderId());
        String seller = signed(fixture.seller()), buyer = signed(fixture.buyer());
        var responses = overlap(fixture,
                () -> command(fixture, seller, "confirm", null),
                () -> command(fixture, buyer, "cancel", "{\"reasonCode\":\"CHANGED_MIND\"}"));
        String state = jdbc.queryForObject("select status from orders where id=?", String.class, fixture.orderId());
        boolean confirmed = state.equals("CONFIRMED");
        assertThat(state).isIn("CONFIRMED", "CANCELLED_BEFORE_CONFIRMATION");
        assertThat(responses.get(0).getStatus()).isEqualTo(confirmed ? 200 : 409);
        assertThat(responses.get(1).getStatus()).isEqualTo(confirmed ? 403 : 200);
        assertThat(responses.get(confirmed ? 1 : 0).getContentAsString())
                .contains(confirmed ? "ORDER_ACTION_NOT_ALLOWED" : "ORDER_STATE_CONFLICT");
        assertSinglePendingOutcome(fixture, confirmed);
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void confirmationAndSellerRejectionSerializeWithOneStateEvent() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        jdbc.update("update orders set status='PENDING_CONFIRMATION', confirmed_at=null where id=?", fixture.orderId());
        String seller = signed(fixture.seller());
        var responses = overlap(fixture,
                () -> command(fixture, seller, "confirm", null),
                () -> command(fixture, seller, "reject", "{\"reasonCode\":\"MERCHANT_UNAVAILABLE\"}"));
        assertThat(responses.stream().map(response -> response.getStatus()).toList()).containsExactlyInAnyOrder(200, 409);
        assertThat(responses.stream().filter(response -> response.getStatus() == 409).findFirst().orElseThrow().getContentAsString())
                .contains("ORDER_STATE_CONFLICT");
        String state = jdbc.queryForObject("select status from orders where id=?", String.class, fixture.orderId());
        assertThat(state).isIn("CONFIRMED", "REJECTED_BY_SELLER");
        assertSinglePendingOutcome(fixture, state.equals("CONFIRMED"));
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void buyerCompletionAndAutomaticCompletionSerializeWithOneSaleAndProjection() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        jdbc.update("update orders set status='AWAITING_BUYER_CONFIRMATION' where id=?", fixture.orderId());
        String buyer = signed(fixture.buyer());
        var responses = overlap(fixture,
                () -> command(fixture, buyer, "confirm-received", null),
                () -> {
                    scheduledTransitions.autoCompleteOne(fixture.orderId());
                    return new org.springframework.mock.web.MockHttpServletResponse();
                });
        assertThat(responses.get(0).getStatus()).isIn(200, 409);
        if (responses.get(0).getStatus() == 409) assertThat(responses.get(0).getContentAsString()).contains("ORDER_STATE_CONFLICT");
        assertThat(responses.get(1).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from orders where id=?", String.class, fixture.orderId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select event_type from order_events where order_id=?", String.class, fixture.orderId()))
                .isEqualTo(responses.get(0).getStatus() == 200 ? "BUYER_CONFIRMED_RECEIVED" : "ORDER_AUTO_COMPLETED");
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, fixture.orderId())).isEqualTo("COMMITTED");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=? and movement_type='SALE'", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForMap("select stock_on_hand,reserved_quantity from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", fixture.orderId()))
                .containsEntry("stock_on_hand", 1L).containsEntry("reserved_quantity", 0L);
        dispatch(fixture);
        assertThat(jdbc.queryForObject("select count(*) from reputation_order_outcomes where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
    }

    private org.springframework.mock.web.MockHttpServletResponse command(Fixture fixture, String bearer, String action, String body) throws Exception {
        var request = post("/api/v1/orders/" + fixture.orderId() + "/" + action)
                .header("Authorization", bearer).header("Idempotency-Key", action + "-" + fixture.orderId());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private List<org.springframework.mock.web.MockHttpServletResponse> overlap(Fixture fixture,
            java.util.concurrent.Callable<org.springframework.mock.web.MockHttpServletResponse> first,
            java.util.concurrent.Callable<org.springframework.mock.web.MockHttpServletResponse> second) throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var blocker = jdbc.getDataSource().getConnection();
             var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("select id from orders where id=? for update")) {
                lock.setObject(1, fixture.orderId());
                try (var row = lock.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            var firstResult = workers.submit(() -> { assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); return first.call(); });
            var secondResult = workers.submit(() -> { assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); return second.call(); });
            start.countDown();
            try {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                int waiting;
                do {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%orders%'", Integer.class);
                    if (waiting >= 2) break;
                    Thread.sleep(25);
                } while (System.nanoTime() < deadline);
                assertThat(waiting).as("both named actors reached the locked order").isGreaterThanOrEqualTo(2);
            } finally { blocker.commit(); }
            return List.of(firstResult.get(10, java.util.concurrent.TimeUnit.SECONDS), secondResult.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private void assertSinglePendingOutcome(Fixture fixture, boolean confirmed) {
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, fixture.orderId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, fixture.orderId())).isEqualTo(confirmed ? "ACTIVE" : "RELEASED");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=?", Long.class, fixture.orderId())).isEqualTo(confirmed ? 0L : 1L);
        assertThat(jdbc.queryForMap("select stock_on_hand,reserved_quantity from inventory_balances b join inventory_reservations r on r.location_variant_id=b.location_variant_id where r.order_id=?", fixture.orderId()))
                .containsEntry("stock_on_hand", 2L).containsEntry("reserved_quantity", confirmed ? 1L : 0L);
    }

    private void seedActiveReservation(Fixture fixture) {
        UUID categoryId = UUID.randomUUID(), itemId = UUID.randomUUID(), variantId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID(), orderItemId = UUID.randomUUID(), reservationId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,?,?,'PRODUCT','ACTIVE',0,0)", categoryId, "Food", "food-" + categoryId);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?,'PRODUCT','STANDARD',?,?,?,'ACTIVE','APPROVED',?,?,0)", itemId, fixture.merchantId(), categoryId, "Rice", "rice-" + itemId, now, now);
        jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values (?,?,?,true,'ACTIVE',?,?,0)", variantId, itemId, "Default", now, now);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,created_at,updated_at,version) values (?,?,?,10000,'VND',true,'TRACKED',?,?,0)", offerId, fixture.locationId(), variantId, now, now);
        jdbc.update("insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values (?,?,?, ?,0)", offerId, 2, 1, now);
        jdbc.update("insert into order_items(id,order_id,catalog_item_id,variant_id,location_variant_id,item_type,quantity,unit_price,line_total,currency_code,item_snapshot,created_at) values (?,?,?,?,?,'PRODUCT',1,10000,10000,'VND','{}'::jsonb,?)", orderItemId, fixture.orderId(), itemId, variantId, offerId, now);
        jdbc.update("insert into inventory_reservations(id,location_variant_id,order_id,order_item_id,quantity,reservation_status,created_at) values (?,?,?,?,1,'ACTIVE',?)", reservationId, offerId, fixture.orderId(), orderItemId, now);
    }

    private Actor actor(String prefix, Instant now) {
        UUID userId = UUID.randomUUID(), sessionId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(userId, prefix + "-" + userId, now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plusSeconds(86_400)));
        return new Actor(userId, sessionId);
    }

    private String signed(Actor actor) {
        return "Bearer " + issuer.issue(actor.userId(), new vn.covie.auth.model.DeviceMetadata("Order lifecycle", "order-http")).accessToken().value();
    }

    private void dispatch(Fixture fixture) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and status<>'PUBLISHED'", Long.class, fixture.orderId()) == 0) break;
            if (!outboxProcessor.processOne()) {
                try { Thread.sleep(10); }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting for fixture outbox publication", exception);
                }
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and status<>'PUBLISHED'", Long.class, fixture.orderId())).isZero();
    }

    private record Fixture(Actor seller, Actor buyer, UUID merchantId, UUID locationId, UUID orderId) {}
    private record Actor(UUID userId, UUID sessionId) {
        org.springframework.test.web.servlet.request.RequestPostProcessor jwt() {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString()).claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}



