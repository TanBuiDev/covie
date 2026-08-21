import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { ShippingPolicyForm } from "./shipping-policy-form";

it("explains that negotiated shipping is not zero", () => {
  render(<ShippingPolicyForm merchantId="merchant" locationId="location" />);
  fireEvent.change(screen.getByLabelText("Loại phí"), { target: { value: "NEGOTIATED" } });
  expect(screen.getByText(/Phí giao hàng sẽ được thỏa thuận/)).toBeInTheDocument();
  expect(screen.queryByLabelText(/Phí cơ bản/)).not.toBeInTheDocument();
});
