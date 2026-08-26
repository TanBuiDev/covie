import type { components } from "@covie/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ShippingPolicyForm } from "./shipping-policy-form";

type Policy = components["schemas"]["ShippingPolicyResponse"];

const mocks = vi.hoisted(() => ({
  refresh: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mocks.refresh }),
}));

vi.mock("./shipping-api", () => ({
  createShippingPolicy: mocks.create,
  updateShippingPolicy: mocks.update,
  deleteShippingPolicy: mocks.remove,
}));

beforeEach(() => {
  mocks.refresh.mockReset();
  mocks.create.mockReset().mockResolvedValue({});
  mocks.update.mockReset().mockResolvedValue({});
  mocks.remove.mockReset().mockResolvedValue(undefined);
});

it("submits conditional shipping with backend-owned rules and frozen ANY/ALL presentation", async () => {
  render(<ShippingPolicyForm merchantId="merchant-1" locationId="location-1" />);

  fireEvent.change(screen.getByLabelText(/^Loại phí giao/), { target: { value: "CONDITIONAL" } });
  fireEvent.change(screen.getByRole("spinbutton", { name: /^Phí mặc định/ }), { target: { value: "10000" } });
  fireEvent.change(screen.getByLabelText("Ngưỡng điều kiện 1"), { target: { value: "150000" } });
  fireEvent.click(screen.getByLabelText("Tất cả điều kiện phải đúng"));
  fireEvent.click(screen.getByRole("button", { name: "Lưu phí giao" }));

  await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
  expect(mocks.create).toHaveBeenCalledWith("merchant-1", "location-1", {
    mode: "CONDITIONAL",
    status: "ACTIVE",
    currencyCode: "VND",
    baseFee: 10000,
    ruleMatchMode: "ALL",
    rules: [{
      conditionType: "ORDER_AMOUNT_MIN",
      operator: "GTE",
      thresholdValue: 150000,
      actionType: "FREE_SHIPPING",
      priority: 1,
      enabled: true,
    }],
  });
});

it("preserves an existing backend status instead of silently reactivating policy on edit", async () => {
  const policy: Policy = {
    id: "policy-1",
    mode: "FIXED",
    status: "INACTIVE",
    currencyCode: "VND",
    baseFee: 10000,
    version: 3,
  };

  render(<ShippingPolicyForm merchantId="merchant-1" locationId="location-1" policy={policy} />);
  fireEvent.change(screen.getByRole("spinbutton", { name: /^Phí cố định/ }), { target: { value: "12000" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu phí giao" }));

  await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(1));
  expect(mocks.update).toHaveBeenCalledWith("merchant-1", "location-1", "policy-1", 3, {
    mode: "FIXED",
    status: "INACTIVE",
    currencyCode: "VND",
    baseFee: 12000,
    ruleMatchMode: undefined,
    rules: [],
  });
});

it("blocks save when backend returns an unsupported conditional rule instead of coercing it", async () => {
  const policy: Policy = {
    id: "policy-unknown-rule",
    mode: "CONDITIONAL",
    status: "ACTIVE",
    currencyCode: "VND",
    baseFee: 10000,
    ruleMatchMode: "ANY",
    rules: [{
      conditionType: undefined,
      operator: "GTE",
      thresholdValue: 1,
      actionType: "FREE_SHIPPING",
      priority: 1,
      enabled: true,
    }],
    version: 2,
  };

  render(<ShippingPolicyForm merchantId="merchant-1" locationId="location-1" policy={policy} />);
  expect(screen.getByText("Điều kiện đã được cập nhật")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Lưu phí giao" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("chưa được phiên bản hiện tại hỗ trợ");
  expect(mocks.update).not.toHaveBeenCalled();
});
