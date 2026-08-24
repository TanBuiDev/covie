import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SellerShell } from "@/components/layout/seller-shell";

describe("SellerShell", () => {
  it("keeps the merchant id in every seller route and exposes all P0 sections", () => {
    render(
      <SellerShell merchantId="merchant-1" merchantName="Tiệm A" switcher={<span>Đổi cửa hàng</span>}>
        <p>Nội dung</p>
      </SellerShell>,
    );

    expect(screen.getByText("Tiệm A")).toBeInTheDocument();
    expect(screen.getByText("Đổi cửa hàng")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Orders" })).toHaveAttribute("href", "/seller/merchant-1/orders");
    expect(screen.getByRole("link", { name: "Catalog" })).toHaveAttribute("href", "/seller/merchant-1/catalog");
    expect(screen.getByRole("link", { name: "Locations" })).toHaveAttribute("href", "/seller/merchant-1/locations");
    expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute("href", "/seller/merchant-1/settings");
  });
});
