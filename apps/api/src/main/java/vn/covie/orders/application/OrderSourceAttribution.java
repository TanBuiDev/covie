package vn.covie.orders.application;

import java.util.Objects;
import java.util.UUID;
import vn.covie.orders.model.OrderSourceType;

public record OrderSourceAttribution(
        OrderSourceType sourceType,
        UUID needRequestId,
        UUID needResponseItemId) {

    public static OrderSourceAttribution marketplace() {
        return new OrderSourceAttribution(OrderSourceType.MARKETPLACE, null, null);
    }

    public static OrderSourceAttribution need(UUID needRequestId, UUID needResponseItemId) {
        return new OrderSourceAttribution(OrderSourceType.NEED_REQUEST,
                Objects.requireNonNull(needRequestId), Objects.requireNonNull(needResponseItemId));
    }
}
