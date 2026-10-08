import { afterEach, expect, it, vi } from "vitest";
import { requestBrowserApi } from "@/lib/api/api-client";
import { performAdCampaignAction, trackAdEvent, updateAdCampaign } from "./ad-api";
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: vi.fn() }));
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); });
it("sends version preconditions on merchant editing and admin review", async () => {
  vi.mocked(requestBrowserApi).mockImplementation(async () => new Response(JSON.stringify({ id: "ad" })));
  await updateAdCampaign({ kind: "seller", merchantId: "merchant" }, "ad", 3, { title: "New" } as never);
  expect(requestBrowserApi).toHaveBeenCalledWith("PATCH", "/api/v1/merchants/merchant/ads/ad", { title: "New" }, { "If-Match": '"v3"' });
  await performAdCampaignAction({ kind: "admin" }, "ad", 4, "REJECT", "Ảnh chưa rõ");
  expect(requestBrowserApi).toHaveBeenCalledWith("POST", "/api/v1/admin/ads/ad/review", { approved: false, reason: "Ảnh chưa rõ" }, { "If-Match": '"v4"' });
});
it("click telemetry starts immediately and survives navigation using keepalive fallback", async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetchMock);
  await trackAdEvent({ id: "ad", deliveryToken: "delivery" } as never, "CLICK", "event");
  expect(fetchMock).toHaveBeenCalledWith("/api/v1/ads/ad/events", expect.objectContaining({ keepalive: true, headers: { "Content-Type": "application/json" } }));
});
it("telemetry failure is swallowed and never retries business writes", async () => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
  await expect(trackAdEvent({ id: "ad", deliveryToken: "token" } as never, "IMPRESSION", "event")).resolves.toBeUndefined();
});
it("uses a beacon without waiting on any network read", async () => {
  const beacon = vi.fn().mockReturnValue(true);
  vi.stubGlobal("navigator", { sendBeacon: beacon });
  const fetchMock = vi.fn(); vi.stubGlobal("fetch", fetchMock);
  await trackAdEvent({ id: "ad", deliveryToken: "delivery" } as never, "CLICK", "event");
  expect(beacon).toHaveBeenCalledWith("/api/v1/ads/ad/events", expect.any(Blob));
  expect(fetchMock).not.toHaveBeenCalled();
});
