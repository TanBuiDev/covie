package vn.covie.reputation.api.dto;

public record BuyerReputationSummary(long eligibleOrders, long completedOrders,
        Integer completionRateBasisPoints, boolean warning) {}
