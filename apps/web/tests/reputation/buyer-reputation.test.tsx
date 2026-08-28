import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { BuyerReputation } from "@/features/reputation/buyer-reputation";

it("renders the policy-backed buyer reputation summary and warning", () => {
  render(<BuyerReputation reputation={{
    eligibleOrders: 10,
    completedOrders: 8,
    completionRateBasisPoints: 8000,
    warning: true,
  }} />);

  expect(screen.getByRole("heading", { name: "Uy tín người mua" })).toBeInTheDocument();
  expect(screen.getByText("80%")).toBeInTheDocument();
  expect(screen.getByRole("status")).toHaveTextContent("cảnh báo");
});

it("renders an honest empty state before any eligible orders", () => {
  render(<BuyerReputation reputation={{ eligibleOrders: 0, completedOrders: 0, warning: false }} />);
  expect(screen.getByRole("heading", { name: "Chưa có dữ liệu uy tín" })).toBeInTheDocument();
});
