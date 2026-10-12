import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SellerShell } from "@/components/layout/seller-shell";

describe("SellerShell", () => {
  it("keeps the merchant id in every IA-locked seller route", () => {
    render(
      <SellerShell merchantId="merchant-1" switcher={<span>Tiệm A · Đổi cửa hàng</span>}>
        <p>Nội dung</p>
      </SellerShell>,
    );

    expect(screen.getAllByText("Tiệm A · Đổi cửa hàng").length).toBeGreaterThan(0);

    for (const link of screen.getAllByRole("link", { name: "Đơn hàng" })) {
      expect(link).toHaveAttribute("href", "/seller/merchant-1/orders");
    }
    for (const link of screen.getAllByRole("link", { name: "Danh mục" })) {
      expect(link).toHaveAttribute("href", "/seller/merchant-1/catalog");
    }
    for (const link of screen.getAllByRole("link", { name: "Điểm bán" })) {
      expect(link).toHaveAttribute("href", "/seller/merchant-1/locations");
    }
    for (const link of screen.getAllByRole("link", { name: "Cài đặt" })) {
      expect(link).toHaveAttribute("href", "/seller/merchant-1/settings");
    }
    for (const link of screen.getAllByRole("link", { name: "Quảng cáo" })) {
      expect(link).toHaveAttribute("href", "/seller/merchant-1/ads");
    }
  });

  it("toggles between expanded and collapsed sidebar mode", () => {
    const { container } = render(
      <SellerShell merchantId="merchant-1">
        <p>Nội dung</p>
      </SellerShell>,
    );

    const collapseBtn = screen.getByRole("button", { name: "Thu gọn thanh điều hướng" });
    expect(container.querySelector(".seller-shell--expanded")).toBeInTheDocument();

    fireEvent.click(collapseBtn);
    expect(container.querySelector(".seller-shell--collapsed")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mở rộng thanh điều hướng" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Mở rộng thanh điều hướng" }));
    expect(container.querySelector(".seller-shell--expanded")).toBeInTheDocument();
  });

  it("renders merchant name and active status badge in store card", () => {
    render(
      <SellerShell
        merchantId="merchant-1"
        merchantName="Tiệm Bánh Mì Chú Bảy"
        merchantStatus="ACTIVE"
      >
        <p>Nội dung</p>
      </SellerShell>,
    );

    expect(screen.getByText("Tiệm Bánh Mì Chú Bảy")).toBeInTheDocument();
    expect(screen.getByText("đang hoạt động")).toBeInTheDocument();
  });
});
