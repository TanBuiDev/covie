import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { AdminBillingSubscriptions } from "./admin-billing-subscriptions";

const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.request }));
const firstShop = "11111111-1111-4111-8111-111111111111";
const secondShop = "22222222-2222-4222-8222-222222222222";
const ownerId = "33333333-3333-4333-8333-333333333333";
const subscription = (merchantId: string) => ({ merchantId, merchantName: merchantId,
  planCode: "MONTHLY", status: "ACTIVE", periodStart: "2026-10-01T00:00:00Z",
  periodEnd: "2026-11-01T00:00:00Z", graceUntil: "2026-11-04T00:00:00Z",
  cancelAtPeriodEnd: true, currentOwnerUserIds: [ownerId] });
const response = (entries: unknown) => new Response(JSON.stringify(entries), { status: 200 });
beforeEach(() => { mocks.request.mockReset(); });

it("queries exact merchant scope and presents stored status, cancellation and current owner memberships without a mutation", async () => {
  mocks.request.mockResolvedValue(response([subscription(firstShop)]));
  render(<AdminBillingSubscriptions />);
  fireEvent.change(screen.getByLabelText("Merchant ID tra cứu thuê bao"), { target: { value: firstShop } });
  fireEvent.click(screen.getByRole("button", { name: "Tra cứu thuê bao" }));
  const region = await screen.findByRole("region", { name: "Danh sách thuê bao Seller" });
  expect(region).toHaveAttribute("tabindex", "0");
  expect(region.querySelector("table")).toHaveStyle({ whiteSpace: "nowrap" });
  expect(screen.getByText(ownerId)).toBeInTheDocument();
  expect(screen.getByRole("columnheader", { name: "Membership OWNER đang ACTIVE" })).toBeInTheDocument();
  expect(screen.getByText("ACTIVE", { exact: true })).toBeInTheDocument();
  expect(screen.getByText("Có", { exact: true })).toBeInTheDocument();
  expect(mocks.request).toHaveBeenCalledWith("GET", `/api/v1/admin/billing/subscriptions?limit=20&offset=0&merchantId=${firstShop}`);
  expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
  expect(screen.getByRole("link", { name: "Tra cứu nhật ký quản trị" })).toHaveAttribute("href", "/admin/audit");
});

it("keeps denied reads distinct from empty results and paginates after an explicit retry", async () => {
  mocks.request.mockResolvedValueOnce(new Response(JSON.stringify({ error: { code: "ACCESS_DENIED",
    message: "Không có quyền đọc thuê bao" } }), { status: 403 }));
  render(<AdminBillingSubscriptions />);
  fireEvent.click(screen.getByRole("button", { name: "Tra cứu thuê bao" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không có quyền đọc thuê bao");
  expect(screen.queryByText("Không có thuê bao trong trang này.")).not.toBeInTheDocument();
  mocks.request.mockResolvedValueOnce(response(Array.from({ length: 20 }, (_, index) => ({
    ...subscription(`${firstShop.slice(0, -2)}${String(index).padStart(2, "0")}`), currentOwnerUserIds: [],
  })))).mockResolvedValueOnce(response([]));
  fireEvent.click(screen.getByRole("button", { name: "Thử lại thuê bao" }));
  await screen.findByRole("region", { name: "Danh sách thuê bao Seller" });
  fireEvent.click(screen.getByRole("button", { name: "Trang thuê bao tiếp" }));
  await screen.findByText("Không có thuê bao trong trang này.");
  expect(mocks.request).toHaveBeenLastCalledWith("GET", "/api/v1/admin/billing/subscriptions?limit=20&offset=20");
  expect(screen.getByRole("button", { name: "Trang thuê bao trước" })).toBeEnabled();
});

it("keeps unknown OWNER data from an older response distinct from an empty membership list", async () => {
  mocks.request.mockResolvedValue(response([{ ...subscription(firstShop), currentOwnerUserIds: undefined }]));
  render(<AdminBillingSubscriptions />);
  fireEvent.click(screen.getByRole("button", { name: "Tra cứu thuê bao" }));
  await screen.findByRole("region", { name: "Danh sách thuê bao Seller" });
  expect(screen.getByText("Chưa có dữ liệu OWNER")).toBeInTheDocument();
  expect(screen.queryByText("Không có membership OWNER đang ACTIVE")).not.toBeInTheDocument();
});

it("discards a delayed previous merchant invoice read and clears records on filter changes", async () => {
  let finishFirst: (value: Response) => void = () => {};
  mocks.request.mockResolvedValueOnce(response([subscription(firstShop), subscription(secondShop)]))
    .mockImplementationOnce(() => new Promise<Response>(resolve => { finishFirst = resolve; }))
    .mockResolvedValueOnce(response([{ id: "invoice-second", merchantId: secondShop, invoiceNumber: "SECOND-INVOICE",
      status: "PAID", amountVnd: 15000, createdAt: "2026-10-19T00:00:00Z", allowedActions: [] }]));
  render(<AdminBillingSubscriptions />);
  fireEvent.click(screen.getByRole("button", { name: "Tra cứu thuê bao" }));
  await screen.findByRole("region", { name: "Danh sách thuê bao Seller" });
  fireEvent.click(screen.getByRole("button", { name: `Xem hóa đơn của ${firstShop}` }));
  await waitFor(() => expect(mocks.request).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByRole("button", { name: `Xem hóa đơn của ${secondShop}` }));
  await screen.findByText(/SECOND-INVOICE/);
  expect(mocks.request).toHaveBeenLastCalledWith("GET", `/api/v1/admin/billing/invoices?merchantId=${secondShop}&limit=20&offset=0`);
  await act(async () => finishFirst(response([{ id: "invoice-first", merchantId: firstShop,
    invoiceNumber: "FIRST-INVOICE", status: "PAID", amountVnd: 10000 }])));
  expect(screen.queryByText(/FIRST-INVOICE/)).not.toBeInTheDocument();
  expect(screen.getByText(/SECOND-INVOICE/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Merchant ID tra cứu thuê bao"), { target: { value: firstShop } });
  expect(screen.queryByText(/SECOND-INVOICE/)).not.toBeInTheDocument();
});
