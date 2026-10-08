import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AppHeader } from "@/components/layout/app-header";

vi.mock("@/features/location/use-browse-location", () => ({
  useBrowseLocation: () => ({
    location: { label: "The Rainbow" },
  }),
}));

vi.mock("@/features/cart/use-cart-count", () => ({
  useCartCount: () => 3,
}));

vi.mock("@/features/location/location-chooser", () => ({
  LocationChooser: ({ onCancel }: { onCancel: () => void }) => (
    <div data-testid="location-modal">
      <button type="button" onClick={onCancel}>Đóng modal</button>
    </div>
  ),
}));

describe("AppHeader (Mobile)", () => {
  it("renders mobile header with brand, location pill, and action links", () => {
    render(<AppHeader />);

    expect(screen.getByRole("link", { name: "CoVie - Trang chủ" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chọn khu vực cư dân" })).toHaveTextContent("The Rainbow");
    expect(screen.getByRole("link", { name: "Tìm kiếm" })).toHaveAttribute("href", "/search");
    expect(screen.getByRole("link", { name: "Thông báo" })).toHaveAttribute("href", "/me/notifications");
    expect(screen.getByRole("link", { name: "Mở giỏ hàng" })).toHaveAttribute("href", "/cart");
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("opens location chooser modal when clicking location button", () => {
    render(<AppHeader />);

    const locationBtn = screen.getByRole("button", { name: "Chọn khu vực cư dân" });
    fireEvent.click(locationBtn);

    expect(screen.getByTestId("location-modal")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Đóng modal" }));
    expect(screen.queryByTestId("location-modal")).not.toBeInTheDocument();
  });
});
