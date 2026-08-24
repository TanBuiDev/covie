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

    expect(screen.getByLabelText("Số lượng")).toHaveTextContent("1");
    expect(screen.queryByRole("button", { name: "Tăng số lượng" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tiếp tục đặt hàng" })).toHaveAttribute(
      "href",
      "/checkout?source=CART&cartId=service-cart",
    );
  });
});
