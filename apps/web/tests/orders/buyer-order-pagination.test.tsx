import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { OrderList } from "@/features/orders/order-list";
import { CovieApiError } from "@/lib/api/api-error";
const mocks = vi.hoisted(() => ({ myOrders: vi.fn() }));
vi.mock("@/features/orders/order-api", () => ({ myOrders: mocks.myOrders, merchantOrders: vi.fn() }));
beforeEach(() => mocks.myOrders.mockReset());

it("renders item names and quantities from the stored summary", async () => {
  mocks.myOrders.mockResolvedValue({ items: [{ id: "items", orderCode: "CV-ITEMS", items: [{ name: "Tên lúc đặt", quantity: 2 }, { name: null, quantity: 1 }] }] });
  render(<OrderList />);
  expect(await screen.findByText("2 × Tên lúc đặt")).toBeInTheDocument();
  expect(screen.getByText("1 × Mục đơn hàng")).toBeInTheDocument();
});

it.each([true, false])("shows stored pricing without claiming a negotiated total: %s", async negotiated => {
  mocks.myOrders.mockResolvedValue({ items: [{ id: "priced", orderCode: "CV-PRICE", updatedAt: "2026-10-07T00:00:00Z",
    pricing: { knownTotal: 100000, totalAmount: negotiated ? null : 120000, currency: "VND", shippingFeeType: negotiated ? "NEGOTIATED" : "FIXED" } }] });
  render(<OrderList />);
  await screen.findByText("CV-PRICE");
  expect(screen.getByText(negotiated ? "Số tiền đã biết" : "Tổng tiền")).toBeInTheDocument();
  expect(screen.getByText(negotiated ? /100.*000.*₫/ : /120.*000.*₫/)).toBeInTheDocument();
  expect(screen.getByText(/^Cập nhật /)).toBeInTheDocument();
  if (negotiated) expect(screen.getByText("Phí giao hàng cần thỏa thuận.")).toBeInTheDocument();
});

it("loads the buyer cursor once and deduplicates overlapping pages", async () => {
  mocks.myOrders.mockResolvedValueOnce({ items: [{ id: "a", orderCode: "CV-A" }], nextCursor: "cursor-a" });
  let resolve!: (value: unknown) => void;
  mocks.myOrders.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
  render(<OrderList />);
  const more = await screen.findByRole("button", { name: "Tải thêm đơn hàng" });
  act(() => { more.click(); more.click(); });
  expect(mocks.myOrders).toHaveBeenCalledTimes(2);
  expect(mocks.myOrders).toHaveBeenLastCalledWith({ cursor: "cursor-a", limit: 20 });
  await act(async () => resolve({ items: [{ id: "a", orderCode: "CV-A" }, { id: "b", orderCode: "CV-B" }] }));
  expect(screen.getAllByText("CV-A")).toHaveLength(1);
  expect(screen.getByText("CV-B")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Tải thêm đơn hàng" })).not.toBeInTheDocument();
});

it("keeps loaded orders on cursor failure and retries that cursor", async () => {
  mocks.myOrders.mockResolvedValueOnce({ items: [{ id: "a", orderCode: "CV-A" }], nextCursor: "cursor-a" })
    .mockRejectedValueOnce(new Error("private diagnostic"))
    .mockResolvedValueOnce({ items: [{ id: "b", orderCode: "CV-B" }] });
  render(<OrderList />);
  fireEvent.click(await screen.findByRole("button", { name: "Tải thêm đơn hàng" }));
  expect(await screen.findByRole("alert")).not.toHaveTextContent("private diagnostic");
  expect(screen.getByText("CV-A")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Tải thêm đơn hàng" }));
  await waitFor(() => expect(screen.getByText("CV-B")).toBeInTheDocument());
  expect(mocks.myOrders).toHaveBeenLastCalledWith({ cursor: "cursor-a", limit: 20 });
});

it("replaces loaded orders with login when the cursor expires authentication", async () => {
  mocks.myOrders.mockResolvedValueOnce({ items: [{ id: "a", orderCode: "CV-A" }], nextCursor: "cursor-a" })
    .mockRejectedValueOnce(new CovieApiError(401, "AUTH_REQUIRED", "private diagnostic"));
  render(<OrderList />);
  fireEvent.click(await screen.findByRole("button", { name: "Tải thêm đơn hàng" }));
  expect(await screen.findByRole("link", { name: "Đăng nhập ngay" })).toHaveAttribute("href", "/login?returnTo=%2Forders");
  expect(screen.queryByText("CV-A")).not.toBeInTheDocument();
});
