import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AdCampaignForm } from "@/features/ads/management/ad-campaign-form";
import { uploadAdMedia } from "@/features/media/direct-ad-upload";
import { AdAnalytics } from "@/features/ads/management/ad-analytics";
import { getAdCampaignStats } from "@/features/ads/ad-api";

vi.mock("@/features/media/direct-ad-upload", () => ({ uploadAdMedia: vi.fn() }));
vi.mock("@/features/ads/ad-api", () => ({ getAdCampaignStats: vi.fn() }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.clearAllMocks(); delete (URL as unknown as { createObjectURL?: unknown }).createObjectURL; delete (URL as unknown as { revokeObjectURL?: unknown }).revokeObjectURL; });
it("recovers preview when a failed existing image is replaced with verified upload", async () => {
  Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn(() => "blob:replacement") });
  Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: vi.fn() });
  vi.mocked(uploadAdMedia).mockResolvedValue({ mediaId: "new-media" });
  const campaign = { id: "ad-1", title: "Campaign", ctaLabel: "View", targetUrl: "/search", creativeType: "IMAGE", mediaId: "old-media", mediaUrl: "https://cdn.test/broken.png", placement: "IN_FEED", device: "ALL", startsAt: "2026-10-01T00:00:00Z", endsAt: "2026-11-01T00:00:00Z", priority: 1, enabled: true };
  render(<AdCampaignForm campaign={campaign as never} busy={false} onSave={vi.fn()} onCancel={vi.fn()} onAuth={vi.fn()} />);
  fireEvent.error(screen.getByRole("img"));
  expect(screen.queryByRole("img")).toBeNull();
  fireEvent.change(screen.getByLabelText("Ảnh / video quảng cáo"), { target: { files: [new File(["x"], "new.png", { type: "image/png" })] } });
  await waitFor(() => expect(uploadAdMedia).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(screen.getByRole("img")).toHaveAttribute("src", "blob:replacement"));
});

it("positions daily chart data on the selected calendar rather than equally spacing sparse days", async () => {
  vi.mocked(getAdCampaignStats).mockResolvedValue({ impressions: 30, clicks: 3, ctr: 10, days: [
    { date: "2026-10-01", impressions: 10, clicks: 1, ctr: 10 },
    { date: "2026-10-02", impressions: 10, clicks: 1, ctr: 10 },
    { date: "2026-10-30", impressions: 10, clicks: 1, ctr: 10 },
  ] });
  render(<AdAnalytics scope={{ kind: "admin" }} campaign={{ id: "ad-1", title: "Campaign" } as never} onClose={vi.fn()} onAuth={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-10-01" } });
  fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-30" } });
  const chart = await screen.findByRole("img", { name: "Biểu đồ lượt xem và lượt nhấp theo ngày" });
  const secondX = Number(chart.querySelector("polyline")?.getAttribute("points")?.split(" ")[1]?.split(",")[0]);
  expect(secondX).toBeCloseTo(30 + 540 / 29, 2);
});

it("applies duration presets when clicking quick duration buttons", () => {
  const campaign = { id: "ad-1", title: "Campaign", ctaLabel: "View", targetUrl: "/search", creativeType: "CSS", template: "HERO", placement: "IN_FEED", device: "ALL", startsAt: "2026-10-01T00:00:00.000Z", endsAt: "2026-10-08T00:00:00.000Z", priority: 1, enabled: true };
  render(<AdCampaignForm campaign={campaign as never} busy={false} onSave={vi.fn()} onCancel={vi.fn()} onAuth={vi.fn()} />);
  const plus30 = screen.getByRole("button", { name: "+1 tháng" });
  fireEvent.click(plus30);
  const endsAtInput = screen.getByLabelText("Kết thúc") as HTMLInputElement;
  expect(endsAtInput.value).toContain("2026-10-31");
});

