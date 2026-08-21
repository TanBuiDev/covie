package vn.covie.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.cart.model.CartEntity;
import vn.covie.cart.model.CartStatus;
import vn.covie.cart.model.CartType;
import vn.covie.cart.persistence.CartItemRepository;
import vn.covie.cart.persistence.CartRepository;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.model.CheckoutSessionEntity;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.inventory.application.InventoryLockService;
import vn.covie.inventory.application.InventoryReservationService;
import vn.covie.orders.application.CreatedOrder;
import vn.covie.orders.application.OrderCreationService;
import vn.covie.orders.model.OrderStatus;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.outbox.OutboxEventWriter;

class CheckoutCommitServiceTest {
    @Test
    void createsOrderAppendsOutboxAndConsumesSessionAfterSuccessfulComparison() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID user = UUID.randomUUID();
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(CheckoutSourceType.DIRECT, null,
                List.of(new CheckoutPreviewRequest.DirectItem(UUID.randomUUID(), 1)), FulfillmentMethod.PICKUP,
                null, PaymentMethod.COD);
        CheckoutSessionEntity session = CheckoutSessionEntity.create(UUID.randomUUID(), "hash", user,
                CheckoutSourceType.DIRECT, mapper.createObjectNode(), null, mapper.valueToTree(request),
                mapper.valueToTree(new CheckoutComparableSnapshot(CheckoutSourceType.DIRECT, UUID.randomUUID(), null,
                        FulfillmentMethod.PICKUP, null, 10_000, "VND", List.of())),
                Instant.parse("2026-08-22T00:15:00Z"), Instant.parse("2026-08-22T00:00:00Z"));
        CheckoutSessionService sessions = mock(CheckoutSessionService.class);
        when(sessions.lock("chk_test", user)).thenReturn(session);
        CheckoutPreviewService previews = mock(CheckoutPreviewService.class);
        CheckoutRevalidation revalidated = mock(CheckoutRevalidation.class);
        when(revalidated.comparableSnapshot()).thenReturn(mapper.treeToValue(session.getPreviewSnapshot(), CheckoutComparableSnapshot.class));
        when(revalidated.trackedOfferIds()).thenReturn(java.util.Set.of());
        when(previews.revalidate(eq(user), any())).thenReturn(revalidated);
        when(previews.revalidate(eq(user), any(), anyMap())).thenReturn(revalidated);
        CheckoutComparisonService comparison = mock(CheckoutComparisonService.class);
        when(comparison.compare(any(), any())).thenReturn(List.of());
        OrderCreationService orders = mock(OrderCreationService.class);
        UUID orderId = UUID.randomUUID();
        when(orders.createPending(any())).thenReturn(new CreatedOrder(orderId, "CV-260822-ABC123", Map.of()));
        OutboxEventWriter outbox = mock(OutboxEventWriter.class);
        CheckoutCommitService service = new CheckoutCommitService(sessions, previews, comparison, orders,
                mock(InventoryLockService.class), mock(InventoryReservationService.class), outbox, mapper,
                () -> Instant.parse("2026-08-22T00:01:00Z"), mock(CartRepository.class), mock(CartItemRepository.class));

        var result = service.commit(user, "chk_test");

        assertThat(result.id()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
        assertThat(session.getConsumedAt()).isEqualTo(Instant.parse("2026-08-22T00:01:00Z"));
        verify(outbox).append(any());
    }

    @Test
    void checksOutTheLockedCartOnlyAfterOrderCreationSucceeds() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID user = UUID.randomUUID(), cartId = UUID.randomUUID();
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(CheckoutSourceType.CART, cartId, null,
                FulfillmentMethod.PICKUP, null, PaymentMethod.COD);
        CheckoutSessionEntity session = CheckoutSessionEntity.create(UUID.randomUUID(), "hash", user,
                CheckoutSourceType.CART, mapper.createObjectNode(), 0L, mapper.valueToTree(request),
                mapper.valueToTree(new CheckoutComparableSnapshot(CheckoutSourceType.CART, UUID.randomUUID(), 0L,
                        FulfillmentMethod.PICKUP, null, 10_000, "VND", List.of())),
                Instant.parse("2026-08-22T00:15:00Z"), Instant.parse("2026-08-22T00:00:00Z"));
        CheckoutSessionService sessions = mock(CheckoutSessionService.class);
        when(sessions.lock("chk_test", user)).thenReturn(session);
        CartEntity cart = CartEntity.active(cartId, user, UUID.randomUUID(), CartType.PRODUCT, Instant.EPOCH);
        CartRepository carts = mock(CartRepository.class);
        when(carts.findActiveByIdAndUserIdForUpdate(cartId, user)).thenReturn(java.util.Optional.of(cart));
        CheckoutPreviewService previews = mock(CheckoutPreviewService.class);
        CheckoutRevalidation revalidated = mock(CheckoutRevalidation.class);
        when(revalidated.comparableSnapshot()).thenReturn(mapper.treeToValue(session.getPreviewSnapshot(), CheckoutComparableSnapshot.class));
        when(revalidated.trackedOfferIds()).thenReturn(java.util.Set.of());
        when(previews.revalidate(eq(user), any())).thenReturn(revalidated);
        when(previews.revalidate(eq(user), any(), anyMap())).thenReturn(revalidated);
        CheckoutComparisonService comparison = mock(CheckoutComparisonService.class);
        when(comparison.compare(any(), any())).thenReturn(List.of());
        OrderCreationService orders = mock(OrderCreationService.class);
        when(orders.createPending(any())).thenReturn(new CreatedOrder(UUID.randomUUID(), "CV-260822-ABC123", Map.of()));
        CartItemRepository cartItems = mock(CartItemRepository.class);
        CheckoutCommitService service = new CheckoutCommitService(sessions, previews, comparison, orders,
                mock(InventoryLockService.class), mock(InventoryReservationService.class), mock(OutboxEventWriter.class), mapper,
                () -> Instant.parse("2026-08-22T00:01:00Z"), carts, cartItems);

        service.commit(user, "chk_test");

        assertThat(cart.getCartStatus()).isEqualTo(CartStatus.CHECKED_OUT);
        verify(cartItems).deleteAllByCartId(cartId);
    }
}
