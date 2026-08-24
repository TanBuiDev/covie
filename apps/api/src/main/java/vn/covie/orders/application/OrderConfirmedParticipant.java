package vn.covie.orders.application;

import java.util.UUID;
import vn.covie.orders.model.OrderEntity;

/**
 * Synchronous transaction-bound participant invoked after an Order is validated
 * and transitioned to CONFIRMED. Implementations must not perform network I/O.
 */
public interface OrderConfirmedParticipant {
    void onConfirmed(OrderEntity order, UUID confirmingActorUserId);
}
