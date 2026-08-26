import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CartDetail } from "@/features/cart/cart-detail";

describe("cart detail", () => {
  it("keeps service quantity fixed at one", () => {
    render(
      <CartDetail
        initialCart={{
          id: "service-cart",
          merchantLocationId: "location-a",
          cartType: "SERVICE",
          version: 3,
          items: [{ id: "item-a", itemName: "Dọn nhà", quantity: 1 }],
        }}
      />,
    );

    expect(screen.getByRole("heading", { name: "Giỏ dịch vụ" })).toBeInTheDocument();
    expect(screen.getByText("Số lượng: 1 (cố định)")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tăng số lượng" })).not.toBeInTheDocument();
    expect(screen.queryByText("location-a")).not.toBeInTheDocument();
    expect(screen.getByText("Tạm tính hiện tại: 0 ₫")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tiếp tục đặt hàng" })).toHaveAttribute(
      "href",
      "/checkout?source=CART&cartId=service-cart",
    );
  });

  it("renders product prices and line amount in Vietnamese currency", () => {
    render(
      <CartDetail
        initialCart={{
          id: "product-cart",
          merchantLocationId: "location-b",
          cartType: "PRODUCT",
          version: 1,
          items: [{ id: "item-b", itemName: "Rau củ theo mùa", quantity: 2, priceSnapshot: 85_000 }],
        }}
      />,
    );

    expect(screen.getByRole("heading", { name: "Giỏ sản phẩm" })).toBeInTheDocument();
    expect(screen.getByText("85.000 ₫")).toBeInTheDocument();
    expect(screen.getByText("170.000 ₫")).toBeInTheDocument();
    expect(screen.getByText("Phí giao hàng sẽ được hiển thị khi bạn xác nhận đơn.")).toBeInTheDocument();
  });
});
