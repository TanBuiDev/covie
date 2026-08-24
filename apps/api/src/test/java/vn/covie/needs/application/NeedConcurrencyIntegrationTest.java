package vn.covie.needs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.needs.model.NeedResponseEntity;
import vn.covie.needs.model.NeedResponseItemEntity;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.needs.persistence.NeedResponseItemRepository;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderSourceType;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.support.PostgresIntegrationTest;

class NeedConcurrencyIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired NeedRequestRepository needs;
    @Autowired PlatformTransactionManager transactionManager;

    private final UUID buyerId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();
    private final UUID needId = UUID.randomUUID();
    private final UUID orderOneId = UUID.randomUUID();
    private final UUID orderTwoId = UUID.randomUUID();

    @BeforeEach
    void seed() {
        Instant now = Instant.parse("2026-08-24T12:00:00Z");
        jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,?, 'ACTIVE',?,?)",
                buyerId, "Buyer", now, now);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order) values (?,?,?,'PRODUCT','ACTIVE',0)",
                categoryId, "Plan 06 category", "plan-06-" + categoryId);
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,?,?,'ACTIVE','NOT_REQUIRED',?,?)",
                merchantId, "Merchant", "merchant-" + merchantId, now, now);
        jdbc.update("""
                insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,
                    pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at)
                values (?,?,?, ?, ST_SetSRID(ST_MakePoint(106.7,10.8),4326)::geography,'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,true,false,'VND',?,?)
                """, locationId, merchantId, "Location", "Ho Chi Minh City", now, now);
        insertPlaceholderOrder(orderOneId, "P06-A-" + orderOneId, now);
        insertPlaceholderOrder(orderTwoId, "P06-B-" + orderTwoId, now);
        jdbc.update("""
                insert into need_requests(id,buyer_user_id,need_type,category_id,title,query_text,requested_quantity,geo_point,
                    location_snapshot,search_radius_meters,request_status,expires_at,created_at,updated_at)
                values (?,?, 'PRODUCT',?,?,?,1,ST_SetSRID(ST_MakePoint(106.7,10.8),4326)::geography,'{}'::jsonb,5000,'ACTIVE',?,?,?)
                """, needId, buyerId, categoryId, "Need", "query", now.plusSeconds(3600), now, now);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from need_requests where id=?", needId);
        jdbc.update("delete from orders where id in (?,?)", orderOneId, orderTwoId);
        jdbc.update("delete from merchant_locations where id=?", locationId);
        jdbc.update("delete from merchant_profiles where id=?", merchantId);
        jdbc.update("delete from categories where id=?", categoryId);
        jdbc.update("delete from users where id=?", buyerId);
    }

    @Test
    void twoConcurrentSourceConfirmationsFulfillNeedExactlyOnce() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        NeedResponseItemRepository items = mock(NeedResponseItemRepository.class);
        NeedResponseRepository responses = mock(NeedResponseRepository.class);
        NeedResponseItemEntity item = mock(NeedResponseItemEntity.class);
        NeedResponseEntity response = mock(NeedResponseEntity.class);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(item.getNeedResponseId()).thenReturn(responseId);
        when(responses.findById(responseId)).thenReturn(Optional.of(response));
        when(response.getNeedRequestId()).thenReturn(needId);
        OutboxEventWriter outbox = mock(OutboxEventWriter.class);
        NeedFulfillmentOnOrderConfirm participant = new NeedFulfillmentOnOrderConfirm(
                needs, responses, items, outbox, () -> Instant.parse("2026-08-24T12:00:10Z"));

        OrderEntity orderOne = sourceOrder(orderOneId, itemId);
        OrderEntity orderTwo = sourceOrder(orderTwoId, itemId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> runAfter(start, () -> tx.executeWithoutResult(ignored -> participant.onConfirmed(orderOne, buyerId))));
            Future<?> second = executor.submit(() -> runAfter(start, () -> tx.executeWithoutResult(ignored -> participant.onConfirmed(orderTwo, buyerId))));
            start.countDown();
            first.get();
            second.get();
        }

        var persisted = needs.findById(needId).orElseThrow();
        assertThat(persisted.getRequestStatus()).isEqualTo(NeedStatus.FULFILLED);
        assertThat(persisted.getFulfilledOrderId()).isIn(Set.of(orderOneId, orderTwoId));
        verify(outbox, times(1)).append(org.mockito.ArgumentMatchers.any());
    }

    private void insertPlaceholderOrder(UUID orderId, String code, Instant now) {
        jdbc.update("""
                insert into orders(id,order_code,order_type,buyer_user_id,merchant_profile_id,merchant_location_id,status,source_type,
                    payment_method,subtotal,shipping_fee_type,shipping_fee,known_total,total_amount,currency_code,
                    confirmation_expires_at,created_at,updated_at)
                values (?,?, 'PRODUCT',?,?,?,'PENDING_CONFIRMATION','MARKETPLACE','COD',0,'FREE',0,0,0,'VND',?,?,?)
                """, orderId, code, buyerId, merchantId, locationId, now.plusSeconds(600), now, now);
    }

    private OrderEntity sourceOrder(UUID orderId, UUID itemId) {
        OrderEntity order = mock(OrderEntity.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getSourceType()).thenReturn(OrderSourceType.NEED_REQUEST);
        when(order.getSourceNeedRequestId()).thenReturn(needId);
        when(order.getSourceNeedResponseItemId()).thenReturn(itemId);
        return order;
    }

    private static void runAfter(CountDownLatch start, Runnable action) {
        try {
            start.await();
            action.run();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
