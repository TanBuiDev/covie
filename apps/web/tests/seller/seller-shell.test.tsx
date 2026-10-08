import { render, screen } from "@testing-library/react";
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
});
