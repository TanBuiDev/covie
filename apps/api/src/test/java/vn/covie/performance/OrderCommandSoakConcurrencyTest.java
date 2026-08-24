package vn.covie.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.orders.api.dto.CancellationResponseBody;
import vn.covie.orders.api.dto.CancelOrderRequest;
import vn.covie.orders.api.dto.IssueRequest;
import vn.covie.orders.application.OrderCancellationService;
import vn.covie.orders.application.OrderCommandService;
import vn.covie.orders.application.OrderCompletionService;
import vn.covie.orders.application.OrderIssueService;
import vn.covie.orders.application.OrderScheduledTransitionService;
import vn.covie.orders.model.OrderCancellationRequestEntity;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderCancellationRequestRepository;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class OrderCommandSoakConcurrencyTest extends PostgresIntegrationTest {
    @Autowired OrderCancellationService cancellations;
    @Autowired OrderCommandService commands;
    @Autowired OrderCompletionService completion;
    @Autowired OrderIssueService issues;
    @Autowired OrderScheduledTransitionService scheduledTransitions;
    @Autowired OrderRepository orders;
    @Autowired OrderCancellationRequestRepository requests;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OrderAccessPolicy access;
    @MockitoBean CurrentActorProvider actors;

    @Test
    void repeatsOverlappingCancellationAcceptAndRejectWithoutDuplicateEvents() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            RaceFixture fixture = fixture();
            when(access.requireMerchantProcessor(any())).thenReturn(new OrderAccess(
                    fixture.merchantUserId(), OrderActorType.MERCHANT, fixture.orderId(), fixture.merchantId(), fixture.locationId()));

            CyclicBarrier ready = new CyclicBarrier(2);
            try (var pool = Executors.newFixedThreadPool(2)) {
                List<Future<Boolean>> results = pool.invokeAll(List.of(
                        respond(ready, () -> cancellations.accept(fixture.orderId(), new CancellationResponseBody("accepted"))),
                        respond(ready, () -> cancellations.reject(fixture.orderId(), new CancellationResponseBody("rejected")))));
                assertThat(results.stream().filter(OrderCommandSoakConcurrencyTest::result).count()).isEqualTo(1);
            }

            assertThat(jdbc.queryForObject("select count(*) from order_events where order_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from order_cancellation_requests where order_id = ? and request_status in ('ACCEPTED', 'REJECTED')", Integer.class, fixture.orderId())).isEqualTo(1);
        }
    }

    @Test
    void repeatsOverlappingBuyerCompletionAndSellerCancellationWithoutDuplicateEvents() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            RaceFixture fixture = confirmedFixture();
            when(actors.currentActor()).thenReturn(new CurrentActor(
                    fixture.merchantUserId(), java.util.Set.of(SystemRole.USER), UUID.randomUUID()));
            when(access.requireMerchantProcessor(any())).thenReturn(new OrderAccess(
                    fixture.merchantUserId(), OrderActorType.MERCHANT, fixture.orderId(), fixture.merchantId(), fixture.locationId()));
            when(access.requireBuyer(any())).thenReturn(new OrderAccess(
                    fixture.buyerId(), OrderActorType.BUYER, fixture.orderId(), fixture.merchantId(), fixture.locationId()));

            CyclicBarrier ready = new CyclicBarrier(2);
            try (var pool = Executors.newFixedThreadPool(2)) {
                List<Future<Boolean>> results = pool.invokeAll(List.of(
                        respond(ready, () -> completion.confirmReceived(fixture.orderId())),
                        respond(ready, () -> commands.cancel(fixture.orderId(), "seller-cancel-" + fixture.orderId(),
                                new CancelOrderRequest("MERCHANT_UNAVAILABLE", "Closed")))));
                assertThat(results.stream().filter(OrderCommandSoakConcurrencyTest::result).count()).isEqualTo(1);
            }

            assertThat(jdbc.queryForObject("select count(*) from order_events where order_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
        }
    }

    @Test
    void repeatsOverlappingBuyerIssueAndAutomaticCompletionWithoutDuplicateEvents() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            RaceFixture fixture = awaitingBuyerConfirmationFixture();
            when(access.requireBuyer(any())).thenReturn(new OrderAccess(
                    fixture.buyerId(), OrderActorType.BUYER, fixture.orderId(), fixture.merchantId(), fixture.locationId()));

            CyclicBarrier ready = new CyclicBarrier(2);
            try (var pool = Executors.newFixedThreadPool(2)) {
                List<Future<Boolean>> results = pool.invokeAll(List.of(
                        respond(ready, () -> issues.reportDeliveryIssue(fixture.orderId(), new IssueRequest("Not received"))),
                        respond(ready, () -> scheduledTransitions.autoCompleteOne(fixture.orderId()))));
                assertThat(results.stream().filter(OrderCommandSoakConcurrencyTest::result).count()).isGreaterThanOrEqualTo(1);
            }

            assertThat(jdbc.queryForObject("select count(*) from order_events where order_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, fixture.orderId()))
                    .isIn(OrderStatus.COMPLETED.name(), OrderStatus.DELIVERY_ISSUE_REPORTED.name());
        }
    }

    private Callable<Boolean> respond(CyclicBarrier ready, Runnable command) {
        return () -> {
            ready.await(5, TimeUnit.SECONDS);
            try {
                command.run();
                return true;
            } catch (ApiException expected) {
                return false;
            }
        };
    }

    private RaceFixture fixture() {
        return fixture(true);
    }

    private RaceFixture confirmedFixture() {
        return fixture(false);
    }

    private RaceFixture awaitingBuyerConfirmationFixture() {
        RaceFixture fixture = confirmedFixture();
        jdbc.update("update orders set status = 'AWAITING_BUYER_CONFIRMATION', updated_at = now() where id = ?", fixture.orderId());
        return fixture;
    }

    private RaceFixture fixture(boolean cancellationRequested) {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        UUID buyerId = UUID.randomUUID(), merchantUserId = UUID.randomUUID(), merchantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID(), orderId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(buyerId, "buyer-" + buyerId, now));
        users.saveAndFlush(UserEntity.active(merchantUserId, "merchant-" + merchantUserId, now));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values (?,?,?,'ACTIVE','APPROVED',?,?,0)", merchantId, "Store", "store-" + merchantId, timestamp, timestamp);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at,version) values (?,?,?, ?, ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'), 'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,false,false,'VND',?,?,0)", locationId, merchantId, "Main", "1 Street", timestamp, timestamp);

        OrderEntity order = OrderEntity.pending(orderId, "CV" + orderId.toString().substring(0, 8), OrderType.PRODUCT,
                buyerId, merchantId, locationId, PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE,
                0L, 10_000, 10_000L, "VND", now.plusSeconds(600), now);
        order.confirm(now);
        if (cancellationRequested) {
            order.transitionTo(OrderStatus.CANCELLATION_REQUESTED, now);
        }
        orders.saveAndFlush(order);
        if (cancellationRequested) {
            requests.saveAndFlush(OrderCancellationRequestEntity.pending(UUID.randomUUID(), orderId, buyerId,
                    "BUYER_CHANGED_MIND", null, OrderStatus.CONFIRMED, now));
        }
        return new RaceFixture(buyerId, merchantUserId, merchantId, locationId, orderId);
    }

    private static boolean result(Future<Boolean> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private record RaceFixture(UUID buyerId, UUID merchantUserId, UUID merchantId, UUID locationId, UUID orderId) {}
}
