import { describe, expect, it } from "vitest";
import { notificationDeepLink } from "@/features/notifications/notification-deep-link";

describe("notification deep links", () => {
  it("derives buyer, seller and Need routes from typed ids", () => {
    expect(notificationDeepLink({ type: "ORDER_UPDATED", orderId: "o-1", recipientRole: "BUYER" })).toBe("/orders/o-1");
    expect(notificationDeepLink({ type: "ORDER_CREATED", orderId: "o-1", merchantId: "m-1", recipientRole: "SELLER" })).toBe("/seller/m-1/orders/o-1");
    expect(notificationDeepLink({ type: "NEED_RESPONSE_CREATED", needRequestId: "n-1" })).toBe("/needs/n-1");
    expect(notificationDeepLink({ type: "NEED_MATCH_AVAILABLE", merchantId: "m-1" })).toBe("/seller/m-1/needs");
  });

  it("falls back safely for unknown or malformed notification data", () => {
    expect(notificationDeepLink({ type: "UNKNOWN" })).toBe("/me/notifications");
    expect(notificationDeepLink({ type: "ORDER_UPDATED" })).toBe("/me/notifications");
  });
});
