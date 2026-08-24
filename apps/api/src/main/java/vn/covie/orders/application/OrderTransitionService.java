package vn.covie.orders.application;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderTransitionService {
    private final OrderRepository orders;

    public OrderTransitionService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedOrder lock(UUID orderId) {
        OrderEntity order = orders.lockById(orderId).orElseThrow(() -> new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
        return new LockedOrder(order);
    }

    public void requireStatus(OrderEntity order, OrderStatus... allowed) {
        if (Arrays.stream(allowed).noneMatch(status -> status == order.getStatus())) {
            throw new ApiException(ErrorCode.ORDER_STATE_CONFLICT, HttpStatus.CONFLICT,
                    "The order can no longer perform this action.", Map.of("currentStatus", order.getStatus().name()));
        }
    }
}
