import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { SellerBillingPanel } from "./seller-billing-panel";

const mocks = vi.hoisted(() => ({ request: vi.fn(), qr: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.request }));
vi.mock("qrcode", () => ({ toDataURL: mocks.qr }));

const base = "/api/v1/merchants/shop/billing";
const invoice = { invoiceId: "invoice", invoiceNumber: "CB-123", amountVnd: 15000,
  status: "PENDING_PAYMENT", periodStart: "2026-10-17T00:00:00Z", periodEnd: "2026-11-17T00:00:00Z" };
const payment = { invoiceId: "invoice", invoiceStatus: "PENDING_PAYMENT", amountVnd: 15000,
  checkoutUrl: "https://pay.payos.vn/web/recovered", expiresAt: "2026-11-17T00:00:00Z" };
const response = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });

beforeEach(() => {
  mocks.request.mockReset();
  mocks.qr.mockReset().mockResolvedValue("data:image/png;base64,Zml4dHVyZQ==");
});

function fixture(pendingInitially: boolean, checkoutFailureStatus = 503) {
  let pending = pendingInitially;
  let paid = false;
  mocks.request.mockImplementation(async (method: string, path: string) => {
    if (path === "/api/v1/billing/plans") return response([
      { id: "plan", code: "MONTHLY", name: "Monthly", priceVnd: 15000, cycleMonths: 1, revision: 1 },
    ]);
    if (path === base + "/summary") return response({ enforced: false });
    if (path === base + "/subscription") return response({ status: "NONE" });
    if (path === base + "/invoices") return response(pending ? [{ ...invoice, status: paid ? "PAID" : invoice.status }] : []);
    if (path === base + "/invoices/invoice") return response({ ...payment, invoiceStatus: paid ? "PAID" : payment.invoiceStatus });
    if (path.includes("/quote?")) return response({ code: null, discountVnd: 0, amountDueVnd: 15000,
      quoteId: "quote", quoteExpiresAt: new Date(Date.now() + 60000).toISOString(), planRevision: 1 });
    if (method === "POST" && path === base + "/checkout") {
      pending = true;
      return response({ error: { code: "BILLING_PAYMENT_UNAVAILABLE", message: "Payment pending",
        details: { invoiceId: invoice.invoiceId, statusPath: base + "/invoices/invoice" } } }, checkoutFailureStatus);
    }
    throw new Error("Unexpected request " + method + " " + path);
  });
  return () => { paid = true; };
}

it("recovers the pending invoice link after mount without creating another payment", async () => {
  fixture(true);
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  expect(await screen.findByRole("link", { name: /Mở payOS/ })).toHaveAttribute("href", payment.checkoutUrl);
  expect(screen.getByRole("button", { name: "Tạo link thanh toán payOS" })).toBeDisabled();
  expect(mocks.request.mock.calls.filter(([method, path]) => method === "POST" && path.endsWith("/checkout"))).toHaveLength(0);
});

it("shows server period days without overriding the independent selling permission", async () => {
  fixture(false);
  const successfulRead = mocks.request.getMockImplementation()!;
  mocks.request.mockImplementation((method: string, path: string) => path === base + "/subscription"
    ? Promise.resolve(response({ status: "ACTIVE", planCode: "MONTHLY", periodEnd: new Date(Date.now() + 86400000).toISOString() }))
    : path === base + "/summary" ? Promise.resolve(response({ enforced: true, canSell: false })) : successfulRead(method, path));
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  expect(await screen.findByText(/Còn khoảng 1 ngày/)).toBeInTheDocument();
  expect(screen.getByText("Chưa có thuê bao hợp lệ để mở bán.")).toBeInTheDocument();
});

it("shows a failed manual status refresh and safely retries without another checkout", async () => {
  fixture(true);
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  await screen.findByRole("link", { name: /Mở payOS/ });
  mocks.request.mockRejectedValueOnce(new Error("Không thể tải lại trạng thái"));
  fireEvent.click(screen.getByRole("button", { name: "Tải lại trạng thái thanh toán" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải lại trạng thái");
  expect(screen.getByRole("link", { name: /Mở payOS/ })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Tải lại trạng thái thanh toán" }));
  await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
  expect(screen.getByRole("button", { name: "Tạo link thanh toán payOS" })).toBeDisabled();
  expect(mocks.request.mock.calls.filter(([method, path]) => method === "POST" && path.endsWith("/checkout"))).toHaveLength(0);
});

it("retains server-confirmed payment after a recovered invoice leaves the pending list", async () => {
  const markPaid = fixture(true);
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  await screen.findByRole("link", { name: /Mở payOS/ });
  markPaid();
  await waitFor(() => expect(screen.getByRole("cell", { name: "PAID" })).toBeInTheDocument(), { timeout: 6500 });
  expect(screen.getByRole("heading", { name: "Đã xác nhận thanh toán" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: /Mở payOS/ })).not.toBeInTheDocument();
  expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
}, 10000);

it("presents the supplied current checkout QR, invoice reference and advisory expiry timers", async () => {
  fixture(false);
  const successfulRead = mocks.request.getMockImplementation()!;
  mocks.request.mockImplementation((method: string, path: string) => method === "POST" && path === base + "/checkout"
    ? Promise.resolve(response({ ...payment, orderCode: 4000000000, qrPayload: "signed-provider-qr" }))
    : successfulRead(method, path));
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  fireEvent.click(await screen.findByRole("button", { name: "Kiểm tra số tiền thanh toán" }));
  await screen.findByText("Phải thanh toán: 15.000đ");
  fireEvent.click(screen.getByRole("button", { name: "Tạo link thanh toán payOS" }));
  expect(await screen.findByRole("img", { name: "Mã QR thanh toán payOS cho hóa đơn CB-4000000000" }))
    .toHaveAttribute("src", "data:image/png;base64,Zml4dHVyZQ==");
  const paymentHeading = screen.getByRole("heading", { name: "Đang chờ xác nhận" });
  expect(paymentHeading).toBeInTheDocument();
  expect(paymentHeading.querySelector("[role='status']")).not.toBeInTheDocument();
  expect(paymentHeading.closest("[role='status']")).toBeInTheDocument();
  expect(screen.getByRole("timer", { name: "Báo giá còn hiệu lực" })).toHaveAttribute("aria-live", "off");
  expect(mocks.qr).toHaveBeenCalledWith("signed-provider-qr", expect.objectContaining({ margin: 4 }));
  expect(screen.getByRole("button", { name: "Sao chép mã hóa đơn CB-4000000000" })).toBeInTheDocument();
  expect(screen.getByRole("timer", { name: "Link thanh toán còn hiệu lực" })).toHaveAttribute("aria-live", "off");
  expect(screen.getByRole("button", { name: "Tạo link thanh toán payOS" })).toBeDisabled();
  expect(screen.queryByText("Đã xác nhận thanh toán")).not.toBeInTheDocument();
});

it.each([503, 409])("refreshes the reserved invoice after checkout %s without repeating checkout", async checkoutStatus => {
  fixture(false, checkoutStatus);
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  fireEvent.click(await screen.findByRole("button", { name: "Kiểm tra số tiền thanh toán" }));
  await screen.findByText("Phải thanh toán: 15.000đ");
  await waitFor(() => expect(screen.getByRole("button", { name: "Tạo link thanh toán payOS" })).toBeEnabled());
  fireEvent.click(screen.getByRole("button", { name: "Tạo link thanh toán payOS" }));
  expect(await screen.findByRole("link", { name: /Mở payOS/ })).toHaveAttribute("href", payment.checkoutUrl);
  expect(screen.getByRole("button", { name: "Tạo link thanh toán payOS" })).toBeDisabled();
  expect(mocks.request.mock.calls.some(([method, path]) => method === "GET" && path === base + "/invoices/invoice")).toBe(true);
  expect(mocks.request.mock.calls.filter(([method, path]) => method === "POST" && path.endsWith("/checkout"))).toHaveLength(1);
});

it("clears the obsolete provider error only after server polling confirms payment", async () => {
  const markPaid = fixture(false);
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  fireEvent.click(await screen.findByRole("button", { name: "Kiểm tra số tiền thanh toán" }));
  await screen.findByText("Phải thanh toán: 15.000đ");
  await waitFor(() => expect(screen.getByRole("button", { name: "Tạo link thanh toán payOS" })).toBeEnabled());
  fireEvent.click(screen.getByRole("button", { name: "Tạo link thanh toán payOS" }));
  await screen.findByRole("link", { name: /Mở payOS/ });
  expect(screen.getByRole("alert")).toHaveTextContent("Payment pending");
  markPaid();
  await waitFor(() => expect(screen.getByRole("cell", { name: "PAID" })).toBeInTheDocument(), { timeout: 6500 });
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
}, 10000);

it("does not report enforcement disabled after an initial read failure and offers read-only recovery", async () => {
  fixture(false);
  const successfulRead = mocks.request.getMockImplementation()!;
  let failed = true;
  mocks.request.mockImplementation((method: string, path: string) => path === base + "/invoices" && failed
    ? Promise.resolve(response({ error: { code: "BILLING_PAYMENT_UNAVAILABLE", message: "Tạm thời không đọc được hóa đơn" } }, 503))
    : successfulRead(method, path));
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("Tạm thời không đọc được hóa đơn");
  expect(screen.queryByText("Chế độ thu phí chưa được kích hoạt cho các cửa hàng hiện tại.")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Tạo link thanh toán payOS" })).not.toBeInTheDocument();
  failed = false;
  fireEvent.click(screen.getByRole("button", { name: "Tải lại dữ liệu thuê bao" }));
  await screen.findByRole("button", { name: "Kiểm tra số tiền thanh toán" });
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
});

it("keeps owner-only finance controls hidden when retry is still forbidden", async () => {
  fixture(false);
  const successfulRead = mocks.request.getMockImplementation()!;
  mocks.request.mockImplementation((method: string, path: string) => path === base + "/invoices"
    ? Promise.resolve(response({ error: { code: "ACCESS_DENIED", message: "Không có quyền đọc hóa đơn" } }, 403))
    : successfulRead(method, path));
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("Không có quyền đọc hóa đơn");
  expect(screen.queryByRole("button", { name: "Tạo link thanh toán payOS" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Tải lại dữ liệu thuê bao" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không có quyền đọc hóa đơn");
  expect(screen.queryByRole("button", { name: "Kiểm tra số tiền thanh toán" })).not.toBeInTheDocument();
  expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
});

it("uses the same readable keyboard-scrollable table treatment for Seller invoice history", async () => {
  fixture(true);
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  await screen.findByRole("link", { name: /Mở payOS/ });
  const region = screen.getByRole("region", { name: "Lịch sử hóa đơn thuê bao" });
  expect(region).toHaveAttribute("tabindex", "0");
  expect(region).toHaveClass("admin-table-scroll");
  const table = within(region).getByRole("table");
  expect(table).toHaveClass("admin-data-table");
  for (const header of within(table).getAllByRole("columnheader")) expect(header).toHaveAttribute("scope", "col");
});

it("shows a server-confirmed empty catalog without claiming payment success", async () => {
  fixture(false);
  const successfulRead = mocks.request.getMockImplementation()!;
  mocks.request.mockImplementation((method: string, path: string) => path === "/api/v1/billing/plans"
    ? Promise.resolve(response([])) : successfulRead(method, path));
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  await screen.findByText("Chưa có gói đang bán.");
  expect(screen.queryByRole("button", { name: "Tạo link thanh toán payOS" })).not.toBeInTheDocument();
  expect(screen.queryByText("Đã xác nhận thanh toán")).not.toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

it.each([
  ["ACTIVE", true], ["TRIALING", true], ["PENDING_PAYMENT", false],
  ["PAST_DUE", false], ["EXPIRED", false], ["CANCELLED", false],
])("renders %s with the server's selling eligibility and never derives payment success from a label", async (status, canSell) => {
  fixture(status === "PENDING_PAYMENT");
  const successfulRead = mocks.request.getMockImplementation()!;
  mocks.request.mockImplementation((method: string, path: string) => {
    if (path === base + "/summary") return Promise.resolve(response({ enforced: true, canSell }));
    if (path === base + "/subscription") return Promise.resolve(response({ status, planCode: "MONTHLY",
      cancelAtPeriodEnd: false, periodStart: "2026-10-17T00:00:00Z", periodEnd: "2026-11-17T00:00:00Z" }));
    return successfulRead(method, path);
  });
  render(<SellerBillingPanel merchantId="shop" merchantName="Shop" />);
  await screen.findByText(`Gói hiện tại: MONTHLY — trạng thái: ${status}.`);
  expect(screen.getByText(canSell ? "Cửa hàng đang có quyền mở bán." : "Chưa có thuê bao hợp lệ để mở bán.")).toBeInTheDocument();
  expect(screen.queryByText("Đã xác nhận thanh toán")).not.toBeInTheDocument();
  expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
});
