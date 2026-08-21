package vn.covie.checkout.application;

import vn.covie.orders.application.CreatedOrder;

/** Internal seam used by transactional integration tests to verify rollback boundaries. */
@FunctionalInterface
public interface CheckoutCommitHook {
    void afterOrderCreated(CreatedOrder order);
}
