package vn.covie.orders.application;

import java.time.Instant;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.orders.security.OrderActorType;

public record OrderActionContext(OrderActorType actorType, OrderType orderType, OrderStatus status,
        ProductFulfillmentMethod productFulfillmentMethod, Instant confirmationExpiresAt,
        Instant noShowEligibleAt, Instant now) {}
