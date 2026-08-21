package vn.covie.checkout.application;

import org.springframework.stereotype.Component;
import vn.covie.orders.application.CreatedOrder;

@Component
final class NoOpCheckoutCommitHook implements CheckoutCommitHook {
    @Override
    public void afterOrderCreated(CreatedOrder order) {
        // Intentionally empty: tests replace this collaborator to force a rollback.
    }
}
