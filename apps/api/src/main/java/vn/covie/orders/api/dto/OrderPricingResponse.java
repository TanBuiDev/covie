package vn.covie.orders.api.dto;

import vn.covie.orders.model.OrderShippingFeeType;

public record OrderPricingResponse(long subtotal, OrderShippingFeeType shippingFeeType, Long shippingFee,
        long knownTotal, Long totalAmount, String currency) {}
