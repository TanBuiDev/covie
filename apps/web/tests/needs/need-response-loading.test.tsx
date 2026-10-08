import {act, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {beforeEach, expect, it, vi} from "vitest";
import {NeedResponseList} from "@/features/needs/need-response-list";
import {CovieApiError} from "@/lib/api/api-error";
const mocks = vi.hoisted(() => ({listNeedResponses:vi.fn(), listNeedAddresses:vi.fn()}));
vi.mock("@/features/needs/need-api", () => ({...mocks, previewNeedCheckout:vi.fn(), commitNeedCheckout:vi.fn()}));
beforeEach(() => {vi.resetAllMocks(); mocks.listNeedAddresses.mockResolvedValue([]);});
it.each(["responses", "addresses", "cursor"])("requires login and hides stale checkout actions after %s returns 401", async source => {
  const first = {id:"response-1",merchantName:"Người bán cũ",status:"ACTIVE",items:[{id:"item-1",actionable:true,available:true}]};
  const authError = new CovieApiError(401,"AUTH_REQUIRED","private detail");
  mocks.listNeedResponses.mockResolvedValueOnce({items:[first],hasMore:true,nextCursor:"next-1"});
  if(source === "responses") mocks.listNeedResponses.mockReset().mockRejectedValueOnce(authError);
  if(source === "addresses") mocks.listNeedAddresses.mockRejectedValueOnce(authError);
  if(source === "cursor") mocks.listNeedResponses.mockRejectedValueOnce(authError);
  render(<NeedResponseList needId="need-1"/>);
  if(source === "cursor") fireEvent.click(await screen.findByRole("button",{name:"Tải thêm phản hồi"}));
  expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fneeds%2Fneed-1");
  expect(screen.queryByRole("link",{name:"Xem lại và đặt hàng"})).not.toBeInTheDocument();
  expect(screen.queryByText("Chưa có phản hồi phù hợp")).not.toBeInTheDocument();
  expect(screen.queryByText("private detail")).not.toBeInTheDocument();
});
it("shows the selected catalog item and links to review checkout instead of placing an order", async () => {
  mocks.listNeedResponses.mockResolvedValue({items:[{id:"response-1",merchantName:"Người bán",status:"ACTIVE",items:[{
    id:"item-1",catalogItemId:"catalog-1",catalogItemName:"Cơm tấm",locationVariantId:"offer-1",
    currentPrice:{amount:65000,currency:"VND"},available:true,actionable:true,
  }]}],hasMore:false});
  render(<NeedResponseList needId="need-1"/>);
  expect(await screen.findByRole("link",{name:"Cơm tấm"})).toHaveAttribute("href","/items/catalog-1");
  expect(screen.getByRole("link",{name:"Xem lại và đặt hàng"})).toHaveAttribute("href",
    "/checkout?source=NEED_REQUEST&needRequestId=need-1&needResponseItemId=item-1&quantity=1");
  expect(screen.queryByRole("button",{name:"Nhận tại cửa hàng"})).not.toBeInTheDocument();
});
it("keeps available responses when address loading fails and retries addresses separately", async () => {
  mocks.listNeedResponses.mockResolvedValue({items:[{id:"response-1",merchantName:"Người bán còn đọc được",status:"ACTIVE",items:[]}],hasMore:false});
  mocks.listNeedAddresses.mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValueOnce([{id:"address-1",label:"Nhà",addressText:"Địa chỉ nhà",defaultAddress:true}]);
  render(<NeedResponseList needId="need-1"/>);
  await screen.findByRole("heading",{name:"Người bán còn đọc được"});
  expect(await screen.findByText("Không thể tải địa chỉ giao hàng. Bạn vẫn có thể xem phản hồi.")).toBeInTheDocument();
  expect(screen.queryByText("Không thể tải phản hồi. Vui lòng thử lại.")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"Thử lại tải địa chỉ"}));
  await screen.findByRole("option",{name:"Nhà · Địa chỉ nhà"});
  expect(mocks.listNeedResponses).toHaveBeenCalledTimes(1);
});
it("does not append an old need's cursor result after navigating to another need", async () => {
  let resolveOld!: (page: object) => void;
  mocks.listNeedResponses.mockResolvedValueOnce({items:[{id:"old",merchantName:"Người bán cũ",status:"ACTIVE",items:[]}],hasMore:true,nextCursor:"old-cursor"})
    .mockReturnValueOnce(new Promise(resolve=>{resolveOld=resolve;}))
    .mockResolvedValueOnce({items:[{id:"new",merchantName:"Người bán mới",status:"ACTIVE",items:[]}],hasMore:false});
  const {rerender}=render(<NeedResponseList needId="need-old"/>);
  await screen.findByRole("heading",{name:"Người bán cũ"});
  fireEvent.click(screen.getByRole("button",{name:"Tải thêm phản hồi"}));
  rerender(<NeedResponseList needId="need-new"/>);
  await screen.findByRole("heading",{name:"Người bán mới"});
  await act(async()=>resolveOld({items:[{id:"late",merchantName:"Kết quả cũ trả muộn",status:"ACTIVE",items:[]}],hasMore:false}));
  expect(screen.queryByRole("heading",{name:"Kết quả cũ trả muộn"})).not.toBeInTheDocument();
  expect(screen.queryByRole("heading",{name:"Người bán cũ"})).not.toBeInTheDocument();
});
it("loads cursor pages, retains responses on a next-page error and retries the same cursor", async () => {
  const first = {id:"response-1", merchantName:"Người bán đầu", status:"ACTIVE", items:[]};
  const second = {id:"response-2", merchantName:"Người bán sau", status:"ACTIVE", items:[]};
  mocks.listNeedResponses.mockResolvedValueOnce({items:[first],hasMore:true,nextCursor:"next-1"})
    .mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValueOnce({items:[first,second],hasMore:false});
  render(<NeedResponseList needId="need-1"/>);
  await screen.findByRole("heading",{name:"Người bán đầu"});
  fireEvent.click(screen.getByRole("button",{name:"Tải thêm phản hồi"}));
  await screen.findByText("Không thể tải thêm phản hồi. Vui lòng thử lại.");
  expect(screen.getByRole("heading",{name:"Người bán đầu"})).toBeInTheDocument();
  expect(screen.queryByText("Chưa có phản hồi phù hợp")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"Thử lại tải thêm phản hồi"}));
  await screen.findByRole("heading",{name:"Người bán sau"});
  expect(screen.getAllByRole("heading",{name:"Người bán đầu"})).toHaveLength(1);
  expect(mocks.listNeedResponses).toHaveBeenNthCalledWith(2,"need-1","next-1");
  expect(mocks.listNeedResponses).toHaveBeenNthCalledWith(3,"need-1","next-1");
  await waitFor(()=>expect(screen.queryByRole("button",{name:"Tải thêm phản hồi"})).not.toBeInTheDocument());
});
it("separates failed loading from an empty response list and allows retry", async () => {
  mocks.listNeedResponses.mockRejectedValueOnce(new Error("An unexpected error occurred."))
    .mockResolvedValueOnce({items:[]});
  render(<NeedResponseList needId="need-1"/>);
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải phản hồi.");
  expect(screen.queryByText("Chưa có phản hồi phù hợp")).not.toBeInTheDocument();
  expect(screen.queryByText("An unexpected error occurred.")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", {name:"Thử lại tải phản hồi"}));
  expect(await screen.findByText("Chưa có phản hồi phù hợp")).toBeInTheDocument();
});
