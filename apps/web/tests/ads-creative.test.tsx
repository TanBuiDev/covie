import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdCreative, safeMediaUrl } from "@/components/ads/ad-creative";
import type { PublicAd } from "@/features/ads/ad-api";

describe("safeMediaUrl", () => {
  it("allows blob URLs for uploaded media previews", () => {
    expect(safeMediaUrl("blob:http://localhost:3000/123-abc")).toBe("blob:http://localhost:3000/123-abc");
    expect(safeMediaUrl("blob:https://covie.vn/456-def")).toBe("blob:https://covie.vn/456-def");
  });

  it("allows https and http media URLs", () => {
    expect(safeMediaUrl("https://cdn.covie.vn/ads/banner.jpg")).toBe("https://cdn.covie.vn/ads/banner.jpg");
    expect(safeMediaUrl("http://localhost:9000/bucket/video.mp4")).toBe("http://localhost:9000/bucket/video.mp4");
  });

  it("allows safe relative paths", () => {
    expect(safeMediaUrl("/media/ads/promo.webp")).toBe("/media/ads/promo.webp");
  });

  it("rejects dangerous or malformed protocols", () => {
    expect(safeMediaUrl("javascript:alert(1)")).toBeUndefined();
    expect(safeMediaUrl("data:text/html,<script>alert(1)</script>")).toBeUndefined();
    expect(safeMediaUrl("//attacker.com/evil.jpg")).toBeUndefined();
    expect(safeMediaUrl("vbscript:msgbox(1)")).toBeUndefined();
    expect(safeMediaUrl(undefined)).toBeUndefined();
    expect(safeMediaUrl("")).toBeUndefined();
    expect(safeMediaUrl("   ")).toBeUndefined();
  });
});

describe("AdCreative", () => {
  beforeEach(() => {
    vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
    vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(async () => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const baseAd: PublicAd = {
    id: "ad-test",
    title: "Khuyến mãi hot",
    body: "Giảm 50% tuần này",
    ctaLabel: "Xem ngay",
    targetUrl: "/promo",
    creativeType: "IMAGE",
    placement: "IN_FEED",
  };

  it("renders image creative with safe media url", () => {
    render(<AdCreative ad={{ ...baseAd, mediaUrl: "https://cdn.covie.vn/img.jpg" }} />);
    const img = screen.getByRole("img", { name: "Khuyến mãi hot" });
    expect(img).toHaveAttribute("src", "https://cdn.covie.vn/img.jpg");
  });

  it("renders preview with blob url without error", () => {
    render(<AdCreative ad={{ ...baseAd, mediaUrl: "blob:http://localhost:3000/test-blob" }} />);
    const img = screen.getByRole("img", { name: "Khuyến mãi hot" });
    expect(img).toHaveAttribute("src", "blob:http://localhost:3000/test-blob");
  });

  it("renders video creative with safe media url", () => {
    render(<AdCreative ad={{ ...baseAd, creativeType: "VIDEO", mediaUrl: "https://cdn.covie.vn/video.mp4" }} />);
    const video = document.querySelector("video");
    expect(video).toBeInTheDocument();
    expect(video).toHaveAttribute("src", "https://cdn.covie.vn/video.mp4");
  });

  it("does not render unsafe media url on img", () => {
    render(<AdCreative ad={{ ...baseAd, mediaUrl: "javascript:alert(1)" }} />);
    const img = screen.getByRole("img", { name: "Khuyến mãi hot" });
    expect(img).not.toHaveAttribute("src");
  });
});
