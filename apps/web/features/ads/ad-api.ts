import type { components } from "@covie/api-client";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export type AdCampaign = components["schemas"]["AdCampaignResponse"];
export type AdCampaignInput = components["schemas"]["AdCampaignRequest"];
export type PublicAd = components["schemas"]["PublicAdResponse"];
export type AdStats = components["schemas"]["AdStatsResponse"];
export type AdPlacement = AdCampaignInput["placement"];
export type AdScope = { kind: "admin" } | { kind: "seller"; merchantId: string };

function base(scope: AdScope) {
  return scope.kind === "admin" ? "/api/v1/admin/ads" : `/api/v1/merchants/${encodeURIComponent(scope.merchantId)}/ads`;
}

async function read<T>(method: string, path: string, body?: unknown, version?: number): Promise<T> {
  const response = await requestBrowserApi(method, path, body, version == null ? undefined : { "If-Match": `"v${version}"` });
  if (!response.ok) throw await readApiError(response);
  return response.status === 204 ? undefined as T : await response.json() as T;
}

export function listAdCampaigns(scope: AdScope, offset = 0) { return read<AdCampaign[]>("GET", `${base(scope)}?${new URLSearchParams({ offset: String(offset) })}`); }
export function createAdCampaign(scope: AdScope, body: AdCampaignInput) { return read<AdCampaign>("POST", base(scope), body); }
export function updateAdCampaign(scope: AdScope, id: string, version: number, body: AdCampaignInput) {
  return read<AdCampaign>("PATCH", `${base(scope)}/${encodeURIComponent(id)}`, body, version);
}
export function deleteAdCampaign(scope: AdScope, id: string, version: number) {
  return read<void>("DELETE", `${base(scope)}/${encodeURIComponent(id)}`, undefined, version);
}
export function performAdCampaignAction(scope: AdScope, id: string, version: number, action: "APPROVE" | "REJECT" | "SUBMIT", reason?: string) {
  const suffix = action === "SUBMIT" ? "submit" : "review";
  const body = action === "SUBMIT" ? undefined : { approved: action === "APPROVE", reason: reason ?? "" };
  return read<AdCampaign>("POST", `${base(scope)}/${encodeURIComponent(id)}/${suffix}`, body, version);
}
export function getAdCampaignStats(scope: AdScope, id: string, from: string, to: string) {
  return read<AdStats>("GET", `${base(scope)}/${encodeURIComponent(id)}/stats?${new URLSearchParams({ from, to })}`);
}
export async function listActiveAds(placement: AdPlacement, device: "DESKTOP" | "MOBILE"): Promise<PublicAd[]> {
  const response = await fetch(`/api/v1/ads/active?${new URLSearchParams({ placement: placement ?? "IN_FEED", device })}`, { cache: "no-store", credentials: "same-origin" });
  if (!response.ok) throw await readApiError(response);
  return response.json() as Promise<PublicAd[]>;
}

/** Best effort telemetry. Navigation never depends on a successful tracking request. */
export async function trackAdEvent(ad: PublicAd, type: "IMPRESSION" | "CLICK", eventId: string): Promise<void> {
  try {
    const path = `/api/v1/ads/${encodeURIComponent(ad.id ?? "")}/events`;
    const body = JSON.stringify({ deliveryToken: ad.deliveryToken, eventId, type, visibleMillis: type === "IMPRESSION" ? 1000 : 0 });
    // This public telemetry endpoint validates an opaque, expiring delivery proof.
    // It is the only ad POST that does not require session CSRF credentials.
    if (typeof navigator.sendBeacon === "function" && navigator.sendBeacon(path, new Blob([body], { type: "application/json" }))) return;
    await fetch(path, {
      method: "POST", credentials: "same-origin", keepalive: true,
      headers: { "Content-Type": "application/json" }, body,
    });
  } catch { /* Failure must not interrupt browsing or replay clicks. */ }
}
