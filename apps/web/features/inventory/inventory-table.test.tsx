import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { InventoryTable } from "./inventory-table";

it("renders backend inventory balances without editable reserved or available fields", () => {
  render(<InventoryTable merchantId="merchant" items={[{
    locationVariantId: "offer",
    itemName: "Cà phê",
    variantName: "Nóng",
    mode: "TRACKED",
    stockOnHand: 8,
    reservedQuantity: 3,
    availableQuantity: 5,
  }]} />);

  expect(screen.getByText("Đã giữ")).toBeInTheDocument();
  expect(screen.getByText("Có thể bán")).toBeInTheDocument();
  expect(screen.getByRole("spinbutton", { name: "Đặt tồn thực tế" })).toHaveValue(8);
  expect(screen.queryByRole("spinbutton", { name: /Đã giữ/i })).not.toBeInTheDocument();
  expect(screen.queryByRole("spinbutton", { name: /Có thể bán/i })).not.toBeInTheDocument();
});

it("does not mount stock mutation controls for untracked offers", () => {
  render(<InventoryTable merchantId="merchant" items={[{
    locationVariantId: "offer-untracked",
    itemName: "Bánh",
    mode: "UNTRACKED",
  }]} />);

  expect(screen.getByText("Không theo dõi")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Lưu tồn kho" })).not.toBeInTheDocument();
});
