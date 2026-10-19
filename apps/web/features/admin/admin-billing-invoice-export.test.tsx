import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AdminBillingInvoiceExport } from "./admin-billing-invoice-export";

const mocks = vi.hoisted(() => ({
  get: vi.fn(), createClient: vi.fn(), createUrl: vi.fn(), revokeUrl: vi.fn(),
  downloads: [] as Array<{ href: string; filename: string }>,
}));
vi.mock("@/lib/api/api-client", () => ({ createApiClient: mocks.createClient }));
const NativeURL = URL;
const csv = "invoiceId,merchantId,invoiceNumber,status,amountVnd,createdAtUtc,paidAtUtc\r\n";

beforeEach(() => {
  mocks.get.mockReset().mockImplementation(async () => new Response(csv, { headers: { "Content-Type": "text/csv" } }));
  mocks.createClient.mockReset().mockImplementation((_base: string, observe: (response: Response) => void) => ({
    GET: async (path: string, options: { parseAs?: string }) => {
      const response: Response = await mocks.get(path, options);
      observe(response.clone());
      if (!response.ok) return { response, error: await response.json() };
      return { response, data: options.parseAs === "blob" ? await response.blob() : await response.json() };
    },
  }));
  mocks.createUrl.mockReset().mockReturnValue("blob:billing-csv");
  mocks.revokeUrl.mockReset();
  mocks.downloads.length = 0;
  vi.stubGlobal("URL", Object.assign(class extends NativeURL {}, {
    createObjectURL: mocks.createUrl, revokeObjectURL: mocks.revokeUrl,
  }));
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
    mocks.downloads.push({ href: this.href, filename: this.download });
  });
});
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("downloads an actual CSV Blob only after explicit export and cleans its temporary URL", async () => {
  render(<AdminBillingInvoiceExport />);
  expect(mocks.get).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Xuất trang CSV" }));
  await waitFor(() => expect(mocks.downloads).toEqual([{ href: "blob:billing-csv", filename: "seller-billing-invoices.csv" }]));
  expect(mocks.get).toHaveBeenCalledWith("/api/v1/admin/billing/invoices/export", {
    params: { query: { limit: 20, offset: 0 } }, parseAs: "blob",
  });
  const reader = new FileReader();
  const content = new Promise<string>((resolve, reject) => {
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
  });
  reader.readAsText(mocks.createUrl.mock.calls[0]?.[0]);
  await expect(content).resolves.toBe(csv);
  expect(mocks.revokeUrl).toHaveBeenCalledExactlyOnceWith("blob:billing-csv");
  expect(document.querySelector("a[download]")).toBeNull();
});

it("blocks duplicate exports while the same request is pending", async () => {
  let finish!: (response: Response) => void;
  mocks.get.mockImplementationOnce(() => new Promise<Response>(resolve => { finish = resolve; }));
  render(<AdminBillingInvoiceExport />);
  fireEvent.click(screen.getByRole("button", { name: "Xuất trang CSV" }));
  const pending = screen.getByRole("button", { name: "Đang xuất CSV…" });
  expect(pending).toBeDisabled();
  expect(pending).toHaveAttribute("aria-busy", "true");
  fireEvent.click(pending);
  expect(mocks.get).toHaveBeenCalledTimes(1);
  finish(new Response(csv));
  await waitFor(() => expect(screen.getByRole("button", { name: "Xuất trang CSV" })).toBeEnabled());
  expect(mocks.downloads).toHaveLength(1);
});

it("retains structured denied errors after client parsing and permits a safe retry", async () => {
  mocks.get.mockResolvedValueOnce(new Response(JSON.stringify({ error: { code: "ACCESS_DENIED", message: "Không có quyền đọc tài chính." } }), { status: 403 }));
  render(<AdminBillingInvoiceExport />);
  fireEvent.click(screen.getByRole("button", { name: "Xuất trang CSV" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không có quyền đọc tài chính.");
  expect(mocks.createUrl).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Xuất trang CSV" }));
  await waitFor(() => expect(mocks.downloads).toHaveLength(1));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});
