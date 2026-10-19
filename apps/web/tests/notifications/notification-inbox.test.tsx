import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { NotificationInbox } from "@/features/notifications/notification-inbox";
import { CovieApiError } from "@/lib/api/api-error";
const { inbox, markRead, markAllRead, push }=vi.hoisted(()=>({inbox:vi.fn(),markRead:vi.fn(),markAllRead:vi.fn(),push:vi.fn()}));
vi.mock("@/features/notifications/notification-api",()=>({inbox,markRead,markAllRead}));
vi.mock("next/navigation",()=>({useRouter:()=>({push})}));
beforeEach(()=>{inbox.mockReset();markRead.mockReset();markAllRead.mockReset();push.mockReset();});
it("opens the existing Billing owner destination after marking its reminder read", async () => {
  const merchantId = "22222222-2222-4222-8222-222222222222";
  const billingUrl = `/seller/${merchantId}/billing`;
  inbox.mockResolvedValueOnce({ items: [{ id: "billing-reminder", type: "BILLING_RENEWAL_REMINDER",
    read: false, data: { merchantId, billingUrl } }] });
  markRead.mockResolvedValueOnce(undefined);
  render(<NotificationInbox />);
  fireEvent.click(await screen.findByText("Có cập nhật mới"));
  await waitFor(() => expect(push).toHaveBeenCalledWith(billingUrl));
  expect(markRead).toHaveBeenCalledOnce();
  expect(markRead).toHaveBeenCalledWith("billing-reminder");
  expect(screen.queryByText(/Kỳ đã thanh toán không thay đổi/)).not.toBeInTheDocument();
});
it("shows immutable public price notice facts without claiming an automatic charge", async () => {
  inbox.mockResolvedValueOnce({ items: [{ id: "price-notice", type: "BILLING_RENEWAL_REMINDER", read: true,
    data: { noticeKind: "PRICE_CHANGED", deepLink: "/seller", planCode: "FOUNDER", revision: 2, priceVnd: 15000,
      cycleMonths: 1, effectiveAt: "2026-10-20T02:00:00Z" } }] });
  render(<NotificationInbox />);
  expect(await screen.findByText("Bảng giá thuê bao được cập nhật")).toBeInTheDocument();
  expect(screen.getByText(/Gói FOUNDER, phiên bản 2/)).toHaveTextContent("15.000đ / 1 tháng");
  expect(screen.getByText(/Gói FOUNDER, phiên bản 2/)).toHaveTextContent("09:00");
  expect(screen.getByText(/Gói FOUNDER, phiên bản 2/)).toHaveTextContent("Kỳ đã thanh toán không thay đổi; không tự động thu phí.");
  fireEvent.click(screen.getByRole("button", { name: "Bảng giá thuê bao được cập nhật. Mở chi tiết" }));
  await waitFor(() => expect(push).toHaveBeenCalledWith("/seller"));
  expect(markRead).not.toHaveBeenCalled();
});
it("does not expose arbitrary fallback content from a malformed price notice", async () => {
  inbox.mockResolvedValueOnce({ items: [{ id: "invalid-price", type: "BILLING_RENEWAL_REMINDER", read: true,
    title: "private provider diagnostic", body: "private provider diagnostic",
    data: { noticeKind: "PRICE_CHANGED", planCode: "FOUNDER", revision: 2, priceVnd: "private provider diagnostic",
      cycleMonths: 1, effectiveAt: "invalid", body: "private provider diagnostic" } }] });
  render(<NotificationInbox />);
  expect(await screen.findByText("Bảng giá thuê bao được cập nhật")).toBeInTheDocument();
  expect(screen.queryByText(/private provider diagnostic/)).not.toBeInTheDocument();
  expect(screen.queryByText(/Kỳ đã thanh toán không thay đổi/)).not.toBeInTheDocument();
});
it("does not claim the whole inbox is read while another cursor page exists",async()=>{
  inbox.mockResolvedValueOnce({items:[{id:"n1",type:"ORDER_CONFIRMED",read:true,data:{}}],nextCursor:"cursor"});
  render(<NotificationInbox/>);
  await screen.findByRole("button",{name:"Tải thêm"});
  expect(screen.queryByText("Bạn đã đọc tất cả thông báo hiện có.")).not.toBeInTheDocument();
  expect(screen.getByText("Các thông báo đang hiển thị đều đã đọc.")).toBeInTheDocument();
});
it.each(["load","more","item","all"])("shows login after %s returns401",async source=>{
  const authError=new CovieApiError(401,"AUTH_REQUIRED","private diagnostic");
  inbox.mockResolvedValueOnce({items:[{id:"n1",type:"ORDER_CONFIRMED",read:false,data:{}}],nextCursor:"cursor"});
  if(source === "load")inbox.mockReset().mockRejectedValueOnce(authError);
  if(source === "more")inbox.mockRejectedValueOnce(authError);
  markRead.mockRejectedValueOnce(authError);markAllRead.mockRejectedValueOnce(authError);
  render(<NotificationInbox/>);
  if(source === "more")fireEvent.click(await screen.findByRole("button",{name:"Tải thêm"}));
  if(source === "item")fireEvent.click(await screen.findByText("Đơn hàng đã được xác nhận"));
  if(source === "all"){await screen.findByText("Đơn hàng đã được xác nhận");fireEvent.click(screen.getByRole("button",{name:"Đánh dấu đã đọc"}));}
  expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fme%2Fnotifications");
  expect(screen.queryByText("Đơn hàng đã được xác nhận")).not.toBeInTheDocument();
  expect(screen.queryByText("private diagnostic")).not.toBeInTheDocument();
  expect(push).not.toHaveBeenCalled();
});
it("serializes same-batch cursor loads and deduplicates overlapping pages", async()=>{
  const item={id:"n1",type:"ORDER_CONFIRMED",read:false,data:{}};
  let resolvePage!: (page: object)=>void;
  inbox.mockResolvedValueOnce({items:[item],nextCursor:"cursor"}).mockReturnValueOnce(new Promise(resolve=>{resolvePage=resolve;}));
  render(<NotificationInbox/>);
  const more=await screen.findByRole("button",{name:"Tải thêm"});
  act(()=>{more.click();more.click();});
  expect(inbox).toHaveBeenCalledTimes(2);
  await act(async()=>{resolvePage({items:[item,{...item,id:"n2"}]});});
  expect(screen.getAllByText("Đơn hàng đã được xác nhận")).toHaveLength(2);
});
it("serializes item and mark-all clicks within one event batch",async()=>{
  inbox.mockResolvedValueOnce({items:[{id:"n1",type:"ORDER_CONFIRMED",read:false,data:{}}]});
  markRead.mockReturnValueOnce(new Promise(()=>{}));
  render(<NotificationInbox/>);
  const item=await screen.findByText("Đơn hàng đã được xác nhận");
  const all=screen.getByRole("button",{name:"Đánh dấu đã đọc"});
  act(()=>{item.click();all.click();item.click();});
  expect(markRead).toHaveBeenCalledTimes(1);
  expect(markAllRead).not.toHaveBeenCalled();
});
it("shows a retryable load failure without claiming an empty or fully read inbox", async () => {
  inbox.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce({items:[]});
  render(<NotificationInbox/>);
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải thông báo.");
  expect(screen.queryByText("Chưa có thông báo")).not.toBeInTheDocument();
  expect(screen.queryByText("Bạn đã đọc tất cả thông báo hiện có.")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", {name:"Thử lại tải thông báo"}));
  expect(await screen.findByText("Chưa có thông báo")).toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});
it("marks a notification read without exposing backend fallback content",async()=>{inbox.mockResolvedValue({items:[{id:"n1",type:"ORDER_CONFIRMED",read:false,createdAt:"2026-01-01T00:00:00Z",data:{}}]});render(<NotificationInbox/>);await waitFor(()=>expect(screen.getByText("Đơn hàng đã được xác nhận")).toBeInTheDocument());fireEvent.click(screen.getByText("Đơn hàng đã được xác nhận"));await waitFor(()=>expect(markRead).toHaveBeenCalledWith("n1"));});
