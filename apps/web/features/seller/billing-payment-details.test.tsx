import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { BillingPaymentDetails } from "./billing-payment-details";

const mocks = vi.hoisted(() => ({ encode: vi.fn(), copy: vi.fn() }));
vi.mock("qrcode", () => ({ toDataURL: mocks.encode }));
const props = { invoiceId: "invoice", reference: "CB-4000000000", status: "PENDING_PAYMENT",
  expiresAt: "2099-01-01T00:00:00Z", checkout: { invoiceId: "invoice", qrPayload: "signed-provider-qr" } };

beforeEach(() => {
  mocks.encode.mockReset().mockResolvedValue("data:image/png;base64,Zml4dHVyZQ==");
  mocks.copy.mockReset().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", { clipboard: { writeText: mocks.copy } });
});
afterEach(() => vi.unstubAllGlobals());

it("encodes only the matching provider QR locally and copies the exact support reference", async () => {
  render(<BillingPaymentDetails {...props} />);
  expect(await screen.findByRole("img")).toHaveAttribute("src", "data:image/png;base64,Zml4dHVyZQ==");
  expect(mocks.encode).toHaveBeenCalledWith("signed-provider-qr", expect.objectContaining({ margin: 4 }));
  fireEvent.click(screen.getByRole("button", { name: "Sao chép mã hóa đơn CB-4000000000" }));
  expect(await screen.findByRole("status")).toHaveTextContent("Đã sao chép mã hóa đơn.");
  expect(mocks.copy).toHaveBeenCalledWith("CB-4000000000");
});

it("never reuses another invoice's retained QR and explains hosted-page recovery", () => {
  render(<BillingPaymentDetails {...props} checkout={{ invoiceId: "older-invoice", qrPayload: "older-qr" }} />);
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  expect(mocks.encode).not.toHaveBeenCalled();
  expect(screen.getByText(/Mã QR hiện có trên trang thanh toán payOS/)).toBeInTheDocument();
});

it.each([
  ["PAID", "2099-01-01T00:00:00Z"], ["PENDING_PAYMENT", "2020-01-01T00:00:00Z"],
])("hides the QR for %s with expiry %s without making a payment decision", (status, expiresAt) => {
  render(<BillingPaymentDetails {...props} status={status} expiresAt={expiresAt} />);
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  expect(mocks.encode).not.toHaveBeenCalled();
  expect(screen.queryByText("Đã xác nhận thanh toán")).not.toBeInTheDocument();
});

it("keeps a failed local encoding recoverable through the existing payOS link", async () => {
  mocks.encode.mockRejectedValueOnce(new Error("fixture encoding failure"));
  render(<BillingPaymentDetails {...props} />);
  expect(await screen.findByText(/Mã QR hiện có trên trang thanh toán payOS/)).toBeInTheDocument();
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  expect(screen.queryByText(/fixture encoding failure/)).not.toBeInTheDocument();
});

it("does not claim clipboard success on rejection and allows an explicit retry", async () => {
  mocks.copy.mockRejectedValueOnce(new Error("permission denied"));
  render(<BillingPaymentDetails {...props} />);
  const button = screen.getByRole("button", { name: "Sao chép mã hóa đơn CB-4000000000" });
  fireEvent.click(button);
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể sao chép");
  expect(screen.queryByText("Đã sao chép mã hóa đơn.")).not.toBeInTheDocument();
  fireEvent.click(button);
  await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
  expect(screen.getByRole("status")).toHaveTextContent("Đã sao chép mã hóa đơn.");
});
