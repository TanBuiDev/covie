package vn.covie.orders.application;

import vn.covie.orders.model.OrderShippingFeeType;

public record OrderMoney(
        OrderShippingFeeType shippingFeeType,
        Long shippingFee,
        long knownTotal,
        Long totalAmount) {}
