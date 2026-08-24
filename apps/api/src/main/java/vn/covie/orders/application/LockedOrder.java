package vn.covie.orders.application;

import vn.covie.orders.model.OrderEntity;

public record LockedOrder(OrderEntity order) {}
