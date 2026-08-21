package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.checkout.application.CheckoutCandidate;
import vn.covie.checkout.application.CheckoutCandidateItem;
import vn.covie.checkout.application.FulfillmentSnapshots;
import vn.covie.checkout.application.RevalidatedCheckout;
import vn.covie.checkout.application.RevalidatedCheckoutItem;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventEntity;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.persistence.OrderEventRepository;
import vn.covie.orders.persistence.OrderItemRepository;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.orders.persistence.ProductFulfillmentRepository;
import vn.covie.orders.persistence.ServiceFulfillmentRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.id.UuidV7Generator;

class OrderCreationServiceTest {
    @Test
    void createsPendingProductPickupWithSortedItemsAndInitialEvent() {
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.existsByOrderCode(any())).thenReturn(false);
        OrderItemRepository items = mock(OrderItemRepository.class);
        ProductFulfillmentRepository productFulfillments = mock(ProductFulfillmentRepository.class);
        OrderEventRepository events = mock(OrderEventRepository.class);
        OrderCreationService service = new OrderCreationService(orders, items, productFulfillments,
                mock(ServiceFulfillmentRepository.class), events, new OrderCodeGenerator(() -> Instant.parse("2026-08-22T00:00:00Z")),
                new OrderSnapshotFactory(), new UuidV7Generator(), () -> Instant.parse("2026-08-22T00:00:00Z"));
        ObjectMapper mapper = new ObjectMapper();
        UUID buyer = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        CheckoutCandidate candidate = new CheckoutCandidate(CheckoutSourceType.DIRECT, buyer, UUID.randomUUID(), location,
                CatalogItemType.PRODUCT, List.of(new CheckoutCandidateItem(UUID.fromString("00000000-0000-0000-0000-000000000002"), UUID.randomUUID(), UUID.randomUUID(), 1),
                        new CheckoutCandidateItem(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.randomUUID(), UUID.randomUUID(), 1)), null, mapper.createObjectNode());
        RevalidatedCheckout checkout = new RevalidatedCheckout(candidate, FulfillmentMethod.PICKUP, PaymentMethod.COD,
                List.of(new RevalidatedCheckoutItem(candidate.items().get(0), 20_000, mapper.createObjectNode()),
                        new RevalidatedCheckoutItem(candidate.items().get(1), 10_000, mapper.createObjectNode())),
                30_000, "VND", null, new FulfillmentSnapshots(null, null, mapper.createObjectNode(), null,
                        mapper.createObjectNode(), mapper.createObjectNode(), null));

        CreatedOrder created = service.createPending(checkout);

        ArgumentCaptor<OrderEntity> savedOrder = ArgumentCaptor.forClass(OrderEntity.class);
        ArgumentCaptor<OrderEventEntity> savedEvent = ArgumentCaptor.forClass(OrderEventEntity.class);
        verify(orders).save(savedOrder.capture());
        verify(events).save(savedEvent.capture());
        verify(productFulfillments).save(any());
        assertThat(savedOrder.getValue().getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
        assertThat(created.orderItemIdsByLocationVariant()).hasSize(2);
    }
}
