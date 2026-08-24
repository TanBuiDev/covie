package vn.covie.orders.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConfigOrderPolicyProvider implements OrderPolicyProvider {
    private final OrderPolicySnapshot policy;

    public ConfigOrderPolicyProvider(OrderLifecycleProperties properties) {
        this.policy = new OrderPolicySnapshot(properties.confirmationTimeout(), properties.autoCompleteGrace(),
                properties.pickupNoShowGrace(), properties.issueCloseTimeout());
    }

    @Override
    public OrderPolicySnapshot resolve(UUID merchantId, UUID merchantLocationId) {
        return policy;
    }
}
