import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { OrderList } from "@/features/orders/order-list";
const mocks = vi.hoisted(() => ({ myOrders: vi.fn() }));
vi.mock("@/features/orders/order-api", () => ({ myOrders: mocks.myOrders, merchantOrders: vi.fn() }));
afterEach(() => { cleanup(); mocks.myOrders.mockReset(); });

it("offers login with the orders destination for an opaque401", async () => {
  mocks.myOrders.mockRejectedValue(new CovieApiError(401, "AUTH_REQUIRED", "opaque"));
  render(<OrderList />);
  expect(await screen.findByRole("link", { name: "Đăng nhập ngay" })).toHaveAttribute("href", "/login?returnTo=%2Forders");
});

it.each([403,503])("offers retry instead of login for status%i regardless of token wording", async status => {
  mocks.myOrders.mockRejectedValue(new CovieApiError(status, "ERROR", "token failure"));
  render(<OrderList />);
  expect(await screen.findByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Đăng nhập ngay" })).not.toBeInTheDocument();
});
