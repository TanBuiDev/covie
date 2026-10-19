import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { AdminBillingScreen } from "./admin-billing-screen";
import { formatAdminDate } from "./admin-ui";

const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.request }));
vi.mock("./admin-billing-invoice-export", () => ({ AdminBillingInvoiceExport: () => null }));
vi.mock("./admin-billing-reconciliation", () => ({ AdminBillingReconciliation: () => null }));

const invoiceId = "11111111-1111-4111-8111-111111111111";
const merchantId = "22222222-2222-4222-8222-222222222222";
const createdAt = "2026-10-18T00:00:00Z";

beforeEach(() => {
  vi.restoreAllMocks();
  mocks.request.mockReset().mockImplementation(async (_method: string, path: string) => {
    const fixtures: Record<string, unknown> = {
      "/api/v1/billing/plans": [{ id: "plan", code: "MONTHLY", name: "Monthly", revision: 1,
        priceVnd: 15000, cycleMonths: 1, effectiveAt: createdAt }],
      "/api/v1/admin/billing/policy": { id: "policy", revision: 1, onboardingMode: "PAY_BEFORE_SELL",
        trialDays: 0, graceDays: 3, effectiveAt: createdAt },
      "/api/v1/admin/billing/overview": { collectedVnd: 15000, paidInvoices: 1, pendingInvoices: 0,
        entitledShops: 1, reconciliationAlerts: 0 },
      "/api/v1/admin/billing/invoices?limit=20": [{ id: invoiceId, merchantId, invoiceNumber: "CB-READABLE",
        status: "PAID", amountVnd: 15000, createdAt, paidAt: createdAt, allowedActions: [] }],
      "/api/v1/admin/billing/finance-cases?limit=30": [{ id: "case", invoiceId, merchantId, kind: "REFUND",
        status: "CONFIRMED", reason: "Local readability fixture", createdAt }],
      "/api/v1/admin/billing/vouchers": [{ code: "LOCAL", planCode: "MONTHLY", discountType: "FIXED",
        discountValue: 1000, maxRedemptions: 10, perMerchantLimit: 1, startsAt: createdAt,
        endsAt: "2027-01-01T00:00:00Z", enabled: true }],
      "/api/v1/admin/billing/finance-access": [{ userId: "33333333-3333-4333-8333-333333333333",
        role: "READER", assignedBy: "admin", updatedAt: createdAt }],
    };
    const alias = path === "/api/v1/admin/billing/plans" ? "/api/v1/billing/plans"
      : path === "/api/v1/admin/billing/policy/latest" ? "/api/v1/admin/billing/policy" : path;
    if (!(alias in fixtures)) throw new Error("Unexpected request " + path);
    return new Response(JSON.stringify(fixtures[alias]), { status: 200 });
  });
});

it("announces the pending initial billing read as a status", () => {
  mocks.request.mockReturnValue(new Promise<Response>(() => {}));
  render(<AdminBillingScreen />);
  expect(screen.getByRole("status")).toHaveTextContent("Đang tải Billing");
});

it("keeps all financial tables readable in named keyboard-focusable scroll regions", async () => {
  render(<AdminBillingScreen />);
  await screen.findByText("CB-READABLE");
  for (const name of ["Danh sách giao dịch thuê bao", "Danh sách phân quyền tài chính",
    "Danh sách hồ sơ hoàn phí và tranh chấp", "Danh sách mã giảm giá thuê bao"]) {
    const region = screen.getByRole("region", { name });
    expect(region).toHaveAttribute("tabindex", "0");
    expect(region).toHaveClass("admin-table-scroll");
    const table = within(region).getByRole("table");
    expect(table).toHaveClass("admin-data-table");
    for (const header of within(table).getAllByRole("columnheader")) {
      expect(header).toHaveAttribute("scope", "col");
    }
  }
  expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
});

it("labels scheduled Admin prices as latest publications rather than currently effective prices", async () => {
  render(<AdminBillingScreen />);
  await screen.findByText("CB-READABLE");
  expect(screen.getByRole("heading", { name: "Bảng giá công bố mới nhất" })).toBeInTheDocument();
  expect(screen.getByText(/tháng, phiên bản 1; có hiệu lực từ/)).not.toHaveTextContent("T00:00");
  expect(mocks.request).toHaveBeenCalledWith("GET", "/api/v1/admin/billing/plans");
  expect(mocks.request).not.toHaveBeenCalledWith("GET", "/api/v1/billing/plans");
});

it("does not present an unread ledger as zero and recovers initial reads without a mutation", async () => {
  mocks.request.mockRejectedValueOnce(new Error("Không tải được dữ liệu Billing"));
  render(<AdminBillingScreen />);
  expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được dữ liệu Billing");
  expect(screen.queryByText("Tổng quan thu phí Seller")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Tải lại dữ liệu Billing" }));
  await screen.findByText("CB-READABLE");
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
});

it("keeps pricing available without representing forbidden finance reads as zero or empty", async () => {
  const successfulRead = mocks.request.getMockImplementation()!;
  mocks.request.mockImplementation((method: string, path: string) => [
    "/api/v1/admin/billing/overview", "/api/v1/admin/billing/invoices?limit=20",
    "/api/v1/admin/billing/finance-cases?limit=30",
  ].includes(path) ? Promise.resolve(new Response(JSON.stringify({
    error: { code: "ACCESS_DENIED", message: "Financial read denied" },
  }), { status: 403 })) : successfulRead(method, path));
  render(<AdminBillingScreen />);
  await screen.findByRole("alert");
  expect(screen.queryByText(/^Đã thu:/)).not.toBeInTheDocument();
  expect(screen.queryByText("Chưa có giao dịch thuê bao.")).not.toBeInTheDocument();
  expect(screen.queryByText("Không có hồ sơ cần xem.")).not.toBeInTheDocument();
  expect(screen.getByLabelText("Giá mới (VND)")).toHaveValue(15000);
});

it("distinguishes a successfully read empty ledger from a failed or forbidden read", async () => {
  const successfulRead = mocks.request.getMockImplementation()!;
  mocks.request.mockImplementation((method: string, path: string) => {
    if (["/api/v1/admin/billing/invoices?limit=20", "/api/v1/admin/billing/finance-cases?limit=30",
      "/api/v1/admin/billing/vouchers", "/api/v1/admin/billing/finance-access"].includes(path)) {
      return Promise.resolve(new Response("[]", { status: 200 }));
    }
    return successfulRead(method, path);
  });
  render(<AdminBillingScreen />);
  await screen.findByText("Chưa có giao dịch thuê bao.");
  expect(screen.getByText("Không có hồ sơ cần xem.")).toBeInTheDocument();
  expect(screen.getByText("Chưa có mã giảm phí thuê bao.")).toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

it.each(["price", "policy"] as const)("previews %s values before confirmation and never publishes on cancellation", async kind => {
  const reads = mocks.request.getMockImplementation()!;
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  mocks.request.mockImplementation(async (method: string, path: string, body: unknown) => {
    if (method === "GET") return reads(method, path);
    expect(path).toMatch(/\/preview$/);
    expect(confirm).not.toHaveBeenCalled();
    return new Response(JSON.stringify({ previous: { priceVnd: 15000, cycleMonths: 1,
      revision: 1, onboardingMode: "PAY_BEFORE_SELL", trialDays: 0, graceDays: 3 }, proposed: body,
      referencingMerchants: 7, observedAt: createdAt }), { status: 200 });
  });
  render(<AdminBillingScreen />);
  await screen.findByText("CB-READABLE");
  const effectiveAt = "2099-01-01T12:00";
  fireEvent.change(screen.getByLabelText("Có hiệu lực từ"), { target: { value: effectiveAt } });
  fireEvent.click(screen.getByRole("button", { name: kind === "price" ? "Công bố giá mới" : "Công bố chính sách mới" }));
  await waitFor(() => expect(confirm).toHaveBeenCalledOnce());
  const message = confirm.mock.calls[0]?.[0];
  expect(message).toContain("7");
  expect(message).toContain("mọi trạng thái");
  expect(message).toContain("Không tự thay đổi");
  expect(message).toContain(formatAdminDate(new Date(effectiveAt).toISOString()));
  expect(mocks.request.mock.calls.filter(([method]) => method === "POST")).toHaveLength(1);
  confirm.mockRestore();
});

it("binds delayed preview confirmation and publication to the captured request and handles final revision conflicts", async () => {
  const reads = mocks.request.getMockImplementation()!;
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
  let finishPreview: (response: Response) => void = () => {};
  let previewBody: unknown;
  mocks.request.mockImplementation((method: string, path: string, body: unknown) => {
    if (method === "GET") return reads(method, path);
    if (path.endsWith("/preview")) {
      previewBody = body;
      return new Promise<Response>(resolve => { finishPreview = resolve; });
    }
    return Promise.resolve(new Response(JSON.stringify({ error: { code: "VALIDATION_FAILED",
      message: "Reload and review the latest revision." } }), { status: 409 }));
  });
  render(<AdminBillingScreen />);
  await screen.findByText("CB-READABLE");
  fireEvent.change(screen.getByLabelText("Có hiệu lực từ"), { target: { value: "2099-01-01T12:00" } });
  fireEvent.change(screen.getByLabelText("Giá mới (VND)"), { target: { value: "16000" } });
  fireEvent.click(screen.getByRole("button", { name: "Công bố giá mới" }));
  await waitFor(() => expect(previewBody).toBeDefined());
  expect(confirm).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Giá mới (VND)"), { target: { value: "99000" } });
  await act(async () => finishPreview(new Response(JSON.stringify({ previous: { priceVnd: 15000,
    cycleMonths: 1, revision: 1 }, proposed: previewBody, referencingMerchants: 2, observedAt: createdAt }), { status: 200 })));
  expect(await screen.findByRole("alert")).toHaveTextContent("Reload and review");
  const posts = mocks.request.mock.calls.filter(([method]) => method === "POST");
  expect(posts).toHaveLength(2);
  expect(posts[1]?.[1]).toBe("/api/v1/admin/billing/plans/MONTHLY/prices");
  expect(posts[1]?.[2]).toEqual(previewBody);
  expect(posts[1]?.[3]).toEqual({ "If-Match": '"1"' });
  expect(confirm.mock.calls[0]?.[0]).not.toContain("99.000");
  expect(screen.getByLabelText("Giá mới (VND)")).toHaveValue(99000);
  expect(screen.getByRole("button", { name: "Tải lại phiên bản để xem lại" })).toBeInTheDocument();
  confirm.mockRestore();
});

it("never confirms or publishes a rejected stale preview", async () => {
  const reads = mocks.request.getMockImplementation()!;
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
  mocks.request.mockImplementation((method: string, path: string) => method === "GET" ? reads(method, path)
    : Promise.resolve(new Response(JSON.stringify({ error: { code: "VALIDATION_FAILED",
      message: "Reload and review the latest revision." } }), { status: 409 })));
  render(<AdminBillingScreen />);
  await screen.findByText("CB-READABLE");
  fireEvent.change(screen.getByLabelText("Có hiệu lực từ"), { target: { value: "2099-01-01T12:00" } });
  fireEvent.click(screen.getByRole("button", { name: "Công bố chính sách mới" }));
  await screen.findByRole("alert");
  expect(confirm).not.toHaveBeenCalled();
  expect(mocks.request.mock.calls.filter(([method]) => method === "POST")).toHaveLength(1);
  confirm.mockRestore();
});
