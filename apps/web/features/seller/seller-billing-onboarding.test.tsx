import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { SellerBillingOnboarding } from "./seller-billing-onboarding";

const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.request }));

const plan = { code: "SELLER_STANDARD", name: "CoVie Seller", priceVnd: 10000, cycleMonths: 1 };

beforeEach(() => { mocks.request.mockReset(); });

it("keeps registration unready on policy failure and retries only the policy GET", async () => {
  const ready = vi.fn();
  mocks.request.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(new Response(JSON.stringify({
    enforcementEnabled: false, registrationEnabled: true, plans: [plan],
  })));
  render(<SellerBillingOnboarding onRegistrationReady={ready} />);
  fireEvent.click(await screen.findByRole("button", { name: "Tải lại chính sách thuê bao" }));
  await waitFor(() => expect(ready).toHaveBeenCalledWith(true));
  expect(mocks.request.mock.calls).toEqual([
    ["GET", "/api/v1/billing/onboarding"], ["GET", "/api/v1/billing/onboarding"],
  ]);
});

it("discloses current server price and does not imply buyers pay a subscription", async () => {
  mocks.request.mockResolvedValue(new Response(JSON.stringify({
    enforcementEnabled: true, registrationEnabled: true,
    onboardingMode: "PAY_BEFORE_SELL", trialDays: 0, graceDays: 3, plans: [plan]
  }), { status: 200, headers: { "Content-Type": "application/json" } }));
  render(<SellerBillingOnboarding />);
  await waitFor(() => expect(screen.getByText(/CoVie Seller/)).toBeInTheDocument());
  expect(screen.getByText(/10\.000đ/)).toBeInTheDocument();
  expect(screen.getByText(/mua hàng hoàn toàn miễn phí/)).toBeInTheDocument();
  expect(screen.getByText(/thanh toán thuê bao trước khi mở bán/)).toBeInTheDocument();
  expect(mocks.request).toHaveBeenCalledWith("GET", "/api/v1/billing/onboarding");
});

it("shows the trial configured by Admin, not a hardcoded thirty-day claim", async () => {
  mocks.request.mockResolvedValue(new Response(JSON.stringify({
    enforcementEnabled: true, registrationEnabled: true,
    onboardingMode: "TRIAL", trialDays: 14, graceDays: 2, plans: [plan]
  }), { status: 200 }));
  render(<SellerBillingOnboarding />);
  await waitFor(() => expect(screen.getByText(/dùng thử 14 ngày/)).toBeInTheDocument());
  expect(screen.queryByText(/dùng thử 30 ngày/)).not.toBeInTheDocument();
});

it("does not suggest payment when billing enforcement is disabled", async () => {
  mocks.request.mockResolvedValue(new Response(JSON.stringify({
    enforcementEnabled: false, registrationEnabled: false,
    onboardingMode: "PAY_BEFORE_SELL", trialDays: 0, graceDays: 3, plans: [plan]
  }), { status: 200 }));
  render(<SellerBillingOnboarding />);
  await waitFor(() => expect(screen.getByText(/chưa kích hoạt yêu cầu thu phí Seller/)).toBeInTheDocument());
  expect(screen.getByRole("alert")).toHaveTextContent(/tạm dừng tiếp nhận cửa hàng mới/);
});
