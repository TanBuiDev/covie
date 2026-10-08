import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { OrderDetailView } from "@/features/orders/order-detail";
const mocks=vi.hoisted(()=>({getOrder:vi.fn()}));
vi.mock("@/features/orders/order-api",()=>({getOrder:mocks.getOrder}));
vi.mock("@/features/orders/order-detail-mobile",()=>({OrderDetailMobile:()=>null}));
afterEach(()=>{cleanup();mocks.getOrder.mockReset();});

it("keeps the newest result when retries overlap for the same order",async()=>{
  mocks.getOrder.mockRejectedValueOnce(new CovieApiError(503,"ERROR","initial"));
  render(<OrderDetailView orderId="order-1"/>);
  const retry=await screen.findByRole("button",{name:"Tải lại"});
  let rejectEarlier!: (reason:unknown)=>void;
  mocks.getOrder.mockReturnValueOnce(new Promise((_,reject)=>{rejectEarlier=reject;}));
  mocks.getOrder.mockRejectedValueOnce(new CovieApiError(401,"AUTH_REQUIRED","newest"));
  fireEvent.click(retry);
  fireEvent.click(retry);
  await screen.findByRole("link",{name:"Đăng nhập lại"});
  await act(async()=>{rejectEarlier(new CovieApiError(503,"ERROR","earlier"));});
  expect(screen.getByRole("link",{name:"Đăng nhập lại"})).toBeInTheDocument();
});

it("clears a loaded order immediately when its ID changes",async()=>{
  mocks.getOrder.mockResolvedValueOnce({id:"order-1",orderCode:"OLD-ORDER",type:"PRODUCT",status:"COMPLETED",items:[],allowedActions:[]});
  const view=render(<OrderDetailView orderId="order-1"/>);
  await screen.findByRole("heading",{name:"Đơn OLD-ORDER"});
  mocks.getOrder.mockReturnValue(new Promise(()=>{}));
  view.rerender(<OrderDetailView orderId="order-2"/>);
  expect(screen.queryByRole("heading",{name:"Đơn OLD-ORDER"})).not.toBeInTheDocument();
  expect(screen.getByText("Đang tải đơn hàng…")).toBeInTheDocument();
});

it("ignores a late failure belonging to the previous order",async()=>{
  let rejectOld!: (reason: unknown)=>void;
  mocks.getOrder.mockReturnValueOnce(new Promise((_,reject)=>{rejectOld=reject;}));
  const view=render(<OrderDetailView orderId="order-1"/>);
  await waitFor(()=>expect(mocks.getOrder).toHaveBeenCalledWith("order-1"));
  mocks.getOrder.mockRejectedValueOnce(new CovieApiError(401,"AUTH_REQUIRED","opaque"));
  view.rerender(<OrderDetailView orderId="order-2"/>);
  await screen.findByRole("link",{name:"Đăng nhập lại"});
  await act(async()=>{rejectOld(new CovieApiError(503,"ERROR","old failure"));});
  expect(screen.getByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Forders%2Forder-2");
});

it("offers login back to the Buyer order for an opaque401",async()=>{
  mocks.getOrder.mockRejectedValue(new CovieApiError(401,"AUTH_REQUIRED","opaque"));
  render(<OrderDetailView orderId="order-1"/>);
  expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Forders%2Forder-1");
  expect(screen.queryByText("opaque")).not.toBeInTheDocument();
});

it.each([403,503])("keeps status%i as a read failure with safe copy",async status=>{
  mocks.getOrder.mockRejectedValue(new CovieApiError(status,"ERROR","private token detail"));
  render(<OrderDetailView orderId="order-1"/>);
  expect(await screen.findByRole("button",{name:"Tải lại"})).toBeInTheDocument();
  expect(screen.queryByText("private token detail")).not.toBeInTheDocument();
  expect(screen.queryByRole("link",{name:"Đăng nhập lại"})).not.toBeInTheDocument();
});
