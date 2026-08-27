import { describe, expect, it } from "vitest";
import { notificationDeepLink } from "@/features/notifications/notification-deep-link";

describe("notification deep links", () => {
  it("accepts a backend-owned same-origin relative path", () => {
    expect(notificationDeepLink({ deepLink: "/orders/o-1" })).toBe("/orders/o-1");
    expect(notificationDeepLink({ href: "/seller/m-1/orders/o-1?tab=history#latest" })).toBe("/seller/m-1/orders/o-1?tab=history#latest");
    expect(notificationDeepLink({ path: "/needs/n-1" })).toBe("/needs/n-1");
    expect(notificationDeepLink({ route: "/seller/m-1/needs" })).toBe("/seller/m-1/needs");
  });

  it("never infers a destination from notification type or entity ids", () => {
    expect(notificationDeepLink({ type: "ORDER_UPDATED", orderId: "o-1", recipientRole: "BUYER" })).toBeNull();
    expect(notificationDeepLink({ type: "ORDER_CREATED", orderId: "o-1", merchantId: "m-1", recipientRole: "SELLER" })).toBeNull();
    expect(notificationDeepLink({ type: "NEED_RESPONSE_CREATED", needRequestId: "n-1" })).toBeNull();
    expect(notificationDeepLink({ type: "NEED_MATCH_AVAILABLE", merchantId: "m-1" })).toBeNull();
  });

  it("rejects external, protocol-relative and malformed destinations", () => {
    expect(notificationDeepLink({ deepLink: "https://evil.example/orders/o-1" })).toBeNull();
    expect(notificationDeepLink({ deepLink: "//evil.example/orders/o-1" })).toBeNull();
    expect(notificationDeepLink({ deepLink: "javascript:alert(1)" })).toBeNull();
    expect(notificationDeepLink({ deepLink: "/orders/o-1\\redirect" })).toBeNull();
    expect(notificationDeepLink({ deepLink: "/orders/o-1\u0000" })).toBeNull();
    expect(notificationDeepLink({ deepLink: 42 })).toBeNull();
    expect(notificationDeepLink({ type: "UNKNOWN" })).toBeNull();
    expect(notificationDeepLink(undefined)).toBeNull();
  });
});
