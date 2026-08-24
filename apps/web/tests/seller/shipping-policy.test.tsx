import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/features/shipping/shipping-api", () => ({ createShippingPolicy: vi.fn() }));

import { ShippingPolicyForm } from "@/features/shipping/shipping-policy-form";

describe("shipping policy", () => {
  it("never asks negotiated shipping for a zero fee", () => {
    render(<ShippingPolicyForm merchantId="merchant-1" locationId="location-1" />);
    fireEvent.change(screen.getByLabelText("Loại phí"), { target: { value: "NEGOTIATED" } });
    expect(screen.getByText(/không được biểu diễn thành 0đ/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Phí cơ bản/)).not.toBeInTheDocument();
  });

  it("exposes ANY and ALL only for conditional shipping", () => {
    render(<ShippingPolicyForm merchantId="merchant-1" locationId="location-1" />);
    expect(screen.queryByLabelText("Cách khớp điều kiện")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Loại phí"), { target: { value: "CONDITIONAL" } });
    expect(screen.getByLabelText("Cách khớp điều kiện")).toBeInTheDocument();
  });
});
