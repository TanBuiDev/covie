package vn.covie.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.notifications.model.NotificationType;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.outbox.application.OutboxEventContext;

class OrderNotificationHandlerTest {
    @Test
    void closedWithIssueNotifiesBuyerAndLocationScopedMerchantProcessors() {
        var orders=mock(OrderRepository.class); var recipients=mock(MerchantNotificationRecipientResolver.class); var notifications=mock(NotificationService.class);
        UUID eventId=UUID.randomUUID(), orderId=UUID.randomUUID(), buyer=UUID.randomUUID(), merchantUser=UUID.randomUUID(), merchant=UUID.randomUUID(), location=UUID.randomUUID();
        var order=mock(OrderEntity.class); when(order.getId()).thenReturn(orderId); when(order.getBuyerUserId()).thenReturn(buyer); when(order.getMerchantProfileId()).thenReturn(merchant); when(order.getMerchantLocationId()).thenReturn(location); when(order.getOrderCode()).thenReturn("CV-ISSUE"); when(order.getStatus()).thenReturn(OrderStatus.CLOSED_WITH_ISSUE);
        when(orders.findById(orderId)).thenReturn(Optional.of(order)); when(recipients.processorsForLocation(merchant,location)).thenReturn(Set.of(merchantUser));
        when(notifications.createFromOutbox(eq(eventId),any(),eq(NotificationType.ORDER_CLOSED_WITH_ISSUE),anyString(),anyString(),eq("ORDER"),eq(orderId),anyMap())).thenAnswer(invocation -> new NotificationCreationResult(UUID.randomUUID(),true));

        var signals=new OrderNotificationHandler(orders,recipients,notifications).handle(new OutboxEventContext(eventId,"ORDER_CLOSED_WITH_ISSUE","ORDER",orderId,JsonNodeFactory.instance.objectNode(),Instant.now()));

        verify(notifications).createFromOutbox(eq(eventId),eq(buyer),eq(NotificationType.ORDER_CLOSED_WITH_ISSUE),anyString(),anyString(),eq("ORDER"),eq(orderId),anyMap());
        verify(notifications).createFromOutbox(eq(eventId),eq(merchantUser),eq(NotificationType.ORDER_CLOSED_WITH_ISSUE),anyString(),anyString(),eq("ORDER"),eq(orderId),anyMap());
        assertThat(signals).hasSize(4).allMatch(signal -> Set.of(buyer,merchantUser).contains(signal.userId()));
    }
}
