import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { AdminBillingReconciliation } from "./admin-billing-reconciliation";

const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.request }));

const invoice = { id: "invoice", invoiceNumber: "CB-100", allowedActions: ["RECONCILE"] };
const result = { operationId: "operation", invoiceId: "invoice", invoiceStatus: "PENDING_PAYMENT",
  providerStatus: "PENDING", discrepancy: "NONE", checkedAt: "2026-10-18T00:00:00Z" };
const response = () => new Response(JSON.stringify(result), { status: 200 });

beforeEach(() => { mocks.request.mockReset(); });

function fill(reason = "Investigate missing callback") {
  fireEvent.change(screen.getByLabelText("Hóa đơn cần đối soát"), { target: { value: "invoice" } });
  fireEvent.change(screen.getByLabelText("Lý do đối soát"), { target: { value: reason } });
}

it("does not expose a mutation when the server does not allow reconciliation", () => {
  render(<AdminBillingReconciliation invoices={[{ ...invoice, allowedActions: [] }]} onComplete={vi.fn()} />);
  expect(screen.queryByRole("button", { name: "Đối soát với payOS" })).not.toBeInTheDocument();
  expect(mocks.request).not.toHaveBeenCalled();
});

it("prevents double submission and displays the server result without claiming payment", async () => {
  let finish!: (value: Response) => void;
  mocks.request.mockReturnValue(new Promise<Response>(resolve => { finish = resolve; }));
  const completed = vi.fn();
  render(<AdminBillingReconciliation invoices={[invoice]} onComplete={completed} />);
  fill();
  const button = screen.getByRole("button", { name: "Đối soát với payOS" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(mocks.request).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText("Lý do đối soát")).toBeDisabled();
  finish(response());
  expect(await screen.findByRole("status")).toHaveTextContent("PENDING_PAYMENT");
  expect(screen.getByRole("status")).toHaveTextContent("PENDING");
  expect(screen.getByRole("status")).toHaveTextContent("NONE");
  await waitFor(() => expect(completed).toHaveBeenCalledTimes(1));
  expect(mocks.request.mock.calls[0]).toEqual(["POST", "/api/v1/admin/billing/invoices/invoice/reconciliation",
    { reason: "Investigate missing callback" }, expect.objectContaining({ "Idempotency-Key": expect.any(String) })]);
});

it("reuses the same key after an uncertain failure and starts a new operation after completion", async () => {
  mocks.request.mockRejectedValueOnce(new Error("Network response unavailable"))
    .mockImplementation(async () => response());
  render(<AdminBillingReconciliation invoices={[invoice]} onComplete={vi.fn()} />);
  fill();
  fireEvent.click(screen.getByRole("button", { name: "Đối soát với payOS" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Network response unavailable");
  fireEvent.click(screen.getByRole("button", { name: "Đối soát với payOS" }));
  await screen.findByRole("status");
  expect(mocks.request.mock.calls[1]?.[3]).toEqual(mocks.request.mock.calls[0]?.[3]);
  await waitFor(() => expect(screen.getByRole("button", { name: "Đối soát với payOS" })).toBeEnabled());
  fireEvent.click(screen.getByRole("button", { name: "Đối soát với payOS" }));
  await waitFor(() => expect(mocks.request).toHaveBeenCalledTimes(3));
  expect(mocks.request.mock.calls[2]?.[3]).not.toEqual(mocks.request.mock.calls[1]?.[3]);
});
