package vn.covie.orders.application;

import java.util.UUID;

public interface OrderPolicyProvider {
    OrderPolicySnapshot resolve(UUID merchantId, UUID merchantLocationId);
}
