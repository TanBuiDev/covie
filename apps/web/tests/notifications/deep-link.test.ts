import { describe, expect, it } from "vitest";
import { notificationDeepLink } from "@/features/notifications/notification-deep-link";

describe("notification deep links", () => {
  it("accepts only an explicit same-origin path supplied by the backend", () => {
    expect(notificationDeepLink({ deepLink: "/orders/o-1" })).toBe("/orders/o-1");
    expect(notificationDeepLink({ href: "/seller/m-1/orders/o-1" })).toBe("/seller/m-1/orders/o-1");
    expect(notificationDeepLink({ path: "/needs/n-1" })).toBe("/needs/n-1");
    expect(notificationDeepLink({ route: "/seller/m-1/needs" })).toBe("/seller/m-1/needs");
  });

  it("rejects inferred, external and malformed notification destinations", () => {
    expect(notificationDeepLink({ type: "ORDER_UPDATED", orderId: "o-1" })).toBeNull();
    expect(notificationDeepLink({ deepLink: "https://evil.example/orders/o-1" })).toBeNull();
    expect(notificationDeepLink({ deepLink: "//evil.example/orders/o-1" })).toBeNull();
    expect(notificationDeepLink({ deepLink: "/orders/o-1\\redirect" })).toBeNull();
  });
});
