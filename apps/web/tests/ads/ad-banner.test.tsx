import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdBanner, type AdBannerVariant } from "@/components/ads/ad-banner";

describe("AdBanner demo creatives", () => {
  const variants: AdBannerVariant[] = ["flash", "nearby", "spotlight", "hero"];

  it.each(variants)("renders a labeled, clickable %s creative", (variant) => {
    render(<AdBanner variant={variant} />);
    const link = screen.getByRole("link", { name: /Quảng cáo mẫu:/i });
    expect(link).toHaveAttribute("href", "/search");
    expect(link).toHaveTextContent("Quảng cáo · Mẫu");
  });

  it("uses the supplied destination rather than a hard-coded redirect", () => {
    render(<AdBanner variant="hero" href="/search?type=SERVICE" />);
    expect(screen.getByRole("link", { name: /Quảng cáo mẫu:/i }))
      .toHaveAttribute("href", "/search?type=SERVICE");
  });
});
