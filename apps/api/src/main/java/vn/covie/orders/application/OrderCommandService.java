package vn.covie.orders.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.orders.api.dto.CancelOrderRequest;
import vn.covie.orders.api.dto.OrderCommandResult;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderCommandService {
    private final IdempotencyExecutor idempotency;
    private final OrderTransitionService transitions;
    private final OrderAccessPolicy access;
    private final CurrentActorProvider actors;
    private final OrderEventService events;
    private final OutboxEventWriter outbox;
    private final vn.covie.inventory.application.InventoryReservationService inventory;
    private final vn.covie.shared.time.AppClock clock;
    private final List<OrderConfirmedParticipant> confirmedParticipants;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderCommandService(
            IdempotencyExecutor idempotency,
            OrderTransitionService transitions,
            OrderAccessPolicy access,
            CurrentActorProvider actors,
            OrderEventService events,
            OutboxEventWriter outbox,
            vn.covie.inventory.application.InventoryReservationService inventory,
            vn.covie.shared.time.AppClock clock,
            List<OrderConfirmedParticipant> confirmedParticipants) {
        this.idempotency = idempotency;
        this.transitions = transitions;
        this.access = access;
        this.actors = actors;
        this.events = events;
        this.outbox = outbox;
        this.inventory = inventory;
        this.clock = clock;
        this.confirmedParticipants = List.copyOf(confirmedParticipants);
    }

    public OrderCommandService(
            IdempotencyExecutor idempotency,
            OrderTransitionService transitions,
            OrderAccessPolicy access,
            CurrentActorProvider actors,
            OrderEventService events,
            OutboxEventWriter outbox,
            vn.covie.inventory.application.InventoryReservationService inventory,
            vn.covie.shared.time.AppClock clock) {
        this(idempotency, transitions, access, actors, events, outbox, inventory, clock, List.of());
    }

    public OrderCommandService(
            IdempotencyExecutor idempotency,
            OrderTransitionService transitions,
            OrderAccessPolicy access,
            CurrentActorProvider actors,
            OrderEventService events,
            OutboxEventWriter outbox,
            vn.covie.shared.time.AppClock clock) {
        this(idempotency, transitions, access, actors, events, outbox, null, clock, List.of());
    }

    public OrderCommandResult confirm(UUID orderId, String idempotencyKey) {
        UUID actorId = actors.currentActor().userId();
        return idempotency.execute(actorId, "order.confirm:" + orderId, idempotencyKey,
                Map.of("orderId", orderId), 200, OrderCommandResult.class, () -> confirmLocked(orderId));
    }

    public OrderCommandResult reject(UUID orderId, String idempotencyKey, String reasonCode, String note) {
        UUID actorId = actors.currentActor().userId();
        Map<String, Object> body = Map.of(
                "orderId", orderId,
                "reasonCode", reasonCode,
                "note", note == null ? "" : note);
        return idempotency.execute(actorId, "order.reject:" + orderId, idempotencyKey, body, 200,
                OrderCommandResult.class, () -> rejectLocked(orderId, reasonCode, note));
    }

    public OrderCommandResult cancel(UUID orderId, String idempotencyKey, CancelOrderRequest request) {
        UUID actorId = actors.currentActor().userId();
        CancelOrderRequest normalized = request == null ? new CancelOrderRequest(null, null) : request;
        return idempotency.execute(actorId, "order.cancel:" + orderId, idempotencyKey, normalized, 200,
                OrderCommandResult.class, () -> cancelLocked(orderId, normalized));
    }

    @Transactional
    protected OrderCommandResult confirmLocked(UUID orderId) {
        OrderEntity order = transitions.lock(orderId).order();
        UUID actorId = access.requireMerchantProcessor(order).actorUserId();
        transitions.requireStatus(order, OrderStatus.PENDING_CONFIRMATION);
        Instant now = clock.now();
        if (order.getConfirmationExpiresAt() == null || !now.isBefore(order.getConfirmationExpiresAt())) {
            throw new ApiException(
                    ErrorCode.ORDER_STATE_CONFLICT,
                    HttpStatus.CONFLICT,
                    "The order can no longer perform this action.",
                    Map.of("currentStatus", order.getStatus().name()));
        }

        order.confirm(now);
        for (OrderConfirmedParticipant participant : confirmedParticipants) {
            participant.onConfirmed(order, actorId);
        }
        events.append(
                order,
                OrderEventType.SELLER_CONFIRMED,
                actorId,
                OrderActorContext.MERCHANT,
                OrderStatus.PENDING_CONFIRMATION,
                OrderStatus.CONFIRMED,
                null,
                null,
                Map.of());
        outbox.append(new OutboxEventIntent(
                OrderOutboxEvents.ORDER_CONFIRMED,
                "ORDER",
                order.getId(),
                Map.of(
                        "orderId", order.getId(),
                        "orderCode", order.getOrderCode(),
                        "status", order.getStatus().name())));
        return result(order);
    }

    @Transactional
    protected OrderCommandResult rejectLocked(UUID orderId, String reasonCode, String note) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "reasonCode is required.");
        }
        OrderEntity order = transitions.lock(orderId).order();
        UUID actorId = access.requireMerchantProcessor(order).actorUserId();
        transitions.requireStatus(order, OrderStatus.PENDING_CONFIRMATION);
        Instant now = clock.now();
        if (inventory == null) throw new IllegalStateException("Inventory reservation service is required.");
        inventory.releaseActiveForOrder(orderId, actorId, reasonCode, now);
        order.close(OrderStatus.REJECTED_BY_SELLER, now);
        events.append(
                order,
                OrderEventType.SELLER_REJECTED,
                actorId,
                OrderActorContext.MERCHANT,
                OrderStatus.PENDING_CONFIRMATION,
                OrderStatus.REJECTED_BY_SELLER,
                reasonCode,
                note,
                Map.of());
        outbox.append(new OutboxEventIntent(
                OrderOutboxEvents.ORDER_REJECTED,
                "ORDER",
                order.getId(),
                Map.of(
                        "orderId", order.getId(),
                        "orderCode", order.getOrderCode(),
                        "status", order.getStatus().name())));
        return result(order);
    }

    @Transactional
    protected OrderCommandResult cancelLocked(UUID orderId, CancelOrderRequest request) {
        OrderEntity order = transitions.lock(orderId).order();
        if (actors.currentActor().userId().equals(order.getBuyerUserId())) {
            if (order.getStatus() == OrderStatus.CONFIRMED
                    || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY
                    || order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
                throw new ApiException(
                        ErrorCode.ORDER_ACTION_NOT_ALLOWED,
                        HttpStatus.FORBIDDEN,
                        "Request cancellation for a confirmed order instead.");
            }
            return cancelAsBuyer(order, request);
        }
        return cancelAsMerchant(order, request);
    }

    private OrderCommandResult cancelAsBuyer(OrderEntity order, CancelOrderRequest request) {
        UUID actorId = access.requireBuyer(order).actorUserId();
        transitions.requireStatus(order, OrderStatus.PENDING_CONFIRMATION);
        Instant now = clock.now();
        if (order.getConfirmationExpiresAt() == null || !now.isBefore(order.getConfirmationExpiresAt())) {
            throw new ApiException(
                    ErrorCode.ORDER_STATE_CONFLICT,
                    HttpStatus.CONFLICT,
                    "The order can no longer perform this action.",
                    Map.of("currentStatus", order.getStatus().name()));
        }
        if (inventory == null) throw new IllegalStateException("Inventory reservation service is required.");
        inventory.releaseActiveForOrder(
                order.getId(),
                actorId,
                request.reasonCode() == null || request.reasonCode().isBlank()
                        ? "BUYER_CANCELLED"
                        : request.reasonCode(),
                now);
        order.close(OrderStatus.CANCELLED_BEFORE_CONFIRMATION, now);
        events.append(
                order,
                OrderEventType.BUYER_CANCELLED_BEFORE_CONFIRMATION,
                actorId,
                OrderActorContext.BUYER,
                OrderStatus.PENDING_CONFIRMATION,
                OrderStatus.CANCELLED_BEFORE_CONFIRMATION,
                request.reasonCode(),
                request.note(),
                Map.of());
        outbox.append(new OutboxEventIntent(
                OrderOutboxEvents.ORDER_CANCELLED,
                "ORDER",
                order.getId(),
                Map.of(
                        "orderId", order.getId(),
                        "orderCode", order.getOrderCode(),
                        "status", order.getStatus().name())));
        return result(order);
    }

    private OrderCommandResult cancelAsMerchant(OrderEntity order, CancelOrderRequest request) {
        if (request.reasonCode() == null || request.reasonCode().isBlank()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "reasonCode is required.");
        }
        UUID actorId = access.requireMerchantProcessor(order).actorUserId();
        transitions.requireStatus(order,
                OrderStatus.CONFIRMED,
                OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.READY_FOR_PICKUP);
        Instant now = clock.now();
        if (inventory == null) throw new IllegalStateException("Inventory reservation service is required.");
        OrderStatus before = order.getStatus();
        inventory.releaseActiveForOrder(order.getId(), actorId, request.reasonCode(), now);
        order.close(OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION, now);
        events.append(
                order,
                OrderEventType.SELLER_CANCELLED_AFTER_CONFIRMATION,
                actorId,
                OrderActorContext.MERCHANT,
                before,
                OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION,
                request.reasonCode(),
                request.note(),
                Map.of());
        outbox.append(new OutboxEventIntent(
                OrderOutboxEvents.ORDER_CANCELLED,
                "ORDER",
                order.getId(),
                Map.of(
                        "orderId", order.getId(),
                        "orderCode", order.getOrderCode(),
                        "status", order.getStatus().name())));
        return result(order);
    }

    private static OrderCommandResult result(OrderEntity order) {
        return new OrderCommandResult(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getVersion(),
                order.getUpdatedAt(),
                order.getConfirmedAt(),
                order.getCompletedAt(),
                order.getClosedAt(),
                Set.of());
    }
}
