import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { NeedDetail } from "@/features/needs/need-detail";
const mocks = vi.hoisted(() => ({getMyNeed:vi.fn()}));
vi.mock("@/features/needs/need-api",()=>({getMyNeed:mocks.getMyNeed,cancelNeed:vi.fn()}));
vi.mock("@/features/needs/need-response-list",()=>({NeedResponseList:()=>null}));
afterEach(()=>{cleanup();mocks.getMyNeed.mockReset();});

it("ignores a late read from the previous Need after the new Need has loaded",async()=>{
  let resolveOld!: (value: unknown) => void;
  mocks.getMyNeed.mockReturnValueOnce(new Promise(resolve=>{resolveOld=resolve;}));
  const view=render(<NeedDetail needId="need-1"/>);
  const detail={type:"PRODUCT",status:"ACTIVE",allowedActions:[],requestedQuantity:1,radiusMeters:1000,responseCount:0,expiresAt:"2026-10-10"};
  mocks.getMyNeed.mockResolvedValueOnce({...detail,id:"need-2",title:"Nhu cầu mới"});
  view.rerender(<NeedDetail needId="need-2"/>);
  await screen.findByText("Nhu cầu mới");
  await act(async()=>{resolveOld({...detail,id:"need-1",title:"Phản hồi cũ"});});
  expect(screen.getByText("Nhu cầu mới")).toBeInTheDocument();
  expect(screen.queryByText("Phản hồi cũ")).not.toBeInTheDocument();
});

it("clears the previous Need and cancellation confirmation immediately when its ID changes",async()=>{
  mocks.getMyNeed.mockResolvedValueOnce({id:"need-1",title:"Nhu cầu cũ",type:"PRODUCT",status:"ACTIVE",allowedActions:["CANCEL"],requestedQuantity:1,radiusMeters:1000,responseCount:0,expiresAt:"2026-10-10"});
  const view=render(<NeedDetail needId="need-1"/>);
  await screen.findByText("Nhu cầu cũ");
  fireEvent.click(screen.getByRole("button",{name:"Hủy yêu cầu"}));
  expect(screen.getByRole("button",{name:"Xác nhận hủy"})).toBeInTheDocument();
  mocks.getMyNeed.mockReturnValue(new Promise(()=>{}));
  view.rerender(<NeedDetail needId="need-2"/>);
  expect(screen.queryByText("Nhu cầu cũ")).not.toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"Xác nhận hủy"})).not.toBeInTheDocument();
  expect(screen.getByText("Đang tải nhu cầu…")).toBeInTheDocument();
});

it("offers login back to the same Need for an opaque expired session",async()=>{
  mocks.getMyNeed.mockRejectedValue(new CovieApiError(401,"AUTH_REQUIRED","opaque"));
  render(<NeedDetail needId="need-1"/>);
  expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fneeds%2Fneed-1");
  expect(screen.queryByText("opaque")).not.toBeInTheDocument();
});

it("retries an initial transient read without exposing backend details",async()=>{
  mocks.getMyNeed.mockRejectedValueOnce(new CovieApiError(503,"ERROR","private backend detail"));
  render(<NeedDetail needId="need-1"/>);
  const retry=await screen.findByRole("button",{name:"Thử lại"});
  expect(screen.queryByText("private backend detail")).not.toBeInTheDocument();
  mocks.getMyNeed.mockResolvedValue({id:"need-1",title:"Đã tải lại",type:"PRODUCT",status:"ACTIVE",allowedActions:[],requestedQuantity:1,radiusMeters:1000,responseCount:0,expiresAt:"2026-10-10"});
  fireEvent.click(retry);
  expect(await screen.findByText("Đã tải lại")).toBeInTheDocument();
  expect(mocks.getMyNeed).toHaveBeenCalledTimes(2);
});
