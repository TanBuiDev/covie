import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}));

vi.mock("@/features/shipping/shipping-api", () => ({ createShippingPolicy: vi.fn() }));

import { ShippingPolicyForm } from "@/features/shipping/shipping-policy-form";

describe("shipping policy", () => {
  it("never asks negotiated shipping for a zero fee", () => {
    render(<ShippingPolicyForm merchantId="merchant-1" locationId="location-1" />);
    fireEvent.change(screen.getByLabelText("Loại phí giao"), { target: { value: "NEGOTIATED" } });
    expect(screen.getByText(/không được biểu diễn thành 0\s*₫/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Phí (cố định|mặc định)/)).not.toBeInTheDocument();
  });

  it("exposes ANY and ALL only for conditional shipping", () => {
    render(<ShippingPolicyForm merchantId="merchant-1" locationId="location-1" />);
    expect(screen.queryByLabelText("Bất kỳ điều kiện nào đúng")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Tất cả điều kiện phải đúng")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Loại phí giao"), { target: { value: "CONDITIONAL" } });
    expect(screen.getByLabelText("Bất kỳ điều kiện nào đúng")).toBeInTheDocument();
    expect(screen.getByLabelText("Tất cả điều kiện phải đúng")).toBeInTheDocument();
  });
});
