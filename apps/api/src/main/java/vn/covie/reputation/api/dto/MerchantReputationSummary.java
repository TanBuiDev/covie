package vn.covie.reputation.api.dto;

public record MerchantReputationSummary(long eligibleOrders, long completedOrders,
        Integer completionRateBasisPoints) {}
