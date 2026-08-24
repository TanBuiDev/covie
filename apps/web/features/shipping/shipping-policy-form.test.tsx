import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ShippingPolicyForm } from "./shipping-policy-form";

describe("ShippingPolicyForm", () => {
  it("explains that negotiated shipping is not zero", () => {
    render(<ShippingPolicyForm merchantId="merchant" locationId="location" />);
    fireEvent.change(screen.getByLabelText("Loại phí"), { target: { value: "NEGOTIATED" } });
    expect(screen.getByText(/Phí giao được thỏa thuận/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Phí cơ bản/)).not.toBeInTheDocument();
  });

  it("shows ANY or ALL matching only for conditional policies", () => {
    render(<ShippingPolicyForm merchantId="merchant" locationId="location" />);
    expect(screen.queryByLabelText("Cách khớp điều kiện")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Loại phí"), { target: { value: "CONDITIONAL" } });
    expect(screen.getByLabelText("Cách khớp điều kiện")).toHaveValue("ANY");
    fireEvent.change(screen.getByLabelText("Cách khớp điều kiện"), { target: { value: "ALL" } });
    expect(screen.getByLabelText("Cách khớp điều kiện")).toHaveValue("ALL");
  });
});
