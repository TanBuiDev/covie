package vn.covie.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.outbox.application.OutboxEventContext;
import vn.covie.reviews.model.ReviewEntity;
import vn.covie.reviews.persistence.ReviewRepository;

class ReviewNotificationHandlerTest {
    @Test
    void notifiesLocationScopedMerchantRecipientsAndSuppressesDuplicateNotificationSignal() {
        var reviews=mock(ReviewRepository.class); var orders=mock(OrderRepository.class); var recipients=mock(MerchantNotificationRecipientResolver.class); var notifications=mock(NotificationService.class);
        UUID reviewId=UUID.randomUUID(), orderId=UUID.randomUUID(), eventId=UUID.randomUUID(), merchant=UUID.randomUUID(), location=UUID.randomUUID(), owner=UUID.randomUUID(), staff=UUID.randomUUID(), item=UUID.randomUUID();
        var review=mock(ReviewEntity.class); when(review.getId()).thenReturn(reviewId); when(review.getOrderId()).thenReturn(orderId); when(review.getCatalogItemId()).thenReturn(item); when(review.getRating()).thenReturn((short)5);
        var order=mock(OrderEntity.class); when(order.getMerchantProfileId()).thenReturn(merchant); when(order.getMerchantLocationId()).thenReturn(location);
        when(reviews.findById(reviewId)).thenReturn(java.util.Optional.of(review)); when(orders.findById(orderId)).thenReturn(java.util.Optional.of(order)); when(recipients.processorsForLocation(merchant,location)).thenReturn(Set.of(owner,staff));
        when(notifications.createFromOutbox(eq(eventId),any(),any(),anyString(),anyString(),eq("REVIEW"),eq(reviewId),anyMap()))
                .thenAnswer(invocation -> new NotificationCreationResult(UUID.randomUUID(),invocation.getArgument(1).equals(owner)));

        var signals=new ReviewNotificationHandler(reviews,orders,recipients,notifications).handle(new OutboxEventContext(eventId,"REVIEW_CREATED","REVIEW",reviewId,JsonNodeFactory.instance.objectNode(),Instant.now()));

        verify(notifications,times(2)).createFromOutbox(eq(eventId),any(),eq(vn.covie.notifications.model.NotificationType.REVIEW_RECEIVED),anyString(),anyString(),eq("REVIEW"),eq(reviewId),argThat(payload -> payload.keySet().containsAll(Set.of("reviewId","catalogItemId","orderId","rating"))));
        assertThat(signals).extracting(signal -> signal.eventName()).containsExactlyInAnyOrder("notification.created","review.created","review.created");
        assertThat(signals).extracting(signal -> signal.userId()).containsExactlyInAnyOrder(owner,owner,staff);
    }
}
