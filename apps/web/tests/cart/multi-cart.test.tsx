import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CartList } from "@/features/cart/cart-list";

describe("multi cart overview", () => {
  it("keeps product and service carts visibly separate", () => {
    render(
      <CartList
        initialCarts={[
          { id: "product-cart", merchantLocationId: "location-a", cartType: "PRODUCT", items: [{ id: "p1", itemName: "Gạo", quantity: 2 }] },
          { id: "service-cart", merchantLocationId: "location-a", cartType: "SERVICE", items: [{ id: "s1", itemName: "Dọn nhà", quantity: 1 }] },
        ]}
      />,
    );

    expect(screen.getByText("Sản phẩm")).toBeInTheDocument();
    expect(screen.getByText("Dịch vụ")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Xem giỏ này" })).toHaveLength(2);
  });

  it("does not render a combined checkout action", () => {
    render(<CartList initialCarts={[{ id: "cart-a", cartType: "PRODUCT", items: [] }]} />);
    expect(screen.queryByRole("link", { name: /thanh toán|đặt hàng/i })).not.toBeInTheDocument();
  });
});
