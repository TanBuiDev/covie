package vn.covie.orders.application;

public final class OrderOutboxEvents {
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_REJECTED = "ORDER_REJECTED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_EXPIRED = "ORDER_EXPIRED";
    public static final String ORDER_CANCELLATION_REQUESTED = "ORDER_CANCELLATION_REQUESTED";
    public static final String ORDER_CANCELLATION_RESPONDED = "ORDER_CANCELLATION_RESPONDED";
    public static final String ORDER_FULFILLMENT_UPDATED = "ORDER_FULFILLMENT_UPDATED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String ORDER_ISSUE_REPORTED = "ORDER_ISSUE_REPORTED";
    public static final String ORDER_CLOSED_WITH_ISSUE = "ORDER_CLOSED_WITH_ISSUE";
    private OrderOutboxEvents() {}
}
