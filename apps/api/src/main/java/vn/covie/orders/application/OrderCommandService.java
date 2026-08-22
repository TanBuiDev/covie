package vn.covie.orders.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
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
    private final vn.covie.shared.time.AppClock clock;

    public OrderCommandService(IdempotencyExecutor idempotency, OrderTransitionService transitions,
            OrderAccessPolicy access, CurrentActorProvider actors, OrderEventService events,
            OutboxEventWriter outbox, vn.covie.shared.time.AppClock clock) {
        this.idempotency = idempotency; this.transitions = transitions; this.access = access; this.actors = actors;
        this.events = events; this.outbox = outbox; this.clock = clock;
    }

    public OrderCommandResult confirm(UUID orderId, String idempotencyKey) {
        UUID actorId = actors.currentActor().userId();
        return idempotency.execute(actorId, "order.confirm:" + orderId, idempotencyKey,
                Map.of("orderId", orderId), 200, OrderCommandResult.class, () -> confirmLocked(orderId));
    }

    @Transactional
    protected OrderCommandResult confirmLocked(UUID orderId) {
        OrderEntity order = transitions.lock(orderId).order();
        UUID actorId = access.requireMerchantProcessor(order).actorUserId();
        transitions.requireStatus(order, OrderStatus.PENDING_CONFIRMATION);
        Instant now = clock.now();
        if (order.getConfirmationExpiresAt() == null || !now.isBefore(order.getConfirmationExpiresAt())) {
            throw new ApiException(ErrorCode.ORDER_STATE_CONFLICT, HttpStatus.CONFLICT,
                    "The order can no longer perform this action.", Map.of("currentStatus", order.getStatus().name()));
        }
        order.confirm(now);
        events.append(order, OrderEventType.SELLER_CONFIRMED, actorId, OrderActorContext.MERCHANT,
                OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED, null, null, Map.of());
        outbox.append(new OutboxEventIntent(OrderOutboxEvents.ORDER_CONFIRMED, "ORDER", order.getId(),
                Map.of("orderId", order.getId(), "orderCode", order.getOrderCode(), "status", order.getStatus().name())));
        return result(order);
    }

    private static OrderCommandResult result(OrderEntity order) {
        return new OrderCommandResult(order.getId(), order.getOrderCode(), order.getStatus(), order.getVersion(),
                order.getUpdatedAt(), order.getConfirmedAt(), order.getCompletedAt(), order.getClosedAt(), Set.of());
    }
}
