import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { MarketplaceViewToggle } from "@/features/marketplace-view/marketplace-view-toggle";

describe("MarketplaceViewToggle", () => {
  it("exposes accessible labels, pressed state and changes mode", () => {
    const changeMode = vi.fn();
    render(<MarketplaceViewToggle mode="catalog" onModeChange={changeMode} />);
    const social = screen.getByRole("button", { name: "Khám phá" });
    const catalog = screen.getByRole("button", { name: "Mua sắm nhanh" });
    expect(social.getAttribute("aria-pressed")).toBe("false");
    expect(catalog.getAttribute("aria-pressed")).toBe("true");
    fireEvent.click(social);
    expect(changeMode).toHaveBeenCalledWith("social");
  });
});
