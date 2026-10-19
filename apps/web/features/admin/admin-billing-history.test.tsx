import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { AdminBillingHistory } from "./admin-billing-history";

const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.request }));

const priceEntry = (code: string, revision = 1) => ({ version: { id: `${code}-${revision}`, code,
  name: code, revision, priceVnd: 15000, cycleMonths: 1, effectiveAt: "2099-01-01T00:00:00Z" },
  publishedAt: "2026-10-19T00:00:00Z", retired: true });
const response = (entries: unknown) => new Response(JSON.stringify(entries), { status: 200 });

beforeEach(() => { mocks.request.mockReset(); });

it("reads retired plan history without offering a mutation and keeps publication/effective times distinct", async () => {
  mocks.request.mockResolvedValue(response([priceEntry("RETIRED_PLAN", 2)]));
  render(<AdminBillingHistory />);
  fireEvent.change(screen.getByLabelText("Mã gói tra cứu lịch sử"), { target: { value: "RETIRED_PLAN" } });
  fireEvent.click(screen.getByRole("button", { name: "Xem lịch sử giá" }));
  const region = await screen.findByRole("region", { name: "Lịch sử phiên bản Billing" });
  expect(region).toHaveAttribute("tabindex", "0");
  expect(within(region).getByRole("columnheader", { name: "Công bố lúc" })).toBeInTheDocument();
  expect(within(region).getByRole("columnheader", { name: "Có hiệu lực từ" })).toBeInTheDocument();
  expect(within(region).getByText("Đã ngừng bán")).toBeInTheDocument();
  expect(screen.getByText("Lịch sử giá: RETIRED_PLAN")).toBeInTheDocument();
  expect(mocks.request).toHaveBeenCalledWith("GET", "/api/v1/admin/billing/plans/RETIRED_PLAN/prices/history?limit=20&offset=0");
  expect(screen.queryByRole("button", { name: /Công bố|Khôi phục/ })).not.toBeInTheDocument();
});

it("pages policy history and distinguishes successful emptiness from denied reads with retry", async () => {
  mocks.request.mockResolvedValueOnce(new Response(JSON.stringify({ error: { code: "ACCESS_DENIED",
    message: "Không có quyền xem lịch sử công bố" } }), { status: 403 }));
  render(<AdminBillingHistory />);
  fireEvent.click(screen.getByRole("button", { name: "Xem lịch sử chính sách" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không có quyền xem lịch sử công bố");
  expect(screen.queryByText("Không có phiên bản trong trang này.")).not.toBeInTheDocument();
  const entries = Array.from({ length: 20 }, (_, index) => ({ version: { id: `policy-${index}`,
    revision: 21 - index, onboardingMode: "TRIAL", trialDays: 14, graceDays: 3,
    effectiveAt: "2099-01-01T00:00:00Z" }, publishedAt: "2026-10-19T00:00:00Z" }));
  mocks.request.mockResolvedValueOnce(response(entries)).mockResolvedValueOnce(response([]));
  fireEvent.click(screen.getByRole("button", { name: "Thử lại lịch sử" }));
  await screen.findByRole("region", { name: "Lịch sử phiên bản Billing" });
  fireEvent.click(screen.getByRole("button", { name: "Trang lịch sử tiếp" }));
  await screen.findByText("Không có phiên bản trong trang này.");
  expect(mocks.request).toHaveBeenLastCalledWith("GET", "/api/v1/admin/billing/policy/history?limit=20&offset=20");
  expect(screen.getByRole("button", { name: "Trang lịch sử trước" })).toBeEnabled();
  expect(screen.getByRole("button", { name: "Trang lịch sử tiếp" })).toBeDisabled();
  expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
});

it("does not show a delayed previous plan response under a newly entered plan", async () => {
  let finishFirst: (value: Response) => void = () => {};
  mocks.request.mockImplementationOnce(() => new Promise<Response>(resolve => { finishFirst = resolve; }))
    .mockResolvedValueOnce(response([priceEntry("SECOND_PLAN")]));
  render(<AdminBillingHistory />);
  const input = screen.getByLabelText("Mã gói tra cứu lịch sử");
  fireEvent.change(input, { target: { value: "FIRST_PLAN" } });
  fireEvent.click(screen.getByRole("button", { name: "Xem lịch sử giá" }));
  await waitFor(() => expect(mocks.request).toHaveBeenCalledOnce());
  expect(screen.getByRole("status")).toHaveTextContent("Đang tải lịch sử");
  fireEvent.change(input, { target: { value: "SECOND_PLAN" } });
  fireEvent.click(screen.getByRole("button", { name: "Xem lịch sử giá" }));
  await screen.findByText("Lịch sử giá: SECOND_PLAN");
  await act(async () => finishFirst(response([priceEntry("FIRST_PLAN")])));
  expect(screen.queryByText("FIRST_PLAN")).not.toBeInTheDocument();
  expect(screen.getByText("Lịch sử giá: SECOND_PLAN")).toBeInTheDocument();
});
