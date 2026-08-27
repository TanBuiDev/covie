import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CartList } from "@/features/cart/cart-list";

describe("multi cart overview", () => {
  it("keeps product and service carts visibly separate", () => {
    render(
      <CartList
        initialCarts={[
          { id: "product-cart", merchantLocationId: "location-a", cartType: "PRODUCT", items: [{ id: "p1", itemName: "Gạo", quantity: 2, priceSnapshot: 85_000 }] },
          { id: "service-cart", merchantLocationId: "location-a", cartType: "SERVICE", items: [{ id: "s1", itemName: "Dọn nhà", quantity: 1, priceSnapshot: 430_000 }] },
        ]}
      />,
    );

    expect(screen.getByText("Sản phẩm")).toBeInTheDocument();
    expect(screen.getByText("Dịch vụ")).toBeInTheDocument();
    expect(screen.getByText("170.000 ₫")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem và thanh toán giỏ này" })).toHaveAttribute("href", "/cart/product-cart");
    expect(screen.getByRole("link", { name: "Xem giỏ dịch vụ" })).toHaveAttribute("href", "/cart/service-cart");
    expect(screen.queryByText("location-a")).not.toBeInTheDocument();
  });

  it("renders the exact BUY-001 empty-state copy", () => {
    render(<CartList initialCarts={[]} />);
    expect(screen.getByRole("heading", { name: "Giỏ hàng của bạn đang trống" })).toBeInTheDocument();
    expect(screen.getByText("Khám phá sản phẩm, dịch vụ và đồ thanh lý gần bạn để bắt đầu.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Khám phá gần bạn" })).toHaveAttribute("href", "/search");
  });

  it("does not render a combined checkout action", () => {
    render(<CartList initialCarts={[{ id: "cart-a", cartType: "PRODUCT", items: [] }]} />);
    expect(screen.getAllByRole("link", { name: "Xem và thanh toán giỏ này" })).toHaveLength(1);
    expect(screen.queryByRole("link", { name: /thanh toán tất cả|đặt tất cả/i })).not.toBeInTheDocument();
  });
});
