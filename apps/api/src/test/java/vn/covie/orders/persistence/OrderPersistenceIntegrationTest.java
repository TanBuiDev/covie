package vn.covie.orders.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class OrderPersistenceIntegrationTest extends PostgresIntegrationTest {
    @Autowired EntityManagerFactory entities;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;

    @Test
    void pessimisticOrderLockSerializesConcurrentLifecycleWrites() throws Exception {
        UUID orderId = createOrder();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Void> first = executor.submit(() -> {
                EntityManager entityManager = entities.createEntityManager();
                try {
                    entityManager.getTransaction().begin();
                    OrderEntity order = entityManager.find(OrderEntity.class, orderId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                    order.transitionTo(OrderStatus.CONFIRMED, Instant.parse("2026-08-22T01:00:00Z"));
                    firstLocked.countDown();
                    assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                    entityManager.getTransaction().commit();
                    return null;
                } finally {
                    if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
                    entityManager.close();
                }
            });
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<OrderStatus> second = executor.submit(() -> {
                EntityManager entityManager = entities.createEntityManager();
                try {
                    entityManager.getTransaction().begin();
                    OrderEntity locked = entityManager.find(OrderEntity.class, orderId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                    OrderStatus status = locked.getStatus();
                    entityManager.getTransaction().commit();
                    return status;
                } finally {
                    if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
                    entityManager.close();
                }
            });
            Thread.sleep(150);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    private UUID createOrder() {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        UUID buyer = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(buyer, "buyer-" + buyer, now));
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant, "Merchant", "merchant-" + merchant, now));
        UUID location = locations.saveAndFlush(MerchantLocationEntity.create(
                UUID.randomUUID(), merchant, "Main", "1 CoVie", 10.8, 106.7, now)).getId();
        OrderEntity order = OrderEntity.pending(UUID.randomUUID(), "CV" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), OrderType.PRODUCT,
                buyer, merchant, location, PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE, 0L,
                10_000, 10_000L, "VND", now.plusSeconds(1800), now);
        return orders.saveAndFlush(order).getId();
    }
}
