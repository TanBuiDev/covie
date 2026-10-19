import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BuyerDesktopHeader } from "@/components/layout/buyer-desktop-header";

const navigation = vi.hoisted(() => ({
  pathname: "/search",
  query: "q=cơm&type=PRODUCT&categoryId=food&sort=PRICE_ASC&minPrice=0&maxPrice=100000&pickup=true&canServe=true&shipping=FREE",
}));

vi.mock("next/navigation", () => ({
  usePathname: () => navigation.pathname,
  useSearchParams: () => new URLSearchParams(navigation.query),
}));

vi.mock("@/features/location/use-browse-location", () => ({
  useBrowseLocation: () => ({ location: { label: "The Rainbow" } }),
}));

vi.mock("@/features/cart/use-cart-count", () => ({
  useCartCount: () => 5,
}));

vi.mock("@/features/location/location-chooser", () => ({
  LocationChooser: ({ onCancel }: { onCancel: () => void }) => (
    <div data-testid="desktop-location-chooser">
      <button type="button" onClick={onCancel}>Đóng popover</button>
    </div>
  ),
}));

describe("BuyerDesktopHeader", () => {
  it("keeps sticky content below the measured header and releases observation on unmount", () => {
    let notifyResize: (() => void) | undefined;
    const disconnect = vi.fn();
    const observe = vi.fn();
    vi.stubGlobal("ResizeObserver", class {
      constructor(callback: () => void) { notifyResize = callback; }
      observe = observe;
      disconnect = disconnect;
    });
    const bounds = vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({ height: 115 } as DOMRect);
    try {
      const { unmount } = render(<BuyerDesktopHeader />);
      expect(document.documentElement.style.getPropertyValue("--buyer-desktop-header-height")).toBe("115px");
      expect(observe).toHaveBeenCalledWith(document.querySelector(".buyer-desktop-header"));
      bounds.mockReturnValue({ height: 73 } as DOMRect);
      notifyResize?.();
      expect(document.documentElement.style.getPropertyValue("--buyer-desktop-header-height")).toBe("73px");
      unmount();
      expect(disconnect).toHaveBeenCalledOnce();
      expect(document.documentElement.style.getPropertyValue("--buyer-desktop-header-height")).toBe("");
    } finally {
      bounds.mockRestore();
      vi.unstubAllGlobals();
    }
  });

  it("reflects URL keywords and preserves filters when searching from the laptop header", () => {
    const { rerender } = render(<BuyerDesktopHeader />);
    expect(screen.getByRole("searchbox")).toHaveValue("cơm");
    expect(document.querySelector('input[type="hidden"][name="categoryId"]')).toHaveValue("food");
    expect(document.querySelector('input[type="hidden"][name="sort"]')).toHaveValue("PRICE_ASC");
    expect(document.querySelector('input[type="hidden"][name="minPrice"]')).toHaveValue("0");
    expect(document.querySelector('input[type="hidden"][name="maxPrice"]')).toHaveValue("100000");
    expect(document.querySelector('input[type="hidden"][name="pickup"]')).toHaveValue("true");
    expect(document.querySelector('input[type="hidden"][name="canServe"]')).toHaveValue("true");
    expect(document.querySelector('input[type="hidden"][name="shipping"]')).toHaveValue("FREE");

    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "rau" } });
    navigation.query = "q=trà&type=SERVICE";
    rerender(<BuyerDesktopHeader />);
    expect(screen.getByRole("searchbox")).toHaveValue("trà");
    expect(document.querySelector('input[name="categoryId"]')).not.toBeInTheDocument();
  });

  it("renders streamlined 1-row layout with Seller CTA, cart count badge, and location popover", () => {
    render(<BuyerDesktopHeader />);

    // Brand lockup
    expect(screen.getByRole("link", { name: "CoVie - Trang chủ" })).toBeInTheDocument();

    // Location selector pill
    const locationBtn = screen.getByRole("button", { name: /The Rainbow/i });
    expect(locationBtn).toBeInTheDocument();
    fireEvent.click(locationBtn);
    expect(screen.getByTestId("desktop-location-chooser")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Đóng popover" }));
    expect(screen.queryByTestId("desktop-location-chooser")).not.toBeInTheDocument();

    // Seller CTA
    const sellerCta = screen.getByRole("link", { name: /Mở quầy \/ Pass đồ/i });
    expect(sellerCta).toHaveAttribute("href", "/seller");

    // Notifications & Cart with badge
    expect(screen.getByRole("link", { name: "Thông báo" })).toHaveAttribute("href", "/me/notifications");
    expect(screen.getByRole("link", { name: "Mở giỏ hàng" })).toHaveAttribute("href", "/cart");
    expect(screen.getByText("5")).toBeInTheDocument();

    // Navigation links
    expect(screen.getByRole("link", { name: "Trang chủ" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "Khám phá" })).toHaveAttribute("href", "/search");
    expect(screen.getByRole("link", { name: "Cần mua" })).toHaveAttribute("href", "/needs");
    expect(screen.getByRole("link", { name: "Đơn" })).toHaveAttribute("href", "/orders");
  });
});
