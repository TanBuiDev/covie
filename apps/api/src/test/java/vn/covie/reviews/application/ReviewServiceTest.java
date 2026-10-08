package vn.covie.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.NullNode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderItemEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderItemRepository;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.reviews.api.dto.CreateReviewRequest;
import vn.covie.reviews.persistence.ReviewRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class ReviewServiceTest {
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ReviewPolicyProvider policy = mock(ReviewPolicyProvider.class);
    private final ReviewEligibilityEvaluator eligibility = new ReviewEligibilityEvaluator();
    private final RatingProjectionService projections = mock(RatingProjectionService.class);
    private final OutboxEventWriter outbox = mock(OutboxEventWriter.class);
    private final ReviewService service = new ReviewService(mock(vn.covie.auth.security.CurrentActorProvider.class), items, orders, reviews,
            policy, eligibility, projections, outbox, mock(IdempotencyExecutor.class), new UuidV7Generator(), () -> NOW);
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void completedOwnedOrderItemCanBeReviewed() {
        Fixture fixture = completedFixture();
        when(policy.creationEnabled()).thenReturn(true);
        when(items.findById(fixture.item.getId())).thenReturn(Optional.of(fixture.item));
        when(orders.lockById(fixture.order.getId())).thenReturn(Optional.of(fixture.order));

        var response = service.createInsideTransaction(fixture.buyerId, new CreateReviewRequest(fixture.item.getId(), 5, "Great"));

        assertThat(response.orderItemId()).isEqualTo(fixture.item.getId());
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(projections).add(any());
        verify(outbox).append(any());
    }

    @Test
    void confirmedOrderCannotBeReviewedAndForeignOrderItemIsNotExposed() {
        Fixture fixture = completedFixture();
        fixture.order.confirm(NOW);
        when(policy.creationEnabled()).thenReturn(true);
        when(items.findById(fixture.item.getId())).thenReturn(Optional.of(fixture.item));
        when(orders.lockById(fixture.order.getId())).thenReturn(Optional.of(fixture.order));
        assertApiError(() -> service.createInsideTransaction(fixture.buyerId, new CreateReviewRequest(fixture.item.getId(), 5, null)),
                ErrorCode.REVIEW_ORDER_NOT_COMPLETED, HttpStatus.CONFLICT);
        assertApiError(() -> service.createInsideTransaction(UUID.randomUUID(), new CreateReviewRequest(fixture.item.getId(), 5, null)),
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    void sharedEligibilityReasonsMapToTheExistingCreationErrors() {
        Fixture fixture = completedFixture();
        when(items.findById(fixture.item.getId())).thenReturn(Optional.of(fixture.item));
        when(orders.lockById(fixture.order.getId())).thenReturn(Optional.of(fixture.order));

        when(policy.creationEnabled()).thenReturn(false);
        assertApiError(() -> service.createInsideTransaction(fixture.buyerId,
                        new CreateReviewRequest(fixture.item.getId(), 5, null)),
                ErrorCode.REVIEW_CREATION_DISABLED, HttpStatus.UNPROCESSABLE_ENTITY);

        when(policy.creationEnabled()).thenReturn(true);
        when(reviews.existsByOrderItemId(fixture.item.getId())).thenReturn(true);
        assertApiError(() -> service.createInsideTransaction(fixture.buyerId,
                        new CreateReviewRequest(fixture.item.getId(), 5, null)),
                ErrorCode.REVIEW_ALREADY_EXISTS, HttpStatus.CONFLICT);
    }

    @Test
    void itemWithoutACatalogTargetRemainsHiddenAsNotFound() {
        Fixture fixture = completedFixture(null);
        when(policy.creationEnabled()).thenReturn(true);
        when(items.findById(fixture.item.getId())).thenReturn(Optional.of(fixture.item));
        when(orders.lockById(fixture.order.getId())).thenReturn(Optional.of(fixture.order));

        assertApiError(() -> service.createInsideTransaction(fixture.buyerId,
                        new CreateReviewRequest(fixture.item.getId(), 5, null)),
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    private void assertApiError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(ApiException.class, error -> { assertThat(error.code()).isEqualTo(code); assertThat(error.status()).isEqualTo(status); });
    }

    private Fixture completedFixture() {
        return completedFixture(UUID.randomUUID());
    }

    private Fixture completedFixture(UUID catalog) {
        UUID buyer = UUID.randomUUID(), merchant = UUID.randomUUID(), orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(orderId, "review-test", OrderType.PRODUCT, buyer, merchant, UUID.randomUUID(), PaymentMethod.COD, 100, OrderShippingFeeType.FREE, 0L, 100, 100L, "VND", NOW.plusSeconds(60), NOW);
        order.complete(NOW);
        OrderItemEntity item = OrderItemEntity.create(UUID.randomUUID(), orderId, catalog, UUID.randomUUID(), UUID.randomUUID(), CatalogItemType.PRODUCT, 1, 100, "VND", NullNode.getInstance(), NOW);
        return new Fixture(buyer, order, item);
    }
    private record Fixture(UUID buyerId, OrderEntity order, OrderItemEntity item) {}
}
