import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { InventoryTable } from "./inventory-table";

it("renders reserved quantity as read-only output", () => {
  render(<InventoryTable merchantId="merchant" items={[{ locationVariantId: "offer", itemName: "Cà phê", variantName: "Nóng", mode: "TRACKED", stockOnHand: 8, reservedQuantity: 3, availableQuantity: 5 }]} />);
  expect(screen.getByLabelText("Đã giữ Cà phê").tagName).toBe("OUTPUT");
  expect(screen.queryByDisplayValue("3")).not.toBeInTheDocument();
});
