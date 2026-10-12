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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("routingCases")
    void routesEachSupportedEventToItsSpecifiedRecipients(String eventType, OrderStatus status, NotificationType type, String target) {
        var orders = mock(OrderRepository.class);
        var recipients = mock(MerchantNotificationRecipientResolver.class);
        var notifications = mock(NotificationService.class);
        UUID eventId = UUID.randomUUID(), orderId = UUID.randomUUID(), buyer = UUID.randomUUID(), seller = UUID.randomUUID();
        UUID merchant = UUID.randomUUID(), location = UUID.randomUUID();
        var order = mock(OrderEntity.class);
        when(order.getId()).thenReturn(orderId); when(order.getBuyerUserId()).thenReturn(buyer);
        when(order.getMerchantProfileId()).thenReturn(merchant); when(order.getMerchantLocationId()).thenReturn(location);
        when(order.getOrderCode()).thenReturn("CV-MATRIX"); when(order.getStatus()).thenReturn(status);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(recipients.processorsForLocation(merchant, location)).thenReturn(target.equals("both") ? Set.of(buyer, seller) : Set.of(seller));
        when(notifications.createFromOutbox(eq(eventId), any(), eq(type), anyString(), anyString(), eq("ORDER"), eq(orderId), anyMap()))
                .thenAnswer(invocation -> new NotificationCreationResult(UUID.randomUUID(), true));
        Set<UUID> expected = switch (target) { case "buyer" -> Set.of(buyer); case "merchant" -> Set.of(seller); default -> Set.of(buyer, seller); };
        var handler = new OrderNotificationHandler(orders, recipients, notifications);
        var signals = handler.handle(new OutboxEventContext(eventId, eventType, "ORDER", orderId, JsonNodeFactory.instance.objectNode(), Instant.now()));
        var targets = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(notifications, times(expected.size())).createFromOutbox(eq(eventId), targets.capture(), eq(type), anyString(), anyString(), eq("ORDER"), eq(orderId), anyMap());
        assertThat(targets.getAllValues()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(signals).hasSize(expected.size() * 2).allMatch(signal -> expected.contains(signal.userId()));
        assertThat(signals.stream().filter(signal -> signal.eventName().equals("order.updated"))).hasSize(expected.size());
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> routingCases() {
        return java.util.stream.Stream.of(
                routing("ORDER_CREATED", OrderStatus.PENDING_CONFIRMATION, NotificationType.ORDER_NEW, "merchant"),
                routing("ORDER_CONFIRMED", OrderStatus.CONFIRMED, NotificationType.ORDER_CONFIRMED, "buyer"),
                routing("ORDER_REJECTED", OrderStatus.REJECTED_BY_SELLER, NotificationType.ORDER_REJECTED, "buyer"),
                routing("ORDER_EXPIRED", OrderStatus.EXPIRED, NotificationType.ORDER_EXPIRED, "buyer"),
                routing("ORDER_CANCELLATION_REQUESTED", OrderStatus.CANCELLATION_REQUESTED, NotificationType.ORDER_CANCELLATION_REQUESTED, "merchant"),
                routing("ORDER_CANCELLATION_RESPONDED", OrderStatus.CONFIRMED, NotificationType.ORDER_CANCELLATION_RESPONDED, "buyer"),
                routing("ORDER_CANCELLED", OrderStatus.CANCELLED_BEFORE_CONFIRMATION, NotificationType.ORDER_CANCELLED, "merchant"),
                routing("ORDER_CANCELLED", OrderStatus.CANCELLED_BY_BUYER_AFTER_CONFIRMATION, NotificationType.ORDER_CANCELLED, "merchant"),
                routing("ORDER_CANCELLED", OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION, NotificationType.ORDER_CANCELLED, "buyer"),
                routing("ORDER_FULFILLMENT_UPDATED", OrderStatus.BUYER_NO_SHOW, NotificationType.ORDER_FULFILLMENT_UPDATED, "buyer"),
                routing("ORDER_ISSUE_REPORTED", OrderStatus.NO_SHOW_CONTESTED, NotificationType.ORDER_ISSUE_REPORTED, "merchant"),
                routing("ORDER_COMPLETED", OrderStatus.COMPLETED, NotificationType.ORDER_COMPLETED, "both"),
                routing("ORDER_CLOSED_WITH_ISSUE", OrderStatus.CLOSED_WITH_ISSUE, NotificationType.ORDER_CLOSED_WITH_ISSUE, "both"));
    }

    private static org.junit.jupiter.params.provider.Arguments routing(String event, OrderStatus status, NotificationType type, String target) {
        return org.junit.jupiter.params.provider.Arguments.of(event, status, type, target);
    }
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
